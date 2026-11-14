# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import copy
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location("semgrep_complete", Path(__file__).with_name("check-semgrep-complete.py"))
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class SemgrepCompletenessTest(unittest.TestCase):
    def document(self):
        return {"version": "2.1.0", "runs": [{"invocations": [
            {"executionSuccessful": True, "toolExecutionNotifications": []}]}]}

    def test_successful_scan_with_findings_remains_complete(self):
        document = self.document()
        document["runs"][0]["results"] = [{"ruleId": "synthetic-finding", "level": "error"}]
        GUARD.verify(document)

    def test_each_run_and_invocation_must_confirm_execution(self):
        for invalid in ({}, {"executionSuccessful": False}, {"executionSuccessful": 1}):
            with self.subTest(invalid=invalid):
                document = self.document()
                document["runs"][0]["invocations"].append(invalid)
                with self.assertRaises(ValueError):
                    GUARD.verify(document)

    def test_timeout_and_parse_warnings_are_incomplete_even_when_exit_was_successful(self):
        for level in (None, "warning", "error"):
            document = self.document()
            notice = {"message": {"text": "Private diagnostic must not be copied to output"}}
            if level is not None:
                notice["level"] = level
            document["runs"][0]["invocations"][0]["toolExecutionNotifications"].append(notice)
            with self.subTest(level=level), self.assertRaisesRegex(ValueError, "SEMGREP_EXECUTION_INCOMPLETE"):
                GUARD.verify(document)

    def test_informational_notes_do_not_hide_real_errors(self):
        document = self.document()
        notices = document["runs"][0]["invocations"][0]["toolExecutionNotifications"]
        notices.append({"level": "note"})
        GUARD.verify(document)
        notices.append({"level": "error"})
        with self.assertRaises(ValueError):
            GUARD.verify(document)

    def test_empty_or_malformed_evidence_cannot_be_complete(self):
        for document in (None, [], {}, {"version": "2.1.0", "runs": []},
                         {"version": "2.1.0", "runs": [{}]},
                         {"version": "2.1.0", "runs": [{"invocations": []}]}):
            with self.subTest(document=document), self.assertRaises(ValueError):
                GUARD.verify(document)

    def test_unknown_notification_shape_is_not_silently_ignored(self):
        for invalid in (None, {}, [None]):
            document = copy.deepcopy(self.document())
            document["runs"][0]["invocations"][0]["toolExecutionNotifications"] = invalid
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                GUARD.verify(document)
