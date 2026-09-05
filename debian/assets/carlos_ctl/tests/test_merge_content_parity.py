# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The merge class, checked by VALUE (M22 stage 4, third part).

A merge table's live rows come from two places — CARLOS's seed and the
clinic's appended rows — and after the insert nothing in the live table
says which is which. `row_parity` therefore cannot see the merge's
actual policy at all: a merge that OVERWROTE every seed row with the
clinic's values moves exactly the same number of rows as one that let
the seed win.

`etl_merge_table` takes a snapshot of the target before the insert
(`<table>__preseed`), which is what makes three separate claims
checkable:

1. the seed rows are still exactly as they were;
2. every appended row holds what the merge's own INSERT produced,
   paired through the id map when AUTO_INCREMENT moved the id;
3. every seed row that beat a clinic row carries that row's values in
   its `import_archived_` columns — requirement B for the merge class.

Run (from debian/assets):
    python3 -m unittest carlos_ctl.tests.test_merge_content_parity -v
"""

import unittest

from carlos_ctl import o19etl, o19map_schema


#: a merge table whose id AUTO_INCREMENT reassigns, so the appended rows
#: are paired through the id map
SURROGATE = "appointment_status"
#: a merge table whose natural key IS its primary key (no id to move)
NATURAL = "encountertemplate"
#: ... and one the manifest also excludes rows from
EXCLUDED = "secObjectName"


def dst_cols(names, **over):
    """Target column info: nullable varchars unless overridden."""
    out = {}
    for name in names:
        out[name] = {"type": "varchar", "column_type": "varchar(255)",
                     "nullable": True, "char_len": 255, "octet_len": 1020,
                     "has_default": False, "default": None,
                     "auto_increment": False}
        out[name].update(over.get(name) or {})
    return out


def entry_for(table):
    return o19map_schema.TABLES[table]


class TestTheSeedStatement(unittest.TestCase):
    """`merge_seed_change_sql` — claim 1."""

    def sql(self, cols=("id", "status"), key=("id",), **kw):
        return o19etl.merge_seed_change_sql(
            "t", "carlos", "o19_archive", dst_cols(cols), key,
            [c for c in cols], **kw)

    def test_it_reads_the_snapshot_against_the_live_table(self):
        sql = self.sql()
        self.assertIn("FROM `o19_archive`.`t__preseed` p LEFT JOIN "
                      "`carlos`.`t` d", sql)

    def test_a_deleted_seed_row_is_a_violation_not_a_pass(self):
        """An INNER JOIN would silently forgive the import for removing
        a seed row, which is the loudest way the merge could be wrong."""
        sql = self.sql()
        self.assertIn("LEFT JOIN", sql)
        self.assertIn("WHERE (d.`id` IS NULL OR NOT (", sql)

    def test_it_pairs_on_every_primary_key_column(self):
        sql = self.sql(cols=("a", "b", "v"), key=("a", "b"))
        self.assertIn("ON d.`a` <=> p.`a` AND d.`b` <=> p.`b`", sql)

    def test_a_character_column_is_compared_under_a_binary_collation(self):
        """The target's own collation folds accents and case, so a plain
        `<=>` would call 'Santé' and 'Sante' the same seed row."""
        self.assertIn("CONVERT(d.`status` USING utf8mb4) COLLATE "
                      "utf8mb4_bin <=> CONVERT(p.`status` USING utf8mb4) "
                      "COLLATE utf8mb4_bin", self.sql())

    def test_a_number_is_compared_by_value_not_rendered(self):
        sql = o19etl.merge_seed_change_sql(
            "t", "carlos", "o19_archive",
            dst_cols(["id"], id={"type": "int", "column_type": "int(11)",
                                 "nullable": False}),
            ("id",), ["id"])
        self.assertIn("d.`id` <=> p.`id`", sql)
        self.assertNotIn("CONVERT", sql)

    def test_a_projection_replaces_the_count(self):
        self.assertTrue(self.sql(select="p.`id`").startswith(
            "SELECT p.`id` FROM"))

    def test_a_declared_later_deletion_is_not_read_as_seed_loss(self):
        """P6 prunes the removed modules' `property` rows, and the
        prune matches on NAME — so it can take a CARLOS seed row as
        readily as a clinic one. Without the exclusion the import's own
        declared deletion reads as the seed having been destroyed."""
        sql = self.sql(exclude=o19etl.pruned_property_predicate(
            ["caisi."], ["oldkey"], alias="p"))
        self.assertIn("AND NOT (p.`name` LIKE 'caisi.%' OR "
                      "p.`name` = 'oldkey')", sql)
        # and the exclusion narrows the finding, never the pairing
        self.assertIn("LEFT JOIN", sql)

    def test_the_exclusion_asks_about_the_snapshot_not_staging(self):
        """The seed claim's subject is the pre-merge row. A predicate
        addressed to `s` would name no column of this statement."""
        self.assertNotIn(
            "s.`name`", o19etl.pruned_property_predicate(
                ["caisi."], (), alias="p"))

    def test_only_property_has_a_declared_later_deletion(self):
        """A blanket exclusion would quietly stop the seed claim
        working on every other merge table."""
        self.assertIsNone(o19etl.seed_exclusion(
            "appointment_status", ["caisi."], ["oldkey"]))
        self.assertIsNotNone(o19etl.seed_exclusion(
            "property", ["caisi."], ()))
        # ... and nothing is excluded when nothing is pruned
        self.assertIsNone(o19etl.seed_exclusion("property", (), ()))


class TestTheAppendedStatement(unittest.TestCase):
    """`merge_appended_mismatch_sql` — claim 2."""

    def sql(self, table=SURROGATE, entry=None, **kw):
        entry = entry or entry_for(table)
        cols = dst_cols(entry["cols"])
        return o19etl.merge_appended_mismatch_sql(
            table, entry, "o19_import", "carlos", "o19_archive",
            cols, ["id"] if entry.get("surrogate_pk") else
            entry["merge_keys"], **kw)

    def test_a_surrogate_table_is_paired_through_the_id_map(self):
        """AUTO_INCREMENT chose the live id, so nothing but the map
        knows which live row a staging row became."""
        sql = self.sql()
        self.assertIn("JOIN `o19_archive`.`appointment_status__idmap` m "
                      "ON m.old_id <=> s.`id`", sql)
        self.assertIn("JOIN `carlos`.`appointment_status` d ON d.`id` "
                      "<=> m.new_id", sql)

    def test_a_natural_key_table_is_paired_on_that_key(self):
        entry = entry_for(NATURAL)
        sql = self.sql(table=NATURAL, entry=entry)
        self.assertNotIn("__idmap", sql)
        self.assertIn("JOIN `carlos`.`encountertemplate` d ON", sql)
        for k in entry["merge_keys"]:
            self.assertIn("d.`{0}` <=>".format(k), sql)

    def test_rows_the_seed_won_are_excluded(self):
        """Their live values are CARLOS's by design; comparing them to
        staging would report the merge's own policy as a defect."""
        self.assertIn("NOT EXISTS (SELECT 1 FROM `o19_archive`."
                      "`appointment_status__preseed` p WHERE p.`id` <=> "
                      "d.`id`)", self.sql())

    def test_the_surrogate_is_not_itself_compared(self):
        """The merge deliberately did not carry the clinic's id."""
        sql = self.sql()
        self.assertNotIn("d.`id` <=> s.`id`", sql)

    def test_the_comparison_is_the_merges_own_expression(self):
        entry = entry_for(SURROGATE)
        cols = dst_cols(entry["cols"])
        sql = self.sql()
        insert = o19etl.merge_statement(
            SURROGATE, entry, "o19_import", "carlos", cols)
        for col in entry["cols"]:
            if col == entry.get("surrogate_pk"):
                continue
            expr = o19etl.written_expr(entry, col, cols)
            self.assertIn(expr, sql, col)
            self.assertIn(expr, insert, col)

    def test_excluded_rows_are_left_out_the_way_the_insert_left_them_out(
            self):
        entry = entry_for(EXCLUDED)
        sql = self.sql(table=EXCLUDED, entry=entry)
        self.assertIn("AND NOT ({0})".format(entry["merge_exclude"]), sql)


class TestTheBackfillStatement(unittest.TestCase):
    """`merge_backfill_mismatch_sql` — claim 3, requirement B."""

    ENTRY = {"class": "merge", "merge_keys": ["name"],
             "cols": ["id", "name", "import_archived_note"],
             "archived_cols": {"import_archived_note": "note"},
             "renames": {"import_archived_note": "note"}}

    def sql(self, entry=None, **kw):
        return o19etl.merge_backfill_mismatch_sql(
            "t", entry or self.ENTRY, "o19_import", "carlos",
            "o19_archive", ("id",), **kw)

    def test_it_asks_only_about_rows_that_were_already_there(self):
        self.assertIn("EXISTS (SELECT 1 FROM `o19_archive`.`t__preseed` "
                      "p WHERE p.`id` <=> d.`id`)", self.sql())

    def test_it_compares_the_archived_column_against_its_source(self):
        self.assertIn("d.`import_archived_note` <=> s.`note`", self.sql())

    def test_a_twin_is_enough_rather_than_every_twin(self):
        """The backfill's UPDATE ... JOIN assigns from ONE of several
        staging rows sharing the key and the server does not say which,
        so demanding all of them would fail an import that is right."""
        sql = self.sql()
        self.assertIn("AND EXISTS (", sql)
        self.assertIn("AND NOT EXISTS (", sql)

    def test_a_seed_row_with_no_clinic_twin_is_not_a_finding(self):
        """Nothing was dropped for it, so nothing is owed to it."""
        self.assertIn("AND EXISTS (SELECT 1 FROM `o19_import`.`t` s "
                      "WHERE", self.sql())

    def test_excluded_rows_are_owed_nothing(self):
        entry = dict(self.ENTRY, merge_exclude="s.`name` = 'x'")
        self.assertEqual(self.sql(entry=entry).count(
            "AND NOT (s.`name` = 'x')"), 2)


class Db(object):
    """Answers the shapes `merge_content_parity` asks for."""

    def __init__(self, src_tables, arch_columns, keys, counts=None,
                 errors=None):
        self.src_tables = set(src_tables)
        self.arch_columns = dict(arch_columns)
        self.keys = dict(keys)
        self.counts = dict(counts or {})
        self.errors = dict(errors or {})
        self.sql = []

    def kind(self, sql):
        if "__preseed` p LEFT JOIN" in sql:
            return "seed"
        if "FROM `o19_import`." in sql.split(" WHERE ", 1)[0]:
            return "appended"
        return "backfill"

    def __call__(self, sql, db=None):
        self.sql.append(sql)
        if "information_schema.TABLES" in sql:
            return [[t] for t in sorted(self.src_tables)]
        if "information_schema.STATISTICS" in sql:
            return [[t, c] for t in sorted(self.keys)
                    for c in self.keys[t]]
        if "information_schema.COLUMNS" in sql:
            return [[t, c, "varchar"] for t in sorted(self.arch_columns)
                    for c in self.arch_columns[t]]
        kind = self.kind(sql)
        if kind in self.errors:
            raise RuntimeError("banner\n" + self.errors[kind])
        return [[str(self.counts.get(kind, 0))]]


class TestTheDriver(unittest.TestCase):

    TABLE = SURROGATE

    def drive(self, counts=None, errors=None, keys=None, arch=None,
              cols=None, details=None):
        entry = entry_for(self.TABLE)
        cols = cols or entry["cols"]
        arch = arch if arch is not None else {
            o19etl.preseed_table(self.TABLE): list(cols),
            o19etl.idmap_table(self.TABLE): ["old_id", "new_id"],
        }
        db = Db([self.TABLE],
                arch,
                keys if keys is not None else {self.TABLE: ["id"]},
                counts, errors)
        info = {self.TABLE: dst_cols(cols)}
        return db, o19etl.merge_content_parity(
            db, "o19_import", "carlos", "o19_archive", info, info,
            details=details)

    def test_a_faithful_merge_passes_all_three_claims(self):
        _db, (ok, bad) = self.drive()
        self.assertEqual(bad, [])
        self.assertTrue(any("seed rows are unchanged" in x for x in ok), ok)
        self.assertTrue(any("every appended row" in x for x in ok), ok)

    def test_a_changed_seed_row_fails(self):
        _db, (ok, bad) = self.drive(counts={"seed": 2})
        self.assertTrue(any("2 pre-merge CARLOS row(s)" in x for x in bad),
                        bad)

    def test_an_appended_row_holding_another_value_fails(self):
        _db, (ok, bad) = self.drive(counts={"appended": 5})
        self.assertTrue(any("5 appended row(s)" in x for x in bad), bad)

    def test_a_failed_query_is_a_finding_not_a_pass(self):
        _db, (ok, bad) = self.drive(errors={"seed": "ERROR 1142 denied"})
        self.assertTrue(any("could not be checked" in x for x in bad), bad)
        self.assertTrue(any("ERROR 1142" in x for x in bad), bad)
        self.assertFalse(any("banner" in x for x in bad), bad)

    def test_a_missing_snapshot_is_reported_not_assumed_fine(self):
        """An import from before the snapshot existed cannot tell a seed
        row from an appended one — which is not the same as passing."""
        _db, (ok, bad) = self.drive(arch={})
        self.assertEqual(bad, [])
        self.assertTrue(any(x.startswith("NOT CHECKED") and
                            "no pre-merge snapshot" in x for x in ok), ok)

    def test_a_missing_id_map_blocks_only_the_appended_claim(self):
        arch = {o19etl.preseed_table(self.TABLE):
                list(entry_for(self.TABLE)["cols"])}
        _db, (ok, bad) = self.drive(arch=arch)
        self.assertEqual(bad, [])
        self.assertTrue(any("no id map" in x for x in ok), ok)
        # the seed claim still stands: it needs no map
        self.assertTrue(any("seed rows are unchanged" in x for x in ok), ok)

    def test_a_table_with_no_primary_key_is_reported_unchecked(self):
        _db, (ok, bad) = self.drive(keys={})
        self.assertEqual(bad, [])
        self.assertTrue(any(x.startswith("NOT CHECKED") for x in ok), ok)

    def test_a_post_step_rewrite_is_named_rather_than_reported_wrong(self):
        db = Db(["secObjPrivilege"],
                {o19etl.preseed_table("secObjPrivilege"): ["objectName"]},
                {"secObjPrivilege": ["objectName"]})
        info = {"secObjPrivilege": dst_cols(["objectName"])}
        ok, bad = o19etl.merge_content_parity(
            db, "o19_import", "carlos", "o19_archive", info, info)
        self.assertEqual(bad, [])
        self.assertTrue(any("NOT CHECKED" in x and "secObjPrivilege" in x
                            for x in ok), ok)
        self.assertEqual(
            [q for q in db.sql if "`carlos`.`secObjPrivilege`" in q], [])

    def test_a_table_absent_from_the_dump_is_left_to_row_parity(self):
        entry = entry_for(self.TABLE)
        db = Db([], {}, {self.TABLE: ["id"]})
        info = {self.TABLE: dst_cols(entry["cols"])}
        ok, bad = o19etl.merge_content_parity(
            db, "o19_import", "carlos", "o19_archive", info, info)
        self.assertEqual((ok, bad), ([], []))

    def test_the_backfill_claim_is_made_only_where_columns_were_archived(
            self):
        """A table with nothing archived owes nothing, and a check that
        printed a pass for it would overstate what was verified."""
        _db, (ok, _bad) = self.drive()
        self.assertFalse(any("archived values" in x for x in ok), ok)

    def test_a_backfilled_table_makes_the_third_claim(self):
        table, cols = self.TABLE, ["id", "status", "import_archived_x"]
        entry = dict(entry_for(table), cols=cols,
                     archived_cols={"import_archived_x": "x"},
                     renames={"import_archived_x": "x"})
        db = Db([table],
                {o19etl.preseed_table(table): cols,
                 o19etl.idmap_table(table): ["old_id", "new_id"]},
                {table: ["id"]}, {"backfill": 3})
        info = {table: dst_cols(cols)}
        saved = o19map_schema.TABLES[table]
        o19map_schema.TABLES[table] = entry
        try:
            ok, bad = o19etl.merge_content_parity(
                db, "o19_import", "carlos", "o19_archive", info, info)
        finally:
            o19map_schema.TABLES[table] = saved
        self.assertTrue(any("3 seed row(s)" in x for x in bad), bad)

    def test_the_archived_backfill_is_not_read_as_seed_corruption(self):
        """P4 folds the `import_archived_` columns into the entry before
        it writes; P7 must fold the same way. Without it the backfill --
        which writes those columns onto SEED rows, after the snapshot,
        by design -- reads as the seed having been corrupted, on every
        clinic whose dump carries an unmapped column."""
        table = self.TABLE
        entry = entry_for(table)
        # a column the manifest has no home for: P4 preserves it as
        # `import_archived_clinicextra`, filling it on seed rows too
        src = dict(dst_cols(list(entry["cols"]) + ["clinicextra"]))
        dst = dict(dst_cols(list(entry["cols"])
                            + ["import_archived_clinicextra"]))
        arch = {o19etl.preseed_table(table):
                list(entry["cols"]) + ["import_archived_clinicextra"],
                o19etl.idmap_table(table): ["old_id", "new_id"]}
        db = Db([table], arch, {table: ["id"]})
        ok, bad = o19etl.merge_content_parity(
            db, "o19_import", "carlos", "o19_archive",
            {table: src}, {table: dst})
        self.assertEqual(bad, [])
        seed = [q for q in db.sql if "__preseed` p LEFT JOIN" in q][0]
        self.assertNotIn("import_archived_clinicextra", seed)
        # ... and the column is not merely ignored: it gets its own claim
        self.assertTrue(any("carries its archived values" in line
                            for line in ok), ok)

    def test_each_failing_claim_contributes_its_own_keys(self):
        """Three claims, three different rows to name — and the seed
        claim reads them from the SNAPSHOT, because a seed row the
        import deleted has no live side left to name it by."""
        table, cols = self.TABLE, ["id", "status", "import_archived_x"]
        entry = dict(entry_for(table), cols=cols,
                     archived_cols={"import_archived_x": "x"},
                     renames={"import_archived_x": "x"})
        db = Db([table],
                {o19etl.preseed_table(table): cols,
                 o19etl.idmap_table(table): ["old_id", "new_id"]},
                {table: ["id"]},
                {"seed": 1, "appended": 2, "backfill": 3})
        info = {table: dst_cols(cols)}
        saved = o19map_schema.TABLES[table]
        o19map_schema.TABLES[table] = entry
        details = []
        try:
            _ok, bad = o19etl.merge_content_parity(
                db, "o19_import", "carlos", "o19_archive", info, info,
                details=details)
        finally:
            o19map_schema.TABLES[table] = saved
        self.assertEqual(len(bad), 3, bad)
        for label in ("merge/seed", "merge/appended", "merge/backfill"):
            self.assertTrue(any(label in line for line in details),
                            (label, details))
        seed = [q for q in db.sql
                if "__preseed` p LEFT JOIN" in q and "LIMIT" in q][0]
        self.assertTrue(seed.startswith("SELECT p.`id` FROM"), seed)

    def test_a_faithful_merge_contributes_no_keys(self):
        details = []
        _db, (_ok, bad) = self.drive(details=details)
        self.assertEqual(bad, [])
        self.assertEqual(details, [])

    def test_the_snapshots_own_columns_bound_the_seed_comparison(self):
        """A snapshot taken by an older carlos-ctl may lack a column the
        live table has now; naming it would make the statement an error
        rather than an answer."""
        entry = entry_for(self.TABLE)
        arch = {o19etl.preseed_table(self.TABLE): [entry["cols"][0]],
                o19etl.idmap_table(self.TABLE): ["old_id", "new_id"]}
        db, (ok, bad) = self.drive(arch=arch)
        seed = [q for q in db.sql if "__preseed` p LEFT JOIN" in q][0]
        for col in entry["cols"][1:]:
            self.assertNotIn("p.`{0}`".format(col), seed)


if __name__ == "__main__":
    unittest.main()
