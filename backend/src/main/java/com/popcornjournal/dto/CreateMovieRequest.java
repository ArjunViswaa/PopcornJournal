package com.popcornjournal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** A new movie, optionally with the first time it was watched. */
public record CreateMovieRequest(
        @NotNull @Valid MovieRequest movie,
        @Valid ViewingSessionRequest initialViewing) {
}
