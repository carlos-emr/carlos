# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Contracts of the roles/privileges post-step (M8): every write is
idempotent, the break-glass admin is usable, memberships use the clinic's
own role ids, custom roles get CARLOS-era grants from a deterministic
template, and the verify checks fail closed on what the step guarantees.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import os
import unittest

from carlos_ctl import o19etl, o19map_schema, o19roles


def idempotent(sql):
    """Every write the step issues is safe to re-run: inserts are guarded
    or INSERT IGNORE, DDL is DROP IF EXISTS + CREATE, and every UPDATE or
    DELETE carries a WHERE whose predicate is false once the write has
    happened (the row is gone, or the column already holds the new value,
    or the old value it selects on is no longer there)."""
    s = sql.upper()
    if s.startswith("INSERT IGNORE") or s.startswith("DROP TABLE IF EXISTS") \
            or s.startswith("CREATE TABLE"):
        return True
    if s.startswith("RENAME TABLE"):
        # The rebuild swap. This one statement is NOT re-runnable alone --
        # after it, its source no longer exists -- but the block it closes
        # is: `rebuild_statements` opens with an unconditional
        # DROP ... IF EXISTS of both `__new` and `__old`, so re-running the
        # sequence rebuilds the scratch copy and swaps again to the same
        # end state. Accepted only in that shape, so an unguarded rename
        # elsewhere still fails this check.
        return "__NEW` TO " in s and "` TO `" in s
    if s.startswith("INSERT"):
        return "NOT EXISTS" in s
    if s.startswith("DELETE"):
        return " WHERE " in s
    if s.startswith("UPDATE"):
        if " WHERE " not in s:
            return False
        where = s.split(" WHERE ", 1)[1]
        return any(guard in where for guard in (
            "IS NULL",          # activeyn: NULL -> 1, the NULL is gone
            "BINARY PREVENTION_TYPE = ",  # legacy code -> canonical code
            "FID = ",           # status := 0 for one row (0 -> 0 repeats)
        ))
    return False


class TestStatementShapes(unittest.TestCase):

    """The SQL the roles post-step issues, statement by statement.

    Every write is idempotent by construction, scoped to the schema it
    names, and ordered so a crash between any two leaves a state the
    next run can continue from."""
    def test_snapshot_copies_every_seed_table_into_the_archive(self):
        stmts = o19roles.snapshot_statements("carlos", "o19_archive")
        self.assertEqual(len(stmts), 6 * len(o19roles.SNAPSHOT_TABLES))
        self.assertIn("CREATE TABLE `o19_archive`.`carlos_seed_"
                      "secObjPrivilege__new` AS SELECT * FROM "
                      "`carlos`.`secObjPrivilege`", stmts)
        for table in ("secObjPrivilege", "secObjectName", "secRole",
                      "access_type"):
            self.assertIn(table, o19roles.SNAPSHOT_TABLES)

    def test_the_snapshot_never_drops_the_previous_one_first(self):
        # this snapshot is taken BEFORE the merges, so once they have run
        # the target no longer holds only the seed and it cannot be
        # rebuilt: a DROP whose CREATE then failed would take the
        # privilege baseline the roles diff needs with it
        stmts = o19roles.snapshot_statements("carlos", "o19_archive")
        for sql in stmts:
            if sql.startswith("DROP TABLE"):
                self.assertRegex(sql, r"__(new|old)`$", sql)
        self.assertEqual(
            len([x for x in stmts if x.startswith("RENAME TABLE")]),
            len(o19roles.SNAPSHOT_TABLES))

    def test_startup_rows_delete_in_manifest_order_with_bound_schema(self):
        stmts = o19roles.startup_row_delete_statements("carlos")
        tables = [s.split("`")[3] for s in stmts]
        self.assertEqual(tables, [t for t, _ in
                                  o19map_schema.STARTUP_CREATED_ROWS])
        self.assertLess(tables.index("program_provider"),
                        tables.index("program"))
        pp = [s for s in stmts if "`program_provider`" in s][0]
        self.assertIn("FROM `carlos`.program WHERE name = 'OSCAR'", pp)
        self.assertNotIn("{schema}", pp)
        self.assertIn("WHERE name = 'Main Clinic'",
                      o19roles.startup_row_count_sql(
                          "site", "name = 'Main Clinic'", "carlos"))

    def test_guaranteed_roles_and_carlos_roles_append_by_name(self):
        stmts = o19roles.guaranteed_role_statements("carlos")
        self.assertEqual(len(stmts), 2)
        for sql in stmts:
            self.assertIn("WHERE NOT EXISTS", sql)
        self.assertIn("'doctor'", stmts[0])
        append = o19roles.carlos_role_append_statement("carlos", "o19_archive")
        self.assertIn("FROM `o19_archive`.`carlos_seed_secRole` s", append)
        self.assertIn("d.role_name = s.role_name", append)
        self.assertNotIn("role_no,", append.split("SELECT")[0])  # fresh ids

    def test_oscar_program_insert_is_guarded_and_never_null_facility(self):
        sql = o19roles.oscar_program_statement("carlos")
        self.assertIn("HAVING MIN(f.id) IS NOT NULL", sql)
        self.assertIn("NOT EXISTS (SELECT 1 FROM `carlos`.program WHERE "
                      "name = 'OSCAR')", sql)
        self.assertIn("'Service', 'active', 99999", sql)
        # every NOT NULL column without a default is supplied, so the
        # insert does not lean on the executor's sql_mode
        self.assertIn("transgender, firstNation, alcohol, physicalHealth, "
                      "mentalHealth, housing, exclusiveView, ageMin, ageMax)",
                      sql)
        self.assertIn("0, 0, 0, 0, 0, 0, 'no', 0, 0", sql)

    def test_membership_uses_the_clinics_role_no_and_skips_members(self):
        stmts = o19roles.membership_statements("carlos")
        self.assertEqual(len(stmts), 2)
        first, fallback = stmts
        self.assertIn("JOIN `carlos`.secRole r ON r.role_name = ur.role_name",
                      first)
        self.assertIn("ur.activeyn = 1", first)
        # doctor when held, else the active role with the most grants,
        # lowest role_no on a tie — deterministic, never MIN(role_no)
        self.assertIn("ORDER BY (r.role_name = 'doctor') DESC, (SELECT "
                      "COUNT(*) FROM `carlos`.secObjPrivilege g WHERE "
                      "g.roleUserGroup = r.role_name) DESC, r.role_no LIMIT 1",
                      first)
        # no active role at all -> the clinic role with the FEWEST grants,
        # never a secRole row named like a pseudo-group
        self.assertNotIn("'doctor'", fallback)
        self.assertIn("r.role_name NOT REGEXP "
                      "'^(-?[0-9]+|_all|_queue\\\\..*)$'", fallback)
        self.assertIn("ORDER BY (SELECT COUNT(*) FROM `carlos`.secObjPrivilege"
                      " g WHERE g.roleUserGroup = r.role_name) ASC, r.role_no "
                      "LIMIT 1", fallback)
        cand = o19roles.fallback_membership_candidates_sql("carlos")
        self.assertIn("NOT EXISTS (SELECT 1 FROM `carlos`.secUserRole ur",
                      cand)
        self.assertIn("ur.activeyn = 1", cand)
        for sql in stmts:
            self.assertIn("NOT EXISTS (SELECT 1 FROM `carlos`.program_provider"
                          " pp WHERE pp.provider_no = pr.provider_no)", sql)
            self.assertIn("pr.status = '1'", sql)
            self.assertNotIn("caisi_role", sql)

    def test_facility_link_targets_the_first_enabled_facility(self):
        sql = o19roles.provider_facility_statement("carlos")
        self.assertIn("MIN(f.id) FROM `carlos`.Facility f WHERE "
                      "f.disabled = 0", sql)
        self.assertIn("NOT EXISTS", sql)

    def test_activeyn_update_is_scoped_to_live_accounts(self):
        sql = o19roles.activeyn_update_statement("carlos")
        self.assertIn("SET ur.activeyn = 1", sql)
        self.assertIn("ur.activeyn IS NULL", sql)
        self.assertIn("p.status = '1'", sql)
        # EXISTS, never a join: a provider with two logins counts once
        self.assertIn("EXISTS (SELECT 1 FROM `carlos`.security s", sql)
        self.assertNotIn("JOIN", sql)
        # the seed's system pseudo-provider is never a live account
        self.assertIn("p.provider_no <> '-1'", sql)
        self.assertIn("activeyn IS NULL",
                      o19roles.activeyn_candidates_sql("carlos"))
        # admin assignments are never activated automatically: CARLOS
        # treats a NULL admin row as inactive on purpose
        self.assertIn("LOWER(ur.role_name) <> 'admin'", sql)
        self.assertIn("LOWER(ur.role_name) <> 'admin'",
                      o19roles.activeyn_candidates_sql("carlos"))
        self.assertIn("LOWER(ur.role_name) = 'admin'",
                      o19roles.activeyn_admin_left_sql("carlos"))
        dangling = o19roles.dangling_role_assignments_sql(
            "carlos", ["HRMAdmin", "Site Manager"])
        self.assertIn("ur.role_name IN ('HRMAdmin', 'Site Manager')",
                      dangling)
        # the NULL rows step 3 activates count as active assignments here
        self.assertIn("ur.activeyn = 1 OR (ur.activeyn IS NULL AND EXISTS",
                      dangling)
        # the remaining count is the complement: rows of accounts that
        # are NOT live, so the dormant admin rows are not counted twice
        remaining = o19roles.activeyn_null_remaining_sql("carlos")
        self.assertIn("activeyn IS NULL AND NOT (EXISTS", remaining)

    def test_role_names_fold_like_the_pad_space_collation(self):
        # trailing spaces and case are insignificant to the column; a
        # LEADING space is significant and stays so
        self.assertEqual(o19roles.custom_roles(
            ["Triage Nurse "], [("triage nurse", "_rx", "x", "0")],
            ["doctor"]), ["Triage Nurse "])
        self.assertEqual(o19roles.custom_roles(
            [" Triage Nurse"], [("Triage Nurse", "_rx", "x", "0")],
            ["doctor"]), [])

    def test_system_pseudo_provider_gets_no_membership_or_link(self):
        for sql in ([o19roles.provider_facility_statement("carlos"),
                     o19roles.fallback_membership_candidates_sql("carlos"),
                     o19roles.providers_without_membership_sql("carlos")]
                    + o19roles.membership_statements("carlos")):
            self.assertIn("provider_no <> '-1'", sql)

    def test_every_write_is_idempotent(self):
        writes = (o19roles.snapshot_statements("c", "a")
                  + o19roles.startup_row_delete_statements("c")
                  + o19roles.guaranteed_role_statements("c")
                  + [o19roles.carlos_role_append_statement("c", "a"),
                     o19roles.provider_facility_statement("c"),
                     o19roles.oscar_program_statement("c"),
                     o19roles.activeyn_update_statement("c"),
                     o19roles.backfill_statement("c", "a", "x", "nurse",
                                                 ["_fax"]),
                     o19roles.rtl_disable_statement("c", "7")]
                  + o19roles.membership_statements("c")
                  + [d for _, _, d in o19roles.property_prune_statements(
                      "c", ["born"])]
                  + [u for _, _, _, u in o19roles.prevention_type_statements(
                      "c", {"Flu": "Inf"})])
        for sql in writes:
            self.assertTrue(idempotent(sql), sql)
        # the classifier itself rejects what it should
        self.assertFalse(idempotent("UPDATE t SET n = n + 1"))
        self.assertFalse(idempotent("UPDATE t SET n = 1 WHERE x = 2"))
        self.assertFalse(idempotent("DELETE FROM t"))
        self.assertFalse(idempotent("INSERT INTO t VALUES (1)"))


class TestCustomRoleBackfill(unittest.TestCase):

    """Giving a clinic-custom role the grants CARLOS added since O19.

    The template is the closest stock role, and "closest" has to be
    defined precisely enough to be argued with: the objects O19 never
    knew, scored by overlap, ties broken alphabetically."""
    SEED = [("doctor", "_rx", "x", "0"), ("doctor", "_fax", "x", "0"),
            ("doctor", "_email", "x", "0"), ("nurse", "_rx", "r", "0"),
            ("nurse", "_fax", "x", "0"), ("admin", "_admin", "x", "0"),
            ("-1", "_email", "x", "0"), ("HRMAdmin", "_hrm.administrator",
                                         "x", "0")]
    STAGE = [("doctor", "_rx", "x", "0"), ("Triage Nurse", "_rx", "r", "0"),
             ("Triage Nurse", "_tickler", "x", "0"),
             ("999997", "_rx", "o", "0"), ("_all", "_eChart$5", "|or|", "0"),
             ("_queue.2", "_edoc", "x", "0"), ("Ghost", "_rx", "x", "0")]
    OBJECTS = ["_rx", "_tickler", "_admin", "_edoc"]

    def test_carlos_era_objects_are_those_o19_never_knew(self):
        era = o19roles.carlos_era_objects(self.SEED, self.STAGE, self.OBJECTS)
        self.assertEqual(era, ["_email", "_fax", "_hrm.administrator"])

    def test_a_trailing_blank_still_counts_as_known(self):
        # the column's collation is PAD SPACE, so the clinic's '_hrm '
        # already covers '_hrm'. Treating it as CARLOS-era would grant
        # every custom role an object the clinic already scoped itself.
        seed = list(self.SEED) + [["doctor", "_hrm", "x", "0"]]
        objects = list(self.OBJECTS) + ["_hrm "]
        self.assertNotIn(
            "_hrm", o19roles.carlos_era_objects(seed, self.STAGE, objects))
        stage = list(self.STAGE) + [["nurse", "_HRM", "r", "0"]]
        self.assertNotIn(
            "_hrm", o19roles.carlos_era_objects(seed, stage, self.OBJECTS))

    def test_custom_roles_exclude_provider_numbers_queues_and_stock(self):
        roles = o19roles.custom_roles(
            ["doctor", "Triage Nurse", "Ghost", "Unused"], self.STAGE,
            ["doctor", "nurse", "admin"])
        # Ghost has grants and is in the catalogue -> custom; Unused has
        # no imported grant -> nothing to resemble, left alone
        self.assertEqual(roles, ["Ghost", "Triage Nurse"])
        for group in ("999997", "-1", "_all", "_queue.2"):
            self.assertFalse(o19roles.is_role_group(group))
        self.assertTrue(o19roles.is_role_group("Triage Nurse"))
        # role_name is unique case-insensitively: `Doctor` IS the stock role,
        # and grant rows spelled `ghost` belong to the role `Ghost`
        self.assertEqual(o19roles.custom_roles(
            ["Doctor", "Ghost"], [("Doctor", "_rx", "x", "0")] + self.STAGE,
            ["doctor"]), ["Ghost"])
        self.assertEqual(o19roles.custom_roles(
            ["Ghost"], [("ghost", "_rx", "x", "0")], ["doctor"]), ["Ghost"])
        self.assertEqual(o19roles.role_pairs([("ghost", "_rx", "x", "0")],
                                             "Ghost"), {("_rx", "x")})
        self.assertEqual(o19roles.non_role_named_roles(
            ["doctor", "123", "_queue.9"]), ["123", "_queue.9"])

    def test_template_is_the_closest_stock_role_ties_alphabetical(self):
        template, score = o19roles.choose_template(
            "Triage Nurse", self.STAGE, self.SEED, 0.3)
        # shares (_rx, r) with nurse only
        self.assertEqual(template, "nurse")
        self.assertGreater(score, 0.3)
        # Ghost shares (_rx, x) with doctor -> doctor
        self.assertEqual(o19roles.choose_template(
            "Ghost", self.STAGE, self.SEED, 0.3)[0], "doctor")
        # a tie is broken alphabetically, never by dict order
        seed = [("beta", "_a", "x", "0"), ("alpha", "_a", "x", "0")]
        stage = [("custom", "_a", "x", "0")]
        self.assertEqual(o19roles.choose_template("custom", stage, seed,
                                                  0.3)[0], "alpha")

    def test_low_similarity_yields_no_template(self):
        stage = [("Odd", "_zzz", "x", "0"), ("Odd", "_yyy", "x", "0")]
        template, score = o19roles.choose_template("Odd", stage, self.SEED,
                                                   0.3)
        self.assertIsNone(template)
        self.assertEqual(score, 0.0)

    def test_pseudo_groups_and_non_stock_groups_never_become_templates(self):
        # the seed's `-1` pseudo-group holds 4 grants; a 3-grant custom role
        # sharing two of them scores 0.4 against it and far less against
        # any real role — it must never be offered as a template, nor may
        # a seed group that is not a stock ROLE name
        seed = [("-1", "_demographic", "r", "0"), ("-1", "_msg", "x", "0"),
                ("-1", "_email", "x", "0"), ("-1", "_fax", "x", "0"),
                ("Twin Group", "_demographic", "r", "0"),
                ("Twin Group", "_msg", "x", "0"),
                ("Twin Group", "_tickler", "x", "0"),
                ("999998", "_demographic", "r", "0"),
                ("999998", "_msg", "x", "0"), ("999998", "_tickler", "x", "0")]
        seed += [("doctor", o, "x", "0") for o in (
            "_demographic", "_msg", "_rx", "_lab", "_eChart", "_tickler",
            "_billing", "_appointment", "_admin", "_edoc")]
        stage = [("Small", "_demographic", "r", "0"),
                 ("Small", "_msg", "x", "0"), ("Small", "_tickler", "x", "0")]
        # without the stock list the identical non-stock group wins
        self.assertEqual(o19roles.choose_template("Small", stage, seed,
                                                  0.3)[0], "Twin Group")
        # with it, only doctor is a candidate (matched case-insensitively,
        # seed spelling kept) and it is below the floor
        template, score = o19roles.choose_template("Small", stage, seed, 0.3,
                                                   ["DOCTOR"])
        self.assertIsNone(template)
        self.assertLess(score, 0.3)
        self.assertGreater(score, 0.0)

    def test_backfill_is_insert_ignore_over_carlos_era_objects_only(self):
        sql = o19roles.backfill_statement("carlos", "o19_archive",
                                          "Triage Nurse", "nurse",
                                          ["_fax", "_email"])
        self.assertTrue(sql.startswith("INSERT IGNORE INTO "
                                       "`carlos`.secObjPrivilege"))
        self.assertIn("SELECT 'Triage Nurse', objectName, privilege, "
                      "priority, NULL FROM "
                      "`o19_archive`.`carlos_seed_secObjPrivilege`", sql)
        self.assertIn("roleUserGroup = 'nurse' AND objectName IN ('_fax', "
                      "'_email')", sql)

    def test_role_template_flag_parses_and_validates(self):
        parsed = o19roles.parse_role_templates(
            ["Triage Nurse=nurse", " Ghost = doctor "])
        self.assertEqual(parsed, {"Triage Nurse": "nurse", "Ghost": "doctor"})
        for bad in (["nope"], ["=doctor"], ["x="],
                    ["a=doctor", "a=nurse"]):
            with self.assertRaises(ValueError):
                o19roles.parse_role_templates(bad)
        # an exact or case-only repeat is not a conflict; a case-only
        # twin with a different template is (the column is unique
        # case-insensitively)
        self.assertEqual(o19roles.parse_role_templates(
            ["a=doctor", "a=doctor", "A=Doctor"]), {"a": "doctor"})
        with self.assertRaises(ValueError):
            o19roles.parse_role_templates(["a=doctor", "A=nurse"])
        self.assertTrue(o19roles.same_role_templates(
            {"Triage Nurse": "nurse"}, {"triage nurse": "Nurse"}))
        self.assertFalse(o19roles.same_role_templates(
            {"Triage Nurse": "nurse"}, {"Triage Nurse": "doctor"}))
        problems = o19roles.validate_role_templates(
            {"Triage Nurse": "nurse", "Stranger": "doctor",
             "Ghost": "Pharmacist"}, ["Triage Nurse", "Ghost"],
            ["doctor", "nurse"])
        self.assertEqual(len(problems), 2)
        self.assertTrue(any("Stranger" in p for p in problems))
        self.assertTrue(any("Pharmacist" in p for p in problems))
        # case-insensitive like the column, re-keyed to the exact spellings
        self.assertEqual(o19roles.validate_role_templates(
            {"triage nurse": "Nurse"}, ["Triage Nurse"], ["nurse"]), [])
        self.assertEqual(o19roles.normalise_role_templates(
            {"triage nurse": "Nurse"}, ["Triage Nurse"], ["nurse"]),
            {"Triage Nurse": "nurse"})
        pending = o19roles.backfill_pending_count_sql(
            "carlos", "o19_archive", "Triage Nurse", "nurse", ["_fax"])
        self.assertIn("s.roleUserGroup = 'nurse' AND s.objectName IN "
                      "('_fax')", pending)
        self.assertIn("d.roleUserGroup = 'Triage Nurse' AND d.objectName = "
                      "s.objectName", pending)


class TestDiffPruneNormalise(unittest.TestCase):

    """The review listings, the property prune and the prevention map."""
    def test_privilege_diff_compares_clinic_rows_with_the_seed_snapshot(self):
        sql = o19roles.privilege_diff_sql("o19_import", "o19_archive")
        self.assertIn("FROM `o19_import`.secObjPrivilege s JOIN "
                      "`o19_archive`.`carlos_seed_secObjPrivilege` d", sql)
        self.assertIn("NOT (s.privilege <=> d.privilege AND s.priority <=> "
                      "d.priority)", sql)

    def test_excluded_grants_count_uses_the_manifest_predicate(self):
        sql = o19roles.excluded_grants_count_sql("o19_import")
        self.assertIsNotNone(sql)
        self.assertIn(o19map_schema.TABLES["secObjPrivilege"]["merge_exclude"],
                      sql)
        itemised = o19roles.excluded_grants_sql("o19_import")
        self.assertTrue(itemised.startswith(
            "SELECT s.roleUserGroup, s.objectName, s.privilege FROM"))
        restored = o19roles.restored_seed_grants_sql("o19_import",
                                                     "o19_archive")
        self.assertIn("d.roleUserGroup IN (SELECT role_name FROM "
                      "`o19_import`.secRole)", restored)
        self.assertIn("NOT EXISTS (SELECT 1 FROM `o19_import`."
                      "secObjPrivilege s", restored)
        appends = o19roles.stock_role_appends_sql("o19_import", "o19_archive",
                                                  ["doctor", "nurse"])
        self.assertIn("s.roleUserGroup IN ('doctor', 'nurse')", appends)
        self.assertIn("NOT EXISTS (SELECT 1 FROM `o19_archive`."
                      "`carlos_seed_secObjPrivilege` d", appends)
        self.assertIn(" AND NOT (", appends)  # exclusions not double-listed

    def test_property_prune_escapes_like_wildcards(self):
        pairs = o19roles.property_prune_statements(
            "carlos", ["INTEGRATOR_", "util.erx."])
        self.assertEqual(len(pairs), 2)
        prefix, count_sql, delete_sql = pairs[0]
        self.assertEqual(prefix, "INTEGRATOR_")
        self.assertIn("name LIKE 'INTEGRATOR\\_%'", count_sql)
        self.assertTrue(delete_sql.startswith("DELETE FROM `carlos`.property"))

    def test_prevention_type_statements_follow_the_generated_map(self):
        stmts = o19roles.prevention_type_statements(
            "carlos", {"Flu": "Inf", "VZ": "Var"})
        self.assertEqual([(a, b) for a, b, _, _ in stmts],
                         [("Flu", "Inf"), ("VZ", "Var")])
        # BINARY: the column's collation is case-insensitive and the map
        # holds 'dTaP' while 'DTaP' is a valid pediatric code
        self.assertEqual(stmts[0][3], "UPDATE `carlos`.preventions SET "
                                      "prevention_type = 'Inf' WHERE "
                                      "BINARY prevention_type = 'Flu'")
        self.assertIn("WHERE BINARY prevention_type = 'Flu'", stmts[0][2])
        unknown = o19roles.unknown_prevention_types_sql("carlos",
                                                        ["Inf", "Var"])
        self.assertIn("BINARY prevention_type NOT IN ('Inf', 'Var')", unknown)
        # valid under ONLY_FULL_GROUP_BY: this read runs on the plain client
        self.assertTrue(unknown.startswith(
            "SELECT IFNULL(MIN(prevention_type), '<NULL>'), COUNT(*)"))
        self.assertIn("GROUP BY BINARY prevention_type ORDER BY 1", unknown)
        # a NULL type renders as unconfigured too, so it is listed
        self.assertIn("WHERE prevention_type IS NULL OR BINARY", unknown)


class TestRichTextLetter(unittest.TestCase):

    """The Rich Text Letter form, in every state a clinic can have it.

    Stock and current, stock and legacy, edited beyond reach, disabled
    on purpose, renamed, or absent entirely -- each has a different
    right answer, and getting it wrong either breaks letters or
    silently re-enables a form the clinic turned off."""
    LEGACY = ("12", "Rich Text Letter", "1",
              "Rich Text Letter Generator v2.1", "0", "1", "1")
    MODERN = ("12", "Rich Text Letter", "1",
              "Rich Text Letter Generator 2026.3.0", "1", "0", "1")

    def test_rows_are_found_by_title_and_flagged_by_version_and_route(self):
        sql = o19roles.rtl_rows_sql("carlos")
        self.assertIn("form_html LIKE '%<title>Rich Text Letter</title>%'",
                      sql)
        self.assertIn("form_html LIKE '%RTL 2026.3.0%'", sql)
        self.assertIn("form_html LIKE '%../eform/attachEform.jsp%'", sql)
        # the database decides "canonical" with the scripts' own predicate
        self.assertIn("(" + o19roles.RTL_CANONICAL_PREDICATE + ")", sql)
        self.assertIn("OR (" + o19roles.RTL_CANONICAL_PREDICATE + ")", sql)

    def test_canonical_legacy_row_gets_the_fixups(self):
        disable, scripts, restore, notes = o19roles.rtl_plan([self.LEGACY])
        self.assertEqual(disable, [])
        self.assertEqual(restore, [])
        self.assertEqual(scripts, list(o19roles.RTL_FIXUP_SCRIPTS))
        self.assertTrue(any("form_html replaced" in n for n in notes))

    def test_modern_row_is_left_alone(self):
        disable, scripts, restore, notes = o19roles.rtl_plan([self.MODERN])
        self.assertEqual(scripts, [])
        self.assertEqual(disable, [])
        self.assertTrue(o19roles.rtl_current([self.MODERN]))

    def test_marked_row_with_dead_routes_gets_only_the_route_fix(self):
        # a crash between modernize and the route fix, or a form modernised
        # before the route fix existed: the marker alone is not "current"
        row = ("12", "Rich Text Letter", "1",
               "Rich Text Letter Generator 2026.3.0", "1", "1", "1")
        self.assertEqual(o19roles.fixup_scripts_needed([row]),
                         [o19roles.RTL_ROUTE_FIX_SCRIPT])
        self.assertFalse(o19roles.rtl_current([row]))

    def test_edited_subject_is_out_of_reach_and_reported(self):
        row = ("12", "Rich Text Letter", "1", "Our clinic letter", "0", "1",
               "0")
        disable, scripts, restore, notes = o19roles.rtl_plan([row])
        # a case variant the database calls canonical is canonical
        self.assertTrue(o19roles.is_rtl_canonical(
            ("13", "rich text letter", "1", "RICH TEXT LETTER GENERATOR",
             "0", "1", "1")))
        # not canonical: the seed is applied (the scripts cannot address
        # this row), the row itself is neither disabled nor claimed fixed
        self.assertEqual(disable, [])
        self.assertEqual(scripts[0], o19roles.RTL_SEED_SCRIPT)
        self.assertTrue(any("cannot address it" in n for n in notes))
        self.assertFalse(o19roles.rtl_current([row]))

    def test_clinic_disabled_canonical_row_is_re_disabled_after_fixups(self):
        row = ("12", "Rich Text Letter", "0",
               "Rich Text Letter Generator v2.1", "0", "1", "1")
        disable, scripts, restore, notes = o19roles.rtl_plan([row])
        self.assertEqual(disable, [])
        self.assertEqual(restore, ["12"])
        self.assertIn(o19roles.RTL_ENABLE_SCRIPT, scripts)
        self.assertTrue(any("left disabled" in n for n in notes))

    def test_letter_named_row_is_disabled_and_seed_added_when_absent(self):
        rows = [("1", "letter", "1", "letter generator", "0", "1", "0")]
        disable, scripts, restore, notes = o19roles.rtl_plan(rows)
        self.assertTrue(any("ENABLED Rich Text Letter" in n for n in notes))
        self.assertEqual(disable, ["1"])
        self.assertEqual(scripts[0], o19roles.RTL_SEED_SCRIPT)
        self.assertEqual(scripts[1:], list(o19roles.RTL_FIXUP_SCRIPTS))
        self.assertTrue(any("legacy" in n for n in notes))
        # already disabled legacy rows are not touched again
        self.assertEqual(o19roles.rtl_plan(
            [("1", "letter", "0", "x", "0", "1", "0")])[0], [])

    def test_rtl_derived_clinic_form_without_the_sink_is_left_alone(self):
        # 8-column rows: the last flag is the RptByExample.do sink
        canonical = ("2", "Rich Text Letter", "1",
                     "Rich Text Letter Generator 2026.3.0", "1", "0", "1",
                     "0")
        clone = ("57", "Consult Letter - Dr Smith", "1", "Our letter",
                 "0", "0", "0", "0")
        sink = ("58", "Old copy", "1", "x", "0", "0", "0", "1")
        dead = ("59", "Older copy", "1", "x", "0", "1", "0", "0")
        legacy = ("1", "letter", "1", "letter generator", "0", "0", "0", "0")
        disable, scripts, restore, notes = o19roles.rtl_plan(
            [canonical, clone, sink, dead, legacy])
        self.assertEqual(disable, ["58", "59", "1"])
        self.assertEqual(scripts, [])
        joined = "\n".join(notes)
        self.assertIn("fid 57: Rich Text Letter-derived clinic form", joined)
        # form names never reach the report (they can carry a clinician's
        # name); fids are enough for the review
        self.assertNotIn("Dr Smith", joined)
        self.assertNotIn("Old copy", joined)
        self.assertIn("form_html LIKE '%RptByExample.do%'",
                      o19roles.rtl_rows_sql("carlos"))

    def test_no_rtl_at_all_seeds_then_fixes(self):
        disable, scripts, restore, notes = o19roles.rtl_plan([])
        self.assertEqual(scripts[0], o19roles.RTL_SEED_SCRIPT)
        self.assertIn("WHERE fid = 7",
                      o19roles.rtl_disable_statement("carlos", "7"))


class TestRoleSpelling(unittest.TestCase):
    """CARLOS matches role names with exact Java string equality; the
    database matches them case-insensitively. The privilege merge keeps
    the seed's `nurse` rows and drops the clinic's `Nurse` ones, while
    secUserRole still says `Nurse` — every grant on that role goes
    inert."""

    def test_the_drift_probe_compares_binary_under_a_folded_join(self):
        sql = o19roles.role_spelling_drift_sql("carlos")
        self.assertIn("p.roleUserGroup = ur.role_name", sql)
        self.assertIn("BINARY p.roleUserGroup <> BINARY ur.role_name", sql)
        self.assertIn("ur.activeyn = 1", sql)

    def test_both_tables_are_aligned_to_the_secRole_catalogue(self):
        stmts = o19roles.role_spelling_statements("carlos")
        self.assertEqual(len(stmts), 2)
        self.assertIn("`carlos`.secObjPrivilege", stmts[0])
        self.assertIn("SET p.roleUserGroup = r.role_name", stmts[0])
        self.assertIn("`carlos`.secUserRole", stmts[1])
        self.assertIn("SET ur.role_name = r.role_name", stmts[1])
        for sql in stmts:
            # guarded, so a re-run matches nothing: both target tables
            # key role names case-insensitively, so at most one spelling
            # of a role exists and the update cannot collide
            self.assertIn("WHERE BINARY", sql)
            self.assertTrue(sql.startswith("UPDATE "), sql)

    def test_comma_named_roles_are_reported_never_rewritten(self):
        sql = o19roles.comma_named_roles_sql("carlos")
        self.assertTrue(sql.startswith("SELECT role_name"), sql)
        self.assertIn("LIKE '%,%'", sql)


class TestVerifyRoleChecks(unittest.TestCase):
    """A fake query answers the exact shapes verify_role_checks issues."""

    def make_query(self, **over):
        answers = {
            "roles": [["doctor"], ["admin"], ["Triage Nurse"]],
            "admin_active": "2", "admin_grant": "1", "facility": "1",
            "clinic": "1", "program": "1", "missing": "0", "unlinked": "0",
            "grants": "600", "no_role": [], "no_grant": [], "locked": [],
            "jobs": "1", "spelling_drift": "0",
            "rtl": [["12", "Rich Text Letter", "1",
                     "Rich Text Letter Generator 2026.3.0",
                     "1", "0", "1"]],
        }
        answers.update(over)

        def q(sql):
            if "BINARY p.roleUserGroup" in sql:
                return [[answers["spelling_drift"]]]
            if "SELECT role_name FROM" in sql and "secRole`" not in sql \
                    and "DISTINCT" not in sql:
                return answers["roles"]
            if "activeyn = 1" in sql and "COUNT(*)" in sql \
                    and "objectName" not in sql:
                return [[answers["admin_active"]]]
            if "p.objectName = '_admin'" in sql:
                return [[answers["admin_grant"]]]
            if "Facility WHERE disabled = 0" in sql:
                return [[answers["facility"]]]
            if ".clinic" in sql:
                return [[answers["clinic"]]]
            if "program WHERE name = 'OSCAR'" in sql:
                return [[answers["program"]]]
            if "NOT EXISTS (SELECT 1 FROM `carlos`.program_provider" in sql:
                return [[answers["missing"]]]
            if "provider_facility" in sql:
                return [[answers["unlinked"]]]
            if sql.startswith("SELECT COUNT(*) FROM `carlos`.secObjPrivilege"):
                return [[answers["grants"]]]
            if "NOT EXISTS (SELECT 1 FROM `carlos`.secUserRole" in sql:
                return answers["no_role"]
            if "SELECT DISTINCT ur.role_name" in sql:
                return answers["no_grant"]
            if "b_ExpireSet = 1" in sql:
                return answers["locked"]
            if "OscarJobType" in sql:
                return [[answers["jobs"]]]
            if "<title>Rich Text Letter</title>" in sql:
                return answers["rtl"]
            raise AssertionError("unexpected SQL: " + sql)
        return q

    def test_clean_target_passes_every_hard_check(self):
        ok, bad, adv, private = o19roles.verify_role_checks(
            self.make_query(), "carlos", "100001", 513)
        self.assertEqual(bad, [])
        self.assertEqual(adv, [])
        self.assertEqual(private, [])
        self.assertTrue(any("holds _admin" in line for line in ok))

    def test_inert_admin_missing_program_and_low_floor_fail(self):
        ok, bad, adv, private = o19roles.verify_role_checks(
            self.make_query(admin_active="0", admin_grant="0", program="0",
                            grants="10", missing="2"),
            "carlos", "100001", 513)
        self.assertEqual(len(bad), 5, bad)
        self.assertTrue(any("no active secUserRole" in b for b in bad))
        self.assertTrue(any("seed floor" in b for b in bad))
        self.assertTrue(any("without program_provider" in b for b in bad))

    def test_a_trailing_blank_role_name_is_the_same_role(self):
        # the collation is PAD SPACE; .lower() alone would call a
        # guaranteed role missing and fail a verified import
        query = self.make_query(roles=[["doctor "], ["admin "],
                                       ["Triage Nurse"]])
        _ok, problems, _adv, _priv = o19roles.verify_role_checks(
            query, "carlos", None, 0)
        self.assertEqual(
            [p for p in problems if "missing from secRole" in p], [])
        # a LEADING blank is significant and is NOT the same role
        query = self.make_query(roles=[[" doctor"], ["admin"]])
        _ok, problems, _adv, _priv = o19roles.verify_role_checks(
            query, "carlos", None, 0)
        self.assertTrue(any("missing from secRole" in p for p in problems),
                        problems)

    def test_missing_guaranteed_role_fails(self):
        ok, bad, adv, private = o19roles.verify_role_checks(
            self.make_query(roles=[["admin"]]), "carlos", None, 1)
        self.assertTrue(any("'doctor' missing" in b for b in bad))
        # `Doctor` satisfies the check: role_name is unique case-insensitively
        ok, bad, adv, private = o19roles.verify_role_checks(
            self.make_query(roles=[["Admin"], ["Doctor"]]), "carlos", None, 1)
        self.assertEqual(bad, [])

    def test_rtl_not_current_is_an_advisory_not_a_failure(self):
        ok, bad, adv, private = o19roles.verify_role_checks(
            self.make_query(rtl=[["1", "letter", "0", "x", "0", "1", "0"]]),
            "carlos", "100001", 513)
        self.assertEqual(bad, [])
        self.assertTrue(any("Rich Text Letter" in a for a in adv))

    def test_clinic_conditions_are_advisories_with_private_names(self):
        ok, bad, adv, private = o19roles.verify_role_checks(
            self.make_query(no_role=[["p7"], ["p8"]],
                            no_grant=[["Ghost"]], locked=[["olduser"]],
                            jobs="0"),
            "carlos", "100001", 513)
        self.assertEqual(bad, [])
        self.assertEqual(len(adv), 4)  # no_role, no_grant, locked, jobs
        joined = "\n".join(adv)
        self.assertNotIn("p7", joined)
        self.assertNotIn("olduser", joined)
        self.assertIn("Ghost", joined)  # role names are not PHI
        self.assertTrue(any("p7, p8" in line for line in private))
        self.assertTrue(any("olduser" in line for line in private))


class TestSeedAdminClone(unittest.TestCase):

    """The break-glass administrator's roles, cloned from the seed."""
    def test_admin_roles_are_cloned_active(self):
        stmts = o19etl.seed_admin_statements(
            "carlos", "breakglass", "100001", "{bcrypt}$2b$12$x", "1234")
        self.assertEqual(len(stmts), 3)
        self.assertIn("(provider_no, role_name, orgcd, activeyn, "
                      "lastUpdateDate)", stmts[2])
        self.assertIn("IFNULL(orgcd, 'R0000001'), 1, NOW()", stmts[2])


class TestParityWithAppendedRows(unittest.TestCase):

    """Rows the roles step synthesises are an expected parity delta.

    Expected, but only the ones its own ledger recorded: anything else
    without a staging twin is a mismatch."""
    def test_appended_row_keys_cover_only_role_step_tables(self):
        self.assertEqual(set(o19etl.APPENDED_ROW_KEYS),
                         {"secRole", "program", "program_provider",
                          "provider_facility", "eform"})
        # every roles-step table is refused by the pre-checks when absent
        for table in o19etl.APPENDED_ROW_KEYS:
            self.assertIn(table, o19etl.ROLES_STEP_TABLES)
        self.assertIsNone(o19etl.appended_row_count_sql("demographic",
                                                        "s", "d"))
        sql = o19etl.appended_row_count_sql("program_provider", "stage",
                                            "carlos")
        self.assertIn("d.`program_id` <=> s.`program_id` AND d.`provider_no`"
                      " <=> s.`provider_no` AND d.`role_id` <=> s.`role_id`",
                      sql)

    def _query(self, target, twinless):
        def q(sql):
            if "information_schema" in sql:
                return [["secRole"]]
            if "WHERE NOT EXISTS" in sql:
                return [[str(twinless)]]
            if "`stage`.`secRole`" in sql:
                return [["31"]]
            if "`carlos`.`secRole`" in sql:
                return [[str(target)]]
            return [["0"]]
        return q

    def test_recorded_appended_rows_are_tolerated(self):
        ok, bad = o19etl.row_parity(self._query(34, 3), "stage", "carlos",
                                    appended={"secRole": 3})
        self.assertEqual(bad, [])
        self.assertTrue(any("+3 synthesised" in line for line in ok))

    def test_parity_rejects_appended_rows_the_ledger_did_not_record(self):
        ok, bad = o19etl.row_parity(self._query(34, 3), "stage", "carlos")
        self.assertEqual(len(bad), 1)
        ok, bad = o19etl.row_parity(self._query(34, 4), "stage", "carlos",
                                    appended={"secRole": 3})
        self.assertEqual(len(bad), 1)
        self.assertIn("roles ledger recorded 3", bad[0])


class TestPackagedFixups(unittest.TestCase):
    """debian/rules must ship exactly the scripts o19roles replays, and
    they must exist in the tree the package is built from."""

    ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "..", "..")

    def test_rules_installs_every_rtl_script_and_they_exist(self):
        with open(os.path.join(self.ROOT, "debian", "rules"),
                  encoding="utf-8") as fh:
            rules = fh.read()
        for name in (o19roles.RTL_SEED_SCRIPT,) + o19roles.RTL_FIXUP_SCRIPTS:
            self.assertIn("database/mysql/updates/" + name, rules, name)
            self.assertTrue(os.path.isfile(os.path.join(
                self.ROOT, "database", "mysql", "updates", name)), name)
        self.assertIn("schema/o19-fixups/", rules)
        self.assertEqual(o19roles.DEFAULT_FIXUPS_DIR,
                         "/usr/share/carlos-emr/schema/o19-fixups")

    def test_scripts_match_the_rows_the_planner_calls_canonical(self):
        # the planner's canonical test mirrors the scripts' own WHERE
        updates = os.path.join(self.ROOT, "database", "mysql", "updates")
        for name in o19roles.RTL_FIXUP_SCRIPTS:
            with open(os.path.join(updates, name), encoding="utf-8") as fh:
                text = fh.read()
            self.assertRegex(text, r"`?form_name`? = 'Rich Text Letter'",
                             name)
            self.assertIn("Rich Text Letter Generator%", text, name)
        # the planner's predicate is the scripts' WHERE, backticks aside
        self.assertEqual(o19roles.RTL_CANONICAL_PREDICATE,
                         "form_name = 'Rich Text Letter' AND subject LIKE "
                         "'Rich Text Letter Generator%'")
        with open(os.path.join(updates, o19roles.RTL_ROUTE_FIX_SCRIPT),
                  encoding="utf-8") as fh:
            route_fix = fh.read()
        for route in o19roles.RTL_DEAD_ROUTES:
            self.assertIn("'" + route + "'", route_fix)


if __name__ == "__main__":
    unittest.main()
