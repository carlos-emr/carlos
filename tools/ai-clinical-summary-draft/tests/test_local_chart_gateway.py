# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
import uuid
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'browser'))
import local_chart_gateway as local


class LocalChartGatewayTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.gateway = local.Gateway(self.directory.name)
        self.source = self.gateway.notes[-1][2]
        self.request = {'contract_version': 1, 'request_id': str(uuid.uuid4()),
                        'workflow': 'chart-update-proposals', 'data_classification': 'clinical-document',
                        'instructions': local.chart_updates.PROMPT, 'output_schema': local.chart_updates.SCHEMA,
                        'sources': [{'id': 'document', 'title': 'Document', 'text': self.source}]}
        self.output = {'proposals': [{'kind': 'history', 'evidence': self.source[:100]}]}
        self.calls = []

    def call(self, route, data=None):
        self.calls.append(route)
        return {'/api/show': {}, '/api/tags': {'models': [{'name': local.MODEL, 'digest': 'a'*64}]},
                '/api/version': {'version': 'test'}, '/api/generate': {'model': local.MODEL, 'done': True,
                'done_reason': 'stop', 'response': json.dumps(self.output)}}[route]

    def test_rejects_modified_source_before_model_access(self):
        request = copy.deepcopy(self.request)
        request['sources'][0]['text'] += '\nAdditional clinical text'
        with patch.object(self.gateway, 'call') as call, self.assertRaises(ValueError):
            self.gateway.generate(request)
        call.assert_not_called()

    def test_cache_reuses_validated_output_with_new_request_identity(self):
        with patch.object(self.gateway, 'call', side_effect=self.call):
            first = self.gateway.generate(self.request)
            request = dict(self.request, request_id=str(uuid.uuid4()))
            second = self.gateway.generate(request)
        self.assertEqual(first['output'], second['output'])
        self.assertEqual(request['request_id'], second['request_id'])
        self.assertEqual(1, self.calls.count('/api/generate'))

    def test_rejected_evidence_is_retained_without_another_model_call(self):
        self.output['proposals'][0]['evidence'] = 'Fabricated source passage'
        with patch.object(self.gateway, 'call', side_effect=self.call):
            for _ in range(2):
                with self.assertRaises(ValueError): self.gateway.generate(self.request)
        self.assertEqual(1, self.calls.count('/api/generate'))
        self.assertEqual(1, len(list(Path(self.directory.name).glob('*-raw.json'))))

    def test_refuses_cloud_backed_local_model(self):
        with patch.object(self.gateway, 'call', return_value={'remote_host': 'https://example.invalid'}) as call:
            with self.assertRaises(ValueError): self.gateway.generate(self.request)
        self.assertEqual(1, call.call_count)


if __name__ == '__main__':
    unittest.main()
