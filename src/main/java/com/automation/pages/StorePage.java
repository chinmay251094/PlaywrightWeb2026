package com.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Page Object for the WooCommerce Store / Shop listing page.
 * URL: https://askomdch.com/store/
 */
public class StorePage extends BasePage {

    private static final Logger log = LogManager.getLogger(StorePage.class);

    // ─── Locators ──────────────────────────────────────────────────────────────
    private static final String PRODUCT_CARD          = "ul.products li.product";
    private static final String PRODUCT_TITLE         = ".woocommerce-loop-product__title";
    private static final String PRODUCT_PRICE         = ".price";
    private static final String ADD_TO_CART_BTN       = "a.add_to_cart_button";
    private static final String VIEW_CART_BTN         = "a.added_to_cart";
    private static final String PRODUCT_THUMBNAIL     = ".attachment-woocommerce_thumbnail";
    private static final String RESULTS_COUNT         = ".woocommerce-result-count";
    private static final String SORT_SELECT           = "select.orderby";
    private static final String SEARCH_RESULT_HEADER  = "h1.page-title, .woocommerce-products-header__title";
    private static final String NO_PRODUCTS_NOTICE    = ".woocommerce-info";
    private static final String PAGINATION_NEXT       = "a.next";
    private static final String PAGINATION_PREV       = "a.prev";

    public StorePage(Page page) {
        super(page);
        log.debug("StorePage instantiated.");
    }

    // ─── Actions ────────────────────────────────────────────────────────────────

    public StorePage load(String storeUrl) {
        navigateTo(storeUrl);
        return this;
    }

    /**
     * Adds a product to the cart by matching the product title (case-insensitive substring).
     * Returns this StorePage so callers can chain further store actions or navigate to cart.
     */
    public StorePage addProductToCartByName(String productName) {
        log.info("Adding product to cart by name: '{}'", productName);
        Locator card = findProductCardByName(productName);
        card.locator(ADD_TO_CART_BTN).click();
        // Wait for the "View cart" confirmation link to appear — signals cart update completed
        page.waitForSelector(VIEW_CART_BTN, new Page.WaitForSelectorOptions()
                .setTimeout(15_000));
        log.info("Product '{}' added to cart.", productName);
        return this;
    }

    /** Clicks the 'View Cart' link that appears after adding a product. */
    public CartPage clickViewCart() {
        click(VIEW_CART_BTN);
        waitForPageLoad();
        return new CartPage(page);
    }

    /** Opens a product detail page by clicking its title link. */
    public ProductPage openProductByName(String productName) {
        log.info("Opening product detail page for: '{}'", productName);
        Locator card = findProductCardByName(productName);
        card.locator(PRODUCT_TITLE + " a, a[href]").first().click();
        waitForPageLoad();
        return new ProductPage(page);
    }

    public void sortBy(String sortValue) {
        log.info("Sorting products by: {}", sortValue);
        page.locator(SORT_SELECT).selectOption(sortValue);
        waitForPageLoad();
    }

    public void goToNextPage() {
        click(PAGINATION_NEXT);
        waitForPageLoad();
    }

    public void goToPreviousPage() {
        click(PAGINATION_PREV);
        waitForPageLoad();
    }

    // ─── State queries ────────────────────────────────────────────────────────

    public List<String> getAllProductTitles() {
        return page.locator(PRODUCT_CARD + " " + PRODUCT_TITLE)
                .allTextContents()
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public List<String> getAllProductPrices() {
        return page.locator(PRODUCT_CARD + " " + PRODUCT_PRICE)
                .allTextContents()
                .stream()
                .map(String::trim)
                .toList();
    }

    public int getProductCount() {
        return page.locator(PRODUCT_CARD).count();
    }

    public String getResultsCount() {
        Locator count = page.locator(RESULTS_COUNT);
        return count.count() > 0 ? count.textContent().trim() : "";
    }

    public boolean isProductDisplayed(String productName) {
        return getAllProductTitles().stream()
                .anyMatch(t -> t.equalsIgnoreCase(productName) || t.contains(productName));
    }

    public boolean hasNoProductsNotice() {
        return page.locator(NO_PRODUCTS_NOTICE).count() > 0;
    }

    public boolean isNextPageAvailable() {
        return page.locator(PAGINATION_NEXT).count() > 0;
    }

    public String getPageTitle() {
        Locator h = page.locator(SEARCH_RESULT_HEADER);
        return h.count() > 0 ? h.first().textContent().trim() : page.title();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Locator findProductCardByName(String productName) {
        List<Locator> cards = page.locator(PRODUCT_CARD).all();
        for (Locator card : cards) {
            Locator titleLocator = card.locator(PRODUCT_TITLE);
            if (titleLocator.count() > 0) {
                String title = titleLocator.textContent().trim();
                if (title.toLowerCase().contains(productName.toLowerCase())) {
                    return card;
                }
            }
        }
        throw new com.automation.exceptions.FrameworkException(
                "Product not found on store page: '" + productName + "'");
    }
}
