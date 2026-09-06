#!/bin/bash

set -e  # Exit on any error

echo "[Bootstrap] Seeding initial database files..."

# The MariaDB entrypoint only runs database initialization against an empty
# volume. Reapply the idempotent development privilege repair here so existing
# local volumes receive new Administration grants after a devcontainer rebuild.
export MYSQL_PWD="${MARIADB_ROOT_PASSWORD:-${MYSQL_ROOT_PASSWORD:-password}}"
mariadb -h db -u root carlos \
    < /workspace/.devcontainer/db/scripts/development_privileges.sql

# The runtime document volume masks directories created in the image. Recreate
# the configured incoming-document root in that mounted volume so fresh
# devcontainers can render lazily-created queue children as empty queues.
mkdir -p /var/lib/CarlosDocument/carlos/incomingdocs

# Seeding initial database files for documents
cp -vn /db-data/documents/* /var/lib/CarlosDocument/carlos/document/
# HRM reports live in the same document directory. The snapshot's HRM rows name
# files that never shipped; demo-hrm-report.sql (populate_db.sh) points one
# demographic-1 report at this fixture so the HRM attachment family works.
cp -vn /db-data/hrm/*.xml /var/lib/CarlosDocument/carlos/document/
echo "[Bootstrap] Finished copying documents."
