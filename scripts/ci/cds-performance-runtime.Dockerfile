# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
FROM ubuntu:26.04@sha256:19fcd1bf268f02b60aca3bd30bd8a6d9a11759a430e32921833e65f16d87348a AS cli-builder
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential debhelper dpkg-dev python3 python3-bcrypt groff-base ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY cli /tmp/carlos-ctl
WORKDIR /tmp/carlos-ctl
# This is the companion's actual Debian build, including its own tests.
RUN dpkg-buildpackage -us -uc -b

FROM ubuntu:26.04@sha256:19fcd1bf268f02b60aca3bd30bd8a6d9a11759a430e32921833e65f16d87348a
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-25-jdk-headless tomcat11-common python3 python3-bcrypt python3-defusedxml \
    mariadb-client libxml2-utils util-linux procps strace ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=cli-builder /tmp/carlos-ctl_*_all.deb /tmp/cli-package/
RUN apt-get update && apt-get install -y --no-install-recommends /tmp/cli-package/*.deb \
    && rm -rf /tmp/cli-package /var/lib/apt/lists/* \
    && groupadd --system carlos \
    && useradd --system --gid carlos --home-dir /var/lib/carlos-emr --create-home --shell /usr/sbin/nologin carlos
WORKDIR /workspace
USER carlos
CMD ["sleep", "infinity"]
