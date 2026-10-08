#!/bin/bash
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
#
# deb-server-log-phi-scan.sh -- fail when the packaged application wrote a
# test patient's data into its server log, at ANY level. Run as root on a deb
# host after (or around) a Playwright suite run.
#
# WHY. scripts/deb-server-log-audit.sh reads only ERROR, FATAL and SEVERE
# events and never prints message text, so patient data written at INFO, WARN
# or DEBUG is invisible to it. Findings 141 (a note's text in the journal) and
# 144 (prescription instructions) were found only by someone reading the
# journal for another reason. This scan looks for the data the suite itself
# plants and fails on any occurrence.
#
# WHAT IT SEARCHES FOR (literal text, never a regular expression):
#   - the harness markers and the throwaway names it creates. Every owned
#     patient, provider, note and document carries "FAKE-PW<hex>", and the
#     throwaway logins "FAKEPW<hex>"; both prefixes are built in, so no run
#     has to be named (--only-given turns them off, for a check that scans
#     just its own fixture);
#   - health numbers, given with --hin (repeatable) or
#     $CARLOS_LOG_PHI_SCAN_HINS. A 10-digit number is also searched as
#     "NNNN NNN NNN" and "NNNN-NNN-NNN". Nothing is hard-coded: a fixture HIN
#     is chosen by the check and handed to this scan by whoever runs both;
#   - further literals, given with --marker (repeatable) or
#     $CARLOS_LOG_PHI_SCAN_MARKERS (a patient name a check types, a note text).
#
# WHERE. The carlos-emr unit's journal at every priority (where the log4j
# output of a deb install goes, together with Tomcat's console output) and
# Tomcat's own catalina.*.log files. Not scanned: the nginx and ModSecurity
# logs (the WAF audit log keeps the bodies of blocked requests by design) and
# Tomcat's access log (it records the request path, not the query string).
#
# PHI. This script is itself a place patient data could leak, so it prints
# neither the needle, nor the line, nor the message. A match is reported as
# the source (journal, catalina), the level, the logger or class that wrote the
# event, the needle CLASS (marker or hin) and two counts: events (log events
# that matched; a stack trace is one event) and lines (matching lines).
# A matching continuation line (an exception message, a stack frame) is
# attributed to the event header above it, so the logger named is the one that
# logged the exception. Needles are never echoed in an error message either.
#
# Usage:
#   scripts/deb-server-log-phi-scan.sh [--since 'YYYY-MM-DD HH:MM:SS'] [--hin NUMBER]...
#                                      [--marker TEXT]... [--only-given] [--unit carlos-emr]
#                                      [--catalina-dir DIR] [--input FILE]
#
#   --since       start of the window (default: $CARLOS_LOG_AUDIT_SINCE, else
#                 the current boot). deb-docker-validation.sh writes
#                 CARLOS_LOG_AUDIT_SINCE into /root/suite-env.sh, so the scan
#                 and the audit read the same window.
#   --hin         a health number to look for (6-24 letters, digits or dashes).
#   --marker      another literal to look for (4-80 printable characters). A
#                 token shorter than six characters is searched literally
#                 everywhere in the window, so choose one no log line holds by
#                 chance (a random five-character string): finding 144 logs a
#                 prescription instruction only while it is shorter than six
#                 characters, and a longer needle cannot be in that line.
#   --only-given  search only the --hin and --marker needles (and their
#                 environment equivalents), not the built-in FAKE-PW and
#                 FAKEPW prefixes. Needs at least one needle.
#   --input       read the journal's lines from FILE instead of the journal
#                 (tests); the Tomcat directory is then read only when
#                 --catalina-dir is given too.
#   --catalina-dir  Tomcat log directory (default /var/log/carlos-emr/tomcat).
#
# Exit status: 0 no match, 1 at least one match, 2 usage error or no log line
# read at all (a window that read nothing proves nothing).
set -euo pipefail

SINCE="${CARLOS_LOG_AUDIT_SINCE:-}"
UNIT=carlos-emr
CATALINA_DIR=/var/log/carlos-emr/tomcat
CATALINA_EXPLICIT=false
INPUT=""
HINS=()
MARKERS=()
ONLY_GIVEN=false

usage() { sed -n '/^# Usage:/,/^# Exit status/p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }
usage_error() { echo "deb-server-log-phi-scan: $1" >&2; usage >&2; exit 2; }

# A needle is checked, never echoed: the error names the option, not the value.
check_hin() {
  [[ "$1" =~ ^[0-9A-Za-z-]{6,24}$ ]] || usage_error "$2 value is not a valid health number (6-24 letters, digits or dashes)"
}
check_marker() {
  # [[:print:]] excludes newline, tab and other control characters, which would break the needle list.
  if ! [[ "$1" =~ ^[[:print:]]{4,80}$ ]]; then
    usage_error "$2 value is too short or not printable text (4-80 printable characters)"
  fi
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --since) [ -n "${2:-}" ] || usage_error "--since needs a value"; SINCE="$2"; shift 2 ;;
    --hin) [ -n "${2:-}" ] || usage_error "--hin needs a value"; check_hin "$2" --hin; HINS+=("$2"); shift 2 ;;
    --marker) [ -n "${2:-}" ] || usage_error "--marker needs a value"; check_marker "$2" --marker; MARKERS+=("$2"); shift 2 ;;
    --only-given) ONLY_GIVEN=true; shift ;;
    --unit) [ -n "${2:-}" ] || usage_error "--unit needs a name"; UNIT="$2"; shift 2 ;;
    --catalina-dir) [ -n "${2:-}" ] || usage_error "--catalina-dir needs a directory"; CATALINA_DIR="$2"; CATALINA_EXPLICIT=true; shift 2 ;;
    --input) [ -n "${2:-}" ] || usage_error "--input needs a file"; INPUT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "deb-server-log-phi-scan: unknown argument" >&2; usage >&2; exit 2 ;;
  esac
done

# Needles from the environment: separated by commas, spaces or newlines.
env_list() { printf '%s' "${1:-}" | tr ',\n' '  '; }
for needle in $(env_list "${CARLOS_LOG_PHI_SCAN_HINS:-}"); do
  check_hin "$needle" '$CARLOS_LOG_PHI_SCAN_HINS'; HINS+=("$needle")
done
for needle in $(env_list "${CARLOS_LOG_PHI_SCAN_MARKERS:-}"); do
  check_marker "$needle" '$CARLOS_LOG_PHI_SCAN_MARKERS'; MARKERS+=("$needle")
done

if [ -n "$SINCE" ] && ! date -d "$SINCE" >/dev/null 2>&1; then
  echo "deb-server-log-phi-scan: --since is not a date" >&2; exit 2
fi
if [ "$ONLY_GIVEN" = true ] && [ "${#HINS[@]}" -eq 0 ] && [ "${#MARKERS[@]}" -eq 0 ]; then
  usage_error "--only-given needs at least one --hin or --marker (or their environment variables)"
fi

work="$(mktemp -d)"
chmod 700 "$work"
trap 'rm -rf "$work"' EXIT

# 1. The needle list: "class<TAB>literal", one per line.
{
  if [ "$ONLY_GIVEN" != true ]; then
    printf 'marker\tFAKE-PW\n'
    printf 'marker\tFAKEPW\n'
  fi
  for needle in "${MARKERS[@]+"${MARKERS[@]}"}"; do printf 'marker\t%s\n' "$needle"; done
  for needle in "${HINS[@]+"${HINS[@]}"}"; do
    printf 'hin\t%s\n' "$needle"
    if [[ "$needle" =~ ^[0-9]{10}$ ]]; then
      printf 'hin\t%s %s %s\n' "${needle:0:4}" "${needle:4:3}" "${needle:7:3}"
      printf 'hin\t%s-%s-%s\n' "${needle:0:4}" "${needle:4:3}" "${needle:7:3}"
    fi
  done
} > "$work/needles.tsv"

# 2. Scan one stream. Emits "S<TAB>source<TAB>lines" and one
#    "H<TAB>source<TAB>level<TAB>logger<TAB>class<TAB>events<TAB>lines" per distinct
#    (level, logger, class). `since_iso` bounds the catalina files only; the
#    journal is bounded by journalctl itself.
scan_stream() {
  local source="$1" since_iso="$2"
  awk -v src="$source" -v since="$since_iso" -v needlefile="$work/needles.tsv" '
    BEGIN {
      split("Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec", m, " ")
      for (i = 1; i <= 12; i++) mon[m[i]] = sprintf("%02d", i)
      while ((getline row < needlefile) > 0) {
        tab = index(row, "\t")
        if (tab == 0) continue
        n++; ncls[n] = substr(row, 1, tab - 1); nlit[n] = substr(row, tab + 1)
      }
      close(needlefile)
      level = "-"; logger = "(no-event-header)"; inwin = (since == "")
    }
    function flush(   c, key) {
      for (c in hit) {
        key = level SUBSEP logger SUBSEP c
        events[key]++; lines[key] += hit[c]
      }
      delete hit
    }
    # Returns 1 and sets level, logger and iso when the line starts a log event.
    function header(line,   f, nf, rest, close_at, name, dot) {
      # log4j packaged layout: 2026-10-08 18:40:36,876 WARN  utility.ErrorPageLogger (File.java:125) - message
      if (line ~ /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9:,.]+ +[A-Za-z]+ +[^ ]+/) {
        nf = split(line, f, " ")
        nlevel = f[3]; nlogger = f[4]; niso = f[1] " " substr(f[2], 1, 8)
        return 1
      }
      # Tomcat (JUL) layout: 08-Oct-2026 12:11:03.867 INFO [main] org.apache.Class.method message
      if (line ~ /^[0-9][0-9]-[A-Z][a-z][a-z]-[0-9][0-9][0-9][0-9] [0-9:.]+ [A-Z]+ \[/) {
        nf = split(line, f, " ")
        nlevel = f[3]
        niso = substr(f[1], 8, 4) "-" mon[substr(f[1], 4, 3)] "-" substr(f[1], 1, 2) " " substr(f[2], 1, 8)
        close_at = index(line, "] ")
        if (close_at == 0) { nlogger = "(unparsed-tomcat-event)"; return 1 }
        rest = substr(line, close_at + 2)
        split(rest, f, " ")
        name = f[1]
        dot = 0
        for (i = length(name); i > 0; i--) if (substr(name, i, 1) == ".") { dot = i; break }
        if (dot > 1) name = substr(name, 1, dot - 1)
        nlogger = name
        return 1
      }
      return 0
    }
    {
      if (header($0)) {
        flush()
        level = nlevel; logger = nlogger
        inwin = (since == "" || niso >= since)
      }
      if (!inwin) next
      seen++
      for (k = 1; k <= n; k++) {
        if (index($0, nlit[k]) > 0) hit[ncls[k]]++
      }
    }
    END {
      flush()
      printf "S\t%s\t%d\n", src, seen
      for (key in events) {
        split(key, p, SUBSEP)
        printf "H\t%s\t%s\t%s\t%s\t%d\t%d\n", src, p[1], p[2], p[3], events[key], lines[key]
      }
    }
  '
}

# 3. Collect and scan the window.
since_iso=""
[ -n "$SINCE" ] && since_iso="$(date -d "$SINCE" '+%Y-%m-%d %H:%M:%S')"

: > "$work/result.tsv"
if [ -n "$INPUT" ]; then
  scan_stream journal "" < "$INPUT" >> "$work/result.tsv"
elif [ -n "$SINCE" ]; then
  journalctl -u "$UNIT" --no-pager -o cat --since "$SINCE" | scan_stream journal "" >> "$work/result.tsv"
else
  journalctl -u "$UNIT" --no-pager -o cat -b | scan_stream journal "" >> "$work/result.tsv"
fi

if { [ -z "$INPUT" ] || [ "$CATALINA_EXPLICIT" = true ]; } && [ -d "$CATALINA_DIR" ]; then
  for f in "$CATALINA_DIR"/catalina.*.log; do
    [ -e "$f" ] || continue
    scan_stream catalina "$since_iso" < "$f" >> "$work/result.tsv"
  done
fi

# 4. Report. Several catalina files fold into one source.
journal_lines=$(awk -F'\t' '$1 == "S" && $2 == "journal" { n += $3 } END { print n + 0 }' "$work/result.tsv")
catalina_lines=$(awk -F'\t' '$1 == "S" && $2 == "catalina" { n += $3 } END { print n + 0 }' "$work/result.tsv")
window="${SINCE:-current boot}"

if [ $((journal_lines + catalina_lines)) -eq 0 ]; then
  echo "deb-server-log-phi-scan: read no log line since $window (journal and Tomcat logs unreadable or empty); a scan that read nothing proves nothing" >&2
  exit 2
fi

awk -F'\t' '
  $1 == "H" {
    key = $2 "\t" $3 "\t" $4 "\t" $5
    ev[key] += $6; ln[key] += $7
  }
  END { for (key in ev) { split(key, p, "\t"); printf "%s\t%s\t%s\t%s\t%d\t%d\n", p[1], p[2], p[3], p[4], ev[key], ln[key] } }
' "$work/result.tsv" | sort -t$'\t' -k1,1 -k2,2 -k3,3 -k4,4 > "$work/hits.tsv"

loggers=$(wc -l < "$work/hits.tsv")
if [ "$loggers" -gt 0 ]; then
  while IFS=$'\t' read -r source level logger class events lines; do
    printf 'HIT  %-8s %-8s %s  %s  events=%d lines=%d\n' "$source" "$level" "$logger" "$class" "$events" "$lines"
  done < "$work/hits.tsv"
  total_events=$(awk -F'\t' '{ n += $5 } END { print n + 0 }' "$work/hits.tsv")
  echo "FAIL log PHI scan: $loggers logger(s) matched, $total_events event(s) (journal $journal_lines line(s), catalina $catalina_lines line(s) since $window)"
  exit 1
fi
echo "PASS log PHI scan: no match (journal $journal_lines line(s), catalina $catalina_lines line(s) since $window)"
