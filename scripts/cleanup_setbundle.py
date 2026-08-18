#!/usr/bin/env python3

# Copyright (c) 2026 Sebastian Ibanez. All Rights Reserved.
#
# This software is published under the GNU General Public License.
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

"""Cleanup script to remove redundant fmt:setBundle calls from JSP files.

For each JSP file under src/main/webapp/:
1. Removes all <fmt:setBundle basename="oscarResources"/> tags
2. Inserts a single instance near the top of the file
3. Cleans up lines that become empty after removal

Usage:
    python3 scripts/cleanup_setbundle.py --diff      # Show diffs without modifying files
    python3 scripts/cleanup_setbundle.py              # Apply changes
    python3 scripts/cleanup_setbundle.py --dry-run    # List files that would be modified
"""

import difflib
import os
import re
import sys

WEBAPP_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                          "src", "main", "webapp")

# Matches all variants: double/single quotes, optional space before />
SETBUNDLE_RE = re.compile(r'<fmt:setBundle\s+basename\s*=\s*["\']oscarResources["\']\s*/>')

# For finding insertion points (priority order)
FMT_TAGLIB_RE = re.compile(r'<%@\s*taglib\s+.*prefix\s*=\s*["\']fmt["\']\s*%>')
TAGLIBS_INCLUDE_RE = re.compile(r'<%@\s*include\s+file\s*=\s*["\'].*/taglibs\.jsp["\']\s*%>')
DIRECTIVE_RE = re.compile(r'<%@\s*(taglib|page|include)\s+')

SETBUNDLE_LINE = '<fmt:setBundle basename="oscarResources"/>\n'


def find_insertion_line(lines):
    """Find the line index AFTER which to insert the setBundle declaration.

    Priority:
    1. After the fmt taglib declaration
    2. After a taglibs.jsp include
    3. After the last <%@ directive
    4. Line 0 (top of file) as fallback
    """
    fmt_taglib_idx = None
    taglibs_include_idx = None
    last_directive_idx = None

    for i, line in enumerate(lines):
        if FMT_TAGLIB_RE.search(line):
            fmt_taglib_idx = i
        if TAGLIBS_INCLUDE_RE.search(line):
            taglibs_include_idx = i
        if DIRECTIVE_RE.search(line):
            last_directive_idx = i

    if fmt_taglib_idx is not None:
        return fmt_taglib_idx + 1
    if taglibs_include_idx is not None:
        return taglibs_include_idx + 1
    if last_directive_idx is not None:
        return last_directive_idx + 1
    return 0


def process_file(filepath):
    """Process a single JSP file. Returns (new_content, stats) or (None, stats) if no changes."""
    try:
        with open(filepath, "r", encoding="utf-8", errors="replace") as f:
            original = f.read()
    except OSError as e:
        return None, {"error": str(e)}

    count = len(SETBUNDLE_RE.findall(original))
    if count == 0:
        return None, {"skipped": True, "reason": "no setBundle found"}

    # Step 1: Remove all setBundle tags
    cleaned = SETBUNDLE_RE.sub("", original)

    # Step 2: Remove lines that became empty/whitespace-only after removal
    original_lines = original.splitlines(keepends=True)
    cleaned_lines = cleaned.splitlines(keepends=True)

    # Rebuild: drop lines that are now whitespace-only but weren't before
    final_lines = []
    for orig_line, clean_line in zip(original_lines, cleaned_lines):
        # If the line had content before but is now empty/whitespace, skip it
        if orig_line.strip() and not clean_line.strip():
            continue
        final_lines.append(clean_line)
    # Handle case where removal shortened line count (shouldn't happen with sub, but safety)
    if len(cleaned_lines) > len(original_lines):
        final_lines.extend(cleaned_lines[len(original_lines):])

    # Step 3: Find insertion point and insert single setBundle
    insert_idx = find_insertion_line(final_lines)
    final_lines.insert(insert_idx, SETBUNDLE_LINE)

    new_content = "".join(final_lines)

    # Validation
    warnings = validate(filepath, original, new_content, count)

    stats = {
        "removed": count,
        "inserted": 1,
        "warnings": warnings,
        "original_lines": len(original_lines),
        "new_lines": len(final_lines),
    }
    return new_content, stats


def validate(filepath, original, new_content, removed_count):
    """Validate the processed file for signs of corruption."""
    warnings = []

    # Check: file should have exactly 1 setBundle after processing
    new_count = len(SETBUNDLE_RE.findall(new_content))
    if new_count != 1:
        warnings.append(f"Expected 1 setBundle after cleanup, found {new_count}")

    # Check: if original had fmt:message, new content should too
    orig_msg_count = original.count("fmt:message")
    new_msg_count = new_content.count("fmt:message")
    if orig_msg_count != new_msg_count:
        warnings.append(f"fmt:message count changed: {orig_msg_count} -> {new_msg_count}")

    # Check: no non-setBundle content should be lost.
    # After stripping all setBundle tags from both versions, the remaining content
    # should be identical (modulo blank lines that were removed).
    orig_stripped = SETBUNDLE_RE.sub("", original)
    # Remove blank lines from orig_stripped for comparison
    orig_nonblank = [l for l in orig_stripped.splitlines() if l.strip()]
    new_nonblank = [l for l in new_content.splitlines() if l.strip()]
    # The new content has 1 inserted setBundle line; remove it for comparison
    new_nonblank_filtered = [l for l in new_nonblank
                             if l.strip() != '<fmt:setBundle basename="oscarResources"/>']
    if orig_nonblank != new_nonblank_filtered:
        warnings.append("Non-setBundle content differs after cleanup — possible data loss")

    return warnings


def make_diff(filepath, original, new_content):
    """Generate a unified diff between original and new content."""
    rel = filepath.replace(os.getcwd() + "/", "")
    orig_lines = original.splitlines(keepends=True)
    new_lines = new_content.splitlines(keepends=True)
    return "".join(difflib.unified_diff(orig_lines, new_lines, fromfile=f"a/{rel}", tofile=f"b/{rel}"))


def main():
    mode = "apply"
    if "--diff" in sys.argv:
        mode = "diff"
    elif "--dry-run" in sys.argv:
        mode = "dry-run"

    total_files = 0
    total_removed = 0
    total_warnings = 0
    warning_files = []

    for root, _dirs, files in sorted(os.walk(WEBAPP_DIR)):
        for fname in sorted(files):
            if not fname.endswith(".jsp"):
                continue
            fpath = os.path.join(root, fname)

            new_content, stats = process_file(fpath)

            if new_content is None:
                continue

            total_files += 1
            total_removed += stats["removed"]
            rel = fpath.replace(os.getcwd() + "/", "")

            if stats["warnings"]:
                total_warnings += len(stats["warnings"])
                warning_files.append((rel, stats["warnings"]))
                for w in stats["warnings"]:
                    print(f"WARNING: {rel}: {w}", file=sys.stderr)

            if mode == "diff":
                with open(fpath, "r", encoding="utf-8", errors="replace") as f:
                    original = f.read()
                diff = make_diff(fpath, original, new_content)
                if diff:
                    print(diff)
            elif mode == "dry-run":
                print(f"  Would modify: {rel} (remove {stats['removed']}, insert 1)")
            else:
                with open(fpath, "w", encoding="utf-8") as f:
                    f.write(new_content)

    # Summary
    print("=" * 70, file=sys.stderr)
    print(f"fmt:setBundle Cleanup {'(DRY RUN)' if mode != 'apply' else 'COMPLETE'}", file=sys.stderr)
    print("=" * 70, file=sys.stderr)
    print(f"Files processed:     {total_files}", file=sys.stderr)
    print(f"Total tags removed:  {total_removed}", file=sys.stderr)
    print(f"Tags inserted:       {total_files} (1 per file)", file=sys.stderr)
    print(f"Warnings:            {total_warnings}", file=sys.stderr)

    if warning_files:
        print(f"\nFiles with warnings:", file=sys.stderr)
        for rel, warns in warning_files:
            for w in warns:
                print(f"  {rel}: {w}", file=sys.stderr)

    return 1 if total_warnings > 0 else 0


if __name__ == "__main__":
    sys.exit(main())