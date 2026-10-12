package com.popcornjournal.dto;

import com.popcornjournal.entity.MovieFormat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MovieDetailDto(
        Long id,
        String title,
        String originalTitle,
        MovieFormat format,
        Integer releaseYear,
        String language,
        String synopsis,
        Integer rating,
        boolean ratingBeyondScale,
        String review,
        String privateNotes,
        boolean favourite,
        String director,
        String castMembers,
        Integer runtimeMinutes,
        /** The poster URL the user entered, if any; it wins over a fetched poster. */
        String posterUrl,
        FetchedPoster fetchedPoster,
        String metadataSource,
        List<GenreDto> genres,
        List<ViewingSessionDto> viewings,
        List<SourceRow> importedFrom,
        Instant createdAt,
        Instant updatedAt) {

    /** A poster found by the optional lookup and stored locally, with where it came from. */
    public record FetchedPoster(String url, String source, String sourcePageUrl, String matchedTitle,
                                Instant fetchedAt) {
    }

    /** Where an imported movie came from in the spreadsheet, with the original cells. */
    public record SourceRow(String sheet, int rowNumber, Map<String, Object> originalValues) {
    }
}
