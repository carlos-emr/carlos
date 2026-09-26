#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Pins changed_line_audit.py: diff parsing, per-file counting and the JaCoCo XML guard.

Run: python3 -m unittest scripts/coverage/test_changed_line_audit.py
"""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import changed_line_audit as audit_script  # noqa: E402

JACOCO = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
<report name="t">
  <package name="io/github/carlos_emr/carlos/lab/service">
    <sourcefile name="MrpRoutingService.java">
      <line nr="10" mi="0" ci="3" mb="0" cb="0"/>
      <line nr="11" mi="2" ci="0" mb="0" cb="0"/>
      <line nr="12" mi="0" ci="1" mb="0" cb="0"/>
    </sourcefile>
  </package>
</report>
"""

DIFF = """diff --git a/src/main/java/io/github/carlos_emr/carlos/lab/service/MrpRoutingService.java b/src/main/java/io/github/carlos_emr/carlos/lab/service/MrpRoutingService.java
@@ -0,0 +9,4 @@
diff --git a/src/main/java/io/github/carlos_emr/carlos/Unbuilt.java b/src/main/java/io/github/carlos_emr/carlos/Unbuilt.java
@@ -1 +1 @@
"""

SERVICE = "src/main/java/io/github/carlos_emr/carlos/lab/service/MrpRoutingService.java"


class ChangedLineAuditTest(unittest.TestCase):

    def write(self, content):
        handle = tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False)
        handle.write(content)
        handle.close()
        self.addCleanup(os.unlink, handle.name)
        return handle.name

    def test_should_parse_added_line_ranges_for_each_file(self):
        changed = audit_script.parse_diff(DIFF)
        self.assertEqual(changed[SERVICE], {9, 10, 11, 12})
        self.assertEqual(changed["src/main/java/io/github/carlos_emr/carlos/Unbuilt.java"], {1})

    def test_should_count_covered_and_missed_lines_per_file(self):
        lines = audit_script.read_jacoco_lines(self.write(JACOCO))
        files, unmapped = audit_script.audit(lines, audit_script.parse_diff(DIFF))
        # Line 9 is not executable (no JaCoCo entry), 10 and 12 ran, 11 did not.
        self.assertEqual(files[SERVICE], (2, 1, [11]))
        self.assertEqual(unmapped, ["src/main/java/io/github/carlos_emr/carlos/Unbuilt.java"])

    def test_should_reject_unexpected_doctype(self):
        hostile = JACOCO.replace('"report.dtd"', '"file:///etc/passwd"')
        with self.assertRaises(ValueError):
            audit_script.read_jacoco_lines(self.write(hostile))

    def test_should_refuse_paths_outside_production_sources(self):
        with self.assertRaises(SystemExit):
            audit_script.main([self.write(JACOCO), "HEAD", "HEAD", "--path", "src/test/java"])


if __name__ == "__main__":
    unittest.main()
