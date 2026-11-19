# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Exercise the shipped postinst's report lease warning under sh."""

import os
import re
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
POSTINST = ROOT / "debian" / "carlos-emr.postinst"
SHIPPED = ROOT / "src" / "main" / "resources" / "carlos.properties"
KEY = "db_remove_abandoned_timeout"


class TestReportLeaseUpgrade(unittest.TestCase):
    def warning_for(self, installed):
        block = re.search(
            r"^        REPORT_LEASE_TIMEOUT_KEY=.*?^        fi\n",
            POSTINST.read_text(encoding="utf-8"),
            re.M | re.S,
        )
        self.assertIsNotNone(block, "the report lease warning is missing from postinst")
        with tempfile.TemporaryDirectory() as directory:
            properties = Path(directory) / "carlos.properties"
            properties.write_bytes(installed)
            properties.chmod(0o640)
            result = subprocess.run(
                ["sh", "-c", "set -e\n" + block.group(0)],
                env=dict(os.environ, PROPERTIES=str(properties)),
                capture_output=True, text=True, check=True,
            )
            self.assertEqual(properties.read_bytes(), installed)
            self.assertEqual(stat.S_IMODE(properties.stat().st_mode), 0o640)
            return result.stderr

    def test_warns_when_an_upgrade_retains_the_previous_stock_setting(self):
        warning = self.warning_for(
            b"db_remove_abandoned = true\ndb_remove_abandoned_timeout = 15\n"
        )
        self.assertIn(KEY, warning)
        self.assertIn("15", warning)
        self.assertIn("360", warning)
        self.assertIn("sudo carlos-ctl restart", warning)

    def test_preserves_a_site_specific_setting(self):
        self.assertEqual(self.warning_for(b"db_remove_abandoned_timeout = 17\n"), "")

    def test_last_definition_wins(self):
        old = b"  db_remove_abandoned_timeout : 15  \r\n"
        custom = b"\tdb_remove_abandoned_timeout 420\r\n"
        self.assertEqual(self.warning_for(old + custom), "")
        self.assertIn(KEY, self.warning_for(custom + old))

    def test_warns_for_a_continued_effective_timeout(self):
        installed = b"db_remove_abandoned_timeout=360\r\ndb_remove_abandoned_timeout = \\\r\n\t15\r\n"
        self.assertIn(KEY, self.warning_for(installed))

    def test_uses_the_effective_spelling_of_an_escaped_key(self):
        stock = b"db_remove_abandoned_timeout=15\n"
        custom = b"db_remove_abandoned_time\\u006fut : 420\n"
        self.assertEqual(self.warning_for(stock + custom), "")
        self.assertIn(KEY, self.warning_for(custom + stock))

    def test_current_skeleton_does_not_warn(self):
        self.assertEqual(self.warning_for(SHIPPED.read_bytes()), "")
