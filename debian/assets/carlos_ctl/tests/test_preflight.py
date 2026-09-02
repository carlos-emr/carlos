# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Preflight verdict contract tests (docs plan §6.1).

A fake query callable serves canned information_schema and COUNT results,
so every blocker/advisory classification and the verdict/exit-code/accept
contract is pinned without a database.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import unittest

from carlos_ctl import o19_preflight as pf


class FakeDb(object):
    """Serves the exact SQL shapes run_checks() issues."""

    def __init__(self, tables=None, where_counts=None, columns=None):
        # tables: {name: rowcount}; where_counts: {(table, substring): n}
        self.tables = dict(tables or {})
        self.where_counts = dict(where_counts or {})
        self.columns = dict(columns or {})
        self.queries = []

    def __call__(self, sql):
        self.queries.append(sql)
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
        raise AssertionError("unexpected SQL: " + sql)


def clean_props():
    return {"billregion": "ON"}


def base_tables(**extra):
    t = {"demographic": 40, "provider": 3, "appointment": 100,
         "casemgmt_note": 200, "document": 10, "drugs": 50}
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
        props = clean_props()
        props["casemgmt.note.password.enabled"] = "true"
        report = pf.run_checks(FakeDb(base_tables()), properties=props)
        self.assertEqual(report["verdict"], "no-go")

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

    def test_missing_properties_is_an_advisory(self):
        report = pf.run_checks(FakeDb(base_tables()), properties=None)
        ids = [x["id"] for x in report["findings"]]
        self.assertIn("no-properties", ids)

    def test_archive_config_rows_are_advisory_not_blocking(self):
        db = FakeDb(base_tables(report_template=4))
        report = pf.run_checks(db, properties=clean_props())
        self.assertEqual(report["verdict"], "go")
        f = [x for x in report["findings"] if x["id"] == "archive-config"][0]
        self.assertEqual(f["data"], {"report_template": 4})

    def test_mojibake_sampling_is_advisory(self):
        db = FakeDb(base_tables(),
                    where_counts={("demographic",
                                   pf.double_encoded_predicate("last_name")):
                                  3})
        report = pf.run_checks(db, properties=clean_props())
        f = [x for x in report["findings"]
             if x["id"] == "charset-mojibake"][0]
        self.assertEqual(f["severity"], pf.ADVISORY)
        self.assertIn("demographic.last_name", f["data"])


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
        self.assertIn("CONVERT(`last_name` USING utf8mb4) REGEXP "
                      "'[^\\\\x00-\\\\x7F]'", p)
        self.assertIn("CONVERT(CONVERT(`last_name` USING utf8mb4) USING "
                      "latin1)", p)
        self.assertNotIn("HEX(", p)
        self.assertNotIn("C383", p)

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
