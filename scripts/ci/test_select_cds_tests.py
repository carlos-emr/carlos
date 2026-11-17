#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Exercise CDS selection against real Git changes without starting the CDS jobs."""
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

DIRECTORY = Path(__file__).resolve().parent
ROOT = DIRECTORY.parents[1]
SPEC = importlib.util.spec_from_file_location('select_cds_tests', DIRECTORY / 'select-cds-tests.py')
SELECT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SELECT)
CDS = 'src/main/java/io/github/carlos_emr/carlos/demographic/cdsimport/service/Example.java'


class CdsSelectionTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        # CI may itself run under a Git hook; isolate the fixture's Git metadata.
        self.env = {key: value for key, value in os.environ.items()
                    if not key.startswith('GIT_')}
        self.git('init', '-q', '-b', 'release/2026.08')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'user.name', 'CDS selection fixture')
        self.write('README.md')
        self.write(CDS)
        self.base = self.save()

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, env=self.env,
                                       stderr=subprocess.PIPE).decode().strip()

    def write(self, path, content='fixture\n'):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)

    def save(self):
        self.git('add', '.')
        self.git('commit', '-qm', 'fixture')
        return self.git('rev-parse', 'HEAD')

    def pr(self, branch='release/2026.08', base=None):
        return {'pull_request': {'base': {'ref': branch, 'sha': base or self.base},
                                 'head': {'sha': self.git('rev-parse', 'HEAD')}}}

    def selected(self, event_name, event):
        return SELECT.select(event_name, event, self.root)[0]

    def test_either_cds_changes_or_main_target_runs_tests(self):
        self.write('src/main/webapp/billing/unrelated.jsp')
        self.save()
        for branch in ('release/2026.08', 'develop', 'main'):
            with self.subTest(branch=branch, related=False):
                self.assertEqual(branch == 'main', self.selected('pull_request', self.pr(branch)))
        self.write(CDS, 'changed\n')
        self.save()
        for branch in ('release/2026.08', 'develop', 'main'):
            with self.subTest(branch=branch, related=True):
                self.assertTrue(self.selected('pull_request', self.pr(branch)))

    def test_retargeted_main_pr_runs_without_changed_files(self):
        event = self.pr('main')
        event.update(action='edited', changes={'base': {'ref': {'from': 'release/2026.08'}}})
        self.assertTrue(self.selected('pull_request', event))

    def test_target_branch_only_changes_do_not_select_unrelated_pr(self):
        self.git('checkout', '-qb', 'feature')
        self.write('README.md', 'feature\n')
        self.save()
        event = self.pr()
        self.git('checkout', '-q', 'release/2026.08')
        self.write(CDS, 'base branch change\n')
        event['pull_request']['base']['sha'] = self.save()
        self.assertFalse(self.selected('pull_request', event))

    def test_deleting_cds_source_runs_tests(self):
        (self.root / CDS).unlink()
        self.save()
        self.assertTrue(self.selected('pull_request', self.pr()))

    def test_rename_out_of_cds_scope_still_runs_tests(self):
        self.git('mv', CDS, 'renamed.java')
        self.save()
        self.assertTrue(self.selected('pull_request', self.pr()))

    def test_cds_change_after_more_than_300_unrelated_files_is_not_missed(self):
        for index in range(350):
            self.write(f'docs/unrelated-{index}.md')
        self.write(CDS, 'changed\n')
        self.save()
        self.assertTrue(self.selected('pull_request', self.pr()))

    def test_push_compares_every_commit_since_previous_head(self):
        self.write(CDS, 'changed\n')
        self.save()
        self.write('README.md', 'later unrelated commit\n')
        after = self.save()
        self.assertTrue(self.selected('push', {'before': self.base, 'after': after}))
        self.assertFalse(self.selected('push', {'before': self.git('rev-parse', 'HEAD^'), 'after': after}))

    def test_new_branch_and_existing_manual_runs_are_conservative(self):
        self.assertTrue(self.selected('push', {'before': '0' * 40, 'after': self.base}))
        self.assertTrue(self.selected('workflow_dispatch', {}))

    def test_missing_or_invalid_git_revision_cannot_silently_skip(self):
        event = self.pr()
        event['pull_request']['head']['sha'] = 'f' * 40
        with self.assertRaises(subprocess.CalledProcessError):
            self.selected('pull_request', event)
        event['pull_request']['head']['sha'] = '--output=unexpected'
        with self.assertRaises(ValueError):
            self.selected('pull_request', event)

    def test_cli_publishes_decision_and_summary(self):
        for name in ('select-cds-tests.py', 'cds-paths.txt', 'cds-coordinated-test-classes.json'):
            destination = self.root / 'scripts/ci' / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(DIRECTORY / name, destination)
        event = self.root / 'event.json'
        event.write_text(json.dumps(self.pr()))
        output, summary = self.root / 'output', self.root / 'summary'
        subprocess.run([sys.executable, str(self.root / 'scripts/ci/select-cds-tests.py')],
                       cwd=self.root, env={**self.env, 'GITHUB_EVENT_NAME': 'pull_request',
                           'GITHUB_EVENT_PATH': str(event), 'GITHUB_OUTPUT': str(output),
                           'GITHUB_STEP_SUMMARY': str(summary)}, check=True, capture_output=True)
        self.assertEqual('run-tests=false\n', output.read_text())
        self.assertIn('**skip**', summary.read_text())


class CdsPathInventoryTest(unittest.TestCase):
    def test_shared_dependencies_and_cds_assets_are_selected(self):
        patterns = SELECT.related_paths()
        paths = [
            'src/main/java/io/github/carlos_emr/carlos/commn/model/Drug.java',
            'src/main/java/io/github/carlos_emr/carlos/utility/OscarTrackingBasicDataSource.java',
            'src/main/resources/cds-import/schema/EMR_Data_Migration_Schema.xsd',
            'src/main/resources/spring_cds_export.xml',
            'src/main/webapp/WEB-INF/jsp/demographic/demographicImport.jsp',
            'src/main/webapp/js/demographic/cds-export.js',
            'src/test/resources/demographic/cds-import-all-categories.xml',
            'database/mysql/migration/common/V1.0.59.50__cds_preparation_payload_capacity.sql',
            'scripts/ci/cds-paths.txt', 'scripts/ci/cds-coordinated-test-classes.json',
            'scripts/ci/run-cds-native-tier.py', 'scripts/ci/compiled_classes.py',
            '.github/workflows/cds-change-detection.yml', '.github/actions/dev-container/action.yml',
            '.devcontainer/development/Dockerfile', 'debian/carlos-ctl.pin', 'pom.xml',
            # This shared test has no CDS identifier in its filename.
            'src/test/java/io/github/carlos_emr/carlos/app/contract/MutatorActionGetRejectionContractUnitTest.java',
        ]
        for path in paths:
            with self.subTest(path=path):
                self.assertTrue(SELECT.is_related(path, patterns))

    def test_unrelated_application_docs_and_ci_changes_are_skipped(self):
        for path in ('README.md', 'docs/release-process.md', 'src/main/webapp/billing/invoice.jsp',
                     'src/main/java/io/github/carlos_emr/carlos/billing/Billing2Action.java',
                     'src/main/resources/billing_invoice_email.properties',
                     'scripts/ci/report-codefactor-findings.py', '.github/workflows/pmd.yml'):
            with self.subTest(path=path):
                self.assertFalse(SELECT.is_related(path, SELECT.related_paths()))

    def test_local_java_dependencies_imported_by_cds_engines_remain_selected(self):
        source_root = ROOT / 'src/main/java'
        patterns = SELECT.related_paths()
        for component in ('cds', 'cdsimport', 'cdsexport'):
            for source in source_root.glob('**/demographic/' + component + '/**/*.java'):
                for name in re.findall(r'^import\s+(?:static\s+)?([\w.]+);', source.read_text(), re.M):
                    path = source_root / (name.replace('.', '/') + '.java')
                    if path.is_file():
                        with self.subTest(dependency=str(path.relative_to(ROOT))):
                            self.assertTrue(SELECT.is_related(str(path.relative_to(ROOT)), patterns))


if __name__ == '__main__':
    unittest.main()
