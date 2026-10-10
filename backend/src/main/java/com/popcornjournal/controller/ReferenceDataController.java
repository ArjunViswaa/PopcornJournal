package com.popcornjournal.controller;

import com.popcornjournal.dto.ReferenceDtos.GenreRequest;
import com.popcornjournal.dto.ReferenceDtos.GenreSummary;
import com.popcornjournal.dto.ReferenceDtos.PlatformRequest;
import com.popcornjournal.dto.ReferenceDtos.PlatformSummary;
import com.popcornjournal.dto.ReferenceDtos.VenueRequest;
import com.popcornjournal.dto.ReferenceDtos.VenueSummary;
import com.popcornjournal.service.ReferenceDataService;
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
@RequestMapping("/api/v1")
@Tag(name = "Genres, platforms and venues")
public class ReferenceDataController {

    private final ReferenceDataService referenceData;

    public ReferenceDataController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    @GetMapping("/genres")
    public List<GenreSummary> genres() {
        return referenceData.listGenres();
    }

    @PostMapping("/genres")
    @ResponseStatus(HttpStatus.CREATED)
    public GenreSummary createGenre(@Valid @RequestBody GenreRequest request) {
        return referenceData.createGenre(request);
    }

    @PutMapping("/genres/{id}")
    public GenreSummary updateGenre(@PathVariable Long id, @Valid @RequestBody GenreRequest request) {
        return referenceData.updateGenre(id, request);
    }

    @DeleteMapping("/genres/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGenre(@PathVariable Long id) {
        referenceData.deleteGenre(id);
    }

    @GetMapping("/platforms")
    public List<PlatformSummary> platforms() {
        return referenceData.listPlatforms();
    }

    @PostMapping("/platforms")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformSummary createPlatform(@Valid @RequestBody PlatformRequest request) {
        return referenceData.createPlatform(request);
    }

    @PutMapping("/platforms/{id}")
    public PlatformSummary updatePlatform(@PathVariable Long id, @Valid @RequestBody PlatformRequest request) {
        return referenceData.updatePlatform(id, request);
    }

    @DeleteMapping("/platforms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlatform(@PathVariable Long id) {
        referenceData.deletePlatform(id);
    }

    @GetMapping("/venues")
    public List<VenueSummary> venues() {
        return referenceData.listVenues();
    }

    @PostMapping("/venues")
    @ResponseStatus(HttpStatus.CREATED)
    public VenueSummary createVenue(@Valid @RequestBody VenueRequest request) {
        return referenceData.createVenue(request);
    }

    @PutMapping("/venues/{id}")
    public VenueSummary updateVenue(@PathVariable Long id, @Valid @RequestBody VenueRequest request) {
        return referenceData.updateVenue(id, request);
    }

    @DeleteMapping("/venues/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVenue(@PathVariable Long id) {
        referenceData.deleteVenue(id);
    }

    @GetMapping("/languages")
    public List<String> languages() {
        return referenceData.listLanguages();
    }
}
