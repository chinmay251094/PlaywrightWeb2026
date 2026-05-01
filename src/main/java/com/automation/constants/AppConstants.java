package com.automation.constants;

/**
 * Framework-wide constants.
 * All magic strings, file paths, and timeout values live here so there is
 * exactly one place to update when values change.
 */
public final class AppConstants {

    private AppConstants() {
        // Utility class — prevent instantiation
    }

    // ─── Timeout values (milliseconds) ───────────────────────────────────────
    public static final int DEFAULT_TIMEOUT_MS      = 30_000;
    public static final int EXPLICIT_WAIT_MS        = 15_000;
    public static final int PAGE_LOAD_TIMEOUT_MS    = 60_000;
    public static final int SHORT_WAIT_MS           = 5_000;
    public static final int POLL_INTERVAL_MS        = 500;

    // ─── Config file paths (relative to src/test/resources) ─────────────────
    public static final String CONFIG_FILE_PATH     = "config/config.properties";
    public static final String QA_CONFIG_PATH       = "config/qa.properties";
    public static final String UAT_CONFIG_PATH      = "config/uat.properties";
    public static final String PROD_CONFIG_PATH     = "config/prod.properties";

    // ─── Test data ────────────────────────────────────────────────────────────
    public static final String TEST_DATA_PATH       = "src/test/resources/testdata/TestData.xlsx";
    public static final String SCREENSHOT_DIR       = "test-output/screenshots/";
    public static final String REPORTS_DIR          = "test-output/reports/";
    public static final String LOGS_DIR             = "test-output/logs/";

    // ─── Excel sheet names ────────────────────────────────────────────────────
    // Single control sheet: every test method, its browser, run-flag, and data live here.
    public static final String TESTSUITE_SHEET      = "TestSuite";

    // Legacy per-feature sheets kept for reference; no longer used by the DataProvider.
    public static final String LOGIN_SHEET          = "LoginData";
    public static final String SEARCH_SHEET         = "SearchData";
    public static final String CART_SHEET           = "CartData";
    public static final String ACCOUNT_SHEET        = "AccountData";
    public static final String NAVIGATION_SHEET     = "NavigationData";

    // ─── Browser names (must match Playwright factory keys) ──────────────────
    public static final String BROWSER_CHROMIUM     = "chromium";
    public static final String BROWSER_FIREFOX      = "firefox";
    public static final String BROWSER_WEBKIT       = "webkit";

    // ─── Environment names ────────────────────────────────────────────────────
    public static final String ENV_QA               = "qa";
    public static final String ENV_UAT              = "uat";
    public static final String ENV_PROD             = "prod";

    // ─── Report constants ─────────────────────────────────────────────────────
    public static final String REPORT_TITLE         = "Playwright Automation Report";
    public static final String REPORT_NAME          = "Hybrid Framework Execution";
    public static final String REPORT_FILE          = REPORTS_DIR + "TestReport.html";

    // ─── Retry logic ──────────────────────────────────────────────────────────
    public static final int MAX_RETRY_COUNT         = 2;

    // ─── Test groups ──────────────────────────────────────────────────────────
    public static final String GROUP_SMOKE          = "smoke";
    public static final String GROUP_REGRESSION     = "regression";
    public static final String GROUP_SANITY         = "sanity";
    public static final String GROUP_NEGATIVE       = "negative";

    // ─── Date/time format used in screenshot and report names ─────────────────
    public static final String TIMESTAMP_FORMAT     = "yyyy-MM-dd_HH-mm-ss";

    // ─── System property keys ─────────────────────────────────────────────────
    public static final String PROP_ENV             = "env";
    public static final String PROP_BROWSER         = "browser";
    public static final String PROP_HEADLESS        = "headless";
}
