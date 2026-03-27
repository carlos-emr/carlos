#!/usr/bin/env sh
echo 'Setting up all databases...'
cd /database/mysql || exit 1

# Use MYSQL_ROOT_PASSWORD environment variable, fallback to 'password' for development
DB_PASSWORD="${MYSQL_ROOT_PASSWORD:-password}"

echo 'Creating development database...'
./createdatabase_on.sh root "$DB_PASSWORD" oscar
echo 'Creating test database...'
./createdatabase_on.sh root "$DB_PASSWORD" oscar_test
echo 'Creating drugref2 database...'
mysql -u root -p"$DB_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS drugref2;"
mysql -u root -p"$DB_PASSWORD" drugref2 < /database/mysql/development-drugref.sql
echo 'Applying schema updates...'
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2025-01-29.sql
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2025-02-27.sql
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2025-05-27.sql
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2025-08-14-study-removal.sql
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2025-12-16-provider-module-singular.sql
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2026-01-02-add-flowsheet-admin-privilege.sql
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2026-01-26-tickler-indexes.sql
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2026-02-10-fax-provider-type.sql
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2026-02-14-facility-integrator-removal.sql
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2026-03-25-security-mfa-default.sql
# CAUTION: This migration drops deprecated form tables (formONAR, formIntakeHx, etc.)
# and deletes their encounterForm entries. Run manually only after verifying no patient
# data exists in these tables: mysql oscar < /database/mysql/updates/update-2026-03-25-remove-deprecated-form-tables.sql
echo 'Loading demo data for development...'
mysql -u root -p"$DB_PASSWORD" oscar < /scripts/development.sql
echo 'Preparing demographic names for development environment...'
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2025-11-06-demo-name-sanitization.sql
echo 'Creating eForm images directory for RTL asset deployment...'
mkdir -p /var/lib/OscarDocument/oscar/eform/images/
echo 'Seeding Rich Text Letter eForm...'
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2012-07-12.sql
echo 'Modernizing Rich Text Letter eForm to 2026.3.0...'
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2026-03-22-rtl-2026.3.0-modernize.sql
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2026-03-12-rtl-enable-direct.sql
cd ../../
echo 'Database initialization complete!'
