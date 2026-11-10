# syntax=docker/dockerfile:1
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
FROM ubuntu:26.04@sha256:19fcd1bf268f02b60aca3bd30bd8a6d9a11759a430e32921833e65f16d87348a AS cli-builder
ENV DEBIAN_FRONTEND=noninteractive
RUN --mount=type=secret,id=ci_ca,required=true,mode=0444 \
    sed -i 's#http://#https://#g' /etc/apt/sources.list.d/ubuntu.sources \
    && for attempt in 1 2 3; do \
      apt-get -o Acquire::https::CaInfo=/run/secrets/ci_ca -o Acquire::https::Timeout=30 -o Acquire::Retries=3 -o Acquire::http::No-Cache=true -o Acquire::By-Hash=force -o APT::Update::Error-Mode=any update && break; \
      test "$attempt" -lt 3 || exit 1; \
      rm -rf /var/lib/apt/lists/*; \
      sleep 2; \
    done \
    && apt-get -o Acquire::https::CaInfo=/run/secrets/ci_ca -o Acquire::https::Timeout=30 -o Acquire::Retries=3 install -y --no-install-recommends \
    build-essential debhelper dpkg-dev python3 python3-bcrypt groff-base ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY cli /tmp/carlos-ctl
WORKDIR /tmp/carlos-ctl
# This is the companion's actual Debian build, including its own tests.
RUN dpkg-buildpackage -us -uc -b

FROM ubuntu:26.04@sha256:19fcd1bf268f02b60aca3bd30bd8a6d9a11759a430e32921833e65f16d87348a
ENV DEBIAN_FRONTEND=noninteractive
RUN --mount=type=secret,id=ci_ca,required=true,mode=0444 \
    sed -i 's#http://#https://#g' /etc/apt/sources.list.d/ubuntu.sources \
    && for attempt in 1 2 3; do \
      apt-get -o Acquire::https::CaInfo=/run/secrets/ci_ca -o Acquire::https::Timeout=30 -o Acquire::Retries=3 -o Acquire::http::No-Cache=true -o Acquire::By-Hash=force -o APT::Update::Error-Mode=any update && break; \
      test "$attempt" -lt 3 || exit 1; \
      rm -rf /var/lib/apt/lists/*; \
      sleep 2; \
    done \
    && apt-get -o Acquire::https::CaInfo=/run/secrets/ci_ca -o Acquire::https::Timeout=30 -o Acquire::Retries=3 install -y --no-install-recommends \
    openjdk-25-jdk-headless tomcat11-common python3 python3-bcrypt python3-defusedxml \
    mariadb-client libxml2-utils util-linux procps strace ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=cli-builder /tmp/carlos-ctl_*_all.deb /tmp/cli-package/
RUN --mount=type=secret,id=ci_ca,required=true,mode=0444 \
    for attempt in 1 2 3; do \
      apt-get -o Acquire::https::CaInfo=/run/secrets/ci_ca -o Acquire::https::Timeout=30 -o Acquire::Retries=3 -o Acquire::http::No-Cache=true -o Acquire::By-Hash=force -o APT::Update::Error-Mode=any update && break; \
      test "$attempt" -lt 3 || exit 1; \
      rm -rf /var/lib/apt/lists/*; \
      sleep 2; \
    done \
    && apt-get -o Acquire::https::CaInfo=/run/secrets/ci_ca -o Acquire::https::Timeout=30 -o Acquire::Retries=3 install -y --no-install-recommends /tmp/cli-package/*.deb \
    && rm -rf /tmp/cli-package /var/lib/apt/lists/* \
    && groupadd --system carlos \
    && useradd --system --gid carlos --home-dir /var/lib/carlos-emr --create-home --shell /usr/sbin/nologin carlos
WORKDIR /workspace
USER carlos
CMD ["sleep", "infinity"]
