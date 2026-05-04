package com.automation.tests;

import com.automation.base.BaseTest;
import com.automation.constants.AppConstants;
import com.automation.dataproviders.ExcelDataProvider;
import com.automation.pages.StorePage;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Search and store listing tests — https://askomdch.com/store/
 *
 * All inputs (search term, expected product, browser) come from
 * the "TestSuite" sheet where testMethod = method name.
 */
public class SearchTest extends BaseTest {

    // ─── Data-driven search ───────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_SMOKE, AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Searches the site and validates results against expected product / no-results flag"
    )
    public void testSearch(Map<String, String> data) {
        String searchTerm      = data.get("searchTerm");
        String expectedProduct = data.getOrDefault("expectedProduct", "");
        boolean expectResults  = Boolean.parseBoolean(data.getOrDefault("expectSearchResults", "true"));

        step("Navigate to store page.");
        StorePage store = homePage.navigateToStore();

        step("Search for: '" + searchTerm + "'.");
        store.searchFor(searchTerm);

        StorePage results = on(StorePage.class);
        SoftAssertions soft = new SoftAssertions();

        if (expectResults) {
            soft.assertThat(results.getProductCount())
                .as("Search should return at least one result for: " + searchTerm)
                .isGreaterThan(0);

            if (!expectedProduct.isBlank()) {
                soft.assertThat(results.isProductDisplayed(expectedProduct))
                    .as("Expected product '" + expectedProduct + "' should be in results")
                    .isTrue();
            }
        } else {
            soft.assertThat(results.hasNoProductsNotice() || results.getProductCount() == 0)
                .as("No-results notice should appear for: " + searchTerm)
                .isTrue();
        }

        soft.assertAll();
    }

    // ─── Search initiated from the store page ─────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Navigates to the store page then searches and verifies results load"
    )
    public void testSearchFromStorePage(Map<String, String> data) {
        String searchTerm = data.getOrDefault("searchTerm", "Blue");

        step("Navigate to store page.");
        StorePage store = homePage.navigateToStore();

        step("Search from within store page for: '" + searchTerm + "'.");
        store.searchFor(searchTerm);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(on(StorePage.class).getProductCount())
            .as("At least one result expected for: " + searchTerm).isGreaterThan(0);
        soft.assertThat(page.url()).as("URL should reflect the search query").contains("s=");
        soft.assertAll();
    }

    // ─── Store page loads with products ───────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_SANITY },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Opens the store page and confirms products and results count are displayed"
    )
    public void testStorePageLoadsWithProducts(Map<String, String> data) {
        step("Navigate directly to /store/.");
        page.navigate(config.getBaseUrl() + "/store/");
        StorePage store = on(StorePage.class);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(store.getProductCount()).as("Store should have at least one product").isGreaterThan(0);
        soft.assertThat(store.getResultsCount()).as("Results count text should appear").isNotBlank();
        soft.assertAll();
    }
}
