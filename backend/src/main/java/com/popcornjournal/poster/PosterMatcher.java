package com.popcornjournal.poster;

import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.support.Titles;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the search result that is really this movie, or nothing. Precision matters more
 * than coverage: a wrong poster is worse than the placeholder, so a candidate needs a
 * matching title plus agreeing year/format evidence to be accepted.
 */
public final class PosterMatcher {

    /** A search result: page title, short description, image URL and search rank (1 = best). */
    public record Candidate(String pageTitle, String description, String imageUrl, String pageUrl, int rank) {
    }

    public record Match(Candidate candidate, int score) {
    }

    static final int ACCEPT_SCORE = 75;

    private static final Pattern PARENTHETICAL = Pattern.compile("\\s*\\(([^)]*)\\)\\s*$");
    private static final Pattern YEAR = Pattern.compile("\\b(18[89]\\d|19\\d\\d|20\\d\\d)\\b");
    private static final List<String> NOT_A_TITLE = List.of(
            "album", "soundtrack", "song", "single by", "video game", "novel", "book", "comic", "episode",
            "list of", "actor", "actress", "director", "singer", "composer", "politician", "cricketer",
            "topics referred to", "disambiguation", "character", "franchise", "film series", "season of");

    private PosterMatcher() {
    }

    public static Optional<Match> choose(PosterQuery query, List<Candidate> candidates) {
        return candidates.stream()
                .filter(c -> c.imageUrl() != null)
                .map(c -> new Match(c, score(query, c)))
                .filter(m -> m.score() >= ACCEPT_SCORE)
                .max(Comparator.comparingInt(Match::score).thenComparing(m -> -m.candidate().rank()));
    }

    static int score(PosterQuery query, Candidate candidate) {
        String description = candidate.description() == null ? "" : candidate.description().toLowerCase(Locale.ROOT);
        Matcher qualifier = PARENTHETICAL.matcher(candidate.pageTitle());
        String bareTitle = qualifier.find() ? candidate.pageTitle().substring(0, qualifier.start()) : candidate.pageTitle();
        String evidence = description + " " + (qualifier.find(0) ? qualifier.group(1).toLowerCase(Locale.ROOT) : "");

        for (String wrongKind : NOT_A_TITLE) {
            if (description.contains(wrongKind) || candidate.pageTitle().toLowerCase(Locale.ROOT).startsWith(wrongKind)) {
                return 0;
            }
        }

        int score = titleScore(query.baseTitle(), bareTitle);
        if (score == 0) {
            return 0;
        }

        boolean isSeries = query.format() == MovieFormat.SERIES;
        boolean saysSeries = evidence.contains("series") || evidence.contains("sitcom") || evidence.contains("show");
        boolean saysFilm = evidence.contains("film") || evidence.contains("movie");
        if (isSeries ? saysSeries : saysFilm && !saysSeries) {
            score += 20;
        } else if (isSeries ? saysFilm && !saysSeries : saysSeries) {
            return 0;
        }

        if (query.releaseYear() != null) {
            score += yearScore(query.releaseYear(), evidence, isSeries);
        }
        if (query.language() != null && description.contains(query.language().toLowerCase(Locale.ROOT))) {
            score += 10;
        }
        if (candidate.rank() == 1) {
            score += 5;
        }
        return score;
    }

    /**
     * 50 for the same title (ignoring punctuation, "&" vs "and" and a leading article),
     * 35 for a one-or-two letter transliteration difference, 30 when one extends the other.
     */
    private static int titleScore(String movieTitle, String pageTitle) {
        String movieKey = withoutArticle(movieTitle);
        String pageKey = withoutArticle(pageTitle);
        if (movieKey.isEmpty() || pageKey.isEmpty()) {
            return 0;
        }
        if (movieKey.equals(pageKey)) {
            return 50;
        }
        // "F1 : The Movie" vs "F1", "Kantara - A legend" vs "Kantara"
        if (movieKey.startsWith(pageKey) || pageKey.startsWith(movieKey)) {
            return 30;
        }
        // "Kanguvaa" vs "Kanguva": with 35 the year must also agree to reach the accept score.
        // Single letters and numbers must match exactly, so "Side A" never passes for "Side B".
        int shorter = Math.min(movieKey.length(), pageKey.length());
        int allowed = Math.min(2, Math.max(1, shorter / 8));
        if (shorter >= 5 && editDistance(movieKey, pageKey) <= allowed
                && distinguishingTokens(movieTitle).equals(distinguishingTokens(pageTitle))) {
            return 35;
        }
        return 0;
    }

    /** Words that tell sequels and parts apart: single letters and plain numbers. */
    static Set<String> distinguishingTokens(String title) {
        Set<String> tokens = new TreeSet<>();
        for (String word : title.replace("&", " and ").toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            // "a" is skipped because it's usually the article, not a part letter
            if (word.length() == 1 && !word.equals("a") || word.matches("\\d+")) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static int yearScore(int releaseYear, String evidence, boolean isSeries) {
        Matcher years = YEAR.matcher(evidence);
        boolean anyYear = false;
        while (years.find()) {
            anyYear = true;
            int year = Integer.parseInt(years.group(1));
            if (year == releaseYear) {
                return 25;
            }
            if (Math.abs(year - releaseYear) == 1) {
                return 10;
            }
        }
        // a film page naming another year is a different film; series run for years, so let it pass
        return anyYear && !isSeries ? -40 : 0;
    }

    private static String withoutArticle(String title) {
        title = title.replace("&", " and ");
        String key = Titles.key(title);
        for (String article : List.of("the", "a", "an")) {
            if (key.startsWith(article) && key.length() > article.length() + 2
                    && title.strip().toLowerCase(Locale.ROOT).startsWith(article + " ")) {
                return key.substring(article.length());
            }
        }
        return key;
    }
}
