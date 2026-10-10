package com.popcornjournal.importer;

import java.time.Instant;
import java.util.List;

/**
 * What an import would do (dry run) or did (commit). Messages never quote review or
 * synopsis text, so the report is safe to print in a terminal or log.
 */
public record ImportReport(
        boolean dryRun,
        String fileName,
        String sha256,
        Instant generatedAt,
        Long importRunId,
        List<SheetSummary> sheets,
        Totals totals,
        List<RowOutcome> rows,
        List<String> newGenres,
        List<String> newPlatforms,
        List<String> newVenues,
        List<SpellingMerge> mergedSpellings,
        List<Message> fileMessages) {

    public enum Level { INFO, WARNING, ERROR }

    public enum RowStatus {
        /** A new movie (or watchlist item) will be created. */
        NEW,
        /** Same movie as an earlier row: added as another viewing of that movie. */
        NEW_VIEWING,
        /** Imported by an earlier run; left untouched. */
        ALREADY_IMPORTED,
        /** Imported earlier and later deleted in the app; not re-created. */
        DELETED_IN_APP,
        /** A movie with the same identity was added in the app; skipped to avoid a duplicate. */
        CONFLICTS_WITH_EXISTING,
        /** Could not be imported; see messages. */
        ERROR
    }

    public enum SheetRole { WATCHED, WATCHLIST, IGNORED }

    public record SheetSummary(String name, SheetRole role, int dataRows, int blankRowsSkipped,
                               List<String> mappedColumns, List<String> unmappedColumns) {
    }

    public record Totals(int rowsRead, int newMovies, int newViewings, int newWatchlistItems,
                         int mergedDuplicateRows, int alreadyImported, int deletedInApp,
                         int conflicts, int errors, int warnings) {
    }

    public record RowOutcome(String sheet, int row, String title, RowStatus status,
                             int viewings, List<Message> messages) {
    }

    public record Message(Level level, String field, String text) {

        public static Message info(String field, String text) {
            return new Message(Level.INFO, field, text);
        }

        public static Message warning(String field, String text) {
            return new Message(Level.WARNING, field, text);
        }

        public static Message error(String field, String text) {
            return new Message(Level.ERROR, field, text);
        }
    }

    /** Several spellings in the sheet that resolve to one platform/venue/genre. */
    public record SpellingMerge(String kind, String storedAs, List<String> spellings) {
    }
}
