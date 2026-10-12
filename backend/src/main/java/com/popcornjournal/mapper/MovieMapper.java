package com.popcornjournal.mapper;

import com.popcornjournal.dto.GenreDto;
import com.popcornjournal.dto.MovieDetailDto;
import com.popcornjournal.dto.MovieRequest;
import com.popcornjournal.dto.NamedRef;
import com.popcornjournal.dto.ReferenceDtos.DuplicateCandidate;
import com.popcornjournal.dto.ReferenceDtos.WatchlistItemDto;
import com.popcornjournal.dto.ViewingSessionDto;
import com.popcornjournal.entity.Genre;
import com.popcornjournal.entity.ImportSourceRow;
import com.popcornjournal.entity.Movie;
import com.popcornjournal.entity.ViewingSession;
import com.popcornjournal.entity.WatchlistItem;
import com.popcornjournal.repository.MoviePosterRepository;
import com.popcornjournal.support.Titles;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class MovieMapper {

    /** Copies editable fields onto the entity. Genres are resolved by the caller. */
    public void apply(MovieRequest request, Movie movie) {
        movie.setTitle(Titles.clean(request.title()));
        movie.setOriginalTitle(blankToNull(request.originalTitle()));
        movie.setFormat(request.format());
        movie.setReleaseYear(request.releaseYear());
        movie.setLanguage(Titles.clean(request.language()));
        movie.setSynopsis(blankToNull(request.synopsis()));
        movie.rate(request.rating(), request.ratingBeyondScale());
        movie.setReview(blankToNull(request.review()));
        movie.setPrivateNotes(blankToNull(request.privateNotes()));
        movie.setFavourite(request.favourite());
        movie.setDirector(Titles.clean(request.director()));
        movie.setCastMembers(blankToNull(request.castMembers()));
        movie.setRuntimeMinutes(request.runtimeMinutes());
        movie.setPosterUrl(blankToNull(request.posterUrl()));
        movie.setMetadataSource(Titles.clean(request.metadataSource()));
    }

    public MovieDetailDto toDetail(Movie movie, List<ViewingSession> viewings, List<ImportSourceRow> sourceRows,
                                   Optional<MoviePosterRepository.PosterInfo> fetchedPoster) {
        List<GenreDto> genres = movie.getGenres().stream()
                .sorted(Comparator.comparing(Genre::getName, String.CASE_INSENSITIVE_ORDER))
                .map(g -> new GenreDto(g.getId(), g.getName(), g.getCategory()))
                .toList();
        return new MovieDetailDto(
                movie.getId(), movie.getTitle(), movie.getOriginalTitle(), movie.getFormat(),
                movie.getReleaseYear(), movie.getLanguage(), movie.getSynopsis(),
                movie.getRating(), movie.isRatingBeyondScale(), movie.getReview(), movie.getPrivateNotes(),
                movie.isFavourite(), movie.getDirector(), movie.getCastMembers(), movie.getRuntimeMinutes(),
                movie.getPosterUrl(),
                fetchedPoster.map(p -> new MovieDetailDto.FetchedPoster(p.url(), p.sourceName(), p.sourcePageUrl(),
                        p.matchedTitle(), p.fetchedAt())).orElse(null),
                movie.getMetadataSource(), genres,
                viewings.stream().map(this::toDto).toList(),
                sourceRows.stream()
                        .map(r -> new MovieDetailDto.SourceRow(r.getSheetName(), r.getRowNumber(), r.getRawValues()))
                        .toList(),
                movie.getCreatedAt(), movie.getUpdatedAt());
    }

    public ViewingSessionDto toDto(ViewingSession session) {
        return new ViewingSessionDto(
                session.getId(),
                session.getMovie().getId(),
                session.getMovie().getTitle(),
                session.getWatchedOn(),
                session.getWatchedAtTime(),
                session.getMethod(),
                session.getPlatform() == null ? null
                        : new NamedRef(session.getPlatform().getId(), session.getPlatform().getName()),
                session.getVenue() == null ? null
                        : new NamedRef(session.getVenue().getId(), session.getVenue().getName()),
                session.getLocation(),
                session.getRating(),
                session.isRatingBeyondScale(),
                session.getNotes(),
                session.getImportKey() != null,
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    public DuplicateCandidate toCandidate(Movie movie, boolean exactMatch) {
        return new DuplicateCandidate(movie.getId(), movie.getTitle(), movie.getFormat(),
                movie.getReleaseYear(), movie.getLanguage(), exactMatch);
    }

    public WatchlistItemDto toDto(WatchlistItem item) {
        return new WatchlistItemDto(item.getId(), item.getTitle(), item.getRecommendedBy(),
                item.getGenre() == null ? null : item.getGenre().getName(),
                item.getNotes(), item.getImportKey() != null, item.getCreatedAt());
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
