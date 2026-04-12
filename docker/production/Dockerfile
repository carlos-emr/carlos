# CARLOS EMR - Production Application Container (prebuilt WAR)
#
# This Dockerfile expects a WAR (or exploded WAR directory) to exist at
# the build context path specified by WAR_PATH. Typical uses:
#
#   # Use a prebuilt WAR from GitHub releases
#   curl -L -o carlos.war https://github.com/carlos-emr/carlos/releases/download/vX.Y.Z/carlos.war
#   docker build -f docker/production/Dockerfile -t carlos-emr:latest \
#       --build-arg WAR_PATH=carlos.war .
#
#   # Use a locally-built WAR (after `make install` or `mvn package`)
#   docker build -f docker/production/Dockerfile -t carlos-emr:latest \
#       --build-arg WAR_PATH=target/carlos-0-SNAPSHOT.war .
#
# If you want the build to compile from source instead, use Dockerfile.build.
#
# Base image: Tomcat 11 + JDK 21 Temurin (same as dev for API parity).
# Runs as non-root user 'carlos' (uid 1000).
#
# License: GPL-2.0+

FROM tomcat:11.0-jdk21-temurin

LABEL org.opencontainers.image.source="https://github.com/carlos-emr/carlos"
LABEL org.opencontainers.image.description="CARLOS EMR - Production"
LABEL org.opencontainers.image.licenses="GPL-2.0-or-later"

# Build argument: path to the WAR file or exploded WAR directory relative to
# the build context. Defaults to a file named carlos.war at the context root.
ARG WAR_PATH=carlos.war

# -----------------------------------------------------------------------------
# Install only runtime dependencies.
# -----------------------------------------------------------------------------
# curl    - for the healthcheck script
# fonts   - required by JasperReports, OpenPDF, and barcode rendering (ZXing)
# locales - for consistent number/date formatting
# tzdata  - respected by TZ environment variable for correct audit timestamps
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        fonts-roboto \
        fonts-liberation \
        fonts-noto-color-emoji \
        libfreetype6 \
        libjpeg62-turbo \
        libpng16-16 \
        locales \
        tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen \
    && locale-gen

ENV LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8

# -----------------------------------------------------------------------------
# Remove default Tomcat webapps (ROOT, docs, examples, host-manager, manager).
# These expose version info and admin endpoints that have no place in production.
# -----------------------------------------------------------------------------
RUN rm -rf /usr/local/tomcat/webapps/* /usr/local/tomcat/webapps.dist

# -----------------------------------------------------------------------------
# Apply hardened Tomcat configuration (no AJP, hidden version, access logs).
# -----------------------------------------------------------------------------
COPY docker/production/config/server.xml /usr/local/tomcat/conf/server.xml
COPY docker/production/scripts/healthcheck.sh /usr/local/bin/healthcheck.sh
RUN chmod +x /usr/local/bin/healthcheck.sh

# -----------------------------------------------------------------------------
# Create non-root user. CARLOS writes to document directories, temp files,
# and Tomcat logs - all of which are chowned to this user below.
# -----------------------------------------------------------------------------
RUN groupadd -r -g 1000 carlos && useradd -r -u 1000 -g carlos -d /home/carlos -m carlos

# -----------------------------------------------------------------------------
# Create runtime directories documented in docs/runtime-directories.md.
# BASE_DOCUMENT_DIR defaults to /var/lib/OscarDocument per carlos.properties.
# These must be writable by the carlos user and persisted via a named volume
# (see docker-compose.yml).
# -----------------------------------------------------------------------------
RUN mkdir -p \
        /var/lib/OscarDocument/oscar/document \
        /var/lib/OscarDocument/oscar/eform/images \
        /var/lib/OscarDocument/oscar/billing/download \
        /var/lib/OscarDocument/oscar/billing/invoices \
        /var/lib/OscarDocument/oscar/incomingdocs \
        /var/lib/OscarDocument/oscar/export \
        /etc/carlos \
    && chown -R carlos:carlos \
        /var/lib/OscarDocument \
        /etc/carlos \
        /usr/local/tomcat/logs \
        /usr/local/tomcat/webapps \
        /usr/local/tomcat/work \
        /usr/local/tomcat/temp \
        /usr/local/tomcat/conf/Catalina 2>/dev/null || true \
    && mkdir -p /usr/local/tomcat/conf/Catalina/localhost \
    && chown -R carlos:carlos /usr/local/tomcat/conf/Catalina

# -----------------------------------------------------------------------------
# Copy the WAR. If WAR_PATH is a file ending in .war, Tomcat will explode it
# on first startup (autoDeploy disabled in server.xml, but unpackWARs=true).
# If WAR_PATH is a directory (exploded WAR from `mvn war:exploded`), COPY it
# as the exploded webapp directly.
# -----------------------------------------------------------------------------
COPY --chown=carlos:carlos ${WAR_PATH} /usr/local/tomcat/webapps/carlos

# -----------------------------------------------------------------------------
# Point CARLOS to the config file mounted at runtime. JVM tuning matches
# the dev container's MAVEN_OPTS but with a slightly smaller initial heap
# for production (4G max is the compose mem_limit).
# -----------------------------------------------------------------------------
ENV CATALINA_OPTS="-Xms1G -Xmx3G -XX:+UseG1GC -Dcarlos_override_properties=/etc/carlos/carlos.properties -Djava.awt.headless=true"

# Persistent data mounts (declared as volumes so any write outside them
# is ephemeral - aligns with immutable infrastructure principles).
VOLUME ["/etc/carlos", "/var/lib/OscarDocument"]

EXPOSE 8080

# Health check: 120 second start period for cold-start (Spring init + Hibernate
# schema validation). After that, poll every 30 seconds.
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=120s \
    CMD /usr/local/bin/healthcheck.sh

USER carlos:carlos

CMD ["catalina.sh", "run"]
