# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""State-ledger and P0 pristine-gate contracts for the O19 importer.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import argparse
import json
import os
import shutil
import tempfile
import unittest

from carlos_ctl import (o19etl, o19import, o19map_schema,
                        o19report)


class TestStateLedger(unittest.TestCase):

    def setUp(self):
        self.state_dir = tempfile.mkdtemp(prefix="o19state-test-")
        self.addCleanup(shutil.rmtree, self.state_dir)

    def test_fresh_state_is_empty(self):
        state = o19import.load_state(self.state_dir)
        self.assertEqual(state["phases"], {})
        self.assertEqual(state["accepted"], [])

    def test_mark_done_round_trips(self):
        state = o19import.load_state(self.state_dir)
        o19import.mark_done(self.state_dir, state, "stage",
                            dump_sha256="abc123")
        reloaded = o19import.load_state(self.state_dir)
        self.assertTrue(o19import.phase_done(reloaded, "stage"))
        self.assertEqual(reloaded["phases"]["stage"]["dump_sha256"],
                         "abc123")
        self.assertFalse(o19import.phase_done(reloaded, "etl"))

    def test_corrupt_state_file_is_fatal_not_fresh(self):
        # a fresh ledger would re-sweep a mid-import target and send the
        # operator to the wrong remedy
        os.makedirs(self.state_dir, exist_ok=True)
        with open(o19import.state_path(self.state_dir), "w") as fh:
            fh.write("{not json")
        with self.assertRaises(SystemExit):
            o19import.load_state(self.state_dir)

    def test_absent_state_file_is_a_fresh_run(self):
        self.assertEqual(o19import.load_state(self.state_dir)["phases"], {})

    def test_accepted_flags_persist_in_state(self):
        state = o19import.load_state(self.state_dir)
        state["accepted"] = ["archived-forms"]
        o19import.save_state(self.state_dir, state)
        self.assertEqual(o19import.load_state(self.state_dir)["accepted"],
                         ["archived-forms"])

    def test_report_appends_sections(self):
        o19import.report_append(self.state_dir, "P0", "fine")
        o19import.report_append(self.state_dir, "P1", "also fine")
        with open(os.path.join(self.state_dir, "report.txt")) as fh:
            text = fh.read()
        self.assertIn("== P0 ==", text)
        self.assertIn("== P1 ==", text)


class TestPristineGate(unittest.TestCase):
    """The stock-initial-deploy sweep (user requirement: absolute on a
    packaged host, warning-only under --dev-target)."""

    def seeds(self):
        return dict(o19map_schema.SEED_ROW_COUNTS)

    def test_exact_seed_counts_pass(self):
        counts = self.seeds()
        counts["demographic"] = 0
        counts["appointment"] = 0
        self.assertEqual(o19import.pristine_violations(counts), [])

    def test_a_single_demo_patient_violates(self):
        counts = self.seeds()
        counts["demographic"] = 1
        v = o19import.pristine_violations(counts)
        self.assertEqual(len(v), 1)
        self.assertIn("demographic", v[0])
        self.assertIn("expected 0", v[0])

    def test_missing_seed_rows_also_violate(self):
        # fewer rows than the seed is just as non-stock as extra rows
        counts = self.seeds()
        seeded_table = next(t for t, n in counts.items() if n > 0)
        counts[seeded_table] = 0
        v = o19import.pristine_violations(counts)
        self.assertTrue(any(seeded_table in x for x in v))

    def test_a_tolerated_table_with_rows_is_waved_through(self):
        # `log` carries this deploy's own audit rows (a sysadmin's
        # verification login). The copy deletes them before the clinic's
        # id-intact rows land, so they are not a non-stock target — but
        # run_check_pristine still puts the count in the report, because
        # P0 is the last place anyone looks before they are deleted.
        tolerated = getattr(o19map_schema, "PRISTINE_TOLERATED_TABLES", ())
        self.assertTrue(tolerated)
        counts = self.seeds()
        for table in tolerated:
            counts[table] = 12
        self.assertEqual(o19import.pristine_violations(counts), [])

    def test_no_accept_class_can_clear_the_gate(self):
        # the gate is not expressed as a preflight blocker at all, so the
        # accept vocabulary cannot touch it — pin the vocabulary here.
        self.assertNotIn("non-pristine", o19import.ACCEPT_CLASSES)
        self.assertNotIn("pristine", " ".join(o19import.ACCEPT_CLASSES))

    def test_merge_tables_take_a_seed_floor_not_an_exact_count(self):
        # reference seeds grown by INSERT ... SELECT migrations are not
        # statically countable: more rows is fine, fewer is not
        counts = self.seeds()
        merge = [t for t in counts
                 if o19map_schema.TABLES[t]["class"] == "merge"]
        if not merge:
            self.skipTest("no seeded merge table in manifest")
        counts[merge[0]] += 5
        self.assertEqual(o19import.pristine_violations(counts), [])
        counts[merge[0]] = 0
        v = o19import.pristine_violations(counts)
        self.assertTrue(any(merge[0] in x and "at least" in x for x in v))

    def test_copy_tables_are_exact(self):
        counts = self.seeds()
        counts["provider"] = counts.get("provider", 0) + 1
        v = o19import.pristine_violations(counts)
        self.assertTrue(any(x.startswith("provider:") for x in v))

    def test_startup_created_rows_are_tolerated_once(self):
        # a packaged host booted before the import: the webapp added the
        # OSCAR program, its membership and the default site
        counts = self.seeds()
        counts["program"] += 1
        counts["program_provider"] += 1
        counts["site"] = 1
        counts["providersite"] = 1
        self.assertTrue(o19import.pristine_violations(counts))
        adjusted = o19import.tolerate_startup_rows(
            counts, {"program": 1, "program_provider": 1, "site": 1,
                     "providersite": 1})
        self.assertEqual(o19import.pristine_violations(adjusted), [])
        # a second OSCAR program is still a violation — even when the live
        # predicate count says 2: the webapp creates each row exactly once
        counts["program"] += 1
        adjusted = o19import.tolerate_startup_rows(
            counts, {"program": 2, "program_provider": 1, "site": 1,
                     "providersite": 1})
        self.assertTrue(any(v.startswith("program:")
                            for v in o19import.pristine_violations(adjusted)))

    def test_an_audit_row_from_a_verification_login_is_tolerated(self):
        # CARLOS writes a `log` row for every login attempt, failed ones
        # included. Refusing the host for it would make one confirmation
        # login by the sysadmin cost a reprovision, and the copy deletes
        # those rows before the clinic's land (log is replace_seed).
        counts = self.seeds()
        counts["log"] = 3
        self.assertEqual(o19import.pristine_violations(counts), [])
        for table in o19map_schema.PRISTINE_TOLERATED_TABLES:
            self.assertTrue(
                o19map_schema.TABLES[table].get("replace_seed"),
                "{0} is tolerated but its rows are never cleared".format(
                    table))
        # a clinical table is still a refusal
        counts["demographic"] = 1
        self.assertTrue(o19import.pristine_violations(counts))

    def test_the_rollback_hint_follows_what_p3_actually_did(self):
        # every refusal downstream of P3 names the snapshot; P3 can be
        # told to skip it, and handing the operator a remedy that does
        # not exist is worst at the point they have least time
        self.assertEqual(o19import.rollback_hint({}),
                         "restore the pre-import restic snapshot")
        skipped = {"phases": {"backup": {"skipped": "no-pre-backup"}}}
        hint = o19import.rollback_hint(skipped)
        self.assertIn("NO pre-import snapshot", hint)
        self.assertIn("destroy-data", hint)

    def test_startup_row_counts_follow_the_manifest(self):
        def q(sql):
            if "information_schema" in sql:
                return [["site"], ["program"]]
            if "`site`" in sql:
                return [["1"]]
            if "`program`" in sql:
                self.assertIn("name = 'OSCAR'", sql)
                return [["0"]]
            raise AssertionError(sql)
        self.assertEqual(o19import.startup_row_counts(q, "carlos"),
                         {"site": 1})

    def test_provider_and_security_seeds_are_expected(self):
        # provider/security ARE seeded — the sweep must expect their seed
        # rows rather than demanding zero.
        self.assertGreater(
            o19map_schema.SEED_ROW_COUNTS.get("provider", 0), 0)
        self.assertGreater(
            o19map_schema.SEED_ROW_COUNTS.get("security", 0), 0)


class TestDiskHeadroom(unittest.TestCase):

    def test_tiny_requirement_passes(self):
        self.assertIsNone(o19import.check_disk_headroom(1024, 0))

    def test_absurd_requirement_fails_with_paths(self):
        msg = o19import.check_disk_headroom(1 << 60, 0)
        self.assertIsNotNone(msg)
        self.assertIn("insufficient disk", msg)

    def test_documents_tar_counts_on_the_state_volume(self):
        self.assertIsNone(o19import.check_disk_headroom(1024, 0, 0))
        msg = o19import.check_disk_headroom(1024, 0, documents_size=1 << 60)
        self.assertIsNotNone(msg)
        self.assertIn("state volume", msg)

    def test_uncompressed_size_measures_the_expanded_dump(self):
        import gzip
        work = tempfile.mkdtemp(prefix="o19size-")
        self.addCleanup(shutil.rmtree, work)
        payload = b"INSERT INTO t VALUES (1);\n" * 20000
        path = os.path.join(work, "d.sql.gz")
        with gzip.open(path, "wb") as fh:
            fh.write(payload)
        self.assertLess(os.path.getsize(path), len(payload) // 10)
        self.assertEqual(o19import.uncompressed_size(path), len(payload))
        plain = os.path.join(work, "d.sql")
        with open(plain, "wb") as fh:
            fh.write(payload)
        self.assertEqual(o19import.uncompressed_size(plain), len(payload))


class TestResumeContract(unittest.TestCase):
    """--resume is the only way to continue recorded state (never implied)."""

    def test_fresh_state_needs_no_flag(self):
        self.assertIsNone(o19import.require_resume_for_existing_state(
            {"phases": {}}, resume=False, dry_run=False))

    def test_a_staged_dump_alone_is_reusable_without_resume(self):
        # a dry run / assessment leaves only the stage phase behind; the
        # real run that follows must not be forced into --resume
        self.assertIsNone(o19import.require_resume_for_existing_state(
            {"phases": {"stage": {"status": "done"}}}, False, False))

    def test_existing_state_without_resume_is_refused(self):
        msg = o19import.require_resume_for_existing_state(
            {"phases": {"stage": {"status": "done"},
                        "check-pristine": {"status": "done"}}}, False, False)
        self.assertIsNotNone(msg)
        self.assertIn("--resume", msg)
        self.assertIn("check-pristine", msg)

    def test_resume_proceeds_but_a_dry_run_over_a_run_in_progress_does_not(
            self):
        state = {"phases": {"stage": {"status": "done"},
                            "backup": {"status": "done"}}}
        self.assertIsNone(o19import.require_resume_for_existing_state(
            state, True, False))
        # a dry run re-extracts the bundle and would rewrite the inputs
        # the real run resumes from: refused, with the two ways out
        msg = o19import.require_resume_for_existing_state(state, False, True)
        self.assertIsNotNone(msg)
        self.assertIn("--resume", msg)
        self.assertIn("--cleanup", msg)

    def test_resume_with_nothing_recorded_is_refused(self):
        # the flag must match a recorded run: over an empty workspace, or
        # one holding only a staged dump, it would silently start afresh
        refuse = o19import.nothing_to_resume_refusal
        self.assertIn("--resume", refuse({"phases": {}}, True, False))
        self.assertIsNotNone(refuse(
            {"phases": {"stage": {"status": "done"}}}, True, False))
        self.assertIsNone(refuse(
            {"phases": {"stage": {"status": "done"},
                        "backup": {"status": "done"}}}, True, False))
        # an ETL ledger with writes is a recorded run even without phases
        self.assertIsNone(refuse({"phases": {}}, True, True))
        self.assertIsNone(refuse({"phases": {}}, False, False))

    def test_assessment_refusal_covers_ledger_and_interrupted_cleanup(self):
        import tempfile
        import shutil
        d = tempfile.mkdtemp(prefix="o19assess-")
        self.addCleanup(shutil.rmtree, d)
        self.assertIsNone(o19import.assessment_refusal({"phases": {}}, d))
        self.assertIsNone(o19import.assessment_refusal(
            {"phases": {"stage": {"status": "done"}}}, d))
        self.assertIn("--resume", o19import.assessment_refusal(
            {"phases": {"backup": {"status": "done"}}}, d))
        from carlos_ctl import o19etl
        o19etl.save_progress(d, {"tables": {"demographic": {"done": True}},
                                 "dump_sha256": "x"})
        self.assertIn("etl ledger", o19import.assessment_refusal(
            {"phases": {}}, d))
        self.assertIn("--cleanup again", o19import.assessment_refusal(
            {"phases": {}, "cleanup": "in-progress"}, d))

    def test_resume_hint_follows_recorded_phases(self):
        self.assertEqual(o19import.resume_hint({"phases": {}}), "")
        self.assertEqual(o19import.resume_hint(
            {"phases": {"stage": {"status": "done"}}}), "")
        self.assertEqual(o19import.resume_hint(
            {"phases": {"check-pristine": {"status": "done"}}}), " --resume")

    def test_statement_timeout_is_a_session_bound(self):
        self.assertEqual(o19import.statement_timeout_prelude(600),
                         "SET SESSION max_statement_time=600")
        self.assertIn("carry-credentials", o19import.ACCEPT_CLASSES)

    def test_error_text_never_carries_a_credential_literal(self):
        sql = ("CREATE USER 'o19_import'@'localhost' IDENTIFIED BY "
               "'s3cr3t-password-value'")
        text = o19import.redact_statement(sql)
        self.assertNotIn("s3cr3t", text)
        self.assertIn("IDENTIFIED BY '<redacted>'", text)
        # masked BEFORE the width cut: a truncated literal cannot leak
        self.assertNotIn("s3cr3t", o19import.redact_statement(sql, 60))
        self.assertEqual(o19import.redact_statement("SELECT 1"), "SELECT 1")
        # a stored HASH is a credential too — it is directly replayable
        hashed = ("GRANT ALL ON x.* TO 'a'@'b' IDENTIFIED BY PASSWORD "
                  "'*ABC123DEADBEEF'")
        self.assertNotIn("ABC123DEADBEEF",
                         o19import.redact_statement(hashed))
        # an escaped quote inside the literal must not end the match
        escaped = r"SET PASSWORD = 'a\'b-s3cr3t'"
        self.assertNotIn("s3cr3t", o19import.redact_statement(escaped))

    def test_etl_started_reads_the_ledger(self):
        from carlos_ctl import o19etl
        d = tempfile.mkdtemp(prefix="o19resume-")
        self.addCleanup(shutil.rmtree, d)
        self.assertFalse(o19import.etl_started(d))
        o19etl.save_progress(d, {"tables": {}, "admin_provider_no": "7"})
        self.assertTrue(o19import.etl_started(d))


class TestStatementTimeoutFlag(unittest.TestCase):

    def test_restore_client_carries_the_timeout_when_set(self):
        argv = o19import.staging_client_argv(
            ["mariadb", "--protocol=socket", "--user=root"], "/tmp/c.cnf",
            statement_timeout=30)
        init = [a for a in argv if a.startswith("--init-command=")][0]
        self.assertIn("max_statement_time=30", init)
        argv = o19import.staging_client_argv(
            ["mariadb", "--protocol=socket", "--user=root"], "/tmp/c.cnf")
        self.assertNotIn("max_statement_time", " ".join(argv))

    def test_negative_or_non_numeric_seconds_are_refused_by_the_parser(
            self):
        import argparse
        self.assertEqual(o19import._nonnegative_seconds("0"), 0)
        self.assertEqual(o19import._nonnegative_seconds("45"), 45)
        for bad in ("-1", "x", "1.5"):
            with self.assertRaises(argparse.ArgumentTypeError):
                o19import._nonnegative_seconds(bad)


class TestCleanupGate(unittest.TestCase):
    """--cleanup destroys the resume ledger, so it is allowed only after a
    passed verification or before the copy started; --dry-run grants
    nothing."""

    def setUp(self):
        self.state_dir = tempfile.mkdtemp(prefix="o19cleanup-")
        self.addCleanup(shutil.rmtree, self.state_dir)

    def test_verified_run_may_clean_up(self):
        self.assertIsNone(o19import.cleanup_refusal(
            {"phases": {"verify": {"status": "done"}}}, self.state_dir,
            False))

    def test_untouched_target_may_clean_up(self):
        self.assertIsNone(o19import.cleanup_refusal(
            {"phases": {"stage": {"status": "done"}}}, self.state_dir,
            False))

    def test_mid_import_workspace_is_refused(self):
        from carlos_ctl import o19etl
        o19etl.save_progress(self.state_dir,
                             {"tables": {"demographic": {"done": True}}})
        msg = o19import.cleanup_refusal(
            {"phases": {"stage": {"status": "done"}}}, self.state_dir, False)
        self.assertIsNotNone(msg)
        self.assertIn("--resume", msg)
        # the dev-database override is the only bypass
        self.assertIsNone(o19import.cleanup_refusal(
            {"phases": {}}, self.state_dir, True))

    def test_restored_documents_count_as_written(self):
        msg = o19import.cleanup_refusal(
            {"phases": {"documents": {"status": "done"}}}, self.state_dir,
            False)
        self.assertIsNotNone(msg)

    def test_a_verified_run_still_may_not_drop_a_homeless_table(self):
        """The hole this closes: --cleanup was permitted on a passed
        verify alone, and the verification behind it had never counted an
        archived row -- so it dropped staging while staging held the only
        copy of every removed-module table."""
        msg = o19import.cleanup_data_refusal(
            True, ["cr_user: 12 staging row(s) and no copy at "
                   "carlos.import_archived_cr_user"])
        self.assertIsNotNone(msg)
        self.assertIn("no verified home", msg)
        self.assertIn("cr_user", msg)

    def test_a_clean_parity_lets_cleanup_through(self):
        self.assertIsNone(o19import.cleanup_data_refusal(True, []))

    def test_a_run_that_never_copied_is_not_measured(self):
        # staging holds a restore of the operator's own dump and nothing
        # was written, so every table would flag for the wrong reason
        self.assertIsNone(o19import.cleanup_data_refusal(
            False, ["demographic: staging 100 -> target 0"]))

    def test_the_refusal_lists_at_most_ten_tables(self):
        msg = o19import.cleanup_data_refusal(
            True, ["t{0}: homeless".format(i) for i in range(14)])
        self.assertIn("14 table(s)", msg)
        self.assertIn("t9: homeless", msg)
        self.assertNotIn("t10: homeless", msg)


class TestStagingDropRefusal(unittest.TestCase):
    """P1's first act is DROP DATABASE o19_import. That is safe while the
    dump that made it can be restored again -- and not safe when the rows
    belong to a dump this workspace never staged."""

    def test_an_empty_staging_schema_may_be_dropped(self):
        self.assertIsNone(o19import.staging_drop_refusal(
            False, None, "aaa", False))

    def test_rows_from_an_unknown_dump_are_refused(self):
        msg = o19import.staging_drop_refusal(True, None, "aaa", False)
        self.assertIsNotNone(msg)
        self.assertIn("only copy", msg)
        self.assertIn("--restage", msg)

    def test_the_refusal_names_the_dump_the_workspace_staged(self):
        msg = o19import.staging_drop_refusal(
            True, "bbbbbbbbbbbbbbbb", "aaa", False)
        self.assertIn("bbbbbbbbbbbb...", msg)

    def test_an_interrupted_restore_of_the_same_dump_may_retry(self):
        # the rows are this dump's own: mark_started recorded the digest
        # before the drop precisely so the retry is not refused
        self.assertIsNone(o19import.staging_drop_refusal(
            True, "aaa", "aaa", False))

    def test_restage_says_so_explicitly(self):
        self.assertIsNone(o19import.staging_drop_refusal(
            True, "bbb", "aaa", True))


class TestMarkStarted(unittest.TestCase):
    """A destructive phase records what it is about to work on BEFORE it
    starts, so its own wreckage is distinguishable from someone else's
    data."""

    def setUp(self):
        self.state_dir = tempfile.mkdtemp(prefix="o19started-")
        self.addCleanup(shutil.rmtree, self.state_dir)

    def test_the_phase_records_its_subject_before_it_succeeds(self):
        state = {}
        o19import.mark_started(self.state_dir, state, "stage",
                               dump_sha256="aaa")
        phase = o19import.load_state(self.state_dir)["phases"]["stage"]
        self.assertEqual(phase["status"], "in-progress")
        self.assertEqual(phase["dump_sha256"], "aaa")

    def test_a_completed_phase_is_not_reopened_by_its_own_fields(self):
        state = {}
        o19import.mark_done(self.state_dir, state, "stage",
                            dump_sha256="aaa")
        o19import.mark_started(self.state_dir, state, "stage",
                               dump_sha256="bbb")
        phase = o19import.load_state(self.state_dir)["phases"]["stage"]
        self.assertEqual(phase["status"], "in-progress")
        self.assertEqual(phase["dump_sha256"], "bbb")


class TestInheritedImportRefusal(unittest.TestCase):
    """A host that has already imported a clinic must not quietly take a
    second one's rows into the same tables."""

    def test_an_inherited_archive_schema_is_refused(self):
        msg = o19import.inherited_import_refusal(True, [], "carlos")
        self.assertIn("o19_archive", msg)

    def test_preserved_tables_left_in_the_live_schema_are_refused(self):
        # the emptiness sweep cannot see these: it iterates the manifest,
        # and a preserved table is by definition not in it
        msg = o19import.inherited_import_refusal(
            False, ["import_archived_cr_user", "import_archived_Eyeform"],
            "carlos")
        self.assertIn("2 table(s)", msg)
        self.assertIn("import_archived_Eyeform", msg)
        self.assertIn("--resume", msg)

    def test_the_listing_is_capped(self):
        msg = o19import.inherited_import_refusal(
            False, ["import_archived_t{0}".format(i) for i in range(9)],
            "carlos")
        self.assertIn("9 table(s)", msg)
        self.assertIn(", ...", msg)

    def test_a_pristine_host_is_not_refused(self):
        self.assertIsNone(
            o19import.inherited_import_refusal(False, [], "carlos"))


class TestStateArchiving(unittest.TestCase):

    def setUp(self):
        self.state_dir = tempfile.mkdtemp(prefix="o19archivestate-")
        self.addCleanup(shutil.rmtree, self.state_dir)

    def test_state_is_archived_so_the_run_cannot_be_resumed(self):
        o19import.save_state(self.state_dir,
                             {"phases": {"verify": {"status": "done"}}})
        archived = o19import.archive_state(self.state_dir)
        self.assertTrue(archived.startswith("state.json.completed-"))
        self.assertEqual(o19import.load_state(self.state_dir)["phases"], {})
        self.assertIsNone(o19import.archive_state(self.state_dir))

    def test_every_run_file_is_retired_with_the_state(self):
        # the ETL ledger and the properties fragments included; the
        # break-glass credentials file deliberately not
        o19import.save_state(self.state_dir,
                             {"phases": {"verify": {"status": "done"}}})
        for name in o19import.RUN_FILES + ("admin-credentials.txt",):
            with open(os.path.join(self.state_dir, name), "w") as fh:
                fh.write("x")
        self.assertIn("etl-progress.json", o19import.RUN_FILES)
        self.assertIn("o19-derived-carlos.properties", o19import.RUN_FILES)
        archived = o19import.archive_state(self.state_dir)
        suffix = archived[len("state.json"):]
        left = sorted(os.listdir(self.state_dir))
        for name in o19import.RUN_FILES:
            self.assertNotIn(name, left)
            self.assertIn(name + suffix, left)
        self.assertIn("admin-credentials.txt", left)


class TestDevModePolicy(unittest.TestCase):
    """--dev-target/--mariadb-arg are for development databases: refused on
    a packaged host, and --dev-target alone (no seam) is meaningless."""

    def test_no_dev_flags_is_always_fine(self):
        self.assertIsNone(o19import.dev_mode_refusal(False, None, True))
        self.assertIsNone(o19import.dev_mode_refusal(False, None, False))

    def test_dev_flags_are_refused_on_a_packaged_host(self):
        self.assertIn("packaged host",
                      o19import.dev_mode_refusal(True, ["-uroot"], True))
        self.assertIn("packaged host",
                      o19import.dev_mode_refusal(False, ["-uroot"], True))

    def test_dev_target_needs_the_connection_seam(self):
        self.assertIn("--mariadb-arg",
                      o19import.dev_mode_refusal(True, None, False))
        self.assertIsNone(o19import.dev_mode_refusal(True, ["-uroot"], False))
        self.assertIsNone(o19import.dev_mode_refusal(False, ["-uroot"],
                                                     False))


class TestStagingRestore(unittest.TestCase):
    """The restore of clinic-supplied SQL runs as a throwaway account whose
    grants stop at the staging schema, and a dump that would steer the
    restore elsewhere is refused before a byte reaches the server."""

    def test_redirect_markers_are_refused(self):
        for text in (b"\nUSE `oscar`;\n", b"\nCREATE DATABASE `x`;\n",
                     b"\nuse oscar;\n"):
            msg = o19import.dump_redirect_marker(text)
            self.assertIsNotNone(msg, text)
            self.assertIn("--databases", msg)
        self.assertIn("GTID_PURGED", o19import.dump_redirect_marker(
            b"\nSET @@GLOBAL.GTID_PURGED='abc';\n"))

    def test_ordinary_dump_text_passes(self):
        self.assertIsNone(o19import.dump_redirect_marker(
            b"\n-- MySQL dump\nINSERT INTO `t` VALUES ('use this');\n"
            b"/*!40101 SET NAMES utf8 */;\n"))

    def test_redirect_markers_survive_every_spelling(self):
        for text in (b"\nUse `oscar`;\n", b"\n  USE `oscar`;\n",
                     b"\n\tcreate  database x;\n",
                     b"\nCreate Database x;\n"):
            self.assertIsNotNone(o19import.dump_redirect_marker(text), text)
        # anchored: the word inside a value or a comment is data
        for text in (b"\nINSERT INTO t VALUES ('use oscar');\n",
                     b"-- USE is mentioned here\n"):
            self.assertIsNone(o19import.dump_redirect_marker(text), text)

    def _scan(self, chunks):
        scanner = o19import.RedirectScanner()
        for chunk in chunks:
            found = scanner.feed(chunk)
            if found:
                return found
        return None

    def test_a_marker_split_across_a_chunk_boundary_is_caught(self):
        # `CREATE` plus more whitespace than any fixed-size carry would
        # hold: neither chunk contains the marker on its own
        self.assertIsNotNone(self._scan(
            [b"INSERT INTO t VALUES (1);\nCREATE" + b" " * 4096,
             b"DATABASE evil;\n"]))
        self.assertIsNotNone(self._scan([b"x;\n", b"USE other;\n"]))

    def test_a_note_that_starts_a_chunk_is_not_a_marker(self):
        # `^` must not match mid-line: the buffer the scanner carries is
        # always a line start, so clinical text cannot forge one
        self.assertIsNone(self._scan(
            [b"INSERT INTO n VALUES ('a b c ",
             b"use the inhaler twice daily');\n"]))

    def test_a_line_longer_than_the_carry_bound_stays_mid_line(self):
        filler = b"x" * (o19import.DUMP_CARRY_MAX * 2)
        big = b"INSERT INTO n VALUES ('" + filler + b" use it');\n"
        half = len(big) // 2
        self.assertIsNone(self._scan([big[:half], big[half:]]))
        # and a real marker on the line AFTER it is still caught
        self.assertIsNotNone(self._scan(
            [big[:half], big[half:] + b"USE other;\n"]))

    def test_restore_argv_replaces_identity_and_scopes_the_database(self):
        argv = o19import.staging_client_argv(
            ["mariadb", "--protocol=socket", "--user=root"], "/s/.cnf")
        self.assertEqual(argv[:2], ["mariadb",
                                    "--defaults-extra-file=/s/.cnf"])
        self.assertIn("--protocol=socket", argv)
        self.assertNotIn("--user=root", argv)
        self.assertIn("--one-database", argv)
        self.assertEqual(argv[-1], o19import.STAGING_SCHEMA)
        # the identity is repeated ON THE ARGV: --defaults-extra-file does
        # not suppress ~/.my.cnf, which is read after it and would
        # otherwise connect the clinic's dump as root
        self.assertIn("--user=" + o19import.STAGING_USER, argv)
        self.assertLess(argv.index("--defaults-extra-file=/s/.cnf"),
                        argv.index("--user=" + o19import.STAGING_USER))
        self.assertIn("--local-infile=0", argv)
        # a dev seam's own credentials are stripped the same way
        argv = o19import.staging_client_argv(
            ["mariadb", "--host=db", "-uroot", "-psecret",
             "--defaults-file=/x"], "/s/.cnf")
        self.assertEqual([a for a in argv
                          if a.startswith(("-uroot", "-psecret"))], [])
        self.assertIn("--host=db", argv)
        self.assertNotIn("--defaults-file=/x", argv)

    def test_paired_identity_options_lose_their_values_too(self):
        # `--user root --password s3` as separate tokens: neither the option
        # nor its value may survive (a stray value would become the client's
        # positional database name)
        tail = o19import.strip_client_identity(
            ["--host", "db", "--user", "root", "--password", "s3",
             "-u", "admin", "-p", "--port=3307", "--defaults-extra-file",
             "/etc/x.cnf"])
        self.assertEqual(tail, ["--host", "db", "--port=3307"])
        # a bare -p followed by an option keeps the option
        self.assertEqual(o19import.strip_client_identity(
            ["-p", "--host=db"]), ["--host=db"])

    def test_account_grants_stop_at_the_staging_schema(self):
        stmts = o19import.staging_account_statements("pw'x")
        self.assertTrue(stmts[0].startswith("DROP USER IF EXISTS"))
        self.assertIn("IDENTIFIED BY 'pw\\'x'", stmts[1])
        grants = [s for s in stmts if s.startswith("GRANT")]
        self.assertEqual(len(grants), len(o19import.STAGING_ACCOUNT_HOSTS))
        for grant in grants:
            self.assertIn("ON `{0}`.*".format(o19import.STAGING_SCHEMA),
                          grant)
            self.assertNotIn("*.*", grant)

    def test_missing_binlog_admin_is_refused_not_widened(self):
        # no SUPER fallback: a server without BINLOG ADMIN is refused and
        # the half-created account is dropped again
        seen = []

        def q(sql):
            seen.append(sql)
            if "BINLOG ADMIN" in sql:
                raise RuntimeError("ERROR 1064: unknown privilege")
            return []
        cnf_dir = tempfile.mkdtemp(prefix="o19cnf-")
        self.addCleanup(shutil.rmtree, cnf_dir)
        cnf = os.path.join(cnf_dir, "c.cnf")
        with self.assertRaises(SystemExit):
            o19import.grant_staging_account(q, cnf)
        self.assertFalse(any("SUPER" in s for s in seen))
        self.assertTrue(any(s.startswith("DROP USER") for s in seen[-2:]))


class TestBundleDigest(unittest.TestCase):
    """openssl enc has no integrity check: the digest the clinic conveys
    separately must match, or the operator must sign off on skipping it."""

    ACTUAL = "ab" * 32

    def test_matching_digest_opens(self):
        self.assertIsNone(o19import.bundle_digest_refusal(
            "AB" * 32, self.ACTUAL, []))

    def test_mismatch_is_refused_without_bypass(self):
        msg = o19import.bundle_digest_refusal("cd" * 32, self.ACTUAL,
                                              ["unverified-bundle"])
        self.assertIn("mismatch", msg)

    def test_malformed_digest_is_refused(self):
        self.assertIn("64 hex", o19import.bundle_digest_refusal(
            "not-a-digest", self.ACTUAL, []))

    def test_missing_digest_needs_the_sign_off(self):
        self.assertIn("--bundle-sha256",
                      o19import.bundle_digest_refusal(None, self.ACTUAL, []))
        self.assertIsNone(o19import.bundle_digest_refusal(
            None, self.ACTUAL, ["unverified-bundle"]))
        self.assertIn("unverified-bundle", o19import.ACCEPT_CLASSES)

    def test_recorded_sign_off_survives_resume(self):
        # a real run persisted `unverified-bundle`; the resume passes
        # neither the flag nor a digest and must still open the bundle
        state = {"accepted": ["unverified-bundle"]}
        accepted = o19import.merged_acknowledgements([], state, True)
        self.assertEqual(accepted, ["unverified-bundle"])
        self.assertIsNone(o19import.bundle_digest_refusal(
            None, self.ACTUAL, accepted))

    def test_recorded_sign_off_does_not_carry_into_a_fresh_run(self):
        # the ledger records `accepted` before the first phase runs, so a
        # run that dies in a P0 gate leaves sign-offs behind with nothing
        # to resume; the next (necessarily fresh) invocation must not
        # inherit them — `no-pre-backup` would skip the rollback snapshot
        # for a run nobody acknowledged
        state = {"accepted": ["no-pre-backup", "unverified-bundle"]}
        self.assertEqual(
            o19import.merged_acknowledgements([], state, False), [])
        self.assertEqual(
            o19import.merged_acknowledgements(["charset-repair"], state,
                                              False),
            ["charset-repair"])
        # and a resume still continues on what the run recorded
        self.assertEqual(
            o19import.merged_acknowledgements([], state, True),
            ["no-pre-backup", "unverified-bundle"])

    def test_resolve_inputs_uses_the_merged_acknowledgements(self):
        tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, tmp)
        bundle = os.path.join(tmp, "o19-bundle.tar")
        with open(bundle, "wb") as fh:
            fh.write(b"\0" * 512)
        opened = []
        real_open = o19import.o19bundle.open_bundle
        o19import.o19bundle.open_bundle = lambda *a, **kw: (
            opened.append(a) or {"dump": None, "documents": None,
                                 "properties": None, "bundle_sha256": "x",
                                 "members": {}})
        self.addCleanup(setattr, o19import.o19bundle, "open_bundle",
                        real_open)
        args = argparse.Namespace(
            bundle=bundle, bundle_pass=None, bundle_sha256=None,
            bundle_cipher="aes-256-cbc", bundle_openssl_opt=[],
            dump=None, properties=None, documents=None, accept=[])
        digest = o19import.sha256_file(bundle)
        with self.assertRaises(SystemExit):  # no sign-off anywhere
            o19import._resolve_inputs(args, tmp, [])
        self.assertEqual(opened, [])
        # the ledger's sign-off covers the file it was recorded for ...
        o19import._resolve_inputs(args, tmp, ["unverified-bundle"],
                                  recorded_digest=digest)
        self.assertEqual(len(opened), 1)
        # ... never a replacement bundle: that needs its own digest or a
        # fresh --accept
        with self.assertRaises(SystemExit):
            o19import._resolve_inputs(args, tmp, ["unverified-bundle"],
                                      recorded_digest="00" * 32)
        self.assertEqual(len(opened), 1)
        args.accept = ["unverified-bundle"]
        o19import._resolve_inputs(args, tmp, ["unverified-bundle"],
                                  recorded_digest="00" * 32)
        self.assertEqual(len(opened), 2)

    def test_recorded_sign_off_is_bound_to_the_recorded_file(self):
        merged = ["charset-repair", "unverified-bundle"]
        self.assertEqual(
            o19import.bundle_acknowledgements([], merged, self.ACTUAL,
                                              self.ACTUAL), merged)
        self.assertEqual(
            o19import.bundle_acknowledgements([], merged, "cd" * 32,
                                              self.ACTUAL), [])
        self.assertEqual(
            o19import.bundle_acknowledgements([], merged, None,
                                              self.ACTUAL), [])
        self.assertEqual(
            o19import.bundle_acknowledgements(["unverified-bundle"], merged,
                                              "cd" * 32, self.ACTUAL),
            ["unverified-bundle"])


class TestGuardedExit(unittest.TestCase):
    """A failed client statement ends in one error line; the preflight
    verb's low exit codes are verdicts, so its failure is the tool-error
    code, never a code a caller could read as go."""

    def _boom():
        raise o19import.o19etl.QueryError("mariadb: cannot connect",
                                          "stderr")

    def test_default_failure_code_is_one(self):
        with self.assertRaises(SystemExit) as cm:
            o19import._guarded(TestGuardedExit._boom)
        self.assertEqual(cm.exception.code, 1)

    def test_preflight_fails_with_the_tool_error_code(self):
        real = o19import._cmd_o19_preflight
        o19import._cmd_o19_preflight = lambda argv: TestGuardedExit._boom()
        self.addCleanup(setattr, o19import, "_cmd_o19_preflight", real)
        with self.assertRaises(SystemExit) as cm:
            o19import.cmd_o19_preflight([])
        self.assertEqual(cm.exception.code,
                         o19import.o19_preflight.EXIT_TOOL_ERROR)
        self.assertNotIn(cm.exception.code, (0, 1, 2))


class TestAcceptIdDriftLock(unittest.TestCase):

    def test_every_preflight_accept_id_is_a_cli_class(self):
        # a blocker whose --accept name the CLI does not know could never
        # be acknowledged; read the ids out of the preflight source
        import re
        from carlos_ctl import o19_preflight
        with open(o19_preflight.__file__, encoding="utf-8") as fh:
            source = fh.read()
        ids = set(re.findall(r'accept="([a-z-]+)"', source))
        self.assertTrue(ids)
        self.assertTrue(ids <= set(o19import.ACCEPT_CLASSES),
                        ids - set(o19import.ACCEPT_CLASSES))


class TestHeadCollations(unittest.TestCase):

    def test_extracts_collations_from_dump_head(self):
        head = (b"CREATE TABLE t (a varchar(5)) "
                b"DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;\n"
                b"/*!40101 SET NAMES latin1 */; COLLATE latin1_swedish_ci")
        self.assertEqual(
            o19import.head_collations(head),
            ["latin1_swedish_ci", "utf8mb4_uca1400_ai_ci"])


if __name__ == "__main__":
    unittest.main()


class TestVerifyPhaseFiles(unittest.TestCase):
    """run_p7 writes its per-patient lines and the roles findings to the
    root-only files and replaces the P7 block on every rerun."""

    def setUp(self):
        import tempfile
        import shutil
        self.state_dir = tempfile.mkdtemp(prefix="o19p7-")
        self.addCleanup(shutil.rmtree, self.state_dir)
        from carlos_ctl import o19roles
        self._parity = o19import._row_parity
        self._checks = o19roles.verify_role_checks
        o19import._row_parity = lambda ctx: (["t: 1 -> 1"], [])
        self.private = ["expired logins: fixture.expired"]
        o19roles.verify_role_checks = lambda *a, **k: (
            ["role 'doctor' present"], [], ["1 login(s) import expired"],
            list(self.private))
        self.addCleanup(self._restore)

    def _restore(self):
        from carlos_ctl import o19roles
        o19import._row_parity = self._parity
        o19roles.verify_role_checks = self._checks

    def _ctx(self):
        def query(sql, db=None):
            if "COUNT(*) FROM `o19_import`.demographic" in sql:
                return [["2"]]
            if "ORDER BY RAND(" in sql:
                # the sample is seeded from the recorded dump digest, so
                # a re-run draws the SAME patients
                self.assertIn("RAND(0)", sql)
                return [["7"], ["9"]]
            if "billing_on_cheader1 GROUP BY" in sql:
                return []
            if "WHERE `demographic_no` = 7" in sql \
                    or "WHERE `demographicNo` = 7" in sql:
                return [["1"]]
            return [["0"]] if "COUNT(*)" in sql else []
        return {"state_dir": self.state_dir, "state": {"phases": {}},
                "query": query, "target_db": "carlos"}

    def test_private_files_carry_the_identifiers_and_report_the_counts(
            self):
        ctx = self._ctx()
        o19import.run_p7(ctx)
        details = os.path.join(self.state_dir, "verify-details.txt")
        self.assertEqual(os.stat(details).st_mode & 0o777, 0o600)
        with open(details) as fh:
            text = fh.read()
        self.assertIn("spot checks on 2 of 2 patient(s)", text)
        with open(os.path.join(self.state_dir, "report.txt")) as fh:
            report = fh.read()
        self.assertNotIn("patient 7", report)
        self.assertNotIn("fixture.expired", report)
        self.assertIn("1 login(s) import expired", report)
        roles = os.path.join(self.state_dir, "roles-details.txt")
        with open(roles) as fh:
            self.assertIn("P7 verify:\nexpired logins: fixture.expired",
                          fh.read())
        self.assertTrue(o19import.phase_done(ctx["state"], "verify"))

    def test_a_one_sided_billing_table_is_not_reported_as_a_match(self):
        # billing_on_cheader1 present in the target but absent from
        # staging: the run records that it cannot compare. Zeroing both
        # sides to keep the equality test simple used to print "billing
        # totals match for 0 fiscal year(s)" directly under that failure.
        ctx = self._ctx()
        inner = ctx["query"]

        def query(sql, db=None):
            if "billing_on_cheader1 GROUP BY" in sql and "o19_import" in sql:
                raise RuntimeError(
                    "ERROR 1146 (42S02) at line 1: Table "
                    "'o19_import.billing_on_cheader1' doesn't exist")
            return inner(sql, db)
        ctx["query"] = query
        with self.assertRaises(SystemExit):
            o19import.run_p7(ctx)
        with open(os.path.join(self.state_dir, "report.txt")) as fh:
            report = fh.read()
        self.assertIn("billing totals: NOT COMPARED", report)
        self.assertNotIn("billing totals match", report)
        self.assertIn("verification cannot compare billing totals", report)

    def test_rerun_replaces_the_verify_block(self):
        o19import.write_private(
            os.path.join(self.state_dir, "roles-details.txt"),
            "activated: 1=doctor\nP7 verify:\nstale line\n")
        ctx = self._ctx()
        o19import.run_p7(ctx)
        with open(os.path.join(self.state_dir, "roles-details.txt")) as fh:
            text = fh.read()
        self.assertIn("activated: 1=doctor\n", text)
        self.assertNotIn("stale line", text)
        self.assertEqual(text.count("P7 verify:"), 1)
        # a second run with nothing private still rewrites the block
        self.private[:] = []
        ctx = self._ctx()
        o19import.run_p7(ctx)
        with open(os.path.join(self.state_dir, "roles-details.txt")) as fh:
            text = fh.read()
        self.assertNotIn("fixture.expired", text)
        self.assertEqual(text.count("P7 verify:"), 1)


class TestTheImportReport(unittest.TestCase):
    """The operator's validation report: the document a human uses to
    decide whether the migration is sound.

    `report.txt` is a phase log -- chronological, headerless, and (on a
    clean import) missing the per-table counts entirely, because those
    were written only when they were wrong.
    """

    def setUp(self):
        self.state_dir = tempfile.mkdtemp(prefix="o19report-")
        self.addCleanup(shutil.rmtree, self.state_dir)

    def ctx(self):
        return {"state_dir": self.state_dir, "target_db": "carlos",
                "province": "on",
                "state": {"phases": {"stage": {
                    "status": "done", "at": "2026-09-04T09:00:00",
                    "dump_sha256": "abc123"}}}}

    LEDGER = {"report_lines": {
        "absent": ["log (absent: the target's own rows were cleared)"],
        "drop": ["cr_user: 12 row(s) not migrated (removed module "
                 "infrastructure); preserved at o19_archive.cr_user and "
                 "carlos.import_archived_cr_user"],
        "reference": ["icd9: 9 row(s) kept at o19_archive.icd9"],
        "unknown": ["clinic_notes: 4 row(s) preserved"],
        "archived_cols": ["Contact: programNo -> "
                          "import_archived_programNo"],
        "idmap": ["HL7Map: 3 row(s) received a new id"],
        "fk": ["appointment.provider_no: 2 row(s) dangling"],
        "shadow": ["Contact.gone: dropped column absent from this dump"]}}

    def build(self, problems=(), ok=("demographic: staging 10 -> target "
                                     "10",)):
        return o19import.import_report(
            self.ctx(), self.LEDGER, list(ok), list(problems),
            ["row parity: 1 table(s) match"], ["1 login(s) import expired"],
            "2026-09-04T10:00:00")

    def test_the_header_names_the_dump_and_the_manifest(self):
        text = o19report.render_text(self.build())
        self.assertIn("target schema:        carlos", text)
        self.assertIn("dump sha256:          abc123", text)
        self.assertIn(o19map_schema.SCHEMA_MAP_VERSION, text)
        self.assertIn("import started:       2026-09-04T09:00:00", text)

    def test_a_clean_import_still_itemises_what_arrived(self):
        # the gap this closes: on a clean import report.txt recorded
        # "N table(s) match; 0 mismatch" and threw the per-table lines
        # away, though the guide promises them
        text = o19report.render_text(self.build())
        self.assertIn("VERDICT: PASSED", text)
        self.assertIn("WHAT ARRIVED", text)
        self.assertIn("demographic: staging 10 -> target 10", text)

    def test_what_did_not_arrive_says_where_it_went_instead(self):
        text = o19report.render_text(self.build())
        self.assertIn("WHAT DID NOT ARRIVE, AND WHERE IT WENT INSTEAD",
                      text)
        for line in ("preserved at o19_archive.cr_user",
                     "icd9: 9 row(s) kept at o19_archive.icd9",
                     "clinic_notes: 4 row(s) preserved",
                     "import_archived_programNo",
                     "log (absent:"):
            self.assertIn(line, text)

    def test_findings_are_ordered_by_severity_not_by_arrival(self):
        # given in the wrong order on purpose: a report whose order is
        # only ever right because the caller happened to append in that
        # order is not ordered at all
        report = o19report.build(
            {}, "PASSED", [],
            [o19report.finding("info", "third"),
             o19report.finding("failure", "first"),
             o19report.finding("advisory", "second")])
        self.assertEqual([f["title"] for f in report["findings"]],
                         ["first", "second", "third"])

    def test_a_problem_makes_the_verdict_a_failure(self):
        report = self.build(problems=["demographic: staging 10 -> 9"])
        self.assertEqual(report["findings"][0]["severity"], "failure")
        self.assertIn("FAILED (1 problem(s))", report["verdict"])
        self.assertIn("demographic: staging 10 -> 9",
                      o19report.render_text(report))

    def test_the_next_steps_are_in_the_artifact_not_only_on_the_console(
            self):
        text = o19report.render_text(self.build())
        self.assertIn("NEXT STEPS", text)
        self.assertIn("carlos-ctl backup full", text)
        self.assertIn("--cleanup", text)

    def test_the_json_twin_carries_the_same_facts(self):
        report = self.build()
        twin = json.loads(o19report.render_json(report))
        self.assertEqual(twin["verdict"], "PASSED")
        self.assertEqual(twin["header"]["dump_sha256"], "abc123")
        self.assertTrue(any("cr_user" in ln for s in twin["sections"]
                            for ln in s["lines"]))

    def test_an_empty_section_says_so_rather_than_vanishing(self):
        # a heading over nothing reads as an omission; a missing section
        # reads as "not checked"
        report = o19import.import_report(
            self.ctx(), {}, [], [], [], [], "2026-09-04T10:00:00")
        text = o19report.render_text(report)
        self.assertIn("nothing was compared", text)
        self.assertIn("every staging table had a home in CARLOS", text)

    def test_an_unknown_severity_is_refused(self):
        with self.assertRaises(ValueError):
            o19report.finding("catastrophe", "x")

    def test_both_artifacts_are_written_root_only(self):
        ctx = self.ctx()
        o19etl.save_progress(self.state_dir, dict(self.LEDGER))
        o19import.write_import_report(ctx, ["t: staging 1 -> target 1"],
                                      [], ["row parity: 1 match"], [])
        for name in ("import-report.txt", "import-report.json"):
            path = os.path.join(self.state_dir, name)
            self.assertEqual(os.stat(path).st_mode & 0o777, 0o600, name)
            self.assertIn(name, o19import.RUN_FILES)
        with open(os.path.join(self.state_dir,
                               "import-report.json")) as fh:
            self.assertEqual(json.load(fh)["kind"],
                             "carlos-o19-import-report")

    def test_the_running_log_is_root_only_too(self):
        # report.txt was the only run artifact left at the umask's 0644,
        # and it carries table names, row counts and roles findings
        o19import.report_append(self.state_dir, "P4 etl", "body")
        path = os.path.join(self.state_dir, "report.txt")
        self.assertEqual(os.stat(path).st_mode & 0o777, 0o600)
        o19import.report_append(self.state_dir, "P7 verify", "more")
        with open(path) as fh:
            text = fh.read()
        self.assertIn("== P4 etl ==", text)
        self.assertIn("== P7 verify ==", text)


class TestProcessGrantState(unittest.TestCase):
    """The replica gate's PROCESS-privilege determination.

    Without PROCESS the server does not error on
    information_schema.PROCESSLIST — it silently restricts the rows to
    the caller's own threads, so the binlog-dump count comes back 0 and
    an attached replica goes unnoticed by a binlog-off bulk copy.
    """

    def test_all_privileges_is_held(self):
        self.assertEqual(o19import.process_grant_state(
            [["GRANT ALL PRIVILEGES ON *.* TO `root`@`localhost` "
              "WITH GRANT OPTION"]]), "held")

    def test_process_inside_a_privilege_list_is_held(self):
        self.assertEqual(o19import.process_grant_state(
            [["GRANT SELECT, PROCESS, RELOAD ON *.* TO `x`@`h`"]]), "held")

    def test_all_privileges_on_one_schema_is_not_global_process(self):
        # PROCESS is a GLOBAL privilege, so only a grant `ON *.*` can
        # carry it. `ALL PRIVILEGES ON `somedb`.*` is everything the
        # SCHEMA level offers, which does not include PROCESS — reading
        # it as global was the same fail-open in a new place.
        self.assertEqual(o19import.process_grant_state(
            [["GRANT ALL PRIVILEGES ON `somedb`.* TO `u`@`h`"]]), "absent")
        self.assertEqual(o19import.process_grant_state(
            [["GRANT ALL PRIVILEGES ON `db`.`t` TO `u`@`h`"]]), "absent")
        self.assertEqual(o19import.process_grant_state(
            [["GRANT USAGE ON *.* TO `u`@`h`"],
             ["GRANT ALL PRIVILEGES ON `oscar`.* TO `u`@`h`"]]), "absent")

    def test_a_privilege_from_an_active_role_is_held(self):
        # MariaDB expands an enabled default role in SHOW GRANTS
        self.assertEqual(o19import.process_grant_state(
            [["GRANT PROCESS ON *.* TO `o19role`"]]), "held")

    def test_an_identifier_containing_process_is_not_the_privilege(self):
        # the fail-OPEN this replaces: a substring test went quiet for an
        # account holding no PROCESS at all, because one of its grants
        # named a schema called hl7_processing
        self.assertEqual(o19import.process_grant_state(
            [["GRANT USAGE ON *.* TO `o19np`@`localhost`"],
             ["GRANT SELECT ON `hl7_processing`.* TO `o19np`@`localhost`"]]),
            "absent")

    def test_a_username_containing_process_on_a_global_grant(self):
        # the case the scope check does NOT defend: this IS `ON *.*`, so
        # only reading the privilege list keeps it honest. Every account
        # has a `GRANT USAGE ON *.*` line, so any account whose NAME
        # contains "process" would otherwise read as holding it.
        self.assertEqual(o19import.process_grant_state(
            [["GRANT USAGE ON *.* TO `hl7_process`@`localhost`"],
             ["GRANT SELECT ON `oscar`.* TO `hl7_process`@`localhost`"]]),
            "absent")

    def test_a_table_named_processlist_is_not_the_privilege(self):
        self.assertEqual(o19import.process_grant_state(
            [["GRANT SELECT ON `db`.`processlist_cache` TO `u`@`h`"]]),
            "absent")

    def test_an_unparseable_dialect_is_unknown_not_absent(self):
        # only a POSITIVE determination may refuse: an unfamiliar server
        # must never turn this into a false refusal blocking a migration
        for rows in ([["SOMETHING ELSE ENTIRELY"]],
                     [["GRANT `r1`@`%` TO `u`@`%`"]],
                     [], None):
            self.assertEqual(o19import.process_grant_state(rows),
                             "unknown", repr(rows))
