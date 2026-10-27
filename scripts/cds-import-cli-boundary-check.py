#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Explicit root/service-user proof against the actual CARLOS Java CLI main.

Run only in a disposable build container with no production properties present.
The real capability/configuration/delegation path runs; import stays disabled,
so no Spring, database connection or clinical write is permitted by this gate.
Paired clinical acceptance/recovery tests remain separate required gates.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pwd
import stat
import struct
import subprocess
import time
import uuid
from pathlib import Path


def digest(arguments: list[str]) -> str:
    sha = hashlib.sha256()
    for argument in arguments:
        value = argument.encode("utf-8")
        sha.update(struct.pack(">I", len(value)))
        sha.update(value)
    return sha.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True)
    parser.add_argument("--java", default="/usr/bin/java")
    parser.add_argument("--disposable", action="store_true")
    arguments = parser.parse_args()
    if os.geteuid() != 0 or not arguments.disposable or not Path("/.dockerenv").exists():
        parser.error("requires root in an explicitly disposable Docker build container")
    account = pwd.getpwnam("carlos")
    if account.pw_uid == 0:
        raise RuntimeError("service identity must be non-root")
    properties = Path("/etc/carlos-emr/carlos.properties")
    if properties.exists() or properties.is_symlink():
        raise RuntimeError("refusing to replace any existing production configuration")
    parent_created = not properties.parent.exists()
    if parent_created:
        properties.parent.mkdir(mode=0o750)
        os.chown(properties.parent, 0, account.pw_gid)
    delegation_root = Path("/run/carlos-cds-delegations")
    delegation_created = not delegation_root.exists()
    if delegation_created:
        delegation_root.mkdir(mode=0o750)
        os.chown(delegation_root, 0, account.pw_gid)
    metadata = delegation_root.stat()
    if metadata.st_uid != 0 or metadata.st_gid != account.pw_gid or stat.S_IMODE(metadata.st_mode) != 0o750:
        raise RuntimeError("existing delegation directory does not satisfy the production contract")
    nonce = str(uuid.uuid4())
    ticket = delegation_root / (nonce + ".json")
    source_arguments = ["--operation", "import", "--provider", "999998", "--input", "/synthetic/é.xml",
                        "--source-namespace", "synthetic-source", "--source-profile", "pcon-2024-02"]
    issued = int(time.time())
    proof = {"protocol": 1, "operator": "root", "operatorUid": 0, "provider": "999998", "operation": "import",
             "argvSha256": digest(source_arguments), "nonce": nonce,
             "issuedEpochSeconds": issued, "expiresEpochSeconds": issued + 300}
    environment = {key: value for key, value in os.environ.items()
                   if key not in {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH"}}
    environment["CARLOS_CDS_PROPERTIES"] = str(properties)
    command = [arguments.java, "-Xmx128m", "-cp", arguments.classpath,
               "io.github.carlos_emr.carlos.demographic.cdsimport.CdsImport"]

    def invoke(argv: list[str], expected: int, *, delegated: bool = False, as_root: bool = False) -> str:
        selected = dict(environment)
        if delegated:
            selected["CARLOS_CDS_DELEGATION_TICKET"] = str(ticket)
        else:
            selected.pop("CARLOS_CDS_DELEGATION_TICKET", None)
        prefix = [] if as_root else ["runuser", "-u", "carlos", "--"]
        result = subprocess.run([*prefix, *command, *argv], env=selected, text=True,
                                capture_output=True, check=False, timeout=30, cwd="/")
        if result.returncode != expected:
            raise RuntimeError("actual Java CLI boundary returned an unexpected exit: " + str(result.returncode)
                               + "; safe diagnostic=" + result.stderr[:500])
        if "jdbc:" in result.stdout + result.stderr or "/synthetic/" in result.stdout + result.stderr:
            raise RuntimeError("runtime configuration/source detail leaked into console output")
        if argv != ["--capabilities"] and result.stdout:
            raise RuntimeError("refused pre-bootstrap operation emitted unexpected stdout")
        return result.stdout

    try:
        descriptor = os.open(properties, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o640)
        with os.fdopen(descriptor, "w") as stream:
            stream.write("cds.import.cliHeapMiB:7\\\n  68\ncds.import.enabled=false\n"
                         "cds.import.operator.root.providers=999998\n"
                         "db_uri=jdbc:mariadb://127.0.0.1:1/\ndb_name=must-not-connect\n")
        os.chown(properties, 0, account.pw_gid)
        os.chmod(properties, 0o640)
        descriptor = os.open(ticket, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o640)
        with os.fdopen(descriptor, "w") as stream:
            json.dump(proof, stream, separators=(",", ":"))
        os.chown(ticket, 0, account.pw_gid)
        os.chmod(ticket, 0o640)
        caps = json.loads(invoke(["--capabilities"], 0))
        if caps != {"protocol": 1, "java": 25, "cliHeapMiB": 768,
                    "operations": ["import", "status", "resume", "cancel", "report"]}:
            raise RuntimeError("actual Java capability contract did not match")
        invoke(source_arguments, 3)
        # Valid root proof reaches explicit disabled refusal before Spring/DB.
        invoke(source_arguments, 4, delegated=True)
        invoke([*source_arguments, "--dry-run"], 3, delegated=True)
        invoke(source_arguments, 3, delegated=True, as_root=True)
        os.chmod(ticket, 0o660)
        invoke(source_arguments, 3, delegated=True)
        os.chmod(ticket, 0o640)
        os.chown(ticket, account.pw_uid, account.pw_gid)
        invoke(source_arguments, 3, delegated=True)
        os.chown(ticket, 0, account.pw_gid)
        proof["expiresEpochSeconds"] = issued - 1
        ticket.write_text(json.dumps(proof, separators=(",", ":")))
        invoke(source_arguments, 3, delegated=True)
        os.chmod(properties, 0o644)
        invoke(["--capabilities"], 2)
        print("Actual CARLOS Java CLI boundary verified: protocol1, Java25, Properties semantics, "
              "no-DB capabilities, root-owned service-readable delegation, UID/mode/expiry/digest refusal")
    finally:
        # These paths were absent before this explicit disposable-only test.
        ticket.unlink(missing_ok=True)
        properties.unlink(missing_ok=True)
        if delegation_created:
            delegation_root.rmdir()
        if parent_created:
            properties.parent.rmdir()


if __name__ == "__main__":
    main()
