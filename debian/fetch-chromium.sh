#!/bin/sh
# Fetch the pinned Chromium + chromedriver for the carlos-emr-eform-renderer
# binary package.
#
#   debian/fetch-chromium.sh <rev> <chrome_sha256> <chromedriver_sha256> <destdir>
#
# Kept out of debian/rules so an air-gapped builder can replace it. (The
# CHROMIUM_DIST=/path/to/dir escape hatch is honoured by debian/rules itself,
# which then never invokes this script — nothing here reads it.)
#
# Leaves <destdir>/chrome-linux/ and <destdir>/chromedriver in place.
set -eu

rev="$1"
chrome_sha="$2"
driver_sha="$3"
dest="$4"

base="${CHROMIUM_BASE_URL:-https://commondatastorage.googleapis.com/chromium-browser-snapshots/Linux_x64}"

for tool in curl unzip sha256sum; do
    command -v "$tool" >/dev/null 2>&1 || {
        echo "fetch-chromium: $tool is not installed (it is in Build-Depends; for an offline build export CHROMIUM_DIST instead)" >&2
        exit 1
    }
done

# Guard the recursive delete: an empty or root-ish $dest from a caller bug must
# fail here, not become an rm -rf of something that matters.
case "$dest" in
    ""|/|.|..|*/.|*/..|-*)
        echo "fetch-chromium: refusing to delete suspicious destination '$dest'" >&2
        exit 1
        ;;
esac
if [ ! -d "$(dirname "$dest")" ]; then
    echo "fetch-chromium: parent of '$dest' does not exist" >&2
    exit 1
fi

rm -rf -- "$dest"
mkdir -p "$dest"

# Verify BEFORE unpacking, not after: an unverified archive must never be
# written into the staging tree that becomes the .deb.
fetch_verify() {
    url="$1" out="$2" want="$3"
    echo "fetching $url"
    curl -fsSL -o "$out" "$url"
    got="$(sha256sum "$out" | cut -d' ' -f1)"
    if [ "$got" != "$want" ]; then
        echo "fetch-chromium: checksum mismatch for $url" >&2
        echo "  expected $want" >&2
        echo "  actual   $got" >&2
        exit 1
    fi
    echo "  sha256 ok"
}

fetch_verify "$base/$rev/chrome-linux.zip" "$dest/chrome-linux.zip" "$chrome_sha"
fetch_verify "$base/$rev/chromedriver_linux64.zip" "$dest/chromedriver_linux64.zip" "$driver_sha"

unzip -q "$dest/chrome-linux.zip" -d "$dest"
unzip -q "$dest/chromedriver_linux64.zip" -d "$dest"
rm -f "$dest/chrome-linux.zip" "$dest/chromedriver_linux64.zip"

# The driver's path inside the archive has moved between upstream revisions
# (chromedriver_linux64/chromedriver vs. a bare chromedriver); normalise it so
# debian/rules has one path to install.
if [ ! -f "$dest/chromedriver" ]; then
    found="$(find "$dest" -type f -name chromedriver -print | head -n 1)"
    [ -n "$found" ] || { echo "fetch-chromium: chromedriver not found in archive" >&2; exit 1; }
    mv -- "$found" "$dest/chromedriver"
fi
chmod 0755 "$dest/chromedriver" "$dest/chrome-linux/chrome"

[ -x "$dest/chrome-linux/chrome" ] || { echo "fetch-chromium: chrome missing after unpack" >&2; exit 1; }
echo "chromium $rev staged at $dest"
