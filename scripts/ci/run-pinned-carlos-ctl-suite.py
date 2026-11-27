#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Test the pinned CLI with current data without changing its source or tests."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile

from carlos_ctl_pin import verify_checkout


ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    source = Path(os.environ["CARLOS_CTL_SRC"]).resolve()
    verify_checkout(source)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="pinned-cli-", dir=output.parent) as temporary:
        copy = Path(temporary) / "source"
        prepare = [sys.executable, str(ROOT / "scripts/ci/prepare-cli-manifest-test-copy.py")]
        subprocess.run([*prepare, str(source), str(copy), str(ROOT)], check=True)
        environment = {**os.environ, "CARLOS_SRC": str(ROOT), "CARLOS_CTL_SRC": str(copy),
                       "PYTHONPATH": str(copy), "PYTHONDONTWRITEBYTECODE": "1"}
        result = subprocess.run([sys.executable, str(ROOT / "scripts/ci/run-python-release-tests.py"),
                                 "--start", "tests", "--top-level", ".", "--output", str(output)],
                                cwd=copy, env=environment)
        # Verify even after a test failure; propagate either failure.
        verified = subprocess.run([*prepare, "--verify", str(source), str(copy), str(ROOT)])
        return result.returncode or verified.returncode


if __name__ == "__main__":
    raise SystemExit(main())
