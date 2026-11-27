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
    def configure(self, properties, *, previous="1.0", locked=True, extra_env=None):
        block = re.search(
            r"^        # BEGIN REPORT_LEASE_UPGRADE\n.*?^        # END REPORT_LEASE_UPGRADE$",
            POSTINST.read_text(encoding="utf-8"),
            re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(block, "the report lease migration is missing from postinst")
        env = dict(os.environ, SHARE=str(SHARE), STATE=str(properties.parent),
                   PROPERTIES=str(properties), PROVISION_LOCK_HELD=str(int(locked)))
        if extra_env:
            env.update(extra_env)
        return subprocess.run(
            ["/bin/sh", "-ec", block.group(0), "postinst", "configure", previous],
            env=env,
            capture_output=True, text=True, check=True,
        )

    def warning_for(self, installed, *, expected=None):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory).chmod(0o711)
            properties = Path(directory) / "carlos.properties"
            properties.write_bytes(installed)
            gid = grp.getgrnam("carlos").gr_gid
            os.chown(properties, 0, gid)
            properties.chmod(0o640)
            result = self.configure(properties)
            self.assertEqual(properties.read_bytes(), installed if expected is None else expected)
            self.assertEqual(stat.S_IMODE(properties.stat().st_mode), 0o640)
            self.assertEqual((properties.stat().st_uid, properties.stat().st_gid), (0, gid))
            self.assertEqual(list(Path(directory).glob("carlos.properties.*")), [])
            return result.stderr

    def test_upgrade_adopts_shipped_timeout_and_preserves_other_bytes(self):
        before = b"# site settings\r\ndb_remove_abandoned = true\r\n"
        after = b"clinic.name=Cliniq\xfce"
        for old in (b"db_remove_abandoned_timeout = 15\n", b"db_remove_abandoned_timeout=15\r\n",
                    b"  db_remove_abandoned_timeout : 15  \r\n"):
            with self.subTest(old=old):
                self.assertEqual(self.warning_for(before + old + after, expected=before + after), "")

    def test_retries_after_lock_deferral_and_preserves_a_later_opt_back_to_15(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory).chmod(0o711)
            properties = Path(directory) / "carlos.properties"
            old = b"db_remove_abandoned_timeout = 15\n"
            properties.write_bytes(old)
            properties.chmod(0o644)
            self.configure(properties, locked=False)
            self.assertEqual(properties.read_bytes(), old)
            self.configure(properties)
            self.assertEqual(properties.read_bytes(), b"")
            properties.write_bytes(old)
            self.assertIn("resolves to 15 seconds", self.configure(properties).stderr)
            self.assertEqual(properties.read_bytes(), old)

    def test_fresh_install_leaves_a_subsequent_custom_setting_in_place(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory).chmod(0o711)
            properties = Path(directory) / "carlos.properties"
            properties.write_bytes(SHIPPED.read_bytes())
            properties.chmod(0o644)
            self.configure(properties, previous="")
            old = b"db_remove_abandoned_timeout = 15\n"
            properties.write_bytes(old)
            self.assertIn("resolves to 15 seconds", self.configure(properties).stderr)
            self.assertEqual(properties.read_bytes(), old)

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

    def test_keeps_a_stock_looking_continuation_and_duplicate_literals(self):
        stock = b"db_remove_abandoned_timeout = 15\n"
        self.assertEqual(self.warning_for(b"note=continued\\\n" + stock), "")
        self.assertIn("resolves to 15 seconds", self.warning_for(stock + stock))

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

    def test_checks_env_selected_and_jvm_overridden_site_properties_without_marking_migrated(self):
        old = b"db_remove_abandoned_timeout = 15\n"
        for mode in ("env-properties", "jvm-override"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                root.chmod(0o755)
                managed = root / "carlos.properties"
                site = root / "site.properties"
                env_file = root / "carlos-emr.env"
                marker = root / ".report-lease-default-migrated"
                managed.write_bytes(old)
                site.write_bytes(old)
                managed.chmod(0o644)
                site.chmod(0o644)
                if mode == "env-properties":
                    env_file.write_text(f'PROPERTIES="{site}"\n', encoding="utf-8")
                else:
                    env_file.write_text(
                        f'CARLOS_JAVA_OPTS="-Dcarlos_override_properties={site}"\n', encoding="utf-8"
                    )
                env_file.chmod(0o644)

                first = self.configure(managed, extra_env={"ENV_FILE": str(env_file)})
                self.assertIn("resolves to 15 seconds", first.stderr)
                self.assertNotIn("migrated the old 15-second", first.stdout)
                self.assertEqual(managed.read_bytes(), old)
                self.assertEqual(site.read_bytes(), old)
                self.assertFalse(marker.exists())

                second = self.configure(managed, extra_env={"ENV_FILE": str(env_file)})
                self.assertIn("resolves to 15 seconds", second.stderr)
                self.assertFalse(marker.exists())

                site.write_bytes(b"db_remove_abandoned_timeout = 420\n")
                safe = self.configure(managed, extra_env={"ENV_FILE": str(env_file)})
                self.assertEqual(safe.stderr, "")
                self.assertEqual(managed.read_bytes(), old)
                self.assertFalse(marker.exists())

    def test_warns_and_leaves_marker_unset_when_launch_selection_is_unverifiable(self):
        old = b"db_remove_abandoned_timeout = 15\n"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            root.chmod(0o755)
            managed = root / "carlos.properties"
            env_file = root / "carlos-emr.env"
            marker = root / ".report-lease-default-migrated"
            managed.write_bytes(old)
            managed.chmod(0o644)

            # Unverifiable shell command substitution in carlos-emr.env.
            env_file.write_text("PROPERTIES=$(echo /etc/carlos-emr/site.properties)\n", encoding="utf-8")
            env_file.chmod(0o644)
            res = self.configure(managed, extra_env={"ENV_FILE": str(env_file)})
            self.assertIn("could not verify", res.stderr)
            self.assertEqual(managed.read_bytes(), old)
            self.assertFalse(marker.exists())

            # Unverifiable Tomcat setenv.sh.
            env_file.write_text('CARLOS_JAVA_OPTS=""\n', encoding="utf-8")
            catalina_base = root / "catalina"
            (catalina_base / "bin").mkdir(parents=True)
            (catalina_base / "bin" / "setenv.sh").write_text("export CATALINA_OPTS=''\n", encoding="utf-8")
            res = self.configure(managed, extra_env={
                "ENV_FILE": str(env_file),
                "CARLOS_CATALINA_BASE": str(catalina_base),
            })
            self.assertIn("could not verify", res.stderr)
            self.assertEqual(managed.read_bytes(), old)
            self.assertFalse(marker.exists())

            # Unverifiable systemd drop-in overriding CARLOS_JAVA_OPTS.
            systemd_dir = root / "systemd"
            drop_in_dir = systemd_dir / "carlos-emr.service.d"
            drop_in_dir.mkdir(parents=True)
            (drop_in_dir / "override.conf").write_text(
                '[Service]\nEnvironment="CARLOS_JAVA_OPTS=-Dcarlos_override_properties=/tmp/x"\n',
                encoding="utf-8",
            )
            res = self.configure(managed, extra_env={
                "ENV_FILE": str(env_file),
                "CARLOS_SYSTEMD_UNIT_DIRS": str(systemd_dir),
            })
            self.assertIn("could not verify", res.stderr)
            self.assertEqual(managed.read_bytes(), old)
            self.assertFalse(marker.exists())


if __name__ == "__main__":
    unittest.main()
