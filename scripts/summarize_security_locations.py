#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Emit only allowlisted SARIF location metadata; never echo finding prose or source."""
import json
from pathlib import Path, PurePosixPath
import re
import sys
from urllib.parse import unquote, urlsplit

REPOSITORY_ROOT = Path(__file__).resolve().parent.parent


def syntax_error_locations(notice):
    """Semgrep puts parse coordinates in prose; retain only verified repository coordinates."""
    message = notice.get("message", {})
    text = message.get("text") if isinstance(message, dict) else None
    if not isinstance(text, str):
        return []
    match = re.match(r"\ASyntax error at line ([^:\r\n]{1,4096}):([1-9][0-9]{0,9}):", text)
    if not match:
        return []
    path = PurePosixPath(match.group(1))
    if path.is_absolute():
        try:
            path = path.relative_to(REPOSITORY_ROOT)
        except ValueError:
            return []
    if (".." in path.parts or "\\" in str(path)
            or not (REPOSITORY_ROOT / path).resolve().is_relative_to(REPOSITORY_ROOT)
            or not (REPOSITORY_ROOT / path).is_file()
            or (REPOSITORY_ROOT / path).is_symlink()):
        return []
    return [{"physicalLocation": {"artifactLocation": {"uri": path.as_posix()},
                                  "region": {"startLine": int(match.group(2))}}}]


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


def execution_records(report):
    """Retain scanner status and coordinates, excluding diagnostic prose and snippets."""
    records = []
    for run_index, run in enumerate(report.get("runs", [])):
        invocations = run.get("invocations", [])
        if not isinstance(invocations, list):
            raise ValueError("Invalid invocation list")
        for invocation_index, invocation in enumerate(invocations):
            success = invocation.get("executionSuccessful")
            if success is not None and type(success) is not bool:
                raise ValueError("Invalid execution status")
            identity = {"runIndex": run_index, "invocationIndex": invocation_index}
            records.append({"kind": "scannerExecution", **identity, "executionSuccessful": success})
            notifications = invocation.get("toolExecutionNotifications", [])
            if not isinstance(notifications, list) or len(notifications) > 10000:
                raise ValueError("Invalid execution notifications")
            for notice in notifications:
                descriptor = notice.get("descriptor", {}).get("id", "SEMGREP_TOOL_NOTIFICATION")
                if not isinstance(descriptor, str) or not re.fullmatch(r"[A-Za-z0-9_.:/ -]{1,512}", descriptor):
                    raise ValueError("Invalid notification descriptor")
                # SARIF notification descriptors are human labels, unlike rule identifiers.
                # Semgrep's real parser reports "Syntax error", with no locations array.
                rule = "Semgrep." + descriptor.replace(" ", "_") if " " in descriptor else descriptor
                level = notice.get("level", "warning")
                # Reuse the same strict identifier/coordinate validation as finding summaries.
                locations = notice.get("locations", [])
                if descriptor == "Syntax error" and not locations:
                    locations = syntax_error_locations(notice)
                selected = {"ruleId": rule, "level": level, "locations": locations}
                locations = location_records({"runs": [{"results": [selected]}]})
                base = {"kind": "scannerNotification", **identity}
                if locations:
                    records.extend({**base, **location} for location in locations)
                else:
                    records.append({**base, "ruleId": rule, "level": level})
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
        document = json.loads(report.read_text(encoding="utf-8"))
        records.extend(location_records(document))
        records.extend(execution_records(document))
    # Validate every record before printing, so a malformed report cannot leave a partial summary.
    for record in records:
        print(json.dumps(record, ensure_ascii=True, separators=(",", ":")))


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (OSError, ValueError, TypeError, KeyError, IndexError, AttributeError):
        print("Security location diagnostics unavailable: invalid report", file=sys.stderr)
        sys.exit(1)
