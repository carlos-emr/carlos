#!/bin/sh
# Fetch the DrugRef2 source for the carlos-emr-drugref binary package.
#
#   debian/fetch-drugref.sh <ref> <destdir>
#
# Kept out of debian/rules so an air-gapped builder can replace it, or skip it
# entirely by exporting DRUGREF_SRC=/path/to/checkout.
set -eu

ref="$1"
dest="$2"
repo="${DRUGREF_REPO:-https://github.com/carlos-emr/drugref2026.git}"

if [ -d "$dest/.git" ]; then
    echo "drugref source already present at $dest"
    exit 0
fi

echo "fetching DrugRef2 $ref from $repo"
rm -rf "$dest"
# A full clone, then an explicit checkout: --depth 1 --branch only accepts a
# branch or tag, and the pin is expected to be a commit SHA for release builds.
git clone --quiet "$repo" "$dest"
git -C "$dest" checkout --quiet --detach "$ref"
echo "drugref source at $(git -C "$dest" rev-parse HEAD)"
