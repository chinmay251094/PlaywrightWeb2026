package com.automation.driver;

import com.automation.config.ConfigReader;
import com.automation.constants.AppConstants;
import com.automation.exceptions.FrameworkException;
import com.microsoft.playwright.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.List;

/**
 * Thread-safe Playwright browser factory.
 *
 * Design rationale:
 *   - Each parallel test thread owns its own Playwright + Browser + BrowserContext + Page.
 *   - ThreadLocal guarantees zero cross-thread state sharing (the root cause of most
 *     parallel-execution flakiness with WebDriver-style frameworks).
 *   - Callers MUST call closeBrowser() in @AfterMethod so ThreadLocal storage is freed
 *     and native browser processes are terminated. Without cleanup, long suites leak
 *     memory and open processes.
 *
 * Lifecycle per thread:
 *   initBrowser() → [test executes] → closeBrowser()
 */
public final class PlaywrightFactory {

    private static final Logger log = LogManager.getLogger(PlaywrightFactory.class);

    // One instance of each per thread — Playwright, Browser, and Page are NOT thread-safe
    private static final ThreadLocal<Playwright>      tlPlaywright  = new ThreadLocal<>();
    private static final ThreadLocal<Browser>         tlBrowser     = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext>  tlContext     = new ThreadLocal<>();
    private static final ThreadLocal<Page>            tlPage        = new ThreadLocal<>();

    private PlaywrightFactory() { /* static factory — no instantiation */ }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Initialises a Playwright browser for the current thread and returns a
     * ready-to-use Page.  Reads browser/headless configuration from ConfigReader,
     * which in turn respects -Dbrowser and -Dheadless JVM flags.
     */
    public static Page initBrowser() {
        ConfigReader cfg = ConfigReader.get();
        String browserName = cfg.getBrowser();
        boolean headless   = cfg.isHeadless();
        return initBrowser(browserName, headless);
    }

    /**
     * Overload — allows a test class to request a specific browser explicitly,
     * useful for cross-browser test groups.
     */
    public static Page initBrowser(String browserName, boolean headless) {
        log.info("Thread [{}] — initialising browser: {} | headless: {}",
                Thread.currentThread().getId(), browserName, headless);

        Playwright playwright = Playwright.create();
        tlPlaywright.set(playwright);

        Browser browser = launchBrowser(playwright, browserName, headless);
        tlBrowser.set(browser);

        BrowserContext context = browser.newContext(buildContextOptions());
        tlContext.set(context);

        Page page = context.newPage();
        page.setDefaultTimeout(AppConstants.DEFAULT_TIMEOUT_MS);
        page.setDefaultNavigationTimeout(AppConstants.PAGE_LOAD_TIMEOUT_MS);
        tlPage.set(page);

        log.info("Thread [{}] — browser initialised successfully.", Thread.currentThread().getId());
        return page;
    }

    /** Returns the Page for the current thread. Throws if not yet initialised. */
    public static Page getPage() {
        Page page = tlPage.get();
        if (page == null) {
            throw new FrameworkException(
                    "No Page found for thread [" + Thread.currentThread().getId() + "]. " +
                    "Ensure initBrowser() was called in @BeforeMethod.");
        }
        return page;
    }

    /** Returns the BrowserContext for the current thread. */
    public static BrowserContext getBrowserContext() {
        BrowserContext ctx = tlContext.get();
        if (ctx == null) throw new FrameworkException("No BrowserContext for current thread.");
        return ctx;
    }

    /**
     * Closes all Playwright resources for the current thread and removes
     * them from ThreadLocal storage to prevent memory leaks.
     */
    public static void closeBrowser() {
        long threadId = Thread.currentThread().getId();
        try {
            BrowserContext ctx = tlContext.get();
            if (ctx != null) {
                ctx.close();
                log.debug("Thread [{}] — BrowserContext closed.", threadId);
            }

            Browser browser = tlBrowser.get();
            if (browser != null) {
                browser.close();
                log.debug("Thread [{}] — Browser closed.", threadId);
            }

            Playwright playwright = tlPlaywright.get();
            if (playwright != null) {
                playwright.close();
                log.debug("Thread [{}] — Playwright instance closed.", threadId);
            }
        } finally {
            // Always clear ThreadLocal refs — avoids classloader leaks in app-server envs
            tlPage.remove();
            tlContext.remove();
            tlBrowser.remove();
            tlPlaywright.remove();
            log.info("Thread [{}] — all Playwright resources released.", threadId);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private static Browser launchBrowser(Playwright playwright, String browserName, boolean headless) {
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setSlowMo(0) // set >0 for visual debugging
                .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage"));

        return switch (browserName.toLowerCase()) {
            case AppConstants.BROWSER_FIREFOX -> playwright.firefox().launch(launchOptions);
            case AppConstants.BROWSER_WEBKIT  -> playwright.webkit().launch(launchOptions);
            default                           -> playwright.chromium().launch(launchOptions);
        };
    }

    private static Browser.NewContextOptions buildContextOptions() {
        ConfigReader cfg = ConfigReader.get();
        Browser.NewContextOptions opts = new Browser.NewContextOptions();

        // Respect viewport config; falls back to a standard 1920×1080 desktop
        int width  = cfg.getInt("viewport.width",  1920);
        int height = cfg.getInt("viewport.height", 1080);
        opts.setViewportSize(width, height);

        // Locale and timezone for consistent date/number formatting in tests
        opts.setLocale(cfg.getProperty("browser.locale", "en-US"));
        opts.setTimezoneId(cfg.getProperty("browser.timezone", "America/New_York"));

        // Store screenshots/videos under test-output when tracing is enabled
        if (cfg.getBoolean("trace.enabled", false)) {
            opts.setRecordVideoDir(Paths.get(AppConstants.REPORTS_DIR + "videos/"));
        }

        return opts;
    }
}
