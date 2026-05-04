#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# run-tests.sh — Docker-based test runner
#
# Builds the Docker image (if needed) and runs the test suite, mounting the
# local test-output/ directory so reports, screenshots, and logs land on disk.
#
# Usage:
#   ./scripts/run-tests.sh [OPTIONS]
#
# Options:
#   -s, --suite      smoke | regression          (default: smoke)
#   -b, --browser    chromium | firefox | webkit  (default: chromium)
#   -e, --env        qa | uat                     (default: qa)
#   -t, --threads    parallel thread count        (default: 4)
#   --no-cache       force Docker rebuild
#   -h, --help       show this message
#
# Examples:
#   ./scripts/run-tests.sh
#   ./scripts/run-tests.sh -s regression -b firefox
#   ./scripts/run-tests.sh -s smoke -b chromium -e uat
#   ./scripts/run-tests.sh --no-cache -s smoke
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

# ── Defaults ──────────────────────────────────────────────────────────────────
SUITE="smoke"
BROWSER="chromium"
ENV="qa"
THREADS="4"
IMAGE_NAME="playwright-tests"
NO_CACHE=""

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--suite)    SUITE="$2";    shift 2 ;;
    -b|--browser)  BROWSER="$2";  shift 2 ;;
    -e|--env)      ENV="$2";      shift 2 ;;
    -t|--threads)  THREADS="$2";  shift 2 ;;
    --no-cache)    NO_CACHE="--no-cache"; shift ;;
    -h|--help)
      sed -n '/^# Usage/,/^# ─/p' "$0" | head -n -1 | sed 's/^# //'
      exit 0 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

# ── Validation ─────────────────────────────────────────────────────────────────
valid_browsers=("chromium" "firefox" "webkit")
valid_suites=("smoke" "regression")
valid_envs=("qa" "uat")

contains() { local val="$1"; shift; for item in "$@"; do [[ "$item" == "$val" ]] && return 0; done; return 1; }
contains "$BROWSER" "${valid_browsers[@]}" || { echo "Invalid browser: $BROWSER"; exit 1; }
contains "$SUITE"   "${valid_suites[@]}"   || { echo "Invalid suite: $SUITE";   exit 1; }
contains "$ENV"     "${valid_envs[@]}"     || { echo "Invalid env: $ENV";       exit 1; }

# ── Resolve project root ───────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="${PROJECT_ROOT}/test-output"

mkdir -p "$OUTPUT_DIR"

# ── Build image ────────────────────────────────────────────────────────────────
echo "Building Docker image: ${IMAGE_NAME} ..."
docker build ${NO_CACHE} -t "${IMAGE_NAME}" "${PROJECT_ROOT}"

# ── Run tests ──────────────────────────────────────────────────────────────────
echo ""
echo "────────────────────────────────────────────────"
echo " Suite  : ${SUITE}"
echo " Browser: ${BROWSER}"
echo " Env    : ${ENV}"
echo " Threads: ${THREADS}"
echo " Reports: ${OUTPUT_DIR}"
echo "────────────────────────────────────────────────"
echo ""

docker run --rm \
  --shm-size=2gb \
  -v "${OUTPUT_DIR}:/app/test-output" \
  "${IMAGE_NAME}" \
  test \
    -P "${SUITE},${ENV}" \
    -Dbrowser="${BROWSER}" \
    -Dheadless=true \
    -Dthread.count="${THREADS}" \
    --no-transfer-progress

EXIT_CODE=$?

echo ""
if [[ $EXIT_CODE -eq 0 ]]; then
  echo "Tests PASSED. Reports: ${OUTPUT_DIR}/reports/"
else
  echo "Tests FAILED (exit ${EXIT_CODE}). Check: ${OUTPUT_DIR}/screenshots/"
fi

exit $EXIT_CODE
