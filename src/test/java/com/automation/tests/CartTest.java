package com.automation.tests;

import com.automation.base.BaseTest;
import com.automation.constants.AppConstants;
import com.automation.dataproviders.ExcelDataProvider;
import com.automation.pages.CartPage;
import com.automation.pages.StorePage;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Shopping cart tests — https://askomdch.com/cart/
 *
 * Product name, quantity, and browser all come from the
 * "TestSuite" sheet where testMethod = method name.
 */
public class CartTest extends BaseTest {

    // ─── Add product to cart ──────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_SMOKE, AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Adds a product to the cart and verifies cart state and total"
    )
    public void testAddProductToCart(Map<String, String> data) {
        String product = data.get("productName");

        step("Navigate to store and add '" + product + "' to cart.");
        page.navigate(config.getBaseUrl() + "/store/");
        StorePage store = on(StorePage.class);
        store.addProductToCartByName(product);
        CartPage cart = store.clickViewCart();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(cart.isEmpty()).as("Cart should not be empty").isFalse();
        soft.assertThat(cart.containsProduct(product)).as("Cart should contain: " + product).isTrue();
        soft.assertThat(cart.getCartTotal()).as("Cart total should be present").isNotBlank();
        soft.assertAll();
    }

    // ─── Remove product from cart ─────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Adds a product then removes it and verifies cart is empty"
    )
    public void testRemoveProductFromCart(Map<String, String> data) {
        String product = data.getOrDefault("productName", "Blue Shoes");

        step("Add '" + product + "' to cart.");
        page.navigate(config.getBaseUrl() + "/store/");
        on(StorePage.class).addProductToCartByName(product);
        CartPage cart = on(StorePage.class).clickViewCart();

        step("Remove item from cart.");
        cart.removeItem(product);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(cart.isEmpty()).as("Cart should be empty after removal").isTrue();
        soft.assertAll();
    }

    // ─── Update quantity ──────────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_REGRESSION },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Adds a product and updates its quantity; verifies the change persists"
    )
    public void testUpdateCartQuantity(Map<String, String> data) {
        String product  = data.getOrDefault("productName", "Blue Shoes");
        int    quantity = Integer.parseInt(data.getOrDefault("quantity", "2"));

        step("Add '" + product + "' and update quantity to " + quantity + ".");
        page.navigate(config.getBaseUrl() + "/store/");
        on(StorePage.class).addProductToCartByName(product);
        CartPage cart = on(StorePage.class).clickViewCart();
        cart.setItemQuantity(product, quantity).updateCart();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(cart.getItemQuantity(product))
            .as("Quantity should update to " + quantity)
            .isEqualTo(String.valueOf(quantity));
        soft.assertAll();
    }

    // ─── Proceed to checkout ──────────────────────────────────────────────────

    @Test(
        groups            = { AppConstants.GROUP_SMOKE },
        dataProvider      = "testData",
        dataProviderClass = ExcelDataProvider.class,
        description       = "Adds a product and proceeds to checkout; verifies checkout page loads"
    )
    public void testProceedToCheckout(Map<String, String> data) {
        String product = data.getOrDefault("productName", "Blue Shoes");

        step("Add '" + product + "' and proceed to checkout.");
        page.navigate(config.getBaseUrl() + "/store/");
        on(StorePage.class).addProductToCartByName(product);
        on(StorePage.class).clickViewCart().proceedToCheckout();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(page.url()).as("URL should contain /checkout/").contains("/checkout/");
        soft.assertThat(page.title()).containsIgnoringCase("checkout");
        soft.assertAll();
    }
}
