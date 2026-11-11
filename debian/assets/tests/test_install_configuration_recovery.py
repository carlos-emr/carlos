# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Execute shipped nginx decisions over isolated operator files and commands."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
POSTINST = ROOT / "debian/carlos-emr.postinst"


def function(path, name):
    return re.search(r"\n" + name + r"\(\) \{.*?\n\}", path.read_text(), re.S).group(0)


class TestDefaultSiteRecovery(unittest.TestCase):
    def run_case(self, target="stock", customized=False, query_fails=False):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            available = root / "available"
            enabled = root / "enabled"
            available.mkdir()
            enabled.mkdir()
            stock = available / "default"
            original = b"# stock distribution site\nlisten [::]:80;\n"
            stock.write_bytes(original if not customized else original + b"# operator changes\n")
            link = enabled / "default"
            custom = available / "custom"
            custom.write_text("# operator site\n")
            if target == "regular":
                link.write_text("# operator regular file\n")
            else:
                link.symlink_to(stock if target == "stock" else custom)
            stub = root / "dpkg-query"
            checksum = hashlib.md5(original, usedforsecurity=False).hexdigest()
            stub.write_text("#!/bin/sh\nprintf ' " + str(stock) + " " + checksum + "\\n'\n"
                            + ("exit 1\n" if query_fails else "exit 0\n"))
            stub.chmod(0o755)
            script = function(POSTINST, "retire_nginx_default_site")
            script = script.replace("/etc/nginx/sites-enabled/default", str(link))
            script = script.replace("/etc/nginx/sites-available/default", str(stock))
            result = subprocess.run(["sh", "-c", script + "\nretire_nginx_default_site"],
                env={**os.environ, "PATH": str(root) + ":" + os.environ["PATH"]},
                capture_output=True, text=True, check=False)
            return result, link.exists(), stock.read_bytes(), custom.read_text()

    def test_only_unchanged_packaged_default_link_is_retired(self):
        result, present, stock, custom = self.run_case()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(present)
        self.assertIn("retired", result.stderr)
        self.assertIn(b"listen [::]:80", stock)
        self.assertEqual(custom, "# operator site\n")

    def test_custom_targets_modified_stock_and_regular_files_survive(self):
        for options in ({"target": "custom"}, {"target": "regular"}, {"customized": True}, {"query_fails": True}):
            with self.subTest(options=options):
                result, present, _, _ = self.run_case(**options)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertTrue(present)

    def test_cleanup_is_outside_the_provisioning_gate_and_precedes_rendering(self):
        source = POSTINST.read_text()
        call = source.index("        if ! retire_nginx_default_site; then")
        self.assertLess(call, source.index('            free_front_door "${2:-}"'))
        self.assertLess(call, source.index("carlos-ctl init-config ||"))


class TestCertificateServiceState(unittest.TestCase):
    def run_case(self, active, valid=True, reload_fails=False):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            calls = root / "calls"
            script = function(ROOT / "debian/assets/bin/carlos-emr-cert", "reload_nginx")
            script = script.replace("/run/systemd/system", str(root))
            preamble = f'''set -e
log() {{ printf '%s\\n' "$*"; }}
warn() {{ printf '%s\\n' "$*" >&2; }}
nginx() {{ return {0 if valid else 1}; }}
systemctl() {{
    printf '%s\\n' "$*" >> "{calls}"
    case "$1" in
        is-active) return {0 if active else 3} ;;
        reload) return {1 if reload_fails else 0} ;;
        *) return 0 ;;
    esac
}}
'''
            result = subprocess.run(["sh", "-c", preamble + script + "\nreload_nginx"],
                                    capture_output=True, text=True, check=False)
            return result, calls.read_text().splitlines() if calls.exists() else []

    def test_renewal_keeps_inactive_nginx_stopped(self):
        result, calls = self.run_case(active=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(any(line.startswith(("start ", "restart ", "reload ")) for line in calls))
        self.assertIn("not running", result.stdout)

    def test_running_nginx_reloads_and_retains_restart_recovery(self):
        for fails in (False, True):
            with self.subTest(reload_fails=fails):
                result, calls = self.run_case(active=True, reload_fails=fails)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("reload nginx.service", calls)
                self.assertEqual("restart nginx.service" in calls, fails)

    def test_invalid_configuration_fails_before_changing_service_state(self):
        result, calls = self.run_case(active=True, valid=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(calls, [])


class TestPublishedConffileCompatibility(unittest.TestCase):
    def test_real_dpkg_upgrade_preserves_both_operator_edited_published_conffiles(self):
        # This owns a private dpkg root/database; it cannot touch host packages.
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            installed = root / "installed"
            database = installed / "var/lib/dpkg"
            database.mkdir(parents=True)
            (database / "status").write_text("")
            names = {"debian/assets/tomcat/Catalina/localhost/carlos.xml": "etc/carlos-emr/tomcat/Catalina/localhost/carlos.xml",
                     "debian/assets/tomcat/logging.properties": "etc/carlos-emr/tomcat/logging.properties"}
            archives = []
            for version in ("1.0", "2.0"):
                staging = root / version
                control = staging / "DEBIAN"; control.mkdir(parents=True); control.chmod(0o755)
                (control / "control").write_text("Package: carlos-conffile-probe\nVersion: " + version
                    + "\nArchitecture: all\nMaintainer: Synthetic <fake@example.invalid>\nDescription: private conffile upgrade probe\n")
                (control / "conffiles").write_text("".join("/" + name + "\n" for name in names.values()))
                for source, target in names.items():
                    destination = staging / target; destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes((ROOT / source).read_bytes())
                archive = root / (version + ".deb")
                subprocess.run(["dpkg-deb", "--build", str(staging), str(archive)], check=True, capture_output=True)
                archives.append(archive)
            command = ["dpkg", "--root=" + str(installed), "--admindir=" + str(database),
                       "--log=" + str(root / "dpkg.log"), "--force-not-root", "--force-bad-path", "--install"]
            subprocess.run(command + [str(archives[0])], input="", text=True, check=True, capture_output=True, timeout=20)
            modified = {}
            for target in names.values():
                path = installed / target
                modified[target] = path.read_bytes() + b"\n# operator retention/tuning canary\n"
                path.write_bytes(modified[target])
            upgraded = subprocess.run(command + [str(archives[1])], input="", text=True, capture_output=True, timeout=20)
            self.assertEqual(upgraded.returncode, 0, upgraded.stdout + upgraded.stderr)
            self.assertNotIn("Configuration file '", upgraded.stdout + upgraded.stderr)
            for target, expected in modified.items(): self.assertEqual((installed / target).read_bytes(), expected)

    def test_matching_debian_stamp_preserves_frozen_archive_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            war = Path(temporary) / "candidate.war"
            with zipfile.ZipFile(war, "w") as archive:
                archive.writestr("WEB-INF/classes/carlos-build.properties", "build.job=standalone\nbuild.number=1\n")
            script = ROOT / "debian/stamp-war-build-identity.sh"
            command = ["sh", str(script), str(war), "carlos-emr-deb", "2026.09.0~snapshot30"]
            subprocess.run(command, check=True, capture_output=True)
            before = war.read_bytes()
            subprocess.run(command, check=True, capture_output=True)
            self.assertEqual(war.read_bytes(), before)

    def test_payloads_keep_published_beta6_and_beta7_conffile_bytes(self):
        # Both published tags have these Git blob identities. Byte equality is
        # what dpkg needs to avoid a merge prompt on locally edited files.
        files = {"debian/assets/tomcat/Catalina/localhost/carlos.xml": "4abfd10eba11b3589a22a7c4222a36da28e3df4a",
                 "debian/assets/tomcat/logging.properties": "6f263af4398fcb72a309926d6773819d91c48c78"}
        for filename, expected in files.items():
            with self.subTest(filename=filename):
                data = (ROOT / filename).read_bytes()
                digest = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data,
                                      usedforsecurity=False).hexdigest()
                self.assertEqual(digest, expected)

    def test_new_snapshot_sorts_after_the_published_development_baseline(self):
        version = re.search(r"^carlos-emr \(([^)]+)\)", (ROOT / "debian/changelog").read_text()).group(1)
        result = subprocess.run(["dpkg", "--compare-versions", version, "gt", "2026.09.0~snapshot29"], check=False)
        self.assertEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
