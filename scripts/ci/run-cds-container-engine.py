#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Opt-in real CDS container launcher gate on a confined synthetic database.

Run inside the prepared UID10001 runtime, with root-issued delegation and
read-only production configuration/source mounts. No clinical payload is printed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import struct
import subprocess
import time
import uuid

MAIN = "io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsCliEngineFixture"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--disposable", action="store_true")
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--source-namespace", required=True)
    parser.add_argument("--source-profile", required=True)
    parser.add_argument("--provider", required=True)
    parser.add_argument("--jdbc-properties", type=Path, required=True)
    parser.add_argument("--classpath", required=True, help="confined fixture helper plus actual candidate dependencies")
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--launcher", default="/usr/local/bin/carlos-cds-import")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    if not args.disposable or os.geteuid() != 0 or not Path("/.dockerenv").is_file():
        parser.error("requires root in an explicitly disposable container")
    if not args.input.is_absolute() or args.input.is_symlink() or not args.input.is_file():
        parser.error("requires the explicit synthetic source file")
    for path in (args.input, Path("/run/carlos-config/carlos.properties")):
        if not os.statvfs(path).f_flag & os.ST_RDONLY:
            parser.error("source and production configuration must be mounted read-only")
    if args.evidence.exists() or not args.evidence.is_absolute():
        parser.error("evidence directory must be absent and absolute")
    if not args.jdbc_properties.is_file() or args.jdbc_properties.is_symlink() or args.jdbc_properties.stat().st_mode & 0o077:
        parser.error("requires protected synthetic JDBC properties")
    args.evidence.mkdir(mode=0o700)
    environment = {key: value for key, value in os.environ.items()
                   if key not in {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH", "CATALINA_OPTS"}}
    drop = ["setpriv", "--reuid=10001", "--regid=10001", "--clear-groups", "--bounding-set=-all",
            "--inh-caps=-all", "--ambient-caps=-all", "--no-new-privs"]

    def stop_group(child: subprocess.Popen, number: int) -> None:
        try:
            os.killpg(child.pid, number)
        except ProcessLookupError:
            return
        except PermissionError:
            # The test supervisor retains no CAP_KILL. Signal the demoted
            # worker as that same service UID without granting Java capabilities.
            stopped = subprocess.run([*drop, "python3", "-c", "import os,signal,sys; os.killpg(int(sys.argv[1]),int(sys.argv[2]))",
                                      str(child.pid), str(number)], env=environment,
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10, check=False)
            if stopped.returncode and child.poll() is None:
                raise RuntimeError("container worker termination could not be confirmed")

    def execute(label: str, command: list[str], *, payload: str | None = None) -> tuple[int, list[dict]]:
        log = args.evidence / (label + ".log")
        descriptor = os.open(log, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "wb") as output:
            child = subprocess.Popen(command, env=environment, stdin=subprocess.PIPE if payload else subprocess.DEVNULL,
                                     stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                child.communicate(payload.encode() if payload else None, timeout=300)
            except subprocess.TimeoutExpired:
                stop_group(child, signal.SIGTERM)
                try:
                    child.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    stop_group(child, signal.SIGKILL)
                    child.wait(timeout=30)
                raise RuntimeError("container operation timed out; retain accepted evidence and reconcile") from None
        values = []
        with log.open(encoding="utf-8") as stream:
            for _ in range(4096):
                line = stream.readline(65538)
                if not line:
                    break
                if len(line) > 65536 or not line.endswith("\n"):
                    raise RuntimeError("container response exceeded the bounded operational row contract")
                if line.startswith("{"):
                    row = json.loads(line)
                    if not isinstance(row, dict):
                        raise RuntimeError("container response was not an object")
                    if row.get("event") != "PROGRESS":
                        values.append(row)
            else:
                raise RuntimeError("container response exceeded the bounded operational row count")
        return child.returncode, values

    code, values = execute("capabilities", [*drop, args.launcher, "--capabilities"])
    if code or len(values) != 1:
        raise RuntimeError("actual container engine capability probe failed")
    caps = values[0]
    heap = caps.get("cliHeapMiB")
    operations = caps.get("operations")
    if type(caps.get("protocol")) is not int or caps["protocol"] != 1 \
            or type(caps.get("java")) is not int or caps["java"] != 25 or caps.get("submissionLookup") is not True \
            or type(heap) is not int or not 128 <= heap <= 16384 \
            or not isinstance(operations, list) or len(operations) != 5 \
            or not all(isinstance(value, str) for value in operations) \
            or set(operations) != {"import", "status", "resume", "cancel", "report"}:
        raise RuntimeError("actual container engine protocol did not match")
    environment["CARLOS_CDS_HEAP_MIB"] = str(heap)

    def operation(label: str, argv: list[str]) -> tuple[str, dict]:
        nonce = str(uuid.uuid4())
        digest = hashlib.sha256()
        for value in argv:
            encoded = value.encode("utf-8")
            digest.update(struct.pack(">I", len(encoded)))
            digest.update(encoded)
        now = int(time.time())
        ticket = json.dumps({"protocol": 1, "operator": "root", "operatorUid": 0, "provider": args.provider,
                             "operation": argv[1], "argvSha256": digest.hexdigest(), "nonce": nonce,
                             "issuedEpochSeconds": now, "expiresEpochSeconds": now + 300})
        code, values = execute(label, [args.launcher, nonce, *argv], payload=ticket)
        terminal = [value for value in values if value.get("event") == "RESULT"]
        if code != 0 or len(terminal) != 1:
            raise RuntimeError("actual container engine operation failed; inspect its protected evidence")
        result = terminal[0]
        if result.get("cleanComplete") is not True or result.get("clinicalComplete") is not True \
                or result.get("reportsDelivered") is not True or result.get("cleanupComplete") is not True \
                or result.get("pendingUnits") != 0:
            raise RuntimeError("container engine did not prove complete clinical/report/cleanup outcomes")
        return nonce, result

    submission, result = operation("import", ["--operation", "import", "--provider", args.provider,
        "--input", str(args.input), "--source-namespace", args.source_namespace,
        "--source-profile", args.source_profile, "--no-match-providers"])
    run = result["runId"]
    _, status = operation("status", ["--operation", "status", "--provider", args.provider, "--run", run])
    _, recovered = operation("submission-status", ["--operation", "status", "--provider", args.provider,
                                                  "--submission-key", submission])
    if status.get("runId") != run or recovered.get("runId") != run:
        raise RuntimeError("container status did not recover the original accepted run")
    code, counts = execute("native-counts", [args.java, "-Xmx128m", "-cp", args.classpath, MAIN,
                                           "inspect", str(args.jdbc_properties), run])
    if code or len(counts) != 1 or counts[0].get("nativeTargets") != 1 or counts[0].get("nativeNotes") != 1 \
            or counts[0].get("holds") != 0 or counts[0].get("checkpoints") != result.get("expectedUnits") \
            or result.get("committedUnits") != result.get("expectedUnits"):
        raise RuntimeError("container native writes did not match the independent completion receipt")
    print(json.dumps({"containerEngine": "PASS", "runId": run, "clinicalComplete": True,
                      "nativePatients": 1, "nativeNotes": 1, "heapMiB": heap}))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, KeyError):
        raise SystemExit("CDS container-engine gate failed; inspect its protected operational evidence") from None
