# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("cds_cli_engine", Path(__file__).with_name("run-cds-cli-engine.py"))
ENGINE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ENGINE)


class CliEvidenceContractTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
