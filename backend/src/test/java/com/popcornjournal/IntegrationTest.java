package com.popcornjournal;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Base for tests that need the real schema. Each test class gets an embedded PostgreSQL
 * (real binaries, no Docker) migrated by Flyway; tables are emptied before every test.
 * "Today" is fixed at 15 July 2026 so date-based statistics are deterministic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(IntegrationTest.FixedClock.class)
public abstract class IntegrationTest {

    public static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.execute("""
                TRUNCATE import_source_rows, import_runs, viewing_sessions, movie_genres, movies,
                         watchlist_items, genres, platforms, venues RESTART IDENTITY CASCADE""");
    }

    protected long count(String table) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Kolkata"));
        }
    }
}
