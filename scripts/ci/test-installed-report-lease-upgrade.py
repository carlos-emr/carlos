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
STATE_BASE = "/var/lib/carlos-emr"


class TestReportLeaseUpgrade(unittest.TestCase):
    def temp_dir(self):
        return tempfile.TemporaryDirectory(dir=STATE_BASE)

    def configure(self, properties, *, previous="1.0", locked=True, extra_env=None, use_host_launch=False):
        block = re.search(
            r"^        # BEGIN REPORT_LEASE_UPGRADE\n.*?^        # END REPORT_LEASE_UPGRADE$",
            POSTINST.read_text(encoding="utf-8"),
            re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(block, "the report lease migration is missing from postinst")
        env = dict(os.environ, SHARE=str(SHARE), STATE=str(properties.parent),
                   PROPERTIES=str(properties), PROVISION_LOCK_HELD=str(int(locked)))
        if not use_host_launch:
            isolated = properties.parent / "_isolated_launch"
            catalina_base = isolated / "catalina-base"
            localhost_dir = catalina_base / "conf" / "Catalina" / "localhost"
            localhost_dir.mkdir(parents=True, exist_ok=True)
            carlos_xml = localhost_dir / "carlos.xml"
            if not carlos_xml.exists():
                carlos_xml.write_text(
                    f'<Context docBase="{SHARE / "webapp" / "carlos"}"/>\n',
                    encoding="utf-8",
                )
            manager_env = isolated / "manager.env"
            if not manager_env.exists():
                manager_env.write_bytes(b"")
            for p in (isolated, catalina_base, catalina_base / "conf",
                      catalina_base / "conf" / "Catalina", localhost_dir):
                p.chmod(0o755)
            carlos_xml.chmod(0o644)
            manager_env.chmod(0o644)
            env["ENV_FILE"] = str(isolated / "carlos-emr.env")
            env["CARLOS_CATALINA_BASE"] = str(catalina_base)
            env["CARLOS_CATALINA_HOME"] = str(isolated / "catalina-home")
            env["CARLOS_SYSTEMD_UNIT_DIRS"] = f"{isolated / 'systemd'}:/usr/lib/systemd/system"
            env["CARLOS_MANAGER_ENV_FILE"] = str(manager_env)
            env["CARLOS_SYSTEMD_MANAGER_CONF_DIRS"] = str(isolated / "systemd-manager")
            env["CARLOS_ETC_ENVIRONMENT"] = ""
            env["CARLOS_ENVIRONMENT_CONF_DIRS"] = str(isolated / "env.d")
        else:
            for key in (
                "ENV_FILE",
                "CARLOS_CATALINA_BASE",
                "CARLOS_CATALINA_HOME",
                "CARLOS_SYSTEMD_UNIT_DIRS",
                "CARLOS_MANAGER_ENV_FILE",
                "CARLOS_MANAGER_ENV_UNAVAILABLE",
                "CARLOS_SYSTEMD_MANAGER_CONF_DIRS",
                "CARLOS_ETC_ENVIRONMENT",
                "CARLOS_ENVIRONMENT_CONF_DIRS",
            ):
                env.pop(key, None)
        if extra_env:
            env.update(extra_env)
        return subprocess.run(
            ["/bin/sh", "-ec", block.group(0), "postinst", "configure", previous],
            env=env,
            capture_output=True, text=True, check=True,
        )

    def warning_for(self, installed, *, expected=None):
        with self.temp_dir() as directory:
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
        with self.temp_dir() as directory:
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
        with self.temp_dir() as directory:
            Path(directory).chmod(0o711)
            properties = Path(directory) / "carlos.properties"
            properties.write_bytes(SHIPPED.read_bytes())
            properties.chmod(0o644)
            self.configure(properties, previous="")
            old = b"db_remove_abandoned_timeout = 15\n"
            properties.write_bytes(old)
            self.assertIn("resolves to 15 seconds", self.configure(properties).stderr)
            self.assertEqual(properties.read_bytes(), old)

    def test_purge_then_reinstall_migrates_retained_stock_15_once(self):
        with self.temp_dir() as directory:
            root = Path(directory)
            root.chmod(0o711)
            properties = root / "carlos.properties"
            marker = root / ".report-lease-default-migrated"
            old = b"db_remove_abandoned_timeout = 15\nclinic.name=Retained\n"
            properties.write_bytes(old)
            properties.chmod(0o644)

            reinstall = self.configure(properties, previous="")
            self.assertEqual(reinstall.stderr, "")
            self.assertIn("migrated the old 15-second", reinstall.stdout)
            self.assertEqual(properties.read_bytes(), b"clinic.name=Retained\n")
            self.assertTrue(marker.exists())

            # A subsequent explicit opt-back to 15 is preserved.
            properties.write_bytes(old)
            later = self.configure(properties, previous="1.0")
            self.assertIn("resolves to 15 seconds", later.stderr)
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
            with self.subTest(mode=mode), self.temp_dir() as directory:
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
        with self.temp_dir() as directory:
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
            (catalina_base / "bin").chmod(0o755)
            catalina_base.chmod(0o755)
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
            systemd_dir.chmod(0o755)
            drop_in_dir.chmod(0o755)
            (drop_in_dir / "override.conf").write_text(
                '[Service]\nEnvironment="CARLOS_JAVA_OPTS=-Dcarlos_override_properties=/tmp/x"\n',
                encoding="utf-8",
            )
            res = self.configure(managed, extra_env={
                "ENV_FILE": str(env_file),
                "CARLOS_SYSTEMD_UNIT_DIRS": f"{systemd_dir}:/usr/lib/systemd/system",
            })
            self.assertIn("could not verify", res.stderr)
            self.assertEqual(managed.read_bytes(), old)
            self.assertFalse(marker.exists())

    def test_active_tomcat_context_docbase_with_15_prevents_migration_and_marker(self):
        old = b"db_remove_abandoned_timeout = 15\n"
        with self.temp_dir() as directory:
            root = Path(directory)
            root.chmod(0o755)
            managed = root / "carlos.properties"
            marker = root / ".report-lease-default-migrated"
            managed.write_bytes(old)
            managed.chmod(0o644)

            custom_webapp = root / "custom-webapp"
            custom_classes = custom_webapp / "WEB-INF" / "classes"
            custom_classes.mkdir(parents=True)
            for p in (custom_webapp, custom_webapp / "WEB-INF", custom_classes):
                p.chmod(0o755)
            (custom_classes / "jdbc-defaults.properties").write_text(
                "db_remove_abandoned_timeout=15\n", encoding="utf-8"
            )
            (custom_classes / "carlos.properties").write_text("", encoding="utf-8")

            catalina_base = root / "catalina-custom"
            localhost_dir = catalina_base / "conf" / "Catalina" / "localhost"
            localhost_dir.mkdir(parents=True)
            for p in (catalina_base, catalina_base / "conf", catalina_base / "conf" / "Catalina", localhost_dir):
                p.chmod(0o755)
            carlos_xml = localhost_dir / "carlos.xml"
            carlos_xml.write_text(f'<Context docBase="{custom_webapp}"/>\n', encoding="utf-8")
            carlos_xml.chmod(0o644)

            res = self.configure(managed, extra_env={"CARLOS_CATALINA_BASE": str(catalina_base)})
            self.assertIn("resolves to 15 seconds", res.stderr)
            self.assertNotIn("migrated the old 15-second", res.stdout)
            self.assertEqual(managed.read_bytes(), old)
            self.assertFalse(marker.exists())

            # Missing carlos.xml fails closed without migrating or writing marker.
            carlos_xml.unlink()
            missing = self.configure(managed, extra_env={"CARLOS_CATALINA_BASE": str(catalina_base)})
            self.assertIn("could not verify", missing.stderr)
            self.assertEqual(managed.read_bytes(), old)
            self.assertFalse(marker.exists())

    def test_private_tmp_home_override_defers_verification_and_leaves_marker_unset(self):
        old = b"db_remove_abandoned_timeout = 15\n"
        with self.temp_dir() as directory, tempfile.TemporaryDirectory(dir="/tmp") as host_tmp:
            root = Path(directory)
            root.chmod(0o755)
            tmp_home = Path(host_tmp)
            tmp_home.chmod(0o755)
            host_only_props = tmp_home / "carlos.properties"
            host_only_props.write_bytes(b"db_remove_abandoned_timeout = 360\n")
            host_only_props.chmod(0o644)

            managed = root / "carlos.properties"
            env_file = root / "carlos-emr.env"
            marker = root / ".report-lease-default-migrated"
            managed.write_bytes(old)
            managed.chmod(0o644)
            env_file.write_text(f'CARLOS_JAVA_OPTS="-Duser.home={tmp_home}"\n', encoding="utf-8")
            env_file.chmod(0o644)

            res = self.configure(managed, extra_env={"ENV_FILE": str(env_file)})
            self.assertIn("could not verify", res.stderr)
            self.assertEqual(managed.read_bytes(), old)
            self.assertFalse(marker.exists())

    def test_systemd_manager_environment_override_prevents_false_ok_and_marker(self):
        with self.temp_dir() as directory:
            root = Path(directory)
            root.chmod(0o755)
            managed = root / "carlos.properties"
            site = root / "site.properties"
            marker = root / ".report-lease-default-migrated"
            managed.write_bytes(SHIPPED.read_bytes())
            site.write_bytes(b"db_remove_abandoned_timeout = 15\n")
            managed.chmod(0o644)
            site.chmod(0o644)

            # 1. Static DefaultEnvironment= in systemd manager configuration.
            mgr_dir = root / "systemd-manager"
            mgr_dropin = mgr_dir / "system.conf.d"
            mgr_dropin.mkdir(parents=True)
            mgr_dir.chmod(0o755)
            mgr_dropin.chmod(0o755)
            (mgr_dropin / "10-java.conf").write_text(
                f'[Manager]\nDefaultEnvironment="_JAVA_OPTIONS=-Dcarlos_override_properties={site}"\n',
                encoding="utf-8",
            )
            res = self.configure(managed, extra_env={"CARLOS_SYSTEMD_MANAGER_CONF_DIRS": str(mgr_dir)})
            self.assertIn("resolves to 15 seconds", res.stderr)
            self.assertFalse(marker.exists())

            # 2. Unavailable manager environment defers verification and marker.
            unavailable = self.configure(managed, extra_env={"CARLOS_MANAGER_ENV_UNAVAILABLE": "true"})
            self.assertIn("could not verify", unavailable.stderr)
            self.assertFalse(marker.exists())

            # 3. Live PID 1 manager environment via systemctl set-environment.
            subprocess.run(
                ["systemctl", "set-environment", f"_JAVA_OPTIONS=-Dcarlos_override_properties={site}"],
                check=True,
            )
            try:
                live = self.configure(managed, use_host_launch=True)
                self.assertIn("resolves to 15 seconds", live.stderr)
                self.assertFalse(marker.exists())
            finally:
                subprocess.run(["systemctl", "unset-environment", "_JAVA_OPTIONS"], check=True)

    def test_fresh_install_with_separate_properties_records_marker_and_preserves_later_custom_15(self):
        with self.temp_dir() as directory:
            root = Path(directory)
            root.chmod(0o755)
            managed = root / "carlos.properties"
            site = root / "site.properties"
            env_file = root / "carlos-emr.env"
            marker = root / ".report-lease-default-migrated"
            managed.write_bytes(SHIPPED.read_bytes())
            site.write_bytes(b"db_remove_abandoned_timeout = 420\n")
            managed.chmod(0o644)
            site.chmod(0o644)
            env_file.write_text(f'PROPERTIES="{site}"\n', encoding="utf-8")
            env_file.chmod(0o644)

            fresh = self.configure(managed, previous="", extra_env={"ENV_FILE": str(env_file)})
            self.assertEqual(fresh.stderr, "")
            self.assertTrue(marker.exists())

            # Later switching back to the managed file with an explicit 15-second setting must not migrate it.
            old = b"db_remove_abandoned_timeout = 15\n"
            managed.write_bytes(old)
            env_file.write_text('CARLOS_JAVA_OPTS=""\n', encoding="utf-8")
            upgrade = self.configure(managed, previous="1.0", extra_env={"ENV_FILE": str(env_file)})
            self.assertIn("resolves to 15 seconds", upgrade.stderr)
            self.assertEqual(managed.read_bytes(), old)

    def test_packaged_host_launch_environment_verifies_cleanly(self):
        with self.temp_dir() as directory:
            root = Path(directory)
            root.chmod(0o711)
            properties = root / "carlos.properties"
            properties.write_bytes(SHIPPED.read_bytes())
            properties.chmod(0o644)
            result = self.configure(properties, previous="", use_host_launch=True)
            self.assertEqual(result.stderr, "")


if __name__ == "__main__":
    unittest.main()
