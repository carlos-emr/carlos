# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The run_etl driver against a fake database.

Until this existed, `run_etl` was the one phase carrying real decisions
that no test could execute: 588 lines, 72 branches and 22 report lines
reachable only by reading its source text. That is not a theoretical gap
-- the absent-table report line that vanished on --resume lived at one of
those call sites, shipped once, and defeated three source-text guards
before an AST invariant caught it. Every other phase already had the
driver that would have caught it on the first attempt: `run_checks` has
`test_preflight.FakeDb`, `run_roles` has `test_roles_driver.FakeDb`,
`run_p7` is driven from `test_state.py`.

So this file drives the real `run_etl` over a small synthetic manifest and
asserts on the two things an operator and a resume actually depend on: the
SQL that reaches the database, and the report body. The manifest is
synthetic on purpose -- 580 real tables would make every assertion a
needle in a haystack, and the branches under test are per-class, not
per-table.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import os
import re
import shutil
import tempfile
import unittest
from unittest import mock

from carlos_ctl import o19etl, o19map_schema

SRC, DST, ARCH = "o19_import", "carlos", "o19_archive"

#: A manifest small enough to assert against, carrying one table of every
#: class plus the shapes with their own branch: a chunked copy, a
#: seed-replacing copy, a merge with a surrogate pk, and a copy with a
#: dropped column. Real table names are used where the ETL keys off them
#: (`log` is the tolerated table, `Facility`/`clinic` gate the pre-checks).
MANIFEST = {
    "AppDefinition": {"class": "copy", "cols": ["id", "name"]},
    "Contact": {
        "class": "copy",
        "cols": ["id", "provider_no"],
        "dropped": {"programNo": {
            "nondefault": "s.`programNo` IS NOT NULL AND s.`programNo` <> 0"}},
    },
    "Facility": {"class": "copy", "cols": ["id", "disabled"]},
    "clinic": {"class": "copy", "cols": ["clinic_no", "clinic_name"]},
    "log": {"class": "copy", "cols": ["id", "content"],
            "replace_seed": True},
    "measurements": {"class": "copy", "cols": ["id", "demographic_no"],
                     "chunk_by": "id"},
    "HL7Map": {"class": "merge", "merge_keys": ["site"],
               "surrogate_pk": "id", "cols": ["id", "site"]},
    "Eyeform": {"class": "archive"},
    "sharing_actor": {"class": "drop"},
    "icd9": {"class": "reference"},
}

#: information_schema for both schemas. The staged dump carries one column
#: the manifest does not know (`Contact.vendorExtra`) so the vendor-fork
#: shadow path is exercised, and the dropped column it does know.
SRC_COLUMNS = {
    "AppDefinition": ["id", "name"],
    "Contact": ["id", "provider_no", "programNo", "vendorExtra"],
    "Facility": ["id", "disabled"],
    "clinic": ["clinic_no", "clinic_name"],
    "log": ["id", "content"],
    "measurements": ["id", "demographic_no"],
    "HL7Map": ["id", "site"],
    "Eyeform": ["id", "notes"],
    "sharing_actor": ["id"],
    "icd9": ["code"],
    "provider": ["provider_no"],
    "security": ["security_no", "user_name"],
    "secUserRole": ["id"],
    "secRole": ["role_name"],
    "secObjPrivilege": ["roleUserGroup", "objectName", "privilege",
                        "priority"],
}
DST_COLUMNS = {t: [c for c in cols if c not in ("programNo", "vendorExtra")]
               for t, cols in SRC_COLUMNS.items()
               if t not in ("Eyeform", "sharing_actor")}


#: Row counts every run needs: the pre-checks refuse a dump with no
#: enabled Facility row and no clinic row, both being things CARLOS
#: dereferences on every login and every letterhead.
DEFAULT_COUNTS = {(SRC, "Facility"): 1, (SRC, "clinic"): 1}

#: Substring-matched scalar answers. The break-glass probe is the rewind
#: witness: on a resume `run_etl` asks whether the target still holds the
#: administrator this import created, because a restored snapshot does
#: not cover the workspace and would otherwise leave a ledger describing
#: writes the database no longer has. 1 means "not rewound", which is
#: what an ordinary resume finds; a test that wants the refusal sets 0.
ADMIN_PROBE = "`carlos`.provider WHERE provider_no = "
DEFAULT_SCALARS = {ADMIN_PROBE: 1}


def _col(name):
    """One information_schema row's worth of column metadata: nullable
    varchar with no default, which is the shape that keeps every
    sanitizer and pre-check a no-op so the assertions stay about the
    branch under test."""
    return {"type": "varchar", "column_type": "varchar(255)",
            "nullable": True, "char_len": 255, "octet_len": 1020,
            "has_default": False, "default": None, "auto_increment": False}


class FakeDb(object):
    """Serves the reads `run_etl` issues and records every write.

    Scalar reads answer 0 by default -- the pre-checks are all "how many
    rows are wrong", so 0 means "nothing to refuse" and a test that wants
    a refusal says so explicitly through `counts`.
    """

    IS_COLUMNS = "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE"
    IS_TABLES = "SELECT TABLE_NAME FROM information_schema.TABLES"
    #: the one DDL the ETL issues against the target, modelled so a
    #: resumed run introspects the column it added on the first pass --
    #: MySQL 8 has no ADD COLUMN IF NOT EXISTS, so that skip is the
    #: whole idempotency story and a fake that forgot it would pass
    ADD_COLUMN = re.compile(
        r"^ALTER TABLE `([^`]+)`\.`([^`]+)` ADD COLUMN `([^`]+)`")

    def __init__(self, **over):
        self.writes = []
        self.reads = []
        # the column lists are copied, not shared with the module
        # constants: the fake applies ADD COLUMN below, and a mutation
        # that leaked into SRC_COLUMNS/DST_COLUMNS would follow every
        # later test in the process
        self.columns = {
            SRC: {t: list(c) for t, c in
                  over.pop("src_columns", SRC_COLUMNS).items()},
            DST: {t: list(c) for t, c in
                  over.pop("dst_columns", DST_COLUMNS).items()}}
        #: {(schema, table): rows} for COUNT(*), everything else 0.
        #: Facility and clinic default to one row because the pre-checks
        #: refuse a dump with neither -- a synthetic dump is still a
        #: clinic dump.
        self.counts = dict(DEFAULT_COUNTS)
        self.counts.update(over.pop("counts", {}))
        #: {table: (min, max)} for the chunked copy's bounds probe
        self.bounds = over.pop("bounds", {"measurements": (1, 3)})
        self.scalars = dict(DEFAULT_SCALARS)
        self.scalars.update(over.pop("scalars", {}))
        self.fail_on = over.pop("fail_on", None)
        if over:
            raise TypeError("unexpected FakeDb kwargs: {0}".format(
                sorted(over)))

    # -- the ETL executor: writes, and the counts run under its prelude
    def query(self, sql, db=None):
        if self.fail_on and self.fail_on in sql:
            self.fail_on = None
            raise o19etl.QueryError("planted failure", "boom")
        if sql.lstrip().upper().startswith("SELECT"):
            self.reads.append(sql)
            return self._scalar(sql)
        self.writes.append(sql)
        added = self.ADD_COLUMN.match(sql)
        if added:
            schema, table, col = added.groups()
            cols = self.columns.setdefault(schema, {}).setdefault(table, [])
            if col in cols:
                raise o19etl.QueryError(
                    "SQL failed ({0})".format(sql),
                    "ERROR 1060 (42S21): Duplicate column name '{0}'"
                    .format(col))
            cols.append(col)
        return []

    # -- the plain client: reads, plus the archive schema CREATE
    def plain(self, sql, db=None):
        if sql.startswith(self.IS_COLUMNS):
            return self._information_schema(sql)
        if sql.startswith(self.IS_TABLES):
            # row_parity asks which tables the dump has; without this the
            # loop matches nothing and every parity assertion passes
            # vacuously
            schema = re.search(r"TABLE_SCHEMA = '([^']+)'", sql).group(1)
            return [[t] for t in sorted(self.columns.get(schema, {}))]
        if sql == "SELECT @@SESSION.sql_mode":
            return [["STRICT_TRANS_TABLES"]]
        if sql.lstrip().upper().startswith("SELECT"):
            self.reads.append(sql)
            return self._scalar(sql)
        self.writes.append(sql)
        return []

    # -- helpers -----------------------------------------------------------
    def _information_schema(self, sql):
        schema = re.search(r"TABLE_SCHEMA = '([^']+)'", sql).group(1)
        rows = []
        for table, cols in sorted(self.columns.get(schema, {}).items()):
            for col in cols:
                rows.append([table, col, "varchar", "varchar(255)", "YES",
                             255, "\\0NONE", "", 1020])
        return rows

    def _rows(self, sql):
        """Row count for a `FROM \\`schema\\`.\\`table\\`` reference."""
        m = re.search(r"FROM `([^`]+)`\.`?([A-Za-z0-9_$]+)`?", sql)
        if not m:
            return 0
        return self.counts.get((m.group(1), m.group(2)), 0)

    def _scalar(self, sql):
        for needle, value in self.scalars.items():
            if needle in sql:
                return [[str(value)]]
        if "IFNULL(MIN(" in sql:
            m = re.search(r"FROM `[^`]+`\.`([^`]+)`", sql)
            lo, hi = self.bounds.get(m.group(1) if m else "", (0, 0))
            return [[str(lo), str(hi)]]
        if "IFNULL(MAX(" in sql:
            return [["0"]]
        if sql.startswith("SELECT COUNT(*)"):
            return [[str(self._rows(sql))]]
        if sql.startswith("SELECT role_name") or \
                sql.startswith("SELECT roleUserGroup"):
            return []
        return [["0"]]


def make_ctx(state_dir, db, **over):
    """The ctx `run_etl` reads, with the report captured rather than
    appended to a file so a test can assert on what the operator sees."""
    lines = []
    ctx = {"query": db.plain, "query_etl": db.query, "src_schema": SRC,
           "target_db": DST, "archive_schema": ARCH, "state_dir": state_dir,
           "report": lines.append, "accepted": set(), "admin_user": "bgadmin",
           "dump_sha256": "a" * 64, "role_templates": None}
    ctx.update(over)
    return ctx, lines


def fake_password():
    """The injected crypto: `run_etl` takes it as a callable precisely so
    a test never pays for bcrypt."""
    return ("pw", "{bcrypt}$2a$10$" + "x" * 53, "1234")


class EtlDriverBase(unittest.TestCase):
    """Patches the manifest down to MANIFEST and drives the real run_etl.

    `REQUIRED_TABLES` is narrowed with it: the shipped tuple names tables
    a synthetic dump has no reason to carry, and the pre-check that reads
    it would refuse before any branch under test could run.
    """

    def setUp(self):
        self.state_dir = tempfile.mkdtemp(prefix="o19etl-driver-")
        self.addCleanup(shutil.rmtree, self.state_dir, ignore_errors=True)
        patches = [
            mock.patch.object(o19map_schema, "TABLES", MANIFEST),
            mock.patch.object(o19map_schema, "SEED_ROW_COUNTS", {}),
            mock.patch.object(o19map_schema, "PRISTINE_TOLERATED_TABLES",
                              ["log"]),
            mock.patch.object(o19map_schema, "CREDENTIAL_TABLES", []),
            mock.patch.object(o19etl, "ROLES_STEP_TABLES",
                              ("Facility", "clinic", "provider")),
            # the roles post-step has its own driver (test_roles_driver);
            # here it would only add a few hundred statements of noise
            mock.patch("carlos_ctl.o19roles.run_roles", return_value=None),
        ]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)

    def run_etl(self, db=None, ctx_over=None, **db_kwargs):
        db = db or FakeDb(**db_kwargs)
        ctx, lines = make_ctx(self.state_dir, db, **(ctx_over or {}))
        counts = o19etl.run_etl(ctx, fake_password)
        return db, lines, counts

    def report_text(self, lines):
        return "\n".join(lines)

    def writes_matching(self, db, pattern):
        rx = re.compile(pattern)
        return [w for w in db.writes if rx.search(w)]


class TestTheCopyPath(EtlDriverBase):
    """What reaches the database for each manifest class."""

    def test_a_copy_table_is_inserted_from_staging(self):
        db, _lines, counts = self.run_etl()
        inserts = self.writes_matching(
            db, r"^INSERT INTO `carlos`\.`AppDefinition`")
        self.assertEqual(len(inserts), 1, db.writes)
        self.assertIn("FROM `o19_import`.`AppDefinition` s", inserts[0])
        self.assertEqual(counts["copy"], 6)

    def test_a_reference_table_keeps_carlos_rows_and_archives_the_clinics(
            self):
        # CARLOS's own Flyway seed wins in the live table, but the
        # clinic's rows are not thrown away -- they are the only record
        # of a locally curated code
        db, lines, counts = self.run_etl(counts={(SRC, "icd9"): 12})
        self.assertEqual(
            self.writes_matching(db, r"INTO `carlos`\.`icd9`"), [])
        self.assertIn("CREATE TABLE `o19_archive`.`icd9__new` LIKE "
                      "`o19_import`.`icd9`", db.writes)
        self.assertEqual(counts["reference"], 1)
        self.assertIn("icd9: 12 row(s) kept at o19_archive.icd9",
                      self.report_text(lines))

    def test_an_archive_table_lands_in_both_the_archive_and_the_live_schema(
            self):
        # requirement B: o19_archive is the verification copy, and the
        # live twin is what the nightly backup dumps and --cleanup keeps
        db, _lines, counts = self.run_etl()
        self.assertIn("CREATE TABLE `o19_archive`.`Eyeform__new` LIKE "
                      "`o19_import`.`Eyeform`", db.writes)
        self.assertIn("INSERT INTO `o19_archive`.`Eyeform__new` SELECT * "
                      "FROM `o19_import`.`Eyeform`", db.writes)
        self.assertIn("CREATE TABLE `carlos`.`import_archived_Eyeform__new` "
                      "LIKE `o19_import`.`Eyeform`", db.writes)
        self.assertIn("INSERT INTO `carlos`.`import_archived_Eyeform__new` "
                      "SELECT * FROM `o19_import`.`Eyeform`", db.writes)
        self.assertEqual(counts["archive"], 1)

    def test_the_archive_copy_is_written_before_the_live_twin(self):
        # each half is its own build-aside swap, but ordering still
        # matters: the live twin is built while a copy already exists
        # outside staging
        db, _lines, _counts = self.run_etl()
        arch = db.writes.index("INSERT INTO `o19_archive`.`Eyeform__new` "
                               "SELECT * FROM `o19_import`.`Eyeform`")
        live = db.writes.index("INSERT INTO `carlos`."
                               "`import_archived_Eyeform__new` SELECT * "
                               "FROM `o19_import`.`Eyeform`")
        self.assertLess(arch, live)

    def test_no_rebuild_ever_drops_a_live_preserved_table(self):
        # a preserved table is the clinic's only copy of what CARLOS has
        # no home for; every DROP either rebuild issues must name a
        # scratch, in the archive schema and in the live one alike
        db, _lines, _counts = self.run_etl()
        for sql in self.writes_matching(
                db, r"^DROP TABLE .*(`o19_archive`|import_archived_)"):
            self.assertRegex(sql, r"__(new|old)`$",
                             "a DROP named a live preserved table: " + sql)

    def test_a_merge_table_is_merged_and_gets_an_id_map(self):
        db, _lines, counts = self.run_etl()
        self.assertTrue(self.writes_matching(
            db, r"INSERT INTO `carlos`\.`HL7Map`"), db.writes)
        self.assertTrue(self.writes_matching(
            db, r"`o19_archive`\.`HL7Map__idmap`"), db.writes)
        self.assertEqual(counts["merge"], 1)

    def test_a_replace_seed_table_is_emptied_before_the_copy(self):
        # `log` carries the deploy's own audit rows and the copy is
        # id-intact, so the DELETE must precede the INSERT
        db, _lines, _counts = self.run_etl()
        delete = db.writes.index("DELETE FROM `carlos`.`log`")
        insert = next(i for i, w in enumerate(db.writes)
                      if w.startswith("INSERT INTO `carlos`.`log`"))
        self.assertLess(delete, insert)

    def test_a_chunked_table_is_copied_one_window_at_a_time(self):
        db, _lines, _counts = self.run_etl(bounds={"measurements": (1, 3)})
        windows = self.writes_matching(
            db, r"^INSERT INTO `carlos`\.`measurements`")
        self.assertTrue(windows)
        for sql in windows:
            self.assertIn("WHERE s.`id` >", sql)


class TestTheDropClass(EtlDriverBase):
    """Removed-module tables: no CARLOS home, and no deletion either.

    These rows used to be counted and then destroyed with the staging
    schema at --cleanup, with a passed verification. They now take the
    preserved path like any other table CARLOS cannot hold."""

    def test_drop_class_rows_are_preserved_not_migrated(self):
        db, lines, counts = self.run_etl(
            counts={(SRC, "sharing_actor"): 7})
        # still not migrated: the live CARLOS table is untouched
        self.assertEqual(
            self.writes_matching(db, r"INTO `carlos`\.`sharing_actor`"), [])
        self.assertIn("INSERT INTO `o19_archive`.`sharing_actor__new` "
                      "SELECT * FROM `o19_import`.`sharing_actor`",
                      db.writes)
        self.assertIn("INSERT INTO `carlos`."
                      "`import_archived_sharing_actor__new` SELECT * FROM "
                      "`o19_import`.`sharing_actor`", db.writes)
        self.assertEqual(counts["drop"], 1)
        text = self.report_text(lines)
        self.assertIn("sharing_actor: 7 row(s) not migrated", text)
        self.assertIn("preserved at o19_archive.sharing_actor and "
                      "carlos.import_archived_sharing_actor", text)

    def test_an_empty_drop_table_is_neither_copied_nor_reported(self):
        db, lines, _counts = self.run_etl(counts={})
        self.assertNotIn("sharing_actor", self.report_text(lines))
        self.assertEqual(
            self.writes_matching(db, r"sharing_actor"), [], db.writes)


class TestAbsentTables(EtlDriverBase):
    """The regression that shipped once and defeated three guards: the
    report line for a manifest table this dump does not carry must survive
    --resume, because P0 tolerated the target's rows only on the strength
    of the copy clearing them."""

    def dump_without_log(self):
        cols = {t: c for t, c in SRC_COLUMNS.items() if t != "log"}
        return FakeDb(src_columns=cols)

    def test_a_tolerated_absent_table_is_cleared_and_reported(self):
        db, lines, _counts = self.run_etl(db=self.dump_without_log())
        self.assertIn("DELETE FROM `carlos`.`log`", db.writes)
        self.assertIn("log (absent: the target's own rows were cleared)",
                      self.report_text(lines))

    def test_the_absent_line_survives_a_resume_that_does_not_clear(self):
        first, lines_one, _ = self.run_etl(db=self.dump_without_log())
        self.assertIn("DELETE FROM `carlos`.`log`", first.writes)
        second, lines_two, _ = self.run_etl(db=self.dump_without_log())
        # the ledger gates the DELETE and nothing else
        self.assertNotIn("DELETE FROM `carlos`.`log`", second.writes)
        self.assertIn("log (absent: the target's own rows were cleared)",
                      self.report_text(lines_two))
        self.assertEqual(
            [ln for ln in lines_one if "absent" in ln],
            [ln for ln in lines_two if "absent" in ln],
            "the absent-table block changed on resume")

    def test_a_non_copy_absent_table_contributes_no_report_line(self):
        # absent_table_plan returns line=None for these; an unguarded
        # append would put None into the list and the report build would
        # raise TypeError instead of importing
        cols = {t: c for t, c in SRC_COLUMNS.items() if t != "Eyeform"}
        _db, lines, _counts = self.run_etl(db=FakeDb(src_columns=cols))
        self.assertNotIn("Eyeform", self.report_text(lines))


class TestShadowCapture(EtlDriverBase):
    """Columns CARLOS has no home for are captured, not dropped."""

    def test_a_dropped_column_is_shadow_captured(self):
        db, _lines, _counts = self.run_etl()
        shadow = self.writes_matching(
            db, r"`o19_archive`\.`Contact__dropped__new`")
        self.assertTrue(shadow, db.writes)
        self.assertTrue(any("s.`programNo`" in x for x in shadow), shadow)

    def test_a_vendor_fork_column_is_shadow_captured_and_reported(self):
        db, lines, counts = self.run_etl()
        self.assertTrue(self.writes_matching(
            db, r"`o19_archive`\.`Contact__unknown_cols`"), db.writes)
        self.assertIn("vendorExtra", self.report_text(lines))
        self.assertEqual(counts["unknown_column_shadows"], 1)

    def test_shadow_capture_does_not_repeat_on_a_resume(self):
        self.run_etl()
        second, _lines, _counts = self.run_etl()
        self.assertEqual(self.writes_matching(
            second, r"`o19_archive`\.`Contact__dropped`"), [])


class TestArchivedColumns(EtlDriverBase):
    """Requirement B, column half: every source column CARLOS has no home
    for joins the live table as `import_archived_<col>`.

    Two populations reach it -- the manifest's curated `dropped` columns
    (`Contact.programNo`) and a vendor-fork column the manifest has never
    seen (`Contact.vendorExtra`).
    """

    def altered(self, db):
        return self.writes_matching(db, r"^ALTER TABLE .*ADD COLUMN")

    def test_both_populations_reach_the_live_table(self):
        db, _lines, _counts = self.run_etl()
        added = " | ".join(self.altered(db))
        self.assertIn("`carlos`.`Contact` ADD COLUMN "
                      "`import_archived_programNo`", added)
        self.assertIn("`carlos`.`Contact` ADD COLUMN "
                      "`import_archived_vendorExtra`", added)

    def test_the_column_keeps_the_source_type_and_is_nullable(self):
        # NOT NULL would abort the run in missing_required_columns, and a
        # widened type would stop the copy being verbatim
        db, _lines, _counts = self.run_etl()
        for sql in self.altered(db):
            self.assertIn(" varchar(255) NULL COMMENT ", sql)
            self.assertIn("preserved by import-o19", sql)

    def test_the_alter_precedes_the_copy_it_feeds(self):
        db, _lines, _counts = self.run_etl()
        alter = max(db.writes.index(sql) for sql in self.altered(db)
                    if "`Contact`" in sql)
        insert = next(i for i, w in enumerate(db.writes)
                      if w.startswith("INSERT INTO `carlos`.`Contact`"))
        self.assertLess(alter, insert)

    def test_the_copy_carries_the_source_value_verbatim(self):
        db, _lines, _counts = self.run_etl()
        insert = next(w for w in db.writes
                      if w.startswith("INSERT INTO `carlos`.`Contact`"))
        self.assertIn("`import_archived_programNo`", insert)
        self.assertIn("`import_archived_vendorExtra`", insert)
        self.assertIn("s.`programNo`, s.`vendorExtra`", insert)
        # no sanitizer wrapped around them: an archived value that
        # differs from the source is not an archive
        self.assertNotIn("NULLIF(s.`programNo`", insert)
        self.assertNotIn("CASE WHEN s.`programNo`", insert)

    def test_a_resume_does_not_re_alter(self):
        # MySQL 8 has no ADD COLUMN IF NOT EXISTS, so the guard is the
        # introspected schema; the fake raises 1060 if it is missed
        first, _lines, _counts = self.run_etl()
        self.assertTrue(self.altered(first))
        # the SAME fake, so the column it added is there to be
        # introspected the second time; only the recorded statements are
        # cleared, not the schema
        del first.writes[:]
        second, _lines, _counts = self.run_etl(db=first)
        self.assertEqual(self.altered(second), [])

    def test_the_report_names_every_preserved_column(self):
        _db, lines, _counts = self.run_etl()
        text = self.report_text(lines)
        self.assertIn("columns CARLOS has no home for, preserved on the "
                      "live table", text)
        self.assertIn("Contact: programNo -> import_archived_programNo, "
                      "vendorExtra -> import_archived_vendorExtra", text)

    def test_a_merge_back_fills_the_rows_it_kept_carlos_copies_of(self):
        # a merge keeps CARLOS's row on a shared key, so a clinic row
        # with a twin never passes through the insert; without the
        # back-fill its unmapped columns are the one population
        # requirement B still orphans
        cols = dict(SRC_COLUMNS, HL7Map=["id", "site", "vendorNote"])
        db, _lines, _counts = self.run_etl(db=FakeDb(src_columns=cols))
        update = self.writes_matching(
            db, r"^UPDATE `carlos`\.`HL7Map` d JOIN")
        self.assertEqual(len(update), 1, db.writes)
        self.assertIn("SET d.`import_archived_vendorNote` = "
                      "s.`vendorNote`", update[0])
        insert = next(i for i, w in enumerate(db.writes)
                      if w.startswith("INSERT INTO `carlos`.`HL7Map`"))
        self.assertLess(insert, db.writes.index(update[0]))

    def test_the_archive_shadows_are_still_written(self):
        # o19_archive stays the verification copy: preserving a column on
        # the live table must not quietly retire the shadow capture that
        # the unknown-as-archive sign-off promises
        db, lines, _counts = self.run_etl()
        self.assertTrue(self.writes_matching(
            db, r"`o19_archive`\.`Contact__dropped__new`"), db.writes)
        self.assertTrue(self.writes_matching(
            db, r"`o19_archive`\.`Contact__unknown_cols__new`"), db.writes)
        self.assertIn("unmapped column(s) vendorExtra shadow-captured",
                      self.report_text(lines))


class TestUnknownTables(EtlDriverBase):
    """A table the manifest never classified -- a clinic's own
    customisation -- is archived whole rather than discarded."""

    def with_custom_table(self, rows):
        cols = dict(SRC_COLUMNS, clinic_custom_notes=["id", "body"])
        return FakeDb(src_columns=cols,
                      counts={(SRC, "clinic_custom_notes"): rows})

    def test_a_populated_unknown_table_is_preserved_in_both_schemas(self):
        db, lines, counts = self.run_etl(db=self.with_custom_table(4))
        self.assertTrue(self.writes_matching(
            db, r"`o19_archive`\.`clinic_custom_notes`"), db.writes)
        self.assertTrue(self.writes_matching(
            db, r"`carlos`\.`import_archived_clinic_custom_notes"),
            db.writes)
        self.assertEqual(counts["unknown_archived"], 1)
        self.assertIn("clinic_custom_notes: 4 row(s) preserved at "
                      "o19_archive.clinic_custom_notes and "
                      "carlos.import_archived_clinic_custom_notes",
                      self.report_text(lines))

    def test_an_empty_unknown_table_is_reported_but_not_materialised(self):
        db, lines, counts = self.run_etl(db=self.with_custom_table(0))
        self.assertEqual(self.writes_matching(
            db, r"`o19_archive`\.`clinic_custom_notes`"), [])
        self.assertEqual(counts["unknown_archived"], 0)
        self.assertIn("clinic_custom_notes: empty, not archived",
                      self.report_text(lines))


class TestTheLedger(EtlDriverBase):
    """What a resume repeats, and what it must not."""

    def test_a_completed_table_is_not_copied_twice(self):
        first, _lines, _counts = self.run_etl()
        second, _lines, _counts = self.run_etl()
        self.assertTrue(self.writes_matching(
            first, r"^INSERT INTO `carlos`\.`AppDefinition`"))
        self.assertEqual(self.writes_matching(
            second, r"^INSERT INTO `carlos`\.`AppDefinition`"), [])

    def test_the_seed_block_runs_once(self):
        first, _lines, _counts = self.run_etl()
        second, _lines, _counts = self.run_etl()
        self.assertTrue(self.writes_matching(first, r"^INSERT INTO "
                                             r"`carlos`\.provider"))
        self.assertEqual(self.writes_matching(
            second, r"^INSERT INTO `carlos`\.provider"), [])

    def test_the_break_glass_credentials_are_written_once_at_0600(self):
        self.run_etl()
        path = os.path.join(self.state_dir, "admin-credentials.txt")
        self.assertTrue(os.path.exists(path))
        self.assertEqual(os.stat(path).st_mode & 0o777, 0o600)
        with open(path, encoding="utf-8") as fh:
            self.assertIn("bgadmin", fh.read())

    def test_a_resume_under_a_different_admin_user_is_refused(self):
        self.run_etl()
        with self.assertRaises(SystemExit):
            self.run_etl(ctx_over={"admin_user": "someoneelse"})

    def test_a_resume_onto_a_rewound_target_is_refused(self):
        # an operator who follows the rollback advice restores a snapshot
        # that covers the CARLOS schema but NOT the workspace, so the
        # ledger still claims work the database no longer holds. The
        # break-glass admin is the witness: this run created it, so its
        # absence means the target is not the one the ledger describes.
        self.run_etl()
        with self.assertRaises(SystemExit):
            self.run_etl(scalars={ADMIN_PROBE: 0})


class TestTheEtlSummary(EtlDriverBase):
    """The closing report line, which is the only per-class tally an
    operator sees today."""

    def test_the_summary_counts_every_class(self):
        _db, lines, counts = self.run_etl()
        text = self.report_text(lines)
        self.assertIn("ETL complete: 6 copied, 1 merged, 1 archived, "
                      "1 reference (CARLOS wins, clinic rows kept in "
                      "o19_archive), 1 removed-module table(s) preserved, "
                      "0 unknown table(s) preserved", text)
        self.assertIn("Preserved tables live at o19_archive.<table> and "
                      "carlos.import_archived_<table>", text)
        self.assertEqual(counts["copy"], 6)

    def test_every_password_is_force_reset_after_the_copy(self):
        db, lines, _counts = self.run_etl()
        self.assertTrue(self.writes_matching(db, r"forcePasswordReset"),
                        db.writes)
        self.assertIn("forcePasswordReset set for every imported user",
                      self.report_text(lines))


class TestThePreChecks(EtlDriverBase):
    """Refusals fire before the first write, so a refused run leaves the
    target untouched."""

    def assert_refused_without_writing(self, db):
        ctx, _lines = make_ctx(self.state_dir, db)
        with self.assertRaises(SystemExit):
            o19etl.run_etl(ctx, fake_password)
        self.assertEqual(
            [w for w in db.writes if w.startswith(("INSERT", "DELETE",
                                                   "UPDATE"))], [],
            "a pre-check refusal wrote to the database")

    def test_a_fork_table_whose_preserved_name_would_not_fit_is_refused(
            self):
        # 47 characters plus the 16-character prefix and the rebuild
        # suffix overflows MySQL's identifier limit; failing on the
        # CREATE halfway through the loop would leave a half-preserved
        # import
        long_name = "vendor_" + "x" * 40
        cols = dict(SRC_COLUMNS)
        cols[long_name] = ["id"]
        self.assert_refused_without_writing(FakeDb(src_columns=cols))

    def test_a_dump_without_facility_is_refused(self):
        cols = {t: c for t, c in SRC_COLUMNS.items() if t != "Facility"}
        self.assert_refused_without_writing(FakeDb(src_columns=cols))

    def test_a_dump_without_clinic_is_refused(self):
        cols = {t: c for t, c in SRC_COLUMNS.items() if t != "clinic"}
        self.assert_refused_without_writing(FakeDb(src_columns=cols))

    def test_a_server_with_no_backslash_escapes_is_refused(self):
        db = FakeDb()
        db.plain = lambda sql, _db=None: (
            [["NO_BACKSLASH_ESCAPES"]]
            if sql == "SELECT @@SESSION.sql_mode"
            else FakeDb.plain(db, sql))
        self.assert_refused_without_writing(db)

    def test_an_identifier_outside_the_accepted_class_is_refused(self):
        cols = dict(SRC_COLUMNS)
        cols["weird-table"] = ["id"]
        self.assert_refused_without_writing(FakeDb(src_columns=cols))

    def test_a_manifest_column_the_target_lacks_is_refused(self):
        dst = {t: [c for c in cols if c != "name"]
               for t, cols in DST_COLUMNS.items()}
        self.assert_refused_without_writing(FakeDb(dst_columns=dst))


class TestRowParityOnALowerPatchLevel(EtlDriverBase):
    """Parity must compare the shape the copy actually wrote.

    The copy reduces every entry through `effective_entry` to the columns
    the dump carries. Parity used to iterate the raw manifest, so on a
    dump missing a merge key `merge_missing_count_sql` emitted
    `s.<column>` for a column that does not exist -- and it failed AFTER
    the copy had completed and been declared unoverridable, which is the
    worst moment to find out.
    """

    def parity(self, src_columns):
        db = FakeDb(src_columns=src_columns)
        o19etl.row_parity(db.plain, SRC, DST,
                          dst_info=dict.fromkeys(DST_COLUMNS, {}),
                          archive_schema=ARCH)
        return db.reads

    def test_parity_never_names_a_column_the_dump_does_not_carry(self):
        # HL7Map merges on `site`; a lower patch level without it must not
        # produce SQL referencing it
        reduced = dict(SRC_COLUMNS, HL7Map=["id"])
        db = FakeDb(src_columns=reduced)
        _ok, bad = o19etl.row_parity(
            db.plain, SRC, DST, dst_info=dict.fromkeys(DST_COLUMNS, {}),
            archive_schema=ARCH)
        # asserting "no HL7Map read mentions `site`" would be vacuous:
        # parity reports the table and `continue`s, so it emits NO SQL for
        # it at all. Assert that directly -- it is the actual behaviour,
        # and it fails if the guard is removed and broken SQL reappears.
        self.assertEqual([q for q in db.reads if "HL7Map" in q], [],
                         "parity generated SQL for a table it cannot check")
        self.assertTrue([b for b in bad if "HL7Map" in b and "site" in b],
                        "parity said nothing about the unusable key")

    def test_the_etl_refuses_a_dump_missing_a_merge_key(self):
        # the real remedy: refuse before the copy rather than fail parity
        # after it, when the target already holds the clinic's data and
        # no flag overrides parity
        db = FakeDb(src_columns=dict(SRC_COLUMNS, HL7Map=["id"]))
        ctx, _lines = make_ctx(self.state_dir, db)
        with self.assertRaises(SystemExit):
            o19etl.run_etl(ctx, fake_password)
        self.assertEqual(
            [w for w in db.writes
             if w.startswith(("INSERT", "DELETE", "UPDATE"))], [],
            "the refusal came after a write")

    def test_parity_still_checks_the_columns_the_dump_does_carry(self):
        # the reduction must not become "skip the table": a full dump
        # still gets its merge-twin check
        reads = self.parity(dict(SRC_COLUMNS))
        self.assertTrue([q for q in reads if "HL7Map" in q],
                        "the merge table was not checked at all")


if __name__ == "__main__":
    unittest.main()
