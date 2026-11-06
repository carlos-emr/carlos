# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from process_group import ProcessGroupFailure
spec = importlib.util.spec_from_file_location('signals', Path(__file__).with_name('run-installed-smoke-signals.py'))
probe = importlib.util.module_from_spec(spec); spec.loader.exec_module(probe)


class InstalledSignalEvidenceTest(unittest.TestCase):
    def rows(self):
        return [{'name': name, 'requestedSignal': signal, 'outcome': 'PASS', 'detail': 'interrupted',
            'delivery': 'os', 'signal': None, 'exit': 130 if signal == 'SIGINT' else 143,
            'checkpoint': probe.CHECKPOINTS[name], 'credentialRestored': True,
            'residue': [], 'fileChanges': {'added': 0, 'removed': 0, 'changed': 0}}
            for name, signal in sorted(probe.EXPECTED)]

    def test_omitted_duplicated_or_wrong_signal_execution_cannot_qualify(self):
        rows = self.rows(); self.assertTrue(all(probe.validate(rows).values()))
        for invalid in (rows[:-1], rows + [rows[0]], rows[:-1] + [rows[0]]):
            self.assertFalse(probe.validate(invalid)['all-six-real-os-signals'])
        for field, value in [('delivery', 'handler'), ('exit', 0), ('outcome', 'SKIP'), ('detail', 'timeout')]:
            invalid = copy.deepcopy(rows); invalid[0][field] = value
            self.assertFalse(probe.validate(invalid)['all-six-real-os-signals'])

    def test_row_counts_alone_cannot_hide_changed_credentials_or_leftover_files(self):
        for field, value in [('credentialRestored', False), ('credentialRestored', None),
                             ('residue', [{'table': 'security'}]), ('fileChanges', {'added': False, 'removed': 0, 'changed': 0})]:
            rows = self.rows(); row = next(row for row in rows if row['name'] == 'login'); row[field] = value
            self.assertFalse(probe.validate(rows)['all-owned-state-restored'])

    def test_timeout_records_failure_and_stops_before_another_installed_audit(self):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.dict(os.environ, {'CARLOS_DISPOSABLE_HOST': 'true', 'CARLOS_SUITE_PROVINCE': 'ON'}), \
                patch.object(probe, 'run_process_group', side_effect=ProcessGroupFailure('timeout', True)) as runner:
            output = Path(temporary) / 'result.json'
            self.assertEqual(probe.main(['--profile', 'ON', '--output', str(output)]), 1)
            runner.assert_called_once()
            self.assertGreater(runner.call_args.kwargs['timeout'], 300)
            result = json.loads(output.read_text())
            self.assertFalse(any(result['assertions'].values()))
            failure = json.loads((output.parent / 'signal-details.json').read_text())['failure']
            self.assertTrue(failure['processGroupStopped'])
            self.assertFalse(failure['restorationVerified'])
            self.assertTrue(failure['hostRequiresReconciliation'])
