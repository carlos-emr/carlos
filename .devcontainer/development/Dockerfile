FROM tomcat:9.0.97-jdk21-temurin

# Set environment variables for Tomcat
ENV CATALINA_HOME /usr/local/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH
# ENV CATALINA_OPTS="--add-opens java.base/java.net=ALL-UNNAMED -agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n"
ENV CATALINA_OPTS="--add-opens java.base/java.net=ALL-UNNAMED -Djava.awt.headless=true"
ENV JPDA_OPTS="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n"
ENV JPDA_ADDRESS=8000
ENV JPDA_TRANSPORT=dt_socket

# Copy Tomcat installation from the official Tomcat image
COPY --from=tomcat:9.0.97 /usr/local/tomcat $CATALINA_HOME

# Labels
LABEL author="OpenOSP" \
      version="0.0.2" \
      description="OpenOSP Docker Image for Development Environment"

ARG TOMCAT_PATH=/usr/local/tomcat
ARG DOCS_PATH='/home/oscar/development/volumes'
ARG OSCAR_DOCUMENT="${DOCS_PATH}/OscarDocument"
ARG DB_DOCS="/db-data/documents"
ARG DOCS_DEST="/var/lib/OscarDocument/oscar/document"

WORKDIR /workspace

# Install required packages including Playwright browser dependencies and clean up apt lists
# Note: Ubuntu 24.04 uses t64 packages (time_t 64-bit migration)
RUN apt-get update && apt-get install -y --no-install-recommends \
    dos2unix curl git wget apt-transport-https ca-certificates gnupg lsb-release \
    libjpeg-turbo8 \
    locales iputils-ping gettext fontconfig libc6 libfreetype6 \
    libpng16-16t64 libssl-dev libstdc++6 libx11-6 libxcb1 libxext6 libxtst6 \
    libxrender1 libfontconfig1 libxi6 xfonts-75dpi xfonts-base zlib1g maven mariadb-client \
    nano openssh-client vim-tiny \
    python3 python3-pip \
    inotify-tools \
    fonts-roboto fonts-liberation fonts-noto-color-emoji \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install Node.js (latest LTS)
RUN curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - \
    && apt-get install -y nodejs \
    && node --version && npm --version

# Install GitHub CLI
RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg \
    && chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | tee /etc/apt/sources.list.d/github-cli.list > /dev/null \
    && apt-get update \
    && apt-get install -y gh

# Install Go
RUN apt-get update && apt-get install -y golang-go

# Install javadoc2md utility for converting Javadoc to Markdown
RUN go install github.com/dburkart/javadoc2md/cmd/javadoc2md@latest

# Install Claude Code CLI and Docusaurus
RUN which npm && npm install -g @anthropic-ai/claude-code @docusaurus/core @docusaurus/preset-classic

# Install Gemini CLI.
RUN npm install -g @google/gemini-cli

# Install Playwright globally first to ensure it's available
# Version 1.56.0 has correct Ubuntu 24.04 t64 package names in its dependency list
RUN npm install -g playwright@1.56.0 && \
    echo "✅ Playwright CLI installed: $(npx playwright --version)"

# Install all Playwright browsers (Chromium, Chrome, Firefox) with system dependencies
# Note: --with-deps installs required system libraries for each browser
# Version 1.56.0+ correctly uses t64 package names (e.g., libasound2t64) for Ubuntu 24.04
RUN npx playwright install --with-deps chromium chrome firefox && \
    echo "✅ Playwright browsers installed (Chromium, Chrome, Firefox)"

# Install pipx and Aider AI coding assistant
RUN apt-get update && apt-get install -y pipx && \
    pipx install aider-chat && \
    pipx ensurepath

# Install Postfix for mail testing with default values
RUN DEBIAN_FRONTEND=noninteractive apt-get update && apt-get install -y postfix

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
    $OSCAR_DOCUMENT/oscar/eform/images \
    $OSCAR_DOCUMENT/oscar/form/records \
    $OSCAR_DOCUMENT/oscar/onEDTDocs/inbox \
    $OSCAR_DOCUMENT/oscar/onEDTDocs/outbox \
    $OSCAR_DOCUMENT/oscar/onEDTDocs/sent \
    $OSCAR_DOCUMENT/oscar/onEDTDocs/archive \
    $OSCAR_DOCUMENT/oscar/oscarEncounter/oscarMeasurements/styles \
    $OSCAR_DOCUMENT/oscar/incomingdocs \
    $OSCAR_DOCUMENT/oscar/export \
    $DB_DOCS \
    $DOCS_DEST

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

# Convert script files from DOS to Unix format (if necessary)
RUN dos2unix /scripts/* \
    && dos2unix /setup-dir/setup.sh

# Make scripts executable and add to PATH
RUN chmod +x /scripts/* /setup-dir/*.sh && /setup-dir/setup.sh
ENV PATH="/scripts:${PATH}"

# Expose Tomcat port
EXPOSE 8080

# Clean up
RUN apt-get autoclean && apt-get clean && apt-get autoremove

# Set up Maven repository directory
RUN mkdir -p /root/.m2 \
    && chown -R root:root /root/.m2 \
    && chmod -R 777 /root/.m2

# Development CMD
CMD ["tail", "-f", "/dev/null"]
