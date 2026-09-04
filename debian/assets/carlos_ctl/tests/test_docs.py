# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Documents-phase contracts: context detection, merge-move safety, HRM
path rewriting, batch-field unescaping, reconciliation classification and
the archive CSV export.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import contextlib
import csv
import io
import os
import shutil
import tempfile
import unittest
from unittest import mock

from carlos_ctl import o19docs


def _read_csv(path):
    """The export's own rows, read back with the quoting it was written
    with. QUOTE_NOTNULL (3.12+) yields None for a bare empty field (SQL
    NULL) and a string for a quoted one; older interpreters yield ""
    for both, which is the documented degradation."""
    quoting = getattr(csv, "QUOTE_NOTNULL", None)
    with open(path, newline="", encoding="utf-8") as fh:
        reader = (csv.reader(fh, quoting=quoting) if quoting is not None
                  else csv.reader(fh))
        return [row for row in reader]


def _null_or_empty(cell):
    """None for a cell that means SQL NULL on either interpreter."""
    return None if cell in (None, "") else cell


class TestDetectContextDir(unittest.TestCase):

    """Finding the OSCAR context directory inside the documents tar.

    Two candidates or none is a refusal: the tree is merged into the
    live document root, and merging the wrong root is not undoable."""
    def test_single_context_is_detected(self):
        names = ["oscar_mcmaster/", "oscar_mcmaster/document/",
                 "oscar_mcmaster/document/a.pdf",
                 "oscar_mcmaster/eform/images/logo.png"]
        self.assertEqual(o19docs.detect_context_dir(names),
                         "oscar_mcmaster")

    def test_two_contexts_are_refused(self):
        with self.assertRaises(ValueError) as cm:
            o19docs.detect_context_dir(["a/", "a/x.pdf", "b/", "b/y.pdf"])
        self.assertIn("exactly ONE", str(cm.exception))

    def test_loose_files_are_refused(self):
        with self.assertRaises(ValueError) as cm:
            o19docs.detect_context_dir(["oscar/", "oscar/a.pdf",
                                        "stray.pdf"])
        self.assertIn("loose", str(cm.exception))


class TestHrmRewrite(unittest.TestCase):
    """CARLOS's HRMReportParser only trusts an absolute reportFile that
    exists inside DOCUMENT_DIR, so every O19 path — whatever context or
    OMD_hrm directory it named — becomes <root>/carlos/document/<basename>
    and the files are moved there (relocate_hrm_reports)."""

    def test_rewrite_points_every_report_into_document_dir(self):
        update, select = o19docs.hrm_rewrite_sql(
            "carlos", "/var/lib/carlos-emr/OscarDocument")
        self.assertIn("UPDATE `carlos`.HRMDocument SET reportFile = CONCAT("
                      "'/var/lib/carlos-emr/OscarDocument/carlos/document/', "
                      "SUBSTRING_INDEX(REPLACE(reportFile, '\\\\', '/'), "
                      "'/', -1))", update)
        self.assertIn("WHERE reportFile IS NOT NULL AND reportFile <> ''",
                      update)
        self.assertTrue(select.startswith(
            "SELECT id, reportFile FROM `carlos`.HRMDocument WHERE"))

    def test_rewrite_is_idempotent_on_its_own_output(self):
        # SUBSTRING_INDEX on the already-rewritten path yields the same
        # basename, so a resumed pass changes nothing
        update, _ = o19docs.hrm_rewrite_sql("carlos", "/srv/docs")
        self.assertIn("SUBSTRING_INDEX(REPLACE(reportFile, '\\\\', '/'), "
                      "'/', -1)", update)
        self.assertNotIn("LIKE", update)

    def test_basename_twins_query_counts_distinct_paths(self):
        sql = o19docs.hrm_basename_twins_sql("carlos")
        self.assertIn("COUNT(DISTINCT REPLACE(reportFile, '\\\\', '/'))",
                      sql)
        self.assertIn("HAVING paths > 1", sql)

    def test_relocation_walks_the_nested_o19_tree_and_dedupes(self):
        # O19 keeps HRM reports under hrm/sftp_downloads/<date>/decrypted/
        root = tempfile.mkdtemp(prefix="o19docs-hrmtree-")
        self.addCleanup(shutil.rmtree, root)
        a = os.path.join(root, "hrm", "sftp_downloads", "01012020",
                         "decrypted")
        b = os.path.join(root, "hrm", "sftp_downloads", "02012020",
                         "decrypted")
        os.makedirs(a)
        os.makedirs(b)
        for d in (a, b):
            with open(os.path.join(d, "same.xml"), "w") as fh:
                fh.write("<r/>")  # identical re-send
        with open(os.path.join(a, "only.xml"), "w") as fh:
            fh.write("<x/>")
        with open(os.path.join(root, "hrm", "top.xml"), "w") as fh:
            fh.write("<t/>")
        lines = o19docs.relocate_hrm_reports(root)
        doc = os.path.join(root, "document")
        self.assertEqual(sorted(os.listdir(doc)),
                         ["only.xml", "same.xml", "top.xml"])
        self.assertIn("moved 3 HRM report file(s)", lines[0])
        self.assertIn("1 identical duplicate(s) dropped", lines[0])
        self.assertFalse(os.path.exists(a))  # emptied directories go
        # a second pass (resume) finds nothing left and changes nothing
        self.assertEqual(o19docs.relocate_hrm_reports(root), [])

    def test_relocation_refuses_differing_copies_and_names_them_privately(
            self):
        root = tempfile.mkdtemp(prefix="o19docs-hrmdiff-")
        self.addCleanup(shutil.rmtree, root)
        a = os.path.join(root, "hrm", "d1")
        b = os.path.join(root, "hrm", "d2")
        os.makedirs(a)
        os.makedirs(b)
        with open(os.path.join(a, "r.xml"), "w") as fh:
            fh.write("one")
        with open(os.path.join(b, "r.xml"), "w") as fh:
            fh.write("two")
        private = []
        with self.assertRaises(SystemExit):
            o19docs.relocate_hrm_reports(root, private=private.extend)
        self.assertTrue(any("r.xml" in line for line in private))
        # nothing moved
        self.assertFalse(os.path.exists(os.path.join(root, "document",
                                                     "r.xml")))

    def test_a_name_a_document_row_claims_is_never_taken(self):
        # the dangerous case is a document row whose file the tar did NOT
        # carry: nothing sits at that path, so without the reservation
        # the HRM report moves in and reconciliation — which only asks
        # whether a file of that name exists — passes while one patient's
        # chart serves another patient's hospital report
        root = tempfile.mkdtemp(prefix="o19docs-hrmclaim-")
        self.addCleanup(shutil.rmtree, root)
        src = os.path.join(root, "hrm", "sftp_downloads", "01012020",
                           "decrypted")
        os.makedirs(src)
        with open(os.path.join(src, "report.pdf"), "w") as fh:
            fh.write("patient A HRM lab")
        private = []
        with self.assertRaises(SystemExit):
            o19docs.relocate_hrm_reports(root, private=private.extend,
                                         reserved={"report.pdf"})
        self.assertTrue(any("report.pdf" in line for line in private))
        self.assertTrue(any("document row already claims" in line
                            for line in private), private)
        # the refusal names the source tree, so the source must still
        # hold what it names
        self.assertTrue(os.path.isfile(os.path.join(src, "report.pdf")))
        self.assertFalse(os.path.exists(
            os.path.join(root, "document", "report.pdf")))

    def test_nothing_moves_when_a_later_name_is_refused(self):
        # the scan runs to completion before the first move: a refusal on
        # the second name must not leave the first one relocated
        root = tempfile.mkdtemp(prefix="o19docs-hrmscan-")
        self.addCleanup(shutil.rmtree, root)
        a = os.path.join(root, "hrm", "d1")
        b = os.path.join(root, "hrm", "d2")
        os.makedirs(a)
        os.makedirs(b)
        for path, body in ((os.path.join(a, "aaa.xml"), "fine"),
                           (os.path.join(a, "zzz.xml"), "one"),
                           (os.path.join(b, "zzz.xml"), "two")):
            with open(path, "w") as fh:
                fh.write(body)
        with self.assertRaises(SystemExit):
            o19docs.relocate_hrm_reports(root, private=lambda _l: None)
        self.assertFalse(os.path.exists(
            os.path.join(root, "document", "aaa.xml")))
        self.assertTrue(os.path.isfile(os.path.join(a, "aaa.xml")))

    def test_context_with_sql_metacharacters_is_refused(self):
        for bad in ("x'; DROP TABLE HRMDocument; --", "a b", "../etc", ""):
            with self.assertRaises(ValueError):
                o19docs.detect_context_dir([bad + "/", bad + "/document/"])

    def test_hrm_files_are_classified_inside_document_dir(self):
        doc_dir = tempfile.mkdtemp(prefix="o19docs-hrm-")
        self.addCleanup(shutil.rmtree, doc_dir)
        with open(os.path.join(doc_dir, "r1.xml"), "w") as fh:
            fh.write("<report/>")
        open(os.path.join(doc_dir, "empty.xml"), "w").close()
        rows = [("1", doc_dir + "/r1.xml"), ("2", doc_dir + "/gone.xml"),
                ("3", doc_dir + "/empty.xml"), ("4", "../escape.xml"),
                ("5", "/etc/passwd")]
        problems = o19docs.classify_hrm_files(rows, doc_dir)
        self.assertEqual(len(problems), 4)
        self.assertTrue(any("HRMDocument 2" in p for p in problems))
        self.assertTrue(any("HRMDocument 3" in p for p in problems))
        # containment is checked on the FULL value, relative or absolute,
        # not on the basename the rewrite would produce
        self.assertTrue(any("HRMDocument 4" in p and "escapes" in p
                            for p in problems))
        self.assertTrue(any("HRMDocument 5" in p and "escapes" in p
                            for p in problems))


class TestContainment(unittest.TestCase):

    """Whether a path stays inside the tree it is supposed to.

    NUL bytes, absolute names, traversal and symlinks all escape; a
    document row that escapes blocks the phase rather than being
    skipped."""
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="o19docs-contain-")
        self.addCleanup(shutil.rmtree, self.root)
        self.outside = tempfile.mkdtemp(prefix="o19docs-outside-")
        self.addCleanup(shutil.rmtree, self.outside)
        with open(os.path.join(self.outside, "secret.pdf"), "w") as fh:
            fh.write("x")

    def test_nul_in_a_name_is_never_contained(self):
        self.assertFalse(o19docs.contained(self.root, "a\0b.pdf"))

    def test_relative_names_are_contained(self):
        self.assertTrue(o19docs.contained(self.root, "a.pdf"))
        self.assertTrue(o19docs.contained(self.root, "sub/a.pdf"))

    def test_absolute_and_traversal_escape(self):
        self.assertFalse(o19docs.contained(
            self.root, os.path.join(self.outside, "secret.pdf")))
        self.assertFalse(o19docs.contained(self.root, "../x.pdf"))

    def test_symlink_pointing_outside_escapes(self):
        os.symlink(self.outside, os.path.join(self.root, "link"))
        self.assertFalse(o19docs.contained(self.root, "link/secret.pdf"))

    def test_escaping_document_row_is_blocking_not_satisfied(self):
        rows = [("9", os.path.join(self.outside, "secret.pdf")),
                ("10", "../../etc/passwd")]
        missing, empty = o19docs.classify_document_files(rows, self.root)
        self.assertEqual(len(missing), 2)
        self.assertIn("escapes", missing[0])
        self.assertEqual(empty, [])


class TestBatchUnescape(unittest.TestCase):

    """Decoding MariaDB's batch-mode escaping of column values."""
    def test_round_trips_mariadb_batch_escapes(self):
        self.assertEqual(o19docs.unescape_batch_field("a\\nb\\tc\\\\d"),
                         "a\nb\tc\\d")
        self.assertEqual(o19docs.unescape_batch_field("plain"), "plain")
        self.assertEqual(o19docs.unescape_batch_field("tail\\"), "tail\\")


class TestImageRefs(unittest.TestCase):

    """Pulling OSCAR image path references out of note text."""
    def test_extracts_oscar_image_path_references(self):
        html = ('<img src="${oscar_image_path}logo.png"/>'
                "<img src='${oscar_image_path}sig.jpg'>"
                '<img src="${oscar_image_path}logo.png"/>')
        self.assertEqual(o19docs.image_refs(html), ["logo.png", "sig.jpg"])


class TestMoveIntoPlace(unittest.TestCase):

    """`_move_into_place` on its own, because the merge cannot reach the
    case that matters.

    `merge_move`'s pre-scan refuses a symlink that is ALREADY at the
    destination, so a functional test of the merge passes whether or not
    the move itself is safe -- the first version of this test did
    exactly that, and survived reverting the fix. The hazard is a
    symlink planted in the window between `_merge_entry`'s `lexists`
    check and the move, which only a direct call can stage.
    """

    def setUp(self):
        self.work = tempfile.mkdtemp(prefix="o19move-")
        self.addCleanup(shutil.rmtree, self.work)
        self.src = os.path.join(self.work, "src")
        self.elsewhere = os.path.join(self.work, "attacker")
        os.makedirs(self.elsewhere)

    def test_a_directory_is_not_moved_through_a_planted_symlink(self):
        os.makedirs(self.src)
        with open(os.path.join(self.src, "a.pdf"), "w") as fh:
            fh.write("phi")
        dst = os.path.join(self.work, "dst")
        os.symlink(self.elsewhere, dst)     # the planted symlink
        with self.assertRaises(OSError):
            o19docs._move_into_place(self.src, dst)
        self.assertEqual(
            os.listdir(self.elsewhere), [],
            "a patient document subtree was written through a symlink")

    def test_a_file_replaces_the_symlink_rather_than_writing_through_it(
            self):
        with open(self.src, "w") as fh:
            fh.write("phi")
        target = os.path.join(self.elsewhere, "victim")
        with open(target, "w") as fh:
            fh.write("original")
        dst = os.path.join(self.work, "dst")
        os.symlink(target, dst)
        o19docs._move_into_place(self.src, dst)
        self.assertFalse(os.path.islink(dst))
        with open(target) as fh:
            self.assertEqual(fh.read(), "original",
                             "the move wrote through the symlink")
        with open(dst) as fh:
            self.assertEqual(fh.read(), "phi")

    def test_an_ordinary_directory_move_still_works(self):
        os.makedirs(self.src)
        with open(os.path.join(self.src, "a.pdf"), "w") as fh:
            fh.write("phi")
        dst = os.path.join(self.work, "dst")
        o19docs._move_into_place(self.src, dst)
        self.assertTrue(os.path.isfile(os.path.join(dst, "a.pdf")))
        self.assertFalse(os.path.exists(self.src))


class TestMergeMove(unittest.TestCase):

    """Merging the clinic's tree into the CARLOS document root.

    A collision is a refusal, not an overwrite, and the names involved
    go to the root-only file rather than the shareable report. A resume
    accepts files already in place only when they are identical."""
    def setUp(self):
        self.work = tempfile.mkdtemp(prefix="o19docs-test-")
        self.addCleanup(shutil.rmtree, self.work)
        self.src = os.path.join(self.work, "incoming", "oscar_mcmaster")
        self.dst = os.path.join(self.work, "OscarDocument", "carlos")
        os.makedirs(os.path.join(self.src, "document"))
        os.makedirs(os.path.join(self.src, "document_cache"))
        with open(os.path.join(self.src, "document", "a.pdf"), "w") as fh:
            fh.write("x")

    def test_moves_children_and_skips_cache_dirs(self):
        lines = o19docs.merge_move(self.src, self.dst)
        self.assertTrue(os.path.isfile(
            os.path.join(self.dst, "document", "a.pdf")))
        self.assertFalse(os.path.exists(
            os.path.join(self.dst, "document_cache")))
        self.assertTrue(any("cache" in line for line in lines))

    def test_empty_skeleton_dirs_are_replaced(self):
        os.makedirs(os.path.join(self.dst, "document"))
        o19docs.merge_move(self.src, self.dst)
        self.assertTrue(os.path.isfile(
            os.path.join(self.dst, "document", "a.pdf")))

    def test_a_plain_move_still_lands(self):
        # the symlink-safe move must not turn the ordinary case into a
        # refusal; `_move_into_place` is what carries this
        os.makedirs(self.dst)
        o19docs.merge_move(self.src, self.dst)
        self.assertTrue(os.path.isfile(
            os.path.join(self.dst, "document", "a.pdf")))

    def test_file_collision_is_refused(self):
        # the same path as a FILE on both sides: the target is not pristine
        os.makedirs(os.path.join(self.dst, "document"))
        with open(os.path.join(self.dst, "document", "a.pdf"), "w") as fh:
            fh.write("y")
        with self.assertRaises(SystemExit):
            o19docs.merge_move(self.src, self.dst)

    def test_nested_skeleton_is_merged_recursively(self):
        # the deb postinst installs incomingdocs/1/{Fax,File,...}: the tar's
        # incomingdocs/1/Fax/x.pdf must land INSIDE that skeleton, not
        # replace it
        os.makedirs(os.path.join(self.dst, "incomingdocs", "1", "Fax"))
        os.makedirs(os.path.join(self.dst, "incomingdocs", "1", "Mail"))
        os.makedirs(os.path.join(self.src, "incomingdocs", "1", "Fax"))
        with open(os.path.join(self.src, "incomingdocs", "1", "Fax",
                               "x.pdf"), "w") as fh:
            fh.write("z")
        lines = o19docs.merge_move(self.src, self.dst)
        self.assertTrue(os.path.isfile(os.path.join(
            self.dst, "incomingdocs", "1", "Fax", "x.pdf")))
        self.assertTrue(os.path.isdir(os.path.join(
            self.dst, "incomingdocs", "1", "Mail")))
        self.assertTrue(any("merged into existing incomingdocs/" in line
                            for line in lines))

    def test_nested_collision_leaves_the_target_untouched(self):
        # the collision sits two levels deep and sorts AFTER a sibling that
        # would otherwise be moved first: nothing at all may move
        os.makedirs(os.path.join(self.src, "incomingdocs", "1", "Fax"))
        with open(os.path.join(self.src, "incomingdocs", "1", "Fax",
                               "a.pdf"), "w") as fh:
            fh.write("a")
        with open(os.path.join(self.src, "incomingdocs", "1", "Fax",
                               "z.pdf"), "w") as fh:
            fh.write("z")
        os.makedirs(os.path.join(self.dst, "incomingdocs", "1", "Fax"))
        with open(os.path.join(self.dst, "incomingdocs", "1", "Fax",
                               "z.pdf"), "w") as fh:
            fh.write("existing")
        with self.assertRaises(SystemExit):
            o19docs.merge_move(self.src, self.dst)
        self.assertFalse(os.path.exists(os.path.join(
            self.dst, "incomingdocs", "1", "Fax", "a.pdf")))
        self.assertFalse(os.path.exists(os.path.join(self.dst, "document",
                                                     "a.pdf")))
        self.assertTrue(os.path.isfile(os.path.join(
            self.src, "document", "a.pdf")))

    def test_symlink_in_the_tree_is_refused(self):
        os.symlink("/etc", os.path.join(self.src, "evil"))
        with self.assertRaises(SystemExit):
            o19docs.merge_move(self.src, self.dst)

    def test_collision_names_go_to_the_private_callback_only(self):
        os.makedirs(os.path.join(self.dst, "document"))
        with open(os.path.join(self.dst, "document", "a.pdf"), "w") as fh:
            fh.write("y")
        private = []
        with self.assertRaises(SystemExit) as cm:
            o19docs.merge_move(self.src, self.dst, private=private.extend)
        self.assertTrue(any("a.pdf" in line for line in private))
        self.assertNotIn("a.pdf", str(cm.exception))

    def test_resume_accepts_identical_files_already_in_place(self):
        # an interrupted merge left a.pdf at its destination; the same
        # tar re-extracted must complete, not refuse
        os.makedirs(os.path.join(self.dst, "document"))
        with open(os.path.join(self.dst, "document", "a.pdf"), "w") as fh:
            fh.write("x")
        with open(os.path.join(self.src, "document", "b.pdf"), "w") as fh:
            fh.write("b")
        with self.assertRaises(SystemExit):
            o19docs.merge_move(self.src, self.dst)  # not a resume: refused
        lines = o19docs.merge_move(self.src, self.dst, resume=True)
        self.assertTrue(os.path.isfile(
            os.path.join(self.dst, "document", "b.pdf")))
        self.assertFalse(os.path.exists(os.path.join(self.src, "document")))
        self.assertTrue(any("merged into existing document/" in line
                            for line in lines))

    def test_resume_still_refuses_differing_content(self):
        os.makedirs(os.path.join(self.dst, "document"))
        with open(os.path.join(self.dst, "document", "a.pdf"), "w") as fh:
            fh.write("different")
        with self.assertRaises(SystemExit):
            o19docs.merge_move(self.src, self.dst, resume=True)
        with open(os.path.join(self.dst, "document", "a.pdf")) as fh:
            self.assertEqual(fh.read(), "different")  # untouched


class TestReconciliationClassification(unittest.TestCase):

    """Which reconciliation findings block the import and which report.

    A file CARLOS cannot open, or one that is missing or empty, is
    blocking; an orphan on disk is report-only. The counts are exact
    even where the sample is not."""
    def setUp(self):
        self.doc_dir = tempfile.mkdtemp(prefix="o19docs-recon-")
        self.addCleanup(shutil.rmtree, self.doc_dir)
        with open(os.path.join(self.doc_dir, "present.pdf"), "w") as fh:
            fh.write("content")
        open(os.path.join(self.doc_dir, "empty.pdf"), "w").close()
        with open(os.path.join(self.doc_dir, "orphan.pdf"), "w") as fh:
            fh.write("orphan")

    def test_names_carlos_cannot_open_are_blocking(self):
        # PathValidationUtils.sanitizeFileName runs the value through
        # FilenameUtils.getName and refuses a dot-leading basename, so a
        # file that exists at the nested path is still never served
        nested = os.path.join(self.doc_dir, "sub")
        os.makedirs(nested)
        for name in ("deep.pdf", ".hidden.pdf"):
            with open(os.path.join(nested, name), "w") as fh:
                fh.write("x")
        with open(os.path.join(self.doc_dir, ".hidden.pdf"), "w") as fh:
            fh.write("x")
        rows = [("4", "sub/deep.pdf"), ("5", ".hidden.pdf")]
        missing, empty = o19docs.classify_document_files(rows, self.doc_dir)
        self.assertEqual(empty, [])
        self.assertEqual(len(missing), 2, missing)
        self.assertIn("names a subdirectory", missing[0])
        self.assertIn("leading dot", missing[1])

    def test_missing_and_empty_files_are_blocking(self):
        rows = [("1", "present.pdf"), ("2", "gone.pdf"), ("3", "empty.pdf")]
        missing, empty = o19docs.classify_document_files(rows, self.doc_dir)
        self.assertEqual(missing, ["document 2: gone.pdf"])
        self.assertEqual(empty, ["document 3: empty.pdf (zero bytes)"])

    def test_orphans_are_report_only(self):
        total, sample = o19docs.find_orphans(
            self.doc_dir, {"present.pdf", "empty.pdf"})
        self.assertEqual((total, sample), (1, ["orphan.pdf"]))

    def test_the_orphan_count_is_not_capped_by_the_sample(self):
        # the report states this number: a capped one would understate
        # what the clinic is carrying
        extra = tempfile.mkdtemp(prefix="o19docs-orph-")
        self.addCleanup(shutil.rmtree, extra)
        for i in range(60):
            with open(os.path.join(extra, "x{0:03d}.pdf".format(i)),
                      "w") as fh:
                fh.write("x")
        total, sample = o19docs.find_orphans(extra, set(), cap=50)
        self.assertEqual(total, 60)
        self.assertEqual(len(sample), 50)

    def test_report_lines_carry_counts_never_file_names(self):
        ctx_root = tempfile.mkdtemp(prefix="o19docs-ctx-")
        self.addCleanup(shutil.rmtree, ctx_root)
        doc_dir = os.path.join(ctx_root, "document")
        os.makedirs(doc_dir)
        with open(os.path.join(doc_dir, "present.pdf"), "w") as fh:
            fh.write("content")
        with open(os.path.join(doc_dir, "SMITH_JOHN_scan.pdf"), "w") as fh:
            fh.write("orphan")

        def query(sql):
            if ".document" in sql:
                return [("1", "present.pdf"), ("2", "DOE_JANE_scan.pdf")]
            return []

        problems, lines, private = o19docs.reconcile(query, "x", ctx_root)
        joined = "\n".join(lines)
        self.assertNotIn("SMITH", joined)
        self.assertNotIn("DOE", joined)
        self.assertIn("1 orphan file(s)", joined)
        self.assertTrue(any("SMITH_JOHN_scan.pdf" in p for p in private))
        self.assertTrue(any("DOE_JANE_scan.pdf" in p for p in problems))


class TestEformImageRefs(unittest.TestCase):
    """eForm HTML references its images through ${oscar_image_path}; the
    tokens are often URL-encoded by editors, and every spelling must be
    reconciled against eform/images."""

    def test_plain_and_url_encoded_tokens_are_found(self):
        html = ('<img src="${oscar_image_path}logo.png">'
                '<img src="$%7Boscar_image_path%7Dsig.png">'
                "<img src='%24%7Boscar_image_path%7Dstamp.gif'>"
                '<a href="${oscar_image_path}form.pdf?x=1">')
        self.assertEqual(o19docs.image_refs(html),
                         sorted(["logo.png", "sig.png", "stamp.gif",
                                 "form.pdf?x=1"]))

    def test_lookup_name_keeps_the_query_and_drops_the_fragment(self):
        # the browser never sends `#page=2`; a `?v=2` stays inside the
        # imagefile value, so CARLOS looks up a file literally named so
        self.assertEqual(o19docs.image_ref_lookup("logo.png?v=2"),
                         "logo.png?v=2")
        self.assertEqual(o19docs.image_ref_lookup("form.pdf#page=2"),
                         "form.pdf")
        self.assertEqual(o19docs.image_ref_lookup("plain.gif"), "plain.gif")
        self.assertEqual(o19docs.image_ref_lookup("#only-fragment"), "")

    def test_unrelated_html_has_no_refs(self):
        self.assertEqual(o19docs.image_refs("<p>no images</p>"), [])

    def test_references_are_decoded_the_way_the_route_receives_them(self):
        # a quoted value may carry spaces (real forms do), editors write
        # entities and percent-encoding, and a second query parameter
        # is not part of the imagefile value
        html = ('<img src="${oscar_image_path}my scan[1].png">'
                "<img src='${oscar_image_path}my%20logo.png'>"
                '<img src="${oscar_image_path}logo.png&amp;x=1">'
                '<img src=${oscar_image_path}bare.gif width=3>'
                '<img src="${oscar_image_path}a.png#top">'
                '<img src="${oscar_image_path}sub/deep.png">'
                '<div style="background:url(${oscar_image_path}bg.png)">')
        self.assertEqual(o19docs.image_refs(html),
                         sorted(["my scan[1].png", "my logo.png",
                                 "logo.png", "bare.gif", "a.png",
                                 "sub/deep.png", "bg.png"]))

    def test_a_percent_encoded_name_is_decoded_exactly_once(self):
        # image_refs already percent-decoded; decoding again in reconcile
        # would split a name that legitimately contains '#' or '&' and
        # turn a working form into a blocking P5 failure
        root = tempfile.mkdtemp(prefix="o19docs-pct-")
        self.addCleanup(shutil.rmtree, root)
        os.makedirs(os.path.join(root, "document"))
        os.makedirs(os.path.join(root, "eform", "images"))
        for name in ("chart#2.png", "a&b.png"):
            with open(os.path.join(root, "eform", "images", name),
                      "wb") as fh:
                fh.write(b"png")

        def query(sql):
            if ".eform" in sql:
                return [("9", "Chart",
                         '<img src="${oscar_image_path}chart%232.png">'
                         '<img src="${oscar_image_path}a%26b.png">')]
            return []

        problems, lines, _private = o19docs.reconcile(query, "o19_import",
                                                      root)
        self.assertEqual(problems, [])
        self.assertIn("2 eForm image reference(s) checked", lines)

    def test_the_css_wrapper_is_recognised_however_it_is_written(self):
        # CSS keywords are case-insensitive and whitespace is allowed
        # around the parenthesis; missing the wrapper leaves the closing
        # ')' on the filename, so a present image reads as missing
        for wrapper in ("url(", "URL(", "Url (", "url( ", "URL ( "):
            html = ('<div style="background:{0}${{oscar_image_path}}'
                    'bg.png)">'.format(wrapper))
            self.assertEqual(o19docs.image_refs(html), ["bg.png"],
                             "wrapper {0!r}".format(wrapper))
        # outside a wrapper ')' stays part of the name, and a word that
        # merely ends in "url" is not one
        self.assertEqual(
            o19docs.image_refs("<img src=${oscar_image_path}my(1).png>"),
            ["my(1).png"])
        self.assertEqual(
            o19docs.image_refs("curl(${oscar_image_path}odd).png "),
            ["odd).png"])

    def test_a_reference_that_escapes_the_image_dir_still_blocks(self):
        # unlike a subdirectory or a query suffix, this is not a form
        # addressing a PRESENT asset wrongly: no migration should
        # complete carrying a traversal-shaped reference
        root = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, root)
        os.makedirs(os.path.join(root, "document"))
        os.makedirs(os.path.join(root, "eform", "images"))

        def query(sql):
            if ".eform" in sql:
                return [("11", "Escaping",
                         '<img src="${oscar_image_path}../../etc/x.png">')]
            return []

        problems, _lines, _private = o19docs.reconcile(query, "o19_import",
                                                       root)
        self.assertEqual(len(problems), 1, problems)
        self.assertIn("escapes eform/images", problems[0])

    def test_subdirectory_references_are_reported_not_blocking(self):
        root = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, root)
        os.makedirs(os.path.join(root, "document"))
        os.makedirs(os.path.join(root, "eform", "images", "sub"))
        with open(os.path.join(root, "eform", "images", "sub", "d.png"),
                  "wb") as fh:
            fh.write(b"png")

        def query(sql):
            if ".eform" in sql:
                return [("8", "Deep",
                         '<img src="${oscar_image_path}sub/d.png">')]
            return []

        problems, lines, private = o19docs.reconcile(query, "o19_import",
                                                     root)
        # the asset is present; only the form HTML addresses it wrongly,
        # and no tar or file copy can fix that — so it is reported, not
        # a refusal the operator cannot clear
        self.assertEqual(problems, [])
        self.assertTrue(any("cannot route to" in ln for ln in lines), lines)
        self.assertTrue(any("names a subdirectory" in ln
                            for ln in private), private)
        self.assertFalse(any("d.png" in ln for ln in lines), lines)

    def test_reconcile_checks_the_full_reference_as_carlos_resolves_it(self):
        root = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, root)
        os.makedirs(os.path.join(root, "document"))
        os.makedirs(os.path.join(root, "eform", "images"))
        with open(os.path.join(root, "eform", "images", "logo.png"),
                  "wb") as fh:
            fh.write(b"png")

        def query(sql):
            if ".eform" in sql:
                return [("7", "Consent",
                         '<img src="${oscar_image_path}logo.png?v=2">'
                         '<img src="${oscar_image_path}logo.png">'
                         '<img src="${oscar_image_path}logo.png#top">'
                         '<img src="${oscar_image_path}gone.gif">')]
            return []

        problems, lines, private = o19docs.reconcile(query, "o19_import",
                                                     root)
        # logo.png and logo.png#top are one served reference (the
        # fragment never leaves the browser); the other two are not
        self.assertEqual(len(problems), 1, problems)
        self.assertIn("missing image asset: gone.gif", problems[0])
        # the query suffix is a routing defect, not a missing file
        suffixed = [ln for ln in private if "logo.png?v=2" in ln]
        self.assertEqual(len(suffixed), 1)
        self.assertIn("does not strip", suffixed[0])
        self.assertIn("logo.png is present", suffixed[0])
        self.assertIn("3 eForm image reference(s) checked", lines)


class TestArchiveCsvExport(unittest.TestCase):

    """The CSV rendering of the archive schema handed to the clinic."""
    def test_exports_tables_with_decoded_values(self):
        # the client wrapper (o19import.batch_rows) decodes batch escapes
        # once; the phase must write the decoded value as-is, never decode
        # again (a literal backslash-n in clinic data would become a newline)
        out = tempfile.mkdtemp(prefix="o19docs-csv-")
        self.addCleanup(shutil.rmtree, out)

        def q(sql):
            if "information_schema.TABLES" in sql:
                return [["formONAR"]]
            if "information_schema.COLUMNS" in sql:
                return [["ID"], ["note"]]
            # the export asks for each column next to its IS NULL flag;
            # the rows arrive decoded exactly once (a stored backslash-n
            # is two characters, a newline is a newline)
            self.assertIn("`ID`, (`ID` IS NULL), `note`, (`note` IS NULL)",
                          sql)
            return [["1", "0", "line1\nline2", "0"],
                    ["2", "0", "back\\nslash", "0"]]

        lines = o19docs.export_archive_csv(q, "o19_archive", out)
        self.assertEqual(lines, ["formONAR.csv: 2 row(s)"])
        # assert the VALUES, not the quoting: the writer runs under
        # QUOTE_NOTNULL on 3.12+ (which quotes every non-NULL field) and
        # under QUOTE_MINIMAL below that, so a rendering assertion passes
        # on the dev interpreter and fails on the one the package targets
        # (Ubuntu 26.04 ships 3.14). Read it back the way it was written.
        self.assertEqual(_read_csv(os.path.join(out, "formONAR.csv")),
                         [["ID", "note"],
                          ["1", "line1\nline2"],
                          ["2", "back\\nslash"]])   # decoded exactly once


class TestArchiveCsvWindowing(unittest.TestCase):

    """The export reads one window at a time.

    A `SELECT *` over an archive table put the whole table in memory
    twice -- the client buffers the result set and `batch_rows`
    materialises a second copy -- as root, on a host that is also
    running MariaDB. The archive schema is the one place a clinic's own
    data decides the size, so the read has to be bounded by the tool.
    """

    def setUp(self):
        self.work = tempfile.mkdtemp(prefix="o19csvwin-")
        self.addCleanup(shutil.rmtree, self.work)
        self.seen = []

    def query_for(self, total, window):
        def q(sql):
            self.seen.append(sql)
            if sql.startswith("SELECT TABLE_NAME"):
                return [["t"]]
            if sql.startswith("SELECT COLUMN_NAME"):
                return [["a"]]
            off = int(sql.rsplit("OFFSET ", 1)[1])
            take = max(0, min(window, total - off))
            return [[str(off + i), "0"] for i in range(take)]
        return q

    def rows_written(self):
        with open(os.path.join(self.work, "t.csv")) as fh:
            return [ln for ln in fh.read().splitlines() if ln][1:]

    def test_a_table_larger_than_one_window_is_read_in_windows(self):
        with mock.patch.object(o19docs, "CSV_EXPORT_WINDOW", 10):
            lines = o19docs.export_archive_csv(
                self.query_for(25, 10), "arch", self.work)
        selects = [s for s in self.seen if " OFFSET " in s]
        self.assertEqual([int(s.rsplit("OFFSET ", 1)[1]) for s in selects],
                         [0, 10, 20])
        self.assertEqual(len(self.rows_written()), 25)
        self.assertIn("t.csv: 25 row(s)", lines)

    def test_every_row_arrives_exactly_once_and_in_order(self):
        # a windowed read that repeats or drops a slice would still
        # produce a plausible CSV, and for an archive-only table this
        # file is the only copy the clinic keeps
        with mock.patch.object(o19docs, "CSV_EXPORT_WINDOW", 4):
            o19docs.export_archive_csv(
                self.query_for(11, 4), "arch", self.work)
        self.assertEqual([r.strip('"') for r in self.rows_written()],
                         [str(i) for i in range(11)])

    def test_an_exact_multiple_costs_one_empty_window(self):
        with mock.patch.object(o19docs, "CSV_EXPORT_WINDOW", 5):
            o19docs.export_archive_csv(
                self.query_for(10, 5), "arch", self.work)
        selects = [s for s in self.seen if " OFFSET " in s]
        self.assertEqual(len(selects), 3)
        self.assertEqual(len(self.rows_written()), 10)


class TestArchiveCsvRowShape(unittest.TestCase):

    """Row width and ordering in the CSV export.

    A row of the wrong width is a refusal: a silently ragged CSV is a
    file the clinic cannot trust and cannot check."""
    def test_a_row_of_the_wrong_width_is_refused(self):
        # padding a short row or dropping a long row's tail writes a
        # plausible but wrong archive, and for an archive-only table the
        # CSV is the only copy the clinic keeps
        out = tempfile.mkdtemp(prefix="o19docs-csvshape-")
        self.addCleanup(shutil.rmtree, out)

        def short(sql):
            if "information_schema.TABLES" in sql:
                return [["t"]]
            if "information_schema.COLUMNS" in sql:
                return [["a"], ["b"], ["c"]]
            return [["1", "0", "2", "0"]]        # 4 fields, 6 expected

        with self.assertRaises(SystemExit):
            o19docs.export_archive_csv(short, "arch", out)

        def long_(sql):
            if "information_schema.TABLES" in sql:
                return [["t"]]
            if "information_schema.COLUMNS" in sql:
                return [["a"]]
            return [["1", "0", "EXTRA"]]         # 3 fields, 2 expected

        with self.assertRaises(SystemExit):
            o19docs.export_archive_csv(long_, "arch", out)

    def test_rows_are_exported_in_a_stable_order(self):
        out = tempfile.mkdtemp(prefix="o19docs-csvorder-")
        self.addCleanup(shutil.rmtree, out)
        seen = []

        def q(sql):
            if "information_schema.TABLES" in sql:
                return [["t"]]
            if "information_schema.COLUMNS" in sql:
                return [["a"], ["b"]]
            seen.append(sql)
            return [["1", "0", "x", "0"]]

        o19docs.export_archive_csv(q, "arch", out)
        # the ORDER BY is what makes the LIMIT/OFFSET windowing a
        # partition of the result rather than an arbitrary re-slice, so
        # it must sit immediately before the window clause
        self.assertIn("ORDER BY 1, 3 LIMIT ", seen[0])


class TestArchiveCsvNulls(unittest.TestCase):

    """SQL NULL vs the empty string vs the literal text "NULL".

    They are three different values in a clinical record, and the export
    has to keep them distinguishable."""
    def test_null_flag_becomes_empty_field_and_null_text_survives(self):
        out = tempfile.mkdtemp(prefix="o19docs-csvnull-")
        self.addCleanup(shutil.rmtree, out)

        def q(sql):
            if "information_schema.TABLES" in sql:
                return [["t"]]
            if "information_schema.COLUMNS" in sql:
                return [["a"], ["b"]]
            # the batch client prints SQL NULL as the word NULL, exactly
            # like a stored string 'NULL': only the flag tells them apart
            return [["1", "0", "NULL", "1"], ["NULL", "1", "x\ty", "0"],
                    ["3", "0", "NULL", "0"]]
        o19docs.export_archive_csv(q, "arch", out)
        rows = _read_csv(os.path.join(out, "t.csv"))
        self.assertEqual(rows[0], ["a", "b"])
        # a SQL NULL reads back as None under QUOTE_NOTNULL and as "" on
        # an interpreter without it (the documented degradation), so the
        # NULL cells are compared through one helper
        self.assertEqual([_null_or_empty(c) for c in rows[1]], ["1", None])
        self.assertEqual([_null_or_empty(c) for c in rows[2]], [None, "x\ty"])
        # the stored four-character string 'NULL', never SQL NULL
        self.assertEqual(rows[3], ["3", "NULL"])

    @unittest.skipUnless(hasattr(csv, "QUOTE_NOTNULL"),
                         "interpreter predates csv.QUOTE_NOTNULL (3.12)")
    def test_sql_null_is_distinguishable_from_an_empty_string(self):
        # the whole reason the writer asks for QUOTE_NOTNULL: on the
        # interpreter the package actually ships against, a stored '' and
        # a SQL NULL must not both come back as an empty cell
        out = tempfile.mkdtemp(prefix="o19docs-csvnull2-")
        self.addCleanup(shutil.rmtree, out)

        def q(sql):
            if "information_schema.TABLES" in sql:
                return [["t"]]
            if "information_schema.COLUMNS" in sql:
                return [["a"], ["b"]]
            return [["", "0", "", "1"]]        # stored '' , then SQL NULL
        o19docs.export_archive_csv(q, "arch", out)
        with open(os.path.join(out, "t.csv"), newline="") as fh:
            raw = fh.read()
        self.assertIn('"",\r\n', raw)       # quoted '' then a bare NULL
        row = _read_csv(os.path.join(out, "t.csv"))[1]
        self.assertEqual(row[0], "")
        self.assertIsNone(row[1])

    def test_archive_names_outside_the_identifier_class_are_refused(self):
        out = tempfile.mkdtemp(prefix="o19docs-csvname-")
        self.addCleanup(shutil.rmtree, out)

        def q(sql):
            if "information_schema.TABLES" in sql:
                return [["t`;DROP DATABASE x;--"]]
            return []
        with self.assertRaises(SystemExit):
            o19docs.export_archive_csv(q, "arch", out)
        self.assertEqual(os.listdir(out), [])


class TestOwnershipSymlinkGuard(unittest.TestCase):
    """apply_ownership refuses a documents tree holding symbolic links.

    The tree is owned by the unprivileged service account, so a link
    planted there and followed by a root-run `chown -R` would hand that
    account ownership of whatever it points at.
    """

    def setUp(self):
        self._run = o19docs.run
        self._geteuid = os.geteuid
        os.geteuid = lambda: 0
        self.calls = []
        self.addCleanup(self._restore)

    def _restore(self):
        o19docs.run = self._run
        os.geteuid = self._geteuid

    def _install(self, find_rc, find_out):
        class CP(object):
            """Completed-subprocess stand-in (returncode, stdout)."""

            def __init__(self, rc, out=""):
                self.returncode = rc
                self.stdout = out

        def fake_run(argv, **kw):
            self.calls.append(argv)
            if argv[0] == "find" and "-type" in argv and "l" in argv:
                return CP(find_rc, find_out)
            return CP(0, "")
        o19docs.run = fake_run

    def test_a_link_whose_name_holds_a_space_counts_as_one(self):
        # -print0 output: whitespace splitting would report two links and
        # send the operator looking for a file that is not there
        self._install(0, "/docs/my scan.pdf\0")
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            with self.assertRaises(SystemExit):
                o19docs.apply_ownership("/docs", False)
        self.assertIn("1 symbolic link(s)", err.getvalue())
        self.assertNotIn("chown", [c[0] for c in self.calls])

    def test_an_unreadable_tree_is_fatal_rather_than_chowned(self):
        # find failing with no output is not "no links found": chowning
        # blind is exactly what this guard exists to prevent
        self._install(1, "")
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            with self.assertRaises(SystemExit):
                o19docs.apply_ownership("/docs", False)
        self.assertIn("could not scan", err.getvalue())
        self.assertNotIn("chown", [c[0] for c in self.calls])

    def test_a_clean_tree_is_chowned_without_dereferencing_links(self):
        self._install(0, "")
        o19docs.apply_ownership("/docs", False)
        chowns = [c for c in self.calls if c[0] == "chown"]
        self.assertEqual(len(chowns), 1)
        self.assertIn("-Rh", chowns[0])


if __name__ == "__main__":
    unittest.main()
