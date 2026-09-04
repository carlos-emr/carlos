#!/usr/bin/env python3
"""Convert a SpotBugs XML report to a SARIF file of SECURITY-category findings.

Body of the "Convert SpotBugs security XML to SARIF" step of
.github/workflows/spotbugs.yml, moved here unchanged so it can be linted and
run locally. Only findings in the SECURITY category with a usable source
location are emitted (they are what is uploaded to GitHub Code Scanning); a
metrics file records what was skipped for the job summary.

Environment:
  REPORT  path to the SpotBugs XML report (required)
Requires defusedxml (scripts/ci/requirements.txt).
Outputs, in the current directory:
  spotbugs-security-report.sarif, spotbugs-security-metrics.env

License:
This file is part of the CARLOS EMR project and is subject to the licensing
terms outlined in the repository's LICENSE file.
"""
import os
import json, sys

# defusedxml rather than the stdlib parser: the report comes from SpotBugs in
# the same job, but the converter is now a standalone script anyone can point
# at any file, so it must not be XXE / entity-expansion capable. Installed in
# CI from scripts/ci/requirements.txt (hash-pinned).
try:
    import defusedxml.ElementTree as ET
except ImportError:
    sys.exit("defusedxml is required: python3 -m pip install -r scripts/ci/requirements.txt")

report_path = os.environ["REPORT"]
sarif_path = "spotbugs-security-report.sarif"
metrics_path = "spotbugs-security-metrics.env"

try:
    tree = ET.parse(report_path)
except ET.ParseError as e:
    print(f"Failed to parse SpotBugs XML: {e}", file=sys.stderr)
    sys.exit(1)

root = tree.getroot()
results = []
rules_map = {}
total_findings = 0
skipped_non_security = 0
skipped_no_source = 0

for bug in root.findall("BugInstance"):
    total_findings += 1
    bug_type = bug.get("type", "unknown")
    category = bug.get("category", "")
    category_normalized = category.strip().upper()
    if category_normalized != "SECURITY":
        skipped_non_security += 1
        continue

    priority = int(bug.get("priority", "3"))
    message_elem = bug.find("LongMessage")
    short_msg_elem = bug.find("ShortMessage")
    message = ""
    if message_elem is not None and message_elem.text:
        message = message_elem.text
    elif short_msg_elem is not None and short_msg_elem.text:
        message = short_msg_elem.text
    else:
        message = bug_type

    level_map = {1: "error", 2: "warning", 3: "note"}
    security_severity_map = {1: "8.0", 2: "5.0", 3: "2.0"}
    level = level_map.get(priority, "warning")
    security_severity = security_severity_map.get(priority, "5.0")

    source_line = bug.find("SourceLine")
    if source_line is None:
        for child in bug:
            sl = child.find("SourceLine")
            if sl is not None and sl.get("sourcepath"):
                source_line = sl
                break

    uri = ""
    start_line = 1
    end_line = 1
    if source_line is not None:
        sourcepath = source_line.get("sourcepath", "")
        if sourcepath:
            uri = f"src/main/java/{sourcepath}"
        start = source_line.get("start")
        end = source_line.get("end")
        if start:
            start_line = int(start)
        if end:
            end_line = int(end)

    if not uri:
        skipped_no_source += 1
        print(
            f"Skipping SECURITY finding without source location: {bug_type}",
            file=sys.stderr
        )
        continue

    if bug_type not in rules_map:
        rules_map[bug_type] = {
            "id": bug_type,
            "shortDescription": {"text": bug_type},
            "properties": {
                "category": category_normalized,
                "tags": ["security"],
                "security-severity": security_severity
            }
        }
    else:
        properties = rules_map[bug_type]["properties"]
        if float(security_severity) > float(properties["security-severity"]):
            properties["security-severity"] = security_severity

    result = {
        "ruleId": bug_type,
        "level": level,
        "message": {"text": message},
        "locations": [{
            "physicalLocation": {
                "artifactLocation": {"uri": uri, "uriBaseId": "%SRCROOT%"},
                "region": {"startLine": start_line, "endLine": end_line}
            }
        }]
    }
    results.append(result)

sarif = {
    "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
    "version": "2.1.0",
    "runs": [{
        "tool": {
            "driver": {
                "name": "SpotBugs + Find Security Bugs",
                "informationUri": "https://spotbugs.github.io/",
                "rules": list(rules_map.values())
            }
        },
        "results": results
    }]
}

with open(sarif_path, "w") as f:
    json.dump(sarif, f, indent=2)

with open(metrics_path, "w") as f:
    f.write(f"TOTAL_FINDINGS={total_findings}\n")
    f.write(f"SECURITY_FINDINGS_UPLOADED={len(results)}\n")
    f.write(f"SKIPPED_NON_SECURITY={skipped_non_security}\n")
    f.write(f"SKIPPED_NO_SOURCE={skipped_no_source}\n")

print(f"Converted {len(results)} security findings to SARIF")
print(f"Skipped {skipped_non_security} non-security findings from {total_findings} total findings")
print(f"Skipped {skipped_no_source} security findings without source locations")
