# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Deployment ownership must describe CARLOS, including when it is down."""
import contextlib
import io
import subprocess
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


class TestTomcatListenerStartup(unittest.TestCase):
    """`carlos-ctl check` straight after an install reported "nothing is listening on
    18080" while Tomcat was still initialising, then passed the front-door probe a moment
    later. The listener check now waits for a service that is active but not yet bound."""

    def run_probe(self, emr_running, answers):
        calls = iter(answers)
        sleeps = []
        text = io.StringIO()
        with patch.object(validate, "_listeners", side_effect=lambda port: next(calls)), \
                contextlib.redirect_stdout(text):
            addrs = validate._tomcat_listeners(emr_running, sleep=sleeps.append, attempts=3)
        return addrs, sleeps, text.getvalue()

    def test_a_bound_connector_is_returned_without_waiting(self):
        addrs, sleeps, text = self.run_probe(True, [["127.0.0.1:18080"]])
        self.assertEqual(addrs, ["127.0.0.1:18080"])
        self.assertEqual(sleeps, [])
        self.assertEqual(text, "")

    def test_a_starting_service_is_waited_for_until_it_binds(self):
        addrs, sleeps, text = self.run_probe(True, [[], [], ["127.0.0.1:18080"]])
        self.assertEqual(addrs, ["127.0.0.1:18080"])
        self.assertEqual(sleeps, [10, 10])
        self.assertIn("waiting up to 2 minutes", text)

    def test_a_service_that_never_binds_still_reports_nothing_listening(self):
        addrs, sleeps, _ = self.run_probe(True, [[], [], [], []])
        self.assertEqual(addrs, [])
        self.assertEqual(sleeps, [10, 10, 10])

    def test_a_stopped_service_is_not_waited_for(self):
        addrs, sleeps, text = self.run_probe(False, [[]])
        self.assertEqual(addrs, [])
        self.assertEqual(sleeps, [])
        self.assertEqual(text, "")
