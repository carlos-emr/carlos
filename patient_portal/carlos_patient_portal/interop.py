import json
import re
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, date, datetime
from hashlib import sha256
from pathlib import Path

from fhir.resources.bundle import Bundle
from fhir.resources.capabilitystatement import CapabilityStatement
from fhir.resources.documentreference import DocumentReference
from fhir.resources.operationoutcome import OperationOutcome
from fhir.resources.organization import Organization
from fhir.resources.patient import Patient
from fhir.resources.practitioner import Practitioner
from hl7apy.consts import VALIDATION_LEVEL
from hl7apy.core import Field, Message
from hl7apy.exceptions import HL7apyException
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
FHIR_CAPABILITY_STATEMENT_DATE = "2026-07-28T00:00:00Z"
PORTAL_SOFTWARE_VERSION = "0.1.0"
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
HL7_PATIENT_REGISTRATION_PROFILE_ID = "carlos.portal.patient-registration.adt-a04.v251"
HL7_PATIENT_REGISTRATION_PROFILE_FILENAME = "carlos_patient_registration_adt_a04_v251.json"
HL7_PATIENT_REGISTRATION_PROFILE_DIR = Path(__file__).resolve().parent / "interop_profiles"
HL7_DEMOGRAPHIC_IDENTIFIER_TYPE = "MR"
HL7_HEALTH_CARD_IDENTIFIER_TYPE = "JHN"
HL7_HEALTH_CARD_ASSIGNING_AUTHORITY = "CARLOSHCN"
HL7_EMAIL_USE_CODE = "NET"
HL7_EMAIL_EQUIPMENT_TYPE = "Internet"
MAX_PATIENT_NAME_LENGTH = 128
MAX_HL7_NAMESPACE_ID_LENGTH = 20
HL7_SEPARATOR_PATTERN = re.compile(r"[|^~\\&\r\n]")
HL7_NAMESPACE_ID_PATTERN = re.compile(r"^[A-Za-z0-9._-]+$")
# C0 and C1 control characters. None of these are HL7 separators, so the separator check above does
# not see them, but NUL terminates a string in a C-based HL7 receiver — silently truncating PID-5
# and everything after it in that field — and the same value is rejected outright by PostgreSQL
# `text` columns on the FHIR side. Whitespace controls are already collapsed before this runs.
HL7_CONTROL_CHARACTER_PATTERN = re.compile(r"[\x00-\x1f\x7f-\x9f]")


@dataclass(frozen=True)
class PortalPatientInteroperabilityIdentity:
    clinic_id: str
    demographic_no: int
    email: str
    date_of_birth: date
    health_card_number: str
    family_name: str
    given_name: str


class Hl7ConformanceProfileError(ValueError):
    """Raised when an HL7 message fails the CARLOS-owned conformance profile."""


def reject_control_characters(value: str, field_name: str) -> None:
    if HL7_CONTROL_CHARACTER_PATTERN.search(value) is not None:
        raise ValueError(f"{field_name} must not contain control characters")


def normalize_patient_name_part(value: str, field_name: str) -> str:
    normalized_value = " ".join(value.strip().split())
    if not normalized_value:
        raise ValueError(f"{field_name} must not be blank")
    if len(normalized_value) > MAX_PATIENT_NAME_LENGTH:
        raise ValueError(f"{field_name} must be {MAX_PATIENT_NAME_LENGTH} characters or fewer")
    if HL7_SEPARATOR_PATTERN.search(normalized_value) is not None:
        raise ValueError(f"{field_name} must not contain HL7 separator characters")
    reject_control_characters(normalized_value, field_name)
    return normalized_value


def normalize_fhir_display_text(value: str, field_name: str) -> str:
    normalized_value = " ".join(value.strip().split())
    if not normalized_value:
        raise ValueError(f"{field_name} must not be blank")
    if len(normalized_value) > MAX_PATIENT_NAME_LENGTH:
        raise ValueError(f"{field_name} must be {MAX_PATIENT_NAME_LENGTH} characters or fewer")
    reject_control_characters(normalized_value, field_name)
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
    normalized_parts = tuple(str(part).strip() for part in parts)
    if not normalized_parts or any(not part for part in normalized_parts):
        raise ValueError("FHIR ID components must not be blank")
    encoded_tuple = "".join(f"{len(part.encode('utf-8'))}:{part}" for part in normalized_parts)
    return f"portal-{sha256(encoded_tuple.encode('utf-8')).hexdigest()[:56]}"


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
                "value": (f"{normalized_identity.clinic_id}/{normalized_identity.demographic_no}"),
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
        "date": FHIR_CAPABILITY_STATEMENT_DATE,
        "publisher": "CARLOS EMR",
        "kind": "instance",
        "implementation": {
            "description": service_name,
            "url": base_url.rstrip("/"),
        },
        "software": {"name": service_name, "version": PORTAL_SOFTWARE_VERSION},
        "fhirVersion": FHIR_VERSION,
        "format": ["json"],
        "rest": [
            {
                "mode": "server",
                "security": {
                    "cors": False,
                    "service": [
                        {
                            "text": (
                                "CARLOS portal Bearer session token; this MVP does not "
                                "implement SMART on FHIR."
                            ),
                        }
                    ],
                    "description": (
                        "Patient-scoped read access using a CARLOS portal Bearer session token."
                    ),
                },
                "resource": [
                    {
                        "type": resource_type,
                        "interaction": [{"code": "read"}, {"code": "search-type"}],
                        "searchParam": fhir_search_parameters(resource_type),
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


def fhir_search_parameters(resource_type: str) -> list[dict[str, str]]:
    common = [
        {"name": "_id", "type": "token", "documentation": "FHIR logical resource id."},
        {"name": "_count", "type": "number", "documentation": "Page size, from 1 to 100."},
        {
            "name": "_offset",
            "type": "number",
            "documentation": "Zero-based offset used by this portal's bounded search.",
        },
    ]
    if resource_type == "DocumentReference":
        common.append(
            {
                "name": "subject",
                "type": "reference",
                "documentation": "Must identify the authenticated portal patient.",
            }
        )
    return common


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
    total: int | None = None,
    next_link: str | None = None,
    previous_link: str | None = None,
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
        "total": len(resources) if total is None else total,
        "entry": entries,
    }
    links = [
        {"relation": relation, "url": url}
        for relation, url in (
            ("self", self_link),
            ("previous", previous_link),
            ("next", next_link),
        )
        if url is not None
    ]
    if links:
        bundle_payload["link"] = links
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


def build_fhir_r4_practitioner(
    *,
    clinic_id: str,
    name: str,
    provider_id: str | None = None,
) -> dict[str, object]:
    normalized_name = normalize_fhir_display_text(name, "name")
    return Practitioner(
        {
            "resourceType": "Practitioner",
            "id": build_fhir_practitioner_id(
                clinic_id=clinic_id,
                name=normalized_name,
                provider_id=provider_id,
            ),
            "active": True,
            "name": [{"text": normalized_name}],
        }
    ).as_json()


def build_fhir_practitioner_id(
    *,
    clinic_id: str,
    name: str,
    provider_id: str | None = None,
) -> str:
    return build_fhir_id(
        "practitioner",
        normalize_clinic_id(clinic_id),
        (
            normalize_fhir_display_text(provider_id, "provider_id")
            if provider_id is not None
            else normalize_fhir_display_text(name, "name")
        ),
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
        "date": fhir_instant(unlock_secret.created_at),
        "description": title,
        "author": [
            {
                "reference": "Practitioner/"
                + build_fhir_practitioner_id(
                    clinic_id=unlock_secret.clinic_id,
                    name=unlock_secret.created_by,
                    provider_id=getattr(unlock_secret, "created_by_id", None),
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


def fhir_instant(value: datetime) -> str:
    aware_value = value if value.tzinfo is not None else value.replace(tzinfo=UTC)
    return aware_value.astimezone(UTC).isoformat().replace("+00:00", "Z")


def load_hl7_v251_patient_registration_profile() -> dict[str, object]:
    profile_path = HL7_PATIENT_REGISTRATION_PROFILE_DIR / HL7_PATIENT_REGISTRATION_PROFILE_FILENAME
    profile_text = profile_path.read_text(encoding="utf-8")
    profile = json.loads(profile_text)
    if not isinstance(profile, dict):
        raise Hl7ConformanceProfileError("HL7 conformance profile must be a JSON object")
    return profile


def get_hl7_field_repetitions(message: Message, field_path: str) -> list[Field]:
    segment_id, separator, field_number = field_path.partition("-")
    if not separator or not segment_id or not field_number:
        raise Hl7ConformanceProfileError(f"invalid HL7 field path: {field_path}")
    segment = getattr(message, segment_id.lower())
    field_name = f"{segment_id}_{field_number}".upper()
    return [field for field in segment.children if field.name == field_name]


def get_hl7_component_value(field: Field, component_number: str) -> str:
    component_suffix = f"_{component_number}"
    for component in field.children:
        if component.name.endswith(component_suffix):
            return component.to_er7()
    return ""


def get_hl7_profile_path_values(message: Message, path: str) -> list[str]:
    """Every repetition's value at `path`, not just the first.

    A profile check that reads only `repetitions[0]` certifies a message whose second repetition
    says something else entirely, which is how a conflicting identifier or an extra contact address
    rides along on a message the portal declared conformant.
    """
    field_path, _, component_number = path.partition(".")
    repetitions = get_hl7_field_repetitions(message, field_path)
    if not component_number:
        return [repetition.to_er7() for repetition in repetitions]
    return [get_hl7_component_value(repetition, component_number) for repetition in repetitions]


def get_hl7_profile_path_value(message: Message, path: str) -> str:
    values = get_hl7_profile_path_values(message, path)
    return values[0] if values else ""


def resolve_hl7_profile_expected_value(message: Message, value: object) -> str:
    expected_value = str(value)
    if expected_value.startswith("${") and expected_value.endswith("}"):
        return get_hl7_profile_path_value(message, expected_value[2:-1])
    return expected_value


def get_hl7_profile_entries(profile: dict[str, object], key: str) -> list[object]:
    entries = profile.get(key, [])
    if not isinstance(entries, list):
        raise Hl7ConformanceProfileError(f"profile {key} must be a list")
    return entries


def has_hl7_segment(message: Message, segment_id: str) -> bool:
    return any(child.name == segment_id for child in message.children)


def check_hl7_profile_metadata(profile_payload: dict[str, object]) -> list[str]:
    """The profile document itself is the right one and targets the supported HL7 version."""
    errors: list[str] = []
    if profile_payload.get("id") != HL7_PATIENT_REGISTRATION_PROFILE_ID:
        errors.append("profile id does not match CARLOS patient registration profile")
    if profile_payload.get("hl7_version") != HL7_V2_VERSION:
        errors.append("profile HL7 version does not match supported version")
    return errors


def check_hl7_required_segments(message: Message, profile_payload: dict[str, object]) -> list[str]:
    """Every segment the profile marks usage "R" is present."""
    errors: list[str] = []
    for segment in get_hl7_profile_entries(profile_payload, "segments"):
        if not isinstance(segment, dict):
            errors.append("segment entry must be an object")
            continue
        segment_id = str(segment.get("id", ""))
        if segment.get("usage") == "R" and not has_hl7_segment(message, segment_id):
            errors.append(f"{segment_id} segment is required")
    return errors


def check_hl7_fixed_fields(message: Message, profile_payload: dict[str, object]) -> list[str]:
    """Every repetition of each pinned path carries the value the profile fixes it to."""
    errors: list[str] = []
    for fixed_field in get_hl7_profile_entries(profile_payload, "fixed_fields"):
        if not isinstance(fixed_field, dict):
            errors.append("fixed field entry must be an object")
            continue
        path = str(fixed_field.get("path", ""))
        expected_value = resolve_hl7_profile_expected_value(message, fixed_field.get("value", ""))
        # Every repetition has to match, not just the first: a conforming first repetition must not
        # vouch for whatever follows it.
        for actual_value in get_hl7_profile_path_values(message, path) or [""]:
            if actual_value != expected_value:
                errors.append(f"{path} expected {expected_value!r}, got {actual_value!r}")
    return errors


def check_hl7_field_cardinality(message: Message, profile_payload: dict[str, object]) -> list[str]:
    """Fields the profile declares single-valued do not repeat.

    Checked directly rather than inferred from the fixed-field comparisons: an extra repetition can
    match every component the profile pins and still carry a different address or identifier in one
    it does not.
    """
    errors: list[str] = []
    for single_valued_field in get_hl7_profile_entries(profile_payload, "single_valued_fields"):
        path = str(single_valued_field)
        repetition_count = len(get_hl7_field_repetitions(message, path))
        if repetition_count > 1:
            errors.append(f"{path} must not repeat, got {repetition_count} repetitions")
    return errors


def check_hl7_required_fields(message: Message, profile_payload: dict[str, object]) -> list[str]:
    """Every path the profile requires carries a non-empty value."""
    errors: list[str] = []
    for required_field in get_hl7_profile_entries(profile_payload, "required_fields"):
        if not isinstance(required_field, dict):
            errors.append("required field entry must be an object")
            continue
        path = str(required_field.get("path", ""))
        if not get_hl7_profile_path_value(message, path):
            errors.append(f"{path} is required")
    return errors


def check_hl7_required_identifiers(
    message: Message,
    profile_payload: dict[str, object],
) -> list[str]:
    """Each required identifier type is present exactly once per assigning authority."""
    errors: list[str] = []
    for required_identifier in get_hl7_profile_entries(profile_payload, "required_identifiers"):
        if not isinstance(required_identifier, dict):
            errors.append("required identifier entry must be an object")
            continue
        field_path = str(required_identifier.get("field", ""))
        expected_identifier_type = str(required_identifier.get("identifier_type", ""))
        expected_assigning_authority = resolve_hl7_profile_expected_value(
            message,
            required_identifier.get("assigning_authority", ""),
        )
        matching_identifiers = [
            get_hl7_component_value(identifier, "1")
            for identifier in get_hl7_field_repetitions(message, field_path)
            if get_hl7_component_value(identifier, "1")
            and get_hl7_component_value(identifier, "4") == expected_assigning_authority
            and get_hl7_component_value(identifier, "5") == expected_identifier_type
        ]
        if not matching_identifiers:
            errors.append(
                f"{field_path} requires {expected_identifier_type!r} identifier "
                f"from {expected_assigning_authority!r}"
            )
        elif len(matching_identifiers) != 1:
            # Two different identifiers of the same type from the same authority. A consumer that
            # takes the last repetition binds the record to a different patient than one taking
            # the first, so this cannot be certified as conformant either way.
            errors.append(
                f"{field_path} has duplicate or conflicting "
                f"{expected_identifier_type!r} identifiers "
                f"from {expected_assigning_authority!r}"
            )
    return errors


# Each rule group returns its own errors, so every rule the profile declares runs on every message
# and the caller reports all of them at once rather than only the first to fail.
HL7_PROFILE_RULES: tuple[Callable[[Message, dict[str, object]], list[str]], ...] = (
    check_hl7_required_segments,
    check_hl7_fixed_fields,
    check_hl7_field_cardinality,
    check_hl7_required_fields,
    check_hl7_required_identifiers,
)


def validate_hl7_v251_patient_registration_profile(
    encoded_message: str,
    *,
    profile: dict[str, object] | None = None,
) -> str:
    """Validate an encoded message against the CARLOS patient-registration profile.

    Raises `Hl7ConformanceProfileError` listing every rule that failed, not just the first.
    """
    try:
        normalized_message = validate_hl7_v251_message(encoded_message)
        message = parse_message(
            normalized_message,
            validation_level=VALIDATION_LEVEL.STRICT,
            find_groups=True,
        )
    except HL7apyException as exc:
        raise Hl7ConformanceProfileError(f"HL7 v2.5.1 validation failed: {exc}") from exc

    profile_payload = load_hl7_v251_patient_registration_profile() if profile is None else profile
    errors = check_hl7_profile_metadata(profile_payload)
    for rule in HL7_PROFILE_RULES:
        errors.extend(rule(message, profile_payload))

    if errors:
        raise Hl7ConformanceProfileError("; ".join(errors))
    return normalized_message


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
