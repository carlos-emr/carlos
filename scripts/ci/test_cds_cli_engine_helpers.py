# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock

SPEC = importlib.util.spec_from_file_location("cds_cli_engine", Path(__file__).with_name("run-cds-cli-engine.py"))
ENGINE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ENGINE)


class CliEvidenceContractTest(unittest.TestCase):
    def test_preview_cannot_claim_clinical_success_before_ordinary_import(self):
        submit, invoke, fixture = Mock(), Mock(), Mock()
        submit.return_value = {"state": "COMPLETE", "clinicalComplete": True, "committedUnits": 1}
        with self.assertRaisesRegex(RuntimeError, "preview was mislabeled"):
            ENGINE.qualify_ordinary_and_replay({"sources": {"dryrun": "synthetic.xml"}},
                                              submit, invoke, fixture, [], {}, {}, Path)
        invoke.assert_not_called()
        fixture.assert_not_called()

    def test_hard_crash_cannot_deliver_a_terminal_result_or_start_recovery(self):
        invoke = Mock(side_effect=[[], [], [{"event": "RESULT"}]])
        fixture = Mock(side_effect=[{"cancelRequested": True}, {"checkpoints": 2, "nativeNotes": 1}])
        with self.assertRaisesRegex(RuntimeError, "hard-crashed engine falsely delivered"):
            ENGINE.qualify_interruption_and_resume({"sources": {"cancel": "synthetic.xml"}}, invoke, fixture,
                                                  [], [], {"interrupted": "original-run"}, Path)
        self.assertEqual(3, invoke.call_count)
        self.assertEqual(2, fixture.call_count)

    def test_course_shift_must_match_every_native_copy_before_refusal_controls(self):
        complete = {"clinicalComplete": True, "discoveryComplete": True, "inventoryState": "SEALED",
                    "pendingUnits": 0, "targetCopiesPlanned": 2, "reportsDelivered": True,
                    "cleanupComplete": True, "resumeRequested": False, "maintenanceReleasePending": False,
                    "maintenanceReleaseState": "RELEASED"}
        fixture = Mock(return_value={"nativeTargets": 2, "shiftedBirthDates": 2, "shiftedMedicationDates": 2,
                                     "shiftedAllergyDates": 2, "shiftedNoteDates": 1})
        invoke = Mock()
        with self.assertRaisesRegex(RuntimeError, "exactly two independent copies"):
            ENGINE.qualify_course_refusals({"courseId": 1, "sources": {"course": "synthetic.xml"}},
                                          Mock(return_value=complete), invoke, fixture, [], [], {"course": "course-run"}, Path)
        invoke.assert_not_called()
        fixture.assert_called_once_with("inspect", "course-run")

    def read(self, value):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "owned-evidence.log"
            path.write_text(value, encoding="utf-8")
            return ENGINE.rows(path)

    def test_partial_foreground_line_does_not_invent_a_result(self):
        accepted = {"event": "ACCEPTED", "runId": "recorded-handle"}
        self.assertEqual([accepted], self.read(json.dumps(accepted) + '\n{"event":"RESULT"'))

    def test_progress_is_filtered_but_unknown_operational_json_is_refused(self):
        self.assertEqual([], self.read('{"event":"PROGRESS"}\n'))
        with self.assertRaisesRegex(RuntimeError, "unexpected JSON"):
            self.read('{"event":"UNKNOWN"}\n')

    def test_oversized_line_is_refused_before_unbounded_parsing(self):
        with self.assertRaisesRegex(RuntimeError, "bound"):
            self.read("x" * 65537 + "\n")

    def test_exactly_one_result_is_required(self):
        result = {"event": "RESULT", "state": "COMPLETE"}
        self.assertEqual(result, ENGINE.result([result]))
        for values in ([], [result, result]):
            with self.subTest(values=values), self.assertRaises(RuntimeError):
                ENGINE.result(values)

    def test_clinical_completion_requires_delivered_reports_and_released_maintenance(self):
        complete = {"clinicalComplete": True, "discoveryComplete": True, "inventoryState": "SEALED",
                    "pendingUnits": 0, "targetCopiesPlanned": 1, "reportsDelivered": True,
                    "cleanupComplete": True, "resumeRequested": False, "maintenanceReleasePending": False,
                    "maintenanceReleaseState": "RELEASED"}
        ENGINE.assert_complete(complete)
        for changed in ({"reportsDelivered": False}, {"maintenanceReleaseState": "HELD"},
                        {"clinicalComplete": 1}, {"pendingUnits": 1}):
            with self.subTest(changed=changed), self.assertRaises(RuntimeError):
                ENGINE.assert_complete(complete | changed)

    def test_protected_report_receipt_must_match_the_actual_delivered_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "owned-report.bin"
            path.write_bytes(b"owned evidence")
            receipt = {"protectedPath": str(path), "bytes": path.stat().st_size,
                       "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
            rows = [{"event": "PROTECTED_REPORTS", "reports": dict.fromkeys(
                ("event", "error", "receipt", "units"), receipt)}]
            ENGINE.assert_protected_reports(rows, Path)
            path.write_bytes(b"changed evidence")
            with self.assertRaisesRegex(RuntimeError, "size/hash"):
                ENGINE.assert_protected_reports(rows, Path)


if __name__ == "__main__":
    unittest.main()
