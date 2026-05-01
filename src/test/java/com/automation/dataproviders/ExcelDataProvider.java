package com.automation.dataproviders;

import com.automation.utils.ExcelUtils;
import org.testng.annotations.DataProvider;

import java.lang.reflect.Method;

/**
 * Single DataProvider entry point for the entire framework.
 *
 * One method handles all tests — it receives the calling test's Method
 * object from TestNG and asks ExcelUtils to return only the rows in the
 * "TestSuite" sheet whose "testMethod" column matches the method name.
 *
 * Usage in every test class:
 *   @Test(dataProvider = "testData", dataProviderClass = ExcelDataProvider.class)
 *   public void myTest(Map<String, String> data) { ... }
 *
 * The Excel sheet controls:
 *   - which tests run         (RunFlag = Y / N)
 *   - which browser to use    (browser column)
 *   - headless/headed mode    (headless column)
 *   - all test-specific data  (username, searchTerm, productName, …)
 */
public class ExcelDataProvider {

    private ExcelDataProvider() { /* static provider */ }

    /**
     * TestNG injects the calling Method automatically when the DataProvider
     * method declares a {@code Method} parameter.
     *
     * parallel=false (the default) is intentional.
     * Setting parallel=true spawns a separate DataProvider thread pool that
     * is independent of the suite thread-count. This causes setUp(@BeforeMethod)
     * to run in a DataProvider thread while the test executes in a different
     * TestNG thread — the ThreadLocal<Page> created in setUp is then invisible
     * to the test, resulting in "Playwright connection closed" crashes.
     * Parallelism across test METHODS is already handled by parallel="methods"
     * in testng.xml; the DataProvider must remain sequential.
     */
    @DataProvider(name = "testData")
    public static Object[][] testData(Method method) {
        return ExcelUtils.getDataForMethod(method.getName());
    }
}
