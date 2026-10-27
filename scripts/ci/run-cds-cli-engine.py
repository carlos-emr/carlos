#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Opt-in installed CLI gate; requires a disposable root container and synthetic DB.

Fixture preparation uses the real JDBC model and protected properties file. All
imports run through the installed carlos-ctl and its real root/service boundary.
No credential, source XML or clinical report payload is printed by this script.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import pwd
import resource
import re
import shutil
import signal
import subprocess
import sys
import time
import uuid
from pathlib import Path

FIXTURE = "io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsCliEngineFixture"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True, help="real test-fixture helper plus candidate JDBC dependencies")
    parser.add_argument("--jdbc-properties", type=Path, required=True, help="protected synthetic JDBC properties; never credentials in argv")
    parser.add_argument("--fixture-root", type=Path, required=True)
    parser.add_argument("--java", default="/usr/bin/java")
    parser.add_argument("--cli", default="/usr/sbin/carlos-ctl")
    parser.add_argument("--disposable", action="store_true")
    parser.add_argument("--prepared-fixture", action="store_true", help="continue verified preparation only when no run has been accepted")
    parser.add_argument("--recorded-attempt", type=Path, help="continue completed exact recorded import handles without resubmitting them")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    if os.geteuid() != 0 or not args.disposable or not Path("/.dockerenv").is_file():
        parser.error("requires root in an explicitly disposable Docker container")
    if args.timeout < 30 or args.timeout > 3600 or not args.fixture_root.is_absolute():
        parser.error("timeout must be 30..3600 seconds and fixture root must be absolute")
    if args.prepared_fixture and args.recorded_attempt:
        parser.error("choose exactly one continuation mode")
    continuing = args.prepared_fixture or args.recorded_attempt is not None
    if args.fixture_root.is_symlink() or (args.fixture_root.exists() and not continuing):
        parser.error("fixture root must be absent unless explicitly verifying an unaccepted preparation")
    if not args.jdbc_properties.is_file() or args.jdbc_properties.is_symlink():
        parser.error("protected JDBC properties must be a regular file")
    if args.jdbc_properties.stat().st_mode & 0o077:
        parser.error("JDBC properties must not be group/other accessible")
    if not Path(args.cli).is_file() or shutil.which(args.java) is None:
        parser.error("actual installed CLI and Java runtime are required")
    # Only the helper may create the fixed production properties, and it refuses
    # an existing configuration and non-test database before any mutation.
    if Path("/etc/carlos-emr/carlos.properties").exists() and not continuing:
        parser.error("refusing to replace existing production configuration")
    environment = {key: value for key, value in os.environ.items()
                   if key not in {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH"}}
    helper = [args.java, "-Xmx128m", "-cp", args.classpath, FIXTURE]

    def fixture(operation: str, *values: str) -> dict:
        completed = subprocess.run([*helper, operation, str(args.jdbc_properties), *values], env=environment,
                                   text=True, capture_output=True, timeout=args.timeout, check=False)
        if completed.returncode or len(completed.stdout.encode()) > 65536:
            for line in completed.stderr[-65536:].splitlines():
                if re.fullmatch(r"CDS_SYNTHETIC_FIXTURE_FAILED:[A-Za-z0-9_$]+:[A-Za-z0-9_$]+:[0-9]+"
                                r"(?::sqlState=[A-Z0-9]{5}:vendorCode=-?[0-9]+)?", line):
                    print(line, file=sys.stderr)
            raise RuntimeError("synthetic fixture operation failed; no raw JDBC diagnostic is printed")
        document = json.loads(completed.stdout)
        if not isinstance(document, dict):
            raise RuntimeError("synthetic fixture metadata was not an object")
        return document

    operation = "verify-fixture" if args.recorded_attempt else ("verify-prepared" if args.prepared_fixture else "prepare")
    metadata = fixture(operation, str(args.fixture_root))
    root = args.fixture_root.resolve(strict=True)
    recorded = args.recorded_attempt
    if recorded is not None:
        if not recorded.is_absolute() or recorded.parent != root or recorded.is_symlink() \
                or not re.fullmatch(r"operator-evidence(?:-continuation-[a-f0-9]{32}|-recorded-[a-f0-9]{32})?", recorded.name) \
                or not recorded.is_dir() or recorded.stat().st_uid != 0 or recorded.stat().st_mode & 0o077:
            raise RuntimeError("recorded attempt must be the protected original fixture evidence directory")
        if (recorded / "interrupted.log").exists():
            raise RuntimeError("interrupted or partial runs require their explicit recorded recovery path")
    logs = root / ("operator-evidence-recorded-" + uuid.uuid4().hex if recorded else
                   "operator-evidence-continuation-" + uuid.uuid4().hex if args.prepared_fixture else "operator-evidence")
    logs.mkdir(mode=0o700)
    common = ["--provider", str(metadata["provider"])]
    contract = ["--source-namespace", metadata["namespace"], "--source-profile", metadata["profile"], "--no-match-providers"]
    runs: dict[str, str] = {}
    submissions: dict[str, str] = {}

    def path_from(value: str) -> Path:
        path = Path(value)
        if not path.is_absolute() or path.is_symlink() or not path.resolve().is_relative_to(root):
            raise RuntimeError("fixture supplied a path outside its private root")
        return path

    def rows(path: Path) -> list[dict]:
        values = []
        with path.open("r", encoding="utf-8") as stream:
            for _ in range(4096):
                line = stream.readline(65538)
                if not line:
                    break
                if len(line) > 65536:
                    raise RuntimeError("CLI operational row exceeded its documented bound")
                if not line.endswith("\n"):
                    # A foreground println may still be in flight while the
                    # cancellation gate waits for its accepted handle.
                    break
                if line.startswith("{"):
                    value = json.loads(line)
                    if not isinstance(value, dict):
                        raise RuntimeError("CLI operational row was not an object")
                    if value.get("event") not in {"INITIALIZING", "SUBMISSION", "ACCEPTED", "PROGRESS", "RESULT", "PROTECTED_REPORTS"}:
                        raise RuntimeError("unexpected JSON was emitted into the operational console")
                    if value.get("event") != "PROGRESS":
                        values.append(value)
            else:
                raise RuntimeError("CLI evidence exceeded the bounded gate limit")
        return values

    def stop(process: subprocess.Popen) -> None:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=30)
                raise RuntimeError("forced CLI termination requires durable reconciliation; gate stopped")

    def hard_crash_engine(process: subprocess.Popen, run: str, observed: dict, label: str) -> None:
        # runuser and Java have their own session. Killing the outer CLI group
        # would strand a live engine; target only the exact verified service JVM.
        def children_of(parent: int) -> list[int]:
            found = []
            seen = 0
            for entry in Path("/proc").iterdir():
                if not entry.name.isdecimal():
                    continue
                seen += 1
                if seen > 4096:
                    raise RuntimeError("process tree exceeded the bounded hard-crash lookup")
                try:
                    fields = (entry / "stat").read_text().rsplit(")", 1)[1].split()
                except FileNotFoundError:
                    continue
                if int(fields[1]) == parent:
                    found.append(int(entry.name))
            return found

        children = children_of(process.pid)
        if len(children) != 1:
            raise RuntimeError("hard-crash target has an ambiguous supervisor child")
        runuser = int(children[0])
        if Path(f"/proc/{runuser}/comm").read_text().strip() != "runuser" \
                or Path(f"/proc/{runuser}/exe").resolve(strict=True) != Path("/usr/sbin/runuser").resolve(strict=True):
            raise RuntimeError("hard-crash target is not the expected runuser supervisor")
        descendants = children_of(runuser)
        if len(descendants) != 1:
            raise RuntimeError("hard-crash target has ambiguous service descendants")
        service = pwd.getpwnam("carlos")
        script = r"""
import hashlib,json,os,signal,stat,struct,sys,time
from pathlib import Path
pid,parent,wrapper,uid=map(int,sys.argv[1:5]); exe,run,provider=sys.argv[5:8]
def start(number):
    return Path(f'/proc/{number}/stat').read_text().rsplit(')',1)[1].split()[19]
expected_start=start(pid); parent_start=start(parent)
with os.fdopen(os.open(f'/proc/{pid}/status',os.O_RDONLY)) as source: status=source.read(65537)
assert len(status)<=65536
fields=dict(line.split(':',1) for line in status.splitlines() if ':' in line)
assert int(fields['PPid'])==parent and set(map(int,fields['Uid'].split()))=={uid}
assert Path(f'/proc/{pid}/exe').resolve(strict=True)==Path(exe).resolve(strict=True)
assert Path(f'/proc/{parent}/stat').read_text().rsplit(')',1)[1].split()[1]==str(wrapper)
argv=Path(f'/proc/{pid}/cmdline').read_bytes(); assert len(argv)<=65536
argv=[value.decode('utf-8') for value in argv.rstrip(b'\0').split(b'\0')]
main='io.github.carlos_emr.carlos.demographic.cdsimport.CdsImport'
assert argv.count(main)==1
arguments=argv[argv.index(main)+1:]
assert arguments==['--operation','resume','--provider',provider,'--run',run]
env=Path(f'/proc/{pid}/environ').read_bytes(); assert len(env)<=65536
values=[value for value in env.split(b'\0') if value.startswith(b'CARLOS_CDS_DELEGATION_TICKET=')]
assert len(values)==1
ticket=Path(values[0].split(b'=',1)[1].decode('utf-8'))
assert ticket.parent==Path('/run/carlos-cds-delegations') and ticket.resolve(strict=True)==ticket
for path in (Path('/run'),ticket.parent):
    entry=path.lstat(); assert stat.S_ISDIR(entry.st_mode) and entry.st_uid==0 and not entry.st_mode&0o022
entry=ticket.lstat(); assert stat.S_ISREG(entry.st_mode) and entry.st_uid==0 and entry.st_gid==os.getgid() and stat.S_IMODE(entry.st_mode)==0o640
payload=ticket.read_bytes(); assert len(payload)<=65536
proof=json.loads(payload); digest=hashlib.sha256()
for argument in arguments:
    encoded=argument.encode('utf-8'); digest.update(struct.pack('>I',len(encoded))); digest.update(encoded)
assert proof['protocol']==1 and proof['operation']=='resume' and proof['provider']==provider and proof['operator']=='root' and proof['operatorUid']==0
assert proof['argvSha256']==digest.hexdigest() and ticket.name==proof['nonce']+'.json'
assert 0<proof['expiresEpochSeconds']-proof['issuedEpochSeconds']<=300 and proof['issuedEpochSeconds']<=int(time.time())
fd=os.pidfd_open(pid)
try:
    assert start(pid)==expected_start and start(parent)==parent_start
    signal.pidfd_send_signal(fd,signal.SIGKILL)
finally: os.close(fd)
print(json.dumps({'signal':'SIGKILL','verifiedServiceUid':uid,'javaPid':pid,'processStart':expected_start,'nonce':proof['nonce'],'ticket':str(ticket)}))
"""
        command = ["setpriv", "--reuid=" + str(service.pw_uid), "--regid=" + str(service.pw_gid), "--clear-groups",
                   "--bounding-set=-all", "--inh-caps=-all", "--ambient-caps=-all", "--no-new-privs", "python3", "-c", script,
                   str(descendants[0]), str(runuser), str(process.pid), str(service.pw_uid), args.java, run, str(metadata["provider"])]
        killed = subprocess.run(command, env=environment, capture_output=True, timeout=30, check=False)
        if killed.returncode or len(killed.stdout) > 4096:
            raise RuntimeError("exact service JVM hard-crash proof failed; no alternate process was targeted")
        proof = json.loads(killed.stdout)
        if process.wait(timeout=30) != 137 or Path(proof["ticket"]).exists():
            raise RuntimeError("hard-crashed JVM supervisor did not report SIGKILL and retire only its ticket")
        proof.update({"runId": run, "checkpointsBeforeCrash": observed["checkpoints"],
                      "nativeNotesBeforeCrash": observed["nativeNotes"], "expectedUnits": observed["expectedUnits"]})
        descriptor = os.open(logs / (label + "-proof.json"), os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as evidence:
            json.dump(proof, evidence, separators=(",", ":"))

    def invoke(label: str, options: list[str], expected: set[int], *, interrupt: bool = False,
               resume_run: str | None = None, minimum_checkpoints: int = 0, minimum_notes: int = 0,
               hard_crash: bool = False) -> list[dict]:
        log = logs / (label + ".log")
        descriptor = os.open(log, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        process = None
        try:
            with os.fdopen(descriptor, "wb") as output:
                process = subprocess.Popen([args.cli, "cds-import", *options], env=environment,
                                           stdout=output, stderr=subprocess.STDOUT, start_new_session=True, cwd="/")
                if interrupt:
                    deadline = time.monotonic() + args.timeout
                    while time.monotonic() < deadline:
                        accepted = [row for row in rows(log) if row.get("event") == "ACCEPTED"]
                        if accepted:
                            runs[label] = accepted[-1]["runId"]
                        target = runs.get(label) or resume_run
                        if target is not None:
                            observed = fixture("inspect", target)
                            committed = observed.get("checkpoints", 0)
                            expected_units = observed.get("expectedUnits", 0)
                            if observed.get("nativeNotes", 0) > minimum_notes and minimum_checkpoints < committed < expected_units:
                                # Require new actual clinical work and outstanding
                                # units before either graceful stop or hard crash.
                                if hard_crash:
                                    hard_crash_engine(process, target, observed, label)
                                else:
                                    stop(process)
                                break
                        if process.poll() is not None:
                            raise RuntimeError("cancel fixture completed before a partial clinical import could be interrupted")
                        time.sleep(0.05)
                    else:
                        raise RuntimeError("accepted handle was not delivered within the gate deadline")
                code = process.wait(timeout=args.timeout)
                if code not in expected:
                    raise RuntimeError(label + " returned unexpected exit " + str(code) + "; inspect protected operator evidence")
        finally:
            if process is not None:
                stop(process)
        return rows(log)

    def result(values: list[dict]) -> dict:
        selected = [row for row in values if row.get("event") == "RESULT"]
        if len(selected) != 1:
            raise RuntimeError("CLI did not deliver exactly one terminal operational result")
        return selected[0]

    def submit(label: str, source: str, *extra: str, clean: bool = True, selected_provider: str | None = None) -> dict:
        source_path = path_from(source)
        provider = ["--provider", selected_provider or str(metadata["provider"])]
        request = ["--input", str(source_path), *provider, *contract, *extra]
        prior = recorded / (label + ".log") if recorded else None
        if prior is not None and prior.exists():
            if prior.is_symlink() or not prior.is_file() or prior.stat().st_uid != 0 or prior.stat().st_mode & 0o077:
                raise RuntimeError("recorded command evidence is not protected")
            values = rows(prior)
            handles = [row for row in values if row.get("event") == "ACCEPTED"]
            keys = [row for row in values if row.get("event") == "SUBMISSION"]
            if not clean or len(handles) != 1 or len(keys) != 1 or result(values).get("cleanComplete") is not True:
                raise RuntimeError("unknown or incomplete accepted work requires explicit recorded-run reconciliation")
            run = handles[0]["runId"]
            bound = fixture("verify-run", str(root), run, "--operation", "import", *request)
            if bound.get("runId") != run or bound.get("verified") is not True or bound.get("submissionKey") != keys[0].get("submissionKey"):
                raise RuntimeError("recorded command binding did not match the immutable accepted job")
            current = result(invoke(label + "-recorded-status", ["--status", run, *provider], {0}))
            if current.get("runId") != run or current.get("cleanComplete") is not True:
                raise RuntimeError("recorded accepted run no longer has a complete clean outcome")
            # Preserve the original acceptance evidence in every continuation.
            # A later continuation must reuse this exact handle rather than see
            # an absent log and create another submission.
            destination = logs / (label + ".log")
            source_hash = hashlib.sha256()
            descriptor = os.open(destination, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            with prior.open("rb") as source, os.fdopen(descriptor, "wb") as target:
                total = 0
                for block in iter(lambda: source.read(65536), b""):
                    total += len(block)
                    if total > 4096 * 65538:
                        raise RuntimeError("recorded command evidence exceeded its bounded limit")
                    source_hash.update(block)
                    target.write(block)
                target.flush()
                os.fsync(target.fileno())
            proof = {"originalEvidence": str(prior), "sha256": source_hash.hexdigest(), "runId": run,
                     "submissionKey": keys[0]["submissionKey"], "verifiedImmutableCommand": True}
            descriptor = os.open(logs / (label + "-recorded-binding.json"), os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            with os.fdopen(descriptor, "w", encoding="utf-8") as target:
                json.dump(proof, target, separators=(",", ":"))
                target.flush()
                os.fsync(target.fileno())
            runs[label] = run
            submissions[label] = keys[0]["submissionKey"]
            return current
        values = invoke(label, request, {0} if clean else {4})
        accepted = [row for row in values if row.get("event") == "ACCEPTED"]
        if len(accepted) != 1:
            raise RuntimeError("submission did not preserve its durable accepted handle")
        runs[label] = accepted[0]["runId"]
        submission = [row for row in values if row.get("event") == "SUBMISSION"]
        if len(submission) != 1 or not isinstance(submission[0].get("submissionKey"), str):
            raise RuntimeError("submission did not retain its acceptance recovery key")
        submissions[label] = submission[0]["submissionKey"]
        outcome = result(values)
        if outcome.get("runId") != runs[label] or outcome.get("cleanComplete") is not clean:
            raise RuntimeError("terminal outcome did not match the accepted run and expected completeness")
        return outcome

    def assert_complete(outcome: dict, copies: int = 1) -> None:
        if not (outcome.get("clinicalComplete") is True and outcome.get("discoveryComplete") is True
                and outcome.get("inventoryState") == "SEALED" and outcome.get("pendingUnits") == 0
                and outcome.get("targetCopiesPlanned") == copies and outcome.get("reportsDelivered") is True
                and outcome.get("cleanupComplete") is True and outcome.get("resumeRequested") is False
                and outcome.get("maintenanceReleasePending") is False and outcome.get("maintenanceReleaseState") == "RELEASED"):
            raise RuntimeError("clinical completion counters or delivery were incomplete")

    dry = submit("dry-run", metadata["sources"]["dryrun"], "--dry-run")
    if dry.get("state") != "DRY_RUN_COMPLETE" or dry.get("clinicalComplete") is not False or dry.get("committedUnits") != 0:
        raise RuntimeError("preview was mislabeled as clinical completion")
    if dry.get("resumeRequested") is not False or dry.get("maintenanceReleasePending") is not False \
            or dry.get("maintenanceReleaseState") != "RELEASED" or dry.get("reportsDelivered") is not True \
            or dry.get("cleanupComplete") is not True:
        raise RuntimeError("preview report, cleanup or verified maintenance release was incomplete")
    ordinary = submit("ordinary", metadata["sources"]["ordinary"])
    assert_complete(ordinary)
    recovered = result(invoke("submission-status", ["--status-submission", submissions["ordinary"], *common], {0}))
    if recovered.get("runId") != runs["ordinary"]:
        raise RuntimeError("submission lookup did not recover the original accepted run")
    assert_complete(recovered)
    unknown = invoke("unknown-submission", ["--status-submission", "unknown-submission", *common], {4})
    if any(row.get("event") in {"ACCEPTED", "RESULT"} for row in unknown):
        raise RuntimeError("unknown submission fabricated an accepted or terminal result")
    counts = fixture("inspect", runs["ordinary"])
    if counts.get("nativeTargets") != 1 or counts.get("holds") != 0 or counts.get("checkpoints") != ordinary["expectedUnits"]:
        raise RuntimeError("native/checkpoint/hold evidence did not match the completion receipt")
    before_noop = fixture("inspect", "fixture")
    noop = submit("no-op", metadata["sources"]["ordinary"])
    assert_complete(noop)
    if noop.get("committedUnits") != 0 or noop.get("alreadyCompleteUnits") != noop.get("expectedUnits"):
        raise RuntimeError("unchanged reimport did not reconcile as an explicit no-op")
    if fixture("inspect", "fixture") != before_noop or fixture("inspect", runs["ordinary"]) != counts:
        raise RuntimeError("unchanged no-op modified native counts or the original completion evidence")
    resumed = result(invoke("completed-resume", ["--resume", runs["ordinary"], *common], {0}))
    assert_complete(resumed)
    for operation in ("status", "report"):
        values = invoke("completed-" + operation, ["--" + operation, runs["ordinary"], *common], {0})
        assert_complete(result(values))
        if operation == "report":
            reports = [row for row in values if row.get("event") == "PROTECTED_REPORTS"]
            if len(reports) != 1 or set(reports[0].get("reports", {})) != {"event", "error", "receipt", "units"}:
                raise RuntimeError("protected report set was incomplete")
            for entry in reports[0]["reports"].values():
                path = path_from(entry["protectedPath"])
                sha = hashlib.sha256()
                with path.open("rb") as stream:
                    for block in iter(lambda: stream.read(65536), b""):
                        sha.update(block)
                if path.stat().st_size != entry["bytes"] or sha.hexdigest() != entry["sha256"]:
                    raise RuntimeError("protected report size/hash did not match its delivered receipt")
    invoke("unapproved-provider", ["--status", runs["ordinary"], "--provider", "T099"], {3})
    complete = submit("all-categories", metadata["sources"]["allcategory"])
    assert_complete(complete)
    expected_categories = dict.fromkeys((
        "Demographics", "PersonalHistory", "FamilyHistory", "PastHealth", "ProblemList", "RiskFactors",
        "AllergiesAndAdverseReactions", "MedicationsAndTreatments", "Immunizations", "LaboratoryResults",
        "Appointments", "ClinicalNotes", "CareElements", "AlertsAndSpecialNeeds", "NewCategory"), 1)
    expected_categories["Reports"] = 2
    category_rows = complete.get("categories", [])
    categories = {value["category"]: value for value in category_rows}
    if len(categories) != len(category_rows) or set(categories) != set(expected_categories):
        raise RuntimeError("full contract corpus did not account for the exact expected category set")
    for category, expected in expected_categories.items():
        actual = categories[category]
        if actual.get("expected") != expected or actual.get("committed") != expected \
                or actual.get("alreadyComplete") != 0 or actual.get("pending") != 0:
            raise RuntimeError("full contract corpus category occurrence counts did not match")
    native = fixture("inspect", runs["all-categories"])
    for key, expected in {"nativeProfessionalContacts": 2, "nativeDemographicContacts": 3,
                          "nativeUnknownContactConsents": 3, "nativePreferredPharmacies": 1,
                          "nativeLaboratoryResults": 1,
                          "nativeLaboratoryProjection": 1,
                          "nativeLaboratoryPrivateNoteIsolated": 1, "nativeDrugs": 1,
                          "nativeAllergies": 1, "nativePreventions": 1, "nativeYearPreventions": 1,
                          "nativeAppointments": 1, "nativeMeasurements": 2, "nativeDocuments": 2,
                          "nativeUnclockedReports": 2, "nativeMonthReports": 1, "nativeUnknownReports": 1,
                          "nativeFamilyPhysicians": 1, "nativeReferringPhysicians": 1,
                          "nativeUnknownActivePharmacyConsents": 1, "nativeNotesWithoutObservationClock": 7,
                          "nativeUnsignedAuthorFreeNotes": 7}.items():
        if native.get(key) != expected:
            raise RuntimeError("full contract corpus native association/result counts did not match")
    if native.get("nativeNotes") != 7:
        raise RuntimeError("full contract corpus did not create the expected native clinical notes")
    alpha = submit("all-categories-alphanumeric", metadata["sources"]["allcategoryAlpha"],
                   selected_provider=str(metadata["alphanumericProvider"]))
    assert_complete(alpha)
    if fixture("inspect", runs["all-categories-alphanumeric"]).get("preventionCreatorMatchesProvider") is not True:
        raise RuntimeError("native prevention creator did not retain the authorized alphanumeric provider key")
    directory = submit("directory", metadata["sources"]["directory"])
    assert_complete(directory, 2)
    archive = submit("zip", metadata["sources"]["archive"])
    assert_complete(archive, 2)
    archive_counts = fixture("inspect", runs["zip"])
    if archive.get("committedUnits") != archive.get("expectedUnits") or archive_counts.get("nativeTargets") != 2 \
            or archive_counts.get("checkpoints") != archive.get("expectedUnits"):
        raise RuntimeError("initial ZIP import did not commit two new complete native patients")
    invoke("interrupted", ["--input", str(path_from(metadata["sources"]["cancel"])), *common, *contract],
           {130, 143, -signal.SIGTERM, 4}, interrupt=True)
    invoke("cancel", ["--cancel", runs["interrupted"], *common], {4})
    if fixture("inspect", runs["interrupted"]).get("cancelRequested") is not True:
        raise RuntimeError("cancellation was not durably recorded on the partially committed import")
    before_crash = fixture("inspect", runs["interrupted"])
    crashed = invoke("interrupted-hard-resume", ["--resume", runs["interrupted"], *common], {137}, interrupt=True,
                     resume_run=runs["interrupted"], minimum_checkpoints=before_crash["checkpoints"],
                     minimum_notes=before_crash["nativeNotes"], hard_crash=True)
    if any(row.get("event") == "RESULT" for row in crashed):
        raise RuntimeError("hard-crashed engine falsely delivered a terminal success")
    after_crash = fixture("inspect", runs["interrupted"])
    if after_crash.get("cancelRequested") is not False or not 0 < after_crash.get("checkpoints", 0) < after_crash.get("expectedUnits", 0):
        raise RuntimeError("hard crash did not preserve an uncancelled incomplete original run")
    recovered = result(invoke("interrupted-resume", ["--resume", runs["interrupted"], *common], {0}))
    assert_complete(recovered)
    recovered_counts = fixture("inspect", runs["interrupted"])
    if recovered_counts.get("nativeTargets") != 1 or recovered_counts.get("nativeNotes") != 256 \
            or recovered_counts.get("checkpoints") != recovered.get("expectedUnits") or recovered_counts.get("holds") != 0:
        raise RuntimeError("partial-import resume duplicated or omitted native units/patient or retained a hold")
    course_options = ["--synthetic", "--course", str(metadata["courseId"]), "--timeshift", "7"]
    if metadata.get("facilityId") is not None:
        course_options += ["--facility", str(metadata["facilityId"])]
    course = submit("course", metadata["sources"]["course"], *course_options)
    assert_complete(course, 2)
    course_counts = fixture("inspect", runs["course"])
    if any(course_counts.get(key) != 2 for key in ("nativeTargets", "shiftedBirthDates",
                                                  "shiftedMedicationDates", "shiftedAllergyDates", "shiftedNoteDates")):
        raise RuntimeError("course import did not create exactly two independent copies with consistent clinical date shifts")
    for control in ("missing-personal-program", "inactive-personal-program", "inactive-students", "inactive-course"):
        before = fixture("inspect", "fixture")
        fixture("control", metadata["metadataFile"], "reset")
        fixture("control", metadata["metadataFile"], control)
        if control == "inactive-course":
            denied = invoke("course-" + control,
                            ["--input", str(path_from(metadata["sources"]["course"])), *common, *contract, *course_options], {3})
            if any(row.get("event") == "ACCEPTED" for row in denied):
                raise RuntimeError("inactive course was accepted before its authorization refusal")
        else:
            refused = submit("course-" + control, metadata["sources"]["course"], *course_options, clean=False)
            if refused.get("committedUnits") != 0 or refused.get("clinicalComplete") is not False:
                raise RuntimeError("ineligible course imported clinical units or implied a real-chart fallback")
        after = fixture("inspect", "fixture")
        if after.get("totalNativePatients") != before.get("totalNativePatients"):
            raise RuntimeError("refused course changed the native patient count")
    fixture("control", metadata["metadataFile"], "reset")
    print(json.dumps({"gate": "installed-cds-cli-engine", "outcome": "PASS", "runs": runs,
                      "maxChildRssKiB": resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss,
                      "operatorEvidence": str(logs)}, separators=(",", ":")))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Private logs retain generated operational handles; never print raw
        # JDBC/source/report exceptions into the general build console.
        raise SystemExit("CDS installed-engine gate failed; inspect its protected operator evidence") from None
