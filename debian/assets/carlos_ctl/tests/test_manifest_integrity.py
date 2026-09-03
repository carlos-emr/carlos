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

from carlos_ctl import o19etl, o19map_props, o19map_schema

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

    def test_every_seeded_copy_table_reconciles_its_seeds(self):
        # a Flyway-seeded copy-class table WITHOUT seed handling collides
        # on PK the moment the clinic's rows arrive (found live in the M7
        # rehearsal on clinic_location) — every one must either replace
        # its seeds or be covered by the carlosdoc seed-delete script.
        deleted = {t for t, _ in o19map_schema.CARLOSDOC_SEED_DELETES}
        for table, n in o19map_schema.SEED_ROW_COUNTS.items():
            entry = o19map_schema.TABLES[table]
            if entry["class"] != "copy" or n == 0:
                continue
            self.assertTrue(
                entry.get("replace_seed") or table in deleted,
                "{0} is Flyway-seeded ({1} rows) but neither replace_seed "
                "nor seed-deleted — its PKs will collide".format(table, n))

    def test_seed_row_counts_cover_only_copy_and_merge_tables(self):
        copyish = {t for t, e in o19map_schema.TABLES.items()
                   if e["class"] in ("copy", "merge")}
        for table in o19map_schema.SEED_ROW_COUNTS:
            self.assertIn(table, copyish, table)

    def test_privilege_tables_are_merged_on_their_primary_key(self):
        # the role matrix: CARLOS grants win on collision, clinic-custom
        # roles / provider overrides / patient lockouts append (plan §4.5)
        priv = o19map_schema.TABLES["secObjPrivilege"]
        self.assertEqual(priv["class"], "merge")
        self.assertEqual(priv["merge_keys"], ["roleUserGroup", "objectName"])
        self.assertNotIn("surrogate_pk", priv)
        self.assertIn("merge_exclude", priv)  # the dead-object list
        obj = o19map_schema.TABLES["secObjectName"]
        self.assertEqual(obj["class"], "merge")
        self.assertEqual(obj["merge_keys"], ["objectName"])
        # secPrivilege is the token vocabulary and stays CARLOS-owned
        self.assertEqual(o19map_schema.TABLES["secPrivilege"]["class"],
                         "reference")

    def test_property_and_gender_lists_merge_with_carlos_defaults(self):
        prop = o19map_schema.TABLES["property"]
        self.assertEqual(prop["class"], "merge")
        self.assertEqual(prop["merge_keys"], ["name", "provider_no"])
        self.assertEqual(prop["surrogate_pk"], "id")
        self.assertIn("NULLIF", prop["value_exprs"]["provider_no"])
        gender = o19map_schema.TABLES["lst_gender"]
        self.assertEqual(gender["class"], "merge")
        self.assertEqual(gender["merge_keys"], ["code"])

    def test_privilege_seed_floor_reflects_later_deletions(self):
        # 514 baseline tuples + the V1.0.6 INSERT IGNORE row - the carlosdoc
        # denial V1.0.9 deletes = 514, which is what a live target holds
        self.assertEqual(o19map_schema.SEED_ROW_COUNTS["secObjPrivilege"],
                         514)
        self.assertEqual(o19map_schema.SEED_ROW_COUNTS["secObjectName"],
                         133)

    def test_insert_ignore_seeded_lookups_keep_their_floors(self):
        # V1.0.5 seeds these copy-class tables with INSERT IGNORE only; a
        # floor of 0 made P0 refuse every Flyway-built host (review round)
        expected = {"bed_type": 1, "lst_sector": 4, "lst_organization": 3,
                    "lst_discharge_reason": 3, "lst_admission_status": 2,
                    "lst_program_type": 3}
        for table, floor in expected.items():
            self.assertEqual(o19map_schema.SEED_ROW_COUNTS.get(table), floor,
                             table)
            self.assertEqual(o19map_schema.TABLES[table]["class"], "copy")

    def test_appended_row_keys_are_raw_copied_columns(self):
        # row parity joins the appended-row keys raw; a charset repair or
        # value_expr on a key column would break the twin join silently
        for table, keys in o19etl.APPENDED_ROW_KEYS.items():
            entry = o19map_schema.TABLES[table]
            self.assertEqual(entry["class"], "copy", table)
            for k in keys:
                self.assertIn(k, entry["cols"], table)
                self.assertNotIn(k, entry.get("value_exprs", {}), table)
                self.assertNotIn(k, entry.get("charset_scan", []), table)

    def test_merge_exclusions_name_dead_objects_only(self):
        # every excluded object is one no CARLOS code checks and no CARLOS
        # seed grants (the `_pmm%` pattern of the first cut caught live
        # objects); the list is explicit, never a wildcard
        exclude = o19map_schema.TABLES["secObjPrivilege"]["merge_exclude"]
        self.assertNotRegex(exclude, r"(?i)\b(?:LIKE|REGEXP|RLIKE)\b")
        self.assertNotIn("%", exclude)
        # exactly the dead objects, nothing broader
        import re
        names = sorted(re.findall(r"'([^']*)'", exclude))
        self.assertEqual(names, sorted([
            "_admin.traceability", "_newCasemgmt.clearTempNotes",
            "_caisi.documentationWarning", "_caisi.documentationWarning ",
            "_pmm.editProgram.schedules", "_pmm.functionalCentre"]))
        self.assertIn("'_admin.traceability'", exclude)
        self.assertNotIn("'_admin.pmm'", exclude)  # seeded by CARLOS

    def test_prevention_map_case_collisions_are_the_known_ones(self):
        # 'dTaP'/'dTap' -> 'Tdap' collide case-insensitively with the valid
        # pediatric code 'DTaP'; the importer and preflight compare BINARY,
        # so the collision is harmless — pin it so a new one is noticed
        known = {k.casefold(): k for k in o19map_schema.KNOWN_PREVENTION_TYPES}
        case_only = sorted(k for k in o19map_schema.PREVENTION_TYPE_MAP
                           if k.casefold() in known and known[k.casefold()]
                           != k)
        self.assertEqual(case_only, ["dTaP", "dTap"])
        self.assertEqual(o19map_schema.PREVENTION_TYPE_MAP["Flu"], "Inf")

    def test_startup_created_rows_name_copy_tables(self):
        # the seed script deletes them before the id-intact copy; a merge
        # table could not be cleared that way
        tables = [t for t, _ in o19map_schema.STARTUP_CREATED_ROWS]
        self.assertEqual(tables, ["site", "providersite",
                                  "program_provider", "program"])
        for table, where in o19map_schema.STARTUP_CREATED_ROWS:
            self.assertEqual(o19map_schema.TABLES[table]["class"], "copy",
                             table)
            self.assertTrue(where.strip(), table)

    def test_role_and_prevention_constants_are_populated(self):
        self.assertIn("doctor", o19map_schema.STOCK_ROLE_NAMES)
        self.assertIn("admin", o19map_schema.STOCK_ROLE_NAMES)
        self.assertIn("HRMAdmin", o19map_schema.STOCK_ROLE_NAMES)
        self.assertEqual(o19map_schema.STOCK_ROLE_NAMES,
                         sorted(set(o19map_schema.STOCK_ROLE_NAMES)))
        self.assertEqual(o19map_schema.PREVENTION_TYPE_MAP["Flu"], "Inf")
        for canonical in set(o19map_schema.PREVENTION_TYPE_MAP.values()):
            self.assertIn(canonical, o19map_schema.KNOWN_PREVENTION_TYPES,
                          "map targets a code PreventionItems.xml lacks")
        # the operator docs quote this value
        self.assertEqual(o19map_schema.ROLE_TEMPLATE_MIN_JACCARD, 0.3)

    def test_value_expr_targets_are_copied_columns(self):
        # a synthesized column that is not in `cols` is silently never
        # written (found in M8 on pharmacyInfo.uid)
        for table, entry in o19map_schema.TABLES.items():
            for col in entry.get("value_exprs", {}):
                self.assertIn(col, entry["cols"],
                              "{0}.{1} has a value_exprs entry but is not "
                              "copied".format(table, col))

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

    def test_manifest_identifiers_are_plain_word_characters(self):
        # the manifest's own names are interpolated as constants; this
        # pins that none of them would ever need quoting beyond backticks
        from carlos_ctl import o19etl
        for table, entry in o19map_schema.TABLES.items():
            self.assertTrue(o19etl.IDENTIFIER_RE.match(table), table)
            names = list(entry.get("cols", [])) + list(
                entry.get("dropped", {})) + list(
                entry.get("renames", {}).values()) + list(
                entry.get("merge_keys", []))
            for name in names:
                self.assertTrue(o19etl.IDENTIFIER_RE.match(name),
                                "{0}.{1}".format(table, name))

    def test_version_token_is_not_calver_shaped(self):
        # Release 2026.08+ trains use CalVer; the manifest token must never
        # be mistakable for a CARLOS release version.
        v = o19map_schema.SCHEMA_MAP_VERSION
        p = o19map_props.PROPS_MAP_VERSION
        self.assertRegex(v, r"^o19map-\d+\+[0-9a-f]{8}$", v)
        self.assertRegex(p, r"^o19map-\d+\+[0-9a-f]{8}$", p)
        # same hand-maintained base; the suffix is derived from each
        # module's own content, so a classification change the author
        # forgot to bump still invalidates a --resume across it
        self.assertEqual(v.split("+")[0], p.split("+")[0])

    def test_the_version_suffix_tracks_the_classification(self):
        import hashlib
        digest = hashlib.sha256(
            repr(sorted(o19map_schema.TABLES.items()))
            .encode("utf-8")).hexdigest()[:8]
        self.assertEqual(
            o19map_schema.SCHEMA_MAP_VERSION.split("+", 1)[1], digest,
            "regenerate the manifests: the shipped token does not "
            "describe the shipped TABLES")


class TestDroppedPrefixesTrackTheFileRules(unittest.TestCase):
    """The same prefix list prunes the clinic's `property` TABLE and
    reports dropped keys from oscar.properties. Maintained separately it
    drifts into a contradiction: keys dropped from the file while the
    matching rows survive in the table."""

    def test_every_dropped_flag_prefix_is_embedded(self):
        from carlos_ctl import o19_preflight
        derived = sorted(
            p for p, spec in o19map_props.PREFIX_RULES
            if spec.get("d") == "dropped-flag")
        self.assertEqual(sorted(o19_preflight.DROPPED_PROP_PREFIXES),
                         derived)

    def test_every_dropped_flag_key_is_embedded(self):
        # a key classified by NAME rather than by prefix has the same
        # problem one level down: dropped from oscar.properties while
        # its `property` table row survives and CARLOS reads it back
        from carlos_ctl import o19_preflight
        derived = sorted(k for k, spec in o19map_props.KEYS.items()
                         if spec.get("d") == "dropped-flag")
        self.assertEqual(sorted(o19_preflight.DROPPED_PROP_KEYS), derived)
        self.assertTrue(derived)


class TestPreflightDriftLock(unittest.TestCase):
    """The data embedded in o19_preflight.py must be exactly derivable from
    o19map_schema — the two ship together and must never drift."""

    def test_embedded_data_matches_schema_manifest(self):
        from carlos_ctl import o19_preflight as pf
        self.assertEqual(pf.SCHEMA_MAP_VERSION,
                         o19map_schema.SCHEMA_MAP_VERSION)
        self.assertEqual(sorted(pf.CREDENTIAL_TABLES),
                         sorted(o19map_schema.CREDENTIAL_TABLES))
        self.assertEqual(
            pf.PATIENT_DATA_TABLES,
            sorted(t for t, e in o19map_schema.TABLES.items()
                   if e.get("patient_data")))
        self.assertEqual(pf.KNOWN_TABLES,
                         {t: e["class"]
                          for t, e in o19map_schema.TABLES.items()})
        b3 = {}
        for t, e in o19map_schema.TABLES.items():
            for col, d in e.get("dropped", {}).items():
                if d.get("b3"):
                    b3.setdefault(t, {})[col] = \
                        d["nondefault"].replace("s.`", "`")
        self.assertEqual(pf.B3_FLAGGED_COLUMNS, b3)
        self.assertEqual(
            pf.CHARSET_SCAN,
            {t: e["charset_scan"] for t, e in o19map_schema.TABLES.items()
             if e.get("charset_scan")})
        self.assertEqual(pf.STOCK_ROLE_NAMES, o19map_schema.STOCK_ROLE_NAMES)
        self.assertEqual(pf.LEGACY_PREVENTION_TYPES,
                         sorted(o19map_schema.PREVENTION_TYPE_MAP))


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
