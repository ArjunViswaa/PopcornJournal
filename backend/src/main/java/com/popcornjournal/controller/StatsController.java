package com.popcornjournal.controller;

import com.popcornjournal.dto.StatsDtos.Dashboard;
import com.popcornjournal.dto.StatsDtos.Insights;
import com.popcornjournal.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Statistics")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Headline numbers and charts for the home page")
    public Dashboard dashboard() {
        return statsService.dashboard();
    }

    @GetMapping("/insights")
    @Operation(summary = "Detailed statistics: averages, distributions and trends")
    public Insights insights() {
        return statsService.insights();
    }
}
