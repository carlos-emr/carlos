#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Read test identities/outcomes without publishing configuration or test output."""
from dataclasses import dataclass
import json
import os
from pathlib import Path
import stat
import re

import defusedxml.ElementTree as ET
from evidence_errors import EvidenceError


@dataclass(frozen=True)
class Case:
    classname: str
    name: str
    outcome: str
    container: str = ""

    @property
    def identity(self):
        return self.classname, self.container, self.name


FAILURE_TAGS = frozenset({"failure", "error", "flakyFailure", "flakyError",
                          "rerunFailure", "rerunError"})


def display_literal(literal):
    """Normalize Java-only escapes before decoding a quoted annotation value."""
    def normalize(match):
        token = match[1]
        if token == "'":
            return "'"
        if token == "s":
            return " "
        if token.startswith("u"):
            return "\\u" + token.lstrip("u")
        if token[0] in "01234567":
            return json.dumps(chr(int(token, 8)))[1:-1]
        return match[0]  # Preserve escaped backslashes; \\s is literal text.

    normalized = re.sub(r"\\(\\|u+[0-9A-Fa-f]{4}|[0-3][0-7]{0,2}|[4-7][0-7]?|'|s)", normalize, literal)
    try:
        return json.loads(normalized)
    except ValueError as failure:
        raise EvidenceError("Unsupported escape in test display label") from failure


def read_surefire(path: Path, expected_suite: str, suffix: str = "", diagnostics=None) -> list[Case]:
    """Retain first failures even when Surefire's final counters report success."""
    try:
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
        with os.fdopen(descriptor, "rb") as stream:
            info = os.fstat(stream.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_size > 64 * 1024 * 1024:
                raise EvidenceError("Test report is not a bounded regular file")
            root = ET.parse(stream, forbid_dtd=True).getroot()
    except (OSError, ET.ParseError) as failure:
        raise EvidenceError("Cannot read test report") from failure
    if root.tag != "testsuite":
        raise EvidenceError("Expected one Surefire testsuite")

    def unsuffix(value: str) -> str:
        if suffix:
            for ending in ("-" + suffix, "(" + suffix + ")"):
                if value.endswith(ending):
                    return value[:-len(ending)]
        return value

    suite = unsuffix(root.get("name", ""))
    if suite != expected_suite:
        raise EvidenceError("Report filename and suite identity disagree")
    cases = []
    identities = set()
    for element in root.iter("testcase"):
        classname = unsuffix(element.get("classname", ""))
        name = element.get("name", "")
        container = ""
        if not classname or not name or any(ord(c) < 32 for c in classname + name):
            raise EvidenceError("Test case has no usable identity")
        if classname != suite and not classname.startswith(suite + "$"):
            if re.fullmatch(r"[^\W\d][\w$]*(?:\.[^\W\d][\w$]*)+", classname):
                # Some real @DisplayName labels look exactly like binary names
                # (MSPReconcile.updateStat). Accept only labels declared in the
                # owning source, rather than guessing from package casing.
                source_root = Path(__file__).resolve().parents[2] / "src/test/java"
                source = source_root.joinpath(*suite.split("$", 1)[0].split(".")).with_suffix(".java")
                labels = set()
                if source.resolve().is_relative_to(source_root) and source.is_file():
                    for literal in re.findall(r'@(?:org\.junit\.jupiter\.api\.)?DisplayName\s*\(\s*("(?:[^"\\]|\\.)*")\s*\)',
                                              source.read_text(encoding="utf-8")):
                        labels.add(display_literal(literal))
                if classname not in labels:
                    raise EvidenceError("Test case belongs to another suite")
            # Surefire/JUnit 6 puts @DisplayName text in testcase@classname,
            # while testsuite@name and the filename retain the binary class.
            # Bind those human labels to the verified owning suite; they are
            # not Java discovery identities and cannot supply another family.
            container, classname = classname, suite
        identity = (classname, container, name)
        if identity in identities:
            raise EvidenceError("Duplicate test case identity")
        identities.add(identity)
        tags = {child.tag for child in element}
        outcome = "failure" if tags & FAILURE_TAGS else "skipped" if "skipped" in tags else "pass"
        cases.append(Case(classname, name, outcome, container))
    for field in ("tests", "failures", "errors", "skipped"):
        value = root.get(field, "")
        if not value.isascii() or not value.isdecimal() or len(value) > 9:
            raise EvidenceError("Missing or invalid test suite counter")
    declared = int(root.get("tests"))
    if declared != len(cases):
        # Surefire 3.5.6/JUnit 6 collapses equal method names in different
        # nested display-name containers in the header, while retaining all
        # testcase elements. Accept only that exact, observable collision;
        # count and inspect every distinct container/case, including failures.
        by_name = {}
        for case in cases:
            by_name.setdefault(case.name, []).append(case)
        collision = declared == len(by_name) and declared < len(cases) and all(
            len(group) == 1 or all(case.container for case in group) for group in by_name.values())
        if not collision:
            raise EvidenceError("Suite counter does not match executed test cases")
        if diagnostics is not None:
            diagnostics.append({"suite": suite, "declaredTests": declared, "executedCases": len(cases),
                                "reason": "Surefire nested-container method-name collision"})
    if int(root.get("failures")) + int(root.get("errors")) and not any(
            case.outcome == "failure" for case in cases):
        raise EvidenceError("Suite failure counter has no corresponding test case")
    if int(root.get("skipped")) != sum(case.outcome == "skipped" for case in cases):
        raise EvidenceError("Suite skip counter disagrees with its test cases")
    return cases
