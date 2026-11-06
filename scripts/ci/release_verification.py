#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Source-bound release evidence. A diagnostic success is not release readiness.

Receipts contain identities and counters, never configuration, HTTP bodies,
stdout, credentials or patient values. Raw logs stay in a private run directory.
Run only against disposable test installations and synthetic clinical fixtures.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import stat
import subprocess
import sys
import time
import uuid

from junit_evidence import EvidenceError, read_surefire
from java_discovery import reconcile, expected_families
from release_deployment import attest
from cds_war_manifest import build_manifest
from command_evidence import validate_contract, results as command_results, relative_file
import compiled_classes

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PLAN = ROOT / "docs/testing/release-2026.08-verification.json"
HEX = re.compile(r"[0-9a-f]{64}\Z")


def digest(path: Path) -> str:
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
    with os.fdopen(descriptor, "rb") as stream:
        if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
            raise EvidenceError("Evidence input is not a regular file")
        os.set_blocking(stream.fileno(), True)
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_json(path: Path):
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 64 * 1024 * 1024:
        raise EvidenceError("Missing, symlinked or oversized evidence")
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise EvidenceError("Duplicate JSON key")
            result[key] = value
        return result
    try:
        return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)
    except (ValueError, UnicodeError) as failure:
        raise EvidenceError("Malformed JSON evidence") from failure


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with path.open("x", encoding="utf-8") as stream:
        os.chmod(path, 0o600)
        json.dump(value, stream, indent=2, sort_keys=True, allow_nan=False)
        stream.write("\n")


def source_identity(root=ROOT):
    def git(*args):
        return subprocess.check_output(["git", *args], cwd=root, text=True).strip()
    # Ignored build output/dependencies do not dirty a checkout. Untracked source does.
    return {"commit": git("rev-parse", "HEAD"), "tree": git("rev-parse", "HEAD^{tree}"),
            "clean": not bool(git("status", "--porcelain", "--untracked-files=normal"))}


def production_class_identity(root=ROOT):
    try:
        files = compiled_classes.fingerprints(root)
    except ValueError as failure:
        raise EvidenceError(str(failure)) from failure
    return {'schemaVersion': 1, 'sourceTree': source_identity(root)['tree'], 'classes': files}


def runtime_versions(command, required=()):
    versions = {"python": sys.version.split()[0]}
    executable = Path(command[0]).name if command else ""
    probes = [("node", ["node", "--version"], r"^v([0-9]+\.[0-9]+\.[0-9]+)$"),
              ("npm", ["npm", "--version"], r"^([0-9]+\.[0-9]+\.[0-9]+)$")] if executable == "node" or {"node", "npm"} & set(required) else []
    if executable == "mvn" or "java" in required:
        probes.append(("java", ["mvn", "-version"], r'Java version: ([0-9][0-9A-Za-z._+\-]*)'))
    for name, argv, pattern in probes:
        try:
            process = subprocess.run(argv, capture_output=True, text=True, timeout=10)
            match = re.search(pattern, process.stdout + "\n" + process.stderr, re.MULTILINE)
            versions[name] = match[1] if process.returncode == 0 and match else None
        except (OSError, subprocess.TimeoutExpired):
            versions[name] = None
    return versions


def runtime_matches(versions, constraints):
    for name, expected in constraints.items():
        version = versions.get(name)
        if not isinstance(version, str):
            return False
        parts = expected.split(".")
        if (version.split(".")[:len(parts)] != parts if len(parts) < 3 else version != expected):
            return False
    return True


def load_plan(path=DEFAULT_PLAN, root=ROOT):
    plan = read_json(path)
    if plan.get("schemaVersion") != 1 or plan.get("targetBranch") != "release/2026.08":
        raise EvidenceError("Wrong plan schema or target branch")
    work = plan.get("workstreams", [])
    if len(work) != 40 or {row.get("id") for row in work} != {f"T{i:02}" for i in range(1, 41)}:
        raise EvidenceError("The 40 reviewed workstreams must appear exactly once")
    checks = plan.get("checks", [])
    names = [check.get("id") for check in checks]
    if not names or len(set(names)) != len(names):
        raise EvidenceError("Empty or duplicate check registry")
    for check in checks:
        if 'requiredCases' in check and (check.get('kind') not in ('python', 'node')
                or not isinstance(check['requiredCases'], list)
                or any(not isinstance(case, str) or not case for case in check['requiredCases'])
                or len(check['requiredCases']) != len(set(check['requiredCases']))):
            raise EvidenceError('Required Python/Node cases must have unique nonempty identities')
        if type(check.get('requiresArtifact', False)) is not bool:
            raise EvidenceError('Artifact prerequisite must be boolean')
        programs = check.get('requiresExecutables', [])
        if (not isinstance(programs, list)
                or any(not isinstance(program, str) or not re.fullmatch(r'[A-Za-z0-9_+.-]+', program)
                       for program in programs) or len(programs) != len(set(programs))):
            raise EvidenceError('Executable prerequisites must be unique command names')
        if "resultContract" in check:
            if check.get("kind") != "command":
                raise EvidenceError("Only command checks accept result contracts")
            validate_contract(check["resultContract"])
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]+", check.get("id", "")):
            raise EvidenceError("Invalid check identity")
        if check.get("kind") not in {"junit", "node", "python", "browser", "command", "build", "external"}:
            raise EvidenceError("Unknown evidence adapter")
        if type(check.get("repetitions", 1)) is not int or check.get("repetitions", 1) < 1:
            raise EvidenceError("Invalid repetition requirement")
        if not check.get("profiles") or len(set(check["profiles"])) != len(check["profiles"]):
            raise EvidenceError("Empty or duplicate profile matrix")
        if check["kind"] != "external" and (not check.get("command") or not all(
                isinstance(token, str) and token for token in check["command"])):
            raise EvidenceError("Runnable check has no argv")
        for route in check.get("routedSkips", {}).values():
            if route.get("check") not in names or route["check"] == check["id"] or not route.get("cases"):
                raise EvidenceError("A non-applicable Java case must route to a different required check")
            for case in route["cases"]:
                if isinstance(case, str):
                    if not case:
                        raise EvidenceError("Empty Java route case")
                    continue
                if not isinstance(case, dict) or not isinstance(case.get("name"), str) or not case["name"] or not isinstance(case.get("container", ""), str):
                    raise EvidenceError("Invalid Java route case")
                if "destinations" in case:
                    targets = case["destinations"]
                    if not isinstance(targets, list) or not targets or any(
                            not isinstance(target, dict) or not isinstance(target.get("name"), str)
                            or not target["name"] or not isinstance(target.get("container", ""), str)
                            for target in targets):
                        raise EvidenceError("Parameterized Java route has invalid destinations")
                    if len({(target.get("container", ""), target["name"]) for target in targets}) != len(targets):
                        raise EvidenceError("Parameterized Java route has duplicate destinations")
    for row in work:
        if not row.get("cases") or not row.get("acceptance") or not row.get("requiredChecks"):
            raise EvidenceError("Workstream lacks cases, acceptance or evidence requirements")
        if set(row["requiredChecks"]) - set(names):
            raise EvidenceError("Workstream names an unknown required check")
        if not row.get("sources"):
            raise EvidenceError("Workstream has no concrete source/test references")
        for source in row["sources"]:
            if Path(source).is_absolute() or ".." in Path(source).parts or not (root / source).is_file():
                raise EvidenceError(f"Missing test/source reference in {row['id']}: {source}")
    if not plan.get("knownOutcomeLedger"):
        raise EvidenceError("Plan must name its knownOutcomeLedger")
    if plan["knownOutcomeLedger"]:
        ledger = read_json(root / plan["knownOutcomeLedger"])
        manifest = read_json(root / "scripts/playwright-suite.json")["checks"]
        actual = {(c["name"], c["expectedFailure"]["finding"], c["expectedFailure"]["step"])
                  for c in manifest if c.get("expectedFailure")}
        declared = [(c["check"], c["finding"], c["step"]) for c in ledger["browserExpectedFailures"]]
        if set(declared) != actual or len(declared) != len(set(declared)):
            raise EvidenceError("Known-failure ledger omits or duplicates a manifest expectation")
        node_cases = [row["case"] for row in ledger["retiredNodeTodos"]]
        required_node = next(check for check in checks if check["id"] == "node-all").get("requiredCases", [])
        if len(node_cases) != len(set(node_cases)) or not set(node_cases) <= set(required_node):
            raise EvidenceError("Retired Node TODOs must remain required active regressions")
        if plan.get("requireManualBrowserCoverage"):
            required = {name for row in work for name in row["requiredChecks"]}
            for entry in manifest:
                if not entry.get("manual"):
                    continue
                provinces = {"ON", "BC"} if "all" in entry["provinces"] else {
                    province.upper() for province in entry["provinces"]}
                covered = {profile.upper() for check in checks if check["id"] in required
                           and entry["name"] in check.get("selection", [])
                           and check.get("propertyProfile", "") == entry.get("profile", "")
                           for profile in check["profiles"]}
                if not provinces <= covered:
                    raise EvidenceError("Manual browser check lacks required province coverage: " + entry["name"])
    return plan


def junit_results(directory: Path, check):
    if directory.is_symlink() or not directory.is_dir():
        raise EvidenceError("Missing or symlinked Java report directory")
    files = sorted(directory.glob("TEST-*.xml"))
    if not files:
        raise EvidenceError("No executed Java tests")
    identities = set()
    counts = {"tests": 0, "failed": 0, "skipped": 0}
    families = {}
    routed = []
    collisions = []
    suffix = check.get("reportSuffix", "")
    for path in files:
        suite = path.stem.removeprefix("TEST-")
        if suffix:
            if not suite.endswith("-" + suffix):
                raise EvidenceError("Report belongs to another lane")
            suite = suite.removesuffix("-" + suffix)
        cases = read_surefire(path, suite, suffix, collisions)
        family = suite.rsplit(".", 1)[-1].split("$", 1)[0]
        families[family] = families.get(family, 0) + len(cases)
        for case in cases:
            identity = case.identity
            if identity in identities:
                raise EvidenceError("Duplicate executed test identity")
            identities.add(identity)
            counts["tests"] += 1
            counts["failed"] += case.outcome == "failure"
            counts["skipped"] += case.outcome == "skipped"
            route = check.get("routedSkips", {}).get(case.classname)
            if case.outcome == "skipped" and route:
                for item in route["cases"]:
                    expected = {"name": item} if isinstance(item, str) else item
                    if expected["name"] == case.name and expected.get("container", "") == case.container:
                        routed_case = {"classname": case.classname, "container": case.container,
                                       "name": case.name, "check": route["check"]}
                        if "destinations" in expected:
                            routed_case["destinations"] = expected["destinations"]
                        routed.append(routed_case)
                        break
    missing = [name for name, minimum in check.get("requiredFamilies", {}).items()
               if families.get(name, 0) < minimum]
    return {**counts, "missingFamilies": missing, "families": families, "routedSkips": routed, "counterCollisions": collisions,
            "ready": counts["tests"] >= check.get("minimumCases", 1)
                and not counts["failed"] and counts["skipped"] == len(routed) and not missing}


def node_results(path: Path, root=ROOT, minimum=1, required=()):
    result = read_json(path)
    if result.get("schemaVersion") != 2:
        raise EvidenceError("Node evidence lacks engine execution identities")
    summary = result.get("summary") or {}
    counts = summary.get("counts") or {}
    keys = ("tests", "passed", "failed", "cancelled", "skipped", "todo")
    if any(type(counts.get(key)) is not int or counts[key] < 0 for key in keys):
        raise EvidenceError("Node report has no complete final counters")
    expected = {str(p.relative_to(root)) for p in (root / "scripts").glob("*.test.js")}
    files = result.get("files", [])
    if not expected or len(files) != len(set(files)) or set(files) != expected:
        raise EvidenceError("Node discovery does not match every checked-in test file")
    cases = result.get("cases") or []
    if not cases or len(cases) != counts["tests"]:
        raise EvidenceError("Node case inventory disagrees with execution counters")
    if any(type(c.get("testNumber")) is not int or c["testNumber"] < 1 or not isinstance(c.get("parents"), list)
           for c in cases):
        raise EvidenceError("Node execution has no complete engine identity")
    identities = [(c.get("file"), json.dumps(c["parents"], sort_keys=True), c["testNumber"], c.get("line"),
                   c.get("column"), c.get("name")) for c in cases]
    if len(set(identities)) != len(identities):
        raise EvidenceError("Duplicate Node test identity")
    if {c.get("file") for c in cases} != expected or any(
            c.get("file") not in expected or not isinstance(c.get("name"), str)
            or c["name"] in (c["file"], str((root / c["file"]).resolve())) for c in cases):
        raise EvidenceError("Node file lacks real test case executions")
    ready = summary.get("success") is True and counts["tests"] >= minimum and counts["tests"] == counts["passed"]
    ready = ready and not any(counts[k] for k in keys[2:]) and all(c.get("outcome") == "PASS" for c in cases)
    if set(required) - {case.get("name") for case in cases if case.get("outcome") == "PASS"}:
        raise EvidenceError("A required Node regression did not execute successfully")
    return {**counts, "ready": ready}


def python_results(path: Path, minimum=1, required=()):
    result = read_json(path)
    cases = result.get("cases") or []
    tests = result.get("tests")
    if type(tests) is not int or tests < 0:
        raise EvidenceError("Invalid Python execution count")
    names = [case.get("id") for case in cases]
    if any(not isinstance(name, str) or not name for name in names) or len(set(names)) != len(names):
        raise EvidenceError("Missing or duplicate Python test identity")
    ready = result.get("ready") is True and not result.get("duplicateIdentities") and len(cases) == tests
    ready = ready and tests >= minimum and all(case.get("outcomes") == ["PASS"] for case in cases)
    if set(required) - {case.get('id') for case in cases if case.get('outcomes') == ['PASS']}:
        raise EvidenceError('A required Python regression did not execute successfully')
    return {"tests": tests, "ready": ready}


def require_locked_chromium(versions, root=ROOT):
    lock = read_json(root / "package-lock.json")
    version = lock["packages"]["node_modules/playwright-core"]["version"]
    directories = [parent / "node_modules/playwright-core" for parent in (root, *root.parents)]
    installed = next((directory for directory in directories if (directory / "package.json").is_file()), None)
    if installed is None or read_json(installed / "package.json").get("version") != version:
        raise EvidenceError("Installed Playwright does not match the committed lock")
    browsers = read_json(installed / "browsers.json").get("browsers", [])
    expected_versions = {row["browserVersion"] for row in browsers if row.get("name") == "chromium"}
    observed = set()
    for value in versions:
        match = re.fullmatch(r'(?:chromium )?(\d+\.\d+\.\d+\.\d+)', value)
        if not match: raise EvidenceError('Missing or unqualified launched Chromium version')
        observed.add(match[1])
    if len(expected_versions) != 1 or observed != expected_versions:
        raise EvidenceError("Missing or unqualified launched Chromium version")


def browser_results(path: Path, check, root=ROOT):
    # Strict runner JUnit retains product outcomes but converts every non-PASS
    # classification to a readiness failure. Reconcile names with the manifest.
    import defusedxml.ElementTree as ET
    with path.open("rb") as stream:
        document = ET.parse(stream, forbid_dtd=True).getroot()
    if check.get("requireLockedChromium"):
        observed_versions = {item.strip() for prop in document.iter("property")
                             if prop.get("name") == "browserVersion"
                             for item in prop.get("value", "").split(",") if item.strip()}
        require_locked_chromium(observed_versions, root)
    cases = list(document.iter("testcase"))
    profile = check["selectedProfile"]
    manifest = read_json(root / "scripts/playwright-suite.json")["checks"]
    selected = check.get("selection", [])
    expected = {c["name"] for c in manifest if (not selected or c["name"] in selected)
                and (not c.get("manual") or selected)
                and c.get("profile", "") == check.get("propertyProfile", "")
                and any(p.lower() in ("all", profile.lower()) for p in c["provinces"])}
    names = [c.get("name") for c in cases]
    if not expected or len(set(names)) != len(names) or set(names) != expected:
        raise EvidenceError("Browser selection is empty, duplicated, or incomplete")
    failed = sum(any(c.find(tag) is not None for tag in ("failure", "error", "skipped")) for c in cases)
    return {"tests": len(cases), "failed": failed, "ready": not failed}


def summarize(check, run_dir: Path, exit_code, root=ROOT):
    kind = check["kind"]
    if kind == "junit":
        summary = junit_results(run_dir / "reports", check)
        if check.get("reconcileDiscovery"):
            discovery = reconcile(root / "src/test/java", run_dir / "reports")
            summary["discovery"] = discovery
            summary["ready"] = summary["ready"] and discovery["ready"]
        if check.get("requireCoverage"):
            import defusedxml.ElementTree as ET
            execution = run_dir / "jacoco.exec"
            report = run_dir / "coverage/jacoco.xml"
            if execution.is_symlink() or not execution.is_file() or execution.stat().st_size == 0 or report.is_symlink():
                raise EvidenceError("Missing fresh JaCoCo agent data")
            with report.open("rb") as stream:
                coverage = ET.parse(stream).getroot()
            counters = {counter.get("type"): int(counter.get("covered", "0")) for counter in coverage.findall("counter")}
            if coverage.tag != "report" or not coverage.findall("package") or counters.get("INSTRUCTION", 0) < 1:
                raise EvidenceError("Coverage contains no executed production instructions")
            summary["coverage"] = counters
        if check.get('requireClassIdentity'):
            classes = read_json(run_dir / 'classes.json')
            if classes.get('schemaVersion') != 1 or classes.get('sourceTree') != source_identity(root)['tree'] or not classes.get('classes'):
                raise EvidenceError('Coverage class identity is missing or belongs to another source tree')
            for name, fingerprint in classes['classes'].items():
                if Path(name).is_absolute() or '..' in Path(name).parts or not name.endswith('.class') or not isinstance(fingerprint, str) or not HEX.fullmatch(fingerprint):
                    raise EvidenceError('Malformed compiled class identity')
            summary['classIdentitySha256'] = digest(run_dir / 'classes.json')
    elif kind == "node":
        summary = node_results(run_dir / "node.json", root, check.get("minimumCases", 1), check.get("requiredCases", []))
    elif kind == "browser":
        summary = browser_results(run_dir / "browser.xml", check, root)
    elif kind == "python":
        summary = python_results(run_dir / "python.json", check.get("minimumCases", 1), check.get('requiredCases', []))
    elif kind == "build":
        war = run_dir / "carlos.war"
        manifest = build_manifest(war)
        summary = {"artifactSha256": manifest["warSha256"], "entries": manifest["entryCount"], "ready": True}
    elif kind == "command":
        summary = {"ready": True}
        if check.get("resultContract"):
            summary, _ = command_results(check["resultContract"], run_dir, read_json, digest)
        if check["id"] == "scope-inventory":
            report = read_json(run_dir / "scope.json")
            if report.get("schemaVersion") != 1 or report.get("head") != source_identity(root)["commit"]:
                raise EvidenceError("Scope report does not identify the tested source")
            summary = {"ready": report.get("ready") is True, "scopeSha256": digest(run_dir / "scope.json")}
    else:
        raise EvidenceError("External acceptance requires its dedicated evidence adapter")
    summary["ready"] = summary["ready"] and exit_code == 0
    return summary


def run_check(plan_path, check_id, evidence: Path, profile, iteration, artifact=None, root=ROOT):
    plan = load_plan(plan_path, root)
    matches = [c for c in plan["checks"] if c["id"] == check_id]
    if len(matches) != 1:
        raise EvidenceError("Unknown check")
    check = dict(matches[0], selectedProfile=profile)
    if profile not in check["profiles"] or type(iteration) is not int or iteration not in range(1, check.get("repetitions", 1) + 1):
        raise EvidenceError("Profile or repetition outside the reviewed matrix")
    source = source_identity(root)
    if not source["clean"]:
        raise EvidenceError("Commit source changes before producing release evidence")
    if evidence.resolve().is_relative_to(root.resolve()) and not str(evidence.resolve()).startswith(str(root / "target") + os.sep):
        raise EvidenceError("Store evidence outside source or under target/")
    evidence.mkdir(parents=True, exist_ok=True, mode=0o700)
    if evidence.is_symlink():
        raise EvidenceError("Evidence root cannot be a symlink")
    run_id = str(uuid.uuid4())
    run_dir = evidence / run_id
    run_dir.mkdir(mode=0o700)
    receipt = {"schemaVersion": 1, "runId": run_id, "source": source,
               "planSha256": digest(plan_path), "checkId": check_id, "profile": profile,
               "iteration": iteration, "startedNs": time.time_ns(), "outcome": "BLOCKED",
               "exitCode": None, "summary": {"ready": False}, "inputs": [], "problems": []}
    receipt["runtimes"] = runtime_versions(check.get("command", []), check.get("runtimeConstraints", {}))
    if artifact is not None:
        receipt["artifactSha256"] = digest(artifact)
    missing = [name for name in check.get("requiresEnvironment", []) if not os.environ.get(name)]
    missing_programs = [name for name in check.get('requiresExecutables', []) if shutil.which(name) is None]
    try:
        if not runtime_matches(receipt["runtimes"], check.get("runtimeConstraints", {})):
            receipt["reason"] = "Required runtime version is unavailable"
        elif check["kind"] == "external" or missing or missing_programs:
            receipt["reason"] = "External acceptance pending" if check["kind"] == "external" else "Missing named test prerequisites"
        elif (check.get("packaged") or check.get('requiresArtifact')) and artifact is None:
            receipt["reason"] = "Packaged acceptance requires the exact built WAR"
        else:
            if check.get("packaged"):
                receipt["deployment"] = attest(artifact, base_url=os.environ.get("BASE_URL", ""), root=root)
            substitutions = {"run": str(run_dir.resolve()), "root": str(root.resolve()),
                             "runId": run_id,
                             "evidence": str(evidence.resolve()),
                             "profile": profile, "artifact": str(artifact.resolve()) if artifact else ""}
            substitutions.update({name: os.environ.get(variable, "") for name, variable in check.get("environmentInputs", {}).items()})
            argv = [arg.format_map(substitutions) for arg in check["command"]]
            if argv[0] == "python3":
                # An absolute venv entry point must keep its pinned dependencies
                # even when the caller has not activated that venv in PATH.
                argv[0] = sys.executable
            receipt["command"] = check["command"]  # Template only: no expanded credentials/paths.
            environment = dict(os.environ)
            environment.update({key: value.format_map(substitutions) for key, value in check.get("environment", {}).items()})
            if check.get('requireClassIdentity'):
                # A clean Git checkout alone says nothing about ignored class files.
                # Each lane must compile from this source before it can earn evidence.
                compiled_classes.reset(root, evidence)
            with (run_dir / "private.log").open("xb") as log:
                os.chmod(run_dir / "private.log", 0o600)
                process = subprocess.Popen(argv, cwd=root, env=environment, stdout=log, stderr=log, start_new_session=True)
                try:
                    receipt["exitCode"] = process.wait(timeout=check.get("timeoutSeconds", 3600))
                except (subprocess.TimeoutExpired, KeyboardInterrupt) as failure:
                    # Give harness finally blocks a chance to restore their own fixtures.
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=20)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
                    receipt["reason"] = "Timeout or interruption; cleanup is not certified"
                    receipt["exitCode"] = -1
                    if isinstance(failure, KeyboardInterrupt):
                        receipt["cancelled"] = True
            if receipt["exitCode"] != 0:
                receipt["problems"].append({"stage": "execution", "exitCode": receipt["exitCode"],
                                            "cancelled": receipt.get("cancelled", False)})
            if check.get('requireClassIdentity'):
                write_json(run_dir / 'classes.json', production_class_identity(root))
            receipt["summary"] = summarize(check, run_dir, receipt["exitCode"], root)
            if source_identity(root) != source or digest(plan_path) != receipt["planSha256"]:
                raise EvidenceError("Source or plan changed during execution")
            if artifact is not None and digest(artifact) != receipt["artifactSha256"]:
                raise EvidenceError("Packaged bytes changed during execution")
            if check.get("packaged") and attest(artifact, base_url=os.environ.get("BASE_URL", ""), root=root) != receipt["deployment"]:
                raise EvidenceError("Installed application changed during execution")
            receipt["outcome"] = "PASS" if receipt["summary"]["ready"] else "FAIL"
    except Exception as failure:
        receipt["outcome"] = "FAIL"
        reason = str(failure) if isinstance(failure, EvidenceError) else type(failure).__name__
        receipt.setdefault("reason", reason)
        receipt["problems"].append({"stage": "evidence", "reason": reason})
    receipt["completedNs"] = time.time_ns()
    for path in evidence_files(check, run_dir):
        if path.is_symlink() or not path.is_file():
            receipt["outcome"] = "FAIL"
            receipt["reason"] = "Evidence contains a symlink or non-file"
            continue
        receipt["inputs"].append({"path": str(path.relative_to(run_dir)), "sha256": digest(path)})
    write_json(run_dir / "receipt.json", receipt)
    return receipt


def evidence_files(check, directory):
    files = {p for pattern in ("reports/TEST-*.xml", "node.json", "python.json", "browser.xml", "carlos.war",
                              "jacoco.exec", "coverage/jacoco.xml", "javascript-coverage.json", "scope.json", "classes.json")
             for p in directory.glob(pattern)}
    if check.get("resultContract"):
        result = directory / check["resultContract"]["file"]
        if result.exists() or result.is_symlink():
            files.add(result)
            try:
                _, names = command_results(check["resultContract"], directory, read_json, digest)
                files.update(relative_file(directory, name) for name in names)
            except EvidenceError:
                # summarize records malformed/missing inputs as FAIL. Retain the
                # result itself without losing the failed receipt during collection.
                pass
    return sorted(files)


def verify(plan_path, evidence: Path, artifact: Path, root=ROOT):
    plan = load_plan(plan_path, root)
    source = source_identity(root)
    if not source["clean"]:
        raise EvidenceError("Release evidence requires a clean committed source tree")
    artifact_hash = digest(artifact)
    checks = {c["id"]: c for c in plan["checks"]}
    required = {name for row in plan["workstreams"] for name in row["requiredChecks"]}
    expected = {(name, profile, iteration) for name in required for profile in checks[name]["profiles"]
                for iteration in range(1, checks[name].get("repetitions", 1) + 1)}
    seen = set()
    runs = set()
    incomplete = []
    build_bound = False
    routed_cases = []
    executed_cases = set()
    executed_families = set()
    for path in sorted(evidence.glob("*/receipt.json")):
        receipt = read_json(path)
        key = (receipt.get("checkId"), receipt.get("profile"), receipt.get("iteration"))
        if key not in expected or key in seen or receipt.get("runId") in runs:
            raise EvidenceError("Unknown, duplicate or repeated evidence identity")
        seen.add(key)
        runs.add(receipt["runId"])
        if type(receipt.get("iteration")) is not int or type(receipt.get("schemaVersion")) is not int or receipt.get("schemaVersion") != 1 or receipt.get("source") != source or receipt.get("planSha256") != digest(plan_path):
            raise EvidenceError("Stale evidence source or plan")
        if receipt.get("runId") != path.parent.name:
            raise EvidenceError("Receipt directory identity mismatch")
        if type(receipt.get("startedNs")) is not int or type(receipt.get("completedNs")) is not int or not (
                0 < receipt["startedNs"] <= receipt["completedNs"] <= time.time_ns()):
            raise EvidenceError("Invalid execution interval")
        check = dict(checks[key[0]], selectedProfile=key[1])
        if not runtime_matches(receipt.get("runtimes", {}), check.get("runtimeConstraints", {})):
            raise EvidenceError("Evidence used an unqualified runtime")
        if (check.get("packaged") or check.get('requiresArtifact')) and receipt.get("artifactSha256") != artifact_hash:
            raise EvidenceError("Installed acceptance belongs to different packaged bytes")
        if check.get("packaged") and receipt.get("outcome") == "PASS" and (
                receipt.get("deployment", {}).get("warSha256") != artifact_hash
                or receipt["deployment"].get("scope") != "installed-package-application"
                or not HEX.fullmatch(receipt["deployment"].get("contentSha256", ""))):
            raise EvidenceError("Packaged acceptance has no installed-byte binding")
        inputs = receipt.get("inputs", [])
        input_names = [item.get("path") for item in inputs]
        if len(input_names) != len(set(input_names)):
            raise EvidenceError("Duplicate evidence file")
        for item in inputs:
            relative = Path(item.get("path", ""))
            if relative.is_absolute() or ".." in relative.parts:
                raise EvidenceError("Evidence path escapes its run")
            file = path.parent / relative
            if file.is_symlink() or not file.is_file() or not HEX.fullmatch(item.get("sha256", "")) or digest(file) != item["sha256"]:
                raise EvidenceError("Missing or changed evidence input")
        if receipt.get("outcome") != "PASS" or receipt.get("exitCode") != 0 or receipt.get("cancelled"):
            incomplete.append({"checkId": key[0], "profile": key[1], "iteration": key[2], "outcome": receipt.get("outcome", "MISSING")})
            continue
        if receipt.get("command") != check.get("command") or check["kind"] == "external":
            raise EvidenceError("Receipt was not produced by its reviewed executable check")
        actual_inputs = {str(p.relative_to(path.parent)) for p in evidence_files(check, path.parent)}
        if actual_inputs != set(input_names):
            raise EvidenceError("Evidence file inventory changed")
        summary = summarize(check, path.parent, 0, root)
        if summary != receipt.get("summary") or not summary.get("ready"):
            raise EvidenceError("Receipt counters disagree with underlying results")
        if check["kind"] == "build" and summary["artifactSha256"] == artifact_hash:
            build_bound = True
        if check["kind"] == "junit":
            routed_cases.extend(summary.get("routedSkips", []))
            suffix = check.get("reportSuffix", "")
            for report in (path.parent / "reports").glob("TEST-*.xml"):
                suite = report.stem.removeprefix("TEST-")
                if suffix:
                    suite = suite.removesuffix("-" + suffix)
                for case in read_surefire(report, suite, suffix):
                    if case.outcome == "pass":
                        executed_cases.add((check["id"], *case.identity))
                        executed_families.add(case.classname.split("$", 1)[0])
    for key in sorted(expected - seen):
        incomplete.append({"checkId": key[0], "profile": key[1], "iteration": key[2], "outcome": "MISSING"})
    if not build_bound:
        incomplete.append({"checkId": "build", "outcome": "MISSING_SOURCE_BOUND_BUILD"})
    for route in routed_cases:
        for target in route.get("destinations", [route]):
            if (route["check"], route["classname"], target.get("container", ""), target["name"]) not in executed_cases:
                incomplete.append({"checkId": route["check"], "case": route["classname"] + "::" + target["name"],
                                   "outcome": "ROUTED_CASE_NOT_EXECUTED"})
    if plan.get("requireJavaDiscovery"):
        for family in sorted(expected_families(root / "src/test/java").keys() - executed_families):
            incomplete.append({"checkId": "java-discovery", "class": family, "outcome": "NO_PASSING_EXECUTION"})
    return {"schemaVersion": 1, "source": source, "planSha256": digest(plan_path), "artifactSha256": artifact_hash,
            "requiredExecutions": len(expected), "receivedExecutions": len(seen), "incomplete": incomplete,
            "releaseReady": not incomplete}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, default=DEFAULT_PLAN)
    sub = parser.add_subparsers(dest="operation", required=True)
    sub.add_parser("validate-plan")
    sub.add_parser("list")
    run = sub.add_parser("run")
    run.add_argument("--check", required=True)
    run.add_argument("--evidence", type=Path, required=True)
    run.add_argument("--profile", default="all")
    run.add_argument("--iteration", type=int, default=1)
    run.add_argument("--artifact", type=Path)
    gate = sub.add_parser("verify")
    gate.add_argument("--evidence", type=Path, required=True)
    gate.add_argument("--artifact", type=Path, required=True)
    gate.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    if args.operation == "verify":
        args.output.unlink(missing_ok=True)
    try:
        plan = load_plan(args.plan)
        if args.operation == "validate-plan":
            print(f"Validated {len(plan['workstreams'])} workstreams and {len(plan['checks'])} checks")
            return 0
        if args.operation == "list":
            for check in plan["checks"]:
                print(json.dumps({k: check[k] for k in ("id", "kind", "profiles", "repetitions") if k in check}))
            return 0
        if args.operation == "run":
            def interrupted(_signum, _frame):
                raise KeyboardInterrupt
            previous = signal.signal(signal.SIGTERM, interrupted)
            try:
                result = run_check(args.plan, args.check, args.evidence, args.profile, args.iteration, args.artifact)
            finally:
                signal.signal(signal.SIGTERM, previous)
            print(json.dumps({k: result[k] for k in ("runId", "checkId", "profile", "iteration", "outcome")}))
            return 0 if result["outcome"] == "PASS" else 1
        result = verify(args.plan, args.evidence, args.artifact)
        write_json(args.output, result)
        print(json.dumps(result, indent=2))
        return 0 if result["releaseReady"] else 1
    except Exception as failure:
        reason = str(failure) if isinstance(failure, EvidenceError) else type(failure).__name__
        print(f"Release evidence refused: {reason}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
