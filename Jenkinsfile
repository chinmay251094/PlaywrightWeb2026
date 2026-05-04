// ─────────────────────────────────────────────────────────────────────────────
// Jenkinsfile — Playwright TestNG Hybrid Framework
//
// Declarative pipeline covering:
//   • Smoke tests  — every push to any branch
//   • Regression   — only when building the main branch, 3 browsers in parallel
//   • Nightly cron — full regression at 02:00 every day
//   • Manual run   — parameterised via the "Build with Parameters" button
//
// Required Jenkins plugins:
//   Pipeline, Git, JUnit, HTML Publisher, Workspace Cleanup
// ─────────────────────────────────────────────────────────────────────────────

pipeline {

    // "agent any" tells Jenkins: run this on whatever machine (agent/node) is
    // available. For Docker-based setups replace with:
    //   agent { docker { image 'playwright-tests' } }
    agent any

    // Maven-3.9 is auto-installed by Jenkins on first use (configured via Tools)
    tools {
        maven 'Maven-3.9'
    }

    // ── Build parameters (shown in "Build with Parameters" UI) ───────────────
    parameters {
        choice(
            name: 'SUITE',
            choices: ['smoke', 'regression'],
            description: 'Test suite to run'
        )
        choice(
            name: 'BROWSER',
            choices: ['chromium', 'firefox', 'webkit'],
            description: 'Browser for the manual run'
        )
        choice(
            name: 'ENVIRONMENT',
            choices: ['qa', 'uat'],
            description: 'Target environment'
        )
        string(
            name: 'THREAD_COUNT',
            defaultValue: '4',
            description: 'Parallel thread count'
        )
    }

    // ── Automatic triggers ────────────────────────────────────────────────────
    triggers {
        // Nightly at 02:00 — same schedule as the GitHub Actions nightly workflow
        cron('0 2 * * *')
        // Poll GitHub every 5 minutes for new commits (use webhook instead in prod)
        pollSCM('H/5 * * * *')
    }

    // ── Environment variables available to every stage ────────────────────────
    environment {
        MAVEN_OPTS             = '-Xmx2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1'
        // Store Playwright browser binaries inside the workspace so they persist
        // across builds on the same agent (manual caching — Jenkins has no built-in cache)
        PLAYWRIGHT_BROWSERS_PATH = "${WORKSPACE}/.cache/ms-playwright"
    }

    // ── Options ───────────────────────────────────────────────────────────────
    options {
        // Keep only the last 10 build records to save disk space
        buildDiscarder(logRotator(numToKeepStr: '10'))
        // Fail the build if it runs longer than 30 minutes
        timeout(time: 30, unit: 'MINUTES')
        // Add timestamps to every log line
        timestamps()
        // Do not run concurrent builds on the same branch
        disableConcurrentBuilds()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STAGES
    // ══════════════════════════════════════════════════════════════════════════
    stages {

        // ── Stage 1: Checkout ────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                // Checks out the branch/commit that triggered this build
                checkout scm
                echo "Branch: ${env.BRANCH_NAME} | Build: ${env.BUILD_NUMBER}"
            }
        }

        // ── Stage 2: Resolve Maven dependencies ─────────────────────────────
        stage('Resolve Dependencies') {
            steps {
                sh 'mvn dependency:resolve -q --no-transfer-progress'
            }
        }

        // ── Stage 3: Install Playwright browsers ─────────────────────────────
        // Checks whether binaries already exist in the workspace cache before
        // downloading (~300 MB each). On the first run they are always downloaded.
        stage('Install Playwright Browsers') {
            steps {
                // System dependencies are pre-installed in the Jenkins container image.
                // We only download browser binaries here (no --with-deps needed).
                sh '''
                    mvn -q --no-transfer-progress exec:java \
                      -Dexec.mainClass=com.microsoft.playwright.CLI \
                      -Dexec.args="install chromium"
                '''
            }
        }

        // ── Stage 4: Smoke Tests ─────────────────────────────────────────────
        // Runs on every branch, every build — fast sanity gate (~3–5 min)
        stage('Smoke Tests') {
            steps {
                sh """
                    mvn test -P smoke,${params.ENVIRONMENT} \
                      -Dbrowser=${params.BROWSER} \
                      -Dheadless=true \
                      --no-transfer-progress
                """
            }
            post {
                always {
                    // Publish JUnit XML to Jenkins test results trend graph
                    junit allowEmptyResults: true,
                          testResults: 'target/surefire-reports/*.xml'

                    // Archive the ExtentReports HTML for download
                    archiveArtifacts artifacts: 'test-output/reports/**',
                                     allowEmptyArchive: true
                }
                failure {
                    // Only archive screenshots and logs when tests fail
                    archiveArtifacts artifacts: 'test-output/screenshots/**, test-output/logs/**',
                                     allowEmptyArchive: true
                }
            }
        }

        // ── Stage 5: Cross-Browser Regression ───────────────────────────────
        // Only runs when building the main branch (after smoke passes).
        // Three browsers run in parallel — each in its own sub-stage.
        stage('Regression — Cross Browser') {
            when {
                anyOf {
                    branch 'main'
                    // Also runs when triggered manually with suite=regression
                    expression { return params.SUITE == 'regression' }
                }
            }
            // fail-fast: false equivalent — continue other browsers if one fails
            failFast false
            parallel {

                stage('Regression — Chromium') {
                    steps {
                        sh """
                            mvn test -P regression,${params.ENVIRONMENT} \
                              -Dbrowser=chromium \
                              -Dheadless=true \
                              -Dthread.count=${params.THREAD_COUNT} \
                              --no-transfer-progress
                        """
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: 'target/surefire-reports/*.xml'
                            archiveArtifacts artifacts: 'test-output/**',
                                             allowEmptyArchive: true
                        }
                    }
                }

                stage('Regression — Firefox') {
                    steps {
                        sh """
                            mvn test -P regression,${params.ENVIRONMENT} \
                              -Dbrowser=firefox \
                              -Dheadless=true \
                              -Dthread.count=${params.THREAD_COUNT} \
                              --no-transfer-progress
                        """
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: 'target/surefire-reports/*.xml'
                            archiveArtifacts artifacts: 'test-output/**',
                                             allowEmptyArchive: true
                        }
                    }
                }

                stage('Regression — WebKit') {
                    steps {
                        sh """
                            mvn test -P regression,${params.ENVIRONMENT} \
                              -Dbrowser=webkit \
                              -Dheadless=true \
                              -Dthread.count=${params.THREAD_COUNT} \
                              --no-transfer-progress
                        """
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: 'target/surefire-reports/*.xml'
                            archiveArtifacts artifacts: 'test-output/**',
                                             allowEmptyArchive: true
                        }
                    }
                }

            } // end parallel
        } // end Regression stage

    } // end stages

    // ══════════════════════════════════════════════════════════════════════════
    // POST — runs after ALL stages, regardless of outcome
    // ══════════════════════════════════════════════════════════════════════════
    post {

        always {
            echo "Pipeline finished — Status: ${currentBuild.currentResult}"
            // Clean the workspace to free disk space (browser cache is preserved
            // because PLAYWRIGHT_BROWSERS_PATH is inside the workspace — remove
            // the cleanWs() call if you want to keep it across builds)
            cleanWs(
                cleanWhenSuccess:  true,
                cleanWhenFailure:  false,   // keep workspace on failure for debugging
                cleanWhenAborted:  true,
                deleteDirs:        true,
                // Preserve the browser binary cache between builds
                patterns: [[pattern: '.cache/ms-playwright/**', type: 'EXCLUDE']]
            )
        }

        success {
            echo "All tests PASSED."
        }

        failure {
            echo "Tests FAILED. Download artifacts from the build page for screenshots and logs."
        }

        unstable {
            echo "Build is UNSTABLE — some tests failed. Check the JUnit report."
        }

    }

}
