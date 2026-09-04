# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The content digest: the parts of it that, done naively, make the
check worthless.

Every assertion here corresponds to a case measured against MariaDB
10.11 before the module was written. They are unit tests on the SQL the
module builds, not on a live server -- the suite must run without a
database -- so each one pins the FEATURE of the expression that makes
the measured case come out right.

Run (from debian/assets):
    python3 -m unittest carlos_ctl.tests.test_digest -v
"""

import unittest

from carlos_ctl import o19digest


class TestTheRowEncodingIsUnambiguous(unittest.TestCase):

    """`CONCAT_WS` skips NULLs, so ('a', NULL, 'c') and ('a', 'c', NULL)
    hash IDENTICALLY unless every column contributes something. A
    column-swap bug would verify clean -- measured, not supposed."""

    TYPES = {"a": "varchar", "b": "varchar", "c": "varchar"}

    def test_every_column_contributes_even_when_null(self):
        expr = o19digest.row_hash_expr(["a", "b", "c"], self.TYPES)
        # one marker per column, or a NULL vanishes from the encoding
        self.assertEqual(expr.count(o19digest.NULL_MARK), 3)

    def test_a_value_is_length_prefixed(self):
        # the prefix is what makes the encoding prefix-free: without it a
        # literal marker in clinic data forges a NULL
        expr = o19digest.value_expr("a", "varchar")
        self.assertIn("CHAR_LENGTH(", expr)
        self.assertIn("':'", expr)

    def test_the_null_marker_cannot_be_a_value(self):
        # a value always contributes "<digits>:...", the marker does not
        self.assertFalse(o19digest.NULL_MARK.strip("'")[0].isdigit())

    def test_a_column_list_must_not_be_empty(self):
        with self.assertRaises(ValueError):
            o19digest.row_hash_expr([], {})


class TestTheTwoLanesAreIndependent(unittest.TestCase):

    """One aggregate is not enough, and the reasons differ.

    BIT_XOR cannot see a deleted IDENTICAL PAIR -- two equal rows cancel
    (measured: the digest is unchanged). SUM alone promotes to DOUBLE
    past 2^53 and goes silently inexact."""

    def sql(self):
        return o19digest.digest_sql("s", "t", ["a"], {"a": "varchar"})

    def test_both_lanes_are_present(self):
        sql = self.sql()
        self.assertIn("SUM(", sql)
        self.assertIn("BIT_XOR(", sql)

    def test_the_sum_lane_is_exact_not_floating(self):
        # DECIMAL(30, 0), or a 20M-row clinic's sum is approximate and
        # two different tables compare equal
        self.assertIn("AS DECIMAL(30, 0)", self.sql())

    def test_the_lanes_read_different_halves_of_the_hash(self):
        # so a collision in one half cannot take the other with it
        sql = self.sql()
        self.assertIn("SUBSTR(SHA2", sql)
        self.assertIn(", 1, 16)", sql)
        self.assertIn(", 17, 16)", sql)

    def test_an_empty_table_digests_to_zero_not_null(self):
        # SUM and BIT_XOR are NULL over no rows; a NULL would compare
        # unequal to a genuine zero and report a phantom mismatch
        # each LANE, specifically -- value_expr uses IFNULL per column
        # too, so counting them all measures the wrong thing
        sql = self.sql()
        self.assertIn("IFNULL(SUM(", sql)
        self.assertIn("IFNULL(BIT_XOR(", sql)
        self.assertEqual(o19digest.Digest.from_row(["0", "", ""]),
                         o19digest.Digest(0, 0, 0))


class TestValuesAreNormalisedBeforeHashing(unittest.TestCase):

    """The clinic stores latin1 and staging is utf8mb4, so the same
    logical text has different STORED BYTES ('Santé' is 53 61 6E 74 E9
    there and 53 61 6E 74 C3 A9 here). Hashing the bytes would disagree
    on every accented row of every clinic."""

    def test_text_is_converted_to_one_charset(self):
        self.assertIn("CONVERT(`v` USING utf8mb4)",
                      o19digest.value_expr("v", "varchar"))

    def test_a_binary_column_is_hexed_not_converted(self):
        # running a scanned document through a character set is not a
        # round trip
        for t in ("blob", "longblob", "varbinary"):
            expr = o19digest.value_expr("doc", t)
            self.assertIn("HEX(`doc`)", expr)
            self.assertNotIn("USING utf8mb4", expr)

    def test_the_length_prefix_measures_the_rendered_form(self):
        # prefixing the raw column while hashing the converted one would
        # describe something other than what is hashed
        expr = o19digest.value_expr("v", "varchar")
        self.assertIn("CHAR_LENGTH(CONVERT(`v` USING utf8mb4))", expr)

    def test_an_identifier_with_a_backtick_is_quoted(self):
        self.assertIn("`we``ird`", o19digest.value_expr("we`ird", "varchar"))


class TestTheComparisonSaysWhatWentWrong(unittest.TestCase):

    """A mismatch report an operator cannot act on is a mismatch report
    nobody acts on."""

    def test_agreement_is_silent(self):
        d = o19digest.Digest(5, 10, 20)
        self.assertEqual(o19digest.compare("t", d, d), [])

    def test_a_lost_row_is_reported_as_a_count(self):
        lines = o19digest.compare("t", o19digest.Digest(5, 10, 20),
                                  o19digest.Digest(4, 8, 19))
        self.assertEqual(len(lines), 1)
        self.assertIn("5 row(s) expected, 4 found", lines[0])

    def test_same_count_different_content_says_so(self):
        # THE case the count-only parities have always missed
        lines = o19digest.compare("t", o19digest.Digest(5, 10, 20),
                                  o19digest.Digest(5, 11, 21))
        self.assertEqual(len(lines), 1)
        self.assertIn("CONTENT differs", lines[0])
        self.assertIn("same number of rows", lines[0])

    def test_a_short_row_is_refused_not_guessed(self):
        with self.assertRaises(ValueError):
            o19digest.Digest.from_row(["1", "2"])


if __name__ == "__main__":
    unittest.main()
