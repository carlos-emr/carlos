# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""The migrated schema must hold the pinned OSCAR source capacities."""

import importlib.util
import contextlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from unittest import TestCase, mock, skipUnless
import uuid

ROOT = Path(__file__).resolve().parents[4]
spec = importlib.util.spec_from_file_location(
    "o19_capacity_generator", ROOT / "scripts/migration/o19/generate_manifests.py")
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)
audit_spec = importlib.util.spec_from_file_location(
    "o19_historical_capacity", ROOT / "scripts/migration/o19/audit_text_capacity.py")
capacity_audit = importlib.util.module_from_spec(audit_spec)
audit_spec.loader.exec_module(capacity_audit)


class TestHistoricalDeclarations(TestCase):
    def test_byte_sized_sources_are_compared_in_the_target_size_unit(self):
        """Do not narrow a VARCHAR merely because a source TEXT counts bytes."""
        for source_type, target_type, expected in (("TINYTEXT", "VARCHAR(300)", None),
                                                   ("TINYTEXT", "VARCHAR(200)", "varchar(255)"),
                                                   ("TEXT", "VARCHAR(300)", "MEDIUMTEXT"),
                                                   ("TINYBLOB", "VARBINARY(300)", None)):
            with self.subTest(source=source_type, target=target_type), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                (root / "source.sql").write_text("CREATE TABLE sample(value " + source_type + ");")
                migration = root / "database/mysql/migration/common"
                migration.mkdir(parents=True)
                (migration / "V1__base.sql").write_text("CREATE TABLE sample(value " + target_type + ");")
                tables = {"sample": {"class": "copy", "cols": ["value"]}}
                manifest = {"TABLES": tables, "PROFILES": {"bc": {"TABLES": tables}}}
                with mock.patch.object(capacity_audit.g, "o19_sql_sources", return_value=["source.sql"]):
                    rows = capacity_audit.audit(root, root, manifest)
                self.assertEqual([row["new_type"] for row in rows], [expected] if expected else [])
                if expected == "MEDIUMTEXT":
                    self.assertEqual(rows[0]["required_utf8_bytes"], 65535 * 4)

    def test_renames_keep_wider_historical_declarations(self):
        """Both table and column renames retain a width hidden by an earlier narrowing."""
        schema = capacity_audit.SourceCapacitySchema("union")
        schema.feed("CREATE TABLE previous(value VARCHAR(500)); ALTER TABLE previous MODIFY value VARCHAR(2); "
                    "ALTER TABLE previous CHANGE value renamed VARCHAR(2); RENAME TABLE previous TO current;")
        self.assertEqual(schema.tables["current"]["renamed"], "VARCHAR(2)")
        self.assertEqual(schema.largest["current"]["renamed"], "VARCHAR(500)")

    def test_missing_source_or_empty_glob_refuses_audit_before_parsing(self):
        """A partial checkout must not produce an apparently complete inventory."""
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for pattern in ("missing.sql", "updates/*.sql"):
                with self.subTest(pattern=pattern), \
                        mock.patch.object(capacity_audit.g, "o19_sql_sources", return_value=[pattern]), \
                        mock.patch.object(capacity_audit.g, "load_schema") as parse:
                    with self.assertRaises(FileNotFoundError):
                        capacity_audit.audit(root, root, {})
                    parse.assert_not_called()

    def test_fixed_width_recommendations_over_255_use_valid_variable_types(self):
        """Audit fixed targets against real wider source declarations."""
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "source.sql").write_text("CREATE TABLE sample(value VARCHAR(300), bytes VARBINARY(300));")
            migration = root / "database/mysql/migration/common"
            migration.mkdir(parents=True)
            (migration / "V1__base.sql").write_text("CREATE TABLE sample(value CHAR(1), bytes BINARY(1));")
            tables = {"sample": {"class": "copy", "cols": ["value", "bytes"]}}
            manifest = {"TABLES": tables, "PROFILES": {"bc": {"TABLES": tables}}}
            with mock.patch.object(capacity_audit.g, "o19_sql_sources", return_value=["source.sql"]):
                rows = capacity_audit.audit(root, root, manifest)
            self.assertEqual({row["column"]: row["new_type"] for row in rows},
                             {"value": "varchar(300)", "bytes": "varbinary(300)"})

    def test_overlapping_creates_preserve_the_largest_declaration(self):
        schema = capacity_audit.SourceCapacitySchema("union")
        schema.apply_create("sample", "value VARCHAR(1)")
        schema.apply_create("sample", "value VARCHAR(255)", True)
        self.assertEqual(schema.tables["sample"]["value"], "VARCHAR(1)")
        self.assertEqual(schema.largest["sample"]["value"], "VARCHAR(255)")

    def test_multiple_modifies_and_column_case_do_not_hide_a_larger_type(self):
        schema = capacity_audit.SourceCapacitySchema("union")
        schema.apply_create("Sample", "Value VARCHAR(1)")
        schema.apply_alter("Sample", "MODIFY value VARCHAR(4000), MODIFY value VARCHAR(2)")
        self.assertEqual(schema.largest["sample"]["value"], "VARCHAR(4000)")


class TestPhysicalColumnInventory(TestCase):
    def test_archive_audit_columns_have_physical_datetime_precision(self):
        """The native schema must preserve time-of-day independently of JPA DDL."""
        for province in ("on", "bc"):
            document = json.loads((ROOT / "database/mysql/schema-contract" / (province + ".json")).read_text())
            physical = {(row[0], row[1]): row[2] for row in document["schema"]["columns"]}
            for table in ("demographicArchive", "providerArchive"):
                with self.subTest(province=province, table=table):
                    self.assertEqual(physical[table, "lastUpdateDate"], "datetime")

    def test_all_named_history_fields_fit_live_capacities(self):
        """Check every shared text/binary field across native archive/history pairs."""
        for province in ("on", "bc"):
            contract = json.loads((ROOT / "database/mysql/schema-contract" / (province + ".json")).read_text())
            columns = {}
            for row in contract["schema"]["columns"]:
                columns.setdefault(row[0].lower(), {})[row[1].lower()] = row[2]
            for table, archive in columns.items():
                matched = re.fullmatch(r"(.+?)(?:_?archive|_?history|_?deleted)", table)
                if not matched or matched[1] not in columns:
                    continue
                for col, declaration in columns[matched[1]].items():
                    live_kind = declaration.lower().split("(")[0]
                    archive_kind = archive.get(col, "").lower().split("(")[0]
                    if live_kind in {"datetime", "timestamp", "time"} and archive_kind:
                        self.assertNotEqual(archive_kind, "date", table + "." + col + " would lose time-of-day")
                    live = capacity_audit.capacity(declaration)
                    history = capacity_audit.capacity(archive.get(col, ""))
                    if not live or not history or history[0] != live[0]:
                        continue
                    with self.subTest(province=province, field=table + "." + col):
                        self.assertFalse(live[2] == "varchar" and history[2] == "char",
                                         "fixed-width history would change live text padding")
                        required = live[1] * (4 if live[0] == "text" and not live[3] and history[3] else 1)
                        self.assertFalse(live[3] and not history[3], "byte-sized live text needs a byte-sized history destination")
                        self.assertGreaterEqual(history[1], required)

    def test_existing_columns_keep_attributes_and_never_lose_capacity(self):
        """Compare old and new physical contracts for unintended destructive changes."""
        contracts = ROOT / "database/mysql/schema-contract"
        for province in ("on", "bc"):
            before = json.loads((contracts / ("o19-pre-copy-" + province + ".json")).read_text())
            after = json.loads((contracts / (province + ".json")).read_text())
            columns = {tuple(row[:2]): row for row in after["schema"]["columns"]}
            for old in before["schema"]["columns"]:
                with self.subTest(province=province, table=old[0], column=old[1]):
                    new = columns[tuple(old[:2])]
                    if old[0] == "Consent" and old[1] in ("explicit", "optout", "deleted"):
                        continue  # audited, intentional flag normalization
                    # Adding a column before an existing one changes its absolute ordinal.
                    # Retain every other attribute; relative order is checked below.
                    self.assertEqual(old[:2] + old[3:-1], new[:2] + new[3:-1])
                    previous, current = capacity_audit.capacity(old[2]), capacity_audit.capacity(new[2])
                    if previous and current:
                        required = previous[1]
                        if current[3] and not previous[3] and previous[0] == "text":
                            required *= 4
                        self.assertGreaterEqual(current[1], required)
            old_by_table = {}
            for row in sorted(before["schema"]["columns"], key=lambda row: (row[0], row[-1])):
                old_by_table.setdefault(row[0], []).append(row[1])
            for table, old_columns in old_by_table.items():
                existing = set(old_columns)
                actual = [row[1] for row in sorted(after["schema"]["columns"], key=lambda row: (row[0], row[-1]))
                          if row[0] == table and row[1] in existing]
                self.assertEqual(old_columns, actual, table + ": existing physical columns were reordered")

    def test_manifest_columns_equal_independently_exported_physical_columns(self):
        """Ensure each shipped destination inventory matches physical metadata."""
        manifest = json.loads((ROOT / "debian/assets/o19-manifest/o19map_schema.json").read_text())
        for province in ("on", "bc"):
            profile = manifest if province == "on" else manifest["PROFILES"][province]
            contract = json.loads((ROOT / "database/mysql/schema-contract" / (province + ".json")).read_text())
            physical = {}
            for row in contract["schema"]["columns"]:
                physical.setdefault(row[0], set()).add(row[1])
            for table, columns in profile["CARLOS_COLUMNS"].items():
                with self.subTest(province=province, table=table):
                    self.assertEqual(set(columns), physical[table])


class TestOscarTextCapacity(TestCase):
    @classmethod
    def setUpClass(cls):
        """Reuse exported physical types instead of reparsing both migration trees."""
        cls.cases = json.loads((ROOT / "scripts/migration/o19/fixtures/schema-capacity.json").read_text())["columns"]
        cls.columns = {}
        for province in ("on", "bc"):
            contract = json.loads((ROOT / "database/mysql/schema-contract" / (province + ".json")).read_text())
            cls.columns[province] = {(row[0], row[1]): row[2] for row in contract["schema"]["columns"]}

    def test_supported_text_fields_have_the_required_capacity(self):
        """Verify every audited source limit fits its migrated physical target."""
        byte_limits = {"tinytext": 255, "text": 65535,
                       "mediumtext": 16777215, "longtext": 4294967295}
        for case in self.cases:
            for province in case["provinces"]:
                with self.subTest(province=province, table=case["table"], column=case["column"]):
                    declaration = self.columns[province][case["table"], case["column"]].lower()
                    kind = re.match(r"\w+", declaration).group()
                    if case["required_utf8_bytes"]:
                        self.assertGreaterEqual(byte_limits[kind], case["required_utf8_bytes"])
                    else:
                        expected = int(re.search(r"\((\d+)\)", case["new_type"]).group(1))
                        if kind in byte_limits:
                            # A text family upgrade must fit every four-byte UTF-8 scalar.
                            self.assertGreaterEqual(byte_limits[kind], expected * 4)
                        else:
                            size = int(re.search(r"\((\d+)\)", declaration).group(1))
                            self.assertGreaterEqual(size, expected)


@skipUnless(os.environ.get("CARLOS_TEST_O19_REFERENCE_DB")
            and os.environ.get("CARLOS_TEST_O19_REFERENCE_BC_DB")
            and os.environ.get("CARLOS_TEST_MARIADB_SOCKET"),
            "requires disposable migrated ON and BC reference databases")
class TestConsultationArchiveCapacity(TestCase):
    def test_archive_migration_preserves_existing_dates_and_future_timestamps(self):
        """Replay the forward migration over old DATE columns without losing history."""
        migration = (ROOT / "database/mysql/migration/common/V1.0.59.32__preserve_native_archive_capacity.sql").read_text()
        tables = {"consultationRequestsArchive": "consultation_history", "consultationRequestExtArchive": "extension_history",
                  "demographicArchive": "demographic_history", "providerArchive": "provider_history"}
        for original, temporary in tables.items():
            migration = migration.replace("`" + original + "`", "`" + temporary + "`")
        client = ["mariadb", "--no-defaults", "--user=root", "--batch", "--skip-column-names",
                  "--socket=" + os.environ["CARLOS_TEST_MARIADB_SOCKET"]]
        for reference in (os.environ["CARLOS_TEST_O19_REFERENCE_DB"], os.environ["CARLOS_TEST_O19_REFERENCE_BC_DB"]):
            self.assertRegex(reference, r"^[A-Za-z0-9_]+$")
            sql = "USE `{0}`; SET @before_mode=@@SESSION.sql_mode; ".format(reference)
            sql += " ".join("CREATE TEMPORARY TABLE `{0}` LIKE `{1}`;".format(temporary, original)
                            for original, temporary in tables.items())
            for temporary in ("demographic_history", "provider_history"):
                sql += ("ALTER TABLE `{0}` MODIFY lastUpdateDate DATE DEFAULT NULL; "
                        "INSERT INTO `{0}`(lastUpdateDate) VALUES('2026-01-03'); ").format(temporary)
            sql += migration + migration
            for temporary in ("demographic_history", "provider_history"):
                sql += ("INSERT INTO `{0}`(lastUpdateDate) VALUES('2026-11-01 14:32:17'); "
                        "SELECT DATE_FORMAT(lastUpdateDate,'%Y-%m-%d %H:%i:%s') FROM `{0}` ORDER BY lastUpdateDate; ").format(temporary)
            sql += "SELECT @@SESSION.sql_mode=@before_mode;"
            sql += ("INSERT INTO demographic_history(date_of_birth,month_of_birth) VALUES('1 ','2 '); "
                    "SELECT HEX(date_of_birth),HEX(month_of_birth) FROM demographic_history "
                    "WHERE date_of_birth IS NOT NULL;")
            result = subprocess.run(client, input=sql, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.strip(),
                             "2026-01-03 00:00:00\n2026-11-01 14:32:17\n" * 2 + "1\n3120\t3220")

    def test_large_imported_text_archives_without_loss_in_both_sql_modes(self):
        """Exercise the manager's verbatim live-to-history storage on both provinces."""
        fields = ("allergies", "clinicalInfo", "concurrentProblems", "currentMeds",
                  "letterheadAddress", "reason", "statusText")
        names = ",".join("`" + name + "`" for name in fields)
        client = ["mariadb", "--no-defaults", "--user=root", "--batch", "--skip-column-names",
                  "--socket=" + os.environ["CARLOS_TEST_MARIADB_SOCKET"]]
        for province, reference in (("on", os.environ["CARLOS_TEST_O19_REFERENCE_DB"]),
                                    ("bc", os.environ["CARLOS_TEST_O19_REFERENCE_BC_DB"])):
            self.assertRegex(reference, r"^[A-Za-z0-9_]+$")
            for mode in ("STRICT_ALL_TABLES,NO_AUTO_VALUE_ON_ZERO", "NO_AUTO_VALUE_ON_ZERO"):
                with self.subTest(province=province, mode=mode):
                    sql = ("USE `{0}`; SET SESSION sql_mode='{1}'; "
                           "SET @history_text=CONVERT(REPEAT(CONVERT(0xE9 USING latin1),40000) USING utf8mb4); "
                           "CREATE TEMPORARY TABLE consultation_live LIKE consultationRequests; "
                           "CREATE TEMPORARY TABLE consultation_history LIKE consultationRequestsArchive; "
                           "CREATE TEMPORARY TABLE extension_live LIKE consultationRequestExt; "
                           "CREATE TEMPORARY TABLE extension_history LIKE consultationRequestExtArchive; "
                           "INSERT INTO consultation_live(lastUpdateDate,{2}) VALUES(NOW(),{3}); "
                           "INSERT INTO consultation_history(lastUpdateDate,requestId,{2}) "
                           "SELECT lastUpdateDate,requestId,{2} FROM consultation_live; "
                           "INSERT INTO extension_live(dateCreated,name,requestId,value) "
                           "VALUES(CURDATE(),'archive-capacity',1,@history_text); "
                           "INSERT INTO extension_history(dateCreated,name,originalId,requestId,consultationRequestArchiveId,value) "
                           "SELECT dateCreated,name,id,requestId,1,value FROM extension_live; "
                           "SELECT OCTET_LENGTH(h.allergies),{4} FROM consultation_live l "
                           "JOIN consultation_history h ON h.requestId=l.requestId; "
                           "SELECT OCTET_LENGTH(h.value),BINARY h.value=BINARY l.value "
                           "FROM extension_live l JOIN extension_history h ON h.originalId=l.id;").format(
                               reference, mode, names, ",".join("@history_text" for _ in fields),
                               ",".join("BINARY h.`{0}`=BINARY l.`{0}`".format(field) for field in fields))
                    result = subprocess.run(client, input=sql, text=True, capture_output=True)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertEqual(result.stdout.strip(),
                                     "80000\t" + "\t".join("1" for _ in fields) + "\n80000\t1")


@skipUnless(os.environ.get("CARLOS_TEST_O19_REFERENCE_DB")
                     and os.environ.get("CARLOS_TEST_MARIADB_SOCKET"),
                     "requires a disposable migrated MariaDB reference database")
class TestConsentDemoSeed(TestCase):
    def test_demo_insert_omits_generated_column_and_keeps_decisions(self):
        reference = os.environ["CARLOS_TEST_O19_REFERENCE_DB"]
        self.assertRegex(reference, r"^[A-Za-z0-9_]+$")
        database = "o19_consent_demo_" + uuid.uuid4().hex
        client = ["mariadb", "--no-defaults", "--user=root", "--batch", "--skip-column-names",
                  "--socket=" + os.environ["CARLOS_TEST_MARIADB_SOCKET"]]
        snapshot = (ROOT / ".devcontainer/db/scripts/development.sql").read_text()
        insert = re.search(r"^INSERT INTO `Consent` .*;$", snapshot, re.M).group()

        def execute(sql):
            return subprocess.run(client, input=sql, text=True, capture_output=True,
                                  check=True).stdout.strip()

        execute("CREATE DATABASE `{0}`".format(database))
        try:
            execute("CREATE TABLE `{0}`.Consent LIKE `{1}`.Consent; USE `{0}`; ".format(database, reference) + insert)
            self.assertEqual(execute("SELECT COUNT(*), SUM(deleted=0), SUM(live_demographic_no IS NOT NULL) "
                                     "FROM `{0}`.Consent".format(database)), "3\t2\t2")
        finally:
            execute("DROP DATABASE `{0}`".format(database))


@skipUnless(os.environ.get("CARLOS_TEST_O19_REFERENCE_BC_DB")
                     and os.environ.get("CARLOS_TEST_MARIADB_SOCKET"),
                     "requires a disposable migrated BC reference database")
class TestBCARUrineCultureText(TestCase):
    @contextlib.contextmanager
    def disposable_database(self):
        """Use real tables so information_schema sees adopted column definitions."""
        database = "o19_bcar_definition_" + uuid.uuid4().hex
        client = ["mariadb", "--no-defaults", "--user=root", "--batch", "--skip-column-names",
                  "--socket=" + os.environ["CARLOS_TEST_MARIADB_SOCKET"]]
        subprocess.run(client, input="CREATE DATABASE `{0}` CHARACTER SET utf8mb4;".format(database),
                       text=True, capture_output=True, check=True)
        try:
            yield client + [database]
        finally:
            subprocess.run(client, input="DROP DATABASE `{0}`;".format(database),
                           text=True, capture_output=True, check=True)

    def test_an_existing_short_source_definition_is_upgraded_without_losing_values(self):
        """Widen the older physical VARCHAR(50) field and retain existing text."""
        migration = next((ROOT / "database/mysql/migration/bc").glob(
            "*__restore_bcar2007_urine_culture_text.sql")).read_text()
        sql = ("CREATE TABLE formBCAR2007(demographic_no INT, ar2_urineCStxt VARCHAR(50)); "
               "INSERT INTO formBCAR2007 VALUES(1,REPEAT('U',40)); ")
        sql += migration
        sql += ("INSERT INTO formBCAR2007 VALUES(2,REPEAT('V',255)); "
                "SELECT CHAR_LENGTH(ar2_urineCStxt) FROM formBCAR2007 ORDER BY demographic_no;")
        with self.disposable_database() as client:
            result = subprocess.run(client, input=sql, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "40\n255")

    def test_adopted_wider_types_and_column_attributes_are_preserved(self):
        """Adoption must never narrow an existing field or replace its attributes."""
        migration = next((ROOT / "database/mysql/migration/bc").glob(
            "*__restore_bcar2007_urine_culture_text.sql")).read_text()
        for declaration, expected, size in (("VARCHAR(500)", "varchar(500)", 400),
                                            ("VARCHAR(50)", "varchar(255)", 40),
                                            ("LONGTEXT", "longtext", 400)):
            with self.subTest(declaration=declaration), self.disposable_database() as client:
                sql = ("CREATE TABLE formBCAR2007(ar2_urineCStxt {0} CHARACTER SET utf8mb4 "
                       "COLLATE utf8mb4_bin NOT NULL DEFAULT 'kept' COMMENT 'adopted'); "
                       "INSERT INTO formBCAR2007 VALUES(REPEAT('U',{1})); ").format(declaration, size)
                sql += migration
                sql += ("SELECT column_type,is_nullable,column_default,collation_name,column_comment "
                        "FROM information_schema.columns WHERE table_schema=DATABASE() AND "
                        "table_name='formBCAR2007' AND column_name='ar2_urineCStxt'; "
                        "SELECT CHAR_LENGTH(ar2_urineCStxt) FROM formBCAR2007;")
                result = subprocess.run(client, input=sql, text=True, capture_output=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                # A no-op SELECT 1 is the deliberate wider-definition path.
                self.assertTrue(result.stdout.strip().endswith(
                    expected + "\tNO\t'kept'\tutf8mb4_bin\tadopted\n" + str(size)), result.stdout)

    def test_incompatible_adopted_definition_is_rejected_unchanged(self):
        """Refuse a non-text field explicitly before changing its definition or data."""
        migration = next((ROOT / "database/mysql/migration/bc").glob(
            "*__restore_bcar2007_urine_culture_text.sql")).read_text()
        with self.disposable_database() as client:
            subprocess.run(client, input="CREATE TABLE formBCAR2007(ar2_urineCStxt INT NOT NULL DEFAULT 7); "
                           "INSERT INTO formBCAR2007 VALUES(42);", text=True, capture_output=True, check=True)
            result = subprocess.run(client, input=migration, text=True, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("bcar2007_urine_culture_definition_requires_review", result.stderr)
            after = subprocess.run(client, input="SHOW COLUMNS FROM formBCAR2007; SELECT ar2_urineCStxt FROM formBCAR2007;",
                                   text=True, capture_output=True, check=True)
            self.assertIn("\tint(11)\tNO\t\t7\t", after.stdout)
            self.assertTrue(after.stdout.strip().endswith("42"))

    def test_the_native_form_field_holds_the_full_oscar_value(self):
        reference = os.environ["CARLOS_TEST_O19_REFERENCE_BC_DB"]
        self.assertRegex(reference, r"^[A-Za-z0-9_]+$")
        client = ["mariadb", "--no-defaults", "--user=root", "--batch", "--skip-column-names",
                  "--socket=" + os.environ["CARLOS_TEST_MARIADB_SOCKET"]]
        # The setup clone may issue a metadata-only row-size warning for this
        # legacy wide form. The real import must no longer need this clone to
        # add an archived twin: the source field has a native destination.
        sql = ("CREATE TEMPORARY TABLE `{0}`.bcar_capacity LIKE `{0}`.formBCAR2007; "
               "INSERT INTO `{0}`.bcar_capacity (demographic_no,ar2_urineCStxt) VALUES(1,REPEAT('U',255)); "
               "SELECT CHAR_LENGTH(ar2_urineCStxt) FROM `{0}`.bcar_capacity;").format(reference)
        result = subprocess.run(client, input=sql, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "255")
