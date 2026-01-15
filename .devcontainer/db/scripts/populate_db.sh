#!/usr/bin/env sh
echo 'Setting up all databases...'
cd /database/mysql || exit 1
echo 'Creating development database...'
./createdatabase_on.sh root password oscar
echo 'Creating test database...'
./createdatabase_on.sh root password oscar_test
echo 'Creating drugref2 database...'
mysql -u root -ppassword -e "CREATE DATABASE IF NOT EXISTS drugref2;"
mysql -u root -ppassword drugref2 < /database/mysql/development-drugref.sql
echo 'Loading demo data for development...'
mysql -u root -ppassword oscar < /scripts/development.sql
echo 'Preparing demographic names for development environment...'
mysql -u root -ppassword oscar < /database/mysql/updates/update-2025-11-06-demo-name-sanitization.sql
cd ../../
