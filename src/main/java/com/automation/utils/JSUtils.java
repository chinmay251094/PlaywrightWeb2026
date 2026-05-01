package com.automation.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * JavaScript execution utilities.
 *
 * Reserved for cases where Playwright's built-in locator API is not
 * sufficient: scrolling to exact positions, reading computed styles,
 * manipulating browser storage, or interacting with elements that are
 * otherwise not reachable through normal DOM events.
 */
public class JSUtils {

    private static final Logger log = LogManager.getLogger(JSUtils.class);
    private final Page page;

    public JSUtils(Page page) {
        this.page = page;
    }

    // ─── Click ────────────────────────────────────────────────────────────────

    /** JS click — last resort for elements blocked by overlays. */
    public void jsClick(Locator locator) {
        log.debug("Performing JavaScript click.");
        locator.evaluate("el => el.click()");
    }

    public void jsClick(String selector) {
        log.debug("Performing JavaScript click on selector: {}", selector);
        page.evaluate("document.querySelector('" + selector + "').click()");
    }

    // ─── Scroll ───────────────────────────────────────────────────────────────

    public void scrollToTop() {
        page.evaluate("window.scrollTo(0, 0)");
    }

    public void scrollToBottom() {
        page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
    }

    public void scrollToElement(Locator locator) {
        locator.evaluate("el => el.scrollIntoView({behavior: 'smooth', block: 'center'})");
    }

    public void scrollBy(int x, int y) {
        page.evaluate(String.format("window.scrollBy(%d, %d)", x, y));
    }

    // ─── DOM manipulation ─────────────────────────────────────────────────────

    /** Sets a value on an input and dispatches the change event (for React/Vue inputs). */
    public void setValueWithEvent(String selector, String value) {
        log.debug("Setting value via JS with change event on: {}", selector);
        page.evaluate(String.format(
                "var el = document.querySelector('%s'); " +
                "var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set; " +
                "nativeInputValueSetter.call(el, '%s'); " +
                "el.dispatchEvent(new Event('input', { bubbles: true })); " +
                "el.dispatchEvent(new Event('change', { bubbles: true }));",
                selector, value));
    }

    public void removeAttribute(String selector, String attribute) {
        page.evaluate(String.format(
                "document.querySelector('%s').removeAttribute('%s')", selector, attribute));
    }

    public void setAttribute(String selector, String attribute, String value) {
        page.evaluate(String.format(
                "document.querySelector('%s').setAttribute('%s', '%s')", selector, attribute, value));
    }

    // ─── Browser storage ──────────────────────────────────────────────────────

    public void setLocalStorageItem(String key, String value) {
        page.evaluate(String.format("localStorage.setItem('%s', '%s')", key, value));
    }

    public String getLocalStorageItem(String key) {
        Object result = page.evaluate(String.format("localStorage.getItem('%s')", key));
        return result != null ? result.toString() : null;
    }

    public void clearLocalStorage() {
        page.evaluate("localStorage.clear()");
    }

    public void setSessionStorageItem(String key, String value) {
        page.evaluate(String.format("sessionStorage.setItem('%s', '%s')", key, value));
    }

    // ─── Cookie helpers ───────────────────────────────────────────────────────

    public void deleteCookies() {
        page.context().clearCookies();
    }

    // ─── DOM queries ─────────────────────────────────────────────────────────

    public String getInnerText(String selector) {
        Object result = page.evaluate(String.format(
                "document.querySelector('%s')?.innerText", selector));
        return result != null ? result.toString().trim() : "";
    }

    public boolean isElementInViewport(Locator locator) {
        Object result = locator.evaluate(
                "el => { const r = el.getBoundingClientRect(); " +
                "return r.top >= 0 && r.left >= 0 && " +
                "r.bottom <= (window.innerHeight || document.documentElement.clientHeight) && " +
                "r.right <= (window.innerWidth || document.documentElement.clientWidth); }");
        return Boolean.TRUE.equals(result);
    }

    /** Returns the computed CSS property value of an element. */
    public String getComputedStyle(Locator locator, String property) {
        Object result = locator.evaluate(
                String.format("el => window.getComputedStyle(el).getPropertyValue('%s')", property));
        return result != null ? result.toString() : "";
    }

    // ─── Page info ────────────────────────────────────────────────────────────

    public String getPageReadyState() {
        return page.evaluate("document.readyState").toString();
    }

    public long getPageLoadTime() {
        Object result = page.evaluate(
                "window.performance.timing.loadEventEnd - window.performance.timing.navigationStart");
        return result != null ? Long.parseLong(result.toString()) : -1L;
    }
}
