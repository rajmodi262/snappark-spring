# Local Jenkins controller for running this repo's Jenkinsfile. JDK 21 comes with the base image;
# the Docker CLI talks to the host daemon through the mounted socket (Testcontainers + image build).
FROM jenkins/jenkins:lts-jdk21

USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends docker-cli \
 && rm -rf /var/lib/apt/lists/* \
 && git config --system --add safe.directory '*'

RUN jenkins-plugin-cli --plugins \
      configuration-as-code \
      workflow-aggregator \
      git \
      job-dsl \
      junit

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true" \
    CASC_JENKINS_CONFIG=/var/jenkins_conf/casc.yaml
COPY casc.yaml /var/jenkins_conf/casc.yaml
