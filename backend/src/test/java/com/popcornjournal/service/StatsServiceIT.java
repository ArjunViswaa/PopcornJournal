package com.popcornjournal.service;

import com.popcornjournal.IntegrationTest;
import com.popcornjournal.dto.StatsDtos.Dashboard;
import com.popcornjournal.dto.StatsDtos.Insights;
import com.popcornjournal.dto.StatsDtos.LabelCount;
import com.popcornjournal.dto.StatsDtos.PeriodCount;
import com.popcornjournal.dto.StatsDtos.RatingBucket;
import com.popcornjournal.importer.SpreadsheetImportService;
import com.popcornjournal.importer.WorkbookFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Statistics over the sample workbook, with "today" fixed at 15 July 2026. */
class StatsServiceIT extends IntegrationTest {

    @Autowired
    StatsService stats;

    @Autowired
    SpreadsheetImportService importer;

    @Test
    void emptyDatabaseHasNoAverageRatherThanZero() {
        Dashboard dashboard = stats.dashboard();

        assertThat(dashboard.totalMovies()).isZero();
        assertThat(dashboard.rating().average()).isNull();
        assertThat(dashboard.timeline()).isEmpty();
        assertThat(dashboard.ratingDistribution()).hasSize(11).allSatisfy(b -> assertThat(b.count()).isZero());
    }

    @Test
    void dashboardSeparatesMoviesFromViewings() {
        importer.commit(WorkbookFixture.sample().toBytes(), "sample.xlsx", "CLI");

        Dashboard dashboard = stats.dashboard();

        assertThat(dashboard.totalMovies()).isEqualTo(7);
        assertThat(dashboard.totalViewings()).isEqualTo(8);
        assertThat(dashboard.datedViewings()).isEqualTo(3);
        assertThat(dashboard.undatedViewings()).isEqualTo(5);
        // dated viewings: Harbour Lights 20 Jun, Midnight Signal 1 Jul and 10 Jul
        assertThat(dashboard.moviesThisYear()).isEqualTo(2);
        assertThat(dashboard.viewingsThisYear()).isEqualTo(3);
        assertThat(dashboard.moviesThisMonth()).isEqualTo(1);
        assertThat(dashboard.viewingsThisMonth()).isEqualTo(2);

        // ratings 9, 7, 8, 10, 6 average 8.0; the ∞ movie and the unrated one are counted separately
        assertThat(dashboard.rating().average()).isCloseTo(8.0, within(0.001));
        assertThat(dashboard.rating().ratedCount()).isEqualTo(5);
        assertThat(dashboard.rating().beyondScaleCount()).isEqualTo(1);
        assertThat(dashboard.rating().unratedCount()).isEqualTo(1);

        assertThat(dashboard.topRated().getFirst().ratingBeyondScale()).isTrue();
        assertThat(dashboard.recentlyWatched()).extracting(r -> r.watchedOn().toString())
                .containsExactly("2026-07-10", "2026-07-01", "2026-06-20");
        assertThat(dashboard.platforms()).startsWith(new LabelCount("Jio Hotstar", 2), new LabelCount("Netflix", 2));
        assertThat(dashboard.timeline()).extracting(PeriodCount::period).containsExactly("2026-06", "2026-07");
        assertThat(dashboard.ratingDistribution()).filteredOn(RatingBucket::beyondScale)
                .singleElement().satisfies(b -> assertThat(b.count()).isEqualTo(1));
    }

    @Test
    void insightsReportUnrecordedMethodsInsteadOfDroppingThem() {
        importer.commit(WorkbookFixture.sample().toBytes(), "sample.xlsx", "CLI");

        Insights insights = stats.insights();

        assertThat(insights.methods()).extracting(LabelCount::label)
                .containsExactlyInAnyOrder("STREAMING", "CINEMA", "OTHER");
        assertThat(insights.averageByLanguage()).anySatisfy(a -> {
            assertThat(a.label()).isEqualTo("Malayalam");
            assertThat(a.movieCount()).isEqualTo(3);
            assertThat(a.beyondScaleCount()).isEqualTo(1);
            assertThat(a.averageRating()).isCloseTo(8.0, within(0.001));
        });
        assertThat(insights.averageByLanguage()).anySatisfy(a -> {
            assertThat(a.label()).isEqualTo("Korean");
            assertThat(a.averageRating()).isNull();
        });
        assertThat(insights.undatedViewings()).isEqualTo(5);
    }

    @Test
    void monthGapsInsideTheRangeAreZeroButOutsideAreOmitted() {
        var filled = StatsService.fillMonthGaps(java.util.List.of(
                new PeriodCount("2025-11", 2, 2), new PeriodCount("2026-02", 1, 1)));

        assertThat(filled).extracting(PeriodCount::period)
                .containsExactly("2025-11", "2025-12", "2026-01", "2026-02");
        assertThat(filled.get(1).viewings()).isZero();
    }
}
