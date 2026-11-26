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
ROSTER_INDEX = (ROOT / "database/mysql/migration/common/V1.0.59.50.2__ensure_historical_roster_lookup.sql").read_text()
OHIP_FILE_INDEXES = (ROOT / "database/mysql/migration/on/V1.0.59.50.3__index_ohip_billing_file_owners.sql").read_text()
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


class TestHistoricalRosterIndex(DatabaseTest):
    name = "idx_demographicArchive_provider_roster_date"

    def setUp(self):
        super().setUp()
        self.sql("CREATE TABLE demographicArchive (id INT PRIMARY KEY, provider_no VARCHAR(25), roster_date DATE);"
                 "INSERT INTO demographicArchive VALUES (1,'000001','2000-01-01');")

    def indexes(self):
        result = self.sql("SELECT index_name, non_unique, ignored, GROUP_CONCAT(CONCAT(column_name, "
                          "IFNULL(CONCAT('(',sub_part,')'),'')) ORDER BY seq_in_index) "
                          "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                          "AND table_name='demographicArchive' GROUP BY index_name, non_unique, ignored")
        return set(result.stdout.splitlines())

    def test_missing_and_stock_indexes_converge_once(self):
        before = self.indexes()
        self.sql(ROSTER_INDEX)
        expected = before | {self.name + "\t1\tNO\tprovider_no,roster_date"}
        self.assertEqual(self.indexes(), expected)
        self.sql(ROSTER_INDEX)
        self.assertEqual(self.indexes(), expected)

    def test_custom_unique_ignored_and_prefix_indexes_are_preserved(self):
        self.sql("ALTER TABLE demographicArchive ADD UNIQUE INDEX " + self.name + "(roster_date), "
                 "ADD UNIQUE INDEX provider_no(id), ADD INDEX clinic_prefix(provider_no(2),roster_date), "
                 "ADD INDEX clinic_ignored(provider_no,roster_date) IGNORED")
        before = self.indexes()
        self.sql(ROSTER_INDEX)
        added = self.indexes() - before
        self.assertEqual(len(added), 1)
        self.assertTrue(added.pop().endswith("\t1\tNO\tprovider_no,roster_date"))
        self.assertTrue(before <= self.indexes())
        after = self.indexes()
        self.sql(ROSTER_INDEX)
        self.assertEqual(self.indexes(), after)
        self.assertEqual(self.sql("SELECT id,provider_no,roster_date FROM demographicArchive").stdout.strip(),
                         "1\t000001\t2000-01-01")

    def test_existing_compound_lookup_with_another_name_is_reused(self):
        self.sql("ALTER TABLE demographicArchive ADD INDEX " + self.name + "(roster_date), "
                 "ADD INDEX clinic_history(provider_no,roster_date,id)")
        before = self.indexes()
        self.sql(ROSTER_INDEX)
        self.assertEqual(self.indexes(), before)

    def test_index_limit_does_not_block_upgrade_or_remove_clinic_indexes(self):
        indexes = ["ADD INDEX " + self.name + "(roster_date)"]
        indexes.extend("ADD INDEX clinic_{}(roster_date)".format(n) for n in range(62))
        self.sql("ALTER TABLE demographicArchive " + ", ".join(indexes))
        before = self.indexes()
        # Inspect the warning immediately after CALL, before its cleanup statement.
        result = self.sql(ROSTER_INDEX.replace("CALL carlos_ensure_historical_roster_lookup();",
                          "CALL carlos_ensure_historical_roster_lookup(); SHOW WARNINGS;"))
        self.assertIn("Roster lookup index exceeds local index limits", result.stdout)
        self.assertEqual(self.indexes(), before)


class TestOntarioBillingFileIndexes(DatabaseTest):
    def setUp(self):
        super().setUp()
        self.sql("""
            CREATE TABLE billactivity(id INT PRIMARY KEY, ohipfilename VARCHAR(50), htmlfilename VARCHAR(50),
                                      providerohipno VARCHAR(6));
            CREATE TABLE billing_on_diskname(id INT PRIMARY KEY, ohipfilename VARCHAR(50), UNIQUE KEY(ohipfilename));
            CREATE TABLE billing_on_filename(id INT PRIMARY KEY, disk_id INT, htmlfilename VARCHAR(50),
                                             providerno VARCHAR(6), UNIQUE KEY(htmlfilename));
        """)

    def indexes(self):
        return set(self.sql("""
            SELECT table_name, index_name, non_unique, ignored,
                   GROUP_CONCAT(CONCAT(column_name, IFNULL(CONCAT('(',sub_part,')'),'')) ORDER BY seq_in_index)
              FROM information_schema.statistics WHERE table_schema=DATABASE()
             GROUP BY table_name, index_name, non_unique, ignored
        """).stdout.splitlines())

    def test_stock_rows_and_repeated_filenames_survive_and_reapply_is_a_noop(self):
        self.sql("""
            INSERT INTO billactivity VALUES (1,'old.001','a.htm','123456'), (2,'old.001','a.htm','123457'),
                                            (3,NULL,NULL,NULL);
            INSERT INTO billing_on_filename VALUES (1,7,'a.html','123456'), (2,7,'b.html','123457'), (3,NULL,NULL,NULL);
        """)
        rows = "SELECT * FROM billactivity ORDER BY id; SELECT * FROM billing_on_filename ORDER BY id;"
        before = self.sql(rows).stdout
        expected = self.indexes() | {
            "billactivity\tohipfilename\t1\tNO\tohipfilename",
            "billactivity\thtmlfilename\t1\tNO\thtmlfilename",
            "billing_on_filename\tdisk_id\t1\tNO\tdisk_id",
        }
        for _ in range(2):
            self.sql(OHIP_FILE_INDEXES)
            self.assertEqual(self.indexes(), expected)
            self.assertEqual(self.sql(rows).stdout, before)

    def test_custom_leading_keys_are_reused_and_conflicting_names_are_preserved(self):
        self.sql("""
            ALTER TABLE billactivity ADD UNIQUE KEY clinic_archive(ohipfilename,id),
                ADD INDEX htmlfilename(providerohipno,htmlfilename), ADD INDEX clinic_prefix(htmlfilename(3)),
                ADD INDEX clinic_ignored(htmlfilename) IGNORED;
            ALTER TABLE billing_on_filename ADD UNIQUE KEY disk_id(providerno), ADD INDEX clinic_files(disk_id,id);
        """)
        before = self.indexes()
        self.sql(OHIP_FILE_INDEXES)
        after = self.indexes()
        self.assertTrue(before <= after)
        self.assertEqual(len(after - before), 1)
        self.assertRegex((after - before).pop(), r"^billactivity\thtmlfilename_[0-9]+\t1\tNO\thtmlfilename$")
        self.sql(OHIP_FILE_INDEXES)
        self.assertEqual(self.indexes(), after)

    def test_only_missing_full_keys_are_added_while_all_existing_keys_are_retained(self):
        self.sql("""
            ALTER TABLE billactivity ADD INDEX by_report(htmlfilename), ADD INDEX ohipfilename(ohipfilename(2));
            ALTER TABLE billing_on_filename ADD INDEX disk_id(disk_id) IGNORED;
        """)
        before = self.indexes()
        self.sql(OHIP_FILE_INDEXES)
        after = self.indexes()
        self.assertTrue(before <= after)
        definitions = (row.split('\t') for row in after - before)
        added = {(table, non_unique, ignored, keys) for table, _, non_unique, ignored, keys in definitions}
        self.assertEqual(added, {("billactivity", "1", "NO", "ohipfilename"),
                                 ("billing_on_filename", "1", "NO", "disk_id")})
        self.assertEqual(len(after - before), 2)

    def test_index_limit_warns_keeps_clinic_indexes_and_still_repairs_the_other_table(self):
        self.sql("ALTER TABLE billactivity " + ", ".join(
            "ADD INDEX clinic_{}(providerohipno)".format(n) for n in range(63)))
        before = self.indexes()
        result = self.sql(OHIP_FILE_INDEXES + "\nSHOW WARNINGS;")
        self.assertIn("OHIP ownership lookup indexes exceed local index limits", result.stdout)
        self.assertEqual(self.indexes(), before | {"billing_on_filename\tdisk_id\t1\tNO\tdisk_id"})

    def test_an_unexpected_schema_error_fails_the_migration(self):
        self.sql("ALTER TABLE billactivity DROP COLUMN htmlfilename")
        result = self.sql(OHIP_FILE_INDEXES, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("htmlfilename", result.stderr)

    def test_ownership_queries_use_the_filename_keys_and_the_disk_id_join(self):
        self.sql("""
            INSERT INTO billactivity SELECT seq, CONCAT('claim-',seq), CONCAT('report-',seq), '123456'
                FROM seq_1_to_20000;
            INSERT INTO billing_on_diskname SELECT seq, CONCAT('disk-',seq) FROM seq_1_to_4000;
            INSERT INTO billing_on_filename SELECT seq, 1+((seq-1) DIV 5), CONCAT('file-',seq), '123456'
                FROM seq_1_to_20000;
        """)

        def plans():
            self.sql("ANALYZE TABLE billactivity, billing_on_diskname, billing_on_filename")
            old = self.sql("EXPLAIN SELECT ohipfilename,htmlfilename,providerohipno FROM billactivity "
                           "WHERE ohipfilename IN ('claim-301','report-606') OR htmlfilename IN ('claim-301','report-606')")
            disk = self.sql("EXPLAIN SELECT d.ohipfilename,f.providerno FROM billing_on_diskname d "
                            "LEFT JOIN billing_on_filename f ON f.disk_id=d.id WHERE d.ohipfilename IN ('disk-300','disk-600')")
            result = {}
            for line in (old.stdout + disk.stdout).splitlines():
                columns = line.split('\t')
                result[columns[2]] = (columns[3], columns[5])
            return result

        before = plans()
        self.assertEqual(before["billactivity"][0], "ALL")
        self.assertEqual(before["f"][0], "ALL")
        self.sql(OHIP_FILE_INDEXES)
        after = plans()
        self.assertEqual(after["billactivity"][0], "index_merge")
        self.assertEqual(set(after["billactivity"][1].split(',')), {"ohipfilename", "htmlfilename"})
        self.assertEqual(after["f"], ("ref", "disk_id"))


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
