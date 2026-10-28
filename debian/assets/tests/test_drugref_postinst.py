# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""The carlos-emr-drugref postinst: what it tells the administrator, and when
it may touch the database.

Regressions for findings 174 and 175 in `docs/ui-tests/app-findings-log.md`:

  * finding 174 -- the two paths that `exit 1` when the provisioning lock
    cannot be taken previously printed `sudo dpkg-reconfigure carlos-emr-drugref` as the
    way to recover. A failed postinst leaves the package half-configured, and
    dpkg-reconfigure refuses a package that is not fully installed, so the
    hint cannot be followed. (carlos-emr.postinst says `dpkg --configure`
    for the same state.)
  * finding 175 -- `acquire_provision_lock` previously asked the OSCAR 19 import guard
    only when the lock was BUSY. A paused import holds no lock but its ledger
    still reads "in progress", so with the lock free the function returned 0
    and `db-users` and the grants run under an import that owns the database.

These assertions are required to pass. Lock/guard controls also cover an import
ledger appearing during acquisition, after a wait, and without flock available.

Run (from the repository root):
    python3 -m unittest discover -s debian/assets/tests
"""

import re
import shlex
import shutil
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
POSTINST = ROOT / "debian" / "carlos-emr-drugref.postinst"


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
    # The match ends at the first closing brace in column 0. A heredoc or other multi-line construct
    # that put one inside the function would cut it short and the tests would run a broken function;
    # a syntax check turns that silent truncation into a loud failure.
    syntax = subprocess.run(["sh", "-n"], input=match.group(0), capture_output=True,
                            text=True, timeout=20, check=False)
    if syntax.returncode != 0:
        raise AssertionError("function %s() extracted from %s does not parse (truncated at a "
                             "column-0 brace?): %s" % (name, POSTINST.name, syntax.stderr.strip()))
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

    def test_no_hint_asks_for_dpkg_reconfigure_after_a_failed_postinst(self):
        for body in _lock_failure_branches():
            self.assertNotIn(
                "dpkg-reconfigure", body,
                "this branch exits 1, leaving carlos-emr-drugref "
                "half-configured; dpkg-reconfigure refuses such a package, so "
                "the hint below cannot be followed:\n" + body)

    def test_each_failure_names_a_recovery_that_works_when_half_configured(self):
        # `dpkg --configure <pkg>` (what carlos-emr.postinst prints for the
        # same state), `dpkg --configure -a` (every pending package, the
        # form dpkg itself suggests) and apt's --fix-broken all complete a
        # package whose postinst failed.
        usable = re.compile(r"dpkg --configure (?:carlos-emr-drugref|-a|--pending)\b"
                            r"|(?:apt|apt-get) (?:install -f|--fix-broken)")
        for body in _lock_failure_branches():
            self.assertRegex(body, usable)


@unittest.skipUnless(shutil.which("flock"), "flock(1) is not installed; the "
                     "function returns before the lock without it")
class TestAcquireProvisionLock(unittest.TestCase):

    """Run the SHIPPED acquire_provision_lock under /bin/sh with a stand-in
    for the import guard and a real flock(1)."""

    def run_acquire(self, guard_exit=0, hold_lock=False, *, wait_outcome=None,
                    no_flock=False, ledger_text=None):
        """Returns acquire_provision_lock's exit status."""
        functions = (_function_source("o19_import_holds_database")
                     + _function_source("acquire_provision_lock"))
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            state = tmp / "state"
            lock = state / ".finish-install.lock"
            guard = tmp / "o19-guard"
            statuses = guard_exit if isinstance(guard_exit, list) else [guard_exit]
            counter = shlex.quote(str(tmp / "guard-calls"))
            guard.write_text("\n".join([
                "#!/bin/sh",
                "count=0",
                "[ ! -f %s ] || read -r count < %s" % (counter, counter),
                "count=$((count + 1))",
                "printf '%s\\n' \"$count\" > " + counter,
                'case "$count" in',
                *["  %d) exit %d ;;" % (i, status) for i, status in enumerate(statuses, 1)],
                "  *) exit %d ;;" % statuses[-1],
                "esac\n",
            ]))
            if ledger_text is not None:
                ledger = tmp / "state.json"
                ledger.write_text(ledger_text)
                shipped_guard = ROOT / "debian" / "assets" / "bin" / "carlos-emr-o19-guard"
                guard.write_text("#!/bin/sh\nexec %s %s\n" % (
                    shlex.quote(str(shipped_guard)), shlex.quote(str(ledger))))
            guard.chmod(0o755)
            setup = []
            if no_flock:
                # The guard uses only shell builtins, so it remains executable
                # while command -v flock accurately reports it unavailable.
                setup.append("PATH=/no-carlos-test-commands")
            if wait_outcome is not None:
                # Deterministic acquisition race: the nonblocking attempt is
                # busy, then the bounded wait succeeds or times out. Other
                # controls below exercise a real flock rather than this stub.
                setup.append('flock() { [ "$1" != -n ] && return %d; return 1; }' % wait_outcome)
            script = "\n".join([
                *setup,
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

    def test_free_lock_during_a_paused_import_is_refused(self):
        # A paused import holds no lock, but the guard still reports it in
        # progress; provisioning must not rewrite grants under it.
        self.assertEqual(
            self.run_acquire(guard_exit=1, hold_lock=False), 2,
            "acquire_provision_lock took the free lock and returned before "
            "asking the import guard")

    def test_import_appearing_while_taking_a_free_lock_is_refused(self):
        self.assertEqual(self.run_acquire([0, 1]), 2)

    def test_import_paused_before_waiting_owner_releases_lock_is_refused(self):
        self.assertEqual(self.run_acquire([0, 0, 1], wait_outcome=0), 2)

    def test_waiting_lock_with_no_import_can_provision(self):
        self.assertEqual(self.run_acquire(0, wait_outcome=0), 0)

    def test_import_appearing_during_a_timed_out_wait_is_reported(self):
        self.assertEqual(self.run_acquire([0, 0, 1], wait_outcome=1), 2)

    def test_timed_out_lock_without_import_is_a_lock_failure(self):
        self.assertEqual(self.run_acquire(0, wait_outcome=1), 1)

    def test_missing_flock_does_not_bypass_a_paused_import(self):
        self.assertEqual(self.run_acquire(1, no_flock=True), 2)

    def test_missing_flock_retains_legacy_fallback_when_no_import_exists(self):
        self.assertEqual(self.run_acquire(0, no_flock=True), 0)

    def test_missing_flock_rechecks_import_before_legacy_fallback(self):
        self.assertEqual(self.run_acquire([0, 1], no_flock=True), 2)

    def test_real_guard_refuses_a_paused_import_with_no_lock_owner(self):
        self.assertEqual(self.run_acquire(
            ledger_text='{"phases":{"stage":{"status":"done"},"etl":{"status":"done"}}}'), 2)

    def test_real_guard_allows_a_staged_assessment(self):
        self.assertEqual(self.run_acquire(
            ledger_text='{"phases":{"stage":{"status":"done"}}}'), 0)

    def test_real_guard_allows_a_verified_import(self):
        self.assertEqual(self.run_acquire(
            ledger_text='{"phases":{"verify":{"status":"done"}}}'), 0)


if __name__ == "__main__":
    unittest.main()
