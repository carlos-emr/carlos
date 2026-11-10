"""Independent failure/structure tests for the CDS specification crosswalk gate.

Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
"""

import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("cds_spec_crosswalk", ROOT / "scripts/cds-spec-crosswalk.py")
crosswalk = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(crosswalk)

MAIN = """<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="cds" xmlns:dt="cds_dt"
 targetNamespace="cds" elementFormDefault="qualified">
 <xs:import namespace="cds_dt" schemaLocation="types.xsd"/>
 <xs:element name="OmdCds"><xs:complexType><xs:sequence>
 <xs:element name="PatientRecord"><xs:complexType><xs:sequence>
 <xs:element name="Tiny" type="dt:payload" minOccurs="0" maxOccurs="unbounded"/>
 </xs:sequence></xs:complexType></xs:element>
 </xs:sequence></xs:complexType></xs:element></xs:schema>"""
TYPES = """<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:dt="cds_dt"
 targetNamespace="cds_dt" elementFormDefault="qualified">
 <xs:simpleType name="code"><xs:restriction base="xs:token"><xs:maxLength value="9"/>
 <xs:enumeration value="ABC"/></xs:restriction></xs:simpleType>
 <xs:complexType name="payload"><xs:choice>
 <xs:element name="Code" type="dt:code"/>
 <xs:sequence><xs:element name="Number" type="xs:positiveInteger"/>
 <xs:element name="Suffix" minOccurs="0"><xs:simpleType><xs:restriction base="xs:string">
 <xs:maxLength value="4"/></xs:restriction></xs:simpleType></xs:element></xs:sequence>
 </xs:choice><xs:attribute name="kind" type="xs:token" use="required"/>
 <xs:attribute name="label" type="xs:string" default="default-label"/>
 </xs:complexType>
 <xs:complexType name="unused"><xs:attribute name="notOnWire" type="xs:string"/></xs:complexType>
 </xs:schema>"""


class CrosswalkContractTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.main = Path(self.directory.name) / "main.xsd"
        self.types = self.main.with_name("types.xsd")
        self.main.write_text(MAIN, encoding="utf-8")
        self.types.write_text(TYPES, encoding="utf-8")

    def inventory(self):
        return crosswalk.SchemaSet(self.main).inventory()

    def review(self, inventory):
        ref = "scripts/ci/test_cds_spec_crosswalk.py"
        return {"scope": "synthetic test only",
                "schemaPins": {row["file"]: row["sha256"] for row in inventory["schemas"]},
                "reviewedImplementationSha256": {ref: crosswalk.digest((ROOT / ref).read_bytes())},
                "choices": {choice["id"]: {arm["id"]: {
                    "fieldGroups": ["reviewed-source-only"], "evidence": [ref],
                    "evidenceScope": "synthetic imported type structure only"} for arm in choice["arms"]}
                    for choice in inventory["choices"]},
                "groups": {"reviewed-source-only": {
            "paths": [field["path"] for field in inventory["fields"]], "disposition": "source-only",
            "destination": "protected source fields, no native claim", "normalization": "none",
            "original": "retained", "reader": ["scripts/ci/test_cds_spec_crosswalk.py"],
            "tests": ["scripts/ci/test_cds_spec_crosswalk.py#test_new_wire_leaf_is_not_automatically_classified"],
            "errorPolicy": "refuse unclassified", "transaction": "metadata test only"}}}

    def test_imported_named_and_anonymous_types_attributes_and_sequences_are_expanded(self):
        inventory = self.inventory()
        fields = {row["path"]: row for row in inventory["fields"]}
        prefix = "/cds:OmdCds/cds:PatientRecord/cds:Tiny/"
        self.assertEqual(set(fields), {prefix + value for value in
                                     ("dt:Code", "dt:Number", "dt:Suffix", "@kind", "@label")})
        self.assertEqual(fields[prefix + "dt:Code"]["variants"][0]["scalar"]["base"], "xs:token")
        self.assertEqual(fields[prefix + "dt:Suffix"]["variants"][0]["scalar"]["whiteSpace"], "preserve")
        self.assertEqual(fields[prefix + "@label"]["variants"][0]["default"], "default-label")
        self.assertEqual(fields[prefix + "@kind"]["variants"][0]["occurrences"][-1]["min"], "1")
        parent = next(row for row in fields[prefix + "@kind"]["variants"][0]["occurrences"]
                      if row["path"] == prefix[:-1])
        self.assertEqual(parent, {"path": prefix[:-1], "min": "0", "max": "unbounded"})
        self.assertEqual(inventory["counts"]["choices"], 1)
        self.assertEqual(inventory["counts"]["choiceArms"], 2)
        self.assertEqual(len(fields[prefix + "dt:Number"]["variants"][0]["choiceArms"]), 1)
        self.assertEqual(fields[prefix + "@kind"]["variants"][0]["choiceArms"], [])

    def test_unused_declarations_are_reported_without_inventing_valid_wire_attributes(self):
        inventory = self.inventory()
        unused = next(row for row in inventory["globalTypes"] if row["name"] == "dt:unused")
        self.assertFalse(unused["reachableFromEnvelope"])
        self.assertNotIn("notOnWire", json.dumps(inventory["fields"]))

    def test_new_wire_leaf_is_not_automatically_classified(self):
        reviewed = self.review(self.inventory())
        self.types.write_text(TYPES.replace('<xs:element name="Number"',
                              '<xs:element name="NewFact" type="xs:string"/><xs:element name="Number"'))
        updated = self.inventory()
        reviewed["schemaPins"] = {row["file"]: row["sha256"] for row in updated["schemas"]}
        with self.assertRaisesRegex(ValueError, "Unclassified/stale paths.*NewFact"):
            crosswalk.apply_review(updated, reviewed)

    def test_removed_wire_leaf_cannot_keep_a_stale_review(self):
        reviewed = self.review(self.inventory())
        self.types.write_text(TYPES.replace('<xs:element name="Number" type="xs:positiveInteger"/>', ""))
        updated = self.inventory()
        reviewed["schemaPins"] = {row["file"]: row["sha256"] for row in updated["schemas"]}
        with self.assertRaisesRegex(ValueError, "Unclassified/stale paths.*Number"):
            crosswalk.apply_review(updated, reviewed)

    def test_scalar_facet_change_requires_explicit_review_even_without_a_new_path(self):
        reviewed = self.review(self.inventory())
        self.types.write_text(TYPES.replace('maxLength value="9"', 'maxLength value="10"'))
        with self.assertRaisesRegex(ValueError, "Reviewed schema pins changed"):
            crosswalk.apply_review(self.inventory(), reviewed)

    def test_implementation_drift_cannot_reuse_a_prior_mapping_review(self):
        inventory = self.inventory()
        reviewed = self.review(inventory)
        reviewed["reviewedImplementationSha256"]["scripts/ci/test_cds_spec_crosswalk.py"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "Reviewed implementation/reader bytes changed"):
            crosswalk.apply_review(inventory, reviewed)

    def test_duplicate_classification_is_not_last_writer_wins(self):
        inventory = self.inventory()
        reviewed = self.review(inventory)
        reviewed["groups"]["conflict"] = copy.deepcopy(reviewed["groups"]["reviewed-source-only"])
        with self.assertRaisesRegex(ValueError, "Multiply classified"):
            crosswalk.apply_review(inventory, reviewed)

    def test_duplicate_json_review_keys_are_not_last_writer_wins(self):
        review = self.main.with_name("review.json")
        review.write_text('{"groups":{"reviewed":1,"reviewed":2}}')
        with self.assertRaisesRegex(ValueError, "Duplicate crosswalk review key"):
            crosswalk.read_review(review)

    def test_choice_and_arm_review_cannot_be_missing_even_when_fields_are_classified(self):
        for missing_choice in (True, False):
            with self.subTest(missing_choice=missing_choice):
                inventory = self.inventory()
                reviewed = self.review(inventory)
                choice = inventory["choices"][0]
                if missing_choice:
                    reviewed["choices"].pop(choice["id"])
                else:
                    reviewed["choices"][choice["id"]].pop(choice["arms"][0]["id"])
                with self.assertRaisesRegex(ValueError, "Unclassified/stale choice"):
                    crosswalk.apply_review(inventory, reviewed)

    def test_choice_cannot_claim_a_native_group_or_nonexistent_evidence(self):
        inventory = self.inventory()
        reviewed = self.review(inventory)
        choice = inventory["choices"][0]
        reviewed["choices"][choice["id"]][choice["arms"][0]["id"]]["fieldGroups"] = ["invented-native"]
        with self.assertRaisesRegex(ValueError, "Incomplete reviewed choice"):
            crosswalk.apply_review(inventory, reviewed)
        reviewed = self.review(inventory)
        reviewed["choices"][choice["id"]][choice["arms"][0]["id"]]["evidence"] = ["scripts/ci/absent-choice-test.py"]
        with self.assertRaisesRegex(ValueError, "Missing choice evidence reference"):
            crosswalk.apply_review(inventory, reviewed)

    def test_native_and_source_disposition_cannot_be_unspecified_or_arbitrary(self):
        for disposition in ("", "complete", "native"):
            with self.subTest(disposition=disposition):
                inventory = self.inventory()
                reviewed = self.review(inventory)
                reviewed["groups"]["reviewed-source-only"]["disposition"] = disposition
                with self.assertRaises(ValueError):
                    crosswalk.apply_review(inventory, reviewed)

    def test_missing_reader_test_file_or_symbol_fails(self):
        for ref in ("scripts/ci/no-such-reader.py", "scripts/ci/test_cds_spec_crosswalk.py#" + "missingSymbol" + "GuaranteedAbsent"):
            with self.subTest(reference=ref):
                inventory = self.inventory()
                reviewed = self.review(inventory)
                reviewed["groups"]["reviewed-source-only"]["reader"] = [ref]
                with self.assertRaisesRegex(ValueError, "Missing crosswalk"):
                    crosswalk.apply_review(inventory, reviewed)

    def test_reference_cannot_escape_the_repository(self):
        inventory = self.inventory()
        reviewed = self.review(inventory)
        reviewed["groups"]["reviewed-source-only"]["reader"] = ["../work/private-evidence.json"]
        with self.assertRaisesRegex(ValueError, "Missing crosswalk reference"):
            crosswalk.apply_review(inventory, reviewed)

    def test_unsupported_wildcard_is_a_visible_failure(self):
        self.types.write_text(TYPES.replace('<xs:element name="Number" type="xs:positiveInteger"/>',
                                           '<xs:any minOccurs="0"/>'))
        with self.assertRaisesRegex(ValueError, "Unsupported XSD construct"):
            self.inventory()

    def test_mixed_content_attribute_is_not_silently_treated_as_leaf_only(self):
        self.types.write_text(TYPES.replace('complexType name="payload"', 'complexType name="payload" mixed="true"'))
        with self.assertRaisesRegex(ValueError, "Unsupported XSD attribute"):
            self.inventory()

    def test_scoped_prefix_rebinding_requires_an_explicit_resolver_review(self):
        self.types.write_text(TYPES.replace('<xs:element name="Code"', '<xs:element xmlns:dt="other" name="Code"'))
        with self.assertRaisesRegex(ValueError, "Scoped namespace rebinding"):
            self.inventory()

    def test_unreviewed_scalar_derivation_does_not_disappear(self):
        self.types.write_text(TYPES.replace('<xs:restriction base="xs:token"><xs:maxLength value="9"/>\n <xs:enumeration value="ABC"/></xs:restriction>',
                                           '<xs:union memberTypes="xs:token xs:integer"/>'))
        with self.assertRaisesRegex(ValueError, "Unsupported XSD construct"):
            self.inventory()

    def test_external_import_and_wrong_namespace_are_rejected(self):
        for replacement in ('schemaLocation="../external.xsd"', 'schemaLocation="https://example.invalid/types.xsd"',
                            'namespace="unexpected" schemaLocation="types.xsd"'):
            with self.subTest(replacement=replacement):
                self.main.write_text(MAIN.replace('namespace="cds_dt" schemaLocation="types.xsd"', replacement))
                with self.assertRaises(ValueError):
                    self.inventory()

    def test_dtd_and_entity_declarations_are_forbidden(self):
        self.main.write_text('<!DOCTYPE schema [<!ENTITY x "secret">]>' + MAIN)
        with self.assertRaises(Exception) as failure:
            self.inventory()
        self.assertIn("Forbidden", type(failure.exception).__name__)

    def test_recursive_types_fail_instead_of_truncating_depth(self):
        self.types.write_text(TYPES.replace('<xs:element name="Code" type="dt:code"/>',
                                           '<xs:element name="Recursive" type="dt:payload"/>'))
        with self.assertRaisesRegex(ValueError, "Recursive complex type"):
            self.inventory()

    def test_pinned_schema_bytes_and_import_set_are_checked(self):
        pins = {"main.xsd": crosswalk.digest(self.main.read_bytes()),
                "types.xsd": crosswalk.digest(self.types.read_bytes())}
        self.types.write_text(TYPES + "\n")
        with self.assertRaisesRegex(ValueError, "Pinned schema mismatch"):
            crosswalk.SchemaSet(self.main, pins)

    def test_shared_phone_sequence_leaf_keeps_both_branch_conditions(self):
        inventory = crosswalk.SchemaSet(crosswalk.SCHEMA, crosswalk.PINS).inventory()
        extension = next(row for row in inventory["fields"] if row["path"].endswith("cds:Demographics/cds:PhoneNumber/dt:extension"))
        self.assertEqual(len(extension["variants"]), 2)
        self.assertNotEqual(extension["variants"][0]["choiceArms"], extension["variants"][1]["choiceArms"])

    def test_missing_choice_fixture_is_a_gate_failure(self):
        schemas = crosswalk.SchemaSet(crosswalk.SCHEMA, crosswalk.PINS)
        inventory = schemas.inventory()
        fixtures = crosswalk.generate_fixtures(schemas, inventory)
        fixtures.pop("choices-04.xml")
        with self.assertRaisesRegex(ValueError, "Synthetic field coverage differs|Unexercised choice arm"):
            crosswalk.fixture_coverage(inventory, fixtures)

    def test_real_pinned_structure_counts_are_independent_review_sentinels(self):
        inventory = crosswalk.SchemaSet(crosswalk.SCHEMA, crosswalk.PINS).inventory()
        self.assertEqual(inventory["counts"], {"fields": 407, "choices": 45, "choiceArms": 130, "compositors": 150})
        self.assertEqual(sum(field["kind"] == "attribute" for field in inventory["fields"]), 8)
        self.assertEqual(len({field["category"] for field in inventory["fields"]}), 16)

    def test_checked_in_inventory_and_fixtures_match_the_exact_reviewed_schema(self):
        self.assertEqual(crosswalk.main(["--check"]), 0)


if __name__ == "__main__":
    unittest.main()
