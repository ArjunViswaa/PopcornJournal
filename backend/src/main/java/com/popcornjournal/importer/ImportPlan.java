package com.popcornjournal.importer;

import com.popcornjournal.entity.ImportSourceRow;
import com.popcornjournal.entity.Movie;
import com.popcornjournal.entity.ViewingMethod;
import com.popcornjournal.importer.ImportReport.Message;
import com.popcornjournal.importer.ImportReport.RowStatus;
import com.popcornjournal.importer.ImportReport.SheetSummary;
import com.popcornjournal.importer.RowInterpreter.WatchedRow;
import com.popcornjournal.importer.RowInterpreter.WatchlistRow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Everything the importer decided, before anything is written. */
final class ImportPlan {

    final List<SheetSummary> sheets = new ArrayList<>();
    final List<Message> fileMessages = new ArrayList<>();
    String watchedSheetName;
    String watchlistSheetName;
    final List<PlannedMovieRow> movieRows = new ArrayList<>();
    final List<PlannedWatchlistRow> watchlistRows = new ArrayList<>();
    /** Unused dropdown values from the sheet's Genre column, offered as genres. */
    final List<String> genreOptionKeys = new ArrayList<>();
    NameBook platforms;
    NameBook venues;
    NameBook genres;

    boolean hasWatchedSheet() {
        return watchedSheetName != null;
    }

    static final class PlannedMovieRow {
        final WatchedRow row;
        final List<Message> messages;
        String recordKey;
        RowStatus status;
        /** First row with the same identity, when this row is a repeat viewing. */
        PlannedMovieRow primary;
        /** Set when the movie already exists in the database. */
        Movie existingMovie;
        ImportSourceRow existingSourceRow;
        String genreKey;
        final List<PlannedViewing> viewings = new ArrayList<>();

        PlannedMovieRow(WatchedRow row) {
            this.row = row;
            this.messages = new ArrayList<>(row.messages());
        }
    }

    static final class PlannedWatchlistRow {
        final WatchlistRow row;
        final List<Message> messages;
        String recordKey;
        RowStatus status;
        ImportSourceRow existingSourceRow;
        String genreKey;

        PlannedWatchlistRow(WatchlistRow row) {
            this.row = row;
            this.messages = new ArrayList<>(row.messages());
        }
    }

    /** One viewing session to create. platformKey and venueKey refer to the name books. */
    record PlannedViewing(String importKey, ViewingMethod method, String platformKey, String venueKey,
                          LocalDate watchedOn, Integer rating, boolean ratingBeyondScale, String notes) {
    }
}
