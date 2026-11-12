#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
# SPDX-License-Identifier: GPL-2.0-or-later
#
# Compile the packaged Edit Appointment JSP with the deployment's Jasper,
# including the generated servlet. Translation alone misses Java errors such
# as the non-final lambda capture reported in #5179. No database is needed.
# Usage: CATALINA_HOME=/usr/local/tomcat bash scripts/compile-edit-appointment-jsp.sh EXPLODED_WEBAPP
set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 EXPLODED_WEBAPP" >&2
    exit 2
fi
webapp=$(cd "$1" && pwd)
tomcat=${CATALINA_HOME:?Set CATALINA_HOME to the deployment Tomcat distribution}
jsp=WEB-INF/jsp/appointment/editappointment.jsp
for required in "$webapp/$jsp" "$webapp/WEB-INF/classes" "$webapp/WEB-INF/lib" "$tomcat/lib/jasper.jar"; do
    if [ ! -e "$required" ]; then
        echo "Missing JSP compilation input: $required" >&2
        exit 1
    fi
done

output=$(mktemp -d)
trap 'rm -rf -- "$output"' EXIT
java_command=java
if [ -n "${JAVA_HOME:-}" ]; then
    java_command=$JAVA_HOME/bin/java
fi
# Use Jasper's defaults, as the runtime does. The application JDK can exceed
# the source/target levels this distribution's JDTCompiler accepts.
"$java_command" -cp "$tomcat/lib/*:$tomcat/bin/*:$webapp/WEB-INF/lib/*:$webapp/WEB-INF/classes" \
    org.apache.jasper.JspC -uriroot "$webapp" -d "$output" \
    -compile -failFast -die1 "$jsp"

# Assert bytecode exists as well as checking Jasper's exit status. Keep generated
# classes outside the webapp so this smoke check cannot change the shipped WAR.
test -s "$output/org/apache/jsp/WEB_002dINF/jsp/appointment/editappointment_jsp.class"
echo "PASS Edit Appointment JSP and generated servlet compile with deployment Jasper"
