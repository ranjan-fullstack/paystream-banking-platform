// ═══════════════════════════════════════════════════════════════
// JENKINS CI PIPELINE — PayStream Banking Platform
// (CI only — CD is handled by ArgoCD GitOps)
//
// Builds the full Maven reactor once, then for EACH of the 14
// deployable PayStream microservices: Docker build → Trivy scan →
// push to its own ECR repo → update that service's Helm image tag.
//
// Flow:
//   1. Build + Test + OWASP + SonarQube Quality Gate (whole reactor)
//   2. Upload JARs to Nexus Artifactory
//   3. For each service: Docker Build + Trivy Scan + Push to ECR
//   4. Update each service's image tag in helm/paystream-service/values/<service>.yaml
//   5. ArgoCD detects the Git change → deploys to DEV/STAGING/PROD
// ═══════════════════════════════════════════════════════════════
pipeline {
    agent any

    parameters {
        booleanParam(
            name: 'RUN_PERF_TEST',
            defaultValue: false,
            description: 'Run k6 performance test against staging after deployment'
        )
        choice(
            name: 'PERF_SCENARIO',
            choices: ['smoke', 'average_load', 'stress'],
            description: 'k6 scenario to run (smoke = quick sanity, average_load = SLA gate)'
        )
    }

    environment {
        AWS_REGION    = 'ap-south-1'
        ECR_REGISTRY  = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        IMAGE_TAG     = "${BUILD_NUMBER}"
        SONAR_PROJECT = 'paystream-banking-platform'
        NEXUS_URL     = 'http://localhost:8081/repository/maven-releases'
        SLACK_CHANNEL = '#paystream-deployments'
        STAGING_URL   = 'http://staging.paystream.internal'
        GIT_REPO      = 'https://github.com/ranjan-fullstack/paystream-banking-platform.git'
        // 14 deployable PayStream microservices (common-lib is a shared
        // dependency JAR, not a deployable — excluded here).
        SERVICES      = 'config-server,discovery-server,api-gateway,auth-service,customer-service,account-service,neft-service,rtgs-service,imps-service,upi-service,transaction-service,fraud-detection-service,notification-service,audit-service'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {

        // ─────────────────────────────────────────────
        stage('Checkout') {
        // ─────────────────────────────────────────────
            steps {
                checkout scm
                echo "Building commit: ${env.GIT_COMMIT?.take(7)}"
            }
        }

        // ─────────────────────────────────────────────
        stage('Build & Test (full reactor)') {
        // ─────────────────────────────────────────────
            steps {
                sh '''
                    mvn clean verify \
                        -Dspring.profiles.active=test \
                        -Dmaven.test.failure.ignore=false
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '*/target/surefire-reports/*.xml'
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('OWASP Dependency Check') {
        // ─────────────────────────────────────────────
        // Scans all Maven dependencies (whole reactor) for known CVEs.
        // Fails on CVSS score >= 7 (HIGH).
        // ─────────────────────────────────────────────
            steps {
                sh '''
                    mvn org.owasp:dependency-check-maven:aggregate \
                        -DfailBuildOnCVSS=7 \
                        -Dformat=ALL
                '''
            }
            post {
                always {
                    publishHTML(target: [
                        allowMissing: true,
                        reportDir: 'target',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'OWASP Dependency Report'
                    ])
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('SonarQube Analysis') {
        // ─────────────────────────────────────────────
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh """
                        mvn sonar:sonar \
                            -Dsonar.projectKey=${SONAR_PROJECT} \
                            -Dsonar.projectName='PayStream Banking Platform' \
                            -Dsonar.java.coveragePlugin=jacoco
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Quality Gate') {
        // ─────────────────────────────────────────────
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Package + Upload to Nexus') {
        // ─────────────────────────────────────────────
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'nexus-credentials',
                    usernameVariable: 'NEXUS_USER',
                    passwordVariable: 'NEXUS_PASS'
                )]) {
                    sh """
                        mvn package -DskipTests

                        mvn deploy \
                            -DskipTests \
                            -DaltDeploymentRepository=nexus::default::${NEXUS_URL} \
                            -s /var/jenkins_home/.m2/settings.xml
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Docker Build + Trivy Scan + Push (per service)') {
        // ─────────────────────────────────────────────
        // Loops over every deployable PayStream service: builds its
        // Docker image, scans it with Trivy, and pushes to its own
        // ECR repo (provisioned in infrastructure/ecr.tf).
        // ─────────────────────────────────────────────
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                                  credentialsId: 'aws-credentials']]) {
                    sh """
                        aws ecr get-login-password --region ${AWS_REGION} \
                          | docker login --username AWS --password-stdin ${ECR_REGISTRY}

                        for SERVICE in \$(echo ${SERVICES} | tr ',' ' '); do
                            echo "=== Building \$SERVICE ==="
                            docker build \
                              -t \$SERVICE:${IMAGE_TAG} \
                              -t \$SERVICE:latest \
                              --label git-commit=${env.GIT_COMMIT?.take(7)} \
                              --label build-number=${IMAGE_TAG} \
                              ./\$SERVICE

                            echo "=== Trivy scanning \$SERVICE ==="
                            trivy image \
                                --exit-code 1 \
                                --severity CRITICAL \
                                --no-progress \
                                --format table \
                                \$SERVICE:${IMAGE_TAG}

                            echo "=== Pushing \$SERVICE to ECR ==="
                            docker tag \$SERVICE:${IMAGE_TAG} ${ECR_REGISTRY}/\$SERVICE:${IMAGE_TAG}
                            docker tag \$SERVICE:${IMAGE_TAG} ${ECR_REGISTRY}/\$SERVICE:latest
                            docker push ${ECR_REGISTRY}/\$SERVICE:${IMAGE_TAG}
                            docker push ${ECR_REGISTRY}/\$SERVICE:latest
                        done
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Update Image Tags in Git (GitOps)') {
        // ─────────────────────────────────────────────
        // For each service, updates helm/paystream-service/values/<service>.yaml
        // with the new image tag and commits. ArgoCD watches this repo and
        // auto-deploys to DEV/STAGING. PROD requires manual sync approval.
        // ─────────────────────────────────────────────
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'github-credentials',
                    usernameVariable: 'GIT_USER',
                    passwordVariable: 'GIT_TOKEN'
                )]) {
                    sh """
                        git config user.email "jenkins@paystream.com"
                        git config user.name "Jenkins CI"

                        for SERVICE in \$(echo ${SERVICES} | tr ',' ' '); do
                            sed -i "s|repository: .*|repository: \\"${ECR_REGISTRY}/\$SERVICE\\"|g" \
                                helm/paystream-service/values/\$SERVICE.yaml
                            sed -i "s|tag: .*|tag: \\"${IMAGE_TAG}\\"|g" \
                                helm/paystream-service/values/\$SERVICE.yaml
                        done

                        git add helm/paystream-service/values/*.yaml

                        git commit -m "ci: update PayStream service images to ${IMAGE_TAG} [skip ci]" || echo "No changes to commit"

                        git push https://${GIT_USER}:${GIT_TOKEN}@github.com/ranjan-fullstack/paystream-banking-platform.git HEAD:main
                    """
                }
                echo "✅ Image tags updated in Git — ArgoCD will sync DEV + STAGING automatically"
                echo "ℹ️  PROD requires manual approval in ArgoCD UI: http://<argocd-url>"
            }
        }

        // ─────────────────────────────────────────────
        stage('Performance Gate (Staging)') {
        // ─────────────────────────────────────────────
        // Runs k6 against the staging environment after ArgoCD deploys.
        // Fails the pipeline if SLA thresholds are breached:
        //   p(95) < 200 ms, p(99) < 500 ms, error rate < 0.1%
        // Enable with: Build with Parameters → RUN_PERF_TEST = true
        // ─────────────────────────────────────────────
            when {
                expression { params.RUN_PERF_TEST == true }
            }
            steps {
                sh 'echo "Waiting 3 minutes for ArgoCD to sync staging..." && sleep 180'

                withCredentials([usernamePassword(
                    credentialsId: 'staging-test-credentials',
                    usernameVariable: 'STAGING_USER',
                    passwordVariable: 'STAGING_PASS'
                )]) {
                    sh """
                        k6 run \
                            -e BASE_URL=${STAGING_URL} \
                            -e USERNAME=\${STAGING_USER} \
                            -e PASSWORD=\${STAGING_PASS} \
                            -e SCENARIO=${params.PERF_SCENARIO} \
                            --out json=k6-staging-results.json \
                            load-testing/products-load-test.js
                    """
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'k6-staging-results.json',
                                     allowEmptyArchive: true
                }
                failure {
                    slackSend(
                        channel: "${SLACK_CHANNEL}",
                        color: 'danger',
                        message: """⚠️ *Performance Gate FAILED* — Build #${IMAGE_TAG}
• Scenario: ${params.PERF_SCENARIO}
• SLA breached: p95 > 200ms or error rate > 0.1%
• PROD promotion blocked — check k6 results: ${env.BUILD_URL}artifact/k6-staging-results.json"""
                    )
                }
            }
        }
    }

    post {
        success {
            slackSend(
                channel: "${SLACK_CHANNEL}",
                color: 'good',
                message: """✅ *CI Build #${IMAGE_TAG} SUCCESS*
• Services built: ${SERVICES}
• Registry: ${ECR_REGISTRY}
• Commit: ${env.GIT_COMMIT?.take(7)}
• ArgoCD syncing DEV + STAGING automatically
• PROD: manual approval needed in ArgoCD"""
            )
        }
        failure {
            slackSend(
                channel: "${SLACK_CHANNEL}",
                color: 'danger',
                message: "❌ *CI Build #${IMAGE_TAG} FAILED* — Check: ${env.BUILD_URL}"
            )
        }
        always {
            cleanWs()
        }
    }
}
