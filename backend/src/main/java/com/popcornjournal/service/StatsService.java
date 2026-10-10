package com.popcornjournal.service;

import com.popcornjournal.dto.StatsDtos.Dashboard;
import com.popcornjournal.dto.StatsDtos.Insights;
import com.popcornjournal.dto.StatsDtos.PeriodCount;
import com.popcornjournal.dto.StatsDtos.RatingBucket;
import com.popcornjournal.repository.StatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class StatsService {

    static final int BEYOND_SCALE_VALUE = 11;

    private final StatsRepository stats;
    private final Clock clock;

    public StatsService(StatsRepository stats, Clock clock) {
        this.stats = stats;
        this.clock = clock;
    }

    public Dashboard dashboard() {
        LocalDate today = LocalDate.now(clock);
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        long viewings = stats.countViewings();
        long dated = stats.countDatedViewings();

        return new Dashboard(
                today,
                stats.countMovies(),
                viewings,
                dated,
                viewings - dated,
                stats.countMoviesWatchedBetween(yearStart, today),
                stats.countViewingsBetween(yearStart, today),
                stats.countMoviesWatchedBetween(monthStart, today),
                stats.countViewingsBetween(monthStart, today),
                stats.ratingSummary(),
                stats.topRated(6),
                stats.recentViewings(6),
                stats.viewingsByPlatform(),
                stats.moviesByLanguage(),
                stats.moviesByGenreCategory(),
                stats.moviesByReleaseDecade(),
                ratingDistribution(),
                fillMonthGaps(stats.viewingsPerMonth()));
    }

    public Insights insights() {
        long viewings = stats.countViewings();
        return new Insights(
                fillMonthGaps(stats.viewingsPerMonth()),
                stats.viewingsPerYear(),
                viewings - stats.countDatedViewings(),
                stats.averageByGenre(),
                stats.averageByGenreCategory(),
                stats.averageByLanguage(),
                stats.averageByFormat(),
                stats.viewingsByPlatform(),
                stats.viewingsByVenue(),
                stats.viewingsByMethod(),
                stats.moviesByGenre(),
                stats.moviesByReleaseYear(),
                stats.ratingSummary(),
                ratingDistribution());
    }

    /** Always returns all eleven buckets (1-10 and ∞) so charts keep a stable axis. */
    List<RatingBucket> ratingDistribution() {
        Map<Integer, Long> counts = stats.ratingCounts();
        List<RatingBucket> buckets = new ArrayList<>();
        for (int value = 1; value <= 10; value++) {
            buckets.add(new RatingBucket(String.valueOf(value), value, false, counts.getOrDefault(value, 0L)));
        }
        buckets.add(new RatingBucket("∞", null, true, counts.getOrDefault(BEYOND_SCALE_VALUE, 0L)));
        return buckets;
    }

    /**
     * Inserts months with no dated viewings between the first and last recorded month.
     * Those zeros are real (nothing was logged that month); months outside the range
     * are left out rather than shown as zero.
     */
    static List<PeriodCount> fillMonthGaps(List<PeriodCount> months) {
        if (months.size() < 2) {
            return months;
        }
        Map<String, PeriodCount> byPeriod = new HashMap<>();
        months.forEach(m -> byPeriod.put(m.period(), m));
        YearMonth first = YearMonth.parse(months.getFirst().period());
        YearMonth last = YearMonth.parse(months.getLast().period());
        List<PeriodCount> filled = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            String key = month.toString();
            filled.add(byPeriod.getOrDefault(key, new PeriodCount(key, 0, 0)));
        }
        return filled;
    }
}
