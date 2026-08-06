#!/bin/bash

set -e  # Exit on any error

echo "[Bootstrap] Seeding initial database files..."

# The runtime document volume masks directories created in the image. Recreate
# the configured incoming-document root in that mounted volume so fresh
# devcontainers can render lazily-created queue children as empty queues.
mkdir -p /var/lib/OscarDocument/oscar/incomingdocs

# Seeding initial database files for documents
cp -vn /db-data/documents/* /var/lib/OscarDocument/oscar/document/
echo "[Bootstrap] Finished copying documents."
