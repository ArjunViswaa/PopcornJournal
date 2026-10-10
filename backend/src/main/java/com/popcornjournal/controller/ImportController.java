package com.popcornjournal.controller;

import com.popcornjournal.entity.ImportRun;
import com.popcornjournal.exception.InvalidRequestException;
import com.popcornjournal.importer.ImportReport;
import com.popcornjournal.importer.SpreadsheetImportService;
import com.popcornjournal.repository.ImportRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Spreadsheet import")
public class ImportController {

    private final SpreadsheetImportService importService;
    private final ImportRunRepository runs;

    public ImportController(SpreadsheetImportService importService, ImportRunRepository runs) {
        this.importService = importService;
        this.runs = runs;
    }

    public record ImportRunDto(Long id, String fileName, String triggeredBy, Instant startedAt,
                               Instant finishedAt, Map<String, Object> summary) {
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Dry run: validate a spreadsheet and report what would be imported")
    public ImportReport preview(@RequestParam("file") MultipartFile file) {
        return importService.preview(readXlsx(file), file.getOriginalFilename());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import a spreadsheet. Rows imported earlier are left untouched.")
    public ImportReport commit(@RequestParam("file") MultipartFile file) {
        return importService.commit(readXlsx(file), file.getOriginalFilename(), "WEB");
    }

    @GetMapping
    @Operation(summary = "Recent committed imports")
    public List<ImportRunDto> history() {
        return runs.findTop20ByOrderByStartedAtDesc().stream().map(ImportController::toDto).toList();
    }

    private static ImportRunDto toDto(ImportRun run) {
        return new ImportRunDto(run.getId(), run.getSourceFileName(), run.getTriggeredBy(),
                run.getStartedAt(), run.getFinishedAt(), run.getSummary());
    }

    /** Size is capped by spring.servlet.multipart; content is checked again by the reader. */
    private static byte[] readXlsx(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (file.isEmpty()) {
            throw new InvalidRequestException("The uploaded file is empty");
        }
        if (!name.endsWith(".xlsx")) {
            throw new InvalidRequestException("Only .xlsx spreadsheets can be imported");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new InvalidRequestException("The upload could not be read", ex);
        }
    }
}
