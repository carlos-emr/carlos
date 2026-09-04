# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The run_docs driver against a fake database and a real tar.

`run_docs` was the other phase with no driver: its helpers
(`detect_context_dir`, `merge_move`, `hrm_rewrite_sql`,
`classify_document_files`) are each well covered by `test_docs.py`, but
nothing exercised the function that decides which of them run, in what
order, and what a --resume repeats. That ordering is load-bearing: the
HRM twins refusal has to fire before the tar is touched, the restore mark
has to land before the steps that follow it, and the reconciliation has
to re-run on every pass even when the tree was already restored.

The tar is real (built per test into a temp tree) because the extraction
path shells out to tar and the merge path moves actual files; faking that
would test the fake. The database is not.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import os
import shutil
import tarfile
import tempfile
import unittest

from carlos_ctl import o19docs, o19import

DST = "carlos"
CTX = o19docs.TARGET_CTX


class FakeDb(object):
    """Answers the reads `run_docs` issues and records every write.

    Keyed by the distinctive part of each statement rather than by exact
    text: the SQL builders have their own tests, and pinning their output
    twice would make this file fail for a formatting change.
    """

    def __init__(self, **over):
        self.writes = []
        self.twins = over.pop("twins", [])
        self.documents = over.pop("documents", [])
        self.hrm = over.pop("hrm", [])
        self.eforms = over.pop("eforms", [])
        self.archive_tables = over.pop("archive_tables", [])
        if over:
            raise TypeError("unexpected FakeDb kwargs: {0}".format(
                sorted(over)))

    def __call__(self, sql, db=None):
        if not sql.lstrip().upper().startswith("SELECT"):
            self.writes.append(sql)
            return []
        # hrm_basename_twins_sql is the only statement with a HAVING
        # clause, and it must be matched BEFORE the generic
        # HRMDocument arm below or it falls through to the rewrite
        # select and silently answers "no twins".
        if "HAVING paths > 1" in sql:
            return self.twins
        if sql.startswith("SELECT DISTINCT docfilename"):
            return [[f] for _no, f in self.documents]
        if sql.startswith("SELECT document_no, docfilename"):
            return [list(r) for r in self.documents]
        if sql.startswith("SELECT fid, form_name, form_html"):
            return self.eforms
        if "information_schema.TABLES" in sql:
            return [[t] for t in self.archive_tables]
        if "HRMDocument" in sql or "report_file" in sql:
            return self.hrm
        return []


def make_tar(path, files, ctx="oscar"):
    """A documents tar shaped the way the documented export command makes
    one: a single top-level context directory holding the tree."""
    with tarfile.open(path, "w:gz") as tf:
        for name, body in files.items():
            member = os.path.join(ctx, name)
            data = body.encode("utf-8")
            info = tarfile.TarInfo(member)
            info.size = len(data)
            import io
            tf.addfile(info, io.BytesIO(data))
    return path


class DocsDriverBase(unittest.TestCase):
    """A temp state dir, a temp documents root, and a real tar."""

    FILES = {"document/a.pdf": "PDF-a", "document/b.pdf": "PDF-b",
             "eform/images/logo.png": "PNG"}

    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="o19docs-driver-")
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.state_dir = os.path.join(self.tmp, "state")
        os.makedirs(self.state_dir, mode=0o700)
        self.docs_root = os.path.join(self.tmp, "OscarDocument")
        os.makedirs(os.path.join(self.docs_root, CTX), mode=0o750)
        self.tar = make_tar(os.path.join(self.tmp, "docs.tar.gz"),
                            dict(self.FILES))

    def ctx_root(self):
        return os.path.join(self.docs_root, CTX)

    def make_ctx(self, db, **over):
        ctx = {"state": {"phases": {}}, "state_dir": self.state_dir,
               "query": db, "documents": self.tar, "accepted": set(),
               "dev_target": True, "target_db": DST,
               "documents_root": self.docs_root,
               "archive_schema": "o19_archive"}
        ctx.update(over)
        return ctx

    def run_docs(self, db=None, **over):
        db = db or FakeDb(documents=[["1", "a.pdf"], ["2", "b.pdf"]])
        ctx = self.make_ctx(db, **over)
        o19docs.run_docs(ctx)
        return db, ctx

    def report(self):
        path = os.path.join(self.state_dir, "report.txt")
        if not os.path.exists(path):
            return ""
        with open(path, encoding="utf-8") as fh:
            return fh.read()

    def details(self):
        path = os.path.join(self.state_dir, "documents-details.txt")
        if not os.path.exists(path):
            return ""
        with open(path, encoding="utf-8") as fh:
            return fh.read()


class TestTheHappyPath(DocsDriverBase):
    """One clean pass over a tar whose files all have rows."""

    def test_the_tree_is_restored_under_the_carlos_context(self):
        self.run_docs()
        for rel in self.FILES:
            self.assertTrue(
                os.path.exists(os.path.join(self.ctx_root(), rel)),
                "{0} was not restored".format(rel))

    def test_the_phase_is_marked_done_with_the_tar_digest(self):
        _db, ctx = self.run_docs()
        phase = ctx["state"]["phases"]["documents"]
        self.assertEqual(phase["status"], "done")
        self.assertEqual(phase["tar_sha256"],
                         o19import.sha256_file(self.tar))
        self.assertTrue(phase["restored"])

    def test_the_hrm_basename_rewrite_runs(self):
        db, _ctx = self.run_docs()
        self.assertTrue([w for w in db.writes if w.startswith("UPDATE")],
                        db.writes)

    def test_the_report_records_the_restore_and_the_reconciliation(self):
        self.run_docs()
        text = self.report()
        self.assertIn("== P5 documents restore ==", text)
        self.assertIn("== P5 reconciliation ==", text)
        self.assertIn("document row(s) reconciled", text)

    def test_the_archive_csv_export_runs_in_this_phase(self):
        # the CSV rendering of o19_archive is the clinic's only readable
        # copy of what became archive-only, and it is produced here
        self.run_docs()
        self.assertIn("archive CSV export:", self.report())


class TestTheSkipPath(DocsDriverBase):
    """--skip-documents, which is a sign-off rather than a default."""

    def test_no_tar_without_the_sign_off_is_refused(self):
        with self.assertRaises(SystemExit):
            self.run_docs(documents=None)

    def test_no_tar_with_the_sign_off_is_recorded_as_skipped(self):
        _db, ctx = self.run_docs(documents=None,
                                 accepted={"no-documents"})
        phase = ctx["state"]["phases"]["documents"]
        self.assertEqual(phase["status"], "done")
        self.assertEqual(phase["skipped"], "no-documents")
        self.assertIn("SKIPPED (no-documents acknowledged)", self.report())

    def test_skipping_after_a_restore_is_refused(self):
        _db, ctx = self.run_docs()
        with self.assertRaises(SystemExit):
            o19docs.run_docs(self.make_ctx(
                FakeDb(), documents=None, accepted={"no-documents"},
                state=ctx["state"]))


class TestTheRefusals(DocsDriverBase):
    """Every refusal, and what it must not have touched first."""

    def test_hrm_basename_twins_refuse_before_the_tree_is_touched(self):
        db = FakeDb(twins=[["report.pdf", "2"]])
        with self.assertRaises(SystemExit):
            self.run_docs(db=db)
        self.assertEqual(os.listdir(self.ctx_root()), [],
                         "the tree was written before the twins refusal")
        self.assertIn("report.pdf", self.details())

    def test_a_missing_document_file_fails_reconciliation(self):
        db = FakeDb(documents=[["1", "a.pdf"], ["9", "gone.pdf"]])
        with self.assertRaises(SystemExit):
            self.run_docs(db=db)
        self.assertIn("P5 reconciliation FAILURES", self.report())
        self.assertIn("missing file for document 9: gone.pdf", self.details())

    def test_a_reconciliation_failure_names_no_file_in_the_report(self):
        # document names can carry a patient's name: itemised in the
        # root-only details file, counted in the shareable report
        db = FakeDb(documents=[["9", "SMITH-referral.pdf"]])
        with self.assertRaises(SystemExit):
            self.run_docs(db=db)
        self.assertNotIn("SMITH", self.report())
        self.assertIn("SMITH", self.details())

    def test_a_different_tar_after_a_restore_is_refused(self):
        _db, ctx = self.run_docs()
        other = make_tar(os.path.join(self.tmp, "other.tar.gz"),
                         {"document/c.pdf": "PDF-c"})
        with self.assertRaises(SystemExit):
            o19docs.run_docs(self.make_ctx(
                FakeDb(), documents=other, state=ctx["state"]))


class TestTheResume(DocsDriverBase):
    """What a second pass over the same tar repeats, and what it skips."""

    def test_the_same_tar_is_not_re_extracted(self):
        _db, ctx = self.run_docs()
        marker = os.path.join(self.ctx_root(), "document", "operator.txt")
        with open(marker, "w", encoding="utf-8") as fh:
            fh.write("added by the operator between passes")
        db = FakeDb(documents=[["1", "a.pdf"], ["2", "b.pdf"]])
        o19docs.run_docs(self.make_ctx(db, state=ctx["state"]))
        self.assertTrue(os.path.exists(marker),
                        "the resume re-extracted over the restored tree")

    def test_reconciliation_re_runs_on_every_pass(self):
        # an operator who fixed the tree by hand between passes must get
        # a fresh verdict, not the recorded one
        _db, ctx = self.run_docs()
        os.remove(os.path.join(self.state_dir, "report.txt"))
        db = FakeDb(documents=[["1", "a.pdf"], ["2", "b.pdf"]])
        o19docs.run_docs(self.make_ctx(db, state=ctx["state"]))
        self.assertIn("document row(s) reconciled", self.report())

    def test_the_details_file_is_truncated_per_pass(self):
        # every step re-itemises what it finds; appending across passes
        # would stack undelimited blocks in the file the refusals cite.
        # The two passes share one state so the second is a real resume:
        # given a FRESH state the merge collision scan refuses instead,
        # which is correct but is not what this test is about.
        db = FakeDb(documents=[["9", "gone.pdf"]])
        state = {"phases": {}}
        for _ in range(2):
            with self.assertRaises(SystemExit):
                o19docs.run_docs(self.make_ctx(db, state=state))
        self.assertTrue(state["phases"]["documents"].get("restored"),
                        "the second pass was not a resume")
        self.assertEqual(
            self.details().count("missing file for document 9: gone.pdf"),
            1, self.details())


class TestOrphansAndReporting(DocsDriverBase):
    """Files on disk that no row references are reported, not refused."""

    def test_an_orphan_file_is_counted_in_the_report(self):
        db = FakeDb(documents=[["1", "a.pdf"]])
        self.run_docs(db=db)
        self.assertIn("orphan file(s) on disk", self.report())

    def test_orphan_names_stay_out_of_the_shareable_report(self):
        db = FakeDb(documents=[["1", "a.pdf"]])
        self.run_docs(db=db)
        self.assertNotIn("b.pdf", self.report())
        self.assertIn("b.pdf", self.details())


if __name__ == "__main__":
    unittest.main()
