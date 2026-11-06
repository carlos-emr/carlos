# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Fault injection against real subprocess receipts, source commits and result files."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

import release_verification as gate
from junit_evidence import EvidenceError


class ReleaseEvidenceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name)
        self.root = self.base / "source"
        self.root.mkdir()
        self.evidence = self.base / "evidence"
        self.plan_path = self.root / "plan.json"
        self.tool = self.root / "controlled.py"
        self.tool.write_text('''import sys, pathlib, zipfile
directory=pathlib.Path(sys.argv[1]); directory.mkdir(parents=True,exist_ok=True)
if sys.argv[2]=='build':
 with zipfile.ZipFile(directory/'carlos.war','w') as archive:
  for name in ['WEB-INF/web.xml','WEB-INF/classes/io/github/carlos_emr/carlos/demographic/cdsimport/service/CdsImportJobService.class','WEB-INF/classes/io/github/carlos_emr/carlos/demographic/cdsimport/cli/CdsImportCli.class','WEB-INF/classes/db/migration/common/V1.0.59.8.1__cds_import_ledger.sql','WEB-INF/lib/synthetic.jar']:
   archive.writestr(name,'synthetic fixture bytes')
else:
 (directory/'reports').mkdir()
 fault=sys.argv[3] if len(sys.argv)>3 else ''
 skipped='1' if '<skipped' in fault else '0'
 (directory/'reports/TEST-example.RequiredTest.xml').write_text('<testsuite name="example.RequiredTest" tests="1" failures="0" errors="0" skipped="'+skipped+'"><testcase classname="example.RequiredTest" name="shouldWrite_atomically">'+fault+'</testcase></testsuite>')
if '--mutate' in sys.argv: pathlib.Path('controlled.py').write_text('# changed while executing')
if '--nonzero' in sys.argv: sys.exit(7)
''')
        self.plan = {"schemaVersion": 1, "targetBranch": "release/2026.08",
            "workstreams": [{"id": f"T{i:02}", "sources": ["controlled.py"], "cases": "independent state oracle",
                             "acceptance": "complete", "requiredChecks": ["build", "java"]} for i in range(1, 41)],
            "checks": [{"id": "build", "kind": "build", "profiles": ["all"],
                        "command": [sys.executable, str(self.tool), "{run}", "build"]},
                       {"id": "java", "kind": "junit", "profiles": ["all"], "minimumCases": 1,
                        "requiredFamilies": {"RequiredTest": 1},
                        "command": [sys.executable, str(self.tool), "{run}", "test"]}]}
        self.plan["checks"].append({"id": "node-all", "kind": "node", "profiles": ["all"],
                                    "command": [sys.executable, str(self.tool)], "requiredCases": []})
        self.plan["knownOutcomeLedger"] = "ledger.json"
        (self.root / "ledger.json").write_text(json.dumps({"browserExpectedFailures": [], "retiredNodeTodos": []}))
        (self.root / "scripts").mkdir()
        (self.root / "scripts/playwright-suite.json").write_text('{"checks": []}')
        self.save_plan()
        self.git("init", "--quiet")
        self.git("config", "user.email", "synthetic@example.invalid")
        self.git("config", "user.name", "Synthetic test")
        self.commit()

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, stderr=subprocess.DEVNULL, text=True)

    def commit(self):
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", "test: controlled source")

    def save_plan(self):
        self.plan_path.write_text(json.dumps(self.plan))

    def run_check(self, name="java", profile="all", iteration=1):
        return gate.run_check(self.plan_path, name, self.evidence, profile, iteration, root=self.root)

    def seed(self):
        build = self.run_check("build")
        self.artifact = self.evidence / build["runId"] / "carlos.war"
        self.result = self.run_check()

    def verify(self):
        return gate.verify(self.plan_path, self.evidence, self.artifact, self.root)

    def test_clean_source_bound_control_is_ready(self):
        self.seed()
        result = self.verify()
        self.assertTrue(result["releaseReady"])
        self.assertEqual(result["requiredExecutions"], 2)
        self.assertEqual(result["receivedExecutions"], 2)

    def test_command_results_bind_underlying_inputs_through_final_verification(self):
        producer = self.root / "measured.py"
        producer.write_text('''import hashlib,json,pathlib,sys
run=pathlib.Path(sys.argv[1])
data=run/'metrics.json'; data.write_text('{"active":0}')
(run/'result.json').write_text(json.dumps({'schemaVersion':1,'assertions':{'restored':True},
 'inputs':[{'path':'metrics.json','sha256':hashlib.sha256(data.read_bytes()).hexdigest()}]}))
''')
        self.plan["checks"].append({"id": "measured", "kind": "command", "profiles": ["all"],
            "command": [sys.executable, str(producer), "{run}"],
            "resultContract": {"file": "result.json", "assertions": ["restored"]}})
        self.plan["workstreams"][0]["requiredChecks"].append("measured")
        self.save_plan()
        self.commit()
        self.seed()
        receipt = self.run_check("measured")
        self.assertEqual(receipt["outcome"], "PASS")
        self.assertEqual({row["path"] for row in receipt["inputs"]}, {"metrics.json", "result.json"})
        self.assertTrue(self.verify()["releaseReady"])
        (self.evidence / receipt["runId"] / "metrics.json").write_text('{"active":100}')
        with self.assertRaisesRegex(EvidenceError, "Missing or changed evidence input"):
            self.verify()

    def test_command_exit_zero_without_result_retains_failed_receipt(self):
        self.plan["checks"].append({"id": "noop", "kind": "command", "profiles": ["all"],
            "command": [sys.executable, "-c", "pass"],
            "resultContract": {"file": "result.json", "assertions": ["restored"]}})
        self.save_plan()
        self.commit()
        receipt = self.run_check("noop")
        self.assertEqual(receipt["outcome"], "FAIL")
        self.assertEqual(gate.read_json(self.evidence / receipt["runId"] / "receipt.json")["outcome"], "FAIL")

    def test_python_children_use_the_entrypoint_interpreter_without_venv_activation(self):
        alternate = self.base / "alternate-bin"
        alternate.mkdir()
        python = alternate / "python3"
        python.write_text("#!/bin/sh\nexit 91\n")
        python.chmod(0o700)
        self.plan["checks"][1]["command"][0] = "python3"
        self.save_plan()
        self.commit()
        with patch.dict(os.environ, {"PATH": str(alternate) + os.pathsep + os.environ["PATH"]}):
            result = self.run_check()
        self.assertEqual(result["exitCode"], 0)
        self.assertEqual(result["outcome"], "PASS")

    def test_missing_lane_is_incomplete(self):
        self.seed()
        shutil.rmtree(self.evidence / self.result["runId"])
        result = self.verify()
        self.assertFalse(result["releaseReady"])
        self.assertEqual(result["incomplete"][0]["checkId"], "java")

    def test_missing_build_is_incomplete(self):
        self.seed()
        artifact = self.base / "candidate.war"
        shutil.copyfile(self.artifact, artifact)
        shutil.rmtree(self.artifact.parent)
        self.artifact = artifact
        result = self.verify()
        self.assertFalse(result["releaseReady"])
        self.assertIn({"checkId": "build", "outcome": "MISSING_SOURCE_BOUND_BUILD"}, result["incomplete"])

    def test_missing_known_outcome_ledger_is_refused(self):
        del self.plan["knownOutcomeLedger"]
        self.save_plan()
        with self.assertRaisesRegex(EvidenceError, "knownOutcomeLedger"):
            gate.load_plan(self.plan_path, self.root)

    def test_scope_output_is_required_and_bound_to_its_receipt(self):
        scope_tool = self.root / "scope.py"
        scope_tool.write_text("import json,pathlib,subprocess,sys\n"
            "p=pathlib.Path(sys.argv[1]); p.write_text(json.dumps({'schemaVersion':1,'ready':True,"
            "'head':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()}))\n")
        self.plan["checks"].append({"id": "scope-inventory", "kind": "command", "profiles": ["all"],
            "command": [sys.executable, str(scope_tool), "{run}/scope.json"]})
        self.plan["workstreams"][0]["requiredChecks"].append("scope-inventory")
        self.save_plan(); self.commit(); self.seed()
        receipt = self.run_check("scope-inventory")
        self.assertEqual(receipt["outcome"], "PASS")
        self.assertEqual([row["path"] for row in receipt["inputs"]], ["scope.json"])
        self.assertTrue(self.verify()["releaseReady"])
        report = self.evidence / receipt["runId"] / "scope.json"
        report.write_text(report.read_text().replace('true', 'false'))
        with self.assertRaisesRegex(EvidenceError, "changed evidence input"):
            self.verify()
        report.unlink()
        with self.assertRaises(EvidenceError):
            gate.summarize(self.plan["checks"][-1], report.parent, 0, self.root)

    def test_duplicate_repetition_cannot_replace_a_missing_profile(self):
        self.seed()
        self.run_check()
        with self.assertRaisesRegex(EvidenceError, "duplicate"):
            self.verify()

    def test_source_change_invalidates_old_receipts(self):
        self.seed()
        (self.root / "new.py").write_text("# source changed")
        self.commit()
        with self.assertRaisesRegex(EvidenceError, "Stale"):
            self.verify()

    def test_dirty_and_untracked_source_are_refused_before_any_child(self):
        (self.root / "new.py").write_text("# not committed")
        with self.assertRaisesRegex(EvidenceError, "Commit source"):
            self.run_check()
        self.assertFalse(self.evidence.exists())

    def test_changed_plan_invalidates_old_receipts(self):
        self.seed()
        self.plan["checks"][1]["minimumCases"] = 2
        self.save_plan()
        self.commit()
        with self.assertRaises(EvidenceError):
            self.verify()

    def test_changed_xml_is_not_accepted_from_a_success_receipt(self):
        self.seed()
        report = self.evidence / self.result["runId"] / "reports/TEST-example.RequiredTest.xml"
        report.write_text(report.read_text().replace("</testcase>", "<flakyFailure/></testcase>"))
        with self.assertRaisesRegex(EvidenceError, "changed evidence"):
            self.verify()

    def test_tampered_counters_cannot_override_actual_flaky_failure(self):
        self.seed()
        directory = self.evidence / self.result["runId"]
        report = directory / "reports/TEST-example.RequiredTest.xml"
        report.write_text(report.read_text().replace("</testcase>", "<flakyFailure/></testcase>"))
        receipt = gate.read_json(directory / "receipt.json")
        receipt["inputs"][0]["sha256"] = gate.digest(report)
        (directory / "receipt.json").write_text(json.dumps(receipt))
        with self.assertRaisesRegex(EvidenceError, "counters disagree"):
            self.verify()

    def test_zero_exit_does_not_hide_failure_skip_or_empty_selection(self):
        for fault in ("<failure/>", "<error/>", "<flakyFailure/>", "<flakyError/>", "<rerunFailure/>", "<rerunError/>", "<skipped/>"):
            with self.subTest(fault=fault):
                self.plan["checks"][1]["command"] = [sys.executable, str(self.tool), "{run}", "test", fault]
                self.save_plan()
                self.commit()
                self.assertEqual(self.run_check()["outcome"], "FAIL")

    def test_nonzero_exit_cannot_be_overridden_by_clean_xml(self):
        self.plan["checks"][1]["command"] += ["", "--nonzero"]
        # An argv entry cannot be empty: use the final optional argument as the exit switch.
        self.plan["checks"][1]["command"][-2] = "<system-out/>"
        self.save_plan()
        self.commit()
        result = self.run_check()
        self.assertEqual(result["exitCode"], 7)
        self.assertEqual(result["outcome"], "FAIL")

    def test_child_source_mutation_is_not_release_evidence(self):
        self.plan["checks"][1]["command"] += ["<system-out/>", "--mutate"]
        self.save_plan()
        self.commit()
        result = self.run_check()
        self.assertEqual(result["outcome"], "FAIL")
        self.assertIn("changed during", result["reason"])

    def test_missing_executable_blocks_before_any_test_is_spawned(self):
        self.plan["checks"][1]["requiresExecutables"] = ["release-test-prerequisite-not-installed"]
        self.save_plan(); self.commit()
        result = self.run_check()
        self.assertEqual(result["outcome"], "BLOCKED")
        self.assertIsNone(result["exitCode"])
        self.assertFalse((self.evidence / result["runId"] / "reports").exists())

    def test_named_missing_prerequisite_produces_blocked_not_pass(self):
        self.plan["checks"][1]["requiresEnvironment"] = ["CARLOS_SYNTHETIC_UNSET_PREREQUISITE"]
        self.save_plan()
        self.commit()
        result = self.run_check()
        self.assertEqual(result["outcome"], "BLOCKED")
        self.assertIsNone(result["exitCode"])

    def test_external_acceptance_has_no_manually_green_bypass(self):
        self.plan["checks"][1].update(kind="external", reason="Vendor staging unavailable")
        self.save_plan()
        self.commit()
        self.seed()
        self.assertEqual(self.result["outcome"], "BLOCKED")
        self.assertFalse(self.verify()["releaseReady"])

    def test_invalid_profile_or_repeat_is_refused(self):
        for profile, iteration in (("wrong", 1), ("all", 0), ("all", 2)):
            with self.assertRaises(EvidenceError):
                self.run_check(profile=profile, iteration=iteration)

    def test_required_repetitions_and_profiles_cannot_be_omitted(self):
        self.plan["checks"][1].update(profiles=["ON", "BC"], repetitions=3)
        self.save_plan()
        self.commit()
        build = self.run_check("build")
        self.artifact = self.evidence / build["runId"] / "carlos.war"
        self.run_check(profile="ON", iteration=1)
        result = self.verify()
        self.assertFalse(result["releaseReady"])
        self.assertEqual(len(result["incomplete"]), 5)

    def test_non_applicable_case_requires_the_exact_passing_execution_in_its_named_lane(self):
        physical = dict(self.plan["checks"][1], id="java-physical")
        self.plan["checks"].append(physical)
        self.plan["checks"][1] = {**self.plan["checks"][1],
            "command": [*self.plan["checks"][1]["command"], "<skipped/>"],
            "routedSkips": {"example.RequiredTest": {"check": "java-physical", "cases": ["shouldWrite_atomically"]}}}
        for row in self.plan["workstreams"]:
            row["requiredChecks"].append("java-physical")
        self.save_plan(); self.commit(); self.seed()
        self.assertEqual(self.result["outcome"], "PASS", "Only the lane is complete; the skipped case remains routed")
        result = self.verify()
        self.assertFalse(result["releaseReady"])
        self.assertTrue(any(row["outcome"] == "ROUTED_CASE_NOT_EXECUTED" for row in result["incomplete"]))
        self.run_check("java-physical")
        self.assertTrue(self.verify()["releaseReady"])

    def test_receipt_paths_cannot_escape_owned_run(self):
        self.seed()
        path = self.evidence / self.result["runId"] / "receipt.json"
        receipt = gate.read_json(path)
        receipt["inputs"][0]["path"] = "../outside.xml"
        path.write_text(json.dumps(receipt))
        with self.assertRaisesRegex(EvidenceError, "escapes"):
            self.verify()

    def configure_parameterized_route(self, native_count):
        writer = self.root / "invocations.py"
        writer.write_text('''import pathlib,sys
directory=pathlib.Path(sys.argv[1]); (directory/'reports').mkdir()
names=['shouldWrite_atomically[1]','shouldWrite_atomically[2]'][:int(sys.argv[2])]
body=''.join('<testcase classname="example.RequiredTest" name="'+name+'"/>' for name in names)
(directory/'reports/TEST-example.RequiredTest.xml').write_text('<testsuite name="example.RequiredTest" tests="'+str(len(names))+'" failures="0" errors="0" skipped="0">'+body+'</testsuite>')
''')
        physical = dict(self.plan["checks"][1], id="java-physical",
                        command=[sys.executable, str(writer), "{run}", str(native_count)])
        self.plan["checks"].append(physical)
        self.plan["checks"][1].update(
            command=[*self.plan["checks"][1]["command"], "<skipped/>"],
            routedSkips={"example.RequiredTest": {"check": "java-physical", "cases": [
                {"name": "shouldWrite_atomically", "destinations": [
                    {"name": "shouldWrite_atomically[1]"}, {"name": "shouldWrite_atomically[2]"}]}]}})
        for row in self.plan["workstreams"]:
            row["requiredChecks"].append("java-physical")
        self.save_plan(); self.commit()

    def test_parameterized_route_accepts_all_exact_native_invocations(self):
        self.configure_parameterized_route(2)
        self.seed()
        self.assertEqual(self.run_check("java-physical")["outcome"], "PASS")
        self.assertTrue(self.verify()["releaseReady"])

    def test_parameterized_route_cannot_pass_with_only_one_native_invocation(self):
        self.configure_parameterized_route(1)
        self.seed()
        self.assertEqual(self.run_check("java-physical")["outcome"], "PASS")
        missing = self.verify()["incomplete"]
        self.assertEqual(missing, [{"checkId": "java-physical",
                                   "case": "example.RequiredTest::shouldWrite_atomically[2]",
                                   "outcome": "ROUTED_CASE_NOT_EXECUTED"}])

    def test_parameterized_routes_refuse_empty_or_duplicate_destinations(self):
        self.configure_parameterized_route(2)
        case = self.plan["checks"][1]["routedSkips"]["example.RequiredTest"]["cases"][0]
        for targets in ([], [{"name": "same"}, {"name": "same"}], [{"name": ""}]):
            with self.subTest(destinations=targets):
                case["destinations"] = targets
                self.save_plan()
                with self.assertRaisesRegex(EvidenceError, "destinations"):
                    gate.load_plan(self.plan_path, self.root)

    def test_symlink_and_missing_evidence_are_refused(self):
        self.seed()
        path = self.evidence / self.result["runId"] / "reports/TEST-example.RequiredTest.xml"
        original = path.read_bytes()
        path.unlink()
        outside = self.base / "outside.xml"
        outside.write_bytes(original)
        path.symlink_to(outside)
        with self.assertRaises(EvidenceError):
            self.verify()

    def test_duplicate_json_keys_are_refused(self):
        path = self.base / "duplicate.json"
        path.write_text('{"outcome":"FAIL","outcome":"PASS"}')
        with self.assertRaises(EvidenceError):
            gate.read_json(path)

    def test_workstream_and_source_omissions_are_refused(self):
        for mutate in (lambda p: p["workstreams"].pop(),
                       lambda p: p["workstreams"][0].update(requiredChecks=["not-registered"]),
                       lambda p: p["workstreams"][0].update(sources=["missing.java"])):
            plan = json.loads(json.dumps(self.plan))
            mutate(plan)
            self.plan_path.write_text(json.dumps(plan))
            with self.assertRaises(EvidenceError):
                gate.load_plan(self.plan_path, self.root)

    def test_receipts_do_not_export_private_logs_or_environment(self):
        self.seed()
        receipt = (self.evidence / self.result["runId"] / "receipt.json").read_text()
        self.assertNotIn("private.log", receipt)
        self.assertNotIn("PATH", receipt)
        self.assertNotIn("<testsuite", receipt)

    def test_cli_optimized_python_never_accepts_missing_evidence(self):
        self.seed()
        output = self.base / "summary.json"
        output.write_text("stale success")
        command = [sys.executable, "-O", str(Path(gate.__file__).with_name("verify-release-evidence.py")),
                   "--plan", str(self.plan_path), "verify", "--evidence", str(self.evidence),
                   "--artifact", str(self.artifact), "--output", str(output)]
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotEqual(output.read_text() if output.exists() else "", "stale success")

    def test_real_interruptions_terminate_owned_children_and_preserve_failed_evidence(self):
        waiter = self.root / "waiter.py"
        marker = self.base / "started"
        restored = self.base / "restored"
        waiter.write_text('''import os,signal,sys
from pathlib import Path
def stop(signum,frame):
 Path(sys.argv[2]).write_text('restored')
 raise SystemExit(143)
signal.signal(signal.SIGTERM,stop)
Path(sys.argv[1]).write_text(str(os.getpid()))
while True: signal.pause()
''')
        self.plan["checks"][1]["command"] = [sys.executable, str(waiter), str(marker), str(restored)]
        self.save_plan()
        driver = self.root / "driver.py"
        driver.write_text('''import signal,sys
from pathlib import Path
sys.path.insert(0,sys.argv[1])
from release_verification import run_check
def interrupted(signum,frame): raise KeyboardInterrupt
signal.signal(signal.SIGTERM,interrupted)
result=run_check(Path(sys.argv[2]),'java',Path(sys.argv[3]),'all',1,root=Path(sys.argv[4]))
raise SystemExit(0 if result['outcome']=='PASS' else 1)
''')
        self.commit()
        for interrupt in (signal.SIGINT, signal.SIGTERM):
            with self.subTest(signal=interrupt):
                marker.unlink(missing_ok=True); restored.unlink(missing_ok=True)
                process = subprocess.Popen([sys.executable, str(driver), str(Path(gate.__file__).parent),
                                            str(self.plan_path), str(self.evidence), str(self.root)],
                                           stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                try:
                    deadline = time.monotonic() + 5
                    while not marker.exists() and time.monotonic() < deadline and process.poll() is None:
                        time.sleep(0.01)
                    self.assertTrue(marker.exists(), "Wait on an explicit child-ready barrier")
                    process.send_signal(interrupt)
                    process.communicate(timeout=5)
                    self.assertNotEqual(process.returncode, 0)
                    self.assertEqual(restored.read_text(), "restored")
                    receipts = [gate.read_json(path) for path in self.evidence.glob('*/receipt.json')]
                    self.assertTrue(receipts, "Interrupted runs must preserve their receipts")
                    self.assertTrue(all(r['outcome'] == 'FAIL' and r.get('cancelled') for r in receipts))
                    self.assertTrue(all([p['stage'] for p in r['problems']] == ['execution', 'evidence'] for r in receipts))
                finally:
                    if process.poll() is None:
                        process.kill(); process.communicate(timeout=5)
                    if marker.exists():
                        child = int(marker.read_text())
                        cmdline = Path(f'/proc/{child}/cmdline')
                        if cmdline.exists() and str(waiter).encode() in cmdline.read_bytes():
                            os.killpg(child, signal.SIGKILL)


class DiscoveryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("java_discovery", Path(__file__).with_name("verify-java-discovery.py"))
        cls.discovery = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.discovery)

    def test_nested_parameterized_dynamic_and_missing_sources_are_reconciled(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "src"
            reports = root / "reports"
            source.mkdir(); reports.mkdir()
            (source / "RequiredTest.java").write_text("package example; class RequiredTest { @TestFactory void dynamic() {} }")
            (source / "AbstractTest.java").write_text("package example; abstract class AbstractTest { @Test void inherited() {} }")
            summary = self.discovery.reconcile(source, reports)
            self.assertFalse(summary["ready"])
            self.assertEqual(summary["sourceFamilies"], 1)
            (reports / "TEST-example.RequiredTest$Nested.xml").write_text(
                '<testsuite name="example.RequiredTest$Nested" tests="2" failures="0" errors="0" skipped="0">'
                '<testcase classname="example.RequiredTest$Nested" name="dynamic()[1]"/>'
                '<testcase classname="example.RequiredTest$Nested" name="dynamic()[2]"/></testsuite>')
            summary = self.discovery.reconcile(source, reports)
            self.assertTrue(summary["ready"])
            self.assertEqual(summary["executedCases"], 2)


if __name__ == "__main__":
    unittest.main()
