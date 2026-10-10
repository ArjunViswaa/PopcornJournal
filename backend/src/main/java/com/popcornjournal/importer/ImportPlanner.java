package com.popcornjournal.importer;

import com.popcornjournal.entity.Genre;
import com.popcornjournal.entity.ImportSourceRow;
import com.popcornjournal.entity.Movie;
import com.popcornjournal.entity.Platform;
import com.popcornjournal.entity.Venue;
import com.popcornjournal.entity.ViewingMethod;
import com.popcornjournal.entity.WatchlistItem;
import com.popcornjournal.importer.ImportPlan.PlannedMovieRow;
import com.popcornjournal.importer.ImportPlan.PlannedViewing;
import com.popcornjournal.importer.ImportPlan.PlannedWatchlistRow;
import com.popcornjournal.importer.ImportReport.Message;
import com.popcornjournal.importer.ImportReport.RowStatus;
import com.popcornjournal.importer.ImportReport.SheetRole;
import com.popcornjournal.importer.ImportReport.SheetSummary;
import com.popcornjournal.importer.RowInterpreter.WatchedRow;
import com.popcornjournal.importer.RowInterpreter.WatchlistRow;
import com.popcornjournal.importer.SpreadsheetReader.SheetData;
import com.popcornjournal.repository.GenreRepository;
import com.popcornjournal.repository.ImportSourceRowRepository;
import com.popcornjournal.repository.MovieRepository;
import com.popcornjournal.repository.PlatformRepository;
import com.popcornjournal.repository.VenueRepository;
import com.popcornjournal.repository.WatchlistItemRepository;
import com.popcornjournal.support.Titles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Works out what an import would do: which rows are new, which repeat an earlier row,
 * which were imported before, and which viewings each row produces. Reads the database
 * but never writes to it.
 */
@Component
class ImportPlanner {

    static final String WATCHED_PREFIX = "watched|";
    static final String WATCHLIST_PREFIX = "watchlist|";

    private final SpreadsheetReader reader;
    private final RowInterpreter interpreter;
    private final MovieRepository movies;
    private final WatchlistItemRepository watchlist;
    private final ImportSourceRowRepository sourceRows;
    private final GenreRepository genres;
    private final PlatformRepository platforms;
    private final VenueRepository venues;
    private final Set<String> nonStreamingKeys;

    ImportPlanner(SpreadsheetReader reader, RowInterpreter interpreter, MovieRepository movies,
                  WatchlistItemRepository watchlist, ImportSourceRowRepository sourceRows,
                  GenreRepository genres, PlatformRepository platforms, VenueRepository venues,
                  ImportProperties properties) {
        this.reader = reader;
        this.interpreter = interpreter;
        this.movies = movies;
        this.watchlist = watchlist;
        this.sourceRows = sourceRows;
        this.genres = genres;
        this.platforms = platforms;
        this.venues = venues;
        this.nonStreamingKeys = properties.nonStreamingPlatforms().stream().map(Titles::key)
                .collect(Collectors.toSet());
    }

    ImportPlan plan(byte[] content) {
        ImportPlan plan = new ImportPlan();
        plan.platforms = new NameBook("platform", platforms.findAll().stream().map(Platform::getName).toList());
        plan.venues = new NameBook("venue", venues.findAll().stream().map(Venue::getName).toList());
        plan.genres = new NameBook("genre", genres.findAll().stream().map(Genre::getName).toList());

        for (SheetData sheet : reader.read(content)) {
            classify(sheet, plan);
        }
        if (!plan.hasWatchedSheet()) {
            plan.fileMessages.add(Message.error(null,
                    "No sheet has a 'Movie / Series Name' (or 'Title') column, so there is nothing to import"));
        }
        planWatchedRows(plan);
        planWatchlistRows(plan);
        return plan;
    }

    boolean isNonStreaming(String platformKey) {
        return nonStreamingKeys.contains(platformKey);
    }

    // ---- sheets

    private void classify(SheetData sheet, ImportPlan plan) {
        ColumnMapping.Resolved columns = ColumnMapping.Resolved.of(sheet.headers());
        String nameKey = Titles.key(sheet.name());
        boolean looksLikeWatchlist = columns.has(ColumnMapping.RECOMMENDED_BY)
                || nameKey.contains("recommend") || nameKey.contains("watchlist");
        boolean looksWatched = columns.has(ColumnMapping.TITLE) && !looksLikeWatchlist
                && (columns.has(ColumnMapping.RATING) || columns.has(ColumnMapping.REVIEW)
                || columns.has(ColumnMapping.WATCH_DATE) || columns.has(ColumnMapping.PLATFORM));

        SheetRole role = SheetRole.IGNORED;
        if (looksWatched && plan.watchedSheetName == null) {
            role = SheetRole.WATCHED;
            plan.watchedSheetName = sheet.name();
            sheet.rows().forEach(r -> plan.movieRows.add(new PlannedMovieRow(interpreter.watched(r, columns))));
            List<String> options = sheet.dropdownOptions().getOrDefault(columns.headerFor().get(ColumnMapping.GENRE),
                    List.of());
            options.forEach(option -> plan.genreOptionKeys.add(plan.genres.see(option)));
        } else if (looksLikeWatchlist && columns.has(ColumnMapping.TITLE) && plan.watchlistSheetName == null) {
            role = SheetRole.WATCHLIST;
            plan.watchlistSheetName = sheet.name();
            sheet.rows().forEach(r -> plan.watchlistRows.add(new PlannedWatchlistRow(interpreter.watchlist(r, columns))));
        } else if (!sheet.rows().isEmpty()) {
            plan.fileMessages.add(Message.warning(null, "Sheet '" + sheet.name()
                    + "' was not recognised and is not imported"));
        }

        List<String> mapped = columns.headerFor().entrySet().stream()
                .map(e -> e.getValue() + " -> " + e.getKey().name().toLowerCase())
                .toList();
        List<String> unmapped = new ArrayList<>(columns.unmapped());
        if (role != SheetRole.IGNORED && !unmapped.isEmpty()) {
            plan.fileMessages.add(Message.warning(null, "Sheet '" + sheet.name() + "' has columns with no field: "
                    + String.join(", ", unmapped) + ". Their values are kept in the stored source rows."));
        }
        plan.sheets.add(new SheetSummary(sheet.name(), role, sheet.rows().size(), sheet.blankRows(),
                mapped, unmapped));
    }

    // ---- watched rows

    private void planWatchedRows(ImportPlan plan) {
        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, PlannedMovieRow> primaries = new LinkedHashMap<>();
        Map<String, List<PlannedMovieRow>> byTitleKey = new LinkedHashMap<>();

        for (PlannedMovieRow planned : plan.movieRows) {
            WatchedRow row = planned.row;
            planned.genreKey = plan.genres.see(row.genre());
            plan.platforms.see(row.platform());
            plan.venues.see(row.venue());
            if (row.hasErrors()) {
                planned.status = RowStatus.ERROR;
                continue;
            }
            String identity = row.identityKey();
            int occurrence = occurrences.merge(identity, 1, Integer::sum);
            planned.recordKey = WATCHED_PREFIX + identity + (occurrence > 1 ? "#" + occurrence : "");
            if (occurrence == 1) {
                primaries.put(identity, planned);
            } else {
                planned.primary = primaries.get(identity);
            }
            byTitleKey.computeIfAbsent(Titles.key(row.title()), k -> new ArrayList<>()).add(planned);
        }
        flagSimilarTitles(byTitleKey);

        List<String> recordKeys = plan.movieRows.stream().map(p -> p.recordKey).filter(Objects::nonNull).toList();
        Map<String, ImportSourceRow> seenBefore = index(sourceRows.findByRecordKeyIn(recordKeys),
                ImportSourceRow::getRecordKey);
        Map<String, Movie> importedMovies = index(movies.findByImportKeyIn(recordKeys), Movie::getImportKey);
        Map<String, List<Movie>> moviesByTitleKey = movies.findByTitleKeyIn(byTitleKey.keySet()).stream()
                .collect(Collectors.groupingBy(Movie::getTitleKey));

        for (PlannedMovieRow planned : plan.movieRows) {
            if (planned.status == RowStatus.ERROR) {
                continue;
            }
            planned.existingSourceRow = seenBefore.get(planned.recordKey);
            if (planned.primary == null) {
                decidePrimary(planned, importedMovies, moviesByTitleKey);
            } else {
                decideRepeat(planned);
            }
            if (planned.status == RowStatus.NEW || planned.status == RowStatus.NEW_VIEWING) {
                planViewings(planned);
            }
        }
    }

    private void decidePrimary(PlannedMovieRow planned, Map<String, Movie> importedMovies,
                               Map<String, List<Movie>> moviesByTitleKey) {
        Movie imported = importedMovies.get(planned.recordKey);
        if (imported != null) {
            planned.status = RowStatus.ALREADY_IMPORTED;
            planned.existingMovie = imported;
            reportDifferences(planned, imported);
            return;
        }
        if (planned.existingSourceRow != null) {
            planned.status = RowStatus.DELETED_IN_APP;
            planned.messages.add(Message.info(null,
                    "Imported before and deleted in the app since; not re-created"));
            return;
        }
        WatchedRow row = planned.row;
        Movie clash = moviesByTitleKey.getOrDefault(Titles.key(row.title()), List.of()).stream()
                .filter(m -> m.getFormat() == row.format()
                        && Objects.equals(m.getReleaseYear(), row.releaseYear())
                        && Objects.equals(lower(m.getLanguage()), lower(row.language())))
                .findFirst().orElse(null);
        if (clash != null) {
            planned.status = RowStatus.CONFLICTS_WITH_EXISTING;
            planned.existingMovie = clash;
            planned.messages.add(Message.warning(null, "A movie with the same title, year and language was added "
                    + "in the app (id " + clash.getId() + "); row skipped so it isn't duplicated"));
            return;
        }
        planned.status = RowStatus.NEW;
    }

    private void decideRepeat(PlannedMovieRow planned) {
        PlannedMovieRow primary = planned.primary;
        String sameAs = "Same movie as row " + primary.row.rowNumber();
        if (planned.existingSourceRow != null) {
            planned.status = planned.existingSourceRow.getMovie() == null
                    ? RowStatus.DELETED_IN_APP : RowStatus.ALREADY_IMPORTED;
            planned.existingMovie = planned.existingSourceRow.getMovie();
            return;
        }
        switch (primary.status) {
            case NEW, ALREADY_IMPORTED -> {
                planned.status = RowStatus.NEW_VIEWING;
                planned.existingMovie = primary.existingMovie;
                planned.messages.add(Message.warning(null, sameAs
                        + "; added as another viewing of it. Movie details come from row "
                        + primary.row.rowNumber() + "."));
                if (!Objects.equals(primary.row.rating(), planned.row.rating())
                        || primary.row.ratingBeyondScale() != planned.row.ratingBeyondScale()
                        || !Objects.equals(primary.row.review(), planned.row.review())) {
                    planned.messages.add(Message.info("Rating", "This row's rating and review differ from row "
                            + primary.row.rowNumber() + " and are kept on this viewing"));
                }
            }
            default -> {
                planned.status = primary.status;
                planned.messages.add(Message.info(null, sameAs + ", which was not imported either"));
            }
        }
    }

    private static void flagSimilarTitles(Map<String, List<PlannedMovieRow>> byTitleKey) {
        byTitleKey.values().forEach(rows -> {
            long identities = rows.stream().map(r -> r.row.identityKey()).distinct().count();
            if (identities < 2) {
                return;
            }
            for (PlannedMovieRow planned : rows) {
                String others = rows.stream().filter(o -> o != planned)
                        .filter(o -> !o.row.identityKey().equals(planned.row.identityKey()))
                        .map(o -> String.valueOf(o.row.rowNumber())).collect(Collectors.joining(", "));
                planned.messages.add(Message.warning("Title", "Same title as row(s) " + others
                        + " but a different year, format or language; kept as separate movies"));
            }
        });
    }

    /** Sheet values that no longer match the journal are reported, never written. */
    private static void reportDifferences(PlannedMovieRow planned, Movie movie) {
        WatchedRow row = planned.row;
        List<String> differs = new ArrayList<>();
        if (!Objects.equals(row.rating(), movie.getRating()) || row.ratingBeyondScale() != movie.isRatingBeyondScale()) {
            differs.add("rating");
        }
        if (!Objects.equals(row.review(), movie.getReview())) {
            differs.add("review");
        }
        if (!Objects.equals(row.synopsis(), movie.getSynopsis())) {
            differs.add("description");
        }
        if (!Objects.equals(Titles.clean(row.title()), movie.getTitle())) {
            differs.add("title");
        }
        if (!differs.isEmpty()) {
            planned.messages.add(Message.info(null, "Sheet differs from the journal in: " + String.join(", ", differs)
                    + ". The journal was not changed."));
        }
    }

    /**
     * A theatre means a cinema viewing. A platform means a streaming (or other) viewing;
     * when a row has both, the platform viewing gets no date because the date belongs to
     * the cinema visit. A row with neither but a date still gets a viewing.
     */
    private void planViewings(PlannedMovieRow planned) {
        WatchedRow row = planned.row;
        String venueKey = NameBook.keyOf(row.venue());
        String platformKey = NameBook.keyOf(row.platform());
        boolean repeat = planned.status == RowStatus.NEW_VIEWING;
        Integer rating = repeat ? row.rating() : null;
        boolean beyond = repeat && row.ratingBeyondScale();
        String notes = repeat ? row.review() : null;

        if (venueKey != null) {
            planned.viewings.add(new PlannedViewing(planned.recordKey + ":cinema", ViewingMethod.CINEMA,
                    null, venueKey, row.watchedOn(), rating, beyond, notes));
        }
        if (platformKey != null) {
            ViewingMethod method = isNonStreaming(platformKey) ? ViewingMethod.OTHER : ViewingMethod.STREAMING;
            boolean first = planned.viewings.isEmpty();
            planned.viewings.add(new PlannedViewing(planned.recordKey + ":platform", method, platformKey, null,
                    first ? row.watchedOn() : null, first ? rating : null, first && beyond, first ? notes : null));
            if (!first) {
                planned.messages.add(Message.info("Platform", "Theatre and platform both recorded: two viewings, "
                        + "the platform one without a date"));
            }
        }
        if (planned.viewings.isEmpty()) {
            if (row.watchedOn() != null) {
                planned.viewings.add(new PlannedViewing(planned.recordKey + ":viewing", null, null, null,
                        row.watchedOn(), rating, beyond, notes));
                planned.messages.add(Message.info(null, "Watch date but no platform or theatre: "
                        + "viewing recorded without a method"));
            } else {
                planned.messages.add(Message.info(null, "No date, platform or theatre: "
                        + "movie imported without a viewing session"));
            }
        }
    }

    // ---- watchlist rows

    private void planWatchlistRows(ImportPlan plan) {
        Map<String, Integer> occurrences = new HashMap<>();
        for (PlannedWatchlistRow planned : plan.watchlistRows) {
            WatchlistRow row = planned.row;
            planned.genreKey = plan.genres.see(row.genre());
            if (row.hasErrors()) {
                planned.status = RowStatus.ERROR;
                continue;
            }
            String key = Titles.key(row.title());
            int occurrence = occurrences.merge(key, 1, Integer::sum);
            planned.recordKey = WATCHLIST_PREFIX + key + (occurrence > 1 ? "#" + occurrence : "");
        }
        List<String> keys = plan.watchlistRows.stream().map(p -> p.recordKey).filter(Objects::nonNull).toList();
        Map<String, ImportSourceRow> seenBefore = index(sourceRows.findByRecordKeyIn(keys), ImportSourceRow::getRecordKey);
        Set<String> importedItems = watchlist.findByImportKeyIn(keys).stream().map(WatchlistItem::getImportKey)
                .collect(Collectors.toSet());
        Set<String> watchedTitleKeys = plan.movieRows.stream().filter(p -> p.row.title() != null)
                .map(p -> Titles.key(p.row.title())).collect(Collectors.toSet());

        for (PlannedWatchlistRow planned : plan.watchlistRows) {
            if (planned.status == RowStatus.ERROR) {
                continue;
            }
            planned.existingSourceRow = seenBefore.get(planned.recordKey);
            if (importedItems.contains(planned.recordKey)) {
                planned.status = RowStatus.ALREADY_IMPORTED;
            } else if (planned.existingSourceRow != null) {
                planned.status = RowStatus.DELETED_IN_APP;
                planned.messages.add(Message.info(null, "Imported before and deleted in the app since; not re-created"));
            } else {
                planned.status = RowStatus.NEW;
            }
            if (watchedTitleKeys.contains(Titles.key(planned.row.title()))) {
                planned.messages.add(Message.info("Title", "Also appears on the watched sheet"));
            }
        }
    }

    private static <T> Map<String, T> index(List<T> items, Function<T, String> key) {
        Map<String, T> map = new HashMap<>();
        items.forEach(item -> map.put(key.apply(item), item));
        return map;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }
}
