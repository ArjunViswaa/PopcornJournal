package com.popcornjournal.dto;

import com.popcornjournal.entity.ViewingMethod;

import java.time.LocalDate;
import java.util.List;

/**
 * Statistics responses. Missing values are never folded into zero: unrated movies,
 * undated viewings and unrecorded methods are counted separately.
 */
public final class StatsDtos {

    private StatsDtos() {
    }

    public record LabelCount(String label, long count) {
    }

    /** averageRating is null when no movie in the group has a 1-10 rating. */
    public record LabelAverage(String label, Double averageRating, long ratedCount,
                               long beyondScaleCount, long movieCount) {
    }

    /** label is "1".."10" or "∞"; unrated movies are reported in RatingSummary instead. */
    public record RatingBucket(String label, Integer value, boolean beyondScale, long count) {
    }

    /** period is "yyyy-MM" or "yyyy". */
    public record PeriodCount(String period, long viewings, long movies) {
    }

    public record RatingSummary(Double average, long ratedCount, long beyondScaleCount, long unratedCount) {
    }

    public record TopMovie(Long id, String title, Integer releaseYear, String language,
                           Integer rating, boolean ratingBeyondScale, LocalDate lastWatchedOn) {
    }

    public record RecentViewing(Long sessionId, Long movieId, String title, LocalDate watchedOn,
                                ViewingMethod method, String platform, String venue,
                                Integer rating, boolean ratingBeyondScale) {
    }

    public record Dashboard(
            LocalDate today,
            long totalMovies,
            long totalViewings,
            long datedViewings,
            long undatedViewings,
            long moviesThisYear,
            long viewingsThisYear,
            long moviesThisMonth,
            long viewingsThisMonth,
            RatingSummary rating,
            List<TopMovie> topRated,
            List<RecentViewing> recentlyWatched,
            List<LabelCount> platforms,
            List<LabelCount> languages,
            List<LabelCount> genreCategories,
            List<LabelCount> releaseDecades,
            List<RatingBucket> ratingDistribution,
            List<PeriodCount> timeline) {
    }

    public record Insights(
            List<PeriodCount> viewingsPerMonth,
            List<PeriodCount> viewingsPerYear,
            long undatedViewings,
            List<LabelAverage> averageByGenre,
            List<LabelAverage> averageByCategory,
            List<LabelAverage> averageByLanguage,
            List<LabelAverage> averageByFormat,
            List<LabelCount> platforms,
            List<LabelCount> venues,
            List<LabelCount> methods,
            List<LabelCount> genresByMovies,
            List<LabelCount> releaseYears,
            RatingSummary rating,
            List<RatingBucket> ratingDistribution) {
    }
}
