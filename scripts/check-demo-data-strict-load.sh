#!/usr/bin/env bash
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
# check-demo-data-strict-load.sh -- prove the demo dataset loads under MariaDB's strict sql_mode.
#
# Issue #3151: the demo snapshot (.devcontainer/db/scripts/development.sql) was exported from a
# database whose column order differs from the Flyway baseline, so its column-less INSERTs landed one
# column off. Only an empty sql_mode (then set in the devcontainer my.cnf) let that load: every
# mismatch was silently coerced (a name in demographic.genderId became 0). This script builds and starts
# the real devcontainer database image (.devcontainer/db/Dockerfile: the shipped my.cnf, the Flyway
# baseline, populate_db.sh and the demo snapshot) and asserts that
#
#   1. populate_db.sh completes under the server's default strict mode;
#   2. the demo patients, the carlosdoc login and consistent gender/preferred-name columns are intact;
#   3. the Debian additive demo artifact (INSERT IGNORE) loads into a Flyway-only schema with no
#      coercion warnings. Strict mode does NOT protect that path: IGNORE downgrades type errors to
#      warnings and stores the coerced value anyway. Duplicate keys (1062) are expected, because the
#      Flyway row wins by design.
#
# --self-test adds a negative control: it appends one coercion-only fault (a text value in an INT
# column) to a copy of the snapshot, then shows that the strict image aborts initialisation (ERROR 1366)
# while an empty sql_mode loads the same fault silently. This is what proves the guard can fail.
#
# Usage: scripts/check-demo-data-strict-load.sh [--self-test] [--keep]
#   --self-test  also run the negative control (about a minute more)
#   --keep       leave the containers running afterwards, for inspection
#
# Needs only a reachable Docker daemon and network access to pull mariadb:11.8.8 (the image the
# devcontainer pins). Every container is throwaway: its root password is the same fixed development
# default populate_db.sh uses, and nothing is published on a host port.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEV_SQL="${REPO_ROOT}/.devcontainer/db/scripts/development.sql"
IMAGE="${CARLOS_DEMO_CHECK_IMAGE:-carlos-demo-strict-check}"
DB_PASSWORD="password"
INIT_TIMEOUT="${INIT_TIMEOUT:-300}"
MIN_PATIENTS="${MIN_PATIENTS:-2900}"

SELF_TEST=0
KEEP=0
usage() { sed -n '/^# Usage:/,/^#   --keep/p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }
for arg in "$@"; do
  case "$arg" in
    --self-test) SELF_TEST=1 ;;
    --keep) KEEP=1 ;;
    -h|--help) usage 0 ;;
    *) echo "ERROR: unknown argument: $arg" >&2; usage 2 ;;
  esac
done

command -v docker >/dev/null 2>&1 || { echo "ERROR: docker is required" >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo "ERROR: the Docker daemon is not reachable" >&2; exit 2; }
[ -f "$DEV_SQL" ] || { echo "ERROR: $DEV_SQL not found" >&2; exit 2; }

WORK="$(mktemp -d)"
CONTAINERS=()
cleanup() {
  if [ "$KEEP" = 0 ]; then
    local c
    for c in ${CONTAINERS[@]+"${CONTAINERS[@]}"}; do
      # -v: the mariadb image declares /var/lib/mysql a VOLUME, so without it every run leaks a
      # multi-GB anonymous volume (the dev my.cnf sizes the redo log at 2G).
      docker rm -f -v "$c" >/dev/null 2>&1 || true
    done
  fi
  rm -rf "$WORK"
}
trap cleanup EXIT

pass=0
fail=0
ok()  { echo "PASS $1"; pass=$((pass + 1)); }
bad() { echo "FAIL $1"; fail=$((fail + 1)); }
section() { echo; echo "== $1"; }

# start_container NAME [SNAPSHOT_OVERRIDE] [mariadbd args...]
# SNAPSHOT_OVERRIDE bind-mounts a different development.sql over the image's copy. It must exist:
# Docker silently creates a DIRECTORY for a missing bind-mount source.
start_container() {
  local name="$1" snapshot="$2"
  shift 2
  CONTAINERS+=("$name")
  local args=(-d --name "$name" -e "MARIADB_ROOT_PASSWORD=${DB_PASSWORD}")
  if [ -n "$snapshot" ]; then
    [ -f "$snapshot" ] || { echo "ERROR: snapshot override $snapshot is not a file" >&2; exit 2; }
    args+=(-v "${snapshot}:/scripts/development.sql:ro")
  fi
  docker run "${args[@]}" "$IMAGE" "$@" >/dev/null
}

# wait_for_init NAME -> 0 ready, 1 the container exited (init failed), 2 timed out.
# "Database initialization complete!" is printed by populate_db.sh while the entrypoint's TEMPORARY
# server is still up; the real server announces "ready for connections" a second time after the
# restart, and only then are queries safe.
wait_for_init() {
  local name="$1" waited=0
  while [ "$waited" -lt "$INIT_TIMEOUT" ]; do
    if [ "$(docker inspect -f '{{.State.Status}}' "$name")" != running ]; then
      return 1
    fi
    if docker logs "$name" 2>&1 | grep -q 'Database initialization complete!' \
        && [ "$(docker logs "$name" 2>&1 | grep -c 'ready for connections')" -ge 2 ]; then
      return 0
    fi
    sleep 3
    waited=$((waited + 3))
  done
  return 2
}

sql() { docker exec -e "MYSQL_PWD=${DB_PASSWORD}" "$1" mariadb -uroot -N -e "$2"; }

# ---------------------------------------------------------------------------------------------
section "Build the devcontainer database image"
docker build -q -t "$IMAGE" -f "${REPO_ROOT}/.devcontainer/db/Dockerfile" "$REPO_ROOT" >/dev/null
ok "image $IMAGE built from .devcontainer/db/Dockerfile"

# ---------------------------------------------------------------------------------------------
section "populate_db.sh under the shipped my.cnf"
MAIN="carlos-demo-strict-$$"
start_container "$MAIN" ""
rc=0
wait_for_init "$MAIN" || rc=$?
if [ "$rc" != 0 ]; then
  bad "populate_db.sh did not complete (wait_for_init=$rc); last server output:"
  docker logs "$MAIN" 2>&1 | grep -E '^ERROR|ERROR [0-9]+' | tail -5 || true
  echo; echo "$pass passed, $fail failed"
  exit 1
fi
ok "populate_db.sh completed with the container's default sql_mode"

mode="$(sql "$MAIN" 'SELECT @@global.sql_mode')"
case ",${mode}," in
  *,STRICT_TRANS_TABLES,*|*,STRICT_ALL_TABLES,*) ok "server sql_mode is strict (${mode})" ;;
  *) bad "server sql_mode is not strict: '${mode}'" ;;
esac

patients="$(sql "$MAIN" 'SELECT COUNT(*) FROM carlos.demographic')"
if [ "$patients" -ge "$MIN_PATIENTS" ]; then
  ok "demo patients loaded (${patients} >= ${MIN_PATIENTS})"
else
  bad "only ${patients} demo patients loaded (expected >= ${MIN_PATIENTS})"
fi

n="$(sql "$MAIN" "SELECT COUNT(*) FROM carlos.security WHERE user_name='carlosdoc'")"
[ "$n" = 1 ] && ok "carlosdoc login row present" || bad "carlosdoc login rows: ${n} (expected 1)"

# A coerced name leaves a numeric-only preferred name behind, and a shifted gender column stops
# matching sex. Neither can be produced by the intended data.
n="$(sql "$MAIN" "SELECT COUNT(*) FROM carlos.demographic WHERE pref_name REGEXP '^[0-9]+\$'")"
[ "$n" = 0 ] && ok "no demographic.pref_name was coerced to a number" || bad "${n} demographic rows have a numeric-only pref_name"
n="$(sql "$MAIN" "SELECT COUNT(*) FROM carlos.demographic WHERE gender <> '' AND gender <> sex")"
[ "$n" = 0 ] && ok "demographic.gender agrees with sex wherever it is set" || bad "${n} demographic rows have gender <> sex"

# ---------------------------------------------------------------------------------------------
section "Debian additive demo artifact (INSERT IGNORE) into a Flyway-only schema"
bash "${SCRIPT_DIR}/build-demo-additive.sh" "$DEV_SQL" on "${WORK}/demo-on.sql" >/dev/null
docker cp "${WORK}/demo-on.sql" "${MAIN}:/tmp/demo-on.sql"
# carlos_test is built from the Flyway baseline only (populate_db.sh never loads the snapshot there),
# which is the state the deb's demo load starts from.
before="$(sql "$MAIN" 'SELECT COUNT(*) FROM carlos_test.demographic')"
[ "$before" = 0 ] || bad "carlos_test is not demo-free (${before} demographic rows), so the additive check is not meaningful"
# --force keeps going past an error so every one is counted; --show-warnings prints warnings after each
# statement. The client's exit status is deliberately ignored: errors are counted from the output.
docker exec -e "MYSQL_PWD=${DB_PASSWORD}" "$MAIN" sh -c \
  'mariadb -uroot --force --show-warnings carlos_test < /tmp/demo-on.sql' \
  >"${WORK}/additive.out" 2>"${WORK}/additive.err" || true
errors="$(grep -c '^ERROR' "${WORK}/additive.err" || true)"
[ "$errors" = 0 ] && ok "the additive artifact loaded with no errors" || bad "the additive artifact raised ${errors} errors"
coercions="$(cat "${WORK}/additive.out" "${WORK}/additive.err" \
  | sed -n 's/^Warning (Code \([0-9]*\)).*/\1/p' | grep -v -x 1062 | sort | uniq -c | tr '\n' ' ' || true)"
if [ -z "${coercions// /}" ]; then
  ok "no coercion warnings (only duplicate-key 1062, where Flyway data wins)"
else
  bad "INSERT IGNORE coerced values; warning counts by code: ${coercions}"
fi
added="$(sql "$MAIN" 'SELECT COUNT(*) FROM carlos_test.demographic')"
[ "$added" -ge "$MIN_PATIENTS" ] && ok "additive load added ${added} demo patients" || bad "additive load added only ${added} patients"

# ---------------------------------------------------------------------------------------------
if [ "$SELF_TEST" = 1 ]; then
  section "Negative control: one coercion-only fault in an otherwise valid snapshot"
  cp "$DEV_SQL" "${WORK}/fault.sql"
  # A text value for an INT column. It is the original bug in miniature, and independent of which
  # rows the snapshot currently holds.
  printf '%s\n' "INSERT INTO demographic (demographic_no,last_name,first_name,genderId,lastUpdateDate) VALUES (999991,'Fault','Injected','NotAnInteger',NOW());" >> "${WORK}/fault.sql"

  STRICT_FAULT="carlos-demo-fault-strict-$$"
  start_container "$STRICT_FAULT" "${WORK}/fault.sql"
  rc=0
  wait_for_init "$STRICT_FAULT" || rc=$?
  if [ "$rc" = 1 ] && docker logs "$STRICT_FAULT" 2>&1 | grep -q 'ERROR 1366'; then
    ok "strict: initialisation aborts on the fault (ERROR 1366, incorrect integer value)"
  else
    bad "strict: the fault did not abort initialisation (wait_for_init=${rc})"
  fi

  PERMISSIVE_FAULT="carlos-demo-fault-permissive-$$"
  start_container "$PERMISSIVE_FAULT" "${WORK}/fault.sql" --sql-mode=
  rc=0
  wait_for_init "$PERMISSIVE_FAULT" || rc=$?
  if [ "$rc" = 0 ]; then
    g="$(sql "$PERMISSIVE_FAULT" 'SELECT genderId FROM carlos.demographic WHERE demographic_no = 999991')"
    [ "$g" = 0 ] && ok "control: an empty sql_mode loads the same fault silently, as genderId = 0 (issue #3151)" \
                 || bad "control: unexpected genderId '${g}' under an empty sql_mode"
  else
    bad "control: the empty-sql_mode container did not initialise (wait_for_init=${rc})"
  fi
fi

echo
echo "${pass} passed, ${fail} failed"
[ "$fail" = 0 ]
