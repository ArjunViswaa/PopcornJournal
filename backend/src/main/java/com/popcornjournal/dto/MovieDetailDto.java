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
        String posterUrl,
        String metadataSource,
        List<GenreDto> genres,
        List<ViewingSessionDto> viewings,
        List<SourceRow> importedFrom,
        Instant createdAt,
        Instant updatedAt) {

    /** Where an imported movie came from in the spreadsheet, with the original cells. */
    public record SourceRow(String sheet, int rowNumber, Map<String, Object> originalValues) {
    }
}
