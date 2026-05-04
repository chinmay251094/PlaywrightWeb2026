# ─────────────────────────────────────────────────────────────────────────────
# Playwright TestNG Hybrid Framework — Test Execution Image
#
# Base: eclipse-temurin:17-jdk-jammy (JDK 17 on Ubuntu 22.04 LTS)
# Installs: Maven, Playwright browsers (chromium + firefox + webkit) + system deps
#
# Build:   docker build -t playwright-tests .
# Run:     docker run --rm -v $(pwd)/test-output:/app/test-output playwright-tests
# Browser: docker run --rm playwright-tests test -P smoke,qa -Dbrowser=firefox
# ─────────────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:17-jdk-jammy

# ── Environment ────────────────────────────────────────────────────────────
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    MAVEN_OPTS="-Xmx2g -XX:+TieredCompilation" \
    DEBIAN_FRONTEND=noninteractive

# ── System packages ────────────────────────────────────────────────────────
# Maven is in Ubuntu Jammy's apt repo (3.6.3).
# All Playwright browser OS dependencies are handled by `playwright install --with-deps`.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        maven \
        curl \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# ── Working directory ──────────────────────────────────────────────────────
WORKDIR /app

# ── Layer 1: Resolve Maven dependencies ───────────────────────────────────
# Copy pom.xml first so this expensive layer is only re-run when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:resolve -q --no-transfer-progress

# ── Layer 2: Install Playwright browsers + all system dependencies ─────────
# Installs chromium, firefox, and webkit binaries plus every required OS package.
# Running all three browsers keeps the image self-contained for matrix runs.
RUN mvn -q --no-transfer-progress exec:java \
      -Dexec.mainClass=com.microsoft.playwright.CLI \
      -Dexec.args="install --with-deps" && \
    # Smoke-test: verify browsers launched
    mvn -q --no-transfer-progress exec:java \
      -Dexec.mainClass=com.microsoft.playwright.CLI \
      -Dexec.args="--version"

# ── Layer 3: Copy source + TestNG suites ──────────────────────────────────
COPY src ./src
COPY testng.xml testng-smoke.xml testng-regression.xml ./
COPY src/test/resources ./src/test/resources

# ── Layer 4: Compile ───────────────────────────────────────────────────────
RUN mvn compile test-compile -q --no-transfer-progress

# ── Volume: mount test-output on the host to retrieve reports ─────────────
VOLUME ["/app/test-output"]

# ── Entry point ────────────────────────────────────────────────────────────
# Default: smoke suite, Chromium, headless, QA env.
# Override by appending Maven args:
#   docker run --rm playwright-tests test -P regression,qa -Dbrowser=firefox
ENTRYPOINT ["mvn", "--no-transfer-progress"]
CMD ["test", "-P", "smoke,qa", "-Dbrowser=chromium", "-Dheadless=true"]
