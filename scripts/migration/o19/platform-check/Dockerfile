# Ubuntu 26.04 — the platform carlos-emr targets, and the reason this
# harness exists: the development container runs Python 3.11 while the
# package ships against 3.14. That gap hid a real defect. The archive
# CSV export takes `csv.QUOTE_NOTNULL` when the interpreter has it
# (3.12+) so a SQL NULL stays distinguishable from a stored empty
# string; on 3.11 that constant does not exist, so the tests only ever
# exercised the fallback and the branch that actually ships to clinics
# had never run anywhere. It failed the moment it met 26.04.
FROM ubuntu:26.04

# Only https_proxy, matching the host: an agent proxy that answers 405
# to non-CONNECT GETs breaks plain-HTTP archive traffic, which should
# go direct. Harmless when no proxy is set.
ARG https_proxy=
ENV https_proxy=$https_proxy
RUN apt-get -qq update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends \
      python3 dash coreutils findutils tar gzip ca-certificates \
 && rm -rf /var/lib/apt/lists/*
ENV https_proxy=
WORKDIR /work
