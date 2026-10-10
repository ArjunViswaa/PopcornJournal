package com.popcornjournal.importer;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param nonStreamingPlatforms platform names that are recorded as method OTHER instead of
 *                              STREAMING (download/torrent sites in the original sheet)
 */
@ConfigurationProperties(prefix = "popcorn.import")
public record ImportProperties(List<String> nonStreamingPlatforms) {

    public ImportProperties {
        nonStreamingPlatforms = nonStreamingPlatforms == null ? List.of() : List.copyOf(nonStreamingPlatforms);
    }
}
