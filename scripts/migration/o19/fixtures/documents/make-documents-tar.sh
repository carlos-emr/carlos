#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
#
# make-documents-tar.sh — generate the O19 OscarDocument fixture tree and tar
# it. No binary fixtures are committed to the repo: this script produces
# deterministic placeholder files (minimal valid PDF / PNG bytes) in the
# layout an OSCAR 19 server tars up for migration (see manifest.json for
# what each file exercises in the documents-phase reconciliation).
#
# Usage: make-documents-tar.sh <output-dir>
#   writes <output-dir>/o19-documents.tar.gz containing oscar_mcmaster/…

set -euo pipefail
OUT="${1:?usage: make-documents-tar.sh <output-dir>}"
CTX="oscar_mcmaster"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

mkdir -p "$work/$CTX/document" "$work/$CTX/eform/images" \
         "$work/$CTX/incomingdocs/queue1"

# Minimal valid single-page PDF (fixed bytes -> deterministic tar content).
make_pdf() {
  printf '%%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\ntrailer<</Root 1 0 R>>\n%%%%EOF\n' > "$1"
}
# Minimal valid 1x1 PNG.
make_png() {
  printf '\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDATx\x9cc\xf8\xcf\xc0\x00\x00\x00\x03\x00\x01\x9a\x92\x1e\xd0\x00\x00\x00\x00IEND\xaeB`\x82' > "$1"
}

make_pdf "$work/$CTX/document/demo_referral_note.pdf"
make_pdf "$work/$CTX/document/demo_lab_scan.pdf"
make_pdf "$work/$CTX/document/demo_orphan_upload.pdf"   # no DB row: orphan
make_pdf "$work/$CTX/incomingdocs/queue1/demo_incoming_fax.pdf"
make_png "$work/$CTX/eform/images/demo_clinic_logo.png"
# NOTE: demo_missing_scan.pdf is deliberately NOT generated — its document
# row (fixture-document-rows.sql) must trip the reconciliation gate.

mkdir -p "$OUT"
# Fixed mtime/owner/order for reproducible output.
tar --sort=name --owner=0 --group=0 --numeric-owner \
    --mtime='2020-03-09 00:00:00 UTC' \
    -C "$work" -czf "$OUT/o19-documents.tar.gz" "$CTX"
echo "wrote $OUT/o19-documents.tar.gz"
