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

"""Diagnostic script to report fmt:setBundle usage across JSP files.

Scans all JSP files under src/main/webapp/ and reports:
- Total fmt:setBundle instance count
- Files with more than 1 instance (redundant)
- Summary statistics

Usage:
    python3 scripts/check_setbundle.py
    python3 scripts/check_setbundle.py --list-all    # Show all files, not just >1
"""

import os
import re
import sys

WEBAPP_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                          "src", "main", "webapp")

# Matches all variants of <fmt:setBundle basename="oscarResources"/>
SETBUNDLE_RE = re.compile(r'<fmt:setBundle\s+basename\s*=\s*["\']oscarResources["\']\s*/>')


def scan_files():
    """Scan all JSP files and return dict of {filepath: count}."""
    results = {}
    for root, _dirs, files in os.walk(WEBAPP_DIR):
        for fname in files:
            if not fname.endswith(".jsp"):
                continue
            fpath = os.path.join(root, fname)
            try:
                with open(fpath, "r", encoding="utf-8", errors="replace") as f:
                    content = f.read()
            except OSError as e:
                print(f"Warning: could not read {fpath}: {e}", file=sys.stderr)
                continue
            count = len(SETBUNDLE_RE.findall(content))
            if count > 0:
                results[fpath] = count
    return results


def main():
    list_all = "--list-all" in sys.argv

    results = scan_files()
    total_instances = sum(results.values())
    files_with_any = len(results)
    files_with_multiple = {f: c for f, c in results.items() if c > 1}
    files_with_one = {f: c for f, c in results.items() if c == 1}

    print("=" * 70)
    print("fmt:setBundle Diagnostic Report")
    print("=" * 70)
    print(f"Total fmt:setBundle instances:     {total_instances}")
    print(f"Files with any fmt:setBundle:      {files_with_any}")
    print(f"Files with exactly 1 (OK):         {len(files_with_one)}")
    print(f"Files with >1 (REDUNDANT):         {len(files_with_multiple)}")
    print(f"Redundant instances to remove:     {total_instances - files_with_any}")
    print("=" * 70)

    if files_with_multiple or list_all:
        target = results if list_all else files_with_multiple
        label = "All files" if list_all else "Files with REDUNDANT fmt:setBundle (>1)"
        print(f"\n{label}:")
        print("-" * 70)
        rel = WEBAPP_DIR.replace(os.getcwd() + "/", "")
        for fpath in sorted(target.keys()):
            display = fpath.replace(WEBAPP_DIR, rel)
            print(f"  {target[fpath]:4d}  {display}")
        print("-" * 70)
        print(f"  Total: {len(target)} files")

    if not files_with_multiple:
        print("\nAll files have at most 1 fmt:setBundle. Cleanup complete!")
    else:
        print(f"\n{len(files_with_multiple)} files need cleanup.")

    return 0 if not files_with_multiple else 1


if __name__ == "__main__":
    sys.exit(main())