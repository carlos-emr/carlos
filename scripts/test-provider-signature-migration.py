#!/usr/bin/env python3
"""Run against a disposable local MariaDB/MySQL server with CREATE DATABASE access.

MYSQL_HOST defaults to localhost (socket); only localhost/127.0.0.1/::1 allowed.
MYSQL_USER defaults to root. The client reads MYSQL_PWD or its normal option file.
Each case owns a randomly named database and removes it even after a failure.
"""
import os
from pathlib import Path
import shutil
import subprocess
import unittest
import uuid

MIGRATION = (Path(__file__).resolve().parents[1] /
             'database/mysql/migration/common/V1.0.44__enforce_provider_signature_identity.sql').read_text()

# A conflict must be refused by the staging table's validation index, before
# providerExt is rewritten. A refusal from the final providerExt index would
# mean the source had already been deleted and reinserted.
STAGING_REFUSAL = "for key 'signature_identity_validation'"
UNIQUE_PROVIDER_INDEXES = (
    "SELECT COUNT(*) FROM (SELECT index_name FROM information_schema.statistics "
    "WHERE table_schema=DATABASE() AND table_name='providerExt' GROUP BY index_name "
    "HAVING COUNT(*)=1 AND MIN(non_unique)=0 AND MIN(column_name)='provider_no' "
    "AND MIN(sub_part) IS NULL) indexes")


class SignatureIdentityMigration(unittest.TestCase):
    def setUp(self):
        host = os.environ.get('MYSQL_HOST', 'localhost')
        self.assertIn(host, ('localhost', '127.0.0.1', '::1'))
        client = shutil.which('mariadb') or shutil.which('mysql')
        self.assertIsNotNone(client, 'A MariaDB/MySQL client is required')
        self.command = [client, '-N', '-B', '--host=' + host,
                        '--user=' + os.environ.get('MYSQL_USER', 'root')]
        self.database = 'carlos_sig_test_' + uuid.uuid4().hex
        self.run_sql('CREATE DATABASE `' + self.database + '`', database=False)
        self.addCleanup(lambda: self.run_sql('DROP DATABASE `' + self.database + '`', database=False))
        self.run_sql('CREATE TABLE providerExt (provider_no VARCHAR(6), signature VARCHAR(255), '
                     'KEY idx_providerExt_provider_no(provider_no)) ENGINE=InnoDB '
                     'DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci')

    def run_sql(self, sql, *, database=True, success=True, error=STAGING_REFUSAL):
        result = subprocess.run(self.command + ([self.database] if database else []),
                                input=sql, text=True, capture_output=True, timeout=30)
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, 'The migration must refuse this data')
            self.assertIn(error, result.stderr)
        return result.stdout.strip()

    def snapshot(self):
        return self.run_sql("SELECT COALESCE(HEX(provider_no),'NULL'),COALESCE(HEX(signature),'NULL') "
                            "FROM providerExt ORDER BY BINARY provider_no,BINARY signature")

    def test_empty_table_and_future_uniqueness(self):
        self.run_sql(MIGRATION)
        self.run_sql("INSERT INTO providerExt VALUES ('T099',NULL)")
        self.run_sql("INSERT INTO providerExt VALUES ('T099',NULL)", success=False,
                     error="for key 'providerExt_provider_no_uq'")

    def test_duplicate_null_signature_is_repaired(self):
        self.run_sql("INSERT INTO providerExt VALUES ('999998',NULL),('999998',NULL)")
        self.run_sql(MIGRATION)
        self.assertEqual(self.run_sql('SELECT COUNT(*) FROM providerExt'), '1')
        self.assertEqual(self.run_sql('SELECT signature IS NULL FROM providerExt'), '1')

    def test_exact_text_duplicates_preserve_bytes_and_rerun(self):
        self.run_sql("INSERT INTO providerExt VALUES ('T099','O''Neil A&B'),('T099','O''Neil A&B'),('T100','')")
        expected = self.run_sql('SELECT DISTINCT HEX(provider_no),HEX(signature) FROM providerExt ORDER BY 1,2')
        self.run_sql(MIGRATION)
        self.assertEqual(self.snapshot(), expected)
        self.run_sql(MIGRATION)
        self.assertEqual(self.snapshot(), expected)

    def test_conflicting_case_is_not_silently_collapsed(self):
        self.run_sql("INSERT INTO providerExt VALUES ('T099','Doctor'),('T099','Doctor'),('T099','doctor')")
        before = self.snapshot()
        self.run_sql(MIGRATION, success=False)
        self.assertEqual(self.snapshot(), before)

    def test_conflicting_null_and_text_preserves_source(self):
        self.run_sql("INSERT INTO providerExt VALUES ('T099',NULL),('T099','Doctor'),('T099','Doctor')")
        before = self.snapshot()
        self.run_sql(MIGRATION, success=False)
        self.assertEqual(self.snapshot(), before)

    def test_conflicting_trailing_space_preserves_source(self):
        self.run_sql("INSERT INTO providerExt VALUES ('T099','Doctor'),('T099','Doctor'),('T099','Doctor ')")
        before = self.snapshot()
        self.run_sql(MIGRATION, success=False)
        self.assertEqual(self.snapshot(), before)

    def test_conflicting_provider_case_preserves_source(self):
        self.run_sql("INSERT INTO providerExt VALUES ('T099','Doctor'),('T099','Doctor'),('t099','Doctor')")
        before = self.snapshot()
        self.run_sql(MIGRATION, success=False)
        self.assertEqual(self.snapshot(), before)

    def test_existing_primary_key_is_preserved(self):
        self.run_sql('ALTER TABLE providerExt ADD PRIMARY KEY(provider_no)')
        self.run_sql("INSERT INTO providerExt VALUES ('T099','Doctor')")
        self.run_sql(MIGRATION)
        self.assertEqual(self.run_sql("SELECT COUNT(*) FROM information_schema.statistics WHERE "
                                     "table_schema=DATABASE() AND table_name='providerExt' AND non_unique=0"), '1')

    def test_unassigned_legacy_rows_are_not_lost(self):
        self.run_sql("INSERT INTO providerExt VALUES (NULL,'One'),(NULL,'Two')")
        before = self.snapshot()
        self.run_sql(MIGRATION)
        self.assertEqual(self.snapshot(), before)

    def test_identical_unassigned_rows_survive_assigned_deduplication(self):
        self.run_sql("INSERT INTO providerExt VALUES (NULL,'One'),(NULL,'One'),"
                     "(NULL,NULL),(NULL,NULL),('T099','Doctor'),('T099','Doctor')")
        unassigned = ("SELECT COALESCE(HEX(signature),'NULL') FROM providerExt "
                      "WHERE provider_no IS NULL ORDER BY BINARY signature")
        before = self.run_sql(unassigned)
        self.run_sql(MIGRATION)
        self.assertEqual(self.run_sql(unassigned), before)
        self.assertEqual(self.run_sql("SELECT COUNT(*) FROM providerExt WHERE provider_no='T099'"), '1')
        repaired = self.snapshot()
        self.run_sql(MIGRATION)
        self.assertEqual(self.snapshot(), repaired)

    def test_unexpected_column_is_refused_before_rows_change(self):
        # The repair rewrites rows through provider_no and signature only, so an
        # adopted table with any other column would lose that column's values.
        self.run_sql('ALTER TABLE providerExt ADD COLUMN id INT NOT NULL AUTO_INCREMENT UNIQUE, '
                     "ADD COLUMN note VARCHAR(20) DEFAULT 'default'")
        self.run_sql("INSERT INTO providerExt (provider_no,signature,note) VALUES "
                     "('T099','Doctor','kept'),('T099','Doctor','kept')")
        before = self.run_sql('SELECT id,provider_no,signature,note FROM providerExt ORDER BY id')
        self.run_sql(MIGRATION, success=False, error='providerExt_has_unexpected_columns')
        self.assertEqual(self.run_sql('SELECT id,provider_no,signature,note FROM providerExt ORDER BY id'),
                         before)

    def test_single_unexpected_column_is_refused(self):
        self.run_sql("ALTER TABLE providerExt ADD COLUMN note VARCHAR(20) DEFAULT 'default'")
        self.run_sql("INSERT INTO providerExt VALUES ('T099','Doctor','kept'),('T099','Doctor','kept')")
        before = self.run_sql('SELECT provider_no,signature,note FROM providerExt')
        self.run_sql(MIGRATION, success=False, error='providerExt_has_unexpected_columns')
        self.assertEqual(self.run_sql('SELECT provider_no,signature,note FROM providerExt'), before)

    def test_generated_invisible_primary_key_is_not_an_unexpected_column(self):
        # The shape MySQL 8.0.30+ adds under sql_generate_invisible_primary_key.
        self.run_sql('ALTER TABLE providerExt ADD COLUMN my_row_id BIGINT UNSIGNED NOT NULL '
                     'AUTO_INCREMENT INVISIBLE PRIMARY KEY FIRST')
        self.run_sql("INSERT INTO providerExt (provider_no,signature) VALUES ('T099','Doctor'),('T099','Doctor')")
        self.run_sql(MIGRATION)
        self.assertEqual(self.run_sql("SELECT COUNT(*) FROM providerExt WHERE provider_no='T099'"), '1')

    def test_visible_or_data_carrying_my_row_id_is_still_refused(self):
        self.run_sql('ALTER TABLE providerExt ADD COLUMN my_row_id INT')
        self.run_sql("INSERT INTO providerExt VALUES ('T099','Doctor',7),('T099','Doctor',8)")
        self.run_sql(MIGRATION, success=False, error='providerExt_has_unexpected_columns')

    def test_retry_succeeds_after_conflict_is_resolved(self):
        self.run_sql("INSERT INTO providerExt VALUES ('T099','Doctor'),('T099','doctor')")
        self.run_sql(MIGRATION, success=False)
        self.run_sql("DELETE FROM providerExt WHERE BINARY signature = 'doctor'")
        self.run_sql(MIGRATION)
        self.assertEqual(self.snapshot(), '54303939\t446F63746F72')
        self.assertEqual(self.run_sql(UNIQUE_PROVIDER_INDEXES), '1')

    def test_provider_trailing_space_conflict_preserves_source(self):
        # PAD SPACE collations treat 'T099' and 'T099 ' as the same key.
        self.run_sql("INSERT INTO providerExt VALUES ('T099','Doctor'),('T099 ','Doctor')")
        before = self.snapshot()
        self.run_sql(MIGRATION, success=False)
        self.assertEqual(self.snapshot(), before)

    def test_composite_or_prefix_unique_index_is_not_mistaken_for_identity(self):
        for index in ('UNIQUE KEY composite_uq (provider_no, signature)',
                      'UNIQUE KEY prefix_uq (provider_no(3))'):
            with self.subTest(index=index):
                self.run_sql('DROP TABLE providerExt')
                self.run_sql('CREATE TABLE providerExt (provider_no VARCHAR(6), signature VARCHAR(255), '
                             + index + ') DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci')
                self.run_sql("INSERT INTO providerExt VALUES ('T099','Doctor')")
                self.run_sql(MIGRATION)
                self.assertEqual(self.run_sql(UNIQUE_PROVIDER_INDEXES), '1')
                self.run_sql("INSERT INTO providerExt VALUES ('T099','Other')", success=False,
                             error="Duplicate entry")

    def test_existing_identity_index_under_another_name_is_reused(self):
        self.run_sql('ALTER TABLE providerExt ADD UNIQUE KEY legacy_provider_uq (provider_no)')
        self.run_sql("INSERT INTO providerExt VALUES ('T099','Doctor')")
        self.run_sql(MIGRATION)
        self.assertEqual(self.run_sql(UNIQUE_PROVIDER_INDEXES), '1')
        self.assertEqual(self.run_sql("SELECT COUNT(*) FROM information_schema.statistics WHERE "
                                      "table_schema=DATABASE() AND table_name='providerExt' "
                                      "AND index_name='providerExt_provider_no_uq'"), '0')


if __name__ == '__main__':
    unittest.main(verbosity=2)
