package com.popcornjournal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** A committed spreadsheet import. Dry runs are reported but never stored. */
@Entity
@Table(name = "import_runs")
public class ImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_file_name", nullable = false)
    private String sourceFileName;

    @Column(name = "source_sha256", nullable = false, length = 64)
    private String sourceSha256;

    @Column(name = "triggered_by", nullable = false, length = 10)
    private String triggeredBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> summary;

    protected ImportRun() {
    }

    public ImportRun(String sourceFileName, String sourceSha256, String triggeredBy, Instant startedAt) {
        this.sourceFileName = sourceFileName;
        this.sourceSha256 = sourceSha256;
        this.triggeredBy = triggeredBy;
        this.startedAt = startedAt;
        this.finishedAt = startedAt;
        this.summary = Map.of();
    }

    public void finish(Map<String, Object> summary) {
        this.summary = summary;
        this.finishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getSourceSha256() {
        return sourceSha256;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }
}
