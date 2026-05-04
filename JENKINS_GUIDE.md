# Jenkins Guide — Playwright TestNG Hybrid Framework

## Who This Guide Is For

This guide assumes you have never used Jenkins before. Every concept is explained from scratch. By the end you will have Jenkins running locally, connected to your GitHub repository, and automatically running your Playwright tests.

---

## Part 1 — Understanding Jenkins (Read This First)

### What is Jenkins?

Jenkins is an open-source automation server. Think of it as a dedicated computer whose only job is to watch your repository for changes and run your tests (or any other commands) automatically.

The key difference from GitHub Actions:

| | GitHub Actions | Jenkins |
|---|---|---|
| Where it runs | On GitHub's servers (their cloud) | On a server you control |
| Setup required | Zero — just push a `.yml` file | You install and manage the Jenkins server |
| Cost | Free for public repos | Free software, but you pay for the server |
| Best for | Cloud-hosted repos, quick setup | On-premise environments, full control |

### Key Jenkins vocabulary

| Term | What it means in plain English |
|---|---|
| **Jenkins server** | The computer running Jenkins (could be your laptop, a VM, or a Docker container) |
| **Job / Pipeline** | A configured task — e.g. "run tests when code is pushed" |
| **Jenkinsfile** | A text file in your repo that describes exactly what Jenkins should do, step by step |
| **Agent / Node** | The machine that actually executes the commands (like a GitHub Actions runner) |
| **Stage** | A labelled phase of work — e.g. "Checkout", "Run Tests", "Upload Report" |
| **Step** | A single command inside a stage — e.g. `sh 'mvn test'` |
| **Build** | One execution of a pipeline — Jenkins keeps a history of every build |
| **Artifact** | A file produced by the build that Jenkins saves for you to download — e.g. a report |
| **Post** | Actions that run after stages finish — e.g. archive reports, send notifications |
| **Parameters** | Inputs you can provide when manually triggering a build — e.g. which browser to use |
| **Credentials** | Secrets (passwords, tokens) stored securely inside Jenkins |
| **Webhook** | A signal GitHub sends to Jenkins saying "something changed, go run the pipeline" |

### What is a Jenkinsfile?

A `Jenkinsfile` is exactly like GitHub Actions' `.yml` file — it defines what Jenkins does when a build runs. The difference is it uses a language called **Groovy DSL** and it lives at the root of your repository (not inside `.github/workflows/`).

```
PlaywrightWebIndustryStandard/
├── Jenkinsfile          ← Jenkins reads this
├── pom.xml
├── src/
└── ...
```

When Jenkins finds a `Jenkinsfile` in your repo, it reads it and follows the instructions inside.

---

## Part 2 — What Was Built

### Step 1 — Created `Jenkinsfile`

**File:** [Jenkinsfile](Jenkinsfile)

This is the only file needed for Jenkins. It tells Jenkins exactly what to do every time a build runs. Here is what it contains, explained section by section:

---

#### Section: `agent any`

```groovy
agent any
```

This tells Jenkins: "Run this pipeline on whatever machine is available." In a real company setup you might say `agent { label 'linux' }` to target a specific type of machine. For learning, `agent any` works fine.

---

#### Section: `parameters`

```groovy
parameters {
    choice(name: 'SUITE',   choices: ['smoke', 'regression'], ...)
    choice(name: 'BROWSER', choices: ['chromium', 'firefox', 'webkit'], ...)
    ...
}
```

These create a form in the Jenkins UI. When you click **"Build with Parameters"** you can choose which suite to run, which browser, which environment. Without parameters, Jenkins would always run with hardcoded values.

---

#### Section: `triggers`

```groovy
triggers {
    cron('0 2 * * *')       // runs automatically every night at 2am
    pollSCM('H/5 * * * *')  // checks GitHub every 5 minutes for new commits
}
```

`cron` is a scheduling syntax used in Linux. `0 2 * * *` means "at minute 0, hour 2, every day". `pollSCM` tells Jenkins to check your repository every 5 minutes — if there is a new commit, it triggers a build. (In production you would use a webhook instead, which is more efficient.)

---

#### Section: `environment`

```groovy
environment {
    MAVEN_OPTS = '-Xmx2g ...'
    PLAYWRIGHT_BROWSERS_PATH = "${WORKSPACE}/.cache/ms-playwright"
}
```

These are environment variables available to every stage. `WORKSPACE` is a built-in Jenkins variable that points to the folder where Jenkins checked out your code. Storing Playwright browsers inside `WORKSPACE/.cache` means they survive between builds on the same machine (manual caching, since Jenkins has no built-in cache like GitHub Actions).

---

#### Section: `options`

```groovy
options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timeout(time: 30, unit: 'MINUTES')
    timestamps()
    disableConcurrentBuilds()
}
```

- `buildDiscarder` — only keep the last 10 builds so Jenkins doesn't fill your disk
- `timeout` — kill the build if it runs more than 30 minutes (prevents stuck builds forever)
- `timestamps()` — adds a timestamp to every log line (very helpful for debugging)
- `disableConcurrentBuilds()` — don't run two builds of the same branch at the same time

---

#### Section: `stages`

This is the heart of the pipeline. It defines 5 sequential stages:

```
Stage 1: Checkout              → git clone your repo
Stage 2: Resolve Dependencies  → mvn dependency:resolve (download jars)
Stage 3: Install Browsers      → download Playwright browser binaries
Stage 4: Smoke Tests           → run the smoke suite
Stage 5: Regression            → run all 3 browsers in PARALLEL (main branch only)
```

Stage 5 uses `parallel` blocks — all three browsers run at the same time, just like the GitHub Actions matrix. `failFast false` means Firefox and WebKit keep running even if Chromium fails.

---

#### Section: `post`

```groovy
post {
    always  { cleanWs(...) }   // tidy up workspace after every build
    success { echo "PASSED"  }
    failure { echo "FAILED"  }
}
```

`post` runs after all stages finish. `always` runs regardless of pass or fail. `failure` only runs if something broke. The `cleanWs()` step deletes the workspace to free disk space — but it deliberately excludes the `.cache/ms-playwright` folder so browser binaries are reused next time.

---

## Part 3 — Installing Jenkins Locally

The fastest way to get Jenkins running is via Docker. This gives you a real Jenkins server on your own machine in under 5 minutes.

### Prerequisites

- Docker Desktop installed and running
- Nothing else — Jenkins will be fully inside a Docker container

### Start Jenkins

```bash
# Create a named volume so Jenkins data survives container restarts
docker volume create jenkins-data

# Start Jenkins
docker run -d \
  --name jenkins \
  --restart unless-stopped \
  -p 8080:8080 \
  -p 50000:50000 \
  -v jenkins-data:/var/jenkins_home \
  jenkins/jenkins:lts-jdk17
```

| Flag | What it means |
|---|---|
| `-d` | Run in background |
| `--name jenkins` | Give the container a name so you can refer to it |
| `--restart unless-stopped` | Restart Jenkins automatically if Docker restarts |
| `-p 8080:8080` | Expose Jenkins UI on port 8080 of your laptop |
| `-p 50000:50000` | Port for Jenkins agents to connect |
| `-v jenkins-data:/var/jenkins_home` | Persist Jenkins data in a Docker volume |

### Open Jenkins

1. Go to `http://localhost:8080` in your browser
2. Jenkins will ask for an initial admin password. Get it with:

```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

3. Paste the password into the browser
4. Click **"Install suggested plugins"** — this installs everything you need
5. Create your admin user when prompted
6. Jenkins is ready

---

## Part 4 — Required Plugins

The suggested plugin install covers most needs. Verify these are installed at **Manage Jenkins → Plugins → Installed**:

| Plugin | Why it's needed |
|---|---|
| **Pipeline** | Enables `Jenkinsfile`-based pipelines |
| **Git** | Allows Jenkins to clone GitHub repositories |
| **JUnit** | Publishes test results and shows pass/fail trends |
| **HTML Publisher** | Displays the ExtentReports HTML report inside Jenkins |
| **Workspace Cleanup** | Provides the `cleanWs()` step used in the Jenkinsfile |
| **Timestamper** | Adds timestamps to log lines |

If any are missing: **Manage Jenkins → Plugins → Available** → search and install.

---

## Part 5 — Connecting Your GitHub Repository

### Option A — Multibranch Pipeline (recommended)

A Multibranch Pipeline tells Jenkins: "Watch this entire GitHub repository. For every branch that has a `Jenkinsfile`, create and run a pipeline automatically."

1. **New Item** → enter a name → select **Multibranch Pipeline** → click OK
2. Under **Branch Sources** → click **Add source** → **GitHub**
3. Enter your repository URL: `https://github.com/<your-username>/<repo>.git`
4. For public repos, no credentials are needed. For private repos, see the next step.
5. Under **Build Configuration** → confirm **Script Path** is `Jenkinsfile`
6. Click **Save**

Jenkins will immediately scan all branches, find the `Jenkinsfile`, and create a pipeline for each branch that has one.

### Adding credentials for a private repository

1. **Manage Jenkins → Credentials → System → Global credentials → Add Credentials**
2. Kind: **Username with password**
3. Username: your GitHub username
4. Password: a GitHub **Personal Access Token** (not your password) — create one at `github.com → Settings → Developer settings → Personal access tokens → Tokens (classic)` with `repo` scope
5. ID: `github-credentials` (you will reference this in Jenkins)

Then in the Multibranch Pipeline source, select `github-credentials` from the Credentials dropdown.

### Option B — Freestyle / Single Pipeline (simpler but less powerful)

1. **New Item** → name → **Pipeline** → OK
2. Scroll to **Pipeline** section
3. Definition: **Pipeline script from SCM**
4. SCM: **Git**
5. Repository URL: your GitHub URL
6. Script Path: `Jenkinsfile`
7. Click **Save**

---

## Part 6 — Triggering Builds

### Automatic trigger via polling

The `Jenkinsfile` already has `pollSCM('H/5 * * * *')` — Jenkins checks GitHub every 5 minutes. If there is a new commit, it starts a build automatically. No setup needed.

### Automatic trigger via webhook (better for production)

Polling wastes resources. A webhook is more efficient — GitHub notifies Jenkins the moment you push, instead of Jenkins checking every 5 minutes.

**Setup:**

1. In Jenkins: **Manage Jenkins → Configure System** → find **GitHub** section → add your GitHub server and credentials
2. In your GitHub repo: **Settings → Webhooks → Add webhook**
   - Payload URL: `http://<your-jenkins-ip>:8080/github-webhook/`
   - Content type: `application/json`
   - Events: **Just the push event**
3. In your Jenkins Pipeline job: under **Build Triggers** → tick **GitHub hook trigger for GITScm polling**

> Note: For webhooks to work, Jenkins must be accessible from the internet. For a local Jenkins on your laptop, use [ngrok](https://ngrok.com) to create a public tunnel: `ngrok http 8080`, then use the ngrok URL as the webhook payload URL.

### Manual trigger

1. Go to your pipeline in Jenkins
2. Click **Build with Parameters** (left sidebar)
3. Select your suite, browser, and environment
4. Click **Build**

---

## Part 7 — Reading Results

### Build status colours

| Colour | Meaning |
|---|---|
| Blue / Green | All tests passed |
| Yellow | Build is **unstable** — some tests failed but the build itself ran |
| Red | Build **failed** — either a compilation error or all tests errored out |
| Grey | Build was **aborted** |

### Test results

Click any build number → **Test Result** — Jenkins shows:
- Total tests run
- How many passed, failed, skipped
- Which specific test methods failed
- Stack traces for failures

### Artifacts

Click any build number → **Build Artifacts** — download:
- `test-output/reports/TestReport.html` — ExtentReports HTML report (open in browser)
- `test-output/screenshots/` — failure screenshots
- `test-output/logs/` — Log4j2 output
- `target/surefire-reports/` — raw JUnit XML

### Build history trend

On the pipeline main page, Jenkins shows a **Test Result Trend** graph — a bar chart of pass/fail counts across the last N builds. This is the fastest way to spot if a recent commit introduced failures.

---

## Part 8 — Pipeline Flow Diagram

```
Code pushed to GitHub
        │
        │  (webhook or poll, every 5 min)
        ▼
Jenkins detects change
        │
        ▼
┌──────────────────────────────────────────┐
│  Stage 1: Checkout                       │
│  Stage 2: Resolve Dependencies           │
│  Stage 3: Install Playwright Browsers    │
│  Stage 4: Smoke Tests        (all branches)
│    └── JUnit results published           │
│    └── Artifacts archived on failure     │
└──────────────────┬───────────────────────┘
                   │ (main branch only)
                   ▼
┌──────────────────────────────────────────────────────────┐
│  Stage 5: Regression — Cross Browser (parallel)          │
│                                                          │
│   Chromium ──────┐                                       │
│   Firefox  ──────┼──→  all run at the same time          │
│   WebKit   ──────┘                                       │
│                                                          │
│   failFast: false — others continue if one browser fails │
└──────────────────────────────────────────────────────────┘
        │
        ▼
Post actions (always)
  - Publish JUnit results
  - Archive artifacts
  - Clean workspace (preserve browser cache)
  - Print PASSED / FAILED / UNSTABLE
```

---

## Part 9 — GitHub Actions vs Jenkins Side by Side

| | GitHub Actions (`ci.yml`) | Jenkins (`Jenkinsfile`) |
|---|---|---|
| **Where pipeline is defined** | `.github/workflows/ci.yml` | `Jenkinsfile` at repo root |
| **Syntax** | YAML | Groovy DSL |
| **Server** | GitHub's servers (no setup) | You run Jenkins yourself |
| **Browser caching** | `actions/cache@v4` built-in step | Manual — check if folder exists |
| **Parallel matrix** | `strategy.matrix` | `parallel { stage(...) }` |
| **Artifacts** | `upload-artifact` action | `archiveArtifacts` step |
| **Test reports** | No built-in viewer | Built-in JUnit trend graph |
| **Scheduled runs** | `cron:` in `on.schedule` | `cron('...')` in `triggers` |
| **Secrets** | GitHub repository secrets | Jenkins Credentials store |
| **Plugins** | GitHub Marketplace actions | Jenkins Plugin Centre (1800+) |

---

## Part 10 — Troubleshooting

### Jenkins can't find `mvn`

**Symptom:** `mvn: command not found` in the build log.

**Fix:** Tell Jenkins where Maven is. In **Manage Jenkins → Tools → Maven** → add a Maven installation. Either point to an existing path or let Jenkins auto-install it. Then reference it in the `Jenkinsfile`:

```groovy
tools {
    maven 'Maven-3.9'   // must match the name you gave it in Tools
    jdk   'JDK-17'
}
```

### `JAVA_HOME` not set

**Symptom:** Maven complains it can't find Java.

**Fix:** In **Manage Jenkins → Tools → JDK** → add a JDK installation pointing to your Java 17 home, or let Jenkins auto-install it. Then add `jdk 'JDK-17'` to the `tools` block above.

### Playwright browsers fail to download (network)

**Symptom:** `mvn exec:java ... install --with-deps` hangs or times out.

**Fix:** The Jenkins server must have outbound internet access to reach `playwright.azureedge.net` (Playwright's CDN) and `packages.microsoft.com` (system packages). If you are behind a corporate proxy, set:

```groovy
environment {
    HTTPS_PROXY = 'http://your-proxy:8080'
    HTTP_PROXY  = 'http://your-proxy:8080'
}
```

### Build stuck / never triggers

**Symptom:** Pushing code does nothing; Jenkins doesn't start a build.

**Cause:** `pollSCM` has not fired yet (it runs every 5 minutes), or the webhook is not set up.

**Quick fix:** Go to the pipeline → click **Scan Multibranch Pipeline Now** (for Multibranch) or **Build Now** to trigger manually.

### `cleanWs` deletes browser cache

**Symptom:** Browsers are re-downloaded on every build despite the cache logic.

**Cause:** `cleanWs` with `deleteDirs: true` is removing everything including `.cache/`.

**Fix:** The `Jenkinsfile` already has an `EXCLUDE` pattern for `.cache/ms-playwright/**`. Verify the `cleanWs` block in your `post.always` section matches exactly what is in the `Jenkinsfile`.

---

## Part 11 — Stopping and Restarting Jenkins

```bash
# Stop Jenkins container
docker stop jenkins

# Start it again (all data preserved in the volume)
docker start jenkins

# View Jenkins logs
docker logs -f jenkins

# Remove Jenkins entirely (WARNING: deletes all jobs and build history)
docker stop jenkins && docker rm jenkins && docker volume rm jenkins-data
```
