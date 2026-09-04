# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""ETL statement-generation contracts: exact SQL for copy/merge/archive/
shadow/chunk paths, sanitizer wrapping, charset-repair injection, and the
loud pre-checks (never-truncate, NOT-NULL-needs-curation).

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import ast
import inspect
import os
import textwrap
import unittest

from carlos_ctl import o19etl, o19map_schema


def col(dtype="varchar", nullable=True, char_len=0, column_type=None,
        has_default=False, auto_increment=False, octet_len=None):
    return {"type": dtype, "nullable": nullable, "char_len": char_len,
            "column_type": column_type or dtype,
            "octet_len": char_len if octet_len is None else octet_len,
            "has_default": has_default, "auto_increment": auto_increment}


def selected_expr(sql, target_col):
    """The SELECT expression an INSERT ... SELECT feeds into target_col
    (positional pairing of the column list and the select list, splitting
    on top-level commas only)."""
    head, rest = sql.split(") SELECT ", 1)
    targets = [c.strip("` ") for c in head.split("(", 1)[1].split(",")]
    body = rest.rsplit(" FROM ", 1)[0]
    exprs, depth, cur = [], 0, ""
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            exprs.append(cur.strip())
            cur = ""
        else:
            cur += ch
    exprs.append(cur.strip())
    return dict(zip(targets, exprs))[target_col]


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

    def test_enum_out_of_set_prefers_the_introspected_default(self):
        entry = {"class": "copy", "cols": ["e"]}
        info = col("enum", nullable=True, column_type="enum('a','b')",
                   has_default=True)
        info["default"] = "b"
        sql = o19etl.copy_statement("t", entry, "src", "dst", {"e": info})
        self.assertIn("ELSE 'b' END", sql)
        counts = o19etl.enum_fallback_count_sql("t", entry, "src",
                                                {"e": info})
        self.assertEqual(len(counts), 1)
        self.assertEqual(counts[0][0], "e")
        self.assertIn("s.`e` NOT IN ('a', 'b')", counts[0][1])
        self.assertIn("s.`e` IS NOT NULL", counts[0][1])
        # nullable target: a NULL source value is not a fallback
        self.assertNotIn("OR s.`e` IS NULL", counts[0][1])
        strict = dict(info, nullable=False)
        counts = o19etl.enum_fallback_count_sql("t", entry, "src",
                                                {"e": strict})
        # NOT NULL target: a NULL source value falls back too, so count it
        self.assertIn("OR s.`e` IS NULL", counts[0][1])

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

    def test_merge_exclude_predicate_is_appended(self):
        entry = {"class": "merge", "cols": ["name", "value"],
                 "merge_keys": ["name"],
                 "merge_exclude": "s.`name` LIKE '\\_pmm%'"}
        dst = {"name": col(), "value": col()}
        sql = o19etl.merge_statement("t", entry, "src", "dst", dst)
        self.assertTrue(sql.endswith(" AND NOT (s.`name` LIKE '\\_pmm%')"))

    def test_privilege_and_history_value_exprs_reach_the_sql(self):
        # the manifest promises these (plan §4.3 / §4.5)
        doc = o19map_schema.TABLES["document"]
        sql = o19etl.copy_statement("document", doc, "src", "dst",
                                    {c: col() for c in doc["cols"]})
        # bound to the TARGET column, not merely present somewhere in the
        # select list (observationdate is copied as itself too)
        self.assertEqual(selected_expr(sql, "receivedDate"),
                         "s.`observationdate`")
        tick = o19map_schema.TABLES["tickler"]
        sql = o19etl.copy_statement("tickler", tick, "src", "dst",
                                    {c: col() for c in tick["cols"]})
        self.assertEqual(selected_expr(sql, "creation_date"),
                         "COALESCE(NULLIF(NULLIF(s.`update_date`, "
                         "'0001-01-01 00:00:00'), '0000-00-00 00:00:00'), "
                         "NULLIF(NULLIF(s.`service_date`, "
                         "'0001-01-01 00:00:00'), '0000-00-00 00:00:00'), "
                         "'1970-01-02 00:00:00')")

    def test_surrogate_pk_is_excluded_from_the_insert(self):
        entry = {"class": "merge", "cols": ["id", "type"],
                 "merge_keys": ["type"], "surrogate_pk": "id"}
        dst = {"id": col("int"), "type": col()}
        sql = o19etl.merge_statement("t", entry, "src", "dst", dst)
        self.assertNotIn("`id`", sql.split("SELECT")[0])
        self.assertIn("(`type`)", sql)


class TestSurrogateIdRemap(unittest.TestCase):
    """A merged parent's appended rows get new ids; children declared in
    fk_remap read their key through the parent's id map."""

    PARENT = {"class": "merge", "cols": ["id", "name"],
              "merge_keys": ["name"], "surrogate_pk": "id"}
    CHILD = {"class": "merge", "cols": ["id", "lookupListId", "value"],
             "merge_keys": ["lookupListId", "value"], "surrogate_pk": "id",
             "fk_remap": {"lookupListId": "LookupList"}}
    DST = {"id": col("int"), "lookupListId": col("int"), "value": col()}

    PARENT_DST = {"id": col("int"), "name": col()}

    def test_idmap_pairs_natural_key_twins_by_row_number(self):
        # two staging rows sharing the natural key must map to two DISTINCT
        # target rows (MIN() would fold both onto one id and lose a row)
        stmts = o19etl.idmap_statements("LookupList", self.PARENT, "src",
                                        "dst", "arch", self.PARENT_DST)
        self.assertEqual(len(stmts), 3)
        self.assertIn("`arch`.`LookupList__idmap`", stmts[1])
        self.assertIn("old_id BIGINT NOT NULL PRIMARY KEY", stmts[1])
        # the SOURCE partitions on the expression the insert stores, so
        # a key the manifest rewrites cannot split one target row's twins
        # across two partitions (both would then map to the same new id)
        self.assertIn("ROW_NUMBER() OVER (PARTITION BY s.`name` ORDER BY "
                      "s.`id`) AS rn FROM `src`.`LookupList` s", stmts[2])
        self.assertIn("ROW_NUMBER() OVER (PARTITION BY `name` ORDER BY "
                      "`id`) AS rn FROM `dst`.`LookupList`", stmts[2])
        self.assertIn("ON d.`name` <=> s.`name` AND s.rn = d.rn", stmts[2])
        self.assertNotIn("MIN(", stmts[2])
        # a surplus twin (its key already satisfied by a seed row, so the
        # anti-join appended nothing) falls back to the target's first row
        self.assertIn("LEFT JOIN", stmts[2])
        self.assertIn("d1 ON d1.`name` <=> s.`name` AND d1.rn = 1", stmts[2])
        self.assertIn("COALESCE(d.`id`, d1.`id`)", stmts[2])
        self.assertTrue(stmts[2].endswith("IS NOT NULL"))

    def test_the_source_partition_uses_the_rewritten_key(self):
        # value_exprs maps '' to NULL: two source rows with '' and NULL
        # insert as one key and must not land in two partitions
        entry = dict(self.PARENT)
        entry["value_exprs"] = {"name": "NULLIF(s.`name`, '')"}
        stmts = o19etl.idmap_statements("LookupList", entry, "src", "dst",
                                        "arch", self.PARENT_DST)
        self.assertIn("PARTITION BY NULLIF(s.`name`, '')", stmts[2])
        self.assertIn("d.`name` <=> NULLIF(s.`name`, '')", stmts[2])
        self.assertIn("d1.`name` <=> NULLIF(s.`name`, '')", stmts[2])

    def test_no_idmap_without_surrogate(self):
        self.assertEqual(o19etl.idmap_statements(
            "t", {"class": "merge", "cols": ["k"], "merge_keys": ["k"]},
            "src", "dst", "arch", {"k": col()}), [])

    def test_child_reads_the_fk_through_the_map(self):
        # NOT NULL child key: an unmapped id falls back to the raw value
        # (better a dangling id than a rejected row)
        expr = o19etl.source_expr(self.CHILD, "lookupListId",
                                  archive_schema="arch")
        self.assertEqual(
            expr, "IFNULL((SELECT m.new_id FROM `arch`.`LookupList__idmap` "
                  "m WHERE m.old_id = s.`lookupListId`), s.`lookupListId`)")
        # without an archive schema (pure golden tests) the raw column
        self.assertEqual(o19etl.source_expr(self.CHILD, "lookupListId"),
                         "s.`lookupListId`")

    def test_nullable_child_fk_becomes_null_when_unmapped(self):
        # a nullable key must not silently keep an id that no longer
        # exists on the target: NULL, and the fk report names the count
        expr = o19etl.source_expr(self.CHILD, "lookupListId",
                                  archive_schema="arch", nullable=True)
        self.assertEqual(
            expr, "(SELECT m.new_id FROM `arch`.`LookupList__idmap` m "
                  "WHERE m.old_id = s.`lookupListId`)")
        counts = o19etl.fk_unmapped_count_sql("LookupListItem", self.CHILD,
                                              "src", "arch")
        self.assertEqual([(c, p) for c, p, _ in counts],
                         [("lookupListId", "LookupList")])
        self.assertIn("NOT EXISTS", counts[0][2])
        self.assertIn("`arch`.`LookupList__idmap`", counts[0][2])

    def test_child_merge_anti_join_uses_the_remapped_key(self):
        sql = o19etl.merge_statement("LookupListItem", self.CHILD, "src",
                                     "dst", self.DST, archive_schema="arch")
        # the anti-join compares the SAME expression the insert writes
        # (the nullable column maps to NULL when unmapped, on both sides)
        self.assertIn("d.`lookupListId` <=> (SELECT m.new_id FROM "
                      "`arch`.`LookupList__idmap` m WHERE m.old_id = "
                      "s.`lookupListId`)", sql)
        self.assertIn("d.`value` <=> s.`value`", sql)
        self.assertTrue(sql.endswith("ORDER BY s.`id`"))

    def test_copy_statement_also_remaps(self):
        entry = dict(self.CHILD, **{"class": "copy"})
        sql = o19etl.copy_statement("LookupListItem", entry, "src", "dst",
                                    self.DST, archive_schema="arch")
        self.assertIn("`LookupList__idmap`", sql)

    def test_etl_order_puts_parents_first(self):
        tables = {"a_child": {"class": "copy", "fk_remap": {"x": "z_parent"}},
                  "z_parent": {"class": "merge"},
                  "m": {"class": "copy"}}
        order = o19etl.etl_order(tables)
        self.assertLess(order.index("z_parent"), order.index("a_child"))
        self.assertEqual(sorted(order), sorted(tables))

    def test_manifest_fk_parents_are_merge_tables_with_surrogates(self):
        seen = 0
        for t, e in o19map_schema.TABLES.items():
            for colname, parent in e.get("fk_remap", {}).items():
                seen += 1
                self.assertIn(colname, e["cols"], t)
                p = o19map_schema.TABLES[parent]
                self.assertEqual(p["class"], "merge", parent)
                self.assertTrue(p.get("surrogate_pk"), parent)
        self.assertGreaterEqual(seen, 2)
        order = o19etl.etl_order(o19map_schema.TABLES)
        self.assertLess(order.index("LookupList"),
                        order.index("LookupListItem"))
        self.assertLess(order.index("criteria_type"),
                        order.index("criteria_type_option"))


class TestResumeIdempotency(unittest.TestCase):

    def test_window_delete_clears_exactly_the_window(self):
        entry = {"class": "copy", "cols": ["id"], "chunk_by": "id"}
        self.assertEqual(
            o19etl.window_delete_statement("t", entry, "dst", (100, 150)),
            "DELETE FROM `dst`.`t` WHERE `id` > 100 AND `id` <= 150")

    def test_progress_ledger_is_bound_to_the_dump_digest(self):
        import shutil
        import tempfile
        d = tempfile.mkdtemp(prefix="o19progress-")
        self.addCleanup(shutil.rmtree, d)
        o19etl.save_progress(d, {"tables": {"demographic": {"done": True}},
                                 "dump_sha256": "aaa",
                                 "schema_map_version": "o19map-1"})
        same = o19etl.load_progress(d, "aaa", "o19map-1")
        self.assertTrue(same["tables"]["demographic"]["done"])
        # a different dump or manifest can never continue this ledger: the
        # target is mid-import from the OTHER dump, so the run dies
        with self.assertRaises(SystemExit):
            o19etl.load_progress(d, "bbb", "o19map-1")
        with self.assertRaises(SystemExit):
            o19etl.load_progress(d, "aaa", "o19map-2")
        # no digest given: ledger returned as-is (read-only consumers)
        self.assertIn("demographic", o19etl.load_progress(d)["tables"])

    def test_version_less_ledger_with_marks_is_refused(self):
        import shutil
        import tempfile
        d = tempfile.mkdtemp(prefix="o19progress-")
        self.addCleanup(shutil.rmtree, d)
        o19etl.save_progress(d, {"tables": {"demographic": {"done": True}},
                                 "dump_sha256": "aaa"})
        with self.assertRaises(SystemExit):
            o19etl.load_progress(d, "aaa", "o19map-1")

    def test_digest_less_ledger_with_table_marks_is_refused(self):
        # a reset would re-enter the seed block over a target that already
        # holds the admin; the remedy is the snapshot, like a foreign dump
        import shutil
        import tempfile
        d = tempfile.mkdtemp(prefix="o19progress-")
        self.addCleanup(shutil.rmtree, d)
        o19etl.save_progress(d, {"tables": {"demographic": {"done": True}}})
        with self.assertRaises(SystemExit):
            o19etl.load_progress(d, "aaa", "o19map-1")

    def test_corrupt_ledger_is_fatal_and_absent_is_fresh(self):
        import shutil
        import tempfile
        d = tempfile.mkdtemp(prefix="o19progress-")
        self.addCleanup(shutil.rmtree, d)
        self.assertEqual(o19etl.load_progress(d)["tables"], {})
        with open(os.path.join(d, "etl-progress.json"), "w") as fh:
            fh.write("{not json")
        with self.assertRaises(SystemExit):
            o19etl.load_progress(d)


class TestUnknownSchemaCapture(unittest.TestCase):

    ENTRY = {"class": "copy", "cols": ["id", "name"], "chunk_by": "id",
             "dropped": {"legacy": {"nondefault": "s.`legacy` <> ''"}}}

    def test_unknown_columns_exclude_mapped_dropped_and_case_variants(self):
        src = {"id": col("int"), "NAME": col(), "legacy": col(),
               "vendor_flag": col(), "vendor_note": col()}
        self.assertEqual(o19etl.unknown_columns(self.ENTRY, src),
                         ["vendor_flag", "vendor_note"])

    def test_unknown_column_shadow_keeps_row_context(self):
        src = {"id": col("int"), "name": col(), "legacy": col(),
               "vendor_flag": col()}
        stmts = o19etl.unknown_column_shadow_statements(
            "t", self.ENTRY, "src", "arch", src)
        self.assertEqual(len(stmts), 2)
        self.assertIn("`arch`.`t__unknown_cols`", stmts[1])
        self.assertIn("SELECT s.`id`, s.`vendor_flag` FROM `src`.`t` s "
                      "WHERE s.`vendor_flag` IS NOT NULL", stmts[1])

    def test_nothing_unknown_yields_no_statements(self):
        src = {"id": col("int"), "name": col(), "legacy": col()}
        self.assertEqual(o19etl.unknown_column_shadow_statements(
            "t", self.ENTRY, "src", "arch", src), [])


class TestCharsetScanFailures(unittest.TestCase):

    def test_scan_error_propagates_instead_of_passing_as_clean(self):
        def q(sql):
            raise RuntimeError("ERROR 1142 (42000): SELECT command denied")
        with self.assertRaises(RuntimeError):
            o19etl.detect_repairs(q, "src", ["charset-repair"])

    def test_absent_column_is_skipped_with_a_note(self):
        def q(sql):
            raise RuntimeError("ERROR 1054 (42S22): Unknown column 'x'")
        notes = []
        self.assertEqual(o19etl.detect_repairs(q, "src", [], notes), {})
        self.assertTrue(notes)
        self.assertIn("not in this dump", notes[0])


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

    def test_shadow_context_columns_use_their_source_spelling(self):
        # the manifest names the CARLOS column (isActive); the dump spells
        # it isactive — the capture must read the staged name
        entry = {"class": "copy", "cols": ["id", "isActive"],
                 "renames": {"isActive": "isactive"},
                 "dropped": {"legacy": {
                     "nondefault": "s.`legacy` IS NOT NULL"}}}
        src = {"id": col("int"), "isactive": col(), "legacy": col()}
        stmts = o19etl.shadow_statements("t", entry, "src", "arch", src)
        self.assertIn("s.`isactive`", stmts[1])
        self.assertNotIn("s.`isActive`", stmts[1])


class TestIdentifierQuoting(unittest.TestCase):
    """Names the dump chooses (unknown tables, vendor-fork columns) reach
    root-executed SQL: they are quoted with doubled backticks, and the
    ETL pre-check refuses anything outside the identifier class before
    the first write."""

    EVIL = "t1`;DROP DATABASE victim;--"

    def test_ident_doubles_embedded_backticks(self):
        self.assertEqual(o19etl.ident(self.EVIL),
                         "`t1``;DROP DATABASE victim;--`")
        self.assertEqual(o19etl.ident("plain"), "`plain`")

    def test_archive_statements_keep_a_crafted_name_one_identifier(self):
        for sql in o19etl.archive_statements(self.EVIL, "src", "arch"):
            self.assertIn("`t1``;DROP DATABASE victim;--`", sql)
            # the crafted name never closes the identifier: the only
            # backtick before ;DROP is the doubled (escaped) one
            self.assertNotRegex(sql, r"[^`]`;DROP")

    def test_unknown_column_shadow_quotes_the_dumps_column_names(self):
        entry = {"class": "copy", "cols": ["id"]}
        src_cols = {"id": col("int"), "x`; DROP TABLE y; --": col()}
        stmts = o19etl.unknown_column_shadow_statements(
            "t", entry, "src", "arch", src_cols)
        self.assertEqual(len(stmts), 2)
        self.assertIn("s.`x``; DROP TABLE y; --` IS NOT NULL", stmts[1])
        self.assertNotRegex(stmts[1], r"[^`]`; DROP")

    def test_unsafe_identifiers_lists_tables_and_columns(self):
        info = {"ok": {"id": col("int"), "bad col": col()},
                self.EVIL: {"id": col("int")}, "z$1": {"a_b": col()}}
        self.assertEqual(o19etl.unsafe_identifiers(info),
                         ["ok.bad col", self.EVIL])
        self.assertTrue(o19etl.IDENTIFIER_RE.match("secObjPrivilege"))
        self.assertFalse(o19etl.IDENTIFIER_RE.match("a-b"))
        self.assertFalse(o19etl.IDENTIFIER_RE.match(""))


class TestChunkWindows(unittest.TestCase):

    def test_windows_cover_the_range_exactly_once(self):
        w = o19etl.chunk_windows(1, 120, size=50)
        self.assertEqual(w, [(0, 50), (50, 100), (100, 120)])

    def test_degenerate_bounds_yield_one_or_no_window(self):
        # a single row with id 0 still needs one (exclusive, inclusive]
        # window; an inverted range (empty table: MIN NULL -> 1, MAX 0)
        # yields none
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


class TestEffectiveEntry(unittest.TestCase):

    ENTRY = {"class": "copy", "cols": ["id", "name", "extra"],
             "fk_remap": {"name": "Parent"}}

    def test_columns_absent_from_the_dump_are_skipped_with_a_note(self):
        adjusted, notes = o19etl.effective_entry(
            "t", self.ENTRY, {"id": col(), "NAME": col()}, {"t", "Parent"})
        self.assertEqual(adjusted["cols"], ["id", "name"])
        self.assertEqual(adjusted["fk_remap"], {"name": "Parent"})
        self.assertTrue(any("t.extra absent" in n for n in notes))

    def test_fk_remap_is_disabled_when_the_parent_is_absent(self):
        adjusted, notes = o19etl.effective_entry(
            "t", self.ENTRY, {"id": col(), "name": col(), "extra": col()},
            {"t"})
        self.assertNotIn("fk_remap", adjusted)
        self.assertTrue(any("parent table Parent absent" in n
                            for n in notes))

    def test_remap_is_dropped_for_a_column_the_dump_lacks(self):
        # the remapped FK column itself is absent: no source id to map, and
        # the fk report must not query a nonexistent column
        adjusted, notes = o19etl.effective_entry(
            "t", self.ENTRY, {"id": col(), "extra": col()}, {"t", "Parent"})
        self.assertNotIn("fk_remap", adjusted)
        self.assertEqual(adjusted["cols"], ["id", "extra"])
        self.assertTrue(any("t.name: column absent" in n for n in notes))

    def test_unchanged_entry_is_returned_as_is(self):
        adjusted, notes = o19etl.effective_entry(
            "t", self.ENTRY, {"id": col(), "name": col(), "extra": col()},
            {"t", "Parent"})
        self.assertIs(adjusted, self.ENTRY)
        self.assertEqual(notes, [])


class TestCharsetRepairPredicate(unittest.TestCase):

    def test_repair_is_per_row_and_byte_aligned(self):
        expr = o19etl.repair_expr("s.`note`")
        self.assertTrue(expr.startswith("CASE WHEN "))
        # normalised to utf8mb4 before comparing (latin1 staging tables)
        self.assertIn("LENGTH(CONVERT(s.`note` USING utf8mb4)) <> "
                      "CHAR_LENGTH(CONVERT(s.`note` USING utf8mb4))", expr)
        # doubled backslashes in the SQL text (the server's string parser
        # eats one before the regex engine sees the escape)
        # no REGEXP: the Spencer engine of MySQL < 8 misreads a \x class
        self.assertNotIn("REGEXP", expr)
        self.assertIn("CONVERT(BINARY CONVERT(s.`note` USING latin1) USING "
                      "utf8mb4)", expr)
        self.assertTrue(expr.endswith("ELSE s.`note` END"))

    def test_marker_regex_targets_utf8_lead_bytes_only(self):
        # 'Ã©' (double-encoded é) matches; 'São' (legit) does not: the
        # class is the two latin1 lead bytes followed by a continuation
        self.assertEqual(o19etl.MOJIBAKE_MARKER_RE,
                         "'[\\\\x{C3}\\\\x{C2}][\\\\x{80}-\\\\x{BF}]'")


class TestAbsentObjectDetection(unittest.TestCase):

    def test_only_the_servers_stderr_is_inspected(self):
        # the SQL in the message can carry a patient id such as 1054; the
        # verdict must come from the server's error text alone
        exc = o19etl.QueryError(
            "SQL failed (SELECT COUNT(*) FROM t WHERE demographic_no = "
            "1054): ERROR 1205 (HY000): Lock wait timeout exceeded",
            "ERROR 1205 (HY000): Lock wait timeout exceeded")
        self.assertFalse(o19etl.absent_object_error(exc))
        gone = o19etl.QueryError("SQL failed (SELECT 1 FROM x): ...",
                                 "ERROR 1146 (42S02): Table 'x' doesn't exist")
        self.assertTrue(o19etl.absent_object_error(gone))
        # a plain RuntimeError (no stderr attribute) still works on text
        self.assertTrue(o19etl.absent_object_error(
            RuntimeError("Unknown column 'q' in 'field list'")))


class TestRowParity(unittest.TestCase):

    def test_parity_flags_a_short_copy(self):
        def q(sql):
            if "information_schema" in sql:
                return [["demographic"]]
            if "`stage`.`demographic`" in sql:
                return [["100"]]
            if "`carlos`.`demographic`" in sql:
                return [["90"]]
            return [["0"]]
        ok, bad = o19etl.row_parity(q, "stage", "carlos")
        self.assertEqual(len(bad), 1)
        self.assertIn("demographic: staging 100 -> target 90", bad[0])

    def test_without_a_ledger_only_the_admin_identity_rows_are_tolerated(
            self):
        def q(sql):
            if "information_schema" in sql:
                return [["provider"], ["demographic"]]
            if "WHERE provider_no = 'p9'" in sql:
                return [["1"]]
            if "`carlos`.`provider`" in sql:
                return [["11"]]
            if "`stage`.`provider`" in sql:
                return [["10"]]
            return [["5"]]
        ok, bad = o19etl.row_parity(q, "stage", "carlos", admin_user="bg",
                                    admin_provider_no="p9")
        self.assertEqual(bad, [])
        self.assertTrue(any("+1 break-glass admin row" in line
                            for line in ok))


class TestPrecheckScope(unittest.TestCase):
    """A pre-check refusal must not tell a resumed run that nothing was
    written: the earlier phases' writes stand."""

    def setUp(self):
        import tempfile
        import shutil
        self.dir = tempfile.mkdtemp(prefix="o19scope-")
        self.addCleanup(shutil.rmtree, self.dir)

    def _ledger(self, payload):
        import json
        import os
        with open(os.path.join(self.dir, "etl-progress.json"), "w") as fh:
            json.dump(payload, fh)

    def test_untouched_target_says_nothing_was_written(self):
        self.assertEqual(o19etl.precheck_scope(self.dir),
                         "nothing was written")
        self._ledger({"tables": {}})
        self.assertEqual(o19etl.precheck_scope(self.dir),
                         "nothing was written")

    def test_a_ledger_with_work_says_no_further_writes(self):
        self._ledger({"tables": {"demographic": {"done": True}}})
        self.assertEqual(o19etl.precheck_scope(self.dir),
                         "no further writes were made")
        # the break-glass admin alone is already a write to the target
        self._ledger({"tables": {}, "admin_provider_no": "999"})
        self.assertEqual(o19etl.precheck_scope(self.dir),
                         "no further writes were made")

    def test_a_ledger_of_the_wrong_shape_fails_closed(self):
        # valid JSON that is not the writer's mapping: the scope is a
        # phrase inside someone else's refusal, so it must neither raise
        # (which would replace that refusal) nor claim the target is
        # untouched (which would send the operator down the wrong path)
        unreadable = ("the ETL ledger could not be read, so assume "
                      "earlier writes stand")
        for payload in ([], "x", 3, None, {"tables": "oops"}):
            self._ledger(payload)
            self.assertEqual(o19etl.precheck_scope(self.dir), unreadable,
                             "ledger payload {0!r}".format(payload))

    def test_an_unparseable_ledger_fails_closed_too(self):
        # not valid JSON at all, and a path that cannot be read: this
        # function only chooses a phrase for someone else's refusal, so
        # raising here would replace that refusal with a traceback
        import os
        unreadable = ("the ETL ledger could not be read, so assume "
                      "earlier writes stand")
        with open(os.path.join(self.dir, "etl-progress.json"), "w") as fh:
            fh.write("{not json at all")
        self.assertEqual(o19etl.precheck_scope(self.dir), unreadable)
        os.unlink(os.path.join(self.dir, "etl-progress.json"))
        os.mkdir(os.path.join(self.dir, "etl-progress.json"))
        self.assertEqual(o19etl.precheck_scope(self.dir), unreadable)


class TestCoercionPrecheckCuration(unittest.TestCase):

    def test_a_curated_value_expr_silences_the_refusal(self):
        # the refusal names curating one as the remedy, so it must work:
        # without the skip the operator follows the instruction and the
        # pre-check refuses forever
        entry = {"cols": ["archived"],
                 "value_exprs": {"archived": "CAST(s.`archived` AS SIGNED)"}}
        dst = {"archived": col("int")}
        src = {"archived": col("varchar")}
        self.assertEqual(
            o19etl.coercion_precheck_sql("t", entry, "stage", dst, src), [])
        # and without the entry it still refuses
        self.assertEqual(
            [c for c, _ in o19etl.coercion_precheck_sql(
                "t", {"cols": ["archived"]}, "stage", dst, src)],
            ["archived"])

    def test_a_case_differing_source_spelling_is_still_checked(self):
        # effective_entry keeps the column (it matches case-insensitively)
        # and MySQL copies it, so a case-sensitive lookup here would
        # disable the guard for exactly that column
        entry = {"cols": ["isActive"]}
        dst = {"isActive": col("int")}
        src = {"isactive": col("varchar")}
        self.assertEqual(
            [c for c, _ in o19etl.coercion_precheck_sql(
                "t", entry, "stage", dst, src)], ["isActive"])


class TestOverlengthByteCapacity(unittest.TestCase):

    def test_a_same_declared_text_column_is_measured_in_bytes(self):
        # latin1 `text` holds 65535 CHARACTERS, utf8mb4 `text` 65535
        # BYTES: identical declarations, different capacity, and the
        # copy runs under sql_mode='' so the overflow is a silent trim
        entry = {"cols": ["note"]}
        dst = {"note": col("text", char_len=65535, octet_len=65535)}
        src = {"note": col("text", char_len=65535, octet_len=65535)}
        checks = o19etl.overlength_precheck_sql("t", entry, "stage", dst,
                                                src)
        self.assertEqual([c for c, _ in checks], ["note"])
        self.assertIn("LENGTH(CONVERT(", checks[0][1])
        self.assertIn("> 65535", checks[0][1])

    def test_a_sized_column_keeps_the_character_comparison(self):
        entry = {"cols": ["city"]}
        dst = {"city": col("varchar", char_len=30)}
        src = {"city": col("varchar", char_len=60)}
        checks = o19etl.overlength_precheck_sql("t", entry, "stage", dst,
                                                src)
        self.assertIn("CHAR_LENGTH(", checks[0][1])
        # and a widening one is not checked at all
        self.assertEqual(o19etl.overlength_precheck_sql(
            "t", entry, "stage", {"city": col("varchar", char_len=60)},
            {"city": col("varchar", char_len=30)}), [])


class TestMergeReverseParity(unittest.TestCase):

    ENTRY = {"class": "merge", "cols": ["id", "code", "label"],
             "merge_keys": ["code"], "surrogate_pk": "id",
             "merge_exclude": "s.`code` = 'dead'"}
    DST = {"id": col("int"), "code": col(), "label": col()}

    def test_missing_count_is_the_reverse_anti_join(self):
        sql = o19etl.merge_missing_count_sql("t", self.ENTRY, "stage",
                                             "carlos", self.DST)
        self.assertTrue(sql.startswith(
            "SELECT COUNT(*) FROM `stage`.`t` s WHERE NOT EXISTS (SELECT 1 "
            "FROM `carlos`.`t` d WHERE d.`code` <=> s.`code`)"))
        self.assertTrue(sql.endswith("AND NOT (s.`code` = 'dead')"))

    def test_pruned_property_rows_are_not_expected_back(self):
        # the roles step deletes removed-module property rows from the
        # target after the merge; the reverse count must skip them, with
        # the same LIKE escaping the prune itself uses
        from carlos_ctl import o19roles
        pred = o19etl.pruned_property_predicate(["INTEGRATOR_", "a%b"])
        self.assertIn("s.`name` LIKE 'INTEGRATOR\\_%'", pred)
        self.assertIn("s.`name` LIKE 'a\\%b%'", pred)
        self.assertEqual(o19etl.pruned_property_predicate([]), "FALSE")
        # and it must agree with the statement that does the deleting
        stmts = o19roles.property_prune_statements("carlos", ["INTEGRATOR_"])
        self.assertIn("'INTEGRATOR\\_%'", stmts[0][2])
        sql = o19etl.merge_missing_count_sql(
            "property", self.ENTRY, "stage", "carlos", self.DST,
            exclude=pred)
        self.assertTrue(sql.endswith(
            "AND NOT (s.`code` = 'dead') AND NOT ({0})".format(pred)))

    def test_row_parity_applies_the_prune_only_to_property(self):
        entry = o19map_schema.TABLES["property"]
        dst_info = {"property": {c: col() for c in entry["cols"]}}
        seen = []

        def q(sql):
            if "information_schema" in sql:
                return [["property"]]
            seen.append(sql)
            return [["0"]]

        o19etl.row_parity(q, "stage", "carlos", dst_info=dst_info,
                          pruned_property_prefixes=["INTEGRATOR_"])
        self.assertTrue(any("INTEGRATOR\\_%" in x for x in seen), seen)

    def test_row_parity_drops_fk_remaps_whose_parent_is_absent(self):
        # the copy drops them too, so no id map exists: joining through
        # one would reference a table that was never created
        table = next((t for t, e in o19map_schema.TABLES.items()
                      if e["class"] == "merge" and e.get("fk_remap")), None)
        if table is None:
            self.skipTest("no merge table carries fk_remap")
        entry = o19map_schema.TABLES[table]
        dst_info = {table: {c: col() for c in entry["cols"]}}
        seen = []

        def q(sql):
            if "information_schema" in sql:
                return [[table]]      # the parent is NOT in the dump
            seen.append(sql)
            return [["0"]]

        # archive_schema MUST be passed: merge_join only emits the idmap
        # subquery when it is set, so without it the assertion below
        # holds whatever the production code does
        o19etl.row_parity(q, "stage", "carlos", dst_info=dst_info,
                          archive_schema="o19_archive")
        for parent in set(entry["fk_remap"].values()):
            self.assertFalse(
                any("{0}__idmap".format(parent) in x for x in seen),
                "parity joined through a missing id map for " + parent)

        # positive control: with the parent present the join IS emitted,
        # so the test above cannot pass by nothing ever being generated
        parents = sorted(set(entry["fk_remap"].values()))
        seen2 = []

        def q2(sql):
            if "information_schema" in sql:
                return [[table]] + [[p] for p in parents]
            seen2.append(sql)
            return [["0"]]

        dst2 = dict(dst_info)
        for parent in parents:
            pe = o19map_schema.TABLES[parent]
            dst2[parent] = {c: col() for c in pe["cols"]}
        o19etl.row_parity(q2, "stage", "carlos", dst_info=dst2,
                          archive_schema="o19_archive")
        self.assertTrue(
            any("{0}__idmap".format(parents[0]) in x for x in seen2),
            "the id-map join is never generated, so the prune is untested")

    def test_row_parity_checks_merge_tables_in_reverse(self):
        table = next(t for t, e in o19map_schema.TABLES.items()
                     if e["class"] == "merge")
        entry = o19map_schema.TABLES[table]
        dst_info = {table: {c: col() for c in entry["cols"]}}

        def q(sql):
            if "information_schema" in sql:
                return [[table]]
            if "NOT EXISTS" in sql:
                return [["3"]]
            return [["0"]]
        ok, bad = o19etl.row_parity(q, "stage", "carlos", dst_info=dst_info)
        self.assertTrue(any("3 staging row(s) have no target twin" in b
                            for b in bad), bad)
        # without the target columns merge tables are not judged (fakes)
        ok, bad = o19etl.row_parity(q, "stage", "carlos")
        self.assertEqual(bad, [])


class TestCoercionPrecheck(unittest.TestCase):

    def test_text_into_numeric_columns_must_parse(self):
        entry = {"class": "copy", "cols": ["archived", "name"]}
        dst = {"archived": col("tinyint"), "name": col()}
        src = {"archived": col("char", char_len=1), "name": col()}
        pairs = o19etl.coercion_precheck_sql("allergies", entry, "stage",
                                             dst, src)
        self.assertEqual([c for c, _ in pairs], ["archived"])
        sql = pairs[0][1]
        self.assertIn("TRIM(s.`archived`) <> ''", sql)
        self.assertIn("NOT REGEXP", sql)
        # same type on both sides: nothing to check
        self.assertEqual(o19etl.coercion_precheck_sql(
            "t", entry, "stage", dst, {"archived": col("tinyint"),
                                       "name": col()}), [])

    def test_numeric_literal_class_accepts_numbers_only(self):
        import re
        # the pattern travels inside a SQL string literal, whose parser
        # consumes one backslash before the regex engine sees it: model
        # that, or a `\.` that degrades to "any character" still passes
        self.assertNotIn("\\", o19etl.NUMERIC_LITERAL_SQL_RE)
        served = re.sub(r"\\(.)", r"\1", o19etl.NUMERIC_LITERAL_SQL_RE)
        rx = re.compile(served.replace("[[:space:]]", r"\s"))
        for good in ("0", "1", "-3", "+4.5", ".5", "7.", "1e3", " 12 "):
            self.assertTrue(rx.match(good), good)
        for bad in ("yes", "1,800", "12/34", "12abc", "", "-"):
            self.assertFalse(rx.match(bad), bad)


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

    def test_a_column_the_dump_lacks_is_dropped_from_both_sides(self):
        # the shape sweep above builds `dst` FROM the manifest's own
        # column list AND feeds the manifest entry unchanged, so it never
        # exercises the source-side reconciliation copy_statement relies
        # on. Here the DUMP is at a lower patch level than the manifest.
        table = next(t for t, e in o19map_schema.TABLES.items()
                     if e["class"] == "copy" and len(e["cols"]) > 2
                     and not e.get("value_exprs")
                     and not e.get("fk_remap")
                     and not e.get("renames"))
        entry = o19map_schema.TABLES[table]
        absent = entry["cols"][-1]
        src_cols = {c: col() for c in entry["cols"] if c != absent}
        effective, notes = o19etl.effective_entry(table, entry, src_cols)
        self.assertTrue(any(absent in n for n in notes), notes)
        dst = {c: col() for c in entry["cols"]}
        sql = o19etl.copy_statement(table, effective, "src", "dst", dst)
        self.assertNotIn("`{0}`".format(absent), sql,
                         "a column the dump does not carry is still named "
                         "in the copy; the target default must stand")
        for keep in entry["cols"][:-1]:
            self.assertIn("`{0}`".format(keep), sql)

    def test_every_merge_entry_generates_anti_join(self):
        for table, entry in o19map_schema.TABLES.items():
            if entry["class"] != "merge":
                continue
            dst = {c: col() for c in entry["cols"]}
            sql = o19etl.merge_statement(table, entry, "src", "dst", dst)
            self.assertIn("NOT EXISTS", sql)
            for k in entry["merge_keys"]:
                # the anti-join compares the SAME expression the insert
                # stores (a value_exprs key such as property.provider_no
                # joins through its NULLIF, not the raw column)
                self.assertIn("d.`{0}` <=> {1}".format(
                    k, o19etl.source_expr(entry, k)), sql)


class TestChunkSpanRefusal(unittest.TestCase):
    """The windowed-copy id-range bound, evaluated before any write.

    run_etl calls this BEFORE the replace_seed DELETE and the ``started``
    checkpoint, so its "nothing has been written for this table" promise
    is true. A refusal raised after the DELETE would have destroyed the
    target's rows while claiming it had not.
    """

    def test_an_ordinary_id_range_is_copyable(self):
        self.assertIsNone(
            o19etl.chunk_span_refusal("log", "id", 1, 4_000_000))

    def test_a_range_at_the_bound_is_still_copyable(self):
        hi = o19etl.CHUNK_ROWS * (o19etl.MAX_CHUNK_WINDOWS - 1)
        self.assertIsNone(o19etl.chunk_span_refusal("log", "id", 0, hi))

    def test_a_bigint_outlier_is_refused_naming_the_span(self):
        msg = o19etl.chunk_span_refusal("log", "id", 1, 2 ** 62)
        self.assertIsNotNone(msg)
        self.assertIn("log", msg)
        self.assertIn("id", msg)
        self.assertIn("Nothing has been written for this table", msg)
        # ...and the remedy has to be one the operator can reach from
        # here: by the time a table is being copied the ledger already
        # names the break-glass admin, so --restage is refused and the
        # only way out is the pre-import snapshot
        self.assertIn("restore the pre-import snapshot", msg)

    def test_the_refusal_precedes_every_write_in_the_copy_path(self):
        # the ordering is the whole point: assert the call site sits
        # above the replace_seed DELETE inside the chunked branch
        src = inspect.getsource(o19etl.run_etl)
        chunked = src.index('if entry.get("chunk_by"):')
        guard = src.index("chunk_span_refusal(", chunked)
        delete = src.index(
            'query("DELETE FROM `{0}`.`{1}`".format(dst, table))', chunked)
        self.assertLess(guard, delete)


class TestAbsentTableDisposition(unittest.TestCase):
    """What happens to a manifest table this dump does not carry."""

    TOL = ("log",)

    def test_a_tolerated_table_is_cleared_and_said_so(self):
        clear, note = o19etl.absent_table_disposition(
            "log", "copy", self.TOL, True)
        self.assertTrue(clear)
        self.assertIn("the target's own rows were cleared", note)

    def test_the_absent_table_line_survives_a_resumed_run(self):
        # THE regression: the note was produced only when this run did
        # the delete, so --resume dropped it from the shareable report.
        # Three source-text guards failed to pin this -- each was
        # defeated by a differently-shaped ledger gate that reproduced
        # the bug exactly -- because the decision lived at the call site,
        # where `f(x) == f(x)` says nothing. absent_table_plan now owns
        # both halves, so the invariant is finally a value: the ledger
        # changes the DELETE and leaves the line alone.
        args = ("log", "copy", ["log"], True)
        fresh = o19etl.absent_table_plan(*(args + (False,)))
        resumed = o19etl.absent_table_plan(*(args + (True,)))
        self.assertTrue(fresh[0], "a first run must clear the target rows")
        self.assertFalse(resumed[0], "a resumed run must not clear again")
        self.assertIsNotNone(fresh[1])
        self.assertEqual(
            resumed[1], fresh[1],
            "the report line changed on --resume: P0 tolerated this "
            "table's pre-existing rows only because the copy clears "
            "them, and that fact has just fallen out of the report")

    def test_the_absent_table_line_does_not_depend_on_the_clear(self):
        # FOURTH attempt at this guard, and the first three were all
        # defeated by the same bug in a new costume. Counting textual
        # ledger reads was the third: re-gating the append as
        # `if do_clear: if line is not None: ...` reads the ledger no
        # more often, keeps the `if line is not None:` line, and still
        # drops the report line on every --resume, because a resumed run
        # is exactly the one where do_clear is False.
        #
        # The invariant is about NESTING, so assert it on the tree rather
        # than on the text: whatever the guard is called and however it
        # is formatted, appending the report line must not sit inside a
        # branch that tests whether this run cleared, or that reads the
        # ledger.
        #
        # This is no longer the only protection, and should not be read as
        # such. test_etl_driver.TestAbsentTables drives the real run_etl
        # and covers the same ground behaviourally -- including the
        # `line is not None` guard at the call site, which this walk does
        # NOT check (its argument is `line` either way). Both mutations
        # were verified red before this note was written. Keep this test
        # for the nesting shape; keep the driver for what the operator
        # actually gets.
        tree = ast.parse(textwrap.dedent(inspect.getsource(o19etl.run_etl)))
        found = []

        class Walk(ast.NodeVisitor):
            def __init__(self):
                self.ifs = []

            def visit_If(self, node):
                self.ifs.append(node)
                self.generic_visit(node)
                self.ifs.pop()

            def visit_Call(self, node):
                fn = node.func
                if (isinstance(fn, ast.Attribute) and fn.attr == "append"
                        and isinstance(fn.value, ast.Name)
                        and fn.value.id == "absent_tables"):
                    found.append(list(self.ifs))
                self.generic_visit(node)

        Walk().visit(tree)
        self.assertEqual(
            len(found), 1,
            "this test walks run_etl for the single absent_tables.append, "
            "and found {0}. If you deliberately moved or split that "
            "append -- extracting it into a helper is a legitimate "
            "refactor -- this failure is the premise going stale, not "
            "the bug returning: re-point the walk at wherever the append "
            "now lives and keep the invariant it checks, which is that "
            "appending the report line must not be enclosed by any "
            "branch testing whether this run cleared or reading the ETL "
            "ledger. Do not delete the test to make it pass; the "
            "regression it guards has shipped once and slipped past "
            "three replacements.".format(len(found)))
        for branch in found[0]:
            names = {n.id for n in ast.walk(branch.test)
                     if isinstance(n, ast.Name)}
            self.assertNotIn(
                "do_clear", names,
                "the report line is nested under the clear guard: a "
                "resumed run does not clear, so the line would vanish "
                "from the report -- the regression this branch has "
                "shipped once and nearly shipped three times since")
            self.assertNotIn(
                "progress", names,
                "the report line is nested under a branch reading the "
                "ETL ledger; absent_tables is rebuilt every run while "
                "the ledger remembers, so the line would drop on "
                "--resume")

    def test_the_absent_table_delete_still_consults_the_ledger(self):
        # the other half: the DELETE must stay idempotent. Without this,
        # the test above is satisfied by dropping the ledger check
        # altogether and re-deleting the target's rows on every resume.
        src = textwrap.dedent(inspect.getsource(o19etl.run_etl))
        start = src.index("do_clear, line = absent_table_plan(")
        block = src[start:src.index("continue", start)]
        self.assertEqual(block.count('"absent_cleared")'), 1)
        self.assertEqual(block.count('"absent_cleared"] = True'), 1)

    def test_a_non_copy_absent_table_has_no_report_line(self):
        clear, line = o19etl.absent_table_plan(
            "some_archive_only", "archive", [], True, False)
        self.assertFalse(clear)
        self.assertIsNone(line)

    def test_a_tolerated_table_missing_from_the_target_is_left_alone(self):
        clear, note = o19etl.absent_table_disposition(
            "log", "copy", self.TOL, False)
        self.assertFalse(clear)

    def test_a_tolerated_table_of_another_class_is_never_emptied(self):
        # only copy/merge tables are reported at all, so clearing one of
        # another class would destroy target rows with no report line
        for cls in ("reference", "drop", "archive"):
            clear, note = o19etl.absent_table_disposition(
                "log", cls, self.TOL, True)
            self.assertFalse(clear, cls)
            self.assertEqual(note, "", cls)

    def test_a_seeded_table_keeps_the_carlos_defaults_note(self):
        seeded = next(iter(o19map_schema.SEED_ROW_COUNTS))
        clear, note = o19etl.absent_table_disposition(
            seeded, "copy", (), True)
        self.assertFalse(clear)
        self.assertIn("CARLOS defaults stand", note)

    def test_an_ordinary_absent_table_gets_no_note(self):
        self.assertEqual(
            o19etl.absent_table_disposition("zzz_nope", "copy", (), True),
            (False, ""))


if __name__ == "__main__":
    unittest.main()
