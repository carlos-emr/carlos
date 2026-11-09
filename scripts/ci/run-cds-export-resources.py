#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Qualify export byte-layer resources from an exact WAR on a fresh hosted runner.

This is distinct from clinical, query-scaling and installed CLI/browser evidence.
Only synthetic files in this process's newly created private directory are used.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import resource
import shutil
import subprocess
import tempfile
import time
import zipfile

import cds_war_manifest as WAR


def verify_installed(root: Path, manifest: dict) -> int:
    expected = {row["name"]: row for row in manifest["entries"] if row["kind"] == "file"}
    actual = {file.relative_to(root).as_posix(): file for file in root.rglob("*") if file.is_file()}
    if set(actual) != set(expected):
        raise RuntimeError("Installed WAR inventory differs")
    for name, file in actual.items():
        if file.is_symlink() or file.stat().st_size != expected[name]["bytes"]:
            raise RuntimeError("Installed WAR identity differs")
        with file.open("rb") as source:
            digest = hashlib.file_digest(source, "sha256").hexdigest()
        if digest != expected[name]["sha256"]:
            raise RuntimeError("Installed WAR bytes differ")
    return len(actual)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", choices=("large", "disk"), required=True)
    args = parser.parse_args()
    if os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted" or os.environ.get("RUNNER_OS") != "Linux":
        raise RuntimeError("Fresh GitHub-hosted Linux runner required")
    repo = Path(__file__).resolve().parents[2]
    base = Path(tempfile.mkdtemp(prefix="cds-export-resources-", dir=repo / "target"))
    public = base / "public"
    private = base / "private"
    public.mkdir(mode=0o700)
    private.mkdir(mode=0o700)
    proof = {"protocol": 1, "profile": args.profile, "byteLayerOnly": True, "status": "RUNNING"}
    mounted = False
    primary = None
    fixture = private / "fixture"
    fixture.mkdir(mode=0o700)
    started = time.monotonic()

    def save():
        (public / "ci-proof.json").write_text(json.dumps(proof, sort_keys=True, indent=2))

    save()
    try:
        with (private / "harness.log").open("w") as log:
            def run(command, *, timeout=2700):
                return subprocess.run(command, check=True, stdout=log, stderr=log, timeout=timeout)

            proof["sourceCommit"] = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
            wars = list((repo / "target").glob("carlos-*.war"))
            if len(wars) != 1 or wars[0].is_symlink():
                raise RuntimeError("One actual packaged candidate WAR required")
            war = wars[0]
            proof["warContentManifest"] = WAR.publish_manifest(war, public / "candidate-war-content-manifest.json")
            manifest = json.loads((public / "candidate-war-content-manifest.json").read_text())
            proof["warSha256"] = manifest["warSha256"]
            web = private / "webapp"
            web.mkdir(mode=0o700)
            with zipfile.ZipFile(war) as archive:
                for member in archive.infolist():
                    target = web / member.filename
                    # The bounded manifest has already validated all names and kinds.
                    if not target.resolve().is_relative_to(web.resolve()):
                        raise RuntimeError("Unsafe WAR member")
                    if member.is_dir():
                        target.mkdir(parents=True, exist_ok=True)
                    else:
                        target.parent.mkdir(parents=True, exist_ok=True)
                        with archive.open(member) as source, target.open("xb") as output:
                            shutil.copyfileobj(source, output, 65536)
            proof["installedFilesBefore"] = verify_installed(web, manifest)
            classes = private / "helper"
            classes.mkdir(mode=0o700)
            cp = os.pathsep.join((str(classes), str(web / "WEB-INF/classes"), str(web / "WEB-INF/lib/*")))
            run(["javac", "-J-Xmx256m", "--release", "25", "-cp", cp, "-d", str(classes),
                 str(repo / "scripts/ci/CdsExportResourceQualification.java")], timeout=120)
            java = ["java", "-Xmx256m", "-cp", cp, "CdsExportResourceQualification"]
            save()
            if args.profile == "disk":
                run(["sudo", "mount", "-t", "tmpfs", "-o", f"size=32m,mode=0700,uid={os.getuid()},gid={os.getgid()}",
                     "tmpfs", str(fixture)], timeout=30)
                mounted = True
            else:
                run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-noenc", "-days", "2",
                     "-subj", "/CN=Synthetic CDS resource recipient", "-addext", "basicConstraints=critical,CA:FALSE",
                     "-addext", "keyUsage=critical,keyEncipherment", "-keyout", str(fixture / "recipient.key"),
                     "-out", str(fixture / "recipient.pem")], timeout=120)
            run(java + [args.profile, str(fixture), str(public / "resource-proof.json")])
            evidence = json.loads((public / "resource-proof.json").read_text())
            if evidence.get("status") != "PASSED" or evidence.get("byteLayerOnly") is not True:
                raise RuntimeError("Resource proof incomplete")
            if args.profile == "large":
                # Both return codes are mandatory. A verifier failure cannot conceal a broken
                # decryptor, and a successful parse cannot conceal failed CMS authentication.
                decrypt_command = ["openssl", "cms", "-decrypt", "-inform", "DER", "-in", str(fixture / "delivery.cms"),
                                   "-recip", str(fixture / "recipient.pem"), "-inkey", str(fixture / "recipient.key")]
                with subprocess.Popen(decrypt_command, stdout=subprocess.PIPE, stderr=log) as decrypt:
                    try:
                        verify = subprocess.run(java + ["verify", str(fixture), str(public / "recipient-proof.json")],
                                                stdin=decrypt.stdout, stdout=log, stderr=log, timeout=2700)
                        decrypt.stdout.close()
                        decrypt_code = decrypt.wait(timeout=60)
                        if verify.returncode != 0 or decrypt_code != 0:
                            raise RuntimeError("Independent recipient verification or CMS authentication failed")
                    finally:
                        if decrypt.poll() is None:
                            decrypt.kill()
                            decrypt.wait(timeout=30)
                recipient = json.loads((public / "recipient-proof.json").read_text())
                if recipient.get("recipientDecryptionVerified") is not True or recipient.get("status") != "PASSED":
                    raise RuntimeError("Recipient evidence incomplete")
                proof["independentRecipientVerified"] = True
            peak_rss = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss * 1024
            proof["largestChildPeakRssBytes"] = peak_rss
            if peak_rss > (1 << 30):
                raise RuntimeError("Resource process exceeded the 1 GiB RSS budget")
            WAR.verify_archive_binding(war, proof["warSha256"])
            proof["installedFilesAfter"] = verify_installed(web, manifest)
            proof["status"] = "PASSED"
            print("PASS exact-WAR export byte-layer resource profile:", args.profile, flush=True)
            return 0
    except Exception as failure:
        primary = failure
        proof["status"] = "FAILED"
        proof["failureClass"] = type(failure).__name__
        raise
    finally:
        try:
            if mounted:
                subprocess.run(["sudo", "umount", str(fixture)], check=True, timeout=30)
            shutil.rmtree(private)
        except Exception as cleanup:
            proof["status"] = "FAILED"
            proof["cleanupFailureClass"] = type(cleanup).__name__
            if primary is None:
                raise
            primary.add_note("Owned resource-fixture cleanup also failed: " + type(cleanup).__name__)
        finally:
            proof["elapsedSeconds"] = round(time.monotonic() - started, 3)
            save()


if __name__ == "__main__":
    raise SystemExit(main())
