#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
# Fresh JVMs are required: AWT/fontconfig and Jasper cache font resolution.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v fc-match >/dev/null || { echo "Install fontconfig to run the Rourke font matrix." >&2; exit 1; }
alternate_font=$(fc-match -f '%{file}' 'Liberation Sans')
alternate_family=$(fc-match -f '%{family}' 'Liberation Sans')
if [[ "$alternate_family" != 'Liberation Sans' || "$alternate_font" != *.ttf ]]; then
    echo "Install fonts-liberation to test a default font without OS DejaVu." >&2
    exit 1
fi

font_work=$(mktemp -d)
trap 'rm -rf "$font_work"' EXIT
matrix_output="$PWD/target/rourke-font-matrix"
mkdir -p "$matrix_output"
for selection in host liberation; do
    for cache in writable unwritable; do
        scenario="$selection-$cache"
        output="$matrix_output/$scenario"
        mkdir -p "$output"
        cache_dir="$font_work/$scenario-cache"
        xdg_cache="$cache_dir"
        if [[ "$cache" == unwritable ]]; then
            # /proc cannot acquire arbitrary cache directories, even in a root CI container.
            cache_dir=/proc/carlos-rourke-font-cache
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
                printf '<dir>%s</dir>\n' "$(dirname "$alternate_font")"
            fi
            printf '<cachedir>%s</cachedir>\n</fontconfig>\n' "$cache_dir"
        } > "$config"
        FONTCONFIG_FILE="$config" XDG_CACHE_HOME="$xdg_cache" fc-match SansSerif > "$output/fc-match.log" 2>&1
        FONTCONFIG_FILE="$config" XDG_CACHE_HOME="$xdg_cache" fc-list : family > "$output/fc-list.log" 2>&1
        if [[ "$selection" == liberation ]] && grep -q 'DejaVu' "$output/fc-list.log"; then
            echo "DejaVu unexpectedly visible in the no-OS-DejaVu scenario." >&2
            exit 1
        fi
        echo "Rourke font matrix: $scenario ($(head -1 "$output/fc-match.log"))"
        FONTCONFIG_FILE="$config" XDG_CACHE_HOME="$xdg_cache" \
            mvn -B -ntp "$@" -Dtest=Rourke2020PdfReportIntegrationTest -Dtest.forkCount=1 \
                -Drourke.pdf.output="$output" test > "$output/maven.log" 2>&1 || {
                    tail -60 "$output/maven.log" >&2
                    exit 1
                }
        tail -8 "$output/maven.log"
    done
done
