package com.automation.listeners;

import com.automation.constants.AppConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Automatic retry for flaky tests.
 *
 * Enabled per-test via: @Test(retryAnalyzer = RetryAnalyzer.class)
 * Or globally by wiring through TestNGListener.onTestFailure.
 *
 * Strategy: retry up to MAX_RETRY_COUNT times. Keeps a per-thread counter
 * so parallel executions don't interfere with each other's retry state.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger log = LogManager.getLogger(RetryAnalyzer.class);

    // ThreadLocal counter — each parallel thread tracks its own attempt count
    private static final ThreadLocal<Integer> retryCount = ThreadLocal.withInitial(() -> 0);

    @Override
    public boolean retry(ITestResult result) {
        int current = retryCount.get();
        if (current < AppConstants.MAX_RETRY_COUNT) {
            retryCount.set(current + 1);
            log.warn("Retrying test '{}' — attempt {} of {}.",
                    result.getName(), current + 1, AppConstants.MAX_RETRY_COUNT);
            return true;
        }
        retryCount.remove(); // reset for next test on this thread
        return false;
    }

    /** Resets the counter — called in @AfterMethod to ensure clean state. */
    public static void reset() {
        retryCount.remove();
    }
}
