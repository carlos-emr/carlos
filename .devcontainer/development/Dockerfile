FROM tomcat:11.0.24-jdk21-temurin@sha256:543dd74d992458d558875de8493399a718270b361b16e4ea6750371a13dda077 AS ci

# bash with pipefail, not the default /bin/sh (dash), for EVERY RUN below.
#
# This is not style. `curl -fsSL <url> | bash -` under a shell without pipefail reports the exit
# status of the LAST command in the pipeline — bash, which happily exits 0 on empty input — so a
# curl that failed with 403 looked like success and the `&&` chain carried on. That is exactly how
# the Node install silently produced a container with no npm: NodeSource now answers 403 to CI
# runners, curl wrote nothing, bash succeeded, and apt fell back to Ubuntu's nodejs (which packages
# npm separately). The build then died several commands later on `npm: not found`, naming a symptom
# rather than the cause.
#
# With pipefail the pipeline fails at the curl, at the line that is actually wrong. Several other
# steps here use the same `curl | ...` shape and were masked the same way.
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# Set environment variables for Tomcat
ENV CATALINA_HOME /usr/local/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH
# ENV CATALINA_OPTS="--add-opens java.base/java.net=ALL-UNNAMED -agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n"
ENV CATALINA_OPTS="--add-opens java.base/java.net=ALL-UNNAMED -Djava.awt.headless=true -Dcarlos_override_properties=/root/carlos.properties"
# The eForm browser PDF renderer runs headless Chromium unsandboxed by default (Tomcat runs as root
# here and Chromium refuses to start its OS-level sandbox as root); this container IS the isolation
# boundary. No env var is needed for that default. To exercise Chromium's OS sandbox in a container
# that supports it (non-root user + unprivileged user namespaces), set EFORM_RENDER_SANDBOX=true;
# a sandboxed launch that cannot start then fails closed (see EFormBrowserPdfService.sandboxEnabled()).
ENV JPDA_OPTS="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n"
ENV JPDA_ADDRESS=8000
ENV JPDA_TRANSPORT=dt_socket

# Copy Tomcat installation from the official Tomcat image
COPY --from=tomcat:11.0.24-jdk21-temurin@sha256:543dd74d992458d558875de8493399a718270b361b16e4ea6750371a13dda077 /usr/local/tomcat $CATALINA_HOME

# Labels
LABEL author="CARLOS EMR Project" \
      version="1.0.0" \
      description="CARLOS EMR Docker Image for Development Environment"

ARG TOMCAT_PATH=/usr/local/tomcat
ARG DOCS_PATH='/home/oscar/development/volumes'
ARG OSCAR_DOCUMENT="${DOCS_PATH}/OscarDocument"
ARG DB_DOCS="/db-data/documents"
ARG DOCS_DEST="/var/lib/OscarDocument/oscar/document"
ARG EFORM_IMAGES_DIR="/var/lib/OscarDocument/oscar/eform/images"

WORKDIR /workspace

# Retry wrapper for flaky package index mirrors in CI.
RUN cat <<'EOF' > /usr/local/bin/apt-install && chmod +x /usr/local/bin/apt-install
#!/usr/bin/env bash
set -eu
set -o pipefail

for attempt in 1 2 3; do
  if apt-get update -o Acquire::Retries=3 -o Acquire::http::Timeout=20 && \
     DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y --fix-missing "$@"; then
    apt-get clean
    rm -rf /var/lib/apt/lists/*
    exit 0
  fi

  echo "apt install failed (attempt ${attempt}/3), retrying..."
  apt-get clean || true
  rm -rf /var/lib/apt/lists/*
  if [ "$attempt" -eq 3 ]; then
    exit 1
  fi
  sleep 5
done
EOF

# Install required packages including Playwright browser dependencies and clean up apt lists
# Note: Ubuntu 24.04 uses t64 packages (time_t 64-bit migration)
RUN /usr/local/bin/apt-install \
    dos2unix curl git wget apt-transport-https ca-certificates gnupg jq lsb-release \
    libjpeg-turbo8 \
    locales iputils-ping gettext fontconfig libc6 libfreetype6 \
    libpng16-16t64 libssl-dev libstdc++6 libx11-6 libxcb1 libxext6 libxtst6 \
    libxrender1 libfontconfig1 libxi6 xfonts-75dpi xfonts-base zlib1g maven mariadb-client \
    nano openssh-client vim-tiny \
    python3 python3-pip \
    inotify-tools \
    fonts-roboto fonts-liberation fonts-noto-color-emoji

# Install Node.js from the official nodejs.org distribution, NOT NodeSource.
#
# deb.nodesource.com now answers 403 to CI runners (verified: 403 from the GitHub Actions build and
# from a developer machine), so `curl … | bash -` fetched nothing, added no apt repository, and
# `apt-install nodejs` quietly resolved to Ubuntu's nodejs 18. Ubuntu packages npm separately and
# apt-install passes --no-install-recommends, so npm was absent and the build failed later at
# `npm: not found`. Adding an apt repository over the network was the fragile part; this removes it.
#
# The official tarball is a fixed URL with a published checksum, so the install is reproducible and
# tamper-evident. `curl -f` turns an HTTP error into a non-zero exit, and the SHELL pipefail set at
# the top of this file means a failed download can no longer be masked by a later command in a pipe.
#
# Pinned deliberately: an unpinned "latest" reintroduces the moving target that broke this twice.
# Bumping it is a one-line change plus the matching checksum from
# https://nodejs.org/dist/v<version>/SHASUMS256.txt.
# .tar.gz, not .tar.xz: extracting xz needs the `xz` binary, which is not in this base image's
# default package set. A dry run of these exact commands failed on "xz: Cannot exec" — gzip is
# always present, so the gz tarball removes a dependency rather than adding one to apt.
ENV NODE_VERSION=22.11.0
ENV NODE_SHA256=4f862bab52039835efbe613b532238b6e4dde98d139a34e6923193e073438b13
RUN curl -fsSLo /tmp/node.tar.gz "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.gz" \
    && echo "${NODE_SHA256}  /tmp/node.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/node.tar.gz -C /usr/local --strip-components=1 --no-same-owner \
    && rm /tmp/node.tar.gz \
    && node --version && npm --version \
    && case "$(node --version)" in \
         v${NODE_VERSION}) : ;; \
         *) echo "ERROR: expected Node v${NODE_VERSION}, got $(node --version)." >&2; exit 1 ;; \
       esac

# Install the exact Playwright dependency graph recorded by npm. `npm ci` verifies every package
# against the integrity values in package-lock.json and refuses manifest/lockfile drift.
COPY ./ci-tools/package.json ./ci-tools/package-lock.json /opt/carlos-ci-tools/
RUN cd /opt/carlos-ci-tools \
    && npm ci --ignore-scripts \
    && /opt/carlos-ci-tools/node_modules/.bin/playwright --version
ENV PATH="/opt/carlos-ci-tools/node_modules/.bin:${PATH}"

# Install Playwright browsers (Chromium, Firefox) with system dependencies
# Note: Chrome excluded - no ARM64 Linux builds (breaks Apple Silicon). Chromium is equivalent.
# Version 1.60.0 uses the current Playwright browser bundles and Ubuntu 24.04 t64 package names.
RUN playwright install --with-deps chromium firefox && \
    echo "✅ Playwright browsers installed (Chromium, Firefox)"

# Install Postfix for mail testing with default values
RUN /usr/local/bin/apt-install postfix

# Configure Postfix for simple localhost operation
RUN postconf -e 'myhostname=openo-dev.local' && \
    postconf -e 'mydestination=openo-dev.local, localhost' && \
    postconf -e 'mynetworks=127.0.0.0/8' && \
    postconf -e 'inet_interfaces=loopback-only' && \
    postconf -e 'maillog_file=/var/log/mail.log' && \
    # Disable all authentication and encryption
    postconf -e 'smtpd_sasl_auth_enable=no' && \
    postconf -e 'smtpd_use_tls=no' && \
    postconf -e 'smtpd_tls_security_level=none' && \
    # Allow local connections without auth
    postconf -e 'smtpd_client_restrictions=permit_mynetworks,reject'

# Create custom bashrc with tool reminders
COPY ./config/bashrc /root/.bashrc

# Remove unnecessary default www apps in Tomcat
RUN rm -rf $TOMCAT_PATH/webapps/ROOT/* \
    $TOMCAT_PATH/webapps/docs \
    $TOMCAT_PATH/webapps/examples \
    $TOMCAT_PATH/webapps/host-manager \
    $TOMCAT_PATH/webapps/manager

# Create ROOT folder
RUN mkdir -p /usr/local/tomcat/webapps/ROOT

# NOTE: DrugRef2 runs in a separate Java 11 container (see .devcontainer/drugref/)
# Open-Drugref requires Java 11 while Open-O uses Java 21

# Copy scripts and config files
COPY ./scripts /scripts
COPY ./setup/setup.sh /setup-dir/setup.sh
COPY ./setup/setup-hot-reload.sh /setup-dir/setup-hot-reload.sh
COPY ./config/tomcat/conf/server.xml $TOMCAT_PATH/conf/server.xml
COPY ./config/shared/volumes/*.properties /root/

# Copy configuration files
COPY ./openosp_files/index.jsp $TOMCAT_PATH/webapps/ROOT/index.jsp
COPY ./openosp_files/openosp_favicon.ico $TOMCAT_PATH/webapps/ROOT/openosp_favicon.ico
COPY ./openosp_files/logging.properties $TOMCAT_PATH/conf/logging.properties
COPY ./openosp_files/logging-servlet.properties $TOMCAT_PATH/conf/logging-servlet.properties
COPY ./openosp_files/tomcat-users.xml $TOMCAT_PATH/conf/tomcat-users.xml

# Set up document directories, including seeded document data for the database and the destination
# of all documents
RUN mkdir -p $OSCAR_DOCUMENT/oscar/document \
    $OSCAR_DOCUMENT/oscar/billing/download \
    $OSCAR_DOCUMENT/oscar/billing/invoices \
    $OSCAR_DOCUMENT/oscar/form/records \
    $OSCAR_DOCUMENT/oscar/onEDTDocs/inbox \
    $OSCAR_DOCUMENT/oscar/onEDTDocs/outbox \
    $OSCAR_DOCUMENT/oscar/onEDTDocs/sent \
    $OSCAR_DOCUMENT/oscar/onEDTDocs/archive \
    $OSCAR_DOCUMENT/oscar/encounter/oscarMeasurements/styles \
    $OSCAR_DOCUMENT/oscar/incomingdocs \
    $OSCAR_DOCUMENT/oscar/export \
    $DB_DOCS \
    $DOCS_DEST \
    $EFORM_IMAGES_DIR \
    && /scripts/bootstrap-property-directories /root/carlos.properties EFORM_IMAGES_DIR

# Set up GPG for PGP encryption (used by demographic export)
# Create the GNUPGHOME directory and generate a test key for development
RUN mkdir -p /usr/local/gnupg && chmod 700 /usr/local/gnupg \
    && gpg --homedir /usr/local/gnupg --batch --passphrase '' --quick-gen-key "OpenO Dev (Development) <dev@openosp.ca>" default default never \
    && chmod -R 700 /usr/local/gnupg

# Create GPG wrapper script to handle modern GPG argument requirements
# PGPEncrypt.java expects: bin cmd file recipient -> gpg -e file recipient
# Modern GPG needs: gpg -e -r recipient file
# Output file must use .pgp extension (not .gpg) to match Java code expectations
RUN echo '#!/bin/bash' > /usr/local/bin/gpg-encrypt-wrapper \
    && echo 'exec /usr/bin/gpg "$1" -r "$3" --batch --yes --trust-model always -o "$2.pgp" "$2"' >> /usr/local/bin/gpg-encrypt-wrapper \
    && chmod +x /usr/local/bin/gpg-encrypt-wrapper 

# Convert script files from DOS to Unix format, make executable, and set up PATH
RUN dos2unix /scripts/* /setup-dir/*.sh \
    && chmod +x /scripts/* /setup-dir/*.sh \
    && /setup-dir/setup.sh
ENV PATH="/scripts:${PATH}"

# Expose Tomcat port
EXPOSE 8080

# Clean up
RUN apt-get autoclean && apt-get clean && apt-get autoremove

# Set up Maven repository directory
RUN mkdir -p /root/.m2 \
    && chown -R root:root /root/.m2 \
    && chmod -R 777 /root/.m2

# CI CMD
CMD ["tail", "-f", "/dev/null"]

# Developer-only tools live in a separate final stage. CI workflows explicitly build and run the
# `ci` target above, keeping interactive AI clients and their transitive package trees outside the
# trusted build environment. Versions and direct-download checksums are pinned so rebuilding this
# development stage cannot silently select a different tool release.
FROM ci AS development

ENV PATH="${PATH}:/root/.local/bin"

ARG GH_CLI_VERSION=2.97.0
ARG GH_CLI_SHA256=a2c9b8497e1f85b1ad0dfcb78b5a622e098801b8e461e459e88e1ee12f018112
RUN curl -fsSLo /tmp/gh.tar.gz \
        "https://github.com/cli/cli/releases/download/v${GH_CLI_VERSION}/gh_${GH_CLI_VERSION}_linux_amd64.tar.gz" \
    && echo "${GH_CLI_SHA256}  /tmp/gh.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/gh.tar.gz -C /tmp \
    && install -m 0755 "/tmp/gh_${GH_CLI_VERSION}_linux_amd64/bin/gh" /usr/local/bin/gh \
    && rm -rf /tmp/gh.tar.gz "/tmp/gh_${GH_CLI_VERSION}_linux_amd64"

RUN /usr/local/bin/apt-install golang-go

ARG JAVADOC2MD_COMMIT=d480f3c9d5b2f335ab90178bdd7d10311cfa5db2
RUN go install "github.com/dburkart/javadoc2md/cmd/javadoc2md@${JAVADOC2MD_COMMIT}" \
    && install -m 0755 /root/go/bin/javadoc2md /usr/local/bin/javadoc2md

COPY ./dev-tools/package.json ./dev-tools/package-lock.json /opt/carlos-dev-tools/
RUN cd /opt/carlos-dev-tools \
    && npm ci \
    && ln -sf /scripts/gemini-browser /usr/local/bin/xdg-open
ENV PATH="/opt/carlos-dev-tools/node_modules/.bin:${PATH}:/root/.local/bin"

CMD ["tail", "-f", "/dev/null"]
