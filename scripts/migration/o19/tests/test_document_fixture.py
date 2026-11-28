# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Check the document inputs against the pinned OSCAR 19 importer."""

from pathlib import Path
import subprocess
import tarfile
import tempfile
from types import SimpleNamespace
import unittest

from carlos_ctl import o19_preflight, o19docs


FIXTURES = Path(__file__).resolve().parents[1] / "fixtures"


class TestDocumentFixture(unittest.TestCase):
    def test_standalone_archive_preserves_the_synthetic_document_set(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            result = subprocess.run(["bash", FIXTURES / "documents/make-documents-tar.sh", work],
                                    cwd=work, check=True, capture_output=True, text=True)
            self.assertIn("placeholder archive", result.stderr)
            with tarfile.open(work / "o19-documents.tar.gz") as documents:
                self.assertEqual({item.name for item in documents if item.isfile()}, {
                    "oscar_mcmaster/document/demo_referral_note.pdf",
                    "oscar_mcmaster/document/demo_lab_scan.pdf",
                    "oscar_mcmaster/document/demo_orphan_upload.pdf",
                    "oscar_mcmaster/eform/images/demo_clinic_logo.png",
                    "oscar_mcmaster/incomingdocs/queue1/demo_incoming_fax.pdf",
                })

    def test_unusable_explicit_editor_fails_before_writing_an_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            empty = work / "empty.js"
            empty.touch()
            for editor in ("", empty, work / "missing.js"):
                with self.subTest(editor=editor):
                    result = subprocess.run(["bash", FIXTURES / "documents/make-documents-tar.sh", work, editor],
                                            capture_output=True, text=True)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("expected a readable, non-empty OSCAR editor", result.stderr)
                    self.assertIn(str(editor), result.stderr)
                    self.assertFalse((work / "o19-documents.tar.gz").exists())

    def test_only_missing_chart_attachment_requires_a_decision_after_ldap_remediation(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            editor = work / "editControl.js"
            editor.write_bytes(b"/* fixture editor bytes */\n")
            subprocess.run(
                ["bash", FIXTURES / "documents/make-documents-tar.sh", work, editor],
                check=True,
                capture_output=True,
                text=True,
            )
            archive = work / "o19-documents.tar.gz"
            with tarfile.open(archive) as documents:
                with documents.extractfile("oscar_mcmaster/eform/images/editControl.js") as asset:
                    self.assertEqual(asset.read(), editor.read_bytes())

            def query(sql):
                if "FROM `source`.`document`" in sql:
                    return [
                        ["1", "demo_referral_note.pdf"],
                        ["2", "demo_lab_scan.pdf"],
                        ["3", "demo_missing_scan.pdf"],
                    ]
                self.assertIn("FROM `source`.`eform`", sql)
                return [
                    ["1", "Stock Letter", '<script src="${oscar_image_path}editControl.js"></script>'],
                    ["2", "Demo Letterhead", '<img src="${oscar_image_path}demo_clinic_logo.png">'],
                ]

            findings = o19docs.source_document_findings(
                {"state_dir": str(work), "documents": str(archive), "query": query, "dev_target": True},
                "source",
                {"document": "document", "eform": "eform"},
            )
            self.assertEqual([finding["id"] for finding in findings], ["missing-source-documents"])
            details = (work / o19docs.DOCUMENT_PREFLIGHT_DETAILS).read_text(encoding="utf-8")
            self.assertEqual(len(details.splitlines()), 1)
            self.assertIn("demo_missing_scan.pdf", details)

            properties = o19_preflight.parse_properties(FIXTURES / "properties/oscar-clinic-example.properties")
            original = SimpleNamespace(findings=list(findings))
            o19_preflight.check_properties(original, properties, locked_notes=False)
            self.assertEqual(o19_preflight.preflight_verdict(original.findings, ())[:2], ("no-go", 2))
            self.assertIn("B5-ldap", [finding["id"] for finding in original.findings])

            remediated = {key: value for key, value in properties.items() if not key.startswith("ldap.")}
            assessment = SimpleNamespace(findings=list(findings))
            o19_preflight.check_properties(assessment, remediated, locked_notes=False)
            verdict, code, _, outstanding = o19_preflight.preflight_verdict(assessment.findings, ())
            self.assertEqual((verdict, code), ("go-with-acknowledgements", 1))
            self.assertEqual([finding["accept"] for finding in outstanding], ["missing-source-documents"])
