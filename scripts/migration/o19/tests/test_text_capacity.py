# SPDX-License-Identifier: GPL-2.0-or-later
"""The migrated schema must hold the pinned OSCAR source capacities."""

import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import unittest
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


class TestHistoricalDeclarations(unittest.TestCase):
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


class TestPhysicalColumnInventory(unittest.TestCase):
    def test_manifest_columns_equal_independently_exported_physical_columns(self):
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


class TestOscarTextCapacity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cases = json.loads((ROOT / "scripts/migration/o19/fixtures/schema-capacity.json").read_text())["columns"]
        cls.schemas = {p: generator.load_schema(generator.carlos_schema_files(p))
                       for p in ("on", "bc")}

    def test_supported_text_fields_have_the_required_capacity(self):
        byte_limits = {"tinytext": 255, "text": 65535,
                       "mediumtext": 16777215, "longtext": 4294967295}
        for case in self.cases:
            for province in case["provinces"]:
                with self.subTest(province=province, table=case["table"], column=case["column"]):
                    declaration = self.schemas[province].tables[case["table"]][case["column"]].lower()
                    kind = re.match(r"\w+", declaration).group()
                    if case["required_utf8_bytes"]:
                        self.assertGreaterEqual(byte_limits[kind], case["required_utf8_bytes"])
                    else:
                        size = int(re.search(r"\((\d+)\)", declaration).group(1))
                        expected = int(re.search(r"\((\d+)\)", case["new_type"]).group(1))
                        self.assertGreaterEqual(size, expected)


@unittest.skipUnless(os.environ.get("CARLOS_TEST_O19_REFERENCE_DB")
                     and os.environ.get("CARLOS_TEST_MARIADB_SOCKET"),
                     "requires a disposable migrated MariaDB reference database")
class TestConsentDemoSeed(unittest.TestCase):
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


@unittest.skipUnless(os.environ.get("CARLOS_TEST_O19_REFERENCE_BC_DB")
                     and os.environ.get("CARLOS_TEST_MARIADB_SOCKET"),
                     "requires a disposable migrated BC reference database")
class TestBCARUrineCultureText(unittest.TestCase):
    def test_an_existing_short_source_definition_is_upgraded_without_losing_values(self):
        reference = os.environ["CARLOS_TEST_O19_REFERENCE_BC_DB"]
        self.assertRegex(reference, r"^[A-Za-z0-9_]+$")
        client = ["mariadb", "--no-defaults", "--user=root", "--batch", "--skip-column-names",
                  "--socket=" + os.environ["CARLOS_TEST_MARIADB_SOCKET"]]
        migration = next((ROOT / "database/mysql/migration/bc").glob(
            "*__restore_bcar2007_urine_culture_text.sql")).read_text().replace("`formBCAR2007`", "`bcar_short_definition`")
        sql = ("USE `{0}`; CREATE TEMPORARY TABLE bcar_short_definition LIKE `{0}`.formBCAR2007; "
               "ALTER TABLE bcar_short_definition MODIFY ar2_urineCStxt VARCHAR(50); "
               "INSERT INTO bcar_short_definition (demographic_no,ar2_urineCStxt) VALUES(1,REPEAT('U',40)); ").format(reference)
        sql += migration
        sql += ("INSERT INTO bcar_short_definition (demographic_no,ar2_urineCStxt) VALUES(2,REPEAT('V',255)); "
                "SELECT CHAR_LENGTH(ar2_urineCStxt) FROM bcar_short_definition ORDER BY demographic_no;")
        result = subprocess.run(client, input=sql, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "40\n255")

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
