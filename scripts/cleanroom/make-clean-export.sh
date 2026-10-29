#!/usr/bin/env bash
#
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
#
# Builds the clean-room implementer's workspace: a copy of the committed tree with
# the files being replaced removed and NO git history, so neither the original files
# nor any earlier version of them can be read from inside it. Used by
# /cleanroom-spec:cleanroom-implement; see .claude/commands/cleanroom-spec/README.md.
#
# Usage: bash scripts/cleanroom/make-clean-export.sh <export-dir> <spec.md> <target-path>...
#   <export-dir>  new, empty directory OUTSIDE this repository
#   <spec.md>     the approved spec; copied to <export-dir>/CLEANROOM-SPEC.md
#   <target-path> repo-relative path of each file being replaced (removed from the export)

set -euo pipefail

if [ "$#" -lt 3 ]; then
    echo "usage: $0 <export-dir> <spec.md> <target-path>..." >&2
    exit 2
fi

export_dir=$1
spec=$2
shift 2

repo=$(git rev-parse --show-toplevel)
export_abs=$(realpath -m "$export_dir")
case "$export_abs/" in
    "$repo"/*) echo "export dir must be outside the repository ($repo)" >&2; exit 2 ;;
esac
if [ -e "$export_abs" ] && [ -n "$(ls -A "$export_abs" 2>/dev/null)" ]; then
    echo "export dir must be new or empty: $export_abs" >&2
    exit 2
fi
grep -q '^Status: Approved' "$spec" || {
    echo "spec is not approved (needs a line 'Status: Approved by <name> on <date>'): $spec" >&2
    exit 2
}
for t in "$@"; do
    git -C "$repo" ls-files --error-unmatch -- "$t" >/dev/null
done

source_commit=$(git -C "$repo" rev-parse HEAD)
mkdir -p "$export_abs"

# Committed tree only: no .git, no uncommitted or ignored files.
git -C "$repo" archive --format=tar HEAD | tar -x -C "$export_abs"

manifest="$export_abs/CLEANROOM-MANIFEST.txt"
{
    echo "source_repository: $(git -C "$repo" config --get remote.origin.url || echo unknown)"
    echo "source_commit: $source_commit"
    echo "created_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "spec: CLEANROOM-SPEC.md (sha256 $(sha256sum "$spec" | cut -d' ' -f1))"
    echo "removed (git blob of the original at source_commit):"
} > "$manifest"
for t in "$@"; do
    echo "  $t $(git -C "$repo" rev-parse "HEAD:$t")" >> "$manifest"
    rm -f -- "$export_abs/$t"
done

cp -- "$spec" "$export_abs/CLEANROOM-SPEC.md"

# Keep the implementer away from the original repository and from the web, where
# the upstream OSCAR copies of these files are published. Read rules also cover
# Grep and Glob. Bash access is not covered here; the audit script checks the
# implementer's transcript for it.
mkdir -p "$export_abs/.claude"
cat > "$export_abs/.claude/settings.local.json" <<EOF
{
  "permissions": {
    "deny": [
      "Read(/$repo/**)",
      "Edit(/$repo/**)",
      "WebFetch",
      "WebSearch"
    ]
  }
}
EOF

# A fresh repository whose first commit is the export, so the implementer's
# work is exactly the diff from that commit.
git -C "$export_abs" init -q
git -C "$export_abs" -c core.safecrlf=false add -A
git -C "$export_abs" -c user.name="cleanroom-export" -c user.email="cleanroom@localhost" \
    commit -q -m "clean-room export of $source_commit"

echo "Clean export ready: $export_abs"
cat "$manifest"
echo "export_commit: $(git -C "$export_abs" rev-parse HEAD)"
