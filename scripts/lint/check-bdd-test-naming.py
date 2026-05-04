#!/usr/bin/env python3
"""Validate BDD-style test method names in the billing migration scope."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


TEST_ANNOTATION = re.compile(
    r"@(?:[A-Za-z_][\w]*\.)*(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b"
)
METHOD = re.compile(
    r"\b(?:public|protected|private)?\s*(?:static\s+)?"
    r"(?:void|[\w<>?,\s\[\]]+)\s+(\w+)\s*\("
)
BDD_NAME = re.compile(r"should[A-Z][A-Za-z0-9]*_[a-z][A-Za-z0-9]*")
MINIMUM_SCOPED_TEST_FILES = 100

DEFAULT_ROOTS = (
    "src/test/java/io/github/carlos_emr/carlos/billing/CA",
    "src/test/java/io/github/carlos_emr/carlos/billings/ca",
    "src/test/java/io/github/carlos_emr/carlos/commn/dao",
    "src/test/java/io/github/carlos_emr/carlos/commn/model",
)

SHARED_BILLING_TESTS = {
    "BillingONCHeader1DaoIntegrationTest.java",
    "BillingONEAReportDaoIntegrationTest.java",
    "BillingONExtDaoIntegrationTest.java",
    "BillingONItemDaoIntegrationTest.java",
    "BillingONPaymentDaoImplUnitTest.java",
    "BillingONPaymentDaoIntegrationTest.java",
    "BillingOnItemPaymentDaoImplUnitTest.java",
    "BillingOnTransactionDaoImplUnitTest.java",
    "CtlBillingServiceDaoIntegrationTest.java",
    "DiagnosticCodeDaoIntegrationTest.java",
    "OscarAppointmentDaoQueryIntegrationTest.java",
    "BillingItemsNotLoadedExceptionUnitTest.java",
    "BillingONCHeader1UnitTest.java",
    "BillingONItemEqualsContractUnitTest.java",
    "BillingONItemUnitTest.java",
    "BillingONPaymentContractUnitTest.java",
}


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Fail if scoped billing tests do not use should<Action>_<context><Condition> names."
    )
    parser.add_argument(
        "roots",
        nargs="*",
        default=DEFAULT_ROOTS,
        help="Root directories or Java files to scan. Defaults to the billing migration test scope.",
    )
    args = parser.parse_args()

    violations: list[tuple[Path, int, str]] = []
    scanned_files = 0
    for root_name in args.roots:
        root = Path(root_name)
        if root.is_file():
            if is_java_test_in_scope(root):
                scanned_files += 1
                collect_file_violations(root, violations)
            continue
        if not root.exists():
            continue
        for path in root.rglob("*.java"):
            if is_java_test_in_scope(path):
                scanned_files += 1
                collect_file_violations(path, violations)

    if violations:
        print("BDD test naming violations found.")
        print("Expected: should<Action>_<prepositionOrContext><Condition>")
        print("Rules: exactly one underscore, should prefix, lowercase suffix start.")
        for path, line_no, method_name in violations:
            message = f"{method_name} must match {BDD_NAME.pattern}"
            print(f"::error file={path},line={line_no}::{message}")
            print(f"{path}:{line_no}: {message}")
        return 1

    if scanned_files < MINIMUM_SCOPED_TEST_FILES:
        print(
            "BDD test naming scan covered too few files: "
            f"{scanned_files} found, expected at least {MINIMUM_SCOPED_TEST_FILES}."
        )
        return 1

    print(f"BDD test naming check passed. Scanned {scanned_files} files.")
    return 0


def is_java_test_in_scope(path: Path) -> bool:
    if not path.is_file() or path.suffix != ".java":
        return False
    normalized = path.as_posix()
    if "/commn/dao/" in normalized or "/commn/model/" in normalized:
        return path.name in SHARED_BILLING_TESTS
    return True


def collect_file_violations(path: Path, violations: list[tuple[Path, int, str]]) -> None:
    lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    pending_test_method = False
    for index, line in enumerate(lines, start=1):
        if TEST_ANNOTATION.search(line):
            pending_test_method = True
            continue
        if not pending_test_method:
            continue
        stripped = line.strip()
        if not stripped or stripped.startswith("@") or is_comment_line(stripped):
            continue
        pending_test_method = False
        match = METHOD.search(line)
        if match and not BDD_NAME.fullmatch(match.group(1)):
            violations.append((path, index, match.group(1)))


def is_comment_line(stripped: str) -> bool:
    return (
        stripped.startswith("//")
        or stripped.startswith("/*")
        or stripped.startswith("*")
        or stripped.startswith("*/")
    )


if __name__ == "__main__":
    raise SystemExit(main())
