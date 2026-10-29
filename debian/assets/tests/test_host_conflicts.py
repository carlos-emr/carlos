# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""carlos-emr-host-conflicts: name, and where safe stop, what blocks the
front door (TCP 80/443) or MariaDB on a fresh install.

A 2026.08.0-beta2 fresh install on an Ubuntu 26.04 VM met another program
already on 0.0.0.0:80: the distribution's nginx could not start, the postinst
said only "nginx reload failed", and the tester could not tell what held the
port (#5439). These tests run the SHIPPED script over stubbed ss, ps, systemctl,
dpkg-query, kill, journalctl and mariadb, so every decision it makes (stop,
leave alone, explain) is pinned without touching the host. Run (from the
repository root):
    python3 -m unittest discover -s debian/assets/tests
"""

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / "debian" / "assets" / "bin" / "carlos-emr-host-conflicts"
RULES = ROOT / "debian" / "rules"
POSTINST = ROOT / "debian" / "carlos-emr.postinst"
TEMPLATES = ROOT / "debian" / "carlos-emr.templates"

# One stub program for every command the script asks about the host. It reads
# the scenario from state.json, logs each call, and applies the effect of a
# stop (the listeners of a stopped unit or a killed process go away).
STUB = r'''
import json, os, sys
state_path = os.environ["HOST_STUB_STATE"]
with open(state_path) as fh:
    st = json.load(fh)
cmd = sys.argv[1]
args = sys.argv[2:]
with open(os.environ["HOST_STUB_LOG"], "a") as fh:
    fh.write(" ".join([cmd] + args) + "\n")

def save():
    with open(state_path, "w") as fh:
        json.dump(st, fh)

def proc(pid):
    return st.get("procs", {}).get(str(pid), {})

def stop(units):
    dead = {p for p, info in st.get("procs", {}).items() if info.get("unit") in units}
    for l in st["listeners"]:
        l["owners"] = [o for o in l.get("owners", []) if str(o["pid"]) not in dead
                       and not (o["pid"] == 1 and l.get("socket") in units)]
    st["listeners"] = [l for l in st["listeners"] if l["owners"]]
    save()

if cmd == "ss":
    if st.get("ss_fails"):
        sys.exit(1)
    port = args[-1].rsplit(":", 1)[1]
    for l in st["listeners"]:
        if l["addr"].rsplit(":", 1)[1] != port:
            continue
        users = ""
        if l.get("owners"):
            users = " users:(" + ",".join(
                '("%s",pid=%d,fd=6)' % (o["name"], o["pid"]) for o in l["owners"]) + ")"
        print("LISTEN 0      511    %s  0.0.0.0:*%s" % (l["addr"], users))
elif cmd == "ps":
    field = args[args.index("-o") + 1].rstrip("=")
    print(proc(args[args.index("-p") + 1]).get(field, "-" if field == "unit" else ""))
elif cmd == "snap":
    if args[:2] == ["stop", "--disable"]:
        stop(["snap.%s.service" % args[2]])
elif cmd == "systemctl":
    if args[0] in ("disable", "stop"):
        units = [a for a in args[1:] if not a.startswith("-")]
        if any(st.get("units", {}).get(u, {}).get("stop_fails") for u in units):
            sys.exit(1)
        if args[0] == "disable" and any(st.get("units", {}).get(u, {}).get("disable_fails")
                                        for u in units):
            sys.exit(1)
        stop(units)
    elif args[0] == "is-enabled":
        print(st.get("units", {}).get(args[-1], {}).get("enabled_after", "disabled"))
    elif args[0] == "mask":
        if any(st.get("units", {}).get(u, {}).get("mask_fails") for u in args[1:]):
            sys.exit(1)
    elif args[0] == "show":
        prop = args[1].split("=", 1)[1]
        print(st.get("units", {}).get(args[-1], {}).get(prop, ""))
    elif args[0] == "is-active":
        state = st.get("mariadb_state", "active")
        print(state)
        sys.exit(0 if state == "active" else 3)
    elif args[0] == "list-sockets":
        for l in st["listeners"]:
            if l.get("socket"):
                print("%s %s %s" % (l["addr"], l["socket"], l.get("triggers", "")))
elif cmd == "dpkg-query":
    pkg = st.get("dpkg", {}).get(args[-1])
    if not pkg:
        print("dpkg-query: no path found matching pattern " + args[-1], file=sys.stderr)
        sys.exit(1)
    print("%s: %s" % (pkg, args[-1]))
elif cmd == "kill":
    pid = args[-1]
    if st.get("kill_fails"):
        sys.exit(1)
    dead = {pid} | {p for p, info in st.get("procs", {}).items() if info.get("ppid") == pid}
    for l in st["listeners"]:
        l["owners"] = [o for o in l.get("owners", []) if str(o["pid"]) not in dead]
    st["listeners"] = [l for l in st["listeners"] if l["owners"]]
    save()
elif cmd == "journalctl":
    sys.stdout.write(st.get("journal", ""))
elif cmd == "timeout":
    os.execvp(args[1], args[1:])
elif cmd == "mariadb":
    sys.stderr.write(st.get("mariadb_stderr", ""))
    sys.exit(st.get("mariadb_rc", 0))
elif cmd == "sleep":
    pass
elif cmd == "policy-rc.d":
    sys.exit(st.get("policy_rc", 0))
'''

STUBBED = ("ss", "ps", "systemctl", "dpkg-query", "kill", "journalctl",
           "timeout", "mariadb", "sleep", "snap", "policy-rc.d")

APACHE_UNIT = {"FragmentPath": "/usr/lib/systemd/system/apache2.service"}
APACHE_DPKG = {"/usr/lib/systemd/system/apache2.service": "apache2"}


def apache(addr, pid=100):
    return {"addr": addr, "owners": [{"name": "apache2", "pid": pid}]}


class HostConflictsCase(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="carlos-host-conflicts-")
        self.addCleanup(shutil.rmtree, self.tmp)
        self.bin = os.path.join(self.tmp, "bin")
        os.mkdir(self.bin)
        stub = os.path.join(self.tmp, "stub.py")
        with open(stub, "w") as fh:
            fh.write(STUB)
        for name in STUBBED:
            path = os.path.join(self.bin, name)
            with open(path, "w") as fh:
                fh.write('#!/bin/sh\nexec "%s" -I "%s" %s "$@"\n' % (sys.executable, stub, name))
            os.chmod(path, 0o755)
        self.state = os.path.join(self.tmp, "state.json")
        self.log = os.path.join(self.tmp, "calls.log")
        open(self.log, "w").close()

    def run_script(self, scenario, *args):
        with open(self.state, "w") as fh:
            json.dump(scenario, fh)
        env = {"PATH": self.bin + ":/usr/bin:/bin", "HOST_STUB_STATE": self.state,
               "HOST_STUB_LOG": self.log}
        return subprocess.run(["sh", str(SCRIPT)] + list(args), capture_output=True,
                              text=True, env=env, timeout=60)

    def calls(self):
        with open(self.log) as fh:
            return fh.read().splitlines()

    def stops(self):
        return [c for c in self.calls() if c.startswith(
            ("systemctl disable", "systemctl stop", "systemctl mask", "kill ", "snap stop"))]


class TestFrontDoor(HostConflictsCase):

    def test_packaged_web_server_is_stopped_disabled_and_reported(self):
        r = self.run_script({
            "listeners": [apache("*:80"), apache("*:443", pid=101)],
            "procs": {"100": {"unit": "apache2.service"}, "101": {"unit": "apache2.service"}},
            "units": {"apache2.service": APACHE_UNIT}, "dpkg": APACHE_DPKG,
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(self.stops(), ["systemctl disable --now apache2.service"])
        lines = r.stdout.splitlines()
        self.assertEqual(len(lines), 1, r.stdout)
        self.assertTrue(lines[0].startswith("stopped: apache2.service (package apache2)"), lines[0])
        self.assertIn("*:80, *:443", lines[0])
        # The operator is told how to get it back, not only that it is gone.
        self.assertIn("sudo systemctl enable --now apache2.service", lines[0])

    def test_report_names_the_holder_and_changes_nothing(self):
        r = self.run_script({
            "listeners": [apache("0.0.0.0:80")],
            "procs": {"100": {"unit": "apache2.service"}},
            "units": {"apache2.service": APACHE_UNIT}, "dpkg": APACHE_DPKG,
        }, "front-door", "--report", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertEqual(self.stops(), [])
        self.assertIn("blocked: apache2 (pid 100, apache2.service) is listening on 0.0.0.0:80",
                      r.stdout)
        self.assertIn("sudo systemctl disable --now apache2.service", r.stdout)

    def test_nginx_service_own_listeners_are_not_a_conflict(self):
        r = self.run_script({
            "listeners": [{"addr": "0.0.0.0:80", "owners": [{"name": "nginx", "pid": 300},
                                                            {"name": "nginx", "pid": 301}]}],
            "procs": {"300": {"unit": "nginx.service"}, "301": {"unit": "nginx.service"}},
        }, "front-door", "0.0.0.0")
        self.assertEqual((r.returncode, r.stdout), (0, ""))
        self.assertEqual(self.stops(), [])

    def test_container_engine_is_never_stopped_even_when_packaged(self):
        r = self.run_script({
            "listeners": [{"addr": "0.0.0.0:80", "owners": [{"name": "docker-proxy", "pid": 400}]}],
            "procs": {"400": {"unit": "docker.service"}},
            "units": {"docker.service": {"FragmentPath": "/usr/lib/systemd/system/docker.service"}},
            "dpkg": {"/usr/lib/systemd/system/docker.service": "docker.io"},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertEqual(self.stops(), [])
        self.assertIn("a Docker container publishes this port", r.stdout)

    def test_service_no_package_ships_is_stopped_too(self):
        # Whatever installed it, a system service on 80/443 keeps nginx down.
        r = self.run_script({
            "listeners": [{"addr": "0.0.0.0:80", "owners": [{"name": "myweb", "pid": 500}]}],
            "procs": {"500": {"unit": "myweb.service"}},
            "units": {"myweb.service": {"FragmentPath": "/etc/systemd/system/myweb.service"}},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertEqual(self.stops(), ["systemctl disable --now myweb.service"])
        self.assertIn("stopped: myweb.service (unit file /etc/systemd/system/myweb.service)",
                      r.stdout)

    def test_snap_service_is_stopped_through_snapd(self):
        # Ubuntu Server's installer offers the Nextcloud snap, which serves
        # 80/443. systemctl disable would be undone by the next snap refresh.
        r = self.run_script({
            "listeners": [{"addr": "*:80", "owners": [{"name": "httpd", "pid": 800}]},
                          {"addr": "*:443", "owners": [{"name": "httpd", "pid": 800}]}],
            "procs": {"800": {"unit": "snap.nextcloud.apache.service"}},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(self.stops(), ["snap stop --disable nextcloud.apache"])
        self.assertIn("stopped: the nextcloud snap's nextcloud.apache service was listening "
                      "on *:80, *:443", r.stdout)
        self.assertIn("sudo snap start --enable nextcloud.apache", r.stdout)

    def test_container_engine_snap_is_never_stopped(self):
        r = self.run_script({
            "listeners": [{"addr": "0.0.0.0:80", "owners": [{"name": "docker-proxy", "pid": 810}]}],
            "procs": {"810": {"unit": "snap.docker.dockerd.service"}},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertEqual(self.stops(), [])
        self.assertIn("Docker container", r.stdout)

    def test_service_that_cannot_be_disabled_is_masked(self):
        # A static unit has no [Install] section: disable leaves it able to
        # start at the next boot and take port 80 back from nginx.
        r = self.run_script({
            "listeners": [apache("*:80")],
            "procs": {"100": {"unit": "apache2.service"}},
            "units": {"apache2.service": dict(APACHE_UNIT, enabled_after="static")},
            "dpkg": APACHE_DPKG,
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertEqual(self.stops(), ["systemctl disable --now apache2.service",
                                        "systemctl mask apache2.service"])
        self.assertIn("it cannot be disabled, so it is stopped and masked", r.stdout)
        self.assertIn("sudo systemctl unmask apache2.service", r.stdout)

    def test_unit_whose_disable_fails_is_still_stopped(self):
        r = self.run_script({
            "listeners": [apache("*:80")],
            "procs": {"100": {"unit": "apache2.service"}},
            "units": {"apache2.service": dict(APACHE_UNIT, disable_fails=True,
                                              enabled_after="enabled", mask_fails=True)},
            "dpkg": APACHE_DPKG,
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertIn("systemctl stop apache2.service", self.stops())
        self.assertIn("could not be disabled or masked and may take the port again", r.stdout)

    def test_protected_unit_is_named_with_move_advice(self):
        r = self.run_script({
            "listeners": [{"addr": "0.0.0.0:443", "owners": [{"name": "mariadbd", "pid": 900}]}],
            "procs": {"900": {"unit": "mariadb.service"}},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertEqual(self.stops(), [])
        self.assertIn("the installer never stops mariadb.service; move it to other ports", r.stdout)

    def test_listener_on_another_address_does_not_collide_with_a_lan_bind(self):
        r = self.run_script({
            "listeners": [apache("127.0.0.1:80")],
            "procs": {"100": {"unit": "apache2.service"}},
            "units": {"apache2.service": APACHE_UNIT}, "dpkg": APACHE_DPKG,
        }, "front-door", "192.168.1.10")
        self.assertEqual((r.returncode, r.stdout), (0, ""))
        self.assertEqual(self.stops(), [])

    def test_wildcard_listener_collides_with_a_lan_bind(self):
        r = self.run_script({
            "listeners": [apache("0.0.0.0:443")],
            "procs": {"100": {"unit": "apache2.service"}},
            "units": {"apache2.service": APACHE_UNIT}, "dpkg": APACHE_DPKG,
        }, "front-door", "192.168.1.10")
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertEqual(self.stops(), ["systemctl disable --now apache2.service"])

    def test_nginx_started_by_hand_is_named_never_signalled(self):
        # A process is not a service: it may serve other sites, and a PID read
        # a moment earlier is not a safe handle to signal.
        r = self.run_script({
            "listeners": [{"addr": "0.0.0.0:80", "owners": [{"name": "nginx", "pid": 200},
                                                            {"name": "nginx", "pid": 201}]}],
            "procs": {
                "200": {"unit": "session-3.scope", "args": "nginx: master process nginx", "ppid": "1"},
                "201": {"unit": "session-3.scope", "args": "nginx: worker process", "ppid": "200"},
            },
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertEqual(self.stops(), [])
        self.assertIn("blocked: nginx (pid 200, session-3.scope) is listening on 0.0.0.0:80", r.stdout)
        self.assertIn("started by hand", r.stdout)

    def test_programs_from_a_desktop_are_hand_started(self):
        for unit in ("user@1000.service", "app-gnome-org.gnome.Terminal-4242.scope"):
            with self.subTest(unit=unit):
                r = self.run_script({
                    "listeners": [{"addr": "0.0.0.0:80", "owners": [{"name": "nginx", "pid": 210}]}],
                    "procs": {"210": {"unit": unit, "args": "nginx: master process nginx"}},
                }, "front-door", "0.0.0.0")
                self.assertEqual(r.returncode, 1)
                self.assertEqual(self.stops(), [])
                self.assertIn("started by hand, not by a system service", r.stdout)
                self.assertNotIn("systemctl disable", r.stdout)

    def test_nginx_inside_a_container_is_never_killed(self):
        # A container with host networking shows its own nginx on the host's
        # port 80, in the container's scope; that is not a stray to kill.
        r = self.run_script({
            "listeners": [{"addr": "0.0.0.0:80", "owners": [{"name": "nginx", "pid": 220}]}],
            "procs": {"220": {"unit": "docker-0123abcd.scope", "args": "nginx: master process nginx"}},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertEqual(self.stops(), [])
        self.assertIn("Docker container", r.stdout)

    def test_program_from_a_desktop_terminal_is_not_given_systemctl_advice(self):
        r = self.run_script({
            "listeners": [{"addr": "0.0.0.0:80", "owners": [{"name": "node", "pid": 610}]}],
            "procs": {"610": {"unit": "user@1000.service"}},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertEqual(self.stops(), [])
        self.assertIn("started by hand", r.stdout)
        self.assertNotIn("systemctl disable", r.stdout)

    def test_program_from_a_login_session_is_named_not_killed(self):
        r = self.run_script({
            "listeners": [{"addr": "0.0.0.0:80", "owners": [{"name": "python3", "pid": 600}]}],
            "procs": {"600": {"unit": "session-2.scope"}},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertEqual(self.stops(), [])
        self.assertIn("python3 (pid 600, session-2.scope) is listening on 0.0.0.0:80", r.stdout)
        self.assertIn("started by hand", r.stdout)

    def test_a_stop_that_fails_is_reported_as_blocked(self):
        unit = dict(APACHE_UNIT, stop_fails=True)
        r = self.run_script({
            "listeners": [apache("*:80")],
            "procs": {"100": {"unit": "apache2.service"}},
            "units": {"apache2.service": unit}, "dpkg": APACHE_DPKG,
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertIn("blocked: apache2 (pid 100, apache2.service)", r.stdout)
        self.assertNotIn("stopped:", r.stdout)

    def test_socket_activated_service_stops_its_socket_and_service(self):
        r = self.run_script({
            "listeners": [{"addr": "[::]:80", "owners": [{"name": "systemd", "pid": 1}],
                           "socket": "lighttpd.socket", "triggers": "lighttpd.service"}],
            "units": {"lighttpd.socket": {"FragmentPath": "/usr/lib/systemd/system/lighttpd.socket",
                                          "Triggers": "lighttpd.service"}},
            "dpkg": {"/usr/lib/systemd/system/lighttpd.socket": "lighttpd"},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(self.stops(),
                         ["systemctl disable --now lighttpd.socket lighttpd.service"])

    def test_socket_and_the_service_it_started_are_handled_once(self):
        # ss names systemd (the socket) AND the service process it handed the
        # socket to as owners of the same listener.
        scenario = {
            "listeners": [{"addr": "[::]:80", "socket": "lighttpd.socket",
                           "triggers": "lighttpd.service",
                           "owners": [{"name": "systemd", "pid": 1},
                                      {"name": "lighttpd", "pid": 300}]}],
            "procs": {"300": {"unit": "lighttpd.service"}},
            "units": {"lighttpd.socket": {"FragmentPath": "/usr/lib/systemd/system/lighttpd.socket",
                                          "Triggers": "lighttpd.service"},
                      "lighttpd.service": {"FragmentPath": "/usr/lib/systemd/system/lighttpd.service"}},
            "dpkg": {"/usr/lib/systemd/system/lighttpd.socket": "lighttpd",
                     "/usr/lib/systemd/system/lighttpd.service": "lighttpd"},
        }
        plan = self.run_script(scenario, "front-door", "--plan", "0.0.0.0")
        self.assertEqual(plan.stdout.count("will-stop:"), 1, plan.stdout)
        self.assertNotIn("blocked:", plan.stdout)
        r = self.run_script(scenario, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(self.stops(),
                         ["systemctl disable --now lighttpd.socket lighttpd.service"])
        self.assertEqual(r.stdout.count("stopped:"), 1, r.stdout)

    def test_usrmerge_unit_registered_under_lib_is_recognised(self):
        r = self.run_script({
            "listeners": [apache("*:80")],
            "procs": {"100": {"unit": "apache2.service"}},
            "units": {"apache2.service": APACHE_UNIT},
            "dpkg": {"/lib/systemd/system/apache2.service": "apache2"},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertEqual(self.stops(), ["systemctl disable --now apache2.service"])

    def test_unreadable_listeners_exit_2(self):
        r = self.run_script({"listeners": [], "ss_fails": True}, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 2)
        self.assertEqual(self.stops(), [])

    def test_plan_lists_what_would_stop_and_what_would_be_left(self):
        r = self.run_script({
            "listeners": [apache("*:80"),
                          {"addr": "0.0.0.0:443", "owners": [{"name": "docker-proxy", "pid": 400}]}],
            "procs": {"100": {"unit": "apache2.service"}, "400": {"unit": "docker.service"}},
            "units": {"apache2.service": APACHE_UNIT}, "dpkg": APACHE_DPKG,
        }, "front-door", "--plan", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertEqual(self.stops(), [], "--plan must change nothing")
        lines = r.stdout.splitlines()
        self.assertEqual(lines[0], "will-stop: apache2.service (package apache2), on *:80")
        self.assertEqual(len(lines), 2, r.stdout)
        self.assertTrue(lines[1].startswith("blocked: docker-proxy (pid 400, docker.service)"))

    def test_plan_lists_snaps_and_leaves_hand_started_nginx_blocked(self):
        r = self.run_script({
            "listeners": [{"addr": "*:443", "owners": [{"name": "httpd", "pid": 800}]},
                          {"addr": "0.0.0.0:80", "owners": [{"name": "nginx", "pid": 200}]}],
            "procs": {"800": {"unit": "snap.nextcloud.apache.service"},
                      "200": {"unit": "session-3.scope", "args": "nginx: master process nginx"}},
        }, "front-door", "--plan", "0.0.0.0")
        self.assertEqual(self.stops(), [])
        self.assertIn("will-stop: the nextcloud snap's nextcloud.apache service, on *:443", r.stdout)
        self.assertIn("blocked: nginx (pid 200, session-3.scope)", r.stdout)
        self.assertNotIn("will-stop: an nginx", r.stdout)

    def test_policy_rc_d_that_forbids_stopping_is_obeyed(self):
        # deb-systemd-invoke asks policy-rc.d before touching a service; an
        # image build or a staged install that forbids it must not lose a site.
        for code in (101, 106):
            with self.subTest(code=code):
                scenario = {
                    "listeners": [apache("*:80")],
                    "procs": {"100": {"unit": "apache2.service"}},
                    "units": {"apache2.service": APACHE_UNIT}, "dpkg": APACHE_DPKG,
                    "policy_rc": code,
                }
                plan = self.run_script(scenario, "front-door", "--plan", "0.0.0.0")
                self.assertNotIn("will-stop:", plan.stdout)
                r = self.run_script(scenario, "front-door", "0.0.0.0")
                self.assertEqual(r.returncode, 1)
                self.assertEqual(self.stops(), [])
                self.assertIn("policy-rc.d forbids stopping services during this install", r.stdout)
                self.assertIn("policy-rc.d apache2 stop", self.calls())

    def test_policy_rc_d_that_allows_stopping_does_not_get_in_the_way(self):
        r = self.run_script({
            "listeners": [apache("*:80")],
            "procs": {"100": {"unit": "apache2.service"}},
            "units": {"apache2.service": APACHE_UNIT}, "dpkg": APACHE_DPKG,
            "policy_rc": 0,
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertEqual(self.stops(), ["systemctl disable --now apache2.service"])

    def test_socket_that_starts_a_protected_service_is_left_alone(self):
        r = self.run_script({
            "listeners": [{"addr": "[::]:80", "owners": [{"name": "systemd", "pid": 1}],
                           "socket": "odd.socket", "triggers": "docker.service"}],
            "units": {"odd.socket": {"FragmentPath": "/usr/lib/systemd/system/odd.socket",
                                     "Triggers": "docker.service"}},
            "dpkg": {"/usr/lib/systemd/system/odd.socket": "odd"},
        }, "front-door", "0.0.0.0")
        self.assertEqual(r.returncode, 1)
        self.assertEqual(self.stops(), [])
        self.assertIn("blocked:", r.stdout)

    def test_free_ports_print_nothing(self):
        r = self.run_script({"listeners": []}, "front-door", "0.0.0.0")
        self.assertEqual((r.returncode, r.stdout, r.stderr), (0, "", ""))


class TestMariadbCause(HostConflictsCase):

    def test_failed_server_names_what_holds_3306(self):
        r = self.run_script({
            "mariadb_state": "failed",
            "listeners": [{"addr": "127.0.0.1:3306", "owners": [{"name": "docker-proxy", "pid": 700}]}],
            "procs": {"700": {"unit": "docker.service"}},
        }, "mariadb")
        self.assertEqual(r.returncode, 0)
        self.assertIn("cause: mariadb.service is failed because port 3306 is taken: docker-proxy",
                      r.stdout)
        self.assertIn("Docker container", r.stdout)
        # Read-only: a database port is never freed from here.
        self.assertEqual(self.stops(), [])

    def test_failed_server_quotes_its_last_error(self):
        r = self.run_script({
            "mariadb_state": "failed", "listeners": [],
            "journal": "2026-10-29 10:39:21 0 [Note] Starting\n"
                       "2026-10-29 10:39:21 0 [ERROR] InnoDB: Unsupported redo log format\n",
        }, "mariadb")
        self.assertIn("cause: mariadb.service is failed (its last error: [ERROR] InnoDB: "
                      "Unsupported redo log format)", r.stdout)

    def test_root_with_a_password_is_explained(self):
        r = self.run_script({
            "mariadb_state": "active", "listeners": [], "mariadb_rc": 1,
            "mariadb_stderr": "ERROR 1045 (28000): Access denied for user 'root'@'localhost' "
                              "(using password: NO)\n",
        }, "mariadb")
        self.assertIn("root@localhost account was given a password", r.stdout)
        self.assertIn("MariaDB root access", r.stdout)

    def test_locked_root_is_not_called_a_password(self):
        r = self.run_script({
            "mariadb_state": "active", "listeners": [], "mariadb_rc": 1,
            "mariadb_stderr": "ERROR 4151 (HY000): Access denied, this account is locked\n",
        }, "mariadb")
        self.assertIn("root@localhost account is locked", r.stdout)
        self.assertNotIn("given a password", r.stdout)

    def test_other_access_denied_quotes_the_error(self):
        r = self.run_script({
            "mariadb_state": "active", "listeners": [], "mariadb_rc": 1,
            "mariadb_stderr": "ERROR 1698 (28000): Access denied for user 'root'@'localhost'\n",
        }, "mariadb")
        self.assertIn("refuses the system root user over the unix socket (ERROR 1698", r.stdout)
        self.assertNotIn("given a password", r.stdout)

    def test_server_that_answers_now_was_merely_late(self):
        r = self.run_script({"mariadb_state": "active", "listeners": []}, "mariadb")
        self.assertIn("answers root over the unix socket now", r.stdout)

    def test_still_starting(self):
        r = self.run_script({"mariadb_state": "activating", "listeners": []}, "mariadb")
        self.assertIn("still starting", r.stdout)

    def test_error_text_is_printed_verbatim(self):
        # dash's echo would turn \c into "stop printing" and \n into a newline.
        r = self.run_script({
            "mariadb_state": "active", "listeners": [], "mariadb_rc": 1,
            "mariadb_stderr": "ERROR 1105 (HY000): odd \\c path \\n here\n",
        }, "mariadb")
        self.assertIn("cause: MariaDB refused root: ERROR 1105 (HY000): odd \\c path \\n here",
                      r.stdout)


class TestShippedAndWired(unittest.TestCase):

    def test_shipped_file_is_executable_posix_sh(self):
        self.assertTrue(os.access(str(SCRIPT), os.X_OK))
        self.assertEqual(SCRIPT.read_text().splitlines()[0], "#!/bin/sh")
        if shutil.which("dash"):
            subprocess.run(["dash", "-n", str(SCRIPT)], check=True)

    def test_rules_install_it_beside_the_other_helpers(self):
        self.assertIn("install -m 0755 debian/assets/bin/carlos-emr-host-conflicts "
                      "$(STAGE)/$(LIBDIR)/carlos-emr-host-conflicts", RULES.read_text())

    def test_postinst_frees_the_front_door_before_init_config(self):
        text = POSTINST.read_text()
        gate = text.index('        if [ "${PROVISION_LOCK_HELD}" = 1 ]; then\n'
                          '            free_front_door "${2:-}"\n')
        self.assertLess(gate, text.index("carlos-ctl init-config ||"))

    def test_postinst_names_the_cause_when_mariadb_is_unreachable(self):
        text = POSTINST.read_text()
        branch = text[text.index("could not reach MariaDB as root over the unix socket"):]
        branch = branch[:branch.index("clear_incomplete")]
        self.assertIn("host_conflicts mariadb", text)
        self.assertIn('mark_incomplete "MariaDB was not reachable while the package was configured" '
                      '"${DB_CAUSE}"', branch)

    def test_marker_keeps_every_cause_of_the_run(self):
        # The beta2 run failed MariaDB first and nginx second; the marker kept
        # only the nginx line, and `carlos-ctl check` reported the lesser one.
        fn = re.search(r"\nmark_incomplete\(\) \{.*?\n\}", POSTINST.read_text(), re.S).group(0)
        with tempfile.TemporaryDirectory() as tmp:
            script = (f'STATE="{tmp}"\nINCOMPLETE_MARKER="$STATE/.install-incomplete"\n'
                      f'INCOMPLETE_REASONS=""\n{fn}\n'
                      'mark_incomplete "MariaDB was not reachable" "mariadb.service is failed"\n'
                      'mark_incomplete "nginx could not start" "apache2 holds *:80"\n'
                      'mark_incomplete "nginx could not start" "apache2 holds *:80"\n'
                      'mark_incomplete "the schema migration failed"\n')
            r = subprocess.run(["sh", "-c", script], capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stderr)
            body = Path(tmp, ".install-incomplete").read_text()
        self.assertIn("reason=MariaDB was not reachable (mariadb.service is failed); "
                      "nginx could not start (apache2 holds *:80); "
                      "the schema migration failed\n", body)
        self.assertEqual(body.count("reason="), 1)

    def test_marker_dedupe_is_literal_not_a_glob(self):
        fn = re.search(r"\nmark_incomplete\(\) \{.*?\n\}", POSTINST.read_text(), re.S).group(0)
        with tempfile.TemporaryDirectory() as tmp:
            script = (f'STATE="{tmp}"\nINCOMPLETE_MARKER="$STATE/.install-incomplete"\n'
                      f'INCOMPLETE_REASONS=""\n{fn}\n'
                      'mark_incomplete "nginx could not start" "apache2 holds *:80"\n'
                      'mark_incomplete "nginx could not start" "* holds *:80"\n')
            r = subprocess.run(["sh", "-c", script], capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stderr)
            body = Path(tmp, ".install-incomplete").read_text()
        self.assertIn("reason=nginx could not start (apache2 holds *:80); "
                      "nginx could not start (* holds *:80)\n", body)

    def test_the_error_dialog_lists_the_recorded_causes(self):
        self.assertIn("${reasons}", TEMPLATES.read_text())
        self.assertIn("db_subst carlos-emr/install-incomplete reasons", POSTINST.read_text())

    def test_a_note_says_which_services_were_stopped(self):
        templates = TEMPLATES.read_text()
        self.assertIn("Template: carlos-emr/stopped-port-conflicts\nType: note\n", templates)
        self.assertIn("${services}", templates)
        self.assertIn("db_input high carlos-emr/stopped-port-conflicts", POSTINST.read_text())


class TestPostinstConfirmation(unittest.TestCase):
    """free_front_door asks before stopping anything when debconf can ask, and
    goes ahead -- logged -- when it cannot (a non-interactive install takes
    the question's default, true)."""

    PLAN = "will-stop: apache2.service (package apache2), on *:80"
    STOPPED = ("stopped: apache2.service (package apache2) was listening on *:80, which the "
               "CARLOS front door needs; it is stopped and disabled but still installed")

    def run_block(self, answer, plan=True, shown=True, old_version="", nginx_state="failed"):
        text = POSTINST.read_text()
        fns = "\n".join(re.search(r"\n%s\(\) \{.*?\n\}" % name, text, re.S).group(0)
                        for name in ("join_lines", "log_action", "free_front_door"))
        with tempfile.TemporaryDirectory() as tmp:
            systemd = os.path.join(tmp, "systemd")
            os.mkdir(systemd)
            log = os.path.join(tmp, "calls")
            bindir = os.path.join(tmp, "bin")
            os.mkdir(bindir)
            with open(os.path.join(bindir, "logger"), "w") as fh:
                fh.write('#!/bin/sh\necho "logger $*" >> "%s"\n' % log)
            os.chmod(os.path.join(bindir, "logger"), 0o755)
            plan_out = self.PLAN if plan else ""
            script = f"""set -e
PATH="{bindir}:$PATH"
call() {{ echo "$*" >> "{log}"; }}
configured_bind_ip() {{ echo 0.0.0.0; }}
host_conflicts() {{
    call "host_conflicts $*"
    case "$2" in
        --plan) [ -z "{plan_out}" ] || echo "{plan_out}" ;;
        *) echo "{self.STOPPED}" ;;
    esac
}}
db_subst() {{ call "db_subst $*"; }}
db_fset() {{ call "db_fset $*"; }}
db_input() {{ call "db_input $*"; [ {1 if shown else 0} = 1 ] || return 30; }}
db_go() {{ call db_go; }}
db_get() {{ call "db_get $*"; RET="{answer}"; }}
systemctl() {{ [ "$1" = is-active ] || return 1; echo {nginx_state}; [ {nginx_state} = active ]; }}
{fns.replace("/run/systemd/system", systemd)}
free_front_door "{old_version}"
printf 'FREED=%s\n' "$FRONT_DOOR_FREED"
"""
            r = subprocess.run(["sh", "-c", script], capture_output=True, text=True)
            calls = Path(log).read_text().splitlines() if os.path.exists(log) else []
        self.assertEqual(r.returncode, 0, r.stderr)
        return r, calls

    def test_interactive_yes_stops_logs_and_reports(self):
        r, calls = self.run_block("true")
        self.assertIn("db_input high carlos-emr/stop-port-conflicts", calls)
        # The question lists exactly what would be stopped.
        self.assertIn("db_subst carlos-emr/stop-port-conflicts services "
                      "apache2.service (package apache2), on *:80", calls)
        self.assertIn("db_fset carlos-emr/stop-port-conflicts seen false", calls)
        self.assertLess(calls.index("db_input high carlos-emr/stop-port-conflicts"),
                        calls.index("host_conflicts front-door 0.0.0.0"))
        self.assertIn("FREED=" + self.STOPPED[len("stopped: "):], r.stdout)
        self.assertIn("carlos-emr: apache2.service (package apache2) was listening", r.stderr)
        self.assertTrue(any(c.startswith("logger -t carlos-emr -p daemon.notice -- front door: "
                                         "apache2.service") for c in calls), calls)

    def test_interactive_no_changes_nothing_and_logs_the_refusal(self):
        r, calls = self.run_block("false")
        self.assertNotIn("host_conflicts front-door 0.0.0.0", calls)
        self.assertIn("FREED=\n", r.stdout)
        self.assertIn("NOT stopping (declined): apache2.service", r.stderr)
        self.assertTrue(any("front door: declined to stop apache2.service" in c for c in calls), calls)

    def test_noninteractive_takes_the_default_and_goes_ahead(self):
        # db_input returns 30 when the question is not shown; db_get then
        # returns the stored value, which is the template default (true).
        r, calls = self.run_block("true", shown=False)
        self.assertIn("host_conflicts front-door 0.0.0.0", calls)
        self.assertIn("FREED=" + self.STOPPED[len("stopped: "):], r.stdout)
        self.assertTrue(any(c.startswith("logger ") for c in calls), calls)

    def test_nothing_to_stop_asks_nothing(self):
        r, calls = self.run_block("true", plan=False)
        self.assertFalse(any(c.startswith(("db_", "logger")) for c in calls), calls)
        self.assertNotIn("host_conflicts front-door 0.0.0.0", calls)

    def test_upgrade_leaves_an_operator_stopped_nginx_and_its_replacement_alone(self):
        r, calls = self.run_block("true", old_version="2026.08.0~beta2", nginx_state="inactive")
        self.assertEqual([c for c in calls if c.startswith(("host_conflicts", "db_"))], [])
        self.assertIn("FREED=\n", r.stdout)
        self.assertIn("left as they are", r.stderr)

    def test_upgrade_with_a_failed_nginx_still_frees_the_ports(self):
        r, calls = self.run_block("true", old_version="2026.08.0~beta2", nginx_state="failed")
        self.assertIn("host_conflicts front-door 0.0.0.0", calls)

    def test_first_install_frees_the_ports_whatever_nginx_state(self):
        r, calls = self.run_block("true", old_version="", nginx_state="inactive")
        self.assertIn("host_conflicts front-door 0.0.0.0", calls)

    def test_the_question_defaults_to_yes(self):
        templates = TEMPLATES.read_text()
        block = templates[templates.index("Template: carlos-emr/stop-port-conflicts\n"):]
        self.assertTrue(block.startswith("Template: carlos-emr/stop-port-conflicts\n"
                                         "Type: boolean\nDefault: true\n"), block[:120])
        self.assertIn("${services}", block.split("\n\n")[0])


if __name__ == "__main__":
    unittest.main()
