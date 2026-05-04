# CI/CD Guide — Playwright TestNG Hybrid Framework

## Overview

This guide covers the end-to-end CI/CD pipeline for the Playwright Java + TestNG + Maven automation framework targeting [askomdch.com](https://askomdch.com).

The pipeline is optimised for three outcomes:

1. **Fast feedback** — smoke tests on every push and PR (< 5 minutes)
2. **Cross-browser confidence** — full regression across Chromium, Firefox, and WebKit on every merge to `main`
3. **Nightly safety net** — scheduled full-suite run with per-browser artifacts

---

## What Was Done — Step by Step (Start Here If You're New to GitHub Actions)

This section explains exactly what was built and why, in plain language. No prior CI/CD knowledge needed.

---

### Background — How GitHub Actions Works

GitHub Actions is a built-in automation system inside GitHub. The idea is simple:

> "When something happens to my repository (e.g. someone pushes code), automatically run a series of commands on a machine that GitHub spins up for me."

You tell GitHub what to do by placing a `.yml` file inside a special folder: `.github/workflows/`. GitHub reads that file automatically — no registration, no setup, no server to manage. The machine GitHub spins up is called a **runner** (think: a fresh Linux laptop that exists only for the duration of your job, then disappears).

---

### Step 1 — Modified `pom.xml`

**File:** [pom.xml](pom.xml)

**What was added:** A single plugin entry — `exec-maven-plugin`.

**Why:** Playwright stores its browser binaries (Chrome, Firefox, WebKit) separately from the Java library. Before tests can run on any machine (including GitHub's runners), those browsers need to be downloaded and installed. The command to do that is:

```bash
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps"
```

This command uses the `exec-maven-plugin`. Without declaring it in `pom.xml`, Maven doesn't know what `exec:java` means and throws an error. Adding the plugin is what makes browser installation a first-class Maven operation.

**In simple terms:** We told Maven "you are allowed to run Java programs directly — specifically, the Playwright browser installer."

---

### Step 2 — Created `.github/workflows/ci.yml`

**File:** [.github/workflows/ci.yml](.github/workflows/ci.yml)

**What it is:** The main CI pipeline. This is the file GitHub reads and acts on automatically.

**When it runs:**
- Every time you push code to `main` or `develop`
- Every time you open a Pull Request targeting those branches
- Manually, whenever you click "Run workflow" in the GitHub Actions UI

**What it does, in order:**

1. GitHub spins up a fresh Ubuntu Linux machine
2. It checks out your code (like a `git clone`)
3. It installs Java 17
4. It downloads all Maven dependencies (and caches them so the next run is faster)
5. It downloads the Playwright browser binaries (and caches those too)
6. It runs the **smoke test suite** — a small, fast set of tests to check that the core flows work
7. If tests pass and the push was to `main`, it then runs the **full regression suite** across all three browsers (Chromium, Firefox, WebKit) in parallel
8. It uploads the test reports and screenshots as downloadable artifacts

**In simple terms:** Every time you push code, GitHub automatically checks whether your tests pass. You don't have to run anything manually.

---

### Step 3 — Created `.github/workflows/nightly.yml`

**File:** [.github/workflows/nightly.yml](.github/workflows/nightly.yml)

**What it is:** A scheduled pipeline that runs automatically every night at 2 AM (UTC) without anyone pushing code.

**What it does:** Runs the full regression suite across all three browsers (Chromium, Firefox, WebKit) in parallel, and posts a summary table showing how many tests passed and failed per browser.

**Why it exists:** During the day you want fast feedback (smoke only). But once a day you want to know the full picture — did anything break across all browsers? The nightly run gives you that without slowing down every push.

**In simple terms:** Even if nobody touches the code, GitHub will still run your full test suite every night and tell you if something broke.

---

### Step 4 — Created `Dockerfile`

**File:** [Dockerfile](Dockerfile)

**What it is:** A recipe for building a self-contained Docker image that has everything needed to run your tests — Java, Maven, and all three Playwright browsers — pre-installed.

**Why it exists:** GitHub Actions runners work fine for CI, but sometimes you want to run tests locally in an environment that exactly matches CI, or you want to run tests on any machine without setting up Java/Maven/browsers manually. The Docker image solves that.

**How the layers are structured (this matters for speed):**
```
Layer 1 — Base OS + Maven         (rebuilt only if the base image changes)
Layer 2 — Maven dependencies      (rebuilt only if pom.xml changes)
Layer 3 — Playwright browsers     (rebuilt only if Playwright version changes)
Layer 4 — Your compiled tests     (rebuilt on every code change — fast, ~5 MB)
```
Because Docker caches each layer independently, a typical rebuild after a code change only re-runs Layer 4, which takes about 15 seconds.

**In simple terms:** Package everything your tests need into one box. Run that box anywhere — your laptop, a colleague's machine, a server — and get identical results every time.

---

### Step 5 — Created `.dockerignore`

**File:** [.dockerignore](.dockerignore)

**What it is:** A list of files and folders that should NOT be copied into the Docker image.

**Why it exists:** Without it, Docker would copy your entire project folder into the image — including `target/` (compiled classes), `test-output/` (old reports), `.idea/` (IDE settings), and `.git/` (the entire git history). This makes the image unnecessarily large and slow to build.

**In simple terms:** The `.dockerignore` file is to Docker what `.gitignore` is to Git — it tells Docker what to ignore.

---

### Step 6 — Created `scripts/run-tests.sh`

**File:** [scripts/run-tests.sh](scripts/run-tests.sh)

**What it is:** A convenience shell script that wraps the Docker commands so you don't have to remember long `docker run` commands.

**Instead of typing this every time:**
```bash
docker build -t playwright-tests . && docker run --rm --shm-size=2gb \
  -v "$(pwd)/test-output:/app/test-output" playwright-tests \
  test -P smoke,qa -Dbrowser=firefox -Dheadless=true
```

**You just type:**
```bash
./scripts/run-tests.sh -s smoke -b firefox
```

It also validates your inputs (e.g. catches a typo like `-b crome` before Docker even starts) and prints a clear summary of what it's about to run.

**In simple terms:** A shortcut script so running tests via Docker is a single readable command.

---

### How All the Pieces Connect

```
Your code (Java + TestNG)
        │
        │  you push to GitHub
        ▼
.github/workflows/ci.yml          ← GitHub reads this automatically
        │
        ├─ downloads Java 17
        ├─ downloads Maven deps (uses pom.xml — including exec-maven-plugin)
        ├─ downloads Playwright browsers
        ├─ runs smoke tests
        └─ (on main merge) runs regression on 3 browsers in parallel

.github/workflows/nightly.yml     ← GitHub runs this every night at 2 AM
        └─ runs full regression on 3 browsers, posts summary

Dockerfile                        ← for running locally via Docker
        └─ same environment as CI, packaged into one image

scripts/run-tests.sh              ← shortcut to use the Docker image
```

Every piece has one job. The `.yml` files are the brain (they decide when and what to run). `pom.xml` gives Maven the ability to install browsers. The `Dockerfile` makes the environment portable. The script makes Docker easy to use.

---

## Architecture

### Key design decisions

| Decision | Rationale |
|---|---|
| GitHub Actions (ubuntu-22.04) | Free for public repos; matches the Docker base image |
| `eclipse-temurin:17-jdk-jammy` Docker base | Official JDK 17 on Ubuntu 22.04; smallest surface area |
| Browser binary caching | Playwright browsers are ~300 MB each; caching them cuts install time from ~3 min to ~15 s |
| `fail-fast: false` on regression matrix | All three browsers must produce artifacts even when one fails |
| `cancel-in-progress: true` concurrency | Newer pushes to the same branch cancel stale in-flight runs |
| Headless already the default | `config.properties` sets `headless=true`; no extra flag needed in most cases |
| `--no-sandbox` + `--disable-dev-shm-usage` | Already baked into `PlaywrightFactory.launchBrowser()` — zero extra CI configuration |

---

## Pipeline Workflow

### CI pipeline (`ci.yml`)

```
Push / PR to main or develop
         │
         ▼
┌─────────────────────────┐
│  smoke (Chromium only)  │  ← fast gate, ~3–5 min
│  • compile              │
│  • smoke suite          │
│  • upload report        │
└───────────┬─────────────┘
            │ (merge to main only)
            ▼
┌──────────────────────────────────────────────────────────────────┐
│  regression matrix  (parallel, fail-fast: false)                 │
│                                                                  │
│   chromium job ──────┐                                           │
│   firefox  job ──────┼──→  all three produce artifacts           │
│   webkit   job ──────┘                                           │
│                                                                  │
│  • full regression suite, 4 threads per browser                 │
│  • 30-day artifact retention                                     │
└──────────────────────────────────────────────────────────────────┘
```

### Nightly pipeline (`nightly.yml`)

```
02:00 UTC daily  (or manual trigger)
         │
         ▼
┌──────────────────────────────────────────────────────────────────┐
│  nightly-regression matrix  (parallel, fail-fast: false)        │
│                                                                  │
│   chromium job ──────┐                                           │
│   firefox  job ──────┼──→  full regression + job summary table  │
│   webkit   job ──────┘                                           │
└──────────────────────────────────────────────────────────────────┘
```

### Manual trigger (`workflow_dispatch`)

Both workflows expose a **Run workflow** button in GitHub Actions UI with selectable inputs:

| Input | Options | Default |
|---|---|---|
| `suite` | `smoke`, `regression` | `smoke` |
| `browser` | `chromium`, `firefox`, `webkit` | `chromium` |
| `environment` | `qa`, `uat` | `qa` |
| `thread_count` | any integer | `4` |

---

## Setup Instructions

### Prerequisites

| Tool | Required version |
|---|---|
| JDK | 17 (Temurin recommended) |
| Maven | 3.6+ |
| Docker | 20+ (optional, for containerised runs) |
| Git | any recent version |

### Local setup

```bash
# 1. Clone the repository
git clone <repo-url>
cd PlaywrightWebIndustryStandard

# 2. Install Playwright browsers (first time only)
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps"

# 3. Verify the setup
mvn test -P smoke,qa -Dheadless=true
```

### CI setup (GitHub Actions)

No manual setup is required. The workflows activate automatically when the repository is pushed to GitHub. The only prerequisite is that the repository is hosted on GitHub.

If the target application requires credentials (e.g. a private QA environment), add them as **GitHub repository secrets** and reference them as environment variables in the workflow:

```yaml
# In ci.yml, under the "Run smoke tests" step:
env:
  APP_USERNAME: ${{ secrets.APP_USERNAME }}
  APP_PASSWORD: ${{ secrets.APP_PASSWORD }}
```

Then expose them via Maven system properties:

```bash
mvn test -P smoke,qa -DAPP_USERNAME="${APP_USERNAME}" -DAPP_PASSWORD="${APP_PASSWORD}"
```

---

## Verifying Your First Run

Follow this sequence after pushing the repository to GitHub for the first time.

### Step 1 — Confirm the remote is GitHub

The workflows only activate when the repo is hosted on GitHub. Verify:

```bash
git remote -v
# Expected output:
# origin  https://github.com/<your-username>/<repo>.git (fetch)
# origin  https://github.com/<your-username>/<repo>.git (push)
```

If the remote is a local path or another host, create a repo on GitHub and re-add the remote:

```bash
git remote add origin https://github.com/<your-username>/<repo>.git
git push -u origin main
```

---

### Step 2 — Enable GitHub Actions

1. Go to your repo on GitHub.com.
2. Click the **Actions** tab.
3. If you see a banner saying _"Workflows aren't being run on this fork"_ or Actions is disabled, click **Enable Actions**.

The two workflow files (`.github/workflows/ci.yml` and `.github/workflows/nightly.yml`) should now appear in the left sidebar.

---

### Step 3 — Trigger your first run without a new push

Both workflows have `workflow_dispatch`, so you can trigger them immediately without making a code change:

1. **Actions → CI — Playwright TestNG** (left sidebar)
2. Click **Run workflow** (top-right dropdown)
3. Leave all inputs at their defaults (`suite: smoke`, `browser: chromium`, `environment: qa`)
4. Click the green **Run workflow** button

A new run appears within a few seconds.

---

### Step 4 — Watch the live log

Click into the run, then click the **smoke** job. You will see each step execute in real time. Here is what each step does and roughly how long it takes on a cold (first) run:

```
✓ Checkout                                    ~5 s
✓ Set up JDK 17                               ~15 s
✓ Cache Playwright browsers    ← MISS         ~5 s   (first run always misses)
✓ Resolve Maven dependencies   ← MISS         ~90 s  (downloads ~150 MB from Maven Central)
✓ Install Playwright browsers  ← full install ~120 s (downloads ~300 MB chromium binary + apt packages)
✓ Run smoke tests                             ~90 s
✓ Upload HTML report                          ~5 s
─────────────────────────────────────────────────────
  Total first run                             ~6–8 min

  Subsequent runs (caches warm):              ~2–3 min
```

> **First run is always the slowest.** Maven deps and Playwright binaries are both cached after the first run, so every subsequent run benefits from warm caches.

---

### Step 5 — Read the result and download artifacts

Once the run finishes:

- Green checkmark → all smoke tests passed.
- Red X → at least one test failed. A `smoke-failure-<run-id>` artifact is also uploaded.

Click **Summary** at the top of the run. Scroll to the **Artifacts** section at the bottom:

| Artifact name | Contents | When present |
|---|---|---|
| `smoke-report-<run-id>` | `test-output/reports/TestReport.html` | Always (14-day retention) |
| `smoke-failure-<run-id>` | Screenshots, logs, Surefire XML | Failures only (7-day retention) |

Download the report ZIP, unzip it, and open `TestReport.html` in a browser to see the full ExtentReports output.

---

### Step 6 — Verify the regression matrix trigger

The regression matrix only runs on a **push/merge to `main`**. Test it with an empty commit:

```bash
git commit --allow-empty -m "chore: verify regression trigger"
git push origin main
```

Go to **Actions** — you should see:
1. The `smoke` job start and pass (~3 min).
2. Three `Regression — chromium/firefox/webkit` jobs start **in parallel** immediately after smoke passes (~6–8 min each).

All three regression jobs produce separate artifacts (`regression-chromium-<run-id>`, etc.) retained for 30 days.

---

### Step 7 — Verify the nightly workflow (without waiting overnight)

The nightly workflow runs automatically at 02:00 UTC, but you can test it right now:

1. **Actions → Nightly — Cross-Browser Regression** (left sidebar)
2. Click **Run workflow**
3. Leave defaults and click **Run workflow**

All three browser jobs run in parallel. When they finish, check the **Summary** page — the nightly workflow posts a markdown results table per browser directly on that page:

```
Nightly — chromium
| Tests | Failures | Errors |
|-------|----------|--------|
| 42    | 0        | 0      |
```

---

### What a healthy full run looks like

```
CI run on merge to main:

  smoke [chromium]      ✓  ~3 min
  regression
    chromium            ✓  ~6 min  ─┐
    firefox             ✓  ~7 min   ├─ parallel
    webkit              ✓  ~8 min  ─┘

Nightly run:

  nightly-regression
    chromium            ✓  ~8 min  ─┐
    firefox             ✓  ~9 min   ├─ parallel
    webkit              ✓  ~9 min  ─┘
```

---

### Most likely first-run failure and fix

The most common first-run failure is the AUT (`askomdch.com`) being slow to respond from a CI runner, causing `NETWORKIDLE` timeouts in `BaseTest.setUp()`.

**Symptoms:** `TimeoutError: page.waitForLoadState: Timeout 60000ms exceeded` in the log.

**Fix:** Add timeout overrides to the `Run smoke tests` step in `ci.yml`:

```yaml
- name: Run smoke tests
  run: |
    mvn test -P smoke,qa \
      -Dbrowser=chromium \
      -Dheadless=true \
      -Dpage.load.timeout=90000 \
      -Ddefault.timeout=45000 \
      --no-transfer-progress
```

---

## Branch & Trigger Reference

| Event | Workflow | Jobs triggered |
|---|---|---|
| Push to `develop` | `ci.yml` | `smoke` only |
| Push to `main` | `ci.yml` | `smoke` → `regression` (chromium + firefox + webkit) |
| Pull request to `main` or `develop` | `ci.yml` | `smoke` only |
| Manual `workflow_dispatch` | `ci.yml` | `smoke` with selected inputs |
| Daily cron 02:00 UTC | `nightly.yml` | Full regression (chromium + firefox + webkit) |
| Manual `workflow_dispatch` | `nightly.yml` | Full regression with selected env/threads |

> **PRs get smoke only** — this is intentional. Running the full 3-browser matrix on every PR would consume ~24 runner-minutes per PR. Smoke is the fast signal that code compiles and core flows work; the full matrix runs only after merge confirms the branch is clean.

---

## Running Tests

### Locally (Maven)

```bash
# Smoke suite — Chromium, headless (default)
mvn test -P smoke,qa

# Full regression — Firefox, headless
mvn test -P regression,qa -Dbrowser=firefox

# Full regression — WebKit, 6 threads
mvn test -P regression,qa -Dbrowser=webkit -Dthread.count=6

# Smoke with visible browser (debug)
mvn test -P smoke,qa -Dheadless=false

# UAT environment
mvn test -P smoke,uat

# Single test class
mvn test -P qa -Dtest=LoginTest
```

### In CI (manual trigger)

1. Navigate to **Actions → CI — Playwright TestNG** in the GitHub repository.
2. Click **Run workflow**.
3. Select `suite`, `browser`, and `environment`.
4. Click **Run workflow**.

### Via Docker

```bash
# Build the image
docker build -t playwright-tests .

# Run smoke (default)
docker run --rm \
  --shm-size=2gb \
  -v "$(pwd)/test-output:/app/test-output" \
  playwright-tests

# Run regression with Firefox
docker run --rm \
  --shm-size=2gb \
  -v "$(pwd)/test-output:/app/test-output" \
  playwright-tests test -P regression,qa -Dbrowser=firefox -Dheadless=true

# Use the convenience script
chmod +x scripts/run-tests.sh
./scripts/run-tests.sh -s smoke -b chromium
./scripts/run-tests.sh -s regression -b firefox -e uat
./scripts/run-tests.sh --help
```

> **Why `--shm-size=2gb`?** Chromium uses `/dev/shm` for shared memory between renderer processes. The default Docker `/dev/shm` is 64 MB, which causes Chromium to crash. `--shm-size=2gb` is the correct fix. The `PlaywrightFactory` also passes `--disable-dev-shm-usage` as a launch arg, which moves shared memory to `/tmp` as an additional fallback.

---

## Docker Usage

### Build the image

```bash
docker build -t playwright-tests .

# Force rebuild (e.g. after pom.xml changes)
docker build --no-cache -t playwright-tests .
```

### Image layer structure

```
Layer 1  eclipse-temurin:17-jdk-jammy + Maven        (~500 MB, rarely changes)
Layer 2  Maven dependency resolution                  (~150 MB, changes when pom.xml changes)
Layer 3  Playwright browsers + system deps            (~800 MB, changes when playwright version changes)
Layer 4  Compiled test sources                        (~5 MB, changes with every code change)
```

Layers 1–3 are cached and reused unless their inputs change, which means typical rebuilds after a code change take about **15 seconds**.

### Retrieve results

Mount `/app/test-output` to access reports, screenshots, and logs on the host:

```bash
docker run --rm \
  -v "$(pwd)/test-output:/app/test-output" \
  playwright-tests
```

Results are written to:

```
test-output/
├── reports/TestReport.html     ← ExtentReports HTML report
├── screenshots/                ← captured on test failure
└── logs/                       ← Log4j2 rolling log files
```

---

## Reporting

### ExtentReports HTML report

Generated automatically at the end of every test run:

```
test-output/reports/TestReport.html
```

- Dark-theme, mobile-responsive HTML
- Thread-safe: parallel test results are correctly attributed
- Includes: test status, duration, screenshots (embedded on failure), log output

**In CI:** the report is uploaded as a GitHub Actions artifact named `smoke-report-<run-id>` or `regression-<browser>-<run-id>`. Download it from the **Summary** page of the Actions run.

### JUnit XML reports (Surefire)

Generated by Maven Surefire in:

```
target/surefire-reports/
├── TEST-com.automation.tests.LoginTest.xml
├── TEST-com.automation.tests.SearchTest.xml
└── ...
```

These are compatible with any CI reporting tool that understands JUnit XML (Jenkins, Azure DevOps, GitLab CI, Allure).

### Nightly job summary

The nightly workflow posts a markdown summary table to the GitHub Actions run summary for each browser:

```
Nightly — chromium
| Tests | Failures | Errors |
|-------|----------|--------|
| 42    | 0        | 0      |
```

### Debugging failures

1. Download the failure artifact (`smoke-failure-<run-id>` or `regression-<browser>-<run-id>`).
2. Open `test-output/reports/TestReport.html` in a browser.
3. For visual evidence, check `test-output/screenshots/` — screenshots are named `<testName>_FAILURE_<timestamp>_<thread>.png`.
4. Read `test-output/logs/` for structured Log4j2 output with thread IDs.
5. Check `target/surefire-reports/` XML files for raw TestNG output including stack traces.

---

## Environment Variables & Configuration

### Maven system properties (highest priority)

| Property | Default | Description |
|---|---|---|
| `browser` | `chromium` | `chromium`, `firefox`, or `webkit` |
| `headless` | `true` | `true` or `false` |
| `env` | `qa` | `qa`, `uat`, or `prod` |
| `testng.suite` | `testng.xml` | path to TestNG XML suite file |
| `thread.count` | `4` | parallel thread count |
| `trace.enabled` | `false` | enables Playwright video recording |

Override any property at runtime:

```bash
mvn test -P smoke -Dbrowser=webkit -Dheadless=false -Dthread.count=2
```

### Config file hierarchy

```
config.properties         ← base defaults
  └── qa.properties       ← QA environment overrides
  └── uat.properties      ← UAT environment overrides
  └── prod.properties     ← prod environment overrides
      └── -D flags        ← runtime overrides (highest priority)
```

`ConfigReader` merges these layers in order — the last value wins.

### Playwright environment variable

| Variable | Purpose |
|---|---|
| `PLAYWRIGHT_BROWSERS_PATH` | Override where browser binaries are stored/found |

In GitHub Actions this is set to `${{ github.workspace }}/.cache/ms-playwright` to enable caching across workflow runs.

---

## Troubleshooting

### `[FATAL] Non-parseable POM` — double dash in XML comment

**Symptom:**
```
[FATAL] Non-parseable POM .../pom.xml: in comment after two dashes (--)
next character must be > not w (position: END_TAG seen ...exec.args="install --w... @130:102)
```
The `Resolve Maven dependencies` step fails immediately; nothing else runs.

**Root cause:**
The XML specification (section 2.5) forbids the sequence `--` anywhere inside an XML comment except as the closing `-->`. Maven's POM parser enforces this strictly — a comment like:

```xml
<!-- usage: mvn exec:java -Dexec.args="install --with-deps" -->
<!--                                               ^^ ILLEGAL  -->
```

causes a fatal parse error before Maven reads a single dependency. The build never starts.

**Fix:**
Rewrite any `pom.xml` comment that contains a CLI flag with double dashes. Either remove the example from the comment or rephrase it without the `--` sequence:

```xml
<!-- Before (broken) -->
<!-- installs browsers: mvn exec:java -Dexec.args="install --with-deps" -->

<!-- After (fixed) -->
<!-- runs the Playwright CLI for browser installation.
     See CI_CD_GUIDE.md for usage examples. -->
```

**Rule of thumb:** Never paste shell commands containing `--flags` into XML comments. Put them in a `README`, a guide, or a code-level string constant instead.

---

### Browsers fail to launch in CI

**Symptom:** `Browser.launch()` throws with `Executable doesn't exist at ...`

**Cause:** Browser binaries were not installed before the test run.

**Fix:** Ensure the browser installation step ran successfully:
```bash
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium"
```

Check that `PLAYWRIGHT_BROWSERS_PATH` is consistent between the install step and the test run.

---

### Chromium crashes with `Segmentation fault` or `/dev/shm` error

**Symptom:** Tests fail immediately with a native crash or shared memory error.

**Cause:** Docker's default `/dev/shm` (64 MB) is too small for Chromium.

**Fix:**
```bash
docker run --shm-size=2gb ...
```
`PlaywrightFactory` also passes `--disable-dev-shm-usage` as a fallback.

---

### `--no-sandbox` required in CI

**Symptom:** `Failed to launch chromium because sandbox is not supported...`

**Cause:** Linux CI environments often run as root inside containers, which requires disabling the sandbox.

**Fix:** Already handled — `PlaywrightFactory.launchBrowser()` always passes `--no-sandbox` and `--disable-dev-shm-usage`.

---

### WebKit on Linux requires additional fonts

**Symptom:** WebKit renders pages with garbled text or missing fonts.

**Fix:** Install the full font stack:
```bash
apt-get install -y fonts-liberation fonts-noto fontconfig
```
The `playwright install --with-deps webkit` command should handle this, but some minimal images may need it explicitly.

---

### Tests fail only in CI, pass locally

**Common causes:**

1. **Timing** — CI machines are slower. Increase `default.timeout` in `config.properties` or use the `-Ddefault.timeout=60000` flag.
2. **Network** — The AUT may be slower to respond from CI. The `NETWORKIDLE` wait in `BaseTest.setUp()` helps, but flaky network behaviour may require explicit waits.
3. **Font rendering** — Screenshots used in assertions may differ by OS. Avoid pixel-level screenshot comparisons.
4. **Locale/timezone** — The browser context uses `en-US` / `America/New_York`; check if the CI runner's system locale interferes.

---

### Maven download fails in CI

**Symptom:** `Could not resolve dependencies` during the workflow.

**Fix:** Check that Maven Central is reachable from the runner. If using a private Nexus/Artifactory mirror, add a `settings.xml` to `.github/` and reference it:
```yaml
- name: Set up JDK
  uses: actions/setup-java@v4
  with:
    java-version: '17'
    distribution: temurin
    cache: maven
    server-id: your-nexus-id
    server-username: NEXUS_USER
    server-password: NEXUS_PASSWORD
```

---

## Future Improvements

### Cloud browser execution

Replace local browser execution with a cloud grid (BrowserStack Automate, Sauce Labs, LambdaTest) by changing `PlaywrightFactory.launchBrowser()` to connect via `BrowserType.connect(wsEndpoint)`:

```java
Browser browser = playwright.chromium().connect("wss://your-cloud-grid-endpoint");
```

This eliminates browser installation from CI entirely.

### Allure reporting

Add `allure-testng` integration for richer, interactive reports:

1. Add the Allure TestNG dependency to `pom.xml`.
2. Add the `allure-maven` plugin.
3. Wire the Allure listener in `testng.xml`.
4. Use the [allure-report GitHub Action](https://github.com/marketplace/actions/allure-report-with-history) to publish to GitHub Pages.

### Test sharding

For suites with 500+ tests, split execution across multiple runners using TestNG's `data-provider-thread-count` and GitHub Actions' matrix with index-based sharding:

```yaml
matrix:
  shard: [1, 2, 3, 4]  # 4 runners, each taking 25% of tests
```

### Slack / Teams notifications

Add a notification step at the end of the nightly workflow:

```yaml
- name: Notify Slack on failure
  if: failure()
  uses: slackapi/slack-github-action@v1.27.0
  with:
    payload: '{"text": "Nightly run FAILED on ${{ matrix.browser }}: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"}'
  env:
    SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
```

### Playwright trace files

Enable Playwright trace recording for failed tests by setting `trace.enabled=true` in `config.properties` (or via `-Dtrace.enabled=true`). Upload the trace ZIP to artifacts and inspect it with `npx playwright show-trace trace.zip`.
