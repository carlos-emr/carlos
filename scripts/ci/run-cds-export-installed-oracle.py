#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Independent native-chart oracle and schema-valid mutation check for owned CI artifacts."""
from __future__ import annotations
import argparse
import base64
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


def care_equal(record, patient: int) -> bool:
    expected_values = {
        "Weight": {"Weight": "70", "WeightUnit": "kg", "Date": "2026-07-09"},
        "Height": {"Height": "175", "HeightUnit": "cm", "Date": "2026-07-09"},
        "BloodPressure": {"SystolicBP": "120", "DiastolicBP": "80", "BPUnit": "mmHg", "Date": "2026-07-09"},
    }
    care = record.findall("c:CareElements", NS)
    if len(care) != 3:
        return False
    for name, expected_value in expected_values.items():
        values = record.findall("c:CareElements/c:" + name, NS)
        if len(values) != 1 or {child.tag.split("}")[-1]: child.text for child in values[0]} != expected_value:
            return False
    supporting = [entry for entry in record.findall("c:NewCategory", NS)
                  if entry.find("c:CategoryName", NS).text == "CARLOS.NativeObservation.v1"]
    if len(supporting) != 5:
        return False
    active, deleted = {}, []
    for entry in supporting:
        if entry.find("c:CategoryName", NS).text != "CARLOS.NativeObservation.v1":
            return False
        context = json.loads(entry.find("c:ResidualInfo/d:DataElement[d:Name='CARLOS.Observation.v1']/d:Content", NS).text)
        if context.get("version") != 1:
            return False
        main = context["rows"][0]
        fields = main["fields"]
        if fields.get("demographicNo") != str(patient):
            return False
        if context.get("deleted"):
            if main["table"] != "measurementsDeleted":
                return False
            deleted.append(fields)
        else:
            if main["table"] != "measurements" or fields["type"] in active:
                return False
            active[fields["type"]] = fields
    if set(active) != {"WT", "HT", "BP", "TEMP"} or len(deleted) != 1:
        return False
    for code, value, unit in (("WT", "70.000", "kg"), ("HT", "175", "cm"), ("BP", "120/80", "mmHg"), ("TEMP", "37.1", "C")):
        if any(active[code].get(k) != v for k, v in {
            "dataField": value, "measuringInstruction": unit, "dateObserved": "2026-07-09 14:35:00",
            "dateEntered": "2026-07-10 12:30:00", "comments": "Native measurement comment café 😀"}.items()):
            return False
    return all(deleted[0].get(k) == v for k, v in {"dataField": "80", "type": "WT", "dateDeleted": "2026-07-10 14:00:00",
        "comments": "Deleted native measurement comment café 😀"}.items())


def laboratory_expected() -> list[dict[str, str]]:
    return [
        {"c:LaboratoryName": "Native Lab", "c:TestNameReportedByLab": "Glucose",
         "c:LabTestCode": "2345-7", "c:AccessionNumber": "ACC-1", "c:Result/c:Value": "5.4",
         "c:Result/c:UnitOfMeasure": "mmol/L", "c:ReferenceRange/c:ReferenceRangeText": "3.5-7.8",
         "c:CollectionDateTime/d:FullDateTime": "2026-07-09T14:35:00-04:00",
         "c:LabRequisitionDateTime/d:FullDateTime": "2026-07-08T10:00:00-04:00",
         "c:ResultNormalAbnormalFlag/d:resultNormalAbnormalFlagAsEnum": "N",
         "c:NotesFromLab": "Exact café | details 😀", "c:TestResultStatus": "F"},
        {"c:LaboratoryName": "Native Lab", "c:TestNameReportedByLab": "Narrative",
         "c:LabTestCode": "COMMENT", "c:AccessionNumber": "ACC-1", "c:Result/c:Value": "A&B",
         "c:CollectionDateTime/d:FullDateTime": "2026-07-09T14:35:00-04:00",
         "c:ResultNormalAbnormalFlag/d:resultNormalAbnormalFlagAsEnum": "A", "c:TestResultStatus": "F"},
        *[{"c:LaboratoryName": "Native Lab", "c:TestNameReportedByLab": "Repeated native result",
           "c:LabTestCode": "NATIVE", "c:AccessionNumber": "ACC-2", "c:Result/c:Value": value,
           "c:Result/c:UnitOfMeasure": "mg/L", "c:CollectionDateTime/d:FullDate": "2026-07-09",
           "c:ResultNormalAbnormalFlag/d:resultNormalAbnormalFlagAsPlainText": "", "c:TestResultStatus": "P"}
          for value in ("10", "11")],
    ]


def laboratory_equal(record) -> bool:
    laboratories = record.findall("c:LaboratoryResults", NS)
    if len(laboratories) != 4:
        return False
    for result, expected_value in zip(laboratories, laboratory_expected(), strict=True):
        if any(len(result.findall(path, NS)) != 1 or (result.find(path, NS).text or "") != value
               for path, value in expected_value.items()):
            return False
    supporting = [entry for entry in record.findall("c:NewCategory", NS)
                  if entry.find("c:CategoryName", NS).text == "CARLOS.NativeLaboratory.v1"]
    if len(supporting) != 1:
        return False
    context = json.loads(supporting[0].find("c:ResidualInfo/d:DataElement[d:Name='CARLOS.Laboratory.v1']/d:Content", NS).text)
    if context.get("version") != 1 or len(context.get("rows", [])) != 3:
        return False
    rows = {row["table"]: row["fields"] for row in context["rows"]}
    expected_message = (
        "MSH|^~\\&|LABAPP|Native Lab|CARLOS|Clinic|20260710123000-0400||ORU^R01|native-1|P|2.5\r"
        "PID|1||EXTERNAL-27^^^LAB^MR||Synthetic^Native||20000102|F\r"
        "OBR|1||ACC-1|PANEL^Metabolic panel^L||20260708100000-0400|20260709143500-0400\r"
        "OBX|1|NM|2345-7^Glucose^LN||5.4|mmol/L|3.5-7.8|N|||F|||20260709143500-0400\r"
        "NTE|1||Exact café \\F\\ details 😀\r"
        "OBX|2|ST|COMMENT^Narrative^L||A\\T\\B|||A|||F|||20260709143500-0400\r"
        "OBR|2||ACC-2|OTHER^Other panel^L|||20260709\r"
        "OBX|1|NM|NATIVE^Repeated native result^L||10~11|mg/L|||||P\r"
    ).encode("utf-8")
    return (set(rows) == {"hl7TextMessage", "hl7TextInfo", "patientLabRouting"}
            and base64.b64decode(rows["hl7TextMessage"]["message"], validate=True) == expected_message
            and rows["hl7TextInfo"]["sending_facility"] == "Native Lab")


def report_expected() -> dict[str, str]:
    return {
        "c:Media": "Download", "c:Format": "Binary", "c:FileExtensionAndVersion": "txt",
        "c:Class": "Consultant Report", "c:SubClass": "consult",
        "c:EventDateTime/d:FullDateTime": "2026-07-09T14:35:00-04:00",
        "c:ReceivedDateTime/d:FullDate": "2026-07-10",
        "c:SourceAuthorPhysician/c:AuthorFreeText": "Dr. Native Source Author",
        "c:SourceFacility": "Native consultation clinic",
        "c:ReportReviewed/c:Name/d:FirstName": "Synthetic",
        "c:ReportReviewed/c:Name/d:LastName": "Native Export",
        "c:ReportReviewed/c:ReviewingOHIPPhysicianId": "999891",
        "c:ReportReviewed/c:DateTimeReportReviewed/d:FullDate": "2026-07-11",
    }


def report_equal(record) -> bool:
    reports = record.findall("c:Reports", NS)
    if len(reports) != 1:
        return False
    report = reports[0]
    if any(len(report.findall(path, NS)) != 1 or report.find(path, NS).text != value
           for path, value in report_expected().items()):
        return False
    notes = report.find("c:Notes", NS).text
    prefix = "[CARLOS native report source context]\n"
    if not notes.startswith(prefix):
        return False
    context = json.loads(notes[len(prefix):])
    return (context.get("version") == 1
            and context["document"]["docdesc"] == "Native consultation description café 😀"
            and context["document"]["reviewdatetime"] == "2026-07-11 12:30:00"
            and context["publicReviewer"]["lastName"] == "Native Export")


def clinically_equal(root, patient: int) -> bool:
    if root.tag != "{cds}OmdCds" or len(root.findall("./c:PatientRecord", NS)) != 1:
        return False
    record = root.find("./c:PatientRecord", NS)
    categories = ["Demographics", "PersonalHistory", "FamilyHistory", "PastHealth", "ProblemList",
                  "RiskFactors"] + ["LaboratoryResults"] * 4 + ["ClinicalNotes", "Reports"] + ["CareElements"] * 3 + ["AlertsAndSpecialNeeds"] + ["NewCategory"] * 6
    if [item.tag for item in record] != ["{cds}" + name for name in categories]:
        return False
    return history_equal(record) and report_equal(record) and laboratory_equal(record) and care_equal(record, patient) and all(len(root.findall(BASE + path, NS)) == 1 and root.find(BASE + path, NS).text == value
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
        if len(names) != 4 or len(set(name.casefold() for name in names)) != len(names):
            raise RuntimeError("Exactly one patient, one report and two supporting members required")
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
        report_members = [name for name in names if name.startswith("Reports/")]
        if (len(report_members) != 1 or not report_members[0].endswith(".txt")
                or archive.read(report_members[0]) != "Native consultation report café 😀\nIndependent attachment bytes\n".encode("utf-8")):
            raise RuntimeError("Independent native report attachment bytes differ")
    root = SAFE_XML.fromstring(xml_bytes)
    if root.find("./c:PatientRecord/c:Reports/c:FilePath", NS).text != report_members[0]:
        raise RuntimeError("Native report path differs from its exact attachment member")
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
            ("../c:CareElements/c:Weight/d:Weight", "71"),
            ("../c:ClinicalNotes/c:MyClinicalNotesContent", "Edited native clinical note café 😀"),
            ("../c:FamilyHistory/c:ResidualInfo/d:DataElement[d:Name='CARLOS.History.Archived.v1']/d:Content", "false"),
            ("../c:Reports/c:SourceAuthorPhysician/c:AuthorFreeText", "Wrong author"),
            ("../c:LaboratoryResults[1]/c:Result/c:Value", "6.4"),
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
        "protocol": 1, "status": "PASSED", "independentExpectedFacts": len(expected(patient)) + len(history_expected()) + len(report_expected()) + sum(map(len, laboratory_expected())),
        "schemaValidClinicalMutantsDetected": detected, "zipMembers": len(names), "patientXmlMembers": 1,
        "pinnedSchemaValid": True, "plaintextZipSha256": hashlib.sha256(args.archive.read_bytes()).hexdigest(),
        "patientXmlSha256": hashlib.sha256(xml_bytes).hexdigest()
    }, sort_keys=True, indent=2))
    print("PASS independent native clinical oracle:", len(expected(patient)) + len(history_expected()) + len(report_expected()) + sum(map(len, laboratory_expected())), "facts and", detected, "schema-valid mutants")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
