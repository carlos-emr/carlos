# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Optional live SQL regressions for adoption's reference-row moves.

Set CARLOS_TEST_MARIADB_SOCKET to a disposable MariaDB server accepting root
socket connections. Each test creates and drops its own uniquely named database.
No existing database is used. Without that setting these tests are skipped.
"""

import os
import shutil
import subprocess
import unittest
import uuid

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

    def classify(self, canonical=None):
        canonical = canonical or {14902: "Y19"}
        present = int(self.query("SELECT COUNT(*) FROM icd10 WHERE id IN ({0})".format(
            ",".join(str(k) for k in canonical))))
        return dbadopt._classify_seed_rows(
            self, self.database, "icd10", "id", canonical, present,
            self.columns, "V1.0.5.sql")[1]

    def script(self, rehome, keys=(14902,)):
        return dbadopt.seed_collision_script(
            "icd10", "id", keys, self.columns, rehome)

    def run_script(self, script):
        dbadopt._run_script(self, self.database, script, "test seed moves")

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
