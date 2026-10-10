package com.popcornjournal.dto;

import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.entity.ViewingMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Small request/response shapes for genres, platforms, venues, favourites and the watchlist. */
public final class ReferenceDtos {

    private ReferenceDtos() {
    }

    public record GenreSummary(Long id, String name, String category, long movieCount) {
    }

    public record GenreRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 60) String category) {
    }

    public record PlatformSummary(Long id, String name, ViewingMethod defaultMethod, long viewingCount) {
    }

    public record PlatformRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull ViewingMethod defaultMethod) {
    }

    public record VenueSummary(Long id, String name, String city, long viewingCount) {
    }

    public record VenueRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String city) {
    }

    public record FavouriteRequest(boolean favourite) {
    }

    public record DuplicateCandidate(Long id, String title, MovieFormat format, Integer releaseYear,
                                     String language, boolean exactMatch) {
    }

    public record WatchlistItemDto(Long id, String title, String recommendedBy, String genre,
                                   String notes, boolean imported, Instant createdAt) {
    }

    public record WatchlistItemRequest(
            @NotBlank(message = "Title is required") @Size(max = 250) String title,
            @Size(max = 160) String recommendedBy,
            @Size(max = 80) String genre,
            @Size(max = 5_000) String notes) {
    }
}
