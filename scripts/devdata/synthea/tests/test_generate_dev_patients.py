# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Tests of the synthetic development patients generator (#6148).

Run from the repository root (no download needed: the tests build a tiny sample in memory):
    python3 -m unittest discover -s scripts/devdata/synthea/tests -t .
"""

import contextlib
import csv
import importlib.util
import io
import re
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("generate_dev_patients", HERE.parent / "generate_dev_patients.py")
gen = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gen)

# Synthea-shaped ids: the output must never carry them.
P1, P2, P3 = ("0f1e2d3c-4b5a-4968-8776-655443322110", "1a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d",
              "9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a")
E1, E2 = "aa11bb22-cc33-4d44-8e55-ff6600112233", "bb22cc33-dd44-4e55-8f66-001122334455"

COLUMNS = {
    "patients": ["Id", "BIRTHDATE", "DEATHDATE", "FIRST", "LAST", "GENDER"],
    "conditions": ["START", "STOP", "PATIENT", "ENCOUNTER", "CODE", "DESCRIPTION"],
    "medications": ["START", "STOP", "PATIENT", "ENCOUNTER", "CODE", "DESCRIPTION", "REASONDESCRIPTION"],
    "observations": ["DATE", "PATIENT", "ENCOUNTER", "CATEGORY", "CODE", "DESCRIPTION", "VALUE", "UNITS"],
    "encounters": ["Id", "START", "STOP", "PATIENT", "ENCOUNTERCLASS", "DESCRIPTION", "REASONDESCRIPTION"],
    "allergies": ["START", "STOP", "PATIENT", "ENCOUNTER", "DESCRIPTION", "CATEGORY", "DESCRIPTION1", "SEVERITY1"],
    "immunizations": ["DATE", "PATIENT", "ENCOUNTER", "CODE", "DESCRIPTION"],
}


def sample():
    rows = {name: [] for name in COLUMNS}
    rows["patients"] += [
        dict(Id=P1, BIRTHDATE="1960-03-04", DEATHDATE="", FIRST="Testa123", LAST="Person456", GENDER="F"),
        dict(Id=P2, BIRTHDATE="1970-07-08", DEATHDATE="", FIRST="Probe77", LAST="Sample88", GENDER="M"),
        dict(Id=P3, BIRTHDATE="2016-01-01", DEATHDATE="", FIRST="Kid1", LAST="Young2", GENDER="M"),
    ]
    for pid in (P1, P2, P3):
        rows["conditions"] += [
            dict(START="2010-01-01", STOP="", PATIENT=pid, ENCOUNTER=E1, CODE="44054006",
                 DESCRIPTION="Diabetes mellitus type 2 (disorder)"),
            dict(START="2011-01-01", STOP="", PATIENT=pid, ENCOUNTER=E1, CODE="59621000",
                 DESCRIPTION="Essential hypertension (disorder)"),
            dict(START="2026-01-01", STOP="", PATIENT=pid, ENCOUNTER=E1, CODE="66383009",
                 DESCRIPTION="Gingivitis (disorder)"),
            dict(START="2005-01-01", STOP="", PATIENT=pid, ENCOUNTER=E1, CODE="271737000", DESCRIPTION="Anemia (disorder)"),
        ]
        rows["medications"] += [
            dict(START="2015-01-01T08:00:00Z", STOP="", PATIENT=pid, ENCOUNTER=E1, CODE="312961",
                 DESCRIPTION="Simvastatin 20 MG Oral Tablet", REASONDESCRIPTION=""),
            dict(START="2020-01-01T08:00:00Z", STOP="", PATIENT=pid, ENCOUNTER=E1, CODE="617312",
                 DESCRIPTION="atorvastatin 10 MG Oral Tablet", REASONDESCRIPTION=""),
            dict(START="2018-01-01T08:00:00Z", STOP="", PATIENT=pid, ENCOUNTER=E1, CODE="309362",
                 DESCRIPTION="Clopidogrel 75 MG Oral Tablet", REASONDESCRIPTION=""),
            dict(START="2015-01-01T08:00:00Z", STOP="2015-02-01T08:00:00Z", PATIENT=pid, ENCOUNTER=E1, CODE="860975",
                 DESCRIPTION="24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet", REASONDESCRIPTION=""),
            dict(START="2015-02-01T08:00:00Z", STOP="", PATIENT=pid, ENCOUNTER=E1, CODE="860975",
                 DESCRIPTION="24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet", REASONDESCRIPTION=""),
            dict(START="2026-09-01T08:00:00Z", STOP="", PATIENT=pid, ENCOUNTER=E1, CODE="99999",
                 DESCRIPTION="Some Unlisted Product 5 MG Oral Tablet", REASONDESCRIPTION=""),
        ]
        rows["observations"] += [
            dict(DATE="2026-09-01T08:00:00Z", PATIENT=pid, ENCOUNTER=E1, CATEGORY="laboratory", CODE="4548-4",
                 DESCRIPTION="Hemoglobin A1c", VALUE="3.1", UNITS="%"),
            dict(DATE="2026-10-01T08:00:00Z", PATIENT=pid, ENCOUNTER=E1, CATEGORY="laboratory", CODE="4548-4",
                 DESCRIPTION="Hemoglobin A1c", VALUE="4.6", UNITS="%"),
            dict(DATE="2026-09-01T08:00:00Z", PATIENT=pid, ENCOUNTER=E1, CATEGORY="laboratory", CODE="38483-4",
                 DESCRIPTION="Creatinine in Blood", VALUE="1.8", UNITS="mg/dL"),
            dict(DATE="2026-09-01T08:00:00Z", PATIENT=pid, ENCOUNTER=E1, CATEGORY="laboratory", CODE="2160-0",
                 DESCRIPTION="Creatinine in Serum", VALUE="1.1", UNITS="mg/dL"),
            dict(DATE="2026-09-01T08:00:00Z", PATIENT=pid, ENCOUNTER=E1, CATEGORY="laboratory", CODE="33914-3",
                 DESCRIPTION="eGFR", VALUE="113.9", UNITS="mL/min"),
            dict(DATE="2026-09-01T08:00:00Z", PATIENT=pid, ENCOUNTER=E1, CATEGORY="laboratory", CODE="718-7",
                 DESCRIPTION="Hemoglobin", VALUE="16.8", UNITS="g/dL"),
            dict(DATE="2026-09-01T08:00:00Z", PATIENT=pid, ENCOUNTER=E1, CATEGORY="vital-signs", CODE="8480-6",
                 DESCRIPTION="Systolic Blood Pressure", VALUE="128.0", UNITS="mm[Hg]"),
            dict(DATE="2026-09-01T08:00:00Z", PATIENT=pid, ENCOUNTER=E1, CATEGORY="vital-signs", CODE="8462-4",
                 DESCRIPTION="Diastolic Blood Pressure", VALUE="81.0", UNITS="mm[Hg]"),
        ]
        rows["encounters"] += [
            dict(Id=E1, START="2026-09-01T08:00:00Z", STOP="2026-09-01T08:30:00Z", PATIENT=pid,
                 ENCOUNTERCLASS="wellness", DESCRIPTION="Encounter for check up (procedure)", REASONDESCRIPTION=""),
            dict(Id=E2, START="2026-10-01T08:00:00Z", STOP="2026-10-01T08:30:00Z", PATIENT=pid,
                 ENCOUNTERCLASS="ambulatory", DESCRIPTION="Dental care (procedure)", REASONDESCRIPTION="Gingivitis (disorder)"),
        ]
        rows["immunizations"].append(dict(DATE="2025-10-01T08:00:00Z", PATIENT=pid, ENCOUNTER=E1, CODE="140",
                                          DESCRIPTION="Influenza  split virus  trivalent  PF"))
    rows["allergies"].append(dict(START="2001-01-01", STOP="", PATIENT=P2, ENCOUNTER=E1, DESCRIPTION="Penicillin V",
                                  CATEGORY="medication", DESCRIPTION1="Hives", SEVERITY1="MODERATE"))
    return rows


def write_zip(folder):
    path = Path(folder) / "sample.zip"
    with zipfile.ZipFile(path, "w") as archive:
        for name, columns in COLUMNS.items():
            buffer = io.StringIO()
            writer = csv.DictWriter(buffer, fieldnames=columns)
            writer.writeheader()
            writer.writerows(sample()[name])
            archive.writestr(f"{name}.csv", buffer.getvalue())
    return path


class GeneratorTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.folder = tempfile.TemporaryDirectory()
        cls.zip = write_zip(cls.folder.name)
        cls.sql, cls.totals, cls.rules = gen.build(gen.read_sample(cls.zip), "test")

    @classmethod
    def tearDownClass(cls):
        cls.folder.cleanup()

    def test_shouldWriteTheSameFile_forTheSameSample(self):
        again, _, _ = gen.build(gen.read_sample(self.zip), "test")
        self.assertEqual(self.sql, again)

    def test_shouldTakeOnlyAdults_withRichHistories(self):
        self.assertIn("FAKE-Testa FAKE-Person", self.sql)
        self.assertIn("FAKE-Probe FAKE-Sample", self.sql)
        self.assertNotIn("Young", self.sql)

    def test_shouldCarryNoSyntheaIdOrLongHex_inTheOutput(self):
        for synthea_id in (P1, P2, P3, E1, E2):
            self.assertNotIn(synthea_id, self.sql)
        self.assertIsNone(re.search(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-", self.sql))
        self.assertIsNone(re.search(r"[0-9a-fA-F]{20,}", self.sql))
        self.assertIsNone(re.search(r"[A-Za-z0-9+/]{40,}={0,2}", self.sql))
        self.assertIn("UUID()", self.sql)  # note uuids are made at load time

    def test_shouldFailTheOntarioCheckDigit_forEveryHealthNumber(self):
        for seed in range(gen.FIRST_NO, gen.LAST_NO + 1):
            number = gen.ohip_failing(seed)
            self.assertRegex(number, r"^\d{10}$")
            self.assertFalse(gen._luhn_ok(number), number)
        for number in re.findall(r"'(\d{10})','AA'", self.sql):
            self.assertFalse(gen._luhn_ok(number), number)

    def test_shouldStayInsideTheOwnedRange_forEveryPatient(self):
        numbers = [int(n) for n in re.findall(r"^\((\d+),'M[RS]'", self.sql, re.M)]
        self.assertEqual(numbers, [gen.FIRST_NO, gen.FIRST_NO + 1])
        deletes = [line for line in self.sql.splitlines() if line.startswith("DELETE")]
        self.assertGreaterEqual(len(deletes), 12)
        self.assertIn(f"SET @carlos_synthea_first = {gen.FIRST_NO}, @carlos_synthea_last = {gen.LAST_NO};", self.sql)
        self.assertLess(gen.FIRST_NO + gen.COUNT - 1, gen.LAST_NO)
        self.assertGreater(gen.FIRST_NO, 3000)  # development.sql uses 1-3000

    def test_shouldApplyTheCleanupRules_toKnownContradictions(self):
        # R1: 3.1% is impossible; 4.6% is far too low for this patient with diabetes. Both are replaced.
        self.assertIn("R1: 2 HbA1c value(s) of 3.1, 4.6% -> 6.8-7.0%", self.sql)
        self.assertIn("('A1C',90001,'7','7.0'", self.sql)
        self.assertIn("('A1C',90001,'7','6.8'", self.sql)
        self.assertNotIn("('A1C',90001,'7','3.1'", self.sql)
        self.assertNotIn("('A1C',90001,'7','4.6'", self.sql)
        self.assertRegex(self.sql, r"R2: simvastatin 20 mg ends 2019-12-31: atorvastatin 10 mg started 2020-01-01")
        self.assertIn("R3: clopidogrel 75 mg from 2018-01-01 ends 2019-01-01", self.sql)
        self.assertIn("R5: anemia moved to past history", self.sql)
        self.assertIn("'Anemia (resolved) (2005-2026)'", self.sql)
        self.assertIn("R8: metformin ER 500 mg: 2 renewals merged", self.sql)
        self.assertIn("S: medicine not in the code maps, left out: Some Unlisted Product", self.sql)
        # R6: the serum creatinine (1.1 mg/dL = 97 umol/L) and an eGFR computed from it, never the sample's.
        self.assertIn("('SCR',90001,'7','97'", self.sql)
        self.assertIn("('EGFR',90001,'7','56'", self.sql)  # a woman of 66: CKD-EPI 2021
        self.assertNotIn("'113.9'", self.sql)

    def test_shouldLeaveOutDentalVisits_andSkippedConditions(self):
        self.assertNotIn("Gingivitis", self.sql)
        self.assertNotIn("Dental care", self.sql)

    def test_shouldWriteNoKnownDrugAllergies_whenTheSampleListsNone(self):
        self.assertIn("(90001,'2026-09-01','No Known Drug Allergies'", self.sql)
        self.assertIn("'Penicillin V',0,'Hives'", self.sql)

    def test_shouldKeepOpenCoursesCurrent_wheneverTheFileIsLoaded(self):
        # Drug.isCurrent() compares end_date with today: a fixed end date would expire every current medicine.
        self.assertIn("DATE_ADD(CURDATE(), INTERVAL 3 YEAR)", self.sql)
        self.assertNotIn("'2027-01-30'", self.sql)
        self.assertRegex(self.sql, r"'atorvastatin[^']*'")

    def test_shouldUseTheAllergyFormCodes_forSeverityAndOnset(self):
        # The allergy page reads 1 mild, 2 moderate, 3 severe, 4 unknown; "0" shows as a black cell.
        nkda = re.search(r"\(90001,'2026-09-01','No Known Drug Allergies',0,NULL,0,'2026-09-01','(\d)','(\d)',", self.sql)
        self.assertIsNotNone(nkda)
        self.assertEqual(nkda.groups(), ("4", "4"))
        self.assertEqual(re.findall(r"'Penicillin V',0,'Hives',0,'[0-9-]+','(\d)','(\d)',", self.sql), [("2", "4")])

    def test_shouldQuoteSqlStrings_safely(self):
        self.assertEqual(gen.q("O'Brien\\x\nnext"), "'O''Brien\\\\x\\nnext'")
        self.assertEqual(gen.q(None), "NULL")
        self.assertEqual(gen.q(True), "1")

    def test_shouldRefuseAnotherSample_unlessAllowed(self):
        out = Path(self.folder.name) / "out.sql"
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            gen.main(["--zip", str(self.zip), "--out", str(out)])
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(gen.main(["--zip", str(self.zip), "--out", str(out), "--allow-other-sample"]), 0)
        self.assertIn("(NOT the pinned zip)", out.read_text(encoding="utf-8"))


if __name__ == "__main__":
    sys.exit(unittest.main())
