#!/bin/sh
# Fetch the pinned carlos-ctl release package (the .deb and its .sha256).
#
#   debian/fetch-carlos-ctl.sh <destdir> [tag]
#
# The tag defaults to debian/carlos-ctl.pin. The download is verified against
# the release's .sha256 and, when gh is available and authenticated, its
# build-provenance attestation (carlos-emr/carlos-ctl's release workflow
# attests every .deb it publishes). Prints the path of the .deb on success.
#
# Escape hatch for an offline builder or a local build under test:
#   CARLOS_CTL_DEB=/path/to/carlos-ctl_<version>_all.deb
# copies that file (and a .sha256 beside it, or one computed here) instead
# of downloading. CARLOS_CTL_REPO overrides the GitHub repository;
# CARLOS_CTL_SKIP_ATTESTATION=1 skips the attestation check (never for a
# release build: the deb-packages workflow relies on it).
#
# Kept out of debian/rules on purpose: the carlos-emr package does not need
# the carlos-ctl .deb to BUILD (it only Depends on it); the release workflow
# and the install tests do.
set -eu

dest="${1:?usage: fetch-carlos-ctl.sh <destdir> [tag]}"
here="$(cd "$(dirname "$0")" && pwd)"
tag="${2:-$(sed -n 's/^tag=//p' "$here/carlos-ctl.pin")}"
repo="${CARLOS_CTL_REPO:-carlos-emr/carlos-ctl}"
if [ -z "$tag" ]; then
    echo "fetch-carlos-ctl: no tag given and none in debian/carlos-ctl.pin" >&2
    exit 1
fi
mkdir -p "$dest"

if [ -n "${CARLOS_CTL_DEB:-}" ]; then
    name="$(basename "$CARLOS_CTL_DEB")"
    cp -f "$CARLOS_CTL_DEB" "$dest/$name"
    if [ -f "$CARLOS_CTL_DEB.sha256" ]; then
        cp -f "$CARLOS_CTL_DEB.sha256" "$dest/$name.sha256"
    else
        (cd "$dest" && sha256sum "$name" > "$name.sha256")
    fi
    (cd "$dest" && sha256sum -c "$name.sha256" >/dev/null)
    echo "using local carlos-ctl package: $dest/$name" >&2
    echo "$dest/$name"
    exit 0
fi

# GitHub stores a release asset name with the Debian '~' rewritten to '.',
# and a pre-release tag carries '-' where dpkg wants '~': 1.0.0-rc1 is
# published as carlos-ctl_1.0.0.rc1_all.deb.
version="$(printf '%s' "$tag" | sed 's/-/./g')"
name="carlos-ctl_${version}_all.deb"
if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    gh release download "$tag" --repo "$repo" --pattern "$name" \
        --pattern "$name.sha256" --dir "$dest" --clobber
else
    base="https://github.com/$repo/releases/download/$tag"
    curl -fsSL --retry 3 -o "$dest/$name" "$base/$name"
    curl -fsSL --retry 3 -o "$dest/$name.sha256" "$base/$name.sha256"
fi
(cd "$dest" && sha256sum -c "$name.sha256")
if [ "${CARLOS_CTL_SKIP_ATTESTATION:-0}" != 1 ]; then
    if command -v gh >/dev/null 2>&1; then
        # The .sha256 travels with the .deb, so it only catches a corrupt
        # download; the attestation binds the bytes to the workflow run that
        # built them, and nobody with mere contents:write can forge it.
        gh attestation verify "$dest/$name" --repo "$repo"
    else
        echo "fetch-carlos-ctl: gh is not installed, so the build-provenance attestation of $name was NOT verified (set CARLOS_CTL_SKIP_ATTESTATION=1 to silence this on a developer machine)" >&2
    fi
fi
echo "$dest/$name"
