package com.popcornjournal.poster;

import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.poster.PosterMatcher.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Candidates below are shaped like real Wikipedia search results. */
class PosterMatcherTest {

    private static Candidate page(int rank, String title, String description) {
        return new Candidate(title, description, "https://upload.wikimedia.org/x/" + rank + ".jpg",
                "https://en.wikipedia.org/wiki/" + title, rank);
    }

    private static Optional<String> pick(PosterQuery query, Candidate... candidates) {
        return PosterMatcher.choose(query, List.of(candidates)).map(m -> m.candidate().pageTitle());
    }

    private static PosterQuery movie(String title, Integer year, String language) {
        return new PosterQuery(1, title, MovieFormat.MOVIE, year, language);
    }

    @Test
    void exactTitleYearAndFilm() {
        assertThat(pick(movie("Ronth", 2025, "Malayalam"),
                page(1, "Ronth", "2025 Indian Malayalam film"),
                page(3, "Nayattu (2021 film)", "2021 film directed by Martin Prakkat")))
                .contains("Ronth");
    }

    @Test
    void sameNameButDifferentYearIsRejected() {
        assertThat(pick(movie("Kantara - A legend", 2022, "Kannada"),
                page(1, "Kantara (2022 film)", "2022 Indian film by Rishab Shetty"),
                page(2, "Kantara: Chapter 1", "2025 Indian film by Rishab Shetty")))
                .contains("Kantara (2022 film)");

        assertThat(pick(movie("Kantara", 2025, "Kannada"), page(1, "Kantara (2022 film)", "2022 Indian film")))
                .isEmpty();
    }

    @Test
    void albumsGamesAndPeopleAreNeverPosters() {
        assertThat(pick(movie("F1 : The Movie", 2025, "English"),
                page(1, "F1 (film)", "2025 film by Joseph Kosinski"),
                page(3, "F1 the Album", "2025 soundtrack album by various artists"),
                page(4, "F1 25", "2025 racing video game")))
                .contains("F1 (film)");

        assertThat(pick(movie("Dileesh Pothan", null, null), page(1, "Dileesh Pothan", "Malayalam film director and actor")))
                .isEmpty();
    }

    @Test
    void seriesMatchIgnoresSeasonMarkersAndLeadingArticle() {
        PosterQuery familyMan = new PosterQuery(1, "Family Man : S2", MovieFormat.SERIES, 2021, "Hindi");
        assertThat(familyMan.baseTitle()).isEqualTo("Family Man");
        assertThat(familyMan.searchText()).isEqualTo("Family Man TV series");

        assertThat(pick(familyMan,
                page(1, "The Family Man (Indian TV series)", "Indian Hindi-language spy thriller television series"),
                page(2, "Wonder Man (TV series)", "2026 Marvel Studios television series")))
                .contains("The Family Man (Indian TV series)");
        assertThat(new PosterQuery(1, "Squid Game ( S1 )", MovieFormat.SERIES, 2021, null).baseTitle())
                .isEqualTo("Squid Game");
    }

    @Test
    void aFilmIsNotMatchedToASeriesOfTheSameName() {
        assertThat(pick(movie("Paatal Lok", 2020, "Hindi"), page(1, "Paatal Lok", "Indian crime thriller web series")))
                .isEmpty();
    }

    @Test
    void anUnrelatedTitleIsNeverAccepted() {
        assertThat(pick(movie("Youth", 2026, "Tamil"), page(1, "Yuva", "2004 Indian Hindi-language film")))
                .isEmpty();
    }

    @Test
    void resultsWithoutAnImageAreSkipped() {
        Candidate noImage = new Candidate("Ronth", "2025 Indian Malayalam film", null, "u", 1);
        assertThat(PosterMatcher.choose(movie("Ronth", 2025, "Malayalam"), List.of(noImage))).isEmpty();
    }

    @Test
    void ampersandMatchesAnd() {
        assertThat(pick(movie("Harry Potter & The Goblet of Fire", 2005, "English"),
                page(1, "Harry Potter and the Goblet of Fire (film)", "2005 film by Mike Newell")))
                .contains("Harry Potter and the Goblet of Fire (film)");
    }

    @Test
    void transliterationDifferencesNeedTheYearToAgree() {
        assertThat(pick(movie("Alappuzha Gymkhaana", 2025, "Malayalam"),
                page(2, "Alappuzha Gymkhana", "2025 Malayalam film by Khalid Rahman")))
                .contains("Alappuzha Gymkhana");
        // same spelling closeness, wrong year: rejected
        assertThat(pick(movie("Om Shanti Om", 2007, "Hindi"),
                page(2, "Om Shanthi Om", "2015 Indian film by D. Suryaprabhakar")))
                .isEmpty();
        // close spelling without any year evidence isn't enough either
        assertThat(pick(movie("Kanguvaa", null, "Tamil"), page(1, "Kanguva", "Indian film by Siva")))
                .isEmpty();
        assertThat(PosterMatcher.editDistance("kanguvaa", "kanguva")).isEqualTo(1);
    }

    @Test
    void partsAndSidesAreNeverTreatedAsTypos() {
        assertThat(pick(movie("Sapta Sagaradaache Ello - Side B", 2023, "Kannada"),
                page(1, "Sapta Saagaradaache Ello: Side A", "2023 Indian Kannada-language film")))
                .isEmpty();
        assertThat(pick(movie("Sapta Sagaradaache Ello - Side B", 2023, "Kannada"),
                page(2, "Sapta Saagaradaache Ello: Side B", "2023 Indian Kannada-language film")))
                .contains("Sapta Saagaradaache Ello: Side B");
        assertThat(pick(movie("Drishyam 3", 2026, "Malayalam"), page(1, "Drishyam 2", "2026 Malayalam film")))
                .isEmpty();
    }

    @Test
    void fuzzySearchTextMarksEveryWord() {
        assertThat(movie("Alappuzha Gymkhaana", 2025, "Malayalam").fuzzySearchText())
                .isEqualTo("Alappuzha~ Gymkhaana~ film");
        assertThat(new PosterQuery(1, "Family Man : S2", MovieFormat.SERIES, 2021, null).fuzzySearchText())
                .isEqualTo("Family~ Man~ TV series");
    }

    @Test
    void searchTextAddsYearAndNonEnglishLanguage() {
        assertThat(movie("Youth", 2026, "Tamil").searchText()).isEqualTo("Youth 2026 Tamil film");
        assertThat(movie("Sinners", 2025, "English").searchText()).isEqualTo("Sinners 2025 film");
    }
}
