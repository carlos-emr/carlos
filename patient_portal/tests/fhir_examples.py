from __future__ import annotations

import json
import sys
from datetime import datetime
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from carlos_patient_portal.interop import (
    build_fhir_patient_id,
    build_fhir_r4_bundle,
    build_fhir_r4_capability_statement,
    build_fhir_r4_document_reference,
    build_fhir_r4_operation_outcome,
    build_fhir_r4_organization,
    build_fhir_r4_portal_patient,
    build_fhir_r4_practitioner,
)


def write_fhir_examples(output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    base_url = "https://portal.example.test/fhir"
    account = SimpleNamespace(
        clinic_id="default",
        demographic_no=1234,
        email="example.patient@example.com",
        phone_number="+15555550123",
    )
    unlock_secret = SimpleNamespace(
        id=1,
        clinic_id="default",
        # SQLite returns DateTime(timezone=True) values without tzinfo; exercise that path in the
        # resource set sent to the official validator.
        created_at=datetime(2026, 7, 23, 12, 0),
        created_by="CarlosDoc",
        label="Specialist message",
        source_reference="message-3135",
    )

    patient = build_fhir_r4_portal_patient(account)
    document_reference = build_fhir_r4_document_reference(
        unlock_secret,
        patient_reference=f"Patient/{build_fhir_patient_id('default', 1234)}",
    )
    organization = build_fhir_r4_organization(
        clinic_id="default",
        clinic_name="Maple Creek Medical",
    )
    practitioner = build_fhir_r4_practitioner(
        clinic_id="default",
        name="CarlosDoc",
    )

    examples = {
        "document-reference-bundle.json": build_fhir_r4_bundle(
            resources=[document_reference],
            base_url=base_url,
            self_link=f"{base_url}/DocumentReference",
        ),
        "organization-bundle.json": build_fhir_r4_bundle(
            resources=[organization],
            base_url=base_url,
            self_link=f"{base_url}/Organization",
        ),
        "patient-bundle.json": build_fhir_r4_bundle(
            resources=[patient],
            base_url=base_url,
            self_link=f"{base_url}/Patient",
        ),
        "practitioner-bundle.json": build_fhir_r4_bundle(
            resources=[practitioner],
            base_url=base_url,
            self_link=f"{base_url}/Practitioner",
        ),
        "capability-statement.json": build_fhir_r4_capability_statement(
            service_name="CARLOS Patient Portal",
            base_url=base_url,
        ),
        "document-reference.json": document_reference,
        "operation-outcome.json": build_fhir_r4_operation_outcome(
            code="not-found",
            diagnostics="resource not found",
        ),
        "organization.json": organization,
        "patient.json": patient,
        "practitioner.json": practitioner,
    }

    for filename, resource in examples.items():
        (output_dir / filename).write_text(
            json.dumps(resource, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: python tests/fhir_examples.py <output-dir>", file=sys.stderr)
        return 2
    write_fhir_examples(Path(sys.argv[1]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
