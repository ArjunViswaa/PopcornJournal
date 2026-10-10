package com.popcornjournal.dto;

import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.entity.ViewingMethod;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Library query parameters, bound from the query string. Rating bounds treat the
 * beyond-the-scale tier as above 10, so minRating=9 includes it.
 */
public record MovieSearchCriteria(
        String q,
        String language,
        Long genreId,
        String category,
        Long platformId,
        Long venueId,
        ViewingMethod method,
        MovieFormat format,
        Integer watchedYear,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate watchedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate watchedTo,
        Integer minRating,
        Integer maxRating,
        RatingTier ratingTier,
        Boolean favourite,
        PersonalList list,
        MovieSort sort,
        Integer page,
        Integer size) {

    public enum RatingTier { RATED, UNRATED, BEYOND_SCALE }

    public enum PersonalList { FAVOURITES, TOP_RATED, RECENTLY_WATCHED, UNRATED, NO_REVIEW, MISSING_METADATA }

    public enum MovieSort { TITLE, LATEST_WATCHED, EARLIEST_WATCHED, HIGHEST_RATED, LOWEST_RATED, RECENTLY_ADDED, RELEASE_YEAR }

    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = 100;

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** A personal list implies a natural order when no explicit sort is given. */
    public MovieSort sortOrDefault() {
        if (sort != null) {
            return sort;
        }
        if (list == PersonalList.TOP_RATED) {
            return MovieSort.HIGHEST_RATED;
        }
        if (list == PersonalList.RECENTLY_WATCHED) {
            return MovieSort.LATEST_WATCHED;
        }
        return MovieSort.TITLE;
    }
}
