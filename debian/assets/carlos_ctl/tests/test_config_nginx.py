# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""apply_nginx: a reload is proven, never assumed.

`systemctl reload nginx` returns 0 as soon as the signal is delivered; the
master may then fail to bind the new listen sockets (bind(127.0.0.1:80)
against a still-listening 0.0.0.0:80 from the distribution's default site)
and keep serving the OLD configuration. The helper must check the configured
listeners and fall back to a restart."""

import contextlib
import io
import subprocess
import unittest
from unittest import mock

from carlos_ctl import config


def _cp(rc=0, stdout=""):
    return subprocess.CompletedProcess(args=[], returncode=rc, stdout=stdout, stderr="")


class TestApplyNginx(unittest.TestCase):

    def setUp(self):
        self.calls = []
        # ss output: [after reload, after restart]; a single entry serves both.
        self.ss_outputs = []
        # The CARLOS site is enabled by default here: the interesting cases
        # for the proof all assume nginx has been given this package's site.
        self.site_enabled = True
        patches = [
            mock.patch.object(config.os.path, "isdir", return_value=True),
            mock.patch.object(config.os.path, "exists",
                              side_effect=lambda _p: self.site_enabled),
            mock.patch.object(config.time, "sleep", lambda *_: None),
            mock.patch.object(config.time, "monotonic", side_effect=self._clock),
            mock.patch.object(config, "run", side_effect=self._run),
            mock.patch.object(config.util, "out", side_effect=self._out),
        ]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)
        self._t = 0.0
        self.restart_rc = 0
        self.reload_rc = 0
        self.test_rc = 0
        self.active_rc = 0

    def _clock(self):
        self._t += 1.0  # each poll costs a "second"; 3s budget -> ~3 polls
        return self._t

    def _run(self, cmd, **kw):
        self.calls.append(list(cmd))
        if cmd[:2] == ["systemctl", "is-active"]:
            return _cp(self.active_rc)
        if cmd[:2] == ["nginx", "-t"]:
            return _cp(self.test_rc)
        if cmd[:2] == ["systemctl", "reload"]:
            return _cp(self.reload_rc)
        if cmd[:2] == ["systemctl", "restart"]:
            return _cp(self.restart_rc)
        return _cp(0)

    def _out(self, cmd):
        assert cmd == ["ss", "-ltnH"], cmd
        # What ss shows depends on whether nginx has been restarted yet: the
        # first entry is the state after the reload, the second (if any) the
        # state after a restart.
        restarted = ["systemctl", "restart", "nginx.service"] in self.calls
        if restarted and len(self.ss_outputs) > 1:
            return self.ss_outputs[1]
        return self.ss_outputs[0] if self.ss_outputs else ""

    @staticmethod
    def _ss(*addrs):
        return "\n".join(f"LISTEN 0 511 {a} 0.0.0.0:* users:((\"nginx\",pid=1,fd=6))"
                         for a in addrs)

    def _apply(self, bind_ip="127.0.0.1"):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return config.apply_nginx(bind_ip)

    def test_reload_that_binds_the_listeners_is_enough(self):
        self.ss_outputs = [self._ss("127.0.0.1:80", "127.0.0.1:443")]
        self.assertEqual(self._apply(), 0)
        self.assertIn(["systemctl", "reload", "nginx.service"], self.calls)
        self.assertNotIn(["systemctl", "restart", "nginx.service"], self.calls)

    def test_reload_that_leaves_the_old_wildcard_bound_triggers_a_restart(self):
        # The tester's fresh install: the distro default still holds 0.0.0.0:80,
        # the rendered 127.0.0.1 listeners never bound; after the restart they do.
        self.ss_outputs = [self._ss("0.0.0.0:80"),
                           self._ss("127.0.0.1:80", "127.0.0.1:443")]
        self.assertEqual(self._apply(), 0)
        self.assertIn(["systemctl", "restart", "nginx.service"], self.calls)
        self.assertLess(self.calls.index(["systemctl", "reload", "nginx.service"]),
                        self.calls.index(["systemctl", "restart", "nginx.service"]))

    def test_restart_that_still_does_not_bind_is_fatal(self):
        self.ss_outputs = [self._ss("0.0.0.0:80")]
        with self.assertRaises(SystemExit):
            self._apply()
        self.assertIn(["systemctl", "restart", "nginx.service"], self.calls)

    def test_wildcard_bind_is_matched_as_written(self):
        self.ss_outputs = [self._ss("0.0.0.0:80", "0.0.0.0:443", "[::]:80", "[::]:443")]
        self.assertEqual(self._apply("0.0.0.0"), 0)
        self.assertNotIn(["systemctl", "restart", "nginx.service"], self.calls)

    def test_ipv6_literal_is_matched_without_brackets(self):
        self.ss_outputs = [self._ss("[::1]:80", "[::1]:443")]
        self.assertEqual(self._apply("::1"), 0)

    def test_failed_reload_is_fatal_and_never_restarts(self):
        # A reload that systemd itself reports as failed is not papered over
        # with a restart: the operator sees the failure, and a listener that
        # happens to be bound proves nothing about the configuration served.
        self.reload_rc = 1
        self.ss_outputs = [self._ss("127.0.0.1:80", "127.0.0.1:443")]
        with self.assertRaises(SystemExit):
            self._apply()
        self.assertIn(["systemctl", "reload", "nginx.service"], self.calls)
        self.assertNotIn(["systemctl", "restart", "nginx.service"], self.calls)

    def test_site_not_enabled_yet_reloads_without_demanding_the_listeners(self):
        # postinst runs init-config BEFORE it symlinks the site, so on a first
        # install nginx still serves only the distribution's default. Proving
        # the CARLOS listeners there would restart the default site and fail
        # an install the postinst nginx step then completes, leaving
        # .install-incomplete on a healthy host.
        self.site_enabled = False
        self.ss_outputs = [self._ss("0.0.0.0:80")]
        self.assertEqual(self._apply(), 0)
        self.assertIn(["systemctl", "reload", "nginx.service"], self.calls)
        self.assertNotIn(["systemctl", "restart", "nginx.service"], self.calls)

    def test_site_not_enabled_yet_still_refuses_a_broken_configuration(self):
        # The config test comes first: a rendered configuration that does not
        # parse is reported on a first install too, never skipped.
        self.site_enabled = False
        self.test_rc = 1
        self.assertEqual(self._apply(), 1)
        self.assertNotIn(["systemctl", "reload", "nginx.service"], self.calls)

    def test_failed_config_test_never_reloads(self):
        self.test_rc = 1
        self.assertEqual(self._apply(), 1)
        self.assertNotIn(["systemctl", "reload", "nginx.service"], self.calls)
        self.assertNotIn(["systemctl", "restart", "nginx.service"], self.calls)

    def test_inactive_nginx_is_left_alone(self):
        self.active_rc = 3
        self.assertEqual(self._apply(), 0)
        self.assertEqual([c for c in self.calls if c[0] == "nginx"], [])
        self.assertNotIn(["systemctl", "reload", "nginx.service"], self.calls)


if __name__ == "__main__":
    unittest.main()
