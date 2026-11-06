# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import copy
import importlib.util
from pathlib import Path
import unittest
from junit_evidence import EvidenceError

spec = importlib.util.spec_from_file_location('binary_compat', Path(__file__).with_name('run-main-release-wire-compatibility.py'))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class BinaryClientComparisonTest(unittest.TestCase):
    def report(self):
        return {'schemaVersion': 1, 'errors': {}, 'cases': {
            name: {'kind': 'boolean', 'value': 'true'} if name == 'opt-in-soap-payload-leaked'
            else {'kind':'api','value':'{"example.Fake":["method:read"]}'} if name == 'changed-public-java-api'
            else {'kind':'xml','value':'<value>FAKE</value>'} if name.endswith('xml')
            else {'kind': 'json', 'value': '{"value":1,"name":"FAKE"}'} for name in probe.EXPECTED_CASES}}

    def test_preexisting_privacy_failure_is_distinguished_from_a_new_wire_regression(self):
        before = self.report(); after = copy.deepcopy(before)
        result = probe.compare(before, after)
        self.assertTrue(result['ready'])
        self.assertEqual(result['expectedCases'], 628)
        self.assertEqual(result['findings'][0]['classification'], 'pre-existing')
        self.assertTrue(result['findings'][0]['releaseFailedPrivacy'])
        after['cases']['FilterDefinitionTransfer-default-session-json']['value'] = '{"value":true,"name":"FAKE"}'
        result = probe.compare(before, after)
        self.assertFalse(result['ready'])
        self.assertEqual(result['problems'][0]['reason'], 'wire-value-changed')

    def test_omitted_cases_and_identical_errors_cannot_qualify_comparison(self):
        before = self.report(); after = self.report()
        name = 'provider-session-json'
        del after['cases'][name]
        with self.assertRaisesRegex(EvidenceError, 'Missing'): probe.compare(before, after)
        for report in (before, after):
            report['cases'].pop(name, None); report['errors'][name] = 'example.ClientException'
        self.assertFalse(probe.compare(before, after)['ready'])

    def test_xml_prefixes_are_canonicalized_but_clinical_text_is_not_normalized(self):
        row = lambda text: {'kind': 'xml', 'value': text}
        self.assertEqual(probe.canonical(row('<a:value xmlns:a="urn:fake"> café Ω </a:value>')),
                         probe.canonical(row('<b:value xmlns:b="urn:fake"> café Ω </b:value>')))
        self.assertNotEqual(probe.canonical(row('<value> café </value>')),
                            probe.canonical(row('<value>café</value>')))
        self.assertEqual(probe.canonical(row('<value xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
            'xmlns:a="urn:fake" xsi:type="a:Text">a:clinical</value>')),
            probe.canonical(row('<value xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
            'xmlns:b="urn:fake" xsi:type="b:Text">a:clinical</value>')))
        with self.assertRaises(Exception):
            probe.canonical(row('<!DOCTYPE value [<!ENTITY fake "data">]><value>&fake;</value>'))

    def test_missing_fields_duplicate_json_keys_and_nonfinite_numbers_are_not_silently_accepted(self):
        with self.assertRaises(EvidenceError): probe.canonical({'kind': 'json'})
        with self.assertRaises(EvidenceError):
            probe.canonical({'kind': 'json', 'value': '{"value":1,"value":2}'})
        with self.assertRaises(EvidenceError): probe.canonical({'kind': 'json', 'value': 'NaN'})
        self.assertNotEqual(probe.canonical({'kind': 'json', 'value': '{"value":1}'}),
                            probe.canonical({'kind': 'json', 'value': '{}'}))

    def test_new_payload_exposure_is_a_regression_while_its_removal_is_a_fix(self):
        before = self.report(); after = self.report()
        before['cases']['opt-in-soap-payload-leaked']['value'] = 'false'
        result = probe.compare(before, after)
        self.assertFalse(result['ready']); self.assertEqual(result['findings'][0]['classification'], 'regression')
        result = probe.compare(after, before)
        self.assertTrue(result['ready']); self.assertEqual(result['findings'][0]['classification'], 'fixed')

    def test_malformed_release_xml_fails_while_a_repaired_main_response_is_reported(self):
        before = self.report(); after = self.report(); name = 'FilterDefinitionTransfer-default-xml'
        before['cases'][name] = {'kind': 'xml', 'value': '<value>\x00</value>'}
        after['cases'][name] = {'kind': 'xml', 'value': '<value/>'}
        result = probe.compare(before, after)
        self.assertFalse(result['ready'])
        self.assertIn('fixed-main-invalid-wire', {f['classification'] for f in result['findings']})
        self.assertFalse(probe.compare(after, before)['ready'])
        before['errors'][name] = 'example.RejectedInput'; del before['cases'][name]
        self.assertFalse(probe.compare(before, after)['ready'])

    def test_xml_tail_text_and_numeric_precision_are_not_lost(self):
        row = lambda text: {'kind': 'xml', 'value': text}
        self.assertNotEqual(probe.canonical(row('<value><x/> </value>')),
                            probe.canonical(row('<value><x/></value>')))
        row = lambda text: {'kind': 'json', 'value': text}
        self.assertNotEqual(probe.canonical(row('123456789012345678901234567890')),
                            probe.canonical(row('123456789012345678901234567891')))

    def test_public_api_additions_do_not_hide_removals_or_signature_changes(self):
        before = self.report(); after = self.report(); name = 'changed-public-java-api'
        after['cases'][name]['value'] = '{"example.Fake":["method:read","method:write"],"example.New":["constructor:new"]}'
        self.assertTrue(probe.compare(before, after)['ready'])
        after['cases'][name]['value'] = '{"example.Fake":["method:write"]}'
        result = probe.compare(before, after)
        self.assertFalse(result['ready'])
        self.assertEqual(result['problems'][0]['removedMembers'], ['method:read'])
        after['cases'][name] = {'kind':'json','value':'{}'}
        self.assertFalse(probe.compare(before, after)['ready'])

    def test_unresolved_type_on_both_binaries_cannot_look_like_compatibility(self):
        before = self.report(); after = self.report(); name = 'changed-public-java-api'
        for report in (before,after): report['cases'][name]['value'] = '{"example.Missing":[]}'
        self.assertFalse(probe.compare(before,after)['ready'])
        after['cases'][name]['value'] = '{"example.Missing":["not-public"]}'
        self.assertTrue(probe.compare(before,after)['ready'])
        for report in (before, after):
            report['cases'][name]['value'] = '{"example.Missing":["unresolved-api:java.lang.NoClassDefFoundError"]}'
        self.assertFalse(probe.compare(before,after)['ready'])

    def test_added_abstract_methods_enum_constants_and_overloads_require_client_review(self):
        old = ['type:public abstract', 'method:public abstract java.lang.String example.Fake.read(java.lang.String)',
               'constructor:protected example.Fake(java.lang.String)', 'enum-constant:OLD']
        additions = [
            'method:public abstract void example.Fake.close()',
            'method:public java.lang.String example.Fake.read(java.lang.Integer)',
            'constructor:public example.Fake(java.lang.Integer)',
            'enum-constant:NEW']
        before = self.report(); after = self.report(); name = 'changed-public-java-api'
        import json
        before['cases'][name]['value'] = json.dumps({'example.Fake': old})
        after['cases'][name]['value'] = json.dumps({'example.Fake': old + additions})
        result = probe.compare(before, after)
        self.assertFalse(result['ready'])
        self.assertEqual(result['problems'][0]['reason'], 'public-api-addition-needs-client-review')
        self.assertEqual(result['problems'][0]['addedMembers'], sorted(additions))

    def test_new_named_concrete_method_and_new_types_are_not_competing_overloads(self):
        self.assertEqual(probe.risky_additions(
            ['method:public void example.Fake.read()'],
            ['method:public void example.Fake.read()', 'method:public void example.Fake.write()']), [])
        self.assertIsNone(probe.callable_name('field:public java.lang.String example.Fake.read'))
        self.assertEqual(probe.callable_name('method:public <T> T example.Fake.read(T) throws java.io.IOException'), 'read')
