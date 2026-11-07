# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Run the shipped postinst cutover against retained operator configuration."""

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
ENTRY = (b"\n\n# Mandatory modern-chart cutover; retain this on rollback (#4793).\n"
         b"AbandonOldChart=true\n")


def migration_block():
    text = POSTINST.read_text(encoding="utf-8")
    match = re.search(r"^        # BEGIN MODERN_CHART_CUTOVER\n.*?"
                      r"^        # END MODERN_CHART_CUTOVER\n", text, re.S | re.M)
    if not match:
        raise AssertionError("postinst modern-chart cutover block is missing")
    return match.group(0)


class TestModernChartCutover(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.properties = self.root / "carlos.properties"
        self.started = self.root / "application-started"

    def command(self, lock_held="1"):
        env = dict(os.environ, PROVISION_LOCK_HELD=lock_held,
                   PROPERTIES=str(self.properties), STARTED=str(self.started))
        return ["sh", "-c", "set -e\n" + migration_block()
                + '\nprintf started > "$STARTED"\n'], env

    def run_block(self, lock_held="1"):
        command, env = self.command(lock_held)
        return subprocess.run(command, env=env, capture_output=True,
                              text=True, check=False)

    def write_properties(self, setting):
        # Match the package's root:carlos-style mode: the application group
        # can read but cannot write, while the configuring owner can migrate.
        original = (b"# Keep operator comments and encrypted-data key\n"
                    b"db_name=clinic\nencryption.util.secret.key=unchanged-key==\n"
                    + setting + b"other=retained\n")
        self.properties.write_bytes(original)
        self.properties.chmod(0o640)
        return original

    def test_upgrades_absent_false_and_duplicate_settings_without_rewriting_data(self):
        for setting in (b"", b"AbandonOldChart=false\n",
                        b"AbandonOldChart=true\nAbandonOldChart=false\n",
                        b"AbandonOldChart : no\n",
                        b"AbandonOldCh\\u0061rt=off\n"):
            with self.subTest(setting=setting):
                original = self.write_properties(setting)
                before = self.properties.stat()
                result = self.run_block()
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertTrue(self.started.exists())
                self.assertEqual(self.properties.read_bytes(), original + ENTRY)
                after = self.properties.stat()
                self.assertEqual(stat.S_IMODE(after.st_mode), 0o640)
                self.assertEqual((after.st_uid, after.st_gid),
                                 (before.st_uid, before.st_gid))
                self.assertEqual(after.st_ino, before.st_ino,
                                 "a waiting Java key writer must retain the same inode")
                again = self.run_block()
                self.assertEqual(again.returncode, 0, again.stderr)
                self.assertEqual(self.properties.read_bytes(), original + ENTRY)

    def test_terminates_trailing_continuation_before_mandatory_setting(self):
        for ending in (b"\\", b"\\\n", b"\\\r", b"\\\r\n"):
            with self.subTest(ending=ending):
                original = b"other=retained" + ending
                self.properties.write_bytes(original)
                result = self.run_block()
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.properties.read_bytes(), original + ENTRY)

    def test_failure_refuses_start_without_creating_stub_configuration(self):
        result = self.run_block()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("AbandonOldChart=true", result.stderr)
        self.assertIn("application will not be started", result.stderr)
        self.assertFalse(self.properties.exists())
        self.assertFalse(self.started.exists())

    def test_directory_source_refuses_start(self):
        self.properties.mkdir()
        result = self.run_block()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("AbandonOldChart=true", result.stderr)
        self.assertFalse(self.started.exists())
        self.assertTrue(self.properties.is_dir())

    def test_no_rewrite_without_provisioning_lock(self):
        original = self.write_properties(b"AbandonOldChart=false\n")
        result = self.run_block(lock_held="0")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), original)

    def test_parallel_configures_append_one_complete_entry(self):
        original = self.write_properties(b"AbandonOldChart=false\n")
        command, env = self.command()
        processes = [subprocess.Popen(command, env=env, stdout=subprocess.PIPE,
                                      stderr=subprocess.PIPE, text=True)
                     for _ in range(2)]
        try:
            for process in processes:
                _, stderr = process.communicate(timeout=15)
                self.assertEqual(process.returncode, 0, stderr)
            self.assertEqual(self.properties.read_bytes(), original + ENTRY)
        finally:
            for process in processes:
                if process.poll() is None:
                    process.kill()
                process.wait(timeout=5)

    def test_cutover_runs_after_configuration_render_and_before_service_start(self):
        source = POSTINST.read_text(encoding="utf-8")
        cutover = source.index("# BEGIN MODERN_CHART_CUTOVER")
        self.assertGreater(cutover, source.index("carlos-ctl init-config ||"))
        self.assertGreater(cutover, source.index("if ! carlos-ctl db-users; then"))
        self.assertLess(cutover, source.index('emr_verb=start', cutover))
        self.assertIn('if [ "${PROVISION_LOCK_HELD}" = 1 ]', migration_block())

    def test_fresh_install_skeleton_explicitly_selects_modern_chart(self):
        settings = re.findall(r"^AbandonOldChart=(.*)$", SHIPPED.read_text(), re.M)
        self.assertEqual(settings, ["true"])


if __name__ == "__main__":
    unittest.main()
