"""Patient-scoped FHIR R4 routes and the HL7 v2.5.1 conformance artifact."""

import json
from datetime import UTC, datetime
from unittest import mock

import pytest
from fastapi.testclient import TestClient
from fhir.resources.bundle import Bundle
from fhir.resources.capabilitystatement import CapabilityStatement
from fhir.resources.documentreference import DocumentReference
from fhir.resources.operationoutcome import OperationOutcome
from fhir.resources.organization import Organization
from fhir.resources.patient import Patient
from fhir.resources.practitioner import Practitioner
from sqlalchemy import select

from carlos_patient_portal import presenters
from carlos_patient_portal.interop import (
    FHIR_RELEASE,
    FHIR_VERSION,
    HL7_PATIENT_REGISTRATION_PROFILE_ID,
    HL7_V2_VERSION,
    Hl7ConformanceProfileError,
    PortalPatientInteroperabilityIdentity,
    build_fhir_patient_id,
    build_fhir_practitioner_id,
    build_fhir_r4_patient,
    build_fhir_r4_practitioner,
    build_hl7_v251_patient_registration,
    load_hl7_v251_patient_registration_profile,
    normalize_patient_name_part,
    validate_hl7_v251_message,
    validate_hl7_v251_patient_registration_profile,
)
from carlos_patient_portal.invites import normalize_staff_actor
from carlos_patient_portal.models import (
    UNLOCK_SECRET_TYPE_EMAIL,
    PatientPortalAccount,
    PatientPortalAuditEvent,
)
from carlos_patient_portal.routes import fhir as fhir_routes
from carlos_patient_portal.unlock_secrets import (
    MAX_UNLOCK_SECRET_PROVIDER_OPTIONS,
    create_unlock_secret,
    list_unlock_secret_provider_options,
    list_unlock_secret_providers,
    revoke_unlock_secret,
)
from carlos_patient_portal.view_models import (
    ProviderFilterOptionViewModel,
)
from tests.support import (
    SEEDED_INVITE_DOB,
    SEEDED_INVITE_EMAIL,
    SEEDED_INVITE_HCN,
    UNLOCK_SECRET_ENCRYPTION_SECRET,
    activate_seeded_patient_account,
    bearer_headers,
    migrated_development_app,
    sign_in_patient_api_session,
)


def test_fhir_metadata_returns_capability_statement() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    response = client.get("/fhir/metadata")
    preflight = client.options(
        "/fhir/Patient",
        headers={
            "Origin": "https://client.example.test",
            "Access-Control-Request-Method": "GET",
        },
    )
    payload = response.json()

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/fhir+json")
    assert response.headers["cache-control"] == "no-store"
    assert payload["resourceType"] == "CapabilityStatement"
    assert payload["fhirVersion"] == FHIR_VERSION
    assert payload["implementation"]["url"] == "http://testserver/fhir"
    assert payload["rest"][0]["security"]["cors"] is False
    assert preflight.status_code == 405
    assert "access-control-allow-origin" not in preflight.headers
    assert {resource["type"] for resource in payload["rest"][0]["resource"]} == {
        "DocumentReference",
        "Organization",
        "Patient",
        "Practitioner",
    }
    CapabilityStatement(payload)


def test_fhir_patient_endpoints_are_bearer_authenticated_and_patient_scoped() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    patient_token = sign_in_patient_api_session(client)
    auth_headers = bearer_headers(patient_token)
    patient_id = build_fhir_patient_id("default", 1234)

    unauthenticated_response = client.get("/fhir/Patient")
    search_response = client.get("/fhir/Patient", headers=auth_headers)
    read_response = client.get(f"/fhir/Patient/{patient_id}", headers=auth_headers)
    wrong_patient_response = client.get(
        "/fhir/Patient/portal-default-5678",
        headers=auth_headers,
    )

    assert unauthenticated_response.status_code == 401
    assert unauthenticated_response.headers["content-type"].startswith("application/fhir+json")
    assert unauthenticated_response.json()["resourceType"] == "OperationOutcome"
    OperationOutcome(unauthenticated_response.json())

    assert search_response.status_code == 200
    search_payload = search_response.json()
    assert search_payload["resourceType"] == "Bundle"
    assert search_payload["total"] == 1
    assert search_payload["link"][0] == {
        "relation": "self",
        "url": "http://testserver/fhir/Patient?_count=20&_offset=0",
    }
    assert search_payload["entry"][0]["fullUrl"] == (f"http://testserver/fhir/Patient/{patient_id}")
    assert search_payload["entry"][0]["resource"]["id"] == patient_id
    Bundle(search_payload)

    assert read_response.status_code == 200
    patient_payload = read_response.json()
    assert patient_payload["resourceType"] == "Patient"
    assert patient_payload["id"] == patient_id
    assert patient_payload["identifier"][0]["value"] == "default/1234"
    assert patient_payload["telecom"][0]["value"] == SEEDED_INVITE_EMAIL
    Patient(patient_payload)

    assert wrong_patient_response.status_code == 404
    assert wrong_patient_response.json()["resourceType"] == "OperationOutcome"
    OperationOutcome(wrong_patient_response.json())


def test_fhir_document_organization_and_practitioner_resources_are_scoped() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_a_id = activate_seeded_patient_account(app, client)
    account_b_id = activate_seeded_patient_account(
        app,
        client,
        username="other.patient",
        demographic_no=5678,
        email="other.patient@example.com",
        health_card_number="WXYZ 9876-5432",
    )
    patient_a_token = sign_in_patient_api_session(client)
    auth_headers = bearer_headers(patient_a_token)
    raw_secret_a = "FhirEmail9!"
    raw_secret_b = "OtherFhir9!"
    raw_secret_revoked = "RevokedFhir9!"

    with app.state.session_factory() as session:
        with session.begin():
            created_a = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_a,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Specialist message",
                source_reference="message-3135",
            )
            created_b = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=5678,
                account_id=account_b_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_b,
                created_by="Dr other",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Other patient message",
                source_reference="message-3136",
            )
            created_revoked = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_revoked,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Revoked message",
                source_reference="message-3137",
            )
            revoke_unlock_secret(
                session,
                created_revoked.unlock_secret.id,
                clinic_id="default",
                demographic_no=1234,
                revoked_by="CarlosDoc",
                reason="staff_requested",
            )
            active_a_id = created_a.unlock_secret.id
            other_patient_id = created_b.unlock_secret.id
            revoked_id = created_revoked.unlock_secret.id

    document_search_response = client.get("/fhir/DocumentReference", headers=auth_headers)
    document_read_response = client.get(
        f"/fhir/DocumentReference/{active_a_id}",
        headers=auth_headers,
    )
    other_patient_response = client.get(
        f"/fhir/DocumentReference/{other_patient_id}",
        headers=auth_headers,
    )
    revoked_response = client.get(
        f"/fhir/DocumentReference/{revoked_id}",
        headers=auth_headers,
    )
    organization_search_response = client.get("/fhir/Organization", headers=auth_headers)
    organization_id = organization_search_response.json()["entry"][0]["resource"]["id"]
    organization_read_response = client.get(
        f"/fhir/Organization/{organization_id}",
        headers=auth_headers,
    )
    practitioner_search_response = client.get("/fhir/Practitioner", headers=auth_headers)
    practitioner_id = practitioner_search_response.json()["entry"][0]["resource"]["id"]
    practitioner_read_response = client.get(
        f"/fhir/Practitioner/{practitioner_id}",
        headers=auth_headers,
    )

    assert document_search_response.status_code == 200
    document_search_payload = document_search_response.json()
    assert document_search_payload["resourceType"] == "Bundle"
    assert document_search_payload["total"] == 1
    assert document_search_payload["link"][0] == {
        "relation": "self",
        "url": "http://testserver/fhir/DocumentReference?_count=20&_offset=0",
    }
    assert document_search_payload["entry"][0]["fullUrl"] == (
        f"http://testserver/fhir/DocumentReference/{active_a_id}"
    )
    assert document_search_payload["entry"][0]["resource"]["id"] == str(active_a_id)
    assert raw_secret_a not in document_search_response.text
    assert raw_secret_b not in document_search_response.text
    assert raw_secret_revoked not in document_search_response.text
    Bundle(document_search_payload)

    assert document_read_response.status_code == 200
    document_payload = document_read_response.json()
    assert document_payload["resourceType"] == "DocumentReference"
    assert document_payload["subject"]["reference"] == (
        f"Patient/{build_fhir_patient_id('default', 1234)}"
    )
    assert document_payload["description"] == "Specialist message"
    assert document_payload["date"].endswith("Z")
    assert document_payload["masterIdentifier"]["value"] == "message-3135"
    assert raw_secret_a not in document_read_response.text
    DocumentReference(document_payload)

    for unavailable_response in [other_patient_response, revoked_response]:
        assert unavailable_response.status_code == 404
        assert unavailable_response.json()["resourceType"] == "OperationOutcome"
        OperationOutcome(unavailable_response.json())

    assert organization_search_response.status_code == 200
    organization_search_payload = organization_search_response.json()
    assert organization_search_payload["total"] == 1
    assert organization_search_payload["link"][0] == {
        "relation": "self",
        "url": "http://testserver/fhir/Organization?_count=20&_offset=0",
    }
    assert organization_search_payload["entry"][0]["fullUrl"] == (
        f"http://testserver/fhir/Organization/{organization_id}"
    )
    Organization(organization_search_payload["entry"][0]["resource"])
    assert organization_read_response.status_code == 200
    assert organization_read_response.json()["name"] == "Maple Creek Medical"
    Organization(organization_read_response.json())

    assert practitioner_search_response.status_code == 200
    practitioner_search_payload = practitioner_search_response.json()
    assert practitioner_search_payload["total"] == 1
    assert practitioner_search_payload["link"][0] == {
        "relation": "self",
        "url": "http://testserver/fhir/Practitioner?_count=20&_offset=0",
    }
    assert practitioner_search_payload["entry"][0]["fullUrl"] == (
        f"http://testserver/fhir/Practitioner/{practitioner_id}"
    )
    assert practitioner_search_payload["entry"][0]["resource"]["name"][0]["text"] == "CarlosDoc"
    Practitioner(practitioner_search_payload["entry"][0]["resource"])
    assert practitioner_read_response.status_code == 200
    assert practitioner_read_response.json()["name"][0]["text"] == "CarlosDoc"
    Practitioner(practitioner_read_response.json())


def test_fhir_practitioner_uses_stable_provider_identity_after_rename() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)
    with app.state.session_factory() as session:
        with session.begin():
            create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                created_by="Dr Before",
                created_by_id="provider-42",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                source_reference="provider-before",
            )
            create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                created_by="Dr After",
                created_by_id="provider-42",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                source_reference="provider-after",
            )

    response = client.get(
        "/fhir/Practitioner",
        headers=bearer_headers(token),
    )

    assert response.status_code == 200
    assert response.json()["total"] == 1
    assert response.json()["entry"][0]["resource"]["name"][0]["text"] == "Dr After"
    Bundle(response.json())
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        dashboard = presenters.assemble_email_password_dashboard(
            session,
            account,
            search=None,
            provider="id:provider-42",
            date_from=None,
            date_to=None,
            page=1,
        )

    assert [row.provider for row in dashboard.rows] == ["Dr After", "Dr Before"]
    assert dashboard.provider_options == (
        ProviderFilterOptionViewModel(value="id:provider-42", label="Dr After"),
    )


def test_fhir_document_reference_read_audits_malformed_and_unknown_ids() -> None:
    """A malformed ID must leave the same audited trail as an unknown one, not silently 404."""
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)

    malformed = client.get(
        "/fhir/DocumentReference/not-a-number",
        headers=bearer_headers(token),
    )
    unknown = client.get(
        "/fhir/DocumentReference/999999",
        headers=bearer_headers(token),
    )

    assert malformed.status_code == 404
    assert unknown.status_code == 404
    with app.state.session_factory() as session:
        read_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == "fhir.read")
                .order_by(PatientPortalAuditEvent.id)
            )
        )
        assert [event.resource_id for event in read_events] == ["not-a-number", "999999"]
        assert all(event.outcome == "failure" for event in read_events)
        assert all(event.reason == "not_found" for event in read_events)


def test_fhir_document_reference_search_pages_all_results_and_uses_canonical_origin() -> None:
    app = migrated_development_app(public_base_url="https://portal.example.test")
    client = TestClient(app, base_url="https://portal.example.test")
    account_id = activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)

    with app.state.session_factory() as session:
        with session.begin():
            for index in range(105):
                create_unlock_secret(
                    session,
                    clinic_id="default",
                    demographic_no=1234,
                    account_id=account_id,
                    secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                    secret=f"PagedSecret{index:03d}!",
                    created_by=f"Provider {index:03d}",
                    created_by_id=f"provider-{index:03d}",
                    encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                    label=f"Paged message {index:03d}",
                    source_reference=f"paged-message-{index:03d}",
                )

    first_page = client.get(
        "/fhir/DocumentReference?_count=100&_offset=0",
        headers=bearer_headers(token),
    )
    second_page = client.get(
        "/fhir/DocumentReference?_count=100&_offset=100",
        headers=bearer_headers(token),
    )

    assert first_page.status_code == 200
    assert first_page.json()["total"] == 105
    assert len(first_page.json()["entry"]) == 100
    assert first_page.json()["link"] == [
        {
            "relation": "self",
            "url": ("https://portal.example.test/fhir/DocumentReference?_count=100&_offset=0"),
        },
        {
            "relation": "next",
            "url": ("https://portal.example.test/fhir/DocumentReference?_count=100&_offset=100"),
        },
    ]
    assert second_page.status_code == 200
    assert second_page.json()["total"] == 105
    assert len(second_page.json()["entry"]) == 5
    assert second_page.json()["link"][1]["relation"] == "previous"
    assert all(
        entry["fullUrl"].startswith("https://portal.example.test/fhir/")
        for entry in first_page.json()["entry"]
    )
    Bundle(first_page.json())
    Bundle(second_page.json())
    assert (
        client.get(
            "/fhir/DocumentReference",
            headers={**bearer_headers(token), "Host": "attacker.example"},
        ).status_code
        == 400
    )

    with app.state.session_factory() as session:
        fhir_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == "fhir.search")
                .order_by(PatientPortalAuditEvent.id)
            )
        )
        assert len(fhir_events) == 2
        assert all(event.resource_type == "DocumentReference" for event in fhir_events)
        provider_options = list_unlock_secret_providers(
            session,
            clinic_id="default",
            demographic_no=1234,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
        )
        assert len(provider_options) == 105
        # The dashboard filter is capped so a lifetime of retained passwords cannot render an
        # unbounded <select>; truncation is reported so the UI can say so.
        dashboard_options = list_unlock_secret_provider_options(
            session,
            clinic_id="default",
            demographic_no=1234,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
        )
        assert len(dashboard_options.options) == MAX_UNLOCK_SECRET_PROVIDER_OPTIONS
        assert dashboard_options.truncated is True


def test_interop_helpers_build_valid_fhir_r4_and_hl7_v251_patient_identity() -> None:
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )

    fhir_patient = build_fhir_r4_patient(identity)
    fhir_practitioner = build_fhir_r4_practitioner(
        clinic_id="default",
        name=" Dr | example\nprovider ",
    )
    hl7_message = build_hl7_v251_patient_registration(
        identity,
        message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
        message_control_id="MSG0001",
    )
    hl7_profile = load_hl7_v251_patient_registration_profile()

    assert FHIR_RELEASE == "R4"
    assert fhir_patient["resourceType"] == "Patient"
    assert fhir_patient["birthDate"] == SEEDED_INVITE_DOB
    assert fhir_patient["name"][0]["family"] == "Patient"
    assert fhir_practitioner["resourceType"] == "Practitioner"
    assert fhir_practitioner["name"][0]["text"] == "Dr | example provider"
    assert fhir_practitioner["id"] == build_fhir_practitioner_id(
        clinic_id="default",
        name=" Dr | example\nprovider ",
    )
    Practitioner(fhir_practitioner)
    assert HL7_V2_VERSION == "2.5.1"
    assert "MSH|^~\\&|CARLOS|default|CARLOSPORTAL|default" in hl7_message
    assert "PID|||1234^^^default^MR~ABCD12345678^^^CARLOSHCN^JHN" in hl7_message
    assert "^NET^Internet^example.patient@example.com" in hl7_message
    assert "ADT^A04^ADT_A01" in hl7_message
    assert validate_hl7_v251_message(hl7_message) == hl7_message
    assert hl7_profile["id"] == HL7_PATIENT_REGISTRATION_PROFILE_ID
    assert hl7_profile["message_structure"] == "ADT_A01"
    assert validate_hl7_v251_patient_registration_profile(hl7_message) == hl7_message


def test_hl7_patient_registration_profile_rejects_nonconforming_messages() -> None:
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )
    hl7_message = build_hl7_v251_patient_registration(
        identity,
        message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
        message_control_id="MSG0001",
    )
    wrong_receiver = hl7_message.replace("CARLOSPORTAL", "OTHERAPP", 1)
    missing_health_card = hl7_message.replace("~ABCD12345678^^^CARLOSHCN^JHN", "", 1)
    missing_visit = hl7_message.replace("\rPV1||O", "", 1)

    with pytest.raises(Hl7ConformanceProfileError, match="MSH-5"):
        validate_hl7_v251_patient_registration_profile(wrong_receiver)
    with pytest.raises(Hl7ConformanceProfileError, match="JHN"):
        validate_hl7_v251_patient_registration_profile(missing_health_card)
    with pytest.raises(Hl7ConformanceProfileError, match="PV1"):
        validate_hl7_v251_patient_registration_profile(missing_visit)


def test_hl7_patient_identity_rejects_unsafe_hl7_values() -> None:
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="clinic-with-a-very-long-id-over-twenty-chars",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )
    email_with_separator = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email="a&b@example.com",
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )

    with pytest.raises(ValueError, match="clinic_id"):
        build_hl7_v251_patient_registration(
            identity,
            message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
            message_control_id="MSG0001",
        )
    with pytest.raises(ValueError, match="email"):
        build_hl7_v251_patient_registration(
            email_with_separator,
            message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
            message_control_id="MSG0001",
        )


def test_hl7_patient_identity_rejects_control_characters_in_names() -> None:
    """NUL is not an HL7 separator, so the separator check alone let it into PID-5.

    A C-based HL7 receiver treats NUL as string termination and silently truncates PID-5 and
    everything after it in that field — a wrong patient name on an identity-registration message.
    The same value serialises into FHIR ``Patient.name.family`` as ``\\u0000``, which PostgreSQL
    ``text`` columns reject outright. Neither is a separator, so the whole C0/C1 range has to go.
    """
    def build_identity(family_name: str) -> PortalPatientInteroperabilityIdentity:
        return PortalPatientInteroperabilityIdentity(
            clinic_id="default",
            demographic_no=1234,
            email=SEEDED_INVITE_EMAIL,
            date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
            health_card_number=SEEDED_INVITE_HCN,
            family_name=family_name,
            given_name="Example",
        )

    # \r is the HL7 segment separator, so it is present in every message by construction. Count
    # only characters this name *added* to the encoding.
    baseline_message = build_hl7_v251_patient_registration(
        build_identity("Patient"),
        message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
        message_control_id="MSG0001",
    )
    control_characters = [chr(code) for code in [*range(0x00, 0x20), *range(0x7F, 0xA0)]]
    survivors: list[str] = []
    for control_character in control_characters:
        identity = PortalPatientInteroperabilityIdentity(
            clinic_id="default",
            demographic_no=1234,
            email=SEEDED_INVITE_EMAIL,
            date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
            health_card_number=SEEDED_INVITE_HCN,
            family_name=f"Pat{control_character}ient",
            given_name="Example",
        )
        # Either outcome is safe: the ones Python treats as whitespace are collapsed to a space
        # before they can reach a field, and the rest — NUL among them — must be refused. What
        # must never happen is one reaching PID-5 or Patient.name.family verbatim.
        try:
            hl7_message = build_hl7_v251_patient_registration(
                identity,
                message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
                message_control_id="MSG0001",
            )
            fhir_patient = build_fhir_r4_patient(identity)
        except ValueError:
            continue
        added_to_hl7 = hl7_message.count(control_character) > baseline_message.count(
            control_character
        )
        if added_to_hl7 or control_character in json.dumps(fhir_patient):
            survivors.append(control_character)

    assert survivors == []
    # NUL specifically: not whitespace, so nothing collapses it, and it terminates a string in a
    # C-based receiver. It has to be an outright refusal.
    with pytest.raises(ValueError, match="family_name"):
        normalize_patient_name_part("Pat\x00ient", "family_name")


def test_hl7_profile_rejects_conflicting_field_repetitions() -> None:
    """A second repetition must not ride along unchecked on a message we certify as conformant.

    ``fixed_fields`` compared only the first repetition, so appending a conflicting one passed.
    A consumer that takes the last PID-3 MR repetition would bind the record to the wrong
    demographic — a cross-patient merge on a message the portal declared conformant.
    """
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )
    hl7_message = build_hl7_v251_patient_registration(
        identity,
        message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
        message_control_id="MSG0001",
    )

    # A second, conflicting MR identifier for a different demographic.
    conflicting_identifier = hl7_message.replace(
        "1234^^^default^MR",
        "1234^^^default^MR~9999^^^default^MR",
        1,
    )
    duplicate_identifier = hl7_message.replace(
        "1234^^^default^MR",
        "1234^^^default^MR~1234^^^default^MR",
        1,
    )
    # A second, unvalidated contact address alongside the patient's own. Every component the
    # profile pins (NET, Internet) is identical, so only a cardinality check catches this.
    conflicting_contact = hl7_message.replace(
        f"^NET^Internet^{SEEDED_INVITE_EMAIL}",
        f"^NET^Internet^{SEEDED_INVITE_EMAIL}~^NET^Internet^attacker@evil.example",
        1,
    )

    assert conflicting_identifier != hl7_message
    assert conflicting_contact != hl7_message
    with pytest.raises(Hl7ConformanceProfileError, match="PID-3"):
        validate_hl7_v251_patient_registration_profile(conflicting_identifier)
    with pytest.raises(Hl7ConformanceProfileError, match="PID-3"):
        validate_hl7_v251_patient_registration_profile(duplicate_identifier)
    with pytest.raises(Hl7ConformanceProfileError, match="PID-13"):
        validate_hl7_v251_patient_registration_profile(conflicting_contact)


def test_oversized_and_malformed_resource_ids_are_audited_not_five_hundreds() -> None:
    """Client-supplied ids must never reach the driver or break the audit write."""
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)

    oversized = [
        client.get(f"/fhir/{resource}/{'a' * 300}", headers=bearer_headers(token))
        for resource in ("Patient", "Organization", "Practitioner", "DocumentReference")
    ]
    control_character = client.get("/fhir/Practitioner/%00", headers=bearer_headers(token))
    huge_numeric = client.get(
        f"/api/patient/email-passwords/{2**63}", headers=bearer_headers(token)
    )

    assert [response.status_code for response in oversized] == [404, 404, 404, 404]
    assert control_character.status_code == 404
    assert huge_numeric.status_code == 422
    with app.state.session_factory() as session:
        read_events = list(
            session.scalars(
                select(PatientPortalAuditEvent).where(
                    PatientPortalAuditEvent.event_type == "fhir.read"
                )
            )
        )
        # Every rejected probe still leaves a trace; over-length ids used to lose the event.
        assert len(read_events) == 5
        assert all(len(event.resource_id or "") <= 128 for event in read_events)


def test_control_characters_are_rejected_on_the_way_in_not_on_every_read() -> None:
    """A provider name with a C1 byte must fail at the write boundary, not poison the reads.

    Starlette decodes header bytes as latin-1, so byte 0x92 - the Windows-1252 right single
    quote, pervasive in legacy EMR name data - arrives as U+0092, a C1 control character.
    normalize_staff_actor accepted it (strip + length only) while the FHIR read path called
    reject_control_characters and raised, so one bad row permanently 500'd that patient's
    entire DocumentReference bundle and every Practitioner read, with no patient-side recovery.

    \\x85 and \\x0b slipped through harmlessly because str.split() treats them as whitespace,
    which is what made the failure intermittent and hard to diagnose.
    """
    with pytest.raises(ValueError, match="control characters"):
        normalize_staff_actor("O\x92Brien")

    # \x85 and \x0b previously slipped past the *read* path only because
    # normalize_patient_name_part collapses them via str.split(); normalize_staff_actor does not
    # split, so at this boundary they are rejected consistently with every other C1 byte.
    for control_character in ("\x85", "\x0b"):
        with pytest.raises(ValueError, match="control characters"):
            normalize_staff_actor(f"Dana{control_character}Brien")

    # Ordinary names, including apostrophes and accents, are unaffected.
    assert normalize_staff_actor("  O'Brien, Dana  ") == "O'Brien, Dana"
    assert normalize_staff_actor("Renée Ó Súilleabháin") == "Renée Ó Súilleabháin"


def test_unexpected_fhir_failures_render_an_operation_outcome() -> None:
    """The CapabilityStatement promises OperationOutcome; a bare 500 is not one."""
    app = migrated_development_app()
    client = TestClient(app, raise_server_exceptions=False)

    # Patched where the route resolves it: routes.fhir imports the name directly.
    with mock.patch.object(
        fhir_routes,
        "build_fhir_r4_capability_statement",
        side_effect=ValueError("stored value is unusable"),
    ):
        response = client.get("/fhir/metadata")

    assert response.status_code == 500
    body = response.json()
    assert body["resourceType"] == "OperationOutcome"
    # The exception text is PHI-adjacent and must not be echoed to the patient.
    assert "stored value is unusable" not in response.text


def test_hl7_conformance_errors_report_shape_never_patient_values() -> None:
    """Conformance errors must not carry PID values.

    check_hl7_fixed_fields embedded them verbatim: for PID-5.1, PID-7, PID-13.4 and the JHN
    identifier those are patient family name, date of birth, email and health card number.
    Harmless only while nothing calls this pipeline - the moment it is wired to a route or a
    logger it emits PHI into an error body or the application log, against CLAUDE.md's rule on
    browser-visible exception messages.
    """
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )
    hl7_message = build_hl7_v251_patient_registration(
        identity,
        message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
        message_control_id="MSG0001",
    )
    tampered = hl7_message.replace("CARLOSPORTAL", "OTHERAPP", 1)

    with pytest.raises(Hl7ConformanceProfileError) as raised:
        validate_hl7_v251_patient_registration_profile(tampered)

    reported = str(raised.value)
    assert "MSH-5" in reported, "the failing path must still be named"
    for patient_value in ("OTHERAPP", "CARLOSPORTAL", SEEDED_INVITE_EMAIL, SEEDED_INVITE_HCN):
        assert patient_value not in reported
    assert "characters" in reported


def test_hl7_message_control_id_respects_the_v251_bound() -> None:
    """MSH-10 is bounded at 20 in v2.5.1; borrowing the 128-char name limit certified a
    message a conforming receiver truncates."""
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )

    with pytest.raises(ValueError, match="20 characters or fewer"):
        build_hl7_v251_patient_registration(
            identity,
            message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
            message_control_id="M" * 21,
        )
