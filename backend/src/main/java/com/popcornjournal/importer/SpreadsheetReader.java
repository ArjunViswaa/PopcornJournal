package com.popcornjournal.importer;

import com.popcornjournal.exception.InvalidRequestException;
import com.popcornjournal.support.Titles;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidation;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STDataValidationType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads every sheet of a workbook into header-keyed rows. Nothing is interpreted here
 * beyond the cell type, so the raw values can be stored exactly as they were.
 */
@Component
public class SpreadsheetReader {

    /** A sheet's header row and its non-blank data rows. */
    public record SheetData(String name, List<String> headers, List<SheetRow> rows, int blankRows,
                            Map<String, List<String>> dropdownOptions) {
    }

    /**
     * One data row. values maps header -> String, Double, Boolean or LocalDateTime.
     * Cells under a blank header are kept under their column letter, e.g. "column L".
     */
    public record SheetRow(int rowNumber, Map<String, Object> values) {
    }

    public List<SheetData> read(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') {
            throw new InvalidRequestException("The file is not an .xlsx workbook");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            List<SheetData> sheets = new ArrayList<>();
            for (Sheet sheet : workbook) {
                sheets.add(readSheet(sheet));
            }
            return sheets;
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof InvalidRequestException invalid) {
                throw invalid;
            }
            throw new InvalidRequestException("The workbook could not be read (" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage() + ")", ex);
        }
    }

    private SheetData readSheet(Sheet sheet) {
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        List<String> headers = new ArrayList<>();
        int width = headerRow == null ? 0 : Math.max(headerRow.getLastCellNum(), 0);
        for (int c = 0; c < width; c++) {
            Object value = cellValue(headerRow.getCell(c));
            headers.add(value == null ? null : Titles.clean(value.toString()));
        }

        List<SheetRow> rows = new ArrayList<>();
        int firstData = sheet.getFirstRowNum() + 1;
        for (int r = firstData; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            Map<String, Object> values = new LinkedHashMap<>();
            if (row != null) {
                int last = Math.max(row.getLastCellNum(), 0);
                for (int c = 0; c < last; c++) {
                    Object value = cellValue(row.getCell(c));
                    if (value == null) {
                        continue;
                    }
                    String header = c < headers.size() && headers.get(c) != null
                            ? headers.get(c)
                            : "column " + CellReference.convertNumToColString(c);
                    values.put(header, value);
                }
            }
            if (!values.isEmpty()) {
                rows.add(new SheetRow(r + 1, values));
            }
        }
        // formatted-but-empty rows after the data are not interesting; count only gaps inside it
        int lastDataRow = rows.isEmpty() ? firstData : rows.getLast().rowNumber();
        int blankBetween = Math.max(0, lastDataRow - firstData - rows.size());
        return new SheetData(sheet.getSheetName(), headers, rows, blankBetween, dropdowns(sheet, headers));
    }

    /**
     * List-type data validations by header, e.g. the Genre dropdown's allowed values.
     * Read from the sheet XML because POI refuses to build constraints for lists longer
     * than Excel's 255-character limit, which the source sheet works around with "&".
     */
    private Map<String, List<String>> dropdowns(Sheet sheet, List<String> headers) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        if (!(sheet instanceof XSSFSheet xssf) || !xssf.getCTWorksheet().isSetDataValidations()) {
            return options;
        }
        for (CTDataValidation validation : xssf.getCTWorksheet().getDataValidations().getDataValidationArray()) {
            if (validation.getType() != STDataValidationType.LIST) {
                continue;
            }
            List<String> values = listValues(validation.getFormula1());
            if (values.isEmpty() || validation.getSqref() == null) {
                continue;
            }
            for (Object ref : validation.getSqref()) {
                CellRangeAddress range = CellRangeAddress.valueOf(ref.toString());
                for (int c = range.getFirstColumn(); c <= range.getLastColumn() && c < headers.size(); c++) {
                    if (headers.get(c) != null) {
                        options.putIfAbsent(headers.get(c), values);
                    }
                }
            }
        }
        return options;
    }

    /** Long lists are stored as concatenated string literals: "a,b,c"&"d,e". */
    static List<String> listValues(String formula) {
        if (formula == null || !formula.strip().startsWith("\"")) {
            return List.of();
        }
        String joined = formula.strip().replaceAll("\"\\s*&\\s*\"", "").replace("\"", "");
        return cleanList(Arrays.asList(joined.split(",")));
    }

    private static List<String> cleanList(List<String> values) {
        return values.stream().map(Titles::clean).filter(v -> v != null).distinct().toList();
    }

    private static Object cellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> {
                String text = cell.getStringCellValue();
                yield text == null || text.isBlank() ? null : text;
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime date = cell.getLocalDateTimeCellValue();
                    yield date;
                }
                yield cell.getNumericCellValue();
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            default -> null;
        };
    }
}
