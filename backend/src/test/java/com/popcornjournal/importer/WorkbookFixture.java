package com.popcornjournal.importer;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidation;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidations;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STDataValidationType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds small workbooks shaped like the real journal spreadsheet. Test data only;
 * none of it comes from the user's file.
 */
public final class WorkbookFixture {

    public static final String[] WATCH_HEADERS = {"Movie / Series Name", "Description", "Genre", "Rating",
            "Review", "Language", "Format", "Platform", "Year", "Watch Date", "Theatre"};

    private final List<Object[]> watched = new ArrayList<>();
    private final List<Object[]> recommendations = new ArrayList<>();
    private String genreDropdown;

    public static WorkbookFixture create() {
        return new WorkbookFixture();
    }

    /** Values in WATCH_HEADERS order; LocalDate becomes a date-formatted cell, null a blank cell. */
    public WorkbookFixture watched(Object... values) {
        watched.add(values);
        return this;
    }

    public WorkbookFixture recommendation(String title, String recommendedBy, String genre) {
        recommendations.add(new Object[]{title, recommendedBy, genre});
        return this;
    }

    /** A list validation written the way Excel stores long lists: "a,b"&"c,d". */
    public WorkbookFixture genreDropdown(String formula) {
        this.genreDropdown = formula;
        return this;
    }

    /** The standard fixture used by most import tests. */
    public static WorkbookFixture sample() {
        return create()
                .watched("Midnight Signal", "A radio host hears a voice.", "Thriller - Mystery", 9.0, "Tense.",
                        "English", "Movie", "Netflix", 2024.0, LocalDate.of(2026, 7, 1), "-")
                .watched("Harbour Lights", "Fishermen strike.", "Drama - Social", 7.0, "Slow but honest.",
                        "Tamil", "Movie", "Prime Video", 2023.0, LocalDate.of(2026, 6, 20), "Star Cinemas - Chennai")
                .watched("Paper Kites", "Kids build kites.", "Feel Good", "Infinity", "Perfect.",
                        "Malayalam", "Movie", "jio Hotstar", 2019.0, null, "-")
                .watched("Office Hours", "A sitcom.", "Comedy - Situation", 8.0, "Comfort show.",
                        "English", "Series", "Jio Hotstar", 2015.0, null, "-")
                .watched(2018.0, "Floods hit a state.", "Drama - Real Life", 10.0, "Moving.",
                        "Malayalam", "Movie", null, 2023.0, null, "Star Cinemas - Chennai")
                .watched("Unwatched Maybe", null, "Action", null, null, "Korean", "Movie", null, null, null, "-")
                .watched("Midnight Signal", "Rewatch.", "Thriller - Mystery", 8.0, "Second time less tense.",
                        "English", "Movie", "Netflix", 2024.0, LocalDate.of(2026, 7, 10), "-")
                .watched("Paper Kites", "The 1990 original.", "Feel Good", 6.0, "Dated.",
                        "Malayalam", "Movie", "YTS", 1990.0, null, "-")
                .recommendation("Code Seven", "A friend", "Action - Comedy")
                .genreDropdown("\"Action,Feel Good,Thriller - Mystery\"&\",Comedy - Dark\"");
    }

    public byte[] toBytes() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CreationHelper helper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            XSSFSheet sheet = workbook.createSheet("Watch_Sheet");
            writeRow(sheet, 0, WATCH_HEADERS, dateStyle);
            for (int i = 0; i < watched.size(); i++) {
                writeRow(sheet, i + 1, watched.get(i), dateStyle);
            }
            if (genreDropdown != null) {
                addListValidation(sheet, "C2:C500", genreDropdown);
            }

            Sheet recs = workbook.createSheet("Recommendations");
            writeRow(recs, 0, new Object[]{"Movie / Series Name", "Recommended By", "Genre"}, dateStyle);
            for (int i = 0; i < recommendations.size(); i++) {
                // leave a blank row first, like the real sheet
                writeRow(recs, i + 2, recommendations.get(i), dateStyle);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void writeRow(Sheet sheet, int index, Object[] values, CellStyle dateStyle) {
        Row row = sheet.createRow(index);
        for (int c = 0; c < values.length; c++) {
            Object value = values[c];
            if (value == null) {
                continue;
            }
            var cell = row.createCell(c);
            switch (value) {
                case Double number -> cell.setCellValue(number);
                case LocalDate date -> {
                    cell.setCellValue(date);
                    cell.setCellStyle(dateStyle);
                }
                default -> cell.setCellValue(value.toString());
            }
        }
    }

    private static void addListValidation(XSSFSheet sheet, String sqref, String formula) {
        // POI's API rejects lists over 255 characters, so write the XML directly like Excel does
        CTDataValidations validations = sheet.getCTWorksheet().isSetDataValidations()
                ? sheet.getCTWorksheet().getDataValidations()
                : sheet.getCTWorksheet().addNewDataValidations();
        CTDataValidation validation = validations.addNewDataValidation();
        validation.setType(STDataValidationType.LIST);
        validation.setSqref(List.of(sqref));
        validation.setFormula1(formula);
        validations.setCount(validations.sizeOfDataValidationArray());
    }
}
