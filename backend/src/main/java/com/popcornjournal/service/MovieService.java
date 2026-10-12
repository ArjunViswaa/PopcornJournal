package com.popcornjournal.service;

import com.popcornjournal.dto.CreateMovieRequest;
import com.popcornjournal.dto.MovieDetailDto;
import com.popcornjournal.dto.MovieRequest;
import com.popcornjournal.dto.MovieSearchCriteria;
import com.popcornjournal.dto.MovieSummaryDto;
import com.popcornjournal.dto.PageResponse;
import com.popcornjournal.dto.ReferenceDtos.DuplicateCandidate;
import com.popcornjournal.entity.Movie;
import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.exception.ConflictException;
import com.popcornjournal.exception.NotFoundException;
import com.popcornjournal.mapper.MovieMapper;
import com.popcornjournal.repository.ImportSourceRowRepository;
import com.popcornjournal.repository.MoviePosterRepository;
import com.popcornjournal.repository.MovieRepository;
import com.popcornjournal.repository.MovieSearchRepository;
import com.popcornjournal.repository.ViewingSessionRepository;
import com.popcornjournal.support.Titles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class MovieService {

    private final MovieRepository movies;
    private final MovieSearchRepository search;
    private final ViewingSessionRepository sessions;
    private final ImportSourceRowRepository sourceRows;
    private final ReferenceDataService referenceData;
    private final ViewingSessionService viewingSessions;
    private final MovieMapper mapper;
    private final MoviePosterRepository posters;

    public MovieService(MovieRepository movies, MovieSearchRepository search, ViewingSessionRepository sessions,
                        ImportSourceRowRepository sourceRows, ReferenceDataService referenceData,
                        ViewingSessionService viewingSessions, MovieMapper mapper, MoviePosterRepository posters) {
        this.movies = movies;
        this.search = search;
        this.sessions = sessions;
        this.sourceRows = sourceRows;
        this.referenceData = referenceData;
        this.viewingSessions = viewingSessions;
        this.mapper = mapper;
        this.posters = posters;
    }

    @Transactional(readOnly = true)
    public PageResponse<MovieSummaryDto> search(MovieSearchCriteria criteria) {
        return search.search(criteria);
    }

    @Transactional(readOnly = true)
    public MovieDetailDto get(Long id) {
        Movie movie = movies.findWithGenresById(id).orElseThrow(() -> new NotFoundException("Movie", id));
        return mapper.toDetail(movie, sessions.findForMovie(id),
                sourceRows.findByMovieIdOrderBySheetNameAscRowNumberAsc(id), posters.findInfo(id));
    }

    public MovieDetailDto create(CreateMovieRequest request) {
        MovieRequest fields = request.movie();
        rejectExactDuplicate(fields, null);

        Movie movie = new Movie();
        mapper.apply(fields, movie);
        movie.replaceGenres(referenceData.resolveGenres(fields.genres()));
        movie = movies.save(movie);

        if (request.initialViewing() != null) {
            viewingSessions.addTo(movie, request.initialViewing());
        }
        return get(movie.getId());
    }

    public MovieDetailDto update(Long id, MovieRequest request) {
        Movie movie = movies.findWithGenresById(id).orElseThrow(() -> new NotFoundException("Movie", id));
        rejectExactDuplicate(request, id);
        mapper.apply(request, movie);
        movie.replaceGenres(referenceData.resolveGenres(request.genres()));
        movies.flush();
        return get(id);
    }

    public MovieDetailDto setFavourite(Long id, boolean favourite) {
        Movie movie = movies.findById(id).orElseThrow(() -> new NotFoundException("Movie", id));
        movie.setFavourite(favourite);
        movies.flush();
        return get(id);
    }

    /** Deletes the movie and, through the foreign key, its viewing sessions. */
    public void delete(Long id) {
        Movie movie = movies.findById(id).orElseThrow(() -> new NotFoundException("Movie", id));
        movies.delete(movie);
    }

    /**
     * Movies that share the title's key. An exact match (same format, year and language)
     * would be rejected; the rest are shown as a heads-up only.
     */
    @Transactional(readOnly = true)
    public List<DuplicateCandidate> findPossibleDuplicates(String title, MovieFormat format,
                                                           Integer releaseYear, String language, Long excludeId) {
        String key = Titles.key(title);
        if (key.isEmpty()) {
            return List.of();
        }
        return movies.findTop10ByTitleKeyOrderByReleaseYearAsc(key).stream()
                .filter(m -> excludeId == null || !m.getId().equals(excludeId))
                .map(m -> mapper.toCandidate(m, isSameIdentity(m, format, releaseYear, language)))
                .toList();
    }

    private void rejectExactDuplicate(MovieRequest request, Long excludeId) {
        String language = Titles.clean(request.language());
        List<DuplicateCandidate> clashes = movies
                .findIdentityMatches(Titles.key(request.title()), request.format(), request.releaseYear(), language)
                .stream()
                .filter(m -> excludeId == null || !m.getId().equals(excludeId))
                .map(m -> mapper.toCandidate(m, true))
                .toList();
        if (!clashes.isEmpty()) {
            throw new ConflictException("This movie is already in your journal. Give it a different release year "
                    + "or language if it really is a different film.", clashes);
        }
    }

    private static boolean isSameIdentity(Movie movie, MovieFormat format, Integer releaseYear, String language) {
        return movie.getFormat() == (format == null ? MovieFormat.MOVIE : format)
                && Objects.equals(movie.getReleaseYear(), releaseYear)
                && Objects.equals(lower(movie.getLanguage()), lower(Titles.clean(language)));
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }
}
