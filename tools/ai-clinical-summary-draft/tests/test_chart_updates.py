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
            return ({'proposals': [{'kind': 'history', 'start_id': 1, 'end_id': 1},
                                   {'kind': 'tickler', 'start_id': 2, 'end_id': 2}]}
                    if len(requests) == 1 else {'decisions': [{'id': i, 'keep': True, 'reason': 'Eligible'} for i in ['1', '2']]})
        response = updates.run(self.config, self.request, self.notes, complete)
        self.assertEqual(output, response['output'])
        self.assertEqual(self.request['request_id'], response['request_id'])
        self.assertEqual({'segments': {'1': self.source.splitlines(keepends=True)[0],
                                   '2': self.source.splitlines(keepends=True)[1]}},
                         json.loads(requests[0]['messages'][1]['content']))
        self.assertEqual(2, len(requests))
        self.assertFalse(requests[0]['provider']['allow_fallbacks'])
        self.assertTrue(requests[0]['provider']['zdr'])

    def test_copies_multiline_evidence_with_source_typos_and_crlf(self):
        source = 'History\r\n- Suspected ashtma\r\n  if investigations confirm.\r\n\r\nOther context'
        raw = {'proposals': [{'kind': 'history', 'start_id': 1, 'end_id': 3}]}
        result = updates.resolve_ranges(raw, updates.source_segments(source), source)
        self.assertEqual(source.split('\r\n\r\n')[0], result['proposals'][0]['evidence'])

    def test_followup_can_be_selected_without_neighbouring_prescription(self):
        source = 'Prescribe drug A. Advised to follow up with GP in 7 days.\r\n'
        segments = updates.source_segments(source)
        self.assertEqual(source, ''.join(segments.values()))
        output = updates.resolve_ranges({'proposals': [
            {'kind': 'tickler', 'start_id': 2, 'end_id': 2}]}, segments, source)
        self.assertEqual('Advised to follow up with GP in 7 days.', output['proposals'][0]['evidence'])

    def test_segmentation_preserves_every_complete_synthetic_note(self):
        for _, _, source in agent.committed_notes()[0]:
            self.assertEqual(source, ''.join(updates.source_segments(source).values()))

    def test_rejects_unknown_reversed_noninteger_and_extra_reference_fields(self):
        for change in ({'start_id': 0}, {'end_id': 99}, {'start_id': 2, 'end_id': 1},
                       {'start_id': True}, {'end_id': '2'}, {'evidence': 'invented'}):
            row = dict(kind='history', start_id=1, end_id=1)
            row.update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                updates.resolve_ranges({'proposals': [row]}, updates.source_segments(self.source), self.source)

    def test_family_section_cannot_become_patient_history_without_heading(self):
        source = 'Family History\n- HTN\n\nPast Medical History\n- Asthma'
        raw = {'proposals': [{'kind': 'history', 'start_id': 2, 'end_id': 2},
                             {'kind': 'history', 'start_id': 4, 'end_id': 5}]}
        result = updates.resolve_ranges(raw, updates.source_segments(source), source)
        self.assertEqual([{'kind': 'history', 'evidence': 'Past Medical History\n- Asthma'}],
                         result['proposals'])

    def test_selected_history_bullets_keep_the_adjacent_historical_heading(self):
        source = 'Past Medical History\n- HTN\n- Mild osteoarthritis'
        raw = {'proposals': [{'kind': 'history', 'start_id': 2, 'end_id': 3}]}
        output = updates.resolve_ranges(raw, updates.source_segments(source), source)
        self.assertEqual(source, output['proposals'][0]['evidence'])

    def test_repeated_history_is_deduplicated_without_rewriting_first_quote(self):
        source = 'History: suspected asthma.\n\nHistory:  suspected asthma.'
        raw = {'proposals': [{'kind': 'history', 'start_id': 1, 'end_id': 1},
                             {'kind': 'history', 'start_id': 3, 'end_id': 3}]}
        result = updates.resolve_ranges(raw, updates.source_segments(source), source)
        self.assertEqual([{'kind': 'history', 'evidence': 'History: suspected asthma.'}],
                         result['proposals'])

    def test_review_can_remove_stale_candidate_and_sees_later_source(self):
        source = self.source + '\nLater: follow-up completed.'
        request = copy.deepcopy(self.request)
        request['sources'][0]['text'] = source
        replies = [{'proposals': [{'kind': 'tickler', 'start_id': 2, 'end_id': 2}]}, {'decisions': [{'id': '1', 'keep': False, 'reason': 'Completed later'}]}]
        seen = []
        def complete(payload):
            seen.append(payload)
            return replies.pop(0)
        result = updates.run(self.config, request, [('TEST', '', source)], complete)
        self.assertEqual([], result['output']['proposals'])
        self.assertIn('Later: follow-up completed.', seen[1]['messages'][1]['content'])

    def test_review_must_be_valid_before_any_proposals_are_released(self):
        valid = {'id': '1', 'keep': True, 'reason': 'Eligible'}
        for review in ({'decisions': [dict(valid, id='unknown')]}, {'decisions': [valid, valid]},
                       {'decisions': [dict(valid, keep='yes')]}, {'decisions': []},
                       {'decisions': [valid], 'extra': True}):
            replies = [{'proposals': [{'kind': 'history', 'start_id': 1, 'end_id': 1}]}, review]
            with self.subTest(review=review), self.assertRaises(ValueError):
                updates.run(self.config, self.request, self.notes, lambda _: replies.pop(0))

    def test_empty_candidates_need_no_review_call(self):
        with patch('chart_updates.distill.payload', wraps=updates.distill.payload) as payload:
            result = updates.run(self.config, self.request, self.notes, lambda _: {'proposals': []})
            self.assertEqual([], result['output']['proposals'])
            self.assertEqual(1, payload.call_count)

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
