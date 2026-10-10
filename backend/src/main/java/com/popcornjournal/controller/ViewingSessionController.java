package com.popcornjournal.controller;

import com.popcornjournal.dto.PageResponse;
import com.popcornjournal.dto.ViewingSessionDto;
import com.popcornjournal.dto.ViewingSessionRequest;
import com.popcornjournal.service.ViewingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Viewing sessions")
public class ViewingSessionController {

    private final ViewingSessionService viewingService;

    public ViewingSessionController(ViewingSessionService viewingService) {
        this.viewingService = viewingService;
    }

    @GetMapping("/movies/{movieId}/viewings")
    @Operation(summary = "All viewings of one movie, newest first")
    public List<ViewingSessionDto> forMovie(@PathVariable Long movieId) {
        return viewingService.listForMovie(movieId);
    }

    @PostMapping("/movies/{movieId}/viewings")
    @Operation(summary = "Log another viewing of a movie")
    public ResponseEntity<ViewingSessionDto> create(@PathVariable Long movieId,
                                                    @Valid @RequestBody ViewingSessionRequest request) {
        ViewingSessionDto created = viewingService.create(movieId, request);
        return ResponseEntity.created(URI.create("/api/v1/viewings/" + created.id())).body(created);
    }

    @GetMapping("/viewings")
    @Operation(summary = "Viewing diary across all movies")
    public PageResponse<ViewingSessionDto> diary(@RequestParam(required = false) Integer year,
                                                 @RequestParam(defaultValue = "false") boolean undated,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "30") int size) {
        return viewingService.diary(year, undated, page, size);
    }

    @GetMapping("/viewings/{id}")
    public ViewingSessionDto get(@PathVariable Long id) {
        return viewingService.get(id);
    }

    @PutMapping("/viewings/{id}")
    @Operation(summary = "Edit a viewing")
    public ViewingSessionDto update(@PathVariable Long id, @Valid @RequestBody ViewingSessionRequest request) {
        return viewingService.update(id, request);
    }

    @DeleteMapping("/viewings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one viewing; the movie is kept")
    public void delete(@PathVariable Long id) {
        viewingService.delete(id);
    }
}
