# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Argument contract of the verbs that take none.

`carlos-ctl bootstrap-admin --help` once RAN bootstrap-admin: the dispatcher
handed every trailing argument to the handler, and a handler that reads none
silently reset a tester's freshly set administrator password. A verb that
takes no arguments must answer --help with its usage and refuse anything
else without running."""

import contextlib
import io
import unittest
from unittest import mock

from carlos_ctl import cli


class TestNoArgumentVerbs(unittest.TestCase):

    def _dispatch(self, argv):
        handler = mock.Mock(return_value=0)
        stdout, stderr = io.StringIO(), io.StringIO()
        with mock.patch.dict(cli._VERBS, {"bootstrap-admin": handler,
                                          "init-config": handler}):
            with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                try:
                    rc = cli.main(argv)
                except SystemExit as e:  # util.die
                    rc = e.code
        return rc, handler, stdout.getvalue(), stderr.getvalue()

    def test_help_prints_usage_and_never_runs_the_verb(self):
        for flag in ("--help", "-h", "help"):
            rc, handler, out, _ = self._dispatch(["bootstrap-admin", flag])
            self.assertEqual(rc, 0, flag)
            handler.assert_not_called()
            self.assertIn("carlos-ctl bootstrap-admin", out)
            self.assertIn("takes no arguments", out)

    def test_stray_argument_is_refused_not_ignored(self):
        rc, handler, _, err = self._dispatch(["bootstrap-admin", "--force"])
        self.assertNotEqual(rc, 0)
        handler.assert_not_called()
        self.assertIn("takes no arguments", err)
        self.assertIn("--force", err)

    def test_bare_verb_still_runs(self):
        rc, handler, _, _ = self._dispatch(["init-config"])
        self.assertEqual(rc, 0)
        handler.assert_called_once_with([])

    def test_every_listed_verb_ignores_argv_in_its_handler(self):
        # The guard is only correct for handlers that read no arguments; a
        # verb that grows options must leave the set.
        import ast
        import inspect
        for verb in cli._NO_ARGUMENT_VERBS:
            handler = cli._VERBS[verb]
            self.assertIsNotNone(handler, verb)
            if handler.__name__ == "<lambda>":
                continue
            src = inspect.getsource(handler)
            # Strip the leading indentation so nested-def sources parse.
            tree = ast.parse("\n".join(l[4:] if l.startswith("    ") else l
                                       for l in src.splitlines()) if src.startswith("    ") else src)
            fn = tree.body[0]
            reads = [n for n in ast.walk(fn)
                     if isinstance(n, ast.Name) and n.id == "argv"]
            self.assertEqual(reads, [], f"{verb} reads argv; drop it from _NO_ARGUMENT_VERBS")

    def test_verb_usage_extracts_the_continuation_lines(self):
        text = cli._verb_usage("init-config")
        self.assertIn("carlos-ctl init-config", text.splitlines()[0])
        self.assertIn("carlos-emr.env", text)


if __name__ == "__main__":
    unittest.main()
