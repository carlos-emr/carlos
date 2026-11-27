# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Run on a disposable host with the candidate carlos-emr package installed."""

import grp
import os
import re
import runpy
import shlex
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
    def warning_for(self, installed, *, options="", override=None, home=None, env_text="", setenv=False):
        block = re.search(
            r"^        REPORT_WEB_INF=.*?^        }\n",
            POSTINST.read_text(encoding="utf-8"),
            re.M | re.S,
        )
        self.assertIsNotNone(block, "the report lease warning is missing from postinst")
        with tempfile.TemporaryDirectory(dir="/var/lib/carlos-emr") as directory:
            Path(directory).chmod(0o711)
            properties = Path(directory) / "carlos.properties"
            properties.write_bytes(installed)
            os.chown(properties, 0, grp.getgrnam("carlos").gr_gid)
            properties.chmod(0o640)
            # The installed launcher reads options from this environment file.
            # Paths with spaces also exercise Catalina's nested argument quoting.
            site_file = Path(directory) / "site override.properties"
            if override is not None:
                site_file.write_bytes(override)
                site_file.chmod(0o644)
            literal_file = Path(directory) / "literal.properties"
            literal_file.symlink_to(site_file.name)
            user_home = Path(directory) / "home"
            user_home.mkdir()
            user_home.chmod(0o755)
            if home is not None:
                (user_home / "carlos.properties").write_bytes(home)
                (user_home / "carlos.properties").chmod(0o644)
            flags = options.format(site=shlex.quote(str(site_file)), home=shlex.quote(str(user_home)))
            env_file = Path(directory) / "carlos-emr.env"
            probe = Path(directory) / "read-only-probe"
            probe.touch()
            os.chown(probe, 0, grp.getgrnam("carlos").gr_gid)
            probe.chmod(0o660)
            extra = env_text.replace("{probe}", shlex.quote(str(probe))).replace("{literal}", str(literal_file))
            base = Path(directory) / "catalina"
            (base / "bin").mkdir(parents=True)
            base.chmod(0o755)
            (base / "bin").chmod(0o755)
            tomcat_home = Path(directory) / "tomcat"
            tomcat_home.mkdir()
            tomcat_home.chmod(0o755)
            if setenv:
                hook = base / "bin" / "setenv.sh"
                hook.write_text(f'CATALINA_OPTS="$CATALINA_OPTS -Dcarlos_override_properties={shlex.quote(str(site_file))}"\n')
                hook.chmod(0o644)
            env_file.write_text(f"CATALINA_HOME={tomcat_home}\nCATALINA_BASE={base}\n"
                                + "CARLOS_JAVA_OPTS=" + shlex.quote(flags) + "\n" + extra)
            env_file.chmod(0o644)
            result = subprocess.run(
                ["sh", "-c", "set -e\n" + block.group(0)],
                env={"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "SHARE": str(SHARE),
                     "PROPERTIES": str(properties), "ENV_FILE": str(env_file)},
                capture_output=True, text=True, check=True,
            )
            self.assertEqual(probe.read_bytes(), b"", "configure executed a site environment command")
            self.assertEqual(properties.read_bytes(), installed)
            self.assertEqual(stat.S_IMODE(properties.stat().st_mode), 0o640)
            if override is not None:
                self.assertEqual(site_file.read_bytes(), override)
            return result.stderr

    def test_warns_when_an_upgrade_retains_the_previous_stock_setting(self):
        warning = self.warning_for(
            b"db_remove_abandoned = true\ndb_remove_abandoned_timeout = 15\n"
        )
        self.assertIn(KEY, warning)
        self.assertIn("resolves to 15 seconds", warning)
        self.assertIn("360", warning)
        self.assertIn("sudo carlos-ctl restart", warning)

    def test_accepts_the_shipped_service_environment_and_literal_assignments(self):
        stock = (SHARE / "skel" / "carlos-emr.env").read_text()
        settings = stock + "\nCARLOS_JAVA_OPTS='' # local setting\n"
        self.assertEqual("", self.warning_for(b"db_remove_abandoned_timeout=420\n", env_text=settings))

    def test_preserves_a_site_specific_setting(self):
        self.assertEqual(self.warning_for(b"db_remove_abandoned_timeout = 17\n"), "")

    def test_uses_the_last_selected_file_instead_of_the_managed_file(self):
        flags = "-Dcarlos_override_properties=/missing -Dcarlos_override_properties={site}"
        unsafe = b"db_remove_abandoned_timeout=15\n"
        safe = b"db_remove_abandoned_timeout=420\n"
        self.assertIn("resolves to 15 seconds", self.warning_for(safe, options=flags, override=unsafe))
        self.assertEqual("", self.warning_for(unsafe, options=flags, override=safe))
        self.assertEqual("", self.warning_for(unsafe, options=flags, override=b""))

    def test_uses_the_literal_properties_path_selected_by_the_service_wrapper(self):
        warning = self.warning_for(b"db_remove_abandoned_timeout=420\n", env_text="PROPERTIES={literal}\n",
                                   override=b"db_remove_abandoned_timeout=15\n")
        self.assertIn("resolves to 15 seconds", warning)

    def test_service_home_loads_after_the_selected_override(self):
        flags = "-Duser.home={home} -Dcarlos_override_properties={site}"
        warning = self.warning_for(b"", options=flags, override=b"db_remove_abandoned_timeout=420\n",
                                   home=b"db_remove_abandoned_timeout=15\n")
        self.assertIn("resolves to 15 seconds", warning)

    def test_unreadable_selection_or_indirect_options_warn_without_exposing_values(self):
        for flags in ("-Dcarlos_override_properties={site}", "@/private-marker", "-Duser.dir=/private-marker"):
            with self.subTest(flags=flags):
                warning = self.warning_for(b"db_remove_abandoned_timeout=360\n", options=flags)
                self.assertIn("could not verify", warning)
                self.assertNotIn("private-marker", warning)

    def test_reports_that_a_catalina_startup_hook_can_change_the_selection(self):
        warning = self.warning_for(b"db_remove_abandoned_timeout=360\n", setenv=True,
                                   override=b"db_remove_abandoned_timeout=15\n")
        self.assertIn("could not verify", warning)

    def test_refuses_dynamic_environment_settings_without_executing_them(self):
        warning = self.warning_for(b"db_remove_abandoned_timeout=360\n",
                                   env_text='CARLOS_JAVA_OPTS="$(printf private-marker > {probe})"\n')
        self.assertIn("could not verify", warning)
        self.assertNotIn("private-marker", warning)

    def test_warns_when_a_path_would_be_hidden_in_the_service_mount_namespace(self):
        warning = self.warning_for(b"db_remove_abandoned_timeout=420\n", options="-Duser.home=/tmp")
        self.assertIn("could not verify", warning)

    def test_rejects_site_agents_that_can_change_system_properties(self):
        for option in ("-javaagent:/private-marker.jar", "-agentlib:private-marker", "-agentpath:/private-marker"):
            with self.subTest(option=option):
                warning = self.warning_for(b"db_remove_abandoned_timeout=360\n", options=option)
                self.assertIn("could not verify", warning)
                self.assertNotIn("private-marker", warning)

    def test_rejects_shell_startup_files_and_relative_wrapper_paths(self):
        for setting in ("BASH_ENV=/private-marker", "PROPERTIES=relative.properties"):
            with self.subTest(setting=setting):
                warning = self.warning_for(b"db_remove_abandoned_timeout=360\n", env_text=setting + "\n")
                self.assertIn("could not verify", warning)
                self.assertNotIn("private-marker", warning)

    def test_warns_when_tomcat_can_select_another_java_home_or_launcher(self):
        for key, value in (("JSSE_OPTS", "-Duser.home=/private-marker"),
                           ("CATALINA_LOGGING_CONFIG", "-Duser.home=/private-marker"),
                           ("LOGGING_MANAGER", "-Duser.home=/private-marker"),
                           ("_RUNJAVA", "/private-marker/bin/java"),
                           ("JAVA_HOME", "/private-marker")):
            with self.subTest(key=key):
                warning = self.warning_for(b"db_remove_abandoned_timeout=360\n", env_text=f"{key}={value}\n")
                self.assertIn("could not verify", warning)
                self.assertNotIn("private-marker", warning)

    def test_detects_jvm_options_in_a_real_systemd_drop_in(self):
        # Use a private, never-started unit on the disposable package test host.
        check = runpy.run_path("/usr/lib/carlos-emr/carlos-emr-report-lease-check")["service_environment_is_known"]
        reload_manager = ["/usr/bin/systemctl", "daemon-reload"]
        try:
            with tempfile.NamedTemporaryFile(mode="w", dir="/run/systemd/system", prefix="carlos-lease-test-",
                                             suffix=".service") as unit:
                unit.write("[Service]\nUser=carlos\nGroup=carlos\n"
                           "EnvironmentFile=-/etc/carlos-emr/carlos-emr.env\n"
                           "ExecStart=/usr/lib/carlos-emr/carlos-emr-tomcat run\n")
                unit.flush()
                unit_path = Path(unit.name)
                unit_path.chmod(0o644)
                subprocess.run(reload_manager, check=True)
                self.assertTrue(check(unit_path.name))
                dropin = Path(unit.name + ".d")
                dropin.mkdir()
                conf = dropin / "site.conf"
                try:
                    conf.write_text('[Service]\nEnvironment="JAVA_TOOL_OPTIONS=-Duser.home=/private-marker"\n')
                    subprocess.run(reload_manager, check=True)
                    self.assertFalse(check(unit_path.name))
                finally:
                    conf.unlink(missing_ok=True)
                    dropin.rmdir()
        finally:
            subprocess.run(reload_manager, check=True)

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
