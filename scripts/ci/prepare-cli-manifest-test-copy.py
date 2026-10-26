#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Prepare/verify pinned CLI tests with CARLOS's current standalone data.

The packages version independently. Use the pinned CLI's public export
handler to refresh only its standalone data in a disposable test copy;
the pinned checkout, every test, and all runtime code stay unchanged.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys


MODULE = "carlos_ctl/o19_preflight.py"
BEGIN = b"# === BEGIN GENERATED DATA (generate_manifests.py) ==="
END = b"# === END GENERATED DATA ==="


def git(source, *args):
    return subprocess.check_output(
        ["git", "-C", str(source), *args],
        env=dict(os.environ, GIT_OPTIONAL_LOCKS="0"))


def snapshot(root, paths):
    result = {}
    for name in paths:
        path = root / name
        if path.is_symlink() or not path.is_file():
            raise ValueError("expected regular file: " + str(path))
        result[name] = {"sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                        "mode": stat.S_IMODE(path.stat().st_mode)}
    return result


def outside_block(content):
    if content.count(BEGIN) != 1 or content.count(END) != 1:
        raise ValueError("standalone data markers must each occur once")
    begin, end = content.index(BEGIN), content.index(END) + len(END)
    if end <= begin:
        raise ValueError("standalone data markers out of order")
    return content[:begin], content[end:]


def verify(source, destination, carlos, receipt):
    paths = list(receipt["pinnedFiles"])
    assert git(source, "rev-parse", "HEAD").decode().strip() == receipt["pinnedCommit"]
    assert not git(source, "status", "--porcelain")
    assert snapshot(source, paths) == receipt["pinnedFiles"]
    assert snapshot(carlos, receipt["manifests"]) == receipt["manifests"]
    actual = []
    for path in destination.rglob("*"):
        assert not path.is_symlink(), "test copy must not contain symlinks"
        assert path.name not in (".git", "__pycache__") and path.suffix != ".pyc"
        if path.is_file():
            actual.append(path.relative_to(destination).as_posix())
    assert set(actual) == set(paths), "test copy gained or lost files"
    assert snapshot(destination, paths) == receipt["testCopyFiles"]
    assert outside_block((source / MODULE).read_bytes()) == outside_block(
        (destination / MODULE).read_bytes()), "runtime code outside data changed"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("carlos", type=Path)
    args = parser.parse_args()
    source, destination, carlos = (p.resolve() for p in
                                   (args.source, args.destination, args.carlos))
    receipt_path = destination.with_name(destination.name + "-proof.json")
    if args.verify:
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
        verify(source, destination, carlos, receipt)
        print("Pinned runtime code, tests, current data and original checkout verified")
        return

    if destination == source or source in destination.parents or destination in source.parents:
        raise ValueError("test copy must be separate from the pinned checkout")
    assert not git(source, "status", "--porcelain"), "pinned checkout must be clean"
    paths = git(source, "ls-files", "-z").decode().rstrip("\0").split("\0")
    pinned = snapshot(source, paths)
    destination.mkdir(parents=True, exist_ok=False)
    for name in paths:
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / name, target)
    assert snapshot(destination, paths) == pinned

    # cli.main also checks for an installed webapp. This public verb handler
    # supports export without a host or database, as its own CLI tests verify.
    environment = dict(os.environ, PYTHONDONTWRITEBYTECODE="1",
                       PYTHONPATH=str(destination), CARLOS_SRC=str(carlos),
                       CARLOS_CTL_SRC=str(destination),
                       CARLOS_CTL_O19_MANIFEST_DIR=str(
                           carlos / "debian/assets/o19-manifest"))
    subprocess.run(
        [sys.executable, "-c", "from carlos_ctl import o19import; import sys; "
         "raise SystemExit(o19import.cmd_o19_preflight(sys.argv[1:]))",
         "--write-standalone", str(destination / MODULE)],
        cwd=destination, env=environment, check=True)
    # The public exporter emits a 0644 standalone artifact. This copy is a
    # pinned runtime module, so retain its original tracked executable mode.
    export_mode = stat.S_IMODE((destination / MODULE).stat().st_mode)
    os.chmod(destination / MODULE, pinned[MODULE]["mode"])
    exported = snapshot(destination, paths)
    assert {p for p in paths if pinned[p] != exported[p]} <= {MODULE}
    assert exported[MODULE]["mode"] == pinned[MODULE]["mode"]
    manifests = [p.relative_to(carlos).as_posix() for p in sorted(
        (carlos / "debian/assets/o19-manifest").glob("*.json"))]
    receipt = {"pinnedCommit": git(source, "rev-parse", "HEAD").decode().strip(),
               "pinnedTree": git(source, "rev-parse", "HEAD^{tree}").decode().strip(),
               "pinnedFiles": pinned, "testCopyFiles": exported,
               "standaloneExportMode": export_mode,
               "manifests": snapshot(carlos, manifests)}
    verify(source, destination, carlos, receipt)
    with receipt_path.open("x", encoding="utf-8") as output:
        json.dump(receipt, output, indent=2)
        output.write("\n")
    print(json.dumps({"pinnedCommit": receipt["pinnedCommit"],
                      "pinnedFileCount": len(paths),
                      "onlyAllowedDataOverlay": MODULE,
                      "proof": str(receipt_path)}))


if __name__ == "__main__":
    main()
