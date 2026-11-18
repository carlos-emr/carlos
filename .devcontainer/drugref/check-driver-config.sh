#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
probe_dir=$(mktemp -d)
trap 'rm -rf "$probe_dir"' EXIT
cd "$probe_dir"
jar xf /usr/local/tomcat/webapps/drugref2.war
java --class-path 'WEB-INF/classes:WEB-INF/lib/*' \
  /driver-check/io/github/carlos_emr/carlos/devcontainer/DriverConfigurationProbe.java /drugref2.properties \
  WEB-INF/classes/spring_config.xml
