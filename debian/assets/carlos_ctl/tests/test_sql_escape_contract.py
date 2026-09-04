# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The SQL escape has one implementation, and one deliberate copy.

Run (from debian/assets):
    python3 -m unittest carlos_ctl.tests.test_sql_escape_contract -v
"""

import ast
import unittest
from pathlib import Path

from carlos_ctl import (dbops, o19docs, o19etl, o19_preflight,
                        o19props, util)

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


class TestEveryStandaloneCopyIsPinned(unittest.TestCase):

    """The escape is not the only thing `o19_preflight.py` duplicates.

    It is carried to the clinic's server ALONE and may import nothing
    from the package, so several helpers exist twice. Two of them were
    pinned here; four were not, and nothing but habit kept them in step
    -- the same shape as the drift this file was written after, where
    "the one that lost a case was the one no test compared".

    These check BEHAVIOUR, not source: the standalone file carries no
    annotations (Python 3.4) and words its docstrings differently, so
    comparing text would fail on differences that do not matter and
    would have to be relaxed until it caught nothing."""

    #: java.util.Properties corner cases: separators, continuations,
    #: escapes, surrogate pairs, duplicate keys, CRLF, a valueless key.
    PROPERTIES = [
        "a=1\nb:2\nc 3\n",
        "key\\ with\\ space = v\n",
        "cont = one\\\n  two\n",
        "esc = a\\tb\\nc\\u00e9\n",
        "trail = value   \n",
        "dup = first\ndup = second\n",
        "# comment\n! also comment\nreal = x\n",
        "empty =\n",
        "colonkey\\:x = v\n",
        "uni = \\ud83d\\ude00\n",
        "crlf = v\r\nnext = w\r\n",
        "novalue\n",
    ]

    def test_both_property_parsers_read_the_same_file_the_same_way(self):
        """The preflight's advisories and the import's fragment are built
        from the SAME clinic file. If the two parses drift, a clinic can
        be given a verdict about properties the import then reads
        differently -- and neither side would say so."""
        for text in self.PROPERTIES:
            a = o19_preflight.parse_properties_text(text)
            b = o19props.parse_properties_text(text)
            # the shapes differ ON PURPOSE (the preflight only needs a
            # lookup; the fragment needs order), so compare the parse
            self.assertEqual(
                dict(a) if not isinstance(a, dict) else a,
                dict(b),
                "the two property parsers disagree on {0!r}".format(text))

    def test_the_escape_and_surrogate_helpers_agree(self):
        for raw in ("a\\tb", "\\u00e9", "\\ud83d\\ude00", "plain",
                    "trailing\\", "\\x", ""):
            self.assertEqual(o19_preflight._unescape_property(raw),
                             o19props._unescape_property(raw), raw)
        for raw in ("\ud83d\ude00", "no surrogates", "\ud800lone"):
            self.assertEqual(o19_preflight._join_surrogates(raw),
                             o19props._join_surrogates(raw), repr(raw))

    def test_the_mojibake_predicate_is_the_same_test_on_both_sides(self):
        """The preflight BLOCKS on this predicate and the ETL REPAIRS on
        it. Drift means blocking a clinic whose data would not be
        repaired, or passing one whose data would be rewritten."""
        for col in ("x", "last_name", "we ird", "Sant\u00e9"):
            # each side takes its own argument: the standalone one is
            # given a column NAME and quotes it, the ETL an expression
            # that is already quoted
            self.assertEqual(
                o19_preflight.double_encoded_predicate(col),
                o19etl.double_encoded_predicate("`{0}`".format(col)),
                col)


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
