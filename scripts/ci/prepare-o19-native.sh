#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
# Prepare real ON/BC references for the generator suite's required native cases.
set -euo pipefail
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
: "${GITHUB_ENV:?GITHUB_ENV is required}"
if docker container inspect carlos-o19-ci >/dev/null 2>&1; then
  echo 'Refusing an existing carlos-o19-ci container' >&2
  exit 1
fi
# A rerun gets a fresh private workspace; never reuse or delete an old socket.
work=$(mktemp -d "$RUNNER_TEMP/carlos-o19-native.XXXXXXXX")
mkdir "$work/socket"
version=$(bash scripts/ci/download-flyway-cli.sh "$work/flyway.tgz")
tar xzf "$work/flyway.tgz" -C "$work"
docker run -d --name carlos-o19-ci -p 127.0.0.1:13308:3306 \
  -e MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=1 \
  --mount "type=bind,src=$work/socket,dst=/run/mysqld" \
  mariadb:11.8.8 --innodb-page-size=32768 --innodb-strict-mode=0 --sql-mode=
ready=false
for ((attempt=0; attempt<60; attempt++)); do
  if docker exec carlos-o19-ci mariadb --protocol=TCP -h127.0.0.1 -uroot -e 'SELECT 1' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
[ "$ready" = true ] || { echo 'MariaDB readiness timed out' >&2; exit 1; }
[ "$(docker exec carlos-o19-ci mariadb -N -uroot -e 'SELECT @@innodb_page_size')" = 32768 ]
# The image changes this directory to mysql-owned 0700. Let the unprivileged
# CI runner traverse it; the job-owned parent remains private (0700).
docker exec carlos-o19-ci chmod 0711 /run/mysqld
for province in on bc; do
  database="carlos_o19_reference_$province"
  docker exec carlos-o19-ci mariadb -uroot -e "CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
  "$work/flyway-$version/flyway" -url="jdbc:mariadb://127.0.0.1:13308/$database" -user=root -password= \
    -initSql="SET SESSION sql_mode='', innodb_strict_mode=0" \
    -locations="filesystem:database/mysql/migration/common,filesystem:database/mysql/migration/$province" migrate
done
# These are synthetic, job-owned databases and a local socket, never clinic credentials.
{
  echo "CARLOS_TEST_MARIADB_SOCKET=$work/socket/mysqld.sock"
  echo 'CARLOS_TEST_O19_REFERENCE_DB=carlos_o19_reference_on'
  echo 'CARLOS_TEST_O19_REFERENCE_BC_DB=carlos_o19_reference_bc'
} >> "$GITHUB_ENV"
