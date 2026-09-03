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

import contextlib
import io
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
               "Rich Text Letter Generator v2.1", "0", "1", "1"]]
RTL_MODERN = [["12", "Rich Text Letter", "1",
               "Rich Text Letter Generator 2026.3.0", "1", "0", "1"]]
RTL_DISABLED = [["12", "Rich Text Letter", "0",
                 "Rich Text Letter Generator v2.1", "0", "1", "1"]]
RTL_ENABLED_MODERN = [["12", "Rich Text Letter", "1",
                       "Rich Text Letter Generator 2026.3.0", "1", "0",
                       "1"]]


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
                         "program_provider": 5, "eform": 0},
            "counts": {"secRole": 36, "provider_facility": 8,
                       "program_provider": 40},
            "facility": 1, "clinic": 1,
            "activeyn_candidates": [["999902", "doctor"]],
            "admin_left": [["999904"]], "activeyn_left": 1,
            "dangling": [["999905", "Site Manager"]],
            "without_membership": 5, "fallback": [["999903"]],
            "pending": 1, "property_counts": {"INTEGRATOR_": 1},
            "prevention_counts": {"Flu": 1}, "unknown": [["Weird", "2"]],
            "restored": [["receptionist", "_billing", "r", "0"]],
        }
        self.answers.update(over)
        prune = o19roles.property_prune_statements(
            DST, o19_preflight.DROPPED_PROP_PREFIXES,
            o19_preflight.DROPPED_PROP_KEYS)
        self.prune_counts = {c: self.answers["property_counts"].get(p, 0)
                             for p, c, _d in prune}
        prev = o19roles.prevention_type_statements(
            DST, o19map_schema.PREVENTION_TYPE_MAP)
        self.prev_counts = {c: self.answers["prevention_counts"].get(code, 0)
                            for code, _k, c, _u in prev}

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
        if sql == o19roles.activeyn_admin_left_sql(DST):
            return a["admin_left"]
        if sql == o19roles.dangling_role_assignments_sql(
                DST, ["HRMAdmin", "Partner Doctor", "Site Manager"]):
            return a["dangling"]
        if sql == o19roles.restored_seed_grants_sql(SRC, ARCH):
            return a["restored"]
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
        if sql == o19roles.role_spelling_drift_sql(DST):
            return [[str(a.get("spelling_drift", 0))]]
        if sql == o19roles.comma_named_roles_sql(DST):
            return a.get("comma_roles", [])
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
                          "program_provider": 5, "eform": 0})
        # the RTL plan is persisted before the first write
        self.assertEqual(ledger["rtl_plan"][1],
                         list(o19roles.RTL_FIXUP_SCRIPTS))
        self.assertTrue(saves)  # save() called for every mark

    def test_the_spelling_step_reports_the_drift_it_repaired(self):
        # the security-relevant case: CARLOS matches role names exactly
        # while the database matches them case-insensitively, so a
        # provider whose privilege rows carry a different spelling can
        # log in and open nothing
        db = FakeDb(spelling_drift=4,
                    comma_roles=[["Nurse, RN"], ["Locum, Dr"]])
        progress, _saves = self.run_roles(db)
        ledger = progress["roles"]
        self.assertTrue(ledger.get("role_spelling"))
        self.assertTrue(ledger.get("role_comma_listed"))
        for sql in o19roles.role_spelling_statements(DST):
            self.assertIn(sql, db.writes)
        report = "\n".join(self.reports)
        self.assertIn("4 active assignment(s)", report)
        self.assertIn("2 role name(s) contain a comma", report)
        # the names are a person's; they go to the private file only
        self.assertNotIn("Nurse, RN", report)
        details = self.private("roles-details.txt")
        self.assertIn("Nurse, RN", details)

    def test_no_drift_leaves_the_report_quiet(self):
        db = FakeDb()
        progress, _saves = self.run_roles(db)
        self.assertTrue(progress["roles"].get("role_spelling"))
        report = "\n".join(self.reports)
        self.assertNotIn("active assignment(s) named a role", report)
        self.assertNotIn("contain a comma", report)
        # the alignment runs regardless: it is idempotent, and a clean
        # target must stay clean
        for sql in o19roles.role_spelling_statements(DST):
            self.assertIn(sql, db.writes)

    def test_writes_are_the_builders_output_in_order(self):
        db = FakeDb()
        self.run_roles(db)
        w = db.writes
        self.assertEqual(w[:2], o19roles.guaranteed_role_statements(DST))
        self.assertEqual(w[2], o19roles.carlos_role_append_statement(DST,
                                                                     ARCH))
        self.assertIn(o19roles.provider_facility_statement(DST), w)
        self.assertIn(o19roles.activeyn_update_statement(DST), w)
        self.assertNotIn("'admin'", o19roles.activeyn_update_statement(DST)
                         .replace("<> 'admin'", ""))
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
        self.assertIn("999902=doctor", details)   # activated assignment
        self.assertIn("999904", details)   # dormant admin row, left alone
        self.assertIn("999903", details)   # least-privilege membership
        self.assertIn("999905=Site Manager", details)  # dangling assignment
        self.assertIn("123", details)      # digit-named secRole row
        report = "\n".join(self.reports)
        for pn in ("999902", "999903", "999904", "999905", "123"):
            self.assertNotIn(pn, report)
        self.assertIn("doctor x1", report)
        self.assertIn("1 NULL admin assignment(s) of active accounts left "
                      "inactive", report)
        self.assertIn("1 active assignment(s) to them now carry", report)
        self.assertIn("1 secRole row(s) named like non-role groups", report)
        diff = self.private("privilege-diff.txt")
        self.assertIn("doctor | _billing | r/0 -> x/0", diff)
        self.assertIn("receptionist | _billing | r/0", diff)  # restored
        self.assertIn("admin | _admin.consult | x/0", diff)
        self.assertIn("nurse | _admin.traceability | x", diff)
        self.assertIn("administration objects: admin/_admin.consult", report)
        self.assertIn("1 seed grant(s) on the clinic's roles have no clinic "
                      "row", report)
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


class TestSeedReplay(RunRolesBase):

    def test_seed_script_runs_once_across_a_crash_and_resume(self):
        # no canonical row -> seed + fixups; the seed INSERT commits, the
        # crash hits the modernize script; the resume finds the seeded
        # row and must not seed again
        seeded = [["40", "Rich Text Letter", "1",
                   "Rich Text Letter Generator v2.1", "0", "1", "1", "0"]]
        modern = [["40", "Rich Text Letter", "1",
                   "Rich Text Letter Generator 2026.3.0", "1", "0", "1",
                   "0"]]
        db = FakeDb(rtl_sequence=[[], seeded, modern],
                    fail_on="-- " + o19roles.RTL_MODERNIZE_SCRIPT,
                    twinless={"secRole": 3, "provider_facility": 2,
                              "program": 1, "program_provider": 5,
                              "eform": 1})
        # resume from the ledger the crashed run ACTUALLY persisted, not a
        # hand-built one: that is what pins the pre-RTL marks and the RTL
        # plan having been written before the first fixup ran
        progress2 = {"tables": {}}
        with self.assertRaises(o19etl.QueryError):
            self.run_roles(db, progress=progress2)
        self.assertIn("rtl_plan", progress2["roles"],
                      "the plan must be persisted before the first write")
        self.assertNotIn("rtl", progress2["roles"],
                         "the crash landed before the step was marked")
        for step in ("roles_appended", "facility_links", "activeyn",
                     "program", "backfill", "diff", "property_pruned",
                     "prevention_types"):
            self.assertIn(step, progress2["roles"], step)
        db2 = FakeDb(rtl_sequence=[seeded, modern],
                     twinless={"secRole": 3, "provider_facility": 2,
                               "program": 1, "program_provider": 5,
                               "eform": 1})
        self.run_roles(db2, progress=progress2)
        seeds = [w for w in db2.writes
                 if w.startswith("-- " + o19roles.RTL_SEED_SCRIPT)]
        self.assertEqual(seeds, [])
        fixups = [w for w in db2.writes if w.startswith("-- update-2026")]
        self.assertEqual(len(fixups), len(o19roles.RTL_FIXUP_SCRIPTS))
        self.assertEqual(progress2["roles"]["appended"]["eform"], 1)
        self.assertIn("modernised", progress2["roles"]["rtl"]["outcome"])
        # ... and the first (crashed) run did seed exactly once
        seeds = [w for w in db.writes
                 if w.startswith("-- " + o19roles.RTL_SEED_SCRIPT)]
        self.assertEqual(len(seeds), 1)


class TestAdminTemplateFloor(RunRolesBase):

    def test_weak_admin_resemblance_is_held_for_the_operator(self):
        # a custom role whose grants overlap `admin` a little (0.5 > J >=
        # 0.3): the administrator objects are not handed out automatically
        stage = STAGE_ROWS + [["Clerk", "_admin", "x", "0"],
                              ["Clerk", "_tickler", "x", "0"]]
        seed = SEED_ROWS + [["admin", "_admin.fax", "x", "0"]]
        rows_sql = ("SELECT roleUserGroup, objectName, privilege, priority "
                    "FROM `{0}`.secObjPrivilege".format(SRC))

        class Db(FakeDb):
            def plain(self, sql, db=None):
                if sql == rows_sql:
                    return stage
                if sql == ("SELECT roleUserGroup, objectName, privilege, "
                           "priority FROM " + SNAP):
                    return seed
                if sql == "SELECT role_name FROM `{0}`.secRole".format(DST):
                    return TARGET_ROLES + [["Clerk"]]
                return FakeDb.plain(self, sql, db)

        db = Db()
        progress, _ = self.run_roles(db)
        held = progress["roles"]["backfill_plan"]["admin_held"]
        self.assertIn("Clerk", held)
        self.assertLess(held["Clerk"], o19roles.ADMIN_TEMPLATE_MIN_JACCARD)
        self.assertNotIn("Clerk", progress["roles"]["backfill"]["templates"])
        backfills = [w for w in db.writes
                     if "INSERT IGNORE" in w and "'Clerk'" in w]
        self.assertEqual(backfills, [])
        report = "\n".join(self.reports)
        self.assertIn("'Clerk': closest stock role is 'admin'", report)
        self.assertIn("--role-template 'Clerk=admin'", report)


class TestRoleTemplateBinding(RunRolesBase):

    def test_first_run_records_the_mapping_and_uses_it(self):
        db = FakeDb()
        progress, _ = self.run_roles(
            db, role_templates={"triage nurse": "Doctor"})
        # recorded once it validated, in the spelling the operator gave
        self.assertEqual(progress["roles"]["role_templates"],
                         {"triage nurse": "Doctor"})
        # normalised to the exact spellings of the tables
        self.assertIn(o19roles.backfill_statement(DST, ARCH, "Triage Nurse",
                                                  "doctor", ["_fax"]),
                      db.writes)
        self.assertEqual(progress["roles"]["backfill"]["templates"],
                         {"Triage Nurse": "doctor"})

    def test_resume_with_a_different_mapping_is_refused_once_decided(self):
        db = FakeDb()
        progress, _ = self.run_roles(
            db, role_templates={"Triage Nurse": "doctor"})
        del progress["roles"]["backfill"]  # the plan is still there
        with self.assertRaises(SystemExit):
            self.run_roles(db, progress, role_templates={"Triage Nurse":
                                                         "nurse"})
        # a case-only variant of the recorded mapping is the same mapping
        n = len(db.writes)
        self.run_roles(db, progress, role_templates={"triage nurse":
                                                     "DOCTOR"})
        self.assertGreater(len(db.writes), n)

    def test_flag_added_or_changed_before_the_decision_is_taken(self):
        # a crash in step 3, then a resume that adds the flag: nothing
        # depended on the mapping yet, so it is accepted and reported
        db = FakeDb(fail_on=o19roles.activeyn_update_statement(DST))
        progress = {"tables": {}}
        with self.assertRaises(o19etl.QueryError):
            self.run_roles(db, progress)
        self.assertNotIn("role_templates", progress["roles"])
        self.run_roles(db, progress, role_templates={"Triage Nurse":
                                                     "doctor"})
        self.assertEqual(progress["roles"]["role_templates"],
                         {"Triage Nurse": "doctor"})
        self.assertIn(o19roles.backfill_statement(DST, ARCH, "Triage Nurse",
                                                  "doctor", ["_fax"]),
                      db.writes)

    def test_typo_in_the_flag_is_recoverable_by_the_hinted_resume(self):
        db = FakeDb()
        progress = {"tables": {}}
        with self.assertRaises(SystemExit):
            self.run_roles(db, progress, role_templates={"Triage Nurse":
                                                         "nrse"})
        # the bad mapping was never recorded
        self.assertNotIn("role_templates", progress["roles"])
        self.assertNotIn("backfill_plan", progress["roles"])
        self.run_roles(db, progress, role_templates={"Triage Nurse":
                                                     "nurse"})
        self.assertEqual(progress["roles"]["backfill"]["templates"],
                         {"Triage Nurse": "nurse"})

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
        err = io.StringIO()
        with contextlib.redirect_stderr(err), \
                self.assertRaises(SystemExit):
            self.run_roles(db, role_templates={"Nobody": "doctor"})
        self.assertIn("--resume", err.getvalue())
        self.assertIn("'Nobody'", err.getvalue())

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

    def test_missing_scripts_fail_closed_before_any_rtl_write(self):
        # a broken package install, not a clinic condition: die, resumable
        os.remove(os.path.join(self.fixups, o19roles.RTL_MODERNIZE_SCRIPT))
        db = FakeDb()
        progress = {"tables": {}}
        with self.assertRaises(SystemExit):
            self.run_roles(db, progress)
        self.assertFalse(progress["roles"].get("rtl"))
        self.assertFalse(any(sql.startswith("-- update-") for sql in
                             db.writes))

    def test_current_form_runs_nothing(self):
        db = FakeDb(rtl_sequence=[RTL_MODERN])
        progress, _ = self.run_roles(db)
        self.assertEqual(progress["roles"]["rtl"]["scripts"], [])
        self.assertEqual(progress["roles"]["rtl"]["outcome"], "unchanged")

    def test_clinic_disabled_form_stays_disabled_across_a_crash(self):
        # the enable script flips the row on; a crash before the restore
        # must not lose the decision — the plan was persisted first
        db = FakeDb(rtl_sequence=[RTL_DISABLED, RTL_ENABLED_MODERN,
                                  RTL_ENABLED_MODERN],
                    fail_on=o19roles.RTL_ROUTE_FIX_SCRIPT)
        progress = {"tables": {}}
        with self.assertRaises(o19etl.QueryError):
            self.run_roles(db, progress)
        self.assertEqual(progress["roles"]["rtl_plan"][2], ["12"])
        self.run_roles(db, progress)
        self.assertIn(o19roles.rtl_disable_statement(DST, "12"), db.writes)
        self.assertEqual(progress["roles"]["rtl"]["restored_disabled"],
                         ["12"])
        self.assertIn("modernised", progress["roles"]["rtl"]["outcome"])

    def test_seed_path_records_the_new_eform_row_for_parity(self):
        db = FakeDb(rtl_sequence=[[], RTL_MODERN], twinless={
            "secRole": 3, "provider_facility": 2, "program": 1,
            "program_provider": 5, "eform": 1})
        progress, _ = self.run_roles(db)
        self.assertEqual(progress["roles"]["rtl"]["scripts"][0],
                         o19roles.RTL_SEED_SCRIPT)
        self.assertEqual(progress["roles"]["appended"]["eform"], 1)
        self.assertIn("ENABLED Rich Text Letter",
                      "\n".join(self.reports))


if __name__ == "__main__":
    unittest.main()
