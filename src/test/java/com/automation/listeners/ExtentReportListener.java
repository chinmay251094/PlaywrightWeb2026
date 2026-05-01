package com.automation.listeners;

/**
 * Superseded by {@link CustomReportListener}.
 *
 * Reporting is now handled by a zero-dependency custom HTML reporter
 * that writes the table:
 *   Test Scenario | Environment (Browser) | Expected Result | Status
 *
 * This file is kept as an empty placeholder so any stale IDE run
 * configurations that reference the class name do not cause a
 * ClassNotFoundException at startup.  It is not wired into any
 * @Listeners annotation or testng XML listener block.
 */
public final class ExtentReportListener {
    private ExtentReportListener() {}
}
