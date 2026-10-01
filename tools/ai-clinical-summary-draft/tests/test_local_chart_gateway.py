# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
import uuid
import threading
from http.server import HTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, ProxyHandler, build_opener
from unittest.mock import MagicMock, patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'browser'))
import local_chart_gateway as local


class LocalChartGatewayTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.gateway = local.Gateway(self.directory.name)
        self.source = 'History: asthma.\nPlan: GP review in four weeks.'
        self.gateway.notes = [('TEST', '2026-09-28', self.source)]
        self.request = {'contract_version': 1, 'request_id': str(uuid.uuid4()),
                        'workflow': 'chart-update-proposals', 'data_classification': 'clinical-document',
                        'instructions': local.chart_updates.PROMPT, 'output_schema': local.chart_updates.SCHEMA,
                        'sources': [{'id': 'document', 'title': 'Document', 'text': self.source}]}
        self.output = {'proposals': [{'kind': 'history', 'start_id': 1, 'end_id': 1}]}
        self.digest = 'a'*64
        self.version = 'test'
        self.calls = []

    def call(self, route, data=None):
        self.calls.append(route)
        return {'/api/show': {}, '/api/tags': {'models': [{'name': local.MODEL, 'digest': self.digest}]},
                '/api/version': {'version': self.version}, '/api/generate': {'model': local.MODEL, 'done': True,
                'done_reason': 'stop', 'response': json.dumps(
                    {'decisions': [{'id': '1', 'keep': True, 'reason': 'Eligible'}]}
                    if data and 'decisions' in data.get('format', {}).get('properties', {}) else self.output)}}[route]

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
        self.assertEqual(2, self.calls.count('/api/generate'))

    def test_rejected_evidence_is_retained_without_another_model_call(self):
        self.output['proposals'][0]['start_id'] = 999
        with patch.object(self.gateway, 'call', side_effect=self.call):
            for _ in range(2):
                with self.assertRaises(ValueError): self.gateway.generate(self.request)
        self.assertEqual(1, self.calls.count('/api/generate'))
        self.assertEqual(1, len(list(Path(self.directory.name).glob('*-raw.json'))))

    def test_cache_misses_when_model_identity_changes(self):
        with patch.object(self.gateway, 'call', side_effect=self.call):
            self.gateway.generate(self.request)
            self.digest = 'b'*64
            self.gateway.generate(self.request)
            self.version = 'changed'
            self.gateway.generate(self.request)
        self.assertEqual(6, self.calls.count('/api/generate'))

    def test_model_unavailability_returns_502_without_leaking_transport_details(self):
        server = HTTPServer(('127.0.0.1', 0), local.agent.handler_for(self.gateway))
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            url = f'http://127.0.0.1:{server.server_port}' + local.chart_updates.PATH
            opener = build_opener(ProxyHandler({}))
            def unavailable_tags(route, data=None):
                return {'models': []} if route == '/api/tags' else self.call(route, data)
            for failure in ('connection', 'missing_model'):
                stub = (patch.object(self.gateway.opener, 'open', side_effect=URLError('private model endpoint details'))
                        if failure == 'connection' else patch.object(self.gateway, 'call', side_effect=unavailable_tags))
                with self.subTest(failure=failure), stub:
                    with self.assertRaises(HTTPError) as caught:
                        opener.open(Request(url, data=json.dumps(self.request).encode(),
                                            headers={'Content-Type': 'application/json'}), timeout=5)
                    with caught.exception as response:
                        self.assertEqual(502, response.code)
                        self.assertEqual({'error': 'Model service unavailable; see local gateway status'},
                                         json.loads(response.read()))
            self.assertEqual([], list(Path(self.directory.name).glob('*.json')))
        finally:
            server.shutdown()
            server.server_close()
            worker.join(timeout=5)

    def test_malformed_envelopes_and_incomplete_completions_return_upstream_errors(self):
        for envelope in ([], None, 3, 'text'):
            response = MagicMock()
            response.__enter__.return_value.read.return_value = json.dumps(envelope).encode()
            with self.subTest(envelope=envelope), patch.object(self.gateway.opener, 'open', return_value=response):
                with self.assertRaises(local.agent.UpstreamError):
                    self.gateway.call('/api/show')
        for envelope in ({'models': None}, {'models': [None]}):
            def malformed(route, data=None):
                return envelope if route == '/api/tags' else self.call(route, data)
            with self.subTest(envelope=envelope), patch.object(self.gateway, 'call', side_effect=malformed):
                with self.assertRaises(local.agent.UpstreamError):
                    self.gateway.generate(self.request)
        def incomplete(route, data=None):
            result = self.call(route, data)
            if route == '/api/generate':
                result['done_reason'] = 'length'
            return result
        with patch.object(self.gateway, 'call', side_effect=incomplete):
            with self.assertRaises(local.agent.UpstreamError):
                self.gateway.generate(self.request)

    def test_model_calls_share_one_request_deadline(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = b'{}'
        self.gateway.deadline = 200
        with patch.object(local.time, 'monotonic', side_effect=[100, 175, 201]), patch.object(
                self.gateway.opener, 'open', return_value=response) as opened:
            self.gateway.call('/api/show')
            self.gateway.call('/api/version')
            with self.assertRaises(local.agent.UpstreamError):
                self.gateway.call('/api/tags')
        self.assertEqual([100, 25], [call.kwargs['timeout'] for call in opened.call_args_list])

    def test_refuses_cloud_backed_local_model(self):
        with patch.object(self.gateway, 'call', return_value={'remote_host': 'https://example.invalid'}) as call:
            with self.assertRaises(ValueError): self.gateway.generate(self.request)
        self.assertEqual(1, call.call_count)


if __name__ == '__main__':
    unittest.main()
