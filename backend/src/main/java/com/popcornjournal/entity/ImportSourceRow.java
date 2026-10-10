package com.popcornjournal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/** The original cell values of an imported spreadsheet row, kept for traceability. */
@Entity
@Table(name = "import_source_rows")
public class ImportSourceRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sheet_name", nullable = false, length = 60)
    private String sheetName;

    @Column(name = "record_key", nullable = false, length = 400)
    private String recordKey;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_values", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawValues;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_item_id")
    private WatchlistItem watchlistItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "first_import_run_id", nullable = false, updatable = false)
    private ImportRun firstImportRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_import_run_id", nullable = false)
    private ImportRun lastImportRun;

    protected ImportSourceRow() {
    }

    public ImportSourceRow(String sheetName, String recordKey, int rowNumber,
                           Map<String, Object> rawValues, ImportRun run) {
        this.sheetName = sheetName;
        this.recordKey = recordKey;
        this.rowNumber = rowNumber;
        this.rawValues = rawValues;
        this.firstImportRun = run;
        this.lastImportRun = run;
    }

    /**
     * A later import saw the same record again. Only the location is refreshed;
     * the raw values stay as they were when the record was first imported.
     */
    public void seenAgain(int rowNumber, ImportRun run) {
        this.rowNumber = rowNumber;
        this.lastImportRun = run;
    }

    public Long getId() {
        return id;
    }

    public String getSheetName() {
        return sheetName;
    }

    public String getRecordKey() {
        return recordKey;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public Map<String, Object> getRawValues() {
        return rawValues;
    }

    public Movie getMovie() {
        return movie;
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    public WatchlistItem getWatchlistItem() {
        return watchlistItem;
    }

    public void setWatchlistItem(WatchlistItem watchlistItem) {
        this.watchlistItem = watchlistItem;
    }
}
