package com.popcornjournal.repository;

import com.popcornjournal.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    List<Venue> findAllByOrderByNameAsc();
}
