package com.popcornjournal.controller;

import com.popcornjournal.dto.ReferenceDtos.WatchlistItemDto;
import com.popcornjournal.dto.ReferenceDtos.WatchlistItemRequest;
import com.popcornjournal.service.WatchlistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlist")
@Tag(name = "Watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistItemDto> list() {
        return watchlistService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistItemDto create(@Valid @RequestBody WatchlistItemRequest request) {
        return watchlistService.create(request);
    }

    @PutMapping("/{id}")
    public WatchlistItemDto update(@PathVariable Long id, @Valid @RequestBody WatchlistItemRequest request) {
        return watchlistService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        watchlistService.delete(id);
    }
}
