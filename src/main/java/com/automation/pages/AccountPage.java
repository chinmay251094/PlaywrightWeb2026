package com.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the WooCommerce My Account dashboard.
 * URL: https://askomdch.com/account/
 * Shown after a successful login — differs from LoginPage (pre-auth).
 */
public class AccountPage extends BasePage {

    private static final Logger log = LogManager.getLogger(AccountPage.class);

    // ─── Dashboard navigation ─────────────────────────────────────────────────
    private static final String NAV_MENU           = ".woocommerce-MyAccount-navigation";
    private static final String NAV_ORDERS         = ".woocommerce-MyAccount-navigation-link--orders a";
    private static final String NAV_DOWNLOADS      = ".woocommerce-MyAccount-navigation-link--downloads a";
    private static final String NAV_ADDRESSES      = ".woocommerce-MyAccount-navigation-link--edit-address a";
    private static final String NAV_ACCOUNT_DETAIL = ".woocommerce-MyAccount-navigation-link--edit-account a";
    private static final String NAV_LOGOUT         = ".woocommerce-MyAccount-navigation-link--customer-logout a";

    // ─── Dashboard content ────────────────────────────────────────────────────
    private static final String WELCOME_MESSAGE    = ".woocommerce-MyAccount-content p:first-child";
    private static final String EDIT_DETAILS_FORM  = ".woocommerce-EditAccountForm";
    private static final String FIRST_NAME_INPUT   = "#account_first_name";
    private static final String LAST_NAME_INPUT    = "#account_last_name";
    private static final String DISPLAY_NAME_INPUT = "#account_display_name";
    private static final String EMAIL_INPUT        = "#account_email";
    private static final String SAVE_CHANGES_BTN   = "button[name='save_account_details']";

    // ─── Orders section ───────────────────────────────────────────────────────
    private static final String ORDER_ROWS         = ".woocommerce-orders-table tbody tr";
    private static final String NO_ORDERS_NOTICE   = ".woocommerce-message--info";

    public AccountPage(Page page) {
        super(page);
        log.debug("AccountPage instantiated for URL: {}", page.url());
    }

    // ─── Navigation actions ────────────────────────────────────────────────────

    public AccountPage goToOrders() {
        log.info("Navigating to Orders tab.");
        click(NAV_ORDERS);
        waitForPageLoad();
        return this;
    }

    public AccountPage goToAddresses() {
        click(NAV_ADDRESSES);
        waitForPageLoad();
        return this;
    }

    public AccountPage goToAccountDetails() {
        click(NAV_ACCOUNT_DETAIL);
        waitForPageLoad();
        return this;
    }

    public LoginPage logout() {
        log.info("Logging out.");
        click(NAV_LOGOUT);
        waitForPageLoad();
        return new LoginPage(page);
    }

    // ─── Account detail editing ───────────────────────────────────────────────

    public AccountPage updateFirstName(String firstName) {
        waitForVisible(FIRST_NAME_INPUT);
        Locator input = page.locator(FIRST_NAME_INPUT);
        input.fill("");
        input.fill(firstName);
        return this;
    }

    public AccountPage saveAccountDetails() {
        click(SAVE_CHANGES_BTN);
        waitForPageLoad();
        return this;
    }

    // ─── State queries ────────────────────────────────────────────────────────

    /** True when the My Account navigation sidebar is present — confirms auth state. */
    public boolean isDashboardVisible() {
        return page.locator(NAV_MENU).count() > 0 && page.locator(NAV_MENU).isVisible();
    }

    public String getWelcomeMessage() {
        Locator msg = page.locator(WELCOME_MESSAGE);
        return msg.count() > 0 ? msg.textContent().trim() : "";
    }

    public boolean isLoggedOut() {
        return !isDashboardVisible() && isElementVisible("#username");
    }

    public int getOrderCount() {
        return page.locator(ORDER_ROWS).count();
    }

    public boolean hasNoOrders() {
        return page.locator(NO_ORDERS_NOTICE).count() > 0;
    }

    public String getAccountEmail() {
        Locator emailField = page.locator(EMAIL_INPUT);
        return emailField.count() > 0 ? emailField.inputValue() : "";
    }
}
