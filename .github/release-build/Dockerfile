# Minimal environment for building the CARLOS release WAR and CycloneDX SBOM.
#
# publish-release.yml builds this image from the TAGGED source and runs the
# release `mvn package` inside it. It used to build the full development
# container (.devcontainer/development) for that: the same JDK, but also
# Node, Playwright browsers, Go tooling and several AI CLIs fetched from the
# network at build time, none of which the WAR build uses, at ~5 minutes per
# release. This image keeps the two properties the release process relies on
#   * the toolchain comes from the tagged tree (this file), and
#   * the JDK is the digest-pinned base image below --
# and nothing else. Maven and git come from the base image's Ubuntu archive,
# exactly as they do in the development container.
#
# KEEP THE BASE DIGEST IN SYNC with .devcontainer/development/Dockerfile so the
# release WAR is compiled by the same JDK build that CI verified it with.
#
# License:
# This file is part of the CARLOS EMR project and is subject to the licensing
# terms outlined in the repository's LICENSE file.
FROM tomcat:11.0.24-jdk21-temurin@sha256:543dd74d992458d558875de8493399a718270b361b16e4ea6750371a13dda077

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8

RUN apt-get update \
    && apt-get install -y --no-install-recommends maven git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
