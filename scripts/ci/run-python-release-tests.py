#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Run unittest with structured outcomes, including subtests and cleanup errors."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest


class StrictResult(unittest.TestResult):
    def __init__(self):
        super().__init__()
        self.cases = []
        self.active = {}
        self.duplicates = False

    def startTest(self, test):
        super().startTest(test)
        identity = test.id()
        self.duplicates |= identity in self.active
        record = {"id": identity, "outcomes": []}
        self.active[identity] = record
        self.cases.append(record)

    def record(self, test, outcome):
        self.active[test.id()]["outcomes"].append(outcome)

    def addSuccess(self, test):
        super().addSuccess(test)
        self.record(test, "PASS")

    def addError(self, test, err):
        super().addError(test, err)
        # setUpClass/module failures use an _ErrorHolder without startTest.
        if test.id() not in self.active:
            self.active[test.id()] = {"id": test.id(), "outcomes": []}
            self.cases.append(self.active[test.id()])
        self.record(test, "ERROR")

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self.record(test, "FAIL")

    def addSkip(self, test, reason):
        super().addSkip(test, reason)
        if test.id() not in self.active:
            self.active[test.id()] = {"id": test.id(), "outcomes": []}
            self.cases.append(self.active[test.id()])
        self.record(test, "SKIP")

    def addExpectedFailure(self, test, err):
        super().addExpectedFailure(test, err)
        self.record(test, "EXPECTED_FAILURE")

    def addUnexpectedSuccess(self, test):
        super().addUnexpectedSuccess(test)
        self.record(test, "UNEXPECTED_PASS")

    def addSubTest(self, test, subtest, err):
        super().addSubTest(test, subtest, err)
        if err is not None:
            self.record(test, "SUBTEST_FAIL")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    select = parser.add_mutually_exclusive_group(required=True)
    select.add_argument("--start", type=Path)
    select.add_argument("--module-file", type=Path)
    parser.add_argument("--pattern", default="test_*.py")
    args = parser.parse_args(argv)
    args.output.unlink(missing_ok=True)
    if args.start:
        suite = unittest.defaultTestLoader.discover(str(args.start), pattern=args.pattern)
    else:
        spec = importlib.util.spec_from_file_location("release_qualification_tests", args.module_file)
        module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = module
        spec.loader.exec_module(module)
        suite = unittest.defaultTestLoader.loadTestsFromModule(module)
    result = StrictResult()
    suite.run(result)
    ready = result.testsRun > 0 and bool(result.cases) and not result.duplicates and all(
        record["outcomes"] == ["PASS"] for record in result.cases)
    record = {"schemaVersion": 1, "tests": result.testsRun, "cases": result.cases,
              "duplicateIdentities": result.duplicates, "ready": ready,
              "runtime": sys.version.split()[0]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as stream:
        os.chmod(args.output, 0o600)
        json.dump(record, stream, indent=2)
        stream.write("\n")
    print(f"Python release tests: {result.testsRun} executed; readiness {'PASS' if ready else 'INCOMPLETE'}")
    return 0 if ready else 1


if __name__ == "__main__":
    raise SystemExit(main())
