#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Independent native-chart oracle and schema-valid mutation check for owned CI artifacts."""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
from pathlib import Path, PurePosixPath
import subprocess
import tempfile
import zipfile
from defusedxml import ElementTree as SAFE_XML
from xml.etree import ElementTree as XML

NS = {"c": "cds", "d": "cds_dt"}
NOTE = "Native café 😀 </unotes> literal patient note"
BASE = "./c:PatientRecord/c:Demographics/"


def expected(patient: int) -> dict[str, str]:
    # These constants describe the independently inserted ordinary native chart.
    # No exporter mapper, fingerprint, receipt or retained XML supplies expected values.
    return {
        "c:Names/d:LegalName/d:FirstName/d:Part": "Native",
        "c:Names/d:LegalName/d:LastName/d:Part": "SYNTHETIC Native Export",
        "c:DateOfBirth": "2000-01-02",
        "c:Gender": "F",
        "c:UniqueVendorIdSequence": "C" + str(patient),
        "c:Address[@addressType='M']/d:Structured/d:Line1": "17 Native Fixture Road",
        "c:Address[@addressType='M']/d:Structured/d:City": "Ottawa",
        "c:Address[@addressType='M']/d:Structured/d:CountrySubdivisionCode": "CA-ON",
        "c:Address[@addressType='M']/d:Structured/d:PostalZipCode/d:PostalCode": "K1A0B1",
        "c:PhoneNumber[@phoneNumberType='R']/d:phoneNumber": "613-555-0198",
        "c:PrimaryPhysician/c:Name/d:FirstName": "Synthetic",
        "c:PrimaryPhysician/c:Name/d:LastName": "Native Export",
        "c:PersonStatusCode/c:PersonStatusAsEnum": "A",
        "c:NoteAboutPatient": NOTE,
    }


def history_expected() -> dict[str, str]:
    # Literal facts written by the independent fixture, without transfer mapper output.
    return {
        "c:PersonalHistory/c:ResidualInfo/d:DataElement[d:Name='CARLOS.History.PersonalNarrative.v1']/d:Content": "Native SocHistory narrative café 😀",
        "c:FamilyHistory/c:ProblemDiagnosisProcedureDescription": "Native FamHistory narrative café 😀",
        "c:FamilyHistory/c:Relationship": "Mother",
        "c:FamilyHistory/c:StartDate/d:YearMonth": "2020-02",
        "c:PastHealth/c:PastHealthProblemDescriptionOrProcedures": "Native MedHistory narrative café 😀",
        "c:ProblemList/c:ProblemDiagnosisDescription": "Native Concerns narrative café 😀",
        "c:RiskFactors/c:RiskFactor": "Native RiskFactors narrative café 😀",
        "c:AlertsAndSpecialNeeds/c:AlertDescription": "Native Reminders narrative café 😀",
        "c:ClinicalNotes/c:EventDateTime/d:FullDateTime": "2026-07-09T00:00:00-04:00",
    }


def history_equal(record) -> bool:
    if not all(len(record.findall(path, NS)) == 1 and record.find(path, NS).text == value
               for path, value in history_expected().items()):
        return False
    for category in ("PersonalHistory", "FamilyHistory", "PastHealth", "ProblemList", "RiskFactors", "AlertsAndSpecialNeeds"):
        path = "c:" + category + "/c:ResidualInfo/d:DataElement[d:Name='CARLOS.History.Archived.v1']/d:Content"
        if len(record.findall(path, NS)) != 1 or record.find(path, NS).text != ("true" if category == "FamilyHistory" else "false"):
            return False
    note = record.find("c:ClinicalNotes", NS)
    content = note.find("c:MyClinicalNotesContent", NS).text
    body, separator, metadata = content.partition("\n\n[CARLOS source note details]\n")
    if not separator or body != "[Original note]\nOriginal native clinical note café 😀\n\n[Revision 2]\nEdited native clinical note café 😀":
        return False
    details = json.loads(metadata)
    if details.get("version") != 1 or len(details.get("revisions", [])) != 2:
        return False
    participants = note.findall("c:ParticipatingProviders", NS)
    if len(participants) != 2:
        return False
    for participant in participants:
        if (participant.find("c:Name/d:FirstName", NS).text != "Synthetic"
                or participant.find("c:Name/d:LastName", NS).text != "Native Export"
                or participant.find("c:DateTimeNoteCreated/d:FullDateTime", NS).text != "2026-07-10T12:30:00-04:00"):
            return False
    return True


def clinically_equal(root, patient: int) -> bool:
    if root.tag != "{cds}OmdCds" or len(root.findall("./c:PatientRecord", NS)) != 1:
        return False
    record = root.find("./c:PatientRecord", NS)
    categories = ["Demographics", "PersonalHistory", "FamilyHistory", "PastHealth", "ProblemList",
                  "RiskFactors", "AlertsAndSpecialNeeds", "ClinicalNotes"]
    if [item.tag for item in record] != ["{cds}" + name for name in categories]:
        return False
    return history_equal(record) and all(len(root.findall(BASE + path, NS)) == 1 and root.find(BASE + path, NS).text == value
               for path, value in expected(patient).items())


def schema_valid(path: Path, schema: Path) -> bool:
    result = subprocess.run(["xmllint", "--nonet", "--noout", "--schema", str(schema), str(path)],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
    return result.returncode == 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--proof", type=Path, required=True)
    parser.add_argument("--schema", type=Path, required=True)
    args = parser.parse_args()
    if args.archive.is_symlink() or not args.archive.is_file() or args.archive.stat().st_size > (2 << 20):
        raise RuntimeError("Bounded owned archive required")
    fixture = json.loads(args.fixture.read_text())
    patient = fixture["patient"]
    if type(patient) is not int or patient < 1:
        raise RuntimeError("Expected patient identity required")
    with zipfile.ZipFile(args.archive) as archive:
        members = archive.infolist()
        names = [member.filename for member in members]
        if len(names) != 3 or len(set(name.casefold() for name in names)) != len(names):
            raise RuntimeError("Exactly one patient and two supporting members required")
        for member in members:
            name = PurePosixPath(member.filename)
            if name.is_absolute() or ".." in name.parts or "\\" in member.filename or member.is_dir() or member.file_size > (1 << 20):
                raise RuntimeError("Unsafe or oversized fixture member")
        if archive.testzip() is not None:
            raise RuntimeError("Archive CRC mismatch")
        xml_members = [name for name in names if name.endswith(".xml")]
        if len(xml_members) != 1:
            raise RuntimeError("Exactly one XML patient required")
        xml_bytes = archive.read(xml_members[0])
    root = SAFE_XML.fromstring(xml_bytes)
    if not clinically_equal(root, patient):
        raise RuntimeError("Independent native clinical expectations differ")
    with tempfile.TemporaryDirectory(prefix="cds-independent-oracle-", dir=args.proof.parent) as work:
        xml_path = Path(work) / "patient.xml"
        xml_path.write_bytes(xml_bytes)
        if not schema_valid(xml_path, args.schema):
            raise RuntimeError("Pinned OntarioMD schema validation failed")
        mutations = (
            ("c:Gender", "M"),
            ("c:DateOfBirth", "2000-01-03"),
            ("c:Names/d:LegalName/d:FirstName/d:Part", "Altered"),
            ("c:NoteAboutPatient", "Native café 😀 "),
            ("../c:FamilyHistory/c:Relationship", "Father"),
            ("../c:ClinicalNotes/c:MyClinicalNotesContent", "Edited native clinical note café 😀"),
            ("../c:FamilyHistory/c:ResidualInfo/d:DataElement[d:Name='CARLOS.History.Archived.v1']/d:Content", "false"),
        )
        detected = 0
        for index, (path, replacement) in enumerate(mutations):
            mutant = copy.deepcopy(root)
            target = (mutant.find("./c:PatientRecord/" + path[3:], NS) if path.startswith("../")
                      else mutant.find(BASE + path, NS))
            target.text = replacement
            mutant_path = Path(work) / ("mutant-" + str(index) + ".xml")
            XML.ElementTree(mutant).write(mutant_path, encoding="UTF-8", xml_declaration=True)
            if not schema_valid(mutant_path, args.schema):
                raise RuntimeError("Clinical mutant must remain schema valid")
            if clinically_equal(mutant, patient):
                raise RuntimeError("Independent oracle missed a schema-valid clinical mutation")
            detected += 1
    args.proof.write_text(json.dumps({
        "protocol": 1, "status": "PASSED", "independentExpectedFacts": len(expected(patient)) + len(history_expected()),
        "schemaValidClinicalMutantsDetected": detected, "zipMembers": len(names), "patientXmlMembers": 1,
        "pinnedSchemaValid": True, "plaintextZipSha256": hashlib.sha256(args.archive.read_bytes()).hexdigest(),
        "patientXmlSha256": hashlib.sha256(xml_bytes).hexdigest()
    }, sort_keys=True, indent=2))
    print("PASS independent native clinical oracle:", len(expected(patient)) + len(history_expected()), "facts and", detected, "schema-valid mutants")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
