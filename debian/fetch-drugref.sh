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
    # Detach to FETCH_HEAD when the scoped fetch succeeds: fetching a URL
    # updates no local refs, so `checkout --detach <branch>` resolved the
    # STALE clone-time branch tip and silently packaged the old revision.
    # (A sha ref is unaffected either way; branch/tag refs need this.)
    if git -C "$dest" fetch --quiet "$repo" "$ref"; then
        git -C "$dest" checkout --quiet --detach FETCH_HEAD
    else
        git -C "$dest" fetch --quiet "$repo"
        git -C "$dest" checkout --quiet --detach "$ref"
    fi
    echo "drugref source at $(git -C "$dest" rev-parse HEAD)"
    exit 0
fi

echo "fetching DrugRef2 $ref from $repo"
# Guard the recursive delete: an empty or root-ish $dest from a caller bug
# must fail here, not become an rm -rf of something that matters.
case "$dest" in
    ""|/|.|..|*/.|*/..|-*)
        echo "fetch-drugref: refusing to delete suspicious destination '$dest'" >&2
        exit 1
        ;;
esac
if [ ! -d "$(dirname "$dest")" ]; then
    echo "fetch-drugref: parent of '$dest' does not exist" >&2
    exit 1
fi
rm -rf -- "$dest"
git clone --quiet "$repo" "$dest"
git -C "$dest" checkout --quiet --detach "$ref"
echo "drugref source at $(git -C "$dest" rev-parse HEAD)"
