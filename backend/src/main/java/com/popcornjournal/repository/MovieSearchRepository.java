package com.popcornjournal.repository;

import com.popcornjournal.dto.MovieSearchCriteria;
import com.popcornjournal.dto.MovieSearchCriteria.PersonalList;
import com.popcornjournal.dto.MovieSummaryDto;
import com.popcornjournal.dto.PageResponse;
import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.support.Titles;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Library search in plain SQL. Sorting by "last watched" needs an aggregate over
 * viewing sessions, which is far simpler to express here than with JPA criteria.
 * One query for the page, one for the count, one for the page's genres.
 */
@Repository
public class MovieSearchRepository {

    private static final String EFFECTIVE_RATING =
            "(CASE WHEN m.rating_beyond_scale THEN 11 ELSE m.rating END)";

    private final NamedParameterJdbcTemplate jdbc;

    public MovieSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<MovieSummaryDto> search(MovieSearchCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildWhere(criteria, params);

        Long total = jdbc.queryForObject("SELECT count(*) FROM movies m WHERE " + where, params, Long.class);
        int page = criteria.pageOrDefault();
        int size = criteria.sizeOrDefault();
        if (total == null || total == 0) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        params.addValue("limit", size);
        params.addValue("offset", (long) page * size);
        String sql = """
                SELECT m.id, m.title, m.format, m.release_year, m.language, m.rating, m.rating_beyond_scale,
                       m.favourite, %s AS poster_url,
                       (m.review IS NOT NULL AND btrim(m.review) <> '') AS has_review,
                       COALESCE(vs.viewing_count, 0) AS viewing_count,
                       vs.last_watched_on, vs.first_watched_on, vs.platforms, vs.venues
                FROM movies m
                LEFT JOIN LATERAL (
                    SELECT count(*) AS viewing_count,
                           max(s.watched_on) AS last_watched_on,
                           min(s.watched_on) AS first_watched_on,
                           array_remove(array_agg(DISTINCT p.name), NULL) AS platforms,
                           array_remove(array_agg(DISTINCT v.name), NULL) AS venues
                    FROM viewing_sessions s
                    LEFT JOIN platforms p ON p.id = s.platform_id
                    LEFT JOIN venues v ON v.id = s.venue_id
                    WHERE s.movie_id = m.id
                ) vs ON TRUE
                WHERE %s
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(MoviePosterRepository.EFFECTIVE_URL_SQL, where, orderBy(criteria.sortOrDefault()));

        List<Row> rows = jdbc.query(sql, params, (rs, i) -> mapRow(rs));
        Map<Long, List<String>> genres = genresFor(rows.stream().map(Row::id).toList());
        List<MovieSummaryDto> content = rows.stream()
                .map(row -> row.toDto(genres.getOrDefault(row.id(), List.of())))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    private String buildWhere(MovieSearchCriteria c, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        clauses.add("TRUE");

        if (c.q() != null && !c.q().isBlank()) {
            String needle = c.q().strip().toLowerCase(Locale.ROOT);
            params.addValue("q", "%" + escapeLike(needle) + "%");
            String key = Titles.key(needle);
            if (key.isEmpty()) {
                clauses.add("(lower(m.title) LIKE :q OR lower(m.original_title) LIKE :q)");
            } else {
                // the key lets "friends" find "F.R.I.E.N.D.S"
                params.addValue("qKey", "%" + escapeLike(key) + "%");
                clauses.add("(lower(m.title) LIKE :q OR lower(m.original_title) LIKE :q OR m.title_key LIKE :qKey)");
            }
        }
        if (hasText(c.language())) {
            params.addValue("language", c.language().strip());
            clauses.add("lower(m.language) = lower(:language)");
        }
        if (c.genreId() != null) {
            params.addValue("genreId", c.genreId());
            clauses.add("EXISTS (SELECT 1 FROM movie_genres mg WHERE mg.movie_id = m.id AND mg.genre_id = :genreId)");
        }
        if (hasText(c.category())) {
            params.addValue("category", c.category().strip());
            clauses.add("""
                    EXISTS (SELECT 1 FROM movie_genres mg JOIN genres g ON g.id = mg.genre_id
                            WHERE mg.movie_id = m.id AND lower(g.category) = lower(:category))""");
        }
        if (c.format() != null) {
            params.addValue("format", c.format().name());
            clauses.add("m.format = :format");
        }
        if (c.favourite() != null) {
            params.addValue("favourite", c.favourite());
            clauses.add("m.favourite = :favourite");
        }

        List<String> sessionClauses = new ArrayList<>();
        if (c.platformId() != null) {
            params.addValue("platformId", c.platformId());
            sessionClauses.add("s.platform_id = :platformId");
        }
        if (c.venueId() != null) {
            params.addValue("venueId", c.venueId());
            sessionClauses.add("s.venue_id = :venueId");
        }
        if (c.method() != null) {
            params.addValue("method", c.method().name());
            sessionClauses.add("s.method = :method");
        }
        LocalDate from = c.watchedFrom();
        LocalDate to = c.watchedTo();
        if (c.watchedYear() != null) {
            LocalDate yearStart = LocalDate.of(c.watchedYear(), 1, 1);
            LocalDate yearEnd = LocalDate.of(c.watchedYear(), 12, 31);
            from = from == null || from.isBefore(yearStart) ? yearStart : from;
            to = to == null || to.isAfter(yearEnd) ? yearEnd : to;
        }
        if (from != null) {
            params.addValue("watchedFrom", Date.valueOf(from));
            sessionClauses.add("s.watched_on >= :watchedFrom");
        }
        if (to != null) {
            params.addValue("watchedTo", Date.valueOf(to));
            sessionClauses.add("s.watched_on <= :watchedTo");
        }
        if (!sessionClauses.isEmpty()) {
            // all session conditions must hold for the same viewing
            clauses.add("EXISTS (SELECT 1 FROM viewing_sessions s WHERE s.movie_id = m.id AND "
                    + String.join(" AND ", sessionClauses) + ")");
        }

        if (c.minRating() != null) {
            params.addValue("minRating", c.minRating());
            clauses.add(EFFECTIVE_RATING + " >= :minRating");
        }
        if (c.maxRating() != null) {
            params.addValue("maxRating", c.maxRating());
            clauses.add(EFFECTIVE_RATING + " <= :maxRating");
        }
        if (c.ratingTier() != null) {
            clauses.add(switch (c.ratingTier()) {
                case RATED -> "m.rating IS NOT NULL";
                case UNRATED -> "(m.rating IS NULL AND NOT m.rating_beyond_scale)";
                case BEYOND_SCALE -> "m.rating_beyond_scale";
            });
        }
        if (c.list() != null) {
            clauses.add(listClause(c.list()));
        }
        return String.join(" AND ", clauses);
    }

    private static String listClause(PersonalList list) {
        return switch (list) {
            case FAVOURITES -> "m.favourite";
            case TOP_RATED -> "(m.rating_beyond_scale OR m.rating >= 9)";
            case RECENTLY_WATCHED ->
                    "EXISTS (SELECT 1 FROM viewing_sessions s WHERE s.movie_id = m.id AND s.watched_on IS NOT NULL)";
            case UNRATED -> "(m.rating IS NULL AND NOT m.rating_beyond_scale)";
            case NO_REVIEW -> "(m.review IS NULL OR btrim(m.review) = '')";
            case MISSING_METADATA -> """
                    (m.release_year IS NULL OR m.language IS NULL
                     OR m.synopsis IS NULL OR btrim(m.synopsis) = ''
                     OR NOT EXISTS (SELECT 1 FROM movie_genres mg WHERE mg.movie_id = m.id))""";
        };
    }

    private static String orderBy(MovieSearchCriteria.MovieSort sort) {
        return switch (sort) {
            case TITLE -> "lower(m.title) ASC, m.id ASC";
            // Titles without a dated viewing come after dated ones. Among them, ones added in the
            // app come first (newest first), then imported ones in spreadsheet order, since the
            // sheet was kept newest-first. Ordering only: no date is inferred or shown.
            case LATEST_WATCHED -> """
                    vs.last_watched_on DESC NULLS LAST,
                    (SELECT min(r.row_number) FROM import_source_rows r WHERE r.movie_id = m.id) ASC NULLS FIRST,
                    m.created_at DESC, m.id DESC""";
            case EARLIEST_WATCHED -> "vs.first_watched_on ASC NULLS LAST, lower(m.title) ASC, m.id ASC";
            case HIGHEST_RATED -> EFFECTIVE_RATING + " DESC NULLS LAST, lower(m.title) ASC, m.id ASC";
            case LOWEST_RATED -> EFFECTIVE_RATING + " ASC NULLS LAST, lower(m.title) ASC, m.id ASC";
            case RECENTLY_ADDED -> "m.created_at DESC, m.id DESC";
            case RELEASE_YEAR -> "m.release_year DESC NULLS LAST, lower(m.title) ASC, m.id ASC";
        };
    }

    private Map<Long, List<String>> genresFor(Collection<Long> movieIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (movieIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                        SELECT mg.movie_id, g.name FROM movie_genres mg
                        JOIN genres g ON g.id = mg.genre_id
                        WHERE mg.movie_id IN (:ids)
                        ORDER BY g.name
                        """,
                new MapSqlParameterSource("ids", movieIds),
                rs -> {
                    result.computeIfAbsent(rs.getLong("movie_id"), id -> new ArrayList<>()).add(rs.getString("name"));
                });
        return result;
    }

    private static Row mapRow(ResultSet rs) throws SQLException {
        Date last = rs.getDate("last_watched_on");
        return new Row(
                rs.getLong("id"),
                rs.getString("title"),
                MovieFormat.valueOf(rs.getString("format")),
                rs.getObject("release_year", Integer.class),
                rs.getString("language"),
                rs.getObject("rating", Integer.class),
                rs.getBoolean("rating_beyond_scale"),
                rs.getBoolean("favourite"),
                rs.getBoolean("has_review"),
                rs.getString("poster_url"),
                rs.getInt("viewing_count"),
                last == null ? null : last.toLocalDate(),
                textArray(rs.getArray("platforms")),
                textArray(rs.getArray("venues")));
    }

    private static List<String> textArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        String[] values = (String[]) array.getArray();
        return Arrays.stream(values).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Row(Long id, String title, MovieFormat format, Integer releaseYear, String language,
                       Integer rating, boolean ratingBeyondScale, boolean favourite, boolean hasReview,
                       String posterUrl, int viewingCount, LocalDate lastWatchedOn,
                       List<String> platforms, List<String> venues) {

        MovieSummaryDto toDto(List<String> genres) {
            return new MovieSummaryDto(id, title, format, releaseYear, language, genres, rating,
                    ratingBeyondScale, favourite, hasReview, posterUrl, viewingCount, lastWatchedOn,
                    platforms, venues);
        }
    }
}
