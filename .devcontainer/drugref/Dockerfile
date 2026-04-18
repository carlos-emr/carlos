# =============================================================================
# Build stage - contains build tools (git, maven)
# =============================================================================
FROM tomcat:11.0-jdk21-temurin AS builder

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
FROM tomcat:11.0-jdk21-temurin

LABEL author="CARLOS EMR" \
      version="1.0.0" \
      description="CARLOS DrugRef2 Container - Java 21 / Tomcat 11 / Jakarta EE 11"

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

# drugref2.properties is mounted at runtime via docker-compose to /root/drugref2.properties.
# drugref2026 discovers the file via the drugref_override_properties system property;
# without this override the app silently falls back to compiled-in defaults that point at
# the wrong database host/name/credentials. --add-opens keeps Spring/Hibernate reflective
# access working under the JDK 21 module system.
ENV CATALINA_OPTS="--add-opens java.base/java.net=ALL-UNNAMED -Djava.awt.headless=true -Ddrugref_override_properties=/root/drugref2.properties"

EXPOSE 8080
CMD ["catalina.sh", "run"]
