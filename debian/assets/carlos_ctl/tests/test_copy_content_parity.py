# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The copy class, checked by VALUE (M22 stage 4, second half).

`row_parity` counts copy-class tables. A copy that moved the right
NUMBER of rows with the wrong values passes it, and the preserved-copy
digest cannot help here: a copy table has a declared TRANSFORM between
the two sides, so the expected value is an expression and the stored one
a column. Hashing those disagrees on a faithful copy (a stored
DECIMAL(10,4) renders '1.4000' where the same value as an expression
over a DECIMAL(10,2) source renders '1.40'), so the pairing is a NULL-
safe `<=>` join, which compares VALUES under the server's own type
rules.

The expressions are the copy's own -- `source_expr` + `sanitize_expr`,
the very ones `copy_statement` selects -- so the check cannot model the
copy differently from the copy.

Run (from debian/assets):
    python3 -m unittest carlos_ctl.tests.test_copy_content_parity -v
"""

import unittest

from carlos_ctl import o19etl, o19map_schema


def dst_cols(**over):
    """Target column info, all nullable varchars unless overridden."""
    out = {}
    for name, info in over.items():
        base = {"type": "varchar", "column_type": "varchar(255)",
                "nullable": True, "char_len": 255, "octet_len": 1020,
                "has_default": False, "default": None,
                "auto_increment": False}
        base.update(info or {})
        out[name] = base
    return out


ENTRY = {"class": "copy", "cols": ["id", "name"]}
COLS = dst_cols(id={"type": "int", "column_type": "int(11)",
                    "nullable": False}, name={})


class TestTheStatementPairsAndCompares(unittest.TestCase):

    def sql(self, entry=None, cols=None, key=("id",), **kw):
        return o19etl.copy_value_mismatch_sql(
            "t", entry or ENTRY, "o19_import", "carlos", cols or COLS,
            key, **kw)

    # `id` is NOT NULL on the target, so both sides carry the NOT NULL
    # coercion `sanitize_expr` adds — the check models the write, and the
    # write is `IFNULL(s.`id`, 0)` because the server would have made the
    # same substitution silently. See `not_null_fallback`.
    def test_it_joins_staging_to_the_target_on_the_primary_key(self):
        sql = self.sql()
        self.assertIn("FROM `o19_import`.`t` s JOIN `carlos`.`t` d", sql)
        self.assertIn("ON d.`id` <=> IFNULL(s.`id`, 0)", sql)

    def test_it_counts_the_rows_whose_values_disagree(self):
        sql = self.sql()
        self.assertTrue(sql.startswith("SELECT COUNT(*)"))
        self.assertIn("WHERE NOT (d.`id` <=> IFNULL(s.`id`, 0) AND "
                      "CONVERT(d.`name` USING utf8mb4) COLLATE "
                      "utf8mb4_bin <=> CONVERT(s.`name` USING utf8mb4) "
                      "COLLATE utf8mb4_bin)", sql)

    def test_the_comparison_is_null_safe(self):
        """A copied NULL must compare equal to the NULL that arrived. `=`
        would make every nullable column's NULL rows look like
        mismatches, on every clinic."""
        self.assertNotIn("d.`name` = ", self.sql())
        self.assertIn("<=>", self.sql())

    def test_a_multi_column_key_pairs_on_all_of_it(self):
        entry = {"class": "copy", "cols": ["a", "b", "v"]}
        cols = dst_cols(a={}, b={}, v={})
        sql = self.sql(entry=entry, cols=cols, key=("a", "b"))
        self.assertIn("ON d.`a` <=> s.`a` AND d.`b` <=> s.`b`", sql)

    def test_a_renamed_column_is_read_from_its_source_name(self):
        entry = {"class": "copy", "cols": ["id", "isactive"],
                 "renames": {"isactive": "isActive"}}
        cols = dst_cols(id={"nullable": False}, isactive={})
        self.assertIn(
            "CONVERT(d.`isactive` USING utf8mb4) COLLATE utf8mb4_bin <=> "
            "CONVERT(s.`isActive` USING utf8mb4) COLLATE utf8mb4_bin",
            self.sql(entry=entry, cols=cols))

    def test_a_curated_expression_is_the_one_the_copy_uses(self):
        entry = {"class": "copy", "cols": ["id", "receivedDate"],
                 "value_exprs": {"receivedDate": "s.`observationdate`"}}
        cols = dst_cols(id={"nullable": False}, receivedDate={})
        self.assertIn(
            "CONVERT(d.`receivedDate` USING utf8mb4) COLLATE utf8mb4_bin "
            "<=> CONVERT(s.`observationdate` USING utf8mb4) COLLATE "
            "utf8mb4_bin", self.sql(entry=entry, cols=cols))

    def test_a_zero_date_is_expected_as_the_null_the_copy_wrote(self):
        entry = {"class": "copy", "cols": ["id", "d"]}
        cols = dst_cols(id={"nullable": False},
                        d={"type": "date", "column_type": "date"})
        sql = self.sql(entry=entry, cols=cols)
        self.assertIn("d.`d` <=> NULLIF(s.`d`, '0000-00-00')", sql)

    def test_an_enum_falls_back_the_way_the_copy_folded_it(self):
        entry = {"class": "copy", "cols": ["id", "e"]}
        cols = dst_cols(id={"nullable": False},
                        e={"type": "enum", "column_type": "enum('a','b')"})
        sql = self.sql(entry=entry, cols=cols)
        self.assertIn("CASE WHEN s.`e` IS NULL THEN NULL WHEN s.`e` "
                      "IN ('a', 'b') THEN s.`e` ELSE NULL END", sql)
        self.assertIn("CONVERT(d.`e` USING utf8mb4) COLLATE utf8mb4_bin",
                      sql)

    def test_a_repaired_column_is_expected_repaired(self):
        entry = {"class": "copy", "cols": ["id", "name"]}
        sql = self.sql(entry=entry, repaired={"name"})
        self.assertIn("CONVERT(", sql)
        self.assertNotIn("d.`name` <=> s.`name` ", sql + " ")

    def test_an_archived_column_is_not_sanitized(self):
        """`copy_statement` copies an `import_archived_` column verbatim
        into a column of the SOURCE's type; sanitizing here would expect
        a zero date to have become NULL when the copy kept it."""
        entry = {"class": "copy", "cols": ["id", "import_archived_d"],
                 "archived_cols": {"import_archived_d": "d"},
                 "renames": {"import_archived_d": "d"}}
        cols = dst_cols(id={"nullable": False},
                        import_archived_d={"type": "date",
                                           "column_type": "date"})
        sql = self.sql(entry=entry, cols=cols)
        self.assertIn("d.`import_archived_d` <=> s.`d`", sql)
        self.assertNotIn("NULLIF", sql)

    def test_a_projection_replaces_the_count(self):
        sql = self.sql(select="s.`id`")
        self.assertTrue(sql.startswith("SELECT s.`id` FROM"))


class TestTheExpressionsAreTheCopysOwn(unittest.TestCase):

    """The one property that makes this a verification rather than a
    second opinion: if the check built its own idea of the transform, a
    divergence in intent would show up as a false alarm on a correct
    migration -- the failure mode that gets a check switched off."""

    def test_every_compared_expression_appears_in_the_copy_statement(self):
        entry = {"class": "copy", "cols": ["id", "isactive", "d", "e"],
                 "renames": {"isactive": "isActive"}}
        cols = dst_cols(
            id={"type": "int", "column_type": "int(11)", "nullable": False},
            isactive={},
            d={"type": "date", "column_type": "date"},
            e={"type": "enum", "column_type": "enum('a','b')"})
        copy = o19etl.copy_statement("t", entry, "o19_import", "carlos",
                                     cols)
        check = o19etl.copy_value_mismatch_sql(
            "t", entry, "o19_import", "carlos", cols, ("id",))
        # the WHERE half only: the ON clause repeats the key comparison
        where = check.split(" WHERE NOT (", 1)[1][:-1]
        for col in entry["cols"]:
            expr = o19etl.sanitize_expr(
                o19etl.source_expr(entry, col, None, None,
                                   cols[col]["nullable"]), cols[col])
            self.assertIn(expr, where,
                          "{0}: the check does not compare against the "
                          "copy's own expression".format(col))
            self.assertIn(expr, copy,
                          "{0}: the check compares against an expression "
                          "the copy never wrote".format(col))


class Db(object):
    """Answers the shapes `copy_content_parity` asks for."""

    def __init__(self, tables, keys, mismatches=None, errors=None):
        self.tables = set(tables)
        self.keys = keys
        self.mismatches = dict(mismatches or {})
        self.errors = dict(errors or {})
        self.sql = []

    def __call__(self, sql, db=None):
        self.sql.append(sql)
        if "information_schema.TABLES" in sql:
            return [[t] for t in sorted(self.tables)]
        if "information_schema.STATISTICS" in sql:
            return [[t, c] for t in sorted(self.keys)
                    for c in self.keys[t]]
        table = sql.split("`o19_import`.`", 1)[1].split("`", 1)[0]
        if table in self.errors:
            raise RuntimeError("banner\n" + self.errors[table])
        return [[str(self.mismatches.get(table, 0))]]


class TestTheDriver(unittest.TestCase):

    TABLE = next(t for t, e in sorted(o19map_schema.TABLES.items())
                 if e["class"] == "copy" and not e.get("value_exprs")
                 and not e.get("fk_remap"))

    def info(self, cols):
        return {self.TABLE: dst_cols(**dict((c, {}) for c in cols))}

    def drive(self, mismatches=None, errors=None, keys=None,
              cols=None):
        entry = o19map_schema.TABLES[self.TABLE]
        cols = cols or entry["cols"][:2]
        db = Db([self.TABLE], keys if keys is not None
                else {self.TABLE: [cols[0]]}, mismatches, errors)
        info = self.info(cols)
        return db, o19etl.copy_content_parity(
            db, "o19_import", "carlos", info, info)

    def test_a_faithful_copy_passes(self):
        _db, (ok, bad) = self.drive()
        self.assertEqual(bad, [])
        self.assertTrue(any(self.TABLE in line for line in ok))

    def test_a_row_whose_twin_holds_another_value_fails(self):
        _db, (ok, bad) = self.drive(mismatches={self.TABLE: 3})
        self.assertEqual(ok, [])
        self.assertIn("3 copied row(s)", bad[0])

    def test_a_table_with_no_primary_key_is_reported_unchecked(self):
        """Named, not silently passed — "we could not look" and "we
        looked and it was fine" are different answers. But not FAILED
        either: a table with no primary key is a structural fact the
        operator cannot change, and refusing it would block every import
        rather than the unsound ones."""
        _db, (ok, bad) = self.drive(keys={})
        self.assertEqual(bad, [])
        self.assertIn("NOT CHECKED", ok[0])
        self.assertIn("no primary key", ok[0])

    def test_a_key_that_is_not_copied_is_reported_unchecked(self):
        entry = o19map_schema.TABLES[self.TABLE]
        cols = entry["cols"][:2]
        _db, (ok, bad) = self.drive(
            keys={self.TABLE: ["not_copied"]}, cols=cols + ["not_copied"])
        self.assertEqual(bad, [])
        self.assertIn("not copied", ok[0])

    def test_an_unchecked_line_never_reads_as_a_verification(self):
        """It lands in the PASSING list, so its wording is the only thing
        stopping an operator reading it as "checked, and fine"."""
        _db, (ok, _bad) = self.drive(keys={})
        self.assertTrue(ok[0].startswith("NOT CHECKED"), ok[0])

    def test_a_failed_check_is_a_mismatch_not_a_pass(self):
        _db, (ok, bad) = self.drive(
            errors={self.TABLE: "ERROR 1142: SELECT command denied"})
        self.assertEqual(ok, [])
        self.assertIn("could not be checked", bad[0])
        # the last line of the client's error only, never its banner
        self.assertIn("ERROR 1142", bad[0])
        self.assertNotIn("banner", bad[0])

    def test_a_post_step_rewrite_is_named_rather_than_reported_wrong(self):
        """Several copy-class tables are deliberately rewritten AFTER
        the copy — the forced password reset, the role-name and activeyn
        rewrites, the disabled eForms, the folded prevention types, the
        repointed HRM report paths. Their rows no longer hold what the
        copy wrote BY DESIGN, so comparing them would report a mismatch
        on every clinic. `test_post_etl_rewrites` owns the list itself;
        this asserts what the check DOES with it."""
        entry = o19map_schema.TABLES["security"]
        cols = [c for c in entry["cols"][:2]]
        db = Db(["security"], {"security": [cols[0]]},
                mismatches={"security": 99})
        info = {"security": dst_cols(**dict((c, {}) for c in cols))}
        ok, bad = o19etl.copy_content_parity(
            db, "o19_import", "carlos", info, info)
        self.assertEqual(bad, [])
        self.assertTrue(any("NOT CHECKED" in line and "security" in line
                            for line in ok), ok)
        # and it never ASKED: no comparison statement was issued for it
        self.assertEqual(
            [q for q in db.sql if "`o19_import`.`security`" in q], [])

    def test_a_preserved_column_is_verified_like_any_other(self):
        """Requirement B's column half is only a promise until something
        reads it. P4 folds the `import_archived_` columns into the entry
        before it writes; unfolded here, the check would quietly not
        look at the one population the manifest has no home for."""
        table = self.TABLE
        cols = o19map_schema.TABLES[table]["cols"][:2]
        # a DATE, so "not sanitized" is visible: a mapped nullable date
        # would carry NULLIF(..., '0000-00-00'); a preserved one must
        # not, because the copy wrote it verbatim
        date = {"type": "date", "column_type": "date"}
        src = dst_cols(**dict((c, date if c == "clinicextra" else {})
                              for c in cols + ["clinicextra"]))
        dst = dst_cols(**dict(
            (c, date if c.startswith("import_archived_") else {})
            for c in cols + ["import_archived_clinicextra"]))
        db = Db([table], {table: [cols[0]]})
        o19etl.copy_content_parity(db, "o19_import", "carlos",
                                   {table: src}, {table: dst})
        compare = [q for q in db.sql
                   if "information_schema" not in q][0]
        # read from its SOURCE name, and never sanitized: the copy puts
        # it into a column of the source's own type
        self.assertIn("d.`import_archived_clinicextra` <=> "
                      "s.`clinicextra`", compare)
        self.assertNotIn("NULLIF", compare)

    def test_a_table_absent_from_the_dump_is_left_to_row_parity(self):
        entry = o19map_schema.TABLES[self.TABLE]
        cols = entry["cols"][:2]
        db = Db([], {self.TABLE: [cols[0]]})
        ok, bad = o19etl.copy_content_parity(
            db, "o19_import", "carlos", self.info(cols), self.info(cols))
        self.assertEqual((ok, bad), ([], []))


class TestTheKeysOfTheDifferingRows(unittest.TestCase):
    """`key_projection` / `detail_lines` — the private half of the
    answer. The report says HOW MANY rows disagree; an operator fixing
    the import needs to know WHICH, and a primary key joins straight
    back to a patient, an appointment or a bill."""

    def test_the_projection_names_the_key_from_the_alias_that_has_it(self):
        self.assertEqual(o19etl.key_projection(["a", "b"], "d"),
                         "d.`a`, d.`b`")

    def test_the_named_rows_are_the_rows_that_were_counted(self):
        """Same statement, different SELECT: anything else would name a
        different set of rows from the one the finding counted."""
        seen = []

        def query(sql, db=None):
            seen.append(sql)
            return [["7", "x"], ["9", "y"]]
        line, = o19etl.detail_lines(query, "t", "copy", "SELECT s FROM u",
                                    ["id", "kind"])
        self.assertEqual(seen[0], "SELECT s FROM u LIMIT {0}".format(
            o19etl.DETAIL_ROWS))
        self.assertEqual(line, "t (copy): (id=7, kind=x), (id=9, kind=y)")

    def test_the_file_is_bounded_rather_than_a_second_copy_of_the_data(
            self):
        """The file exists so an operator can recognise a pattern. An
        unbounded one would write the clinic's rows, keyed, to disk in
        the name of describing them."""
        asked = []
        o19etl.detail_lines(lambda sql, db=None: asked.append(sql) or [],
                            "t", "copy", "SELECT 1", ["id"])
        self.assertTrue(asked[0].endswith(
            " LIMIT {0}".format(o19etl.DETAIL_ROWS)), asked)
        self.assertLessEqual(o19etl.DETAIL_ROWS, 200)

    def test_no_rows_reads_as_none_rather_than_an_empty_line(self):
        line, = o19etl.detail_lines(lambda sql, db=None: [], "t", "copy",
                                    "SELECT 1", ["id"])
        self.assertIn("no keys returned", line)

    def test_a_failed_lookup_records_why_instead_of_pretending(self):
        """The COUNT already made the finding; failing to NAME the rows
        must not turn into a silent empty list that reads as "none"."""
        def query(sql, db=None):
            raise RuntimeError("banner\nERROR 1142: SELECT denied")
        line, = o19etl.detail_lines(query, "t", "copy", "SELECT 1", ["id"])
        self.assertIn("keys unavailable", line)
        self.assertIn("ERROR 1142", line)
        self.assertNotIn("banner", line)


class TestTheDriverCollectsKeys(unittest.TestCase):

    def test_a_mismatch_contributes_its_keys(self):
        table = TestTheDriver.TABLE
        entry = o19map_schema.TABLES[table]
        cols = entry["cols"][:2]
        db = Db([table], {table: [cols[0]]}, mismatches={table: 2})
        info = {table: dst_cols(**dict((c, {}) for c in cols))}
        details = []
        _ok, bad = o19etl.copy_content_parity(
            db, "o19_import", "carlos", info, info, details=details)
        self.assertTrue(bad)
        self.assertTrue(any(line.startswith("{0} (copy)".format(table))
                            for line in details), details)
        self.assertTrue(any("LIMIT" in q for q in db.sql), db.sql)

    def test_a_faithful_copy_contributes_nothing(self):
        table = TestTheDriver.TABLE
        cols = o19map_schema.TABLES[table]["cols"][:2]
        db = Db([table], {table: [cols[0]]})
        info = {table: dst_cols(**dict((c, {}) for c in cols))}
        details = []
        o19etl.copy_content_parity(db, "o19_import", "carlos", info, info,
                                   details=details)
        self.assertEqual(details, [])


class TestATableRuledTwinExempt(unittest.TestCase):

    """P7 must read the exemption the same way P4 wrote it.

    `archived_column_plan` is the ONE place that answers "does this table
    get live `import_archived_` columns". P7 folds the plan into the
    entry before building its value check, so a check that resolved the
    exemption anywhere else -- or not at all -- would name columns the
    ALTER never created and report every row of the table as a
    mismatch."""

    ENTRY = {
        "class": "copy", "cols": ["id"],
        "dropped": {"programNo": {"nondefault": "s.`programNo` <> 0"}},
    }
    SRC = {"id": {"type": "int", "column_type": "int(10)",
                  "nullable": False},
           "programNo": {"type": "int", "column_type": "int(10)",
                         "nullable": True}}

    def test_the_plan_is_what_both_halves_read(self):
        plan = o19etl.archived_column_plan(self.ENTRY, self.SRC)
        self.assertEqual([t for _s, t, _c in plan],
                         ["import_archived_programNo"])
        exempt = dict(self.ENTRY, archive_twins=False)
        self.assertEqual(
            o19etl.archived_column_plan(exempt, self.SRC), [])

    def test_the_folded_entry_names_no_column_that_was_not_added(self):
        exempt = dict(self.ENTRY, archive_twins=False)
        folded = o19etl.with_archived_columns(
            exempt, o19etl.archived_column_plan(exempt, self.SRC))
        self.assertEqual(folded["cols"], ["id"])
        self.assertNotIn("archived_cols", folded)
        # ...and without the ruling it does name it, so the assertion
        # above is about the ruling and not about an empty entry
        folded = o19etl.with_archived_columns(
            self.ENTRY, o19etl.archived_column_plan(self.ENTRY, self.SRC))
        self.assertIn("import_archived_programNo", folded["cols"])


if __name__ == "__main__":
    unittest.main()
