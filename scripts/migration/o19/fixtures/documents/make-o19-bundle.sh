#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
#
# make-o19-bundle.sh — pack the three turnkey inputs into every --bundle
# variant the importer accepts, using the canonical creation command from
# docs/o19-import-deb.md. For tests and rehearsals (fixed test password).
#
# Usage: make-o19-bundle.sh <inputs-dir> <output-dir>
#   <inputs-dir> must hold: o19-fixture.sql.gz (or *.sql.gz),
#   o19-documents.tar.gz, oscar.properties
#   (build-o19-fixture.sh --out produces exactly this layout)

set -euo pipefail
IN="${1:?usage: make-o19-bundle.sh <inputs-dir> <output-dir>}"
OUT="${2:?usage: make-o19-bundle.sh <inputs-dir> <output-dir>}"
TEST_PASSWORD="o19-fixture-test-password"

# exactly ONE dump, mirroring the importer's own ambiguity rule (never
# silently bundle the first of several)
shopt -s nullglob
dumps=("$IN"/*.sql.gz)
shopt -u nullglob
if [ "${#dumps[@]}" -ne 1 ]; then
  echo "expected exactly one *.sql.gz in $IN, found ${#dumps[@]}: ${dumps[*]:-none}" >&2
  exit 1
fi
dump="${dumps[0]}"
docs="$IN/o19-documents.tar.gz"
props="$IN/oscar.properties"
for f in "$dump" "$docs" "$props"; do
  [ -f "$f" ] || { echo "missing input: $f" >&2; exit 1; }
done

mkdir -p "$OUT"
passfile="$OUT/bundle-password.txt"
printf '%s' "$TEST_PASSWORD" > "$passfile"

# members at the archive ROOT, paths trimmed
tarargs=(--sort=name --owner=0 --group=0 --numeric-owner
         --mtime='2020-03-09 00:00:00 UTC'
         -C "$(dirname "$dump")" "$(basename "$dump")"
         -C "$(dirname "$docs")" "$(basename "$docs")"
         -C "$(dirname "$props")" "$(basename "$props")")

tar "${tarargs[@]}" -cf  "$OUT/o19-bundle.tar"
tar "${tarargs[@]}" -czf "$OUT/o19-bundle.tar.gz"
# canonical encryption command (docs/o19-import-deb.md)
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass "file:$passfile" \
    -in "$OUT/o19-bundle.tar"    -out "$OUT/o19-bundle.tar.enc"
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass "file:$passfile" \
    -in "$OUT/o19-bundle.tar.gz" -out "$OUT/o19-bundle.tar.gz.enc"

echo "wrote 4 bundle variants + $passfile in $OUT"
