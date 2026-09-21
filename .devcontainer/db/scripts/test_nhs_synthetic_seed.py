"""Unit tests for the development seed builder; no network or database required."""

import csv
import hashlib
import importlib.util
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("nhs_seed", Path(__file__).with_name("build_nhs_synthetic_seed.py"))
seed = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(seed)


class SeedTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.person = "28570119-9cdc-4120-98c0-4edb76cf36a3"
        self.note = {
            "person_id": self.person, "admission_id": "admission-1",
            "clinical_note_id": "17bf845b-88f8-4604-8983-6e74453aada5",
            "creation_timestamp": "07/01/2026 14:05", "updt_dt_tm": "07/01/2026 14:35",
            "note_subject": "Example", "note_type": "ED", "clean_note_text": "Original 'note'\n\\ text;",
        }
        self.rows = {
            "patients.csv": [{"person_id": self.person, "full_name": "Example Patient",
                              "date_of_birth": "15/05/1984", "gender_identity": "Not specified"}],
            "admissions.csv": [{"patient_id": self.person, "admission_id": "admission-1", "surname": "Patient"}],
            "synthetic_clinical_notes.csv": [self.note],
        }
        self.pinned = ((self.person, "NHSSYN001", 1),)

    def add_second_admission(self):
        self.rows["patients.csv"].append(self.rows["patients.csv"][0].copy())
        self.rows["admissions.csv"].append({"patient_id": self.person, "admission_id": "admission-2",
                                            "surname": "Patient"})
        self.rows["synthetic_clinical_notes.csv"].append(dict(
            self.note, admission_id="admission-2", clinical_note_id="2c0b1c35-0d3f-4f0e-9a51-3f6d0f5f7a10",
            creation_timestamp="03/03/2026 09:00", updt_dt_tm="03/03/2026 09:10", clean_note_text="Second stay."))
        self.pinned = ((self.person, "NHSSYN001", 2),)

    def generate(self, manifest=None):
        checksums = {}
        for name, rows in self.rows.items():
            with (self.root / name).open("w", encoding="utf-8-sig", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
            checksums[name] = hashlib.sha256((self.root / name).read_bytes()).hexdigest()
        with patch.object(seed, "CHECKSUMS", checksums), patch.object(seed, "PATIENTS", self.pinned):
            return seed.build(self.root, manifest)

    def test_manifest_pins_identity_and_exact_seeded_note(self):
        manifest = []
        sql = self.generate(manifest)
        self.assertEqual(sql, self.generate())
        self.assertEqual(len(manifest), 1)
        self.assertEqual(manifest[0]["chart_no"], "NHSSYN001")
        self.assertEqual(manifest[0]["label"], "FAKE-NHS Patient, Example")
        self.assertEqual(manifest[0]["alias"], "NHS synthetic " + self.person)
        self.assertEqual(manifest[0]["notes"][0]["date"], "2026-01-07")
        note_literals = re.findall(r"CONVERT\(0x([0-9a-f]+) USING utf8mb4\)", sql)
        bodies = [bytes.fromhex(value) for value in note_literals
                  if b"SYNTHETIC NHS TEST PATIENT" in bytes.fromhex(value)]
        self.assertEqual(len(bodies), 1)
        self.assertEqual(manifest[0]["notes"][0]["sha256"], hashlib.sha256(bodies[0]).hexdigest())

    def test_deterministic_transactional_additive_seed(self):
        sql = self.generate()
        self.assertEqual(sql, self.generate())
        self.assertIn("START TRANSACTION;", sql)
        self.assertIn("COMMIT;", sql)
        self.assertIn("GET_LOCK(", sql)
        self.assertIn("chart marker collision", sql)
        self.assertIn("note UUID belongs to another chart", sql)
        self.assertIn("archived,appointmentNo,uuid)", sql)
        self.assertIn(",1,'999998','10034','2','',0,0,0,", sql)
        self.assertNotIn("UPDATE demographic", sql)
        self.assertNotIn("TRUNCATE", sql)
        self.assertNotIn("DELETE FROM", sql)

    def test_original_note_text_is_preserved_in_utf8_literal(self):
        self.note["clean_note_text"] = "Quoted ' text \\ newline\n" + chr(0x00B5) + "g"
        sql = self.generate()
        self.assertIn(self.note["clean_note_text"].encode("utf-8").hex(), sql)
        self.assertIn("SYNTHETIC NHS TEST PATIENT".encode().hex(), sql)

    def test_a_patient_with_two_admissions_is_one_chart_holding_both_stays(self):
        self.add_second_admission()
        manifest = []
        sql = self.generate(manifest)
        self.assertEqual(1, sql.count("INSERT INTO demographic "))
        self.assertEqual(2, sql.count("INSERT INTO casemgmt_note "))
        self.assertEqual(["2026-01-07", "2026-03-03"], [note["date"] for note in manifest[0]["notes"]])
        bodies = [bytes.fromhex(value).decode() for value in re.findall(r"CONVERT\(0x([0-9a-f]+) USING utf8mb4\)", sql)
                  if b"SYNTHETIC NHS TEST PATIENT" in bytes.fromhex(value)]
        # Each note keeps the admission it was written in.
        self.assertIn("Source admission ID: admission-1\n", bodies[0])
        self.assertIn("Source admission ID: admission-2\n", bodies[1])
        self.assertIn("-- DEVELOPMENT ONLY. 1 invented NHS patient, 2 synthetic notes.", sql)

    def test_rejects_an_identity_that_changes_between_admissions(self):
        self.add_second_admission()
        self.rows["patients.csv"][1]["date_of_birth"] = "16/05/1984"
        with self.assertRaisesRegex(ValueError, "consistent identity"):
            self.generate()

    def test_final_listing_names_every_pinned_chart(self):
        other = "5cfa1b7e-c60e-4bae-b31f-b6be2c09adcb"
        self.rows["patients.csv"].append(dict(self.rows["patients.csv"][0], person_id=other))
        self.rows["admissions.csv"].append({"patient_id": other, "admission_id": "admission-9", "surname": "Patient"})
        self.rows["synthetic_clinical_notes.csv"].append(dict(
            self.note, person_id=other, admission_id="admission-9",
            clinical_note_id="9d1c7a52-6f3e-4b53-8d0a-0b8f4f1f2a77"))
        self.pinned = ((self.person, "NHSSYN001", 1), (other, "NHSSYN002", 1))
        self.assertIn("WHERE chart_no IN ('NHSSYN001','NHSSYN002') ORDER BY chart_no;", self.generate())

    def test_rejects_cross_admission_note(self):
        self.note["admission_id"] = "other-admission"
        with self.assertRaisesRegex(ValueError, "cross-admission"):
            self.generate()

    def test_rejects_missing_patient_notes(self):
        self.note["person_id"] = "other-patient"
        with self.assertRaisesRegex(ValueError, "note count"):
            self.generate()

    def test_rejects_ambiguous_patient(self):
        self.rows["patients.csv"].append(self.rows["patients.csv"][0].copy())
        with self.assertRaisesRegex(ValueError, "one patient"):
            self.generate()

    def test_rejects_unpinned_source(self):
        (self.root / "patients.csv").write_text("unexpected input", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            seed.read_source(self.root, "patients.csv")

    def test_literals_do_not_allow_sql_escape_sequences(self):
        value = "'); DROP TABLE demographic; -- \\\n"
        self.assertEqual(seed.literal(value), "CONVERT(0x" + value.encode().hex() + " USING utf8mb4)")
        self.assertEqual(seed.literal(""), "''")


if __name__ == "__main__":
    unittest.main()
