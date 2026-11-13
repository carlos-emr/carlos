#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
# Build a JDK-only root preflight: never put application jars on its classpath.
set -eu
out="$(readlink -f "$1")"
javahome="${JAVA_HOME:-/usr}"
rm -rf "$out/configuration-preflight"
mkdir -p "$out/configuration-preflight"
"$javahome/bin/javac" --release 25 -encoding UTF-8 -d "$out/configuration-preflight" \
    src/main/java/io/github/carlos_emr/carlos/config/PackagedConfigurationPreflight.java
"$javahome/bin/jar" --create --file "$out/carlos-configuration-preflight.jar" \
    --main-class io.github.carlos_emr.carlos.config.PackagedConfigurationPreflight \
    -C "$out/configuration-preflight" .
