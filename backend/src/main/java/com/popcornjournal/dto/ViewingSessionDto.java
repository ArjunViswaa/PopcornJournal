package com.popcornjournal.dto;

import com.popcornjournal.entity.ViewingMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ViewingSessionDto(
        Long id,
        Long movieId,
        String movieTitle,
        LocalDate watchedOn,
        LocalTime watchedAtTime,
        ViewingMethod method,
        NamedRef platform,
        NamedRef venue,
        String location,
        Integer rating,
        boolean ratingBeyondScale,
        String notes,
        boolean imported,
        Instant createdAt,
        Instant updatedAt) {
}
