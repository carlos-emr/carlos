# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Validate declared command assertions and retain every referenced evidence file."""
from pathlib import Path
import re

from junit_evidence import EvidenceError


def relative_file(root, name):
    if not isinstance(name, str) or not name:
        raise EvidenceError("Evidence file has no relative name")
    path = Path(name)
    if path.is_absolute() or any(part in ("..", ".") for part in path.parts):
        raise EvidenceError("Evidence path escapes its run")
    candidate = root / path
    if any(parent.is_symlink() for parent in [candidate, *candidate.parents] if parent != root.parent):
        raise EvidenceError("Evidence path contains a symlink")
    if not candidate.is_file() or not candidate.resolve().is_relative_to(root.resolve()):
        raise EvidenceError("Missing command evidence file")
    return candidate


def validate_contract(contract):
    if not isinstance(contract, dict) or set(contract) != {"file", "assertions"}:
        raise EvidenceError("Command result contract needs file and assertions")
    path = contract["file"]
    if not isinstance(path, str) or not re.fullmatch(r"[a-z][a-z0-9-]*\.json", path):
        raise EvidenceError("Invalid command result filename")
    names = contract["assertions"]
    if not isinstance(names, list) or not names or any(
            not isinstance(name, str) or not re.fullmatch(r"[a-z][a-z0-9-]+", name) for name in names):
        raise EvidenceError("Command result needs named assertions")
    if len(names) != len(set(names)):
        raise EvidenceError("Duplicate command assertion")


def results(contract, directory, read_json, digest):
    validate_contract(contract)
    file = relative_file(directory, contract["file"])
    result = read_json(file)
    if not isinstance(result, dict) or set(result) != {"schemaVersion", "assertions", "inputs"}:
        raise EvidenceError("Malformed command evidence")
    if type(result["schemaVersion"]) is not int or result["schemaVersion"] != 1:
        raise EvidenceError("Wrong command evidence version")
    assertions = result["assertions"]
    if not isinstance(assertions, dict) or set(assertions) != set(contract["assertions"]):
        raise EvidenceError("Command omitted or added assertions")
    if any(type(value) is not bool for value in assertions.values()):
        raise EvidenceError("Command assertion is not boolean")
    inputs = result["inputs"]
    if not isinstance(inputs, list) or not inputs:
        raise EvidenceError("Command result has no underlying inputs")
    names = [contract["file"]]
    for row in inputs:
        if not isinstance(row, dict) or set(row) != {"path", "sha256"}:
            raise EvidenceError("Malformed command input")
        path = relative_file(directory, row["path"])
        if row["path"] in names or digest(path) != row["sha256"]:
            raise EvidenceError("Duplicate or changed command input")
        names.append(row["path"])
    return {"ready": all(assertions.values()), "assertions": assertions,
            "resultSha256": digest(file)}, names
