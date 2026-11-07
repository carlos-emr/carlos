# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from pathlib import Path
import unittest
from junit_evidence import EvidenceError

spec = importlib.util.spec_from_file_location('ebs_privacy', Path(__file__).with_name('run-ebs-payload-privacy.py'))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class EbsPrivacyTest(unittest.TestCase):
    def test_payload_canary_is_a_failed_acceptance_not_an_expected_success(self):
        observation = {'schemaVersion': 1, 'syntheticOnly': True, 'soapPayloadLeaked': True}
        self.assertEqual(probe.assertions(observation), {'payload-free-logging': False})
        observation['soapPayloadLeaked'] = False
        self.assertEqual(probe.assertions(observation), {'payload-free-logging': True})

    def test_empty_fabricated_and_untyped_observations_are_refused(self):
        for row in ({}, {'ready': True}, {'schemaVersion': 1, 'syntheticOnly': True, 'soapPayloadLeaked': 0},
                    {'schemaVersion': 1, 'syntheticOnly': False, 'soapPayloadLeaked': False}):
            with self.subTest(row=row), self.assertRaises(EvidenceError): probe.assertions(row)
