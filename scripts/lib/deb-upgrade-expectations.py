#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Derive pending Flyway versions from the target package, never post-upgrade SQL."""
import pathlib
import re
import sys


def version(value):
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)*", value):
        raise ValueError("Unsupported Flyway version")
    parts = [int(part) for part in value.split(".")]
    while len(parts) > 1 and parts[-1] == 0:
        parts.pop()
    return tuple(parts)


def expectations(before, migration_root, province):
    if province.lower() not in ("on", "bc"):
        raise ValueError("Expected an ON or BC package migration inventory")
    if not before or len({version(item) for item in before}) != len(before):
        raise ValueError("Missing or duplicate pre-upgrade Flyway versions")
    latest = max(version(item) for item in before)
    inventory = {}
    for location in ("common", province.lower()):
        directory = pathlib.Path(migration_root) / location
        if not directory.is_dir():
            raise ValueError("Target package migration location is missing")
        for file in directory.glob("V*__*.sql"):
            match = re.fullmatch(r"V([0-9]+(?:[._][0-9]+)*)__.+\.sql", file.name)
            if not match or not file.is_file() or file.is_symlink():
                raise ValueError("Invalid target package migration")
            value = match[1].replace("_", ".")
            key = version(value)
            if key in inventory:
                raise ValueError("Duplicate target package migration version")
            inventory[key] = value
    if not inventory:
        raise ValueError("Target package migration inventory is empty")
    # The packaged runner has outOfOrder=false. Older versions already below
    # an explicit legacy baseline are intentionally not counted as pending.
    pending = [inventory[key] for key in sorted(inventory) if key > latest]
    return len(before) + len(pending), pending


if __name__ == "__main__":
    try:
        snapshot, root, province = sys.argv[1:]
        lines = pathlib.Path(snapshot).read_text().splitlines()
        values = [line.split("=", 1)[1] for line in lines if line.startswith("flyway.versions=")]
        if len(values) != 1:
            raise ValueError("Pre-upgrade snapshot has no unique migration inventory")
        count, pending = expectations(values[0].split(","), root, province)
        print(count)
        print("versions=" + " ".join(pending))
    except (OSError, ValueError) as error:
        print("Upgrade expectations refused: " + str(error), file=sys.stderr)
        sys.exit(2)
