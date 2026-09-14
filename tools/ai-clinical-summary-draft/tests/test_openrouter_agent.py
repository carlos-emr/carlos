# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
from http.server import HTTPServer
import io
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import ProxyHandler, Request, build_opener
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import openrouter_agent as agent
import pipeline
import switch_summary_agent as switch
from validate_artifact import COMMON_WORDS, words


def completion(config, _endpoint, payload):
    sources = json.loads(payload['messages'][1]['content'])['sources']
    claims = []
    for index, source in enumerate(sources):
        word = sorted(words(source['text']) - COMMON_WORDS)[0]
        claims.append({'id': 'c' + str(index), 'text': word + ' is recorded.', 'source_ids': [source['id']]})
    output = {'claims': claims,
              'sections': [{'id': 'clinical_overview', 'title': 'Clinical overview',
                            'claim_ids': [claim['id'] for claim in claims]}],
              'coverage': [{'source_id': source['id'], 'status': 'cited',
                            'reason': 'Reviewed clinical material in ' + source['id']} for source in sources]}
    return {'model': config['model'], 'choices': [{'finish_reason': 'stop',
                                                 'message': {'content': json.dumps(output)}}]}


class OpenRouterTest(unittest.TestCase):
    def setUp(self):
        self.config = dict(agent.DEFAULTS, api_key='test-key-never-a-real-secret')
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

    def test_live_contract_cache_and_new_request_id(self):
        first = self.gateway.run(self.request)
        second = self.gateway.run(dict(self.request, request_id=str(uuid4())))
        self.assertEqual(first['output'], second['output'])
        self.assertNotEqual(first['request_id'], second['request_id'])
        self.assertEqual(1, len(self.calls))
        self.assertEqual(1, self.gateway.cache_hits)
        payload = self.calls[0]
        self.assertEqual({'only': ['google-vertex'], 'allow_fallbacks': False, 'require_parameters': True,
                          'data_collection': 'deny', 'zdr': True}, payload['provider'])
        schema = payload['response_format']['json_schema']['schema']
        self.assertEqual(1, schema['properties']['coverage']['maxItems'])
        self.assertNotIn('maxItems', schema['properties']['claims'])
        self.assertNotIn(self.config['api_key'], json.dumps(payload))
        self.assertNotIn(self.request['request_id'], json.dumps(payload))
        first['output']['claims'].clear()
        self.assertTrue(self.gateway.run(self.request)['output']['claims'])

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
        changed['coverage'] = []
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
                self.assertEqual('carlos-openrouter-synthetic', json.load(response)['service'])
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

    def test_upstream_timeout_is_sanitized_without_retry(self):
        with patch.object(agent, 'build_opener') as opener:
            opener.return_value.open.side_effect = TimeoutError('private exception text')
            with self.assertRaises(agent.UpstreamError) as raised:
                agent.api_request(self.config, 'key')
            self.assertEqual('OpenRouter connection, timeout, or response-format failure', str(raised.exception))
            self.assertEqual(1, opener.return_value.open.call_count)

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


def agent_setting(value):
    return 'clinical.ai_summary_generation.agent=' + value + '\n'


if __name__ == '__main__':
    unittest.main()
