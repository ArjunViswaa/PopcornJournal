package com.popcornjournal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/health")
    @Operation(summary = "API and database status")
    public ResponseEntity<Map<String, String>> health() {
        try {
            String schema = jdbc.queryForObject(
                    "SELECT max(version) FROM flyway_schema_history WHERE success", String.class);
            return ResponseEntity.ok(Map.of("status", "UP", "database", "UP", "schemaVersion", String.valueOf(schema)));
        } catch (RuntimeException ex) {
            log.warn("Health check could not reach the database: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN", "database", "DOWN"));
        }
    }
}
