package com.popcornjournal.repository;

import com.popcornjournal.entity.Movie;
import com.popcornjournal.entity.MovieFormat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    @EntityGraph(attributePaths = "genres")
    Optional<Movie> findWithGenresById(Long id);

    List<Movie> findByImportKeyIn(Collection<String> importKeys);

    List<Movie> findByTitleKeyIn(Collection<String> titleKeys);

    /** Mirrors the ux_movies_identity unique index. */
    @Query("""
            select m from Movie m
            where m.titleKey = :titleKey
              and m.format = :format
              and coalesce(m.releaseYear, 0) = coalesce(:releaseYear, 0)
              and lower(coalesce(m.language, '')) = lower(coalesce(:language, ''))
            """)
    List<Movie> findIdentityMatches(@Param("titleKey") String titleKey,
                                    @Param("format") MovieFormat format,
                                    @Param("releaseYear") Integer releaseYear,
                                    @Param("language") String language);

    List<Movie> findTop10ByTitleKeyOrderByReleaseYearAsc(String titleKey);

    @Query("select distinct m.language from Movie m where m.language is not null order by m.language")
    List<String> findDistinctLanguages();

    long countByGenresId(Long genreId);
}
