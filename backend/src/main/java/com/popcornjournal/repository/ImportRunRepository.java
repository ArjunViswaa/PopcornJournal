package com.popcornjournal.repository;

import com.popcornjournal.entity.ImportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportRunRepository extends JpaRepository<ImportRun, Long> {

    List<ImportRun> findTop20ByOrderByStartedAtDesc();
}
