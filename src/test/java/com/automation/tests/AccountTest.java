package com.automation.tests;

import com.automation.base.BaseTest;
import com.automation.constants.AppConstants;
import com.automation.dataproviders.ExcelDataProvider;
import com.automation.pages.AccountPage;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * My Account tests — https://askomdch.com/account/
 *
 * Credentials and browser all come from the "TestSuite" sheet
 * where testMethod = method name.
 */
public class AccountTest extends BaseTest {

    private static final String ACCOUNT_PATH = "/account/";

    private AccountPage loginWith(Map<String, String> data) {
        page.navigate(config.getBaseUrl() + ACCOUNT_PATH);
        return loginPage.login(data.get("username"), data.get("password"));
    }

    // ─── Dashboard visible ────────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_SMOKE, AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Logs in and verifies the My Account dashboard sidebar is rendered"
    )
    public void testAccountDashboardVisible(Map<String, String> data) {
        step("Login and check dashboard.");
        AccountPage account = loginWith(data);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(account.isDashboardVisible()).as("Dashboard sidebar should be visible").isTrue();
        soft.assertThat(account.getWelcomeMessage()).as("Welcome message should not be blank").isNotBlank();
        soft.assertAll();
    }

    // ─── Orders tab ───────────────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Navigates to the Orders tab and verifies the URL updates to /orders/"
    )
    public void testOrdersTabNavigation(Map<String, String> data) {
        AccountPage account = loginWith(data);
        step("Click Orders tab.");
        account.goToOrders();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(page.url()).as("URL should contain /orders/").contains("/orders/");
        soft.assertAll();
    }

    // ─── Addresses tab ────────────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Navigates to the Addresses tab and verifies the URL updates"
    )
    public void testAddressesTabNavigation(Map<String, String> data) {
        AccountPage account = loginWith(data);
        step("Click Addresses tab.");
        account.goToAddresses();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(page.url()).as("URL should contain edit-address").contains("/edit-address/");
        soft.assertAll();
    }

    // ─── Account details page ─────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Opens Account Details tab and checks email field is pre-filled"
    )
    public void testAccountDetailsPageLoads(Map<String, String> data) {
        AccountPage account = loginWith(data);
        step("Click Account Details tab.");
        account.goToAccountDetails();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(page.url()).as("URL should contain /edit-account/").contains("/edit-account/");
        soft.assertThat(account.getAccountEmail()).as("Email field should be pre-filled").isNotBlank();
        soft.assertAll();
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Logs in and then logs out; verifies login form reappears"
    )
    public void testLogoutFromAccountPage(Map<String, String> data) {
        AccountPage account = loginWith(data);
        step("Log out from account page.");
        account.logout();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(loginPage.isLoginFormVisible()).as("Login form should reappear").isTrue();
        soft.assertAll();
    }
}
