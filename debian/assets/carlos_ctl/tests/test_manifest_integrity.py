# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Integrity contract for the generated OSCAR 19 import manifests.

These tests gate what the manifest is allowed to ship: every table
consciously classified, every copy/merge column list grounded in the CARLOS
target schema, and the archive/drop policy invariants from
docs/oscar19-to-carlos-migration-plan.md §4. They read only the generated
modules — no database, no network.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import unittest

from carlos_ctl import o19map_props, o19map_schema

VALID_CLASSES = {"copy", "merge", "reference", "archive", "drop"}
VALID_DISPOSITIONS = {
    "carry", "carry-secret", "translate", "deploy-owned", "dropped-flag",
}


class TestSchemaManifest(unittest.TestCase):

    def test_every_table_classified_once_with_valid_class(self):
        for table, entry in o19map_schema.TABLES.items():
            self.assertIn(entry["class"], VALID_CLASSES,
                          "unshippable class for {}: {!r}"
                          .format(table, entry["class"]))

    def test_no_unknown_class_ships(self):
        unknown = [t for t, e in o19map_schema.TABLES.items()
                   if e["class"] == "unknown"]
        self.assertEqual(unknown, [],
                         "unclassified O19 tables — curate them in "
                         "scripts/migration/o19/overrides_schema.py")

    def test_copy_and_merge_columns_exist_in_carlos_schema(self):
        for table, entry in o19map_schema.TABLES.items():
            if entry["class"] not in ("copy", "merge"):
                continue
            carlos_cols = o19map_schema.CARLOS_COLUMNS.get(table)
            self.assertIsNotNone(
                carlos_cols, "no CARLOS_COLUMNS for {}".format(table))
            for col in entry["cols"]:
                self.assertIn(col, carlos_cols,
                              "{}.{} not a CARLOS column".format(table, col))

    def test_copy_tables_have_columns(self):
        for table, entry in o19map_schema.TABLES.items():
            if entry["class"] in ("copy", "merge"):
                self.assertTrue(entry["cols"],
                                "{} has an empty column map".format(table))

    def test_merge_tables_have_valid_merge_keys(self):
        for table, entry in o19map_schema.TABLES.items():
            if entry["class"] != "merge":
                continue
            keys = entry.get("merge_keys")
            self.assertTrue(keys, "{} merge entry lacks merge_keys"
                            .format(table))
            for k in keys:
                self.assertIn(k, o19map_schema.CARLOS_COLUMNS[table],
                              "merge key {}.{} not a CARLOS column"
                              .format(table, k))

    def test_chunk_by_is_a_copied_column(self):
        for table, entry in o19map_schema.TABLES.items():
            chunk = entry.get("chunk_by")
            if chunk is not None:
                self.assertIn(chunk, entry["cols"],
                              "chunk column {}.{} is not copied"
                              .format(table, chunk))

    def test_charset_scan_columns_are_copied(self):
        for table, entry in o19map_schema.TABLES.items():
            for col in entry.get("charset_scan", ()):
                self.assertIn(col, entry["cols"],
                              "charset_scan column {}.{} is not copied"
                              .format(table, col))

    def test_archive_patient_tables_carry_accept_class(self):
        for table, entry in o19map_schema.TABLES.items():
            if entry.get("patient_data"):
                self.assertEqual(entry["class"], "archive", table)
                self.assertEqual(entry.get("accept_class"), "archived-forms",
                                 "{} patient-data archive needs the "
                                 "archived-forms accept class".format(table))

    def test_renames_map_onto_copied_columns(self):
        for table, entry in o19map_schema.TABLES.items():
            for target in entry.get("renames", {}):
                self.assertIn(target, entry["cols"],
                              "rename target {}.{} is not copied"
                              .format(table, target))

    def test_dropped_columns_do_not_overlap_copied_sources(self):
        for table, entry in o19map_schema.TABLES.items():
            if entry["class"] not in ("copy", "merge"):
                continue
            sources = {entry.get("renames", {}).get(c, c)
                       for c in entry["cols"]}
            for col in entry.get("dropped", {}):
                self.assertNotIn(col, sources,
                                 "{}.{} is both copied and dropped"
                                 .format(table, col))

    def test_dropped_columns_have_nondefault_predicates(self):
        for table, entry in o19map_schema.TABLES.items():
            for col, d in entry.get("dropped", {}).items():
                self.assertTrue(d.get("nondefault"),
                                "{}.{} lacks a nondefault predicate"
                                .format(table, col))

    def test_seed_deletes_target_copy_tables(self):
        copyish = {t for t, e in o19map_schema.TABLES.items()
                   if e["class"] in ("copy", "merge")}
        for table, _where in o19map_schema.CARLOSDOC_SEED_DELETES:
            self.assertIn(table, copyish,
                          "seed delete targets non-copied table {}"
                          .format(table))

    def test_seed_row_counts_cover_only_copy_and_merge_tables(self):
        copyish = {t for t, e in o19map_schema.TABLES.items()
                   if e["class"] in ("copy", "merge")}
        for table in o19map_schema.SEED_ROW_COUNTS:
            self.assertIn(table, copyish, table)

    def test_core_clinical_tables_are_copied(self):
        # The heart of a clinic record must never silently fall out of the
        # manifest through a parser or curation regression.
        for table in ("demographic", "provider", "security", "appointment",
                      "casemgmt_note", "casemgmt_issue", "document", "drugs",
                      "allergies", "preventions", "measurements", "tickler",
                      "eform", "eform_data", "hl7TextMessage", "hl7TextInfo",
                      "consultationRequests", "billing_on_cheader1",
                      "billing_on_item", "dxresearch", "demographicExt"):
            self.assertIn(table, o19map_schema.TABLES, table)
            self.assertEqual(o19map_schema.TABLES[table]["class"], "copy",
                             "{} must be a straight copy".format(table))

    def test_big_tables_are_chunked(self):
        for table in ("hl7TextMessage", "document", "casemgmt_note",
                      "eform_data", "measurements"):
            self.assertTrue(o19map_schema.TABLES[table].get("chunk_by"),
                            "{} must chunk".format(table))

    def test_version_token_is_not_calver_shaped(self):
        # Release 2026.08+ trains use CalVer; the manifest token must never
        # be mistakable for a CARLOS release version.
        v = o19map_schema.SCHEMA_MAP_VERSION
        self.assertRegex(v, r"^o19map-\d+$", v)
        self.assertEqual(o19map_props.PROPS_MAP_VERSION, v)


class TestPropsManifest(unittest.TestCase):

    def test_key_dispositions_are_valid(self):
        for key, spec in o19map_props.KEYS.items():
            self.assertIn(spec["d"], VALID_DISPOSITIONS, key)

    def test_prefix_dispositions_are_valid(self):
        for prefix, spec in o19map_props.PREFIX_RULES:
            self.assertIn(spec["d"], VALID_DISPOSITIONS, prefix)

    def test_translate_keys_name_a_translator(self):
        for key, spec in o19map_props.KEYS.items():
            if spec["d"] == "translate":
                self.assertIn(spec.get("t"), ("docpath", "drugref"), key)

    def test_ldap_prefix_escalates(self):
        rules = dict(o19map_props.PREFIX_RULES)
        self.assertEqual(rules["ldap."]["advisory"], "ldap")

    def test_db_credentials_are_deploy_owned(self):
        for key in ("db_uri", "db_username", "db_password"):
            self.assertEqual(o19map_props.KEYS[key]["d"], "deploy-owned", key)

    def test_defaults_baseline_is_populated(self):
        self.assertGreater(len(o19map_props.O19_DEFAULTS), 300)
        self.assertIn("billregion", o19map_props.O19_DEFAULTS)


if __name__ == "__main__":
    unittest.main()
