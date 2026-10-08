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
# deb-server-log-audit.sh -- fail when the packaged application logged a
# server-side error that no browser check saw. Run as root on a deb host after
# (or around) a Playwright suite run.
#
# WHY. A page can render, a check can pass, and the server can still have
# thrown: an AJAX fragment that failed behind a catch, a scheduled job, a
# 500 on a request no assertion inspected, an include of a JSP that no longer
# exists. Those reach the journal and nothing else. This audit reads the
# carlos-emr unit's journal (where the application's log4j output goes on a
# deb install) and Tomcat's own catalina log, reduces every ERROR, FATAL and
# SEVERE event to a signature, and fails on any signature the baseline does
# not explain.
#
# PHI. Log messages can carry patient data. A signature is built only from
# the logger, its source location, the first CARLOS frame, the exception CLASS
# names in the event's stack, a request path where the message logs one
# (query string cut off and every all-digit path segment replaced by {n}, so a
# demographic or record number never reaches the output), the error page
# status, and a CSRF rejection's method and reason. Message text is never
# printed unless --show-messages is given, and that is for disposable
# demonstration hosts.
#
# Usage:
#   scripts/deb-server-log-audit.sh [--since 'YYYY-MM-DD HH:MM:SS'] [--baseline FILE]
#                                   [--unit carlos-emr] [--catalina-dir DIR]
#                                   [--input FILE] [--show-messages]
#
#   --since       start of the window (default: $CARLOS_LOG_AUDIT_SINCE, else
#                 the current boot). deb-docker-validation.sh writes
#                 CARLOS_LOG_AUDIT_SINCE into /root/suite-env.sh.
#   --baseline    tab-separated "regex<TAB>reason" lines; a signature matching
#                 a regex is reported as known, not failed. Default
#                 scripts/lib/server-log-baseline.tsv. Every reason must cite
#                 an issue (#NNNN), an app-findings-log.md finding, or a doc.
#   --input       read log lines from FILE instead of the journal (tests).
#   --catalina-dir  Tomcat log directory (default /var/log/carlos-emr/tomcat);
#                 its SEVERE lines are audited too.
#
# Exit status: 0 no unexplained signature, 1 at least one, 2 usage error.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SINCE="${CARLOS_LOG_AUDIT_SINCE:-}"
BASELINE="$REPO_ROOT/scripts/lib/server-log-baseline.tsv"
UNIT=carlos-emr
CATALINA_DIR=/var/log/carlos-emr/tomcat
INPUT=""
SHOW_MESSAGES=false

usage() { sed -n '/^# Usage:/,/^# Exit status/p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }
usage_error() { echo "deb-server-log-audit: $1" >&2; usage >&2; exit 2; }

while [ "$#" -gt 0 ]; do
  case "$1" in
    --since) [ -n "${2:-}" ] || usage_error "--since needs a value"; SINCE="$2"; shift 2 ;;
    --baseline) [ -n "${2:-}" ] || usage_error "--baseline needs a file"; BASELINE="$2"; shift 2 ;;
    --unit) [ -n "${2:-}" ] || usage_error "--unit needs a name"; UNIT="$2"; shift 2 ;;
    --catalina-dir) [ -n "${2:-}" ] || usage_error "--catalina-dir needs a directory"; CATALINA_DIR="$2"; shift 2 ;;
    --input) [ -n "${2:-}" ] || usage_error "--input needs a file"; INPUT="$2"; shift 2 ;;
    --show-messages) SHOW_MESSAGES=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "deb-server-log-audit: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
  esac
done

[ -r "$BASELINE" ] || { echo "deb-server-log-audit: baseline not readable: $BASELINE" >&2; exit 2; }
if [ -n "$SINCE" ] && ! date -d "$SINCE" >/dev/null 2>&1; then
  echo "deb-server-log-audit: --since is not a date: $SINCE" >&2; exit 2
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# 1. Collect the window's lines.
if [ -n "$INPUT" ]; then
  cat "$INPUT" > "$work/app.log"
else
  if [ -n "$SINCE" ]; then
    journalctl -u "$UNIT" --no-pager -o cat --since "$SINCE" > "$work/app.log"
  else
    journalctl -u "$UNIT" --no-pager -o cat -b > "$work/app.log"
  fi
  # Tomcat's own JUL log: "08-Oct-2026 04:31:00.123 SEVERE [thread] logger message".
  # Keep SEVERE lines at or after --since (compared as ISO text).
  if [ -d "$CATALINA_DIR" ]; then
    since_iso=""
    [ -n "$SINCE" ] && since_iso="$(date -d "$SINCE" '+%Y-%m-%d %H:%M:%S')"
    for f in "$CATALINA_DIR"/catalina.*.log; do
      [ -e "$f" ] || continue
      awk -v since="$since_iso" '
        BEGIN { split("Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec", m, " "); for (i = 1; i <= 12; i++) mon[m[i]] = sprintf("%02d", i) }
        $3 == "SEVERE" {
          split($1, d, "-"); split($2, t, ".")
          iso = d[3] "-" mon[d[2]] "-" d[1] " " t[1]
          if (since == "" || iso >= since) { $1 = ""; $2 = ""; sub(/^ +/, ""); print "CATALINA " $0 }
        }' "$f" >> "$work/app.log"
    done
  fi
fi

# 2. Reduce events to signatures. An event starts at a log4j line whose level
#    is ERROR or FATAL, or a CATALINA SEVERE line; the stack lines that follow
#    contribute exception class names until the next timestamped line.
awk -v show="$SHOW_MESSAGES" '
  function flush() {
    if (sig != "") {
      out = sig
      if (classes != "") out = out " :: " classes
      print out "\t" msg
    }
    sig = ""; classes = ""; msg = ""; frame = ""
  }
  function add_class(c) {
    if (index(" " classes " ", " " c " ") == 0) classes = (classes == "" ? c : classes " " c)
  }
  /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9:,.]+ +[A-Z]+ / {
    flush()
    level = $3
    if (level == "ERROR" || level == "FATAL") {
      logger = $4; loc = $5
      sig = level " " logger " " loc
      rest = $0; cut = index(rest, ") - "); if (cut > 0) rest = substr(rest, cut + 4)
      # A request path is an operational identifier, not PHI, once the query
      # string is cut off and numeric segments (/demographics/1234) are masked.
      if (match(rest, /uri=[^,) ?\]]+/)) {
        u = substr(rest, RSTART, RLENGTH)
        while (match(u, /\/[0-9]+(\/|$)/)) u = substr(u, 1, RSTART) "{n}" substr(u, RSTART + RLENGTH - (substr(u, RSTART + RLENGTH - 1, 1) == "/" ? 1 : 0))
        sig = sig " " u
      }
      if (logger ~ /ErrorPageLogger$/ && match(rest, /status=[0-9]+/)) sig = sig " " substr(rest, RSTART, RLENGTH)
      # A CSRF rejection is ERROR by design. CSRFGuard logs no request path,
      # and the CARLOS session login is invisible to it, so every violation
      # reads "user:<anonymous>"; the method and the reason are all that tell
      # one rejection from another.
      if (rest ~ /^CSRF violation/) {
        if (match(rest, /method:[A-Z]+/)) sig = sig " csrf-" substr(rest, RSTART, RLENGTH)
        if (match(rest, /error:[^)]+/)) sig = sig " csrf-" substr(rest, RSTART, RLENGTH)
      }
      if (show == "true") msg = substr(rest, 1, 200)
    }
    next
  }
  /^CATALINA / {
    flush()
    sig = "SEVERE " $4
    if (show == "true") { rest = $0; sub(/^CATALINA +SEVERE +\[[^]]*\] +[^ ]+ +/, "", rest); msg = substr(rest, 1, 200) }
    next
  }
  sig != "" {
    line = $0
    # The first CARLOS frame says where the failure surfaced, so a baseline
    # entry for one deliberate 500 cannot also hide the same exception class
    # thrown anywhere else.
    # Servlet filters wrap every request, so a doFilter frame says nothing
    # about where the failure came from; take the first frame past them.
    if (frame == "" && match(line, /^[ \t]+at io\.github\.carlos_emr\.[a-zA-Z0-9_.$]+\(/)) {
      f = substr(line, RSTART, RLENGTH - 1); sub(/^[ \t]+at /, "", f)
      n = split(f, parts, ".")
      if (parts[n] != "doFilter") {
        frame = parts[n - 1] "." parts[n]
        sig = sig " @" frame
      }
    }
    sub(/^[ \t]*Caused by: /, "", line)
    if (match(line, /^[ \t]*[a-zA-Z_$][a-zA-Z0-9_$]*(\.[a-zA-Z_$][a-zA-Z0-9_$]*)+(Exception|Error|Throwable)[a-zA-Z0-9_$]*(:|$)/)) {
      c = substr(line, RSTART, RLENGTH); sub(/:$/, "", c); gsub(/^[ \t]+/, "", c)
      add_class(c)
    }
  }
  END { flush() }
' "$work/app.log" > "$work/events.tsv"

# 3. Count signatures and split them into known and unexplained.
cut -f1 "$work/events.tsv" | sort | uniq -c | sort -rn > "$work/signatures.txt"
grep -vE '^[[:space:]]*(#|$)' "$BASELINE" > "$work/baseline.tsv" || true
unexplained=0
known=0
while read -r count signature; do
  matched=""
  while IFS=$'\t' read -r regex reason; do
    [ -n "$regex" ] || continue
    if printf '%s\n' "$signature" | grep -Eq -- "$regex"; then matched="$reason"; break; fi
  done < "$work/baseline.tsv"
  if [ -n "$matched" ]; then
    known=$((known + 1))
    printf 'KNOWN   %5d  %s\n          (%s)\n' "$count" "$signature" "$matched"
  else
    unexplained=$((unexplained + 1))
    printf 'UNKNOWN %5d  %s\n' "$count" "$signature"
    if [ "$SHOW_MESSAGES" = true ]; then
      awk -F'\t' -v s="$signature" '$1 == s && $2 != "" { print "          e.g. " $2; exit }' "$work/events.tsv"
    fi
  fi
done < "$work/signatures.txt"

events=$(wc -l < "$work/events.tsv")
window="${SINCE:-current boot}"
if [ "$unexplained" -gt 0 ]; then
  echo "FAIL server log audit: $unexplained unexplained signature(s), $known known, $events event(s) since $window"
  exit 1
fi
echo "PASS server log audit: no unexplained signature ($known known, $events event(s) since $window)"
