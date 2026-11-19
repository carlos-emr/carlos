# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Independent negative controls for native objects and unchanged historical readers."""
import copy
import importlib.util
from pathlib import Path
import sys
import unittest

SPEC = importlib.util.spec_from_file_location('native_xml_contracts', Path(__file__).with_name('run-native-xml-binary-contracts.py'))
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class NativeXmlContractsTest(unittest.TestCase):
    def row(self):
        return {'schemaCompiles': True, 'defaultRead': '<value kind="A"><a>7</a><b>8</b></value>',
                'strictRead': '<value kind="A"><a>7</a><b>8</b></value>'}

    def test_exact_native_inventory_is_pinned(self):
        self.assertEqual(MODULE.gate.digest(MODULE.INVENTORY), MODULE.INVENTORY_SHA256)
        ids = MODULE.gate.read_json(MODULE.INVENTORY)
        self.assertEqual(len(ids), 56)
        self.assertEqual(len(set(ids)), 56)
        self.assertIn('webserv.transfer_objects.DrugTransfer:populated', ids)
        self.assertIn('webserv.rest.to.model.AppointmentExtTo:populated', ids)

    def test_missing_cases_and_substitute_provider_are_refused(self):
        for report in [{'provider': 'glassfish', 'cases': {}},
                       {'provider': 'glassfish', 'cases': {'one': {'provider': 'org.eclipse.persistence.jaxb.JAXBContext'}}}]:
            with self.assertRaises(MODULE.EvidenceError): MODULE.require_cases(report, ['one'], 'glassfish')

    def test_changed_attributes_order_values_and_names_are_not_normalized(self):
        original = {'one': self.row()}
        for xml in ['<value kind="B"><a>7</a><b>8</b></value>', '<value kind="A"><b>8</b><a>7</a></value>',
                    '<value kind="A"><a>65</a><b>8</b></value>', '<value kind="A"><a xmlns="urn:new">7</a><b>8</b></value>']:
            candidate = copy.deepcopy(original); candidate['one']['strictRead'] = xml
            self.assertIn('strictRead-bound-state-changed', MODULE.compare_readers(original, candidate)['one']['problems'])

    def test_new_reader_failures_and_missing_strict_schemas_are_refused(self):
        original = {'one': self.row()}
        candidate = copy.deepcopy(original); del candidate['one']['strictRead']
        candidate['one']['strictReadError'] = 'failure'
        self.assertIn('strictRead-new-failure', MODULE.compare_readers(original, candidate)['one']['problems'])
        candidate = copy.deepcopy(original); candidate['one']['strictSchemaUnavailable'] = True
        self.assertIn('strict-schema-unavailable', MODULE.compare_readers(original, candidate)['one']['problems'])

    def test_old_context_failures_remain_explicit_and_cannot_change(self):
        original = {'one': {'error': 'old-context-failure'}}
        self.assertEqual(MODULE.compare_readers(original, original)['one']['contextError'], 'old-context-failure')
        self.assertFalse(MODULE.compare_readers(original, original)['one']['problems'])
        self.assertIn('context-outcome-changed', MODULE.compare_readers(original, {'one': {'error': 'new-failure'}})['one']['problems'])

    def test_unchanged_bound_state_is_compared_with_both_readers(self):
        original = {'one': self.row()}
        result = MODULE.compare_readers(original, original)['one']
        self.assertFalse(result['problems'])
        self.assertEqual(result['readers'], {'defaultRead': 'compared', 'strictRead': 'compared'})
