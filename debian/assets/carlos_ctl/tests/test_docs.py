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

    def test_subdirectory_references_are_blocking(self):
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

        problems, _lines, _private = o19docs.reconcile(query, "o19_import",
                                                       root)
        self.assertEqual(len(problems), 1)
        self.assertIn("names a subdirectory", problems[0])

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

        problems, lines, _private = o19docs.reconcile(query, "o19_import",
                                                      root)
        # logo.png and logo.png#top are one served reference (the
        # fragment never leaves the browser); the other two are not
        self.assertEqual(len(problems), 2, problems)
        suffixed = [p for p in problems if "logo.png?v=2" in p]
        self.assertEqual(len(suffixed), 1)
        self.assertIn("does not strip", suffixed[0])
        self.assertIn("logo.png exists", suffixed[0])
        self.assertTrue(any("missing image asset: gone.gif" in p
                            for p in problems))
        self.assertIn("3 eForm image reference(s) checked", lines)


class TestArchiveCsvExport(unittest.TestCase):

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
        with open(os.path.join(out, "formONAR.csv")) as fh:
            content = fh.read()
        self.assertIn("ID,note", content)
        self.assertIn('"line1\nline2"', content)
        self.assertIn("back\\nslash", content)  # decoded exactly once


class TestArchiveCsvNulls(unittest.TestCase):

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
        with open(os.path.join(out, "t.csv"), newline="") as fh:
            text = fh.read()
        self.assertIn("1,\r\n", text)
        self.assertIn(",x\ty", text)
        self.assertIn("3,NULL", text)  # the stored value, not SQL NULL

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


if __name__ == "__main__":
    unittest.main()
