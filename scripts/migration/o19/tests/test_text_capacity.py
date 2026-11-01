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
