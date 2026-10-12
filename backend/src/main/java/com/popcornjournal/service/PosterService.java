package com.popcornjournal.service;

import com.popcornjournal.entity.Movie;
import com.popcornjournal.exception.ConflictException;
import com.popcornjournal.exception.InvalidRequestException;
import com.popcornjournal.exception.NotFoundException;
import com.popcornjournal.poster.ImageTypes;
import com.popcornjournal.poster.PosterMatcher;
import com.popcornjournal.poster.PosterProperties;
import com.popcornjournal.poster.PosterProvider;
import com.popcornjournal.poster.PosterQuery;
import com.popcornjournal.repository.MoviePosterRepository;
import com.popcornjournal.repository.MoviePosterRepository.LookupStatus;
import com.popcornjournal.repository.MoviePosterRepository.StoredImage;
import com.popcornjournal.repository.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Optional poster lookup. Network calls happen outside database transactions, and each
 * movie's result is saved on its own, so a failure part-way loses nothing already found.
 * Never touches a poster URL the user entered, nor any rating, review or note.
 */
@Service
public class PosterService {

    private static final Logger log = LoggerFactory.getLogger(PosterService.class);
    static final int MAX_BATCH = 25;
    /** source_name of posters the user uploaded; lookups never replace these. */
    public static final String UPLOAD_SOURCE = "Upload";
    static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

    public enum Outcome { FOUND, NOT_FOUND, FAILED }

    public record LookupResult(long movieId, String title, Outcome outcome, String matchedTitle) {
    }

    public record BatchResult(Instant runStartedAt, List<LookupResult> results, long remaining) {
    }

    public record Status(boolean enabled, String source, long totalMovies, long withOwnPoster,
                         long withFetchedPoster, long pending, long notFound, long rejected) {
    }

    private final PosterProperties properties;
    private final PosterProvider provider;
    private final MoviePosterRepository posters;
    private final MovieRepository movies;

    public PosterService(PosterProperties properties, PosterProvider provider, MoviePosterRepository posters,
                         MovieRepository movies) {
        this.properties = properties;
        this.provider = provider;
        this.posters = posters;
        this.movies = movies;
    }

    public Status status() {
        MoviePosterRepository.Counts counts = posters.counts();
        return new Status(properties.enabled(), provider.name(), counts.totalMovies(), counts.ownPoster(),
                counts.fetchedPoster(), counts.pending(), counts.notFound(), counts.rejected());
    }

    /**
     * Looks up the next few movies without a poster. The client calls again with the
     * returned runStartedAt until remaining is 0; within one run no movie is tried twice.
     */
    public BatchResult lookupBatch(Instant runStartedAt, boolean retryNotFound, int batchSize) {
        requireEnabled();
        Instant run = runStartedAt != null ? runStartedAt : Instant.now();
        int size = Math.clamp(batchSize, 1, MAX_BATCH);
        List<LookupResult> results = new ArrayList<>();
        for (PosterQuery query : posters.findPending(run, retryNotFound, size)) {
            results.add(lookUp(query));
        }
        return new BatchResult(run, results, posters.countPending(run, retryNotFound));
    }

    /** An explicit request for one movie, so it also retries not-found and removed posters. */
    public LookupResult lookupOne(long movieId) {
        requireEnabled();
        Movie movie = movies.findById(movieId).orElseThrow(() -> new NotFoundException("Movie", movieId));
        if (movie.getPosterUrl() != null) {
            throw new ConflictException("This movie uses a poster URL you entered; clear it first to look one up");
        }
        if (isUploaded(movieId)) {
            throw new ConflictException("You uploaded this movie's poster; remove it first to look one up");
        }
        return lookUp(new PosterQuery(movie.getId(), movie.getTitle(), movie.getFormat(),
                movie.getReleaseYear(), movie.getLanguage()));
    }

    /**
     * Stores an image the user chose. Works whether or not lookup is enabled, since nothing
     * leaves the machine. The type comes from the file's bytes, not its name.
     */
    public void upload(long movieId, byte[] bytes, String fileName) {
        Movie movie = movies.findById(movieId).orElseThrow(() -> new NotFoundException("Movie", movieId));
        if (movie.getPosterUrl() != null) {
            throw new ConflictException("This movie uses a poster URL you entered, which would hide an upload; "
                    + "clear the URL on the Edit page first");
        }
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new InvalidRequestException("Posters can be up to 5 MB");
        }
        String contentType = ImageTypes.detect(bytes)
                .orElseThrow(() -> new InvalidRequestException("Upload a JPEG, PNG, WebP or GIF image"));
        posters.save(movieId, bytes, contentType, UPLOAD_SOURCE, null, null, safeName(fileName));
    }

    /** Removes a found poster and remembers the choice so bulk runs don't fetch it again. */
    public void remove(long movieId) {
        if (!movies.existsById(movieId)) {
            throw new NotFoundException("Movie", movieId);
        }
        if (!posters.delete(movieId)) {
            throw new NotFoundException("Poster for movie", movieId);
        }
        posters.markLookup(movieId, LookupStatus.REJECTED);
    }

    public Optional<StoredImage> image(long movieId) {
        return posters.findImage(movieId);
    }

    private LookupResult lookUp(PosterQuery query) {
        try {
            Optional<PosterMatcher.Match> match = PosterMatcher.choose(query, provider.search(query.searchText()));
            if (match.isEmpty()) {
                match = PosterMatcher.choose(query, provider.search(query.fuzzySearchText()));
            }
            if (match.isEmpty()) {
                posters.markLookup(query.movieId(), LookupStatus.NOT_FOUND);
                return new LookupResult(query.movieId(), query.title(), Outcome.NOT_FOUND, null);
            }
            PosterMatcher.Candidate candidate = match.get().candidate();
            Optional<PosterProvider.Image> image = provider.download(candidate.imageUrl());
            if (image.isEmpty()) {
                posters.markLookup(query.movieId(), LookupStatus.NOT_FOUND);
                return new LookupResult(query.movieId(), query.title(), Outcome.NOT_FOUND, candidate.pageTitle());
            }
            posters.save(query.movieId(), image.get().bytes(), image.get().contentType(), provider.name(),
                    candidate.pageUrl(), candidate.imageUrl(), candidate.pageTitle());
            posters.markLookup(query.movieId(), LookupStatus.FOUND);
            return new LookupResult(query.movieId(), query.title(), Outcome.FOUND, candidate.pageTitle());
        } catch (RuntimeException ex) {
            // network trouble shouldn't stop the run; FAILED is retried by the next run
            log.warn("Poster lookup failed for movie {}: {}", query.movieId(), ex.getMessage());
            posters.markLookup(query.movieId(), LookupStatus.FAILED);
            return new LookupResult(query.movieId(), query.title(), Outcome.FAILED, null);
        }
    }

    private boolean isUploaded(long movieId) {
        return posters.findInfo(movieId).map(p -> UPLOAD_SOURCE.equals(p.sourceName())).orElse(false);
    }

    /** Only the base name is kept, as a label; it is never used as a path. */
    private static String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String base = fileName.replace('\\', '/');
        return base.substring(base.lastIndexOf('/') + 1);
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ConflictException("Poster lookup is turned off. Set POSTERS_ENABLED=true in .env to use it.");
        }
    }
}
