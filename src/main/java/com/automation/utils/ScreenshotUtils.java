package com.automation.utils;

import com.automation.constants.AppConstants;
import com.automation.exceptions.FrameworkException;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Screenshot capture utility.
 *
 * Saves screenshots to AppConstants.SCREENSHOT_DIR with timestamp-based
 * file names so they never collide across parallel threads.
 * Returns the absolute path so reporters can embed the image directly.
 */
public final class ScreenshotUtils {

    private static final Logger log = LogManager.getLogger(ScreenshotUtils.class);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern(AppConstants.TIMESTAMP_FORMAT);

    private ScreenshotUtils() { /* static utility */ }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Captures a full-page screenshot on test failure.
     *
     * @param page     the Playwright Page for the current thread
     * @param testName used as part of the file name
     * @return absolute path to the saved screenshot, or empty string on error
     */
    public static String captureOnFailure(Page page, String testName) {
        return capture(page, testName + "_FAILURE", true);
    }

    /**
     * Captures a viewport screenshot (not full-page) for embedding in reports.
     */
    public static String captureViewport(Page page, String testName) {
        return capture(page, testName, false);
    }

    /**
     * Captures a screenshot of a specific element identified by selector.
     */
    public static String captureElement(Page page, String selector, String label) {
        ensureDirectoryExists(AppConstants.SCREENSHOT_DIR);
        String fileName = buildFileName(label);
        Path filePath   = Paths.get(AppConstants.SCREENSHOT_DIR, fileName);
        try {
            page.locator(selector).screenshot(
                    new com.microsoft.playwright.Locator.ScreenshotOptions()
                            .setPath(filePath));
            log.info("Element screenshot saved: {}", filePath.toAbsolutePath());
            return filePath.toAbsolutePath().toString();
        } catch (Exception e) {
            log.error("Failed to capture element screenshot: {}", e.getMessage());
            return "";
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private static String capture(Page page, String label, boolean fullPage) {
        ensureDirectoryExists(AppConstants.SCREENSHOT_DIR);
        String fileName = buildFileName(sanitise(label));
        Path filePath   = Paths.get(AppConstants.SCREENSHOT_DIR, fileName);

        try {
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(filePath)
                    .setFullPage(fullPage));
            log.info("Screenshot saved: {}", filePath.toAbsolutePath());
            return filePath.toAbsolutePath().toString();
        } catch (Exception e) {
            log.error("Failed to capture screenshot for '{}': {}", label, e.getMessage());
            return "";
        }
    }

    private static String buildFileName(String label) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        // Include thread name to make parallel captures unique
        String threadName = Thread.currentThread().getName().replaceAll("[^a-zA-Z0-9]", "");
        return String.format("%s_%s_%s.png", label, timestamp, threadName);
    }

    /** Replaces characters that are invalid in file names. */
    private static String sanitise(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private static void ensureDirectoryExists(String dirPath) {
        try {
            Path dir = Paths.get(dirPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.debug("Created screenshot directory: {}", dir.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new FrameworkException("Cannot create screenshot directory: " + dirPath, e);
        }
    }
}
