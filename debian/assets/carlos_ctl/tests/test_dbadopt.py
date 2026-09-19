# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Adopting a pre-Flyway (OSCAR 19 / OpenO) datadir.

Two kinds of test live here.

The first pins the PARSER and the SQL it generates. `db-baseline` reconciles a
live clinical schema against the genesis DDL, so a parser that quietly drops a
table, mistakes a `KEY` line for a column, or loses the trailing comma from a
definition writes wrong DDL onto a database holding patient records.

The second is a CONTRACT over the real migration files: no forward migration
may seed a non-temporary table with an unguarded `INSERT INTO ... VALUES`.
`V1.0.5` does, which is the bug that stops `db-migrate` dead on any adopted
datadir that already holds ICD-10 reference rows -- and because `V1.0.5` ships
in published release tags its checksum cannot be changed, so it is carried here
as a named exception rather than fixed in place. The contract exists to stop a
SECOND one being written."""

import contextlib
import io
import os
import re
import unittest
from unittest import mock

from carlos_ctl import dbadopt


# tests/ -> carlos_ctl/ -> assets/ -> debian/ -> the repository root.
REPO_MIGRATIONS = os.path.abspath(os.path.join(
    os.path.dirname(__file__), *([os.pardir] * 4 + ["database", "mysql", "migration"])))

# `V1.0.5` seeds `icd10` with two statements and only the first says INSERT
# IGNORE. It is present unchanged in every published release tag: editing it
# would break Flyway's checksum for every existing install, so the collision is
# cleared at adopt time instead (dbadopt.seed_collision_script). Nothing else
# may join this list -- write INSERT IGNORE.
KNOWN_UNGUARDED_SEEDS = {
    "V1.0.5__restore_live_legacy_common_tables.sql": {"icd10"},
}

SAMPLE = """
DROP TABLE IF EXISTS `security`;
CREATE TABLE `security` (
  `security_no` int(6) NOT NULL AUTO_INCREMENT,
  `user_name` varchar(30) default NULL,
  `mfaSecret` varchar(255) DEFAULT NULL,
  `b_ExpireSet` enum('a','b,c') NOT NULL DEFAULT 'a',
  PRIMARY KEY  (`security_no`),
  KEY `user_name` (`user_name`),
  CONSTRAINT `fk_x` FOREIGN KEY (`user_name`) REFERENCES `provider` (`p`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""


class TestParseCreateTables(unittest.TestCase):

    def setUp(self):
        self.tables = dbadopt.parse_create_tables(SAMPLE)

    def test_columns_are_kept_and_constraints_are_not(self):
        columns = [name for name, _ in self.tables["security"].columns]
        self.assertEqual(
            columns, ["security_no", "user_name", "mfaSecret", "b_ExpireSet"])

    def test_definition_keeps_commas_inside_an_enum(self):
        definitions = dict(self.tables["security"].columns)
        # A naive split on "," turns this into `enum('a','b` -- DDL that either
        # fails or, worse, silently narrows a column on a clinical table.
        self.assertEqual(definitions["b_ExpireSet"],
                         "enum('a','b,c') NOT NULL DEFAULT 'a'")

    def test_trailing_separator_comma_is_stripped(self):
        definitions = dict(self.tables["security"].columns)
        self.assertEqual(definitions["mfaSecret"], "varchar(255) DEFAULT NULL")


class TestReconciliationStatements(unittest.TestCase):

    def setUp(self):
        tables = dbadopt.parse_create_tables(SAMPLE)
        self.statements, self.skipped = dbadopt.reconciliation_statements(tables)

    def test_every_statement_is_idempotent(self):
        for statement in self.statements:
            if statement.upper().startswith("SET "):
                continue
            self.assertIn("IF NOT EXISTS", statement.upper(), statement[:80])

    def test_returns_only_reconciliation_statements(self):
        # Session pragmas belong to _run_script and the FOREIGN_KEY_CHECKS pair
        # to the caller, so this list is exactly what the dry-run message counts.
        for statement in self.statements:
            self.assertFalse(statement.upper().startswith("SET "), statement)

    def test_session_sql_mode_is_pinned_for_every_script(self):
        # Seven genesis columns default to '0000-00-00'. Under a sql_mode
        # carrying NO_ZERO_DATE or TRADITIONAL those ADD COLUMNs fail PART WAY
        # THROUGH, leaving a half-reconciled schema. The packaged drop-in sets
        # sql_mode="" but is only read at server start, and db-baseline has no
        # ordering against db-apply-settings -- so every script this module
        # runs pins the session itself, not just the reconciliation.
        self.assertEqual(dbadopt.SESSION_PRAGMAS,
                         ("SET NAMES utf8mb4;", "SET SESSION sql_mode='';"))
        sent = {}

        def fake_client(_dbops, _db, args, **kw):
            sent["script"] = kw.get("input", "")
            return mock.Mock(returncode=0, stdout="", stderr="")

        with mock.patch.object(dbadopt, "_client", fake_client):
            dbadopt._run_script(mock.Mock(), "carlos", "DELETE FROM `x`;", "t")
        self.assertTrue(sent["script"].startswith(
            "SET NAMES utf8mb4;\nSET SESSION sql_mode='';\n"), sent["script"])

    def test_auto_increment_columns_are_reported_not_added(self):
        # ADD COLUMN ... AUTO_INCREMENT requires the column to become a key in
        # the same statement; rebuilding a lost primary key is not this pass's
        # job.
        self.assertIn(("security", "security_no"), self.skipped)
        self.assertFalse([s for s in self.statements
                          if "security_no" in s and s.startswith("ALTER")])

    def test_missing_table_is_created_from_the_genesis_statement(self):
        create = [s for s in self.statements if s.upper().startswith("CREATE TABLE")]
        self.assertEqual(len(create), 1)
        self.assertIn("CREATE TABLE IF NOT EXISTS `security`", create[0])
        self.assertIn("PRIMARY KEY", create[0])


class TestPlainSeedInserts(unittest.TestCase):

    def test_ignores_guarded_and_column_listed_and_temporary_forms(self):
        sql = """
CREATE TEMPORARY TABLE `scratch` (`id` int);
INSERT IGNORE INTO `icd10` VALUES (1,'a'),(2,'b');
INSERT INTO `icd10` VALUES (14902,'Y19'),(14903,'Y20');
INSERT INTO `scratch` VALUES (7,'x');
INSERT INTO `other` (`a`, `b`) VALUES (9,'z');
INSERT INTO `third` SELECT * FROM `fourth`;
"""
        found = dbadopt.parse_plain_seed_inserts(sql)
        self.assertEqual(found, {"icd10": [(14902, "Y19"), (14903, "Y20")]})

    def test_seed_collision_script_backs_up_before_it_deletes(self):
        script = dbadopt.seed_collision_script("icd10", "id", [1, 2],
                                               ["id", "icd10", "description"])
        backup = dbadopt.BACKUP_PREFIX + "icd10"
        self.assertLess(script.index("INSERT IGNORE INTO `%s`" % backup),
                        script.index("DELETE t FROM `icd10`"))
        # Exactly the keys the migration is about to insert: a legacy row the
        # canonical seed does not cover keeps its place.
        self.assertIn("DELETE t FROM `icd10` t JOIN `carlos_adopt_backup_icd10` b", script)
        self.assertIn("WHERE t.`id` IN (1,2)", script)

    def test_seed_rows_are_deleted_only_when_the_backup_matches_every_column(self):
        script = dbadopt.seed_collision_script("icd10", "id", [1],
                                               ["id", "icd10", "description"])
        self.assertIn("BINARY b.`icd10` <=> BINARY t.`icd10`", script)
        self.assertIn("BINARY b.`description` <=> BINARY t.`description`", script)

    def test_backup_copy_names_its_columns(self):
        # `SELECT *` into a column-less INSERT binds by POSITION. A backup left
        # by an earlier run, over a table whose shape changed since, would take
        # the rows into the wrong columns -- and it is the only copy of what the
        # next statement deletes.
        script = dbadopt.seed_collision_script("icd10", "id", [1],
                                               ["id", "icd10", "description"])
        self.assertNotIn("SELECT *", script)
        self.assertIn("(`id`, `icd10`, `description`) "
                      "SELECT `id`, `icd10`, `description`", script)


class TestPendingOnlySeedClearing(unittest.TestCase):
    """A seed collision is only cleared for a migration that will actually run.

    Re-running db-baseline against an already-adopted database deleted the 1070
    canonical icd10 rows V1.0.5 had laid down, and the db-migrate that followed
    had nothing pending to restore them. db-validate still passed, because it
    compares the history against the WAR rather than the data -- the exact
    silent-failure shape this module exists to remove."""

    def _plan(self, applied, history_rows=("1.0.5",)):
        seen = []

        def fake_client(_dbops, _db, args, **kw):
            sql = ""
            for i, a in enumerate(args):
                if a == "-e":
                    sql = args[i + 1]
            seen.append(sql)
            if "information_schema.TABLES" in sql:
                return mock.Mock(returncode=0, stdout="1", stderr="")
            if "flyway_schema_history" in sql:
                return mock.Mock(returncode=0, stdout="\n".join(history_rows), stderr="")
            if "INDEX_NAME = 'PRIMARY'" in sql:
                return mock.Mock(returncode=0, stdout="id\tint\t1\t1", stderr="")
            if "ORDER BY ORDINAL_POSITION" in sql:
                return mock.Mock(returncode=0, stdout="id\nicd10\ndescription", stderr="")
            if sql.startswith("SELECT `id`, `icd10` FROM `icd10`"):
                return mock.Mock(returncode=0, stdout="14902\tY19", stderr="")
            if sql.startswith("SELECT COUNT(*) FROM `icd10`"):
                return mock.Mock(returncode=0, stdout="1", stderr="")
            return mock.Mock(returncode=0, stdout="0", stderr="")

        with mock.patch.object(dbadopt, "_client", fake_client):
            return dbadopt.plan_seed_collisions(
                mock.Mock(), "carlos", "on", applied, REPO_MIGRATIONS)

    @unittest.skipUnless(os.path.isdir(REPO_MIGRATIONS),
                         "runs from a source checkout, not the installed package")
    def test_clears_when_the_migration_is_still_pending(self):
        collisions = self._plan(applied=set())
        self.assertEqual([c[0] for c in collisions], ["icd10"])

    @unittest.skipUnless(os.path.isdir(REPO_MIGRATIONS),
                         "runs from a source checkout, not the installed package")
    def test_leaves_the_rows_alone_once_that_migration_has_applied(self):
        # V1.0.5 already ran: those rows ARE the canonical ones.
        self.assertEqual(self._plan(applied={"1.0.5"}), [])


class TestBillingDisambiguation(unittest.TestCase):

    def _script(self, table="billing_on_diskname", column="ohipfilename",
                order_by="createdatetime", suffix="YEAR(b.`createdatetime`)"):
        return dbadopt.billing_disambiguation_script(table, column, order_by, suffix)

    def test_suffix_ends_in_the_primary_key(self):
        # A `-YEAR` suffix alone collides again when a clinic submits the same
        # filename twice in one year, which is exactly the shape legacy
        # OHIP/MCEDT filenames take. The primary key makes it unique by
        # construction.
        self.assertIn("'-', COALESCE(YEAR(b.`createdatetime`), 'x'), '-', b.`id`",
                      self._script())

    def test_original_value_is_recorded_before_the_update(self):
        script = self._script()
        # The string in ohipfilename is the clinic's record of what it actually
        # sent to the Ministry; losing it is not an option.
        self.assertLess(script.index("INSERT IGNORE INTO `%sbilling_on_diskname`"
                                     % dbadopt.BACKUP_PREFIX),
                        script.index("UPDATE `billing_on_diskname`"))

    def test_only_later_rows_are_touched(self):
        # rn = 1 is the earliest submission; it keeps the filename verbatim.
        self.assertEqual(self._script().count("r.rn > 1"), 2)

    def test_result_is_kept_inside_the_column(self):
        self.assertIn("LEFT(b.`ohipfilename`, GREATEST(1, 50 - CHAR_LENGTH(",
                      self._script())

    def test_the_submission_timestamp_is_pinned(self):
        # Both billing tables declare `timestamp` ON UPDATE current_timestamp().
        # Without an explicit self-assignment, disambiguating rewrites the
        # clinic's record of WHEN it submitted -- and on billing_on_filename
        # that column is the ORDER BY this very ranking depends on, so a second
        # run would rank differently. There is no backup of it.
        for script in (self._script(),
                       self._script("billing_on_filename", "htmlfilename",
                                    "timestamp", None)):
            self.assertIn("b.`timestamp` = b.`timestamp`", script)

    def test_rewrite_requires_the_backup_to_match_the_current_filename(self):
        script = self._script()
        self.assertIn("JOIN `carlos_adopt_backup_billing_on_diskname` prior", script)
        self.assertIn("BINARY prior.`original_value` <=> BINARY b.`ohipfilename`", script)

    def test_table_without_a_year_source_still_disambiguates(self):
        script = self._script("billing_on_filename", "htmlfilename", "timestamp", None)
        self.assertIn("'-', 'dup', '-', b.`id`", script)


@unittest.skipUnless(os.path.isdir(REPO_MIGRATIONS),
                     "runs from a source checkout, not the installed package")
class TestPackagedMigrationContract(unittest.TestCase):
    """The genesis and the forward set as they actually ship."""

    def test_genesis_parses_into_the_whole_schema(self):
        tables = {}
        for path in dbadopt.genesis_files("on", REPO_MIGRATIONS):
            with open(path, encoding="utf-8") as handle:
                tables.update(dbadopt.parse_create_tables(handle.read()))
        # A parser regression that drops tables would silently reconcile less
        # of the schema, so hold it to the real order of magnitude rather than
        # an exact count that churns with every migration.
        self.assertGreater(len(tables), 350)
        self.assertGreater(sum(len(t.columns) for t in tables.values()), 9000)

    def test_the_columns_that_broke_a_real_import_are_reconciled(self):
        tables = {}
        for path in dbadopt.genesis_files("on", REPO_MIGRATIONS):
            with open(path, encoding="utf-8") as handle:
                tables.update(dbadopt.parse_create_tables(handle.read()))
        lowered = {name.lower(): [c.lower() for c, _ in table.columns]
                   for name, table in tables.items()}
        # Unknown column 's1_0.mfaSecret' -> login failed
        self.assertIn("mfasecret", lowered["security"])
        # 500 immediately after login
        self.assertIn("defaultbillinglocation", lowered["providerpreference"])

    def test_no_new_forward_migration_seeds_without_insert_ignore(self):
        for province in ("on", "bc"):
            for path in dbadopt.forward_migration_files(province, REPO_MIGRATIONS):
                name = os.path.basename(path)
                with open(path, encoding="utf-8") as handle:
                    found = set(dbadopt.parse_plain_seed_inserts(handle.read()))
                self.assertEqual(
                    found, KNOWN_UNGUARDED_SEEDS.get(name, set()),
                    "{0} seeds a non-temporary table with a plain INSERT INTO. "
                    "Use INSERT IGNORE: a converted OSCAR 19 datadir already "
                    "holds reference rows, and the migration aborts on the "
                    "duplicate key.".format(name))

    def test_the_billing_unique_indexes_this_prepares_for_still_exist(self):
        # If V1.0.11/V1.0.12 are ever renamed or dropped, the disambiguation
        # here becomes an unexplained rewrite of billing identifiers.
        text = ""
        for path in dbadopt.forward_migration_files("on", REPO_MIGRATIONS):
            with open(path, encoding="utf-8") as handle:
                text += handle.read()
        for table, column, _order, _suffix in dbadopt.BILLING_UNIQUE:
            self.assertTrue(
                re.search(r"CREATE\s+UNIQUE\s+INDEX[^;]*\b%s\b[^;]*\(\s*`?%s`?"
                          % (re.escape(table), re.escape(column)), text,
                          re.IGNORECASE | re.DOTALL),
                "no UNIQUE index found over %s.%s" % (table, column))



class TestDryRunDrivesTheWholePlan(unittest.TestCase):
    """`--dry-run` end to end against a stubbed database.

    The generators are unit-tested above; this covers the verb's own wiring --
    the plan tuples it unpacks, the order it probes in, and above all that a
    dry run issues no statement that could change anything."""

    def setUp(self):
        self.executed = []

        def fake_client(_dbops, _db, args, **kw):
            sql = ""
            for i, a in enumerate(args):
                if a == "-e":
                    sql = args[i + 1]
            if kw.get("input"):
                self.executed.append(kw["input"])
            out = "0"
            if "information_schema.COLUMNS" in sql and "COLUMN_NAME," in sql:
                out = "security\tsecurity_no\nsecurity\tuser_name"
            elif "CHARACTER_MAXIMUM_LENGTH" in sql:
                out = "50"
            return mock.Mock(returncode=0, stdout=out, stderr="")

        self.patches = [
            mock.patch.object(dbadopt, "_client", fake_client),
            mock.patch.object(dbadopt, "need_root"),
            mock.patch.object(dbadopt.dbops, "require_db_root"),
            mock.patch.object(dbadopt.dbops, "run_flyway",
                              side_effect=AssertionError("dry run must not stamp")),
            mock.patch.object(dbadopt, "MIGRATION_ROOT", REPO_MIGRATIONS),
            mock.patch.object(dbadopt.config, "load",
                              return_value=mock.Mock(db_name="carlos",
                                                     schema_province="on")),
        ]
        for p in self.patches:
            p.start()
        self.addCleanup(lambda: [p.stop() for p in self.patches])

    @unittest.skipUnless(os.path.isdir(REPO_MIGRATIONS),
                         "runs from a source checkout, not the installed package")
    def test_dry_run_reports_and_writes_nothing(self):
        import contextlib
        import io
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            rc = dbadopt.cmd_db_baseline(["--dry-run"])
        self.assertEqual(rc, 0)
        # No script was ever piped to the client, and run_flyway would have
        # raised had the stamp been attempted.
        self.assertEqual(self.executed, [])
        printed = stdout.getvalue() + stderr.getvalue()
        self.assertIn("genesis:", printed)
        self.assertIn("Nothing was changed", printed)

    @unittest.skipUnless(os.path.isdir(REPO_MIGRATIONS),
                         "runs from a source checkout, not the installed package")
    def test_rejects_an_unknown_option_before_touching_anything(self):
        with self.assertRaises(SystemExit):
            dbadopt.cmd_db_baseline(["--recncile"])
        self.assertEqual(self.executed, [])

    def test_rejects_combined_dry_run_and_stamp_only(self):
        with self.assertRaises(SystemExit):
            dbadopt.cmd_db_baseline(["--dry-run", "--stamp-only"])
        self.assertEqual(self.executed, [])

    @unittest.skipUnless(os.path.isdir(REPO_MIGRATIONS),
                         "runs from a source checkout, not the installed package")
    def test_missing_auto_increment_column_prevents_stamping(self):
        with mock.patch.object(dbadopt, "live_schema",
                               return_value={"security": {"user_name"}}):
            with self.assertRaises(SystemExit):
                dbadopt.cmd_db_baseline([])
        self.assertEqual(self.executed, [])


class TestFailClosedDatabaseProbes(unittest.TestCase):

    def test_table_existence_query_must_succeed(self):
        db = mock.Mock()
        db.db_root.return_value = mock.Mock(returncode=1, stdout="", stderr="connection lost")
        with self.assertRaises(SystemExit):
            dbadopt._table_exists(db, "carlos", "icd10")

    def test_live_schema_query_must_succeed(self):
        db = mock.Mock()
        db.db_root.return_value = mock.Mock(returncode=1, stdout="", stderr="connection lost")
        with self.assertRaises(SystemExit):
            dbadopt.live_schema(db, "carlos")

    def test_composite_primary_key_is_not_treated_as_single_column(self):
        db = mock.Mock()
        db.db_root.return_value = mock.Mock(
            returncode=0, stderr="", stdout="id\tint\t1\t1\nother\tint\t2\t2\n")
        self.assertIsNone(dbadopt._single_integer_pk(db, "carlos", "icd10"))

    def test_divergent_seed_code_is_reported_without_blocking_adoption(self):
        # Deliberately a REPORT, not a refusal. The rows survive in the backup
        # table, so nothing is destroyed; refusing would block the whole
        # adoption on a reference-table discrepancy and leave the operator
        # hand-writing SQL against a clinical database at go-live.
        db = mock.Mock()
        db.db_root.return_value = mock.Mock(
            returncode=0, stderr="", stdout="14902\tlegacy-code\n")
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            diverging = dbadopt._check_seed_rows(
                db, "carlos", "icd10", "id", {14902: "canonical-code"}, 1,
                ["id", "icd10"], "V1.0.5.sql")
        self.assertEqual(diverging, 1)
        printed = stderr.getvalue()
        self.assertIn("1 row(s)", printed)
        self.assertIn(dbadopt.BACKUP_PREFIX + "icd10", printed)
        self.assertIn("before go-live", printed)

    def test_warnings_flush_stdout_so_a_redirected_transcript_stays_in_order(self):
        # log() -> stdout (block-buffered when redirected), warn() -> stderr
        # (unbuffered). Without the flush, `db-baseline > adopt.log 2>&1` hoists
        # every warning above progress lines printed before it -- the wrong
        # order for the one message that has to be acted on.
        flushed = []
        with mock.patch.object(dbadopt.sys.stdout, "flush",
                               lambda: flushed.append(True)), \
                contextlib.redirect_stderr(io.StringIO()):
            dbadopt._warn("something worth reading")
        self.assertEqual(flushed, [True])

    def test_matching_seed_code_reports_nothing(self):
        db = mock.Mock()
        db.db_root.return_value = mock.Mock(
            returncode=0, stderr="", stdout="14902\tcanonical-code\n")
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            diverging = dbadopt._check_seed_rows(
                db, "carlos", "icd10", "id", {14902: "canonical-code"}, 1,
                ["id", "icd10"], "V1.0.5.sql")
        self.assertEqual(diverging, 0)
        self.assertEqual(stderr.getvalue(), "")

    def test_unreadable_seed_code_stops_adoption_before_deletion(self):
        db = mock.Mock()
        db.db_root.return_value = mock.Mock(returncode=1, stderr="connection lost")
        with self.assertRaises(SystemExit):
            dbadopt._check_seed_rows(db, "carlos", "icd10", "id",
                                     {14902: "canonical-code"}, 1,
                                     ["id", "icd10"], "V1.0.5.sql")


if __name__ == "__main__":
    unittest.main()
