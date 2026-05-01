package com.automation.pages;

import com.automation.constants.AppConstants;
import com.automation.utils.ActionUtils;
import com.automation.utils.JSUtils;
import com.automation.utils.WaitUtils;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Abstract base for all Page Object classes.
 *
 * Responsibilities:
 *   - Hold the Playwright Page reference
 *   - Expose shared utility instances (wait, action, JS)
 *   - Provide common navigation helpers available on every page
 *   - Enforce the rule that NO assertions live in page classes
 *
 * Concrete page classes extend this and add page-specific locators and actions.
 */
public abstract class BasePage {

    private static final Logger log = LogManager.getLogger(BasePage.class);

    protected final Page page;
    protected final WaitUtils waitUtils;
    protected final ActionUtils actionUtils;
    protected final JSUtils jsUtils;

    // ─── Shared site-wide navigation locators ─────────────────────────────────
    private static final String NAV_STORE_LINK   = "a[href*='/store']";
    private static final String NAV_ACCOUNT_LINK = "a[href*='/account']";
    private static final String NAV_CART_LINK    = "a[href*='/cart']";
    private static final String CART_COUNT       = ".cart-contents-count";
    private static final String SEARCH_ICON      = ".search-icon, .search-toggle";
    private static final String SEARCH_INPUT     = "input.search-field";

    protected BasePage(Page page) {
        this.page        = page;
        this.waitUtils   = new WaitUtils(page);
        this.actionUtils = new ActionUtils(page);
        this.jsUtils     = new JSUtils(page);
    }

    // ─── Page metadata ────────────────────────────────────────────────────────

    public String getTitle() {
        return page.title();
    }

    public String getCurrentUrl() {
        return page.url();
    }

    // ─── Navigation helpers (available on every page) ─────────────────────────

    public void navigateTo(String url) {
        log.info("Navigating to: {}", url);
        page.navigate(url);
        waitForPageLoad();
    }

    public void goBack() {
        page.goBack();
        waitForPageLoad();
    }

    public void refresh() {
        page.reload();
        waitForPageLoad();
    }

    /**
     * Waits for the browser's network to be idle — appropriate after navigation
     * or actions that trigger XHR/fetch requests (e.g. add-to-cart).
     */
    public void waitForPageLoad() {
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                new Page.WaitForLoadStateOptions().setTimeout(AppConstants.PAGE_LOAD_TIMEOUT_MS));
    }

    // ─── Global site navigation ───────────────────────────────────────────────

    public StorePage navigateToStore() {
        log.info("Clicking 'Store' navigation link.");
        page.locator(NAV_STORE_LINK).first().click();
        waitForPageLoad();
        return new StorePage(page);
    }

    public LoginPage navigateToAccount() {
        log.info("Clicking 'Account' navigation link.");
        page.locator(NAV_ACCOUNT_LINK).first().click();
        waitForPageLoad();
        return new LoginPage(page);
    }

    public CartPage navigateToCart() {
        log.info("Clicking 'Cart' navigation link.");
        page.locator(NAV_CART_LINK).first().click();
        waitForPageLoad();
        return new CartPage(page);
    }

    public void searchFor(String query) {
        log.info("Performing site search for: '{}'", query);
        // Some WooCommerce themes use an icon toggle before the input appears
        Locator searchIconLocator = page.locator(SEARCH_ICON);
        if (searchIconLocator.count() > 0 && searchIconLocator.isVisible()) {
            searchIconLocator.click();
        }
        Locator searchInput = page.locator(SEARCH_INPUT);
        waitUtils.waitForVisible(searchInput);
        searchInput.fill(query);
        searchInput.press("Enter");
        waitForPageLoad();
    }

    // ─── Cart count ───────────────────────────────────────────────────────────

    public int getCartCount() {
        Locator countLocator = page.locator(CART_COUNT);
        if (countLocator.count() == 0 || !countLocator.isVisible()) return 0;
        String text = countLocator.textContent().trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            log.warn("Could not parse cart count '{}'; returning 0.", text);
            return 0;
        }
    }

    // ─── Generic element helpers ──────────────────────────────────────────────

    protected boolean isElementVisible(String selector) {
        return page.locator(selector).isVisible();
    }

    protected String getText(String selector) {
        return page.locator(selector).textContent().trim();
    }

    protected void click(String selector) {
        page.locator(selector).click();
    }

    protected void fill(String selector, String value) {
        page.locator(selector).fill(value);
    }

    protected void waitForVisible(String selector) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(AppConstants.EXPLICIT_WAIT_MS));
    }

    protected void waitForHidden(String selector) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(AppConstants.EXPLICIT_WAIT_MS));
    }
}
