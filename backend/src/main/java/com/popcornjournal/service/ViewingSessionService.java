package com.popcornjournal.service;

import com.popcornjournal.dto.PageResponse;
import com.popcornjournal.dto.ViewingSessionDto;
import com.popcornjournal.dto.ViewingSessionRequest;
import com.popcornjournal.entity.Movie;
import com.popcornjournal.entity.Platform;
import com.popcornjournal.entity.ViewingMethod;
import com.popcornjournal.entity.ViewingSession;
import com.popcornjournal.exception.NotFoundException;
import com.popcornjournal.mapper.MovieMapper;
import com.popcornjournal.repository.MovieRepository;
import com.popcornjournal.repository.ViewingSessionRepository;
import com.popcornjournal.support.Titles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class ViewingSessionService {

    private final ViewingSessionRepository sessions;
    private final MovieRepository movies;
    private final ReferenceDataService referenceData;
    private final MovieMapper mapper;

    public ViewingSessionService(ViewingSessionRepository sessions, MovieRepository movies,
                                 ReferenceDataService referenceData, MovieMapper mapper) {
        this.sessions = sessions;
        this.movies = movies;
        this.referenceData = referenceData;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ViewingSessionDto> listForMovie(Long movieId) {
        if (!movies.existsById(movieId)) {
            throw new NotFoundException("Movie", movieId);
        }
        return sessions.findForMovie(movieId).stream().map(mapper::toDto).toList();
    }

    /** All viewings, newest first. With a year, only dated viewings from that year. */
    @Transactional(readOnly = true)
    public PageResponse<ViewingSessionDto> diary(Integer year, boolean undatedOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        Page<ViewingSession> result;
        if (undatedOnly) {
            result = sessions.findUndated(pageable);
        } else if (year != null) {
            result = sessions.findDiaryBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), pageable);
        } else {
            result = sessions.findDiary(pageable);
        }
        return PageResponse.from(result, mapper::toDto);
    }

    @Transactional(readOnly = true)
    public ViewingSessionDto get(Long id) {
        return mapper.toDto(find(id));
    }

    public ViewingSessionDto create(Long movieId, ViewingSessionRequest request) {
        Movie movie = movies.findById(movieId).orElseThrow(() -> new NotFoundException("Movie", movieId));
        return mapper.toDto(addTo(movie, request));
    }

    ViewingSession addTo(Movie movie, ViewingSessionRequest request) {
        ViewingSession session = new ViewingSession(movie);
        apply(request, session);
        return sessions.save(session);
    }

    public ViewingSessionDto update(Long id, ViewingSessionRequest request) {
        ViewingSession session = find(id);
        apply(request, session);
        sessions.flush();
        return mapper.toDto(session);
    }

    /** Removes one viewing; the movie and its other viewings stay. */
    public void delete(Long id) {
        ViewingSession session = sessions.findById(id).orElseThrow(() -> new NotFoundException("Viewing", id));
        sessions.delete(session);
    }

    private void apply(ViewingSessionRequest request, ViewingSession session) {
        Platform platform = referenceData.resolvePlatform(request.platformName(), request.method()).orElse(null);
        ViewingMethod method = request.method();
        if (method == null && platform != null) {
            method = platform.getDefaultMethod();
        }
        if (method == null && Titles.clean(request.venueName()) != null) {
            method = ViewingMethod.CINEMA;
        }
        session.setWatchedOn(request.watchedOn());
        session.setWatchedAtTime(request.watchedAtTime());
        session.setMethod(method);
        session.setPlatform(platform);
        session.setVenue(referenceData.resolveVenue(request.venueName()).orElse(null));
        session.setLocation(MovieMapper.blankToNull(request.location()));
        session.rate(request.rating(), request.ratingBeyondScale());
        session.setNotes(MovieMapper.blankToNull(request.notes()));
    }

    private ViewingSession find(Long id) {
        return sessions.findDetailedById(id).orElseThrow(() -> new NotFoundException("Viewing", id));
    }
}
