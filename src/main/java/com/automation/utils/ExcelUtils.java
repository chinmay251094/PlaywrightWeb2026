package com.automation.utils;

import com.automation.constants.AppConstants;
import com.automation.exceptions.FrameworkException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Generic Excel test data reader backed by Apache POI.
 *
 * Data contract:
 *   - Row 0 of each sheet contains column headers.
 *   - Each subsequent row is one test case.
 *   - Rows with an empty first cell are skipped (acts as a blank-row guard).
 *   - A "RunFlag" column (optional) — when present, rows where RunFlag != "Y"
 *     are excluded from the returned dataset, enabling selective execution.
 *
 * Return format: Object[][]  where each Object[] is a single-element array
 * containing a Map<String,String> so test methods receive named columns:
 *
 *   @Test(dataProvider = "loginData")
 *   public void testLogin(Map<String, String> data) {
 *       String user = data.get("username");
 *   }
 */
public final class ExcelUtils {

    private static final Logger log = LogManager.getLogger(ExcelUtils.class);
    private static final String RUN_FLAG_COLUMN = "RunFlag";
    private static final String RUN_FLAG_YES    = "Y";

    private ExcelUtils() { /* static utility */ }

    // ─── Single-sheet DataProvider entry point ────────────────────────────────

    /**
     * Returns all enabled rows from the unified "TestSuite" sheet whose
     * {@code testMethod} column matches {@code methodName}.
     *
     * This is the only method the DataProvider needs to call.
     * RunFlag=N rows are silently dropped before returning.
     *
     * @param methodName exact Java method name (from {@code Method.getName()})
     */
    public static Object[][] getDataForMethod(String methodName) {
        log.info("Loading test data for method '{}' from sheet '{}'.",
                methodName, AppConstants.TESTSUITE_SHEET);

        List<Map<String, String>> all = readSheet(AppConstants.TEST_DATA_PATH,
                                                   AppConstants.TESTSUITE_SHEET);
        List<Map<String, String>> filtered = all.stream()
                .filter(row -> methodName.equals(row.get("testMethod")))
                .toList();

        log.info("Found {} executable row(s) for '{}'.", filtered.size(), methodName);

        if (filtered.isEmpty()) {
            log.warn("No rows found for method '{}' — test will be skipped by TestNG.", methodName);
        }

        Object[][] result = new Object[filtered.size()][1];
        for (int i = 0; i < filtered.size(); i++) {
            result[i][0] = filtered.get(i);
        }
        return result;
    }

    // ─── Primary public method ────────────────────────────────────────────────

    /**
     * Reads all executable rows from the given sheet and returns them as
     * a TestNG-compatible Object[][] where each row is Map&lt;String,String&gt;.
     *
     * @param sheetName exact sheet name in TestData.xlsx
     */
    public static Object[][] getSheetData(String sheetName) {
        return getSheetData(AppConstants.TEST_DATA_PATH, sheetName);
    }

    public static Object[][] getSheetData(String filePath, String sheetName) {
        log.info("Loading test data — file: '{}', sheet: '{}'", filePath, sheetName);
        List<Map<String, String>> rows = readSheet(filePath, sheetName);
        log.info("Loaded {} executable row(s) from sheet '{}'.", rows.size(), sheetName);

        Object[][] result = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            result[i][0] = rows.get(i);
        }
        return result;
    }

    /**
     * Returns only rows whose "testCaseId" column matches the supplied ID.
     * Useful when multiple test methods share a sheet but need specific rows.
     */
    public static Object[][] getRowsByTestCaseId(String sheetName, String testCaseId) {
        Object[][] all = getSheetData(sheetName);
        List<Object[]> filtered = new ArrayList<>();
        for (Object[] row : all) {
            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) row[0];
            if (testCaseId.equalsIgnoreCase(data.get("testCaseId"))) {
                filtered.add(row);
            }
        }
        return filtered.toArray(new Object[0][]);
    }

    /**
     * Returns a single row as a Map for use in non-DataProvider contexts
     * (e.g., @BeforeMethod setup reading user credentials).
     */
    public static Map<String, String> getRowData(String sheetName, int rowIndex) {
        List<Map<String, String>> rows = readSheet(AppConstants.TEST_DATA_PATH, sheetName);
        if (rowIndex >= rows.size()) {
            throw new FrameworkException(
                    "Row index " + rowIndex + " exceeds available rows (" + rows.size() + ") in sheet: " + sheetName);
        }
        return rows.get(rowIndex);
    }

    // ─── Core reader ─────────────────────────────────────────────────────────

    private static List<Map<String, String>> readSheet(String filePath, String sheetName) {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new FrameworkException(
                        "Sheet '" + sheetName + "' not found in: " + filePath);
            }

            List<String> headers = extractHeaders(sheet);
            if (headers.isEmpty()) {
                throw new FrameworkException("Sheet '" + sheetName + "' has no header row.");
            }

            List<Map<String, String>> rows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();

            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // Skip blank rows (first cell empty)
                Cell firstCell = row.getCell(0);
                if (firstCell == null || getCellValue(firstCell).isBlank()) continue;

                Map<String, String> rowData = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    rowData.put(headers.get(c), cell != null ? getCellValue(cell) : "");
                }

                // Skip rows where RunFlag column is present and not "Y"
                if (rowData.containsKey(RUN_FLAG_COLUMN)
                        && !RUN_FLAG_YES.equalsIgnoreCase(rowData.get(RUN_FLAG_COLUMN))) {
                    log.debug("Skipping row {} (RunFlag != Y).", r);
                    continue;
                }

                rows.add(Collections.unmodifiableMap(rowData));
            }

            return rows;

        } catch (IOException e) {
            throw new FrameworkException("Failed to read Excel file: " + filePath, e);
        }
    }

    private static List<String> extractHeaders(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return Collections.emptyList();

        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            String header = getCellValue(cell).trim();
            if (!header.isEmpty()) {
                headers.add(header);
            }
        }
        return headers;
    }

    /** Reads a cell value as String regardless of the underlying cell type. */
    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}
