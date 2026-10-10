package com.popcornjournal.repository;

import com.popcornjournal.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformRepository extends JpaRepository<Platform, Long> {

    List<Platform> findAllByOrderByNameAsc();
}
