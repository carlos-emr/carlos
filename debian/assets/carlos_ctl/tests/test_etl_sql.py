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
import re
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

    """The INSERT ... SELECT one manifest table copies through.

    Every clause here is a decision about the clinic's data: which
    source column feeds which target, when a zero date becomes NULL,
    what an out-of-set enum falls back to, and which columns the charset
    repair wraps."""
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

    """The anti-join that keeps CARLOS's seed row and appends the rest."""
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
        create = next(x for x in stmts
                      if x.startswith('CREATE TABLE `arch`'))
        insert = next(x for x in stmts
                      if x.startswith('INSERT INTO'))
        # DROP __new, DROP __old, CREATE __new, INSERT,
        # CREATE IF NOT EXISTS live, RENAME swap, DROP __old
        self.assertEqual(len(stmts), 7)
        self.assertIn("`arch`.`LookupList__idmap__new`", create)
        self.assertIn("old_id BIGINT NOT NULL PRIMARY KEY", create)
        # the SOURCE partitions on the expression the insert stores, so
        # a key the manifest rewrites cannot split one target row's twins
        # across two partitions (both would then map to the same new id)
        self.assertIn("ROW_NUMBER() OVER (PARTITION BY s.`name` ORDER BY "
                      "s.`id`) AS rn FROM `src`.`LookupList` s", insert)
        self.assertIn("ROW_NUMBER() OVER (PARTITION BY `name` ORDER BY "
                      "`id`) AS rn FROM `dst`.`LookupList`", insert)
        self.assertIn("ON d.`name` <=> s.`name` AND s.rn = d.rn", insert)
        self.assertNotIn("MIN(", insert)
        # a surplus twin (its key already satisfied by a seed row, so the
        # anti-join appended nothing) falls back to the target's first row
        self.assertIn("LEFT JOIN", insert)
        self.assertIn("d1 ON d1.`name` <=> s.`name` AND d1.rn = 1", insert)
        # BOTH occurrences, counted: the fallback appears in the SELECT
        # list and again in the trailing WHERE, so `assertIn` alone was
        # satisfied by either one. Deleting the SELECT-side fallback --
        # the half that decides what is STORED -- left the whole suite
        # green while a surplus twin's new_id became NULL against a
        # BIGINT NOT NULL column, aborting P4 mid-merge.
        self.assertEqual(insert.count("COALESCE(d.`id`, d1.`id`)"), 2)
        stored = insert.split(" FROM ", 1)[0]
        self.assertIn("COALESCE(d.`id`, d1.`id`)", stored)
        self.assertTrue(insert.endswith(
            "WHERE COALESCE(d.`id`, d1.`id`) IS NOT NULL"), insert[-90:])

    def test_the_source_partition_uses_the_rewritten_key(self):
        # value_exprs maps '' to NULL: two source rows with '' and NULL
        # insert as one key and must not land in two partitions
        entry = dict(self.PARENT)
        entry["value_exprs"] = {"name": "NULLIF(s.`name`, '')"}
        stmts = o19etl.idmap_statements("LookupList", entry, "src", "dst",
                                        "arch", self.PARENT_DST)
        insert = next(x for x in stmts
                      if x.startswith('INSERT INTO'))
        self.assertIn("PARTITION BY NULLIF(s.`name`, '')", insert)
        # the same duplicate-substring trap: `d.` and `d1.` join
        # predicates both appear, and the shorter one is a substring of
        # neither -- but each occurs once, so pin the count as well as
        # the presence
        self.assertEqual(insert.count("d.`name` <=> NULLIF(s.`name`, '')"),
                         1)
        self.assertEqual(insert.count("d1.`name` <=> NULLIF(s.`name`, '')"),
                         1)

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
        # the parents the unruled-FK guard found: a child copied before
        # its parent would read an id map that does not exist yet
        self.assertLess(order.index("consentType"), order.index("Consent"))
        self.assertLess(order.index("HRMCategory"),
                        order.index("HRMDocument"))
        self.assertLess(order.index("HRMCategory"),
                        order.index("HRMSubClass"))

    def test_consent_type_is_read_through_the_map_on_the_shipped_entry(
            self):
        """The defect the guard exists for, pinned on the real entry:
        `Consent.consent_type_id` must be REWRITTEN through consentType's
        id map, and an id the map does not know must become NULL -- never
        the raw value, which on the target names whichever CARLOS seed
        row happens to hold that id."""
        entry = o19map_schema.TABLES["Consent"]
        cols = {c: {"type": "int" if c.endswith("_id") or c == "id"
                    else "varchar",
                    "column_type": "int(11)" if c.endswith("_id")
                    or c == "id" else "varchar(255)",
                    "nullable": c != "id", "char_len": 255,
                    "octet_len": 1020, "has_default": False,
                    "default": None, "auto_increment": False}
                for c in entry["cols"]}
        sql = o19etl.copy_statement("Consent", entry, "src", "dst", cols,
                                    archive_schema="arch")
        self.assertEqual(
            selected_expr(sql, "consent_type_id"),
            "(SELECT m.new_id FROM `arch`.`consentType__idmap` m WHERE "
            "m.old_id = s.`consent_type_id`)")
        self.assertNotIn("IFNULL", selected_expr(sql, "consent_type_id"))


class TestArchivedColumnPlanSpelling(unittest.TestCase):
    """A dump that spells a dropped column differently from the manifest
    (`programno` for `programNo`) must still be preserved -- and the
    copy must read the column by the name the dump actually has."""

    ENTRY = {"class": "copy", "cols": ["id"],
             "dropped": {"programNo": {"nondefault": "x"}}}
    SRC = {"id": {"column_type": "int(11)"},
           "programno": {"column_type": "varchar(20)"}}

    def test_the_source_half_is_the_dumps_own_spelling(self):
        plan = o19etl.archived_column_plan(self.ENTRY, self.SRC)
        self.assertEqual(plan, [("programno", "import_archived_programNo",
                                 "varchar(20)")])

    def test_the_source_spelling_is_what_the_copy_reads(self):
        entry = o19etl.with_archived_columns(
            self.ENTRY, o19etl.archived_column_plan(self.ENTRY, self.SRC))
        self.assertEqual(entry["renames"]["import_archived_programNo"],
                         "programno")
        # ... and what the caller indexes the dump's columns by: the
        # KeyError this existed for was src_info[table][src_col]
        plan = o19etl.archived_column_plan(self.ENTRY, self.SRC)
        for src_col, _t, _c in plan:
            self.assertIn(src_col, self.SRC)


class TestResumeIdempotency(unittest.TestCase):

    """What a resumed run may repeat, and what it must refuse.

    The ledger is bound to the dump digest and the manifest version: a
    ledger that names neither, or names another dump, describes writes
    this run cannot account for."""
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

    """Capturing the columns a clinic's own fork added."""
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
        ctas = next(x for x in stmts if x.startswith("CREATE TABLE `arch`"))
        self.assertIn("`arch`.`t__unknown_cols__new`", ctas)
        self.assertIn("SELECT s.`id`, s.`vendor_flag` FROM `src`.`t` s "
                      "WHERE s.`vendor_flag` IS NOT NULL", ctas)

    def test_nothing_unknown_yields_no_statements(self):
        src = {"id": col("int"), "name": col(), "legacy": col()}
        self.assertEqual(o19etl.unknown_column_shadow_statements(
            "t", self.ENTRY, "src", "arch", src), [])


class TestCharsetScanFailures(unittest.TestCase):

    """The charset scan's failure modes.

    A scan error must propagate rather than read as "no mojibake
    found"; an absent column is skipped with a note rather than
    silently."""
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

    """Archive copies and shadow captures, and how they are rebuilt.

    The rebuild is build-aside-then-swap: a preserved table is never
    absent while it is the only copy of what CARLOS has no home for."""
    def test_archive_rebuilds_without_ever_dropping_the_live_copy(self):
        # the archive holds the clinic's ONLY copy of records CARLOS has
        # no home for, so the rebuild builds beside it and swaps: the
        # only DROPs name our own scratch tables, never the live one
        stmts = o19etl.archive_statements("formONAR", "src", "arch")
        drops = [x for x in stmts if x.startswith("DROP TABLE")]
        self.assertTrue(drops)
        for d in drops:
            self.assertRegex(d, r"`formONAR__(new|old)`$",
                             "a DROP named the live archive table: " + d)
        self.assertIn("CREATE TABLE `arch`.`formONAR__new` LIKE "
                      "`src`.`formONAR`", stmts)
        self.assertIn("INSERT INTO `arch`.`formONAR__new` SELECT * FROM "
                      "`src`.`formONAR`", stmts)
        # and the swap is one atomic RENAME, so there is no moment at
        # which neither copy exists
        rename = [x for x in stmts if x.startswith("RENAME TABLE")]
        self.assertEqual(len(rename), 1, stmts)
        self.assertEqual(
            rename[0],
            "RENAME TABLE `arch`.`formONAR` TO `arch`.`formONAR__old`, "
            "`arch`.`formONAR__new` TO `arch`.`formONAR`")

    def test_shadow_captures_dropped_columns_with_predicate(self):
        entry = {"class": "copy", "cols": ["id", "name"], "chunk_by": "id",
                 "dropped": {"legacy": {
                     "nondefault": "s.`legacy` IS NOT NULL"}}}
        src = {"id": col("int"), "name": col(), "legacy": col()}
        stmts = o19etl.shadow_statements("t", entry, "src", "arch", src)
        ctas = next(x for x in stmts if x.startswith("CREATE TABLE `arch`"))
        self.assertIn("`arch`.`t__dropped__new`", ctas)
        self.assertIn("WHERE (s.`legacy` IS NOT NULL)", ctas)
        self.assertIn("s.`id`", ctas)
        for d in [x for x in stmts if x.startswith("DROP TABLE")]:
            self.assertRegex(d, r"`t__dropped__(new|old)`$", d)

    def test_shadow_context_columns_use_their_source_spelling(self):
        # the manifest names the CARLOS column (isActive); the dump spells
        # it isactive — the capture must read the staged name
        entry = {"class": "copy", "cols": ["id", "isActive"],
                 "renames": {"isActive": "isactive"},
                 "dropped": {"legacy": {
                     "nondefault": "s.`legacy` IS NOT NULL"}}}
        src = {"id": col("int"), "isactive": col(), "legacy": col()}
        stmts = o19etl.shadow_statements("t", entry, "src", "arch", src)
        ctas = next(x for x in stmts if x.startswith("CREATE TABLE `arch`"))
        self.assertIn("s.`isactive`", ctas)
        self.assertNotIn("s.`isActive`", ctas)


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
        # the rebuild swap appends __new/__old INSIDE the quoting, so the
        # crafted name still cannot close its identifier anywhere
        for sql in o19etl.archive_statements(self.EVIL, "src", "arch"):
            self.assertRegex(
                sql, r"`t1``;DROP DATABASE victim;--(__new|__old)?`",
                sql)
            self.assertNotRegex(sql, r"[^`]`;DROP")

    def test_unknown_column_shadow_quotes_the_dumps_column_names(self):
        entry = {"class": "copy", "cols": ["id"]}
        src_cols = {"id": col("int"), "x`; DROP TABLE y; --": col()}
        stmts = o19etl.unknown_column_shadow_statements(
            "t", entry, "src", "arch", src_cols)
        ctas = next(x for x in stmts if x.startswith("CREATE TABLE `arch`"))
        self.assertIn("s.`x``; DROP TABLE y; --` IS NOT NULL", ctas)
        for sql in stmts:
            self.assertNotRegex(sql, r"[^`]`; DROP")

    def test_unsafe_identifiers_lists_tables_and_columns(self):
        info = {"ok": {"id": col("int"), "bad col": col()},
                self.EVIL: {"id": col("int")}, "z$1": {"a_b": col()}}
        self.assertEqual(o19etl.unsafe_identifiers(info),
                         ["ok.bad col", self.EVIL])
        self.assertTrue(o19etl.IDENTIFIER_RE.match("secObjPrivilege"))
        self.assertFalse(o19etl.IDENTIFIER_RE.match("a-b"))
        self.assertFalse(o19etl.IDENTIFIER_RE.match(""))


class TestChunkWindows(unittest.TestCase):

    """The id windows a chunked copy walks: exact cover, no overlap."""
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

    """Refusals computed before the first write.

    A NOT NULL target column the copy cannot fill would abort the
    import mid-way; the pre-check turns that into a refusal on an
    untouched database."""
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

    """Parsing the member list out of an enum column type."""
    def test_parses_enum_members(self):
        self.assertEqual(o19etl.enum_values("enum('x','y')"), ["x", "y"])
        self.assertEqual(o19etl.enum_values("enum('RO','NR','TE')"),
                         ["RO", "NR", "TE"])
        self.assertEqual(o19etl.enum_values("varchar(10)"), [])


class TestEffectiveEntry(unittest.TestCase):

    """Reducing a manifest entry to what THIS dump actually carries.

    OSCAR 19 sites run different patch levels; a column or parent table
    the dump lacks is dropped from the entry with a note, rather than
    producing SQL that names something that is not there."""
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

    """The per-row double-encoding repair, and what it targets."""
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

    """Telling "that table does not exist" from every other server error.

    Read from the server's own stderr, never from the statement text,
    which can carry a patient identifier."""
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

    """Staging vs target row counts, and the deltas that are expected."""
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


class TestArchivedColumns(unittest.TestCase):
    """The column half of requirement B, at statement level.

    A source column CARLOS has no home for is added to the live table
    under `import_archived_<col>` with the SOURCE type, and copied
    verbatim -- no sanitizer, because an archived value that differs from
    what the clinic had is not an archive.
    """

    ENTRY = {"class": "copy", "cols": ["id", "name"],
             "dropped": {"legacyFlag": {"nondefault": "1"}}}

    def src_cols(self, **over):
        cols = {"id": {"column_type": "int(11)", "type": "int",
                       "nullable": False},
                "name": {"column_type": "varchar(60)", "type": "varchar",
                         "nullable": True},
                "legacyFlag": {"column_type": "tinyint(1)",
                               "type": "tinyint", "nullable": True},
                "vendorNote": {"column_type": "text", "type": "text",
                               "nullable": True}}
        cols.update(over)
        return cols

    def test_the_plan_covers_dropped_and_vendor_fork_columns(self):
        plan = o19etl.archived_column_plan(self.ENTRY, self.src_cols())
        self.assertEqual(
            plan, [("legacyFlag", "import_archived_legacyFlag",
                    "tinyint(1)"),
                   ("vendorNote", "import_archived_vendorNote", "text")])

    def test_a_dump_that_spells_the_column_differently_still_preserves_it(
            self):
        """The dump's casing is the clinic's, not the manifest's.

        `effective_entry` and `unknown_columns` both fold case, so a
        column spelled `legacyflag` is neither planned by an exact lookup
        NOR counted as a vendor-fork column -- it would be preserved
        nowhere at all. The target name keeps the manifest's spelling so
        it is stable across dumps; the SOURCE half is the dump's own
        spelling, because that is what `renames` reads and what the
        caller indexes the dump's columns by (a manifest spelling there
        was a KeyError mid-P4)."""
        cols = self.src_cols()
        cols["legacyflag"] = cols.pop("legacyFlag")
        plan = o19etl.archived_column_plan(self.ENTRY, cols)
        self.assertIn(("legacyflag", "import_archived_legacyFlag",
                       "tinyint(1)"), plan)
        for src_col, _target, _ctype in plan:
            self.assertIn(src_col, cols)

    def test_the_shadow_capture_agrees_about_which_columns_are_absent(
            self):
        # the same fold: reporting a present column absent drops it from
        # the o19_archive capture as well
        cols = self.src_cols()
        cols["legacyflag"] = cols.pop("legacyFlag")
        notes = []
        o19etl.shadow_statements("t", self.ENTRY, "stage", "arch", cols,
                                 notes)
        self.assertEqual(notes, [])

    def test_the_shadow_keeps_a_context_column_the_dump_recased(self):
        """The captured values need a row to join back to. A context
        column the dump spells differently is still THERE, so it must
        reach the SELECT under the dump's spelling."""
        entry = {"class": "copy", "cols": ["id", "name"],
                 "dropped": {"legacyFlag": {"nondefault": "1"}}}
        cols = self.src_cols()
        cols["ID"] = cols.pop("id")
        sql = o19etl.shadow_statements("t", entry, "stage", "arch", cols)
        create = next(x for x in sql if x.startswith("CREATE TABLE"))
        self.assertIn("s.`ID`", create)

    def test_a_dropped_column_this_dump_lacks_is_not_planned(self):
        # nothing to preserve; shadow_statements reports the case
        cols = self.src_cols()
        del cols["legacyFlag"]
        plan = o19etl.archived_column_plan(self.ENTRY, cols)
        self.assertEqual([p[0] for p in plan], ["vendorNote"])

    def test_the_alter_keeps_the_source_type_and_is_nullable(self):
        plan = o19etl.archived_column_plan(self.ENTRY, self.src_cols())
        stmts = o19etl.add_archived_column_statements("t", "carlos", plan,
                                                      {})
        self.assertEqual(len(stmts), 2)
        self.assertIn("ADD COLUMN `import_archived_legacyFlag` tinyint(1) "
                      "NULL COMMENT 'OSCAR 19 t.legacyFlag preserved by "
                      "import-o19'", stmts[0])

    def test_the_alter_carries_the_source_charset_and_collation(self):
        """A column CARLOS has no home for is preserved at the SOURCE's
        capacity, not the target's. TEXT is 65535 bytes: a latin1 `text`
        holds 65535 characters, a utf8mb4 one as few as 16383. Measured
        on MariaDB 10.11, a full latin1 TEXT of accented characters
        copied verbatim into an archived column declared in the target
        table's utf8mb4 came back truncated to 32767 characters, with a
        warning the ETL's sql_mode='' turns into silence. The charset on
        the ALTER is what makes "verbatim" true."""
        cols = self.src_cols(vendorNote={
            "column_type": "text", "type": "text", "nullable": True,
            "charset": "latin1", "collation": "latin1_swedish_ci"})
        plan = o19etl.archived_column_plan(self.ENTRY, cols)
        self.assertIn(("vendorNote", "import_archived_vendorNote",
                       "text CHARACTER SET latin1 COLLATE "
                       "latin1_swedish_ci"), plan)
        stmts = o19etl.add_archived_column_statements("t", "carlos", plan,
                                                      {})
        self.assertIn("ADD COLUMN `import_archived_vendorNote` text "
                      "CHARACTER SET latin1 COLLATE latin1_swedish_ci "
                      "NULL COMMENT", stmts[1])
        # the row-width estimate still recognises the type under the
        # clause (it anchors at the start of the string); a 4-byte-per-
        # char over-measure of a latin1 VARCHAR is the safe direction
        self.assertEqual(o19etl.column_bytes(
            "varchar(10) CHARACTER SET latin1 COLLATE latin1_swedish_ci"),
            42)
        self.assertEqual(o19etl.column_bytes(
            "text CHARACTER SET latin1"), 10)
        self.assertEqual(o19etl.column_bytes(
            "enum('a','b') CHARACTER SET latin1"), 2)

    def test_a_column_without_a_charset_keeps_its_bare_type(self):
        # numbers, dates and BLOBs have no charset in information_schema,
        # and a charset without a collation is still carried
        self.assertEqual(o19etl.archived_column_type(
            {"column_type": "tinyint(1)", "charset": None}), "tinyint(1)")
        self.assertEqual(o19etl.archived_column_type(
            {"column_type": "blob"}), "blob")
        self.assertEqual(o19etl.archived_column_type(
            {"column_type": "varchar(8)", "charset": "utf8mb3"}),
            "varchar(8) CHARACTER SET utf8mb3")

    def test_a_column_already_present_is_not_re_added(self):
        # MySQL 8 has no ADD COLUMN IF NOT EXISTS: this skip is the whole
        # idempotency story for a resumed run
        plan = o19etl.archived_column_plan(self.ENTRY, self.src_cols())
        stmts = o19etl.add_archived_column_statements(
            "t", "carlos", plan, {"import_archived_legacyFlag": {}})
        self.assertEqual(len(stmts), 1)
        self.assertIn("import_archived_vendorNote", stmts[0])

    def test_the_entry_maps_each_target_back_to_its_source(self):
        plan = o19etl.archived_column_plan(self.ENTRY, self.src_cols())
        entry = o19etl.with_archived_columns(self.ENTRY, plan)
        self.assertEqual(entry["cols"][-2:],
                         ["import_archived_legacyFlag",
                          "import_archived_vendorNote"])
        self.assertEqual(entry["renames"]["import_archived_vendorNote"],
                         "vendorNote")
        self.assertEqual(sorted(entry["archived_cols"]),
                         ["import_archived_legacyFlag",
                          "import_archived_vendorNote"])
        # the original entry is not mutated: the shadow captures still
        # need the manifest's own view of the table
        self.assertNotIn("archived_cols", self.ENTRY)

    def test_an_archived_date_is_copied_without_the_zero_date_rewrite(self):
        """The sharpest case for copying verbatim: `sanitize_expr` turns
        '0000-00-00' into NULL, which is right for a live CARLOS column
        and wrong for an archive -- the clinic's row said zero."""
        entry = o19etl.with_archived_columns(
            {"class": "copy", "cols": ["id"], "dropped": {}},
            [("startDate", "import_archived_startDate", "date")])
        dst = {"id": {"type": "int", "column_type": "int(11)",
                      "nullable": False, "char_len": 0,
                      "has_default": False, "auto_increment": False},
               "import_archived_startDate": {
                   "type": "date", "column_type": "date", "nullable": True,
                   "char_len": 0, "has_default": False,
                   "auto_increment": False}}
        sql = o19etl.copy_statement("t", entry, "stage", "carlos", dst)
        self.assertIn("s.`startDate`", sql)
        self.assertNotIn("NULLIF", sql)

    def test_a_merge_back_fills_the_rows_it_did_not_insert(self):
        """A merge keeps CARLOS's row on a shared key, so a clinic row
        with a twin never passes through the insert -- its unmapped
        columns are the one population the copy alone still orphans."""
        entry = o19etl.with_archived_columns(
            {"class": "merge", "cols": ["site"], "merge_keys": ["site"]},
            [("vendorNote", "import_archived_vendorNote", "text")])
        dst = {"site": {"type": "varchar", "column_type": "varchar(60)",
                        "nullable": True, "char_len": 60,
                        "has_default": False, "auto_increment": False}}
        sql = o19etl.archived_backfill_statement("HL7Map", entry, "stage",
                                                 "carlos", dst)
        self.assertIn("UPDATE `carlos`.`HL7Map` d JOIN `stage`.`HL7Map` s "
                      "ON d.`site` <=> s.`site`", sql)
        self.assertIn("SET d.`import_archived_vendorNote` = "
                      "s.`vendorNote`", sql)

    def test_a_merge_with_no_archived_columns_has_no_back_fill(self):
        self.assertIsNone(o19etl.archived_backfill_statement(
            "HL7Map", {"cols": ["site"], "merge_keys": ["site"]}, "stage",
            "carlos", {}))

    def test_the_back_fill_leaves_excluded_rows_alone(self):
        # removed-module rows the merge refuses to carry must not be
        # updated into existence-adjacent state either
        entry = o19etl.with_archived_columns(
            {"class": "merge", "cols": ["site"], "merge_keys": ["site"],
             "merge_exclude": "s.`site` = 'BORN'"},
            [("vendorNote", "import_archived_vendorNote", "text")])
        dst = {"site": {"type": "varchar", "column_type": "varchar(60)",
                        "nullable": True, "char_len": 60,
                        "has_default": False, "auto_increment": False}}
        sql = o19etl.archived_backfill_statement("HL7Map", entry, "stage",
                                                 "carlos", dst)
        self.assertTrue(sql.endswith("WHERE NOT (s.`site` = 'BORN')"), sql)

    def test_every_name_the_manifest_preserves_fits_mysqls_limit(self):
        """16 characters of prefix onto the longest name the manifest
        actually preserves, plus the rebuild suffix -- measured against
        the real manifest rather than asserted in a comment."""
        preserved = [t for t, e in o19map_schema.TABLES.items()
                     if e["class"] in o19etl.PRESERVED_CLASSES]
        longest = max(preserved, key=len)
        name = o19etl.archived_table(longest) + o19etl.REBUILD_OLD
        self.assertLessEqual(len(name), 64, name)
        longest_col = max(
            (c for e in o19map_schema.TABLES.values()
             for c in (e.get("dropped") or {})), key=len, default="")
        self.assertLessEqual(
            len(o19etl.archived_column(longest_col)), 64, longest_col)

    def test_every_manifest_table_leaves_room_for_its_scratch_names(self):
        """`rebuild_statements` appends `__new`/`__old` to a name that
        may already carry a shadow suffix.

        A dump's own table names are bounded by the pre-check below; the
        MANIFEST's are not, and `<table>__unknown_cols__new` is the
        longest identifier this tool constructs. The margin is one
        character today, which is exactly why it is measured rather than
        assumed."""
        longest = max(o19etl.SHADOW_SUFFIXES, key=len)
        over = sorted(t for t in o19map_schema.TABLES
                      if len(t) + len(longest) + len(o19etl.REBUILD_OLD)
                      > o19etl.IDENTIFIER_LIMIT)
        self.assertEqual(over, [])

    def test_a_forks_long_table_name_is_refused_before_the_first_write(self):
        """A clinic's fork names its own tables, and those arrive
        unclassified. Overflowing the identifier limit on the ALTER
        halfway through the loop would leave a half-preserved import, so
        the name is checked where `unsafe_identifiers` is -- and shares
        its remedy."""
        long_table = "vendor_" + "x" * 40          # 47 > 43
        problems = o19etl.oversized_preserved_names(
            {long_table: {}}, [long_table], {})
        self.assertEqual(len(problems), 1)
        self.assertIn("exceed MySQL's 64-character identifier limit",
                      problems[0])
        self.assertIn("rename it in the source and re-export", problems[0])

    def test_a_forks_long_column_name_is_refused(self):
        long_col = "vendor_" + "y" * 45            # 52 > 48
        problems = o19etl.oversized_preserved_names(
            {"t": {}}, [], {"t": [long_col]})
        self.assertEqual(len(problems), 1)
        self.assertIn(long_col, problems[0])

    def test_names_that_fit_raise_nothing(self):
        # a table gets five fewer characters than a column: it also
        # carries the rebuild suffix
        self.assertEqual(o19etl.oversized_preserved_names(
            {}, ["t" * o19etl.MAX_PRESERVED_TABLE],
            {"t": ["c" * o19etl.MAX_PRESERVED_COLUMN]}), [])


class TestAViewIsNotATable(unittest.TestCase):

    """The two halves of the tool must agree about what the dump holds.

    A mysqldump of a clinic database carries its VIEWs, and
    information_schema lists a view among TABLES and describes its
    columns among COLUMNS exactly like a table's. The clinic-side
    assessment has always filtered on TABLE_TYPE = 'BASE TABLE'; the ETL
    side did not, so a view reached the unknown-table preservation step
    as an "unknown table" -- and `CREATE TABLE ... LIKE <view>` is error
    1347, raised mid-P4 with the copy already part-written."""

    def test_the_table_listing_asks_for_base_tables_only(self):
        seen = []

        def plain(sql):
            seen.append(sql)
            return [["demographic"]]
        got = o19etl.schema_tables(plain, "o19_import")
        self.assertEqual(got, {"demographic"})
        self.assertIn("TABLE_TYPE = 'BASE TABLE'", seen[0])

    def test_the_column_introspection_excludes_a_views_columns(self):
        seen = []

        def query(sql):
            seen.append(sql)
            return []
        o19etl.introspect_columns(query, "o19_import")
        self.assertIn("TABLE_TYPE = 'BASE TABLE'", seen[0])
        self.assertIn("information_schema.TABLES", seen[0])


class TestAnUnrunnableClassIsRefused(unittest.TestCase):

    """`run_etl`'s dispatch ends in `else: copy`, and both halves of P7's
    parity skip a class they do not recognise. A class added to the
    manifest without a matching branch would therefore be copied into
    the live schema and verified by nothing -- so it is refused before
    the first write instead."""

    def test_every_shipped_class_is_one_this_build_runs(self):
        self.assertEqual(
            o19etl.unknown_manifest_classes(o19map_schema.TABLES), [])
        for cls in o19etl.KNOWN_CLASSES:
            self.assertEqual(o19etl.unknown_manifest_classes(
                {"t": {"class": cls}}), [])

    def test_a_class_with_no_branch_is_named_and_refused(self):
        lines = o19etl.unknown_manifest_classes(
            {"t": {"class": "transform"}, "u": {"class": "copy"}})
        self.assertEqual(len(lines), 1)
        self.assertIn("t: manifest class 'transform'", lines[0])
        self.assertIn("copy", lines[0])
        # a missing class is not a copy either
        self.assertEqual(len(o19etl.unknown_manifest_classes({"t": {}})), 1)

    def test_the_precheck_actually_asks(self):
        # the refusal only protects anything if P4's pre-check runs it:
        # the function on its own is a guard nobody consults
        src = inspect.getsource(o19etl.etl_precheck_problems)
        self.assertIn("unknown_manifest_classes(", src)

    def test_a_manifest_gone_wholly_wrong_is_summarised(self):
        lines = o19etl.unknown_manifest_classes(
            dict(("t{0}".format(i), {"class": "x"}) for i in range(15)))
        self.assertEqual(len(lines), 11)
        self.assertIn("... and 5 more", lines[-1])


class TestIntrospectColumns(unittest.TestCase):
    """`introspect_columns` against the rows information_schema answers
    with, including the two the charset carry-over added."""

    def query(self, rows):
        return lambda sql: rows

    def test_it_asks_for_and_records_the_column_charset(self):
        seen = []

        def query(sql):
            seen.append(sql)
            return [["t", "note", "text", "text", "YES", 65535,
                     "\\0NONE", "", 65535, "latin1", "latin1_swedish_ci"],
                    ["t", "id", "int", "int(11)", "NO", 0, "\\0NONE",
                     "auto_increment", 0, "", ""]]
        got = o19etl.introspect_columns(query, "stage")
        self.assertIn("CHARACTER_SET_NAME", seen[0])
        self.assertIn("COLLATION_NAME", seen[0])
        self.assertEqual(got["t"]["note"]["charset"], "latin1")
        self.assertEqual(got["t"]["note"]["collation"],
                         "latin1_swedish_ci")
        self.assertEqual(got["t"]["note"]["octet_len"], 65535)
        # a non-character column answers '' (IFNULL) and is recorded as
        # "no charset", the value archived_column_type keys off
        self.assertIsNone(got["t"]["id"]["charset"])
        self.assertIsNone(got["t"]["id"]["collation"])
        self.assertTrue(got["t"]["id"]["auto_increment"])

    def test_a_shorter_answer_reads_as_charset_unknown(self):
        # the tests' fakes answer the nine columns this asked for before
        # the charset carry-over; they must keep working, and read as
        # "not known" rather than crash
        got = o19etl.introspect_columns(self.query(
            [["t", "c", "varchar", "varchar(255)", "YES", 255, "\\0NONE",
              "", 1020]]), "stage")
        self.assertIsNone(got["t"]["c"]["charset"])
        self.assertEqual(got["t"]["c"]["octet_len"], 1020)


class TestRowSizeCeiling(unittest.TestCase):
    """A preserved column that fits the identifier limit can still be a
    column the ROW has no room for.

    MySQL refuses the ALTER, and it refuses it in the middle of the table
    loop with the import already part-written -- so the arithmetic is
    done before the first write instead.
    """

    def dst(self, n, width=255):
        return {"c{0}".format(i): {"column_type":
                                   "varchar({0})".format(width)}
                for i in range(n)}

    def plan(self, n, coltype="varchar(255)"):
        return [("src{0}".format(i), "import_archived_src{0}".format(i),
                 coltype) for i in range(n)]

    def test_a_table_with_room_is_not_refused(self):
        self.assertIsNone(o19etl.oversized_rows(
            "t", self.dst(10), self.plan(5)))

    def test_a_row_pushed_past_the_limit_is_refused_before_any_write(self):
        # 60 varchar(255) columns are ~61 KB; five more do not fit
        msg = o19etl.oversized_rows("t", self.dst(60), self.plan(5))
        self.assertIsNotNone(msg)
        self.assertIn("past MySQL's 65535-byte row limit", msg)
        self.assertIn("import_archived_src0", msg)
        # the refusal must not promise an archive capture: its lines go
        # to the die() that ends P4 before any table is copied, so
        # nothing has reached o19_archive and nothing will on this run
        self.assertIn("refuses before adding the column", msg)
        self.assertNotIn("captured to the archive", msg)
        # ... and it must not claim "nothing was written" either: only
        # precheck_scope can tell a fresh run from a --resume standing on
        # earlier writes, and from the RENAMEs normalize_table_case has
        # already made against staging by this point
        self.assertNotIn("writing anything", msg)

    def test_a_table_with_no_plan_is_never_refused(self):
        self.assertIsNone(o19etl.oversized_rows("t", self.dst(60), []))

    def test_a_resume_does_not_count_its_own_columns_twice(self):
        """On a resume the target already carries some of the preserved
        columns. Counting them once in dst_cols and again in the plan
        would refuse a table that has room -- and refuse it before the
        resume logic that would have skipped the ALTER."""
        plan = self.plan(5)
        already = {t: {"column_type": "varchar(255)"}
                   for _s, t, _c in plan}
        dst = dict(self.dst(58), **already)   # 58 + the 5 already added
        self.assertIsNone(o19etl.oversized_rows("t", dst, plan))

    def test_a_binary_column_is_measured_by_its_declared_length(self):
        # a fork's VARBINARY(60000) is exactly the column that would slip
        # past a gate that treated every unrecognised type as 8 bytes
        self.assertEqual(o19etl.column_bytes("varbinary(60000)"), 60002)
        self.assertEqual(o19etl.column_bytes("binary(16)"), 16)
        # 10 varchar(255) (10,220 bytes) plus 60,002 clears the ceiling;
        # measured as the old 8-byte fallback it would have passed
        msg = o19etl.oversized_rows(
            "t", self.dst(10), [("b", "import_archived_b",
                                 "varbinary(60000)")])
        self.assertIsNotNone(msg)
        self.assertIn("past MySQL's 65535-byte row limit", msg)

    def test_an_unknown_but_sized_type_is_measured_not_guessed(self):
        # over-measuring only makes the refusal more cautious;
        # under-measuring lets the ALTER through
        self.assertEqual(o19etl.column_bytes("somefuturetype(900)"), 900)
        self.assertEqual(o19etl.column_bytes("weird"), 8)

    def test_text_columns_count_as_their_pointer_not_their_capacity(self):
        # a LONGTEXT declares 4 GB and contributes 12 bytes; measuring it
        # as its capacity would refuse every table that has one
        self.assertEqual(o19etl.column_bytes("longtext"), 12)
        self.assertIsNone(o19etl.oversized_rows(
            "t", self.dst(10), self.plan(200, "longtext")))

    def test_the_widths_follow_mysqls_own_arithmetic(self):
        for coltype, expected in (("varchar(255)", 255 * 4 + 2),
                                  ("char(3)", 12), ("int(11)", 4),
                                  ("bigint(20)", 8), ("tinyint(1)", 1),
                                  ("datetime", 8), ("date", 3),
                                  ("decimal(10,2)", 7), ("text", 10),
                                  ("mediumblob", 11),
                                  ("enum('a','b')", 2)):
            self.assertEqual(o19etl.column_bytes(coltype), expected,
                             coltype)


class TestArchivedColumnParity(unittest.TestCase):
    """A row count cannot see a column: a copy that named the prefixed
    column but fed it nothing passes `row_parity` unchanged."""

    def setUp(self):
        #: every count query the run issued, so a test can assert on the
        #: staging-side exclusions rather than only on the verdict
        self.seen = []

    def query(self, src_cols, dst_cols, nonnull):
        """information_schema for both schemas plus IS NOT NULL counts.

        `nonnull` is {(schema, table, column): rows}."""
        def q(sql):
            if sql.startswith("SELECT TABLE_NAME, COLUMN_NAME"):
                schema = re.search(r"TABLE_SCHEMA = '([^']+)'",
                                   sql).group(1)
                cols = {"stage": src_cols, "carlos": dst_cols}[schema]
                return [[t, c, "varchar", "varchar(60)", "YES", 60,
                         "\\0NONE", "", 240]
                        for t, names in sorted(cols.items())
                        for c in names]
            # the staging side aliases the table `s` (its exclusion
            # predicates address that alias); the target side does not
            m = re.search(
                r"FROM `([^`]+)`\.`([^`]+)`(?: s)? WHERE (?:s\.)?`([^`]+)`",
                sql)
            if m:
                self.seen.append(sql)
                return [[str(nonnull.get(m.groups(), 0))]]
            raise AssertionError("unexpected query: " + sql)
        return q

    SRC = {"Contact": ["id", "legacyFlag"]}
    DST = {"Contact": ["id", "import_archived_legacyFlag"]}

    def test_matching_non_null_counts_pass(self):
        ok, bad = o19etl.archived_column_parity(
            self.query(self.SRC, self.DST,
                       {("stage", "Contact", "legacyFlag"): 40,
                        ("carlos", "Contact",
                         "import_archived_legacyFlag"): 40}),
            "stage", "carlos")
        self.assertEqual(bad, [])
        self.assertIn("Contact.legacyFlag: 40 value(s) preserved as "
                      "import_archived_legacyFlag", ok)

    def test_a_column_that_arrived_empty_is_a_mismatch(self):
        ok, bad = o19etl.archived_column_parity(
            self.query(self.SRC, self.DST,
                       {("stage", "Contact", "legacyFlag"): 40}),
            "stage", "carlos")
        self.assertEqual(len(bad), 1)
        self.assertIn("40 non-null value(s) in staging, 0 in "
                      "carlos.import_archived_legacyFlag", bad[0])

    def test_a_target_holding_more_values_than_staging_fails(self):
        """The over-count direction, which no test covered: mutating the
        comparison to `src_n <= dst_n` left the whole suite green, so
        nothing proved this check is an equality rather than a floor.

        It has to be one. A resumed chunked copy that re-ran a window
        without its delete duplicates rows, and the duplicates carry
        their preserved values too -- more non-null values in the live
        column than the clinic ever had. The neighbouring `row_parity`
        DOES tolerate extra target rows (CARLOS's own seeds), so
        loosening this one to match would look reasonable and would let
        that duplication through."""
        ok, bad = o19etl.archived_column_parity(
            self.query(self.SRC, self.DST,
                       {("stage", "Contact", "legacyFlag"): 40,
                        ("carlos", "Contact",
                         "import_archived_legacyFlag"): 41}),
            "stage", "carlos")
        self.assertEqual(ok, [])
        self.assertEqual(len(bad), 1)
        self.assertIn("40 non-null value(s) in staging, 41 in "
                      "carlos.import_archived_legacyFlag", bad[0])

    def test_a_column_this_dump_does_not_carry_is_not_compared(self):
        # preserved by an earlier run against a fuller dump: there is
        # nothing on the staging side to compare it against
        ok, bad = o19etl.archived_column_parity(
            self.query({"Contact": ["id"]}, self.DST, {}), "stage",
            "carlos")
        self.assertEqual((ok, bad), ([], []))

    def test_ordinary_columns_are_not_touched(self):
        ok, bad = o19etl.archived_column_parity(
            self.query({"Contact": ["id"]}, {"Contact": ["id"]}, {}),
            "stage", "carlos")
        self.assertEqual((ok, bad), ([], []))

    # --- the two deliberate deletions this parity must tolerate -------
    #
    # Both are populations `row_parity` already subtracts. Getting one of
    # them wrong does not lose a row: it dead-ends the migration at P4,
    # after a complete and correct copy, with a failure no flag
    # overrides. Three merge tables carry preserved columns and two of
    # them are exactly the tables with a tolerance.

    PROP_SRC = {"property": ["name", "provider_no", "lastUpdateDate"]}
    PROP_DST = {"property": ["name", "provider_no",
                             "import_archived_lastUpdateDate"]}

    def test_the_pruned_property_rows_are_left_out_of_the_staging_count(
            self):
        # the roles post-step deletes removed-module property rows from
        # the TARGET after the merge, taking their import_archived_
        # values with them; their staging twins are still there, and
        # property.lastUpdateDate is a timestamp, so an ordinary clinic
        # hits this
        q = self.query(self.PROP_SRC, self.PROP_DST,
                       {("stage", "property", "lastUpdateDate"): 40,
                        ("carlos", "property",
                         "import_archived_lastUpdateDate"): 40})
        ok, bad = o19etl.archived_column_parity(
            q, "stage", "carlos",
            pruned_property_prefixes=("OLIS_", "ldap."),
            pruned_property_keys=("logintitle",))
        self.assertEqual(bad, [])
        staging = [q for q in self.seen if "`stage`" in q]
        self.assertEqual(len(staging), 1)
        self.assertIn("s.`name` LIKE 'OLIS\\_%'", staging[0])
        self.assertIn("s.`name` = 'logintitle'", staging[0])
        self.assertIn("AND NOT (", staging[0])

    def test_the_target_count_is_not_narrowed_by_the_prune(self):
        # the exclusion belongs on the staging side only: the pruned
        # rows are gone from the target, so narrowing there too would
        # subtract them twice and hide a real mismatch
        q = self.query(self.PROP_SRC, self.PROP_DST, {})
        o19etl.archived_column_parity(
            q, "stage", "carlos", pruned_property_prefixes=("OLIS_",))
        target = [q for q in self.seen if "`carlos`" in q]
        self.assertEqual(len(target), 1)
        self.assertNotIn("AND NOT (", target[0])

    def test_merge_excluded_rows_are_left_out_of_the_staging_count(self):
        # merge_statement never inserts them and
        # archived_backfill_statement deliberately skips them, so they
        # have no target value to count
        entry = o19map_schema.TABLES["secObjectName"]
        self.assertTrue(entry.get("merge_exclude"),
                        "secObjectName no longer carries a merge_exclude "
                        "-- this test has lost its subject")
        q = self.query({"secObjectName": ["objectName", "note"]},
                       {"secObjectName": ["objectName",
                                          "import_archived_note"]},
                       {("stage", "secObjectName", "note"): 7,
                        ("carlos", "secObjectName",
                         "import_archived_note"): 7})
        ok, bad = o19etl.archived_column_parity(q, "stage", "carlos")
        self.assertEqual(bad, [])
        staging = [q for q in self.seen if "`stage`" in q]
        self.assertEqual(len(staging), 1)
        self.assertIn(entry["merge_exclude"], staging[0])

    def test_a_table_with_no_tolerance_gets_no_exclusion(self):
        q = self.query(self.SRC, self.DST,
                       {("stage", "Contact", "legacyFlag"): 3,
                        ("carlos", "Contact",
                         "import_archived_legacyFlag"): 3})
        o19etl.archived_column_parity(
            q, "stage", "carlos", pruned_property_prefixes=("OLIS_",))
        staging = [q for q in self.seen if "`stage`" in q]
        self.assertEqual(len(staging), 1)
        self.assertNotIn("AND NOT (", staging[0])

    def test_a_real_mismatch_still_fails_with_the_tolerance_in_place(self):
        # the tolerance must not become a blanket excuse: a column that
        # arrived short for any other reason is still a mismatch
        q = self.query(self.PROP_SRC, self.PROP_DST,
                       {("stage", "property", "lastUpdateDate"): 40,
                        ("carlos", "property",
                         "import_archived_lastUpdateDate"): 11})
        ok, bad = o19etl.archived_column_parity(
            q, "stage", "carlos", pruned_property_prefixes=("OLIS_",))
        self.assertEqual(len(bad), 1)
        self.assertIn("40 non-null value(s) in staging, 11", bad[0])


class TestArchivedColumnExclusions(unittest.TestCase):

    """The tolerance as a value, so what it covers is assertable without
    reading generated SQL."""

    def test_a_copy_table_has_no_exclusion(self):
        self.assertEqual(
            o19etl.archived_column_exclusions(
                "demographic", ("OLIS_",), ("logintitle",)), [])

    def test_property_carries_the_prune_predicate(self):
        out = o19etl.archived_column_exclusions(
            "property", ("OLIS_",), ("logintitle",))
        self.assertEqual(len(out), 1)
        self.assertIn("s.`name`", out[0])

    def test_property_without_a_prune_list_carries_nothing(self):
        self.assertEqual(o19etl.archived_column_exclusions("property"), [])

    def test_a_merge_excluded_table_carries_its_own_predicate(self):
        out = o19etl.archived_column_exclusions("secObjectName")
        self.assertEqual(
            out, [o19map_schema.TABLES["secObjectName"]["merge_exclude"]])

    def test_an_unknown_table_is_not_an_error(self):
        # parity walks the TARGET's tables, which include CARLOS tables
        # the manifest never mentions
        self.assertEqual(
            o19etl.archived_column_exclusions("not_in_the_manifest"), [])


class TestPreservedParity(unittest.TestCase):
    """The count that makes "no data orphaned" a measurement.

    Every staging table CARLOS itself has no home for -- archive,
    removed-module, reference and the unclassified tables a clinic's own
    fork carries -- has to be found again, row for row, in the copies it
    was preserved into. Before this existed the archive schema had never
    been row-verified at all.
    """

    #: one real table of each preserved class, plus a table the manifest
    #: never classified (a clinic customisation)
    ARCHIVED = next(t for t, e in o19map_schema.TABLES.items()
                    if e["class"] == "archive")
    DROPPED = next(t for t, e in o19map_schema.TABLES.items()
                   if e["class"] == "drop")
    REFERENCE = next(t for t, e in o19map_schema.TABLES.items()
                     if e["class"] == "reference")
    UNKNOWN = "clinic_custom_notes"
    MERGE = next(t for t, e in o19map_schema.TABLES.items()
                 if e["class"] == "merge")

    def query(self, staging, archive, live):
        """A fake answering information_schema table lists and COUNT(*).

        `staging`/`archive`/`live` are {table: rows}; a table absent from
        one of them is absent from that schema entirely, which is the
        case the parity has to catch."""
        def q(sql):
            for schema, rows in (("stage", staging), ("arch", archive),
                                 ("carlos", live)):
                if "TABLE_SCHEMA = '{0}'".format(schema) in sql:
                    return [[t] for t in sorted(rows)]
            for schema, rows in (("`stage`.", staging), ("`arch`.", archive),
                                 ("`carlos`.", live)):
                if schema in sql:
                    name = sql.split(schema, 1)[1].strip().strip("`")
                    return [[str(rows[name])]]
            raise AssertionError("unexpected query: " + sql)
        return q

    def parity(self, staging, archive, live):
        return o19etl.preserved_parity(
            self.query(staging, archive, live), "stage", "carlos", "arch")

    def prefixed(self, table):
        return o19etl.ARCHIVED_PREFIX + table

    # --- merge: archived, but with no live twin -----------------------
    #
    # A merge keeps CARLOS's row on a shared natural key, so the clinic's
    # other columns on that key never become live. That is policy; it is
    # not a licence to leave them nowhere once --cleanup drops staging.
    # No `import_archived_` twin, for the same reason `reference` has
    # none: the live table exists and holds CARLOS's rows.

    def test_a_merge_table_is_verified_against_the_archive_alone(self):
        ok, bad = self.parity({self.MERGE: 9}, {self.MERGE: 9}, {})
        self.assertEqual(bad, [])
        self.assertEqual(len(ok), 1)
        self.assertIn("(merge): staging 9 -> arch.{0} 9".format(self.MERGE),
                      ok[0])

    def test_a_merge_table_with_no_archive_is_a_mismatch(self):
        ok, bad = self.parity({self.MERGE: 9}, {}, {})
        self.assertEqual(len(bad), 1)
        self.assertIn("9 staging row(s) and no copy at arch.{0}".format(
            self.MERGE), bad[0])

    def test_a_merge_table_is_not_asked_for_a_live_twin(self):
        # asking for one would fail every import: nothing creates it, and
        # nothing should
        ok, bad = self.parity({self.MERGE: 9}, {self.MERGE: 9}, {})
        self.assertNotIn(self.prefixed(self.MERGE), " ".join(ok + bad))

    def test_an_empty_merge_table_is_not_a_mismatch(self):
        self.assertEqual(self.parity({self.MERGE: 0}, {}, {}), ([], []))

    def test_a_preserved_table_present_in_both_homes_passes(self):
        ok, bad = self.parity(
            {self.ARCHIVED: 5, self.UNKNOWN: 3},
            {self.ARCHIVED: 5, self.UNKNOWN: 3},
            {self.prefixed(self.ARCHIVED): 5,
             self.prefixed(self.UNKNOWN): 3})
        self.assertEqual(bad, [])
        self.assertEqual(len(ok), 2)

    def test_a_missing_live_twin_is_a_mismatch(self):
        ok, bad = self.parity({self.ARCHIVED: 5}, {self.ARCHIVED: 5}, {})
        self.assertEqual(len(bad), 1)
        self.assertIn("no copy at carlos.{0}".format(
            self.prefixed(self.ARCHIVED)), bad[0])

    def test_a_missing_archive_copy_is_a_mismatch(self):
        ok, bad = self.parity({self.ARCHIVED: 5}, {},
                              {self.prefixed(self.ARCHIVED): 5})
        self.assertEqual(len(bad), 1)
        self.assertIn("no copy at arch.{0}".format(self.ARCHIVED), bad[0])

    def test_a_short_preserved_copy_is_a_mismatch(self):
        ok, bad = self.parity({self.ARCHIVED: 5}, {self.ARCHIVED: 5},
                              {self.prefixed(self.ARCHIVED): 4})
        self.assertEqual(len(bad), 1)
        self.assertIn("staging 5 row(s)", bad[0])
        self.assertIn("holds 4", bad[0])

    def test_a_removed_module_table_is_checked_like_any_other(self):
        # the rows --cleanup used to destroy: they now have to be found
        # in both homes before the drop is allowed
        ok, bad = self.parity({self.DROPPED: 7}, {self.DROPPED: 7}, {})
        self.assertEqual(len(bad), 1)
        self.assertIn(self.DROPPED, bad[0])

    def test_a_reference_table_needs_the_archive_copy_only(self):
        # the live table exists holding CARLOS's own rows -- the clinic's
        # go to the archive schema, and a live twin would have nowhere to
        # live under a name that is already taken
        ok, bad = self.parity({self.REFERENCE: 9}, {self.REFERENCE: 9}, {})
        self.assertEqual(bad, [])
        self.assertEqual(len(ok), 1)

    def test_an_empty_staging_table_needs_no_copy(self):
        # nothing to orphan, so nothing to require
        ok, bad = self.parity({self.ARCHIVED: 0}, {}, {})
        self.assertEqual((ok, bad), ([], []))

    def test_copy_class_tables_are_left_to_row_parity(self):
        copied = next(t for t, e in o19map_schema.TABLES.items()
                      if e["class"] == "copy")
        ok, bad = self.parity({copied: 40}, {}, {})
        self.assertEqual((ok, bad), ([], []))


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

    """Curating away a coercion refusal, and what stays checked."""
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

    """Capacity measured in bytes where MySQL measures in bytes.

    A same-declared TEXT column is not the same capacity once the
    charset widens, which is exactly the latin1 -> utf8mb4 move this
    import makes."""
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


def is_rows(tables):
    """information_schema rows for a fake: the TABLES list, and a COLUMNS
    row per manifest column.

    row_parity introspects the staged columns so it can compare the shape
    the copy actually wrote (effective_entry), so a fake that answers every
    information_schema query with a table list leaves it believing the dump
    has no columns at all.
    """
    def answer(sql, tables=tables):
        if sql.startswith("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE"):
            return [[t, c, "varchar", "varchar(255)", "YES", 255,
                     "\\0NONE", "", 1020]
                    for t, cols in tables.items() for c in cols]
        return [[t] for t in tables]
    return answer


class TestMergeReverseParity(unittest.TestCase):

    """Merge tables verified in reverse: every staging row has a twin."""
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

        info = is_rows({"property": entry["cols"]})

        def q(sql):
            if "information_schema" in sql:
                return info(sql)
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

        # the parent is NOT in the dump; the child's own columns are, or
        # parity refuses it for a missing merge key before reaching the join
        info = is_rows({table: entry["cols"]})

        def q(sql):
            if "information_schema" in sql:
                return info(sql)
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

        cols2 = {table: entry["cols"]}
        for parent in parents:
            cols2[parent] = o19map_schema.TABLES[parent]["cols"]
        info2 = is_rows(cols2)

        def q2(sql):
            if "information_schema" in sql:
                return info2(sql)
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

        info = is_rows({table: entry["cols"]})

        def q(sql):
            if "information_schema" in sql:
                return info(sql)
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

    """Text that CARLOS stores as a number must parse as one.

    Otherwise the copy stores 0 and the value is gone with no error."""
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
        # above the replace_seed DELETE inside the chunked branch.
        # The copy path moved out of run_etl into etl_copy_table when the
        # phase was decomposed; the invariant did not move with it, so
        # the walk follows the code rather than the other way round.
        src = inspect.getsource(o19etl.etl_copy_table)
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
        # The append now lives in etl_copy_table's sibling,
        # etl_absent_table -- extracted verbatim when run_etl was
        # decomposed. That is exactly the "premise going stale" case the
        # failure message below anticipates: the walk is re-pointed and
        # the nesting invariant is unchanged.
        tree = ast.parse(textwrap.dedent(
            inspect.getsource(o19etl.etl_absent_table)))
        found = []

        class Walk(ast.NodeVisitor):
            """Collects every `if` in the function under inspection."""

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
            "this test walks etl_absent_table for the single "
            "absent_tables.append, "
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
        block = textwrap.dedent(
            inspect.getsource(o19etl.etl_absent_table))
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
