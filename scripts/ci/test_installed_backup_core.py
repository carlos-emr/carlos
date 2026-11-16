# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Fault controls for installed backup observations; these do not replace restic qualification."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from junit_evidence import EvidenceError

SPEC = importlib.util.spec_from_file_location('backup_core', Path(__file__).with_name('run-installed-backup-core.py'))
core = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(core)


class InstalledBackupCoreTest(unittest.TestCase):
    def test_selected_province_must_match_actual_unambiguous_installed_configuration(self):
        with tempfile.TemporaryDirectory() as temporary:
            properties = Path(temporary) / 'FAKE.properties'
            for profile in ('ON', 'BC'):
                properties.write_text('billregion=' + profile + '\n')
                self.assertEqual(core.installed_profile(profile, properties), profile)
                with self.assertRaises(EvidenceError):
                    core.installed_profile('BC' if profile == 'ON' else 'ON', properties)
            for text in ('', '# billregion=ON\n', 'billregion=ON\nbillregion=BC\n',
                         'billregion=ON\nbillregion=ON\n', 'billregion=ON # comment\n'):
                properties.write_text(text)
                with self.assertRaises(EvidenceError): core.installed_profile('ON', properties)

    def test_streaming_plaintext_scan_detects_boundary_spanning_and_binary_markers(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'encrypted-FAKE'
            marker = b'FAKE\x00canary'
            path.write_bytes(b'x' * (1024 * 1024 - 3) + marker + b'y' * 40)
            self.assertTrue(core.contains_plaintext(path, marker))
            self.assertFalse(core.contains_plaintext(path, b'FAKE-other'))
            self.assertTrue(core.contains_plaintext(path, b'\x00'))

    def test_configuration_bytes_modes_and_empty_directories_are_independent(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); config = root / 'carlos.properties'; config.write_text('FAKE-key=one')
            (root / 'empty').mkdir(); before = core.configuration_state(root)
            config.write_text('FAKE-key=two'); self.assertNotEqual(before, core.configuration_state(root))
            config.write_text('FAKE-key=one'); config.chmod(0o400)
            self.assertNotEqual(before, core.configuration_state(root))
            config.chmod(before['file:carlos.properties'][1]); self.assertEqual(before, core.configuration_state(root))
            (root / 'empty').rmdir(); self.assertNotEqual(before, core.configuration_state(root))

    def test_file_and_operational_parent_links_cannot_reach_outside_owned_roots(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); outside = root / 'outside'; outside.mkdir()
            actual = outside / 'FAKE'; actual.write_text('preserve')
            linked = root / 'linked'; linked.symlink_to(outside, target_is_directory=True)
            with self.assertRaises(EvidenceError): core.require_unlinked_paths([linked / 'new-repository'])
            file_link = root / 'file-link'; file_link.symlink_to(actual)
            with self.assertRaises(OSError): core.file_state(file_link)
            self.assertEqual(actual.read_text(), 'preserve')

    def test_sql_restore_cannot_hide_ddl_change_behind_equal_rows(self):
        with patch.object(core.state, 'query', side_effect=lambda sql: ['id\tint'] if sql.startswith('SHOW COLUMNS') else ['["H31"]']), \
                patch.object(core, 'table_ddl', return_value='CREATE TABLE `FAKE` (`id` int)') as ddl:
            before = core.restore_state('FAKE-schema', ['FAKE'])
            ddl.return_value = 'CREATE TABLE `FAKE` (`id` int, KEY (`id`))'
            after = core.restore_state('FAKE-schema', ['FAKE'])
            self.assertEqual(before['FAKE']['sha256'], after['FAKE']['sha256'])
            self.assertEqual(before['FAKE']['rows'], after['FAKE']['rows'])
            self.assertNotEqual(before, after)

    def test_sql_ddl_warnings_wrong_table_and_entities_cannot_certify_restore(self):
        valid = '<resultset><row><field name="Table">FAKE</field><field name="Create Table">CREATE TABLE `FAKE` (`id` int)</field></row></resultset>'
        cases = [subprocess.CompletedProcess([], 0, valid, ''),
            subprocess.CompletedProcess([], 0, valid, 'Warning: truncated'),
            subprocess.CompletedProcess([], 0, valid.replace('>FAKE<', '>OTHER<'), ''),
            subprocess.CompletedProcess([], 0, '<!DOCTYPE x [<!ENTITY a "FAKE">]>' + valid, ''),
            subprocess.CompletedProcess([], 1, valid, '')]
        with patch.object(core.subprocess, 'run', return_value=cases[0]):
            self.assertTrue(core.table_ddl('`FAKE`', 'FAKE').startswith('CREATE TABLE'))
        for result in cases[1:]:
            with patch.object(core.subprocess, 'run', return_value=result), self.assertRaises((EvidenceError, ValueError)):
                core.table_ddl('`FAKE`', 'FAKE')

    def test_named_canary_cleanup_preserves_replaced_or_preexisting_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); existing = root / 'existing'; existing.write_text('FAKE-existing')
            own = root / 'owned'; own.write_text('FAKE-owned')
            def record(path, created):
                info = path.lstat()
                return {'path': str(path), 'created': created, 'state': core.file_state(path),
                    'identity': [info.st_dev, info.st_ino]}
            old = record(existing, False); current = record(own, True)
            replacement = root / 'replacement'; replacement.write_bytes(own.read_bytes()); replacement.chmod(own.stat().st_mode)
            replacement.replace(own)
            with self.assertRaises(EvidenceError): core.cleanup_named_canaries([old, current], [])
            self.assertTrue(own.exists()); self.assertTrue(existing.exists())
            core.cleanup_named_canaries([old], []); self.assertTrue(existing.exists())

    def refusal(self, mutation=None, *, stopped=True):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve(); repository = root / 'repository'; repository.mkdir()
            (repository / 'config').write_text('FAKE-original-history')
            original = repository.stat().st_ino; output = root / 'output'; output.mkdir(mode=0o700)
            markers = {'first': ['FAKE'], 'second': ['FAKE'], 'freshness': ['FAKE']}
            inventory = [{'id': 'FAKE-snapshot'}]; calls = []; starts = []
            def run(command, log, **kwargs):
                calls.append(command); content = ''
                if command[:2] == ['systemctl', 'start']:
                    starts.append(True)
                    if mutation == 'replacement':
                        repository.mkdir(); (repository / 'config').write_text('FAKE-unexpected')
                    code = 0 if mutation == 'zero-exit' else 1
                else: code = 0
                if log.name == 'private-refusal-cursor.json': content = json.dumps({'__CURSOR': 'FAKE-cursor'})
                if log.name == 'private-refusal-journal.jsonl':
                    content = json.dumps({'MESSAGE': 'FAKE unrelated failure' if mutation == 'unrelated-log'
                        else 'backup repository is unreachable or damaged — NOT re-initialising'})
                log.write_text(content)
                return code
            marker_after = copy.deepcopy(markers)
            if mutation == 'marker-changed': marker_after['freshness'] = ['different']
            if mutation == 'missing-marker': markers['first'] = None
            after = [{'id': 'OTHER'}] if mutation == 'inventory-changed' else inventory
            with patch.object(core, 'REPOSITORY', repository), patch.object(core, 'run', side_effect=run), \
                    patch.object(core, 'worker_quiescent', return_value=True), \
                    patch.object(core, 'stop_workers', return_value=stopped), \
                    patch.object(core, 'snapshots', side_effect=[inventory, after]), \
                    patch.object(core, 'marker_state', side_effect=[markers, marker_after]):
                if mutation in ('replacement', 'missing-marker') or not stopped:
                    with self.assertRaises(EvidenceError): core.exercise_missing_repository(output)
                    if mutation == 'missing-marker': self.assertEqual(starts, [])
                    else:
                        retained = list(root.glob('FAKE5700-repository-*'))
                        self.assertEqual(len(retained), 1)
                        self.assertEqual((retained[0] / 'config').read_text(), 'FAKE-original-history')
                    if mutation == 'replacement': self.assertEqual((repository / 'config').read_text(), 'FAKE-unexpected')
                    return
                result = core.exercise_missing_repository(output)
                self.assertEqual(repository.stat().st_ino, original)
                self.assertEqual((repository / 'config').read_text(), 'FAKE-original-history')
                self.assertEqual(result, mutation is None)
                journal = [call for call in calls if call[:1] == ['journalctl'] and any(arg.startswith('--after-cursor=') for arg in call)]
                self.assertEqual(len(journal), 1); self.assertIn('--after-cursor=FAKE-cursor', journal[0])

    def test_missing_repository_uses_only_fresh_journal_and_restores_original_inode(self): self.refusal()

    def test_success_or_unrelated_failure_cannot_qualify_repository_safety(self):
        for mutation in ('zero-exit', 'unrelated-log'):
            with self.subTest(mutation=mutation): self.refusal(mutation)

    def test_repository_probe_rejects_changed_history_or_freshness_markers(self):
        for mutation in ('inventory-changed', 'marker-changed'):
            with self.subTest(mutation=mutation): self.refusal(mutation)

    def test_missing_prior_markers_refuse_fault_injection_before_rename(self): self.refusal('missing-marker')

    def test_replacement_repository_is_preserved_for_reconciliation(self): self.refusal('replacement')

    def test_live_workers_prevent_restoration_and_preserve_original_history(self): self.refusal(stopped=False)
