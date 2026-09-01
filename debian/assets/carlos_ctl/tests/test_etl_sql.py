# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""ETL statement-generation contracts: exact SQL for copy/merge/archive/
shadow/chunk paths, sanitizer wrapping, charset-repair injection, and the
loud pre-checks (never-truncate, NOT-NULL-needs-curation).

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import unittest

from carlos_ctl import o19etl, o19map_schema


def col(dtype="varchar", nullable=True, char_len=0, column_type=None,
        has_default=False, auto_increment=False):
    return {"type": dtype, "nullable": nullable, "char_len": char_len,
            "column_type": column_type or dtype,
            "has_default": has_default, "auto_increment": auto_increment}


class TestCopyStatement(unittest.TestCase):

    ENTRY = {"class": "copy", "cols": ["id", "name"], "chunk_by": "id"}
    DST = {"id": col("int"), "name": col("varchar")}

    def test_plain_copy(self):
        sql = o19etl.copy_statement("t", self.ENTRY, "src", "dst", self.DST)
        self.assertEqual(
            sql,
            "INSERT INTO `dst`.`t` (`id`, `name`) "
            "SELECT s.`id`, s.`name` FROM `src`.`t` s")

    def test_chunk_window_bounds_are_exclusive_inclusive(self):
        sql = o19etl.copy_statement("t", self.ENTRY, "src", "dst",
                                    self.DST, window=(100, 200))
        self.assertTrue(sql.endswith(
            "WHERE s.`id` > 100 AND s.`id` <= 200"))

    def test_rename_reads_the_source_column(self):
        entry = {"class": "copy", "cols": ["official_lang"],
                 "renames": {"official_lang": "preferred_lang"}}
        sql = o19etl.copy_statement("t", entry, "src", "dst",
                                    {"official_lang": col()})
        self.assertIn("SELECT s.`preferred_lang`", sql)
        self.assertIn("(`official_lang`)", sql)

    def test_value_expr_overrides_the_source_column(self):
        entry = {"class": "copy", "cols": ["receivedDate"],
                 "value_exprs": {
                     "receivedDate": "COALESCE(s.`observationdate`, NOW())"}}
        sql = o19etl.copy_statement("t", entry, "src", "dst",
                                    {"receivedDate": col("varchar")})
        self.assertIn("COALESCE(s.`observationdate`, NOW())", sql)

    def test_zero_date_nulls_only_when_target_nullable(self):
        entry = {"class": "copy", "cols": ["d"]}
        sql = o19etl.copy_statement(
            "t", entry, "src", "dst", {"d": col("date", nullable=True)})
        self.assertIn("NULLIF(s.`d`, '0000-00-00')", sql)
        sql = o19etl.copy_statement(
            "t", entry, "src", "dst", {"d": col("date", nullable=False)})
        self.assertNotIn("NULLIF", sql)

    def test_datetime_zero_uses_full_timestamp(self):
        entry = {"class": "copy", "cols": ["d"]}
        sql = o19etl.copy_statement(
            "t", entry, "src", "dst", {"d": col("datetime")})
        self.assertIn("'0000-00-00 00:00:00'", sql)

    def test_enum_out_of_set_falls_to_null_or_first_member(self):
        entry = {"class": "copy", "cols": ["e"]}
        nullable = {"e": col("enum", nullable=True,
                             column_type="enum('a','b')")}
        sql = o19etl.copy_statement("t", entry, "src", "dst", nullable)
        self.assertIn("IN ('a', 'b')", sql)
        self.assertIn("ELSE NULL", sql)
        strict = {"e": col("enum", nullable=False,
                           column_type="enum('a','b')")}
        sql = o19etl.copy_statement("t", entry, "src", "dst", strict)
        self.assertIn("ELSE 'a'", sql)

    def test_charset_repair_wraps_only_flagged_columns(self):
        entry = {"class": "copy", "cols": ["name", "note"]}
        dst = {"name": col(), "note": col()}
        sql = o19etl.copy_statement("t", entry, "src", "dst", dst,
                                    repaired={"note"})
        self.assertIn(
            "CONVERT(BINARY CONVERT(s.`note` USING latin1) USING utf8mb4)",
            sql)
        self.assertIn("s.`name`,", sql.replace("SELECT ", ""))
        self.assertNotIn("CONVERT(BINARY CONVERT(s.`name`", sql)


class TestMergeStatement(unittest.TestCase):

    def test_anti_join_on_natural_key(self):
        entry = {"class": "merge", "cols": ["name", "value"],
                 "merge_keys": ["name"]}
        dst = {"name": col(), "value": col()}
        sql = o19etl.merge_statement("t", entry, "src", "dst", dst)
        self.assertIn("WHERE NOT EXISTS (SELECT 1 FROM `dst`.`t` d "
                      "WHERE d.`name` <=> s.`name`)", sql)

    def test_surrogate_pk_is_excluded_from_the_insert(self):
        entry = {"class": "merge", "cols": ["id", "type"],
                 "merge_keys": ["type"], "surrogate_pk": "id"}
        dst = {"id": col("int"), "type": col()}
        sql = o19etl.merge_statement("t", entry, "src", "dst", dst)
        self.assertNotIn("`id`", sql.split("SELECT")[0])
        self.assertIn("(`type`)", sql)


class TestArchiveAndShadow(unittest.TestCase):

    def test_archive_rebuilds_deterministically(self):
        stmts = o19etl.archive_statements("formONAR", "src", "arch")
        self.assertEqual(stmts[0],
                         "DROP TABLE IF EXISTS `arch`.`formONAR`")
        self.assertIn("CREATE TABLE `arch`.`formONAR` LIKE "
                      "`src`.`formONAR`", stmts[1])
        self.assertIn("INSERT INTO `arch`.`formONAR` SELECT * FROM "
                      "`src`.`formONAR`", stmts[2])

    def test_shadow_captures_dropped_columns_with_predicate(self):
        entry = {"class": "copy", "cols": ["id", "name"], "chunk_by": "id",
                 "dropped": {"legacy": {
                     "nondefault": "s.`legacy` IS NOT NULL"}}}
        src = {"id": col("int"), "name": col(), "legacy": col()}
        stmts = o19etl.shadow_statements("t", entry, "src", "arch", src)
        self.assertEqual(len(stmts), 2)
        self.assertIn("`arch`.`t__dropped`", stmts[1])
        self.assertIn("WHERE (s.`legacy` IS NOT NULL)", stmts[1])
        self.assertIn("s.`id`", stmts[1])


class TestChunkWindows(unittest.TestCase):

    def test_windows_cover_the_range_exactly_once(self):
        w = o19etl.chunk_windows(1, 120, size=50)
        self.assertEqual(w, [(0, 50), (50, 100), (100, 120)])

    def test_empty_table_yields_no_windows(self):
        self.assertEqual(o19etl.chunk_windows(0, 0), [(-1, 0)])
        self.assertEqual(o19etl.chunk_windows(1, 0), [])


class TestPrechecks(unittest.TestCase):

    def test_not_null_column_without_default_is_flagged(self):
        entry = {"class": "copy", "cols": ["id"]}
        dst = {"id": col("int"),
               "mustSet": col(nullable=False, has_default=False),
               "hasDefault": col(nullable=False, has_default=True),
               "autoInc": col("int", nullable=False, auto_increment=True),
               "nullableExtra": col(nullable=True)}
        self.assertEqual(o19etl.missing_required_columns(entry, dst),
                         ["mustSet"])

    def test_value_exprs_satisfy_required_columns(self):
        entry = {"class": "copy", "cols": ["id"],
                 "value_exprs": {"mustSet": "NOW()"}}
        dst = {"id": col("int"),
               "mustSet": col(nullable=False, has_default=False)}
        self.assertEqual(o19etl.missing_required_columns(entry, dst), [])

    def test_overlength_precheck_targets_only_narrower_columns(self):
        entry = {"class": "copy", "cols": ["a", "b"]}
        dst = {"a": col(char_len=10), "b": col(char_len=50)}
        src = {"a": col(char_len=30), "b": col(char_len=50)}
        checks = o19etl.overlength_precheck_sql("t", entry, "src", dst, src)
        self.assertEqual([c for c, _ in checks], ["a"])
        self.assertIn("CHAR_LENGTH(s.`a`) > 10", checks[0][1])


class TestEnumValues(unittest.TestCase):

    def test_parses_enum_members(self):
        self.assertEqual(o19etl.enum_values("enum('x','y')"), ["x", "y"])
        self.assertEqual(o19etl.enum_values("enum('RO','NR','TE')"),
                         ["RO", "NR", "TE"])
        self.assertEqual(o19etl.enum_values("varchar(10)"), [])


class TestManifestDrivenGeneration(unittest.TestCase):
    """The generators must work over every real manifest entry, not just
    synthetic ones — catch malformed entries at test time."""

    def test_every_copy_entry_generates_valid_sql_shape(self):
        for table, entry in o19map_schema.TABLES.items():
            if entry["class"] != "copy":
                continue
            dst = {c: col() for c in entry["cols"]}
            sql = o19etl.copy_statement(table, entry, "src", "dst", dst)
            self.assertTrue(sql.startswith(
                "INSERT INTO `dst`.`{0}`".format(table)))
            # exactly one SELECT keyword (columns like SELECT_OPTION_ID
            # exist in the real schema — match the keyword with spaces)
            self.assertEqual(sql.count(") SELECT "), 1)

    def test_every_merge_entry_generates_anti_join(self):
        for table, entry in o19map_schema.TABLES.items():
            if entry["class"] != "merge":
                continue
            dst = {c: col() for c in entry["cols"]}
            sql = o19etl.merge_statement(table, entry, "src", "dst", dst)
            self.assertIn("NOT EXISTS", sql)
            for k in entry["merge_keys"]:
                self.assertIn("d.`{0}` <=> s.`{0}`".format(k), sql)


if __name__ == "__main__":
    unittest.main()
