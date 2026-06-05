#!/usr/bin/env python3
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
