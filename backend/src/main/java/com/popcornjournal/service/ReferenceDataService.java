package com.popcornjournal.service;

import com.popcornjournal.dto.ReferenceDtos.GenreRequest;
import com.popcornjournal.dto.ReferenceDtos.GenreSummary;
import com.popcornjournal.dto.ReferenceDtos.PlatformRequest;
import com.popcornjournal.dto.ReferenceDtos.PlatformSummary;
import com.popcornjournal.dto.ReferenceDtos.VenueRequest;
import com.popcornjournal.dto.ReferenceDtos.VenueSummary;
import com.popcornjournal.entity.Genre;
import com.popcornjournal.entity.Platform;
import com.popcornjournal.entity.Venue;
import com.popcornjournal.entity.ViewingMethod;
import com.popcornjournal.exception.ConflictException;
import com.popcornjournal.exception.InvalidRequestException;
import com.popcornjournal.exception.NotFoundException;
import com.popcornjournal.repository.GenreRepository;
import com.popcornjournal.repository.MovieRepository;
import com.popcornjournal.repository.PlatformRepository;
import com.popcornjournal.repository.VenueRepository;
import com.popcornjournal.repository.ViewingSessionRepository;
import com.popcornjournal.support.Titles;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Genres, platforms and venues. Lookups match on the normalised name, so "Zee5" and
 * "Zee 5" resolve to the same platform instead of creating a near-duplicate.
 */
@Service
@Transactional
public class ReferenceDataService {

    private final GenreRepository genres;
    private final PlatformRepository platforms;
    private final VenueRepository venues;
    private final MovieRepository movies;
    private final ViewingSessionRepository sessions;
    private final NamedParameterJdbcTemplate jdbc;

    public ReferenceDataService(GenreRepository genres, PlatformRepository platforms, VenueRepository venues,
                                MovieRepository movies, ViewingSessionRepository sessions,
                                NamedParameterJdbcTemplate jdbc) {
        this.genres = genres;
        this.platforms = platforms;
        this.venues = venues;
        this.movies = movies;
        this.sessions = sessions;
        this.jdbc = jdbc;
    }

    // ---- lookups used by movie, viewing and import services

    public Set<Genre> resolveGenres(Collection<String> names) {
        Set<Genre> resolved = new LinkedHashSet<>();
        if (names == null) {
            return resolved;
        }
        Map<String, Genre> existing = byKey(genres.findAll(), Genre::getName);
        for (String raw : names) {
            String name = Titles.clean(raw);
            if (name == null) {
                continue;
            }
            Genre genre = existing.computeIfAbsent(Titles.key(name),
                    key -> genres.save(new Genre(name, Titles.genreCategory(name))));
            resolved.add(genre);
        }
        return resolved;
    }

    public Optional<Platform> resolvePlatform(String rawName, ViewingMethod methodIfNew) {
        String name = Titles.clean(rawName);
        if (name == null) {
            return Optional.empty();
        }
        Platform match = byKey(platforms.findAll(), Platform::getName).get(Titles.key(name));
        if (match != null) {
            return Optional.of(match);
        }
        return Optional.of(platforms.save(new Platform(name,
                methodIfNew == null ? ViewingMethod.STREAMING : methodIfNew)));
    }

    public Optional<Venue> resolveVenue(String rawName) {
        String name = Titles.clean(rawName);
        if (name == null) {
            return Optional.empty();
        }
        Venue match = byKey(venues.findAll(), Venue::getName).get(Titles.key(name));
        return Optional.of(match != null ? match : venues.save(new Venue(name)));
    }

    // ---- listings with usage counts

    @Transactional(readOnly = true)
    public List<GenreSummary> listGenres() {
        Map<Long, Long> counts = usageCounts("SELECT genre_id AS id, count(*) AS n FROM movie_genres GROUP BY genre_id");
        return genres.findAllByOrderByCategoryAscNameAsc().stream()
                .map(g -> new GenreSummary(g.getId(), g.getName(), g.getCategory(), counts.getOrDefault(g.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformSummary> listPlatforms() {
        Map<Long, Long> counts = usageCounts("""
                SELECT platform_id AS id, count(*) AS n FROM viewing_sessions
                WHERE platform_id IS NOT NULL GROUP BY platform_id""");
        return platforms.findAllByOrderByNameAsc().stream()
                .map(p -> new PlatformSummary(p.getId(), p.getName(), p.getDefaultMethod(),
                        counts.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VenueSummary> listVenues() {
        Map<Long, Long> counts = usageCounts("""
                SELECT venue_id AS id, count(*) AS n FROM viewing_sessions
                WHERE venue_id IS NOT NULL GROUP BY venue_id""");
        return venues.findAllByOrderByNameAsc().stream()
                .map(v -> new VenueSummary(v.getId(), v.getName(), v.getCity(), counts.getOrDefault(v.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> listLanguages() {
        return movies.findDistinctLanguages();
    }

    // ---- management

    public GenreSummary createGenre(GenreRequest request) {
        String name = requireUnusedName(request.name(), byKey(genres.findAll(), Genre::getName), "genre");
        String category = Titles.clean(request.category());
        Genre genre = genres.save(new Genre(name, category != null ? category : Titles.genreCategory(name)));
        return new GenreSummary(genre.getId(), genre.getName(), genre.getCategory(), 0);
    }

    public GenreSummary updateGenre(Long id, GenreRequest request) {
        Genre genre = genres.findById(id).orElseThrow(() -> new NotFoundException("Genre", id));
        Map<String, Genre> others = byKey(genres.findAll().stream().filter(g -> !g.getId().equals(id)).toList(),
                Genre::getName);
        String name = requireUnusedName(request.name(), others, "genre");
        String category = Titles.clean(request.category());
        genre.setName(name);
        genre.setCategory(category != null ? category : Titles.genreCategory(name));
        return new GenreSummary(genre.getId(), genre.getName(), genre.getCategory(), movies.countByGenresId(id));
    }

    public void deleteGenre(Long id) {
        Genre genre = genres.findById(id).orElseThrow(() -> new NotFoundException("Genre", id));
        long used = movies.countByGenresId(id);
        if (used > 0) {
            throw new ConflictException("Genre '" + genre.getName() + "' is used by " + used
                    + " movie(s); remove it from those movies first");
        }
        genres.delete(genre);
    }

    public PlatformSummary createPlatform(PlatformRequest request) {
        String name = requireUnusedName(request.name(), byKey(platforms.findAll(), Platform::getName), "platform");
        Platform platform = platforms.save(new Platform(name, request.defaultMethod()));
        return new PlatformSummary(platform.getId(), platform.getName(), platform.getDefaultMethod(), 0);
    }

    public PlatformSummary updatePlatform(Long id, PlatformRequest request) {
        Platform platform = platforms.findById(id).orElseThrow(() -> new NotFoundException("Platform", id));
        Map<String, Platform> others = byKey(
                platforms.findAll().stream().filter(p -> !p.getId().equals(id)).toList(), Platform::getName);
        platform.setName(requireUnusedName(request.name(), others, "platform"));
        platform.setDefaultMethod(request.defaultMethod());
        return new PlatformSummary(platform.getId(), platform.getName(), platform.getDefaultMethod(),
                sessions.countByPlatformId(id));
    }

    public void deletePlatform(Long id) {
        Platform platform = platforms.findById(id).orElseThrow(() -> new NotFoundException("Platform", id));
        long used = sessions.countByPlatformId(id);
        if (used > 0) {
            throw new ConflictException("Platform '" + platform.getName() + "' is used by " + used + " viewing(s)");
        }
        platforms.delete(platform);
    }

    public VenueSummary createVenue(VenueRequest request) {
        String name = requireUnusedName(request.name(), byKey(venues.findAll(), Venue::getName), "venue");
        Venue venue = new Venue(name);
        venue.setCity(Titles.clean(request.city()));
        venue = venues.save(venue);
        return new VenueSummary(venue.getId(), venue.getName(), venue.getCity(), 0);
    }

    public VenueSummary updateVenue(Long id, VenueRequest request) {
        Venue venue = venues.findById(id).orElseThrow(() -> new NotFoundException("Venue", id));
        Map<String, Venue> others = byKey(
                venues.findAll().stream().filter(v -> !v.getId().equals(id)).toList(), Venue::getName);
        venue.setName(requireUnusedName(request.name(), others, "venue"));
        venue.setCity(Titles.clean(request.city()));
        return new VenueSummary(venue.getId(), venue.getName(), venue.getCity(), sessions.countByVenueId(id));
    }

    public void deleteVenue(Long id) {
        Venue venue = venues.findById(id).orElseThrow(() -> new NotFoundException("Venue", id));
        long used = sessions.countByVenueId(id);
        if (used > 0) {
            throw new ConflictException("Venue '" + venue.getName() + "' is used by " + used + " viewing(s)");
        }
        venues.delete(venue);
    }

    private static <T> String requireUnusedName(String raw, Map<String, T> existingByKey, String what) {
        String name = Titles.clean(raw);
        if (name == null) {
            throw new InvalidRequestException("A " + what + " needs a name");
        }
        if (existingByKey.containsKey(Titles.key(name))) {
            throw new ConflictException("A " + what + " called '" + name + "' already exists");
        }
        return name;
    }

    private static <T> Map<String, T> byKey(Collection<T> items, Function<T, String> name) {
        Map<String, T> map = new HashMap<>();
        for (T item : items) {
            map.putIfAbsent(Titles.key(name.apply(item)), item);
        }
        return map;
    }

    private Map<Long, Long> usageCounts(String sql) {
        Map<Long, Long> counts = new HashMap<>();
        jdbc.query(sql, rs -> {
            counts.put(rs.getLong("id"), rs.getLong("n"));
        });
        return counts;
    }
}
