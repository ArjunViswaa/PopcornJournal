package com.popcornjournal.controller;

import com.popcornjournal.dto.CreateMovieRequest;
import com.popcornjournal.dto.MovieDetailDto;
import com.popcornjournal.dto.MovieRequest;
import com.popcornjournal.dto.MovieSearchCriteria;
import com.popcornjournal.dto.MovieSummaryDto;
import com.popcornjournal.dto.PageResponse;
import com.popcornjournal.dto.ReferenceDtos.DuplicateCandidate;
import com.popcornjournal.dto.ReferenceDtos.FavouriteRequest;
import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/movies")
@Tag(name = "Movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    @Operation(summary = "Search, filter, sort and page the library")
    public PageResponse<MovieSummaryDto> search(@ParameterObject MovieSearchCriteria criteria) {
        return movieService.search(criteria);
    }

    @GetMapping("/{id}")
    @Operation(summary = "A movie with its genres, viewings and spreadsheet origin")
    public MovieDetailDto get(@PathVariable Long id) {
        return movieService.get(id);
    }

    @PostMapping
    @Operation(summary = "Add a movie, optionally with its first viewing")
    public ResponseEntity<MovieDetailDto> create(@Valid @RequestBody CreateMovieRequest request) {
        MovieDetailDto created = movieService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/movies/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a movie's editable fields")
    public MovieDetailDto update(@PathVariable Long id, @Valid @RequestBody MovieRequest request) {
        return movieService.update(id, request);
    }

    @PatchMapping("/{id}/favourite")
    @Operation(summary = "Mark or unmark as favourite")
    public MovieDetailDto favourite(@PathVariable Long id, @RequestBody FavouriteRequest request) {
        return movieService.setFavourite(id, request.favourite());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a movie and all of its viewings")
    public void delete(@PathVariable Long id) {
        movieService.delete(id);
    }

    @GetMapping("/duplicates")
    @Operation(summary = "Movies with the same title, for a warning before saving")
    public List<DuplicateCandidate> duplicates(@RequestParam String title,
                                               @RequestParam(required = false) MovieFormat format,
                                               @RequestParam(required = false) Integer releaseYear,
                                               @RequestParam(required = false) String language,
                                               @RequestParam(required = false) Long excludeId) {
        return movieService.findPossibleDuplicates(title, format, releaseYear, language, excludeId);
    }
}
