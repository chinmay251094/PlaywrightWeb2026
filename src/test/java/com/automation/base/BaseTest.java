package com.automation.base;

import com.automation.config.ConfigReader;
import com.automation.driver.PlaywrightFactory;
import com.automation.listeners.CustomReportListener;
import com.automation.listeners.RetryAnalyzer;
import com.automation.listeners.TestNGListener;
import com.automation.pages.*;
import com.automation.utils.ScreenshotUtils;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;

import java.util.Map;

/**
 * Abstract base for all test classes.
 *
 * Browser selection and headless mode are now read directly from the
 * Excel DataProvider map rather than from testng.xml parameters.
 * This lets the single "TestSuite" sheet control every aspect of execution.
 *
 * @BeforeMethod receives Object[] params — when a @DataProvider is wired,
 * TestNG populates params[0] with the Map<String,String> row from Excel.
 * For tests without a provider the array is empty and ConfigReader defaults apply.
 */
@Listeners({ CustomReportListener.class, TestNGListener.class })
public abstract class BaseTest {

    private static final Logger log = LogManager.getLogger(BaseTest.class);

    protected Page        page;
    protected HomePage    homePage;
    protected LoginPage   loginPage;
    protected StorePage   storePage;
    protected CartPage    cartPage;
    protected AccountPage accountPage;
    protected ConfigReader config;

    // ─── Setup ────────────────────────────────────────────────────────────────

    /**
     * TestNG passes the DataProvider row as params[0] when the test method
     * uses @DataProvider. We extract browser/headless from it so Excel is the
     * single source of truth for every execution dimension.
     */
    @BeforeMethod(alwaysRun = true)
    public void setUp(Object[] params) {
        config = ConfigReader.get();

        // Defaults from ConfigReader / properties files
        String  browser  = config.getBrowser();
        boolean headless = config.isHeadless();

        // Override from Excel row when available.
        // Exception: if -Dheadless was passed explicitly on the command line (e.g. by CI),
        // that value wins — Excel cannot force headed mode on a headless server.
        boolean headlessExplicit = System.getProperty("headless") != null;
        if (params != null && params.length > 0 && params[0] instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) raw;
            String b = data.get("browser");
            String h = data.get("headless");
            if (b != null && !b.isBlank()) browser = b;
            if (h != null && !h.isBlank() && !headlessExplicit) headless = Boolean.parseBoolean(h);
        }

        log.info("Setting up — browser: {}, headless: {}, env: {}",
                browser, headless, config.getEnvironment());

        page = PlaywrightFactory.initBrowser(browser, headless);

        homePage    = new HomePage(page);
        loginPage   = new LoginPage(page);
        storePage   = new StorePage(page);
        cartPage    = new CartPage(page);
        accountPage = new AccountPage(page);

        navigateWithRetry(config.getBaseUrl());

        log.info("Browser ready — {}", page.url());
    }

    // ─── Teardown ─────────────────────────────────────────────────────────────

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        try {
            if (result.getStatus() == ITestResult.FAILURE) {
                try {
                    String path = ScreenshotUtils.captureOnFailure(page, result.getName());
                    if (!path.isEmpty()) log.info("Failure screenshot: {}", path);
                } catch (Exception e) {
                    log.warn("Could not capture screenshot: {}", e.getMessage());
                }
            }
        } finally {
            PlaywrightFactory.closeBrowser();
            RetryAnalyzer.reset();
            log.info("Teardown complete — {}", result.getName());
        }
    }

    // ─── Test helpers ─────────────────────────────────────────────────────────

    /**
     * Navigates to a URL and retries once on ERR_ABORTED.
     * Demo sites and shared CI IPs occasionally drop the first connection;
     * a single retry with a short pause resolves ~95% of those transient failures.
     */
    private void navigateWithRetry(String url) {
        try {
            page.navigate(url);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        } catch (PlaywrightException e) {
            if (e.getMessage() != null && e.getMessage().contains("ERR_ABORTED")) {
                log.warn("Navigation aborted for '{}', retrying in 3 s...", url);
                try { Thread.sleep(3_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                page.navigate(url);
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            } else {
                throw e;
            }
        }
    }

    /** Logs a named step to Log4j2. */
    protected void step(String description) {
        log.info("  STEP: {}", description);
    }

    /** Returns a fresh page object using the current thread's Page. */
    protected <T extends BasePage> T on(Class<T> pageClass) {
        try {
            return pageClass.getConstructor(Page.class).newInstance(page);
        } catch (Exception e) {
            throw new com.automation.exceptions.FrameworkException(
                    "Could not create page object: " + pageClass.getSimpleName(), e);
        }
    }
}
