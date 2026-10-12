package com.popcornjournal.poster;

import com.popcornjournal.entity.MovieFormat;

import java.util.regex.Pattern;

/** What is known about a movie when looking for its poster. */
public record PosterQuery(long movieId, String title, MovieFormat format, Integer releaseYear, String language) {

    // "Family Man : S2", "Squid Game ( S1 )", "Paatal Lok - Season 2"
    private static final Pattern SEASON_SUFFIX =
            Pattern.compile("\\s*[-:(]?\\s*\\(?\\s*(s|season)\\s*\\d+\\s*\\)?\\s*$", Pattern.CASE_INSENSITIVE);

    /** The title without a season marker, which series articles don't carry. */
    public String baseTitle() {
        String base = format == MovieFormat.SERIES ? SEASON_SUFFIX.matcher(title).replaceFirst("") : title;
        return base.isBlank() ? title : base.strip();
    }

    public String searchText() {
        if (format == MovieFormat.SERIES) {
            return baseTitle() + " TV series";
        }
        StringBuilder text = new StringBuilder(title);
        if (releaseYear != null) {
            text.append(' ').append(releaseYear);
        }
        // the language helps tell apart generic titles ("Youth", "Animal"); English adds nothing
        if (language != null && !language.equalsIgnoreCase("English")) {
            text.append(' ').append(language);
        }
        return text.append(" film").toString();
    }

    /**
     * Fallback search tolerant of transliteration differences ("Gymkhaana" finds
     * "Gymkhana"): every word gets Wikipedia's fuzzy operator. Matches found this way
     * still need the release year to agree before they're accepted.
     */
    public String fuzzySearchText() {
        StringBuilder text = new StringBuilder();
        for (String word : baseTitle().replace("&", " ").split("[^\\p{L}\\p{N}']+")) {
            if (!word.isBlank()) {
                text.append(word).append("~ ");
            }
        }
        return text.append(format == MovieFormat.SERIES ? "TV series" : "film").toString();
    }
}
