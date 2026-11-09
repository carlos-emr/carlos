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
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            REPORT.collect(lambda _: [], "owner/repo", COMMIT, self.check())

    def test_mutated_check_count_is_refused(self):
        with self.assertRaisesRegex(ValueError, "changed"):
            REPORT.collect(lambda _: self.check(1), "owner/repo", COMMIT, self.check(0))

    def test_wrong_head_pending_and_excessive_counts_are_refused(self):
        for change in ({"head_sha": "b" * 40}, {"status": "in_progress"},
                       {"output": {"annotations_count": 20001}}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                REPORT.collect(lambda _: self.fail("Must refuse before reading"), "owner/repo", COMMIT,
                               self.check() | change)


if __name__ == "__main__":
    unittest.main()
