package com.popcornjournal.poster;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional poster lookup. Off unless POSTERS_ENABLED=true: when on, a movie's title, year,
 * format and language are sent to the Wikipedia API to find a matching article image.
 */
@ConfigurationProperties(prefix = "popcorn.posters")
public record PosterProperties(boolean enabled, String wikipediaApi, String userAgent, int thumbnailWidth) {

    public PosterProperties {
        if (wikipediaApi == null || wikipediaApi.isBlank()) {
            wikipediaApi = "https://en.wikipedia.org/w/api.php";
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "PopcornJournal/1.0 (personal local app)";
        }
        if (thumbnailWidth <= 0) {
            thumbnailWidth = 500;
        }
    }
}
