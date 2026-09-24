# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Deployment ownership must describe CARLOS, including when it is down."""
import contextlib
import io
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from carlos_ctl import validate


class TestProcessOwnership(unittest.TestCase):
    def probe(self, pid, owner=""):
        commands = []
        def output(command):
            commands.append(command)
            if command == ["systemctl", "show", "-p", "MainPID", "--value", "carlos-emr"]:
                return pid
            if command == ["ps", "-o", "user:32=", "-p", pid]:
                return owner
            self.fail("Ownership probe inspected unrelated host processes: " + repr(command))
        validate._failures = 0
        text = io.StringIO()
        with patch.object(validate, "out", side_effect=output), contextlib.redirect_stdout(text):
            validate._check_process_ownership()
        return validate._failures, text.getvalue(), commands

    def test_stopped_service_cannot_pass_on_an_unrelated_jvm(self):
        failures, text, commands = self.probe("0", "tomcat")
        self.assertEqual(failures, 1)
        self.assertIn("not running", text)
        self.assertEqual(len(commands), 1)

    def test_systemd_probe_failure_is_not_success(self):
        self.assertEqual(self.probe("")[0], 1)

    def test_running_carlos_process_passes_without_scanning_other_jvms(self):
        failures, text, commands = self.probe("1234", "carlos")
        self.assertEqual(failures, 0)
        self.assertIn("application JVM runs as: carlos", text)
        self.assertEqual(len(commands), 2)

    def test_root_process_fails(self):
        failures, text, _ = self.probe("1234", "root")
        self.assertEqual(failures, 1)
        self.assertIn("ROOT", text)

    def test_unexpected_untruncated_owner_fails(self):
        failures, text, _ = self.probe("1234", "unexpected-long-service-account")
        self.assertEqual(failures, 1)
        self.assertIn("expected 'carlos'", text)

    def test_process_exiting_during_probe_fails(self):
        failures, text, _ = self.probe("1234")
        self.assertEqual(failures, 1)
        self.assertIn("exited while it was being probed", text)


class TestFrontDoorListeners(unittest.TestCase):
    """The front-door probe reported "nginx is listening on 443" on the very
    host whose front door was down: the master held a half-bound 443 socket no
    worker served, and any listener on that port satisfied the old check. The
    probe now asks whether NGINX is bound at the configured address on BOTH
    ports, so a half-set, a stale wildcard, or another daemon's socket all
    read as the failure they are."""

    def listeners(self, *lines):
        def output(command):
            if command == ["ps", "-C", "nginx", "-o", "pid=,args="]:
                return "1 nginx: worker process"
            if command == ["ss", "-ltnpH"]:
                return "\n".join(lines)
            self.fail("front-door probe ran an unexpected command: " + repr(command))
        with patch.object(validate.config.util, "run", side_effect=lambda cmd, **kw:
                          subprocess.CompletedProcess(cmd, 0, output(cmd), "")):
            return [validate.config._listeners(port) for port in ("80", "443")]

    def test_probe_failure_is_reported_without_exiting_validation(self):
        with patch.object(validate.config, "_front_door_missing",
                          side_effect=validate.config.FrontDoorProbeError("ss failed")), \
                patch.object(validate, "_bad") as bad:
            validate._check_front_door("127.0.0.1")
        bad.assert_called_once_with("cannot verify nginx front-door listeners: ss failed")

    @staticmethod
    def _ss(addr, owner="nginx"):
        return f'LISTEN 0 511 {addr} 0.0.0.0:* users:(("{owner}",pid=1,fd=6))'

    def test_both_configured_ports_are_seen_when_nginx_holds_them(self):
        found = self.listeners(self._ss("127.0.0.1:80"), self._ss("127.0.0.1:443"))
        self.assertEqual(found, [["127.0.0.1"], ["127.0.0.1"]])

    def test_a_half_bound_front_door_shows_the_missing_port(self):
        found = self.listeners(self._ss("127.0.0.1:443"))
        self.assertEqual(found, [[], ["127.0.0.1"]])

    def test_a_stale_wildcard_is_not_the_configured_address(self):
        found = self.listeners(self._ss("0.0.0.0:80"), self._ss("0.0.0.0:443"))
        self.assertEqual(found, [["0.0.0.0"], ["0.0.0.0"]])

    def test_another_daemons_sockets_do_not_count_as_the_front_door(self):
        found = self.listeners(self._ss("127.0.0.1:80", owner="haproxy"),
                               self._ss("127.0.0.1:443", owner="haproxy"))
        self.assertEqual(found, [[], []])

    def test_a_neighbouring_port_is_not_the_front_door(self):
        # Matching the port number as a suffix must not accept port 8443.
        found = self.listeners(self._ss("127.0.0.1:8443"))
        self.assertEqual(found, [[], []])

    def test_an_ipv6_literal_compares_as_the_operator_wrote_it(self):
        found = self.listeners(self._ss("[::1]:80"), self._ss("[::1]:443"))
        self.assertEqual(found, [["::1"], ["::1"]])


class TestStaleRendererPackage(unittest.TestCase):
    """A pre-merge renderer left in config-files state must be reported before a purge."""

    def run_check(self, postrm_text=None, control_path=None):
        with tempfile.TemporaryDirectory() as directory:
            path = control_path
            if postrm_text is not None:
                path = os.path.join(directory, "carlos-emr-eform-renderer.postrm")
                with open(path, "w", encoding="utf-8") as fh:
                    fh.write(postrm_text)
            validate._failures = 0
            text = io.StringIO()
            with patch.object(validate, "out", return_value=path or "") as out, \
                    contextlib.redirect_stdout(text):
                validate._check_stale_renderer_package()
            out.assert_called_once_with(
                ["dpkg-query", "--control-path", "carlos-emr-eform-renderer", "postrm"])
            return validate._failures, text.getvalue()

    def test_legacy_postrm_is_reported_with_the_remediation(self):
        failures, text = self.run_check(
            "#!/bin/sh\ncase \"$1\" in purge) rm -f /etc/carlos-emr/render-browser.env ;; esac\n")
        self.assertEqual(failures, 1)
        self.assertIn("Do not purge it", text)
        self.assertIn("carlos-emr-eform-renderer_<version>_all.deb", text)
        self.assertIn("apt install --reinstall carlos-emr", text)

    def test_transitional_package_without_postrm_passes_silently(self):
        self.assertEqual(self.run_check(), (0, ""))

    def test_unrelated_postrm_passes_silently(self):
        self.assertEqual(self.run_check("#!/bin/sh\nexit 0\n"), (0, ""))

    def test_unreadable_control_path_is_not_a_crash(self):
        self.assertEqual(self.run_check(control_path="/nonexistent/renderer.postrm"), (0, ""))


class TestRenderPayload(unittest.TestCase):
    """Only a complete, provisioned payload goes on to the service-level checks."""

    def classify(self, binaries=("chrome", "chromedriver"), make_dir=True, env=True):
        with tempfile.TemporaryDirectory() as directory:
            chromium = os.path.join(directory, "chromium")
            if make_dir:
                os.mkdir(chromium)
                for name in binaries:
                    path = os.path.join(chromium, name)
                    with open(path, "w", encoding="utf-8") as fh:
                        fh.write("#!/bin/sh\n")
                    os.chmod(path, 0o755)
            render_env = os.path.join(directory, "render-browser.env")
            if env:
                with open(render_env, "w", encoding="utf-8") as fh:
                    fh.write("CARLOS_RENDER_URL_BASE=abc\n")
            validate._failures = 0
            text = io.StringIO()
            with contextlib.redirect_stdout(text):
                proceed = validate._check_render_payload(chromium, render_env)
            return proceed, validate._failures, text.getvalue()

    def test_complete_payload_proceeds_to_service_checks(self):
        self.assertEqual(self.classify()[:2], (True, 0))

    def test_payload_without_token_file_fails(self):
        proceed, failures, text = self.classify(env=False)
        self.assertEqual((proceed, failures), (False, 1))
        self.assertIn("postinst never completed", text)

    def test_partial_payload_is_a_failure_not_a_skip_build(self):
        proceed, failures, text = self.classify(binaries=("chrome",), env=False)
        self.assertEqual((proceed, failures), (False, 1))
        self.assertIn("missing or incomplete", text)

    def test_deleted_payload_with_token_left_behind_fails(self):
        proceed, failures, _ = self.classify(make_dir=False, env=True)
        self.assertEqual((proceed, failures), (False, 1))

    def test_skip_build_is_only_a_note(self):
        proceed, failures, text = self.classify(make_dir=False, env=False)
        self.assertEqual((proceed, failures), (False, 0))
        self.assertIn("SKIP_EFORM_RENDERER", text)
