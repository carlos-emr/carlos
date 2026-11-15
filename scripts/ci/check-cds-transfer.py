#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Run the complete reviewed CDS import/export suite against owned disposable test databases."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile


def native_tier():
    """The native tier's report checker: one source of truth for complete evidence."""
    path = Path(__file__).parent / "run-cds-native-tier.py"
    spec = importlib.util.spec_from_file_location("run_cds_native_tier", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--settings", type=Path)
    parser.add_argument("--maven-local-repository", type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    selectors = json.loads((Path(__file__).parent / "cds-coordinated-test-classes.json").read_text())
    if not selectors or len(selectors) != len(set(selectors)):
        parser.error("The reviewed CDS test inventory is missing or contains duplicates")
    command = ["mvn", "-B", "-ntp"]
    if args.offline:
        command.append("-o")
    if args.settings:
        command.extend(["-s", str(args.settings.resolve(strict=True))])
    if args.maven_local_repository:
        command.append("-Dmaven.repo.local=" + str(args.maven_local_repository.resolve(strict=True)))
    properties = {"CDS_TEST_PROPERTIES": "cds.test.properties",
                  "CDS_LEDGER_TEST_PROPERTIES": "cds.ledger.test.properties",
                  "CDS_EXPORT_TEST_PROPERTIES": "cds.export.test.properties"}
    for variable, property_name in properties.items():
        value = os.environ.get(variable)
        if not value:
            parser.error(variable + " must identify an owned disposable database configuration")
        path = Path(value).resolve(strict=True)
        if not path.is_file():
            parser.error(variable + " must identify a regular configuration file")
        command.append("-D" + property_name + "=" + str(path))
    # The native-file suites exercise real trusted ancestry under an owned document root.
    document_root = os.environ.get("CDS_EXPORT_TEST_DOCUMENT_ROOT")
    if not document_root:
        parser.error("CDS_EXPORT_TEST_DOCUMENT_ROOT must identify an owned empty directory")
    root = Path(document_root).resolve(strict=True)
    if not root.is_dir() or any(root.iterdir()):
        parser.error("CDS_EXPORT_TEST_DOCUMENT_ROOT must identify an owned empty directory")
    reports = repo / "target" / "surefire-reports"
    # Clear inherited tag filters: a groups/excludedGroups default must not quietly drop a
    # MariaDB-gated suite from the evidence.
    command.extend(["package", "-Dtest.forkCount=1", "-Dgroups=", "-DexcludedGroups=",
                    "-Dcds.export.test.documentRoot=" + str(root),
                    "-Dtest.reportsDirectory=" + str(reports),
                    "-Dtest=" + ",".join(selectors)])
    tested = subprocess.run(command, cwd=repo)
    # Maven's exit code alone accepts skipped suites; a skipped, missing, rerun or empty suite
    # is not full integration evidence, so every required report is checked.
    with tempfile.TemporaryDirectory() as stripped:
        total, incomplete = native_tier().collect_reports(selectors, reports, Path(stripped))
    if tested.returncode or incomplete:
        print("CDS transfer inventory incomplete: " + ", ".join(incomplete or ["maven failed"]))
        return 1
    print(f"CDS transfer inventory complete: {len(selectors)} suites, {total} tests, no skips.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
