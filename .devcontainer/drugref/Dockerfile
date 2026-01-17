# =============================================================================
# Build stage - contains build tools (git, maven)
# =============================================================================
FROM tomcat:9.0.97-jdk11-temurin AS builder

# Note: DRUGREF_BRANCH accepts branch names or tags, but not commit SHAs
# (shallow clone with --depth 1 doesn't support checking out by SHA)
ARG DRUGREF_REPO=https://github.com/open-osp/Open-Drugref.git
ARG DRUGREF_BRANCH=main

RUN apt-get update && apt-get install -y --no-install-recommends git maven \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build
RUN git clone --depth 1 --branch $DRUGREF_BRANCH $DRUGREF_REPO drugref

WORKDIR /build/drugref
RUN mvn -DskipTests clean package

# =============================================================================
# Final stage - minimal runtime image (no build tools)
# =============================================================================
FROM tomcat:9.0.97-jdk11-temurin

LABEL author="OpenOSP" \
      version="1.0.0" \
      description="OpenOSP DrugRef2 Container - Java 11 for legacy dependency compatibility"

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

# Note: drugref2.properties is not baked into the image.
# It is mounted at runtime via docker-compose to /root/drugref2.properties
# and DrugRef loads its configuration from that path by default.

EXPOSE 8080
CMD ["catalina.sh", "run"]
