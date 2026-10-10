package com.popcornjournal.repository;

import com.popcornjournal.entity.ViewingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ViewingSessionRepository extends JpaRepository<ViewingSession, Long> {

    @Query("""
            select s from ViewingSession s
            left join fetch s.platform
            left join fetch s.venue
            where s.movie.id = :movieId
            order by s.watchedOn desc nulls last, s.id desc
            """)
    List<ViewingSession> findForMovie(@Param("movieId") Long movieId);

    @Query("""
            select s from ViewingSession s
            join fetch s.movie
            left join fetch s.platform
            left join fetch s.venue
            where s.id = :id
            """)
    Optional<ViewingSession> findDetailedById(@Param("id") Long id);

    /** Diary listing, newest first with undated viewings last. */
    @Query(value = """
            select s from ViewingSession s
            join fetch s.movie
            left join fetch s.platform
            left join fetch s.venue
            order by s.watchedOn desc nulls last, s.id desc
            """,
            countQuery = "select count(s) from ViewingSession s")
    Page<ViewingSession> findDiary(Pageable pageable);

    @Query(value = """
            select s from ViewingSession s
            join fetch s.movie
            left join fetch s.platform
            left join fetch s.venue
            where s.watchedOn between :fromDate and :toDate
            order by s.watchedOn desc, s.id desc
            """,
            countQuery = "select count(s) from ViewingSession s where s.watchedOn between :fromDate and :toDate")
    Page<ViewingSession> findDiaryBetween(@Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate,
                                          Pageable pageable);

    @Query(value = """
            select s from ViewingSession s
            join fetch s.movie
            left join fetch s.platform
            left join fetch s.venue
            where s.watchedOn is null
            order by s.id desc
            """,
            countQuery = "select count(s) from ViewingSession s where s.watchedOn is null")
    Page<ViewingSession> findUndated(Pageable pageable);

    long countByPlatformId(Long platformId);

    long countByVenueId(Long venueId);
}
