# =============================================================================
# Build stage - contains build tools (git, maven)
# =============================================================================
FROM tomcat:11.0.27-jdk25-temurin@sha256:5f639b0943621fabc57de055c8789a2813692f3b25322379900ffac00958baea AS builder

# Note: DRUGREF_BRANCH accepts branch names or tags, but not commit SHAs
# (shallow clone with --depth 1 doesn't support checking out by SHA)
ARG DRUGREF_REPO=https://github.com/carlos-emr/drugref2026.git
ARG DRUGREF_BRANCH=master

RUN apt-get update && apt-get install -y --no-install-recommends git maven \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build
RUN git clone --depth 1 --branch $DRUGREF_BRANCH $DRUGREF_REPO drugref

WORKDIR /build/drugref
RUN mvn -DskipTests clean package

# =============================================================================
# Final stage - minimal runtime image (no build tools)
# =============================================================================
FROM tomcat:11.0.27-jdk25-temurin@sha256:5f639b0943621fabc57de055c8789a2813692f3b25322379900ffac00958baea

LABEL author="carlos-emr" \
      version="1.0.0" \
      description="CARLOS DrugRef2026 Container - Java 25 / Tomcat 11"

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Remove unnecessary default webapps
RUN rm -rf /usr/local/tomcat/webapps/ROOT/* \
    /usr/local/tomcat/webapps/docs \
    /usr/local/tomcat/webapps/examples \
    /usr/local/tomcat/webapps/host-manager \
    /usr/local/tomcat/webapps/manager

# Copy only the WAR from builder stage
COPY --from=builder /build/drugref/target/drugref2.war /usr/local/tomcat/webapps/drugref2.war

# The image carries no runtime configuration. Without the external context
# descriptor (conf/Catalina/localhost/drugref2.xml), drugref2.properties at
# /root/drugref2.properties, the Hibernate dialect in CATALINA_OPTS and a
# database holding the drugref2 schema, Tomcat still starts but the drugref2
# context does not deploy. Run it as .devcontainer/docker-compose.yml does;
# docs/drugref-service.md ("Running the Standalone Image") lists the settings,
# and scripts/ci/check-drugref-deployment.sh holds CI to them.

EXPOSE 8080
CMD ["catalina.sh", "run"]
