package com.popcornjournal.importer;

import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.importer.ImportReport.Level;
import com.popcornjournal.importer.RowInterpreter.WatchedRow;
import com.popcornjournal.importer.SpreadsheetReader.SheetRow;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RowInterpreterTest {

    private final RowInterpreter interpreter =
            new RowInterpreter(Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC));
    private final ColumnMapping.Resolved columns =
            ColumnMapping.Resolved.of(Arrays.asList(WorkbookFixture.WATCH_HEADERS));

    private WatchedRow interpret(Object... values) {
        Map<String, Object> cells = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                cells.put(WorkbookFixture.WATCH_HEADERS[i], values[i]);
            }
        }
        return interpreter.watched(new SheetRow(7, cells), columns);
    }

    @Test
    void readsATypicalRow() {
        WatchedRow row = interpret("  Sinners ", "Twins return.", "Thriller - Action", 8.0, "Great.", "English",
                "Movie", "Jio Hotstar", 2025.0, LocalDateTime.of(2026, 3, 21, 0, 0), "-");

        assertThat(row.title()).isEqualTo("Sinners");
        assertThat(row.rating()).isEqualTo(8);
        assertThat(row.ratingBeyondScale()).isFalse();
        assertThat(row.releaseYear()).isEqualTo(2025);
        assertThat(row.watchedOn()).isEqualTo(LocalDate.of(2026, 3, 21));
        assertThat(row.venue()).isNull();
        assertThat(row.messages()).isEmpty();
        assertThat(row.raw()).containsEntry("Watch Date", "2026-03-21").containsEntry("Year", 2025L);
    }

    @Test
    void infinityIsTheBeyondScaleTier() {
        WatchedRow row = interpret("Interstellar", null, null, "Infinity", null, null, "Movie", null, null, null, null);

        assertThat(row.rating()).isNull();
        assertThat(row.ratingBeyondScale()).isTrue();
        assertThat(row.messages()).isEmpty();
    }

    @Test
    void outOfRangeRatingIsDroppedWithAWarning() {
        WatchedRow row = interpret("X", null, null, 11.0, null, null, "Movie", null, null, null, null);

        assertThat(row.rating()).isNull();
        assertThat(row.messages()).singleElement().satisfies(m -> assertThat(m.level()).isEqualTo(Level.WARNING));
    }

    @Test
    void numericTitlesBecomeText() {
        WatchedRow row = interpret(2018.0, null, null, 9.0, null, null, "Movie", null, 2023.0, null, null);

        assertThat(row.title()).isEqualTo("2018");
        assertThat(row.hasErrors()).isFalse();
    }

    @Test
    void aDashMeansNothingWasRecorded() {
        WatchedRow row = interpret("Y", null, null, "-", null, null, "Movie", "-", "-", "-", "-");

        assertThat(row.rating()).isNull();
        assertThat(row.platform()).isNull();
        assertThat(row.releaseYear()).isNull();
        assertThat(row.watchedOn()).isNull();
        assertThat(row.venue()).isNull();
        assertThat(row.messages()).isEmpty();
    }

    @Test
    void textDatesAreParsedAndBadOnesKeptOut() {
        assertThat(interpret("A", null, null, null, null, null, "Movie", null, null, "12/04/2026", null).watchedOn())
                .isEqualTo(LocalDate.of(2026, 4, 12));

        WatchedRow bad = interpret("B", null, null, null, null, null, "Movie", null, null, "last summer", null);
        assertThat(bad.watchedOn()).isNull();
        assertThat(bad.messages()).anySatisfy(m -> assertThat(m.text()).contains("not a date"));
    }

    @Test
    void futureWatchDatesAreFlagged() {
        WatchedRow row = interpret("C", null, null, null, null, null, "Movie", null, null,
                LocalDateTime.of(2027, 1, 1, 0, 0), null);

        assertThat(row.watchedOn()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(row.messages()).anySatisfy(m -> assertThat(m.text()).contains("future"));
    }

    @Test
    void missingTitleAndUnknownFormatAreErrors() {
        assertThat(interpret(null, "x", null, null, null, null, "Movie", null, null, null, null).hasErrors()).isTrue();
        assertThat(interpret("Z", null, null, null, null, null, "Podcast", null, null, null, null).hasErrors()).isTrue();
        assertThat(interpret("S", null, null, null, null, null, "Series", null, null, null, null).format())
                .isEqualTo(MovieFormat.SERIES);
    }

    @Test
    void identityIgnoresPunctuationAndCase() {
        WatchedRow dotted = interpret("F.R.I.E.N.D.S", null, null, null, null, "English", "Series", null, 1994.0, null, null);
        WatchedRow plain = interpret("Friends", null, null, null, null, "english", "Series", null, 1994.0, null, null);

        assertThat(dotted.identityKey()).isEqualTo(plain.identityKey());
    }

    @Test
    void longDropdownFormulasAreJoined() {
        assertThat(SpreadsheetReader.listValues("\"Action,Dram\"&\"a - Real Life,Horror\""))
                .containsExactly("Action", "Drama - Real Life", "Horror");
    }
}
