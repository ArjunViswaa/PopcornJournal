package com.popcornjournal.repository;

import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.poster.PosterQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stored posters and lookup bookkeeping, in plain SQL so the image bytes are only read
 * when the image itself is requested, never alongside movie queries.
 */
@Repository
public class MoviePosterRepository {

    public enum LookupStatus { FOUND, NOT_FOUND, FAILED, REJECTED }

    public record PosterInfo(String url, String sourceName, String sourcePageUrl, String matchedTitle,
                             Instant fetchedAt) {
    }

    public record StoredImage(byte[] bytes, String contentType) {
    }

    public record Counts(long totalMovies, long ownPoster, long fetchedPoster, long pending, long notFound,
                         long rejected) {
    }

    /** Effective poster for list queries: the user's URL first, then a stored poster. */
    public static final String EFFECTIVE_URL_SQL = """
            COALESCE(m.poster_url,
                     (SELECT '/api/v1/movies/' || mp.movie_id || '/poster?v='
                             || floor(extract(epoch FROM mp.fetched_at))::bigint
                      FROM movie_posters mp WHERE mp.movie_id = m.id))""";

    private final NamedParameterJdbcTemplate jdbc;

    public MoviePosterRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PosterInfo> findInfo(long movieId) {
        return jdbc.query("""
                        SELECT movie_id, source_name, source_page_url, matched_title, fetched_at
                        FROM movie_posters WHERE movie_id = :id""",
                Map.of("id", movieId),
                (rs, i) -> {
                    Instant fetchedAt = rs.getTimestamp("fetched_at").toInstant();
                    return new PosterInfo(posterPath(movieId, fetchedAt), rs.getString("source_name"),
                            rs.getString("source_page_url"), rs.getString("matched_title"), fetchedAt);
                }).stream().findFirst();
    }

    public Optional<StoredImage> findImage(long movieId) {
        return jdbc.query("SELECT image, content_type FROM movie_posters WHERE movie_id = :id",
                Map.of("id", movieId),
                (rs, i) -> new StoredImage(rs.getBytes("image"), rs.getString("content_type"))).stream().findFirst();
    }

    public void save(long movieId, byte[] image, String contentType, String sourceName, String pageUrl,
                     String imageUrl, String matchedTitle) {
        jdbc.update("""
                        INSERT INTO movie_posters (movie_id, image, content_type, source_name, source_page_url,
                                                   source_image_url, matched_title, fetched_at)
                        VALUES (:id, :image, :type, :source, :page, :imageUrl, :matched, now())
                        ON CONFLICT (movie_id) DO UPDATE SET
                            image = EXCLUDED.image, content_type = EXCLUDED.content_type,
                            source_name = EXCLUDED.source_name, source_page_url = EXCLUDED.source_page_url,
                            source_image_url = EXCLUDED.source_image_url, matched_title = EXCLUDED.matched_title,
                            fetched_at = now()""",
                new MapSqlParameterSource()
                        .addValue("id", movieId).addValue("image", image).addValue("type", contentType)
                        .addValue("source", sourceName).addValue("page", pageUrl)
                        .addValue("imageUrl", imageUrl).addValue("matched", truncate(matchedTitle, 300)));
    }

    public boolean delete(long movieId) {
        return jdbc.update("DELETE FROM movie_posters WHERE movie_id = :id", Map.of("id", movieId)) > 0;
    }

    /** Writes the lookup outcome directly, without touching updated_at: this isn't a user edit. */
    public void markLookup(long movieId, LookupStatus status) {
        jdbc.update("UPDATE movies SET poster_lookup_status = :status, poster_lookup_at = now() WHERE id = :id",
                Map.of("status", status.name(), "id", movieId));
    }

    public Optional<String> lookupStatus(long movieId) {
        return jdbc.queryForList("SELECT poster_lookup_status FROM movies WHERE id = :id",
                Map.of("id", movieId), String.class).stream().findFirst();
    }

    /**
     * Movies a bulk run should look up: no poster of either kind, never looked up, or a
     * failed/not-found lookup older than this run (so one run never repeats a movie).
     * Rejected posters are never fetched again automatically.
     */
    public List<PosterQuery> findPending(Instant runStartedAt, boolean retryNotFound, int limit) {
        return jdbc.query("""
                        SELECT m.id, m.title, m.format, m.release_year, m.language FROM movies m
                        WHERE m.poster_url IS NULL
                          AND NOT EXISTS (SELECT 1 FROM movie_posters mp WHERE mp.movie_id = m.id)
                          AND (m.poster_lookup_status IS NULL
                               OR (m.poster_lookup_at < :runStartedAt
                                   AND (m.poster_lookup_status = 'FAILED'
                                        OR (:retryNotFound AND m.poster_lookup_status = 'NOT_FOUND'))))
                        ORDER BY m.id
                        LIMIT :limit""",
                new MapSqlParameterSource()
                        .addValue("runStartedAt", Timestamp.from(runStartedAt))
                        .addValue("retryNotFound", retryNotFound)
                        .addValue("limit", limit),
                (rs, i) -> new PosterQuery(rs.getLong("id"), rs.getString("title"),
                        MovieFormat.valueOf(rs.getString("format")), rs.getObject("release_year", Integer.class),
                        rs.getString("language")));
    }

    public long countPending(Instant runStartedAt, boolean retryNotFound) {
        Long count = jdbc.queryForObject("""
                        SELECT count(*) FROM movies m
                        WHERE m.poster_url IS NULL
                          AND NOT EXISTS (SELECT 1 FROM movie_posters mp WHERE mp.movie_id = m.id)
                          AND (m.poster_lookup_status IS NULL
                               OR (m.poster_lookup_at < :runStartedAt
                                   AND (m.poster_lookup_status = 'FAILED'
                                        OR (:retryNotFound AND m.poster_lookup_status = 'NOT_FOUND'))))""",
                new MapSqlParameterSource()
                        .addValue("runStartedAt", Timestamp.from(runStartedAt))
                        .addValue("retryNotFound", retryNotFound),
                Long.class);
        return count == null ? 0 : count;
    }

    public Counts counts() {
        return jdbc.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE m.poster_url IS NOT NULL) AS own,
                       count(*) FILTER (WHERE m.poster_url IS NULL AND mp.movie_id IS NOT NULL) AS fetched,
                       count(*) FILTER (WHERE m.poster_url IS NULL AND mp.movie_id IS NULL
                                        AND (m.poster_lookup_status IS NULL OR m.poster_lookup_status = 'FAILED'))
                           AS pending,
                       count(*) FILTER (WHERE m.poster_url IS NULL AND mp.movie_id IS NULL
                                        AND m.poster_lookup_status = 'NOT_FOUND') AS not_found,
                       count(*) FILTER (WHERE m.poster_url IS NULL AND mp.movie_id IS NULL
                                        AND m.poster_lookup_status = 'REJECTED') AS rejected
                FROM movies m LEFT JOIN movie_posters mp ON mp.movie_id = m.id""",
                Map.of(),
                (rs, i) -> new Counts(rs.getLong("total"), rs.getLong("own"), rs.getLong("fetched"),
                        rs.getLong("pending"), rs.getLong("not_found"), rs.getLong("rejected")));
    }

    static String posterPath(long movieId, Instant fetchedAt) {
        return "/api/v1/movies/" + movieId + "/poster?v=" + fetchedAt.getEpochSecond();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
