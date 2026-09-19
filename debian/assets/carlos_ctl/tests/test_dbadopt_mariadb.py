# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Optional live SQL regressions for adoption's reference-row moves.

Set CARLOS_TEST_MARIADB_SOCKET to a disposable MariaDB server accepting root
socket connections. Each test creates and drops its own uniquely named database.
No existing database is used. Without that setting these tests are skipped.
"""

import contextlib
import io
import os
import shutil
import subprocess
import unittest
import uuid
from pathlib import Path
from unittest import mock

from carlos_ctl import dbadopt
from carlos_ctl.util import sql_escape


@unittest.skipUnless(os.environ.get("CARLOS_TEST_MARIADB_SOCKET")
                     and shutil.which("mariadb"),
                     "set CARLOS_TEST_MARIADB_SOCKET to run live SQL regressions")
class TestSeedMovesMariaDB(unittest.TestCase):
    @staticmethod
    def db_root(args, **kwargs):
        return subprocess.run([
            "mariadb", "--no-defaults", "--protocol=socket", "--user=root",
            "--socket=" + os.environ["CARLOS_TEST_MARIADB_SOCKET"],
        ] + args, text=True, **kwargs)

    def setUp(self):
        self.database = "carlos_adopt_test_" + uuid.uuid4().hex
        self.query("CREATE DATABASE `{0}`".format(self.database), use_db=False)
        self.addCleanup(self.query, "DROP DATABASE `{0}`".format(self.database),
                        use_db=False)
        self.query("CREATE TABLE icd10 (id INT PRIMARY KEY AUTO_INCREMENT, "
                   "icd10 VARCHAR(32), description VARCHAR(100)) "
                   "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci")
        self.columns = ["id", "icd10", "description"]

    def query(self, sql, use_db=True):
        args = ["-N", "-B"]
        if use_db:
            args += ["--database", self.database]
        cp = self.db_root(args, input=sql, capture_output=True)
        self.assertEqual(cp.returncode, 0, cp.stderr)
        return cp.stdout.strip()

    def classify(self, canonical=None, all_seeds=None):
        canonical = canonical or {14902: "Y19"}
        present = int(self.query("SELECT COUNT(*) FROM icd10 WHERE id IN ({0})".format(
            ",".join(str(k) for k in canonical))))
        return dbadopt._classify_seed_rows(
            self, self.database, "icd10", "id", canonical, present,
            self.columns, "V1.0.5.sql", all_seeds=all_seeds)[1]

    def script(self, rehome, keys=(14902,)):
        return dbadopt.seed_collision_script(
            "icd10", "id", keys, self.columns, rehome)

    def run_script(self, script):
        dbadopt._run_script(self, self.database, script, "test seed moves")

    def billing_history(self, version="1.0.11", success=1):
        self.query("CREATE TABLE flyway_schema_history (version VARCHAR(50), "
                   "type VARCHAR(20), script VARCHAR(200), success INT);"
                   "INSERT INTO flyway_schema_history VALUES "
                   "('1.0.2','BASELINE','baseline',1),"
                   "('1.0.5','SQL','V1.0.5__restore_live_legacy_common_tables.sql',1),"
                   "('{0}','SQL','{1}',{2});"
                   "CREATE TABLE billing_on_diskname (id INT PRIMARY KEY AUTO_INCREMENT, "
                   "ohipfilename VARCHAR(50), createdatetime DATETIME, timestamp TIMESTAMP);"
                   "CREATE TABLE billing_on_filename (id INT PRIMARY KEY AUTO_INCREMENT, "
                   "htmlfilename VARCHAR(50), timestamp TIMESTAMP)".format(
                       version, dbadopt.BILLING_INDEX_MIGRATIONS[version], success))

    def stale(self):
        # A genesis gap alone must not invalidate a legitimate older adoption.
        tables = {"security": dbadopt.TableDef("security", [("mfaSecret", "text")], "")}
        return dbadopt._stale_history(self, self.database, tables, {})

    def test_recorded_billing_indexes_distinguish_restored_and_old_adopted_schemas(self):
        self.billing_history()
        self.assertTrue(self.stale())
        self.query("CREATE UNIQUE INDEX billing_on_diskname_ohipfilename_uq "
                   "ON billing_on_diskname (ohipfilename)")
        self.assertTrue(self.stale())
        self.query("CREATE UNIQUE INDEX billing_on_filename_htmlfilename_uq "
                   "ON billing_on_filename (htmlfilename)")
        self.assertFalse(self.stale())
        self.query("UPDATE flyway_schema_history SET version='1.0.12', "
                   "script='V1.0.12__portable_billing_filename_unique_indexes.sql' "
                   "WHERE version='1.0.11'")
        self.assertFalse(self.stale())
        self.query("DROP INDEX billing_on_diskname_ohipfilename_uq ON billing_on_diskname")
        self.assertTrue(self.stale())

    def test_wrong_index_shape_does_not_validate_recorded_history(self):
        self.billing_history()
        self.query("CREATE UNIQUE INDEX billing_on_filename_htmlfilename_uq "
                   "ON billing_on_filename (htmlfilename)")
        for kind, columns in (("INDEX", "ohipfilename"),
                              ("UNIQUE INDEX", "ohipfilename,id"),
                              ("UNIQUE INDEX", "id"),
                              ("UNIQUE INDEX", "ohipfilename(8)")):
            with self.subTest(kind=kind, columns=columns):
                self.query("CREATE {0} billing_on_diskname_ohipfilename_uq "
                           "ON billing_on_diskname ({1})".format(kind, columns))
                self.assertTrue(self.stale())
                self.query("DROP INDEX billing_on_diskname_ohipfilename_uq "
                           "ON billing_on_diskname")

    def test_equivalent_renamed_indexes_preserve_real_history(self):
        self.billing_history()
        self.query("CREATE UNIQUE INDEX clinic_diskname ON billing_on_diskname (ohipfilename);"
                   "CREATE UNIQUE INDEX clinic_filename ON billing_on_filename (htmlfilename)")
        self.assertFalse(self.stale())

    def test_failed_or_unrelated_migration_does_not_claim_billing_indexes(self):
        self.billing_history(success=0)
        self.assertFalse(self.stale())
        self.query("UPDATE flyway_schema_history SET success=1,script='unrelated.sql' "
                   "WHERE version='1.0.11'")
        self.assertFalse(self.stale())
        self.query("UPDATE flyway_schema_history SET type='BASELINE', "
                   "script='V1.0.11__billing_filename_unique_indexes.sql' "
                   "WHERE version='1.0.11'")
        self.assertFalse(self.stale())

    def test_restored_history_reenables_seed_preparation_in_dry_run(self):
        root = Path(__file__).resolve().parents[4] / "database/mysql/migration"
        if not root.is_dir():
            self.skipTest("requires packaged migrations from a source checkout")
        self.billing_history()
        self.query("INSERT INTO icd10 VALUES (14902,'Y19','legacy')")
        output = io.StringIO()
        with mock.patch.object(dbadopt, "MIGRATION_ROOT", str(root)), \
                mock.patch.object(dbadopt, "need_root"), \
                mock.patch.object(dbadopt.dbops, "require_db_root"), \
                mock.patch.object(dbadopt.config, "load", return_value=mock.Mock(
                    db_name=self.database, schema_province="on")), \
                mock.patch.object(dbadopt.dbops, "db_root", self.db_root), \
                mock.patch.object(dbadopt, "_run_script") as mutate, \
                mock.patch.object(dbadopt.dbops, "run_flyway") as stamp, \
                contextlib.redirect_stdout(output):
            self.assertEqual(dbadopt.cmd_db_baseline(["--dry-run"]), 0)
            mutate.assert_not_called()
            stamp.assert_not_called()
        self.assertIn("1 row(s) in `icd10` collide", output.getvalue())
        self.assertIn("It will be renamed aside", output.getvalue())
        self.assertEqual(self.query("SELECT COUNT(*) FROM flyway_schema_history"), "3")
        self.assertEqual(self.query("SELECT id FROM icd10"), "14902")

    def test_failed_index_metadata_probe_cannot_invalidate_history(self):
        self.billing_history()
        original = dbadopt._client

        def fail_index_query(dbops, db_name, args, **kwargs):
            if any("information_schema.STATISTICS" in arg for arg in args):
                return mock.Mock(returncode=1, stdout="", stderr="connection lost")
            return original(dbops, db_name, args, **kwargs)

        with mock.patch.object(dbadopt, "_client", side_effect=fail_index_query):
            with self.assertRaises(SystemExit):
                self.stale()
        self.assertEqual(self.query("SELECT COUNT(*) FROM flyway_schema_history"), "3")

    def test_conflicting_backup_does_not_move_or_delete_live_row(self):
        self.query("INSERT INTO icd10 VALUES (14902,'LOCAL','new description');"
                   "CREATE TABLE carlos_adopt_backup_icd10 LIKE icd10;"
                   "INSERT INTO carlos_adopt_backup_icd10 "
                   "VALUES (14902,'LOCAL','old description')")
        self.run_script(self.script(self.classify()))
        self.assertEqual(self.query("SELECT id,description FROM icd10"),
                         "14902\tnew description")
        self.assertEqual(self.query("SELECT id,description FROM carlos_adopt_backup_icd10"),
                         "14902\told description")

    def test_local_code_and_original_id_survive_and_next_id_is_free(self):
        self.query("INSERT INTO icd10 VALUES (14902,'LOCAL','clinic entry')")
        self.run_script(self.script(self.classify()))
        self.query("INSERT INTO icd10 VALUES (14902,'Y19','canonical');"
                   "INSERT INTO icd10 (icd10,description) VALUES ('NEXT','new')")
        self.assertEqual(self.query("SELECT id,icd10 FROM icd10 ORDER BY id"),
                         "14902\tY19\n14903\tLOCAL\n14904\tNEXT")
        self.assertEqual(self.query("SELECT id,icd10 FROM carlos_adopt_backup_icd10"),
                         "14902\tLOCAL")

    def test_canonical_code_uses_database_case_and_space_comparison(self):
        self.query("INSERT INTO icd10 VALUES (14902,'y19 ','case variant')")
        rehome = self.classify()
        self.assertEqual(rehome, {})
        self.run_script(self.script(rehome))
        self.query("INSERT INTO icd10 VALUES (14902,'Y19','canonical')")
        self.assertEqual(self.query("SELECT COUNT(*) FROM icd10 WHERE icd10='Y19'"), "1")

    def test_outside_survivor_uses_database_case_comparison(self):
        self.query("INSERT INTO icd10 VALUES (500,'LOCAL','outside'),"
                   "(14902,'local','inside')")
        rehome = self.classify()
        self.assertEqual(rehome, {})
        self.run_script(self.script(rehome))
        self.assertEqual(self.query("SELECT id,icd10 FROM icd10"), "500\tLOCAL")

    def test_batch_escaped_code_is_compared_inside_database(self):
        code = sql_escape("LOCAL\t\\\nCODE")
        self.query("SET sql_mode=''; INSERT INTO icd10 VALUES "
                   "(500,'{0}','outside'),(14902,'{0}','inside')".format(code))
        self.assertEqual(self.classify(), {})

    def test_binary_collation_keeps_distinct_case_variant(self):
        self.query("ALTER TABLE icd10 MODIFY icd10 VARCHAR(32) "
                   "CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;"
                   "INSERT INTO icd10 VALUES (14902,'y19','distinct code')")
        self.assertEqual(self.classify(), {14902: 14903})

    def test_ignore_seed_at_empty_key_restores_displaced_code(self):
        self.query("INSERT INTO icd10 VALUES (14902,'n/a','displaced')")
        rehome = self.classify(all_seeds={1: "N/A", 14902: "Y19"})
        self.assertEqual(rehome, {})
        self.run_script(self.script(rehome))
        self.query("INSERT IGNORE INTO icd10 VALUES (1,'N/A','canonical');"
                   "INSERT INTO icd10 VALUES (14902,'Y19','canonical')")
        self.assertEqual(self.query("SELECT COUNT(*) FROM icd10 WHERE icd10='N/A'"), "1")

    def test_ignore_seed_at_occupied_key_does_not_restore_displaced_code(self):
        self.query("INSERT INTO icd10 VALUES (1,'OTHER','occupied'),"
                   "(14902,'N/A','displaced')")
        rehome = self.classify(all_seeds={1: "N/A", 14902: "Y19"})
        self.assertEqual(rehome, {14902: 14903})
        self.run_script(self.script(rehome))
        self.query("INSERT IGNORE INTO icd10 VALUES (1,'N/A','canonical');"
                   "INSERT INTO icd10 VALUES (14902,'Y19','canonical')")
        self.assertEqual(self.query("SELECT id FROM icd10 WHERE icd10='N/A'"), "14903")

    def test_complete_packaged_seed_does_not_duplicate_displaced_code(self):
        root = Path(__file__).resolve().parents[4] / "database/mysql/migration"
        migration = root / "common/V1.0.5__restore_live_legacy_common_tables.sql"
        if not migration.is_file():
            self.skipTest("requires the packaged seed from a source checkout")
        self.query("INSERT INTO icd10 VALUES (14902,'80000','displaced')")
        collisions = dbadopt.plan_seed_collisions(
            self, self.database, "on", set(), str(root))
        self.assertEqual(len(collisions), 1)
        table, pk, keys, present, source, columns, diverged, rehome = collisions[0]
        self.assertEqual(rehome, {})
        self.run_script(dbadopt.seed_collision_script(table, pk, keys, columns, rehome))
        seed_sql = "\n".join(match.group(0) for match in dbadopt._SEED_INSERT.finditer(
            migration.read_text(encoding="utf-8")) if match.group("table") == "icd10")
        self.run_script(seed_sql)
        self.assertEqual(self.query("SELECT COUNT(*) FROM icd10"), "15971")
        self.assertEqual(self.query("SELECT COUNT(*) FROM icd10 WHERE icd10='80000'"), "1")

    def test_interruption_after_first_move_can_resume(self):
        canonical = {14902: "Y19", 14903: "Y20"}
        self.query("INSERT INTO icd10 VALUES (14902,'LOCAL1','one'),"
                   "(14903,'LOCAL2','two')")
        script = self.script(self.classify(canonical), tuple(canonical))
        # Execute through the first move, then simulate losing the connection.
        statements = script.split(";")
        first_move = next(i for i, statement in enumerate(statements)
                          if "SET t.`id`" in statement)
        self.run_script(";".join(statements[:first_move + 1]) + ";")
        self.run_script(self.script(self.classify(canonical), tuple(canonical)))
        self.query("INSERT INTO icd10 VALUES (14902,'Y19','canonical'),"
                   "(14903,'Y20','canonical');"
                   "INSERT INTO icd10 (icd10,description) VALUES ('NEXT','new')")
        self.assertEqual(self.query("SELECT id,icd10 FROM icd10 ORDER BY id"),
                         "14902\tY19\n14903\tY20\n14904\tLOCAL1\n14905\tLOCAL2\n14906\tNEXT")
        self.assertEqual(self.query("SELECT id,icd10 FROM carlos_adopt_backup_icd10 ORDER BY id"),
                         "14902\tLOCAL1\n14903\tLOCAL2")
