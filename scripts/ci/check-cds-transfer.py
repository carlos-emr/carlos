#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Run the complete reviewed CDS import/export suite against owned disposable test databases."""
import argparse
import json
import os
from pathlib import Path
import subprocess


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
    command.extend(["package", "-Dtest.forkCount=1", "-Dtest=" + ",".join(selectors)])
    return subprocess.run(command, cwd=repo).returncode


if __name__ == "__main__":
    raise SystemExit(main())
