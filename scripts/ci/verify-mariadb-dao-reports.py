#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Verify executed MariaDB coverage and preserve outcomes without test payloads."""
from pathlib import Path
import argparse
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--reports", type=Path, default=Path("target/surefire-reports"))
parser.add_argument("--output", type=Path, default=Path("target/mariadb-dao-test-summary.txt"))
args = parser.parse_args()
critical = {
    "MariaDbDaoHarnessIntegrationTest": 1,
    "MeasurementDaoIntegrationTest": 1,
    "MeasurementsExtDaoIntegrationTest": 1,
    "ProviderLabRoutingDaoIntegrationTest": 1,
    "BillingONCHeader1DaoIntegrationTest": 1,
}
required = {"generated": {**critical, "MeasurementPatientMatchMariaDbIntegrationTest": 10,
                          "BillingHistoryDaoIntegrationTest": 1, "LookupDaoIntegrationTest": 1}, "flyway": critical}
counts = {mode: dict(tests=0, failures=0, errors=0, skipped=0) for mode in required}
families = {mode: dict.fromkeys(required[mode], 0) for mode in required}
lines = []
reports = list(args.reports.glob("TEST-*.xml"))
for report in reports:
    root = ET.parse(report).getroot()
    mode = "flyway" if report.stem.endswith("-flyway") else "generated"
    # Actual testcase elements avoid existing nested-suite header discrepancies.
    for case in root.iter("testcase"):
        counts[mode]["tests"] += 1
        outcome = "pass"
        for tag, key in (("failure", "failures"), ("error", "errors"), ("skipped", "skipped")):
            if case.find(tag) is not None:
                counts[mode][key] += 1
                outcome = tag
        lines.append(f"{mode} {outcome}: {case.get('classname', '')}::{case.get('name', '')}")
        for family in families[mode]:
            if family in report.name and outcome == "pass":
                families[mode][family] += 1
summary = "MariaDB DAO integration results\n" + str(counts) + "\n" + str(families) + "\n"
args.output.write_text(summary + "\n".join(lines) + "\n")
print(summary)
assert reports, "No MariaDB DAO reports found"
assert counts["generated"]["tests"] >= 2500, "Broad MariaDB DAO selection unexpectedly lost coverage"
for mode in required:
    assert counts[mode]["tests"] > 0, f"No {mode} MariaDB tests executed"
    for key in ("failures", "errors", "skipped"):
        assert counts[mode][key] == 0, f"MariaDB {mode} run has {key}"
    for family, minimum in required[mode].items():
        assert families[mode][family] >= minimum, f"Required MariaDB {mode} coverage missing: {family}"
