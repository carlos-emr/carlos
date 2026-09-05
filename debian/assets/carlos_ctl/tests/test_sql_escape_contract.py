# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The SQL escape has one implementation, and one deliberate copy.

Run (from debian/assets):
    python3 -m unittest carlos_ctl.tests.test_sql_escape_contract -v
"""

import ast
import unittest
from pathlib import Path

from carlos_ctl import (dbops, o19digest, o19docs, o19etl, o19map_schema,
                        o19_preflight, o19props, util)

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
    # the CRLF case on its own, and a bare CR beside it: the mysql client
    # strips the CR of a CRLF from its stdin before the server sees the
    # statement -- inside a quoted literal too -- so an unescaped CR was
    # silently dropped from the stored value, while a bare CR survived
    "role\r\nname",
    "bare\rcarriage",
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


class TestTheTransportsOwnRule(unittest.TestCase):

    """One escape here is about the CLIENT, not the server, so the
    "agrees with the other copy" tests cannot catch its removal: drop it
    from both and they still agree.

    Every statement this tool runs is fed to the mariadb CLI on stdin,
    and the client strips the CR of a CRLF as a line terminator before
    the server parses the statement -- inside a quoted literal too.
    Measured on MariaDB 10.11 through the real `util.sql_escape`:
    `'a\r\nb'` was stored as `a\nb` (one byte gone), while a bare CR,
    a lone LF and Ctrl-Z all survived. The clinic values that reach a
    hand-built literal are role names and secObjPrivilege.objectName, so
    the damage is a role written under a spelling that no longer matches
    secUserRole.role_name -- grants that exist and grant nothing."""

    def test_a_carriage_return_is_escaped_by_both_copies(self):
        for fn in (util.sql_escape, o19_preflight._sql_literal):
            self.assertEqual(fn("a\r\nb"), "a\\r\nb")
            self.assertEqual(fn("bare\rcr"), "bare\\rcr")
            # and the LF beside it is left alone: it survives intact and
            # escaping it would be a change with no reason behind it
            self.assertEqual(fn("a\nb"), "a\nb")

    def test_the_other_conditions_are_still_unescaped(self):
        # the docstring's remaining claims, pinned so a future edit that
        # "tidies" them has to face the measurement
        for fn in (util.sql_escape, o19_preflight._sql_literal):
            self.assertEqual(fn('say "hi"'), 'say "hi"')
            self.assertEqual(fn("ctrl\x1az"), "ctrl\x1az")


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

    #: server messages the two absent-object predicates must agree on.
    #: The last three are the reason the text regex carries a negative
    #: lookahead: 1932 is a CORRUPT table, not an absent one.
    ERRORS = [
        "ERROR 1146 (42S02) at line 1: Table 'o19.x' doesn't exist",
        "ERROR 1054 (42S22) at line 1: Unknown column 'disabled'",
        "ERROR 1045 (28000): Access denied for user 'root'@'localhost'",
        "ERROR 2002 (HY000): Can't connect to local server",
        "ERROR 1064 (42000): You have an error in your SQL syntax",
        "Unknown column 'disabled' in 'field list'",
        "Table 'o19.formAR' doesn't exist",
        "ERROR 1932 (42S02): Table 'o19.x' doesn't exist in engine",
        "Table 'o19.x' doesn't exist in engine",
        "Table 'o19.x' doesn't exist IN ENGINE",
        "",
    ]

    def test_the_absent_object_predicate_is_the_same_predicate(self):
        """This is the one helper that decides no-go vs shrug: an error
        it calls absent is downgraded to an INFO, and everything else
        stops the import. Drift here means one side reads a corrupt
        table (1932) as merely absent while the other calls it a
        no-go."""
        class _Failure(Exception):
            def __init__(self, stderr):
                Exception.__init__(self, stderr)
                self.stderr = stderr

        for text in self.ERRORS:
            self.assertEqual(
                o19_preflight._absent_object(text),
                o19etl.absent_object_error(_Failure(text)),
                text)

    def test_a_corrupt_table_is_absent_to_neither_side(self):
        # pinned separately from the agreement test above: two copies
        # that lost the lookahead together would still agree
        for text in ("ERROR 1932 (42S02): Table 'o19.x' doesn't exist "
                     "in engine",
                     "Table 'o19.x' doesn't exist in engine"):
            self.assertFalse(o19_preflight._absent_object(text), text)

    def test_the_absent_object_constants_agree(self):
        self.assertEqual(tuple(o19_preflight._ABSENT_OBJECT_CODES),
                         tuple(o19etl.ABSENT_OBJECT_CODES))
        for a, b in ((o19_preflight._ERROR_CODE_RE, o19etl.ERROR_CODE_RE),
                     (o19_preflight._ABSENT_OBJECT_TEXT_RE,
                      o19etl.ABSENT_OBJECT_TEXT_RE)):
            self.assertEqual((a.pattern, a.flags), (b.pattern, b.flags))

    def test_both_sides_accept_the_same_identifiers(self):
        """The preflight's copy says it is 'the identifier class
        o19etl.IDENTIFIER_RE accepts'. Tighten one and the assessment
        passes names the import then refuses before its first write."""
        for name in ("demographic", "billing_on_item", "a$b", "_x", "9",
                     "", "a b", "a-b", "a`b", "a'b", "a;b", "sant\u00e9",
                     "a\nb", "a.b", "A" * 64):
            self.assertEqual(
                bool(o19_preflight.IDENTIFIER_RE.match(name)),
                bool(o19etl.IDENTIFIER_RE.match(name)), repr(name))

    def test_both_batch_decoders_decode_the_same_way(self):
        """o19docs calls itself 'the ONE place batch escapes are
        decoded'; the preflight carries a second one. A value decoded
        differently on the two sides is a clinic value the assessment
        read and the import did not."""
        for raw in ("plain", "a\\tb", "a\\nb", "a\\0b", "a\\\\b",
                    "a\\qb", "trailing\\", "\\", "",
                    "tab\\tand\\nnewline", "NULL"):
            self.assertEqual(o19_preflight._unescape_batch(raw),
                             o19docs.unescape_batch_field(raw), repr(raw))

    def test_the_required_table_list_is_the_manifest_list(self):
        # the preflight refuses a dump the import would refuse; that is
        # only true while the two lists are the same list
        self.assertEqual(list(o19_preflight.REQUIRED_TABLES),
                         list(o19map_schema.REQUIRED_TABLES))

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


class TestTheStandaloneDigestIsTheSameDigest(unittest.TestCase):

    """The content digest exists twice, and MUST hash identically.

    The clinic's live database is the only place the pre-dump content can
    be measured, and `o19_preflight.py` is the only tool that runs there
    -- so the digest SQL is duplicated into it, exactly as the escape and
    the property parser are. Drift here does not fail loudly: it makes
    the P2 transfer check disagree on a CORRECT migration, which is the
    failure mode that gets a check switched off. So compare the SQL TEXT,
    character for character, not merely the shape."""

    #: column names and information_schema DATA_TYPEs chosen so that any
    #: divergence in quoting, in the binary/text split, in the NULL
    #: marker or in the length prefix shows up in at least one case
    COLUMNS = [
        ("demographic_no", "int"),
        ("last_name", "varchar"),
        ("note", "text"),
        ("we`ird", "varchar"),
        ("contents", "blob"),
        ("thumb", "mediumblob"),
        ("raw", "varbinary"),
        ("fixed", "binary"),
        ("BLOB_UPPER", "BLOB"),          # the type match folds case
        ("flags", "bit"),                # CONVERT is not injective here
        ("shape", "geometry"),
        ("money", "decimal"),            # HEX would round it
        ("when", "datetime"),
        ("kind", "enum"),
    ]

    #: types neither side has a safe rendering for; both must refuse
    UNKNOWN = ["", None, "widget", "inet7", "BLOBB"]

    def test_one_column_renders_identically(self):
        for col, coltype in self.COLUMNS:
            self.assertEqual(
                o19_preflight.digest_value_expr(col, coltype),
                o19digest.value_expr(col, coltype),
                "{0} ({1})".format(col, coltype))

    def test_a_whole_row_hashes_identically(self):
        names = [c for c, _t in self.COLUMNS]
        types = dict((c, t) for c, t in self.COLUMNS)
        self.assertEqual(
            o19_preflight.digest_row_hash_expr(names, types),
            o19digest.row_hash_expr(names, types))

    def test_both_refuse_the_same_unknown_types(self):
        """A type in neither list is refused, not guessed at: CONVERT is
        not injective over binary values and HEX rounds a decimal, so the
        wrong guess yields a digest that AGREES while the data differs.
        The two sides must refuse the SAME set, or a clinic measures a
        table the import cannot, or the reverse."""
        for coltype in self.UNKNOWN:
            with self.assertRaises(ValueError):
                o19_preflight.digest_value_expr("v", coltype)
            with self.assertRaises(ValueError):
                o19digest.value_expr("v", coltype)

    def test_the_two_type_lists_are_the_same_two_lists(self):
        self.assertEqual(tuple(o19_preflight.DIGEST_HEXED_TYPES),
                         tuple(o19digest.HEXED_TYPES))
        self.assertEqual(tuple(o19_preflight.DIGEST_CONVERTED_TYPES),
                         tuple(o19digest.CONVERTED_TYPES))
        # the third list decides which values are hashed on their own
        # before the join; one side hashing and the other concatenating
        # is a guaranteed disagreement on every table with a TEXT
        self.assertEqual(tuple(o19_preflight.DIGEST_LARGE_TYPES),
                         tuple(o19digest.LARGE_TYPES))

    def test_no_type_is_in_both_lists(self):
        # a type in both would render one way here and the other way
        # there the day either tuple is reordered
        self.assertEqual(
            set(o19digest.HEXED_TYPES) & set(o19digest.CONVERTED_TYPES),
            set())

    def test_both_refuse_a_row_with_no_columns(self):
        # a table with no columns cannot exist, but a column list lost on
        # the way in would otherwise hash the empty string for every row
        with self.assertRaises(ValueError):
            o19_preflight.digest_row_hash_expr([], {})
        with self.assertRaises(ValueError):
            o19digest.row_hash_expr([], {})

    def test_the_table_query_is_identical_qualified_and_not(self):
        names = [c for c, _t in self.COLUMNS]
        types = dict((c, t) for c, t in self.COLUMNS)
        for schema in (None, "o19_import", "we`ird"):
            for where in (None, "demographic_no > 0"):
                self.assertEqual(
                    o19_preflight.digest_sql(schema, "demo`table", names,
                                             types, where),
                    o19digest.digest_sql(schema, "demo`table", names,
                                         types, where),
                    "{0!r} / {1!r}".format(schema, where))

    def test_the_document_entry_is_the_same_shape(self):
        cols = [("a", "int"), ("b", "varchar")]
        big = 2 ** 70 + 3           # past what a JSON reader keeps exact
        self.assertEqual(
            o19_preflight.digest_entry(cols, 7, big, 11, 1),
            o19digest.digest_entry(cols, o19digest.Digest(7, big, 11, 1)))

    def test_the_document_version_agrees(self):
        # a clinic emitting one version and an import reading another
        # would compare under the wrong rules; both sides refuse instead,
        # which only works if the constant is one number in two files
        self.assertEqual(o19_preflight.DIGEST_FORMAT,
                         o19digest.DIGEST_FORMAT)

    def test_the_constants_themselves_agree(self):
        self.assertEqual(o19_preflight.DIGEST_SEP, o19digest.SEP)
        self.assertEqual(o19_preflight.DIGEST_NULL_MARK,
                         o19digest.NULL_MARK)
        # the session time zone decides how a TIMESTAMP renders; one side
        # pinning it and the other not is a guaranteed false alarm
        self.assertEqual(o19_preflight.DIGEST_UTC_SESSION,
                         o19digest.UTC_SESSION)


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
