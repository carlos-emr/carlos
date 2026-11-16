# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from contextlib import redirect_stdout
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("codefactor_report", Path(__file__).with_name("report-codefactor-findings.py"))
REPORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REPORT)
COMMIT = "a" * 40


class CodeFactorReportTest(unittest.TestCase):
    def check(self, count=101):
        return {"id": 42, "name": "CodeFactor", "head_sha": COMMIT, "status": "completed",
                "conclusion": "failure", "output": {"annotations_count": count}}

    def test_collects_all_pages_and_preserves_last_critical(self):
        paths = []

        def fetch(path):
            paths.append(path)
            if path.endswith("&page=1"):
                return [{"path": "source.java", "annotation_level": "warning"}] * 100
            if path.endswith("&page=2"):
                return [{"path": "last.java", "annotation_level": "failure", "message": "Last finding"}]
            return self.check()

        result = REPORT.collect(fetch, "owner/repo", COMMIT, self.check())
        self.assertTrue(result["complete"])
        self.assertEqual(101, result["annotationCount"])
        self.assertEqual("Last finding", result["annotations"][-1]["message"])
        self.assertEqual(3, len(paths))
        self.assertEqual(64, len(result["annotationsSha256"]))

    def test_incomplete_last_page_is_not_reported_as_complete(self):
        def fetch(path):
            if "annotations?" not in path:
                return self.check()
            return [{"message": "Retained finding"}] * 100 if path.endswith("&page=1") else []

        result = REPORT.collect(fetch, "owner/repo", COMMIT, self.check())
        self.assertFalse(result["complete"])
        self.assertEqual(100, result["annotationCount"])
        self.assertEqual(101, result["declaredAnnotationCount"])
        self.assertEqual(2, result["incomplete"]["page"])
        self.assertEqual("Retained finding", result["annotations"][-1]["message"])

    def test_mutated_check_count_is_refused(self):
        result = REPORT.collect(lambda _: self.check(1), "owner/repo", COMMIT, self.check(0))
        self.assertFalse(result["complete"])
        self.assertEqual("CHECK_CHANGED_DURING_READ", result["incomplete"]["code"])

    def test_wrong_head_pending_and_excessive_counts_are_refused(self):
        for change in ({"head_sha": "b" * 40}, {"status": "in_progress"},
                       {"output": {"annotations_count": 20001}}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                REPORT.collect(lambda _: self.fail("Must refuse before reading"), "owner/repo", COMMIT,
                               self.check() | change)

    def test_missing_current_check_cannot_borrow_a_complete_ancestor_analysis(self):
        ancestor = "b" * 40
        calls = []

        def fetch(path):
            calls.append(path)
            if "check-runs?" in path:
                return {"check_runs": [] if COMMIT in path else [self.check() | {"head_sha": ancestor}]}
            return {"parents": [{"sha": ancestor}]}

        self.assertIsNone(REPORT.latest_completed_check(fetch, "owner/repo", COMMIT))
        self.assertEqual(1, len(calls))
        self.assertIn(COMMIT, calls[0])

    def test_absent_external_analysis_is_bounded(self):
        calls = []

        def fetch(path):
            calls.append(path)
            return {"check_runs": []} if "check-runs?" in path else {"parents": [{"sha": COMMIT}]}

        self.assertIsNone(REPORT.latest_completed_check(fetch, "owner/repo", COMMIT))
        self.assertEqual(1, len(calls))

    def environment(self, output):
        return {"REPORT_REPOSITORY": "owner/repo", "REPORT_COMMIT": COMMIT,
                "GITHUB_TOKEN": "test-token-must-not-be-retained", "REPORT_PATH": str(output)}

    def test_absent_optional_analysis_retains_unavailable_evidence_without_a_clean_result(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "report.json"
            console = io.StringIO()
            with patch.dict(os.environ, self.environment(output)), redirect_stdout(console), \
                    patch.object(REPORT, "latest_completed_check", return_value=None), \
                    patch.object(REPORT.time, "monotonic", side_effect=[0, 901]), \
                    patch.object(REPORT.time, "sleep") as sleep:
                self.assertEqual(0, REPORT.main())
            sleep.assert_not_called()
            result = json.loads(output.read_text())
            self.assertFalse(result["analysisAvailable"])
            self.assertFalse(result["complete"])
            self.assertEqual(COMMIT, result["requestedCommit"])
            self.assertEqual("CODEFACTOR_ANALYSIS_UNAVAILABLE", result["incomplete"]["code"])
            for field in ("commit", "checkId", "conclusion", "annotationCount",
                          "declaredAnnotationCount", "annotationsSha256"):
                self.assertIsNone(result[field], field)
            self.assertEqual([], result["annotations"])
            self.assertIn("::notice title=CodeFactor diagnostics::", console.getvalue())
            self.assertNotIn("test-token-must-not-be-retained", output.read_text() + console.getvalue())

    def test_incomplete_existing_analysis_still_fails_after_retaining_its_evidence(self):
        def fetch(path):
            if "annotations?" not in path:
                return self.check()
            return [{"message": "Retained finding"}] * 100 if path.endswith("&page=1") else []

        partial = REPORT.collect(fetch, "owner/repo", COMMIT, self.check())
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "report.json"
            with patch.dict(os.environ, self.environment(output)), redirect_stdout(io.StringIO()), \
                    patch.object(REPORT, "latest_completed_check", return_value=(COMMIT, self.check())), \
                    patch.object(REPORT, "collect", return_value=partial), \
                    patch.object(REPORT.time, "monotonic", side_effect=[0, 901]):
                self.assertEqual(1, REPORT.main())
            result = json.loads(output.read_text())
            self.assertTrue(result["analysisAvailable"])
            self.assertFalse(result["complete"])
            self.assertEqual(100, result["annotationCount"])
            self.assertEqual(101, result["declaredAnnotationCount"])
            self.assertEqual("INCOMPLETE_ANNOTATION_PAGE", result["incomplete"]["code"])

    def test_request_failure_is_not_downgraded_to_an_absent_analysis(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "report.json"
            with patch.dict(os.environ, self.environment(output)), \
                    patch.object(REPORT, "latest_completed_check", side_effect=RuntimeError("HTTP 403")):
                with self.assertRaisesRegex(RuntimeError, "HTTP 403"):
                    REPORT.main()
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
