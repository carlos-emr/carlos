import re
from dataclasses import dataclass
from datetime import UTC, date, datetime
from hashlib import sha256

from fhir.resources.bundle import Bundle
from fhir.resources.capabilitystatement import CapabilityStatement
from fhir.resources.documentreference import DocumentReference
from fhir.resources.operationoutcome import OperationOutcome
from fhir.resources.organization import Organization
from fhir.resources.patient import Patient
from fhir.resources.practitioner import Practitioner
from hl7apy.consts import VALIDATION_LEVEL
from hl7apy.core import Field, Message
from hl7apy.parser import parse_message

from carlos_patient_portal.identity import (
    normalize_date_of_birth,
    normalize_email,
    normalize_health_card_number,
)
from carlos_patient_portal.invites import normalize_clinic_id
from carlos_patient_portal.models import PatientPortalAccount, PatientPortalUnlockSecret

FHIR_RELEASE = "R4"
FHIR_VERSION = "4.0.1"
HL7_V2_VERSION = "2.5.1"
CARLOS_DEMOGRAPHIC_IDENTIFIER_SYSTEM = (
    "https://github.com/carlos-emr/carlos/fhir/NamingSystem/demographic-no"
)
CARLOS_HEALTH_CARD_IDENTIFIER_SYSTEM = (
    "https://github.com/carlos-emr/carlos/fhir/NamingSystem/health-card-number"
)
CARLOS_UNLOCK_SECRET_IDENTIFIER_SYSTEM = (
    "https://github.com/carlos-emr/carlos/fhir/NamingSystem/unlock-secret"
)
CARLOS_PORTAL_FHIR_BASE = "https://github.com/carlos-emr/carlos/patient-portal/fhir"
HL7_MESSAGE_STRUCTURE = "ADT_A01"
HL7_TRIGGER_EVENT = "A04"
HL7_DEMOGRAPHIC_IDENTIFIER_TYPE = "MR"
HL7_HEALTH_CARD_IDENTIFIER_TYPE = "JHN"
HL7_HEALTH_CARD_ASSIGNING_AUTHORITY = "CARLOSHCN"
HL7_EMAIL_USE_CODE = "NET"
HL7_EMAIL_EQUIPMENT_TYPE = "Internet"
MAX_PATIENT_NAME_LENGTH = 128
MAX_HL7_NAMESPACE_ID_LENGTH = 20
HL7_SEPARATOR_PATTERN = re.compile(r"[|^~\\&\r\n]")
HL7_NAMESPACE_ID_PATTERN = re.compile(r"^[A-Za-z0-9._-]+$")
FHIR_ID_PATTERN = re.compile(r"^[A-Za-z0-9.-]{1,64}$")


@dataclass(frozen=True)
class PortalPatientInteroperabilityIdentity:
    clinic_id: str
    demographic_no: int
    email: str
    date_of_birth: date
    health_card_number: str
    family_name: str
    given_name: str


def normalize_patient_name_part(value: str, field_name: str) -> str:
    normalized_value = " ".join(value.strip().split())
    if not normalized_value:
        raise ValueError(f"{field_name} must not be blank")
    if len(normalized_value) > MAX_PATIENT_NAME_LENGTH:
        raise ValueError(f"{field_name} must be {MAX_PATIENT_NAME_LENGTH} characters or fewer")
    if HL7_SEPARATOR_PATTERN.search(normalized_value) is not None:
        raise ValueError(f"{field_name} must not contain HL7 separator characters")
    return normalized_value


def normalize_fhir_display_text(value: str, field_name: str) -> str:
    normalized_value = " ".join(value.strip().split())
    if not normalized_value:
        raise ValueError(f"{field_name} must not be blank")
    if len(normalized_value) > MAX_PATIENT_NAME_LENGTH:
        raise ValueError(f"{field_name} must be {MAX_PATIENT_NAME_LENGTH} characters or fewer")
    return normalized_value


def normalize_hl7_namespace_id(value: str, field_name: str) -> str:
    normalized_value = " ".join(value.strip().split())
    if not normalized_value:
        raise ValueError(f"{field_name} must not be blank")
    if len(normalized_value) > MAX_HL7_NAMESPACE_ID_LENGTH:
        raise ValueError(f"{field_name} must be {MAX_HL7_NAMESPACE_ID_LENGTH} characters or fewer")
    if HL7_NAMESPACE_ID_PATTERN.fullmatch(normalized_value) is None:
        raise ValueError(
            f"{field_name} may only contain letters, numbers, dots, underscores, or hyphens"
        )
    return normalized_value


def normalize_hl7_email(value: str) -> str:
    normalized_email = normalize_email(value)
    if HL7_SEPARATOR_PATTERN.search(normalized_email) is not None:
        raise ValueError("email must not contain HL7 separator characters")
    return normalized_email


def build_fhir_id(*parts: object) -> str:
    raw_id = "-".join(str(part).strip() for part in parts if str(part).strip())
    safe_id = re.sub(r"[^A-Za-z0-9.-]+", "-", raw_id).strip("-.")
    if FHIR_ID_PATTERN.fullmatch(safe_id) is not None:
        return safe_id
    return f"portal-{sha256(raw_id.encode('utf-8')).hexdigest()[:56]}"


def normalize_interop_identity(
    identity: PortalPatientInteroperabilityIdentity,
) -> PortalPatientInteroperabilityIdentity:
    if identity.demographic_no <= 0:
        raise ValueError("demographic_no must be positive")
    return PortalPatientInteroperabilityIdentity(
        clinic_id=normalize_clinic_id(identity.clinic_id),
        demographic_no=identity.demographic_no,
        email=normalize_email(identity.email),
        date_of_birth=date.fromisoformat(normalize_date_of_birth(identity.date_of_birth)),
        health_card_number=normalize_health_card_number(identity.health_card_number),
        family_name=normalize_patient_name_part(identity.family_name, "family_name"),
        given_name=normalize_patient_name_part(identity.given_name, "given_name"),
    )


def build_fhir_r4_patient(identity: PortalPatientInteroperabilityIdentity) -> dict[str, object]:
    normalized_identity = normalize_interop_identity(identity)
    patient_payload = {
        "resourceType": "Patient",
        "id": build_fhir_id(
            "portal",
            normalized_identity.clinic_id,
            normalized_identity.demographic_no,
        ),
        "active": True,
        "identifier": [
            {
                "system": CARLOS_DEMOGRAPHIC_IDENTIFIER_SYSTEM,
                "value": (
                    f"{normalized_identity.clinic_id}/"
                    f"{normalized_identity.demographic_no}"
                ),
            },
            {
                "system": CARLOS_HEALTH_CARD_IDENTIFIER_SYSTEM,
                "value": normalized_identity.health_card_number,
            },
        ],
        "name": [
            {
                "use": "official",
                "family": normalized_identity.family_name,
                "given": [normalized_identity.given_name],
            }
        ],
        "telecom": [{"system": "email", "value": normalized_identity.email}],
        "birthDate": normalized_identity.date_of_birth.isoformat(),
    }
    return validate_fhir_r4_patient(patient_payload)


def build_fhir_r4_portal_patient(account: PatientPortalAccount) -> dict[str, object]:
    telecom = [{"system": "email", "value": account.email}]
    if account.phone_number is not None:
        telecom.append({"system": "phone", "value": account.phone_number})

    patient_payload = {
        "resourceType": "Patient",
        "id": build_fhir_patient_id(account.clinic_id, account.demographic_no),
        "active": True,
        "identifier": [
            {
                "system": CARLOS_DEMOGRAPHIC_IDENTIFIER_SYSTEM,
                "value": f"{account.clinic_id}/{account.demographic_no}",
            }
        ],
        "telecom": telecom,
    }
    return validate_fhir_r4_patient(patient_payload)


def build_fhir_patient_id(clinic_id: str, demographic_no: int) -> str:
    return build_fhir_id("portal", clinic_id, demographic_no)


def validate_fhir_r4_patient(patient_payload: dict[str, object]) -> dict[str, object]:
    return Patient(patient_payload).as_json()


def build_fhir_r4_capability_statement(
    *,
    service_name: str,
    base_url: str = CARLOS_PORTAL_FHIR_BASE,
) -> dict[str, object]:
    capability_statement = {
        "resourceType": "CapabilityStatement",
        "id": "carlos-patient-portal",
        "status": "draft",
        "date": datetime.now(UTC).isoformat(),
        "publisher": "CARLOS EMR",
        "kind": "instance",
        "implementation": {
            "description": service_name,
            "url": base_url.rstrip("/"),
        },
        "software": {"name": service_name},
        "fhirVersion": FHIR_VERSION,
        "format": ["json"],
        "rest": [
            {
                "mode": "server",
                "resource": [
                    {
                        "type": resource_type,
                        "interaction": [{"code": "read"}, {"code": "search-type"}],
                    }
                    for resource_type in [
                        "Patient",
                        "DocumentReference",
                        "Organization",
                        "Practitioner",
                    ]
                ],
            }
        ],
    }
    return CapabilityStatement(capability_statement).as_json()


def build_fhir_r4_operation_outcome(
    *,
    code: str,
    diagnostics: str,
    severity: str = "error",
) -> dict[str, object]:
    return OperationOutcome(
        {
            "resourceType": "OperationOutcome",
            "issue": [
                {
                    "severity": severity,
                    "code": code,
                    "diagnostics": diagnostics,
                }
            ],
        }
    ).as_json()


def build_fhir_r4_bundle(
    *,
    resources: list[dict[str, object]],
    base_url: str = CARLOS_PORTAL_FHIR_BASE,
    bundle_type: str = "searchset",
    self_link: str | None = None,
) -> dict[str, object]:
    normalized_base_url = base_url.rstrip("/")
    entries = [
        {
            "fullUrl": f"{normalized_base_url}/{resource['resourceType']}/{resource['id']}",
            "resource": resource,
            "search": {"mode": "match"},
        }
        for resource in resources
    ]
    bundle_payload = {
        "resourceType": "Bundle",
        "type": bundle_type,
        "total": len(resources),
        "entry": entries,
    }
    if self_link is not None:
        bundle_payload["link"] = [{"relation": "self", "url": self_link}]
    return Bundle(bundle_payload).as_json()


def build_fhir_r4_organization(*, clinic_id: str, clinic_name: str) -> dict[str, object]:
    return Organization(
        {
            "resourceType": "Organization",
            "id": build_fhir_organization_id(clinic_id),
            "active": True,
            "name": clinic_name,
            "identifier": [
                {
                    "system": f"{CARLOS_PORTAL_FHIR_BASE}/NamingSystem/clinic-id",
                    "value": normalize_clinic_id(clinic_id),
                }
            ],
        }
    ).as_json()


def build_fhir_organization_id(clinic_id: str) -> str:
    return build_fhir_id("organization", normalize_clinic_id(clinic_id))


def build_fhir_r4_practitioner(*, clinic_id: str, name: str) -> dict[str, object]:
    normalized_name = normalize_fhir_display_text(name, "name")
    return Practitioner(
        {
            "resourceType": "Practitioner",
            "id": build_fhir_practitioner_id(clinic_id=clinic_id, name=normalized_name),
            "active": True,
            "name": [{"text": normalized_name}],
        }
    ).as_json()


def build_fhir_practitioner_id(*, clinic_id: str, name: str) -> str:
    return build_fhir_id(
        "practitioner",
        normalize_clinic_id(clinic_id),
        normalize_fhir_display_text(name, "name"),
    )


def build_fhir_r4_document_reference(
    unlock_secret: PatientPortalUnlockSecret,
    *,
    patient_reference: str,
) -> dict[str, object]:
    title = unlock_secret.label or "Email password"
    document_reference = {
        "resourceType": "DocumentReference",
        "id": str(unlock_secret.id),
        "status": "current",
        "identifier": [
            {
                "system": CARLOS_UNLOCK_SECRET_IDENTIFIER_SYSTEM,
                "value": f"{unlock_secret.clinic_id}/{unlock_secret.id}",
            }
        ],
        "subject": {"reference": patient_reference},
        "date": unlock_secret.created_at.isoformat(),
        "description": title,
        "author": [
            {
                "reference": "Practitioner/"
                + build_fhir_practitioner_id(
                    clinic_id=unlock_secret.clinic_id,
                    name=unlock_secret.created_by,
                )
            }
        ],
        "content": [
            {
                "attachment": {
                    "contentType": "text/plain",
                    "title": title,
                }
            }
        ],
    }
    if unlock_secret.source_reference is not None:
        document_reference["masterIdentifier"] = {
            "system": f"{CARLOS_PORTAL_FHIR_BASE}/NamingSystem/source-reference",
            "value": unlock_secret.source_reference,
        }
    return DocumentReference(document_reference).as_json()


def format_hl7_timestamp(value: datetime) -> str:
    comparable_value = value
    if comparable_value.tzinfo is None:
        comparable_value = comparable_value.replace(tzinfo=UTC)
    return comparable_value.astimezone(UTC).strftime("%Y%m%d%H%M%S")


def build_hl7_patient_identifier(
    *,
    identifier: str,
    assigning_authority: str,
    identifier_type: str,
) -> Field:
    patient_identifier = Field(
        "PID_3",
        version=HL7_V2_VERSION,
        validation_level=VALIDATION_LEVEL.STRICT,
    )
    patient_identifier.cx_1 = identifier
    patient_identifier.cx_4.hd_1 = assigning_authority
    patient_identifier.cx_5 = identifier_type
    return patient_identifier


def build_hl7_v251_patient_registration(
    identity: PortalPatientInteroperabilityIdentity,
    *,
    message_time: datetime,
    message_control_id: str,
) -> str:
    normalized_identity = normalize_interop_identity(identity)
    hl7_clinic_id = normalize_hl7_namespace_id(normalized_identity.clinic_id, "clinic_id")
    hl7_email = normalize_hl7_email(normalized_identity.email)
    normalized_message_control_id = normalize_patient_name_part(
        message_control_id,
        "message_control_id",
    )
    message_timestamp = format_hl7_timestamp(message_time)
    message = Message(
        HL7_MESSAGE_STRUCTURE,
        version=HL7_V2_VERSION,
        validation_level=VALIDATION_LEVEL.STRICT,
    )
    message.msh.msh_3 = "CARLOS"
    message.msh.msh_4 = hl7_clinic_id
    message.msh.msh_5 = "CARLOSPORTAL"
    message.msh.msh_6 = hl7_clinic_id
    message.msh.msh_7 = message_timestamp
    message.msh.msh_9 = f"ADT^{HL7_TRIGGER_EVENT}^{HL7_MESSAGE_STRUCTURE}"
    message.msh.msh_10 = normalized_message_control_id
    message.msh.msh_11 = "P"
    message.msh.msh_12 = HL7_V2_VERSION
    message.evn.evn_2 = message_timestamp
    message.pid.add(
        build_hl7_patient_identifier(
            identifier=str(normalized_identity.demographic_no),
            assigning_authority=hl7_clinic_id,
            identifier_type=HL7_DEMOGRAPHIC_IDENTIFIER_TYPE,
        )
    )
    message.pid.add(
        build_hl7_patient_identifier(
            identifier=normalized_identity.health_card_number,
            assigning_authority=HL7_HEALTH_CARD_ASSIGNING_AUTHORITY,
            identifier_type=HL7_HEALTH_CARD_IDENTIFIER_TYPE,
        )
    )
    message.pid.pid_5 = f"{normalized_identity.family_name}^{normalized_identity.given_name}"
    message.pid.pid_7 = normalized_identity.date_of_birth.strftime("%Y%m%d")
    message.pid.pid_13.xtn_2 = HL7_EMAIL_USE_CODE
    message.pid.pid_13.xtn_3 = HL7_EMAIL_EQUIPMENT_TYPE
    message.pid.pid_13.xtn_4 = hl7_email
    message.pv1.pv1_2 = "O"
    message.validate()
    return message.to_er7()


def validate_hl7_v251_message(encoded_message: str) -> str:
    message = parse_message(
        encoded_message,
        validation_level=VALIDATION_LEVEL.STRICT,
        find_groups=True,
    )
    message.validate()
    return message.to_er7()
