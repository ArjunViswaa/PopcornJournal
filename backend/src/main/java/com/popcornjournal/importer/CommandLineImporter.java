package com.popcornjournal.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.popcornjournal.importer.ImportReport.Level;
import com.popcornjournal.importer.ImportReport.RowOutcome;
import com.popcornjournal.importer.ImportReport.RowStatus;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Command-line entry point, active only when --import.file is given:
 *
 * <pre>
 *   --import.file=../data/import/Popcorn_Sheet.xlsx            dry run (default)
 *   --import.file=... --import.commit=true                     write to the database
 *   --import.report-dir=../data/import/reports                 also save the JSON report
 * </pre>
 *
 * The application exits when the import finishes.
 */
@Component
@ConditionalOnProperty(name = "import.file")
public class CommandLineImporter implements ApplicationRunner {

    private final SpreadsheetImportService importService;
    private final Environment environment;
    private final ApplicationContext context;
    private final ObjectMapper objectMapper;

    public CommandLineImporter(SpreadsheetImportService importService, Environment environment,
                               ApplicationContext context, ObjectMapper objectMapper) {
        this.importService = importService;
        this.environment = environment;
        this.context = context;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = execute(System.out);
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    int execute(PrintStream out) {
        Path file = Path.of(environment.getRequiredProperty("import.file")).toAbsolutePath().normalize();
        boolean commit = environment.getProperty("import.commit", Boolean.class, false);
        String reportDir = environment.getProperty("import.report-dir");

        if (!Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase().endsWith(".xlsx")) {
            out.println("ERROR: " + file + " is not an .xlsx file");
            return 2;
        }
        try {
            byte[] content = Files.readAllBytes(file);
            ImportReport report = commit
                    ? importService.commit(content, file.getFileName().toString(), "CLI")
                    : importService.preview(content, file.getFileName().toString());
            print(report, out);
            if (reportDir != null) {
                Path saved = saveReport(report, Path.of(reportDir));
                out.println("Report saved to " + saved);
            }
            return report.totals().errors() > 0 ? 1 : 0;
        } catch (IOException | RuntimeException ex) {
            out.println("ERROR: import failed, nothing was written: " + ex.getMessage());
            return 2;
        }
    }

    private Path saveReport(ImportReport report, Path dir) throws IOException {
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path target = dir.resolve("import-" + (report.dryRun() ? "dry-run-" : "commit-") + stamp + ".json");
        objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValue(target.toFile(), report);
        return target;
    }

    static void print(ImportReport report, PrintStream out) {
        out.println();
        out.println(report.dryRun()
                ? "=== DRY RUN: nothing has been written. Re-run with --import.commit=true to import. ==="
                : "=== IMPORT COMMITTED (run #" + report.importRunId() + ") ===");
        out.println("File: " + report.fileName() + "  sha256 " + report.sha256().substring(0, 12) + "...");
        report.sheets().forEach(sheet -> out.printf("Sheet %-18s %-9s %4d rows, %d blank rows skipped%n",
                sheet.name(), sheet.role(), sheet.dataRows(), sheet.blankRowsSkipped()));
        report.fileMessages().forEach(m -> out.println("  " + m.level() + ": " + m.text()));

        var t = report.totals();
        out.println();
        out.printf("Rows read %d | new movies %d | new viewings %d | watchlist %d | repeat rows merged %d%n",
                t.rowsRead(), t.newMovies(), t.newViewings(), t.newWatchlistItems(), t.mergedDuplicateRows());
        out.printf("Already imported %d | deleted in app %d | conflicts %d | errors %d | warnings %d%n",
                t.alreadyImported(), t.deletedInApp(), t.conflicts(), t.errors(), t.warnings());
        if (!report.newGenres().isEmpty()) out.println("New genres:    " + String.join(", ", report.newGenres()));
        if (!report.newPlatforms().isEmpty()) out.println("New platforms: " + String.join(", ", report.newPlatforms()));
        if (!report.newVenues().isEmpty()) out.println("New venues:    " + String.join(", ", report.newVenues()));
        report.mergedSpellings().forEach(m -> out.println("Merged " + m.kind() + " spellings "
                + m.spellings() + " -> " + m.storedAs()));

        out.println();
        out.println("Rows needing attention:");
        boolean any = false;
        for (RowOutcome row : report.rows()) {
            boolean notable = row.status() != RowStatus.NEW && row.status() != RowStatus.ALREADY_IMPORTED
                    || row.messages().stream().anyMatch(m -> m.level() != Level.INFO);
            if (!notable) {
                continue;
            }
            any = true;
            out.printf("  %s row %d \"%s\" [%s]%n", row.sheet(), row.row(), row.title(), row.status());
            row.messages().forEach(m -> out.println("      " + m.level() + (m.field() == null ? "" : " " + m.field())
                    + ": " + m.text()));
        }
        if (!any) {
            out.println("  none");
        }
    }
}
