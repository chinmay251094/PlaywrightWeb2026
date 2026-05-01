package com.automation.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.MouseButton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reusable browser interaction utilities.
 *
 * Wraps Playwright actions with logging so every UI interaction appears in
 * test logs automatically. Tests and page objects call these instead of
 * raw Playwright calls to keep consistent behaviour (e.g., always wait
 * for enabled before clicking).
 */
public class ActionUtils {

    private static final Logger log = LogManager.getLogger(ActionUtils.class);
    private final Page page;
    private final WaitUtils waitUtils;

    public ActionUtils(Page page) {
        this.page      = page;
        this.waitUtils = new WaitUtils(page);
    }

    // ─── Click variants ───────────────────────────────────────────────────────

    public void click(Locator locator) {
        log.debug("Clicking element.");
        waitUtils.waitForVisible(locator);
        locator.click();
    }

    public void click(String selector) {
        click(page.locator(selector));
    }

    /** Force-click bypasses actionability checks — use only when overlays block normal click. */
    public void forceClick(Locator locator) {
        log.debug("Force-clicking element.");
        locator.click(new Locator.ClickOptions().setForce(true));
    }

    public void doubleClick(Locator locator) {
        log.debug("Double-clicking element.");
        waitUtils.waitForVisible(locator);
        locator.dblclick();
    }

    public void rightClick(Locator locator) {
        log.debug("Right-clicking element.");
        waitUtils.waitForVisible(locator);
        locator.click(new Locator.ClickOptions().setButton(MouseButton.RIGHT));
    }

    // ─── Text input ───────────────────────────────────────────────────────────

    /** Clears the field then types the value. */
    public void fill(Locator locator, String value) {
        log.debug("Filling field with value: '{}'", maskSensitive(value));
        waitUtils.waitForVisible(locator);
        locator.fill(value);
    }

    public void fill(String selector, String value) {
        fill(page.locator(selector), value);
    }

    /** Types character by character — useful for fields with autocomplete listeners. */
    public void type(Locator locator, String value) {
        log.debug("Typing into field: '{}'", maskSensitive(value));
        waitUtils.waitForVisible(locator);
        locator.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(50));
    }

    public void clearAndFill(Locator locator, String value) {
        waitUtils.waitForVisible(locator);
        locator.fill("");
        locator.fill(value);
    }

    // ─── Keyboard ─────────────────────────────────────────────────────────────

    public void pressKey(Locator locator, String key) {
        log.debug("Pressing key '{}' on element.", key);
        locator.press(key);
    }

    public void pressEnter(Locator locator) {
        pressKey(locator, "Enter");
    }

    public void pressTab(Locator locator) {
        pressKey(locator, "Tab");
    }

    // ─── Select (dropdown) ────────────────────────────────────────────────────

    public void selectByValue(Locator locator, String value) {
        log.debug("Selecting option by value: '{}'", value);
        locator.selectOption(value);
    }

    public void selectByLabel(Locator locator, String label) {
        log.debug("Selecting option by label: '{}'", label);
        locator.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label));
    }

    // ─── Hover & scroll ───────────────────────────────────────────────────────

    public void hover(Locator locator) {
        log.debug("Hovering over element.");
        locator.hover();
    }

    public void scrollIntoView(Locator locator) {
        log.debug("Scrolling element into view.");
        locator.scrollIntoViewIfNeeded();
    }

    // ─── Checkbox & Radio ─────────────────────────────────────────────────────

    public void check(Locator locator) {
        log.debug("Checking checkbox/radio.");
        locator.check();
    }

    public void uncheck(Locator locator) {
        log.debug("Unchecking checkbox.");
        locator.uncheck();
    }

    // ─── Value reading ────────────────────────────────────────────────────────

    public String getText(Locator locator) {
        return locator.textContent().trim();
    }

    public String getInputValue(Locator locator) {
        return locator.inputValue();
    }

    public String getAttribute(Locator locator, String attribute) {
        return locator.getAttribute(attribute);
    }

    // ─── State checks ─────────────────────────────────────────────────────────

    public boolean isVisible(Locator locator) {
        return locator.isVisible();
    }

    public boolean isEnabled(Locator locator) {
        return locator.isEnabled();
    }

    public boolean isChecked(Locator locator) {
        return locator.isChecked();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /** Masks passwords/tokens in log output. */
    private String maskSensitive(String value) {
        if (value != null && value.length() > 3) {
            return value.substring(0, 2) + "***";
        }
        return "***";
    }
}
