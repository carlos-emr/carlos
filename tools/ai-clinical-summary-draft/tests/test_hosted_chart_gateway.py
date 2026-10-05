# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
import uuid
from unittest.mock import patch
sys.path[:0] = [str(Path(__file__).resolve().parents[1] / 'browser')]
import hosted_chart_gateway as hosted


class HostedChartGatewayTest(unittest.TestCase):
    def test_cache_revalidates_source_output_and_uses_fresh_request_id(self):
        with tempfile.TemporaryDirectory() as directory:
            gateway = hosted.Gateway(hosted.agent.DEFAULTS, directory,
                                     transport=lambda *_: self.fail('Unexpected network call'))
            source = gateway.trial_notes[0][2]
            request = dict(contract_version=1, request_id=str(uuid.uuid4()), workflow='chart-update-proposals',
                           data_classification='clinical-document', instructions=hosted.chart_updates.PROMPT,
                           output_schema=hosted.chart_updates.SCHEMA,
                           sources=[dict(id='document', title='Document', text=source)])
            with patch.object(gateway, 'complete', return_value={'proposals': []}) as complete:
                first = gateway.run_chart_updates(request)
                self.assertEqual(2, complete.call_count)
            request['request_id'] = str(uuid.uuid4())
            second = gateway.run_chart_updates(request)
            self.assertEqual(request['request_id'], second['request_id'])
            self.assertEqual(first['output'], second['output'])
            self.assertEqual(1, gateway.cache_hits)
            bad = copy.deepcopy(request)
            bad['sources'][0]['text'] += ' unapproved addition'
            with self.assertRaises(ValueError):
                gateway.run_chart_updates(bad)
            gateway.cache_file(request).write_text(json.dumps({'proposals': [
                {'kind': 'history', 'evidence': 'fabricated'}]}))
            with self.assertRaises(ValueError):
                gateway.run_chart_updates(request)
            old = gateway.cache_file(request)
            gateway.implementation = 'changed'
            self.assertNotEqual(old, gateway.cache_file(request))

    def test_chart_completion_budget_does_not_change_summary_budget(self):
        with tempfile.TemporaryDirectory() as directory:
            config = dict(hosted.agent.DEFAULTS, max_tokens=4096)
            gateway = hosted.Gateway(config, directory)
            self.assertEqual(4096, gateway.config['max_tokens'])
            self.assertEqual(8192, gateway.chart_config['max_tokens'])
            self.assertEqual(4096, config['max_tokens'])

    def test_compiled_fixture_is_not_added_to_summary_allowlist(self):
        with tempfile.TemporaryDirectory() as directory:
            gateway = hosted.Gateway(hosted.agent.DEFAULTS, directory)
            self.assertEqual(len(gateway.allowed.notes) + 1, len(gateway.trial_notes))
            self.assertNotIn(gateway.trial_notes[-1], gateway.allowed.notes)


if __name__ == '__main__':
    unittest.main()
