#!/usr/bin/env sh
#
# CARLOS EMR - Production Database Initialization
#
# Runs once on first container startup (when /var/lib/mysql is empty) via
# the mariadb image's /docker-entrypoint-initdb.d mechanism.
#
# Environment variables (set in docker-compose.yml):
#   MYSQL_ROOT_PASSWORD  - required, provided by mariadb image from .env
#   CARLOS_PROVINCE      - "on" (Ontario) or "bc" (British Columbia), default "on"
#   LOAD_DEMO_DATA       - "true" loads demo patients/dev data, "false" (default)
#                          loads only schema + reference data for production use
#
# Exit codes:
#   0 = success
#   1 = missing required environment variable or SQL failure
#
# Source: adapted from .devcontainer/db/scripts/populate_db.sh

set -eu

echo "========================================="
echo "CARLOS EMR Database Initialization"
echo "========================================="

# The mariadb image's entrypoint exports MYSQL_ROOT_PASSWORD for us, but guard
# against misconfiguration. Setup scripts must NEVER default this to a known
# value in production - fail loudly instead.
if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
    echo "ERROR: MYSQL_ROOT_PASSWORD is not set. Aborting." >&2
    echo "Set DB_ROOT_PASSWORD in .env and restart." >&2
    exit 1
fi

PROVINCE="${CARLOS_PROVINCE:-on}"
case "$PROVINCE" in
    on|bc) ;;
    *)
        echo "ERROR: CARLOS_PROVINCE must be 'on' or 'bc', got '$PROVINCE'" >&2
        exit 1
        ;;
esac

LOAD_DEMO="${LOAD_DEMO_DATA:-false}"
echo "Province:       $PROVINCE"
echo "Load demo data: $LOAD_DEMO"
echo ""

cd /database/mysql || { echo "ERROR: /database/mysql not found" >&2; exit 1; }

MYSQL="mysql -u root -p${MYSQL_ROOT_PASSWORD}"

# -----------------------------------------------------------------------------
# Step 1: Base schema via createdatabase_${province}.sh
# -----------------------------------------------------------------------------
# The createdatabase_*.sh scripts call createdatabase_generic.sh which loads
# oscarinit.sql, oscardata.sql, province-specific data, ICD codes, CAISI, etc.
# See database/mysql/createdatabase_generic.sh for full list.

echo "[1/4] Creating oscar database with $PROVINCE schema..."
./createdatabase_${PROVINCE}.sh root "$MYSQL_ROOT_PASSWORD" oscar

echo "[2/4] Creating drugref2 database..."
$MYSQL -e "CREATE DATABASE IF NOT EXISTS drugref2;"
if [ -f /database/mysql/development-drugref.sql ]; then
    $MYSQL drugref2 < /database/mysql/development-drugref.sql
else
    echo "  (no drugref seed data found, skipping)"
fi

# -----------------------------------------------------------------------------
# Step 2: Apply migration scripts
# -----------------------------------------------------------------------------
# Migration order matters. List mirrors .devcontainer/db/scripts/populate_db.sh
# plus all migrations released since. Keep in chronological order.

echo "[3/4] Applying schema migrations..."
apply_update() {
    local file="$1"
    if [ -f "/database/mysql/updates/$file" ]; then
        echo "  - $file"
        $MYSQL oscar < "/database/mysql/updates/$file"
    else
        echo "  - $file (SKIPPED: not found)"
    fi
}

apply_update update-2025-01-29.sql
apply_update update-2025-02-27.sql
apply_update update-2025-05-27.sql
apply_update update-2025-08-14-study-removal.sql
apply_update update-2025-12-16-provider-module-singular.sql
apply_update update-2026-01-02-add-flowsheet-admin-privilege.sql
apply_update update-2026-01-26-tickler-indexes.sql
apply_update update-2026-02-10-fax-provider-type.sql
apply_update update-2026-02-14-facility-integrator-removal.sql
apply_update update-2026-03-10-standardize-prevention-types.sql
apply_update update-2026-03-25-security-mfa-default.sql
# RTL eForm seed + modernization (required regardless of demo/production mode)
apply_update update-2012-07-12.sql
apply_update update-2026-03-22-rtl-2026.3.0-modernize.sql
apply_update update-2026-03-12-rtl-enable-direct.sql

# -----------------------------------------------------------------------------
# Step 3: Optional demo data
# -----------------------------------------------------------------------------
# development.sql contains demo patients, providers, appointments for evaluation
# and UI testing. It is NOT suitable for production - it truncates many tables
# before inserting synthetic records.
#
# demo-name-sanitization.sql rewrites demographic names to obvious fakes
# (e.g. "Test Patient") so there's no mistaking demo data for real PHI.

if [ "$LOAD_DEMO" = "true" ]; then
    echo "[4/4] Loading demo data (LOAD_DEMO_DATA=true)..."
    if [ -f /scripts/development.sql ]; then
        echo "  - development.sql (demo patients, providers, appointments)"
        $MYSQL oscar < /scripts/development.sql
    else
        echo "  - WARNING: /scripts/development.sql not found, skipping demo patients"
    fi
    apply_update update-2025-11-06-demo-name-sanitization.sql
else
    echo "[4/4] Skipping demo data (production mode - no demo patients loaded)."
    echo "      To load demo data, set LOAD_DEMO_DATA=true in .env and rebuild"
    echo "      the database volume (destroys existing data)."
fi

# -----------------------------------------------------------------------------
# Runtime directories for eForm image uploads
# -----------------------------------------------------------------------------
# The CARLOS app container creates these, but the RTL eForm seed inserts rows
# that reference images in this path, so we create a placeholder here too.
mkdir -p /var/lib/OscarDocument/oscar/eform/images/ 2>/dev/null || true

echo ""
echo "========================================="
echo "Database initialization complete!"
echo "========================================="
echo "Default login (change immediately on first use):"
echo "  Username: carlosdoc"
echo "  Password: carlos2026"
echo "  PIN:      1117"
echo "Credentials expire 1 month from first login."
echo "========================================="
