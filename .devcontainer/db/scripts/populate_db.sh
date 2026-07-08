#!/usr/bin/env sh
set -e
echo 'Setting up all databases...'

MIG=/database/mysql/migration
# Use MYSQL_ROOT_PASSWORD environment variable, fallback to 'password' for development
DB_PASSWORD="${MYSQL_ROOT_PASSWORD:-password}"
# MariaDB 11.x dropped the mysql* client symlinks (mysql/mysqladmin/mysqldump); use mariadb.
SQL="mariadb -u root -p${DB_PASSWORD}"

# Build oscar + oscar_test from the Flyway migration set — the SAME files production applies via
# `carlos-ctl db migrate` (common + Ontario locations): a complete, dead-pruned schema + reference
# data. Loaded here with the mariadb CLI (not the Flyway CLI) because the MariaDB initdb temp server
# is socket-only and Flyway needs TCP; dev databases are disposable, so a flyway_schema_history is
# not required. Keep this list of locations in sync with database/mysql/migration/.
for DB in oscar oscar_test; do
  echo "Creating ${DB} from the Flyway baseline (common + on)..."
  $SQL -e "CREATE DATABASE IF NOT EXISTS ${DB};"
  {
    echo "SET FOREIGN_KEY_CHECKS=0;"
    cat "${MIG}/common/V1__baseline_schema.sql" \
        "${MIG}/on/V1.0.1__on_schema.sql" \
        "${MIG}/on/V1.0.2__on_data.sql"
    echo "SET FOREIGN_KEY_CHECKS=1;"
  } | $SQL "${DB}"
done

# drugref2 is a separate database (not part of the oscar schema).
echo 'Creating drugref2 database...'
$SQL -e "CREATE DATABASE IF NOT EXISTS drugref2;"
$SQL drugref2 < /database/mysql/development-drugref.sql
echo 'Applying drugref2 schema patches...'
$SQL drugref2 < /database/mysql/drugref/2026-04-19-drugref-tc-atc-f.sql

# --- Development-only demo data (oscar only; never applied to a production database) ---
# development.sql is a full demo snapshot (truncate+reload) filtered to the live schema. It replaces
# the baseline reference rows with the demo dataset (patients, appointments, notes, etc.).
echo 'Loading demo data for development...'
$SQL oscar < /scripts/development.sql
echo 'Preparing demographic names for development environment...'
$SQL oscar < /database/mysql/updates/update-2025-11-06-demo-name-sanitization.sql
echo 'Seeding Rich Text Letter eForm...'
$SQL oscar < /database/mysql/updates/update-2012-07-12.sql
echo 'Modernizing Rich Text Letter eForm to 2026.3.0...'
$SQL oscar < /database/mysql/updates/update-2026-03-22-rtl-2026.3.0-modernize.sql
$SQL oscar < /database/mysql/updates/update-2026-03-12-rtl-enable-direct.sql
echo 'Database initialization complete!'
