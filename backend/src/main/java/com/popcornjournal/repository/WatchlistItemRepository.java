package com.popcornjournal.repository;

import com.popcornjournal.entity.WatchlistItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    @EntityGraph(attributePaths = "genre")
    List<WatchlistItem> findAllByOrderByCreatedAtDescIdDesc();

    List<WatchlistItem> findByImportKeyIn(java.util.Collection<String> importKeys);
}
