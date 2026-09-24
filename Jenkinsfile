// Declarative pipeline: test -> security scan -> image -> (optional) push to Amazon ECR.
// The agent needs JDK 21 and a Docker CLI; the integration tests start PostgreSQL via Testcontainers.
pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    environment {
        IMAGE = "snappark:${env.BUILD_NUMBER}"
    }

    stages {
        stage('Build & Test') {
            steps {
                sh 'chmod +x mvnw && ./mvnw -B verify'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }

        stage('Security Scan') {
            steps {
                // SpotBugs + FindSecBugs; any finding at Medium or above fails the build
                sh './mvnw -B spotbugs:check'
            }
        }

        stage('Docker Image') {
            steps {
                sh 'docker build -t "$IMAGE" .'
            }
        }

        stage('Push to Amazon ECR') {
            // Runs only when the agent is configured for AWS (e.g. an EC2 agent with an ECR push role).
            when {
                expression { env.ECR_REGISTRY?.trim() }
            }
            steps {
                sh '''
                    aws ecr get-login-password --region "$AWS_REGION" \
                      | docker login --username AWS --password-stdin "$ECR_REGISTRY"
                    docker tag "$IMAGE" "$ECR_REGISTRY/snappark:$BUILD_NUMBER"
                    docker push "$ECR_REGISTRY/snappark:$BUILD_NUMBER"
                '''
            }
        }
    }

    post {
        always {
            sh 'docker image rm "$IMAGE" || true'
        }
    }
}
