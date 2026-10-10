package com.popcornjournal.repository;

import com.popcornjournal.entity.ImportSourceRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ImportSourceRowRepository extends JpaRepository<ImportSourceRow, Long> {

    /** Record keys are prefixed by sheet role, so they're matched regardless of the sheet's name. */
    List<ImportSourceRow> findByRecordKeyIn(Collection<String> recordKeys);

    List<ImportSourceRow> findByMovieIdOrderBySheetNameAscRowNumberAsc(Long movieId);
}
