package com.popcornjournal.dto;

import com.popcornjournal.entity.MovieFormat;

import java.time.LocalDate;
import java.util.List;

/** One card/row in the library. */
public record MovieSummaryDto(
        Long id,
        String title,
        MovieFormat format,
        Integer releaseYear,
        String language,
        List<String> genres,
        Integer rating,
        boolean ratingBeyondScale,
        boolean favourite,
        boolean hasReview,
        String posterUrl,
        int viewingCount,
        LocalDate lastWatchedOn,
        List<String> platforms,
        List<String> venues) {
}
