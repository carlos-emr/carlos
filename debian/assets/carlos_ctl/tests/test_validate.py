# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Deployment ownership must describe CARLOS, including when it is down."""
import contextlib
import io
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
            if command == ["ss", "-ltnpH"]:
                return "\n".join(lines)
            self.fail("front-door probe ran an unexpected command: " + repr(command))
        with patch.object(validate.config.util, "out_checked", side_effect=output):
            return [validate.config._listeners(port) for port in ("80", "443")]

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

    def test_a_probe_that_could_not_run_is_not_an_empty_answer(self):
        # `check` turns this into a FAIL about the probe; an empty list here
        # would instead report a healthy front door as down.
        def failed(command):
            raise validate.config.util.CommandFailed(command, 2)
        with patch.object(validate.config.util, "out_checked", side_effect=failed):
            with self.assertRaises(validate.config.util.CommandFailed):
                validate.config._front_door_missing("127.0.0.1", wait=0)
