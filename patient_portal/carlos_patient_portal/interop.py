import re
from dataclasses import dataclass
from datetime import UTC, date, datetime
from hashlib import sha256

from fhir.resources.patient import Patient
from hl7apy.consts import VALIDATION_LEVEL
from hl7apy.core import Field, Message
from hl7apy.parser import parse_message

from carlos_patient_portal.identity import (
    normalize_date_of_birth,
    normalize_email,
    normalize_health_card_number,
)
from carlos_patient_portal.invites import normalize_clinic_id

FHIR_RELEASE = "R4"
HL7_V2_VERSION = "2.5.1"
CARLOS_DEMOGRAPHIC_IDENTIFIER_SYSTEM = (
    "https://github.com/carlos-emr/carlos/fhir/NamingSystem/demographic-no"
)
CARLOS_HEALTH_CARD_IDENTIFIER_SYSTEM = (
    "https://github.com/carlos-emr/carlos/fhir/NamingSystem/health-card-number"
)
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


def validate_fhir_r4_patient(patient_payload: dict[str, object]) -> dict[str, object]:
    return Patient(patient_payload).as_json()


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
