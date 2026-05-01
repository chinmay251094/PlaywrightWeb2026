package com.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Page Object for the WooCommerce Cart page.
 * URL: https://askomdch.com/cart/
 */
public class CartPage extends BasePage {

    private static final Logger log = LogManager.getLogger(CartPage.class);

    // ─── Locators ──────────────────────────────────────────────────────────────
    private static final String CART_ITEM          = ".cart_item";
    private static final String ITEM_NAME          = ".product-name a";
    private static final String ITEM_QUANTITY      = ".product-quantity input.qty";
    private static final String ITEM_PRICE         = ".product-price .amount";
    private static final String ITEM_SUBTOTAL      = ".product-subtotal .amount";
    private static final String REMOVE_ITEM_BTN    = ".product-remove a.remove";
    private static final String CART_TOTAL         = ".cart-totals .order-total .amount";
    private static final String CART_SUBTOTAL      = ".cart-totals .cart-subtotal .amount";
    private static final String UPDATE_CART_BTN    = "button[name='update_cart'], [name='update_cart']";
    private static final String CHECKOUT_BTN       = ".wc-proceed-to-checkout a.checkout-button";
    private static final String CONTINUE_SHOPPING  = ".return-to-shop a, .wc-empty-cart-message + .return-to-shop a";
    private static final String EMPTY_CART_NOTICE  = ".cart-empty, .woocommerce-info";
    private static final String COUPON_INPUT       = "#coupon_code";
    private static final String APPLY_COUPON_BTN   = "[name='apply_coupon']";
    private static final String COUPON_RESULT      = ".woocommerce-message, .woocommerce-error";

    public CartPage(Page page) {
        super(page);
        log.debug("CartPage instantiated.");
    }

    // ─── Actions ────────────────────────────────────────────────────────────────

    /**
     * Updates the quantity of a cart item identified by its product name.
     * Call updateCart() after to apply the change.
     */
    public CartPage setItemQuantity(String productName, int newQuantity) {
        log.info("Setting quantity of '{}' to {}", productName, newQuantity);
        Locator row = findCartRowByName(productName);
        row.locator(ITEM_QUANTITY).fill(String.valueOf(newQuantity));
        return this;
    }

    /** Clicks the Update Cart button and waits for totals to refresh. */
    public CartPage updateCart() {
        log.info("Updating cart totals.");
        click(UPDATE_CART_BTN);
        waitForPageLoad();
        return this;
    }

    /** Removes a cart item by product name. */
    public CartPage removeItem(String productName) {
        log.info("Removing cart item: '{}'", productName);
        Locator row = findCartRowByName(productName);
        row.locator(REMOVE_ITEM_BTN).click();
        waitForPageLoad();
        return this;
    }

    /** Proceeds to the WooCommerce checkout. */
    public CheckoutPage proceedToCheckout() {
        log.info("Proceeding to checkout.");
        click(CHECKOUT_BTN);
        waitForPageLoad();
        return new CheckoutPage(page);
    }

    /** Applies a coupon code. */
    public CartPage applyCoupon(String couponCode) {
        log.info("Applying coupon: {}", couponCode);
        waitForVisible(COUPON_INPUT);
        fill(COUPON_INPUT, couponCode);
        click(APPLY_COUPON_BTN);
        waitForPageLoad();
        return this;
    }

    // ─── State queries ────────────────────────────────────────────────────────

    public boolean isEmpty() {
        return page.locator(EMPTY_CART_NOTICE).count() > 0
                && page.locator(CART_ITEM).count() == 0;
    }

    public int getItemCount() {
        return page.locator(CART_ITEM).count();
    }

    public List<String> getCartItemNames() {
        return page.locator(CART_ITEM + " " + ITEM_NAME)
                .allTextContents()
                .stream()
                .map(String::trim)
                .toList();
    }

    public boolean containsProduct(String productName) {
        return getCartItemNames().stream()
                .anyMatch(n -> n.equalsIgnoreCase(productName) || n.contains(productName));
    }

    public String getItemQuantity(String productName) {
        return findCartRowByName(productName).locator(ITEM_QUANTITY).inputValue();
    }

    public String getCartTotal() {
        Locator total = page.locator(CART_TOTAL);
        return total.count() > 0 ? total.first().textContent().trim() : "";
    }

    public String getCartSubtotal() {
        Locator subtotal = page.locator(CART_SUBTOTAL);
        return subtotal.count() > 0 ? subtotal.first().textContent().trim() : "";
    }

    public String getCouponResultMessage() {
        Locator msg = page.locator(COUPON_RESULT);
        return msg.count() > 0 ? msg.first().textContent().trim() : "";
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Locator findCartRowByName(String productName) {
        List<Locator> rows = page.locator(CART_ITEM).all();
        for (Locator row : rows) {
            Locator nameCell = row.locator(ITEM_NAME);
            if (nameCell.count() > 0) {
                String name = nameCell.textContent().trim();
                if (name.toLowerCase().contains(productName.toLowerCase())) {
                    return row;
                }
            }
        }
        throw new com.automation.exceptions.FrameworkException(
                "Cart item not found: '" + productName + "'");
    }
}
