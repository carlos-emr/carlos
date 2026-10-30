#!/usr/bin/env bash
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
# One-off comparison of the HTML-to-PDF render service (Flying Saucer) with wkhtmltopdf, for PR 1 of the
# HTML-to-PDF consolidation. Run from anywhere; it works from the repository root.
#
#   bash scripts/htmlpdf/compare-with-wkhtmltopdf.sh                    # compare every fixture
#   bash scripts/htmlpdf/compare-with-wkhtmltopdf.sh size-limit         # layout time of growing tables
#   bash scripts/htmlpdf/compare-with-wkhtmltopdf.sh size-limit 500,5000,50000
#
# Output goes to target/htmlpdf-compare/ (OUT_DIR overrides it): both PDFs of each fixture, a PNG with the
# first two pages side by side, comparison.csv and summary.txt (or size-limit.csv). Attach the PNGs and the
# CSV to the pull request; they are not kept in the repository.
#
# wkhtmltopdf runs the way the application ran it: the vendored JNA library inside the JVM, LANG=C.UTF-8,
# and a fontconfig file that lists only fonts-dejavu-core, so the host's fonts cannot decide what prints.
# It needs the native library to load (libwkhtmltox, extracted from the jar under /tmp/io.woo.htmltopdf).
# This script is deleted together with wkhtmltopdf in the follow-up change.
#
# Environment: SKIP_BUILD=1 reuses target/classes, target/test-classes and target/htmlpdf-cp.txt;
# DEJAVU_DIR (default /usr/share/fonts/truetype/dejavu); JAVA (default java); JAVA_HEAP (default -Xmx2g).
set -euo pipefail

cd "$(dirname "$0")/../.."
mode="${1:-compare}"
out="${OUT_DIR:-target/htmlpdf-compare}"
dejavu="${DEJAVU_DIR:-/usr/share/fonts/truetype/dejavu}"

if [ ! -d "$dejavu" ]; then
  echo "DejaVu fonts not found in $dejavu (package fonts-dejavu-core); set DEJAVU_DIR" >&2
  exit 1
fi

mkdir -p "$out"
# Absolute from here on: OUT_DIR may be relative to the repository root or absolute, and the font
# configuration below must name the same directory either way.
out="$(cd "$out" && pwd)"
cat > "$out/fonts.conf" <<CONF
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <dir>$dejavu</dir>
  <cachedir>$out/fontconfig-cache</cachedir>
</fontconfig>
CONF

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  mvn -q test-compile
  mvn -q dependency:build-classpath -Dmdep.outputFile=target/htmlpdf-cp.txt
fi

LANG=C.UTF-8 LC_ALL=C.UTF-8 FONTCONFIG_FILE="$out/fonts.conf" \
  "${JAVA:-java}" "${JAVA_HEAP:--Xmx2g}" -Djna.encoding=UTF-8 -Djava.awt.headless=true \
  -cp "target/test-classes:target/classes:$(cat target/htmlpdf-cp.txt)" \
  io.github.carlos_emr.carlos.documentManager.htmlpdf.tools.HtmlPdfComparisonMain "$mode" "$out" "${@:2}"
