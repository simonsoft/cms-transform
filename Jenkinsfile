/*
 * Canonical Multibranch Maven pipeline for Java/Quarkus repositories (CMS-2050).
 *
 * Source of truth: simonsoft-agents/jenkins/java-quarkus/Jenkinsfile
 * Policy and setup: simonsoft-agents/docs/maven-versioning.md
 * Copy this file unchanged to the repository root. It needs no per-repo edits.
 *
 * Version policy (committed revision 4.1-SNAPSHOT):
 *   main / master            -> 4.1-SNAPSHOT,         verify + deploy
 *   maintenance/4.0.x        -> 4.0-SNAPSHOT,         verify + deploy
 *   PR #123                  -> 4.1-PR123-SNAPSHOT,   verify + deploy
 *   tag v4.1.2               -> 4.1.2,                verify + deploy
 *   any other branch         -> 4.1-SNAPSHOT,         verify only
 *
 * Jenkins discovers only master/main, maintenance/MAJOR.MINOR.x, PRs and tags; the
 * verify-only case guards against a misconfigured discovery filter.
 *
 * The root POM must use Maven CI-friendly versioning:
 *   <version>${revision}</version>
 *   <properties><revision>4.1-SNAPSHOT</revision></properties>
 *
 * Maven runs inside withMaven so Jenkins records consumed dependencies,
 * produced artifacts, fingerprints, and the Pipeline Maven dependency graph.
 *
 * The post block reports Testcontainers containers that the build left running, in
 * the log and to Slack (Slack Notification plugin). It removes them where the node has
 * /etc/jenkins-builder/remove-leftover-test-containers; otherwise the podman-reaper job does.
 *
 * withMaven gets the Jenkins tool installations (Manage Jenkins -> Tools), both
 * installed automatically: 'maven-3.9' from Apache, 'jdk-21' from adoptium.net
 * (Eclipse Temurin). It sets PATH and JAVA_HOME for each Maven run. A tools {}
 * block is not enough: withMaven then uses the node's own Java.
 */

pipeline {
    agent any

    options {
        // A Multibranch child job publishes one stable Maven coordinate. Do not let
        // two builds of the same PR/branch/tag race while publishing that coordinate.
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        // Jenkins tool installations (Manage Jenkins -> Tools), used by every withMaven.
        MAVEN_TOOL = 'maven-3.9'
        JDK_TOOL = 'jdk-21'
    }

    // Values derived in "Determine version" are set on env from the script block.
    // Do not declare them in the environment {} directive: declarative environment
    // values cannot be overwritten from script, so they would stay empty.

    stages {
        stage('Determine version') {
            steps {
                script {
                    // Read the literal committed property. No Maven run: it would need
                    // repository settings and is not part of the dependency graph.
                    def committedVersion = readMavenPom(file: 'pom.xml').properties['revision']

                    if (!(committedVersion ==~ /\d+\.\d+-SNAPSHOT/)) {
                        error("Expected committed revision like 4.1-SNAPSHOT in pom.xml, got '${committedVersion}'")
                    }

                    def developmentLine = committedVersion.replaceFirst(/-SNAPSHOT$/, '')
                    def buildKind
                    def buildVersion

                    if (env.TAG_NAME) {
                        if (!(env.TAG_NAME ==~ /v\d+\.\d+\.\d+/)) {
                            error("Release tag must match vMAJOR.MINOR.PATCH; got '${env.TAG_NAME}'")
                        }

                        def releaseVersion = env.TAG_NAME.substring(1)
                        def parts = releaseVersion.tokenize('.')
                        def releaseLine = "${parts[0]}.${parts[1]}"
                        if (releaseLine != developmentLine) {
                            error("Tag ${env.TAG_NAME} is for ${releaseLine}, but this source line is ${committedVersion}")
                        }

                        buildKind = 'release'
                        buildVersion = releaseVersion
                    } else if (env.CHANGE_ID) {
                        buildKind = 'pull-request'
                        buildVersion = "${developmentLine}-PR${env.CHANGE_ID}-SNAPSHOT"
                    } else if (env.BRANCH_NAME in ['main', 'master']) {
                        buildKind = 'integration'
                        buildVersion = committedVersion
                    } else if (env.BRANCH_NAME ==~ /maintenance\/\d+\.\d+\.x/) {
                        // A maintenance branch publishes its own line's SNAPSHOT only.
                        def branchLine = env.BRANCH_NAME.replaceFirst(/^maintenance\//, '').replaceFirst(/\.x$/, '')
                        if (branchLine != developmentLine) {
                            error("Branch ${env.BRANCH_NAME} must declare revision ${branchLine}-SNAPSHOT, got ${committedVersion}")
                        }
                        buildKind = 'maintenance'
                        buildVersion = committedVersion
                    } else {
                        buildKind = 'branch'
                        buildVersion = committedVersion
                    }

                    env.BUILD_KIND = buildKind
                    env.BUILD_VERSION = buildVersion
                    // Only PR and tag builds override the committed revision.
                    env.MAVEN_REVISION_ARG = (buildVersion == committedVersion) ? '' : "-Drevision=${buildVersion}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${buildVersion}"

                    echo "Build kind: ${buildKind}"
                    echo "Maven version: ${buildVersion}"
                }

                // Register this build as active on the node: start time and URL. The
                // post block and the podman-reaper job use it to tell left-over
                // test containers from containers that a running build still uses.
                // Nodes without rootless Podman are skipped.
                sh '''
                    set +x
                    dir=/run/user/$(id -u)/jenkins-active-builds
                    if command -v podman > /dev/null && [ -d "/run/user/$(id -u)" ]; then
                        mkdir -p "$dir"
                        echo "$(date +%s) $BUILD_URL" > "$dir/$BUILD_TAG"
                    fi
                '''
            }
        }

        stage('Verify') {
            steps {
                withMaven(maven: env.MAVEN_TOOL, jdk: env.JDK_TOOL, traceability: true) {
                    sh 'mvn ${MAVEN_REVISION_ARG} clean verify'
                }
            }
        }

        stage('Deploy Maven artifacts') {
            when {
                expression { env.BUILD_KIND != 'branch' }
            }
            steps {
                withMaven(maven: env.MAVEN_TOOL, jdk: env.JDK_TOOL, traceability: true) {
                    // Verify has already run. Maven still executes the lifecycle needed
                    // for deploy, but tests are not run a second time.
                    sh 'mvn ${MAVEN_REVISION_ARG} -DskipTests deploy'
                }
            }
        }
    }

    post {
        always {
            script {
                // Testcontainers (Quarkus dev services) runs without Ryuk, so a build
                // that is aborted or killed can leave its containers running. Maven has
                // exited here: a Testcontainers container created since this build
                // started and still running is a leftover, unless another build was
                // active on the node at the same time.
                //
                // The build removes its leftovers only when no other build is active and
                // the node allows it with the file below (the EC2 builder image creates
                // it). Elsewhere it only reports them, so this Jenkinsfile runs unchanged
                // on the controller and on builders.
                def report = sh(returnStdout: true, script: '''
                    set +x
                    rt=/run/user/$(id -u)
                    dir=$rt/jenkins-active-builds
                    own=$dir/$BUILD_TAG
                    [ -f "$own" ] || exit 0
                    start=$(cut -d' ' -f1 "$own")
                    rm -f "$own"
                    export XDG_RUNTIME_DIR=$rt
                    ids=''
                    for id in $(podman ps -q --filter label=org.testcontainers=true); do
                        # The container may stop between ps and inspect.
                        created=$(podman inspect -f '{{.Created.Unix}}' "$id") || continue
                        if [ "$created" -ge "$start" ]; then
                            echo "container $(podman inspect -f '{{.Name}} {{.ImageName}} created {{.Created}}' "$id" || echo "$id")"
                            ids="$ids $id"
                        fi
                    done
                    others=0
                    for f in "$dir"/*; do
                        if [ -f "$f" ]; then
                            echo "other $(cut -d' ' -f2- "$f")"
                            others=1
                        fi
                    done
                    if [ -n "$ids" ] && [ "$others" = 0 ] && [ -f /etc/jenkins-builder/remove-leftover-test-containers ]; then
                        if podman rm -f $ids > /dev/null; then echo removed; else echo remove-failed; fi
                    fi
                ''').trim()

                def leftovers = []
                def others = []
                def outcome = ''
                for (line in report.readLines()) {
                    if (line.startsWith('container ')) {
                        leftovers << line.substring('container '.length())
                    } else if (line.startsWith('other ')) {
                        others << line.substring('other '.length())
                    } else if (line in ['removed', 'remove-failed']) {
                        outcome = line
                    }
                }

                if (leftovers) {
                    def owner = others ?
                        "this build or a build that ran at the same time:\n${others.join('\n')}" :
                        'this build'
                    def action = outcome == 'removed' ? 'The build removed them.' :
                        outcome == 'remove-failed' ? 'Removing them failed; remove them by hand.' :
                        'They stay until the podman-reaper job removes them.'
                    def message = "Test containers left running after <${env.BUILD_URL}|${env.JOB_NAME} #${env.BUILD_NUMBER}> " +
                        "(${currentBuild.currentResult}), started by ${owner}\n" +
                        leftovers.join('\n') + '\n' + action
                    echo message
                    slackSend(color: 'warning', message: message)
                    if (!others) {
                        unstable('Test containers were left running')
                    }
                }
            }
        }
    }
}
