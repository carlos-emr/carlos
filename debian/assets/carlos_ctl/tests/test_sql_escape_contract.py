# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The SQL escape has one implementation, and one deliberate copy.

Run (from debian/assets):
    python3 -m unittest carlos_ctl.tests.test_sql_escape_contract -v
"""

import ast
import unittest
from pathlib import Path

from carlos_ctl import dbops, o19docs, o19etl, o19_preflight, util

# Values chosen so that any escape that drops one of the three cases -- or
# adds a fourth -- produces a different string for at least one of them.
CORPUS = [
    "",
    "plain",
    "O'Brien",
    "back\\slash",
    "both \\' together",
    "nul\0inside",
    "trailing backslash\\",
    "\\'",
    "'; DROP TABLE demographic; --",
    "line\nbreak\r\n",
    "quote\"double",
    "\0\0",
    "\\\\'",
    "unicode Santé ✓",
]


class TestOneImplementation(unittest.TestCase):

    """Every in-package caller escapes through util.sql_escape.

    Four copies of this function had already drifted apart once, and the
    one that lost a case was the one no test compared."""

    def test_the_package_names_all_delegate(self):
        for fn in (dbops.sql_escape, o19etl._sql_str, o19docs._sql_str):
            for value in CORPUS:
                self.assertEqual(fn(value), util.sql_escape(value),
                                 "{0} diverged on {1!r}".format(fn, value))

    def test_only_util_carries_the_escape_itself(self):
        # the shape that drifted: a function building the escape locally
        # out of chained .replace() calls. A delegating one-liner has none,
        # so anything else that grows one is a fourth copy in the making.
        pkg = Path(o19etl.__file__).parent
        offenders = []
        for path in sorted(pkg.glob("*.py")):
            if path.name == "o19_preflight.py":
                continue            # the deliberate copy, pinned below
            text = path.read_text(encoding="utf-8")
            tree = ast.parse(text)
            for node in ast.walk(tree):
                if not isinstance(node, ast.FunctionDef):
                    continue
                body = ast.get_source_segment(text, node) or ""
                if (body.count(".replace(") >= 3
                        and "\\\\" in body and "\\'" in body):
                    offenders.append("{0}:{1}".format(path.name, node.name))
        self.assertEqual(
            offenders, ["util.py:sql_escape"],
            "the SQL escape must have exactly one in-package "
            "implementation, in util.sql_escape; four copies drifted once "
            "already and the one that lost a case was the one no test "
            "compared")


class TestTheStandaloneCopyAgrees(unittest.TestCase):

    """o19_preflight.py is copied ALONE to a 2014-era OSCAR 19 server and
    may import nothing from the package, so its escape is a deliberate
    duplicate. That makes it the one that can drift unnoticed -- and it
    had, having lost the NUL case while the other three kept it."""

    def test_it_matches_util_on_every_case(self):
        for value in CORPUS:
            self.assertEqual(
                o19_preflight._sql_literal(value), util.sql_escape(value),
                "the standalone copy diverged on {0!r}; it is carried to "
                "the clinic's server on its own, so nothing there would "
                "catch it".format(value))

    def test_the_identifier_quoting_also_matches(self):
        # same shape, different rule: backticks double, and nothing else
        for name in ("plain", "we`ird", "``", "sp ace", "Santé"):
            self.assertEqual(o19_preflight._ident(name), o19etl.ident(name))

    def test_the_standalone_file_still_imports_nothing_from_the_package(self):
        # the reason the duplicate exists at all; if this ever stops being
        # true the copy should go, not the other way round
        text = Path(o19_preflight.__file__).read_text(encoding="utf-8")
        self.assertNotIn("from carlos_ctl", text)
        self.assertNotIn("from .util", text)
        self.assertNotIn("from . import", text)


class TestWhatTheEscapeActuallyDoes(unittest.TestCase):

    """The three cases, stated as values rather than as an implementation."""

    def test_a_backslash_doubles(self):
        self.assertEqual(util.sql_escape("a\\b"), "a\\\\b")

    def test_a_single_quote_is_backslash_escaped(self):
        self.assertEqual(util.sql_escape("O'Brien"), "O\\'Brien")

    def test_a_nul_becomes_the_two_character_escape(self):
        # the client refuses a raw NUL in a statement outright, and values
        # decoded from its batch output can carry one
        self.assertEqual(util.sql_escape("a\0b"), "a\\0b")

    def test_a_double_quote_is_left_alone(self):
        # legal inside a single-quoted literal; ANSI_QUOTES, which would
        # change that, is refused by the ETL pre-checks before any write
        self.assertEqual(util.sql_escape('say "hi"'), 'say "hi"')

    def test_the_backslash_pass_runs_first(self):
        # order matters: escaping the quote first would leave the escape's
        # own backslash to be doubled by the later pass, producing \\' --
        # a literal backslash followed by an unescaped quote
        self.assertEqual(util.sql_escape("\\'"), "\\\\\\'")


if __name__ == "__main__":
    unittest.main()
