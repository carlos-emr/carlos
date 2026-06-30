FROM tomcat:11.0-jdk21-temurin

# Set environment variables for Tomcat
ENV CATALINA_HOME /usr/local/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH
# ENV CATALINA_OPTS="--add-opens java.base/java.net=ALL-UNNAMED -agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n"
ENV CATALINA_OPTS="--add-opens java.base/java.net=ALL-UNNAMED -Djava.awt.headless=true -Dcarlos_override_properties=/root/carlos.properties"
ENV JPDA_OPTS="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n"
ENV JPDA_ADDRESS=8000
ENV JPDA_TRANSPORT=dt_socket

# Copy Tomcat installation from the official Tomcat image
COPY --from=tomcat:11.0-jdk21-temurin /usr/local/tomcat $CATALINA_HOME

# Labels
LABEL author="CARLOS EMR Project" \
      version="1.0.0" \
      description="CARLOS EMR Docker Image for Development Environment"

ARG TOMCAT_PATH=/usr/local/tomcat
ARG DOCS_PATH='/home/oscar/development/volumes'
ARG OSCAR_DOCUMENT="${DOCS_PATH}/OscarDocument"
ARG DB_DOCS="/db-data/documents"
ARG DOCS_DEST="/var/lib/OscarDocument/oscar/document"

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

# Install Node.js (latest LTS)
RUN curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - \
    && /usr/local/bin/apt-install nodejs \
    && node --version && npm --version

# Install GitHub CLI
RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg \
    && chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | tee /etc/apt/sources.list.d/github-cli.list > /dev/null \
    && /usr/local/bin/apt-install gh

# Install Go
RUN /usr/local/bin/apt-install golang-go

# Install javadoc2md utility for converting Javadoc to Markdown
RUN go install github.com/dburkart/javadoc2md/cmd/javadoc2md@latest

# Install Claude Code CLI (native binary) and Docusaurus
ENV PATH="${PATH}:/root/.local/bin"
RUN curl -fsSL https://claude.ai/install.sh | bash \
    && which npm && npm install -g @docusaurus/core @docusaurus/preset-classic

# Install Gemini CLI.
# The gemini-browser script intercepts xdg-open so OAuth URLs from any CLI
# (Gemini, Claude Code, etc.) display cleanly in the terminal for copy-paste
# (containers can't open desktop browsers). See the script header for
# details on OAuth handling and callback port forwarding.
RUN npm install -g @google/gemini-cli \
    && ln -sf /scripts/gemini-browser /usr/local/bin/xdg-open

# Install GitHub Copilot CLI (standalone coding agent with model selection)
RUN npm install -g @github/copilot && \
    echo "✅ GitHub Copilot CLI installed: $(copilot --version)"

# Install OpenAI Codex CLI (standalone coding agent)
RUN npm install -g @openai/codex && \
    echo "✅ OpenAI Codex CLI installed: $(codex --version)"

ARG PLAYWRIGHT_VERSION=1.60.0

# Install Playwright globally first to ensure it's available.
# Keep this in sync with package.json, .mcp.json, and .devcontainer/PLAYWRIGHT_SETUP.md.
RUN npm install -g "playwright@${PLAYWRIGHT_VERSION}" && \
    echo "✅ Playwright CLI installed: $(playwright --version)"

# Install Playwright browsers (Chromium, Firefox) with system dependencies
# Note: Chrome excluded - no ARM64 Linux builds (breaks Apple Silicon). Chromium is equivalent.
# Version 1.60.0 uses the current Playwright browser bundles and Ubuntu 24.04 t64 package names.
RUN playwright install --with-deps chromium firefox && \
    echo "✅ Playwright browsers installed (Chromium, Firefox)"

# Install pipx and Aider AI coding assistant
RUN /usr/local/bin/apt-install pipx && \
    pipx install aider-chat && \
    pipx ensurepath

# Install Postfix for local mail capture testing with default values
RUN /usr/local/bin/apt-install postfix

# Capture script: appends the full raw MIME message (stdin) plus envelope
# metadata to a dev-only capture file. Runs as 'nobody' via the Postfix pipe
# transport (see master.cf below), never as root. See docs/postfix-mail-server.md.
RUN cat <<'EOF' > /usr/local/bin/postfix-capture-mail && chmod 0755 /usr/local/bin/postfix-capture-mail
#!/bin/sh
set -eu

CAPTURE_FILE="/var/log/carlos-mail-capture.eml"
sender="${1:-unknown-sender}"
recipient="${2:-unknown-recipient}"

# Serialize the whole append under an exclusive lock so concurrent devcapture
# deliveries cannot interleave and corrupt the captured MIME. fd 9 opens the
# capture file in append mode and is both the write target and the lock object;
# if flock is unavailable, fall back to a plain (non-atomic) append.
{
  if command -v flock >/dev/null 2>&1; then
    flock 9
  fi
  {
    printf '\n\n===== CARLOS DEV EMAIL CAPTURE =====\n'
    printf 'Captured-At: %s\n' "$(date -Is)"
    printf 'Envelope-From: %s\n' "$sender"
    printf 'Envelope-To: %s\n' "$recipient"
    printf -- '----- BEGIN RAW MESSAGE -----\n'
    cat
    printf -- '\n----- END RAW MESSAGE -----\n'
  } >&9
} 9>> "$CAPTURE_FILE"

exit 0
EOF

# Create the capture file writable by the Postfix pipe user ('nobody').
RUN touch /var/log/carlos-mail-capture.eml && \
    chown nobody:nogroup /var/log/carlos-mail-capture.eml && \
    chmod 0660 /var/log/carlos-mail-capture.eml

# Register a custom pipe transport that streams every accepted message to the
# capture script. ${sender}/${recipient} pass envelope metadata; the raw
# message body arrives on stdin.
RUN printf '%s\n%s\n' \
    'devcapture unix  -       n       n       -       -       pipe' \
    '  flags=q user=nobody argv=/usr/local/bin/postfix-capture-mail ${sender} ${recipient}' \
    >> /etc/postfix/master.cf

# Route every recipient to the capture transport.
RUN printf '/.*/ devcapture:\n' > /etc/postfix/carlos-transport-regexp

# Opt-in real-delivery allowlist (see docs/postfix-mail-server.md). Recipients
# listed here are delivered for REAL, but ONLY when the container is started with
# CARLOS_MAIL_ALLOW_SEND=1 (the 'mail' helper wires the allowlist into
# transport_maps in that case). It ships EMPTY, so the env flag alone sends
# nothing — every message stays captured until an address is added here.
RUN printf '%s\n' \
    '# CARLOS dev mail allowlist (regexp transport map).' \
    '# Recipients matched here are delivered EXTERNALLY when the devcontainer is' \
    '# started with CARLOS_MAIL_ALLOW_SEND=1. Everything not matched is still' \
    '# captured locally. NEVER add a real patient address here — use only your own' \
    '# test inbox. Example (uncomment and edit):' \
    '# /^me@my-test-inbox\.example\.com$/  smtp:' \
    > /etc/postfix/carlos-send-allowlist

# Configure Postfix for localhost-only operation with capture-everything delivery.
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
    postconf -e 'smtpd_client_restrictions=permit_mynetworks,reject' && \
    # Defense in depth: route ALL mail to the capture transport so no accepted
    # message can ever use Postfix's normal outbound smtp delivery. The regexp
    # map matches every recipient; the *_transport overrides catch anything the
    # map somehow misses.
    postconf -e 'transport_maps=regexp:/etc/postfix/carlos-transport-regexp' && \
    postconf -e 'default_transport=devcapture:' && \
    postconf -e 'relay_transport=devcapture:' && \
    postconf -e 'local_transport=devcapture:'

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
    $OSCAR_DOCUMENT/oscar/encounter/oscarMeasurements/styles \
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

# Development CMD
CMD ["tail", "-f", "/dev/null"]
