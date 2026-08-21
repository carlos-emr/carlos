#!/bin/sh
# Fetch the DrugRef2 source for the carlos-emr-drugref binary package.
#
#   debian/fetch-drugref.sh <ref> <destdir>
#
# Kept out of debian/rules so an air-gapped builder can replace it. (The
# DRUGREF_SRC=/path/to/checkout escape hatch is honoured by debian/rules
# itself, which then never invokes this script — nothing here reads it.)
set -eu

command -v git >/dev/null 2>&1 || {
    echo "fetch-drugref: git is not installed (it is in Build-Depends; for an offline build export DRUGREF_SRC or DRUGREF_WAR instead)" >&2
    exit 1
}

ref="$1"
dest="$2"
repo="${DRUGREF_REPO:-https://github.com/carlos-emr/drugref2026.git}"

if [ -d "$dest/.git" ]; then
    # An existing checkout must still land on the CURRENT pin: exiting early
    # here meant an incremental build silently packaged whatever revision the
    # previous build fetched, even after debian/drugref.pin moved.
    echo "updating existing DrugRef2 checkout at $dest to $ref"
    git -C "$dest" fetch --quiet "$repo" "$ref" || git -C "$dest" fetch --quiet "$repo"
    git -C "$dest" checkout --quiet --detach "$ref"
    echo "drugref source at $(git -C "$dest" rev-parse HEAD)"
    exit 0
fi

echo "fetching DrugRef2 $ref from $repo"
# Guard the recursive delete: an empty or root-ish $dest from a caller bug
# must fail here, not become an rm -rf of something that matters.
