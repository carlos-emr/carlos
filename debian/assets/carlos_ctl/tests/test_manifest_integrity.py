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

import importlib.util
import re
import unittest
from pathlib import Path

from carlos_ctl import o19etl, o19map_props, o19map_schema


def load_generator():
    """The repo-side generator, for the overlay rulings it resolves --
    province scoping lives there, not in the overlay file."""
    path = (Path(__file__).resolve().parents[4] / "scripts" / "migration"
            / "o19" / "generate_manifests.py")
    spec = importlib.util.spec_from_file_location("generate_manifests",
                                                  path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def profile_data(province=None):
    """The manifest's per-province names for `province`, or the
    module-level default when None.

    Read WITHOUT calling bind(): binding mutates module globals, and a
    test that leaves the manifest on another province leaks into every
    test that runs after it (which is exactly how the first cut of these
    tests failed)."""
    default = o19map_schema._DEFAULT_PROFILE
    if province is None or province == default["O19_PROFILE"]:
        return default
    return o19map_schema.PROFILES[province]


VALID_CLASSES = {"copy", "merge", "reference", "archive", "drop"}
VALID_DISPOSITIONS = {
    "carry", "carry-secret", "translate", "deploy-owned", "dropped-flag",
}


class TestSchemaManifest(unittest.TestCase):

    """The shipped schema manifest, checked as data.

    Every table classified exactly once into a known class, every
    column mapping resolvable on both sides, and every curated overlay
    entry still describing the diff it was written for. A manifest that
    fails here would misroute a clinic's rows with no runtime error.

    Run once per SHIPPED PROFILE (see the subclasses at the end of the
    module). One package carries a profile per province and binds the
    host's at run time, so a check that only ever read the default
    profile would leave every other province's rulings unexamined --
    present in the package, ungated by anything but the province gate,
    and wrong the day the gate lifts. The province-specific content
    lives in TestTheOntarioProfile below; everything here is an
    invariant every profile owes."""

    #: None means the module-level (default) profile
    PROVINCE = None

    @classmethod
    def setUpClass(cls):
        data = profile_data(cls.PROVINCE)
        cls.province = data["O19_PROFILE"]
        cls.tables = data["TABLES"]
        cls.carlos_columns = data["CARLOS_COLUMNS"]
        cls.seed_row_counts = data["SEED_ROW_COUNTS"]
        cls.schema_map_version = data["SCHEMA_MAP_VERSION"]
        cls.stock_role_names = data["STOCK_ROLE_NAMES"]

    def test_every_table_classified_once_with_valid_class(self):
        for table, entry in self.tables.items():
            self.assertIn(entry["class"], VALID_CLASSES,
                          "unshippable class for {}: {!r}"
                          .format(table, entry["class"]))

    def test_no_unknown_class_ships(self):
        unknown = [t for t, e in self.tables.items()
                   if e["class"] == "unknown"]
        self.assertEqual(unknown, [],
                         "unclassified O19 tables — curate them in "
                         "scripts/migration/o19/overrides_schema.py")

    def test_copy_and_merge_columns_exist_in_carlos_schema(self):
        for table, entry in self.tables.items():
            if entry["class"] not in ("copy", "merge"):
                continue
            carlos_cols = self.carlos_columns.get(table)
            self.assertIsNotNone(
                carlos_cols, "no CARLOS_COLUMNS for {}".format(table))
            for col in entry["cols"]:
                self.assertIn(col, carlos_cols,
                              "{}.{} not a CARLOS column".format(table, col))

    def test_copy_tables_have_columns(self):
        for table, entry in self.tables.items():
            if entry["class"] in ("copy", "merge"):
                self.assertTrue(entry["cols"],
                                "{} has an empty column map".format(table))

    def test_merge_tables_have_valid_merge_keys(self):
        for table, entry in self.tables.items():
            if entry["class"] != "merge":
                continue
            keys = entry.get("merge_keys")
            self.assertTrue(keys, "{} merge entry lacks merge_keys"
                            .format(table))
            for k in keys:
                self.assertIn(k, self.carlos_columns[table],
                              "merge key {}.{} not a CARLOS column"
                              .format(table, k))

    def test_chunk_by_is_a_copied_column(self):
        for table, entry in self.tables.items():
            chunk = entry.get("chunk_by")
            if chunk is not None:
                self.assertIn(chunk, entry["cols"],
                              "chunk column {}.{} is not copied"
                              .format(table, chunk))

    def test_charset_scan_columns_are_copied(self):
        for table, entry in self.tables.items():
            for col in entry.get("charset_scan", ()):
                self.assertIn(col, entry["cols"],
                              "charset_scan column {}.{} is not copied"
                              .format(table, col))

    def test_archive_patient_tables_carry_accept_class(self):
        for table, entry in self.tables.items():
            if entry.get("patient_data"):
                self.assertEqual(entry["class"], "archive", table)
                self.assertEqual(entry.get("accept_class"), "archived-forms",
                                 "{} patient-data archive needs the "
                                 "archived-forms accept class".format(table))

    def test_renames_map_onto_copied_columns(self):
        for table, entry in self.tables.items():
            for target in entry.get("renames", {}):
                self.assertIn(target, entry["cols"],
                              "rename target {}.{} is not copied"
                              .format(table, target))

    def test_dropped_columns_do_not_overlap_copied_sources(self):
        for table, entry in self.tables.items():
            if entry["class"] not in ("copy", "merge"):
                continue
            sources = {entry.get("renames", {}).get(c, c)
                       for c in entry["cols"]}
            for col in entry.get("dropped", {}):
                self.assertNotIn(col, sources,
                                 "{}.{} is both copied and dropped"
                                 .format(table, col))

    def test_dropped_columns_have_nondefault_predicates(self):
        for table, entry in self.tables.items():
            for col, d in entry.get("dropped", {}).items():
                self.assertTrue(d.get("nondefault"),
                                "{}.{} lacks a nondefault predicate"
                                .format(table, col))

    def test_seed_deletes_target_copy_tables(self):
        copyish = {t for t, e in self.tables.items()
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
        # collected, not asserted one at a time: the BC pass found four
        # of these and a fail-on-first test reported them one commit at
        # a time
        unreconciled = sorted(
            "{0} ({1} rows)".format(table, n)
            for table, n in self.seed_row_counts.items()
            for entry in [self.tables[table]]
            if entry["class"] == "copy" and n
            and not (entry.get("replace_seed") or table in deleted))
        self.assertEqual(
            unreconciled, [],
            "Flyway-seeded copy tables with neither replace_seed nor a "
            "seed delete in the {0!r} profile — their PKs will collide"
            .format(self.province))

    def test_seed_row_counts_cover_only_copy_and_merge_tables(self):
        copyish = {t for t, e in self.tables.items()
                   if e["class"] in ("copy", "merge")}
        for table in self.seed_row_counts:
            self.assertIn(table, copyish, table)

    def test_privilege_tables_are_merged_on_their_primary_key(self):
        # the role matrix: CARLOS grants win on collision, clinic-custom
        # roles / provider overrides / patient lockouts append (plan §4.5)
        priv = self.tables["secObjPrivilege"]
        self.assertEqual(priv["class"], "merge")
        self.assertEqual(priv["merge_keys"], ["roleUserGroup", "objectName"])
        self.assertNotIn("surrogate_pk", priv)
        self.assertIn("merge_exclude", priv)  # the dead-object list
        obj = self.tables["secObjectName"]
        self.assertEqual(obj["class"], "merge")
        self.assertEqual(obj["merge_keys"], ["objectName"])
        # secPrivilege is the token vocabulary and stays CARLOS-owned
        self.assertEqual(self.tables["secPrivilege"]["class"],
                         "reference")

    def test_property_and_gender_lists_merge_with_carlos_defaults(self):
        prop = self.tables["property"]
        self.assertEqual(prop["class"], "merge")
        self.assertEqual(prop["merge_keys"], ["name", "provider_no"])
        self.assertEqual(prop["surrogate_pk"], "id")
        self.assertIn("NULLIF", prop["value_exprs"]["provider_no"])
        gender = self.tables["lst_gender"]
        self.assertEqual(gender["class"], "merge")
        self.assertEqual(gender["merge_keys"], ["code"])

    def test_insert_ignore_seeded_lookups_keep_their_floors(self):
        # V1.0.5 seeds these copy-class tables with INSERT IGNORE only; a
        # floor of 0 made P0 refuse every Flyway-built host (review round)
        expected = {"bed_type": 1, "lst_sector": 4, "lst_organization": 3,
                    "lst_discharge_reason": 3, "lst_admission_status": 2,
                    "lst_program_type": 3}
        for table, floor in expected.items():
            self.assertEqual(self.seed_row_counts.get(table), floor,
                             table)
            self.assertEqual(self.tables[table]["class"], "copy")

    def test_appended_row_keys_are_raw_copied_columns(self):
        # row parity joins the appended-row keys raw; a charset repair or
        # value_expr on a key column would break the twin join silently
        for table, keys in o19etl.APPENDED_ROW_KEYS.items():
            entry = self.tables[table]
            self.assertEqual(entry["class"], "copy", table)
            for k in keys:
                self.assertIn(k, entry["cols"], table)
                self.assertNotIn(k, entry.get("value_exprs", {}), table)
                self.assertNotIn(k, entry.get("charset_scan", []), table)

    def test_merge_exclusions_name_dead_objects_only(self):
        # every excluded object is one no CARLOS code checks and no CARLOS
        # seed grants (the `_pmm%` pattern of the first cut caught live
        # objects); the list is explicit, never a wildcard
        exclude = self.tables["secObjPrivilege"]["merge_exclude"]
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
            self.assertEqual(self.tables[table]["class"], "copy",
                             table)
            self.assertTrue(where.strip(), table)

    def test_role_and_prevention_constants_are_populated(self):
        self.assertIn("doctor", self.stock_role_names)
        self.assertIn("admin", self.stock_role_names)
        self.assertIn("HRMAdmin", self.stock_role_names)
        self.assertEqual(self.stock_role_names,
                         sorted(set(self.stock_role_names)))
        self.assertEqual(o19map_schema.PREVENTION_TYPE_MAP["Flu"], "Inf")
        for canonical in set(o19map_schema.PREVENTION_TYPE_MAP.values()):
            self.assertIn(canonical, o19map_schema.KNOWN_PREVENTION_TYPES,
                          "map targets a code PreventionItems.xml lacks")
        # the operator docs quote this value
        self.assertEqual(o19map_schema.ROLE_TEMPLATE_MIN_JACCARD, 0.3)

    def test_value_expr_targets_are_copied_columns(self):
        # a synthesized column that is not in `cols` is silently never
        # written (found in M8 on pharmacyInfo.uid)
        for table, entry in self.tables.items():
            for col in entry.get("value_exprs", {}):
                self.assertIn(col, entry["cols"],
                              "{0}.{1} has a value_exprs entry but is not "
                              "copied".format(table, col))

    def test_core_clinical_tables_are_copied(self):
        # The heart of a clinic record must never silently fall out of the
        # manifest through a parser or curation regression. These live in
        # the province-neutral schema, so every profile owes them; the
        # provincial billing stacks are checked per profile below.
        for table in ("demographic", "provider", "security", "appointment",
                      "casemgmt_note", "casemgmt_issue", "document", "drugs",
                      "allergies", "preventions", "measurements", "tickler",
                      "eform", "eform_data", "hl7TextMessage", "hl7TextInfo",
                      "consultationRequests", "dxresearch", "demographicExt"):
            self.assertIn(table, self.tables, table)
            self.assertEqual(self.tables[table]["class"], "copy",
                             "{} must be a straight copy".format(table))

    def test_big_tables_are_chunked(self):
        for table in ("hl7TextMessage", "document", "casemgmt_note",
                      "eform_data", "measurements"):
            self.assertTrue(self.tables[table].get("chunk_by"),
                            "{} must chunk".format(table))

    def test_manifest_identifiers_are_plain_word_characters(self):
        # the manifest's own names are interpolated as constants; this
        # pins that none of them would ever need quoting beyond backticks
        from carlos_ctl import o19etl
        for table, entry in self.tables.items():
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
        v = self.schema_map_version
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
            repr(sorted(self.tables.items()))
            .encode("utf-8")).hexdigest()[:8]
        self.assertEqual(
            self.schema_map_version.split("+", 1)[1], digest,
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

    """The shipped properties manifest, checked as data."""
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

    #: Keys config.py writes where the deployment's value is only a
    #: DEFAULT and the clinic's own (translated) value legitimately wins.
    #: Every one is a document path: `translate_docpath` keeps the
    #: clinic's own subdirectory names under the CARLOS document root,
    #: and that is where the documents phase actually restored the files
    #: -- the deb's generic default would point at an empty directory.
    #: Anything NOT listed here that config.py writes must be
    #: deploy-owned.
    DEPLOY_DEFAULTS_THE_CLINIC_REFINES = {
        "INCOMINGDOCUMENT_DIR", "INVOICE_DIR", "drugref_url",
        "ONEDT_INBOX", "ONEDT_OUTBOX", "ONEDT_SENT", "ONEDT_ARCHIVE",
    }

    def test_every_key_the_deployment_writes_is_ruled(self):
        """config.py and the props overlay must agree on who owns a key.

        A key the deb provisions and the overlay also carries is a
        contradiction the fragment resolves the wrong way round: the
        fragment is applied AFTER carlos.properties, so the clinic's old
        value silently overrides the deployment's. `billregion` was
        exactly that -- invisible while only Ontario shipped, and on a BC
        host a clinic file still saying ON would have put the entire
        billing UI on the wrong province while the schema, the Flyway
        set and the manifest profile all stayed on BC."""
        config = (Path(__file__).resolve().parents[1]
                  / "config.py").read_text(encoding="utf-8")
        written = sorted(set(re.findall(
            r'prop_set\(PROPERTIES,\s*"([^"]+)"', config)))
        self.assertGreater(len(written), 15, "config.py parse found "
                                             "almost nothing")
        wrong = sorted(
            "{0}: {1}".format(key, o19map_props.KEYS.get(key, {}).get("d"))
            for key in written
            if key not in self.DEPLOY_DEFAULTS_THE_CLINIC_REFINES
            and o19map_props.KEYS.get(key, {}).get("d") != "deploy-owned")
        self.assertEqual(
            wrong, [],
            "config.py provisions these but the props overlay does not "
            "rule them deploy-owned (or list them as a default the "
            "clinic's value refines)")

    def test_the_refinement_list_names_only_translated_keys(self):
        # the exemption is "the clinic's translated value is the right
        # one", so an entry that is not translated has no business here
        for key in self.DEPLOY_DEFAULTS_THE_CLINIC_REFINES:
            self.assertEqual(o19map_props.KEYS[key]["d"], "translate", key)

    def test_the_province_is_the_hosts_to_decide(self):
        self.assertEqual(o19map_props.KEYS["billregion"]["d"],
                         "deploy-owned")


OVERRIDES = Path(__file__).resolve().parents[4] / "scripts" / "migration" / \
    "o19" / "overrides_schema.py"


def load_overrides():
    """The curated overlay, loaded from the repo checkout.

    The rename rulings live there rather than in the shipped manifest, so
    these tests need both halves. The generator enforces the same contract
    at emission time -- but it needs an OSCAR 19 checkout to run, and CI
    has none, so this is the copy that actually guards a pull request.
    """
    spec = importlib.util.spec_from_file_location(
        "overrides_schema", OVERRIDES)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def co_occurrences():
    """(table, o19_column, [unfilled CARLOS columns]) for every table with
    BOTH an unmatched O19 column and a CARLOS column the copy never writes.

    That pair is the signature of a rename the name-matching missed: the
    O19 column falls into `dropped` and the CARLOS column takes its
    default, and each half looks deliberate on its own.
    """
    out = []
    for table, entry in sorted(o19map_schema.TABLES.items()):
        dropped = entry.get("dropped") or {}
        if not dropped:
            continue
        mapped = set(entry.get("cols") or ())
        unfilled = [c for c in o19map_schema.CARLOS_COLUMNS.get(table, ())
                    if c not in mapped]
        if not unfilled:
            continue
        for col in sorted(dropped):
            out.append((table, col, unfilled))
    return out


@unittest.skipUnless(OVERRIDES.is_file(), "overlay not in this checkout")
class TestRenameRulings(unittest.TestCase):
    """No column may be dropped opposite an unwritten CARLOS column
    without a human having said which it is."""

    @classmethod
    def setUpClass(cls):
        cls.ov = load_overrides()

    def test_every_co_occurrence_is_ruled(self):
        renames = getattr(self.ov, "RENAMES", {})
        not_renames = getattr(self.ov, "NOT_RENAMES", {})
        unruled = [
            "{0}.{1} (opposite {0}.{{{2}}})".format(t, c, ", ".join(u))
            for t, c, u in co_occurrences()
            if (t, c) not in not_renames
            and c not in renames.get(t, {}).values()]
        self.assertEqual(
            unruled, [],
            "a dropped O19 column sitting opposite an unwritten CARLOS "
            "column is how a rename hides -- each half reads as "
            "deliberate alone. Rule each one in overrides_schema.py, as "
            "RENAMES[table][carlos_col] = o19_col or as a NOT_RENAMES "
            "entry with a reason.")

    def test_no_ruling_names_a_column_that_is_not_dropped(self):
        # a stale ruling silently re-permits the very thing it was added
        # to rule on, so it is an error rather than dead weight
        stale = [
            "{0}.{1}".format(t, c)
            for (t, c) in getattr(self.ov, "NOT_RENAMES", {})
            if c not in (o19map_schema.TABLES.get(t, {}).get("dropped") or {})]
        self.assertEqual(stale, [], "NOT_RENAMES entries no longer describe "
                                    "a dropped column of a shared table")

    def test_every_ruling_carries_a_reason(self):
        blank = [k for k, v in getattr(self.ov, "NOT_RENAMES", {}).items()
                 if not (v[0] or "").strip()]
        self.assertEqual(blank, [],
                         "a ruling without a reason is not a ruling")

    def test_every_ruling_still_covers_the_columns_it_faces(self):
        """A ruling is about a PAIR, and CARLOS is the side still moving.
        Keyed by the O19 column alone, a Flyway migration that adds a
        column to an already-ruled table hands the new column the old
        ruling -- the clinic's data then lands only in the shadow copy
        while the live CARLOS column keeps its default, which is the
        rename this namespace exists to catch."""
        facing = dict(((t, c), u) for t, c, u in co_occurrences())
        stale = []
        for key, value in sorted(
                getattr(self.ov, "NOT_RENAMES", {}).items()):
            if key not in facing:
                continue        # ruled but no longer a co-occurrence
            covered, unfilled = sorted(value[1]), sorted(facing[key])
            if covered != unfilled:
                stale.append("{0}.{1}: covers {2}, now faces {3}".format(
                    key[0], key[1], covered, unfilled))
        self.assertEqual(
            stale, [],
            "a NOT_RENAMES ruling no longer describes the columns it "
            "faces. Re-read the pair and re-rule it in "
            "overrides_schema.py, listing the CARLOS columns it covers.")

    def test_every_table_ruling_names_one_side_of_the_diff(self):
        # the O19 side must be a table the manifest carries; the CARLOS
        # side must NOT be, since a shared table is not a rename candidate
        bad = []
        for pair, reason in getattr(
                self.ov, "NOT_RENAMED_TABLES", {}).items():
            o19_table, carlos_table = pair
            if o19_table not in o19map_schema.TABLES:
                bad.append("{}: O19 side is not in the manifest".format(pair))
            if carlos_table in o19map_schema.TABLES:
                bad.append("{}: CARLOS side is a shared table".format(pair))
            if not (reason or "").strip():
                bad.append("{}: no reason".format(pair))
        self.assertEqual(bad, [])

    def test_a_rename_names_real_columns_on_both_sides(self):
        bad = []
        for table, pairs in getattr(self.ov, "RENAMES", {}).items():
            entry = o19map_schema.TABLES.get(table, {})
            carlos_cols = set(o19map_schema.CARLOS_COLUMNS.get(table, ()))
            dropped = set(entry.get("dropped") or {})
            mapped = {entry.get("renames", {}).get(c, c)
                      for c in (entry.get("cols") or ())}
            for target, source in pairs.items():
                if target not in carlos_cols:
                    bad.append("{0}.{1} is not a CARLOS column".format(
                        table, target))
                if source not in dropped and source not in mapped:
                    bad.append("{0}.{1} is not an O19 column".format(
                        table, source))
        self.assertEqual(bad, [])


@unittest.skipUnless(OVERRIDES.is_file(), "overlay not in this checkout")
class TestOverlayRulingsReachTheManifest(unittest.TestCase):
    """The shipped manifest must still say what the overlay rules.

    The overlay is the human-edited half and the manifest is the
    generated half, but only the manifest ships in the package -- the
    ETL never reads overrides_schema.py. Regeneration needs an OSCAR 19
    checkout, which CI does not have, so an overlay edit committed
    without a regenerated manifest is inert: the reviewer reads the
    ruling, the import ignores it, and --check is never run to notice.
    These tests are that check, in the one place that runs on every PR.

    Both directions matter. Overlay-to-manifest catches a ruling that
    never landed; manifest-to-overlay catches a ruling that was deleted
    (or moved between buckets) while the manifest kept the old answer.
    An overlay name the manifest does not carry at all is skipped, not
    failed: a bucket may legitimately name a table this patch level of
    OSCAR 19 does not have -- the generator warns about those itself.
    """

    #: None means the module-level (default) profile
    PROVINCE = None

    @classmethod
    def setUpClass(cls):
        cls.ov = load_overrides()
        data = profile_data(cls.PROVINCE)
        cls.province = data["O19_PROFILE"]
        cls.tables = data["TABLES"]
        cls.schema_map_version = data["SCHEMA_MAP_VERSION"]
        # the overlay buckets AS THE GENERATOR RESOLVES THEM for this
        # province: PROVINCE_SCOPED removes rulings that belong to
        # another province and BY_PROVINCE adds this one's, so comparing
        # the raw overlay sets against a BC manifest would report every
        # scoped ruling as missing
        cls.rules = load_generator().TableRules(cls.ov, cls.province)

    def shared(self, names):
        """The overlay names that the manifest actually carries."""
        return sorted(n for n in names if n in self.tables)

    def assertClass(self, names, want, bucket):
        wrong = ["{0}: {1} not {2}".format(
            n, self.tables[n].get("class"), want)
            for n in self.shared(names)
            if self.tables[n].get("class") != want]
        self.assertEqual(wrong, [], self.stale(bucket))

    @staticmethod
    def stale(bucket):
        return ("overrides_schema.py {0} disagrees with the shipped "
                "manifest. The overlay was edited without regenerating: "
                "run generate_manifests.py --oscar-src <checkout> and "
                "commit o19map_schema.py with the overlay change."
                .format(bucket))

    def test_class_buckets_are_the_classes_the_manifest_ships(self):
        self.assertClass(self.rules.reference, "reference",
                         "CLASS_REFERENCE")
        self.assertClass(self.rules.archive_patient, "archive",
                         "ARCHIVE_PATIENT")
        self.assertClass(self.rules.archive_other, "archive", "ARCHIVE_OTHER")
        self.assertClass(self.rules.archive_shared, "archive",
                         "ARCHIVE_SHARED")
        self.assertClass(self.rules.drop, "drop", "DROP")
        self.assertClass(self.rules.merge_keys, "merge", "CLASS_MERGE")

    def test_no_manifest_class_is_ruled_by_a_bucket_that_lost_it(self):
        buckets = {
            "reference": set(self.rules.reference),
            "merge": set(self.rules.merge_keys),
            "drop": set(self.rules.drop),
            "archive": (set(self.rules.archive_patient)
                        | set(self.rules.archive_other)
                        | set(self.rules.archive_shared)),
        }
        orphan = sorted(
            "{0} is class {1} with no overlay entry".format(t, cls)
            for t, e in self.tables.items()
            for cls in [e.get("class")]
            if cls in buckets and t not in buckets[cls])
        self.assertEqual(orphan, [], self.stale("class buckets"))

    def test_merge_keys_are_the_overlay_keys_in_order(self):
        wrong = ["{0}: {1} not {2}".format(
            t, self.tables[t].get("merge_keys"), list(keys))
            for t, keys in sorted(self.rules.merge_keys.items())
            if t in self.tables
            and self.tables[t].get("merge_keys") != list(keys)]
        self.assertEqual(wrong, [], self.stale("CLASS_MERGE keys"))

    def test_patient_data_marks_exactly_the_patient_archive_bucket(self):
        want = set(self.shared(self.rules.archive_patient))
        got = set(t for t, e in self.tables.items() if e.get("patient_data"))
        self.assertEqual(got, want, self.stale("ARCHIVE_PATIENT"))

    def test_replace_seed_marks_exactly_the_replace_seed_bucket(self):
        want = set(self.shared(self.rules.replace_seed))
        got = set(t for t, e in self.tables.items() if e.get("replace_seed"))
        self.assertEqual(got, want, self.stale("REPLACE_SEED"))

    def test_chunked_tables_are_exactly_the_chunk_bucket(self):
        want = set(self.shared(self.rules.chunk_tables))
        got = set(t for t, e in self.tables.items() if e.get("chunk_by"))
        self.assertEqual(got, want, self.stale("CHUNK_TABLES"))

    def test_charset_scan_columns_are_the_overlay_columns(self):
        want = dict((t, list(c)) for t, c in self.rules.charset_scan.items()
                    if t in self.tables)
        got = dict((t, e["charset_scan"]) for t, e in self.tables.items()
                   if e.get("charset_scan"))
        self.assertEqual(got, want, self.stale("CHARSET_SCAN"))

    def test_fk_remaps_are_the_overlay_remaps(self):
        want = dict((t, dict(m)) for t, m in self.rules.fk_remap.items()
                    if t in self.tables)
        got = dict((t, e["fk_remap"]) for t, e in self.tables.items()
                   if e.get("fk_remap"))
        self.assertEqual(got, want, self.stale("FK_REMAP"))

    def test_merge_exclusions_are_the_overlay_exclusions(self):
        want = dict((t, w) for t, w in self.rules.merge_exclude.items()
                    if t in self.tables)
        got = dict((t, e["merge_exclude"]) for t, e in self.tables.items()
                   if e.get("merge_exclude"))
        self.assertEqual(got, want, self.stale("MERGE_EXCLUDE"))

    def test_verbatim_overlay_constants_are_copied_through(self):
        # these are emitted unchanged, so equality is the whole contract
        for name in ("CREDENTIAL_TABLES", "PRISTINE_TOLERATED_TABLES",
                     "ROLE_TEMPLATE_MIN_JACCARD", "STARTUP_CREATED_ROWS",
                     "REQUIRED_TABLES", "CARLOSDOC_SEED_DELETES",
                     "SEED_PROVIDER_NO", "SEED_USER_NAME"):
            self.assertEqual(getattr(o19map_schema, name),
                             getattr(self.ov, name),
                             self.stale(name))

    def test_the_map_version_carries_the_overlay_base_token(self):
        # the generator appends "+<digest of the emitted content>", so
        # the base is the part before it -- compared EXACTLY, because a
        # startswith would let an overlay bumped to o19map-21 pass
        # against a shipped manifest still built from o19map-2
        self.assertEqual(
            self.schema_map_version.rsplit("+", 1)[0],
            self.ov.SCHEMA_MAP_VERSION,
            self.stale("SCHEMA_MAP_VERSION"))


class TestTheOntarioProfile(unittest.TestCase):

    """What is true of the Ontario profile ALONE.

    Split out of TestSchemaManifest when the second profile shipped: a
    seed floor counted from Ontario's Flyway set, or a table only the
    Ontario CARLOS schema carries, is not an invariant every province
    owes -- asserting it against BC would fail for the right reason and
    the wrong test."""

    @classmethod
    def setUpClass(cls):
        cls.data = profile_data("on")
        cls.tables = cls.data["TABLES"]

    def test_the_ontario_billing_stack_is_copied(self):
        for table in ("billing_on_cheader1", "billing_on_item", "billing"):
            self.assertIn(table, self.tables, table)
            self.assertEqual(self.tables[table]["class"], "copy", table)

    def test_privilege_seed_floor_reflects_later_deletions(self):
        # 514 baseline tuples + the V1.0.6 INSERT IGNORE row - the carlosdoc
        # denial V1.0.9 deletes = 514, which is what a live target holds
        self.assertEqual(self.data["SEED_ROW_COUNTS"]["secObjPrivilege"],
                         514)
        self.assertEqual(self.data["SEED_ROW_COUNTS"]["secObjectName"],
                         133)


class TestTheBritishColumbiaProfile(unittest.TestCase):

    """What is true of the BC profile ALONE.

    The BC surface was invisible before this pass: the generator read
    only `migration/common + on`, so 51 BC tables had no ruling AND were
    not reported as unknown. These pin the rulings that pass produced,
    including the two the plan named as actively wrong."""

    @classmethod
    def setUpClass(cls):
        cls.data = profile_data("bc")
        cls.tables = cls.data["TABLES"]

    def test_the_bc_billing_and_msp_stack_is_copied(self):
        # Teleplan/MSP claims, the BC billing core and WorkSafeBC: a
        # clinic's whole revenue history
        for table in ("billingmaster", "billing", "wcb",
                      "billing_msp_servicecode_times", "teleplanS00"):
            self.assertIn(table, self.tables, table)
            self.assertEqual(self.tables[table]["class"], "copy", table)

    def test_the_bc_antenatal_forms_are_copied_not_archived(self):
        """`formBCAR2007` is a LIVE table in the CARLOS BC schema.

        Ruled archive + patient_data against the Ontario schema (where
        CARLOS dropped it), which is correct there and actively wrong
        here: a BC clinic's antenatal records would have been moved to
        the archive schema and out of the application."""
        for table in ("formBCAR", "formBCAR2007"):
            self.assertEqual(self.tables[table]["class"], "copy", table)
            self.assertFalse(self.tables[table].get("patient_data"), table)

    def test_the_bc_reference_catalogs_do_not_duplicate_the_seed(self):
        """Four BC-seeded catalogs that a plain copy would have wrecked.

        billinglocation has no key at all (code '00' and region 'BC' on
        every row, 26 repeated descriptions), so a copy would double
        every entry in the billing dropdown. The three directories carry
        AUTO_INCREMENT ids that clinic rows point at, so an id-intact
        copy over the seed collides on the PK."""
        self.assertEqual(self.tables["billinglocation"]["class"],
                         "reference")
        self.assertEqual(self.tables["billingvisit"]["merge_keys"],
                         ["visittype", "region"])
        for table in ("billingreferral", "pharmacyInfo",
                      "professionalSpecialists"):
            self.assertTrue(self.tables[table].get("replace_seed"), table)

    def test_privilege_seed_floor_is_counted_from_the_bc_migrations(self):
        # BC seeds two more privilege tuples and one more object than
        # Ontario; a floor carried over from Ontario would refuse every
        # BC host at P0
        self.assertEqual(self.data["SEED_ROW_COUNTS"]["secObjPrivilege"],
                         516)
        self.assertEqual(self.data["SEED_ROW_COUNTS"]["secObjectName"],
                         134)

    def test_no_ontario_only_table_leaks_into_the_bc_profile(self):
        # PROVINCE_SCOPED removals: these are Ontario CARLOS tables, and
        # a ruling written against them means the opposite here
        for table in ("billing_on_errorCode", "billing_on_cheader1",
                      "billing_on_item", "OLISProviderPreferences",
                      "formONAR"):
            self.assertNotIn(table, self.tables, table)


#: One TestSchemaManifest run per province the package carries beyond
#: the default. Generated rather than written out so a third profile is
#: covered the day it is emitted, instead of the day someone remembers.
for _province in sorted(o19map_schema.PROFILES):
    for _base in (TestSchemaManifest, TestOverlayRulingsReachTheManifest):
        _name = _base.__name__ + _province.upper()
        globals()[_name] = type(
            _name, (_base,),
            {"PROVINCE": _province,
             "__doc__": "{0} against the {1!r} profile."
                        .format(_base.__name__, _province)})


if __name__ == "__main__":
    unittest.main()
