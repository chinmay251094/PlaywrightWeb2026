# How This Framework Works — A Plain English Guide

> This document explains the entire automation framework from scratch.  
> No prior automation experience is needed to understand it.  
> If you are a developer, tester, or manager who wants to know **what was built, why, and how it all fits together** — this is the document for you.

---

## The Big Picture — What Are We Actually Doing?

When a team ships software, they need to make sure the website still works after every change. Doing that manually — opening a browser, clicking through every feature, filling in every form — is slow, error-prone, and needs to be done over and over again.

This framework **automates that clicking**. It opens a real browser (Chrome, Firefox, or Safari), visits the website, performs actions just like a human would, checks whether the result was correct, and produces a report at the end.

The website being tested is **AskOmDch** (`https://askomdch.com`) — an e-commerce store where users can browse products, add them to a cart, check out, and manage their account.

---

## The Toolbox — What Each Tool Does and Why We Picked It

Think of this framework as a kitchen. Different tools handle different jobs. Here is what each one does:

---

### Playwright — The Hands That Drive the Browser

**What it is:** A library that can open and control a real web browser from Java code.

**Analogy:** Imagine a robot that sits at a computer, opens Chrome, types "demouser" into the username field, clicks "Login", and reads what appears on screen. Playwright **is** that robot.

**Why Playwright over alternatives (like Selenium)?**
Selenium is the older, more traditional choice. Playwright is newer and solves several common problems:
- It **waits automatically** — if a button is loading, Playwright waits for it rather than crashing immediately
- It supports **Chromium, Firefox, and WebKit (Safari engine)** out of the box
- It is significantly **faster and more reliable** on modern websites
- Each test gets its own completely **isolated browser session** — like opening a fresh private window each time

---

### TestNG — The Manager / Scheduler

**What it is:** A testing framework that organises, runs, and reports on test methods.

**Analogy:** Think of TestNG as the manager of a call centre. It decides:
- Which agents (tests) are working today
- How many calls (tests) run at the same time
- What happens if an agent fails (retry logic)
- When to produce the daily summary report

**What it specifically does here:**
- Reads `testng.xml` to know which tests to run and how many at a time
- Calls `@BeforeMethod` (setup) before each test and `@AfterMethod` (teardown) after
- Tags tests into groups (`smoke`, `regression`, `sanity`) so you can run just a subset
- Handles retrying failed tests automatically up to 2 times

---

### Maven — The Project Builder

**What it is:** A tool that downloads all required libraries and builds the project.

**Analogy:** Maven is like a shopping list and kitchen setup in one. Before cooking, Maven makes sure every ingredient (library) is in the cupboard. If something is missing, it goes and fetches it from the internet. It also defines the recipe (build process).

**Why it matters:** You can run the entire test suite with a single command:
```
mvn test
```
Maven figures out everything else — which libraries are needed, what Java version to use, and which test suite file to read.

**Profiles** let you switch context by appending flags:
```
mvn test -P smoke        ← run only smoke tests
mvn test -P uat          ← run against UAT environment
```

---

### Excel (TestData.xlsx) — The Control Panel

**What it is:** A single Excel file with one sheet called `TestSuite` that contains every test case the framework will run.

**Analogy:** Think of the Excel sheet as **mission control**. Every row is one mission (test case). The columns answer:
- **What** should be tested (`description`)
- **Which test method** to use (`testMethod`)
- **Which browser** to use (`browser`)
- **Should it run** today or not (`RunFlag`)
- **What data** does it need (credentials, search terms, product names, etc.)

**The most important column is `RunFlag`:**
- `Y` → this test will run
- `N` → this test is skipped (but the row stays so you can switch it back)

**Why Excel?**
Most teams already know how to use Excel. A non-developer can open the file, change `Y` to `N` to disable a test, change `chromium` to `firefox` to test on a different browser, or update a password — all without touching a single line of Java code.

---

### Page Object Model (POM) — The Map of the Website

**What it is:** A design pattern where each page of the website has its own Java class that describes what is on that page and what you can do there.

**Analogy:** Imagine a tour guide who has memorised every room in a building. You say "go to the login room and enter the username", and the guide knows exactly where the username field is and how to type into it. You never have to think about the technical details of *where* things are on the page — the guide handles it.

**Pages modelled in this framework:**

| Page Class | Website Page | What It Covers |
|---|---|---|
| `HomePage` | https://askomdch.com/ | Logo, featured products, shop CTA |
| `LoginPage` | /account/ | Login form, register form, error messages |
| `StorePage` | /store/ | Product listing, search, add to cart |
| `ProductPage` | /product/{name}/ | Product detail, quantity, add to cart |
| `CartPage` | /cart/ | Cart items, update, remove, proceed to checkout |
| `CheckoutPage` | /checkout/ | Billing form, payment, place order |
| `AccountPage` | /account/ (logged in) | Dashboard, orders, logout |

**The rule:** Page classes are only allowed to **describe** the page and **perform actions** on it. They are never allowed to **make assertions** (check if something is right or wrong). That job belongs to the tests themselves.

---

### ExcelDataProvider — The Messenger

**What it is:** A single Java method that fetches the correct rows from the Excel sheet for whichever test is about to run.

**Analogy:** Imagine a delivery person who knows the entire TestSuite sheet. Before any test runs, TestNG asks: "What data does `testLogin` need today?" The delivery person goes to the Excel sheet, finds every row where `testMethod = testLogin` AND `RunFlag = Y`, and hands that data to the test.

**Key design choice — one DataProvider for everything:**  
Earlier frameworks have one DataProvider per feature (one for login, one for search, one for cart). This creates maintenance overhead — every new feature needs a new provider method. Here, **one method handles all tests** by using the test method name as the lookup key against the `testMethod` column in Excel.

```
Test calls testLogin()
    ↓
DataProvider looks up: rows where testMethod = "testLogin" AND RunFlag = "Y"
    ↓
Returns: [TC_LOGIN_001 row, TC_LOGIN_002 row, TC_LOGIN_003 row]
    ↓
TestNG runs testLogin() three times — once per row
```

---

### BaseTest — The Common Setup and Teardown

**What it is:** A Java class that every test class inherits from. It handles what happens before and after each test.

**Analogy:** Think of BaseTest as the standard operating procedure before and after every shift at a shop. Before a test: unlock the doors, turn on the lights, set up the till (open the browser, navigate to the site). After a test: close the till, turn off the lights, lock up (close the browser, take a screenshot if something went wrong).

**Before every test (`@BeforeMethod`):**
1. Read the Excel row to find out which browser to use
2. Open that browser (Chromium, Firefox, or WebKit)
3. Navigate to the base URL
4. Create instances of all page objects (HomePage, LoginPage, etc.)

**After every test (`@AfterMethod`):**
1. If the test failed — take a screenshot and save it with a timestamp
2. Close the browser completely (no session leaks)
3. Reset the retry counter

---

### PlaywrightFactory — The Browser Workshop

**What it is:** A class that creates and manages browser instances for each running thread.

**The problem it solves:** When tests run in parallel (4 at a time), four tests each need their own browser. If they share one browser, Test 1 might navigate to the login page while Test 2 is halfway through checkout — complete chaos.

**The solution — ThreadLocal:**  
`ThreadLocal` is a Java mechanism that gives each thread its own private copy of a variable. This framework uses it to give each running test its own private `Playwright instance → Browser → Browser Context → Page`.

**Analogy:** Imagine four drivers (threads) in a car-share company. Instead of making them share one car and argue over the steering wheel, you give each driver their own car for the duration of their shift. When the shift ends, they park the car and the keys are returned. `ThreadLocal` is the key cabinet — each driver picks up their own key and nobody else can touch it.

---

### ConfigReader — The Settings Manager

**What it is:** A class that reads configuration values from `.properties` files so that nothing is hardcoded in the test code.

**What it reads:**
- The base URL (`https://askomdch.com`)
- Default browser and headless setting (when not specified in Excel)
- Test user credentials (for account tests)
- Timeout values

**Why multiple properties files?**  
The same tests need to run against different environments — QA, UAT, and Production. Each has a different URL and possibly different credentials. Instead of changing code every time you switch environments, you switch the properties file:

```
config.properties   ← base defaults (always loaded first)
qa.properties       ← QA-specific overrides (URL, credentials)
uat.properties      ← UAT-specific overrides
prod.properties     ← Production-specific overrides
```

Loading order matters: base config loads first, then the environment-specific file overwrites any matching values. JVM `-D` flags (e.g. `-Denv=uat`) override everything.

---

### Utilities — The Specialist Tools

These are helper classes that the tests and page objects use repeatedly:

| Utility | What It Does | Analogy |
|---|---|---|
| `WaitUtils` | Waits for elements to appear or disappear | A patient assistant who watches the page until a button is ready |
| `ActionUtils` | Clicks, types, selects — all UI interactions with logging | A secretary who logs every action they take |
| `JSUtils` | Runs JavaScript in the browser for things normal clicks can't do | A back-door maintenance worker |
| `ScreenshotUtils` | Takes a picture of the browser on failure | A crime scene photographer |
| `ExcelUtils` | Reads and filters the TestSuite Excel sheet | The librarian for the test data |

---

### CustomReportListener — The Report Card Writer

**What it is:** A class that watches every test as it runs and writes a final HTML report.

**What the report looks like:**

```
┌────────────────────────────────────────────────────────────────┐
│  Automation Test Report                                        │
│  Suite: Full Suite │ Environment: QA │ Generated: 01 May 2026 │
├──────┬──────┬──────┬──────┬──────────────────────────────────┤
│  28  │  25  │  2   │  1   │  Pass rate: 89.3%                │
│Total │ Pass │ Fail │ Skip │                                   │
├──────┴──────┴──────┴──────┴──────────────────────────────────┤
│ # │ Test Scenario         │ Browser  │ Expected Result  │ Status │
├───┼───────────────────────┼──────────┼──────────────────┼────────┤
│ 1 │ Valid login with      │ Chromium │ Dashboard        │ PASSED │
│   │ correct credentials   │ Headed   │ visible          │        │
├───┼───────────────────────┼──────────┼──────────────────┼────────┤
│ 2 │ Login with wrong      │ Firefox  │ Error message    │ FAILED │
│   │ password              │ Headless │ shown            │        │
│   │ ⚠ AssertionError:...  │          │                  │        │
└───┴───────────────────────┴──────────┴──────────────────┴────────┘
```

**Why a custom report instead of a third-party library?**  
Third-party reporting libraries (like ExtentReports) add a heavy dependency, have opinionated layouts, and require learning their API. The custom reporter here is about 200 lines of Java that produces exactly the four columns the team needs. No external JAR. No configuration learning curve. The HTML file is completely self-contained — you can email it to anyone.

---

### Log4j2 — The Activity Log

**What it is:** A logging library that records what the framework is doing at every step.

**Analogy:** Like a flight data recorder (black box) on an aircraft. You may not need it on a normal flight, but if something goes wrong, it tells you exactly what happened and in what order.

**Three levels of logging:**
- `INFO` → Normal operations: "Browser opened", "Navigating to store", "Test passed"
- `DEBUG` → Detailed operations: every click, every fill, every wait
- `ERROR` → Failures: what went wrong, the exception message, the test name

**Three output destinations:**
- `Console` — INFO and above, shown in IntelliJ or CI pipeline output as tests run
- `automation.log` — DEBUG and above, the full trace for post-failure investigation
- `errors.log` — ERROR only, a clean list of all failures for quick scanning

---

## How Everything Connects — The Flow From Start to Finish

Here is exactly what happens when you run the tests, step by step:

```
YOU RUN: mvn test  (or right-click testng-intellij.xml in IntelliJ)
         │
         ▼
1. Maven loads pom.xml
   → Downloads any missing libraries
   → Reads surefire plugin configuration
   → Passes environment variables to TestNG

2. TestNG reads testng.xml
   → Sees: parallel="methods", thread-count=4
   → Registers listeners: CustomReportListener, TestNGListener
   → Knows which test classes to run

3. For each test method (up to 4 running at the same time):
   │
   ├── 3a. ExcelDataProvider is called
   │       → Reads TestData.xlsx / TestSuite sheet
   │       → Finds rows where testMethod = "thisMethodName"
   │       → Drops rows where RunFlag = "N"
   │       → Returns remaining rows as data to run
   │       (If 0 rows returned → test is skipped automatically)
   │
   ├── 3b. BaseTest.setUp() runs (@BeforeMethod)
   │       → Reads browser name from the Excel row (e.g. "chromium")
   │       → Reads headless flag from the Excel row (e.g. "false")
   │       → Opens that browser in its own private thread-local session
   │       → Navigates to https://askomdch.com
   │       → Creates all page objects (HomePage, LoginPage, etc.)
   │
   ├── 3c. The actual @Test method runs
   │       → Reads test data from the Excel row (username, password, etc.)
   │       → Uses page objects to interact with the website
   │       → Uses AssertJ soft assertions to check results
   │
   └── 3d. BaseTest.tearDown() runs (@AfterMethod)
           → If test FAILED → takes a screenshot → saves to test-output/screenshots/
           → Closes the browser completely (no session left behind)
           → If this was attempt 1 or 2 of a failed test → RetryAnalyzer reruns it

4. After ALL tests complete:
   → CustomReportListener generates test-output/reports/TestReport.html
   → Log4j2 flushes all logs to test-output/logs/automation.log
```

---

## The Folder Structure — Where Everything Lives

```
PlaywrightWebIndustryStandard/
│
├── pom.xml                     ← The project recipe: all libraries and build config
├── testng.xml                  ← Run ALL tests (4 parallel threads)
├── testng-smoke.xml            ← Run only "smoke" tagged tests
├── testng-regression.xml       ← Run only "regression" tagged tests
├── testng-intellij.xml         ← Run locally in IntelliJ (1 thread, headed browser)
│
├── docs/
│   ├── FrameworkDocumentation.md   ← Detailed technical reference
│   └── HowThisFrameworkWorks.md    ← This document (plain English guide)
│
└── src/
    ├── main/java/com/automation/   ← Core framework code (not test-specific)
    │   ├── config/                 ← Reads .properties files
    │   ├── constants/              ← All hardcoded values live here (timeouts, sheet names)
    │   ├── driver/                 ← Opens and closes browsers (ThreadLocal)
    │   ├── pages/                  ← One class per website page (the maps)
    │   ├── utils/                  ← Reusable tools (waits, clicks, screenshots, Excel)
    │   └── exceptions/             ← Custom error types
    │
    └── test/java/com/automation/   ← Everything test-related
        ├── base/                   ← Common setup/teardown for all tests
        ├── dataproviders/          ← The single DataProvider method
        ├── listeners/              ← Report writer, log listener, retry logic
        ├── tests/                  ← The actual test methods (5 files, ~20 test methods)
        └── utils/                  ← ExcelDataGenerator (creates TestData.xlsx)

    └── test/resources/
        ├── config/                 ← qa.properties, uat.properties, prod.properties
        ├── testdata/               ← TestData.xlsx (the control panel)
        └── log4j2.xml              ← Logging configuration
```

---

## The Excel Sheet — Your Day-to-Day Control Panel

The `TestSuite` sheet in `TestData.xlsx` is what you interact with most often. Here is what each column means in plain English:

| Column | Plain English meaning | Example value |
|---|---|---|
| `testCaseId` | A unique label for this test case | `TC_LOGIN_001` |
| `testMethod` | The name of the Java test method this row feeds into | `testLogin` |
| `description` | A human-readable description of what this test is checking | `Valid login with correct credentials` |
| `browser` | Which browser to open for this test | `chromium` / `firefox` / `webkit` |
| `headless` | Should the browser window be visible? | `false` = visible, `true` = invisible |
| `expectedResult` | What a passing test should produce (shown in the report) | `User dashboard is visible after login` |
| `RunFlag` | Should this test run? | `Y` = yes, `N` = skip |
| `username` | Login username (for login/account tests) | `demouser` |
| `password` | Login password | `demopass123` |
| `loginExpected` | Should login succeed or fail? (for login tests) | `success` / `error` |
| `searchTerm` | What to search for (for search tests) | `Blue` |
| `expectedProduct` | Which product should appear in results | `Blue Shoes` |
| `expectSearchResults` | Should the search return results? | `true` / `false` |
| `productName` | Which product to add to cart | `Blue Shoes` |
| `quantity` | How many items to add | `2` |
| `url` | Which page to navigate to (for navigation tests) | `/store/` |
| `expectedTitle` | What the page title should contain | `Store` |
| `expectedUrlFragment` | What the URL should contain | `/store/` |

**Columns you will not always fill in:** Most test cases only use a few of these columns. A search test does not need `username` or `productName`. Just leave those cells blank — the framework handles empty values gracefully.

---

## Common Tasks — Quick Reference

### "I want to disable a test temporarily"
Open `TestData.xlsx`, find the row, change `RunFlag` from `Y` to `N`. Done. The row stays so you can re-enable it any time.

### "I want to run the same test on Firefox as well"
Find the row for that test. Copy the entire row. Change `browser` from `chromium` to `firefox` on the copy. Change `testCaseId` to a new unique value (e.g. add `_FF` at the end). Set `RunFlag = Y`. The test now runs on both browsers.

### "I want to add a brand new test case"
1. Add a row to `TestData.xlsx` with the right `testMethod` name, `description`, `browser`, `expectedResult`, and `RunFlag = Y`
2. Write the corresponding `@Test` method in the right test class using `dataProvider = "testData"`
3. Regenerate the Excel file: `mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass="com.automation.utils.ExcelDataGenerator"`

### "I want to run only the login tests"
```
mvn test -Dtest=LoginTest
```

### "I want to run only smoke tests"
```
mvn test -P smoke
```

### "I want to see the browser while tests run"
Open `TestData.xlsx`, change `headless` from `true` to `false` on the rows you care about.  
Or use the IntelliJ suite (`testng-intellij.xml`) — the rows there are already set to `false`.

### "The tests ran — where is the report?"
Open: `test-output/reports/TestReport.html` in any web browser.

### "A test failed — where is the screenshot?"
Open: `test-output/screenshots/` — each screenshot is named after the failing test method with a timestamp.

---

## Understanding the Test Report

When you open `TestReport.html`, you will see:

**Top section — Summary cards:**
- `Total` = how many test cases ran
- `Passed` = how many passed
- `Failed` = how many failed
- `Skipped` = how many were skipped (usually dependency failures)
- `Pass rate` = the percentage that passed

**Main table — One row per test execution:**
- **Test Scenario** — the `description` from Excel, plus the `testCaseId`. If the test failed, the error message appears here in red.
- **Environment (Browser)** — which browser and whether it was visible (`Headed`) or not (`Headless`)
- **Expected Result** — the `expectedResult` from Excel — what should have happened
- **Status** — `PASSED` (green), `FAILED` (red), or `SKIPPED` (yellow)
- **Duration** — how long the test took

**Colour coding on the left edge of each row:**
- Green stripe = passed
- Red stripe = failed
- Yellow stripe = skipped

---

## Frequently Asked Questions

**Q: Why does the same test run multiple times?**  
Each row in Excel for a test method = one execution. If `testLogin` has 3 rows with `RunFlag = Y`, it runs 3 times with different data (different credentials, different browsers, etc.). This is intentional.

**Q: Why do some tests use Firefox and some use Chromium?**  
The `browser` column in Excel controls this per row. Some tests are configured to run on Firefox specifically to verify cross-browser behaviour.

**Q: What is "headless" mode?**  
When `headless = true`, the browser runs silently in the background with no visible window — ideal for CI pipelines where there is no screen. When `headless = false`, a browser window opens and you can watch the test run in real time.

**Q: Why does the framework open 4 browsers at once sometimes?**  
`thread-count=4` in `testng.xml` means 4 tests run simultaneously. Each has its own browser. This makes the full suite 4× faster than running tests one at a time.

**Q: What happens if a test fails?**  
1. A screenshot is taken automatically
2. The test is retried up to 2 times (in case it was a flaky network issue)
3. If it still fails after retries, it is marked `FAILED` in the report
4. The error message appears in the report row

**Q: What is "smoke" vs "regression"?**  
- **Smoke tests** (`-P smoke`) — a small, fast subset (~5 minutes) that checks the most critical user journeys. Run these before every deployment to catch obvious breakages.
- **Regression tests** (`-P regression`) — the full suite (~15-30 minutes) that verifies everything. Run these nightly or before a major release.

**Q: I changed a password in the config file but tests still use the old one. Why?**  
Tests that are account-related (login, account tab navigation) read credentials from the `username` and `password` columns in the Excel sheet — **not** from the config file. Update `TestData.xlsx` for those tests. The config file credentials are a fallback used only when no Excel data is present.

---

## In Summary

The framework is designed around one core principle: **the Excel sheet is the only file a non-developer should ever need to touch** to control which tests run, on which browser, with which data.

Everything else — browser management, parallelism, reporting, waiting for page elements, taking screenshots — is handled automatically by the framework code. You get a clean HTML report at the end showing exactly what passed, what failed, and why.

---

*Document maintained alongside the framework code in the `docs/` folder.*
