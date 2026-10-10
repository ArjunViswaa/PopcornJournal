package com.popcornjournal.importer;

import com.popcornjournal.entity.Genre;
import com.popcornjournal.entity.ImportRun;
import com.popcornjournal.entity.ImportSourceRow;
import com.popcornjournal.entity.Movie;
import com.popcornjournal.entity.Platform;
import com.popcornjournal.entity.Venue;
import com.popcornjournal.entity.ViewingMethod;
import com.popcornjournal.entity.ViewingSession;
import com.popcornjournal.entity.WatchlistItem;
import com.popcornjournal.exception.InvalidRequestException;
import com.popcornjournal.importer.ImportPlan.PlannedMovieRow;
import com.popcornjournal.importer.ImportPlan.PlannedViewing;
import com.popcornjournal.importer.ImportPlan.PlannedWatchlistRow;
import com.popcornjournal.importer.ImportReport.Level;
import com.popcornjournal.importer.ImportReport.RowOutcome;
import com.popcornjournal.importer.ImportReport.RowStatus;
import com.popcornjournal.importer.ImportReport.SpellingMerge;
import com.popcornjournal.importer.ImportReport.Totals;
import com.popcornjournal.importer.RowInterpreter.WatchedRow;
import com.popcornjournal.repository.GenreRepository;
import com.popcornjournal.repository.ImportRunRepository;
import com.popcornjournal.repository.ImportSourceRowRepository;
import com.popcornjournal.repository.MovieRepository;
import com.popcornjournal.repository.PlatformRepository;
import com.popcornjournal.repository.VenueRepository;
import com.popcornjournal.repository.ViewingSessionRepository;
import com.popcornjournal.repository.WatchlistItemRepository;
import com.popcornjournal.support.Titles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Imports the historical spreadsheet. A preview runs the full plan in a read-only
 * transaction; a commit re-plans and writes everything in one transaction, so a failure
 * part-way leaves the database exactly as it was and the import can simply be re-run.
 */
@Service
public class SpreadsheetImportService {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetImportService.class);

    /** Serialises concurrent commits (e.g. CLI and web at the same time). */
    private static final long IMPORT_LOCK_ID = 7_310_001L;

    private final ImportPlanner planner;
    private final MovieRepository movies;
    private final ViewingSessionRepository sessions;
    private final WatchlistItemRepository watchlist;
    private final ImportRunRepository runs;
    private final ImportSourceRowRepository sourceRows;
    private final GenreRepository genres;
    private final PlatformRepository platforms;
    private final VenueRepository venues;
    private final JdbcTemplate jdbc;

    public SpreadsheetImportService(ImportPlanner planner, MovieRepository movies, ViewingSessionRepository sessions,
                                    WatchlistItemRepository watchlist, ImportRunRepository runs,
                                    ImportSourceRowRepository sourceRows, GenreRepository genres,
                                    PlatformRepository platforms, VenueRepository venues, JdbcTemplate jdbc) {
        this.planner = planner;
        this.movies = movies;
        this.sessions = sessions;
        this.watchlist = watchlist;
        this.runs = runs;
        this.sourceRows = sourceRows;
        this.genres = genres;
        this.platforms = platforms;
        this.venues = venues;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ImportReport preview(byte[] content, String fileName) {
        ImportPlan plan = planner.plan(content);
        return report(plan, true, safeName(fileName), sha256(content), null);
    }

    @Transactional
    public ImportReport commit(byte[] content, String fileName, String triggeredBy) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", IMPORT_LOCK_ID);
        ImportPlan plan = planner.plan(content);
        if (!plan.hasWatchedSheet()) {
            throw new InvalidRequestException("No sheet with a movie title column was found; nothing was imported");
        }
        String name = safeName(fileName);
        String hash = sha256(content);
        ImportRun run = runs.save(new ImportRun(name, hash, triggeredBy, Instant.now()));

        apply(plan, run);

        ImportReport report = report(plan, false, name, hash, run.getId());
        run.finish(summaryFor(report));
        runs.save(run);
        log.info("Import run {} committed: {}", run.getId(), report.totals());
        return report;
    }

    // ---- writing

    private void apply(ImportPlan plan, ImportRun run) {
        Map<String, Genre> genreByKey = ensureGenres(plan);
        Map<String, Platform> platformByKey = new HashMap<>();
        platforms.findAll().forEach(p -> platformByKey.putIfAbsent(Titles.key(p.getName()), p));
        Map<String, Venue> venueByKey = new HashMap<>();
        venues.findAll().forEach(v -> venueByKey.putIfAbsent(Titles.key(v.getName()), v));

        Map<String, Movie> createdByIdentity = new HashMap<>();
        for (PlannedMovieRow planned : plan.movieRows) {
            switch (planned.status) {
                case NEW -> {
                    Movie movie = movies.save(newMovie(planned, genreByKey));
                    createdByIdentity.put(planned.row.identityKey(), movie);
                    addViewings(planned, movie, plan, platformByKey, venueByKey);
                    recordSource(plan.watchedSheetName, planned.recordKey, planned.row.rowNumber(),
                            planned.row.raw(), run).setMovie(movie);
                }
                case NEW_VIEWING -> {
                    Movie movie = planned.existingMovie != null
                            ? planned.existingMovie
                            : createdByIdentity.get(planned.primary.row.identityKey());
                    addViewings(planned, movie, plan, platformByKey, venueByKey);
                    recordSource(plan.watchedSheetName, planned.recordKey, planned.row.rowNumber(),
                            planned.row.raw(), run).setMovie(movie);
                }
                case ALREADY_IMPORTED -> {
                    if (planned.existingSourceRow != null) {
                        planned.existingSourceRow.seenAgain(planned.row.rowNumber(), run);
                    } else {
                        recordSource(plan.watchedSheetName, planned.recordKey, planned.row.rowNumber(),
                                planned.row.raw(), run).setMovie(planned.existingMovie);
                    }
                }
                case DELETED_IN_APP -> planned.existingSourceRow.seenAgain(planned.row.rowNumber(), run);
                case CONFLICTS_WITH_EXISTING, ERROR -> {
                    // reported only
                }
            }
        }

        for (PlannedWatchlistRow planned : plan.watchlistRows) {
            if (planned.status == RowStatus.NEW) {
                WatchlistItem item = new WatchlistItem();
                item.setTitle(planned.row.title());
                item.setRecommendedBy(planned.row.recommendedBy());
                item.setGenre(planned.genreKey == null ? null : genreByKey.get(planned.genreKey));
                item.setImportKey(planned.recordKey);
                item = watchlist.save(item);
                recordSource(plan.watchlistSheetName, planned.recordKey, planned.row.rowNumber(),
                        planned.row.raw(), run).setWatchlistItem(item);
            } else if (planned.existingSourceRow != null) {
                planned.existingSourceRow.seenAgain(planned.row.rowNumber(), run);
            }
        }
    }

    private Map<String, Genre> ensureGenres(ImportPlan plan) {
        Map<String, Genre> byKey = new HashMap<>();
        genres.findAll().forEach(g -> byKey.putIfAbsent(Titles.key(g.getName()), g));
        for (String key : genreKeysToCreate(plan)) {
            String name = plan.genres.storedName(key);
            byKey.put(key, genres.save(new Genre(name, Titles.genreCategory(name))));
        }
        return byKey;
    }

    private static Movie newMovie(PlannedMovieRow planned, Map<String, Genre> genreByKey) {
        WatchedRow row = planned.row;
        Movie movie = new Movie();
        movie.setTitle(row.title());
        movie.setFormat(row.format());
        movie.setReleaseYear(row.releaseYear());
        movie.setLanguage(row.language());
        movie.setSynopsis(row.synopsis());
        movie.rate(row.rating(), row.ratingBeyondScale());
        movie.setReview(row.review());
        movie.setImportKey(planned.recordKey);
        if (planned.genreKey != null) {
            movie.getGenres().add(genreByKey.get(planned.genreKey));
        }
        return movie;
    }

    private void addViewings(PlannedMovieRow planned, Movie movie, ImportPlan plan,
                             Map<String, Platform> platformByKey, Map<String, Venue> venueByKey) {
        for (PlannedViewing viewing : planned.viewings) {
            ViewingSession session = new ViewingSession(movie);
            session.setWatchedOn(viewing.watchedOn());
            session.setMethod(viewing.method());
            if (viewing.platformKey() != null) {
                session.setPlatform(platformByKey.computeIfAbsent(viewing.platformKey(), key -> platforms.save(
                        new Platform(plan.platforms.storedName(key),
                                planner.isNonStreaming(key) ? ViewingMethod.OTHER : ViewingMethod.STREAMING))));
            }
            if (viewing.venueKey() != null) {
                session.setVenue(venueByKey.computeIfAbsent(viewing.venueKey(),
                        key -> venues.save(new Venue(plan.venues.storedName(key)))));
            }
            session.rate(viewing.rating(), viewing.ratingBeyondScale());
            session.setNotes(viewing.notes());
            session.setImportKey(viewing.importKey());
            sessions.save(session);
        }
    }

    private ImportSourceRow recordSource(String sheet, String recordKey, int rowNumber,
                                         Map<String, Object> raw, ImportRun run) {
        return sourceRows.save(new ImportSourceRow(sheet, recordKey, rowNumber, raw, run));
    }

    // ---- reporting

    private ImportReport report(ImportPlan plan, boolean dryRun, String fileName, String hash, Long runId) {
        List<RowOutcome> rows = new ArrayList<>();
        int newMovies = 0, newViewings = 0, newWatchlist = 0, merged = 0, already = 0, deleted = 0,
                conflicts = 0, errors = 0, warnings = 0;
        List<String> usedPlatformKeys = new ArrayList<>();
        List<String> usedVenueKeys = new ArrayList<>();

        for (PlannedMovieRow planned : plan.movieRows) {
            rows.add(new RowOutcome(plan.watchedSheetName, planned.row.rowNumber(), planned.row.title(),
                    planned.status, planned.viewings.size(), planned.messages));
            newViewings += planned.viewings.size();
            planned.viewings.forEach(v -> {
                if (v.platformKey() != null) usedPlatformKeys.add(v.platformKey());
                if (v.venueKey() != null) usedVenueKeys.add(v.venueKey());
            });
            switch (planned.status) {
                case NEW -> newMovies++;
                case NEW_VIEWING -> merged++;
                case ALREADY_IMPORTED -> already++;
                case DELETED_IN_APP -> deleted++;
                case CONFLICTS_WITH_EXISTING -> conflicts++;
                case ERROR -> errors++;
            }
            warnings += (int) planned.messages.stream().filter(m -> m.level() == Level.WARNING).count();
        }
        for (PlannedWatchlistRow planned : plan.watchlistRows) {
            rows.add(new RowOutcome(plan.watchlistSheetName, planned.row.rowNumber(), planned.row.title(),
                    planned.status, 0, planned.messages));
            switch (planned.status) {
                case NEW -> newWatchlist++;
                case ALREADY_IMPORTED -> already++;
                case DELETED_IN_APP -> deleted++;
                case ERROR -> errors++;
                default -> {
                }
            }
            warnings += (int) planned.messages.stream().filter(m -> m.level() == Level.WARNING).count();
        }
        warnings += (int) plan.fileMessages.stream().filter(m -> m.level() == Level.WARNING).count();

        List<SpellingMerge> merges = Stream.of(plan.platforms.merges(), plan.venues.merges(), plan.genres.merges())
                .flatMap(List::stream).toList();
        Totals totals = new Totals(plan.movieRows.size() + plan.watchlistRows.size(), newMovies, newViewings,
                newWatchlist, merged, already, deleted, conflicts, errors, warnings);
        List<String> newGenres = genreKeysToCreate(plan).stream().map(plan.genres::storedName)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return new ImportReport(dryRun, fileName, hash, Instant.now(), runId, plan.sheets, totals, rows,
                newGenres, plan.platforms.newNames(usedPlatformKeys), plan.venues.newNames(usedVenueKeys),
                merges, plan.fileMessages);
    }

    /** Genres of new rows plus the sheet's dropdown options, minus ones that already exist. */
    private static List<String> genreKeysToCreate(ImportPlan plan) {
        Stream<String> fromRows = plan.movieRows.stream()
                .filter(p -> p.status == RowStatus.NEW).map(p -> p.genreKey);
        Stream<String> fromWatchlist = plan.watchlistRows.stream()
                .filter(p -> p.status == RowStatus.NEW).map(p -> p.genreKey);
        return Stream.of(fromRows, fromWatchlist, plan.genreOptionKeys.stream())
                .flatMap(s -> s)
                .filter(Objects::nonNull)
                .distinct()
                .filter(key -> !plan.genres.exists(key))
                .toList();
    }

    private static Map<String, Object> summaryFor(ImportReport report) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Totals t = report.totals();
        summary.put("rowsRead", t.rowsRead());
        summary.put("newMovies", t.newMovies());
        summary.put("newViewings", t.newViewings());
        summary.put("newWatchlistItems", t.newWatchlistItems());
        summary.put("mergedDuplicateRows", t.mergedDuplicateRows());
        summary.put("alreadyImported", t.alreadyImported());
        summary.put("deletedInApp", t.deletedInApp());
        summary.put("conflicts", t.conflicts());
        summary.put("errors", t.errors());
        summary.put("warnings", t.warnings());
        summary.put("newGenres", report.newGenres());
        summary.put("newPlatforms", report.newPlatforms());
        summary.put("newVenues", report.newVenues());
        return summary;
    }

    /** Only the base name is kept; a client-supplied path is never used for anything. */
    private static String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "upload.xlsx";
        }
        String base = fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^\\w .()-]", "_");
        return base.length() > 200 ? base.substring(0, 200) : base;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
