#!/bin/sh
# Compile the Flyway launcher used by `carlos-ctl db-*` against the jars the
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
for jar in flyway-core flyway-mysql mariadb-java-client; do
    "$javahome/bin/jar" --list --file "$war" \
        | sed -n "s|^\(WEB-INF/lib/${jar}-[^/]*\.jar\)$|\1|p" \
        | while read -r entry; do
            ( cd "$work/libs" && "$javahome/bin/jar" xf "$war" "$entry" )
        done
done
# Each artifact checked BY NAME: a bare count passed when flyway-core and the
# JDBC driver were present but flyway-mysql was missing — and that absence
# only surfaces at runtime, as `carlos-ctl db-*` failing to load MariaDB support.
for jar in flyway-core flyway-mysql mariadb-java-client; do
    if ! find "$work/libs" -name "${jar}-*.jar" | grep -q .; then
        echo "ERROR: ${jar} is missing from $war (WEB-INF/lib)" >&2
        echo "       carlos-ctl db-* needs all of: flyway-core, flyway-mysql, mariadb-java-client." >&2
        exit 1
    fi
done
# The compatibility adapter includes version-specific metadata access. A
# prebuilt WAR must use the same tested connector as the pinned Maven build.
set -- "$work"/libs/WEB-INF/lib/mariadb-java-client-*.jar
if [ "$#" -ne 1 ] || [ "$(basename "$1")" != mariadb-java-client-3.5.11.jar ]; then
    echo "ERROR: $war must ship exactly mariadb-java-client-3.5.11.jar" >&2
    exit 1
fi

# Older carlos-ctl installations still pass jdbc:mysql URLs and Connector/J
# option names. Use the WAR's exact compatibility translation, including its
# TLS policy, instead of independently guessing at those settings here.
for name in MariaDbJdbcCompatibility MariaDbCompatibilityDriver MariaDbPreferredTlsPlugin; do
    compat="io/github/carlos_emr/carlos/utility/$name"
    "$javahome/bin/jar" --list --file "$war" \
        | awk -v prefix="WEB-INF/classes/$compat" \
            'index($0, prefix) == 1 && substr($0, length(prefix) + 1) ~ /^([$][^/]+)?[.]class$/ { print }' \
        | while read -r entry; do
            ( cd "$work/libs" && "$javahome/bin/jar" xf "$war" "$entry" )
        done
    if [ ! -f "$work/libs/WEB-INF/classes/${compat}.class" ]; then
        echo "ERROR: $name is missing from $war" >&2
        exit 1
    fi
done
tls_service=WEB-INF/classes/META-INF/services/org.mariadb.jdbc.plugin.TlsSocketPlugin
( cd "$work/libs" && "$javahome/bin/jar" xf "$war" "$tls_service" )
if [ ! -s "$work/libs/$tls_service" ]; then
    echo "ERROR: MariaDB TLS compatibility service registration is missing from $war" >&2
    exit 1
fi
cp -R "$work/libs/WEB-INF/classes/." "$work/classes/"

cp="$work/classes:$(find "$work/libs" -name '*.jar' | tr '\n' ':')"
"$javahome/bin/javac" -nowarn -encoding UTF-8 --release 25 \
    -cp "$cp" -d "$work/classes" \
    debian/assets/flyway-runner/src/main/java/io/github/carlos_emr/carlos/deb/FlywayRunner.java
# Test the warning/error boundary against the exact Flyway API in this WAR.
# Keep test classes out of the installed launcher jar.
mkdir -p "$work/test-classes"
"$javahome/bin/javac" -nowarn -encoding UTF-8 --release 25 \
    -cp "$work/classes:$cp" -d "$work/test-classes" \
    debian/assets/flyway-runner/src/test/java/io/github/carlos_emr/carlos/deb/FlywayRunnerNotesTest.java \
    debian/assets/flyway-runner/src/test/java/io/github/carlos_emr/carlos/deb/FlywayRunnerJdbcTest.java
"$javahome/bin/java" -cp "$work/test-classes:$work/classes:$cp" \
    io.github.carlos_emr.carlos.deb.FlywayRunnerNotesTest
"$javahome/bin/java" -cp "$work/test-classes:$work/classes:$cp" \
    io.github.carlos_emr.carlos.deb.FlywayRunnerJdbcTest
"$javahome/bin/jar" cf "$out/carlos-flyway-runner.jar" -C "$work/classes" .
echo "built $out/carlos-flyway-runner.jar"
