package com.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the WooCommerce Checkout page.
 * URL: https://askomdch.com/checkout/
 */
public class CheckoutPage extends BasePage {

    private static final Logger log = LogManager.getLogger(CheckoutPage.class);

    // ─── Billing form locators ─────────────────────────────────────────────────
    private static final String FIRST_NAME       = "#billing_first_name";
    private static final String LAST_NAME        = "#billing_last_name";
    private static final String EMAIL            = "#billing_email";
    private static final String PHONE            = "#billing_phone";
    private static final String COMPANY          = "#billing_company";
    private static final String ADDRESS_1        = "#billing_address_1";
    private static final String ADDRESS_2        = "#billing_address_2";
    private static final String CITY             = "#billing_city";
    private static final String STATE_SELECT     = "#billing_state";
    private static final String POSTCODE         = "#billing_postcode";
    private static final String COUNTRY_SELECT   = "#billing_country";

    // ─── Order locators ────────────────────────────────────────────────────────
    private static final String ORDER_NOTES      = "#order_comments";
    private static final String PLACE_ORDER_BTN  = "#place_order";
    private static final String ORDER_TOTAL      = ".order-total .amount";
    private static final String ORDER_ITEM       = ".woocommerce-checkout-review-order-table .cart_item";
    private static final String PAYMENT_METHOD   = ".wc_payment_method";
    private static final String COD_RADIO        = "#payment_method_cod";

    // ─── Confirmation locators ─────────────────────────────────────────────────
    private static final String ORDER_CONFIRM_HEADING = ".woocommerce-order-received h2, .entry-title";
    private static final String ORDER_NUMBER          = ".woocommerce-order-overview__order strong";
    private static final String ERROR_NOTICE          = ".woocommerce-error li";

    public CheckoutPage(Page page) {
        super(page);
        log.debug("CheckoutPage instantiated.");
    }

    // ─── Actions ────────────────────────────────────────────────────────────────

    /** Fills the complete billing details form. */
    public CheckoutPage fillBillingDetails(String firstName, String lastName,
                                           String address, String city,
                                           String postcode, String phone,
                                           String email) {
        log.info("Filling billing details for: {} {}", firstName, lastName);
        clearAndFill(FIRST_NAME,   firstName);
        clearAndFill(LAST_NAME,    lastName);
        clearAndFill(ADDRESS_1,    address);
        clearAndFill(CITY,         city);
        clearAndFill(POSTCODE,     postcode);
        clearAndFill(PHONE,        phone);
        clearAndFill(EMAIL,        email);
        return this;
    }

    public CheckoutPage fillOrderNotes(String notes) {
        fill(ORDER_NOTES, notes);
        return this;
    }

    /** Selects Cash on Delivery payment method. */
    public CheckoutPage selectCashOnDelivery() {
        Locator cod = page.locator(COD_RADIO);
        if (cod.count() > 0) cod.check();
        return this;
    }

    /** Clicks Place Order and returns this page (confirmation or error will be on same URL base). */
    public CheckoutPage placeOrder() {
        log.info("Placing order.");
        click(PLACE_ORDER_BTN);
        waitForPageLoad();
        return this;
    }

    // ─── State queries ────────────────────────────────────────────────────────

    public boolean isOrderPlacedSuccessfully() {
        return getCurrentUrl().contains("order-received")
                || page.locator(ORDER_CONFIRM_HEADING).count() > 0;
    }

    public String getOrderNumber() {
        Locator num = page.locator(ORDER_NUMBER);
        return num.count() > 0 ? num.first().textContent().trim() : "";
    }

    public String getOrderTotal() {
        Locator total = page.locator(ORDER_TOTAL);
        return total.count() > 0 ? total.first().textContent().trim() : "";
    }

    public int getOrderItemCount() {
        return page.locator(ORDER_ITEM).count();
    }

    public boolean hasValidationError() {
        return page.locator(ERROR_NOTICE).count() > 0;
    }

    public String getValidationError() {
        Locator err = page.locator(ERROR_NOTICE);
        return err.count() > 0 ? err.first().textContent().trim() : "";
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void clearAndFill(String selector, String value) {
        Locator el = page.locator(selector);
        el.fill("");
        el.fill(value);
    }
}
