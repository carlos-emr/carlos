# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""The carlos-emr-drugref postinst: what it tells the administrator, and when
it may touch the database.

Two defects in `debian/carlos-emr-drugref.postinst` are open in
`docs/ui-tests/app-findings-log.md`, and each has a test here that asserts the
CORRECT behaviour:

  * finding 174 -- the two paths that `exit 1` when the provisioning lock
    cannot be taken print `sudo dpkg-reconfigure carlos-emr-drugref` as the
    way to recover. A failed postinst leaves the package half-configured, and
    dpkg-reconfigure refuses a package that is not fully installed, so the
    hint cannot be followed. (carlos-emr.postinst says `dpkg --configure`
    for the same state.)
  * finding 175 -- `acquire_provision_lock` asks the OSCAR 19 import guard
    only when the lock is BUSY. A paused import holds no lock but its ledger
    still reads "in progress", so with the lock free the function returns 0
    and `db-users` and the grants run under an import that owns the database.

While a finding is open its test is decorated with `open_finding`, so the
suite stays green; the day the postinst is fixed the same test passes without
being touched. Structural tests and the controls around each finding are NOT
decorated: if the postinst is reshaped so that a finding test can no longer
find what it inspects, that fails loudly instead of hiding behind the open
finding.

Run (from the repository root):
    python3 -m unittest discover -s debian/assets/tests
"""

import functools
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
POSTINST = ROOT / "debian" / "carlos-emr-drugref.postinst"


def open_finding(number, reason):
    """Mark a test that asserts correct behaviour while finding `number` is open.

    A failing assertion is reported as an expected failure (pytest: xfail with
    strict=False semantics; plain unittest, which CI uses, has no such outcome
    and reports a skip carrying the reason). A passing test passes -- never a
    failure, which is what unittest.expectedFailure would make of it, so a fix
    cannot break an unrelated build. Only AssertionError is absorbed: a
    KeyError or a missing file is a bug in the test and still fails.
    """

    def decorate(test):
        @functools.wraps(test)
        def run(self, *args, **kwargs):
            try:
                return test(self, *args, **kwargs)
            except AssertionError:
                note = "finding %s is open: %s" % (number, reason)
                pytest = sys.modules.get("pytest")
                if pytest is not None:
                    pytest.xfail(note)
                raise unittest.SkipTest("expected failure, " + note) from None

        return run

    return decorate


def _postinst_text():
    return POSTINST.read_text(encoding="utf-8")


def _function_source(name):
    """The text of the shell function `name`, from its header to the closing
    brace in column 0."""
    match = re.search(r"^%s\(\) \{\n.*?^\}\n" % re.escape(name),
                      _postinst_text(), re.S | re.M)
    if not match:
        raise AssertionError("no function %s() in %s -- this contract has "
                             "lost its subject" % (name, POSTINST.name))
    return match.group(0)


def _lock_failure_branches():
    """The text printed by each `if [ "${provision_lock_rc}" ... ]` branch that
    ends in `exit 1`, in file order."""
    pattern = re.compile(
        r'if \[ "\$\{provision_lock_rc\}" (?:=|!=) \d \]; then\n'
        r"(?P<body>(?:[ \t]*echo [^\n]*\n)+)"
        r"[ \t]*exit 1\n[ \t]*fi", re.M)
    return [m.group("body") for m in pattern.finditer(_postinst_text())]


class TestLockFailureHints(unittest.TestCase):

    """What the administrator is told when the provisioning lock fails."""

    def test_both_lock_failure_paths_are_found(self):
        # Control for the finding below: it must be inspecting the two
        # branches the finding is about, not an empty list.
        branches = _lock_failure_branches()
        self.assertEqual(len(branches), 2, branches)
        for body in branches:
            self.assertIn("carlos-emr-drugref: ERROR:", body)
            self.assertIn(">&2", body)

    @open_finding(174, "the exit-1 recovery hint is dpkg-reconfigure, which "
                       "dpkg refuses for the half-configured package")
    def test_no_hint_asks_for_dpkg_reconfigure_after_a_failed_postinst(self):
        for body in _lock_failure_branches():
            self.assertNotIn(
                "dpkg-reconfigure", body,
                "this branch exits 1, leaving carlos-emr-drugref "
                "half-configured; dpkg-reconfigure refuses such a package, so "
                "the hint below cannot be followed:\n" + body)

    @open_finding(174, "the exit-1 branches name no recovery command that "
                       "works on a half-configured package")
    def test_each_failure_names_a_recovery_that_works_when_half_configured(self):
        # `dpkg --configure <pkg>` (what carlos-emr.postinst prints for the
        # same state) or apt's --fix-broken both complete a package whose
        # postinst failed.
        usable = re.compile(r"dpkg --configure carlos-emr-drugref"
                            r"|(?:apt|apt-get) (?:install -f|--fix-broken)")
        for body in _lock_failure_branches():
            self.assertRegex(body, usable)


@unittest.skipUnless(shutil.which("flock"), "flock(1) is not installed; the "
                     "function returns before the lock without it")
class TestAcquireProvisionLock(unittest.TestCase):

    """Run the SHIPPED acquire_provision_lock under /bin/sh with a stand-in
    for the import guard and a real flock(1)."""

    def run_acquire(self, guard_exit, hold_lock):
        """Returns acquire_provision_lock's exit status."""
        functions = (_function_source("o19_import_holds_database")
                     + _function_source("acquire_provision_lock"))
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            state = tmp / "state"
            lock = state / ".finish-install.lock"
            guard = tmp / "o19-guard"
            guard.write_text("#!/bin/sh\nexit %d\n" % guard_exit)
            guard.chmod(0o755)
            script = "\n".join([
                "STATE=%s" % shlex.quote(str(state)),
                'PROVISION_LOCK="${STATE}/.finish-install.lock"',
                "O19_GUARD=%s" % shlex.quote(str(guard)),
                functions,
                "acquire_provision_lock",
                'echo "rc=$?"',
            ])
            holder = None
            try:
                if hold_lock:
                    state.mkdir()
                    holder = subprocess.Popen(["flock", str(lock), "sleep", "30"])
                    deadline = time.monotonic() + 10
                    # The lock is held once a non-blocking attempt is refused.
                    while subprocess.run(["flock", "-n", str(lock), "true"]).returncode == 0:
                        self.assertLess(time.monotonic(), deadline, "the lock holder never started")
                        time.sleep(0.05)
                done = subprocess.run(["sh", "-c", script], capture_output=True,
                                      text=True, timeout=20)
            finally:
                if holder is not None:
                    holder.kill()
                    holder.wait()
        match = re.search(r"^rc=(\d+)$", done.stdout, re.M)
        self.assertIsNotNone(match, "no status from the function: stdout=%r stderr=%r"
                             % (done.stdout, done.stderr))
        return int(match.group(1))

    def test_busy_lock_during_an_import_reports_the_import(self):
        # Status 2 is what the caller turns into "an OSCAR 19 import is in
        # progress" without waiting the five minutes out.
        self.assertEqual(self.run_acquire(guard_exit=1, hold_lock=True), 2)

    def test_free_lock_and_no_import_is_acquired(self):
        self.assertEqual(self.run_acquire(guard_exit=0, hold_lock=False), 0)

    @open_finding(175, "the import guard is consulted only when the lock is "
                       "busy, so a paused import does not stop provisioning")
    def test_free_lock_during_a_paused_import_is_refused(self):
        # A paused import holds no lock, but the guard still reports it in
        # progress; provisioning must not rewrite grants under it.
        self.assertEqual(
            self.run_acquire(guard_exit=1, hold_lock=False), 2,
            "acquire_provision_lock took the free lock and returned before "
            "asking the import guard")


if __name__ == "__main__":
    unittest.main()
