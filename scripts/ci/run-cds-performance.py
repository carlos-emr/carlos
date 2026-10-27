#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Explicit installed-engine scalability gates; retain all accepted source and receipts.

Run in the disposable root CLI runtime after other gates have finished. The
paired installed carlos-ctl and its configuration/delegation are prerequisites;
this runner never creates, replaces or edits application configuration.
"""
from __future__ import annotations

import argparse
import base64
from collections import Counter
from contextlib import nullcontext
import hashlib
import json
import os
from pathlib import Path
import pwd
import random
import re
import math
import shutil
import signal
import subprocess
import sys
import time
import traceback
import uuid
import zipfile
from urllib.parse import urlsplit

MIB = 1 << 20
GIB = 1 << 30
UUID = re.compile(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\Z")


def supervise(receipt: Path, command: list[str]) -> int:
    """wait4 owns reaping: a short-lived peak cannot disappear between /proc samples."""
    if not receipt.is_absolute() or receipt.exists() or not command or not Path(command[0]).is_absolute():
        raise RuntimeError("invalid kernel-resource supervisor binding")
    child = os.fork()
    if child == 0:
        os.execv(command[0], command)
    def forward(number, _frame):
        try:
            os.kill(child, number)
        except ProcessLookupError:
            pass
    previous = {number:signal.signal(number,forward) for number in (signal.SIGTERM,signal.SIGINT)}
    try:
        _, status, usage = os.wait4(child, 0)
    finally:
        for number, handler in previous.items():
            signal.signal(number,handler)
    # Linux reports the largest process RSS, not a sum of simultaneous processes.
    # Keep this independent from /proc VmHWM: the two kernel interfaces can differ
    # slightly in resident-page accounting. Each must satisfy the same fixed budget.
    value = {"pid": child, "exitCode": os.waitstatus_to_exitcode(status),
             "largestProcessPeakRssBytes": usage.ru_maxrss*1024,
             "userCpuSeconds": usage.ru_utime, "systemCpuSeconds": usage.ru_stime,
             "inputBlocks": usage.ru_inblock, "outputBlocks": usage.ru_oublock,
             "majorPageFaults": usage.ru_majflt, "minorPageFaults": usage.ru_minflt}
    private_json(receipt,value,exclusive=True)
    code = value["exitCode"]
    return code if code >= 0 else 128-code


def gc_evidence(path: Path) -> dict:
    required = {"Timestamp", "YGC", "YGCT", "FGC", "FGCT", "GCT"}
    first = last = None
    count = 0
    with path.open() as stream:
        columns = stream.readline(4096).split()
        if not required.issubset(columns):
            raise RuntimeError("actual service GC columns are missing")
        while line := stream.readline(4096):
            if not line.endswith("\n") or len(line) >= 4096 or count > 86410:
                raise RuntimeError("GC sample evidence exceeded its bounds")
            values = line.split()
            if len(values) != len(columns):
                raise RuntimeError("truncated or diagnostic GC row cannot be numeric evidence")
            sample = {}
            for key, token in zip(columns, values):
                if token == "-" and key not in required:
                    sample[key] = None
                else:
                    value = float(token)
                    if not math.isfinite(value) or value < 0:
                        raise RuntimeError("invalid GC sample")
                    sample[key] = value
            if last and any(sample[key] < last[key] for key in required):
                raise RuntimeError("GC counters reset during the measured process")
            first = first or sample
            last = sample
            count += 1
    if count < 2:
        raise RuntimeError("insufficient actual service GC time samples")
    return {"columns": columns,"samples":count,"first":first,"last":last,
            "delta": {key:last[key]-first[key] for key in sorted(required)}}


def enforce_rss_budget(process_peak: int, kernel: dict, budget: int) -> int:
    waited_peak=kernel.get('largestProcessPeakRssBytes')
    if kernel.get('exitCode')!=0 or not isinstance(waited_peak,int) or waited_peak<1 \
            or not isinstance(process_peak,int) or process_peak<1 or max(process_peak,waited_peak)>budget:
        raise RuntimeError('kernel/process peak RSS or process-exit proof failed')
    return max(process_peak,waited_peak)


def is_import_command(command: list[bytes], source: str, provider: str) -> bool:
    main = b"io.github.carlos_emr.carlos.demographic.cdsimport.CdsImport"
    if not command or Path(os.fsdecode(command[0])).name != 'java' or main not in command or b"--capabilities" in command:
        return False
    for key, value in ((b"--operation",b"import"),(b"--input",source.encode()),(b"--provider",provider.encode())):
        if command.count(key) != 1:
            return False
        index = command.index(key)
        if index+1 >= len(command) or command[index+1] != value:
            return False
    return True


def verify_java_identity(pid: int, uid: int, gid: int, executable: Path, started: str) -> None:
    # /proc/<other-uid>/exe is ptrace-protected on normal unprivileged Docker.
    # Read as that same service UID; do not add SYS_PTRACE to the clinical runtime.
    code = ("import os,pathlib,sys;root=pathlib.Path('/proc')/sys.argv[1];"
            "assert root.joinpath('exe').resolve(strict=True)==pathlib.Path(sys.argv[2]).resolve(strict=True);"
            "assert root.joinpath('stat').read_text().rsplit(')',1)[1].split()[19]==sys.argv[3];"
            "sys.stdout.write('MATCH')")
    checked = subprocess.run([sys.executable,"-c",code,str(pid),str(executable),started],
        user=uid,group=gid,extra_groups=[],capture_output=True,timeout=10)
    if checked.returncode or checked.stdout != b'MATCH':
        raise RuntimeError("actual non-root Java executable/start identity could not be verified")


def service_sample(pid: int) -> dict | None:
    try:
        started = Path(f'/proc/{pid}/stat').read_text().rsplit(')',1)[1].split()[19]
        status = Path(f'/proc/{pid}/status').read_text()
    except FileNotFoundError:
        return None  # A completed JVM may disappear before its supervisor is reaped.
    if re.search(r'^State:\s+[ZX]',status,re.M) and not re.search(r'^VmRSS:',status,re.M):
        return None
    return {'started':started,'uid':int(re.search(r'^Uid:\s+(\d+)',status,re.M)[1]),
            'rss':int(re.search(r'^VmRSS:\s+(\d+) kB$',status,re.M)[1])*1024,
            'highWater':int(re.search(r'^VmHWM:\s+(\d+) kB$',status,re.M)[1])*1024}


def private_json(path: Path, value: dict, *, exclusive: bool = False) -> None:
    if path.is_symlink() or exclusive and path.exists():
        raise RuntimeError("refusing to replace prior or linked evidence")
    pending = path.with_name(path.name + ".pending-" + uuid.uuid4().hex)
    with os.fdopen(os.open(pending, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600), "w", encoding="utf-8") as stream:
        json.dump(value, stream, sort_keys=True, indent=2)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(pending, path)
    directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def safe_failure(failure: Exception) -> dict:
    # Exception messages, locals and source lines can contain SQL or credentials.
    frames=traceback.extract_tb(failure.__traceback__)
    return {'exceptionClass':type(failure).__name__,
            'codeLocations':[{'file':Path(frame.filename).name,'function':frame.name,'line':frame.lineno}
                             for frame in frames[-5:]]}


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        while block := source.read(65536):
            value.update(block)
    return value.hexdigest()


def protected_file(path: Path, *, owner: int = 0) -> Path:
    if not path.is_absolute() or path.is_symlink() or not path.is_file():
        raise RuntimeError("required protected input is not an absolute regular file")
    status = path.stat()
    if status.st_uid != owner or status.st_mode & 0o077 or status.st_size > 65536:
        raise RuntimeError("required protected input has unsafe ownership, mode or size")
    return path


def storage_admission(path: Path, minimum: int) -> dict:
    usage = shutil.disk_usage(path)
    if usage.free < minimum:
        raise RuntimeError("CDS_PERFORMANCE_STORAGE_PREREQUISITE: insufficient real free storage")
    return {"freeBytes": usage.free, "requiredFreeBytes": minimum}


def resource_observation(query, root: Path, run: str | None) -> dict:
    """Finite global/load observations; never select SQL text or transaction parameters."""
    rows = query("SELECT (SELECT VARIABLE_VALUE FROM information_schema.GLOBAL_STATUS WHERE VARIABLE_NAME='Threads_connected'),"
                 "(SELECT VARIABLE_VALUE FROM information_schema.GLOBAL_STATUS WHERE VARIABLE_NAME='Threads_running'),"
                 "COALESCE((SELECT GREATEST(MAX(TIMESTAMPDIFF(SECOND,trx_started,CURRENT_TIMESTAMP)),0) FROM information_schema.INNODB_TRX),0)")
    if len(rows) != 1 or len(rows[0]) != 3 or not all(value.isdecimal() for value in rows[0]):
        raise RuntimeError("bounded database resource sample is unavailable")
    connected, running, age = map(int, rows[0])
    expected = pending = None
    if run is not None:
        if not UUID.fullmatch(run):
            raise RuntimeError("resource observer requires the exact accepted handle")
        rows = query("SELECT execution_digest IS NOT NULL,expected_units,"
                     "(SELECT COUNT(*) FROM cds_import_unit u WHERE u.run_id=r.id AND u.state NOT IN ('COMMITTED','ALREADY_COMPLETE')) "
                     "FROM cds_import_run r WHERE id='" + run + "'")
        if len(rows) != 1 or len(rows[0]) != 3 or rows[0][0] not in ('0','1'):
            raise RuntimeError("accepted resource inventory sample is unavailable")
        if rows[0][0] == '1':
            if not all(value.isdecimal() for value in rows[0][1:]):
                raise RuntimeError("sealed resource inventory metrics are invalid")
            expected, pending = map(int, rows[0][1:])
            if pending > expected:
                raise RuntimeError("sealed resource inventory has impossible backlog")
    space = os.statvfs(root)
    if space.f_bavail < 0 or space.f_favail < 0:
        raise RuntimeError("filesystem availability metrics are unavailable")
    return {'threadsConnected':connected,'threadsRunning':running,
            'oldestActiveTransactionAgeSeconds':age,'expectedUnits':expected,'pendingUnits':pending,
            'freeBytes':space.f_bavail*space.f_frsize,'freeInodes':space.f_favail}


def retain_resource_observation(summary: dict, sample: dict) -> None:
    summary['samples'] = summary.get('samples',0)+1
    for selected, source in (('maxObservedThreadsConnected','threadsConnected'),
                             ('maxObservedThreadsRunning','threadsRunning'),
                             ('maxObservedActiveTransactionAgeSeconds','oldestActiveTransactionAgeSeconds')):
        summary[selected] = max(summary.get(selected,0),sample[source])
    for selected, source in (('minimumObservedFreeBytes','freeBytes'),('minimumObservedFreeInodes','freeInodes')):
        summary[selected] = min(summary.get(selected,sample[source]),sample[source])
    if sample['expectedUnits'] is None:
        summary['unsealedOrNotAcceptedSamples'] = summary.get('unsealedOrNotAcceptedSamples',0)+1
    else:
        summary['sealedInventorySamples'] = summary.get('sealedInventorySamples',0)+1
        prior = summary.get('maxObservedPendingUnits')
        summary['maxObservedPendingUnits'] = max(prior if prior is not None else 0,sample['pendingUnits'])
    summary['last'] = sample


def xml_start(identity: str) -> bytes:
    if not re.fullmatch(r"[A-Z0-9-]{1,20}", identity):
        raise ValueError("synthetic source identity must fit its actual wire capacity")
    return (f'<?xml version="1.0"?><OmdCds xmlns="cds" xmlns:cdsd="cds_dt"><PatientRecord>'
            f'<Demographics><Names><cdsd:LegalName namePurpose="L"><cdsd:FirstName><cdsd:Part>Performance</cdsd:Part>'
            f'<cdsd:PartType>GIV</cdsd:PartType></cdsd:FirstName><cdsd:LastName><cdsd:Part>FAKE-{identity}</cdsd:Part>'
            f'<cdsd:PartType>FAMC</cdsd:PartType></cdsd:LastName></cdsd:LegalName></Names><DateOfBirth>1971-03-04</DateOfBirth>'
            f'<Gender>F</Gender><UniqueVendorIdSequence>{identity}</UniqueVendorIdSequence>'
            f'<PersonStatusCode><PersonStatusAsEnum>A</PersonStatusAsEnum></PersonStatusCode></Demographics>').encode()


def pdf(path: Path, stream_bytes: int, seed: str, *, max_stream_bytes: int = 64 * MIB) -> tuple[int, str]:
    """Valid bounded PDF streams/xref; actual physical source, never sparse padding.

    Comment streams contain fresh entropy and repeated source bytes, giving ZIP
    compression well below the production ratio ceiling200. A5GiB document uses
    multiple64MiB streams instead of an overflowing signed32bit /Length value.
    """
    if not 1 <= stream_bytes <= 64 * GIB or not 1024 <= max_stream_bytes <= GIB:
        raise ValueError('invalid bounded PDF workload')
    stream_count = (stream_bytes + max_stream_bytes - 1) // max_stream_bytes
    if stream_count > 1024:
        raise ValueError('PDF workload object table exceeds its explicit bound')
    rng = random.Random(seed)
    offsets = []
    with path.open("xb") as output:
        output.write(b"%PDF-1.4\n%Synthetic CDS performance fixture\n")
        contents = b' '.join(str(number).encode()+b' 0 R' for number in range(4, 4+stream_count))
        objects = [b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                   b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents ["+contents+b"] >>"]
        for index, body in enumerate(objects, 1):
            offsets.append(output.tell())
            output.write(str(index).encode() + b" 0 obj\n" + body + b"\nendobj\n")
        remaining_total = stream_bytes
        for index in range(stream_count):
            length = min(remaining_total, max_stream_bytes)
            remaining_total -= length
            offsets.append(output.tell())
            output.write(f"{4+index} 0 obj\n<< /Length {length} >>\nstream\n".encode())
            remaining = length
            while remaining:
                text = base64.b64encode(rng.randbytes(6144))
                block = b"%" + text * 7 + b"\n"
                block = block[:min(remaining, len(block))]
                if len(block) == remaining:
                    block = block[:-1] + b"\n"
                output.write(block)
                remaining -= len(block)
            output.write(b"endstream\nendobj\n")
        xref = output.tell()
        objects_count = len(offsets)+1
        output.write(f"xref\n0 {objects_count}\n0000000000 65535 f \n".encode())
        for offset in offsets:
            if offset > 9999999999:
                raise ValueError('PDF xref offset exceeds its actual format capacity')
            output.write(f"{offset:010d} 00000 n \n".encode())
        output.write(f"trailer\n<< /Size {objects_count} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
        output.flush()
        os.fsync(output.fileno())
    path.chmod(0o644)
    return path.stat().st_size, digest(path)


def report(reference: str) -> bytes:
    if not re.fullmatch(r"[A-Za-z0-9_.-]+\.pdf", reference):
        raise ValueError("unsafe synthetic report reference")
    return (f"<Reports><Format>Binary</Format><FileExtensionAndVersion>PDF</FileExtensionAndVersion><FilePath>{reference}</FilePath>"
            f"<Class>Medical Records Report</Class></Reports>").encode()


def generate_case(root: Path, label: str, token: str, patients: int = 1,
                  records: int = 0, payload: int = 0, *, archive: bool = False, validate_xml=None) -> dict:
    folder = root / label
    folder.mkdir(mode=0o755)
    manifests = []
    validations=[]
    # Extract real wire occurrences instead of inventing a second schema fixture.
    from xml.etree import ElementTree as ET
    from defusedxml.ElementTree import parse
    corpus = Path(__file__).resolve().parents[2] / "src/test/resources/demographic/cds-import-all-categories.xml"
    tree = parse(corpus)
    ns = {"c": "cds"}
    ET.register_namespace("", "cds")
    ET.register_namespace("cdsd", "cds_dt")
    laboratory = tree.find("c:PatientRecord/c:LaboratoryResults", ns)
    note = tree.find("c:PatientRecord/c:ClinicalNotes", ns)
    if laboratory is None or note is None:
        raise RuntimeError("required real-wire corpus categories are missing")
    lab_wire, note_wire = ET.tostring(laboratory), ET.tostring(note)
    document_bytes = 0
    source = root / (label + ".zip") if archive else folder
    archive_context = zipfile.ZipFile(source, "x", zipfile.ZIP_DEFLATED, allowZip64=True, compresslevel=1) if archive else nullcontext()
    with archive_context as archive_output:
        def seal_member(member: Path, document: bool) -> None:
            manifests.append({"member": member.name, "bytes": member.stat().st_size, "sha256": digest(member), "document": document})
            if archive_output is not None:
                with member.open("rb") as incoming, archive_output.open(member.name, "w", force_zip64=True) as target:
                    shutil.copyfileobj(incoming, target, 65536)
                # Producer temporary only; no import has been submitted. The unmodified original is now in the ZIP.
                member.unlink()
        for index in range(patients):
            identity = "P" + token[:10].upper() + f"{index:06X}"
            member = folder / f"patient-{index:06d}.xml"
            attachment = None
            with member.open("xb") as output:
                output.write(xml_start(identity))
                for _ in range(records):
                    output.write(lab_wire)
                for _ in range(records):
                    output.write(note_wire)
                if payload:
                    attachment = folder / f"report-{index:06d}.pdf"
                    length, sha256 = pdf(attachment, payload, identity)
                    document_bytes += length
                    output.write(report(attachment.name))
                    if not archive:
                        output.write(b"<Reports><Format>Binary</Format><FileExtensionAndVersion>PDF</FileExtensionAndVersion><Content><cdsd:Media>")
                        with attachment.open("rb") as incoming:
                            while chunk := incoming.read(49152):
                                output.write(base64.b64encode(chunk))
                        output.write(b"</cdsd:Media></Content><Class>Medical Records Report</Class></Reports>")
                        document_bytes += length
                output.write(b"</PatientRecord></OmdCds>")
            member.chmod(0o644)
            if validate_xml is not None:
                validations.append(validate_xml(member))
            else:
                # Tiny unit/external-reference XML uses an independent actual pinned-XSD validator.
                with member.open("rb") as original_xml:
                    validated = subprocess.run(["xmllint", "--nonet", "--stream", "--noout", "--schema",
                        str(Path(__file__).resolve().parents[2] / "src/main/resources/cds-import/schema/EMR_Data_Migration_Schema.xsd"), "-"],
                        stdin=original_xml, capture_output=True, timeout=300)
                if validated.returncode:
                    raise RuntimeError("generated workload failed the actual pinned schema")
            seal_member(member, False)
            if attachment is not None:
                seal_member(attachment, True)
    if archive:
        source.chmod(0o644)
        folder.rmdir()
    return {"label": label, "source": str(source), "patients": patients, "notes": patients * records,
            "labs": patients * records, "documents": patients * (1 if archive else 2) if payload else 0,
            "documentBytes": document_bytes, "sourceBytes": sum(row["bytes"] for row in manifests),
            "archiveBytes": source.stat().st_size if archive else 0, "members": manifests,"sourceValidation":validations}


def production_workload_validation(source: Path, spool: Path, java: str, classpath: str) -> dict:
    started=time.monotonic()
    completed=subprocess.run([java,'-Xmx64m','-cp',classpath,'CdsWorkloadValidator',str(source),str(spool)],
                             capture_output=True,text=True,timeout=300)
    rows=[line.removeprefix('CDS_WORKLOAD_VALIDATED:') for line in completed.stdout.splitlines()
          if line.startswith('CDS_WORKLOAD_VALIDATED:')]
    if completed.returncode or len(completed.stdout)>65536 or len(completed.stderr)>65536 or len(rows)!=1:
        raise RuntimeError('whole-original-source production-parser validation did not complete')
    value=json.loads(rows[0])
    required={'sourceSha256','sourceBytes','records','fields','decodedBinaryBytes','valuesBytes'}
    if not isinstance(value,dict) or set(value)!=required or value['sourceSha256']!=digest(source) \
            or value['sourceBytes']!=source.stat().st_size or any(type(value[key]) is not int or value[key]<0 for key in required-{'sourceSha256'}) \
            or value['records']<1 or value['decodedBinaryBytes']>value['valuesBytes']:
        raise RuntimeError('production-parser validation did not bind the complete original source')
    return {**value,'validator':'actual CdsXmlParser/pinned schema/default limits; no DB or clinical write',
            'heapMiB':64,'elapsedSeconds':time.monotonic()-started}


def rows(path: Path) -> list[dict]:
    result = []
    with path.open(encoding="utf-8") as stream:
        for _ in range(200000):
            line = stream.readline(65538)
            if not line:
                break
            if len(line) > 65536 or not line.endswith("\n"):
                raise RuntimeError("bounded operational log contract was exceeded")
            if line.startswith("{"):
                row = json.loads(line)
                if not isinstance(row, dict) or row.get("event") not in {
                    "INITIALIZING", "SUBMISSION", "ACCEPTED", "PROGRESS", "RESULT", "PROTECTED_REPORTS"}:
                    raise RuntimeError("unexpected operational JSON")
                if row["event"] != "PROGRESS":
                    result.append(row)
        else:
            raise RuntimeError("bounded operational row count was exceeded")
    return result


def technical_failure_projection(value: dict) -> dict:
    if not isinstance(value,dict) or value.get('code')!='CDS_OPERATION_FAILED' \
            or value.get('clinicalAcceptance')!='RECONCILE_DURABLE_STATE' \
            or not isinstance(value.get('causes'),list) or not 0<=len(value['causes'])<=16:
        raise RuntimeError('technical failure receipt has an invalid finite contract')
    causes=[]
    frames=0
    for item in value['causes']:
        if not isinstance(item,dict) or not re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_.$]{0,511}',str(item.get('type',''))) \
                or not isinstance(item.get('frames'),list):
            raise RuntimeError('technical failure type/frames are invalid')
        selected={'type':item['type'],'frames':[]}
        for frame in item['frames']:
            frames+=1
            if frames>32 or not isinstance(frame,str) or len(frame)>1024 \
                    or not re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_.$/]*#[A-Za-z0-9_$<>]+:-?[0-9]+',frame):
                raise RuntimeError('technical failure frame capacity or format is invalid')
            selected['frames'].append(frame)
        if 'sqlState' in item:
            if not isinstance(item['sqlState'],str) or not re.fullmatch(r'[A-Z0-9]{5}',item['sqlState']):
                raise RuntimeError('technical failure SQL state is invalid')
            selected['sqlState']=item['sqlState']
        if 'vendorCode' in item:
            if type(item['vendorCode']) is not int or not -(1<<31)<=item['vendorCode']<(1<<31):
                raise RuntimeError('technical failure vendor code is invalid')
            selected['vendorCode']=item['vendorCode']
        causes.append(selected)
    return {'code':'CDS_OPERATION_FAILED','clinicalAcceptance':'RECONCILE_DURABLE_STATE','causes':causes,
            'technicalDetailsUnavailable':not causes}


def operator_projection(path: Path, diagnostics_root: Path | None=None) -> dict:
    """Retain finite process/receipt evidence, never raw startup/SQL/source log lines."""
    allowed={'event','operation','submissionKey','runId','state','dryRun','discoveryComplete','inventoryState',
        'discoveredFiles','patientXmlFiles','sourceBytes','sourcePatientsIdentified','sourcePatients','targetCopiesPlanned',
        'expectedPatients','expectedUnits','plannedUnits','committedUnits','alreadyCompleteUnits','pendingUnits','outcomes',
        'categories','blockingDiagnostics','clinicalComplete','reportState','reportsDelivered','cleanupState','cleanupComplete',
        'cleanComplete','operationTerminal','reviewVersion','reviewState','reviewId','pendingReviewCount','inactiveDraftCount',
        'reviewErrorCode','resumeRequested','maintenanceReleasePending','maintenanceReleaseState'}
    events=[{key:value for key,value in row.items() if key in allowed} for row in rows(path)
            if row.get('event') in {'INITIALIZING','SUBMISSION','ACCEPTED','RESULT'}]
    prefixes=Counter()
    progress_events=0
    reasons=[]
    technical=[]
    technical_errors=[]
    with path.open() as stream:
        for line in stream:
            if len(line)>65536:
                raise RuntimeError('operator projection row capacity exceeded')
            if line.startswith('{'):
                if json.loads(line).get('event')=='PROGRESS':
                    progress_events+=1
            prefix=re.match(r'^(?:carlos-ctl: )?(CDS_[A-Z_]{1,64}):',line)
            if prefix:
                prefixes[prefix[1]]+=1
            reason=re.fullmatch(r'CDS_REASON: safeCode=([A-Z][A-Z0-9_]{0,95}) outcomeUnknown=(true|false)\n',line)
            if reason:
                reasons.append({'code':reason[1],'outcomeUnknown':reason[2]=='true'})
                if len(reasons)>16:
                    raise RuntimeError('operator reason capacity exceeded')
            if line.startswith('CDS_TECHNICAL: ') or line.startswith('CDS_DIAGNOSTIC: protected operation evidence='):
                try:
                    if line.startswith('CDS_TECHNICAL: '):
                        selected=technical_failure_projection(json.loads(line.removeprefix('CDS_TECHNICAL: ')))
                        selected['delivery']='actor-stderr'
                    else:
                        file=Path(line.removeprefix('CDS_DIAGNOSTIC: protected operation evidence=').removesuffix('\n'))
                        if diagnostics_root is None or not file.is_absolute() or file.parent!=diagnostics_root \
                                or not re.fullmatch(r'cli-operation-[0-9a-f-]{36}\.json',file.name):
                            raise RuntimeError('protected operation receipt escaped the exact diagnostic root')
                        protected_file(file,owner=pwd.getpwnam('carlos').pw_uid)
                        selected=technical_failure_projection(json.loads(file.read_text()))
                        selected.update(delivery='protected-file',bytes=file.stat().st_size,sha256=digest(file))
                    if len(technical)>=4:
                        raise RuntimeError('technical failure receipt count exceeded')
                    technical.append(selected)
                except Exception as failure:
                    technical_errors.append(safe_failure(failure))
                    if len(technical_errors)>4:
                        raise RuntimeError('technical failure receipt error capacity exceeded')
    return {'events':events,'reasonCodes':reasons,'diagnosticPrefixes':dict(prefixes),
            'progressEvents':progress_events,
            'technicalFailureEvidence':technical,'technicalEvidenceErrors':technical_errors,
            'privateLogBytes':path.stat().st_size,'privateLogSha256':digest(path)}


STATEMENT_CLASSES=frozenset({'OBSERVATION','SCHEMA_CATALOGUE','COORDINATOR_LOCK','LEDGER_READ',
    'LEDGER_WRITE','SESSION_CONTROL','TRANSACTION_CONTROL','NATIVE_READ','NATIVE_WRITE','OTHER'})
STATEMENT_DIGEST_SQL="""SELECT COALESCE(DIGEST,'OVERFLOW'),LOWER(SHA2(DIGEST_TEXT,256)),
 CASE
 WHEN DIGEST_TEXT LIKE '%performance_schema%' OR DIGEST_TEXT LIKE 'SHOW GLOBAL STATUS%' THEN 'OBSERVATION'
 WHEN DIGEST_TEXT LIKE '%information_schema%' OR DIGEST_TEXT LIKE 'SHOW %' THEN 'SCHEMA_CATALOGUE'
 WHEN DIGEST_TEXT LIKE '%IS_USED_LOCK%' OR DIGEST_TEXT LIKE '%GET_LOCK%' OR DIGEST_TEXT LIKE '%RELEASE_LOCK%' THEN 'COORDINATOR_LOCK'
 WHEN DIGEST_TEXT REGEXP '^(START TRANSACTION|BEGIN|COMMIT|ROLLBACK)' THEN 'TRANSACTION_CONTROL'
 WHEN DIGEST_TEXT REGEXP '^(SET|DO|USE) ' THEN 'SESSION_CONTROL'
 WHEN DIGEST_TEXT LIKE '%cds_import_%' AND DIGEST_TEXT REGEXP '^(SELECT|WITH) ' THEN 'LEDGER_READ'
 WHEN DIGEST_TEXT LIKE '%cds_import_%' AND DIGEST_TEXT REGEXP '^(INSERT|UPDATE|DELETE|REPLACE) ' THEN 'LEDGER_WRITE'
 WHEN DIGEST_TEXT REGEXP '^(SELECT|WITH) ' THEN 'NATIVE_READ'
 WHEN DIGEST_TEXT REGEXP '^(INSERT|UPDATE|DELETE|REPLACE) ' THEN 'NATIVE_WRITE'
 ELSE 'OTHER' END,SUM(COUNT_STAR),SUM(SUM_TIMER_WAIT)
 FROM performance_schema.events_statements_summary_by_digest WHERE COUNT_STAR>0
 GROUP BY DIGEST,DIGEST_TEXT ORDER BY DIGEST LIMIT 4097"""


def statement_digest_snapshot(rows: list[list[str]]) -> dict:
    """Accept bounded hashes/classes/counters only; SQL text and bound values never leave the server."""
    if len(rows)>4096:
        raise RuntimeError('statement digest inventory capacity exceeded')
    result={}
    for row in rows:
        if len(row)!=5 or not re.fullmatch(r'[0-9a-fA-F]{32}|[0-9a-fA-F]{64}',row[0]) \
                or not re.fullmatch(r'[0-9a-f]{64}',row[1]) or row[2] not in STATEMENT_CLASSES \
                or not row[3].isdecimal() or not row[4].isdecimal() or row[0] in result:
            raise RuntimeError('statement digest overflow, duplicate or unsafe counter projection')
        result[row[0]]={'shapeSha256':row[1],'statementClass':row[2],
                        'count':int(row[3]),'timerPicoseconds':int(row[4])}
    if not result:
        raise RuntimeError('actual statement digest instrumentation is unavailable')
    return result


def statement_digest_delta(before: dict, after: dict) -> dict:
    if any(key not in after for key in before):
        raise RuntimeError('statement digest inventory reset or was evicted')
    entries=[]
    classes=Counter()
    for key,current in after.items():
        previous=before.get(key,dict(current,count=0,timerPicoseconds=0))
        if any(current[field]!=previous[field] for field in ('shapeSha256','statementClass')):
            raise RuntimeError('statement digest identity changed')
        count=current['count']-previous['count']
        timer=current['timerPicoseconds']-previous['timerPicoseconds']
        if min(count,timer)<0:
            raise RuntimeError('statement digest counter reset')
        if count:
            entries.append({'digest':key,**current,'count':count,'timerPicoseconds':timer})
            classes[current['statementClass']]+=count
    return {'scope':'exclusive server; normalized SQL is classified and hashed in-server; counts include observers',
            'statementCount':sum(classes.values()),'countsByClass':dict(classes),
            'digests':sorted(entries,key=lambda row:(-row['count'],row['digest']))}


def verified_query_budget_status(actual: int, budget: int) -> str:
    """Called only after all engine/native/hash/report/release checks succeeded; no exception is caught to continue."""
    if type(actual) is not int or type(budget) is not int or actual<0 or budget<1:
        raise RuntimeError('query counters reset or the budget is invalid')
    return 'PASSED' if actual<=budget else 'FAILED_QUERY_BUDGET'


def require_no_cgroup_oom(initial: dict, current: dict) -> None:
    for key in ('oom','oom_kill','oom_group_kill'):
        values=[snapshot.get('memory.events',{}).get(key) for snapshot in (initial,current)]
        if any(not isinstance(value,str) or not value.isdecimal() for value in values):
            raise RuntimeError('required actual cgroup OOM counters are unavailable')
        first,last=map(int,values)
        if first<0 or last!=first:
            raise RuntimeError('a measured profile caused a cgroup OOM event or counters reset')


def capture_evidence(case: dict, key: str, read) -> None:
    try:
        case[key]=read()
    except Exception as failure:
        case.setdefault('failureEvidenceErrors',{})[key]=safe_failure(failure)


def require_startup_control(exit_code: int, evidence: dict) -> None:
    if exit_code!=4 or evidence.get('reasonCodes')!=[{'code':'SUBMISSION_OUTCOME_NOT_YET_KNOWN','outcomeUnknown':True}] \
            or evidence.get('events')!=[{'event':'INITIALIZING','operation':'status'}]:
        raise RuntimeError('read-only startup control did not prove an unknown submission refusal')


def complete_result(values: list[dict]) -> dict:
    accepted = [row for row in values if row.get("event") == "ACCEPTED"]
    results = [row for row in values if row.get("event") == "RESULT"]
    if len(accepted) != 1 or len(results) != 1 or not UUID.fullmatch(str(accepted[0].get("runId", ""))):
        raise RuntimeError("missing unique accepted handle or terminal result; retain evidence and reconcile")
    result = results[0]
    if result.get("runId") != accepted[0]["runId"] or not all(result.get(key) is True for key in
            ("cleanComplete", "clinicalComplete", "reportsDelivered", "cleanupComplete")) \
            or result.get("resumeRequested") is not False or result.get("maintenanceReleasePending") is not False \
            or result.get("maintenanceReleaseState") != "RELEASED":
        raise RuntimeError("accepted run is not provably clean; never resubmit this source")
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--disposable", action="store_true")
    parser.add_argument("--exclusive-server", action="store_true", help="attest other database tests/writers are stopped during global-counter measurement")
    parser.add_argument("--fixture-metadata", type=Path, required=True)
    parser.add_argument("--jdbc-properties", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True, help="absent child under the existing registered synthetic source root")
    parser.add_argument("--classpath", required=True)
    parser.add_argument("--candidate-sha256", required=True)
    parser.add_argument("--profile", choices=("scaling", "aggregate20", "zip64"), default="scaling")
    parser.add_argument("--java", default="/usr/bin/java")
    parser.add_argument("--cli", default="/usr/sbin/carlos-ctl")
    parser.add_argument("--mysql", default="mariadb")
    parser.add_argument("--timeout", type=int, default=3600)
    parser.add_argument("--max-rss-mib", type=int, default=1024)
    parser.add_argument("--max-queries-per-unit", type=int, default=500)
    parser.add_argument("--expected-memory-mib", type=int)
    parser.add_argument("--expected-cpus", type=int)
    args = parser.parse_args()
    if not args.disposable or not args.exclusive_server or os.geteuid() != 0 or not Path("/.dockerenv").is_file():
        parser.error("requires an explicitly disposable root runtime and exclusive measured database server")
    if not re.fullmatch(r"[0-9a-f]{64}", args.candidate_sha256) or not 30 <= args.timeout <= 86400 \
            or not 128 <= args.max_rss_mib <= 32768 or args.max_queries_per_unit < 1:
        parser.error("invalid frozen candidate or numeric workload budgets")
    metadata = json.loads(protected_file(args.fixture_metadata).read_text())
    source_root = Path(metadata["sources"]["ordinary"]).parent.resolve(strict=True)
    if not args.root.is_absolute() or args.root.exists() or args.root.parent.resolve(strict=True) != source_root:
        parser.error("benchmark root must be an absent direct child of the existing registered source root")
    args.root.mkdir(mode=0o755)
    evidence = args.root / "evidence"
    evidence.mkdir(mode=0o700)
    result = {"protocol": 1, "candidateSha256": args.candidate_sha256, "profile": args.profile,
              "status": "RUNNING", "cases": [], "observerQueriesExecuted":0,
              "queryCounterScope": "server-global; exclusive-server attestation required"}
    proof = evidence / "proof.json"
    private_json(proof, result, exclusive=True)
    def stage(value: str) -> None:
        result['stage']=value
        private_json(proof,result)
        print('CDS_RESOURCE_STAGE',value,flush=True)
    client = evidence / "mysql-client.cnf"
    env = {key: value for key, value in os.environ.items()
           if key not in {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH"}}
    try:
        stage('PROTECTED_PROPERTIES')
        # This standalone companion parser has complete Java-properties semantics
        # without importing OSCAR19 migration manifests irrelevant to CDS.
        sys.path.insert(0, "/usr/lib/carlos-ctl")
        from carlos_ctl.o19_preflight import parse_properties_text
        jdbc = dict(parse_properties_text(protected_file(args.jdbc_properties).read_text(encoding="ISO-8859-1")))
        endpoint = urlsplit(jdbc["jdbcUrl"].removeprefix("jdbc:"))
        database = endpoint.path.removeprefix("/")
        if endpoint.scheme != "mysql" or not re.fullmatch(r"[A-Za-z0-9_]+_test", database) \
                or endpoint.username or endpoint.password or endpoint.query or endpoint.fragment:
            raise RuntimeError("only the existing explicit synthetic MySQL endpoint is permitted")
        escape = lambda value: '"' + str(value).replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r') + '"'
        content = "[client]\n" + "\n".join(key + "=" + escape(value) for key, value in
            {"host": endpoint.hostname, "port": endpoint.port or 3306, "user": jdbc["jdbcUser"], "password": jdbc["jdbcPassword"]}.items()) + "\n"
        stage('METRIC_CLIENT_CONFIGURATION')
        descriptor = os.open(client, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(descriptor, "w") as output:
            output.write(content)
        def query(statement: str) -> list[list[str]]:
            result['observerQueriesExecuted'] += 1
            completed = subprocess.run([args.mysql, "--defaults-file=" + str(client), "--batch", "--raw", "--skip-column-names", database,
                                        "-e", statement], env=env, capture_output=True, text=True, timeout=30, check=False)
            if completed.returncode or len(completed.stdout) > 1048576:
                matched=re.search(r'ERROR\s+([0-9]+)\s+\(([A-Z0-9]{5})\)',completed.stderr)
                result['metricQueryFailure']={'exitCode':completed.returncode,
                    'vendorCode':int(matched[1]) if matched else None,'sqlState':matched[2] if matched else None}
                raise RuntimeError("protected benchmark metric query failed; raw SQL and diagnostic withheld")
            return [row.split("\t") for row in completed.stdout.splitlines()]
        def counters() -> dict:
            return {key: int(value) for key, value in query("SHOW GLOBAL STATUS WHERE Variable_name IN "
                "('Questions','Com_select','Com_insert','Com_update','Com_delete','Threads_connected','Threads_running','Innodb_row_lock_time','Performance_schema_digest_lost')")}
        def statement_digests() -> dict:
            return statement_digest_snapshot(query(STATEMENT_DIGEST_SQL))
        stage('STATEMENT_DIGEST_INSTRUMENTATION')
        if query("SELECT @@performance_schema")[0]!=['1']:
            raise RuntimeError('the owned server must enable actual statement digest instrumentation before startup')
        if query("SELECT ENABLED FROM performance_schema.setup_consumers WHERE NAME='statements_digest'")!=[['YES']]:
            raise RuntimeError('actual statement digest consumer is unavailable')
        statement_digests()
        stage('DATABASE_BINDING_AND_ZERO_HOLD')
        if query("SELECT DATABASE()")[0] != [database] or query("SELECT COUNT(*) FROM cds_import_patient WHERE held=1")[0] != ["0"]:
            raise RuntimeError("benchmark database binding or zero-hold prerequisite failed")
        stage('CGROUP_CONTRACT')
        memory_max = Path('/sys/fs/cgroup/memory.max').read_text().strip()
        cpu_max = Path('/sys/fs/cgroup/cpu.max').read_text().split()
        if args.expected_memory_mib is not None and memory_max != str(args.expected_memory_mib*MIB) \
                or args.expected_cpus is not None and (len(cpu_max)!=2 or not all(value.isdecimal() for value in cpu_max)
                    or int(cpu_max[0])!=args.expected_cpus*int(cpu_max[1])):
            raise RuntimeError("effective cgroup limits do not match the measured workload contract")
        result['runtimeResources']={'memoryMax':memory_max,'cpuMax':cpu_max,'expectedHeapMiB':512,
            'maxLargestProcessRssBytes':args.max_rss_mib*MIB}
        def cgroup_counters():
            return {name:dict(line.split() for line in Path('/sys/fs/cgroup/'+name).read_text().splitlines())
                    for name in ('memory.events','cpu.stat')}
        result['initialCgroupCounters']=cgroup_counters()
        fixture = [args.java, "-Xmx128m", "-cp", args.classpath,
                   "io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsCliEngineFixture", "inspect", str(args.jdbc_properties)]
        stage('IMMUTABLE_FIXTURE_BINDING')
        verified = subprocess.run([*fixture[:-2],"verify-fixture",str(args.jdbc_properties),str(args.fixture_metadata.parent)],
            env=env,capture_output=True,text=True,timeout=60)
        if verified.returncode or len(verified.stdout)>65536 or json.loads(verified.stdout)!=metadata:
            result['fixtureFailureCodes']=[line for line in verified.stderr.splitlines()
                if re.fullmatch(r'CDS_SYNTHETIC_FIXTURE_FAILED:[A-Za-z0-9_$]+:[A-Za-z0-9_$]+:[0-9]+(?::sqlState=[A-Z0-9]{5}:vendorCode=-?[0-9]+)?',line)][:5]
            raise RuntimeError("installed configuration, verification database and immutable fixture binding differ")
        def inspect(run: str) -> dict:
            completed = subprocess.run([*fixture, run], env=env, capture_output=True, text=True, timeout=60)
            if completed.returncode or len(completed.stdout) > 65536:
                raise RuntimeError("protected native verification failed")
            return json.loads(completed.stdout)
        stage('READ_ONLY_STARTUP_SQL_CONTROL')
        control={'candidateSha256':args.candidate_sha256,'configurationSha256':digest(Path('/etc/carlos-emr/carlos.properties')),
                 'operation':'status of a fresh unknown submission; no import requested',
                 'scope':'exclusive per-command global counter window; includes counter observer overhead; no automatic budget subtraction',
                 'sameConfiguredNamespace':metadata['namespace'],'sameConfiguredProfile':metadata['profile']}
        result['startupSqlControl']=control
        baseline_native=inspect('fixture')
        ledger_statement="SELECT (SELECT COUNT(*) FROM cds_import_run),(SELECT COUNT(*) FROM cds_import_unit)," \
            "(SELECT COUNT(*) FROM cds_import_checkpoint),(SELECT COUNT(*) FROM cds_import_patient WHERE held=1)"
        baseline_ledger=query(ledger_statement)
        control_digests_before=statement_digests()
        control['beforeCounters']=counters()
        observer_before=result['observerQueriesExecuted']
        control_log=evidence/'startup-control.log'
        controlled_started=time.monotonic()
        with os.fdopen(os.open(control_log,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600),'wb') as output:
            controlled=subprocess.run([args.cli,'cds-import','--status-submission',str(uuid.uuid4()),'--provider',str(metadata['provider'])],
                                     env=env,stdout=output,stderr=subprocess.STDOUT,timeout=min(args.timeout,180))
        control.update(elapsedSeconds=time.monotonic()-controlled_started,exitCode=controlled.returncode)
        control['afterCounters']=counters()
        control['observerSqlInvocationsDuringWindow']=result['observerQueriesExecuted']-observer_before
        control['globalQueries']=control['afterCounters']['Questions']-control['beforeCounters']['Questions']
        control['statementDigests']=statement_digest_delta(control_digests_before,statement_digests())
        control['operatorEvidence']=operator_projection(control_log,args.fixture_metadata.parent/'state/runs/cli-diagnostics')
        private_json(proof,result)
        require_startup_control(controlled.returncode,control['operatorEvidence'])
        if control['globalQueries']<0 or inspect('fixture')!=baseline_native or query(ledger_statement)!=baseline_ledger \
                or digest(Path('/etc/carlos-emr/carlos.properties'))!=control['configurationSha256']:
            raise RuntimeError('startup control changed clinical/registry/configuration state or counters reset')
        control.update(status='VERIFIED_READ_ONLY',nativeAndLedgerUnchanged=True)
        private_json(proof,result)
        token = uuid.uuid4().hex
        service_account = pwd.getpwnam('carlos')
        expected_java = Path(args.java).resolve(strict=True)
        definitions = [(f"patients-{count}", count, 1, 0, False) for count in (1, 8, 32)] \
            + [(f"chart-{count}", 1, count, 0, False) for count in (128, 512, 2048)] \
            + [(f"payload-{size}", 1, 0, size * MIB, False) for size in (8, 32, 64)]
        if args.profile == "aggregate20":
            definitions = [("aggregate20", 320, 0, 64 * MIB, True)]
        elif args.profile == "zip64":
            definitions = [("zip64", 1, 0, 5 * GIB, True)]
        # Large profiles use compressed producer ZIPs plus physical snapshots and publications.
        # This conservative admission never substitutes logical sizes for real usable storage.
        required = 3 * GIB if args.profile == "scaling" else 48 * GIB if args.profile == "aggregate20" else 18 * GIB
        stage('STORAGE_ADMISSION')
        result["storageAdmission"] = storage_admission(args.root, required)
        for index, (label, patients, records, payload, archive) in enumerate(definitions):
            result['currentCase']=label
            stage('SOURCE_GENERATION')
            validator=(lambda source:production_workload_validation(source,evidence/(label+'-validation'),args.java,args.classpath)) \
                if payload and not archive else None
            source = generate_case(args.root, label, token[:6] + f"{index:04X}", patients, records, payload, archive=archive,validate_xml=validator)
            private_json(evidence / (label + "-source.json"), source, exclusive=True)
            case_digests_before=statement_digests()
            before = counters()
            case = {"label": label, "sourceBytes": source["sourceBytes"], "documentBytes": source["documentBytes"],
                    "expectedPatients": patients, "expectedNotes": source["notes"], "expectedLabs": source["labs"],
                    "expectedDocuments": source["documents"], "beforeCounters": before, "status": "RUNNING",
                    "acceptedRunIds":[],"submissionKeys":[],"phase":"PREPARED"}
            case['sourceValidation']=source['sourceValidation']
            case['resourceObservations']={'intervalSeconds':1,'maxObservedPendingUnits':None,
                'sampledPeaksAreLowerBounds':True,'sampledMinimaAreUpperBounds':True,
                'databaseScope':'exclusive server-global; includes observer connections/queries',
                'transactionMetric':'sampled oldest active transaction AGE; not committed transaction latency',
                'inventoryCountsUnknownUntilSealed':True}
            observer_before = result['observerQueriesExecuted']
            observation_started = time.monotonic()
            observed_at = 0.0
            def observe_resources():
                nonlocal observed_at
                handle = case['acceptedRunIds'][0] if case['acceptedRunIds'] else None
                value = resource_observation(query,args.root,handle)
                value['elapsedSinceObservationStartSeconds']=time.monotonic()-observation_started
                retain_resource_observation(case['resourceObservations'],value)
                case['observerQueriesDuringImport']=result['observerQueriesExecuted']-observer_before
                observed_at = time.monotonic()
                private_json(proof,result)
            result["cases"].append(case)
            stage('INSTALLED_IMPORT')
            observe_resources()
            log = evidence / (label + ".log")
            fd = os.open(log, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            common = ["--provider", str(metadata["provider"])]
            request = [args.cli, "cds-import", "--input", source["source"], *common,
                       "--source-namespace", metadata["namespace"], "--source-profile", metadata["profile"], "--no-match-providers"]
            started = time.monotonic()
            peak = samples = 0
            worker = gc = process = None
            gc_file = gc_error_file = None
            resource_receipt = evidence / (label + "-kernel-resources.json")
            log_offset = 0
            def bindings():
                nonlocal log_offset
                changed = False
                with log.open(encoding="utf-8") as incoming:
                    incoming.seek(log_offset)
                    for _ in range(200000):
                        before_line = incoming.tell()
                        line = incoming.readline(65538)
                        if not line:
                            break
                        if len(line) > 65536:
                            raise RuntimeError("operational row exceeded its bound")
                        if not line.endswith("\n"):
                            incoming.seek(before_line)
                            break
                        if line.startswith('{'):
                            value = json.loads(line)
                            for event,key,target in (("ACCEPTED","runId","acceptedRunIds"),("SUBMISSION","submissionKey","submissionKeys")):
                                if value.get("event") == event:
                                    handle = value.get(key)
                                    if not isinstance(handle,str) or not UUID.fullmatch(handle):
                                        raise RuntimeError("invalid durable command handle")
                                    if handle not in case[target]:
                                        case[target].append(handle)
                                        case.setdefault('observedMilestoneCounters',{})[event]={
                                            'elapsedSeconds':time.monotonic()-started,'globalCounters':counters(),
                                            'scope':'exclusive server; sampled when this command message was observed; includes observers'}
                                        case['observedMilestoneCounters'][event]['statementDigestsSinceCaseStart']= \
                                            statement_digest_delta(case_digests_before,statement_digests())
                                        changed = True
                                    if len(case[target]) != 1:
                                        private_json(proof,result)
                                        raise RuntimeError("ambiguous durable command handles; explicit reconciliation required")
                    log_offset = incoming.tell()
                if changed:
                    case["phase"] = "ACCEPTED" if case["acceptedRunIds"] else "SUBMITTED"
                    private_json(proof,result)
            try:
                with os.fdopen(fd, "wb") as output:
                    case["phase"] = "IMPORT_STARTED"
                    process = subprocess.Popen([sys.executable,str(Path(__file__).resolve()),"--supervise",str(resource_receipt),"--",*request],
                                               env=env, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
                    while process.poll() is None:
                        bindings()
                        if time.monotonic()-observed_at >= 1:
                            observe_resources()
                        if time.monotonic() - started > args.timeout:
                            os.killpg(process.pid, signal.SIGTERM)
                            raise RuntimeError("benchmark deadline exceeded; retain exact accepted run and reconcile")
                        # Only descendants of this exact installed supervisor can become the measured JVM.
                        parents = {}
                        for entry in Path("/proc").iterdir():
                            if not entry.name.isdecimal():
                                continue
                            try:
                                fields = (entry / "stat").read_text().rsplit(")", 1)[1].split()
                                parents[int(entry.name)] = int(fields[1])
                            except FileNotFoundError:
                                pass
                        children = {process.pid}
                        for _ in range(8):
                            children |= {pid for pid, parent in parents.items() if parent in children}
                        candidates = []
                        for pid in children:
                            try:
                                command = Path(f"/proc/{pid}/cmdline").read_bytes().split(b"\0")
                                if is_import_command(command,source["source"],str(metadata["provider"])):
                                    process_status = Path(f'/proc/{pid}/status').read_text()
                                    uid = int(re.search(r'^Uid:\s+(\d+)',process_status,re.M)[1])
                                    if uid == service_account.pw_uid and uid != 0:
                                        candidates.append((pid,command))
                            except FileNotFoundError:
                                pass
                        if len(candidates) > 1:
                            raise RuntimeError("ambiguous service JVM measurement target")
                        if candidates:
                            pid, engine_command = candidates[0]
                            observed = service_sample(pid)
                            if observed is None:
                                time.sleep(0.01)
                                continue
                            process_started, rss, high_water = observed['started'], observed['rss'], observed['highWater']
                            peak = max(peak, rss, high_water)
                            samples += 1
                            case.update(peakRssBytes=peak,rssSamples=samples,rssBudgetBytes=args.max_rss_mib*MIB)
                            if peak > args.max_rss_mib * MIB:
                                raise RuntimeError("actual installed JVM exceeded its measured RSS budget")
                            if worker is None:
                                worker = (pid,process_started)
                                if engine_command.count(b'-Xmx512m') != 1:
                                    raise RuntimeError("measured installed service heap differs from the frozen512MiB workload budget")
                                uid = observed['uid']
                                service = pwd.getpwuid(uid).pw_name
                                jstat = Path(args.java).resolve().parent / "jstat"
                                if uid == 0 or service != "carlos" or not jstat.is_file():
                                    raise RuntimeError("non-root service JDK GC instrumentation is unavailable")
                                verify_java_identity(pid,uid,service_account.pw_gid,expected_java,process_started)
                                case['serviceJvm']={'pid':pid,'startTimeTicks':process_started,'uid':uid,'javaExecutableSha256':digest(expected_java)}
                                private_json(proof,result)
                                gc_file = os.fdopen(os.open(evidence / (label + "-gc.log"), os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "wb")
                                gc_error_file = os.fdopen(os.open(evidence / (label + "-gc-errors.log"), os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "wb")
                                gc = subprocess.Popen([str(jstat), "-gc", "-t", str(pid), "1000"], user=uid,
                                                      group=pwd.getpwuid(uid).pw_gid,extra_groups=[],
                                                      env=env, stdout=gc_file, stderr=gc_error_file)
                            elif worker != (pid,process_started):
                                raise RuntimeError("service JVM identity changed during a measured import")
                        time.sleep(0.1)
                    import_elapsed = time.monotonic()-started
                    case['importElapsedSeconds']=import_elapsed
                    bindings()
                    observe_resources()
                    if process.returncode != 0:
                        raise RuntimeError("installed benchmark import refused or failed; preserve accepted evidence")
            finally:
                if process is not None and process.poll() is None:
                    case["terminationRequested"] = True
                    try:
                        os.killpg(process.pid, signal.SIGTERM)
                        process.wait(timeout=60)
                    except (ProcessLookupError, subprocess.TimeoutExpired):
                        pass
                    case["terminationConfirmed"] = process.poll() is not None
                    case["outcomeRequiresReconciliation"] = True
                    bindings()
                    private_json(proof, result)
                if gc is not None:
                    gc.terminate()
                    gc.wait(timeout=10)
                if gc_file is not None:
                    gc_file.close()
                if gc_error_file is not None:
                    gc_error_file.close()
                case['operatorExitCode']=process.returncode if process is not None else None
                capture_evidence(case,'operatorEvidence',lambda:operator_projection(log,args.fixture_metadata.parent/'state/runs/cli-diagnostics'))
                if resource_receipt.exists():
                    capture_evidence(case,'kernelResources',lambda:json.loads(protected_file(resource_receipt).read_text()))
                if (evidence/(label+'-gc.log')).exists():
                    capture_evidence(case,'gc',lambda:gc_evidence(evidence/(label+'-gc.log')))
                if case['acceptedRunIds']:
                    accepted=case['acceptedRunIds'][0]
                    def diagnostic_codes():
                        codes=query("SELECT severity,code,stage,blocking,resolved,COUNT(*) FROM cds_import_diagnostic WHERE run_id='"
                                    +accepted+"' GROUP BY severity,code,stage,blocking,resolved ORDER BY severity,code,stage,blocking,resolved LIMIT 129")
                        if len(codes)>128 or any(len(row)!=6 or not re.fullmatch(r'[A-Z]{1,16}',row[0])
                            or not re.fullmatch(r'[A-Z][A-Z0-9_]{0,95}',row[1]) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,64}',row[2])
                            or row[3] not in ('0','1') or row[4] not in ('0','1') or not row[5].isdecimal() for row in codes):
                            raise RuntimeError('diagnostic code projection exceeded its finite safe contract')
                        return [dict(zip(('severity','code','stage','blocking','resolved','count'),row)) for row in codes]
                    capture_evidence(case,'diagnosticCodeCounts',diagnostic_codes)
                    if process.returncode!=0:
                        capture_evidence(case,'failedNative',lambda:inspect(accepted))
                private_json(proof,result)
                capture_evidence(case,'statementDigests',lambda:statement_digest_delta(case_digests_before,statement_digests()))
                private_json(proof,result)
            if not samples or worker is None:
                raise RuntimeError("actual installed JVM resident-memory evidence is missing")
            gc_metrics = gc_evidence(evidence / (label+"-gc.log"))
            kernel = json.loads(protected_file(resource_receipt).read_text())
            case['gc']=gc_metrics
            case['kernelResources']=kernel
            private_json(proof,result)
            case['enforcedPeakRssBytes']=enforce_rss_budget(peak,kernel,args.max_rss_mib*MIB)
            after = counters()
            case['afterCounters']=after
            if 'statementDigests' not in case or after.get('Performance_schema_digest_lost')!=0:
                raise RuntimeError('statement digest evidence is missing or actual server digest inventory overflowed')
            stage('NATIVE_AND_REQUIRED_REPORT_VERIFICATION')
            completed = complete_result(rows(log))
            native = inspect(completed["runId"])
            case['native']=native
            private_json(proof,result)
            if native.get("nativeTargets") != patients or native.get("holds") != 0 \
                    or native.get("nativeNotes") != source["notes"] or native.get("nativeLaboratoryResults") != source["labs"] \
                    or native.get("nativeLaboratoryProjection") != source["labs"] \
                    or native.get("nativeLaboratoryPrivateNoteIsolated") != source["labs"] \
                    or native.get("nativeDocuments") != source["documents"] or native.get("checkpoints") != native.get("expectedUnits"):
                raise RuntimeError("native record/checkpoint completeness does not match the generated workset")
            if completed.get('sourceBytes') != source['sourceBytes']:
                raise RuntimeError("engine discovery bytes do not match the physically generated source inventory")
            run_id = completed["runId"]
            # Independently verify native file bytes against generated originals, not registry totals alone.
            expected_documents = Counter((row["bytes"], row["sha256"]) for row in source["members"] if row["document"])
            if archive and (len(expected_documents)!=patients or sum(expected_documents.values())!=patients):
                raise RuntimeError("large archive profile did not generate distinct original document bytes per patient")
            if args.profile=='aggregate20' and source['documentBytes']<20*GIB \
                    or args.profile=='zip64' and not any(row['document'] and row['bytes']>0xffffffff for row in source['members']):
                raise RuntimeError("mandatory large profile did not process its actual promised byte size")
            if payload and not archive:
                expected_documents += expected_documents  # separate inline and external source occurrences
            actual_documents = Counter()
            selected_filenames = set()
            document_root = args.fixture_metadata.parent / "documents"
            for (filename,) in query("SELECT d.docfilename FROM document d JOIN ctl_document c ON c.document_no=d.document_no "
                    "AND c.module='demographic' AND c.status='A' WHERE EXISTS(SELECT 1 FROM cds_import_patient p WHERE p.run_id='"
                    + run_id + "' AND p.target_demographic_no=c.module_id)"):
                location = document_root / filename
                if filename in selected_filenames:
                    raise RuntimeError("distinct native source occurrences unexpectedly share one publication filename")
                selected_filenames.add(filename)
                if location.is_symlink() or not location.is_file() or location.resolve().parent != document_root.resolve():
                    raise RuntimeError("native document escaped the owned fixture document store")
                actual_documents[(location.stat().st_size, digest(location))] += 1
            if actual_documents != expected_documents:
                raise RuntimeError("native document bytes do not reconcile the exact original payload multiset")
            case['nativeDocumentBytesVerified']=sum(size*count for (size,_),count in actual_documents.items())
            case['verifiedNativeDocumentHashMultiset']=[{'bytes':size,'sha256':sha256,'occurrences':count}
                for (size,sha256),count in sorted(actual_documents.items())]
            private_json(proof,result)
            report_log = evidence / (label + "-reports.log")
            with os.fdopen(os.open(report_log, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "wb") as output:
                delivered = subprocess.run([args.cli, "cds-import", "--report", run_id, *common], env=env,
                    stdout=output, stderr=subprocess.STDOUT, timeout=args.timeout)
            reports = [row for row in rows(report_log) if row.get("event") == "PROTECTED_REPORTS"]
            if delivered.returncode or len(reports) != 1 or set(reports[0].get("reports", {})) != {"event", "error", "receipt", "units"}:
                raise RuntimeError("complete protected report set is missing")
            report_proof = {}
            for kind, entry in reports[0]["reports"].items():
                location = Path(entry["protectedPath"])
                if not location.is_absolute() or location.is_symlink() or not location.is_file() \
                        or not location.resolve().is_relative_to(args.fixture_metadata.parent.resolve()) \
                        or location.stat().st_size != entry["bytes"] or digest(location) != entry["sha256"]:
                    raise RuntimeError("protected report delivery size/hash/binding mismatch")
                report_proof[kind] = {"bytes": entry["bytes"], "sha256": entry["sha256"]}
                if kind == "event":
                    totals = Counter()
                    patient_ids = set()
                    with location.open(encoding="utf-8") as stream:
                        for line in stream:
                            if len(line) > 65536:
                                raise RuntimeError("event occurrence row exceeded its bound")
                            row = json.loads(line)
                            if row.get("held") is not False or row.get("inventoryComplete") is not True \
                                    or any(row["notCommittedOccurrences"].values()) or row["patientId"] in patient_ids:
                                raise RuntimeError("event receipt is held, incomplete, duplicated or missing work")
                            patient_ids.add(row["patientId"])
                            totals.update(row["importedOccurrences"])
                    if len(patient_ids) != patients or totals["Demographics"] != patients \
                            or totals["ClinicalNotes"] != source["notes"] or totals["LaboratoryResults"] != source["labs"] \
                            or totals["ReportsBinary"] != source["documents"]:
                        raise RuntimeError("source category event occurrences do not match generated work")
                if kind == "receipt":
                    if location.stat().st_size > 2 * MIB:
                        raise RuntimeError("completion receipt exceeds the bounded metadata contract")
                    receipt = json.loads(location.read_text())
                    if receipt.get("runId") != run_id or receipt.get("cleanComplete") is not True \
                            or receipt.get("manifestDigest") != query("SELECT manifest_digest FROM cds_import_run WHERE id='" + run_id + "'")[0][0]:
                        raise RuntimeError("completion receipt does not bind the actual sealed run")
                if kind == "error":
                    with location.open(encoding="utf-8") as stream:
                        for line in stream:
                            if len(line) > 65536:
                                raise RuntimeError("error receipt row exceeded its bound")
                            row = json.loads(line)
                            if row.get("blocking") is True and row.get("resolved") is False:
                                raise RuntimeError("clean benchmark receipt has unresolved blocking diagnostics")
            query_delta = after["Questions"] - before["Questions"]
            case.update(reports=report_proof,globalQueries=query_delta,
                        queriesPerExpectedUnit=query_delta/max(1,native['expectedUnits']),
                        globalQueryBudget=args.max_queries_per_unit*max(1,native['expectedUnits']))
            private_json(proof,result)
            case['completedCgroupCounters']=cgroup_counters()
            private_json(proof,result)
            require_no_cgroup_oom(result['initialCgroupCounters'],case['completedCgroupCounters'])
            budget_status=verified_query_budget_status(query_delta,case['globalQueryBudget'])
            case.update({"status": budget_status, "phase":"VERIFIED", "runId": completed["runId"], "importElapsedSeconds": import_elapsed,
                         "verificationElapsedSeconds":time.monotonic()-started-import_elapsed,
                         "sourceBytesPerImportSecond":source["sourceBytes"]/import_elapsed,
                         "peakRssBytes": peak, "rssSamples": samples, "rssBudgetBytes": args.max_rss_mib*MIB,
                         "native": native, "afterCounters": after, "globalQueries": query_delta,
                         "nativeDocumentBytesVerified": sum(size * count for (size, _), count in actual_documents.items()),
                         "distinctDocumentDigests":len(expected_documents),
                         "reports": report_proof,
                         "queriesPerExpectedUnit": query_delta/max(1, native["expectedUnits"]),
                         "gc":gc_metrics,"kernelResources":kernel,"remainingDiskBytes": shutil.disk_usage(args.root).free})
            private_json(proof, result)
            print('PASS' if budget_status=='PASSED' else 'FAIL_QUERY_BUDGET', label, "run=" + completed["runId"], "sourceBytes=" + str(source["sourceBytes"]),
                  "peakRssBytes=" + str(peak), flush=True)
        result["status"] = "PASSED"
        result['finalCgroupCounters']=cgroup_counters()
        result['cgroupMemoryPeakIncludingFileCache']=Path('/sys/fs/cgroup/memory.peak').read_text().strip()
        require_no_cgroup_oom(result['initialCgroupCounters'],result['finalCgroupCounters'])
        result['queryBudgetFailureCases']=[case['label'] for case in result['cases'] if case['status']=='FAILED_QUERY_BUDGET']
        if result['queryBudgetFailureCases']:
            raise RuntimeError('one or more fully verified independent imports exceeded the unchanged query budget')
        private_json(proof, result)
    except Exception as failure:
        result["status"] = "FAILED"
        result['failure']=safe_failure(failure)
        if result["cases"] and result["cases"][-1].get("status") == "RUNNING":
            result["cases"][-1].update(status="FAILED",failureClass=type(failure).__name__,outcomeRequiresReconciliation=True)
        private_json(proof, result)
        raise RuntimeError("CDS performance gate failed; inspect protected evidence; never reset or resubmit accepted work") from None
    finally:
        if 'initialCgroupCounters' in result:
            capture_evidence(result,'finalCgroupCounters',cgroup_counters)
            capture_evidence(result,'cgroupMemoryPeakIncludingFileCache',lambda:Path('/sys/fs/cgroup/memory.peak').read_text().strip())
            private_json(proof,result)
        client.unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        if len(sys.argv) >= 5 and sys.argv[1] == "--supervise" and sys.argv[3] == "--":
            raise SystemExit(supervise(Path(sys.argv[2]),sys.argv[4:]))
        main()
    except RuntimeError as failure:
        print(str(failure), flush=True)
        raise SystemExit(1) from None
