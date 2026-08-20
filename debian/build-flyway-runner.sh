#!/bin/sh
# Compile the Flyway launcher used by `carlosctl db` against the jars the
# CARLOS WAR already ships, so the package needs no separate Flyway CLI.
#
#   debian/build-flyway-runner.sh <carlos.war> <outdir>
set -eu

# Absolute paths: the extraction below runs inside a subshell that has cd'd
# into the staging directory, so a relative WAR path would not resolve there.
war="$(readlink -f "$1")"
out="$(readlink -f "$2")"
javahome="${JAVA_HOME:-/usr}"

work="$out/flyway-runner"
rm -rf "$work"
mkdir -p "$work/libs" "$work/classes"

# Only the jars the launcher compiles against — extracting the whole
# WEB-INF/lib (400 MB) to compile one file would dominate the build.
for jar in flyway-core flyway-mysql mysql-connector-j; do
    "$javahome/bin/jar" --list --file "$war" \
        | sed -n "s|^\(WEB-INF/lib/${jar}-[^/]*\.jar\)$|\1|p" \
        | while read -r entry; do
            ( cd "$work/libs" && "$javahome/bin/jar" xf "$war" "$entry" )
        done
done
found=$(find "$work/libs" -name '*.jar' | wc -l)
if [ "$found" -lt 2 ]; then
    echo "ERROR: expected flyway-core and flyway-mysql inside $war, found $found jar(s)" >&2
    echo "       the WAR no longer ships Flyway; carlosctl db would have no engine." >&2
    exit 1
fi

cp="$(find "$work/libs" -name '*.jar' | tr '\n' ':')"
"$javahome/bin/javac" -nowarn -encoding UTF-8 -source 21 -target 21 \
    -cp "$cp" -d "$work/classes" \
    debian/assets/flyway-runner/src/main/java/io/github/carlos_emr/carlos/deb/FlywayRunner.java
"$javahome/bin/jar" cf "$out/carlos-flyway-runner.jar" -C "$work/classes" .
echo "built $out/carlos-flyway-runner.jar"
