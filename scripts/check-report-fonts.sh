#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
#
# Report font matrix (issue #5046): renders the clinical and billing reports in fresh JVMs
# under different host font setups and checks they print the same text every time.
#
#   host        the machine's own fonts
#   liberation  no DejaVu installed; Liberation Sans is the only host face
#   tall        the only host face is Liberation Sans with a 1.65 em line, like Noto Sans,
#               WenQuanYi Zen Hei or a Thai face. Before the bundled-font policy this host
#               made JasperReports drop 12pt fields from their 16pt and 17pt boxes.
#
# each with a writable and an unwritable fontconfig cache.
#
# Fresh JVMs are required: AWT/fontconfig and Jasper cache font resolution. The JDK also
# keeps its own fontconfig answer in ${user.home}/.java/fonts and reuses it while the host's
# cache directories are unchanged, so every scenario gets its own user.home (passed to the
# surefire fork with -Dtest.jvmArgs). Without that, the later scenarios silently reuse the
# first one's fonts. Each scenario records fc-match and the JDK's own view
# (scripts/fonts/JavaFontProbe.java) and refuses to run if the JDK does not see the host it
# is meant to test.
#
# Extra arguments are passed to Maven. REPORT_FONT_TESTS overrides the test classes.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v fc-match >/dev/null || { echo "Install fontconfig to run the report font matrix." >&2; exit 1; }
alternate_font=$(fc-match -f '%{file}' 'Liberation Sans')
alternate_family=$(fc-match -f '%{family}' 'Liberation Sans')
if [[ "$alternate_family" != 'Liberation Sans' || "$alternate_font" != *.ttf ]]; then
    echo "Install fonts-liberation to test a default font without OS DejaVu." >&2
    exit 1
fi

tests=${REPORT_FONT_TESTS:-Rourke2020PdfReportIntegrationTest,PatientEndYearStatementIntegrationTest,JasperBundledFontRenderingUnitTest,JasperTemplateFontPolicyUnitTest,ClinicalFormJasperRenderingUnitTest,PdfFontsUnitTest,ConsultationPdfUnicodeUnitTest}

font_work=$(mktemp -d)
trap 'rm -rf "$font_work"' EXIT
mkdir -p "$font_work/liberation-fonts" "$font_work/tall-fonts"
cp "$alternate_font" "$font_work/liberation-fonts/"
java scripts/fonts/TallLineFont.java "$alternate_font" "$font_work/tall-fonts/LiberationSans-TallLine.ttf"

# The CI artifact step uploads this path; the name predates the matrix covering more than Rourke.
matrix_output="$PWD/target/rourke-font-matrix"
mkdir -p "$matrix_output"
for selection in host liberation tall; do
    for cache in writable unwritable; do
        scenario="$selection-$cache"
        output="$matrix_output/$scenario"
        mkdir -p "$output"
        cache_dir="$font_work/$scenario-cache"
        java_home_dir="$font_work/$scenario-home"
        mkdir -p "$java_home_dir"
        xdg_cache="$cache_dir"
        if [[ "$cache" == unwritable ]]; then
            # /proc cannot acquire arbitrary cache directories, even in a root CI container.
            cache_dir=/proc/carlos-report-font-cache
            xdg_cache="$cache_dir"
        else
            mkdir -p "$cache_dir"
        fi
        config="$font_work/$scenario.conf"
        {
            echo '<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd"><fontconfig>'
            if [[ "$selection" == host ]]; then
                echo '<include ignore_missing="no">/etc/fonts/fonts.conf</include>'
            else
                printf '<dir>%s</dir>\n' "$font_work/$selection-fonts"
            fi
            printf '<cachedir>%s</cachedir>\n</fontconfig>\n' "$cache_dir"
        } > "$config"
        FONTCONFIG_FILE="$config" XDG_CACHE_HOME="$xdg_cache" fc-match SansSerif > "$output/fc-match.log" 2>&1
        FONTCONFIG_FILE="$config" XDG_CACHE_HOME="$xdg_cache" fc-list : family > "$output/fc-list.log" 2>&1
        FONTCONFIG_FILE="$config" XDG_CACHE_HOME="$xdg_cache" \
            java -Djava.awt.headless=true -Duser.home="$java_home_dir" scripts/fonts/JavaFontProbe.java \
            > "$output/java-fonts.log" 2>&1
        java_line=$(sed -n 's/^sansserif.line12=//p' "$output/java-fonts.log")
        java_families=$(sed -n 's/^families=//p' "$output/java-fonts.log")
        if [[ "$selection" != host ]] && grep -q 'DejaVu' "$output/fc-list.log"; then
            echo "DejaVu unexpectedly visible to fontconfig in the $scenario scenario." >&2
            exit 1
        fi
        if [[ "$selection" != host && "$java_families" == *DejaVu* ]]; then
            echo "The JDK still sees DejaVu in the $scenario scenario; its font cache was not isolated." >&2
            exit 1
        fi
        if [[ "$selection" == tall ]] && ! awk -v line="$java_line" 'BEGIN { exit !(line > 17) }'; then
            echo "The JDK's SansSerif line is ${java_line}pt in $scenario; the tall host face was not used." >&2
            exit 1
        fi
        echo "Report font matrix: $scenario ($(head -1 "$output/fc-match.log"); JDK SansSerif 12pt line ${java_line}pt)"
        FONTCONFIG_FILE="$config" XDG_CACHE_HOME="$xdg_cache" \
            mvn -B -ntp "$@" -Dtest="$tests" -Dtest.forkCount=1 \
                -Dtest.jvmArgs="-Duser.home=$java_home_dir" \
                -Drourke.pdf.output="$output" -Dreport.font.output="$output" test > "$output/maven.log" 2>&1 || {
                    tail -60 "$output/maven.log" >&2
                    exit 1
                }
        tail -8 "$output/maven.log"
    done
done
