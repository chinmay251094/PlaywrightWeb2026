# Playwright TestNG Hybrid Framework — Complete Technical Documentation

**Version:** 2.0.0  
**Application Under Test:** https://askomdch.com (WooCommerce e-commerce demo)  
**Stack:** Java 17 · Playwright 1.49 · TestNG 7.9 · Maven · Custom HTML Reporter · Log4j2 · Apache POI

---

## Table of Contents

1. [Framework Overview](#1-framework-overview)
2. [Architecture Diagram](#2-architecture-diagram)
3. [Design Decisions](#3-design-decisions)
4. [Folder Structure Explained](#4-folder-structure-explained)
5. [Execution Flow](#5-execution-flow)
6. [Thread Handling Strategy](#6-thread-handling-strategy)
7. [Data Flow — Excel to Test](#7-data-flow--excel-to-test)
8. [Reporting Workflow](#8-reporting-workflow)
9. [Logging Strategy](#9-logging-strategy)
10. [How to Add a New Test Case](#10-how-to-add-a-new-test-case)
11. [How to Add a New Page Object](#11-how-to-add-a-new-page-object)
12. [How to Add New Test Data](#12-how-to-add-new-test-data)
13. [How to Run Tests](#13-how-to-run-tests)
14. [Scalability Considerations](#14-scalability-considerations)
15. [Limitations and Future Enhancements](#15-limitations-and-future-enhancements)

---

## 1. Framework Overview

This is a **Hybrid Test Automation Framework** combining three patterns:

| Pattern | Purpose |
|---|---|
| **Page Object Model (POM)** | Encapsulates all UI interactions per page; test methods stay assertion-only |
| **Data-Driven Testing** | Every test input, browser choice, and run-toggle lives in a single Excel sheet |
| **Utility-Based Design** | Cross-cutting concerns (waits, screenshots, JS, config) in reusable helpers |

### Key design constraints

| Constraint | How it is met |
|---|---|
| Zero flakiness from parallelism | Each thread has its own `Playwright + Browser + Context + Page` via `ThreadLocal` |
| Single point of control | One Excel sheet (`TestSuite`) owns all test data, browser choice, and run toggles |
| No third-party report dependency | Custom zero-dependency HTML reporter; no ExtentReports JAR required |
| No hardcoding | Every value is either in a `.properties` file or the Excel sheet |
| CI/CD ready | Maven profiles + `-D` flags control env/suite; report is a self-contained HTML file |

---

## 2. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CI / CD Pipeline                              │
│          mvn test -P smoke,qa  /  mvn test -P regression            │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Maven Surefire     │  Reads testng.xml
                    │   Plugin             │  Passes -D system props
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │   TestNG Engine      │  Manages suite/test/method lifecycle
                    │                     │  parallel="methods", thread-count=4
                    └──────────┬──────────┘
                               │ fires lifecycle events
            ┌──────────────────┼──────────────────────┐
            │                  │                       │
     ┌──────▼──────┐   ┌───────▼────────┐   ┌────────▼──────────┐
     │  Custom     │   │  TestNG        │   │  RetryAnalyzer    │
     │  Report     │   │  Listener      │   │  (ThreadLocal     │
     │  Listener   │   │  (log banners  │   │   retry counter)  │
     │  (HTML file)│   │   + retry wire)│   └───────────────────┘
     └──────┬──────┘   └────────────────┘
            │ collects TestEntry records
            │ writes HTML on suite finish
            │
            │
     ┌──────▼──────────────────────────────────┐
     │              BaseTest                    │
     │  @BeforeMethod(Object[] params)          │
     │    → reads browser/headless from         │
     │      params[0] (Excel row Map)           │
     │  @AfterMethod                            │
     │    → screenshot on failure               │
     │    → PlaywrightFactory.closeBrowser()    │
     └──────┬──────────────────────────────────┘
            │
            │ one DataProvider feeds all tests
            │
     ┌──────▼──────────────────────────────────┐
     │         ExcelDataProvider               │
     │  @DataProvider(name = "testData")        │
     │  Method param → getDataForMethod(name)   │
     └──────┬──────────────────────────────────┘
            │
     ┌──────▼──────────────────────────────────┐
     │              ExcelUtils                  │
     │  readSheet("TestSuite")                  │
     │  filter rows: testMethod == callerName   │
     │              AND RunFlag == "Y"          │
     └──────┬──────────────────────────────────┘
            │
     ┌──────▼──────────────────────────────────┐
     │         TestData.xlsx / TestSuite sheet  │
     │  ┌────────────┬──────────┬───────────┐  │
     │  │ testMethod │  browser │  RunFlag  │  │
     │  ├────────────┼──────────┼───────────┤  │
     │  │ testLogin  │ chromium │     Y     │  │
     │  │ testLogin  │ firefox  │     Y     │  │
     │  │ testLogin  │ chromium │     N     │  │ ← skipped
     │  └────────────┴──────────┴───────────┘  │
     └─────────────────────────────────────────┘
            │
     ┌──────▼──────────────────────────────────┐
     │              Page Objects                │
     │  BasePage → HomePage / LoginPage /       │
     │  StorePage / ProductPage / CartPage /    │
     │  CheckoutPage / AccountPage              │
     └──────┬──────────────────────────────────┘
            │
     ┌──────▼──────────────────────────────────┐
     │              Utilities                   │
     │  WaitUtils · ActionUtils · JSUtils       │
     │  ScreenshotUtils · ExcelUtils            │
     └──────┬──────────────────────────────────┘
            │
     ┌──────▼────────────────────────────────────────┐
     │             Test Output                        │
     │  test-output/reports/TestReport.html           │
     │  test-output/screenshots/TESTNAME_FAIL.png     │
     │  test-output/logs/automation.log               │
     └────────────────────────────────────────────────┘
```

---

## 3. Design Decisions

### Why a single Excel sheet instead of per-feature sheets?

The earlier design had five sheets (`LoginData`, `SearchData`, `CartData`, etc.), each with its own `@DataProvider` method. This created friction every time a test was added — you had to touch the sheet, the provider, and the test class.

The current design uses one `TestSuite` sheet and one `@DataProvider` method. The `testMethod` column routes each row to the correct test at runtime. Adding a new test case is one row in one file.

| Concern | Multi-sheet approach | Single-sheet approach |
|---|---|---|
| New test case | Add row + new DataProvider | Add row only |
| Toggle a test | Edit the sheet it lives in | Edit `RunFlag` in `TestSuite` |
| Cross-browser run | Duplicate rows across sheets | Add a second row with `browser=firefox` |
| DataProvider methods | One per feature area | One total |

### Single DataProvider with `Method` injection

```java
@DataProvider(name = "testData", parallel = true)
public static Object[][] testData(Method method) {
    return ExcelUtils.getDataForMethod(method.getName());
}
```

TestNG injects the calling `Method` automatically. `ExcelUtils.getDataForMethod` filters the `TestSuite` sheet to rows whose `testMethod` column matches `method.getName()`. Tests get only their own rows — no routing logic needed in tests.

### Browser per Excel row

Browser and headless mode are columns in the Excel sheet, not XML parameters. This means:

- One row with `browser=chromium` + one row with `browser=firefox` = same test runs on two browsers from a single `@Test` annotation
- CI can run cross-browser coverage without separate Maven profiles or XML files

`BaseTest.setUp(Object[] params)` reads `params[0]` (the data `Map`) and passes `browser`/`headless` to `PlaywrightFactory.initBrowser()`.

### Why a custom HTML reporter instead of ExtentReports?

| Concern | ExtentReports | Custom reporter |
|---|---|---|
| External JAR dependency | Yes (7 MB, transitive) | No dependencies |
| Report format control | Configured via API | Full HTML/CSS control |
| Thread safety | Internal sync + ThreadLocal | CopyOnWriteArrayList |
| Report columns | Configurable but verbose | Exact four columns required |
| Failure screenshot embed | Via MediaEntityBuilder | Direct `<img>` path in row |

The custom `CustomReportListener` writes a self-contained HTML file with the exact table the team needs:
> **Test Scenario | Environment (Browser) | Expected Result | Status**

### Why Playwright over Selenium?

| Concern | Playwright | Selenium |
|---|---|---|
| Auto-wait | Built-in on every action | Manual explicit waits |
| Browser isolation | `BrowserContext` = full session isolation | Requires new WebDriver instance |
| Thread safety | Each thread has its own `Playwright` instance | WebDriver has similar requirements |
| Cross-browser | Chromium, Firefox, WebKit | All major browsers |
| Speed | Faster (Chrome DevTools Protocol) | Slower (W3C WebDriver protocol) |

### Why ThreadLocal for browser management?

TestNG's `parallel="methods"` creates a thread pool. Without isolation, two parallel tests would share the same `Page` and corrupt each other's navigation. `ThreadLocal<Playwright>`, `ThreadLocal<Browser>`, `ThreadLocal<BrowserContext>`, and `ThreadLocal<Page>` guarantee each thread sees only its own browser stack.

---

## 4. Folder Structure Explained

```
PlaywrightWebIndustryStandard/
│
├── pom.xml                              # All deps, plugin config, Maven profiles
├── testng.xml                           # Full suite (all groups, 4 threads)
├── testng-smoke.xml                     # Smoke gate (smoke group, 2 threads)
├── testng-regression.xml                # Nightly regression (4 threads)
├── testng-intellij.xml                  # IntelliJ local run (sequential, headed)
│
├── docs/
│   └── FrameworkDocumentation.md        # This document
│
└── src/
    ├── main/java/com/automation/
    │   ├── config/
    │   │   └── ConfigReader.java        # Singleton. Merges base + env .properties + -D flags.
    │   │
    │   ├── constants/
    │   │   └── AppConstants.java        # TESTSUITE_SHEET, REPORT_FILE, timeout values, group names.
    │   │
    │   ├── driver/
    │   │   └── PlaywrightFactory.java   # ThreadLocal browser lifecycle.
    │   │                                # initBrowser(browser, headless) / getPage() / closeBrowser()
    │   │
    │   ├── pages/
    │   │   ├── BasePage.java            # Common nav helpers, waitForPageLoad, utility instances.
    │   │   │                            # Rule: NO assertions in any page class.
    │   │   ├── HomePage.java
    │   │   ├── LoginPage.java
    │   │   ├── StorePage.java
    │   │   ├── ProductPage.java
    │   │   ├── CartPage.java
    │   │   ├── CheckoutPage.java
    │   │   └── AccountPage.java
    │   │
    │   ├── utils/
    │   │   ├── WaitUtils.java           # Condition-based / fluent waits, no Thread.sleep()
    │   │   ├── ActionUtils.java         # Click, fill, hover — all with logging + masking
    │   │   ├── JSUtils.java             # Scroll, storage, computed-style JS execution
    │   │   ├── ScreenshotUtils.java     # Full-page capture; timestamped thread-safe file names
    │   │   └── ExcelUtils.java          # Apache POI reader; getDataForMethod() routes by testMethod
    │   │
    │   └── exceptions/
    │       ├── FrameworkException.java  # Root unchecked exception — wraps infrastructure errors
    │       └── ConfigException.java     # Thrown on missing / malformed config
    │
    └── test/
        ├── java/com/automation/
        │   ├── base/
        │   │   └── BaseTest.java        # setUp(Object[] params): reads browser from Excel row
        │   │                            # tearDown: screenshot on fail, browser close
        │   │
        │   ├── dataproviders/
        │   │   └── ExcelDataProvider.java  # Single @DataProvider("testData") with Method param
        │   │
        │   ├── listeners/
        │   │   ├── CustomReportListener.java  # Writes TestReport.html — the only reporter
        │   │   ├── TestNGListener.java        # Log4j2 lifecycle banners + dynamic retry wiring
        │   │   ├── RetryAnalyzer.java         # ThreadLocal retry counter, max 2 retries
        │   │   └── ExtentReportListener.java  # Empty stub — superseded, kept for IDE compat
        │   │
        │   ├── tests/
        │   │   ├── LoginTest.java       # testLogin, testLoginPageElementsVisible, testLogout
        │   │   ├── SearchTest.java      # testSearch, testSearchFromStorePage, testStorePageLoadsWithProducts
        │   │   ├── CartTest.java        # testAddProductToCart, testRemoveProductFromCart,
        │   │   │                        # testUpdateCartQuantity, testProceedToCheckout
        │   │   ├── NavigationTest.java  # testPageNavigation, testHomePageLoads, testStoreNavLink,
        │   │   │                        # testAccountNavLink, testBrowserBackNavigation
        │   │   └── AccountTest.java     # testAccountDashboardVisible, testOrdersTabNavigation,
        │   │                            # testAddressesTabNavigation, testAccountDetailsPageLoads,
        │   │                            # testLogoutFromAccountPage
        │   │
        │   └── utils/
        │       └── ExcelDataGenerator.java  # Single TEST_DATA[][] table → one generateTestData() call
        │
        └── resources/
            ├── config/
            │   ├── config.properties    # Base defaults (browser=chromium, headless=true)
            │   ├── qa.properties        # QA: base URL, test credentials
            │   ├── uat.properties       # UAT: base URL, credentials, headless
            │   └── prod.properties      # PROD: read-only credentials, smoke only
            ├── testdata/
            │   └── TestData.xlsx        # Single "TestSuite" sheet — the execution control panel
            └── log4j2.xml               # Console (INFO+) + rolling file (DEBUG+) + errors file
```

---

## 5. Execution Flow

```
1. mvn test  (or right-click testng-intellij.xml in IntelliJ)
   └── Maven Surefire reads testng.xml
       └── -Denv, -Dbrowser (if supplied) are passed as system properties

2. TestNG Engine starts suite
   └── CustomReportListener.onStart(suite)  → clears results list
   └── TestNGListener.onStart(suite)        → logs suite banner

3. TestNG creates thread pool (thread-count=4)

4. Thread N picks up a test method (e.g. LoginTest.testLogin)
   └── TestNGListener.onTestStart()   → log banner
   └── CustomReportListener.onTestStart() — no-op (data captured at pass/fail)

5. ExcelDataProvider.testData(Method method) is called
   └── ExcelUtils.getDataForMethod("testLogin")
       └── Opens TestData.xlsx
       └── Reads "TestSuite" sheet
       └── Filters: testMethod == "testLogin" AND RunFlag == "Y"
       └── Returns Object[][] — each element is one Map<String,String> row
   If 0 rows returned → TestNG reports 0 invocations (test skipped automatically)

6. TestNG calls BaseTest.setUp(Object[] params) [@BeforeMethod]
   └── params[0] = Map{"browser":"chromium","headless":"false","username":"demouser",...}
   └── browser  = map.get("browser")   → "chromium"
   └── headless = map.get("headless")  → false
   └── PlaywrightFactory.initBrowser("chromium", false)
       └── Playwright.create()          → ThreadLocal<Playwright>
       └── playwright.chromium().launch → ThreadLocal<Browser>
       └── browser.newContext()         → ThreadLocal<BrowserContext>
       └── context.newPage()            → ThreadLocal<Page>
   └── All page objects instantiated against the same Page
   └── page.navigate(base.url)

7. @Test method executes
   └── Reads data fields from map: username, password, loginExpected, etc.
   └── Calls page object methods (Playwright auto-wait on every action)
   └── AssertJ SoftAssertions accumulates all failures before throwing

8. BaseTest.tearDown(ITestResult result) [@AfterMethod]
   └── If FAILED → ScreenshotUtils.captureOnFailure(page, testName)
   └── PlaywrightFactory.closeBrowser()
       └── ctx.close() → browser.close() → playwright.close()
       └── ThreadLocal.remove() x4
   └── RetryAnalyzer.reset()

   If RetryAnalyzer returned true (attempt < 2):
   └── Steps 6-8 repeat on same thread

9. CustomReportListener.onTestSuccess/Failure/Skipped
   └── Reads description, browser, headless, expectedResult from result.getParameters()[0]
   └── Adds TestEntry(testCaseId, description, browser, headless,
                      expectedResult, status, durationMs, failureReason)
       to CopyOnWriteArrayList<TestEntry>

10. After all tests finish:
    └── CustomReportListener.onFinish(suite)
        └── Counts PASSED / FAILED / SKIPPED
        └── Builds HTML page (header, summary cards, table rows)
        └── Writes test-output/reports/TestReport.html
    └── Log4j2 flushes rolling file → test-output/logs/automation.log
```

---

## 6. Thread Handling Strategy

### The problem

`parallel="methods"` with `thread-count=4` means up to four test methods run simultaneously. Playwright's `Playwright`, `Browser`, `BrowserContext`, and `Page` are not thread-safe. Sharing any of them causes race conditions.

### The solution: ThreadLocal isolation

```
Thread 1 → ThreadLocal[Playwright₁, Browser₁, Context₁, Page₁]
Thread 2 → ThreadLocal[Playwright₂, Browser₂, Context₂, Page₂]
Thread 3 → ThreadLocal[Playwright₃, Browser₃, Context₃, Page₃]
Thread 4 → ThreadLocal[Playwright₄, Browser₄, Context₄, Page₄]
```

`PlaywrightFactory.getPage()` always returns the calling thread's own `Page`.

### Report thread safety

`CustomReportListener` uses a `CopyOnWriteArrayList<TestEntry>`. Parallel test threads append entries concurrently without locking. The list is read exactly once in `onFinish()` after all threads complete.

### RetryAnalyzer thread safety

`ThreadLocal<Integer>` counter — each thread tracks its own retry count independently. Reset in `@AfterMethod` so the counter does not carry over to the next test on the same thread.

### ConfigReader thread safety

Singleton initialised with double-checked locking (`volatile + synchronized`). After first init, all threads read from the same immutable `Properties` object. `Properties.getProperty()` is thread-safe for reads.

### Memory hygiene

`PlaywrightFactory.closeBrowser()` calls `ThreadLocal.remove()` on all four references. Without this, closed browser objects stay referenced until the thread pool reuses the thread, creating a soft memory leak in long suites.

---

## 7. Data Flow — Excel to Test

### The TestSuite sheet (single source of truth)

```
TestData.xlsx  →  sheet "TestSuite"
─────────────────────────────────────────────────────────────────────
│ testCaseId │ testMethod │ description       │ browser  │ headless │
│ RunFlag    │ username   │ password          │ ...      │ ...      │
─────────────────────────────────────────────────────────────────────
│ TC_L_001   │ testLogin  │ Valid login...    │ chromium │ false    │
│     Y      │ demouser   │ demopass123       │          │          │
─────────────────────────────────────────────────────────────────────
│ TC_L_002   │ testLogin  │ Wrong password... │ firefox  │ true     │
│     Y      │ demouser   │ wrongpassword123  │          │          │
─────────────────────────────────────────────────────────────────────
│ TC_L_004   │ testLogin  │ Empty username... │ chromium │ true     │
│     N      │            │ demopass123       │          │          │  ← skipped
─────────────────────────────────────────────────────────────────────
```

### Routing flow

```
ExcelDataProvider.testData(Method method)
  │
  └── ExcelUtils.getDataForMethod(method.getName())  // "testLogin"
        │
        ├── Opens TestData.xlsx
        ├── Reads all rows from "TestSuite"
        ├── Filters: testMethod == "testLogin"
        ├── Drops:   RunFlag != "Y"
        └── Returns Object[][] = [ [Map₁], [Map₂] ]
                                     │       │
                              TC_L_001   TC_L_002
                              (chromium) (firefox)

TestNG calls testLogin(Map₁)  → Chromium, demouser / demopass123 → expects success
TestNG calls testLogin(Map₂)  → Firefox,  demouser / wrongpassword → expects error
TC_L_004 (RunFlag=N) is never returned → test never invoked
```

### Browser per row

Because `browser` is a column in the sheet, a single `@Test` annotation naturally runs on multiple browsers by adding rows:

| testCaseId | testMethod | browser | RunFlag |
|---|---|---|---|
| TC_CART_001 | testAddProductToCart | chromium | Y |
| TC_CART_002 | testAddProductToCart | firefox  | Y |

No XML profiles, no separate test classes, no code changes.

---

## 8. Reporting Workflow

```
CustomReportListener (implements ISuiteListener + ITestListener)
│
├── onStart(suite)
│     └── results.clear()   ← fresh CopyOnWriteArrayList for the run
│
├── Per test (each thread, concurrent):
│   ├── onTestSuccess(result)
│   │     └── results.add(TestEntry{..., status="PASSED"})
│   │
│   ├── onTestFailure(result)
│   │     ├── ScreenshotUtils.captureOnFailure(page, name)
│   │     └── results.add(TestEntry{..., status="FAILED", failureReason=...})
│   │
│   └── onTestSkipped(result)
│         └── results.add(TestEntry{..., status="SKIPPED"})
│
└── onFinish(suite)
      └── Counts pass / fail / skip / total
      └── buildPage(...)  ← generates the full HTML string
      └── Files.writeString(REPORT_FILE, html)
          ↓
      test-output/reports/TestReport.html
```

### Report HTML structure

```
┌──────────────────────────────────────────────────────────────┐
│  ▶ Automation Test Report                                    │
│  Suite: ... | Environment: QA | Generated: dd MMM yyyy HH:mm│
├──────────┬──────────┬──────────┬──────────┬──────────────────┤
│  Total   │  Passed  │  Failed  │  Skipped │  Pass rate: 92%  │
│    28    │    26    │    1     │    1     │                  │
├──────────┴──────────┴──────────┴──────────┴──────────────────┤
│ # │ Test Scenario          │ Environment (Browser) │ Expected │
│   │                        │                       │ Result   │ Status   │
├───┼────────────────────────┼───────────────────────┼──────────┼──────────┤
│ 1 │ TC001                  │ Chromium   Headed     │ Dashboard│ PASSED   │
│   │ Valid login with...    │                       │ visible  │          │
├───┼────────────────────────┼───────────────────────┼──────────┼──────────┤
│ 2 │ TC002                  │ Firefox    Headless   │ Error msg│ FAILED   │
│   │ Wrong password...      │                       │ shown    │          │
│   │ ⚠ AssertionError: ...  │                       │          │          │
└───┴────────────────────────┴───────────────────────┴──────────┴──────────┘
```

Failure rows show the truncated exception message inline, coloured in red, below the scenario text.

---

## 9. Logging Strategy

Three Log4j2 appenders serve distinct audiences:

| Appender | Target | Level | Audience |
|---|---|---|---|
| `ConsoleAppender` | STDOUT | INFO+ | CI console / developer terminal |
| `FileAppender` | `automation.log` (rolling) | DEBUG+ | Full post-run diagnostic trace |
| `ErrorFileAppender` | `errors.log` (rolling) | ERROR+ | Fast scan of failures only |

**Logger hierarchy:**

```
com.automation.*     → DEBUG → Console + File + ErrorFile
com.microsoft.playwright → INFO → File only (very verbose at DEBUG)
org.apache.poi       → WARN → File only
org.testng           → WARN → Console + File
Root                 → INFO → Console + File
```

Every browser initialisation, navigation, click, fill, and test lifecycle event is logged automatically by the utility and listener classes.

---

## 10. How to Add a New Test Case

**Example:** Add a test verifying the product price on the detail page.

### Step 1 — Add a row to `TEST_DATA` in `ExcelDataGenerator`

```java
{ "TC_PRICE_001", "testProductPrice",
  "Product detail page shows correct price",
  "chromium", "false",
  "Price on detail page matches listing price",
  "Y",
  "", "", "",               // username / password / loginExpected
  "", "", "",               // searchTerm / expectedProduct / expectSearchResults
  "Blue Shoes", "",         // productName / quantity
  "", "", "" },             // url / expectedTitle / expectedUrlFragment
```

Re-run the generator: `mvn exec:java -D exec.mainClass=com.automation.utils.ExcelDataGenerator`

### Step 2 — Write the test method

In `SearchTest.java` (or a new `PriceTest.java`):

```java
@Test(
    groups            = { AppConstants.GROUP_REGRESSION },
    dataProvider      = "testData",
    dataProviderClass = ExcelDataProvider.class,
    description       = "Verifies product price consistency between listing and detail page"
)
public void testProductPrice(Map<String, String> data) {
    String product = data.get("productName");

    step("Navigate to store, open product detail.");
    page.navigate(config.getBaseUrl() + "/store/");
    ProductPage detail = on(StorePage.class).openProductByName(product);

    assertThat(detail.getProductPrice()).as("Price should not be empty").isNotBlank();
}
```

### Step 3 — Add to testng.xml (if a new class)

```xml
<class name="com.automation.tests.PriceTest"/>
```

That is the complete workflow. No DataProvider method to add, no sheet to create, no config to touch.

---

## 11. How to Add a New Page Object

```java
package com.automation.pages;

import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WishlistPage extends BasePage {

    private static final Logger log = LogManager.getLogger(WishlistPage.class);

    // 1. All locators are private static final String constants
    private static final String WISHLIST_ITEMS  = ".wishlist-item";
    private static final String REMOVE_BTN      = ".remove-wishlist-item";

    public WishlistPage(Page page) { super(page); }

    // 2. Action methods — return page objects for chaining
    public WishlistPage removeItem(String name) { /* ... */ return this; }

    // 3. State query methods — NO assertions, return raw values
    public int  getItemCount()              { return page.locator(WISHLIST_ITEMS).count(); }
    public boolean containsProduct(String n){ /* ... */ return false; }
}
```

**Rules:**
- `extends BasePage` — always
- Every locator is a `private static final String` at the top
- No assertions inside page classes — tests assert, pages describe
- Methods that navigate to another page return that page's type

---

## 12. How to Add New Test Data

### Controlled entirely from `ExcelDataGenerator.TEST_DATA`

| Goal | Action |
|---|---|
| Add a new test case | Append a row to `TEST_DATA` with `RunFlag = "Y"` |
| Skip a test case | Change its `RunFlag` from `"Y"` to `"N"` |
| Run the same test on Firefox | Add a second row with the same `testMethod`, `browser = "firefox"` |
| Run a test headed | Set `headless = "false"` on that row |

After any change, regenerate the Excel:

```bash
mvn exec:java -D exec.mainClass=com.automation.utils.ExcelDataGenerator
```

The `ExcelUtils.getDataForMethod()` filters on `testMethod` AND `RunFlag == "Y"`, so disabled rows are silently excluded — the test simply never runs rather than being marked skipped.

---

## 13. How to Run Tests

### One-time setup

```bash
# Install Playwright browsers (once per machine)
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI \
              -D exec.args="install --with-deps"

# Generate TestData.xlsx (once, or after editing TEST_DATA)
mvn exec:java -D exec.mainClass=com.automation.utils.ExcelDataGenerator
```

### Local execution

```bash
# Full suite, QA env, defaults from Excel (chromium, mix of headed/headless)
mvn test

# Smoke suite only
mvn test -P smoke

# Regression suite
mvn test -P regression

# UAT environment
mvn test -P uat

# Single test class
mvn test -Dtest=LoginTest

# Single test method
mvn test -Dtest=LoginTest#testLogin
```

### IntelliJ IDEA (direct)

1. Right-click `testng-intellij.xml` → **Run**  
   or  
2. **Run → Edit Configurations → + → TestNG → Suite** → select `testng-intellij.xml`  
   Set **Working directory** = `$MODULE_WORKING_DIR$`

> The `testng-intellij.xml` uses `parallel="false"` and `thread-count="1"` for sequential, debuggable execution. Browser/headless are still read from Excel rows — change them there, not in the XML.

### GitHub Actions

```yaml
name: Regression Suite
on:
  push:
    branches: [main, develop]

jobs:
  regression:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: actions/cache@v4
        with: { path: ~/.m2, key: "${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}" }
      - name: Install Playwright browsers
        run: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install --with-deps"
      - name: Generate test data
        run: mvn exec:java -D exec.mainClass=com.automation.utils.ExcelDataGenerator
      - name: Run regression
        run: mvn test -P regression
        env:
          TEST_USER_USERNAME: ${{ secrets.TEST_USER_USERNAME }}
          TEST_USER_PASSWORD: ${{ secrets.TEST_USER_PASSWORD }}
      - name: Upload report
        if: always()
        uses: actions/upload-artifact@v4
        with: { name: test-report, path: test-output/reports/ }
      - name: Upload screenshots
        if: failure()
        uses: actions/upload-artifact@v4
        with: { name: failure-screenshots, path: test-output/screenshots/ }
```

---

## 14. Scalability Considerations

### Increasing parallelism

Raise `thread-count` in `testng.xml`. Each thread consumes one browser process (~200 MB RAM). On a 16-core / 32 GB host, `thread-count=12` is a practical upper bound for Chromium.

### Cross-browser scaling via Excel rows

Adding a Firefox row for every existing test doubles the run scope without any code change — only the Excel `TEST_DATA` table grows.

### Remote browser grid

Replace `playwright.chromium().launch()` in `PlaywrightFactory` with:
```java
playwright.chromium().connect("ws://playwright-grid-host:9222")
```
This offloads browser processes to a dedicated grid and allows much higher parallelism.

### Large test suites

`ExcelUtils` loads the entire sheet into memory. For sheets with more than 5 000 rows, switch to SAX-based streaming via `XSSFSheetXMLHandler` to avoid heap pressure.

---

## 15. Limitations and Future Enhancements

| Limitation | Impact | Workaround |
|---|---|---|
| No API test layer | Cart/auth pre-conditions require full UI setup | Add RestAssured-backed `ApiClient` |
| `ExcelDataGenerator` rewrite required for new tests | Developers must edit Java to add rows | Migrate to reading from a CSV the team edits directly |
| No Playwright Tracing | No step-by-step replay of failures | Set `trace.enabled=true` in config |
| No visual regression | No pixel comparison | Integrate Percy or Playwright screenshot assertions |
| Hardcoded credential fallback in `qa.properties` | Credentials in source control | Inject via CI secrets / environment variables |

### Planned enhancements

1. **API setup utility** — RestAssured-backed pre-auth so tests skip login UI for non-auth scenarios
2. **Playwright Trace Viewer** — auto-collect `.zip` trace on failure; upload as CI artifact
3. **CSV-driven data** — replace the in-code `TEST_DATA` table with a CSV the team edits in Excel directly (no Java change to add a row)
4. **Allure adapter** — write an `ITestListener` that emits Allure results alongside the custom report for historical trending
5. **Mobile emulation** — extend `PlaywrightFactory` with `initMobileBrowser(deviceName)` using Playwright device descriptors

---

*For questions or contributions, raise a ticket in the project issue tracker.*
