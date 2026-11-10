#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
"""A failed clinical oracle reports only trusted field paths, never source values."""
import importlib.util
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from xml.etree import ElementTree as XML
import zipfile

SPEC = importlib.util.spec_from_file_location(
    "cds_oracle_diagnostics", Path(__file__).with_name("run-cds-export-installed-oracle.py"))
ORACLE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ORACLE)


class OracleDiagnosticsTest(unittest.TestCase):
    def test_failure_paths_do_not_echo_patient_identity_values_or_unknown_names(self):
        root = XML.Element("{cds}OmdCds")
        record = XML.SubElement(root, "{cds}PatientRecord")
        demo = XML.SubElement(record, "{cds}Demographics")
        XML.SubElement(demo, "{cds}Gender").text = "F"
        XML.SubElement(demo, "{cds}NoteAboutPatient").text = "PRIVATE_SOURCE_VALUE"
        XML.SubElement(record, "{cds}PRIVATE_ELEMENT_NAME").text = "PRIVATE_SOURCE_VALUE"
        failures = ORACLE.clinical_mismatches(root, 731001)
        encoded = json.dumps(failures)
        self.assertIn("Demographics/c:DateOfBirth", failures)
        self.assertNotIn("Demographics/c:Gender", failures)
        for private in ("731001", "PRIVATE_SOURCE_VALUE", "PRIVATE_ELEMENT_NAME"):
            self.assertNotIn(private, encoded)
        self.assertFalse(ORACLE.clinically_equal(root, 731001))

    def test_invalid_root_has_a_bounded_static_failure(self):
        self.assertEqual(ORACLE.clinical_mismatches(XML.Element("PRIVATE_ROOT"), 731001),
                         ["PATIENT_RECORD_SHAPE"])

    def archive(self, path, *, reference=None, member=None, body=None):
        relative = "Reports/" + "a" * 64 + ".txt"
        reference = relative if reference is None else reference
        member = "Physician/" + relative if member is None else member
        root = XML.Element("{cds}OmdCds")
        record = XML.SubElement(root, "{cds}PatientRecord")
        report = XML.SubElement(record, "{cds}Reports")
        XML.SubElement(report, "{cds}FilePath").text = reference
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("ReadMe.txt", "Independent supporting member")
            archive.writestr("ExportEventLog.csv", "Independent supporting member")
            archive.writestr("Physician/patient.xml", XML.tostring(root))
            archive.writestr(member, body if body is not None else
                "Native consultation report café 😀\nIndependent attachment bytes\n".encode("utf-8"))

    def test_report_resolves_relative_to_the_patient_xml_inside_its_physician_folder(self):
        with tempfile.TemporaryDirectory() as work:
            path = Path(work) / "patient.zip"
            self.archive(path)
            root, names, source = ORACLE.read_native_archive(path)
            self.assertEqual(len(names), 4)
            self.assertEqual(root.tag, "{cds}OmdCds")
            self.assertEqual(XML.fromstring(source).tag, root.tag)

    def test_report_at_the_zip_root_or_another_patient_folder_is_refused(self):
        with tempfile.TemporaryDirectory() as work:
            path = Path(work) / "patient.zip"
            relative = "Reports/" + "a" * 64 + ".txt"
            for member in (relative, "OtherPhysician/" + relative):
                with self.subTest(member=member):
                    self.archive(path, member=member)
                    with self.assertRaisesRegex(RuntimeError, "not beside its patient XML"):
                        ORACLE.read_native_archive(path)

    def test_unsafe_report_references_and_changed_attachment_bytes_are_refused(self):
        with tempfile.TemporaryDirectory() as work:
            path = Path(work) / "patient.zip"
            for reference in ("../Reports/" + "a" * 64 + ".txt", "/Reports/" + "a" * 64 + ".txt"):
                with self.subTest(reference=reference):
                    self.archive(path, reference=reference)
                    with self.assertRaisesRegex(RuntimeError, "relative path differs"):
                        ORACLE.read_native_archive(path)
            self.archive(path, body=b"Changed attachment")
            with self.assertRaisesRegex(RuntimeError, "attachment bytes differ"):
                ORACLE.read_native_archive(path)

    def test_oracle_exceptions_leave_a_failed_phase_without_exception_values(self):
        with tempfile.TemporaryDirectory() as work:
            proof = Path(work) / "proof.json"
            arguments = ["oracle", "--archive", "unused.zip", "--fixture", "unused.json",
                         "--schema", "unused.xsd", "--proof", str(proof)]
            with patch("sys.argv", arguments), patch.object(
                    ORACLE, "qualify", side_effect=RuntimeError("PRIVATE_SOURCE_VALUE")), \
                    contextlib.redirect_stdout(io.StringIO()) as output:
                self.assertEqual(ORACLE.main(), 1)
            result = json.loads(proof.read_text())
            self.assertEqual(result["status"], "FAILED")
            self.assertEqual(result["stage"], "ARCHIVE_CONTRACT")
            self.assertNotIn("PRIVATE_SOURCE_VALUE", proof.read_text() + output.getvalue())


if __name__ == "__main__":
    unittest.main()
