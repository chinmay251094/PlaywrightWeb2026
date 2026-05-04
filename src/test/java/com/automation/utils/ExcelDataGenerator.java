package com.automation.utils;

import com.automation.constants.AppConstants;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generates TestData.xlsx with a single "TestSuite" sheet.
 *
 * Run once before your first test execution:
 *   mvn exec:java -D exec.mainClass=com.automation.utils.ExcelDataGenerator
 *
 * Column order (18 columns, same sequence in every row):
 *   [0]  testCaseId          — unique ID shown in the HTML report
 *   [1]  testMethod          — exact Java method name; routes the row to the correct test
 *   [2]  description         — "Test Scenario" text in the HTML report
 *   [3]  browser             — chromium | firefox | webkit
 *   [4]  headless            — true | false
 *   [5]  expectedResult      — "Expected Result" text in the HTML report
 *   [6]  RunFlag             — Y = execute, N = skip
 *   [7]  username            — login / account tests
 *   [8]  password            — login / account tests
 *   [9]  loginExpected       — success | error (login-test branching)
 *   [10] searchTerm          — search tests
 *   [11] expectedProduct     — search tests
 *   [12] expectSearchResults — true | false (no-results scenario)
 *   [13] productName         — cart tests
 *   [14] quantity            — cart quantity-update test
 *   [15] url                 — navigation URL test (relative, e.g. /store/)
 *   [16] expectedTitle       — navigation title assertion
 *   [17] expectedUrlFragment — navigation URL-fragment assertion
 *
 * Toggle execution: change RunFlag to N to skip a row without deleting it.
 * Change browser/headless per row to run that case on a different engine.
 */
public class ExcelDataGenerator {

    private static final int    RUN_FLAG_COL = 6;

    private static final String[] HEADERS = {
        "testCaseId", "testMethod", "description", "browser", "headless",
        "expectedResult", "RunFlag",
        "username", "password", "loginExpected",
        "searchTerm", "expectedProduct", "expectSearchResults",
        "productName", "quantity",
        "url", "expectedTitle", "expectedUrlFragment"
    };

    /**
     * All test data in one flat table.
     * Columns follow the HEADERS order above.
     * Blank cells use empty string "".
     *
     * To add a new test case: append a row here with RunFlag = "Y".
     * To skip a test case:    change its RunFlag cell from "Y" to "N".
     */
    private static final String[][] TEST_DATA = {

        // ── LOGIN TESTS ──────────────────────────────────────────────────────
        //  id              method                      description                                        browser    hl      expected                                    flag  user                     pass              loginExp  search  expProd  expRes  product  qty  url  title  frag
        { "TC_LOGIN_001", "testLogin",                  "Valid login with correct credentials",             "chromium","false","User dashboard is visible after login",     "Y", "demouser",              "demopass123",    "success","",     "",      "",     "",      "",  "",  "",    ""   },
        { "TC_LOGIN_002", "testLogin",                  "Login with incorrect password shows error",        "firefox", "true", "Error message is displayed",               "Y", "demouser",              "wrongpassword123","error", "",     "",      "",     "",      "",  "",  "",    ""   },
        { "TC_LOGIN_003", "testLogin",                  "Login with non-existent username shows error",     "chromium","true", "Error message is displayed",               "Y", "ghost_xyz@test.com",    "anypassword",    "error",  "",     "",      "",     "",      "",  "",  "",    ""   },
        { "TC_LOGIN_004", "testLogin",                  "Login with empty username (disabled)",             "chromium","true", "Browser validation prevents submission",    "N", "",                      "demopass123",    "error",  "",     "",      "",     "",      "",  "",  "",    ""   },
        { "TC_LOGIN_005", "testLoginPageElementsVisible","Login and register forms visible on /account/",   "chromium","false","Both form sections are rendered",          "Y", "",                      "",               "",       "",     "",      "",     "",      "",  "",  "",    ""   },
        { "TC_LOGIN_006", "testLogout",                 "Logged-in user can log out and session ends",      "chromium","false","Login form reappears after logout",         "Y", "demouser",              "demopass123",    "",       "",     "",      "",     "",      "",  "",  "",    ""   },

        // ── SEARCH TESTS ─────────────────────────────────────────────────────
        { "TC_SEARCH_001","testSearch",                 "Search 'Blue' returns Blue Shoes in results",      "chromium","true", "Blue Shoes appears in search results",      "Y", "",  "","", "Blue",                    "Blue Shoes","true", "",      "",  "",  "",    ""   },
        { "TC_SEARCH_002","testSearch",                 "Search 'Blue' returns results on Firefox",         "firefox", "true", "At least one product returned",            "Y", "",  "","", "Blue",                    "",          "true", "",      "",  "",  "",    ""   },
        { "TC_SEARCH_003","testSearch",                 "Search for nonsense term returns no results",       "chromium","true", "No products found notice is displayed",    "Y", "",  "","", "xyznonexistentproduct999","",          "false","",      "",  "",  "",    ""   },
        { "TC_SEARCH_004","testSearchFromStorePage",    "Search from store page returns results",           "chromium","false","Products load and URL contains search query","Y","",  "","", "Blue",                    "",          "true", "",      "",  "",  "",    ""   },
        { "TC_SEARCH_005","testStorePageLoadsWithProducts","Store page shows products and result count",    "chromium","true", "At least one product and count text visible","Y","",  "","", "",                        "",          "",     "",      "",  "",  "",    ""   },

        // ── CART TESTS ───────────────────────────────────────────────────────
        { "TC_CART_001",  "testAddProductToCart",       "Add Blue Shoes to cart (Chromium)",                "chromium","false","Cart contains Blue Shoes with total",       "Y", "",  "","", "",  "","", "Blue Shoes","1",  "",  "",    ""   },
        { "TC_CART_002",  "testAddProductToCart",       "Add Blue Shoes to cart (Firefox)",                 "firefox", "true", "Cart contains Blue Shoes with total",       "Y", "",  "","", "",  "","", "Blue Shoes","1",  "",  "",    ""   },
        { "TC_CART_003",  "testRemoveProductFromCart",  "Remove added product; verify cart is empty",       "chromium","true", "Cart is empty after item removal",          "Y", "",  "","", "",  "","", "Blue Shoes","",   "",  "",    ""   },
        { "TC_CART_004",  "testUpdateCartQuantity",     "Update cart quantity to 2; verify change",         "chromium","true", "Cart quantity shows 2 after update",        "Y", "",  "","", "",  "","", "Blue Shoes","2",  "",  "",    ""   },
        { "TC_CART_005",  "testProceedToCheckout",      "Proceed to checkout from populated cart",          "chromium","false","Checkout page loads (/checkout/ in URL)",   "Y", "",  "","", "",  "","", "Blue Shoes","",   "",  "",    ""   },

        // ── NAVIGATION TESTS ─────────────────────────────────────────────────
        { "TC_NAV_001",   "testPageNavigation",         "Home page title and URL are correct",              "chromium","true", "Title has AskOmDch and URL is /",           "Y", "",  "","", "",  "","", "",          "",   "/",         "AskOmDch",  "/"          },
        { "TC_NAV_002",   "testPageNavigation",         "Store page URL is correct",                        "chromium","true", "URL has /store/",                           "Y", "",  "","", "",  "","", "",          "",   "/store/",   "",          "/store/"    },
        { "TC_NAV_003",   "testPageNavigation",         "Account page URL is correct",                      "chromium","true", "URL has /account/",                         "Y", "",  "","", "",  "","", "",          "",   "/account/", "",          "/account/"  },
        { "TC_NAV_004",   "testPageNavigation",         "Cart page title and URL are correct",              "chromium","true", "Title has Cart and URL has /cart/",          "Y", "",  "","", "",  "","", "",          "",   "/cart/",    "Cart",      "/cart/"     },
        { "TC_NAV_005",   "testHomePageLoads",          "Home page loads with visible branding",            "chromium","false","Home page loaded with non-empty title",      "Y", "",  "","", "",  "","", "",          "",   "",          "",          ""           },
        { "TC_NAV_006",   "testStoreNavLink",           "Store nav link opens store listing",               "chromium","false","URL has /store/ and products are shown",     "Y", "",  "","", "",  "","", "",          "",   "",          "",          ""           },
        { "TC_NAV_007",   "testAccountNavLink",         "Account nav link opens login/account page",        "chromium","false","URL has /account/ and title reflects account","Y","",  "","", "",  "","", "",          "",   "",          "",          ""           },
        { "TC_NAV_008",   "testBrowserBackNavigation",  "Browser back returns to previous page",            "chromium","true", "URL reverts to home after back navigation",  "Y", "",  "","", "",  "","", "",          "",   "",          "",          ""           },

        // ── ACCOUNT TESTS ────────────────────────────────────────────────────
        { "TC_ACC_001",   "testAccountDashboardVisible","My Account dashboard visible after login",         "chromium","false","Dashboard sidebar and welcome message shown", "Y","demouser","demopass123","", "","","", "","", "","",""   },
        { "TC_ACC_002",   "testOrdersTabNavigation",    "Orders tab updates URL to /orders/",               "chromium","false","URL contains /orders/ after click",           "Y","demouser","demopass123","", "","","", "","", "","",""   },
        { "TC_ACC_003",   "testAddressesTabNavigation", "Addresses tab updates URL to /edit-address/",      "chromium","false","URL contains /edit-address/ after click",     "Y","demouser","demopass123","", "","","", "","", "","",""   },
        { "TC_ACC_004",   "testAccountDetailsPageLoads","Account Details shows pre-filled email",           "chromium","false","Email field pre-populated with user's email", "Y","demouser","demopass123","", "","","", "","", "","",""   },
        { "TC_ACC_005",   "testLogoutFromAccountPage",  "Logout from My Account ends the session",          "chromium","false","Login form reappears after logout",           "Y","demouser","demopass123","", "","","", "","", "","",""   },
    };

    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        generateTestData();
    }

    /**
     * Writes the entire TestSuite sheet in one pass:
     *   1. Create workbook + sheet
     *   2. Write styled header row from HEADERS
     *   3. Iterate TEST_DATA — one Excel row per entry; RunFlag cell is colour-coded
     *   4. Auto-size columns and save
     */
    public static void generateTestData() throws IOException {
        Files.createDirectories(Paths.get("src/test/resources/testdata/"));

        try (Workbook wb  = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(AppConstants.TEST_DATA_PATH)) {

            Sheet      sheet   = wb.createSheet(AppConstants.TESTSUITE_SHEET);
            CellStyle  hStyle  = headerStyle(wb);
            CellStyle  yStyle  = flagStyle(wb, IndexedColors.LIGHT_GREEN);
            CellStyle  nStyle  = flagStyle(wb, IndexedColors.ROSE);

            // Header row
            Row hdr = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = hdr.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(hStyle);
            }

            // Data rows — single loop over the flat TEST_DATA table
            for (int r = 0; r < TEST_DATA.length; r++) {
                String[] src     = TEST_DATA[r];
                Row      dataRow = sheet.createRow(r + 1);
                boolean  runYes  = "Y".equalsIgnoreCase(src[RUN_FLAG_COL]);

                for (int c = 0; c < src.length; c++) {
                    Cell cell = dataRow.createCell(c);
                    cell.setCellValue(src[c] != null ? src[c] : "");
                    if (c == RUN_FLAG_COL) cell.setCellStyle(runYes ? yStyle : nStyle);
                }
            }

            for (int c = 0; c < HEADERS.length; c++) sheet.autoSizeColumn(c);

            wb.write(fos);
        }

        System.out.printf("TestData.xlsx written → %s  (%d rows)%n",
                AppConstants.TEST_DATA_PATH, TEST_DATA.length);
    }

    // ─── Style helpers ────────────────────────────────────────────────────────

    private static CellStyle headerStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        f.setColor(IndexedColors.WHITE.getIndex());

        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private static CellStyle flagStyle(Workbook wb, IndexedColors bg) {
        Font f = wb.createFont();
        f.setBold(true);

        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(bg.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }
}
