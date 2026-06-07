#!/usr/bin/env python3

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

"""Remove suppressed SARIF results before uploading to GitHub Code Scanning."""

from __future__ import annotations

import argparse
import json
import os
import tempfile
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Remove SARIF results with non-empty suppressions arrays."
    )
    parser.add_argument("sarif_file", type=Path, help="Path to the SARIF file to filter")
    return parser.parse_args()


def filter_sarif(sarif: dict[str, Any]) -> tuple[int, int]:
    total_results = 0
    suppressed_results = 0

    for run in sarif.get("runs", []):
        results = run.get("results")
        if not isinstance(results, list):
            continue

        kept_results = []
        for result in results:
            total_results += 1
            if isinstance(result, dict) and result.get("suppressions"):
                suppressed_results += 1
                continue
            kept_results.append(result)

        run["results"] = kept_results

    return total_results, suppressed_results


def write_json_atomically(path: Path, data: dict[str, Any]) -> None:
    original_mode = path.stat().st_mode & 0o777 if path.exists() else 0o644

    with tempfile.NamedTemporaryFile(
        "w",
        encoding="utf-8",
        dir=path.parent,
        delete=False,
        prefix=f".{path.name}.",
        suffix=".tmp",
    ) as temp_file:
        json.dump(data, temp_file, indent=2)
        temp_file.write("\n")
        temp_name = temp_file.name

    os.chmod(temp_name, original_mode)
    os.replace(temp_name, path)


def main() -> int:
    args = parse_args()
    sarif_path = args.sarif_file

    with sarif_path.open(encoding="utf-8") as sarif_file:
        sarif = json.load(sarif_file)

    total_results, suppressed_results = filter_sarif(sarif)
    write_json_atomically(sarif_path, sarif)

    kept_results = total_results - suppressed_results
    print(
        "Filtered suppressed SARIF results: "
        f"{suppressed_results} removed, {kept_results} kept, {total_results} total."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
