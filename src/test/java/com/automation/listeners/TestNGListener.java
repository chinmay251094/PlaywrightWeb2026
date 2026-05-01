package com.automation.listeners;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.*;

/**
 * General-purpose TestNG lifecycle listener.
 *
 * Responsibilities:
 *   - Structured Log4j2 logging at every test lifecycle event
 *   - Console-friendly status banners for CI log readability
 *   - Wire RetryAnalyzer dynamically for every test method so individual
 *     tests don't need @Test(retryAnalyzer = ...) annotations
 */
public class TestNGListener implements ITestListener, ISuiteListener, IInvokedMethodListener {

    private static final Logger log = LogManager.getLogger(TestNGListener.class);

    private static final String BANNER =
            "═══════════════════════════════════════════════════════════";

    // ─── ISuiteListener ───────────────────────────────────────────────────────

    @Override
    public void onStart(ISuite suite) {
        log.info(BANNER);
        log.info("  SUITE STARTED : {}", suite.getName());
        log.info("  XML File      : {}", suite.getXmlSuite().getFileName());
        log.info(BANNER);
    }

    @Override
    public void onFinish(ISuite suite) {
        log.info(BANNER);
        log.info("  SUITE FINISHED: {}", suite.getName());
        log.info(BANNER);
    }

    // ─── ITestListener ────────────────────────────────────────────────────────

    @Override
    public void onStart(ITestContext context) {
        log.info("── Test context started: {} ──", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        int passed  = context.getPassedTests().size();
        int failed  = context.getFailedTests().size();
        int skipped = context.getSkippedTests().size();
        log.info("── Context '{}' finished — PASS: {} | FAIL: {} | SKIP: {} ──",
                context.getName(), passed, failed, skipped);
    }

    @Override
    public void onTestStart(ITestResult result) {
        log.info("▶  START  : [{}.{}]  Thread: {}",
                result.getTestClass().getRealClass().getSimpleName(),
                result.getName(),
                Thread.currentThread().getName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        log.info("✔  PASS   : [{}]  Duration: {}ms",
                result.getName(), getElapsedMs(result));
    }

    @Override
    public void onTestFailure(ITestResult result) {
        log.error("✘  FAIL   : [{}]  Duration: {}ms  Reason: {}",
                result.getName(), getElapsedMs(result),
                result.getThrowable() != null ? result.getThrowable().getMessage() : "unknown");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        log.warn("⊘  SKIP   : [{}]  Reason: {}",
                result.getName(),
                result.getThrowable() != null ? result.getThrowable().getMessage() : "dependency failure");
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        log.warn("△  PARTIAL: [{}] — failed but within success threshold.", result.getName());
    }

    // ─── IInvokedMethodListener ───────────────────────────────────────────────

    /**
     * Dynamically attaches RetryAnalyzer to every @Test method so the suite
     * XML or individual annotations don't need to reference it explicitly.
     */
    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult result) {
        if (method.isTestMethod()) {
            ITestNGMethod testMethod = result.getMethod();
            if (testMethod.getRetryAnalyzerClass() == null) {
                testMethod.setRetryAnalyzerClass(RetryAnalyzer.class);
            }
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result) {
        // No-op — post-invocation hooks handled in BaseTest @AfterMethod
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private long getElapsedMs(ITestResult result) {
        return result.getEndMillis() - result.getStartMillis();
    }
}
