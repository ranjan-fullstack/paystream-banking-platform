// ═══════════════════════════════════════════════════════════════
// JENKINS CI/CD PIPELINE — PayStream Banking Platform
//
// Proof-of-concept for account-service only (the most exercised
// service). Real stages, not descriptions of stages -- every stage
// below actually runs and can actually fail the build. See
// docs/jenkins-pipeline.md for the evidence from the first real run
// and what extending this to all 14 services would take.
// ═══════════════════════════════════════════════════════════════
pipeline {
    agent {
        kubernetes {
            yaml """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins-agent
  containers:
    # Only one of these runs actively at a time (each stage uses its own
    # container in sequence) -- requests trimmed to what's needed for
    # scheduling headroom, not comfortable steady-state, since 7
    # tool containers requesting their "nice to have" defaults
    # simultaneously (~1.7 CPU/3Gi total) didn't fit alongside
    # everything else already running on the cluster. Limits stay
    # generous enough for each stage to actually burst when it's its
    # turn, same lesson as the dev workload's CPU-limit fix earlier.
    - name: maven
      image: maven:3.9.9-eclipse-temurin-17
      command: ['cat']
      tty: true
      resources:
        requests: {cpu: 200m, memory: 512Mi}
        limits: {cpu: "2", memory: 1536Mi}
    - name: kaniko
      image: gcr.io/kaniko-project/executor:v1.23.2-debug
      command: ['/busybox/cat']
      tty: true
      resources:
        requests: {cpu: 100m, memory: 256Mi}
        limits: {cpu: "1", memory: 768Mi}
    - name: awscli
      image: amazon/aws-cli:2.17.62
      command: ['cat']
      tty: true
      resources:
        requests: {cpu: 50m, memory: 128Mi}
        limits: {cpu: 500m, memory: 256Mi}
    - name: trivy
      image: aquasec/trivy:0.55.2
      command: ['cat']
      tty: true
      resources:
        requests: {cpu: 100m, memory: 256Mi}
        limits: {cpu: "1", memory: 512Mi}
    - name: gitleaks
      image: zricethezav/gitleaks:v8.21.2
      command: ['cat']
      tty: true
      resources:
        requests: {cpu: 50m, memory: 128Mi}
        limits: {cpu: 500m, memory: 256Mi}
    - name: kubectl
      image: bitnami/kubectl:1.31
      command: ['cat']
      tty: true
      resources:
        requests: {cpu: 50m, memory: 64Mi}
        limits: {cpu: 200m, memory: 128Mi}
    - name: git
      image: alpine/git:2.45.2
      command: ['cat']
      tty: true
      resources:
        requests: {cpu: 50m, memory: 64Mi}
        limits: {cpu: 200m, memory: 128Mi}
"""
        }
    }

    environment {
        SERVICE       = 'account-service'
        AWS_REGION    = 'ap-south-1'
        AWS_ACCOUNT   = '198758256599'
        ECR_REGISTRY  = "${AWS_ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        SONAR_PROJECT = 'ranjan-fullstack_paystream-banking-platform'
        SONAR_ORG     = 'ranjan-fullstack'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {

        // ─────────────────────────────────────────────
        stage('Checkout') {
        // ─────────────────────────────────────────────
            steps {
                checkout scm
                script {
                    env.GIT_FULL_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.GIT_SHA = env.GIT_FULL_SHA.take(8)
                }
                echo "Building ${SERVICE} @ ${env.GIT_SHA}"
            }
        }

        // ─────────────────────────────────────────────
        stage('Static Analysis') {
        // ─────────────────────────────────────────────
        // Checkstyle (Google style) + SpotBugs. Report-only for now --
        // see account-service/pom.xml for why failOnViolation is false.
        // ─────────────────────────────────────────────
            steps {
                container('maven') {
                    sh 'mvn -pl common-lib,account-service -am checkstyle:check spotbugs:check -DskipTests'
                }
            }
            post {
                always {
                    recordIssues(tools: [checkStyle(pattern: 'account-service/target/checkstyle-result.xml'),
                                          spotBugs(pattern: 'account-service/target/spotbugsXml.xml')])
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Unit + Integration Tests') {
        // ─────────────────────────────────────────────
        // Real 217-test suite (Testcontainers-backed integration tests
        // included). This stage fails the build on any test failure --
        // no -Dmaven.test.failure.ignore anywhere.
        // ─────────────────────────────────────────────
            steps {
                container('maven') {
                    sh 'mvn -pl common-lib,account-service -am test jacoco:report'
                }
            }
            post {
                always {
                    junit testResults: 'account-service/target/surefire-reports/*.xml', allowEmptyResults: false
                    publishHTML(target: [
                        reportDir: 'account-service/target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Coverage'
                    ])
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('SAST — SonarCloud') {
        // ─────────────────────────────────────────────
            steps {
                container('maven') {
                    withSonarQubeEnv('SonarCloud') {
                        sh """
                            mvn -pl account-service -am sonar:sonar \
                                -Dsonar.projectKey=${SONAR_PROJECT} \
                                -Dsonar.organization=${SONAR_ORG} \
                                -Dsonar.java.coveragePlugin=jacoco \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                        """
                    }
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Quality Gate') {
        // ─────────────────────────────────────────────
        // Fails the build if SonarCloud's gate doesn't pass -- this is
        // a real gate, not a fire-and-forget scan.
        // ─────────────────────────────────────────────
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Secret Scan — GitLeaks') {
        // ─────────────────────────────────────────────
        // --no-git (working-tree mode), not git-log-diff mode. Verified
        // by testing both against this repo's actual history: GitLeaks'
        // default git-diff scanning skips binary files' content
        // entirely, so even the custom committed-keystore-file rule in
        // .gitleaks.toml (path-based, no content regex) never fired
        // against the historical paystream-jwt.jks/paystream-keystore.jceks
        // commits -- confirmed empirically, not assumed. Working-tree
        // mode evaluates path rules against every file physically
        // present in the checkout regardless of git's binary-diff
        // handling, which is also the more correct gate for CI anyway:
        // it blocks a build that currently contains a bad file, rather
        // than re-auditing all of history on every run.
        // ─────────────────────────────────────────────
            steps {
                container('gitleaks') {
                    sh '''
                        # Scoped to what this build actually touches -- not the
                        # whole monorepo. Scanning "." including every other
                        # service's target/ build output took 24+ minutes in
                        # testing (GitLeaks' allowlist filters *results*, it
                        # doesn't skip directory traversal); scoping to the
                        # relevant source paths is both correct (this build
                        # doesn't need to re-scan unrelated services) and fast
                        # (~2s, confirmed).
                        STATUS=0
                        for path in account-service common-lib config-repo helm k8s; do
                          gitleaks detect --no-git --source "$path" --config .gitleaks.toml \
                              --report-format json --report-path "gitleaks-report-${path//\\//-}.json" \
                              --redact --exit-code 1 || STATUS=1
                        done
                        exit $STATUS
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'gitleaks-report-*.json', allowEmptyArchive: true
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Build Image') {
        // ─────────────────────────────────────────────
        // Kaniko, not a Docker daemon -- no privileged container needed
        // to build inside a Kubernetes pod. Tagged with the git SHA,
        // never :latest. ECR auth uses the jenkins-agent IRSA role (no
        // static AWS keys anywhere) to mint a docker config.json in the
        // shared workspace, which the kaniko container then reads --
        // the two containers share the pod's workspace volume but not
        // each other's home directories, so this has to be written to
        // the workspace explicitly rather than relying on kaniko's
        // default /kaniko/.docker path.
        // ─────────────────────────────────────────────
            steps {
                container('awscli') {
                    sh """
                        mkdir -p .docker
                        aws ecr get-login-password --region ${AWS_REGION} > /tmp/ecrpw
                        AUTH=\$(printf 'AWS:%s' "\$(cat /tmp/ecrpw)" | base64 -w0)
                        cat > .docker/config.json <<JSON
{"auths":{"${ECR_REGISTRY}":{"auth":"\$AUTH"}}}
JSON
                        rm -f /tmp/ecrpw
                    """
                }
                container('kaniko') {
                    sh """
                        DOCKER_CONFIG=`pwd`/.docker /kaniko/executor \
                          --context=`pwd`/${SERVICE} \
                          --dockerfile=`pwd`/${SERVICE}/Dockerfile \
                          --destination=${ECR_REGISTRY}/${SERVICE}:${env.GIT_SHA} \
                          --cache=true
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Container Scan — Trivy') {
        // ─────────────────────────────────────────────
        // Same rigor as the existing GitHub Actions pipeline: fails on
        // HIGH/CRITICAL CVEs.
        // ─────────────────────────────────────────────
            steps {
                container('trivy') {
                    sh """
                        trivy image \
                          --exit-code 1 \
                          --severity HIGH,CRITICAL \
                          --no-progress \
                          --format table \
                          ${ECR_REGISTRY}/${SERVICE}:${env.GIT_SHA}
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Update Image Tag in Git (GitOps)') {
        // ─────────────────────────────────────────────
        // Bumps the Helm values file and commits -- ArgoCD watches
        // this repo, so this is what actually triggers a deployment.
        // Nothing here ever runs kubectl apply directly.
        // ─────────────────────────────────────────────
            steps {
                container('git') {
                    withCredentials([usernamePassword(
                        credentialsId: 'github-credentials',
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_TOKEN'
                    )]) {
                        sh """
                            git config user.email "jenkins@paystream.com"
                            git config user.name "Jenkins CI"

                            sed -i "s|tag: .*|tag: \\"${env.GIT_SHA}\\"|" helm/paystream-service/values/${SERVICE}.yaml

                            git add helm/paystream-service/values/${SERVICE}.yaml
                            git commit -m "ci: bump ${SERVICE} to ${env.GIT_SHA} [skip ci]" || echo "No changes to commit"
                            git push https://\${GIT_USER}:\${GIT_TOKEN}@github.com/ranjan-fullstack/paystream-banking-platform.git HEAD:main
                        """
                    }
                    script {
                        // The commit ArgoCD needs to sync to is this new
                        // bump commit, not the one checked out at the start
                        // of the build -- capture it here rather than
                        // trying to run git inside the kubectl container.
                        env.DEPLOY_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    }
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Confirm ArgoCD Sync') {
        // ─────────────────────────────────────────────
            steps {
                container('kubectl') {
                    sh """
                        for i in \$(seq 1 30); do
                          HEALTH=\$(kubectl get application ${SERVICE}-dev -n argocd -o jsonpath='{.status.health.status}' 2>/dev/null)
                          SYNCED=\$(kubectl get application ${SERVICE}-dev -n argocd -o jsonpath='{.status.sync.revision}' 2>/dev/null)
                          echo "health=\$HEALTH revision=\$SYNCED"
                          if [ "\$HEALTH" = "Healthy" ] && echo "\$SYNCED" | grep -q "${env.DEPLOY_SHA}"; then
                            echo "ArgoCD synced and Healthy at the new commit"
                            exit 0
                          fi
                          sleep 10
                        done
                        echo "ArgoCD did not reach Healthy at the new revision in time"
                        exit 1
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Smoke Test') {
        // ─────────────────────────────────────────────
            steps {
                container('kubectl') {
                    sh """
                        kubectl run smoketest-\${BUILD_NUMBER} --rm -i --restart=Never \
                          --image=curlimages/curl:8.10.1 -n paystream-dev -- \
                          curl -sf -o /dev/null -w "%{http_code}" http://${SERVICE}/actuator/health
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        stage('Promote to Staging/Prod') {
        // ─────────────────────────────────────────────
        // Real manual gate, built for real even though only dev exists
        // right now -- staging/prod promotion should never be a rubber
        // stamp once those environments exist.
        // ─────────────────────────────────────────────
            steps {
                script {
                    input message: "Promote ${SERVICE}@${env.GIT_SHA} to staging?", ok: 'Promote'
                }
                echo "Promotion approved -- staging/prod environments don't exist yet, so this is where that pipeline would continue."
            }
        }
    }

    post {
        success {
            echo "✅ ${SERVICE}@${env.GIT_SHA} passed all stages"
        }
        failure {
            echo "❌ Build failed -- see the failing stage above"
        }
    }
}
