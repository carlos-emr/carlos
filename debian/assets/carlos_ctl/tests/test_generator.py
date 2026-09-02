# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Contracts of the repo-side manifest generator that the shipped modules
depend on: Flyway version ordering, ADD COLUMN IF NOT EXISTS parsing, and
the credential-key filter that keeps stock secrets out of the manifest.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import importlib.util
import os
import unittest
from pathlib import Path

GEN = Path(__file__).resolve().parents[4] / "scripts" / "migration" / \
    "o19" / "generate_manifests.py"


def load_generator():
    spec = importlib.util.spec_from_file_location("generate_manifests", GEN)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


@unittest.skipUnless(GEN.is_file(), "generator not in this checkout")
class TestGenerator(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.gen = load_generator()

    def test_flyway_files_sort_by_numeric_version(self):
        names = ["V1.0.10__a.sql", "V1.0.2__b.sql", "V1__base.sql",
                 "V1.0.9__c.sql", "V1.0.13__d.sql"]
        ordered = sorted((Path(n) for n in names),
                         key=self.gen.flyway_version)
        self.assertEqual([p.name for p in ordered],
                         ["V1__base.sql", "V1.0.2__b.sql", "V1.0.9__c.sql",
                          "V1.0.10__a.sql", "V1.0.13__d.sql"])

    def test_migration_dirs_are_merged_in_version_order(self):
        files = self.gen.carlos_migration_files(
            [self.gen.MIGRATION_DIR / "common",
             self.gen.MIGRATION_DIR / "on"])
        versions = [self.gen.flyway_version(f) for f in files]
        self.assertEqual(versions, sorted(versions))
        self.assertTrue(files[0].name.startswith("V1__"))

    def test_add_column_if_not_exists_records_the_real_column(self):
        schema = self.gen.Schema("skip")
        schema.feed("CREATE TABLE t (id INT NOT NULL PRIMARY KEY);\n"
                    "ALTER TABLE t ADD COLUMN IF NOT EXISTS direction "
                    "VARCHAR(8) NOT NULL DEFAULT 'out';\n"
                    "ALTER TABLE t ADD IF NOT EXISTS `flag` TINYINT;\n")
        cols = schema.tables["t"]
        self.assertIn("direction", cols)
        self.assertIn("flag", cols)
        self.assertNotIn("IF", cols)
        self.assertNotIn("if", cols)

    def test_seed_counter_ignores_comments_between_tuples(self):
        text = ("INSERT INTO `t` VALUES\n(1,'a'),\n(2,'b'),\n"
                "-- a note between tuples\n(3,'c');\n")
        stripped = self.gen.strip_line_comments(text)
        self.assertEqual(self.gen.count_insert_rows(stripped), {"t": 3})

    def test_secret_key_filter(self):
        secret = ("db_password", "hcv.service.pass", "clinicaid_api_key",
                  "hcv.service.conformanceKey", "PGP_KEY", "email.password",
                  "hcv.service.user", "TOMCAT_KEYSTORE_PASSWORD")
        plain = ("password_min_length", "mandatory_password_reset",
                 "casemgmt.note.password.enabled", "email.host",
                 "billregion", "IGNORE_PASSWORD_REQUIREMENTS")
        for k in secret:
            self.assertTrue(self.gen.is_secret_key(k), k)
        for k in plain:
            self.assertFalse(self.gen.is_secret_key(k), k)

    def test_generated_modules_carry_no_wall_clock_stamp(self):
        ctl = self.gen.CTL_DIR
        for name in ("o19map_schema.py", "o19map_props.py"):
            text = (ctl / name).read_text(encoding="utf-8")
            self.assertNotIn("GENERATED_AT", text)


if __name__ == "__main__":
    unittest.main()
