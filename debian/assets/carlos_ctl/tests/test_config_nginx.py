# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""apply_nginx: a reload is proven, never assumed.

`systemctl reload nginx` returns 0 as soon as the signal is delivered; the
master may then fail to bind the new listen sockets (bind(127.0.0.1:80)
against a still-listening 0.0.0.0:80 from the distribution's default site)
and keep serving the OLD configuration. The helper must check the configured
listeners and fall back to a restart."""

import contextlib
import io
import os
import subprocess
import types
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
        assert cmd == ["ss", "-ltnpH"], cmd
        # What ss shows depends on whether nginx has been restarted yet: the
        # first entry is the state after the reload, the second (if any) the
        # state after a restart.
        restarted = ["systemctl", "restart", "nginx.service"] in self.calls
        if restarted and len(self.ss_outputs) > 1:
            return self.ss_outputs[1]
        return self.ss_outputs[0] if self.ss_outputs else ""

    @staticmethod
    def _ss(*addrs, owner="nginx"):
        return "\n".join(f"LISTEN 0 511 {a} 0.0.0.0:* users:((\"{owner}\",pid=1,fd=6))"
                         for a in addrs)

    def _apply(self, bind_ip="127.0.0.1"):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return config.apply_nginx(bind_ip)

    def test_unrelated_processes_cannot_prove_the_front_door(self):
        for owner in ('python3', 'nginx-other'):
            with self.subTest(owner=owner):
                self.calls.clear()
                self.ss_outputs = [self._ss("127.0.0.1:80", "127.0.0.1:443").replace('"nginx"', f'"{owner}"')]
                with self.assertRaises(SystemExit):
                    self._apply()

    def test_missing_socket_ownership_is_not_success(self):
        self.ss_outputs = ["LISTEN 0 511 127.0.0.1:80 0.0.0.0:*\n"
                           "LISTEN 0 511 127.0.0.1:443 0.0.0.0:*"]
        with self.assertRaises(SystemExit):
            self._apply()

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

    def test_site_not_enabled_yet_failed_reload_is_fatal(self):
        self.site_enabled = False
        self.reload_rc = 1
        with self.assertRaises(SystemExit):
            self._apply()
        self.assertNotIn(["systemctl", "restart", "nginx.service"], self.calls)

    def test_site_not_enabled_yet_still_refuses_a_broken_configuration(self):
        # The config test comes first: a rendered configuration that does not
        # parse is reported on a first install too, never skipped.
        self.site_enabled = False
        self.test_rc = 1
        self.assertEqual(self._apply(), 1)
        self.assertNotIn(["systemctl", "reload", "nginx.service"], self.calls)

    def test_listeners_owned_by_another_daemon_do_not_count(self):
        # "something is listening on 80 and 443" is the question that reported
        # a healthy front door on the tester's broken host. A daemon that is
        # not nginx holding both ports must not satisfy the proof: nginx is
        # restarted, and when it still cannot bind, the verb dies.
        self.ss_outputs = [self._ss("127.0.0.1:80", "127.0.0.1:443", owner="haproxy")]
        with self.assertRaises(SystemExit):
            self._apply()
        self.assertIn(["systemctl", "restart", "nginx.service"], self.calls)

    def test_failed_config_test_never_reloads(self):
        self.test_rc = 1
        self.assertEqual(self._apply(), 1)
        self.assertNotIn(["systemctl", "reload", "nginx.service"], self.calls)
        self.assertNotIn(["systemctl", "restart", "nginx.service"], self.calls)

    def test_recovery_starts_inactive_nginx_and_proves_listeners(self):
        self.active_rc = 3
        self.ss_outputs = [self._ss("127.0.0.1:80", "127.0.0.1:443")]
        self.assertEqual(config.apply_nginx("127.0.0.1", start_if_inactive=True), 0)
        self.assertIn(["systemctl", "start", "nginx.service"], self.calls)
        self.assertNotIn(["systemctl", "reload", "nginx.service"], self.calls)

    def test_recovery_never_starts_invalid_configuration(self):
        self.active_rc = 3
        self.test_rc = 1
        self.assertEqual(config.apply_nginx("127.0.0.1", start_if_inactive=True), 1)
        self.assertNotIn(["systemctl", "start", "nginx.service"], self.calls)

    def test_recovery_cannot_complete_without_the_enabled_site(self):
        self.site_enabled = False
        with self.assertRaises(SystemExit):
            config.apply_nginx("127.0.0.1", start_if_inactive=True)
        self.assertNotIn(["systemctl", "start", "nginx.service"], self.calls)

    def test_failed_start_during_recovery_is_fatal(self):
        self.active_rc = 3
        original = self._run
        def run(cmd, **kwargs):
            result = original(cmd, **kwargs)
            return _cp(1) if cmd[:2] == ["systemctl", "start"] else result
        with mock.patch.object(config, "run", side_effect=run), self.assertRaises(SystemExit):
            config.apply_nginx("127.0.0.1", start_if_inactive=True)
        self.assertNotIn(["systemctl", "restart", "nginx.service"], self.calls)

    def test_inactive_nginx_is_left_alone_but_its_configuration_is_tested(self):
        # An operator who stopped nginx keeps it stopped — but a rendered
        # configuration that cannot parse is reported now, not at whatever
        # later start finally trips over it.
        self.active_rc = 3
        self.assertEqual(self._apply(), 0)
        self.assertIn(["nginx", "-t"], self.calls)
        self.assertNotIn(["systemctl", "reload", "nginx.service"], self.calls)
        self.assertNotIn(["systemctl", "start", "nginx.service"], self.calls)

    def test_a_broken_configuration_is_refused_even_while_nginx_is_stopped(self):
        self.active_rc = 3
        self.test_rc = 1
        self.assertEqual(self._apply(), 1)
        self.assertNotIn(["systemctl", "reload", "nginx.service"], self.calls)
        self.assertNotIn(["systemctl", "start", "nginx.service"], self.calls)



class TestListenFragments(unittest.TestCase):
    """`listen ::1:80;` is not valid nginx: an IPv6 literal has to be
    bracketed or the address and port run together and the configuration is
    rejected. CARLOS_BIND_IP holds the bare literal — that is what the
    operator writes and what ss reports — so the brackets belong to the
    directive alone."""

    @staticmethod
    def _listen(bind_ip):
        return config._listen_directive_address(bind_ip)

    def test_an_ipv4_address_is_written_as_given(self):
        self.assertEqual(self._listen("127.0.0.1"), "127.0.0.1")
        self.assertEqual(self._listen("0.0.0.0"), "0.0.0.0")

    def test_an_ipv6_literal_is_bracketed(self):
        self.assertEqual(self._listen("::1"), "[::1]")
        self.assertEqual(self._listen("2001:db8::5"), "[2001:db8::5]")

    def test_an_already_bracketed_literal_is_not_doubled(self):
        self.assertEqual(self._listen("[::1]"), "[::1]")


class TestNginxRendering(unittest.TestCase):
    def render(self, bind_ip, ipv6_available=True):
        # Exercise init-config itself, isolating privileged file/service work.
        # A listener-parser test alone cannot catch invalid rendered syntax.
        with mock.patch.object(config, "env_get", return_value=None):
            settings = config.Settings()
        settings.bind_ip = bind_ip
        rendered = {}
        with contextlib.ExitStack() as stack:
            for owner, name, kwargs in (
                (config, "load", {"return_value": settings}),
                (config.util, "need_root", {}),
                (config, "prop_set", {}),
                (config, "prop_get", {"return_value": None}),
                (config, "prop_comment", {}),
                (config.os, "makedirs", {}),
                (config.os, "chmod", {}),
                (config.os, "chown", {}),
                (config.os.path, "isfile", {"return_value": True}),
                (config.os.path, "exists", {"side_effect": lambda path: path == "/proc/net/if_inet6" and ipv6_available}),
                (config, "_install_proxy_params", {}),
                (config, "_write", {"side_effect": lambda path, value: rendered.update({os.path.basename(path): value})}),
                (config, "run", {"return_value": _cp()}),
                (config, "apply_nginx", {"return_value": 0}),
            ):
                stack.enter_context(mock.patch.object(owner, name, **kwargs))
            stack.enter_context(mock.patch("grp.getgrnam", return_value=types.SimpleNamespace(gr_gid=42)))
            stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
            self.assertEqual(config.cmd_init_config([]), 0)
            config.apply_nginx.assert_called_once_with(bind_ip)
        return rendered

    def test_ipv6_literal_is_bracketed_in_both_rendered_listeners(self):
        rendered = self.render("::1")
        self.assertIn("listen [::1]:80;", rendered["listen-http.conf"])
        self.assertIn("listen [::1]:443 ssl;", rendered["listen-https.conf"])
        self.assertNotIn("listen [::]:", "".join(rendered.values()))

    def test_ipv4_literal_keeps_its_existing_syntax(self):
        rendered = self.render("127.0.0.1")
        self.assertIn("listen 127.0.0.1:80;", rendered["listen-http.conf"])
        self.assertIn("listen 127.0.0.1:443 ssl;", rendered["listen-https.conf"])

    def test_default_only_adds_ipv6_wildcards_when_available(self):
        for available in (True, False):
            with self.subTest(ipv6_available=available):
                rendered = self.render("0.0.0.0", available)
                self.assertIn("listen 0.0.0.0:80;", rendered["listen-http.conf"])
                self.assertEqual("listen [::]:80;" in rendered["listen-http.conf"], available)
                self.assertEqual("listen [::]:443 ssl;" in rendered["listen-https.conf"], available)


if __name__ == "__main__":
    unittest.main()
