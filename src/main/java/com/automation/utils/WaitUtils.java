package com.automation.utils;

import com.automation.constants.AppConstants;
import com.automation.exceptions.FrameworkException;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BooleanSupplier;

/**
 * Centralised wait utilities.
 *
 * All wait logic lives here so page objects and tests never call
 * Thread.sleep() or duplicate wait patterns. Playwright's built-in
 * auto-wait covers most cases; these methods handle compound conditions
 * and configurable timeouts.
 */
public class WaitUtils {

    private static final Logger log = LogManager.getLogger(WaitUtils.class);
    private final Page page;

    public WaitUtils(Page page) {
        this.page = page;
    }

    // ─── Locator-based waits ──────────────────────────────────────────────────

    /** Waits until the locator is visible using the default explicit timeout. */
    public void waitForVisible(Locator locator) {
        waitForVisible(locator, AppConstants.EXPLICIT_WAIT_MS);
    }

    public void waitForVisible(Locator locator, int timeoutMs) {
        log.debug("Waiting for element to be visible (timeout: {}ms)", timeoutMs);
        locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
    }

    /** Waits until the locator is hidden / detached from the DOM. */
    public void waitForHidden(Locator locator) {
        log.debug("Waiting for element to be hidden.");
        locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(AppConstants.EXPLICIT_WAIT_MS));
    }

    /** Waits until the locator is attached to DOM (not necessarily visible). */
    public void waitForAttached(Locator locator) {
        locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED)
                .setTimeout(AppConstants.EXPLICIT_WAIT_MS));
    }

    /** Waits until the element becomes enabled (not disabled). */
    public void waitForEnabled(Locator locator) {
        log.debug("Waiting for element to be enabled.");
        waitForVisible(locator);
        // Playwright's isEnabled checks the disabled property
        long deadline = System.currentTimeMillis() + AppConstants.EXPLICIT_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (locator.isEnabled()) return;
            pause(AppConstants.POLL_INTERVAL_MS);
        }
        throw new FrameworkException("Element was not enabled within " + AppConstants.EXPLICIT_WAIT_MS + "ms");
    }

    // ─── Selector-based waits ─────────────────────────────────────────────────

    public void waitForSelector(String selector) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(AppConstants.EXPLICIT_WAIT_MS));
    }

    public void waitForSelectorHidden(String selector) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(AppConstants.EXPLICIT_WAIT_MS));
    }

    // ─── Page-level waits ─────────────────────────────────────────────────────

    public void waitForNetworkIdle() {
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                new Page.WaitForLoadStateOptions().setTimeout(AppConstants.PAGE_LOAD_TIMEOUT_MS));
    }

    public void waitForDomContentLoaded() {
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                new Page.WaitForLoadStateOptions().setTimeout(AppConstants.PAGE_LOAD_TIMEOUT_MS));
    }

    /** Waits for a URL pattern (substring match) after navigation. */
    public void waitForUrl(String urlSubstring) {
        log.debug("Waiting for URL to contain: {}", urlSubstring);
        page.waitForURL("**" + urlSubstring + "**",
                new Page.WaitForURLOptions().setTimeout(AppConstants.PAGE_LOAD_TIMEOUT_MS));
    }

    // ─── Custom condition wait (fluent polling) ────────────────────────────────

    /**
     * Polls the supplied condition every POLL_INTERVAL_MS until it returns true
     * or the timeout elapses. Cleaner than Thread.sleep in complex scenarios.
     */
    public void waitUntil(BooleanSupplier condition, int timeoutMs, String description) {
        log.debug("Waiting until: '{}' (timeout: {}ms)", description, timeoutMs);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) return;
            } catch (Exception ignored) {
                // Swallow transient DOM exceptions during polling
            }
            pause(AppConstants.POLL_INTERVAL_MS);
        }
        throw new FrameworkException(
                "Condition '" + description + "' was not met within " + timeoutMs + "ms");
    }

    public void waitUntil(BooleanSupplier condition, String description) {
        waitUntil(condition, AppConstants.EXPLICIT_WAIT_MS, description);
    }

    // ─── Fixed pause (use sparingly — prefer condition-based waits) ───────────

    /**
     * Hard pause. Strictly for scenarios where no condition can be polled
     * (e.g., waiting for an animation to complete before screenshot).
     */
    public void pause(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Wait interrupted: {}", e.getMessage());
        }
    }
}
