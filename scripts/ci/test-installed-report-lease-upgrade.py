# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Run on a disposable host with the candidate carlos-emr package installed."""

import grp
import os
import re
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

SHARE = Path("/usr/share/carlos-emr")
POSTINST = Path("/var/lib/dpkg/info/carlos-emr.postinst")
SHIPPED = SHARE / "skel" / "carlos.properties"
KEY = "db_remove_abandoned_timeout"


class TestReportLeaseUpgrade(unittest.TestCase):
    def warning_for(self, installed):
        block = re.search(
            r"^        REPORT_WEB_INF=.*?^        }\n",
            POSTINST.read_text(encoding="utf-8"),
            re.M | re.S,
        )
        self.assertIsNotNone(block, "the report lease warning is missing from postinst")
        with tempfile.TemporaryDirectory() as directory:
            Path(directory).chmod(0o711)
            properties = Path(directory) / "carlos.properties"
            properties.write_bytes(installed)
            os.chown(properties, 0, grp.getgrnam("carlos").gr_gid)
            properties.chmod(0o640)
            result = subprocess.run(
                ["sh", "-c", "set -e\n" + block.group(0)],
                env=dict(os.environ, SHARE=str(SHARE), PROPERTIES=str(properties)),
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
        self.assertIn("resolves to 15 seconds", warning)
        self.assertIn("360", warning)
        self.assertIn("sudo carlos-ctl restart", warning)

    def test_preserves_a_site_specific_setting(self):
        self.assertEqual(self.warning_for(b"db_remove_abandoned_timeout = 17\n"), "")

    def test_last_definition_wins(self):
        old = b"  db_remove_abandoned_timeout : 15  \r\n"
        custom = b"\tdb_remove_abandoned_timeout 420\r\n"
        self.assertEqual(self.warning_for(old + custom), "")
        self.assertIn("resolves to 15 seconds", self.warning_for(custom + old))

    def test_warns_for_a_continued_effective_timeout(self):
        installed = b"db_remove_abandoned_timeout=360\r\ndb_remove_abandoned_timeout = \\\r\n\t15\r\n"
        self.assertIn("resolves to 15 seconds", self.warning_for(installed))

    def test_uses_the_effective_spelling_of_an_escaped_key(self):
        stock = b"db_remove_abandoned_timeout=15\n"
        custom = b"db_remove_abandoned_time\\u006fut : 420\n"
        self.assertEqual(self.warning_for(stock + custom), "")
        self.assertIn("resolves to 15 seconds", self.warning_for(custom + stock))

    def test_current_skeleton_does_not_warn(self):
        shipped = SHIPPED.read_bytes()
        self.assertRegex(shipped, rb"(?m)^db_remove_abandoned_timeout\s*=\s*360$")
        self.assertEqual(self.warning_for(shipped), "")

    def test_warns_for_equivalent_15_second_values(self):
        for value in (b"015", b"+15", b"0x0f", b"${site.lease}", b"${missing.lease:15}"):
            with self.subTest(value=value):
                installed = b"site.lease=15\ndb_remove_abandoned_timeout=" + value + b"\n"
                self.assertIn("resolves to 15 seconds", self.warning_for(installed))

    def test_respects_a_referenced_site_override(self):
        self.assertEqual(self.warning_for(b"db_remove_abandoned_timeout=${site.lease}\nsite.lease=420\n"), "")

    def test_reports_an_uncheckable_value_without_printing_it(self):
        for value in (b"${private-marker}", b"private-marker", b"${db_remove_abandoned_timeout}"):
            with self.subTest(value=value):
                warning = self.warning_for(b"db_remove_abandoned_timeout=" + value + b"\n")
                self.assertIn("could not verify", warning)
                self.assertNotIn("private-marker", warning)


if __name__ == "__main__":
    unittest.main()
