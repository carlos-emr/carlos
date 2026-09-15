#!/bin/sh
# Stamp the Debian build identity into a prebuilt CARLOS WAR.
#
# When debian/rules builds the WAR itself it exports JOB_NAME=carlos-emr-deb and
# BUILD_NUMBER=<changelog version>, and Maven writes them into
# WEB-INF/classes/carlos-build.properties (build.job / build.number), which is
# what BuildInfo renders as "<version> (carlos-emr-deb <deb version>)" on the
# About page, in REST headers and the HL7 SFT segment. A WAR handed in through
# CARLOS_WAR skipped that step and shipped with whatever identity it was built
# under -- usually none -- so a package built that way could not be told apart
# from a bare source build. This rewrites the two keys in place; every other
# key (build.version, build.date) is left as the WAR's own build wrote it.
#
# Usage: stamp-war-build-identity.sh <war> <job-name> <build-number>
set -eu
WAR="${1:?usage: stamp-war-build-identity.sh <war> <job-name> <build-number>}"
JOB="${2:?usage: stamp-war-build-identity.sh <war> <job-name> <build-number>}"
NUMBER="${3:?usage: stamp-war-build-identity.sh <war> <job-name> <build-number>}"
PROPS="WEB-INF/classes/carlos-build.properties"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/WEB-INF/classes"
# A damaged or non-zip WAR must fail loudly; only a *missing* properties entry is
# recoverable, and it is recovered by creating the entry rather than skipping the
# stamp -- silently leaving a prebuilt WAR unidentified is the defect this script
# exists to close.
unzip -tqq "$WAR" || { echo "stamp-war-build-identity: $WAR is not a readable archive" >&2; exit 1; }
if unzip -Z1 "$WAR" | grep -Fxq "$PROPS"; then
  unzip -p "$WAR" "$PROPS" > "$TMP/$PROPS"
else
  echo "stamp-war-build-identity: $WAR carries no $PROPS; creating it" >&2
  : > "$TMP/$PROPS"
fi
# A key that is absent (an older WAR) is appended so the stamp still lands.
for pair in "build.job=$JOB" "build.number=$NUMBER"; do
  key="${pair%%=*}"
  if grep -q "^${key}=" "$TMP/$PROPS"; then
    sed -i "s|^${key}=.*|${pair}|" "$TMP/$PROPS"
  else
    printf '%s\n' "$pair" >> "$TMP/$PROPS"
  fi
done
# jar (openjdk-21-jdk-headless, already a build dependency) replaces the entry in place.
jar uf "$WAR" -C "$TMP" "$PROPS"
echo "stamp-war-build-identity: stamped $JOB $NUMBER into $WAR"
