# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The run_roles driver against a fake database: what the ledger records
survives a crash between a write and its mark, a resume re-runs nothing
that completed, --role-template is bound to the ledger like --admin-user,
the Facility/clinic refusals fire before that step's writes, and the RTL
outcome is verified against the rows rather than asserted.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import os
import shutil
import tempfile
import unittest

from carlos_ctl import o19_preflight, o19etl, o19map_schema, o19roles

SRC, DST, ARCH = "o19_import", "carlos", "o19_archive"
SNAP = "`{0}`.`carlos_seed_secObjPrivilege`".format(ARCH)

SEED_ROWS = [["doctor", "_rx", "x", "0"], ["doctor", "_fax", "x", "0"],
             ["nurse", "_rx", "r", "0"], ["nurse", "_fax", "x", "0"],
             ["admin", "_admin", "x", "0"], ["-1", "_fax", "x", "0"]]
STAGE_ROWS = [["doctor", "_rx", "x", "0"], ["Triage Nurse", "_rx", "r", "0"],
              ["Triage Nurse", "_tickler", "x", "0"],
              ["_all", "_eChart$5", "|or|", "0"]]
STAGE_OBJECTS = [["_rx"], ["_tickler"], ["_admin"]]
TARGET_ROLES = [["doctor"], ["admin"], ["nurse"], ["Triage Nurse"], ["123"]]
RTL_LEGACY = [["12", "Rich Text Letter", "1",
               "Rich Text Letter Generator v2.1", "0", "1"]]
RTL_MODERN = [["12", "Rich Text Letter", "1",
               "Rich Text Letter Generator 2026.3.0", "1", "0"]]


class FakeDb(object):
    """Answers the reads run_roles issues (by exact builder output where
    possible) and records every write. The twinless counts model the
    database AFTER the appends, whether or not the write ran in this
    process — that is the state a resume finds."""

    def __init__(self, **over):
        self.writes = []
        self.fail_on = over.pop("fail_on", None)
        self.rtl_sequence = list(over.pop("rtl_sequence",
                                          [RTL_LEGACY, RTL_MODERN]))
        self.answers = {
            "twinless": {"secRole": 3, "provider_facility": 2, "program": 1,
                         "program_provider": 5},
            "counts": {"secRole": 36, "provider_facility": 8},
            "facility": 1, "clinic": 1,
            "activeyn_candidates": [["999902"]], "activeyn_left": 1,
            "without_membership": 5, "fallback": [["999903"]],
            "pending": 1, "property_counts": {"INTEGRATOR_": 1},
            "prevention_counts": {"Flu": 1}, "unknown": [["Weird", "2"]],
        }
        self.answers.update(over)
        prune = o19roles.property_prune_statements(
            DST, o19_preflight.DROPPED_PROP_PREFIXES)
        self.prune_counts = {c: self.answers["property_counts"].get(p, 0)
                             for p, c, _d in prune}
        prev = o19roles.prevention_type_statements(
            DST, o19map_schema.PREVENTION_TYPE_MAP)
        self.prev_counts = {c: self.answers["prevention_counts"].get(l, 0)
                            for l, _k, c, _u in prev}

    # -- the ETL executor: writes only
    def query(self, sql, db=None):
        if self.fail_on and self.fail_on in sql:
            self.fail_on = None
            raise o19etl.QueryError("planted failure", "boom")
        self.writes.append(sql)
        return []

    # -- the plain client: reads
    def plain(self, sql, db=None):
        a = self.answers
        for table, n in a["twinless"].items():
            if sql == o19etl.appended_row_count_sql(table, SRC, DST):
                return [[str(n)]]
        for table, n in a["counts"].items():
            if sql == "SELECT COUNT(*) FROM `{0}`.`{1}`".format(DST, table):
                return [[str(n)]]
        if sql.startswith("SELECT role_name FROM `{0}`.secRole WHERE "
                          "role_name NOT IN".format(DST)):
            return [["HRMAdmin"], ["Partner Doctor"], ["Site Manager"]]
        if sql == o19roles.enabled_facility_count_sql(DST):
            return [[str(a["facility"])]]
        if sql == o19roles.clinic_count_sql(DST):
            return [[str(a["clinic"])]]
        if sql == o19roles.activeyn_candidates_sql(DST):
            return a["activeyn_candidates"]
        if sql == o19roles.activeyn_null_remaining_sql(DST):
            return [[str(a["activeyn_left"])]]
        if sql == o19roles.providers_without_membership_sql(DST):
            return [[str(a["without_membership"])]]
        if sql == o19roles.fallback_membership_candidates_sql(DST):
            return a["fallback"]
        if sql == ("SELECT roleUserGroup, objectName, privilege, priority "
                   "FROM " + SNAP):
            return SEED_ROWS
        if sql == ("SELECT roleUserGroup, objectName, privilege, priority "
                   "FROM `{0}`.secObjPrivilege".format(SRC)):
            return STAGE_ROWS
        if sql == "SELECT objectName FROM `{0}`.secObjectName".format(SRC):
            return STAGE_OBJECTS
        if sql == "SELECT role_name FROM `{0}`.secRole".format(DST):
            return TARGET_ROLES
        if sql.startswith("SELECT COUNT(*) FROM " + SNAP + " s WHERE "
                          "s.roleUserGroup = "):
            return [[str(a["pending"])]]
        if sql == o19roles.privilege_diff_sql(SRC, ARCH):
            return [["doctor", "_billing", "r", "0", "x", "0"]]
        if sql == o19roles.stock_role_appends_sql(
                SRC, ARCH, o19map_schema.STOCK_ROLE_NAMES):
            return [["admin", "_admin.consult", "x", "0"]]
        if sql == o19roles.excluded_grants_sql(SRC):
            return [["nurse", "_admin.traceability", "x"]]
        if sql in self.prune_counts:
            return [[str(self.prune_counts[sql])]]
        if sql in self.prev_counts:
            return [[str(self.prev_counts[sql])]]
        if sql == o19roles.unknown_prevention_types_sql(
                DST, o19map_schema.KNOWN_PREVENTION_TYPES):
            return a["unknown"]
        if sql == o19roles.rtl_rows_sql(DST):
            if len(self.rtl_sequence) > 1:
                return self.rtl_sequence.pop(0)
            return self.rtl_sequence[0]
        raise AssertionError("unexpected read: " + sql)


class RunRolesBase(unittest.TestCase):

    def setUp(self):
        self.state_dir = tempfile.mkdtemp(prefix="o19roles-test-")
        self.fixups = os.path.join(self.state_dir, "fixups")
        os.makedirs(self.fixups)
        for name in (o19roles.RTL_SEED_SCRIPT,) + o19roles.RTL_FIXUP_SCRIPTS:
            with open(os.path.join(self.fixups, name), "w") as fh:
                fh.write("-- " + name + "\nSELECT 1;\n")
        self.reports = []

    def tearDown(self):
        shutil.rmtree(self.state_dir, ignore_errors=True)

    def ctx(self, db, **over):
        c = {"query_etl": db.query, "query": db.plain, "src_schema": SRC,
             "target_db": DST, "archive_schema": ARCH,
             "report": self.reports.append, "state_dir": self.state_dir,
             "role_templates": {}, "fixups_dir": self.fixups}
        c.update(over)
        return c

    def run_roles(self, db, progress=None, **over):
        progress = progress if progress is not None else {"tables": {}}
        saves = []
        o19roles.run_roles(self.ctx(db, **over), progress,
                           lambda: saves.append(len(progress["roles"])))
        return progress, saves

    def private(self, name):
        path = os.path.join(self.state_dir, name)
        with open(path, encoding="utf-8") as fh:
            return fh.read()


class TestCleanRun(RunRolesBase):

    def test_every_step_marks_and_the_ledger_records_twinless_counts(self):
        db = FakeDb()
        progress, saves = self.run_roles(db)
        ledger = progress["roles"]
        for key in ("roles_appended", "facility_links", "activeyn", "program",
                    "backfill", "diff", "property_pruned", "prevention_types",
                    "rtl"):
            self.assertTrue(ledger.get(key), key)
        # what row_parity will measure, not a before/after delta
        self.assertEqual(ledger["appended"],
                         {"secRole": 3, "provider_facility": 2, "program": 1,
                          "program_provider": 5})
        self.assertTrue(saves)  # save() called for every mark

    def test_writes_are_the_builders_output_in_order(self):
        db = FakeDb()
        self.run_roles(db)
        w = db.writes
        self.assertEqual(w[:2], o19roles.guaranteed_role_statements(DST))
        self.assertEqual(w[2], o19roles.carlos_role_append_statement(DST,
                                                                     ARCH))
        self.assertIn(o19roles.provider_facility_statement(DST), w)
        self.assertIn(o19roles.activeyn_update_statement(DST), w)
        self.assertIn(o19roles.oscar_program_statement(DST), w)
        for sql in o19roles.membership_statements(DST):
            self.assertIn(sql, w)
        # Triage Nurse resembles nurse (shares _rx r); era = {_fax}
        self.assertIn(o19roles.backfill_statement(DST, ARCH, "Triage Nurse",
                                                  "nurse", ["_fax"]), w)
        self.assertTrue(any(sql.startswith("DELETE FROM `carlos`.property")
                            for sql in w))
        self.assertTrue(any("BINARY prevention_type = 'Flu'" in sql
                            for sql in w))
        # the four packaged scripts, fed to the ETL executor
        self.assertEqual([sql for sql in w if sql.startswith("-- update-")],
                         ["-- {0}\nSELECT 1;\n".format(n) for n in
                          o19roles.RTL_FIXUP_SCRIPTS])

    def test_private_files_carry_the_identifiers_and_the_report_does_not(
            self):
        db = FakeDb()
        self.run_roles(db)
        details = self.private("roles-details.txt")
        self.assertIn("999902", details)   # activated assignment
        self.assertIn("999903", details)   # least-privilege membership
        report = "\n".join(self.reports)
        self.assertNotIn("999902", report)
        self.assertNotIn("999903", report)
        self.assertIn("1 the least-privileged clinic role", report)
        diff = self.private("privilege-diff.txt")
        self.assertIn("doctor | _billing | r/0 -> x/0", diff)
        self.assertIn("admin | _admin.consult | x/0", diff)
        self.assertIn("nurse | _admin.traceability | x", diff)
        self.assertIn("secRole rows named like non-role groups", report)
        self.assertIn("123", report)  # a role name, not an identifier
        self.assertIn("modernised to 2026.3.0", report)
        self.assertIn("Weird (2)", report)

    def test_rerun_over_a_complete_ledger_writes_nothing(self):
        db = FakeDb()
        progress, _ = self.run_roles(db)
        before = dict(progress["roles"])
        n_writes = len(db.writes)
        self.run_roles(db, progress)
        self.assertEqual(len(db.writes), n_writes)
        self.assertEqual(progress["roles"], before)


class TestCrashAndResume(RunRolesBase):

    def test_crash_between_write_and_mark_records_the_full_count(self):
        # the membership INSERT commits, the process dies before mark():
        # the resume finds the rows already there and must record them
        first = o19roles.membership_statements(DST)[1]
        db = FakeDb(fail_on=first)
        progress = {"tables": {}}
        with self.assertRaises(o19etl.QueryError):
            self.run_roles(db, progress)
        ledger = progress["roles"]
        self.assertTrue(ledger.get("activeyn"))
        self.assertFalse(ledger.get("program"))
        # the plan (who gets the fallback role) was persisted before the
        # write, so the private list and the counts survive the crash
        self.assertIn("program_plan", ledger)
        self.run_roles(db, progress)
        self.assertTrue(progress["roles"]["program"])
        self.assertEqual(progress["roles"]["appended"]["program_provider"], 5)
        self.assertEqual(self.private("roles-details.txt").count("999903"), 1)

    def test_facility_refusal_fires_before_that_steps_writes(self):
        db = FakeDb(facility=0)
        progress = {"tables": {}}
        with self.assertRaises(SystemExit):
            self.run_roles(db, progress)
        self.assertTrue(progress["roles"]["roles_appended"])
        self.assertFalse(progress["roles"].get("facility_links"))
        self.assertNotIn(o19roles.provider_facility_statement(DST), db.writes)


class TestRoleTemplateBinding(RunRolesBase):

    def test_first_run_records_the_mapping_and_uses_it(self):
        db = FakeDb()
        progress, _ = self.run_roles(
            db, role_templates={"triage nurse": "Doctor"})
        self.assertEqual(progress["roles"]["role_templates"],
                         {"triage nurse": "Doctor"})
        # normalised to the exact spellings of the tables
        self.assertIn(o19roles.backfill_statement(DST, ARCH, "Triage Nurse",
                                                  "doctor", ["_fax"]),
                      db.writes)
        self.assertEqual(progress["roles"]["backfill"]["templates"],
                         {"Triage Nurse": "doctor"})

    def test_resume_with_a_different_mapping_is_refused(self):
        db = FakeDb()
        progress, _ = self.run_roles(
            db, role_templates={"Triage Nurse": "doctor"})
        del progress["roles"]["backfill"]
        with self.assertRaises(SystemExit):
            self.run_roles(db, progress, role_templates={"Triage Nurse":
                                                         "nurse"})

    def test_resume_without_the_flag_continues_with_the_recorded_mapping(
            self):
        db = FakeDb()
        progress, _ = self.run_roles(
            db, role_templates={"Triage Nurse": "doctor"})
        del progress["roles"]["backfill"]
        n = len(db.writes)
        self.run_roles(db, progress)
        self.assertEqual(db.writes[n], o19roles.backfill_statement(
            DST, ARCH, "Triage Nurse", "doctor", ["_fax"]))

    def test_unknown_template_dies_with_a_resume_hint(self):
        db = FakeDb()
        with self.assertRaises(SystemExit):
            self.run_roles(db, role_templates={"Nobody": "doctor"})

    def test_flag_after_backfill_is_reported_not_ignored(self):
        db = FakeDb()
        progress, _ = self.run_roles(
            db, role_templates={"Triage Nurse": "doctor"})
        self.reports[:] = []
        self.run_roles(db, progress, role_templates={"Triage Nurse":
                                                     "doctor"})
        self.assertTrue(any("already applied" in r for r in self.reports))


class TestRichTextLetterOutcome(RunRolesBase):

    def test_scripts_that_leave_no_current_row_are_not_claimed(self):
        db = FakeDb(rtl_sequence=[RTL_LEGACY, RTL_LEGACY])
        progress, _ = self.run_roles(db)
        outcome = progress["roles"]["rtl"]["outcome"]
        self.assertIn("apply by hand", outcome)
        self.assertNotIn("modernised", outcome)

    def test_missing_scripts_are_an_outcome_not_a_crash(self):
        os.remove(os.path.join(self.fixups, o19roles.RTL_MODERNIZE_SCRIPT))
        db = FakeDb()
        progress, _ = self.run_roles(db)
        self.assertIn(o19roles.RTL_MODERNIZE_SCRIPT,
                      progress["roles"]["rtl"]["outcome"])
        self.assertFalse(any(sql.startswith("-- update-") for sql in
                             db.writes))

    def test_current_form_runs_nothing(self):
        db = FakeDb(rtl_sequence=[RTL_MODERN])
        progress, _ = self.run_roles(db)
        self.assertEqual(progress["roles"]["rtl"]["scripts"], [])
        self.assertEqual(progress["roles"]["rtl"]["outcome"], "unchanged")


if __name__ == "__main__":
    unittest.main()
