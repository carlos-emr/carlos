#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Build and copy the single candidate WAR; release_verification binds its source."""
import argparse
from pathlib import Path
import shutil
import subprocess
import os
import tempfile
import signal
import re

def interrupted(_signum, _frame):
    raise KeyboardInterrupt

signal.signal(signal.SIGTERM, interrupted)

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--package-version", required=True)
args = parser.parse_args()
if not re.fullmatch(r"[0-9][0-9A-Za-z.+:~\-]{0,127}", args.package_version):
    raise SystemExit("Package version must use Debian version characters")
if args.output.exists():
    raise SystemExit("Candidate output must not already exist")
args.output.parent.mkdir(parents=True, exist_ok=True)
source = Path(__file__).resolve().parents[2]
revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
environment = {**os.environ, "JOB_NAME": "carlos-emr-deb", "BUILD_NUMBER": args.package_version}
# A fresh local clone cannot retain removed classes/jars from earlier Maven
# runs. It also avoids deleting evidence under target/ with `mvn clean`.
with tempfile.TemporaryDirectory(prefix="candidate-build-", dir=args.output.parent) as temporary:
    checkout = Path(temporary) / "source"
    subprocess.run(["git", "clone", "--quiet", "--shared", "--no-checkout", str(source), str(checkout)], check=True)
    subprocess.run(["git", "checkout", "--quiet", "--detach", revision], cwd=checkout, check=True)
    subprocess.run(["mvn", "-B", "-ntp", "package", "-DskipTests", "-Djacoco.skip=true"],
                   cwd=checkout, env=environment, check=True)
    candidates = list((checkout / "target").glob("*.war"))
    if len(candidates) != 1:
        raise SystemExit("Expected exactly one candidate WAR")
    subprocess.run(["sh", "debian/stamp-war-build-identity.sh", str(candidates[0]),
                    "carlos-emr-deb", args.package_version], cwd=checkout, env=environment, check=True)
    with candidates[0].open("rb") as source_war, args.output.open("xb") as output_war:
        os.chmod(args.output, 0o600)
        shutil.copyfileobj(source_war, output_war)
        output_war.flush()
        os.fsync(output_war.fileno())
