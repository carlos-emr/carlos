# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import release_verification as gate
from junit_evidence import EvidenceError


def module_from(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class PythonOutcomeTest(unittest.TestCase):
    def test_python_required_case_cannot_be_replaced_by_an_unrelated_pass(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'python.json'
            record = {'schemaVersion': 1, 'tests': 2, 'ready': True, 'duplicateIdentities': False,
                'cases': [{'id': 'fake.required', 'outcomes': ['PASS']}, {'id': 'fake.other', 'outcomes': ['PASS']}]}
            path.write_text(json.dumps(record))
            self.assertTrue(gate.python_results(path, 2, ['fake.required'])['ready'])
            record['cases'][0]['id'] = 'fake.replacement'
            path.write_text(json.dumps(record))
            with self.assertRaisesRegex(EvidenceError, 'required Python'):
                gate.python_results(path, 2, ['fake.required'])

    def test_primary_and_cleanup_failures_are_both_retained_without_messages(self):
        adapter = module_from("strict_python", "run-python-release-tests.py")
        class Controlled(unittest.TestCase):
            def test_primary(self):
                self.fail("private synthetic primary")
            def tearDown(self):
                raise RuntimeError("private synthetic cleanup")
        result = adapter.StrictResult()
        unittest.defaultTestLoader.loadTestsFromTestCase(Controlled).run(result)
        self.assertEqual(result.testsRun, 1)
        self.assertEqual(result.cases[0]["outcomes"], ["FAIL", "ERROR"])
        self.assertNotIn("private synthetic", json.dumps(result.cases))

    def test_skip_expected_failure_unexpected_pass_and_subtest_failures_remain_incomplete(self):
        adapter = module_from("strict_python", "run-python-release-tests.py")
        class Controlled(unittest.TestCase):
            @unittest.skip("synthetic")
            def test_skip(self):
                pass
            @unittest.expectedFailure
            def test_expected(self):
                self.fail("synthetic")
            @unittest.expectedFailure
            def test_unexpected(self):
                pass
            def test_subtest(self):
                with self.subTest(private="synthetic"):
                    self.fail("synthetic")
        result = adapter.StrictResult()
        unittest.defaultTestLoader.loadTestsFromTestCase(Controlled).run(result)
        self.assertEqual({outcome for row in result.cases for outcome in row["outcomes"]},
                         {"SKIP", "EXPECTED_FAILURE", "UNEXPECTED_PASS", "SUBTEST_FAIL"})

    def test_python_result_counters_cannot_hide_case_outcomes(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "python.json"
            result = {"schemaVersion": 1, "tests": 1, "ready": True, "duplicateIdentities": False,
                      "cases": [{"id": "example.test_required", "outcomes": ["PASS"]}]}
            path.write_text(json.dumps(result))
            self.assertTrue(gate.python_results(path)["ready"])
            for outcomes in ([], ["FAIL"], ["PASS", "ERROR"], ["SKIP"], ["UNEXPECTED_PASS"]):
                result["cases"][0]["outcomes"] = outcomes
                path.write_text(json.dumps(result))
                self.assertFalse(gate.python_results(path)["ready"])
            result.update(tests=0, cases=[])
            path.write_text(json.dumps(result))
            self.assertFalse(gate.python_results(path)["ready"])


class ResultInventoryTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / "scripts").mkdir()

    def test_node_omitted_file_duplicate_case_or_todo_is_not_complete(self):
        for file in ("one.test.js", "two.test.js"):
            (self.root / "scripts" / file).write_text("// synthetic test")
        result = {"schemaVersion": 2, "files": ["scripts/one.test.js", "scripts/two.test.js"], "summary": {
            "success": True, "counts": {"tests": 2, "passed": 2, "failed": 0, "cancelled": 0, "skipped": 0, "todo": 0}},
            "cases": [{"file": "scripts/one.test.js", "line": 1, "column": 1, "name": "one", "outcome": "PASS", "testNumber": 1, "parents": []},
                      {"file": "scripts/two.test.js", "line": 1, "column": 1, "name": "two", "outcome": "PASS", "testNumber": 1, "parents": []}]}
        path = self.root / "node.json"
        def read():
            path.write_text(json.dumps(result))
            return gate.node_results(path, self.root)
        self.assertTrue(read()["ready"])
        original = dict(result["cases"][1])
        result["cases"][1] = dict(result["cases"][0])
        with self.assertRaisesRegex(EvidenceError, "Duplicate Node"):
            read()
        result["cases"][1] = original
        result["cases"][1]["outcome"] = "TODO"
        self.assertFalse(read()["ready"])
        result["files"].pop()
        with self.assertRaises(EvidenceError):
            read()

    def test_browser_inventory_preserves_profiles_manual_checks_and_case_insensitive_provinces(self):
        checks = [{"name": "required", "provinces": ["on"]},
                  {"name": "other-profile", "provinces": ["all"], "profile": "multisite"},
                  {"name": "manual", "provinces": ["all"], "manual": True}]
        (self.root / "scripts/playwright-suite.json").write_text(json.dumps({"checks": checks}))
        path = self.root / "browser.xml"
        path.write_text('<testsuites><testsuite><testcase name="required"/></testsuite></testsuites>')
        check = {"selectedProfile": "ON"}
        self.assertTrue(gate.browser_results(path, check, self.root)["ready"])
        path.write_text('<testsuites><testsuite><testcase name="required"/><testcase name="required"/></testsuite></testsuites>')
        with self.assertRaises(EvidenceError):
            gate.browser_results(path, check, self.root)
        path.write_text('<testsuites><testsuite><testcase name="manual"/></testsuite></testsuites>')
        self.assertTrue(gate.browser_results(path, {**check, "selection": ["manual"]}, self.root)["ready"])
        path.write_text('<testsuites><testsuite><testcase name="required"><failure type="release.readiness"/></testcase></testsuite></testsuites>')
        self.assertFalse(gate.browser_results(path, check, self.root)["ready"])


class ScopeInventoryTest(unittest.TestCase):
    def test_reviewed_ownership_does_not_waive_rewritten_shared_migration_bytes(self):
        scope = module_from('scope_migration_command', 'release-scope-audit.py')
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=root, stderr=subprocess.DEVNULL, text=True).strip()
            def commit():
                git('add', '.'); git('-c', 'user.name=Synthetic', '-c', 'user.email=synthetic@example.invalid',
                                     'commit', '-qm', 'test: migration fixture')
                return git('rev-parse', 'HEAD')
            git('init', '-q')
            relative = 'database/mysql/migration/common/V1__FAKE.sql'
            path = root / relative; path.parent.mkdir(parents=True); path.write_text('SELECT 1;\n')
            (root / 'test.py').write_text('# concrete synthetic regression')
            base = commit(); path.write_text('SELECT 2;\n'); commit()
            plan = {'baselines': {'currentMain': base}, 'checks': [{'id': 'required'}],
                'workstreams': [{'id': 'T01'}], 'pathBindings': {relative: {'reason': 'reviewed ownership',
                    'tests': ['test.py'], 'checks': ['required'], 'workstreams': ['T01']}}}
            output = root / 'output'; output.mkdir()
            inventory = scope.inventory
            with patch.object(scope, 'load_plan', return_value=plan), patch.object(scope, 'inventory',
                    side_effect=lambda p: inventory(p, root)):
                self.assertEqual(scope.main(['--output', str(output / 'scope.json'),
                    '--result-output', str(output / 'scope-result.json')]), 1)
            result = gate.read_json(output / 'scope-result.json')
            self.assertTrue(result['assertions']['complete-production-ownership'])
            self.assertFalse(result['assertions']['published-migrations-byte-identical'])

    def test_scope_command_retains_real_unowned_paths_and_cannot_qualify_inventory_only(self):
        scope = module_from("scope_command", "release-scope-audit.py")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.DEVNULL).decode().strip()
            git("init", "--quiet")
            git("config", "user.email", "synthetic@example.invalid")
            git("config", "user.name", "Synthetic test")
            (root / "baseline").write_text("synthetic")
            git("add", "."); git("commit", "--quiet", "-m", "test: baseline")
            baseline = git("rev-parse", "HEAD")
            source = root / "src/main/java/example/Guard.java"
            source.parent.mkdir(parents=True)
            source.write_text("class Guard {}")
            git("add", "."); git("commit", "--quiet", "-m", "test: changed guard")
            plan = {"baselines": {"currentMain": baseline}, "checks": [], "workstreams": []}
            out = root / "run"; out.mkdir()
            details = out / "scope-details.json"; result = out / "scope-result.json"
            inventory = scope.inventory
            with patch.object(scope, "load_plan", return_value=plan), patch.object(
                    scope, "inventory", side_effect=lambda p: inventory(p, root)):
                self.assertEqual(scope.main(["--output", str(details), "--result-output", str(result)]), 1)
                contract = {"id": "scope-and-main-disposition", "kind": "command", "resultContract": {"file": "scope-result.json",
                    "assertions": ["complete-production-ownership", "main-only-migration-dispositions", "published-migrations-byte-identical"]}}
                self.assertFalse(gate.summarize(contract, out, 0)["ready"])
                self.assertEqual(gate.read_json(details)["unownedProductionPaths"], ["src/main/java/example/Guard.java"])
                result.unlink()
                self.assertEqual(scope.main(["--output", str(details), "--result-output", str(result), "--inventory-only"]), 1)
                self.assertFalse(result.exists())

    def test_scope_requires_baseline_workstreams_and_hash_bound_migration_dispositions(self):
        scope = module_from("release_scope", "release-scope-audit.py")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.DEVNULL).decode().strip()
            git("init", "--quiet")
            git("config", "user.email", "synthetic@example.invalid")
            git("config", "user.name", "Synthetic test")
            migration = "database/mysql/migration/common/V1.sql"
            path = root / migration
            path.parent.mkdir(parents=True)
            path.write_text("-- synthetic main-only migration")
            (root / "test.py").write_text("# concrete source reference")
            git("add", "."); git("commit", "--quiet", "-m", "test: main baseline")
            baseline = git("rev-parse", "HEAD")
            path.unlink()
            git("add", "."); git("commit", "--quiet", "-m", "test: release scope")
            plan = {"baselines": {}, "checks": [{"id": "required"}], "workstreams": [{"id": "T01"}]}
            with self.assertRaisesRegex(EvidenceError, "currentMain"):
                scope.inventory(plan, root)
            plan["baselines"]["currentMain"] = baseline
            plan["pathBindings"] = {migration: {"tests": ["test.py"], "checks": ["required"], "reason": "reviewed"}}
            with self.assertRaisesRegex(EvidenceError, "workstream"):
                scope.inventory(plan, root)
            plan["pathBindings"][migration]["workstreams"] = ["T01"]
            report = scope.inventory(plan, root)
            sha256 = report["mainOnlyMigrations"][0]["sha256"]
            for disposition in (None, "TODO", {}, {"reason": "reviewed"},
                                {"sha256": "0" * 64, "reason": "reviewed"},
                                {"sha256": sha256, "reason": "  "}):
                with self.subTest(disposition=disposition):
                    plan["mainOnlyMigrationDispositions"] = {migration: disposition}
                    self.assertEqual(scope.inventory(plan, root)["unresolvedMainOnlyMigrations"], [migration])
            plan["mainOnlyMigrationDispositions"][migration] = {"sha256": sha256, "reason": "reviewed exclusion"}
            self.assertTrue(scope.inventory(plan, root)["ready"])
        with tempfile.TemporaryDirectory() as temporary:
            document = gate.read_json(gate.DEFAULT_PLAN)
            document.pop('lifecycleBaselineManifest')
            path = Path(temporary) / 'plan.json'; path.write_text(json.dumps(document))
            with self.assertRaisesRegex(EvidenceError, 'published migration baselines'):
                gate.load_plan(path)

    def test_full_diff_preserves_more_than_300_paths_deletions_and_binary_files(self):
        scope = module_from("release_scope", "release-scope-audit.py")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.DEVNULL).decode().strip()
            git("init", "--quiet")
            git("config", "user.email", "synthetic@example.invalid")
            git("config", "user.name", "Synthetic test")
            (root / "removed.java").write_text("synthetic")
            git("add", "."); git("commit", "--quiet", "-m", "test: baseline")
            baseline = git("rev-parse", "HEAD")
            (root / "removed.java").unlink()
            for index in range(321):
                path = root / f"src/main/java/Synthetic{index}.java"
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("// synthetic")
            (root / "binary.bin").write_bytes(bytes(range(256)))
            git("add", "."); git("commit", "--quiet", "-m", "test: changed source")
            changes = scope.comparison(root, baseline, "HEAD")
            self.assertEqual(len(changes), 323)
            self.assertIn(("D", "removed.java"), changes)
            self.assertIn(("A", "binary.bin"), changes)
            result = scope.inventory({"baselines": {"olderMain": baseline, "currentMain": baseline}, "checks": [], "workstreams": []}, root)
            self.assertEqual(len(result["unownedProductionPaths"]), 321)
            self.assertFalse(result["ready"])

    def test_production_classification_does_not_hide_migrations_or_package_scripts(self):
        scope = module_from("release_scope", "release-scope-audit.py")
        for path in ("database/mysql/migration/common/V1.sql", "debian/carlos-emr.postrm", "debian/assets/bin/carlos-emr-backup",
                     "src/main/webapp/WEB-INF/jsp/fragment.jspf", "src/main/resources/messages_fr.properties", "package-lock.json", "local_repo/cds/library.jar"):
            self.assertTrue(scope.runtime_path(path), path)
        for path in ("debian/assets/tests/test_backup_restic.py", "debian/tests/control",
                     "src/test/java/ExampleUnitTest.java"):
            self.assertFalse(scope.runtime_path(path), path)


if __name__ == "__main__":
    unittest.main()
