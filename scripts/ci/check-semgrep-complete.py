#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Refuse incomplete Semgrep SARIF without printing source or finding payloads."""
import json
from pathlib import Path
import sys

MAX_BYTES = 64 << 20


def verify(document):
    if not isinstance(document, dict) or document.get("version") != "2.1.0":
        raise ValueError("SEMGREP_EVIDENCE_INVALID")
    runs = document.get("runs")
    if not isinstance(runs, list) or not runs:
        raise ValueError("SEMGREP_EVIDENCE_INVALID")
    for run in runs:
        invocations = run.get("invocations") if isinstance(run, dict) else None
        if not isinstance(invocations, list) or not invocations:
            raise ValueError("SEMGREP_EXECUTION_UNCONFIRMED")
        for invocation in invocations:
            if not isinstance(invocation, dict) or invocation.get("executionSuccessful") is not True:
                raise ValueError("SEMGREP_EXECUTION_INCOMPLETE")
            notifications = invocation.get("toolExecutionNotifications", [])
            if not isinstance(notifications, list):
                raise ValueError("SEMGREP_EVIDENCE_INVALID")
            for notice in notifications:
                if not isinstance(notice, dict) or notice.get("level", "warning") != "note":
                    raise ValueError("SEMGREP_EXECUTION_INCOMPLETE")


def main(arguments):
    if not arguments:
        raise ValueError("SEMGREP_EVIDENCE_REQUIRED")
    for argument in arguments:
        with Path(argument).open("rb") as stream:
            payload = stream.read(MAX_BYTES + 1)
        if len(payload) > MAX_BYTES:
            raise ValueError("SEMGREP_EVIDENCE_OVERSIZED")
        verify(json.loads(payload))
    print("PASS complete Semgrep execution evidence")


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (OSError, ValueError):
        print("SEMGREP_EXECUTION_EVIDENCE_REFUSED", file=sys.stderr)
        sys.exit(1)
