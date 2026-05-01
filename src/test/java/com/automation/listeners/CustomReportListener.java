package com.automation.listeners;

import com.automation.constants.AppConstants;
import com.automation.driver.PlaywrightFactory;
import com.automation.utils.ScreenshotUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Custom HTML report listener.
 *
 * Replaces ExtentReports with a zero-dependency HTML file whose table format is:
 *   Test Scenario | Environment (Browser) | Expected Result | Status
 *
 * Thread safety: results are collected in a CopyOnWriteArrayList so parallel
 * test methods can add entries simultaneously without synchronisation blocks.
 * The HTML file is written once in onFinish() after all threads have finished.
 */
public class CustomReportListener implements ITestListener, ISuiteListener {

    private static final Logger log = LogManager.getLogger(CustomReportListener.class);

    // Immutable record — safe to share across threads
    private record TestEntry(
            String testCaseId,
            String description,
            String browser,
            boolean headless,
            String expectedResult,
            String status,       // PASSED | FAILED | SKIPPED
            long   durationMs,
            String failureReason  // blank when PASSED / SKIPPED
    ) {}

    // CopyOnWriteArrayList: lock-free concurrent writes, single read at report time
    private static final List<TestEntry> results = new CopyOnWriteArrayList<>();

    private String suiteName = "Automation Suite";

    // ─── ISuiteListener ───────────────────────────────────────────────────────

    @Override
    public void onStart(ISuite suite) {
        suiteName = suite.getName();
        results.clear();  // fresh slate for each suite run
        log.info("[Report] Suite started: {}", suiteName);
    }

    @Override
    public void onFinish(ISuite suite) {
        generateReport(suiteName);
    }

    // ─── ITestListener ────────────────────────────────────────────────────────

    @Override
    public void onTestSuccess(ITestResult result) {
        results.add(buildEntry(result, "PASSED", null));
    }

    @Override
    public void onTestFailure(ITestResult result) {
        captureScreenshot(result);
        results.add(buildEntry(result, "FAILED", result.getThrowable()));
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        results.add(buildEntry(result, "SKIPPED",
                result.getThrowable() != null ? result.getThrowable() : null));
    }

    // ─── Entry builder ────────────────────────────────────────────────────────

    private TestEntry buildEntry(ITestResult result, String status, Throwable throwable) {
        Map<String, String> data     = extractData(result);
        String testCaseId            = data.getOrDefault("testCaseId",    "—");
        String description           = data.getOrDefault("description",    result.getName());
        String browser               = data.getOrDefault("browser",       "chromium");
        boolean headless             = Boolean.parseBoolean(data.getOrDefault("headless", "true"));
        String expectedResult        = data.getOrDefault("expectedResult", "—");
        long duration                = result.getEndMillis() - result.getStartMillis();
        String failureReason         = throwable != null ? extractMessage(throwable) : "";

        return new TestEntry(testCaseId, description, browser, headless,
                             expectedResult, status, duration, failureReason);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractData(ITestResult result) {
        Object[] params = result.getParameters();
        if (params != null && params.length > 0 && params[0] instanceof Map<?, ?> map) {
            return (Map<String, String>) map;
        }
        return Collections.emptyMap();
    }

    private String extractMessage(Throwable t) {
        if (t == null) return "";
        String msg = t.getMessage();
        // Trim very long stack traces to the first meaningful line
        if (msg != null && msg.length() > 400) msg = msg.substring(0, 400) + "…";
        return msg != null ? escapeHtml(msg) : t.getClass().getSimpleName();
    }

    // ─── Screenshot on failure ────────────────────────────────────────────────

    private void captureScreenshot(ITestResult result) {
        try {
            ScreenshotUtils.captureOnFailure(PlaywrightFactory.getPage(), result.getName());
        } catch (Exception e) {
            log.warn("[Report] Could not capture screenshot for '{}': {}", result.getName(), e.getMessage());
        }
    }

    // ─── HTML generation ─────────────────────────────────────────────────────

    private void generateReport(String suite) {
        try {
            Files.createDirectories(Paths.get(AppConstants.REPORTS_DIR));
        } catch (IOException e) {
            log.error("[Report] Cannot create reports directory: {}", e.getMessage());
            return;
        }

        long passed  = results.stream().filter(r -> "PASSED".equals(r.status())).count();
        long failed  = results.stream().filter(r -> "FAILED".equals(r.status())).count();
        long skipped = results.stream().filter(r -> "SKIPPED".equals(r.status())).count();
        long total   = results.size();
        String passRate = total > 0 ? String.format("%.1f%%", (passed * 100.0) / total) : "0%";
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"));
        String env = System.getProperty(AppConstants.PROP_ENV, "qa").toUpperCase();

        StringBuilder rows = new StringBuilder();
        int index = 1;
        for (TestEntry e : results) {
            rows.append(buildRow(index++, e));
        }

        String html = buildPage(suite, env, timestamp, total, passed, failed, skipped, passRate, rows.toString());

        try {
            Files.writeString(Paths.get(AppConstants.REPORT_FILE), html, StandardCharsets.UTF_8);
            log.info("[Report] HTML report written → {}", Paths.get(AppConstants.REPORT_FILE).toAbsolutePath());
        } catch (IOException e) {
            log.error("[Report] Failed to write HTML report: {}", e.getMessage());
        }
    }

    private String buildRow(int index, TestEntry e) {
        String rowClass    = switch (e.status()) {
            case "PASSED"  -> "row-pass";
            case "FAILED"  -> "row-fail";
            default        -> "row-skip";
        };
        String badgeClass  = switch (e.status()) {
            case "PASSED"  -> "badge-pass";
            case "FAILED"  -> "badge-fail";
            default        -> "badge-skip";
        };

        String envCell = capitalize(e.browser())
                + " <span class=\"headless-tag\">" + (e.headless() ? "Headless" : "Headed") + "</span>";

        String scenarioCell = "<strong class=\"tc-id\">" + escapeHtml(e.testCaseId()) + "</strong>"
                + "<br><span class=\"tc-desc\">" + escapeHtml(e.description()) + "</span>";

        if (!e.failureReason().isBlank()) {
            scenarioCell += "<div class=\"error-msg\">&#9888; " + e.failureReason() + "</div>";
        }

        String duration = e.durationMs() >= 1000
                ? String.format("%.1fs", e.durationMs() / 1000.0)
                : e.durationMs() + "ms";

        return String.format("""
                <tr class="%s">
                  <td class="num">%d</td>
                  <td>%s</td>
                  <td><span class="env-tag">%s</span></td>
                  <td class="expected">%s</td>
                  <td><span class="badge %s">%s</span></td>
                  <td class="dur">%s</td>
                </tr>
                """,
                rowClass, index, scenarioCell, envCell,
                escapeHtml(e.expectedResult()), badgeClass, e.status(), duration);
    }

    // ─── Full HTML page template ──────────────────────────────────────────────

    private String buildPage(String suite, String env, String timestamp,
                             long total, long passed, long failed, long skipped,
                             String passRate, String rows) {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Test Report — %s</title>
<style>
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  body   { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; color: #2c2c2c; }

  /* ── Header ── */
  .hdr           { background: #0f172a; color: #f8fafc; padding: 28px 44px; }
  .hdr h1        { font-size: 22px; font-weight: 700; letter-spacing: .3px; }
  .hdr .meta     { font-size: 13px; color: #94a3b8; margin-top: 6px; }
  .hdr .meta b   { color: #cbd5e1; }

  /* ── Summary cards ── */
  .cards         { display: flex; gap: 14px; padding: 22px 44px; background: #fff;
                   border-bottom: 1px solid #e2e8f0; flex-wrap: wrap; }
  .card          { padding: 16px 28px; border-radius: 10px; text-align: center; min-width: 110px; }
  .card .n       { font-size: 32px; font-weight: 800; line-height: 1; }
  .card .lbl     { font-size: 11px; font-weight: 600; text-transform: uppercase;
                   letter-spacing: .9px; margin-top: 5px; }
  .c-total       { background: #ede9fe; color: #5b21b6; }
  .c-pass        { background: #dcfce7; color: #166534; }
  .c-fail        { background: #fee2e2; color: #991b1b; }
  .c-skip        { background: #fef9c3; color: #854d0e; }
  .pass-rate     { margin-left: auto; align-self: center; font-size: 15px;
                   color: #64748b; font-weight: 500; padding: 0 10px; }

  /* ── Table wrapper ── */
  .wrap          { padding: 28px 44px 44px; }

  table          { width: 100%%; border-collapse: collapse; background: #fff;
                   border-radius: 12px; overflow: hidden;
                   box-shadow: 0 1px 6px rgba(0,0,0,.08); }
  thead tr       { background: #0f172a; }
  th             { padding: 13px 16px; text-align: left; font-size: 12px;
                   font-weight: 600; text-transform: uppercase; letter-spacing: .6px;
                   color: #94a3b8; white-space: nowrap; }
  td             { padding: 12px 16px; font-size: 13.5px; border-bottom: 1px solid #f1f5f9;
                   vertical-align: top; }
  tr:last-child td { border-bottom: none; }
  tbody tr:hover { background: #fafafa; }

  /* ── Row accent bars ── */
  .row-pass td:first-child { border-left: 4px solid #22c55e; }
  .row-fail td:first-child { border-left: 4px solid #ef4444; }
  .row-skip td:first-child { border-left: 4px solid #f59e0b; }

  /* ── Cell styles ── */
  .num      { color: #94a3b8; font-size: 12px; width: 40px; }
  .tc-id    { font-size: 12px; color: #64748b; font-weight: 600;
              background: #f1f5f9; padding: 2px 7px; border-radius: 4px; }
  .tc-desc  { font-size: 13.5px; color: #1e293b; }
  .expected { color: #475569; font-size: 13px; }
  .dur      { color: #94a3b8; font-size: 12px; white-space: nowrap; }

  .env-tag  { display: inline-flex; align-items: center; gap: 5px;
              background: #eff6ff; color: #1d4ed8; padding: 4px 10px;
              border-radius: 999px; font-size: 12px; font-weight: 600; }
  .headless-tag { font-weight: 400; color: #6b7280; font-size: 11px; }

  /* ── Status badges ── */
  .badge         { display: inline-block; padding: 4px 13px; border-radius: 999px;
                   font-size: 11.5px; font-weight: 700; letter-spacing: .5px; }
  .badge-pass    { background: #dcfce7; color: #166534; }
  .badge-fail    { background: #fee2e2; color: #991b1b; }
  .badge-skip    { background: #fef9c3; color: #854d0e; }

  /* ── Failure reason ── */
  .error-msg     { font-size: 12px; color: #b91c1c; background: #fff5f5;
                   border: 1px solid #fecaca; border-radius: 5px;
                   padding: 7px 10px; margin-top: 8px;
                   font-family: 'Consolas', 'Courier New', monospace;
                   word-break: break-word; white-space: pre-wrap; }

  /* ── Responsive ── */
  @media (max-width: 768px) {
    .hdr, .cards, .wrap { padding-left: 18px; padding-right: 18px; }
    .cards { gap: 10px; }
    .card  { min-width: 80px; padding: 12px 16px; }
    th, td { padding: 10px 10px; }
  }
</style>
</head>
<body>

<div class="hdr">
  <h1>&#9654; Automation Test Report</h1>
  <div class="meta">
    <b>Suite:</b> %s &nbsp;&bull;&nbsp;
    <b>Environment:</b> %s &nbsp;&bull;&nbsp;
    <b>Generated:</b> %s
  </div>
</div>

<div class="cards">
  <div class="card c-total"><div class="n">%d</div><div class="lbl">Total</div></div>
  <div class="card c-pass" ><div class="n">%d</div><div class="lbl">Passed</div></div>
  <div class="card c-fail" ><div class="n">%d</div><div class="lbl">Failed</div></div>
  <div class="card c-skip" ><div class="n">%d</div><div class="lbl">Skipped</div></div>
  <div class="pass-rate">Pass rate: <b>%s</b></div>
</div>

<div class="wrap">
<table>
  <thead>
    <tr>
      <th>#</th>
      <th>Test Scenario</th>
      <th>Environment (Browser)</th>
      <th>Expected Result</th>
      <th>Status</th>
      <th>Duration</th>
    </tr>
  </thead>
  <tbody>
%s
  </tbody>
</table>
</div>

</body>
</html>
""".formatted(suite, suite, env, timestamp, total, passed, failed, skipped, passRate, rows);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "Chromium";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
