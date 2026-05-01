package com.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for a WooCommerce single product detail page.
 * URL pattern: https://askomdch.com/product/{slug}/
 */
public class ProductPage extends BasePage {

    private static final Logger log = LogManager.getLogger(ProductPage.class);

    // ─── Locators ──────────────────────────────────────────────────────────────
    private static final String PRODUCT_TITLE      = "h1.product_title";
    private static final String PRODUCT_PRICE      = "p.price";
    private static final String PRODUCT_SKU        = ".sku";
    private static final String PRODUCT_DESC       = "#tab-description, .woocommerce-product-details__short-description";
    private static final String QUANTITY_INPUT     = "input.qty";
    private static final String ADD_TO_CART_BTN    = "button.single_add_to_cart_button";
    private static final String CART_NOTICE        = ".woocommerce-message";
    private static final String BREADCRUMB         = ".woocommerce-breadcrumb";
    private static final String RELATED_PRODUCTS   = ".related.products li.product";
    private static final String STOCK_STATUS       = ".stock";
    private static final String PRODUCT_CATEGORY   = ".posted_in a";
    private static final String PRODUCT_TABS       = ".woocommerce-tabs .tabs li a";
    private static final String REVIEWS_TAB        = "a[href='#tab-reviews']";
    private static final String PRODUCT_IMAGE      = ".woocommerce-product-gallery__image img";
    private static final String VIEW_CART_LINK     = ".woocommerce-message a.button";

    public ProductPage(Page page) {
        super(page);
        log.debug("ProductPage instantiated for: {}", page.url());
    }

    // ─── Actions ────────────────────────────────────────────────────────────────

    /** Sets the quantity and adds the product to the cart. */
    public ProductPage addToCart(int quantity) {
        log.info("Adding {} unit(s) to cart from product page.", quantity);
        setQuantity(quantity);
        click(ADD_TO_CART_BTN);
        waitForPageLoad();
        return this;
    }

    public ProductPage addToCart() {
        return addToCart(1);
    }

    /** Updates the quantity input field. */
    public ProductPage setQuantity(int quantity) {
        Locator qtyInput = page.locator(QUANTITY_INPUT);
        qtyInput.fill(String.valueOf(quantity));
        return this;
    }

    /** Clicks 'View Cart' link in the success notice after adding a product. */
    public CartPage viewCart() {
        waitForVisible(CART_NOTICE);
        click(VIEW_CART_LINK);
        waitForPageLoad();
        return new CartPage(page);
    }

    public void clickReviewsTab() {
        click(REVIEWS_TAB);
    }

    // ─── State queries ────────────────────────────────────────────────────────

    public String getProductTitle() {
        return getText(PRODUCT_TITLE);
    }

    public String getProductPrice() {
        return page.locator(PRODUCT_PRICE).first().textContent().trim();
    }

    public String getProductSku() {
        Locator sku = page.locator(PRODUCT_SKU);
        return sku.count() > 0 ? sku.textContent().trim() : "";
    }

    public String getStockStatus() {
        Locator stock = page.locator(STOCK_STATUS);
        return stock.count() > 0 ? stock.textContent().trim() : "In Stock";
    }

    public boolean isInStock() {
        String status = getStockStatus().toLowerCase();
        return !status.contains("out of stock") && !status.contains("sold out");
    }

    public boolean isAddedToCartSuccessDisplayed() {
        return page.locator(CART_NOTICE).count() > 0 && page.locator(CART_NOTICE).isVisible();
    }

    public String getCartNoticeText() {
        Locator notice = page.locator(CART_NOTICE);
        return notice.count() > 0 ? notice.textContent().trim() : "";
    }

    public String getBreadcrumb() {
        Locator bc = page.locator(BREADCRUMB);
        return bc.count() > 0 ? bc.textContent().trim() : "";
    }

    public int getRelatedProductCount() {
        return page.locator(RELATED_PRODUCTS).count();
    }

    public String getCategory() {
        Locator cat = page.locator(PRODUCT_CATEGORY);
        return cat.count() > 0 ? cat.first().textContent().trim() : "";
    }

    public int getCurrentQuantity() {
        return Integer.parseInt(page.locator(QUANTITY_INPUT).inputValue());
    }
}
