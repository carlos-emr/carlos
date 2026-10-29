#!/usr/bin/env python3
#
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
"""Opt-in, one-submission synthetic CDS container replacement recovery proof.

The default is read-only preflight. Execution additionally requires an externally
released preflight fingerprint. This controller never prepares a source, changes
configuration, writes SQL, raises limits, or retries an uncertain submission.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import http.client
import json
import os
from pathlib import Path, PurePosixPath
import re
import socket
import struct
import subprocess
import sys
import tarfile
import threading
import time
from urllib.parse import quote, urlencode
import uuid

MIB = 1024 * 1024
FIXTURE = "io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsCliEngineFixture"
SOURCE = "/var/lib/carlos-emr/cds-import/source"
STATE = "/var/lib/carlos-emr/cds-import/state"
CONFIG = "/run/carlos-config"
DOCUMENTS = "/var/lib/CarlosDocument"
CAPS = {"CHOWN", "SETUID", "SETGID", "SETPCAP"}
EVENTS = {"INITIALIZING", "SUBMISSION", "ACCEPTED", "PROGRESS", "RESULT", "PROTECTED_REPORTS"}
BOOL_FIELDS = {"dryRun", "discoveryComplete", "clinicalComplete", "reportsDelivered", "cleanupComplete",
               "cleanComplete", "operationTerminal", "resumeRequested", "maintenanceReleasePending"}
INT_FIELDS = {"discoveredFiles", "patientXmlFiles", "sourceBytes", "sourcePatientsIdentified", "sourcePatients",
              "targetCopiesPlanned", "expectedPatients", "expectedUnits", "plannedUnits", "committedUnits",
              "alreadyCompleteUnits", "pendingUnits", "blockingDiagnostics", "reviewVersion", "pendingReviewCount",
              "inactiveDraftCount"}
ENUM_FIELDS = {"state", "inventoryState", "reportState", "cleanupState", "reviewState", "reviewErrorCode",
               "maintenanceReleaseState"}


def require(condition: bool, code: str) -> None:
    if not condition:
        raise RuntimeError(code)


def digest_json(value: object) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def argv_digest(argv: list[str]) -> str:
    result = hashlib.sha256()
    for item in argv:
        data = item.encode("utf-8")
        result.update(struct.pack(">I", len(data)))
        result.update(data)
    return result.hexdigest()


def clinical_key(*parts: str) -> str:
    result = hashlib.sha256()
    for part in parts:
        data = part.encode("utf-8")
        result.update(str(len(data)).encode("ascii") + b":" + data)
    return result.hexdigest()


def is_uuid(value: object) -> bool:
    return isinstance(value, str) and bool(re.fullmatch(r"[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}", value))


def safe_row(row: object) -> dict | None:
    """Persist only bounded public operational fields; never raw stderr or paths."""
    if not isinstance(row, dict) or row.get("event") not in EVENTS:
        return None
    result = {"event": row["event"]}
    for key in ("runId", "submissionKey"):
        if key in row:
            require(is_uuid(row[key]), "INVALID_OPERATION_HANDLE")
            result[key] = row[key]
    if row.get("operation") in {"import", "status", "resume", "report", "cancel"}:
        result["operation"] = row["operation"]
    for key in BOOL_FIELDS:
        if type(row.get(key)) is bool:
            result[key] = row[key]
    for key in INT_FIELDS:
        if type(row.get(key)) is int and 0 <= row[key] < 2**63:
            result[key] = row[key]
    for key in ENUM_FIELDS:
        if isinstance(row.get(key), str) and re.fullmatch(r"[A-Z][A-Z0-9_]{0,95}", row[key]):
            result[key] = row[key]
    if row["event"] == "PROTECTED_REPORTS":
        reports = row.get("reports")
        require(isinstance(reports, dict) and set(reports) == {"event", "error", "receipt", "units"},
                "REPORT_MANIFEST_INCOMPLETE")
        result["reports"] = {}
        for key, value in reports.items():
            require(isinstance(value, dict) and type(value.get("bytes")) is int and value["bytes"] >= 0
                    and isinstance(value.get("sha256"), str) and re.fullmatch(r"[0-9a-f]{64}", value["sha256"]),
                    "REPORT_MANIFEST_INVALID")
            result["reports"][key] = {"bytes": value["bytes"], "sha256": value["sha256"]}
    return result


def save(directory: Path, name: str, value: object) -> None:
    descriptor = os.open(directory / name, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        json.dump(value, output, sort_keys=True, separators=(",", ":"))
        output.write("\n")
        output.flush()
        os.fsync(output.fileno())
    descriptor = os.open(directory, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


class UnixConnection(http.client.HTTPConnection):
    def __init__(self, path: str, timeout: int = 30):
        super().__init__("localhost", timeout=timeout)
        self.path = path

    def connect(self) -> None:
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect(self.path)


class Docker:
    """Structured create avoids exposing Config.Env through a shell or argv."""
    def __init__(self, path: str):
        self.path = path

    def request(self, method: str, route: str, body: object | None = None,
                statuses: tuple[int, ...] = (200,)) -> object:
        connection = UnixConnection(self.path)
        try:
            data = None if body is None else json.dumps(body, separators=(",", ":")).encode()
            connection.request(method, route, body=data, headers={"Content-Type": "application/json"})
            response = connection.getresponse()
            require(response.status in statuses, "DOCKER_REQUEST_FAILED")
            data = response.read(8 * MIB + 1)
            require(len(data) <= 8 * MIB, "DOCKER_RESPONSE_TOO_LARGE")
            return json.loads(data) if data else {}
        finally:
            connection.close()

    def inspect(self, selector: str) -> dict:
        value = self.request("GET", "/containers/" + quote(selector, safe="") + "/json")
        require(isinstance(value, dict), "DOCKER_INSPECT_INVALID")
        return value

    def accepted(self, container: str, submission: str) -> tuple[dict, str]:
        """Archive reads run outside the target cgroup and never persist bodies."""
        connection = UnixConnection(self.path)
        candidates = []
        try:
            connection.request("GET", "/containers/" + container + "/archive?" + urlencode({"path": STATE}))
            response = connection.getresponse()
            require(response.status == 200, "ACCEPTED_DOCUMENT_UNAVAILABLE")
            total = 0
            with tarfile.open(fileobj=response, mode="r|") as archive:
                for index, member in enumerate(archive):
                    total += member.size
                    require(index < 100000 and total <= 256 * MIB, "STATE_ARCHIVE_TOO_LARGE")
                    if not re.fullmatch(r"job-[a-f0-9]{64}\.json", PurePosixPath(member.name).name):
                        continue
                    require(member.isfile() and member.uid == 10001 and member.mode & 0o077 == 0
                            and member.size <= 65536, "ACCEPTED_DOCUMENT_NOT_PRIVATE")
                    source = archive.extractfile(member)
                    require(source is not None, "ACCEPTED_DOCUMENT_UNREADABLE")
                    data = source.read(65537)
                    digest = hashlib.sha256(data).hexdigest()
                    require(PurePosixPath(member.name).name == "job-" + digest + ".json", "ACCEPTED_DOCUMENT_HASH_MISMATCH")
                    document = json.loads(data)
                    if document.get("command", {}).get("submissionKey") == submission:
                        candidates.append((document, digest))
            require(len(candidates) == 1, "ACCEPTED_DOCUMENT_AMBIGUOUS")
            return candidates[0]
        finally:
            connection.close()


def exec_command(args: argparse.Namespace, container: str, command: list[str], user: str = "0",
                 *, interactive: bool = False, heap: bool = False, clear_java: bool = False) -> list[str]:
    result = [args.docker, "--host=unix://" + args.docker_socket, "exec"]
    if interactive:
        result.append("-i")
    result.extend(["--user", user])
    if heap:
        result.extend(["--env", "CARLOS_CDS_HEAP_MIB=512"])
    if clear_java:
        for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH", "CATALINA_OPTS"):
            result.extend(["--env", key + "="])
    return [*result, container, *command]


def bounded_exec(command: list[str], timeout: int = 60) -> object:
    # A temporary bounded pipe reader is used instead of capture_output so a
    # broken helper cannot retain unlimited diagnostics in controller memory.
    child = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    require(child.stdout is not None, "HELPER_PIPE_UNAVAILABLE")
    data = bytearray()
    failure = []

    def read() -> None:
        try:
            while block := child.stdout.read(4096):
                data.extend(block)
                require(len(data) <= 65536, "HELPER_OUTPUT_TOO_LARGE")
        except Exception:
            failure.append(True)
        finally:
            child.stdout.close()

    reader = threading.Thread(target=read, daemon=True)
    reader.start()
    try:
        code = child.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        # Terminate only the local read-only Docker client. Docker exec itself
        # is never signalled; no clinical operation is run through this helper.
        child.terminate()
        raise RuntimeError("READ_ONLY_HELPER_TIMEOUT") from None
    reader.join(timeout=5)
    require(not reader.is_alive() and not failure and code == 0, "READ_ONLY_HELPER_FAILED")
    return json.loads(data)


# This probe executes only before import and before resume. It streams hashes
# without returning names, source bodies, property values, or process environment.
PROBE = r'''
import hashlib,json,os,pathlib,stat,sys,xml.etree.ElementTree as ET
request=json.loads(sys.argv[1]); result={"mounts":{},"private":{},"acceptedDocuments":0}
def digest(root):
    root=pathlib.Path(root); sha=hashlib.sha256(); files=0; size=0
    paths=[root]
    if root.is_dir(): paths.extend(sorted(root.rglob("*")))
    if len(paths)>250000: raise ValueError()
    for path in paths:
        info=path.lstat(); relative=str(path.relative_to(root))
        if not stat.S_ISLNK(info.st_mode) and info.st_mode & 0o022: raise ValueError()
        record=[relative,stat.S_IFMT(info.st_mode),stat.S_IMODE(info.st_mode),info.st_uid,info.st_gid]
        if path.is_symlink():
            record.append(os.readlink(path))
            target=path.resolve(strict=True); target_info=target.stat()
            if not target.is_file() or target_info.st_mode & 0o022: raise ValueError()
            record.extend([stat.S_IMODE(target_info.st_mode),target_info.st_uid,target_info.st_gid])
        if path.is_file():
            leaf=hashlib.sha256()
            with path.open("rb") as stream:
                while block:=stream.read(65536): leaf.update(block);size+=len(block)
            record.append(leaf.hexdigest());files+=1
        elif not path.is_dir(): raise ValueError()
        data=json.dumps(record,separators=(",",":")).encode();sha.update(len(data).to_bytes(4,"big"));sha.update(data)
    return {"sha256":sha.hexdigest(),"files":files,"bytes":size}
for path in request["immutable"]: result["mounts"][path]=digest(path)
for path in request["private"]:
    info=os.lstat(path)
    if stat.S_ISLNK(info.st_mode): raise ValueError()
    result["private"][path]={"uid":info.st_uid,"gid":info.st_gid,"mode":stat.S_IMODE(info.st_mode)}
if "input" in request:
    source=pathlib.Path(request["input"])
    if not source.is_file() or source.is_symlink() or source.stat().st_size>32*1024*1024: raise ValueError()
    with source.open("rb") as stream:
        tail=b""
        while block:=stream.read(65536):
            probe=tail+block
            if b"<!DOCTYPE" in probe or b"<!ENTITY" in probe: raise ValueError()
            tail=probe[-16:]
    counts={"PatientRecord":0,"Demographics":0,"ClinicalNotes":0}
    for _,node in ET.iterparse(source,events=("end",)):
        tag=node.tag.rsplit("}",1)[-1]
        if tag in counts: counts[tag]+=1
        node.clear()
    result["sourceStructure"]=counts
if "state" in request:
    for path in pathlib.Path(request["state"]).rglob("job-*.json"):
        if path.is_file(): result["acceptedDocuments"]+=1
for name in ("memory.max","memory.swap.max","cpu.max","memory.peak","memory.events"):
    result[name]=pathlib.Path("/sys/fs/cgroup",name).read_text().strip()
print(json.dumps(result,separators=(",",":")))
'''


CGROUP = r'''
import json,pathlib
print(json.dumps({key:pathlib.Path("/sys/fs/cgroup",key).read_text().strip() for key in ("memory.max","memory.swap.max","cpu.max","memory.peak","memory.events")}))
'''


def ownership(info: dict, args: argparse.Namespace, *, metric: bool = False) -> None:
    name = info.get("Name", "").removeprefix("/")
    expected = args.metric_container if metric else args.container
    require(name == expected and bool(re.fullmatch(r"carlos-cds-[a-z0-9][a-z0-9-]{1,79}", name)), "CONTAINER_NAME_NOT_OWNED")
    if not metric:
        key, value = args.owner_label.split("=", 1)
        require(info.get("Config", {}).get("Labels", {}).get(key) == value, "CONTAINER_OWNERSHIP_LABEL_MISMATCH")
    require(info.get("State", {}).get("Running") is True and info["State"].get("Paused") is False,
            "CONTAINER_NOT_RUNNING")
    require(isinstance(info.get("Id"), str) and bool(re.fullmatch(r"[a-f0-9]{64}", info["Id"])), "CONTAINER_ID_INVALID")


def contract(info: dict, args: argparse.Namespace) -> tuple[dict, list[str]]:
    ownership(info, args)
    host = info["HostConfig"]
    config = info["Config"]
    command = [info.get("Path"), *info.get("Args", [])]
    require(command in (["/bin/sleep", "infinity"], ["sleep", "infinity"],
                        ["/bin/sh", "-c", "exec /bin/sleep infinity"], ["/bin/sh", "-c", "exec sleep infinity"]),
            "PID1_NOT_IDLE_SLEEP")
    require(config.get("User", "") in {"", "0", "root", "0:0", "root:root"}, "ROOT_TICKET_HELPER_REQUIRED")
    require(host.get("Memory") == 768 * MIB and host.get("MemorySwap") == 1536 * MIB
            and (host.get("NanoCpus") == 1000000000 or host.get("NanoCpus", 0) == 0
                 and host.get("CpuQuota", 0) > 0 and host["CpuQuota"] == host.get("CpuPeriod")), "CGROUP_BUDGET_MISMATCH")
    require(host.get("ReadonlyRootfs") is True and host.get("Privileged") is False and host.get("AutoRemove") is False
            and set(host.get("CapDrop") or []) == {"ALL"}
            and set(host.get("CapAdd") or []) in (CAPS, {"CAP_" + cap for cap in CAPS})
            and set(host.get("SecurityOpt") or []) in ({"no-new-privileges"}, {"no-new-privileges=true"}), "CONFINEMENT_MISMATCH")
    require(not host.get("Devices") and not host.get("DeviceRequests") and not host.get("PortBindings")
            and not host.get("VolumesFrom") and not host.get("Links") and not host.get("ContainerIDFile")
            and host.get("PidMode", "") == "" and host.get("IpcMode") in {"private", ""}
            and not config.get("Healthcheck") and not config.get("Tty"), "UNSUPPORTED_CONTAINER_FEATURE")
    require(host.get("RestartPolicy", {}).get("Name") in {"", "no"}, "AUTOMATIC_RESTART_NOT_ALLOWED")
    networks = info.get("NetworkSettings", {}).get("Networks", {})
    require(set(networks) == {args.network} and host.get("NetworkMode") == args.network, "NETWORK_CONTRACT_MISMATCH")
    require(not networks[args.network].get("IPAMConfig") and not networks[args.network].get("Links"), "STATIC_NETWORK_NOT_SUPPORTED")
    tmpfs = host.get("Tmpfs") or {}
    require(set(tmpfs) == {"/carlos-tmp", "/run/carlos-cds-delegations"}, "TMPFS_CONTRACT_MISMATCH")
    for path, expected in (("/carlos-tmp", {"uid=10001", "gid=10001", "mode=0700"}),
                           ("/run/carlos-cds-delegations", {"uid=0", "gid=10001", "mode=0750"})):
        options = set(tmpfs[path].split(","))
        # Docker accepts the zero-free mode spelling as equivalent.
        options = {"mode=0" + value[5:] if re.fullmatch(r"mode=[0-7]{3}", value) else value for value in options}
        require(expected <= options, "TMPFS_OWNER_OR_MODE_MISMATCH")
    require(isinstance(info.get("Image"), str) and bool(re.fullmatch(r"sha256:[a-f0-9]{64}", info["Image"])), "IMAGE_NOT_PINNED")
    binds = {}
    for mount in info.get("Mounts", []):
        if mount.get("Type") == "tmpfs":
            continue
        require(mount.get("Type") == "bind" and mount.get("Propagation") in {"rprivate", ""}
                and mount.get("Destination") not in binds, "UNSUPPORTED_MOUNT")
        binds[mount["Destination"]] = {"source": mount["Source"], "rw": mount["RW"]}
    base = str(args.prepared_root)
    # The parent alias must also be read-only: otherwise its config/source
    # children have a writable route despite their dedicated read-only mounts.
    expected = {CONFIG: ("config", False), SOURCE: ("source", False), STATE: ("state", True),
                DOCUMENTS: ("documents", True), "/gate-evidence": ("evidence", True), "/gate-preparation": ("", False)}
    for destination, (leaf, writable) in expected.items():
        require(binds.get(destination) == {"source": base + ("/" + leaf if leaf else ""), "rw": writable}, "PERSISTENT_BIND_MISMATCH")
    immutable = [path for path, mount in binds.items() if not mount["rw"]]
    require(all(not mount["rw"] or path in expected for path, mount in binds.items()), "EXTRA_WRITABLE_BIND")
    required = {CONFIG, SOURCE, args.launcher, "/usr/lib/jvm/java-25-openjdk-amd64",
                "/usr/local/tomcat/webapps/carlos/WEB-INF/classes", "/usr/local/tomcat/webapps/carlos/WEB-INF/lib", "/usr/local/tomcat/lib"}
    require(required <= set(immutable), "REQUIRED_IMMUTABLE_BIND_MISSING")
    # Hash installed entrypoints and every dependency byte through their file
    # links, not unrelated workspace/cache trees. All bind mappings/flags remain
    # included in the runtime contract below, including the RO cache target.
    # The redundant parent alias stays bound and permission-checked, but is not
    # a content root: its state/document/evidence children remain mutable.
    immutable = sorted(required)
    # Preserve all host settings in the digest. Dynamic container IDs and IPs
    # are excluded; Config.Env/labels remain secret in memory and appear as SHA.
    aliases = sorted(alias for alias in networks[args.network].get("Aliases") or []
                     if alias not in {info["Id"], info["Id"][:12]})
    runtime = {"image": info["Image"], "configSha256": digest_json({**config, "Image": info["Image"]}),
               "hostConfigSha256": digest_json(host), "binds": binds,
               "network": {"name": args.network, "id": networks[args.network].get("NetworkID"), "aliases": aliases}}
    return runtime, sorted(immutable)


def probe(args: argparse.Namespace, container: str, immutable: list[str]) -> dict:
    private = ["/gate-preparation", CONFIG, SOURCE, STATE, DOCUMENTS, "/gate-evidence", CONFIG + "/carlos.properties",
               "/carlos-tmp", "/run/carlos-cds-delegations"]
    request = {"immutable": immutable, "private": private, "state": STATE, "input": args.input}
    value = bounded_exec(exec_command(args, container, ["python3", "-c", PROBE, json.dumps(request)]), args.timeout)
    require(isinstance(value, dict) and set(value.get("mounts", {})) == set(immutable), "IMMUTABLE_PROBE_INCOMPLETE")
    permissions = {"/gate-preparation": (0, 0, 0o711), CONFIG: (0, 10001, 0o750), SOURCE: (0, 10001, 0o750),
                   STATE: (10001, 10001, 0o700), DOCUMENTS: (10001, 10001, 0o700), "/gate-evidence": (0, 0, 0o700),
                   CONFIG + "/carlos.properties": (0, 10001, 0o640), "/carlos-tmp": (10001, 10001, 0o700),
                   "/run/carlos-cds-delegations": (0, 10001, 0o750)}
    for path, (uid, gid, mode) in permissions.items():
        require(value.get("private", {}).get(path) == {"uid": uid, "gid": gid, "mode": mode}, "PREPARED_PERMISSIONS_MISMATCH")
    require(value.get("sourceStructure") == {"PatientRecord": 1, "Demographics": 1, "ClinicalNotes": 256},
            "LONG_SOURCE_STRUCTURE_MISMATCH")
    cgroup(value)
    return value


def cgroup(value: dict) -> None:
    require(value.get("memory.max") == str(768 * MIB) and value.get("memory.swap.max") == str(768 * MIB), "ACTUAL_MEMORY_BUDGET_MISMATCH")
    cpu = value.get("cpu.max", "").split()
    require(len(cpu) == 2 and all(item.isdecimal() for item in cpu) and int(cpu[0]) > 0 and cpu[0] == cpu[1], "ACTUAL_CPU_BUDGET_MISMATCH")
    events = dict(line.split() for line in value.get("memory.events", "").splitlines())
    require(events.get("oom") == "0" and events.get("oom_kill") == "0", "CGROUP_OOM_OBSERVED")


def recreate_request(info: dict, args: argparse.Namespace) -> dict:
    body = copy.deepcopy(info["Config"])
    body["Image"] = info["Image"]
    body["HostConfig"] = copy.deepcopy(info["HostConfig"])
    aliases = [alias for alias in info["NetworkSettings"]["Networks"][args.network].get("Aliases") or []
               if alias not in {info["Id"], info["Id"][:12]}]
    body["NetworkingConfig"] = {"EndpointsConfig": {args.network: {"Aliases": aliases}}}
    return body


def metric_probe(args: argparse.Namespace, info: dict) -> dict:
    selected = set()
    paths = [args.metric_java, *[part.removesuffix("/*") for part in args.metric_classpath.split(":")]]
    for path in paths:
        require(path.startswith("/") and ".." not in PurePosixPath(path).parts, "METRIC_CLASSPATH_INVALID")
        candidates = [mount for mount in info.get("Mounts", [])
                      if PurePosixPath(path).is_relative_to(mount["Destination"])]
        require(bool(candidates), "METRIC_ARTIFACT_BIND_MISSING")
        mount = max(candidates, key=lambda value: len(value["Destination"]))
        require(mount.get("Type") == "bind" and mount.get("RW") is False, "METRIC_ARTIFACT_NOT_READ_ONLY")
        selected.add(mount["Destination"])
    selected.add(args.jdbc_properties)
    request = {"immutable": sorted(selected), "private": [args.jdbc_properties]}
    value = bounded_exec(exec_command(args, info["Id"], ["python3", "-c", PROBE, json.dumps(request)]), args.timeout)
    require(isinstance(value, dict) and set(value.get("mounts", {})) == selected
            and value.get("private", {}).get(args.jdbc_properties) in (
                {"uid": 0, "gid": 0, "mode": 0o600},
                {"uid": os.getuid(), "gid": os.getgid(), "mode": 0o600}),
            "METRIC_HELPER_OR_CREDENTIALS_NOT_FROZEN")
    # This independent helper has its own cgroup; its budget is deliberately
    # excluded from the candidate's measured 768MiB container contract.
    return {"mounts": value["mounts"], "private": value["private"]}


def idle(docker: Docker, container: str) -> None:
    value = docker.request("GET", "/containers/" + container + "/top?" + urlencode({"ps_args": "-eo pid,args"}))
    rows = value.get("Processes", []) if isinstance(value, dict) else []
    require(isinstance(value, dict) and value.get("Titles") == ["PID", "COMMAND"] and len(rows) == 1
            and len(rows[0]) == 2 and rows[0][0].isdecimal() and int(rows[0][0]) > 0
            and rows[0][1] in {"/bin/sleep infinity", "sleep infinity"},
            "TARGET_HAS_NON_IDLE_PROCESSES")


class Operation:
    """Continuously drain bounded output, retaining only safe operational JSON."""
    def __init__(self, args: argparse.Namespace, container: str, argv: list[str], label: str):
        self.nonce = str(uuid.uuid4())
        now = int(time.time())
        ticket = {"protocol": 1, "operator": "root", "operatorUid": 0, "provider": args.provider,
                  "operation": argv[1], "argvSha256": argv_digest(argv), "nonce": self.nonce,
                  "issuedEpochSeconds": now, "expiresEpochSeconds": now + 300}
        save(args.evidence, label + "-request.json", {"operation": argv[1], "nonce": self.nonce,
             "argvSha256": ticket["argvSha256"], "containerId": container})
        self.rows: list[dict] = []
        self.failure = False
        self.oom = False
        self.lock = threading.Lock()
        self.output = os.fdopen(os.open(args.evidence / (label + ".jsonl"), os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "w")
        self.child = subprocess.Popen(exec_command(args, container, [args.launcher, self.nonce, *argv], interactive=True, heap=True),
                                      stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        assert self.child.stdin is not None and self.child.stdout is not None
        self.child.stdin.write(json.dumps(ticket).encode())
        self.child.stdin.close()
        self.reader = threading.Thread(target=self.read, daemon=True)
        self.reader.start()

    def read(self) -> None:
        total = 0
        count = 0
        try:
            assert self.child.stdout is not None
            while True:
                line = self.child.stdout.readline(65538)
                if not line:
                    break
                total += len(line)
                count += 1
                require(len(line) <= 65536 and total <= 16 * MIB and count <= 16384, "OPERATION_OUTPUT_LIMIT")
                if any(marker in line for marker in (b"OutOfMemoryError", b"Cannot allocate memory", b"Native memory allocation")):
                    self.oom = True
                if not line.endswith(b"\n") or not line.startswith(b"{"):
                    continue
                row = safe_row(json.loads(line))
                if row is not None:
                    with self.lock:
                        self.rows.append(row)
                        require(len(self.rows) <= 4096, "OPERATION_ROW_LIMIT")
                    self.output.write(json.dumps(row, separators=(",", ":")) + "\n")
                    self.output.flush()
                    if row["event"] in {"SUBMISSION", "ACCEPTED", "RESULT"}:
                        os.fsync(self.output.fileno())
        except Exception:
            self.failure = True
        finally:
            self.output.close()
            if self.child.stdout is not None:
                self.child.stdout.close()

    def values(self) -> list[dict]:
        require(not self.oom, "OPERATION_OOM_RETAIN_ACCEPTED_STATE")
        require(not self.failure, "OPERATION_OUTPUT_UNCERTAIN")
        with self.lock:
            return list(self.rows)

    def finish(self, timeout: int, *, killed: bool = False) -> list[dict]:
        try:
            code = self.child.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            raise RuntimeError("OPERATION_TIMEOUT_RETAIN_ACCEPTED_STATE") from None
        self.reader.join(timeout=10)
        require(not self.reader.is_alive(), "OPERATION_CONSOLE_UNCERTAIN")
        values = self.values()
        if killed:
            require(code != 0 and not any(row.get("event") == "RESULT" for row in values), "KILLED_OPERATION_TERMINAL_RESULT")
        else:
            require(code == 0, "OPERATION_FAILED_RETAIN_ACCEPTED_STATE")
        return values


def result(rows: list[dict], run: str) -> dict:
    values = [row for row in rows if row.get("event") == "RESULT"]
    require(len(values) == 1 and values[0].get("runId") == run, "TERMINAL_HANDLE_MISMATCH")
    return values[0]


def complete(value: dict) -> None:
    require(all(value.get(key) is True for key in ("clinicalComplete", "cleanComplete", "reportsDelivered", "cleanupComplete"))
            and value.get("resumeRequested") is False and value.get("maintenanceReleasePending") is False
            and value.get("maintenanceReleaseState") == "RELEASED" and value.get("pendingUnits") == 0
            and value.get("expectedUnits") == 514 and value.get("committedUnits") == 514
            and value.get("dryRun") is False, "RECOVERY_NOT_CLINICALLY_COMPLETE")


def accepted_binding(document: dict, digest: str, args: argparse.Namespace, submission: str) -> dict:
    expected = {"input": Path(args.input).as_uri(), "submissionKey": submission, "providerNo": args.provider,
                "programId": None, "facilityId": None, "sourceNamespace": args.source_namespace,
                "sourceProfile": args.source_profile, "mergeExisting": False, "dryRun": False,
                "matchProviderNames": False, "timeshiftDays": 0, "courseId": None, "synthetic": False}
    require(document.get("command") == expected, "ACCEPTED_OPTIONS_MISMATCH")
    actor = document.get("actor", {})
    require(actor.get("operator") == "root" and actor.get("providerNo") == args.provider and actor.get("securityNo") is None,
            "ACCEPTED_ACTOR_MISMATCH")
    snapshot = document.get("snapshot", {})
    require(isinstance(snapshot, dict) and bool(re.fullmatch(r"[a-f0-9]{64}", snapshot.get("sha256", "")))
            and bool(re.fullmatch(r"[a-f0-9]{64}", document.get("acceptedRulesetDigest", ""))), "ACCEPTED_SOURCE_BINDING_MISSING")
    return {"jobDocumentSha256": digest, "sourceSnapshotSha256": snapshot["sha256"],
            "commandSha256": digest_json(expected), "acceptedRulesetSha256": document["acceptedRulesetDigest"]}


def metrics(args: argparse.Namespace, docker: Docker, selector: str) -> dict:
    info = docker.inspect(args.metric_id)
    ownership(info, args, metric=True)
    require(info["Id"] == args.metric_id and info["Id"] != args.target_id and digest_json(info["Config"]) == args.metric_config
            and digest_json(info["HostConfig"]) == args.metric_host,
            "EXTERNAL_METRIC_CONTAINER_CHANGED")
    value = bounded_exec(exec_command(args, args.metric_id, [args.metric_java, "-Xmx128m", "-cp", args.metric_classpath,
                         FIXTURE, "inspect", args.jdbc_properties, selector], clear_java=True), args.timeout)
    require(isinstance(value, dict) and all(re.fullmatch(r"[A-Za-z][A-Za-z0-9]{0,79}", key)
            and (type(item) is bool or type(item) is int and 0 <= item < 2**63) for key, item in value.items()), "NATIVE_METRICS_INVALID")
    return value


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--disposable", action="store_true", required=True)
    parser.add_argument("--execute", action="store_true", help="submit once and replace the exact owned container")
    parser.add_argument("--released-fingerprint", help="externally approved exact preflight SHA256; required with --execute")
    parser.add_argument("--container", required=True)
    parser.add_argument("--owner-label", required=True, help="io.carlos.cds.KEY=TASK_VALUE on the disposable target")
    parser.add_argument("--prepared-root", type=Path, required=True, help="Docker-daemon host path, never modified")
    parser.add_argument("--input", required=True, help="already prepared long synthetic source path INSIDE the target")
    parser.add_argument("--source-namespace", required=True)
    parser.add_argument("--source-profile", required=True)
    parser.add_argument("--provider", required=True)
    parser.add_argument("--metric-container", required=True)
    parser.add_argument("--metric-classpath", required=True)
    parser.add_argument("--jdbc-properties", required=True, help="protected file INSIDE the separate metric container")
    parser.add_argument("--metric-java", default="/usr/lib/jvm/java-25-openjdk-amd64/bin/java")
    parser.add_argument("--evidence", type=Path, required=True, help="new private directory on the controller host")
    parser.add_argument("--launcher", default="/usr/local/bin/carlos-cds-import")
    parser.add_argument("--network", default="carlos-cds-test")
    parser.add_argument("--docker-socket", default="/var/run/docker.sock")
    parser.add_argument("--docker", default="docker")
    parser.add_argument("--timeout", type=int, default=300, help="per operation/deadline, 30..3600 seconds; no timeout retry")
    args = parser.parse_args()
    require(args.disposable and 30 <= args.timeout <= 3600, "DISPOSABLE_AND_BOUNDED_TIMEOUT_REQUIRED")
    require(bool(re.fullmatch(r"io\.carlos\.cds\.[A-Za-z0-9_.-]+=[A-Za-z0-9_.-]{1,96}", args.owner_label)), "OWNERSHIP_LABEL_REQUIRED")
    require(args.prepared_root.is_absolute() and ".." not in args.prepared_root.parts
            and args.prepared_root != Path("/"), "PREPARED_ROOT_INVALID")
    require(PurePosixPath(args.input).is_relative_to(SOURCE) and ".." not in PurePosixPath(args.input).parts
            and args.input != SOURCE, "EXPLICIT_PREPARED_SOURCE_REQUIRED")
    require(all(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}", value) for value in
                (args.source_namespace, args.source_profile, args.provider)), "SOURCE_CONTRACT_INVALID")
    require(args.metric_container != args.container and args.jdbc_properties.startswith("/")
            and args.metric_java.startswith("/") and args.launcher.startswith("/"), "EXTERNAL_METRIC_SELECTOR_INVALID")
    require(args.evidence.is_absolute() and not args.evidence.exists() and not args.evidence.is_symlink(), "EVIDENCE_DIRECTORY_MUST_BE_NEW")
    require(not args.execute or bool(re.fullmatch(r"[a-f0-9]{64}", args.released_fingerprint or "")), "EXPLICIT_RELEASE_FINGERPRINT_REQUIRED")
    args.evidence.mkdir(mode=0o700)
    try:
        docker = Docker(args.docker_socket)
        original = docker.inspect(args.container)
        runtime, immutable = contract(original, args)
        args.target_id = original["Id"]
        metric = docker.inspect(args.metric_container)
        ownership(metric, args, metric=True)
        args.metric_id = metric["Id"]
        args.metric_config = digest_json(metric["Config"])
        args.metric_host = digest_json(metric["HostConfig"])
        idle(docker, args.target_id)
        metric_prepared = metric_probe(args, metric)
        prepared = probe(args, args.target_id, immutable)
        require(prepared.get("acceptedDocuments") == 0, "EXISTING_ACCEPTANCE_REQUIRES_EXPLICIT_RECONCILIATION")
        snapshot = {"runtime": runtime, "immutable": prepared["mounts"], "permissions": prepared["private"],
                    "clinical": {"input": args.input, "sourceNamespace": args.source_namespace,
                                 "sourceProfile": args.source_profile, "provider": args.provider, "expectedUnits": 514,
                                 "expectedNativePatients": 1, "expectedNativeNotes": 256},
                    "metric": {"containerId": args.metric_id, "configSha256": args.metric_config, "hostConfigSha256": args.metric_host,
                               "immutable": metric_prepared,
                               "classpathSha256": digest_json(args.metric_classpath), "java": args.metric_java,
                               "jdbcSelectorSha256": digest_json(args.jdbc_properties)}}
        fingerprint = digest_json(snapshot)
        save(args.evidence, "preflight.json", {"fingerprint": fingerprint, "snapshot": snapshot, "cgroup": {
             key: prepared[key] for key in ("memory.max", "memory.swap.max", "cpu.max", "memory.peak", "memory.events")}})
        if not args.execute:
            print(json.dumps({"containerRecovery": "HOLD", "preflightFingerprint": fingerprint, "clinicalAcceptance": False}))
            return
        require(args.released_fingerprint == fingerprint, "RELEASE_FINGERPRINT_MISMATCH")
        caps = bounded_exec(exec_command(args, args.target_id, [args.launcher, "--capabilities"], "10001:10001"), args.timeout)
        require(isinstance(caps, dict) and type(caps.get("protocol")) is int and caps["protocol"] == 1
                and type(caps.get("java")) is int and caps["java"] == 25 and caps.get("cliHeapMiB") == 512
                and caps.get("submissionLookup") is True and isinstance(caps.get("operations"), list)
                and len(caps["operations"]) == 5 and set(caps["operations"]) == {"import", "status", "resume", "cancel", "report"},
                "ACTUAL_LAUNCHER_CAPABILITIES_MISMATCH")
        save(args.evidence, "capabilities.json", {key: caps[key] for key in ("protocol", "java", "cliHeapMiB", "submissionLookup", "operations")})
        before = metrics(args, docker, "fixture")
        require(len(before) == 16 and all(type(value) is int for value in before.values())
                and {"totalNativePatients", "totalNativeNotes"} <= set(before), "GLOBAL_NATIVE_BASELINE_INCOMPLETE")
        save(args.evidence, "native-before.json", before)
        argv = ["--operation", "import", "--provider", args.provider, "--input", args.input,
                "--source-namespace", args.source_namespace, "--source-profile", args.source_profile, "--no-match-providers"]
        operation = Operation(args, args.target_id, argv, "import")
        deadline = time.monotonic() + args.timeout
        run = None
        while time.monotonic() < deadline:
            rows = operation.values()
            submissions = [row for row in rows if row.get("event") == "SUBMISSION"]
            handles = [row for row in rows if row.get("event") == "ACCEPTED"]
            require(len(handles) <= 1 and len(submissions) <= 1, "AMBIGUOUS_IMPORT_ACCEPTANCE")
            if handles:
                require(len(submissions) == 1 and submissions[0].get("submissionKey") == operation.nonce, "SUBMISSION_NONCE_MISMATCH")
                if run is None:
                    run = handles[0]["runId"]
                    save(args.evidence, "accepted-run.json", {"runId": run, "submissionKey": operation.nonce,
                         "containerId": args.target_id, "releasedFingerprint": fingerprint, "argvSha256": argv_digest(argv)})
                observed = metrics(args, docker, run)
                if type(observed.get("nativeNotes")) is int and observed["nativeNotes"] > 0 \
                        and 0 < observed.get("checkpoints", 0) < observed.get("expectedUnits", 0):
                    require(observed["expectedUnits"] == 514 and observed.get("cancelRequested") is False, "PARTIAL_NATIVE_CONTRACT_MISMATCH")
                    save(args.evidence, "native-before-kill.json", observed)
                    break
            require(operation.child.poll() is None and not any(row.get("event") == "RESULT" for row in rows),
                    "IMPORT_ENDED_BEFORE_PARTIAL_FAULT_WINDOW")
            time.sleep(0.05)
        else:
            raise RuntimeError("NO_PROVEN_PARTIAL_FAULT_WINDOW_RETAIN_STATE")
        assert run is not None
        document, document_sha = docker.accepted(args.target_id, operation.nonce)
        binding = accepted_binding(document, document_sha, args, operation.nonce)
        save(args.evidence, "accepted-binding.json", binding)
        # Recheck exact ownership/identity/contract immediately before the sole
        # destructive action. No process-group, Java-wide, or alternate kill.
        current = docker.inspect(args.target_id)
        require(contract(current, args)[0] == runtime and current["Id"] == args.target_id, "PRE_KILL_CONTAINER_CHANGED")
        # Preserve original cgroup counters before deletion resets them. This
        # explicitly measured tiny kernel-counter probe counts against 768MiB;
        # all JDBC/native inspection stays in the independent metric container.
        original_cgroup = bounded_exec(exec_command(args, args.target_id, ["python3", "-c", CGROUP]), args.timeout)
        cgroup(original_cgroup)
        save(args.evidence, "cgroup-before-kill.json", original_cgroup)
        latest = metrics(args, docker, run)
        require(latest.get("nativeNotes", 0) > 0 and 0 < latest.get("checkpoints", 0) < latest.get("expectedUnits", 0)
                and operation.child.poll() is None and not any(row.get("event") == "RESULT" for row in operation.values()),
                "PARTIAL_FAULT_WINDOW_CLOSED")
        save(args.evidence, "kill-intent.json", {"containerId": args.target_id, "runId": run,
             "submissionKey": operation.nonce, "signal": "SIGKILL", "observed": latest})
        docker.request("POST", "/containers/" + args.target_id + "/kill?signal=SIGKILL", statuses=(204,))
        operation.finish(30, killed=True)
        stopped = docker.inspect(args.target_id)
        require(stopped["State"].get("Running") is False and stopped["State"].get("OOMKilled") is False
                and stopped["State"].get("ExitCode") == 137, "CONTAINER_SIGKILL_NOT_PROVEN")
        save(args.evidence, "killed-container.json", {"containerId": args.target_id, "runId": run,
             "exitCode": stopped["State"]["ExitCode"], "oomKilled": stopped["State"]["OOMKilled"]})
        killed_counts = metrics(args, docker, run)
        require(killed_counts.get("nativeNotes", 0) > 0 and 0 < killed_counts.get("checkpoints", 0) < 514
                and killed_counts.get("expectedUnits") == 514 and killed_counts.get("cancelRequested") is False,
                "KILLED_RUN_WAS_NOT_PARTIAL")
        save(args.evidence, "native-after-kill.json", killed_counts)
        # No force and no volume deletion. Accepted source/state/documents stay.
        docker.request("DELETE", "/containers/" + args.target_id, statuses=(204,))
        created = docker.request("POST", "/containers/create?" + urlencode({"name": args.container}),
                                 recreate_request(original, args), statuses=(201,))
        require(isinstance(created, dict) and bool(re.fullmatch(r"[a-f0-9]{64}", created.get("Id", "")))
                and created["Id"] != args.target_id, "NEW_CONTAINER_ID_NOT_PROVEN")
        new_id = created["Id"]
        save(args.evidence, "replacement-created.json", {"originalId": args.target_id, "replacementId": new_id, "imageId": original["Image"]})
        docker.request("POST", "/containers/" + new_id + "/start", statuses=(204,))
        replacement = docker.inspect(new_id)
        require(contract(replacement, args)[0] == runtime, "REPLACEMENT_RUNTIME_CONTRACT_CHANGED")
        idle(docker, new_id)
        reprobe = probe(args, new_id, immutable)
        require(reprobe["mounts"] == prepared["mounts"] and reprobe["private"] == prepared["private"], "REPLACEMENT_IMMUTABLE_CONTENT_CHANGED")
        replacement_doc, replacement_sha = docker.accepted(new_id, operation.nonce)
        require(accepted_binding(replacement_doc, replacement_sha, args, operation.nonce) == binding, "ACCEPTED_BINDING_CHANGED")
        save(args.evidence, "replacement-verified.json", {"containerId": new_id, "fingerprint": fingerprint,
             "jobDocumentSha256": replacement_sha, "cgroup": {key: reprobe[key] for key in
             ("memory.max", "memory.swap.max", "cpu.max", "memory.peak", "memory.events")}})
        args.target_id = new_id
        common = ["--provider", args.provider, "--run", run]
        recovered = Operation(args, new_id, ["--operation", "status", "--provider", args.provider,
                              "--submission-key", operation.nonce], "submission-status")
        result(recovered.finish(args.timeout), run)
        resumed = Operation(args, new_id, ["--operation", "resume", *common], "resume")
        complete(result(resumed.finish(args.timeout), run))
        for name in ("status", "report"):
            check = Operation(args, new_id, ["--operation", name, *common], name)
            values = check.finish(args.timeout)
            complete(result(values, run))
            if name == "report":
                require(len([row for row in values if row.get("event") == "PROTECTED_REPORTS" and row.get("runId") == run]) == 1,
                        "PROTECTED_REPORTS_NOT_PROVEN")
        counts = metrics(args, docker, run)
        require(counts.get("nativeTargets") == 1 and counts.get("nativeNotes") == 256 and counts.get("checkpoints") == 514
                and counts.get("expectedUnits") == 514 and counts.get("holds") == 0 and counts.get("cancelRequested") is False,
                "FINAL_NATIVE_COUNTS_MISMATCH")
        save(args.evidence, "native-final.json", counts)
        after = metrics(args, docker, "fixture")
        expected = dict(before)
        expected["totalNativePatients"] += 1
        expected["totalNativeNotes"] += 256
        require(after == expected, "UNRELATED_NATIVE_DELTA_OBSERVED")
        save(args.evidence, "native-after.json", after)
        final_cgroup = bounded_exec(exec_command(args, new_id, ["python3", "-c", CGROUP]), args.timeout)
        cgroup(final_cgroup)
        save(args.evidence, "cgroup-final.json", final_cgroup)
        final_doc, final_sha = docker.accepted(new_id, operation.nonce)
        require(accepted_binding(final_doc, final_sha, args, operation.nonce) == binding, "FINAL_ACCEPTED_BINDING_CHANGED")
        require(metric_probe(args, docker.inspect(args.metric_id)) == metric_prepared, "EXTERNAL_METRIC_ARTIFACT_CHANGED")
        proof = {"containerRecovery": "PASS", "runId": run, "submissionKey": operation.nonce,
                 "releasedFingerprint": fingerprint, "originalContainerId": original["Id"], "replacementContainerId": new_id,
                 "nativePatients": 1, "nativeNotes": 256, "checkpoints": 514, "holds": 0,
                 "clinicalComplete": True, "cleanComplete": True, "reportsDelivered": True, "cleanupComplete": True,
                 "maintenanceReleaseState": "RELEASED", "heapMiB": 512, "memoryLimitMiB": 768, "cpuLimit": 1}
        save(args.evidence, "proof.json", proof)
        print(json.dumps(proof))
    except BaseException as error:
        code = str(error) if type(error) is RuntimeError and re.fullmatch(r"[A-Z0-9_]{1,100}", str(error)) else "CONTROLLER_FAILED_RETAIN_STATE"
        save(args.evidence, "failure.json", {"containerRecovery": "HOLD", "safeCode": code, "acceptedStateRetained": True})
        raise RuntimeError(code) from None


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, KeyError, TypeError, http.client.HTTPException, tarfile.TarError) as error:
        code = str(error) if type(error) is RuntimeError and re.fullmatch(r"[A-Z0-9_]{1,100}", str(error)) else "CONTROLLER_FAILED_RETAIN_STATE"
        print("CDS_CONTAINER_RECOVERY:" + code, file=sys.stderr)
        raise SystemExit(1) from None
