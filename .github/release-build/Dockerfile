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
# Lives under .github/ with the other CI-owned configuration (pmd/, spotbugs/,
# codeql/); the top-level release/ directory is gitignored.
#
# KEEP THE BASE DIGEST IN SYNC with .devcontainer/development/Dockerfile so the
# release and development builders declare the same JDK base image.
#
# License:
# This file is part of the CARLOS EMR project and is subject to the licensing
# terms outlined in the repository's LICENSE file.
FROM tomcat:11.0.27-jdk25-temurin-noble@sha256:317b75f81d274ed68fd42417b29481915af7949f9e550879314afb3994152cce

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8

# Maven and git come from the base image's Ubuntu archive, unpinned, exactly
# as they do in the development container that verifies every PR. Pinning
# apt versions here would break the build the moment the archive supersedes
# them (Ubuntu keeps no old versions). The dependency lock and pom constrain
# artifacts and plugin versions; unpinned archive tooling still means builds
# are not guaranteed to produce identical WAR bytes across time.
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
