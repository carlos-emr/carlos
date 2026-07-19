#!/bin/bash

set -e  # Exit on any error

echo "[Bootstrap] Seeding initial database files..."

# Seeding initial database files for documents
cp -vn /db-data/documents/* /var/lib/OscarDocument/oscar/document/
echo "[Bootstrap] Finished copying documents."