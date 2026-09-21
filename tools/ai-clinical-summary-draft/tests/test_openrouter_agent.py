# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
import io
import json
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import ProxyHandler, Request, build_opener
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import host_checks
import openrouter_agent as agent
import pipeline
import switch_summary_agent as switch
from validate_artifact import COMMON_WORDS, words


def completion(config, _endpoint, payload):
    sources = json.loads(payload['messages'][1]['content'])['sources']
    claims = []
    for index, source in enumerate(sources):
        word = sorted(words(source['text']) - COMMON_WORDS)[0]
        claims.append({'id': 'c' + str(index), 'text': word + f' is recorded in entry {index}.',
                       'source_ids': [source['id']]})
    section_id = payload['response_format']['json_schema']['schema']['properties']['sections']['items']['properties']['id']['enum'][0]
    output = {'claims': claims,
              'sections': [{'id': section_id, 'title': agent.SECTION_TITLES[section_id],
                            'claim_ids': [claim['id'] for claim in claims]}],
              'coverage': [{'source_id': source['id'], 'status': 'cited',
                            'reason': 'Reviewed clinical material in ' + source['id']} for source in sources]}
    return {'model': config['model'], 'choices': [{'finish_reason': 'stop',
                                                 'message': {'content': json.dumps(output)}}]}


class OpenRouterTest(unittest.TestCase):
    def setUp(self):
        self.config = dict(agent.DEFAULTS, api_key='test-key-never-a-real-secret', section_passes=False)
        self.calls = []
        self.now = 0
        def transport(config, endpoint, payload):
            self.calls.append(copy.deepcopy(payload))
            return completion(config, endpoint, payload)
        self.gateway = agent.Gateway(self.config, transport=transport, clock=lambda: self.now)
        fixture, date, body = min(self.gateway.allowed.notes, key=lambda note: len(note[2]))
        self.request = {'contract_version': 1, 'request_id': str(uuid4()), 'workflow': 'patient-overview',
                        'data_classification': 'verified-synthetic', 'instructions': self.gateway.prompt,
                        'output_schema': self.gateway.schema,
                        'sources': [{'id': 'note-611', 'patient_id': 'demographic-3001',
                                     'title': 'Signed encounter note (note-611)', 'date': date, 'text': body}]}

    def test_defaults_are_the_configuration_the_quality_gate_validated(self):
        # QUALITY.md: the only configuration that passed the gate on all three fixtures.
        self.assertEqual({'model': 'qwen/qwen3.5-27b', 'provider': 'siliconflow', 'temperature': 0.0,
                          'reasoning_tokens': 0, 'request_bytes': 50000, 'section_passes': False},
                         {key: agent.DEFAULTS[key] for key in ('model', 'provider', 'temperature',
                                                               'reasoning_tokens', 'request_bytes',
                                                               'section_passes')})
        # Whole-record passes measured 176-209 seconds on 2026-09-18 and 204-288 on 2026-09-21,
        # so neither 180 nor 300 leaves room; the ceiling stays inside the 540-second gateway budget.
        self.assertEqual(420, agent.DEFAULTS['timeout_seconds'])
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'config.json'
            agent.private_write(path, json.dumps(dict(self.config, timeout_seconds=480)))
            self.assertEqual(480, agent.read_config(path)['timeout_seconds'])
            agent.private_write(path, json.dumps(dict(self.config, timeout_seconds=481)))
            with self.assertRaises(ValueError):
                agent.read_config(path)

    def test_live_contract_cache_and_new_request_id(self):
        first = self.gateway.run(self.request)
        second = self.gateway.run(dict(self.request, request_id=str(uuid4())))
        self.assertEqual(first['output'], second['output'])
        self.assertNotEqual(first['request_id'], second['request_id'])
        self.assertEqual(1, len(self.calls))
        self.assertEqual(1, self.gateway.cache_hits)
        payload = self.calls[0]
        self.assertEqual({'enabled': False}, payload['reasoning'])
        self.assertEqual({'only': ['siliconflow'], 'allow_fallbacks': False, 'require_parameters': True,
                          'data_collection': 'deny', 'zdr': True}, payload['provider'])
        schema = payload['response_format']['json_schema']['schema']
        self.assertEqual(1, schema['properties']['coverage']['maxItems'])
        self.assertNotIn('maxItems', schema['properties']['claims'])
        self.assertNotIn(self.config['api_key'], json.dumps(payload))
        self.assertNotIn(self.request['request_id'], json.dumps(payload))
        first['output']['claims'].clear()
        self.assertTrue(self.gateway.run(self.request)['output']['claims'])

    def test_model_reviews_only_uncited_sources_and_the_gateway_completes_coverage(self):
        # Notes without an observation set, so the host has nothing to restore and cite on its own.
        notes = [note for note in self.gateway.allowed.notes
                 if note[0] == 'NHSSYN003' and not host_checks.observation_sets(note[2])][:2]
        sources = [dict(self.request['sources'][0], id=f'note-{i + 1}', patient_id='demographic-3003',
                        title=f'Signed encounter note (note-{i + 1})', date=date, text=body)
                   for i, (_fixture, date, body) in enumerate(notes)]

        def respond(uncited_review):
            def transport(config, endpoint, payload):
                result = completion(config, endpoint, payload)
                output = json.loads(result['choices'][0]['message']['content'])
                output['claims'] = output['claims'][:1]
                output['sections'][0]['claim_ids'] = ['c0']
                output['coverage'] = uncited_review
                result['choices'][0]['message']['content'] = json.dumps(output)
                return result
            return agent.Gateway(dict(self.config, cache_seconds=0), transport=transport)

        reviewed = respond([{'source_id': 'note-2', 'status': 'reviewed_not_cited', 'reason': 'Repeats note-1.'}])
        coverage = reviewed.run(dict(self.request, sources=sources))['output']['coverage']
        self.assertEqual({'note-1': 'cited', 'note-2': 'reviewed_not_cited'},
                         {entry['source_id']: entry['status'] for entry in coverage})
        self.assertIn('recorded by the host', coverage[[e['source_id'] for e in coverage].index('note-1')]['reason'])
        self.assertIn('Repeats note-1.', coverage[[e['source_id'] for e in coverage].index('note-2')]['reason'])
        # A note the model neither cited nor explained is recorded as such, not a reason to fail the draft.
        silent = respond([]).run(dict(self.request, request_id=str(uuid4()), sources=sources))['output']['coverage']
        self.assertEqual({'note-1': 'cited', 'note-2': 'reviewed_not_cited'},
                         {entry['source_id']: entry['status'] for entry in silent})
        self.assertIn('the model gave no reason', silent[[e['source_id'] for e in silent].index('note-2')]['reason'])
        # The usual case: every source is cited, so the model returns no reviews at all.
        all_cited = respond([]).run(dict(self.request, request_id=str(uuid4()), sources=sources[:1]))
        self.assertEqual(['cited'], [entry['status'] for entry in all_cited['output']['coverage']])

    def test_a_draft_that_drops_an_observation_set_gets_it_back_from_the_host(self):
        fixture, date, body = next(note for note in self.gateway.allowed.notes
                                   if note[0] == 'NHSSYN002' and 'HR 2\nBP 124/78\nRR 1' in note[2])
        sources = [dict(self.request['sources'][0], id='note-9', patient_id='demographic-3002',
                        title='Signed encounter note (note-9)', date=date, text=body)]
        output = self.gateway.run(dict(self.request, sources=sources))['output']
        restored = [claim for claim in output['claims'] if claim['id'].startswith('host-obs-')]
        self.assertEqual(1, len(restored))
        self.assertIn('HR 2; BP 124/78; RR 1; Temp 36.8; SpO2 98', restored[0]['text'])
        self.assertIn(restored[0]['id'], next(section['claim_ids'] for section in output['sections']
                                              if section['id'] == 'results_observations'))
        self.gateway.validate_output(sources, output)

    def test_cache_expires_and_can_be_disabled(self):
        self.gateway.run(self.request)
        self.now = 900
        self.gateway.run(self.request)
        self.assertEqual(2, len(self.calls))
        uncached = agent.Gateway(dict(self.config, cache_seconds=0), transport=self.gateway.transport)
        uncached.run(self.request)
        uncached.run(self.request)
        self.assertEqual(4, len(self.calls))
        self.assertFalse(uncached.cache)

    def test_temperature_is_forwarded_and_invalid_settings_are_rejected(self):
        gateway = agent.Gateway(dict(self.config, temperature=0.2), transport=self.gateway.transport)
        gateway.run(self.request)
        self.assertEqual(0.2, self.calls[0]['temperature'])
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'config.json'
            legacy = {k: v for k, v in self.config.items() if k not in ('temperature', 'request_bytes', 'reasoning_tokens')}
            agent.private_write(path, json.dumps(legacy))
            self.assertEqual(self.config, agent.read_config(path))
            for key, value in [('temperature', True), ('temperature', -0.1), ('temperature', 2.1),
                               ('temperature', '0.2'), ('request_bytes', 9999), ('request_bytes', 50001),
                               ('request_bytes', 20000.5), ('reasoning_tokens', -1), ('reasoning_tokens', True),
                               ('reasoning_tokens', 9000)]:
                agent.private_write(path, json.dumps(dict(self.config, **{key: value})))
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    agent.read_config(path)

    def test_reasoning_is_explicit_and_its_text_is_excluded(self):
        gateway = agent.Gateway(dict(self.config, reasoning_tokens=4096), transport=self.gateway.transport)
        gateway.run(self.request)
        self.assertEqual({'enabled': True, 'max_tokens': 4096, 'exclude': True}, self.calls[0]['reasoning'])

    def test_larger_context_keeps_complete_fixture_together(self):
        self.gateway.config['request_bytes'] = 50000
        sources = [dict(self.request['sources'][0], id=f'note-{i + 1}',
                        title=f'Signed encounter note (note-{i + 1})', date=date, text=body)
                   for i, (fixture, date, body) in enumerate(self.gateway.allowed.notes) if fixture == 'NHSSYN001']
        self.assertGreater(len(pipeline.plan(sources, self.gateway.prompt, self.gateway.schema)), 1)
        self.gateway.run(dict(self.request, sources=sources))
        self.assertEqual(1, len(self.calls))
        self.assertEqual(sources, json.loads(self.calls[0]['messages'][1]['content'])['sources'])

    def test_coverage_status_is_derived_without_changing_claims_or_citations(self):
        baseline = self.gateway.run(self.request)['output']
        changed = copy.deepcopy(baseline)
        changed['coverage'][0]['status'] = 'reviewed_not_cited'
        normalized = agent.normalize_coverage_status(changed, self.request['sources'])
        self.assertEqual(baseline['claims'], normalized['claims'])
        self.assertEqual('cited', normalized['coverage'][0]['status'])
        changed['claims'] = []
        changed['sections'] = []
        changed['coverage'][0]['status'] = 'cited'
        normalized = agent.normalize_coverage_status(changed, self.request['sources'])
        self.assertEqual('reviewed_not_cited', normalized['coverage'][0]['status'])
        for entries in ([], changed['coverage'] * 2,
                        [dict(changed['coverage'][0], source_id='unknown')],
                        [dict(changed['coverage'][0], status='invented')]):
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                agent.normalize_coverage_status(dict(changed, coverage=entries), self.request['sources'])

    def test_section_passes_each_see_all_evidence_and_cache_separately(self):
        self.gateway.config['section_passes'] = True
        output = self.gateway.run(self.request)['output']
        self.assertEqual(5, len(self.calls))
        # Passes are scheduled longest-first over a thread pool, so identify each by its
        # constrained section rather than by arrival order.
        by_section = {}
        for payload in self.calls:
            schema = payload['response_format']['json_schema']['schema']['properties']['sections']
            self.assertEqual(1, schema['maxItems'])
            section_ids = schema['items']['properties']['id']['enum']
            self.assertEqual(1, len(section_ids))
            by_section[section_ids[0]] = payload
        self.assertEqual(set(agent.SECTION_SCOPES), set(by_section))
        for payload in by_section.values():
            self.assertEqual(self.request['sources'], json.loads(payload['messages'][1]['content'])['sources'])
        self.gateway.run(self.request)
        self.assertEqual(5, self.gateway.cache_hits)
        self.assertEqual(5, len(self.calls))
        self.assertEqual(1, len(output['coverage']))

    def test_modified_sources_and_settings_are_separate_cache_keys(self):
        self.gateway.run(self.request)
        changed = copy.deepcopy(self.request)
        changed['sources'][0]['text'] = changed['sources'][0]['text'].strip()
        if changed['sources'][0]['text'] == self.request['sources'][0]['text']:
            changed['sources'][0]['text'] = changed['sources'][0]['text'][:-1]
        self.gateway.run(changed)
        self.assertEqual(2, len(self.calls))
        other = agent.Gateway(dict(self.config, model='example/test-model'), transport=self.gateway.transport)
        other.run(self.request)
        self.assertEqual(3, len(self.calls))

    def test_rejects_nonfixture_data_before_network_or_cache(self):
        self.gateway.run(self.request)
        for field, value in (('text', 'Private real patient information'), ('date', '2020-01-01'),
                             ('title', 'A private patient name'), ('patient_id', 'actual-person-name')):
            changed = copy.deepcopy(self.request)
            changed['sources'][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.gateway.run(changed)
        self.assertEqual(1, len(self.calls))
        self.assertEqual(0, self.gateway.cache_hits)

    def test_caller_cannot_smuggle_data_in_instructions_or_schema(self):
        for key, value in (('instructions', 'private chart text'), ('output_schema', {'private': 'data'}),
                           ('data_classification', 'real'), ('contract_version', True), ('extra', 'field')):
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.gateway.run(dict(self.request, **{key: value}))
        self.assertFalse(self.calls)

    def test_mixed_synthetic_fixtures_are_rejected(self):
        own_fixture = next(f for f, d, body in self.gateway.allowed.notes
                           if d == self.request['sources'][0]['date'] and body == self.request['sources'][0]['text'])
        _fixture, date, body = next(note for note in self.gateway.allowed.notes if note[0] != own_fixture)
        other = {'id': 'note-999', 'patient_id': 'demographic-3001',
                 'title': 'Signed encounter note (note-999)', 'date': date, 'text': body}
        with self.assertRaises(ValueError):
            self.gateway.run(dict(self.request, sources=self.request['sources'] + [other]))
        self.assertFalse(self.calls)

    def test_incomplete_refused_and_wrong_model_outputs_are_not_cached(self):
        payload = self.gateway.run(self.request)
        output = json.dumps(payload['output'])
        self.gateway.cache.clear()
        self.gateway.cache_bytes = 0
        for result in ({'model': 'wrong', 'choices': []}, {'error': {'message': 'private'}},
                       {'model': self.config['model'], 'choices': [{'finish_reason': 'content_filter'}]},
                       {'model': self.config['model'], 'choices': [{'finish_reason': 'stop',
                        'message': {'content': output, 'refusal': 'Refused'}}]},
                       {'model': self.config['model'], 'choices': [{'finish_reason': 'stop',
                        'message': {'content': output, 'tool_calls': [{'id': 'bad'}]}}]}):
            self.gateway.transport = lambda *_args: result
            with self.subTest(result=result), self.assertRaises(ValueError):
                self.gateway.run(self.request)
            self.assertFalse(self.gateway.cache)

    def test_invalid_citations_coverage_sections_and_json_are_not_cached(self):
        baseline = self.gateway.run(self.request)['output']
        self.gateway.cache.clear()
        self.gateway.cache_bytes = 0
        variants = []
        changed = copy.deepcopy(baseline)
        changed['claims'][0]['source_ids'] = ['unknown']
        variants.append(json.dumps(changed))
        changed = copy.deepcopy(baseline)
        # An empty review list is valid when every source is cited; a review of an unknown source is not.
        changed['coverage'] = [{'source_id': 'unknown', 'status': 'excluded', 'reason': 'Not supplied.'}]
        variants.append(json.dumps(changed))
        changed = copy.deepcopy(baseline)
        changed['sections'][0]['claim_ids'] = ['unknown']
        variants.append(json.dumps(changed))
        variants += ['{"claims": [], "claims": []}', json.dumps(baseline) + ' trailing text']
        for raw in variants:
            self.gateway.transport = lambda *_args: {'model': self.config['model'], 'choices': [
                {'finish_reason': 'stop', 'message': {'content': raw}}]}
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                self.gateway.run(self.request)
            self.assertFalse(self.gateway.cache)

    def test_token_limit_splits_source_without_dropping_coverage(self):
        _fixture, date, body = next(note for note in self.gateway.allowed.notes if 1500 < len(note[2]) < 3000)
        self.request['sources'][0].update(date=date, text=body)
        calls = []
        def limited(config, endpoint, payload):
            calls.append(payload)
            if len(calls) == 1:
                return {'model': config['model'], 'choices': [{'finish_reason': 'length'}]}
            return completion(config, endpoint, payload)
        self.gateway.transport = limited
        response = self.gateway.run(self.request)
        self.assertGreater(len(calls), 1)
        self.assertEqual(['note-611'], [c['source_id'] for c in response['output']['coverage']])
        self.gateway.validate_output(self.request['sources'], response['output'])

    def test_bounded_cache_evicts_oldest(self):
        for number in range(1, 131):
            source = dict(self.request['sources'][0], id=f'note-{number}',
                          title=f'Signed encounter note (note-{number})')
            self.gateway.run(dict(self.request, sources=[source]))
        self.assertEqual(128, len(self.gateway.cache))
        self.assertEqual(sum(len(raw) for _expiry, raw in self.gateway.cache.values()), self.gateway.cache_bytes)

    def test_loopback_http_contract_and_health(self):
        server = HTTPServer(('127.0.0.1', 0), agent.handler_for(self.gateway))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        opener = build_opener(ProxyHandler({}))
        url = f'http://127.0.0.1:{server.server_port}'
        try:
            with opener.open(url + '/health') as response:
                health = json.load(response)
                self.assertEqual('carlos-openrouter-synthetic', health['service'])
                self.assertEqual(self.config['provider'], health['provider'])
                self.assertNotIn('api_key', health)
            request = Request(url + agent.PATH, data=json.dumps(self.request).encode(),
                              headers={'Content-Type': 'application/json'})
            with opener.open(request) as response:
                self.assertEqual('no-store', response.headers['Cache-Control'])
                self.assertEqual(self.request['request_id'], json.load(response)['request_id'])
            with self.assertRaises(HTTPError) as error:
                opener.open(Request(url + agent.PATH, data=b'{}'))
            self.assertEqual(400, error.exception.code)
            error.exception.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_private_config_permissions_and_validation(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'private/config.json'
            agent.private_write(path, json.dumps(self.config))
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            self.assertEqual(self.config, agent.read_config(path))
            path.chmod(0o644)
            with self.assertRaises(ValueError):
                agent.read_config(path)
            agent.private_write(path, json.dumps(dict(self.config, model='auto')))
            with self.assertRaises(ValueError):
                agent.read_config(path)

    def test_secret_and_upstream_body_not_in_errors(self):
        error = HTTPError(agent.API, 401, 'secret-private-body', {}, io.BytesIO(b'sensitive contents'))
        with patch.object(agent, 'build_opener') as opener:
            opener.return_value.open.side_effect = error
            with self.assertRaises(agent.UpstreamError) as raised:
                agent.api_request(self.config, 'key')
            self.assertEqual('API key rejected (HTTP 401)', str(raised.exception))
            request = opener.return_value.open.call_args.args[0]
            self.assertEqual(agent.API + 'key', request.full_url)
            self.assertEqual('Bearer ' + self.config['api_key'], request.get_header('Authorization'))
            self.assertIsNone(request.data)
        with self.assertRaises(ValueError):
            agent.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://example.com')

    def test_nonfinite_and_duplicate_json_rejected(self):
        for raw in ('{"a": NaN}', '{"a": 1, "a": 2}'):
            with self.assertRaises(ValueError):
                agent.loads(raw)

    def test_provider_schema_omits_unsupported_keyword_but_preserves_host_schema(self):
        original = copy.deepcopy(self.gateway.schema)
        self.gateway.run(self.request)
        schema = self.calls[0]['response_format']['json_schema']['schema']
        self.assertNotIn('uniqueItems', json.dumps(schema))
        self.assertIn('uniqueItems', json.dumps(original))
        self.assertEqual(original, self.gateway.schema)
        self.assertFalse(schema['additionalProperties'])
        self.assertEqual(['note-611'], schema['properties']['coverage']['items']['properties']['source_id']['enum'])

    def test_duplicate_references_still_rejected_before_caching(self):
        baseline = self.gateway.run(self.request)['output']
        self.gateway.cache.clear()
        self.gateway.cache_bytes = 0
        for collection, field in (('claims', 'source_ids'), ('sections', 'claim_ids')):
            output = copy.deepcopy(baseline)
            output[collection][0][field] *= 2
            self.gateway.transport = lambda *_args: {'model': self.config['model'], 'choices': [
                {'finish_reason': 'stop', 'message': {'content': json.dumps(output)}}]}
            with self.subTest(collection=collection), self.assertRaises(ValueError):
                self.gateway.run(self.request)
            self.assertFalse(self.gateway.cache)

    def test_overlapping_headings_place_each_unchanged_claim_once(self):
        baseline = self.gateway.run(self.request)['output']
        self.gateway.cache.clear()
        self.gateway.cache_bytes = 0
        output = copy.deepcopy(baseline)
        # The transport stands in for raw model output; the host labels each review with the
        # source ID on every pass, so replay the baseline without that host-added prefix.
        for entry in output['coverage']:
            prefix = entry['source_id'] + ': '
            self.assertTrue(entry['reason'].startswith(prefix))
            entry['reason'] = entry['reason'][len(prefix):]
        claim_ids = output['sections'][0]['claim_ids'][:]
        output['sections'] += [
            {'id': 'active_problems', 'title': 'Active problems', 'claim_ids': claim_ids},
            {'id': 'plan_follow_up', 'title': 'Plan and follow-up', 'claim_ids': claim_ids}]
        original = copy.deepcopy(output)
        self.gateway.transport = lambda *_args: {'model': self.config['model'], 'choices': [
            {'finish_reason': 'stop', 'message': {'content': json.dumps(output)}}]}
        result = self.gateway.run(self.request)['output']
        self.assertEqual(baseline['claims'], result['claims'])
        self.assertEqual(baseline['coverage'], result['coverage'])
        self.assertEqual([{'id': 'active_problems', 'title': 'Active problems', 'claim_ids': claim_ids}],
                         result['sections'])
        self.assertEqual(original, output)
        self.assertTrue(self.gateway.cache)

    def test_unassigned_claims_are_preserved_in_the_generic_overview(self):
        output = self.gateway.run(self.request)['output']
        output['claims'].append({'id': 'orphan', 'text': output['claims'][0]['text'] + ' Also noted.',
                                 'source_ids': output['claims'][0]['source_ids'][:]})
        for sections in (output['sections'], [], [{'id': 'active_problems', 'title': 'Active problems',
                                                   'claim_ids': [output['claims'][0]['id']]}]):
            original = dict(output, sections=sections)
            normalized = agent.normalize_section_placement(original, self.request['sources'])
            self.gateway.validate_output(self.request['sources'], normalized)
            self.assertEqual(original['claims'], normalized['claims'])
            self.assertEqual(original['coverage'], normalized['coverage'])
            overview = next(s for s in normalized['sections'] if s['id'] == 'clinical_overview')
            self.assertIn('orphan', overview['claim_ids'])

    def test_http_200_schema_error_has_safe_actionable_diagnostic(self):
        value = {'error': {'message': 'Upstream error: Grammar error: Unimplemented keys: '
                                     '["uniqueItems"] private-key-or-source', 'code': 502}}
        with patch.object(agent, 'build_opener') as opener:
            response = opener.return_value.open.return_value.__enter__.return_value
            response.read1.side_effect = [json.dumps(value).encode(), b'']
            response.isclosed.return_value = False
            with self.assertRaises(agent.UpstreamError) as raised:
                agent.api_request(self.config, 'key')
            self.assertEqual('Provider rejected the structured-output schema; no draft accepted', str(raised.exception))
        for malformed in (None, 'secret', {'code': []}, {'message': {}, 'code': True}):
            self.assertEqual('OpenRouter returned an API error; no draft accepted', str(agent.api_error(malformed)))
        self.assertEqual('Rate limited; wait before retrying (API 429)', str(agent.api_error({'code': 429})))

    def test_keepalive_whitespace_cannot_extend_response_deadline(self):
        class DrippingHandler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass

            def do_GET(self):
                self.send_response(200)
                self.end_headers()
                try:
                    for _ in range(50):
                        self.wfile.write(b' ')
                        self.wfile.flush()
                        time.sleep(0.03)
                except (BrokenPipeError, ConnectionResetError):
                    pass
        server = HTTPServer(('127.0.0.1', 0), DrippingHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with build_opener(ProxyHandler({})).open(f'http://127.0.0.1:{server.server_port}', timeout=2) as response:
                started = time.monotonic()
                with self.assertRaises(TimeoutError):
                    agent.read_response(response, started + 0.15)
                self.assertLess(time.monotonic() - started, 0.8)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_upstream_timeout_is_sanitized_without_retry(self):
        with patch.object(agent, 'build_opener') as opener:
            opener.return_value.open.side_effect = TimeoutError('private exception text')
            with self.assertRaises(agent.UpstreamError) as raised:
                agent.api_request(self.config, 'key')
            self.assertEqual('OpenRouter connection, timeout, or response-format failure', str(raised.exception))
            self.assertEqual(1, opener.return_value.open.call_count)

    def test_temporary_rate_limit_retries_then_caches_valid_result(self):
        calls = []
        def temporary(config, endpoint, payload):
            calls.append(payload)
            if len(calls) == 1:
                raise agent.RateLimitError('Rate limited')
            return completion(config, endpoint, payload)
        self.gateway.transport = temporary
        with patch.object(agent.time, 'sleep') as sleep:
            self.gateway.run(self.request)
            self.gateway.run(self.request)
            sleep.assert_called_once_with(2)
        self.assertEqual(2, len(calls))
        self.assertEqual(1, self.gateway.cache_hits)

    def test_rate_limit_retries_are_bounded_and_never_cached(self):
        for retry_after, expected_calls in ((None, 3), (121, 1)):
            with self.subTest(retry_after=retry_after), patch.object(agent.time, 'sleep') as sleep:
                with patch.object(self.gateway, 'transport', side_effect=agent.RateLimitError(
                        'Rate limited', retry_after)) as transport:
                    with self.assertRaises(agent.RateLimitError):
                        self.gateway.run(self.request)
                    self.assertEqual(expected_calls, transport.call_count)
                    self.assertEqual(expected_calls - 1, sleep.call_count)
            self.assertFalse(self.gateway.cache)

    def test_retry_after_header_is_retained_without_logging_upstream_body(self):
        error = HTTPError(agent.API, 429, 'private upstream text', {'Retry-After': '5'}, io.BytesIO(b'private'))
        with patch.object(agent, 'build_opener') as opener:
            opener.return_value.open.side_effect = error
            with self.assertRaises(agent.RateLimitError) as raised:
                agent.api_request(self.config, 'key')
        self.assertEqual(5, raised.exception.retry_after)
        self.assertEqual('Rate limited; wait before retrying (HTTP 429)', str(raised.exception))

    def test_retry_after_handles_dates_seconds_and_fractional_provider_values(self):
        now = datetime(2026, 9, 15, 12, 0, tzinfo=timezone.utc)
        for value, expected in ((None, None), ('60', 60), (' 1.25 ', 2),
                                ('Tue, 15 Sep 2026 12:01:00 GMT', 60),
                                ('Tue, 15 Sep 2026 11:59:00 GMT', 0),
                                ('99999999999999999', 121), ('not a retry date', None)):
            with self.subTest(value=value):
                self.assertEqual(expected, agent.retry_after_seconds(value, now))

    def test_longer_provider_wait_is_honored_within_request_deadline(self):
        calls = []
        def limited(config, endpoint, payload):
            calls.append(payload)
            if len(calls) == 1:
                raise agent.RateLimitError('Rate limited', 60)
            return completion(config, endpoint, payload)
        self.gateway.transport = limited
        with patch.object(agent.time, 'sleep') as sleep:
            self.gateway.run(self.request)
            sleep.assert_called_once_with(60)
        self.assertEqual(2, len(calls))

    def test_total_time_budget_rejects_late_results_before_cache(self):
        def late(config, endpoint, payload):
            self.now = 541
            return completion(config, endpoint, payload)
        self.gateway.transport = late
        with self.assertRaises(agent.UpstreamError):
            self.gateway.run(self.request)
        self.assertFalse(self.gateway.cache)

    def test_restart_selector_excludes_other_tomcat_and_portal_processes(self):
        with tempfile.TemporaryDirectory() as temporary:
            proc = Path(temporary)
            base = Path('/test/ai-summary-runtime/tomcat-8081')
            for pid, arguments in {
                    1: ['java', '-Dcatalina.base=' + str(base), 'org.apache.catalina.startup.Bootstrap'],
                    2: ['java', '-Dcatalina.base=/another/tomcat', 'org.apache.catalina.startup.Bootstrap'],
                    3: ['python', '-m', 'uvicorn', '--port', '8090'],
                    4: ['java', '-Dcatalina.base=' + str(base), 'unrelated.Main']}.items():
                (proc / str(pid)).mkdir()
                (proc / str(pid) / 'cmdline').write_bytes(b'\0'.join(arg.encode() for arg in arguments))
            self.assertEqual([1], switch.runtime_processes(base, proc))

    def test_property_switch_is_repeatable_preserves_unrelated_config(self):
        original = 'db_password=local-private-test\n# existing comment\n' + agent_setting('ollama')
        values = switch.settings('openrouter')
        updated = switch.update_properties(original, values)
        self.assertEqual(updated, switch.update_properties(updated, values))
        self.assertIn('db_password=local-private-test\n# existing comment\n', updated)
        self.assertEqual(1, updated.count('clinical.ai_summary_generation.agent='))
        restored = switch.update_properties(updated, switch.settings('ollama'))
        self.assertIn(agent_setting('ollama'), restored)
        self.assertNotIn(agent_setting('http'), restored)

    def test_switch_refuses_a_budget_below_the_pipeline_floor(self):
        self.assertIn('clinical.ai_summary_generation.http.name=OpenRouter / qwen/qwen3.5-27b',
                      switch.update_properties('', switch.settings('openrouter')))
        switch.settings('openrouter', request_bytes=pipeline.REQUEST_BYTES)
        with self.assertRaises(ValueError):
            switch.settings('openrouter', request_bytes=pipeline.REQUEST_BYTES - 1)


def agent_setting(value):
    return 'clinical.ai_summary_generation.agent=' + value + '\n'


if __name__ == '__main__':
    unittest.main()
