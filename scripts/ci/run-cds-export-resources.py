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
import re
import resource
import shutil
import subprocess
import tempfile
import time
import zipfile

import cds_war_manifest as WAR


def require_resource_evidence(profile: str, proof: dict) -> None:
    if profile not in ("large", "disk", "interop"):
        raise ValueError("Unknown resource evidence profile")
    if proof.get("status") != "PASSED" or proof.get("byteLayerOnly") is not True:
        raise RuntimeError("Resource proof incomplete")
    heap = proof.get("maximumHeapBytes")
    if type(heap) is not int or not 0 < heap <= (300 << 20):
        raise RuntimeError("Bounded heap evidence absent")
    if profile == "disk":
        if not all(proof.get(key) is True for key in ("reserveRefused", "physicalEnospcRefused", "postFailureRecoveryVerified")):
            raise RuntimeError("Physical failure or recovery evidence absent")
        if (type(proof.get("partialStageFiles")) is not int or proof["partialStageFiles"] != 0
                or type(proof.get("filesystemBytes")) is not int or proof["filesystemBytes"] != (32 << 20)):
            raise RuntimeError("Physical filesystem or cleanup evidence differs")
        return
    sizes = ("members", "memberBytes", "aggregateBytes", "zipBytes", "ciphertextBytes", "zip64EntriesVerified", "independentZipReaders")
    if not all(type(proof.get(key)) is int for key in sizes):
        raise RuntimeError("Archive byte/count evidence absent")
    minimum = 4 << 30 if profile == "large" else 1 << 20
    if (proof["members"] != 5 or proof["memberBytes"] <= minimum
            or proof["aggregateBytes"] != proof["members"] * proof["memberBytes"]
            or proof["zipBytes"] < proof["aggregateBytes"] or proof["ciphertextBytes"] < proof["zipBytes"]
            or proof["independentZipReaders"] < 2
            or (profile == "large" and proof["zip64EntriesVerified"] <= 65535)):
        raise RuntimeError("Required archive size or ZIP64 boundary was not exercised")
    if profile == "interop" and (proof["memberBytes"] > (2 << 20) or proof["ciphertextBytes"] > (16 << 20)):
        raise RuntimeError("OpenSSL interoperability fixture is not bounded")


def limit_openssl_memory() -> None:
    resource.setrlimit(resource.RLIMIT_AS, (1 << 30, 1 << 30))


def verify_openssl_recipient(java: list[str], fixture: Path, destination: Path, log) -> None:
    command = ["openssl", "cms", "-decrypt", "-inform", "DER", "-in", str(fixture / "delivery.cms"),
               "-recip", str(fixture / "recipient.pem"), "-inkey", str(fixture / "recipient.key")]
    # OpenSSL's CMS document decoder is used for the bounded interoperability fixture.
    # The multi-GiB case uses a separate streaming recipient process below.
    with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=log, preexec_fn=limit_openssl_memory) as decrypt:
        try:
            verify = subprocess.run(java + ["verify", str(fixture), str(destination)],
                                    stdin=decrypt.stdout, stdout=log, stderr=log, timeout=120)
            decrypt.stdout.close()
            decrypt_code = decrypt.wait(timeout=60)
            if verify.returncode != 0 or decrypt_code != 0:
                raise RuntimeError("Independent recipient verification or CMS authentication failed")
        finally:
            if decrypt.poll() is None:
                decrypt.kill()
                decrypt.wait(timeout=30)


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
    base = Path(tempfile.mkdtemp(prefix="cds-export-resources-", dir=os.environ["RUNNER_TEMP"]))
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
                interop = private / "interop"
                interop.mkdir(mode=0o700)
                for name in ("recipient.key", "recipient.pem"):
                    shutil.copy2(fixture / name, interop / name)
                run(java + ["interop", str(interop), str(public / "interop-proof.json")], timeout=120)
                require_resource_evidence("interop", json.loads((public / "interop-proof.json").read_text()))
                verify_openssl_recipient(java, interop, public / "interop-recipient-proof.json", log)
                proof["openSslInteroperabilityVerified"] = True
            run(java + [args.profile, str(fixture), str(public / "resource-proof.json")])
            evidence = json.loads((public / "resource-proof.json").read_text())
            require_resource_evidence(args.profile, evidence)
            if args.profile == "large":
                run(java + ["decrypt", str(fixture), str(public / "recipient-proof.json")])
                recipient = json.loads((public / "recipient-proof.json").read_text())
                if (recipient.get("recipientDecryptionVerified") is not True or recipient.get("status") != "PASSED"
                        or recipient.get("streamingCmsParser") is not True
                        or recipient.get("aggregateBytes") != evidence["aggregateBytes"]):
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
        log_path = private / "harness.log"
        if log_path.is_file():
            with log_path.open("rb") as source:
                source.seek(max(0, log_path.stat().st_size - 65536))
                tail = source.read(65536).decode("utf-8", "replace")
            proof["javaFailureCodes"] = sorted(set(re.findall(r"(?:IllegalStateException|IOException): ([A-Z][A-Z0-9_]{4,80})\b", tail)))[:16]
            proof["javaFailureLocations"] = sorted(set(re.findall(r"CdsExport[A-Za-z]+\.java:[0-9]+", tail)))[:16]
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
