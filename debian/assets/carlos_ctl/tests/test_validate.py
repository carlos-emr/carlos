# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Deployment ownership must describe CARLOS, including when it is down."""
import contextlib
import io
import subprocess
import types
import unittest
from unittest.mock import patch
from carlos_ctl import validate

# The IPv4 wildcard as test input: a CARLOS_BIND_IP value. No test binds a socket.
WILDCARD_IPV4 = "0.0.0.0"  # nosec B104


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
        self.assertEqual(found, [[WILDCARD_IPV4], [WILDCARD_IPV4]])

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


class TestCheckSections(unittest.TestCase):
    """cmd_check is a list of sections; what one learns must reach the next."""

    SECTIONS = (
        "_check_installation", "_check_services", "_check_process_ownership",
        "_check_network_exposure", "_check_render_browser", "_check_tls",
        "_check_front_door_responses", "_check_ws_catalog", "_check_ws_auth_gate",
        "_check_path_normalisation", "_check_waf", "_check_drugref", "_check_database",
        "_check_backups",
    )

    def check(self, failing=()):
        settings = types.SimpleNamespace(server_name="emr.example", bind_ip="127.0.0.1",
                                         db_name="carlos")
        calls = []

        def section(name):
            def run_section(*args):
                calls.append((name, args))
                if name in failing:
                    validate._bad(name + " failed")
                return {"_check_services": True,
                        "_check_front_door_responses": ["--resolve", "x"]}.get(name)
            return run_section

        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(validate, "need_root"))
            stack.enter_context(patch.object(validate, "_load_settings", return_value=settings))
            for name in self.SECTIONS:
                stack.enter_context(patch.object(validate, name, side_effect=section(name)))
            stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
            return validate.cmd_check([]), calls, settings

    def test_every_section_runs_once_in_the_documented_order(self):
        _, calls, _ = self.check()
        self.assertEqual([name for name, _ in calls], list(self.SECTIONS))

    def test_later_sections_get_what_earlier_ones_learned(self):
        _, calls, settings = self.check()
        args = dict(calls)
        self.assertEqual(args["_check_drugref"], (True,))
        for name in ("_check_ws_catalog", "_check_ws_auth_gate",
                     "_check_path_normalisation", "_check_waf"):
            self.assertEqual(args[name], (settings, ["--resolve", "x"]), name)

    def test_a_clean_run_exits_zero(self):
        self.assertEqual(self.check()[0], 0)

    def test_a_failure_in_any_section_exits_one(self):
        self.assertEqual(self.check(failing=("_check_backups",))[0], 1)

    def test_failures_from_an_earlier_run_are_not_carried_over(self):
        self.check(failing=("_check_tls",))
        self.assertEqual(self.check()[0], 0)
