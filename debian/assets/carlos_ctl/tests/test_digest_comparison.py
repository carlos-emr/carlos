# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Comparing a clinic's digest document against a schema (M22 stage 3).

The P2 transfer check asks one question: did the dump, the transfer and
the restore carry the same VALUES the clinic measured before the dump was
taken? The three outcomes are deliberately distinct -- a table that
AGREED, one that DISAGREED, and one nobody could measure -- because they
call for different actions and are cleared by different sign-offs.

Run (from debian/assets):
    python3 -m unittest carlos_ctl.tests.test_digest_comparison -v
"""

import json
import os
import shutil
import tempfile
import unittest

from carlos_ctl import o19digest, o19import


def entry(columns, rows, total, parity):
    return o19digest.digest_entry(
        columns, o19digest.Digest(rows, total, parity))


COLUMNS = [("id", "int"), ("last_name", "varchar"), ("scan", "blob")]

DOCUMENT = {
    "digest_format": o19digest.DIGEST_FORMAT,
    "generated_at": "2026-09-04T00:00:00",
    "tables": {"demographic": entry(COLUMNS, 3, 900, 77)},
    "errors": {},
}


class Runner(object):
    """Answers digest SQL with canned numbers, and records what it ran."""

    def __init__(self, answers=None, raises=None):
        self.answers = dict(answers or {})
        self.raises = dict(raises or {})
        self.sql = []

    def __call__(self, sql):
        self.sql.append(sql)
        table = sql.split("FROM ", 1)[1].split("`")[3]
        if table in self.raises:
            raise RuntimeError(self.raises[table])
        return o19digest.Digest(*self.answers.get(table, (3, 900, 77)))


def compare(document=None, columns=None, runner=None):
    return o19digest.compare_document(
        document if document is not None else DOCUMENT,
        columns if columns is not None else {"demographic": COLUMNS},
        runner or Runner(), schema="o19_import")


class TestAgreement(unittest.TestCase):

    def test_matching_digests_verify(self):
        result = compare()
        self.assertEqual(result.verified, ["demographic"])
        self.assertEqual(result.failed, [])
        self.assertEqual(result.unverified, [])

    def test_the_sql_uses_the_documents_column_order(self):
        """The order is part of the hash. Re-deriving it from the other
        side's information_schema would compare a different thing while
        looking like it compared the same one."""
        runner = Runner()
        compare(runner=runner)
        sql = runner.sql[0]
        self.assertLess(sql.index("`id`"), sql.index("`last_name`"))
        self.assertLess(sql.index("`last_name`"), sql.index("`scan`"))

    def test_the_sql_names_the_schema_being_compared(self):
        runner = Runner()
        compare(runner=runner)
        self.assertIn("`o19_import`.`demographic`", runner.sql[0])

    def test_a_type_change_within_one_rendering_is_not_a_difference(self):
        """A column the clinic called `varchar` and the restore reports as
        `text` hashes identically -- both CONVERT. Refusing that pair
        would fail a correct restore, which is how a check gets switched
        off."""
        result = compare(columns={"demographic": [
            ("id", "bigint"), ("last_name", "text"), ("scan", "longblob")]})
        self.assertEqual(result.verified, ["demographic"])


class TestDisagreement(unittest.TestCase):

    """Everything here is a real difference between the two sides, so all
    of it is `failed` -- never softened into "could not be measured"."""

    def test_a_content_change_at_the_same_row_count_is_caught(self):
        result = compare(runner=Runner({"demographic": (3, 901, 77)}))
        self.assertEqual(result.verified, [])
        self.assertIn("CONTENT differs", result.failed[0][1])

    def test_a_row_count_change_is_caught_and_named_as_one(self):
        result = compare(runner=Runner({"demographic": (2, 900, 77)}))
        self.assertIn("3 row(s) expected, 2 found", result.failed[0][1])

    def test_a_table_the_restore_did_not_create_is_a_failure(self):
        result = compare(columns={})
        self.assertEqual(result.failed[0][0], "demographic")
        self.assertIn("no such table", result.failed[0][1])

    def test_a_dropped_column_is_a_failure_naming_the_column(self):
        result = compare(columns={"demographic": COLUMNS[:2]})
        self.assertIn("`scan` is missing", result.failed[0][1])

    def test_a_column_that_changed_rendering_class_is_a_failure(self):
        # varchar -> blob: one side would CONVERT and the other HEX, so
        # the two numbers would be incomparable rather than unequal
        result = compare(columns={"demographic": [
            ("id", "int"), ("last_name", "blob"), ("scan", "blob")]})
        self.assertIn("hash differently", result.failed[0][1])

    def test_a_shape_failure_does_not_also_run_a_digest(self):
        runner = Runner()
        compare(columns={"demographic": COLUMNS[:2]}, runner=runner)
        self.assertEqual(runner.sql, [])


class TestWhatCouldNotBeMeasured(unittest.TestCase):

    """The fail-closed half: none of these may be reported as agreement."""

    def test_a_failed_digest_query_is_unverified_not_verified(self):
        result = compare(runner=Runner(
            raises={"demographic": "ERROR 1142: SELECT command denied"}))
        self.assertEqual(result.verified, [])
        self.assertIn("denied", result.unverified[0][1])

    def test_a_table_the_clinic_could_not_measure_is_carried_through(self):
        doc = dict(DOCUMENT, errors={"document": "SELECT denied"})
        result = compare(document=doc)
        self.assertIn(("document",
                       "the clinic could not measure it: SELECT denied"),
                      result.unverified)

    def test_a_table_absent_from_the_document_is_unverified(self):
        result = compare(columns={"demographic": COLUMNS,
                                  "surprise": [("id", "int")]})
        self.assertEqual([t for t, _w in result.unverified], ["surprise"])

    def test_an_unreadable_entry_is_unverified_not_a_zero_digest(self):
        doc = dict(DOCUMENT, tables={"demographic": {"columns": []}})
        result = compare(document=doc)
        self.assertEqual(result.verified, [])
        self.assertEqual(len(result.unverified), 1)


class TestTableNamesAcrossHosts(unittest.TestCase):

    """A CARLOS host running `lower_case_table_names=1` lower-cases every
    restored table name. Matching exactly and nothing else would report
    every capitalised table as missing."""

    def test_a_case_folded_name_resolves_when_there_is_no_exact_match(self):
        doc = dict(DOCUMENT, tables={"Facility": entry(COLUMNS, 3, 900, 77)})
        result = compare(document=doc, columns={"facility": COLUMNS})
        self.assertEqual(result.verified, ["Facility"])

    def test_an_exact_match_wins_over_a_folded_one(self):
        present = {"facility": "Facility", "FACILITY": "FACILITY"}
        self.assertEqual(o19digest.resolve_table("Facility", present),
                         "Facility")

    def test_an_unmatched_name_resolves_to_nothing(self):
        self.assertIsNone(o19digest.resolve_table("gone", {"a": "a"}))


class TestReadingTheDocument(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="o19doc-")
        self.addCleanup(shutil.rmtree, self.dir, True)

    def write(self, obj):
        path = os.path.join(self.dir, "d.json")
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(obj, fh)
        return path

    def test_a_good_document_reads(self):
        self.assertEqual(
            o19digest.load_document(self.write(DOCUMENT))["tables"],
            DOCUMENT["tables"])

    def test_an_unknown_format_is_refused_not_half_understood(self):
        """Numbers compared under different rules than they were taken
        under mean nothing either way -- agreement included."""
        with self.assertRaises(ValueError) as cm:
            o19digest.load_document(
                self.write(dict(DOCUMENT, digest_format=99)))
        self.assertIn("99", str(cm.exception))

    def test_a_document_with_no_tables_is_refused(self):
        with self.assertRaises(ValueError):
            o19digest.load_document(self.write({"digest_format":
                                                o19digest.DIGEST_FORMAT}))

    def test_a_non_document_is_refused(self):
        with self.assertRaises(ValueError):
            o19digest.load_document(self.write([1, 2, 3]))

    def test_a_missing_file_is_a_value_error_not_an_os_error(self):
        with self.assertRaises(ValueError):
            o19digest.load_document(os.path.join(self.dir, "absent.json"))

    def test_unparsable_json_is_refused(self):
        path = os.path.join(self.dir, "bad.json")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("{not json")
        with self.assertRaises(ValueError):
            o19digest.load_document(path)


class TestTheRefusal(unittest.TestCase):

    """`content-transfer` and `no-content-digests` are separate sign-offs
    on purpose: the first says the bytes differ from what was measured,
    the second says nobody measured. Accepting one must never quietly
    accept the other."""

    CLEAN = {"status": "compared", "summary": "1 verified",
             "verified": ["t"], "failed": [], "unverified": []}
    BROKEN = dict(CLEAN, verified=[], failed=[["t", "CONTENT differs"]])
    GAPPED = dict(CLEAN, unverified=[["t", "SELECT denied"]])

    def refusal(self, result, accepted=()):
        return o19import.content_transfer_refusal(result, accepted)

    def test_a_clean_comparison_does_not_refuse(self):
        self.assertIsNone(self.refusal(self.CLEAN))

    def test_a_disagreement_refuses_and_names_its_flag(self):
        message = self.refusal(self.BROKEN)
        self.assertIn("--accept content-transfer", message)
        self.assertIn("CONTENT differs", message)

    def test_a_disagreement_is_cleared_by_its_own_flag(self):
        self.assertIsNone(self.refusal(self.BROKEN, ("content-transfer",)))

    def test_the_gap_flag_does_not_clear_a_disagreement(self):
        # the sign-off that matters most: "nobody measured" must never
        # stand in for "the bytes differ"
        self.assertIn("--accept content-transfer",
                      self.refusal(self.BROKEN, ("no-content-digests",)))

    def test_the_disagreement_flag_does_not_clear_a_gap(self):
        self.assertIn("no-content-digests",
                      self.refusal(self.GAPPED, ("content-transfer",)))

    def test_a_gap_refuses_and_names_its_flag(self):
        self.assertIn("--accept no-content-digests",
                      self.refusal(self.GAPPED))

    def test_a_gap_is_cleared_by_its_own_flag(self):
        self.assertIsNone(self.refusal(self.GAPPED,
                                       ("no-content-digests",)))

    def test_no_document_at_all_refuses_as_a_gap(self):
        absent = {"status": "absent", "summary": "no clinic digests"}
        self.assertIn("--accept no-content-digests", self.refusal(absent))
        self.assertIsNone(self.refusal(absent, ("no-content-digests",)))

    def test_an_unreadable_document_refuses_as_a_gap(self):
        # not silently the same as "none supplied": one was shipped and
        # could not be used, which is a thing to go and fix
        bad = {"status": "unreadable", "summary": "carries format 99"}
        message = self.refusal(bad)
        self.assertIn("format 99", message)
        self.assertIn("--accept no-content-digests", message)

    def test_a_disagreement_shows_at_most_five_tables(self):
        many = dict(self.CLEAN, verified=[], failed=[
            ["t{0}".format(n), "differs"] for n in range(20)])
        self.assertEqual(self.refusal(many).count("differs"), 5)


if __name__ == "__main__":
    unittest.main()


class FakeCtx(dict):
    """The three ctx entries `content_transfer_check` reads."""

    def __init__(self, digests=None, state_dir=None, columns=None,
                 answers=None, raises=None):
        self.columns = columns if columns is not None else {
            "demographic": COLUMNS}
        self.answers = dict(answers or {})
        self.raises = dict(raises or {})
        self.queries = []
        dict.__init__(self, {"o19_digests": digests,
                             "state_dir": state_dir,
                             "query": self._query})

    def _query(self, sql, db=None):
        self.queries.append((sql, db))
        if "information_schema.COLUMNS" in sql:
            return [[t, c, ty] for t in sorted(self.columns)
                    for c, ty in self.columns[t]]
        table = sql.split("FROM ", 1)[1].split("`")[3]
        if table in self.raises:
            raise RuntimeError(self.raises[table])
        return [[str(v) for v in self.answers.get(table, (3, 900, 77))]]


class TestTheCheckAsThePhaseRunsIt(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="o19p2c-")
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.path = os.path.join(self.dir, "digests.json")
        with open(self.path, "w", encoding="utf-8") as fh:
            json.dump(DOCUMENT, fh)

    def test_no_document_reports_absent_without_touching_the_database(self):
        ctx = FakeCtx(digests=None, state_dir=self.dir)
        result = o19import.content_transfer_check(ctx)
        self.assertEqual(result["status"], "absent")
        self.assertEqual(ctx.queries, [])

    def test_an_unreadable_document_is_its_own_status(self):
        bad = os.path.join(self.dir, "bad.json")
        with open(bad, "w", encoding="utf-8") as fh:
            json.dump(dict(DOCUMENT, digest_format=99), fh)
        result = o19import.content_transfer_check(
            FakeCtx(digests=bad, state_dir=self.dir))
        self.assertEqual(result["status"], "unreadable")
        self.assertIn("99", result["summary"])

    def test_a_matching_staging_schema_verifies(self):
        result = o19import.content_transfer_check(
            FakeCtx(digests=self.path, state_dir=self.dir))
        self.assertEqual(result["status"], "compared")
        self.assertEqual(result["verified"], ["demographic"])
        self.assertEqual(result["failed"], [])

    def test_a_changed_staging_schema_fails(self):
        result = o19import.content_transfer_check(FakeCtx(
            digests=self.path, state_dir=self.dir,
            answers={"demographic": (3, 901, 77)}))
        self.assertEqual(result["failed"][0][0], "demographic")

    def test_the_check_reads_the_staging_schema_not_the_live_one(self):
        ctx = FakeCtx(digests=self.path, state_dir=self.dir)
        o19import.content_transfer_check(ctx)
        self.assertTrue(all(db == o19import.STAGING_SCHEMA
                            for _sql, db in ctx.queries))

    def test_views_are_excluded_from_the_staging_inventory(self):
        # a view carries no rows of its own and may not exist on the
        # other side at all
        ctx = FakeCtx(digests=self.path, state_dir=self.dir)
        o19import.staging_column_types(ctx)
        self.assertIn("TABLE_TYPE = 'BASE TABLE'", ctx.queries[0][0])

    def test_the_artifact_is_written_privately(self):
        ctx = FakeCtx(digests=self.path, state_dir=self.dir)
        result = o19import.content_transfer_check(ctx)
        o19import.report_content_transfer(ctx, result)
        artifact = os.path.join(self.dir, "content-transfer.json")
        self.assertEqual(os.stat(artifact).st_mode & 0o777, 0o600)
        with open(artifact, encoding="utf-8") as fh:
            self.assertEqual(json.load(fh)["verified"], ["demographic"])

    def test_the_report_records_what_disagreed(self):
        ctx = FakeCtx(digests=self.path, state_dir=self.dir,
                      answers={"demographic": (2, 900, 77)})
        o19import.report_content_transfer(
            ctx, o19import.content_transfer_check(ctx))
        with open(os.path.join(self.dir, "report.txt"),
                  encoding="utf-8") as fh:
            text = fh.read()
        self.assertIn("P2 content transfer", text)
        self.assertIn("disagreed: demographic", text)


class TestThePhaseCannotPassWithoutTheCheck(unittest.TestCase):

    """A source guard, in the shape the ETL tests already use.

    `run_p2` marks the preflight phase done, and everything after it
    trusts that. A refactor that moved `mark_done` above the content
    check would leave a run recorded as passed while nothing had compared
    the transfer -- and no unit test that drives numbers could see it."""

    def _run_p2_source(self):
        import inspect
        return inspect.getsource(o19import.run_p2)

    def test_the_check_runs_before_the_phase_is_marked_done(self):
        source = self._run_p2_source()
        check = source.index("content_transfer_check(ctx)")
        done = source.index("mark_done(")
        self.assertLess(check, done,
                        "run_p2 marks the preflight phase done before "
                        "comparing the transfer's content")

    def test_the_refusal_is_consulted_and_can_stop_the_run(self):
        source = self._run_p2_source()
        self.assertIn("content_transfer_refusal(content", source)
        refusal = source.index("content_transfer_refusal(content")
        self.assertIn("die(refusal", source[refusal:])
        self.assertLess(refusal, source.index("mark_done("))

    def test_a_dry_run_reports_the_check_too(self):
        # a dry run IS the assessment; learning the transfer is intact
        # before the cutover window is most of what it is for
        source = self._run_p2_source()
        dry = source.index('if ctx.get("dry_run")')
        self.assertIn("content_transfer_check(ctx)",
                      source[dry:source.index("mark_done(")])


class TestTheRunMeasuresTheCopyItTook(unittest.TestCase):

    """P2 reads the digest document AFTER the dump has been staged.

    An operator's `--o19-digests` path is a mutable file: replaced in
    between, it would be compared against data it does not describe, and
    across a `--resume` a different file would silently change what the
    transfer was ever measured against. The run snapshots it and the
    ledger binds the snapshot's sha256."""

    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="o19snap-")
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.state_dir = os.path.join(self.dir, "state")
        os.makedirs(self.state_dir, mode=0o700)
        self.path = os.path.join(self.dir, "digests.json")
        with open(self.path, "w", encoding="utf-8") as fh:
            json.dump(DOCUMENT, fh)

    def args(self, **over):
        import argparse
        ns = argparse.Namespace(
            bundle=None, bundle_pass=None, bundle_cipher=None,
            bundle_openssl_opt=None, bundle_sha256=None, dump=self.path,
            properties=self.path, documents=None, skip_documents=True,
            accept=[], o19_digests=self.path)
        for k, v in over.items():
            setattr(ns, k, v)
        return ns

    def test_the_snapshot_is_written_privately(self):
        copy = o19import.snapshot_digests(self.path, self.state_dir)
        self.assertEqual(os.stat(copy).st_mode & 0o777, 0o600)
        with open(copy, encoding="utf-8") as fh:
            self.assertEqual(json.load(fh), DOCUMENT)

    def test_a_rerun_over_a_loose_copy_tightens_it(self):
        copy = os.path.join(self.state_dir, o19import.DIGESTS_SNAPSHOT)
        with open(copy, "w", encoding="utf-8") as fh:
            fh.write("{}")
        os.chmod(copy, 0o644)
        o19import.snapshot_digests(self.path, self.state_dir)
        self.assertEqual(os.stat(copy).st_mode & 0o777, 0o600)

    def test_resolve_inputs_returns_the_copy_not_the_operators_file(self):
        inputs = o19import._resolve_inputs(self.args(), self.state_dir)
        self.assertNotEqual(inputs["digests"], self.path)
        self.assertEqual(os.path.dirname(inputs["digests"]),
                         self.state_dir)

    def test_replacing_the_operators_file_does_not_change_the_run(self):
        inputs = o19import._resolve_inputs(self.args(), self.state_dir)
        with open(self.path, "w", encoding="utf-8") as fh:
            json.dump({"digest_format": 1, "tables": {}}, fh)
        with open(inputs["digests"], encoding="utf-8") as fh:
            self.assertEqual(json.load(fh), DOCUMENT)


class TestTheLedgerBindsTheDocument(unittest.TestCase):

    """A resumed run must measure against the document the earlier phases
    did, or the comparison means nothing."""

    def refusal(self, recorded, actual):
        return o19import.digests_change_refusal(recorded, actual)

    def test_the_same_document_is_allowed(self):
        self.assertIsNone(self.refusal("a" * 64, "a" * 64))

    def test_a_first_run_with_nothing_recorded_is_allowed(self):
        self.assertIsNone(self.refusal(None, "a" * 64))

    def test_a_run_that_supplies_none_is_allowed(self):
        # the gap is reported by the transfer check's own sign-off, not
        # by refusing the run before anything has been assessed
        self.assertIsNone(self.refusal("a" * 64, None))

    def test_a_different_document_is_refused_and_names_both(self):
        message = self.refusal("a" * 64, "b" * 64)
        self.assertIn("aaaaaaaaaaaa", message)
        self.assertIn("bbbbbbbbbbbb", message)
        self.assertIn("--restage", message)
