# syntax=docker/dockerfile:1
# Build from the repository root, passing the session CA as a BuildKit secret.
# Record the resulting immutable image ID in the private lifecycle configuration.
FROM node:22-bookworm-slim AS node
FROM ubuntu:26.04
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
COPY scripts/ci/requirements.txt /tmp/release-requirements.txt
RUN --mount=type=secret,id=proxy_ca,required=true,mode=0444 \
    sed -i 's|http://archive.ubuntu.com|https://archive.ubuntu.com|g;s|http://security.ubuntu.com|https://security.ubuntu.com|g' /etc/apt/sources.list.d/ubuntu.sources && \
    apt-get -o Acquire::https::CaInfo=/run/secrets/proxy_ca update && \
    DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::https::CaInfo=/run/secrets/proxy_ca install -y --no-install-recommends \
      systemd systemd-sysv ca-certificates curl python3 python3-venv git libxml2-utils gawk mariadb-client poppler-utils openssl && \
    python3 -m venv /opt/review-venv && \
    PIP_CERT=/run/secrets/proxy_ca /opt/review-venv/bin/pip install --require-hashes -r /tmp/release-requirements.txt && \
    ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && \
    rm -rf /var/lib/apt/lists/* /tmp/release-requirements.txt
ENV container=docker
ENV PATH=/opt/review-venv/bin:$PATH
ENV PYTHONDONTWRITEBYTECODE=1
ENV NODE_PATH=/usr/local/lib/node_modules
STOPSIGNAL SIGRTMIN+3
CMD ["/sbin/init"]
