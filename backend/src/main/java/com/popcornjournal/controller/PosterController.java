package com.popcornjournal.controller;

import com.popcornjournal.exception.InvalidRequestException;
import com.popcornjournal.exception.NotFoundException;
import com.popcornjournal.repository.MoviePosterRepository.StoredImage;
import com.popcornjournal.service.PosterService;
import com.popcornjournal.service.PosterService.BatchResult;
import com.popcornjournal.service.PosterService.LookupResult;
import com.popcornjournal.service.PosterService.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Posters")
public class PosterController {

    private final PosterService posterService;

    public PosterController(PosterService posterService) {
        this.posterService = posterService;
    }

    @GetMapping("/posters/status")
    @Operation(summary = "Whether lookup is enabled and how many movies have posters")
    public Status status() {
        return posterService.status();
    }

    @PostMapping("/posters/lookup")
    @Operation(summary = "Look up posters for the next batch of movies without one; repeat until remaining is 0")
    public BatchResult lookupBatch(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant runStartedAt,
            @RequestParam(defaultValue = "false") boolean retryNotFound,
            @RequestParam(defaultValue = "10") int batchSize) {
        return posterService.lookupBatch(runStartedAt, retryNotFound, batchSize);
    }

    @PostMapping("/movies/{id}/poster/lookup")
    @Operation(summary = "Look up a poster for one movie")
    public LookupResult lookupOne(@PathVariable long id) {
        return posterService.lookupOne(id);
    }

    @GetMapping("/movies/{id}/poster")
    @Operation(summary = "The stored poster image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        StoredImage image = posterService.image(id).orElseThrow(() -> new NotFoundException("Poster for movie", id));
        // the URL carries a version (?v=fetch time), so a replaced poster gets a new URL
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(image.bytes());
    }

    @PostMapping(path = "/movies/{id}/poster", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Upload your own poster image (JPEG, PNG, WebP or GIF, up to 5 MB)")
    public void upload(@PathVariable long id, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new InvalidRequestException("The uploaded file is empty");
        }
        posterService.upload(id, file.getBytes(), file.getOriginalFilename());
    }

    @DeleteMapping("/movies/{id}/poster")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a found or uploaded poster; bulk lookups won't fetch one again")
    public void remove(@PathVariable long id) {
        posterService.remove(id);
    }
}
