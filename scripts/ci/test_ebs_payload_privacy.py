# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from pathlib import Path
import unittest
from junit_evidence import EvidenceError

spec = importlib.util.spec_from_file_location('ebs_privacy', Path(__file__).with_name('run-ebs-payload-privacy.py'))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class EbsPrivacyTest(unittest.TestCase):
    def metrics(self, **changes):
        return {'schemaVersion': 2, 'syntheticOnly': True, 'soapPayloadLeaked': False,
                'messageStreamsPreserved': True, 'attachmentsUntouched': True,
                'diagnosticsExercised': True, **changes}

    def test_payload_canary_is_a_failed_acceptance_not_an_expected_success(self):
        observation = self.metrics(soapPayloadLeaked=True)
        self.assertFalse(probe.assertions(observation)['payload-free-logging'])
        observation['soapPayloadLeaked'] = False
        self.assertTrue(all(probe.assertions(observation).values()))

    def test_stream_consumption_attachment_access_or_missing_direction_is_failed_acceptance(self):
        for field in ('messageStreamsPreserved', 'attachmentsUntouched', 'diagnosticsExercised'):
            with self.subTest(field=field):
                self.assertFalse(all(probe.assertions(self.metrics(**{field: False})).values()))

    def test_logger_negative_control_must_really_disclose_canary_without_mutating_transport(self):
        control = self.metrics(soapPayloadLeaked=True)
        self.assertTrue(all(probe.qualified_assertions(self.metrics(), control).values()))
        for field in ('soapPayloadLeaked', 'messageStreamsPreserved', 'attachmentsUntouched', 'diagnosticsExercised'):
            with self.subTest(field=field):
                result = probe.qualified_assertions(self.metrics(), {**control, field: False})
                self.assertFalse(result['logger-negative-control-detected'])
        self.assertFalse(probe.qualified_assertions(self.metrics(soapPayloadLeaked=True), control)['payload-free-logging'])

    def test_empty_fabricated_and_untyped_observations_are_refused(self):
        for row in ({}, {'ready': True}, self.metrics(schemaVersion=1), self.metrics(soapPayloadLeaked=0),
                    self.metrics(syntheticOnly=False),
                    *[self.metrics(**{field: 1}) for field in
                      ('messageStreamsPreserved', 'attachmentsUntouched', 'diagnosticsExercised')]):
            with self.subTest(row=row), self.assertRaises(EvidenceError): probe.assertions(row)
        incomplete = self.metrics(); incomplete.pop('attachmentsUntouched')
        with self.assertRaises(EvidenceError): probe.assertions(incomplete)
