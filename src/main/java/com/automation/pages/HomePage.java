package com.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Page Object for the AskOmDch home page (https://askomdch.com/).
 * Covers hero section, featured products, and primary CTA elements.
 */
public class HomePage extends BasePage {

    private static final Logger log = LogManager.getLogger(HomePage.class);

    // ─── Locators ──────────────────────────────────────────────────────────────
    private static final String HERO_SECTION         = ".hero, .elementor-section, .site-hero";
    private static final String FEATURED_PRODUCTS    = ".products .product";
    private static final String PRODUCT_TITLE        = ".woocommerce-loop-product__title";
    private static final String SHOP_NOW_CTA         = "a[href*='/store'], .shop-now-btn, a.button.wc-forward";
    private static final String NAV_HOME_LINK        = "a[href='/'], a[href*='askomdch.com/']";
    private static final String SITE_LOGO            = ".custom-logo, .site-branding img, .site-title a";
    private static final String WOOCOMMERCE_NOTICE   = ".woocommerce-info, .woocommerce-message";

    public HomePage(Page page) {
        super(page);
        log.debug("HomePage instantiated for URL: {}", page.url());
    }

    // ─── Actions ────────────────────────────────────────────────────────────────

    /** Navigates to the home page and waits for content to load. */
    public HomePage load(String baseUrl) {
        navigateTo(baseUrl);
        log.info("Home page loaded.");
        return this;
    }

    /** Returns true when the home page hero / main content is visible. */
    public boolean isPageLoaded() {
        return page.locator("body.home, body.page").count() > 0
                || page.locator(SITE_LOGO).count() > 0;
    }

    /** Clicks the primary 'Shop Now' or store CTA button. */
    public StorePage clickShopNow() {
        log.info("Clicking primary shop CTA.");
        Locator cta = page.locator(SHOP_NOW_CTA).first();
        waitUtils.waitForVisible(cta);
        cta.click();
        waitForPageLoad();
        return new StorePage(page);
    }

    /** Returns the displayed titles of featured products on the home page. */
    public List<String> getFeaturedProductTitles() {
        return page.locator(FEATURED_PRODUCTS + " " + PRODUCT_TITLE)
                .allTextContents()
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Returns the count of product cards visible on the home page. */
    public int getFeaturedProductCount() {
        return page.locator(FEATURED_PRODUCTS).count();
    }

    /** Returns true if any WooCommerce info/success notice is displayed. */
    public boolean hasNotice() {
        return page.locator(WOOCOMMERCE_NOTICE).count() > 0;
    }

    /** Returns the text of the first visible WooCommerce notice. */
    public String getNoticeText() {
        return page.locator(WOOCOMMERCE_NOTICE).first().textContent().trim();
    }

    /** Returns the site logo locator for visibility checks. */
    public Locator getSiteLogoLocator() {
        return page.locator(SITE_LOGO);
    }
}
