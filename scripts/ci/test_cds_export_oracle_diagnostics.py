#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
"""A failed clinical oracle reports only trusted field paths, never source values."""
import importlib.util
import json
from pathlib import Path
import unittest
from xml.etree import ElementTree as XML

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


if __name__ == "__main__":
    unittest.main()
