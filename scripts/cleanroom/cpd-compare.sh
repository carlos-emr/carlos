#!/usr/bin/env bash
#
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
#
# Compares each original file with its clean-room replacement using PMD's copy-paste
# detector (CPD), with identifiers and literals ignored so renamed copies still match.
# Used by /cleanroom-spec:cleanroom-audit. One piece of evidence, never proof on its own.
#
# Usage: bash scripts/cleanroom/cpd-compare.sh <source-commit> <export-dir> <target-path>...
#   <source-commit> commit the export was made from (CLEANROOM-MANIFEST.txt)
#   <export-dir>    the implementer's export holding the replacement files
#   <target-path>   repo-relative path of each replaced file (.java or .jsp)
# Env: CPD_MIN_TOKENS (default 25) is the shortest token run reported as a match.

set -euo pipefail

if [ "$#" -lt 3 ]; then
    echo "usage: $0 <source-commit> <export-dir> <target-path>..." >&2
    exit 2
fi
source_commit=$1
export_dir=$(realpath "$2")
shift 2
repo=$(git rev-parse --show-toplevel)
min_tokens=${CPD_MIN_TOKENS:-25}
# The plugin version the main build already uses, so no new download is needed.
pmd_plugin=$(sed -n '/<artifactId>maven-pmd-plugin<\/artifactId>/{n;s/.*<version>\(.*\)<\/version>.*/\1/p;q}' "$repo/pom.xml")

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

for target in "$@"; do
    case "$target" in
        *.java) lang=java ;;
        *.jsp|*.jspf) lang=jsp ;;
        *) echo "skip (unsupported type): $target"; continue ;;
    esac
    name=$(basename "$target")
    run="$work/$(echo "$target" | tr '/' '_')"
    mkdir -p "$run/src/old" "$run/src/new"
    git -C "$repo" show "$source_commit:$target" > "$run/src/old/$name"
    if [ ! -f "$export_dir/$target" ]; then
        echo "MISSING replacement: $export_dir/$target"
        continue
    fi
    cp "$export_dir/$target" "$run/src/new/$name"
    cat > "$run/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>local.cleanroom</groupId><artifactId>cpd-compare</artifactId><version>1</version>
  <properties><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
  <build>
    <sourceDirectory>src</sourceDirectory>
    <plugins><plugin>
      <groupId>org.apache.maven.plugins</groupId><artifactId>maven-pmd-plugin</artifactId>
      <version>$pmd_plugin</version>
      <configuration>
        <language>$lang</language>
        <includes><include>**/*.$( [ "$lang" = jsp ] && echo 'jsp*' || echo java )</include></includes>
        <minimumTokens>$min_tokens</minimumTokens>
        <ignoreIdentifiers>true</ignoreIdentifiers>
        <ignoreLiterals>true</ignoreLiterals>
        <format>xml</format>
      </configuration>
    </plugin></plugins>
  </build>
</project>
EOF
    (cd "$run" && mvn -q -B pmd:cpd >/dev/null 2>"$run/mvn.err") || {
        echo "CPD failed for $target:"; tail -20 "$run/mvn.err"; exit 1;
    }
    python3 -I - "$run/target/cpd.xml" "$target" <<'PY'
import sys
import xml.etree.ElementTree as ET

report, target = sys.argv[1], sys.argv[2]
ns = {"c": "https://pmd-code.org/schema/cpd-report"}
root = ET.parse(report).getroot()
totals = {f.get("path"): int(f.get("totalNumberOfTokens")) for f in root.findall("c:file", ns)}
new_total = next((n for p, n in totals.items() if "/src/new/" in p), 0)
covered = set()
matches = []
for dup in root.findall("c:duplication", ns):
    files = dup.findall("c:file", ns)
    new_spans = [f for f in files if "/src/new/" in f.get("path")]
    if not new_spans or not any("/src/old/" in f.get("path") for f in files):
        continue  # repetition inside one file, not a match between old and new
    for f in new_spans:
        covered.update(range(int(f.get("begintoken")), int(f.get("endtoken")) + 1))
        matches.append((int(dup.get("tokens")), int(f.get("line")), int(f.get("endline"))))
share = 100.0 * len(covered) / new_total if new_total else 0.0
print(f"{target}: {len(matches)} matching run(s); {share:.0f}% of the replacement's {new_total} tokens")
for tokens, line, end in sorted(matches, reverse=True):
    print(f"  {tokens} tokens at replacement lines {line}-{end}")
PY
done
