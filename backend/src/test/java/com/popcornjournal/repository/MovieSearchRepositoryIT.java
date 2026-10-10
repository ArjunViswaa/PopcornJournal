package com.popcornjournal.repository;

import com.popcornjournal.IntegrationTest;
import com.popcornjournal.dto.MovieSearchCriteria;
import com.popcornjournal.dto.MovieSearchCriteria.MovieSort;
import com.popcornjournal.dto.MovieSearchCriteria.PersonalList;
import com.popcornjournal.dto.MovieSearchCriteria.RatingTier;
import com.popcornjournal.dto.MovieSummaryDto;
import com.popcornjournal.dto.PageResponse;
import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.entity.ViewingMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MovieSearchRepositoryIT extends IntegrationTest {

    @Autowired
    MovieSearchRepository search;

    long netflix;
    long cinemaHall;
    long thriller;

    @BeforeEach
    void seed() {
        thriller = id("INSERT INTO genres (name, category) VALUES ('Thriller - Crime', 'Thriller') RETURNING id");
        long drama = id("INSERT INTO genres (name, category) VALUES ('Drama - Journey', 'Drama') RETURNING id");
        netflix = id("INSERT INTO platforms (name) VALUES ('Netflix') RETURNING id");
        cinemaHall = id("INSERT INTO venues (name) VALUES ('Broadway') RETURNING id");

        long friends = movie("F.R.I.E.N.D.S", "SERIES", 1994, "English", null, true, "Closest to my heart");
        long ponman = movie("Ponman", "MOVIE", 2025, "Malayalam", 9, false, "Sharp");
        long youth = movie("Youth", "MOVIE", 2026, "Tamil", 4, false, null);
        long blank = movie("A Hard Day", "MOVIE", null, "Korean", null, false, null);

        link(ponman, thriller);
        link(youth, drama);
        link(friends, drama);
        viewing(ponman, "2025-12-26", "STREAMING", netflix, null);
        viewing(youth, "2026-04-17", "STREAMING", netflix, null);
        viewing(youth, null, "CINEMA", null, cinemaHall);
        viewing(friends, null, "STREAMING", netflix, null);
        viewing(blank, null, null, null, null);
    }

    private PageResponse<MovieSummaryDto> find(MovieSearchCriteria criteria) {
        return search.search(criteria);
    }

    private static MovieSearchCriteria criteria() {
        return new MovieSearchCriteria(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    @Test
    void searchMatchesIgnoringPunctuation() {
        var byKey = new MovieSearchCriteria("friends", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        assertThat(find(byKey).content()).extracting(MovieSummaryDto::title).containsExactly("F.R.I.E.N.D.S");

        var partial = new MovieSearchCriteria("ONM", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        assertThat(find(partial).content()).extracting(MovieSummaryDto::title).containsExactly("Ponman");

        var wildcard = new MovieSearchCriteria("%", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        assertThat(find(wildcard).totalElements()).isZero();
    }

    @Test
    void summariesCarryGenresAndWhereItWasWatched() {
        MovieSummaryDto youth = find(criteria()).content().stream()
                .filter(m -> m.title().equals("Youth")).findFirst().orElseThrow();

        assertThat(youth.genres()).containsExactly("Drama - Journey");
        assertThat(youth.viewingCount()).isEqualTo(2);
        assertThat(youth.platforms()).containsExactly("Netflix");
        assertThat(youth.venues()).containsExactly("Broadway");
        assertThat(youth.lastWatchedOn()).hasToString("2026-04-17");
        assertThat(youth.hasReview()).isFalse();
    }

    @Test
    void ratingBoundsTreatInfinityAsAboveTen() {
        var nineAndUp = new MovieSearchCriteria(null, null, null, null, null, null, null, null, null, null, null,
                9, null, null, null, null, MovieSort.HIGHEST_RATED, null, null);
        assertThat(find(nineAndUp).content()).extracting(MovieSummaryDto::title)
                .containsExactly("F.R.I.E.N.D.S", "Ponman");

        var upToFive = new MovieSearchCriteria(null, null, null, null, null, null, null, null, null, null, null,
                null, 5, null, null, null, null, null, null);
        assertThat(find(upToFive).content()).extracting(MovieSummaryDto::title).containsExactly("Youth");

        var unrated = new MovieSearchCriteria(null, null, null, null, null, null, null, null, null, null, null,
                null, null, RatingTier.UNRATED, null, null, null, null, null);
        assertThat(find(unrated).content()).extracting(MovieSummaryDto::title).containsExactly("A Hard Day");
    }

    @Test
    void filtersByGenrePlatformLanguageFormatAndYear() {
        assertThat(find(new MovieSearchCriteria(null, null, thriller, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null)).totalElements()).isEqualTo(1);
        assertThat(find(new MovieSearchCriteria(null, null, null, "drama", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null)).totalElements()).isEqualTo(2);
        assertThat(find(new MovieSearchCriteria(null, null, null, null, netflix, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null)).totalElements()).isEqualTo(3);
        assertThat(find(new MovieSearchCriteria(null, null, null, null, null, cinemaHall, ViewingMethod.CINEMA, null,
                null, null, null, null, null, null, null, null, null, null, null)).totalElements()).isEqualTo(1);
        assertThat(find(new MovieSearchCriteria(null, "TAMIL", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null)).totalElements()).isEqualTo(1);
        assertThat(find(new MovieSearchCriteria(null, null, null, null, null, null, null, MovieFormat.SERIES, null,
                null, null, null, null, null, null, null, null, null, null)).totalElements()).isEqualTo(1);
        assertThat(find(new MovieSearchCriteria(null, null, null, null, null, null, null, null, 2025, null, null,
                null, null, null, null, null, null, null, null)).content())
                .extracting(MovieSummaryDto::title).containsExactly("Ponman");
    }

    @Test
    void latestWatchedPutsUndatedMoviesLast() {
        var latest = new MovieSearchCriteria(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, MovieSort.LATEST_WATCHED, null, null);
        assertThat(find(latest).content()).extracting(MovieSummaryDto::title)
                .containsExactly("Youth", "Ponman", "A Hard Day", "F.R.I.E.N.D.S");
    }

    @Test
    void personalLists() {
        assertThat(titles(PersonalList.NO_REVIEW)).containsExactlyInAnyOrder("Youth", "A Hard Day");
        assertThat(titles(PersonalList.UNRATED)).containsExactly("A Hard Day");
        assertThat(titles(PersonalList.TOP_RATED)).containsExactly("F.R.I.E.N.D.S", "Ponman");
        assertThat(titles(PersonalList.RECENTLY_WATCHED)).containsExactly("Youth", "Ponman");
        // missing year, synopsis or genre
        assertThat(titles(PersonalList.MISSING_METADATA)).contains("A Hard Day");
    }

    @Test
    void pagesThroughResults() {
        var page = new MovieSearchCriteria(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, MovieSort.TITLE, 1, 3);
        PageResponse<MovieSummaryDto> second = find(page);

        assertThat(second.totalElements()).isEqualTo(4);
        assertThat(second.totalPages()).isEqualTo(2);
        assertThat(second.content()).extracting(MovieSummaryDto::title).containsExactly("Youth");
        assertThat(second.last()).isTrue();
    }

    private java.util.List<String> titles(PersonalList list) {
        return find(new MovieSearchCriteria(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, list, null, null, null)).content().stream().map(MovieSummaryDto::title).toList();
    }

    private long id(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private long movie(String title, String format, Integer year, String language, Integer rating,
                       boolean beyond, String review) {
        return id(String.format("""
                        INSERT INTO movies (title, title_key, format, release_year, language, rating,
                                            rating_beyond_scale, review, synopsis)
                        VALUES ('%s', '%s', '%s', %s, '%s', %s, %s, %s, %s) RETURNING id""",
                title, com.popcornjournal.support.Titles.key(title), format, year, language, rating, beyond,
                review == null ? "NULL" : "'" + review + "'", year == null ? "NULL" : "'plot'"));
    }

    private void link(long movie, long genre) {
        jdbc.update("INSERT INTO movie_genres (movie_id, genre_id) VALUES (?, ?)", movie, genre);
    }

    private void viewing(long movie, String date, String method, Long platform, Long venue) {
        jdbc.update("INSERT INTO viewing_sessions (movie_id, watched_on, method, platform_id, venue_id) "
                        + "VALUES (?, CAST(? AS date), ?, ?, ?)", movie, date, method, platform, venue);
    }
}
