# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import json
from pathlib import Path
import sys
import unittest
import uuid
import threading
from http.server import HTTPServer
from urllib.request import Request, ProxyHandler, build_opener
from urllib.error import HTTPError
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import chart_updates as updates
import openrouter_agent as agent
import pipeline


class ChartUpdatesTest(unittest.TestCase):
    def setUp(self):
        self.source = 'History: suspected asthma.\nPlan: review in two weeks if symptoms persist.'
        self.notes = [('TEST', '2026-09-28', self.source)]
        self.config = dict(agent.DEFAULTS)
        self.request = {'contract_version': 1, 'request_id': str(uuid.uuid4()),
                        'workflow': 'chart-update-proposals', 'data_classification': 'clinical-document',
                        'instructions': updates.PROMPT, 'output_schema': updates.SCHEMA,
                        'sources': [{'id': 'document', 'title': 'Document', 'text': self.source}]}

    def test_preserves_uncertainty_condition_and_timing_exactly(self):
        output = {'proposals': [{'kind': 'history', 'evidence': self.source.split('\n')[0]},
                                {'kind': 'tickler', 'evidence': self.source.split('\n')[1]}]}
        requests = []
        def complete(payload):
            requests.append(payload)
            return output
        response = updates.run(self.config, self.request, self.notes, complete)
        self.assertEqual(output, response['output'])
        self.assertEqual(self.request['request_id'], response['request_id'])
        self.assertEqual({'sources': self.request['sources']}, json.loads(requests[0]['messages'][1]['content']))
        self.assertFalse(requests[0]['provider']['allow_fallbacks'])
        self.assertTrue(requests[0]['provider']['zdr'])

    def test_rejects_disclosure_before_network_call(self):
        for source in ('real patient data', self.source[:20], self.source + '\nextra metadata'):
            with self.subTest(source=source):
                request = copy.deepcopy(self.request)
                request['sources'][0]['text'] = source
                with self.assertRaises(ValueError):
                    updates.run(self.config, request, self.notes, lambda _: self.fail('Unexpected network call'))

    def test_rejects_extra_chart_data_modified_contract_and_oversized_requests(self):
        for change in ({'chart': 'private chart'}, {'instructions': 'different'}, {'output_schema': {}},
                       {'workflow': 'apply'}, {'contract_version': True}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                updates.run(self.config, dict(self.request, **change), self.notes, lambda _: self.fail('Network call'))
        with self.assertRaises(ValueError):
            updates.run(dict(self.config, request_bytes=10), self.request, self.notes, lambda _: self.fail('Network call'))

    def test_rejects_fabricated_evidence_write_fields_and_unapproved_kinds(self):
        for row in ({'kind': 'history', 'evidence': 'Confirmed asthma.'},
                    {'kind': 'prescription', 'evidence': self.source},
                    {'kind': 'tickler', 'evidence': self.source, 'due_date': '2026-10-12'},
                    {'kind': 'history', 'evidence': ''}):
            with self.subTest(row=row), self.assertRaises(ValueError):
                updates.validate_output({'proposals': [row]}, self.source)

    def test_allows_no_proposals_and_rejects_duplicate_passages(self):
        updates.validate_output({'proposals': []}, self.source)
        row = {'kind': 'history', 'evidence': self.source}
        with self.assertRaises(ValueError):
            updates.validate_output({'proposals': [row, dict(row, kind='tickler')]}, self.source)

    def test_bounds_count_and_java_utf16_length(self):
        text = '\U0001f600' * 1001
        with self.assertRaises(ValueError):
            updates.validate_output({'proposals': [{'kind': 'history', 'evidence': text}]}, text)
        with self.assertRaises(ValueError):
            updates.validate_output({'proposals': [{'kind': 'history', 'evidence': self.source}] * 21}, self.source)

    def test_gateway_route_rejects_unknown_source_before_transport(self):
        gateway = agent.Gateway(self.config, transport=lambda *_: self.fail('Network call'))
        source = gateway.allowed.notes[0][2]
        request = copy.deepcopy(self.request)
        request['sources'][0]['text'] = source
        output = {'proposals': []}
        server = HTTPServer(('127.0.0.1', 0), agent.handler_for(gateway))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f'http://127.0.0.1:{server.server_port}{updates.PATH}'
            opener = build_opener(ProxyHandler({}))
            with patch.object(gateway, 'complete', return_value=output) as complete:
                with opener.open(Request(url, json.dumps(request).encode(),
                                         {'Content-Type': 'application/json'}), timeout=5) as response:
                    self.assertEqual(output, json.load(response)['output'])
                request['sources'][0]['text'] = 'Unapproved clinical text'
                with self.assertRaises(HTTPError) as error:
                    opener.open(Request(url, json.dumps(request).encode()), timeout=5)
                self.assertEqual(400, error.exception.code)
                self.assertEqual(1, complete.call_count)
        finally:
            server.shutdown()
            thread.join()
            server.server_close()

    def test_gateway_handles_output_limit_without_accepting_partial_proposals(self):
        gateway = agent.Gateway(self.config, transport=lambda *_: self.fail('Network call'))
        request = copy.deepcopy(self.request)
        request['sources'][0]['text'] = gateway.allowed.notes[0][2]
        with patch.object(gateway, 'complete', side_effect=pipeline.OutputLimitError()):
            with self.assertRaises(agent.UpstreamError):
                gateway.run_chart_updates(request)


if __name__ == '__main__':
    unittest.main()
