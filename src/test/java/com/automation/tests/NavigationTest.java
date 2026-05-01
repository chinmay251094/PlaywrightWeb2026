package com.automation.tests;

import com.automation.base.BaseTest;
import com.automation.constants.AppConstants;
import com.automation.dataproviders.ExcelDataProvider;
import com.automation.pages.StorePage;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Navigation tests — https://askomdch.com/
 *
 * URL, expected title, and browser all come from
 * the "TestSuite" sheet where testMethod = method name.
 */
public class NavigationTest extends BaseTest {

    // ─── Data-driven URL / title validation ──────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_SMOKE, AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Navigates to a URL and validates page title and URL fragment"
    )
    public void testPageNavigation(Map<String, String> data) {
        String relativeUrl      = data.get("url");
        String expectedTitle    = data.getOrDefault("expectedTitle", "");
        String expectedFragment = data.getOrDefault("expectedUrlFragment", "");

        step("Navigate to: " + relativeUrl);
        page.navigate(config.getBaseUrl() + relativeUrl);

        SoftAssertions soft = new SoftAssertions();

        if (!expectedTitle.isBlank()) {
            soft.assertThat(page.title())
                .as("Page title for " + relativeUrl)
                .containsIgnoringCase(expectedTitle);
        }
        if (!expectedFragment.isBlank()) {
            soft.assertThat(page.url())
                .as("URL should contain fragment: " + expectedFragment)
                .contains(expectedFragment);
        }

        soft.assertAll();
    }

    // ─── Home page loads ──────────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_SMOKE, AppConstants.GROUP_SANITY },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Verifies the home page loads with visible branding"
    )
    public void testHomePageLoads(Map<String, String> data) {
        // setUp() already navigated to baseUrl — verify state
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(homePage.isPageLoaded()).as("Home page should be loaded").isTrue();
        soft.assertThat(page.title()).as("Home page title should not be blank").isNotBlank();
        soft.assertAll();
    }

    // ─── Store nav link ───────────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_SANITY },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Clicks the Store nav link and confirms the store page loads with products"
    )
    public void testStoreNavLink(Map<String, String> data) {
        step("Click Store navigation link.");
        StorePage store = homePage.navigateToStore();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(page.url()).as("URL should contain /store/").contains("/store/");
        soft.assertThat(store.getProductCount()).as("Store should have at least one product").isGreaterThan(0);
        soft.assertAll();
    }

    // ─── Account nav link ─────────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_SANITY },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Clicks the Account nav link and confirms the login/account page loads"
    )
    public void testAccountNavLink(Map<String, String> data) {
        step("Click Account navigation link.");
        homePage.navigateToAccount();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(page.url()).as("URL should contain /account/").contains("/account/");
        soft.assertThat(page.title()).containsIgnoringCase("account");
        soft.assertAll();
    }

    // ─── Browser back navigation ──────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Navigates forward then uses browser back and verifies URL reverts"
    )
    public void testBrowserBackNavigation(Map<String, String> data) {
        String homeUrl = page.url();

        step("Navigate to store, then go back.");
        homePage.navigateToStore();
        page.goBack();
        homePage.waitForPageLoad();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(page.url()).as("URL should return to home page").isEqualTo(homeUrl);
        soft.assertAll();
    }
}
