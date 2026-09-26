#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Unit tests for changed_line_audit.py: python3 -m unittest discover -s scripts/coverage"""

import os
import subprocess
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import changed_line_audit as audit_script  # noqa: E402

REPORT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
<report name="carlos"><package name="io/github/x">
<sourcefile name="A.java"><line nr="10" mi="0" ci="3"/><line nr="11" mi="2" ci="0"/><line nr="12" mi="1" ci="1"/></sourcefile>
</package></report>"""

DIFF = """diff --git a/src/main/java/io/github/x/A.java b/src/main/java/io/github/x/A.java
@@ -9,0 +10,3 @@ class A {
diff --git a/src/main/java/io/github/x/B.java b/src/main/java/io/github/x/B.java
@@ -1 +1 @@
"""


class ChangedLineAuditTest(unittest.TestCase):

    def write_report(self, text):
        handle, name = tempfile.mkstemp(suffix=".xml")
        with os.fdopen(handle, "w") as stream:
            stream.write(text)
        self.addCleanup(os.remove, name)
        return name

    def test_should_count_covered_and_missed_lines_for_changed_files(self):
        lines = audit_script.read_jacoco_lines(self.write_report(REPORT))
        changed = audit_script.changed_lines(DIFF)
        per_file, unmapped = audit_script.audit(lines, changed)
        self.assertEqual(changed["src/main/java/io/github/x/A.java"], {10, 11, 12})
        # Line 12 is partly covered, so it counts as covered.
        self.assertEqual(per_file, {"src/main/java/io/github/x/A.java": (2, 1)})
        self.assertEqual(unmapped, ["src/main/java/io/github/x/B.java"])

    def test_should_reject_report_with_entity_declaration(self):
        hostile = REPORT.replace('"report.dtd">', '"report.dtd" [<!ENTITY x "y">]>')
        with self.assertRaises(ValueError):
            audit_script.read_jacoco_lines(self.write_report(hostile))

    def test_should_accept_optional_head_and_per_file_flag(self):
        args = audit_script.parse_args(["r.xml", "origin/release/2026.08", "--per-file"])
        self.assertIsNone(args.head)
        self.assertTrue(args.per_file)
        self.assertEqual(audit_script.parse_args(["r.xml", "a", "b"]).head, "b")

    def test_should_ignore_configured_diff_prefixes_in_working_tree_audit(self):
        report = self.write_report(REPORT)
        script = str(Path(audit_script.__file__).resolve())
        with tempfile.TemporaryDirectory() as directory:
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=directory, text=True)
            git("init", "-q")
            source = Path(directory) / "src/main/java/io/github/x/A.java"
            source.parent.mkdir(parents=True)
            source.write_text("old\n" * 12)
            git("add", ".")
            git("-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                "-c", "commit.gpgsign=false", "commit", "-qm", "fixture")
            source.write_text("old\n" * 9 + "new\n" + "old\n" * 2)
            for config in ("diff.mnemonicPrefix", "diff.noprefix"):
                git("config", config, "true")
                output = subprocess.check_output(
                    [sys.executable, script, report, "HEAD", "--per-file"], cwd=directory, text=True)
                self.assertIn("1 covered / 1 (100.0%)", output)
                self.assertIn("src/main/java/io/github/x/A.java", output)


if __name__ == "__main__":
    unittest.main()
