#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Emit only allowlisted SARIF location metadata; never echo finding prose or source."""
import json
from pathlib import Path, PurePosixPath
import re
import sys
from urllib.parse import unquote, urlsplit


def location_records(report):
    records = []
    for run in report.get("runs", []):
        rules = run.get("tool", {}).get("driver", {}).get("rules", [])
        for result in run.get("results", []):
            rule = result.get("ruleId")
            if rule is None:
                index = result.get("ruleIndex")
                if type(index) is not int or index < 0:
                    raise ValueError("Invalid rule reference")
                rule = rules[index]["id"]
            if not isinstance(rule, str) or not re.fullmatch(r"[A-Za-z0-9_.:/-]{1,512}", rule):
                raise ValueError("Invalid rule identifier")
            level = result.get("level")
            if level not in (None, "none", "note", "warning", "error"):
                raise ValueError("Invalid level")
            baseline = result.get("baselineState")
            if baseline not in (None, "new", "unchanged", "updated", "absent"):
                raise ValueError("Invalid baseline state")
            for location in result.get("locations", []):
                physical = location.get("physicalLocation")
                if not physical:
                    continue
                uri = physical.get("artifactLocation", {}).get("uri", "")
                parts = urlsplit(uri)
                path = unquote(parts.path)
                relative = PurePosixPath(path)
                if (parts.scheme or parts.netloc or parts.query or parts.fragment or not path
                        or relative.is_absolute() or ".." in relative.parts
                        or "\\" in path or any(ord(char) < 32 or ord(char) == 127 for char in path)):
                    raise ValueError("Invalid repository location")
                region = physical.get("region", {})
                start = region.get("startLine")
                end = region.get("endLine", start)
                if (type(start) is not int or type(end) is not int
                        or not 0 < start <= end <= 2147483647):
                    raise ValueError("Invalid source region")
                record = {"ruleId": rule, "level": level, "path": relative.as_posix(),
                          "startLine": start, "endLine": end}
                if baseline is not None:
                    record["baselineState"] = baseline
                records.append(record)
    return records


def main(paths):
    records = []
    for path in paths:
        report = Path(path)
        # Cloud scans can be skipped while the independent repository scan exists.
        if not report.exists():
            continue
        if report.is_symlink() or not report.is_file():
            raise ValueError("Invalid report file")
        records.extend(location_records(json.loads(report.read_text(encoding="utf-8"))))
    # Validate every record before printing, so a malformed report cannot leave a partial summary.
    for record in records:
        print(json.dumps(record, ensure_ascii=True, separators=(",", ":")))


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (OSError, ValueError, TypeError, KeyError, IndexError, AttributeError):
        print("Security location diagnostics unavailable: invalid report", file=sys.stderr)
        sys.exit(1)
