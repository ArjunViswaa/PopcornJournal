package com.popcornjournal.importer;

import com.popcornjournal.support.Titles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps spreadsheet headers to fields by name rather than position, so reordering
 * columns in Excel doesn't break the import. Headers are compared by Titles.key.
 */
public enum ColumnMapping {

    TITLE("movieseriesname", "title", "moviename", "name", "movie"),
    DESCRIPTION("description", "synopsis", "plot"),
    GENRE("genre", "genres"),
    RATING("rating", "myrating", "score"),
    REVIEW("review", "myreview"),
    LANGUAGE("language"),
    FORMAT("format", "type"),
    PLATFORM("platform", "service", "ott"),
    YEAR("year", "releaseyear"),
    WATCH_DATE("watchdate", "watchedon", "datewatched"),
    THEATRE("theatre", "theater", "cinema", "venue"),
    RECOMMENDED_BY("recommendedby", "recommender");

    private final Set<String> headerKeys;

    ColumnMapping(String... headerKeys) {
        this.headerKeys = Set.of(headerKeys);
    }

    public static Optional<ColumnMapping> forHeader(String header) {
        String key = Titles.key(header);
        for (ColumnMapping column : values()) {
            if (column.headerKeys.contains(key)) {
                return Optional.of(column);
            }
        }
        return Optional.empty();
    }

    /** Resolved header names for one sheet. */
    public record Resolved(Map<ColumnMapping, String> headerFor, List<String> unmapped) {

        public static Resolved of(List<String> headers) {
            Map<ColumnMapping, String> mapped = new LinkedHashMap<>();
            List<String> unmapped = new ArrayList<>();
            for (String header : headers) {
                if (header == null) {
                    continue;
                }
                Optional<ColumnMapping> column = forHeader(header);
                if (column.isPresent() && !mapped.containsKey(column.get())) {
                    mapped.put(column.get(), header);
                } else {
                    unmapped.add(header);
                }
            }
            return new Resolved(mapped, unmapped);
        }

        public boolean has(ColumnMapping column) {
            return headerFor.containsKey(column);
        }

        public Object value(ColumnMapping column, Map<String, Object> rowValues) {
            String header = headerFor.get(column);
            return header == null ? null : rowValues.get(header);
        }
    }
}
