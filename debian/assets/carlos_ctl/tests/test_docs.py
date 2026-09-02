# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Documents-phase contracts: context detection, merge-move safety, HRM
path rewriting, batch-field unescaping, reconciliation classification and
the archive CSV export.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import os
import shutil
import tempfile
import unittest

from carlos_ctl import o19docs


class TestDetectContextDir(unittest.TestCase):

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
                      "SUBSTRING_INDEX(reportFile, '/', -1))", update)
        self.assertIn("WHERE reportFile IS NOT NULL AND reportFile <> ''",
                      update)
        self.assertTrue(select.startswith(
            "SELECT id, reportFile FROM `carlos`.HRMDocument WHERE"))

    def test_rewrite_is_idempotent_on_its_own_output(self):
        # SUBSTRING_INDEX on the already-rewritten path yields the same
        # basename, so a resumed pass changes nothing
        update, _ = o19docs.hrm_rewrite_sql("carlos", "/srv/docs")
        self.assertIn("SUBSTRING_INDEX(reportFile, '/', -1)", update)
        self.assertNotIn("LIKE", update)

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

    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="o19docs-contain-")
        self.addCleanup(shutil.rmtree, self.root)
        self.outside = tempfile.mkdtemp(prefix="o19docs-outside-")
        self.addCleanup(shutil.rmtree, self.outside)
        with open(os.path.join(self.outside, "secret.pdf"), "w") as fh:
            fh.write("x")

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

    def test_round_trips_mariadb_batch_escapes(self):
        self.assertEqual(o19docs.unescape_batch_field("a\\nb\\tc\\\\d"),
                         "a\nb\tc\\d")
        self.assertEqual(o19docs.unescape_batch_field("plain"), "plain")
        self.assertEqual(o19docs.unescape_batch_field("tail\\"), "tail\\")


class TestImageRefs(unittest.TestCase):

    def test_extracts_oscar_image_path_references(self):
        html = ('<img src="${oscar_image_path}logo.png"/>'
                "<img src='${oscar_image_path}sig.jpg'>"
                '<img src="${oscar_image_path}logo.png"/>')
        self.assertEqual(o19docs.image_refs(html), ["logo.png", "sig.jpg"])


class TestMergeMove(unittest.TestCase):

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


class TestReconciliationClassification(unittest.TestCase):

    def setUp(self):
        self.doc_dir = tempfile.mkdtemp(prefix="o19docs-recon-")
        self.addCleanup(shutil.rmtree, self.doc_dir)
        with open(os.path.join(self.doc_dir, "present.pdf"), "w") as fh:
            fh.write("content")
        open(os.path.join(self.doc_dir, "empty.pdf"), "w").close()
        with open(os.path.join(self.doc_dir, "orphan.pdf"), "w") as fh:
            fh.write("orphan")

    def test_missing_and_empty_files_are_blocking(self):
        rows = [("1", "present.pdf"), ("2", "gone.pdf"), ("3", "empty.pdf")]
        missing, empty = o19docs.classify_document_files(rows, self.doc_dir)
        self.assertEqual(missing, ["document 2: gone.pdf"])
        self.assertEqual(empty, ["document 3: empty.pdf (zero bytes)"])

    def test_orphans_are_report_only(self):
        orphans = o19docs.find_orphans(
            self.doc_dir, {"present.pdf", "empty.pdf"})
        self.assertEqual(orphans, ["orphan.pdf"])


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

    def test_query_string_and_fragment_suffixes_are_recognised(self):
        # CARLOS resolves the whole value as the imagefile parameter, so
        # `logo.png?v=2` names a file that does not exist; the suffix is
        # only split off to explain the failure, never to excuse it
        self.assertEqual(o19docs.image_ref_suffix("logo.png?v=2"), "?v=2")
        self.assertEqual(o19docs.image_ref_suffix("form.pdf#page=2"),
                         "#page=2")
        self.assertEqual(o19docs.image_ref_suffix("plain.gif"), "")
        self.assertEqual(o19docs.image_ref_suffix("?only=query"),
                         "?only=query")

    def test_unrelated_html_has_no_refs(self):
        self.assertEqual(o19docs.image_refs("<p>no images</p>"), [])

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
                         '<img src="${oscar_image_path}gone.gif">')]
            return []

        problems, lines = o19docs.reconcile(query, "o19_import", root)
        self.assertEqual(len(problems), 2, problems)
        suffixed = [p for p in problems if "logo.png?v=2" in p]
        self.assertEqual(len(suffixed), 1)
        self.assertIn("does not strip", suffixed[0])
        self.assertIn("logo.png exists", suffixed[0])
        self.assertTrue(any("missing image asset: gone.gif" in p
                            for p in problems))
        self.assertIn("3 eForm image reference(s) checked", lines)


class TestArchiveCsvExport(unittest.TestCase):

    def test_exports_tables_with_unescaped_values(self):
        out = tempfile.mkdtemp(prefix="o19docs-csv-")
        self.addCleanup(shutil.rmtree, out)

        def q(sql):
            if "information_schema.TABLES" in sql:
                return [["formONAR"]]
            if "information_schema.COLUMNS" in sql:
                return [["ID"], ["note"]]
            return [["1", "line1\\nline2"], ["2", "plain"]]

        lines = o19docs.export_archive_csv(q, "o19_archive", out)
        self.assertEqual(lines, ["formONAR.csv: 2 row(s)"])
        with open(os.path.join(out, "formONAR.csv")) as fh:
            content = fh.read()
        self.assertIn("ID,note", content)
        self.assertIn('"line1\nline2"', content)


class TestArchiveCsvNulls(unittest.TestCase):

    def test_batch_null_marker_becomes_empty_field(self):
        out = tempfile.mkdtemp(prefix="o19docs-csvnull-")
        self.addCleanup(shutil.rmtree, out)

        def q(sql):
            if "information_schema.TABLES" in sql:
                return [["t"]]
            if "information_schema.COLUMNS" in sql:
                return [["a"], ["b"]]
            return [["1", "\\N"], ["\\N", "x\\ty"]]
        o19docs.export_archive_csv(q, "arch", out)
        with open(os.path.join(out, "t.csv"), newline="") as fh:
            text = fh.read()
        self.assertNotIn("\\N", text)
        self.assertIn("1,\r\n", text)
        self.assertIn(",x\ty", text)


if __name__ == "__main__":
    unittest.main()
