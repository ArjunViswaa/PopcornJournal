package com.popcornjournal.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.popcornjournal.entity.ViewingMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** Every field is optional: a viewing can be as vague as the memory of it. */
public record ViewingSessionRequest(
        @PastOrPresent(message = "Watch date can't be in the future") LocalDate watchedOn,
        LocalTime watchedAtTime,
        ViewingMethod method,
        @Size(max = 80) String platformName,
        @Size(max = 160) String venueName,
        @Size(max = 200) String location,
        @Min(value = 1, message = "Rating must be between 1 and 10")
        @Max(value = 10, message = "Rating must be between 1 and 10") Integer rating,
        boolean ratingBeyondScale,
        @Size(max = 10_000) String notes) {

    @JsonIgnore
    @AssertTrue(message = "A watch time needs a watch date")
    public boolean isTimeWithDate() {
        return watchedAtTime == null || watchedOn != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Use either a 1-10 rating or the beyond-the-scale rating, not both")
    public boolean isSingleRating() {
        return !(ratingBeyondScale && rating != null);
    }
}
