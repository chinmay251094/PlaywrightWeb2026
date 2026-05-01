package com.automation.tests;

import com.automation.base.BaseTest;
import com.automation.constants.AppConstants;
import com.automation.dataproviders.ExcelDataProvider;
import com.automation.pages.AccountPage;
import com.automation.pages.LoginPage;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Authentication tests — https://askomdch.com/account/
 *
 * All inputs (username, password, expected outcome, browser) come from
 * the "TestSuite" sheet in TestData.xlsx where testMethod = method name.
 */
public class LoginTest extends BaseTest {

    private static final String ACCOUNT_PATH = "/account/";

    // ─── Login (valid + invalid, driven entirely by Excel) ───────────────────

    @Test(
        groups            = { AppConstants.GROUP_SMOKE, AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Submits the login form and validates outcome against expectedResult column"
    )
    public void testLogin(Map<String, String> data) {
        String username      = data.get("username");
        String password      = data.get("password");
        String loginExpected = data.getOrDefault("loginExpected", "success");

        step("Navigate to login page.");
        page.navigate(config.getBaseUrl() + ACCOUNT_PATH);

        SoftAssertions soft = new SoftAssertions();

        if ("success".equalsIgnoreCase(loginExpected)) {
            step("Submit valid credentials — expect dashboard.");
            AccountPage account = loginPage.login(username, password);

            soft.assertThat(account.isDashboardVisible())
                .as("My Account dashboard should appear after valid login").isTrue();
            soft.assertThat(page.url()).as("URL should contain /account/").contains("/account/");

        } else {
            step("Submit invalid credentials — expect error message.");
            LoginPage failed = loginPage.loginExpectingFailure(username, password);

            soft.assertThat(failed.hasErrorMessage())
                .as("An error message must appear for invalid credentials").isTrue();
            soft.assertThat(failed.getErrorMessage())
                .as("Error message should not be blank").isNotBlank();
        }

        soft.assertAll();
    }

    // ─── Login page structure ─────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_SMOKE, AppConstants.GROUP_SANITY },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Verifies both login and register form elements are rendered on the page"
    )
    public void testLoginPageElementsVisible(Map<String, String> data) {
        step("Navigate to login page.");
        page.navigate(config.getBaseUrl() + ACCOUNT_PATH);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(loginPage.isLoginFormVisible())
            .as("Username and password fields must be visible").isTrue();
        soft.assertThat(loginPage.isRegisterFormVisible())
            .as("Register form must also be on the same page").isTrue();
        soft.assertThat(page.title()).containsIgnoringCase("account");
        soft.assertAll();
    }

    // ─── Logout flow ──────────────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Logs in with valid credentials then logs out and confirms session ends"
    )
    public void testLogout(Map<String, String> data) {
        String username = data.get("username");
        String password = data.get("password");

        step("Navigate to login page and log in.");
        page.navigate(config.getBaseUrl() + ACCOUNT_PATH);
        AccountPage account = loginPage.login(username, password);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(account.isDashboardVisible()).as("Dashboard visible after login").isTrue();

        step("Log out.");
        LoginPage loginAfterLogout = account.logout();

        soft.assertThat(loginAfterLogout.isLoginFormVisible())
            .as("Login form should reappear after logout").isTrue();
        soft.assertAll();
    }
}
