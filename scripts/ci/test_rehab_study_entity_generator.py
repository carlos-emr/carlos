# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""The committed rehabilitation-study entities are exactly the generator's output (issue #5693).

RehabStudyEntitySchemaContractUnitTest checks the entities against the Flyway baseline in the
Java build; this test checks that nobody edited a generated entity, or the generator, without
regenerating, and that the generator refuses tables it cannot map faithfully.
"""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "generate_rehab_study_entities", ROOT / "scripts/forms/generate_rehab_study_entities.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

HEADER = [
    ("ID", "int(10)", "NOT NULL AUTO_INCREMENT"),
    ("demographic_no", "int(10)", "NOT NULL"),
    ("provider_no", "int(10)", "DEFAULT NULL"),
    ("formCreated", "date", "DEFAULT NULL"),
    ("formEdited", "timestamp", "NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()"),
    ("studyID", "varchar(20)", "NOT NULL DEFAULT 'N/A'"),
]


class RehabStudyEntityGeneratorTest(unittest.TestCase):
    def setUp(self):
        self.tables = MODULE.parse_tables(MODULE.BASELINE.read_text(encoding="utf-8"))

    def test_every_committed_entity_is_the_generators_output(self):
        self.assertEqual(sorted(self.tables), sorted(MODULE.TABLES))
        generated = MODULE.sources(self.tables)
        self.assertEqual(set(generated), {f"{entity}.java" for entity, _ in MODULE.TABLES.values()}
                         | {f"{superclass}.java" for superclass in MODULE.SHARED_ANSWERS})
        for name, source in generated.items():
            path = MODULE.TARGET / name
            with self.subTest(file=name):
                self.assertTrue(path.is_file(), f"{path} is missing")
                self.assertEqual(path.read_text(encoding="utf-8"), source,
                                 f"{path.name} differs from the generator; run scripts/forms/generate_rehab_study_entities.py")

    def test_no_entity_exists_that_the_generator_does_not_produce(self):
        produced = set(MODULE.sources(self.tables))
        hand_written = {"RehabStudyAssessment.java", "RehabStudyColumn.java", "RehabStudyEnrolment.java", "package-info.java"}
        self.assertEqual({path.name for path in MODULE.TARGET.glob("*.java")}, produced | hand_written)

    def test_maps_the_sf36_answers_once_for_both_sf36_tables(self):
        generated = MODULE.sources(self.tables)
        self.assertEqual(MODULE.SHARED_ANSWERS, {"SF36Answers": (("formSF36", "formSF36Caregiver"), "SF-36 Health Survey")})
        shared = generated["SF36Answers.java"]
        self.assertIn("@MappedSuperclass\npublic abstract class SF36Answers extends RehabStudyAssessment", shared)
        self.assertEqual(shared.count("@Column("), len(MODULE.body_columns("formSF36", self.tables["formSF36"])))
        for table in ("formSF36", "formSF36Caregiver"):
            entity = generated[f"{MODULE.TABLES[table][0]}.java"]
            with self.subTest(table=table):
                self.assertIn(f'@Table(name = "{table}")', entity)
                self.assertIn("extends SF36Answers", entity)
                self.assertIn("RehabStudyColumn.narrow(answerColumns())", entity)
                self.assertNotIn("@Column(", entity)
        for table in set(MODULE.TABLES) - {"formSF36", "formSF36Caregiver"}:
            self.assertIn("extends RehabStudyAssessment", generated[f"{MODULE.TABLES[table][0]}.java"])

    def test_refuses_to_share_answers_between_tables_that_differ(self):
        tables = dict(self.tables)
        tables["formSF36Caregiver"] = [column for column in tables["formSF36Caregiver"] if column[0] != "Q1Ex"]
        with self.assertRaises(SystemExit):
            MODULE.sources(tables)

    def test_maps_each_physical_kind_to_its_java_type(self):
        columns = HEADER + [("tick", "tinyint(1)", "DEFAULT NULL"), ("day", "date", "DEFAULT NULL"),
                            ("note", "varchar(255)", "DEFAULT NULL"), ("code", "char(2)", "DEFAULT NULL"),
                            ("count", "int(3)", "DEFAULT NULL"), ("score", "double", "DEFAULT NULL")]
        body = MODULE.body_columns("formSF36", columns)
        self.assertEqual([(name, kind, java) for name, _, kind, java, _ in body],
                         [("tick", "Checkbox", "Byte"), ("day", "Day", "Date"), ("note", "Text", "String"),
                          ("code", "Text", "String"), ("count", "Text", "String"), ("score", "Text", "String")])

    def test_refuses_a_column_it_cannot_map_faithfully(self):
        for column in [("blob1", "blob", "DEFAULT NULL"), ("wide", "tinyint(4)", "DEFAULT NULL"),
                       ("required", "varchar(5)", "NOT NULL"), ("_odd", "varchar(5)", "DEFAULT NULL")]:
            with self.subTest(column=column):
                with self.assertRaises(SystemExit):
                    MODULE.body_columns("formSF36", HEADER + [column])

    def test_refuses_a_changed_or_missing_header(self):
        changed = [("studyID", "varchar(30)", "NOT NULL DEFAULT 'N/A'") if c[0] == "studyID" else c for c in HEADER]
        missing = [c for c in HEADER if c[0] != "provider_no"]
        for columns in (changed, missing):
            with self.assertRaises(SystemExit):
                MODULE.body_columns("formSF36", columns)


if __name__ == "__main__":
    unittest.main()
