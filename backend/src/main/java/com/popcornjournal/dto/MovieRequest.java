package com.popcornjournal.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.popcornjournal.entity.MovieFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Fields of a movie that can be created or edited. Only the title and format are required. */
public record MovieRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 250) String title,
        @Size(max = 250) String originalTitle,
        @NotNull(message = "Choose movie or series") MovieFormat format,
        @Min(value = 1870, message = "Release year looks too early")
        @Max(value = 2100, message = "Release year looks too late") Integer releaseYear,
        @Size(max = 40) String language,
        @Size(max = 10_000) String synopsis,
        @Min(value = 1, message = "Rating must be between 1 and 10")
        @Max(value = 10, message = "Rating must be between 1 and 10") Integer rating,
        boolean ratingBeyondScale,
        @Size(max = 20_000) String review,
        @Size(max = 20_000) String privateNotes,
        boolean favourite,
        @Size(max = 250) String director,
        @Size(max = 5_000) String castMembers,
        @Min(value = 1, message = "Runtime must be positive")
        @Max(value = 2000, message = "Runtime looks too long") Integer runtimeMinutes,
        @Size(max = 500)
        @Pattern(regexp = "^https?://\\S+$", message = "Poster must be an http(s) URL") String posterUrl,
        @Size(max = 120) String metadataSource,
        @Size(max = 10, message = "At most 10 genres") List<@NotBlank @Size(max = 80) String> genres) {

    @JsonIgnore
    @AssertTrue(message = "Use either a 1-10 rating or the beyond-the-scale rating, not both")
    public boolean isSingleRating() {
        return !(ratingBeyondScale && rating != null);
    }
}
