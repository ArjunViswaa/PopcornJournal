package com.popcornjournal.repository;

import com.popcornjournal.dto.StatsDtos.LabelAverage;
import com.popcornjournal.dto.StatsDtos.LabelCount;
import com.popcornjournal.dto.StatsDtos.PeriodCount;
import com.popcornjournal.dto.StatsDtos.RatingSummary;
import com.popcornjournal.dto.StatsDtos.RecentViewing;
import com.popcornjournal.dto.StatsDtos.TopMovie;
import com.popcornjournal.entity.ViewingMethod;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregations for the dashboard and statistics pages, computed in PostgreSQL. */
@Repository
public class StatsRepository {

    private static final RowMapper<LabelCount> LABEL_COUNT =
            (rs, i) -> new LabelCount(rs.getString("label"), rs.getLong("count"));

    private static final RowMapper<LabelAverage> LABEL_AVERAGE = (rs, i) -> new LabelAverage(
            rs.getString("label"),
            rs.getObject("average_rating") == null ? null : rs.getDouble("average_rating"),
            rs.getLong("rated_count"),
            rs.getLong("beyond_count"),
            rs.getLong("movie_count"));

    private final NamedParameterJdbcTemplate jdbc;

    public StatsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countMovies() {
        return count("SELECT count(*) FROM movies", Map.of());
    }

    public long countViewings() {
        return count("SELECT count(*) FROM viewing_sessions", Map.of());
    }

    public long countDatedViewings() {
        return count("SELECT count(*) FROM viewing_sessions WHERE watched_on IS NOT NULL", Map.of());
    }

    public long countViewingsBetween(LocalDate from, LocalDate to) {
        return count("SELECT count(*) FROM viewing_sessions WHERE watched_on BETWEEN :from AND :to",
                Map.of("from", Date.valueOf(from), "to", Date.valueOf(to)));
    }

    public long countMoviesWatchedBetween(LocalDate from, LocalDate to) {
        return count("SELECT count(DISTINCT movie_id) FROM viewing_sessions WHERE watched_on BETWEEN :from AND :to",
                Map.of("from", Date.valueOf(from), "to", Date.valueOf(to)));
    }

    public RatingSummary ratingSummary() {
        return jdbc.queryForObject("""
                SELECT avg(rating)::float8 AS average,
                       count(rating) AS rated,
                       count(*) FILTER (WHERE rating_beyond_scale) AS beyond,
                       count(*) FILTER (WHERE rating IS NULL AND NOT rating_beyond_scale) AS unrated
                FROM movies
                """, Map.of(), (rs, i) -> new RatingSummary(
                rs.getObject("average") == null ? null : rs.getDouble("average"),
                rs.getLong("rated"), rs.getLong("beyond"), rs.getLong("unrated")));
    }

    /** Counts per 1-10 value plus the beyond-scale tier as value 11. */
    public Map<Integer, Long> ratingCounts() {
        Map<Integer, Long> counts = new TreeMap<>();
        jdbc.query("""
                SELECT CASE WHEN rating_beyond_scale THEN 11 ELSE rating END AS value, count(*) AS count
                FROM movies
                WHERE rating IS NOT NULL OR rating_beyond_scale
                GROUP BY 1
                """, rs -> {
            counts.put(rs.getInt("value"), rs.getLong("count"));
        });
        return counts;
    }

    public List<TopMovie> topRated(int limit) {
        return jdbc.query("""
                SELECT m.id, m.title, m.release_year, m.language, m.rating, m.rating_beyond_scale,
                       (SELECT max(s.watched_on) FROM viewing_sessions s WHERE s.movie_id = m.id) AS last_watched_on
                FROM movies m
                WHERE m.rating IS NOT NULL OR m.rating_beyond_scale
                ORDER BY (CASE WHEN m.rating_beyond_scale THEN 11 ELSE m.rating END) DESC,
                         last_watched_on DESC NULLS LAST, lower(m.title)
                LIMIT :limit
                """, Map.of("limit", limit), (rs, i) -> new TopMovie(
                rs.getLong("id"), rs.getString("title"),
                rs.getObject("release_year", Integer.class), rs.getString("language"),
                rs.getObject("rating", Integer.class), rs.getBoolean("rating_beyond_scale"),
                toLocalDate(rs, "last_watched_on")));
    }

    public List<RecentViewing> recentViewings(int limit) {
        return jdbc.query("""
                SELECT s.id AS session_id, m.id AS movie_id, m.title, s.watched_on, s.method,
                       p.name AS platform, v.name AS venue, m.rating, m.rating_beyond_scale
                FROM viewing_sessions s
                JOIN movies m ON m.id = s.movie_id
                LEFT JOIN platforms p ON p.id = s.platform_id
                LEFT JOIN venues v ON v.id = s.venue_id
                WHERE s.watched_on IS NOT NULL
                ORDER BY s.watched_on DESC, s.id DESC
                LIMIT :limit
                """, Map.of("limit", limit), (rs, i) -> new RecentViewing(
                rs.getLong("session_id"), rs.getLong("movie_id"), rs.getString("title"),
                toLocalDate(rs, "watched_on"),
                rs.getString("method") == null ? null : ViewingMethod.valueOf(rs.getString("method")),
                rs.getString("platform"), rs.getString("venue"),
                rs.getObject("rating", Integer.class), rs.getBoolean("rating_beyond_scale")));
    }

    public List<LabelCount> viewingsByPlatform() {
        return jdbc.query("""
                SELECT p.name AS label, count(*) AS count
                FROM viewing_sessions s JOIN platforms p ON p.id = s.platform_id
                GROUP BY p.name ORDER BY count DESC, p.name
                """, LABEL_COUNT);
    }

    public List<LabelCount> viewingsByVenue() {
        return jdbc.query("""
                SELECT v.name AS label, count(*) AS count
                FROM viewing_sessions s JOIN venues v ON v.id = s.venue_id
                GROUP BY v.name ORDER BY count DESC, v.name
                """, LABEL_COUNT);
    }

    /** NULL methods are reported under the label "NOT_RECORDED" rather than dropped. */
    public List<LabelCount> viewingsByMethod() {
        return jdbc.query("""
                SELECT COALESCE(method, 'NOT_RECORDED') AS label, count(*) AS count
                FROM viewing_sessions GROUP BY 1 ORDER BY count DESC
                """, LABEL_COUNT);
    }

    public List<LabelCount> moviesByLanguage() {
        return jdbc.query("""
                SELECT language AS label, count(*) AS count FROM movies
                WHERE language IS NOT NULL GROUP BY language ORDER BY count DESC, language
                """, LABEL_COUNT);
    }

    public List<LabelCount> moviesByGenreCategory() {
        return jdbc.query("""
                SELECT g.category AS label, count(DISTINCT mg.movie_id) AS count
                FROM movie_genres mg JOIN genres g ON g.id = mg.genre_id
                GROUP BY g.category ORDER BY count DESC, g.category
                """, LABEL_COUNT);
    }

    public List<LabelCount> moviesByGenre() {
        return jdbc.query("""
                SELECT g.name AS label, count(*) AS count
                FROM movie_genres mg JOIN genres g ON g.id = mg.genre_id
                GROUP BY g.name ORDER BY count DESC, g.name
                """, LABEL_COUNT);
    }

    public List<LabelCount> moviesByReleaseDecade() {
        return jdbc.query("""
                SELECT ((release_year / 10) * 10)::text || 's' AS label, count(*) AS count
                FROM movies WHERE release_year IS NOT NULL
                GROUP BY release_year / 10 ORDER BY release_year / 10
                """, LABEL_COUNT);
    }

    public List<LabelCount> moviesByReleaseYear() {
        return jdbc.query("""
                SELECT release_year::text AS label, count(*) AS count
                FROM movies WHERE release_year IS NOT NULL
                GROUP BY release_year ORDER BY release_year
                """, LABEL_COUNT);
    }

    public List<PeriodCount> viewingsPerMonth() {
        return jdbc.query("""
                SELECT to_char(date_trunc('month', watched_on), 'YYYY-MM') AS period,
                       count(*) AS viewings, count(DISTINCT movie_id) AS movies
                FROM viewing_sessions WHERE watched_on IS NOT NULL
                GROUP BY 1 ORDER BY 1
                """, (rs, i) -> new PeriodCount(rs.getString("period"), rs.getLong("viewings"), rs.getLong("movies")));
    }

    public List<PeriodCount> viewingsPerYear() {
        return jdbc.query("""
                SELECT extract(year FROM watched_on)::int::text AS period,
                       count(*) AS viewings, count(DISTINCT movie_id) AS movies
                FROM viewing_sessions WHERE watched_on IS NOT NULL
                GROUP BY 1 ORDER BY 1
                """, (rs, i) -> new PeriodCount(rs.getString("period"), rs.getLong("viewings"), rs.getLong("movies")));
    }

    public List<LabelAverage> averageByGenre() {
        return averageBy("g.name", true);
    }

    public List<LabelAverage> averageByGenreCategory() {
        return averageBy("g.category", true);
    }

    public List<LabelAverage> averageByLanguage() {
        return averageBy("m.language", false);
    }

    public List<LabelAverage> averageByFormat() {
        return averageBy("m.format", false);
    }

    private List<LabelAverage> averageBy(String labelColumn, boolean viaGenres) {
        String from = viaGenres
                ? "movies m JOIN movie_genres mg ON mg.movie_id = m.id JOIN genres g ON g.id = mg.genre_id"
                : "movies m";
        return jdbc.query("""
                SELECT %1$s AS label,
                       avg(m.rating)::float8 AS average_rating,
                       count(m.rating) AS rated_count,
                       count(*) FILTER (WHERE m.rating_beyond_scale) AS beyond_count,
                       count(DISTINCT m.id) AS movie_count
                FROM %2$s
                WHERE %1$s IS NOT NULL
                GROUP BY %1$s
                ORDER BY movie_count DESC, %1$s
                """.formatted(labelColumn, from), LABEL_AVERAGE);
    }

    private long count(String sql, Map<String, ?> params) {
        Long value = jdbc.queryForObject(sql, new MapSqlParameterSource(params), Long.class);
        return value == null ? 0 : value;
    }

    private static LocalDate toLocalDate(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }
}
