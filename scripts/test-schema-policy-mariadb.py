#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Real MariaDB regression tests for collation/index forward migrations.

Uses unique disposable databases only. MYSQL_HOST/USER/PWD select the test
server. SCHEMA_POLICY_REFERENCE enables full-schema index tests against an
already migrated disposable reference database. No clinic DB is modified.
"""
import os
from pathlib import Path
import subprocess
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
COLLATION = (ROOT / "database/mysql/migration/common/V1.0.59.6__align_clinical_text_and_status_collations.sql").read_text()
INDEXES = (ROOT / "database/mysql/migration/common/V1.0.59.7__reconcile_managed_performance_indexes.sql").read_text()
CONNECTION = ["--no-defaults", "-h", os.environ.get("MYSQL_HOST", "localhost"), "-u", os.environ.get("MYSQL_USER", "root")]


def execute(statement, database=None, check=True):
    return subprocess.run(["mariadb"] + CONNECTION + ["--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4"]
                          + ([database] if database else []), input=statement, text=True,
                          capture_output=True, check=check, timeout=120)


class DatabaseTest(unittest.TestCase):
    def setUp(self):
        self.database = "carlos_policy_test_" + uuid.uuid4().hex
        execute("CREATE DATABASE `{0}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci".format(self.database))
        self.addCleanup(execute, "DROP DATABASE `{0}`".format(self.database))

    def sql(self, statement, check=True):
        return execute(statement, self.database, check)


class TestClinicalCollations(DatabaseTest):
    def setUp(self):
        super().setUp()
        self.sql("""
            CREATE TABLE demographic(id INT PRIMARY KEY, first_name VARCHAR(30) NOT NULL DEFAULT '', last_name VARCHAR(30) NOT NULL DEFAULT '');
            CREATE TABLE provider(id INT PRIMARY KEY, first_name VARCHAR(30) NOT NULL DEFAULT '', last_name VARCHAR(30) NOT NULL DEFAULT '');
            CREATE TABLE casemgmt_note(id INT PRIMARY KEY, note MEDIUMTEXT NOT NULL);
            CREATE TABLE document(id INT PRIMARY KEY, docdesc VARCHAR(255) NOT NULL DEFAULT '');
            CREATE TABLE drugs(id INT PRIMARY KEY, special TEXT);
            CREATE TABLE tickler(id INT PRIMARY KEY, message TEXT);
            CREATE TABLE appointment(id INT PRIMARY KEY, status CHAR(2) COLLATE utf8mb4_bin);
            CREATE TABLE appointment_status(id INT PRIMARY KEY, status CHAR(2) NOT NULL);
        """)

    def test_case_codes_unicode_distinctions_and_exact_bytes_survive(self):
        self.sql("SET NAMES utf8mb4; INSERT INTO demographic VALUES (1,'😀','Café '),(2,'😃','Sante');"
                 "INSERT INTO appointment_status VALUES(1,'C'),(2,'c'),(3,'H'),(4,'h');")
        before = self.sql("SELECT id,HEX(first_name),HEX(last_name) FROM demographic ORDER BY id").stdout
        self.sql(COLLATION)
        self.assertEqual(self.sql("SELECT id,HEX(first_name),HEX(last_name) FROM demographic ORDER BY id").stdout, before)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM demographic WHERE first_name=CONVERT(0xF09F9880 USING utf8mb4)").stdout.strip(), "1")
        for code in ("C", "c", "H", "h"):
            self.assertEqual(self.sql("SELECT COUNT(*) FROM appointment_status WHERE status='{0}'".format(code)).stdout.strip(), "1")
        self.sql(COLLATION)  # Recovery/reapplication is a no-op.

    def test_unique_collisions_stop_before_any_column_change(self):
        self.sql("ALTER TABLE document ADD UNIQUE INDEX clinic_description(docdesc);"
                 "INSERT INTO document VALUES(1,'Straße'),(2,'Strasse');")
        before = self.sql("SELECT id,HEX(docdesc) FROM document ORDER BY id").stdout
        result = self.sql(COLLATION, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("resolve_unique_key_collisions", result.stderr)
        self.assertEqual(self.sql("SELECT id,HEX(docdesc) FROM document ORDER BY id").stdout, before)
        self.assertEqual(self.sql("SELECT collation_name FROM information_schema.columns WHERE table_schema=DATABASE() "
                                 "AND table_name='demographic' AND column_name='first_name'").stdout.strip(), "utf8mb4_general_ci")

    def test_composite_unique_keys_use_final_collations_in_one_alter(self):
        # General_ci distinguishes the sharp-s spelling, but conflates these
        # supplementary characters. UCA14 reverses those equivalences. Both
        # complete keys remain unique before and after migration; changing just
        # first_name would temporarily make them duplicates.
        for table in ("demographic", "provider"):
            self.sql("ALTER TABLE `{0}` ADD UNIQUE INDEX clinic_name(first_name(20), last_name);"
                     "INSERT INTO `{0}` VALUES(1,'Straße','😀'),(2,'Strasse','😃');".format(table))
        before = {table: self.sql("SELECT id,HEX(first_name),HEX(last_name) FROM `{0}` ORDER BY id".format(table)).stdout
                  for table in ("demographic", "provider")}
        self.sql(COLLATION)
        for table in ("demographic", "provider"):
            self.assertEqual(self.sql("SELECT id,HEX(first_name),HEX(last_name) FROM `{0}` ORDER BY id".format(table)).stdout,
                             before[table])
            self.assertEqual(self.sql("SELECT COUNT(*) FROM `{0}` WHERE last_name='😀'".format(table)).stdout.strip(), "1")
            self.assertEqual(self.sql("SELECT COUNT(*) FROM `{0}` WHERE first_name='Strasse'".format(table)).stdout.strip(), "2")
        self.sql(COLLATION)  # All columns already match; the grouped ALTER is a no-op.

    def test_existing_capacity_defaults_nullability_and_comments_are_retained(self):
        self.sql("ALTER TABLE demographic MODIFY first_name VARCHAR(200) NULL DEFAULT 'Unknown' COMMENT 'clinic width';")
        self.sql(COLLATION)
        row = self.sql("SELECT column_type,is_nullable,column_default,column_comment,collation_name "
                       "FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='demographic' "
                       "AND column_name='first_name'").stdout.strip()
        self.assertEqual(row, "varchar(200)\tYES\t'Unknown'\tclinic width\tutf8mb4_uca1400_ai_ci")


@unittest.skipUnless(os.environ.get("SCHEMA_POLICY_REFERENCE"), "set SCHEMA_POLICY_REFERENCE for full index tests")
class TestIndexConvergence(DatabaseTest):
    @classmethod
    def setUpClass(cls):
        reference = os.environ["SCHEMA_POLICY_REFERENCE"]
        if not reference.replace("_", "").isalnum():
            raise ValueError("reference name must be a plain identifier")
        cls.structure = subprocess.run(["mariadb-dump"] + CONNECTION + ["--no-data", "--skip-triggers", reference],
                                       text=True, capture_output=True, check=True, timeout=60).stdout

    def setUp(self):
        super().setUp()
        self.sql(self.structure)

    def definition(self, table, name):
        return self.sql("SELECT column_name,sub_part,non_unique,index_type,ignored FROM information_schema.statistics "
                        "WHERE table_schema=DATABASE() AND table_name='{0}' AND index_name='{1}' ORDER BY seq_in_index"
                        .format(table, name)).stdout

    def test_same_name_wrong_index_and_ignored_index_converge(self):
        expected = self.definition("tickler", "idx_tickler_status_service_date")
        self.sql("ALTER TABLE tickler DROP INDEX idx_tickler_status_service_date, "
                 "ADD INDEX idx_tickler_status_service_date(tickler_no);"
                 "ALTER TABLE document ALTER INDEX idx_document_observationdate IGNORED;"
                 "CREATE INDEX idx_providerExt_provider_no ON providerExt(provider_no);"
                 "CREATE INDEX demographic_no ON demographicExt(demographic_no);")
        self.sql(INDEXES)
        self.assertEqual(self.definition("tickler", "idx_tickler_status_service_date"), expected)
        self.assertIn("\tNO\n", self.definition("document", "idx_document_observationdate"))
        self.assertEqual(self.definition("providerExt", "idx_providerExt_provider_no"), "")
        self.assertEqual(self.definition("demographicExt", "demographic_no"), "")
        self.assertNotEqual(self.definition("providerExt", "providerExt_provider_no_uq"), "")
        self.assertNotEqual(self.definition("demographicExt", "uk_demo_ext_single_value"), "")
        self.sql(INDEXES)

    def test_unexpected_unique_index_is_not_downgraded(self):
        self.sql("ALTER TABLE tickler DROP INDEX idx_tickler_status_service_date, "
                 "ADD INDEX idx_tickler_status_service_date(tickler_no);"
                 "ALTER TABLE document DROP INDEX idx_document_observationdate, "
                 "ADD UNIQUE INDEX idx_document_observationdate(document_no);")
        before = self.definition("tickler", "idx_tickler_status_service_date")
        result = self.sql(INDEXES, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Unexpected unique index", result.stderr)
        self.assertEqual(self.definition("tickler", "idx_tickler_status_service_date"), before)


if __name__ == "__main__":
    unittest.main(verbosity=2)
