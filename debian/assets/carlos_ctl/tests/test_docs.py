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

    def test_rewrite_targets_the_old_context_marker(self):
        update, leftover = o19docs.hrm_rewrite_sql(
            "carlos", "oscar_mcmaster", "/var/lib/carlos-emr/OscarDocument")
        self.assertIn("SUBSTRING_INDEX(reportFile, '/oscar_mcmaster/', -1)",
                      update)
        self.assertIn(
            "CONCAT('/var/lib/carlos-emr/OscarDocument/carlos/'", update)
        # '_' is a LIKE wildcard: the marker must be escaped in the pattern
        self.assertIn("LIKE '%/oscar\\\\_mcmaster/%'", update)
        self.assertIn("NOT LIKE '/var/lib/carlos-emr/OscarDocument/carlos/%'",
                      leftover)

    def test_context_with_sql_metacharacters_is_refused(self):
        for bad in ("x'; DROP TABLE HRMDocument; --", "a b", "../etc", ""):
            with self.assertRaises(ValueError):
                o19docs.hrm_rewrite_sql("carlos", bad)
            with self.assertRaises(ValueError):
                o19docs.detect_context_dir([bad + "/", bad + "/document/"])


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

    def test_non_empty_target_is_refused(self):
        os.makedirs(os.path.join(self.dst, "document"))
        with open(os.path.join(self.dst, "document", "existing.pdf"),
                  "w") as fh:
            fh.write("y")
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


NULL_ROWS = []


class TestArchiveCsvExport(unittest.TestCase):

    def test_exports_tables_with_unescaped_values(self):
        out = tempfile.mkdtemp(prefix="o19docs-csv-")
        self.addCleanup(shutil.rmtree, out)

        def q(sql):
            if "information_schema.TABLES" in sql:
                return [["formONAR"]]
            if "information_schema.COLUMNS" in sql:
                return [["ID"], ["note"]]
            if sql.startswith("SELECT * FROM `arch`.`formONAR`") \
                    and NULL_ROWS:
                return NULL_ROWS
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
