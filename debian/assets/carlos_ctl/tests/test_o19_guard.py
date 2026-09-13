# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The OSCAR-19-in-progress guard: one shipped predicate, three callers.

`debian/assets/bin/carlos-emr-o19-guard` decides whether carlos-emr may
start. carlos-emr.service consults it as ExecCondition= on every start
(so a reboot mid-import does not start the EMR into a half-copied
schema -- seen on the Ubuntu 26.04 rehearsal, where the boot-time start
wrote `site`/`providersite` rows the import's row-parity gate then
rejected), carlos-emr.postinst consults it before migrating and starting,
and `carlos-ctl start|restart` consults it so the refusal lands on the
terminal instead of a silent systemd "condition failed".

These tests run the SHIPPED script (sh + python3) over the full ledger
matrix, and pin that the unit, the packaging and the CLI all point at
it. Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import contextlib
import io
import os
import re
import subprocess
import tempfile
from pathlib import Path
import unittest
from unittest import mock

from carlos_ctl import cli, util

ROOT = Path(__file__).resolve().parents[4]
GUARD = ROOT / "debian" / "assets" / "bin" / "carlos-emr-o19-guard"
UNIT = ROOT / "debian" / "carlos-emr.carlos-emr.service"
RULES = ROOT / "debian" / "rules"
POSTINST = ROOT / "debian" / "carlos-emr.postinst"

IN_PROGRESS = '{"phases":{"etl":{"status":"done"}}}'
FINISHED = '{"phases":{"verify":{"status":"done"}}}'


def _run_guard(ledger_text, path=None):
    """Run the shipped guard over `ledger_text` written to a scratch
    ledger (or, with `ledger_text` None, over a missing path)."""
    with tempfile.TemporaryDirectory(suffix=" with space") as tmp:
        ledger = path or os.path.join(tmp, "state.json")
        if ledger_text is not None:
            with open(ledger, "w") as fh:
                fh.write(ledger_text)
        return subprocess.run(["sh", str(GUARD), ledger],
                              capture_output=True, text=True)


class TestGuardScriptVerdicts(unittest.TestCase):

    """Exit 0 = may start; exit 1 = an import is in progress (or the
    ledger cannot be read: fail CLOSED)."""

    def test_shipped_file_is_executable_posix_sh(self):
        self.assertTrue(GUARD.is_file(), GUARD)
        self.assertTrue(os.access(str(GUARD), os.X_OK),
                        "the guard is not executable in the source tree")
        first = GUARD.read_text().splitlines()[0]
        self.assertEqual(first, "#!/bin/sh",
                         "the unit runs the guard through /bin/sh")

    def test_finished_import_may_start(self):
        self.assertEqual(_run_guard(FINISHED).returncode, 0)

    def test_verify_started_gates(self):
        r = _run_guard('{"phases":{"verify":{"status":"started"}}}')
        self.assertEqual(r.returncode, 1)

    def test_verify_without_status_gates(self):
        self.assertEqual(_run_guard('{"phases":{"verify":{}}}')
                         .returncode, 1)

    def test_verify_not_a_dict_gates(self):
        self.assertEqual(_run_guard('{"phases":{"verify":"done"}}')
                         .returncode, 1)

    def test_etl_without_verify_gates(self):
        self.assertEqual(_run_guard(IN_PROGRESS).returncode, 1)

    def test_stage_only_assessment_may_start(self):
        # a dry run / abandoned preflight stages the dump and nothing
        # else; it holds no half-copied schema, so it must not keep a
        # clinic's EMR down after a reboot
        r = _run_guard('{"phases":{"stage":{"status":"done"}}}')
        self.assertEqual(r.returncode, 0)

    def test_stage_plus_etl_gates(self):
        r = _run_guard('{"phases":{"stage":{"status":"done"},'
                       '"etl":{"status":"done"}}}')
        self.assertEqual(r.returncode, 1)

    def test_empty_phases_gates(self):
        # P0 has begun (the ledger exists) and nothing is recorded yet
        self.assertEqual(_run_guard('{"phases":{}}').returncode, 1)

    def test_missing_phases_key_gates(self):
        self.assertEqual(_run_guard('{"inputs":{}}').returncode, 1)

    def test_phases_not_a_dict_gates(self):
        self.assertEqual(_run_guard('{"phases":[]}').returncode, 1)

    def test_top_level_array_gates_without_traceback(self):
        r = _run_guard("[1,2]")
        self.assertEqual(r.returncode, 1)
        self.assertNotIn("Traceback", r.stderr)

    def test_corrupt_json_gates(self):
        r = _run_guard("not json at all")
        self.assertEqual(r.returncode, 1)
        self.assertNotIn("Traceback", r.stderr)

    def test_empty_file_may_start(self):
        # the importer writes the ledger with os.replace(); a 0-byte file
        # is never a written state, and the -s short-circuit treats it as
        # absent (as the postinst's inline condition always did)
        self.assertEqual(_run_guard("").returncode, 0)

    def test_missing_ledger_may_start(self):
        r = _run_guard(None, path="/nonexistent/o19 guard/state.json")
        self.assertEqual(r.returncode, 0)
        self.assertEqual(r.stderr, "")

    def test_refusal_names_the_remedy_on_stderr(self):
        r = _run_guard(IN_PROGRESS)
        self.assertEqual(r.stdout, "", "the guard must not write stdout")
        self.assertIn("--resume", r.stderr)
        self.assertIn("--cleanup", r.stderr)
        self.assertIn("carlos-emr:", r.stderr)

    def test_permission_may_start_is_silent(self):
        r = _run_guard(FINISHED)
        self.assertEqual((r.stdout, r.stderr), ("", ""))

    def test_default_ledger_is_the_real_path_and_only_that(self):
        # the unit and the postinst pass no argument; the ONE default must
        # be the importer's ledger, and nothing may come from the
        # environment (a root-run ExecCondition must not be steerable by
        # an EnvironmentFile)
        code = "\n".join(ln for ln in GUARD.read_text().splitlines()
                         if not ln.lstrip().startswith("#"))
        self.assertEqual(
            code.count('LEDGER="${1:-/var/lib/carlos-emr/o19-import/'
                       'state.json}"'), 1)
        self.assertNotIn("getenv", code)
        self.assertNotIn("environ", code)
        # the only shell variable the script expands is its own LEDGER
        self.assertEqual(set(re.findall(r"\$\{?([A-Za-z_][A-Za-z0-9_]*)",
                                        code)), {"LEDGER"})


class TestGuardCallers(unittest.TestCase):

    """The three callers all run the shipped file, never a copy."""

    def test_unit_consults_the_guard_as_privileged_exec_condition(self):
        lines = UNIT.read_text().splitlines()
        self.assertIn("ExecCondition=+/usr/lib/carlos-emr/"
                      "carlos-emr-o19-guard", lines)
        # the condition must precede ExecStart= (systemd runs them in
        # file order and a later one still gates, but the reader should
        # not have to know that)
        cond = lines.index("ExecCondition=+/usr/lib/carlos-emr/"
                           "carlos-emr-o19-guard")
        start = next(i for i, ln in enumerate(lines)
                     if ln.startswith("ExecStart="))
        self.assertLess(cond, start)

    def test_rules_installs_the_guard_where_the_unit_points(self):
        self.assertIn("install -m 0755 debian/assets/bin/"
                      "carlos-emr-o19-guard "
                      "$(STAGE)/$(LIBDIR)/carlos-emr-o19-guard",
                      RULES.read_text())

    def test_postinst_predicate_is_the_guard_negated(self):
        text = POSTINST.read_text()
        self.assertIn("! /usr/lib/carlos-emr/carlos-emr-o19-guard", text)
        # and it fails closed when the file is missing
        idx = text.index("[ ! -x /usr/lib/carlos-emr/carlos-emr-o19-guard ]")
        self.assertIn("return 0", text[idx:idx + 400])

    def test_cli_guard_path_matches_the_installed_location(self):
        self.assertEqual(cli.O19_GUARD,
                         "/usr/lib/carlos-emr/carlos-emr-o19-guard")


class TestLifecycleRefusal(unittest.TestCase):

    """`carlos-ctl start|restart` relay the guard's verdict to the
    terminal; `stop` never consults it."""

    def _lifecycle(self, verb, exists=True, rc=0, stderr=""):
        calls = []

        def fake_run(cmd, **kw):
            calls.append(cmd)
            return subprocess.CompletedProcess(cmd, rc, stdout="",
                                               stderr=stderr)
        err = io.StringIO()
        with mock.patch.object(cli, "need_root"), \
                mock.patch.object(util, "reset_emr_start_limit"), \
                mock.patch.object(cli.os.path, "exists",
                                  return_value=exists), \
                mock.patch.object(util, "run", side_effect=fake_run), \
                mock.patch.object(cli.os, "execvp",
                                  side_effect=RuntimeError("execvp")) \
                as execvp, contextlib.redirect_stderr(err):
            try:
                cli._cmd_lifecycle(verb, [])
            except RuntimeError as e:
                self.assertEqual(str(e), "execvp")
                outcome = "exec"
            except SystemExit as e:
                outcome = e.code
        return outcome, calls, execvp, err.getvalue()

    def test_start_refused_while_import_in_progress(self):
        outcome, calls, execvp, err = self._lifecycle(
            "start", rc=1, stderr="carlos-emr: NOT starting: ...\n")
        self.assertEqual(outcome, 1)
        self.assertEqual(calls, [[cli.O19_GUARD]])
        execvp.assert_not_called()
        self.assertIn("OSCAR 19 import is in progress", err)
        self.assertIn("--resume", err)
        # the guard's own explanation is relayed verbatim
        self.assertIn("carlos-emr: NOT starting", err)

    def test_restart_refused_while_import_in_progress(self):
        outcome, calls, execvp, _ = self._lifecycle("restart", rc=1)
        self.assertEqual(outcome, 1)
        self.assertEqual(calls, [[cli.O19_GUARD]])
        execvp.assert_not_called()

    def test_start_proceeds_when_guard_permits(self):
        outcome, calls, execvp, err = self._lifecycle("start", rc=0)
        self.assertEqual(outcome, "exec")
        self.assertEqual(calls, [[cli.O19_GUARD]])
        execvp.assert_called_once_with(
            "systemctl", ["systemctl", "start", "carlos-emr.service"])
        self.assertEqual(err, "")

    def test_start_proceeds_when_guard_file_absent(self):
        # a host whose package predates the guard: unchanged behaviour
        outcome, calls, execvp, _ = self._lifecycle("start", exists=False)
        self.assertEqual(outcome, "exec")
        self.assertEqual(calls, [])
        execvp.assert_called_once()

    def test_stop_never_consults_the_guard(self):
        outcome, calls, execvp, _ = self._lifecycle("stop", rc=1)
        self.assertEqual(outcome, "exec")
        self.assertEqual(calls, [])
        execvp.assert_called_once_with(
            "systemctl", ["systemctl", "stop", "carlos-emr.service"])

    def test_guard_is_consulted_before_the_start_limit_reset(self):
        # order matters: resetting the start-rate counter is a side
        # effect on the unit, and a refused start must leave none
        order = []
        with mock.patch.object(cli, "need_root"), \
                mock.patch.object(util, "reset_emr_start_limit",
                                  side_effect=lambda: order.append(
                                      "reset")), \
                mock.patch.object(cli.os.path, "exists",
                                  return_value=True), \
                mock.patch.object(util, "run", side_effect=lambda c, **k: (
                    order.append("guard"),
                    subprocess.CompletedProcess(c, 1, "", ""))[1]), \
                mock.patch.object(cli.os, "execvp"), \
                contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                cli._cmd_lifecycle("restart", [])
        self.assertEqual(order, ["guard"])


if __name__ == "__main__":
    unittest.main()
