# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Preflight verdict contract tests (docs plan §6.1).

A fake query callable serves canned information_schema and COUNT results,
so every blocker/advisory classification and the verdict/exit-code/accept
contract is pinned without a database.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import contextlib
import io
import unittest

from carlos_ctl import o19_preflight as pf


class FakeDb(object):
    """Serves the exact SQL shapes run_checks() issues."""

    def __init__(self, tables=None, where_counts=None, columns=None,
                 rows=None):
        # tables: {name: rowcount}; where_counts: {(table, substring): n};
        # rows: {sql substring: canned rows} for non-COUNT queries
        self.tables = dict(tables or {})
        self.where_counts = dict(where_counts or {})
        # every Facility row is enabled unless a test says otherwise
        if "Facility" in self.tables and not any(
                t == "Facility" for t, _ in self.where_counts):
            self.where_counts[("Facility", "disabled = 0")] = \
                self.tables["Facility"]
        self.columns = dict(columns or {})
        self.rows = dict(rows or {})
        self.queries = []

    def __call__(self, sql):
        self.queries.append(sql)
        for frag, canned in self.rows.items():
            if frag in sql:
                return canned
        if "information_schema.TABLES" in sql and "TABLE_NAME" in sql \
                and "SUM(" not in sql:
            return [[t] for t in sorted(self.tables)]
        if "information_schema.COLUMNS" in sql:
            out = []
            for t in sorted(self.columns):
                for c in self.columns[t]:
                    out.append([t, c])
            return out
        if "SUM(DATA_LENGTH" in sql:
            return [["123"]]
        if sql.startswith("SELECT COUNT(*) FROM `"):
            table = sql.split("`")[1]
            if " WHERE " in sql:
                where = sql.split(" WHERE ", 1)[1]
                for (t, frag), n in self.where_counts.items():
                    if t == table and frag in where:
                        return [[str(n)]]
                return [["0"]]
            return [[str(self.tables.get(table, 0))]]
        if sql == "SELECT 1":
            return [["1"]]
        if sql.startswith("SELECT role_name FROM "):
            return []       # no clinic-custom roles unless a test says so
        raise AssertionError("unexpected SQL: " + sql)


def clean_props():
    return {"billregion": "ON"}


def base_tables(**extra):
    t = {"demographic": 40, "provider": 3, "appointment": 100,
         "casemgmt_note": 200, "document": 10, "drugs": 50,
         "Facility": 1, "clinic": 1}
    # every table the import cannot run without: a real clinic dump has
    # them, and their absence is now its own blocker
    for name in pf.REQUIRED_TABLES:
        t.setdefault(name, 1)
    t.update(extra)
    return t


class TestVerdicts(unittest.TestCase):

    def test_clean_database_is_go(self):
        report = pf.run_checks(FakeDb(base_tables()),
                               properties=clean_props())
        self.assertEqual(report["verdict"], "go")
        self.assertEqual(report["exit_code"], 0)
        self.assertEqual(report["required_accepts"], [])

    def test_patient_data_table_blocks_with_accept_flag(self):
        db = FakeDb(base_tables(formONAR=12))
        report = pf.run_checks(db, properties=clean_props())
        self.assertEqual(report["verdict"], "go-with-acknowledgements")
        self.assertEqual(report["exit_code"], 1)
        self.assertIn("archived-forms", report["required_accepts"])
        f = [x for x in report["findings"] if x["id"] == "B1-patient-data"][0]
        self.assertEqual(f["data"], {"formONAR": 12})

    def test_accept_flag_acknowledges_the_blocker(self):
        db = FakeDb(base_tables(formONAR=12))
        report = pf.run_checks(db, properties=clean_props(),
                               accepted=["archived-forms"])
        self.assertEqual(report["verdict"], "go")
        self.assertIn("B1-patient-data", report["acknowledged"])

    def test_empty_patient_table_is_not_a_blocker(self):
        report = pf.run_checks(FakeDb(base_tables(formONAR=0)),
                               properties=clean_props())
        self.assertEqual(report["verdict"], "go")

    def test_unknown_table_with_rows_blocks(self):
        db = FakeDb(base_tables(well_custom_widget=7))
        report = pf.run_checks(db, properties=clean_props())
        self.assertEqual(report["verdict"], "go-with-acknowledgements")
        self.assertIn("unknown-as-archive", report["required_accepts"])

    def test_unknown_table_with_unusual_name_is_still_counted(self):
        db = FakeDb(base_tables(**{"custom$table": 3}))
        report = pf.run_checks(db, clean_props())
        ids = {f["id"] for f in report["findings"]}
        self.assertIn("B2-unknown-tables", ids)
        self.assertTrue(any("`custom$table`" in q for q in db.queries))

    def test_a_dump_missing_a_required_table_is_refused_here(self):
        # the ETL refuses it too, but only at P4 — after the pre-import
        # snapshot and the full staging restore
        tables = base_tables()
        del tables["secObjectName"]
        report = pf.run_checks(FakeDb(tables), properties=clean_props())
        f = [x for x in report["findings"]
             if x["id"] == "required-tables-missing"][0]
        self.assertEqual(f["severity"], pf.BLOCKER)
        self.assertIsNone(f.get("accept"))
        self.assertIn("secObjectName", f["data"])
        self.assertEqual(report["verdict"], "no-go")

    def test_a_name_outside_the_identifier_class_is_refused_here(self):
        # the ETL refuses it before its first write and no flag clears
        # it, so counting it as "unknown, acknowledge it" would send the
        # operator to a refusal the sign-off cannot reach
        db = FakeDb(base_tables(**{"demographic bak 2019": 3}))
        report = pf.run_checks(db, properties=clean_props(),
                               accepted=["unknown-as-archive"])
        f = [x for x in report["findings"]
             if x["id"] == "identifier-class"][0]
        self.assertIn("demographic bak 2019", f["data"])
        self.assertIsNone(f.get("accept"))
        self.assertEqual(report["verdict"], "no-go")

    def test_an_absent_column_is_reported_not_a_no_go(self):
        # o19etl has a branch for a Facility table with no `disabled`
        # column; the assessment must not refuse that clinic outright,
        # and must not diagnose it as a privilege problem
        class NoDisabled(FakeDb):
            def __call__(self, sql):
                if "`Facility`" in sql and "disabled" in sql:
                    raise RuntimeError(
                        "ERROR 1054 (42S22): Unknown column 'disabled' "
                        "in 'where clause'")
                return FakeDb.__call__(self, sql)
        report = pf.run_checks(NoDisabled(base_tables()),
                               properties=clean_props())
        ids = {f["id"]: f for f in report["findings"]}
        self.assertNotIn("query-errors", ids)
        self.assertIn("Facility [disabled = 0]", ids["absent-objects"]["data"])
        self.assertEqual(ids["absent-objects"]["severity"], pf.INFO)

    def test_a_missing_properties_file_is_refused(self):
        # ldap.enabled is a no-accept refusal at import time, so an
        # assessment that skipped the file could return "go" for a clinic
        # whose every staff login breaks at cutover
        report = pf.run_checks(FakeDb(base_tables()), properties=None)
        f = [x for x in report["findings"] if x["id"] == "no-properties"][0]
        self.assertEqual(f["severity"], pf.BLOCKER)
        self.assertIsNone(f.get("accept"))
        self.assertEqual(report["verdict"], "no-go")

    def test_drop_class_rows_are_reported(self):
        # dropped with no archive and no CSV: the only silent loss in the
        # design, so it must at least be visible
        drop = sorted(t for t, c in pf.KNOWN_TABLES.items()
                      if c == "drop" and not t.upper().startswith("OLIS"))
        report = pf.run_checks(
            FakeDb(base_tables(**{drop[0]: 4})), properties=clean_props())
        f = [x for x in report["findings"]
             if x["id"] == "drop-tables-with-rows"][0]
        self.assertEqual(f["data"], {drop[0]: 4})
        self.assertEqual(f["severity"], pf.ADVISORY)

    def test_an_olis_nomenclature_table_raises_the_olis_blocker(self):
        # those two are drop-class, so an OLIS clinic that never wrote a
        # preference row used to raise nothing at all
        report = pf.run_checks(
            FakeDb(base_tables(OLISResultNomenclature=9)),
            properties=clean_props())
        self.assertIn("olis-gone", report["required_accepts"])

    def test_uncountable_table_is_a_hard_no_go(self):
        class Denied(FakeDb):
            def __call__(self, sql):
                if sql.startswith("SELECT COUNT(*) FROM `vendor_x`"):
                    raise RuntimeError("ERROR 1142: SELECT command denied")
                return FakeDb.__call__(self, sql)
        db = Denied(base_tables(vendor_x=5))
        report = pf.run_checks(db, clean_props(),
                               accepted=["unknown-as-archive"])
        self.assertEqual(report["verdict"], "no-go")
        errors = [f for f in report["findings"] if f["id"] == "query-errors"]
        self.assertEqual(len(errors), 1)
        self.assertIsNone(errors[0].get("accept"))
        self.assertIn("vendor_x", errors[0]["data"])

    def test_empty_unknown_table_is_ignored(self):
        report = pf.run_checks(FakeDb(base_tables(well_custom_widget=0)),
                               properties=clean_props())
        self.assertEqual(report["verdict"], "go")

    def test_dropped_column_usage_blocks(self):
        db = FakeDb(base_tables(),
                    where_counts={("drugs", "`dispensingUnits`"): 5})
        report = pf.run_checks(db, properties=clean_props())
        self.assertIn("dropped-columns", report["required_accepts"])
        f = [x for x in report["findings"]
             if x["id"] == "B3-dropped-columns"][0]
        self.assertEqual(f["data"], {"drugs.dispensingUnits": 5})

    def test_olis_preferences_block_with_olis_gone(self):
        db = FakeDb(base_tables(OLISSystemPreferences=1))
        report = pf.run_checks(db, properties=clean_props())
        self.assertIn("olis-gone", report["required_accepts"])

    def test_ldap_is_a_hard_no_go(self):
        props = clean_props()
        props["ldap.enabled"] = "true"
        report = pf.run_checks(FakeDb(base_tables()), properties=props)
        self.assertEqual(report["verdict"], "no-go")
        self.assertEqual(report["exit_code"], 2)
        # no accept flag clears it, even if every flag is thrown at it
        report = pf.run_checks(
            FakeDb(base_tables()), properties=props,
            accepted=["archived-forms", "unknown-as-archive",
                      "dropped-columns", "olis-gone", "charset-repair"])
        self.assertEqual(report["verdict"], "no-go")

    def test_encrypted_notes_are_a_hard_no_go(self):
        # judged on the rows: a note with a password is stored encrypted
        props = clean_props()
        report = pf.run_checks(
            FakeDb(base_tables(),
                   where_counts={("casemgmt_note",
                                  "password IS NOT NULL"): 3}),
            properties=props)
        self.assertEqual(report["verdict"], "no-go")
        ids = {f["id"]: f for f in report["findings"]}
        self.assertEqual(ids["B4-encrypted-notes"]["severity"], pf.BLOCKER)

    def test_stock_note_password_property_alone_is_not_a_blocker(self):
        # casemgmt.note.password.enabled=true is the shipped O19 default:
        # with no locked note it is an advisory, never a no-go
        props = clean_props()
        props["casemgmt.note.password.enabled"] = "true"
        report = pf.run_checks(FakeDb(base_tables()), properties=props)
        self.assertEqual(report["verdict"], "go")
        ids = {f["id"]: f for f in report["findings"]}
        self.assertNotIn("B4-encrypted-notes", ids)
        self.assertEqual(ids["notes-password-enabled"]["severity"],
                         pf.ADVISORY)

    def test_accept_typo_is_refused_not_recorded(self):
        with contextlib.redirect_stderr(io.StringIO()):
            rc = pf.main(["--db", "x", "--accept", "archive-forms"])
        self.assertEqual(rc, pf.EXIT_TOOL_ERROR)
        self.assertEqual(set(pf.ACCEPT_IDS),
                         {"archived-forms", "unknown-as-archive",
                          "olis-gone", "dropped-columns",
                          "carry-credentials", "charset-repair"})

    def test_live_credentials_are_a_blocker_cleared_by_sign_off(self):
        report = pf.run_checks(FakeDb(base_tables(ServiceClient=2,
                                                  oscarKeys=1)),
                               properties=clean_props())
        self.assertEqual(report["verdict"], "go-with-acknowledgements")
        ids = {f["id"]: f for f in report["findings"]}
        b9 = ids["B9-credentials-carried"]
        self.assertEqual(b9["severity"], pf.BLOCKER)
        self.assertEqual(b9["accept"], "carry-credentials")
        self.assertEqual(b9["data"], {"ServiceClient": 2, "oscarKeys": 1})
        self.assertIn("carry-credentials", report["required_accepts"])
        report = pf.run_checks(FakeDb(base_tables(ServiceClient=2)),
                               properties=clean_props(),
                               accepted=["carry-credentials"])
        self.assertEqual(report["verdict"], "go")
        # empty credential tables are not a finding
        report = pf.run_checks(FakeDb(base_tables(ServiceClient=0)),
                               properties=clean_props())
        self.assertNotIn("B9-credentials-carried",
                         {f["id"] for f in report["findings"]})

    def test_bc_province_is_a_hard_no_go(self):
        report = pf.run_checks(FakeDb(base_tables()),
                               properties=clean_props(), province="bc")
        self.assertEqual(report["verdict"], "no-go")


class TestAdvisories(unittest.TestCase):

    def test_removed_module_properties_group_as_advisory(self):
        props = clean_props()
        props["born_sftp_host"] = "x"
        props["born_sftp_password"] = "y"
        props["util.erx.enabled"] = "true"
        report = pf.run_checks(FakeDb(base_tables()), properties=props)
        self.assertEqual(report["verdict"], "go")
        f = [x for x in report["findings"]
             if x["id"] == "dropped-properties"][0]
        self.assertEqual(sorted(f["data"]["born"]),
                         ["born_sftp_host", "born_sftp_password"])
        self.assertIn("util.erx.", f["data"])

    def test_archive_config_rows_are_advisory_not_blocking(self):
        db = FakeDb(base_tables(report_template=4))
        report = pf.run_checks(db, properties=clean_props())
        self.assertEqual(report["verdict"], "go")
        f = [x for x in report["findings"] if x["id"] == "archive-config"][0]
        self.assertEqual(f["data"], {"report_template": 4})

    def test_mojibake_blocks_until_the_repair_is_acknowledged(self):
        # the ETL refuses outright without --accept charset-repair, and
        # it refuses at P4 — after the pre-import snapshot and the full
        # staging restore. A "go" here costs the clinic a cutover window.
        db = FakeDb(base_tables(),
                    where_counts={("demographic",
                                   pf.double_encoded_predicate("last_name")):
                                  3})
        report = pf.run_checks(db, properties=clean_props())
        f = [x for x in report["findings"]
             if x["id"] == "charset-mojibake"][0]
        self.assertEqual(f["severity"], pf.BLOCKER)
        self.assertEqual(f["accept"], "charset-repair")
        self.assertIn("demographic.last_name", f["data"])
        # a blocker with an accept class: the assessment now names the
        # flag the operator must carry into the import
        self.assertEqual(report["verdict"], "go-with-acknowledgements")
        self.assertIn("charset-repair", report["required_accepts"])
        cleared = pf.run_checks(db, properties=clean_props(),
                                accepted=("charset-repair",))
        self.assertEqual(cleared["verdict"], "go")


class TestRoleAdvisories(unittest.TestCase):
    """The M8 role/privilege findings inform the reconciliation the import
    performs; none of them may ever block."""

    def db(self):
        return FakeDb(
            base_tables(secRole=5, secUserRole=4, security=3, preventions=2,
                        eform=1, property=2, indicatorTemplate=1, Facility=1,
                        clinic=1),
            where_counts={
                ("secUserRole", "activeyn IS NULL"): 3,
                ("provider", "NOT IN (SELECT provider_no"): 2,
                ("security", "b_ExpireSet = 1"): 1,
                ("preventions", "BINARY prevention_type IN ('"): 2,
                ("Facility", "disabled = 0"): 1,
                ("eform", "Rich Text Letter"): 1,
                ("property", "name LIKE 'INTEGRATOR\\_%'"): 2,
            },
            rows={
                "SELECT role_name FROM `secRole`": [
                    ["doctor"], ["Triage Nurse"]],
                "FROM `indicatorTemplate`": [
                    ["1", "Old dashboard", "SELECT 1 FROM phr_documents"],
                    ["2", "Fine", "SELECT 1 FROM demographic"]],
            })

    def test_role_advisories_never_block(self):
        report = pf.run_checks(self.db(), properties=clean_props())
        self.assertEqual(report["verdict"], "go")
        ids = {f["id"]: f for f in report["findings"]}
        for fid in ("roles-custom", "roles-activeyn-null",
                    "roles-providers-without-active-role", "security-locked",
                    "prevention-legacy-types", "rtl-legacy-form",
                    "property-removed-module-keys",
                    "indicator-templates-dropped-refs"):
            self.assertIn(fid, ids, fid)
            self.assertEqual(ids[fid]["severity"], pf.ADVISORY, fid)
        # a list: a role name may carry a comma
        self.assertEqual(ids["roles-custom"]["data"]["roles"],
                         ["Triage Nurse"])
        self.assertEqual(ids["property-removed-module-keys"]["data"],
                         {"INTEGRATOR_": 2})
        self.assertEqual(ids["indicator-templates-dropped-refs"]["data"],
                         {"Old dashboard (id 1)": "phr_documents"})

    def test_clean_role_data_raises_no_role_advisory(self):
        db = FakeDb(base_tables(secRole=2, secUserRole=2, security=3),
                    rows={"SELECT role_name FROM `secRole`": [
                        ["doctor"], ["admin"]]})
        report = pf.run_checks(db, properties=clean_props())
        ids = {f["id"] for f in report["findings"]}
        for fid in ("roles-custom", "roles-activeyn-null", "security-locked",
                    "rtl-legacy-form", "prevention-legacy-types"):
            self.assertNotIn(fid, ids)

    def test_missing_facility_or_clinic_blocks(self):
        db = FakeDb(base_tables(Facility=1, clinic=0),
                    where_counts={("Facility", "disabled = 0"): 0})
        report = pf.run_checks(db, properties=clean_props())
        ids = {f["id"]: f for f in report["findings"]}
        self.assertEqual(ids["facility-none-enabled"]["severity"], pf.BLOCKER)
        self.assertEqual(ids["clinic-missing"]["severity"], pf.BLOCKER)
        self.assertEqual(report["verdict"], "no-go")
        # a dump without the tables at all is the same refusal
        db = FakeDb({"demographic": 40, "provider": 3})
        report = pf.run_checks(db, properties=clean_props())
        ids = {f["id"]: f for f in report["findings"]}
        self.assertEqual(ids["facility-none-enabled"]["severity"], pf.BLOCKER)
        self.assertEqual(ids["clinic-missing"]["severity"], pf.BLOCKER)
        self.assertEqual(report["verdict"], "no-go")
        self.assertEqual(ids["facility-none-enabled"]["title"],
                         "no Facility table")
        self.assertEqual(ids["clinic-missing"]["title"], "no clinic table")

    def test_stock_roles_are_recognised_case_insensitively(self):
        db = FakeDb(base_tables(secRole=2),
                    rows={"SELECT role_name FROM `secRole`": [
                        ["Doctor"], ["ADMIN"]]})
        report = pf.run_checks(db, properties=clean_props())
        self.assertNotIn("roles-custom",
                         {f["id"] for f in report["findings"]})

    def test_client_batch_escapes_are_decoded_per_value(self):
        self.assertEqual(pf._unescape_batch("a\\tb\\nc\\\\d"),
                         "a\tb\nc\\d")
        # the client prints SQL NULL as the word NULL; a stored backslash-N
        # arrives escaped and decodes to the two characters
        self.assertEqual(pf._unescape_batch("NULL"), "NULL")
        self.assertEqual(pf._unescape_batch("\\\\N"), "\\N")
        # the reason it matters: a line break before the table name
        self.assertEqual(pf.dropped_table_references(
            pf._unescape_batch("SELECT 1\\nFROM\\tphr_documents"),
            ["phr_documents"]), ["phr_documents"])
        self.assertEqual(pf.dropped_table_references(
            "SELECT 1\\nFROM\\tphr_documents", ["phr_documents"]), [])

    def test_role_advisory_texts_state_the_exceptions(self):
        report = pf.run_checks(self.db(), properties=clean_props())
        ids = {f["id"]: f for f in report["findings"]}
        self.assertIn("granting nothing is left",
                      ids["roles-custom"]["detail"])
        self.assertIn("cannot render",
                      ids["prevention-legacy-types"]["detail"])
        # the same predicate as P7: only providers WITH a login
        self.assertIn("account(s)",
                      ids["roles-providers-without-active-role"]["title"])

    def test_dropped_table_scan_matches_whole_words_only(self):
        self.assertEqual(pf.dropped_table_references(
            "select * from phr_documents_x, phr_documents", ["phr_documents"]),
            ["phr_documents"])
        # case-insensitive: lower_case_table_names hosts spell either way
        self.assertEqual(pf.dropped_table_references(
            "select * from PHR_DOCUMENTS", ["phr_documents"]),
            ["phr_documents"])
        self.assertEqual(pf.dropped_table_references(None, ["x"]), [])
        self.assertEqual(pf._like_prefix("util.erx."), "util.erx.%")
        self.assertEqual(pf._like_prefix("INTEGRATOR_"), "INTEGRATOR\\_%")


class TestTableCaseHandling(unittest.TestCase):

    def test_lower_cased_tables_fold_onto_the_manifest_with_columns(self):
        # lower_case_table_names=1: information_schema reports
        # `hl7textmessage`; counts AND column metadata must map onto the
        # manifest spelling
        from carlos_ctl import o19map_schema
        t = base_tables()
        t["hl7textmessage"] = 3
        cols = {"hl7textmessage": set(
            o19map_schema.TABLES["hl7TextMessage"]["cols"]) | {"vendor_x"}}
        db = FakeDb(t, columns=cols)
        report = pf.run_checks(db, properties=clean_props(),
                               schema_map=o19map_schema)
        inv = [x for x in report["findings"] if x["id"] == "inventory"][0]
        self.assertEqual(inv["data"].get("hl7TextMessage"), 3)
        f = [x for x in report["findings"]
             if x["id"] == "B2-unknown-columns"][0]
        self.assertEqual(f["data"], {"hl7TextMessage": ["vendor_x"]})
        self.assertFalse(any(x["id"] == "case-colliding-tables"
                             for x in report["findings"]))

    def test_case_twins_on_a_sensitive_server_are_a_blocker(self):
        t = base_tables()
        t["Demographic"] = 5  # vendor twin next to the real `demographic`
        report = pf.run_checks(FakeDb(t), properties=clean_props())
        f = [x for x in report["findings"]
             if x["id"] == "case-colliding-tables"][0]
        self.assertEqual(f["severity"], pf.BLOCKER)
        self.assertEqual(f["data"], {"demographic": ["Demographic"]})
        self.assertEqual(report["verdict"], "no-go")
        # the exact spelling still counts as the manifest table
        inv = [x for x in report["findings"] if x["id"] == "inventory"][0]
        self.assertEqual(inv["data"].get("demographic"), 40)


class TestImportMode(unittest.TestCase):

    def test_unknown_columns_block_when_schema_map_given(self):
        from carlos_ctl import o19map_schema
        cols = {"demographic": set(
            o19map_schema.TABLES["demographic"]["cols"])}
        cols["demographic"].add("well_extra_field")
        db = FakeDb(base_tables(), columns=cols)
        report = pf.run_checks(db, properties=clean_props(),
                               schema_map=o19map_schema)
        f = [x for x in report["findings"]
             if x["id"] == "B2-unknown-columns"][0]
        self.assertEqual(f["data"], {"demographic": ["well_extra_field"]})
        self.assertIn("unknown-as-archive", report["required_accepts"])

    def test_standalone_mode_defers_column_checks(self):
        report = pf.run_checks(FakeDb(base_tables()),
                               properties=clean_props())
        ids = [x["id"] for x in report["findings"]]
        self.assertIn("column-checks-deferred", ids)


class TestReportContract(unittest.TestCase):

    def test_report_carries_manifest_version_and_inventory(self):
        report = pf.run_checks(FakeDb(base_tables()),
                               properties=clean_props())
        self.assertEqual(report["schema_map_version"], pf.SCHEMA_MAP_VERSION)
        inv = [x for x in report["findings"] if x["id"] == "inventory"][0]
        self.assertEqual(inv["data"]["demographic"], 40)
        self.assertEqual(inv["data"]["_database_mb"], 123)

    def test_text_rendering_names_accept_flags_and_review_note(self):
        db = FakeDb(base_tables(formONAR=12))
        text = pf.render_text(pf.run_checks(db, properties=clean_props()))
        self.assertIn("--accept archived-forms", text)
        self.assertIn("technical review", text)
        self.assertIn("go-with-acknowledgements", text)

    def test_interactive_password_arg_is_detected(self):
        self.assertEqual(pf.interactive_password_arg(["-uroot", "-p"]), "-p")
        self.assertEqual(pf.interactive_password_arg(["--password"]),
                         "--password")
        self.assertIsNone(pf.interactive_password_arg(
            ["-uroot", "--defaults-extra-file=/root/.my.cnf"]))

    # the fake credential is assembled at runtime so the source never holds
    # a literal "--password=<value>" (secret scanners flag the pattern)
    FAKE_PASSWORD = "fixture" + "-only-value"

    def test_password_arg_problem_never_echoes_the_value(self):
        self.assertIn("interactive", pf.password_arg_problem(["-p"]))
        for args in (["-p" + self.FAKE_PASSWORD],
                     ["--password=" + self.FAKE_PASSWORD]):
            problem = pf.password_arg_problem(args)
            self.assertIn("password in argv", problem)
            self.assertNotIn(self.FAKE_PASSWORD, problem)
        self.assertIsNone(pf.password_arg_problem(
            ["-uroot", "--protocol=socket", "--defaults-extra-file=/x"]))

    def test_main_refuses_password_arguments_as_a_tool_error(self):
        import io
        import contextlib
        for bad in ("-p", "--password=" + self.FAKE_PASSWORD,
                    "-p" + self.FAKE_PASSWORD):
            err = io.StringIO()
            with contextlib.redirect_stderr(err):
                rc = pf.main(["--db", "x", "--mysql-arg=-uroot",
                              "--mysql-arg=" + bad])
            # exit 3 is reserved for tool errors so it can never be read
            # as a verdict (0 go / 1 acknowledgements / 2 no-go)
            self.assertEqual(rc, pf.EXIT_TOOL_ERROR)
            self.assertIn("--mysql-password-file", err.getvalue())
            self.assertNotIn(self.FAKE_PASSWORD, err.getvalue())

    def test_bad_cli_argument_is_a_tool_error_and_help_exits_zero(self):
        import io
        import contextlib
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            rc = pf.main(["--no-such-flag"])
        self.assertEqual(rc, pf.EXIT_TOOL_ERROR)
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(pf.main(["--help"]), 0)
        self.assertIn("--db", out.getvalue())

    def test_unreadable_properties_is_a_tool_error(self):
        import io
        import contextlib
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            rc = pf.main(["--db", "x", "--properties",
                          "/nonexistent/oscar.properties"])
        self.assertEqual(rc, pf.EXIT_TOOL_ERROR)

    def test_double_encoded_predicate_is_byte_aligned(self):
        p = pf.double_encoded_predicate("last_name")
        # the value is normalised to utf8mb4 before every comparison: O19
        # tables are usually latin1, and BINARY-comparing across charsets
        # would compare different byte strings (every row 'clean')
        # a byte-vs-character length test, never REGEXP: the Spencer
        # engine of MySQL < 8 / MariaDB < 10.0.5 (the assessment hosts)
        # reads a \x class as a literal bracket expression
        self.assertIn("LENGTH(CONVERT(`last_name` USING utf8mb4)) <> "
                      "CHAR_LENGTH(CONVERT(`last_name` USING utf8mb4))", p)
        self.assertNotIn("REGEXP", p)
        self.assertIn("CONVERT(CONVERT(`last_name` USING utf8mb4) USING "
                      "latin1)", p)
        self.assertNotIn("HEX(", p)
        self.assertNotIn("C383", p)

    def test_properties_parser_line_terminators_match_java(self):
        # only \n, \r, \r\n end a line: a Windows-1252 ellipsis (0x85 read
        # through latin-1) or a form feed stays inside the value; leading
        # NBSP is part of the key; a final continuation keeps the record
        props = pf.parse_properties_text(
            "Support_Contact=Call us\x85 ext 12\r\nk2=a\x0cb\r\n"
            "\xa0odd=1\nlast=v\\")
        self.assertEqual(props["Support_Contact"], "Call us\x85 ext 12")
        self.assertEqual(props["k2"], "a\x0cb")
        self.assertIn("\xa0odd", props)
        self.assertEqual(props["last"], "v")
        self.assertNotIn("ext", props)
        # a \uD83D\uDE00 pair is one character, as in Java
        self.assertEqual(pf.parse_properties_text(
            "smile=\\uD83D\\uDE00")["smile"], "\U0001F600")

    def test_properties_parser_has_java_semantics(self):
        props = pf.parse_properties_text(
            "a=1 \nb : two\nc=x\\\n  y\n#z=9\nd=\\u0041\n")
        self.assertEqual(props, {"a": "1 ", "b": "two", "c": "xy",
                                 "d": "A"})

    def test_generated_data_is_populated(self):
        self.assertNotEqual(pf.SCHEMA_MAP_VERSION, "unpopulated")
        self.assertTrue(pf.PATIENT_DATA_TABLES)
        self.assertTrue(pf.KNOWN_TABLES)


if __name__ == "__main__":
    unittest.main()
