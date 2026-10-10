package com.popcornjournal.importer;

import com.popcornjournal.IntegrationTest;
import com.popcornjournal.dto.CreateMovieRequest;
import com.popcornjournal.dto.MovieRequest;
import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.entity.WatchlistItem;
import com.popcornjournal.exception.InvalidRequestException;
import com.popcornjournal.importer.ImportReport.RowOutcome;
import com.popcornjournal.importer.ImportReport.RowStatus;
import com.popcornjournal.repository.WatchlistItemRepository;
import com.popcornjournal.service.MovieService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class SpreadsheetImportIT extends IntegrationTest {

    @Autowired
    SpreadsheetImportService importService;

    @Autowired
    MovieService movieService;

    @MockitoSpyBean
    WatchlistItemRepository watchlistRepository;

    private final byte[] sample = WorkbookFixture.sample().toBytes();

    @Test
    void dryRunReportsThePlanAndWritesNothing() {
        ImportReport report = importService.preview(sample, "journal.xlsx");

        assertThat(report.dryRun()).isTrue();
        assertThat(report.totals().rowsRead()).isEqualTo(9);
        assertThat(report.totals().newMovies()).isEqualTo(7);
        assertThat(report.totals().newViewings()).isEqualTo(8);
        assertThat(report.totals().mergedDuplicateRows()).isEqualTo(1);
        assertThat(report.totals().newWatchlistItems()).isEqualTo(1);
        assertThat(report.totals().errors()).isZero();
        assertThat(report.newPlatforms()).containsExactly("Jio Hotstar", "Netflix", "Prime Video", "YTS");
        assertThat(report.newVenues()).containsExactly("Star Cinemas - Chennai");
        assertThat(report.newGenres()).contains("Comedy - Dark", "Feel Good");
        assertThat(report.mergedSpellings()).anySatisfy(m -> {
            assertThat(m.storedAs()).isEqualTo("Jio Hotstar");
            assertThat(m.spellings()).containsExactlyInAnyOrder("jio Hotstar", "Jio Hotstar");
        });
        assertThat(report.sheets()).extracting(ImportReport.SheetSummary::blankRowsSkipped).containsExactly(0, 1);

        assertThat(count("movies")).isZero();
        assertThat(count("viewing_sessions")).isZero();
        assertThat(count("import_runs")).isZero();
    }

    @Test
    void commitImportsMoviesViewingsAndSourceRows() {
        ImportReport report = importService.commit(sample, "journal.xlsx", "CLI");

        assertThat(report.importRunId()).isNotNull();
        assertThat(count("movies")).isEqualTo(7);
        assertThat(count("viewing_sessions")).isEqualTo(8);
        assertThat(count("watchlist_items")).isEqualTo(1);
        assertThat(count("import_source_rows")).isEqualTo(9);
        // seven from watched rows and the dropdown, plus the watchlist row's genre
        assertThat(count("genres")).isEqualTo(8);

        // ∞ is its own tier, not a number
        Map<String, Object> kites = jdbc.queryForMap(
                "SELECT rating, rating_beyond_scale FROM movies WHERE title = 'Paper Kites' AND release_year = 2019");
        assertThat(kites.get("rating")).isNull();
        assertThat(kites.get("rating_beyond_scale")).isEqualTo(true);

        // numeric title kept as text
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movies WHERE title = '2018'", Long.class)).isEqualTo(1);

        // theatre + platform: a dated cinema viewing and an undated streaming viewing
        List<Map<String, Object>> harbour = jdbc.queryForList("""
                SELECT s.method, s.watched_on, p.name AS platform, v.name AS venue
                FROM viewing_sessions s JOIN movies m ON m.id = s.movie_id
                LEFT JOIN platforms p ON p.id = s.platform_id LEFT JOIN venues v ON v.id = s.venue_id
                WHERE m.title = 'Harbour Lights' ORDER BY s.method""");
        assertThat(harbour).hasSize(2);
        assertThat(harbour.get(0)).containsEntry("method", "CINEMA").containsEntry("venue", "Star Cinemas - Chennai");
        assertThat(harbour.get(0).get("watched_on")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 6, 20)));
        assertThat(harbour.get(1)).containsEntry("method", "STREAMING").containsEntry("platform", "Prime Video");
        assertThat(harbour.get(1).get("watched_on")).isNull();

        // the repeated row became a second viewing carrying its own rating and review
        List<Map<String, Object>> signal = jdbc.queryForList("""
                SELECT s.watched_on, s.rating, s.notes FROM viewing_sessions s JOIN movies m ON m.id = s.movie_id
                WHERE m.title = 'Midnight Signal' ORDER BY s.watched_on""");
        assertThat(signal).hasSize(2);
        assertThat(signal.get(0).get("rating")).isNull();
        assertThat(signal.get(1)).containsEntry("rating", 8).containsEntry("notes", "Second time less tense.");
        assertThat(jdbc.queryForObject("SELECT rating FROM movies WHERE title = 'Midnight Signal'", Integer.class))
                .isEqualTo(9);

        // download sites are not "streaming"
        assertThat(jdbc.queryForObject("""
                SELECT s.method FROM viewing_sessions s JOIN platforms p ON p.id = s.platform_id
                WHERE p.name = 'YTS'""", String.class)).isEqualTo("OTHER");

        // no viewing is invented for a row without date, platform or theatre
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM viewing_sessions s JOIN movies m ON m.id = s.movie_id
                WHERE m.title = 'Unwatched Maybe'""", Long.class)).isZero();

        // the original cells are kept for traceability
        String raw = jdbc.queryForObject(
                "SELECT raw_values::text FROM import_source_rows WHERE row_number = 4 AND sheet_name = 'Watch_Sheet'",
                String.class);
        assertThat(raw).contains("\"Rating\": \"Infinity\"").contains("\"Platform\": \"jio Hotstar\"");
    }

    @Test
    void rerunningTheSameFileCreatesNothing() {
        importService.commit(sample, "journal.xlsx", "CLI");
        ImportReport second = importService.commit(sample, "journal.xlsx", "CLI");

        assertThat(second.totals().newMovies()).isZero();
        assertThat(second.totals().newViewings()).isZero();
        assertThat(second.totals().newWatchlistItems()).isZero();
        assertThat(second.totals().alreadyImported()).isEqualTo(9);
        assertThat(count("movies")).isEqualTo(7);
        assertThat(count("viewing_sessions")).isEqualTo(8);
        assertThat(count("import_source_rows")).isEqualTo(9);
        assertThat(count("import_runs")).isEqualTo(2);
    }

    @Test
    void editsMadeInTheAppSurviveAReimport() {
        importService.commit(sample, "journal.xlsx", "CLI");
        jdbc.update("UPDATE movies SET rating = 3, title = 'Midnight Signal (edited)' WHERE title = 'Midnight Signal'");

        ImportReport again = importService.preview(sample, "journal.xlsx");

        RowOutcome row = again.rows().stream().filter(r -> r.row() == 2).findFirst().orElseThrow();
        assertThat(row.status()).isEqualTo(RowStatus.ALREADY_IMPORTED);
        assertThat(row.messages()).anySatisfy(m -> assertThat(m.text()).contains("rating").contains("title"));
        importService.commit(sample, "journal.xlsx", "CLI");
        assertThat(jdbc.queryForObject("SELECT rating FROM movies WHERE title = 'Midnight Signal (edited)'",
                Integer.class)).isEqualTo(3);
        assertThat(count("movies")).isEqualTo(7);
    }

    @Test
    void moviesDeletedInTheAppAreNotRecreated() {
        importService.commit(sample, "journal.xlsx", "CLI");
        Long id = jdbc.queryForObject("SELECT id FROM movies WHERE title = 'Office Hours'", Long.class);
        movieService.delete(id);

        ImportReport report = importService.commit(sample, "journal.xlsx", "CLI");

        assertThat(report.totals().deletedInApp()).isEqualTo(1);
        assertThat(count("movies")).isEqualTo(6);
    }

    @Test
    void aMovieAddedByHandIsNotDuplicatedByTheImport() {
        movieService.create(new CreateMovieRequest(new MovieRequest("Office Hours", null, MovieFormat.SERIES, 2015,
                "English", null, 9, false, null, null, false, null, null, null, null, null, List.of()), null));

        ImportReport report = importService.commit(sample, "journal.xlsx", "CLI");

        assertThat(report.totals().conflicts()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movies WHERE title = 'Office Hours'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void sameTitleWithADifferentYearIsFlaggedButKeptSeparate() {
        ImportReport report = importService.preview(sample, "journal.xlsx");

        List<RowOutcome> kites = report.rows().stream().filter(r -> "Paper Kites".equals(r.title())).toList();
        assertThat(kites).hasSize(2).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo(RowStatus.NEW);
            assertThat(r.messages()).anySatisfy(m -> assertThat(m.text()).contains("different year"));
        });
    }

    @Test
    void invalidRowsAreReportedAndSkipped() {
        byte[] file = WorkbookFixture.create()
                .watched(null, "No title here", "Action", 5.0, null, "English", "Movie", null, null, null, "-")
                .watched("Bad Format", null, "Action", 5.0, null, "English", "Podcast", null, null, null, "-")
                .watched("Odd Rating", null, "Action", 15.0, null, "English", "Movie", "Netflix", 2020.0, null, "-")
                .toBytes();

        ImportReport report = importService.commit(file, "bad.xlsx", "WEB");

        assertThat(report.totals().errors()).isEqualTo(2);
        assertThat(report.totals().newMovies()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rating FROM movies WHERE title = 'Odd Rating'", Integer.class)).isNull();
    }

    @Test
    void aFailureMidImportRollsBackEverything() {
        doThrow(new IllegalStateException("simulated failure")).when(watchlistRepository).save(any(WatchlistItem.class));

        assertThatThrownBy(() -> importService.commit(sample, "journal.xlsx", "CLI"))
                .hasMessageContaining("simulated failure");

        assertThat(count("movies")).isZero();
        assertThat(count("viewing_sessions")).isZero();
        assertThat(count("import_runs")).isZero();
        assertThat(count("genres")).isZero();
    }

    @Test
    void rejectsFilesThatAreNotWorkbooks() {
        assertThatThrownBy(() -> importService.preview("name,rating\nx,1".getBytes(), "fake.xlsx"))
                .isInstanceOf(InvalidRequestException.class);
    }
}
