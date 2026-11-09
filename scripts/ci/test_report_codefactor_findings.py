# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from pathlib import Path
import unittest

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

    def test_missing_current_check_retains_exact_ancestor_identity(self):
        ancestor = "b" * 40
        calls = []

        def fetch(path):
            calls.append(path)
            if "check-runs?" in path:
                return {"check_runs": [] if COMMIT in path else [self.check() | {"head_sha": ancestor}]}
            return {"parents": [{"sha": ancestor}]}

        actual, check = REPORT.latest_completed_check(fetch, "owner/repo", COMMIT)
        self.assertEqual(ancestor, actual)
        self.assertEqual(ancestor, check["head_sha"])
        self.assertEqual(3, len(calls))

    def test_absent_external_analysis_is_bounded(self):
        calls = []

        def fetch(path):
            calls.append(path)
            return {"check_runs": []} if "check-runs?" in path else {"parents": [{"sha": COMMIT}]}

        self.assertIsNone(REPORT.latest_completed_check(fetch, "owner/repo", COMMIT))
        self.assertEqual(20, len(calls))


if __name__ == "__main__":
    unittest.main()
