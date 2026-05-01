package com.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the WooCommerce Login / Register page.
 * URL: https://askomdch.com/account/
 *
 * This page serves dual purpose — it shows login AND registration forms.
 * Methods are grouped by form type.
 */
public class LoginPage extends BasePage {

    private static final Logger log = LogManager.getLogger(LoginPage.class);

    // ─── Login form locators ──────────────────────────────────────────────────
    private static final String USERNAME_INPUT      = "#username";
    private static final String PASSWORD_INPUT      = "#password";
    private static final String LOGIN_BTN           = "button[name='login'], .woocommerce-form-login__submit";
    private static final String REMEMBER_ME_CHKBX   = "#rememberme";
    private static final String LOST_PASSWORD_LINK  = "a[href*='lost-password']";

    // ─── Register form locators ───────────────────────────────────────────────
    private static final String REG_EMAIL_INPUT     = "#reg_email";
    private static final String REG_PASSWORD_INPUT  = "#reg_password";
    private static final String REGISTER_BTN        = "button[name='register']";

    // ─── Feedback locators ────────────────────────────────────────────────────
    private static final String ERROR_MESSAGE       = ".woocommerce-error li, .woocommerce-notices-wrapper .woocommerce-error";
    private static final String SUCCESS_MESSAGE     = ".woocommerce-message";
    private static final String MY_ACCOUNT_HEADING  = ".woocommerce-MyAccount-navigation, h1.page-title";

    public LoginPage(Page page) {
        super(page);
        log.debug("LoginPage instantiated.");
    }

    // ─── Login actions ────────────────────────────────────────────────────────

    /**
     * Fills and submits the login form.
     * Returns AccountPage on success — caller should assert destination based on context.
     */
    public AccountPage login(String username, String password) {
        log.info("Attempting login for user: {}", username);
        waitForVisible(USERNAME_INPUT);
        fill(USERNAME_INPUT, username);
        fill(PASSWORD_INPUT, password);
        click(LOGIN_BTN);
        waitForPageLoad();
        return new AccountPage(page);
    }

    /**
     * Submits login and expects to remain on the login page (negative scenario).
     * Useful for invalid-credential tests.
     */
    public LoginPage loginExpectingFailure(String username, String password) {
        log.info("Submitting login expecting failure — user: {}", username);
        waitForVisible(USERNAME_INPUT);
        fill(USERNAME_INPUT, username);
        fill(PASSWORD_INPUT, password);
        click(LOGIN_BTN);
        waitForPageLoad();
        return this;
    }

    /** Checks the 'Remember me' checkbox before logging in. */
    public LoginPage checkRememberMe() {
        page.locator(REMEMBER_ME_CHKBX).check();
        return this;
    }

    public LoginPage clickLostPassword() {
        click(LOST_PASSWORD_LINK);
        waitForPageLoad();
        return this;
    }

    // ─── Register actions ─────────────────────────────────────────────────────

    public AccountPage register(String email, String password) {
        log.info("Registering new account for email: {}", email);
        waitForVisible(REG_EMAIL_INPUT);
        fill(REG_EMAIL_INPUT, email);
        fill(REG_PASSWORD_INPUT, password);
        click(REGISTER_BTN);
        waitForPageLoad();
        return new AccountPage(page);
    }

    // ─── State queries (NO assertions — return data only) ─────────────────────

    public boolean isLoginFormVisible() {
        return isElementVisible(USERNAME_INPUT) && isElementVisible(PASSWORD_INPUT);
    }

    public boolean isRegisterFormVisible() {
        return isElementVisible(REG_EMAIL_INPUT);
    }

    public boolean hasErrorMessage() {
        return page.locator(ERROR_MESSAGE).count() > 0;
    }

    public String getErrorMessage() {
        Locator err = page.locator(ERROR_MESSAGE);
        if (err.count() == 0) return "";
        return err.first().textContent().trim();
    }

    public boolean hasSuccessMessage() {
        return page.locator(SUCCESS_MESSAGE).count() > 0;
    }

    public String getSuccessMessage() {
        Locator msg = page.locator(SUCCESS_MESSAGE);
        if (msg.count() == 0) return "";
        return msg.first().textContent().trim();
    }

    /** Returns true if the browser has landed on the My Account dashboard after login. */
    public boolean isLoggedIn() {
        return getCurrentUrl().contains("/account") && !getCurrentUrl().contains("?redirect_to")
                && page.locator(".woocommerce-MyAccount-navigation").count() > 0;
    }
}
