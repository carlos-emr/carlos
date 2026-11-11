# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Run the shipped postinst cutover against retained operator configuration."""

import base64
import os
import re
import stat
import subprocess
import shlex
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
    @classmethod
    def setUpClass(cls):
        cls.compilation = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.compilation.cleanup)
        cls.classes = Path(cls.compilation.name)
        runtime = Path(os.environ.get("JAVA_HOME", "/usr")) / "bin"
        cls.java = runtime / "java"
        source = ROOT / "src/main/java/io/github/carlos_emr/carlos/config/PackagedConfigurationPreflight.java"
        fixture = ROOT / "debian/assets/tests/fixtures/ConfigurationPreflightFixture.java"
        subprocess.run([str(runtime / "javac"), "--release", "25", "-d", str(cls.classes),
                        str(source), str(fixture)], check=True, capture_output=True, text=True)

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.properties = self.root / "carlos.properties"
        self.started = self.root / "application-started"

    def command(self, lock_held="1"):
        env = dict(os.environ, PROVISION_LOCK_HELD=lock_held,
                   PROPERTIES=str(self.properties), STARTED=str(self.started))
        launcher = shlex.join([str(self.java), "-cp", str(self.classes),
                               "io.github.carlos_emr.carlos.config.ConfigurationPreflightFixture"])
        block = migration_block().replace("/usr/lib/carlos-emr/carlos-emr-config-preflight reconcile",
                                          launcher + ' reconcile "$PROPERTIES"')
        return ["sh", "-c", "set -e\n" + block
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

    def test_deferred_configure_still_persists_the_flag(self):
        original = self.write_properties(b"AbandonOldChart=false\n")
        result = self.run_block(lock_held="0")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), original + ENTRY)

    def test_effective_true_is_unchanged_even_when_other_settings_follow_it(self):
        original = self.write_properties(b"AbandonOldChart=true\n")
        result = self.run_block()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), original)

    def test_actual_java_escape_and_continuation_semantics_select_true(self):
        for setting in (b"AbandonOldCh\\u0061rt : true\n", b"AbandonOldChart=tr\\\n ue\n"):
            with self.subTest(setting=setting):
                original = self.write_properties(setting)
                result = self.run_block()
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.properties.read_bytes(), original)

    def test_upgrade_status_agrees_with_actual_java_properties(self):
        source = (ROOT / 'scripts/deb-upgrade-baseline.sh').read_text()
        function = re.search(r'\nproperties_without_cutover\(\) \{.*?\n\}', source, re.S).group(0)
        cases = (b'AbandonOldChart=true\nother=retained\n', b'AbandonOldChart=false\n',
                 b'AbandonOldCh\\u0061rt : true\n', b'AbandonOldChart=tr\\\n ue\n',
                 b'AbandonOldChart=true\nAbandonOldCh\\art=false\n',
                 b'  ! AbandonOldChart=false\\\nAbandonOldChart=true\n',
                 b'AbandonOldChart\\ =true\n', b'AbandonOldChart=TRUE\r\n# later comment\n')
        for data in cases:
            with self.subTest(data=data):
                self.properties.write_bytes(data)
                expected = subprocess.run([str(self.java), '-cp', str(self.classes),
                    'io.github.carlos_emr.carlos.config.ConfigurationPreflightFixture', 'status', str(self.properties)],
                    capture_output=True, text=True, check=True).stdout
                actual = subprocess.run(['bash', '-c', function + '\nproperties_without_cutover status'],
                    env={**os.environ, 'CARLOS_ETC_DIR': str(self.root)}, capture_output=True, text=True, check=True).stdout
                self.assertEqual(actual, expected)

    def test_explicit_false_override_is_announced_without_printing_its_value(self):
        self.write_properties(b"AbandonOldChart=FAKE-sensitive-setting\n")
        result = self.run_block()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("overriding", result.stderr)
        self.assertNotIn("FAKE-sensitive-setting", result.stderr)

    def test_provisioning_flock_is_not_reacquired_by_the_prestart(self):
        import fcntl
        self.write_properties(b"AbandonOldChart=false\n")
        with (self.root / ".finish-install.lock").open("w") as held:
            fcntl.flock(held, fcntl.LOCK_EX)
            result = self.run_block(lock_held="0")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_service_user_verification_keeps_key_and_home_precedence(self):
        key = b"encryption.util.secret.key=" + base64.b64encode(bytes(range(16))) + b"\n"
        self.properties.write_bytes(key + b"AbandonOldChart=true\n")
        home = self.root / "home.properties"
        home.write_bytes(key + b"other=keep\n")
        command = [str(self.java), "-cp", str(self.classes),
                   "io.github.carlos_emr.carlos.config.ConfigurationPreflightFixture",
                   "verify", str(self.properties), str(home)]
        result = subprocess.run(command, capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("loads after", result.stderr)
        self.assertTrue(home.read_bytes().startswith(key + b"other=keep\n"))
        self.assertNotIn(key.decode().strip(), result.stderr)
        self.assertEqual(self.properties.read_bytes(), key + b"AbandonOldChart=true\n")

    def test_invalid_or_missing_key_refuses_without_creating_one(self):
        for key in (b"", b"encryption.util.secret.key=FAKE-invalid-secret\n"):
            with self.subTest(key=key):
                original = key + b"AbandonOldChart=true\n"
                self.properties.write_bytes(original)
                result = subprocess.run([str(self.java), "-cp", str(self.classes),
                    "io.github.carlos_emr.carlos.config.ConfigurationPreflightFixture",
                    "verify", str(self.properties), str(self.root / "missing-home")],
                    capture_output=True, text=True, check=False)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.properties.read_bytes(), original)
                self.assertNotIn("FAKE-invalid-secret", result.stderr)

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
        self.assertNotIn('if [ "${PROVISION_LOCK_HELD}" = 1 ]', migration_block())
        service = (ROOT / "debian/carlos-emr.carlos-emr.service").read_text()
        self.assertIn("ExecStartPre=+/usr/lib/carlos-emr/carlos-emr-config-preflight reconcile", service)
        self.assertIn("ExecStartPre=/usr/lib/carlos-emr/carlos-emr-config-preflight verify", service)

    def test_fresh_install_skeleton_explicitly_selects_modern_chart(self):
        settings = re.findall(r"^AbandonOldChart=(.*)$", SHIPPED.read_text(), re.M)
        self.assertEqual(settings, ["true"])


if __name__ == "__main__":
    unittest.main()
