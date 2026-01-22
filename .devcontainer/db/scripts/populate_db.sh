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
echo 'Loading demo data for development...'
mysql -u root -p"$DB_PASSWORD" oscar < /scripts/development.sql
echo 'Preparing demographic names for development environment...'
mysql -u root -p"$DB_PASSWORD" oscar < /database/mysql/updates/update-2025-11-06-demo-name-sanitization.sql
cd ../../
echo 'Database initialization complete!'
