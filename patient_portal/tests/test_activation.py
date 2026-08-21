"""Invite activation: turning an invite plus identity proof into a portal account."""

from datetime import timedelta

from fastapi.testclient import TestClient
from sqlalchemy import select

from carlos_patient_portal import web_support
from carlos_patient_portal.audit import hash_sensitive_reference
from carlos_patient_portal.models import (
    AUDIT_EVENT_ACTIVATION,
    AUDIT_EVENT_INVITE_CREATE,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    AUDIT_OUTCOME_THROTTLED,
    INVITE_STATUS_ACCEPTED,
    INVITE_STATUS_PENDING,
    PatientPortalAccount,
    PatientPortalAuditEvent,
    PatientPortalInvite,
    utc_now,
)
from tests.support import (
    AUDIT_HASH_SECRET,
    NON_DEVELOPMENT_SESSION_SECRET,
    SEEDED_INVITE_DOB,
    SEEDED_INVITE_EMAIL,
    SEEDED_INVITE_HCN,
    STRONG_PASSWORD,
    RecordingPortalSmsSender,
    activation_request,
    csrf_token_from_response,
    dev_admin_headers,
    migrated_development_app,
    seeded_invite_request,
)


def test_browser_activation_form_creates_account_without_repopulating_proof_values() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    invite_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    activation_page = client.get("/auth/activate")

    assert activation_page.status_code == 200
    assert 'type="date"' in activation_page.text
    assert "Activate your account" in activation_page.text

    activation_response = client.post(
        "/auth/activate",
        data={
            "csrf_token": csrf_token_from_response(activation_page),
            "invite_code": invite_response.json()["invite_token"],
            "email": SEEDED_INVITE_EMAIL,
            "date_of_birth": SEEDED_INVITE_DOB,
            "health_card_number": SEEDED_INVITE_HCN,
            "username": "browser.patient",
            "password": STRONG_PASSWORD,
            "password_confirmation": STRONG_PASSWORD,
        },
    )

    assert activation_response.status_code == 201
    assert "Account activated" in activation_response.text
    assert SEEDED_INVITE_HCN not in activation_response.text
    assert invite_response.json()["invite_token"] not in activation_response.text
    with app.state.session_factory() as session:
        account = session.scalar(
            select(PatientPortalAccount).where(PatientPortalAccount.username == "browser.patient")
        )
        assert account is not None


def test_browser_activation_rejects_password_mismatch_without_echoing_secrets() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activation_page = client.get("/auth/activate")

    response = client.post(
        "/auth/activate",
        data={
            "csrf_token": csrf_token_from_response(activation_page),
            "invite_code": "sensitive-invite-code",
            "email": SEEDED_INVITE_EMAIL,
            "date_of_birth": SEEDED_INVITE_DOB,
            "health_card_number": SEEDED_INVITE_HCN,
            "username": "browser.patient",
            "password": STRONG_PASSWORD,
            "password_confirmation": "Different1!word",  # ggignore
        },
    )

    assert response.status_code == 400
    assert "password confirmation does not match" in response.text.lower()
    assert "sensitive-invite-code" not in response.text
    assert SEEDED_INVITE_HCN not in response.text
    assert STRONG_PASSWORD not in response.text


def test_patient_activation_creates_account_from_seeded_invite() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert create_response.status_code == 201
    created_invite = create_response.json()
    invite_id = created_invite["id"]
    invite_token = created_invite["invite_token"]
    assert created_invite["has_identity_proof"] is True

    with app.state.session_factory() as session:
        persisted_invite = session.get(PatientPortalInvite, invite_id)
        assert persisted_invite is not None
        assert persisted_invite.proof_email_hash is not None
        assert persisted_invite.proof_date_of_birth_hash is not None
        assert persisted_invite.proof_health_card_hash is not None
        assert persisted_invite.proof_salt is not None
        assert persisted_invite.proof_email_hash != SEEDED_INVITE_EMAIL
        assert persisted_invite.proof_health_card_hash != SEEDED_INVITE_HCN

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token),
    )

    assert activation_response.status_code == 201
    assert activation_response.json() == {"status": "activated", "username": "patient.user"}
    assert activation_response.headers["cache-control"] == "no-store"

    with app.state.session_factory() as session:
        account = session.scalar(
            select(PatientPortalAccount).where(PatientPortalAccount.username == "patient.user")
        )
        accepted_invite = session.get(PatientPortalInvite, invite_id)

        assert account is not None
        assert account.clinic_id == "default"
        assert account.demographic_no == 1234
        assert account.email == SEEDED_INVITE_EMAIL
        assert account.password_hash.startswith("$argon2id$")
        assert account.password_hash != STRONG_PASSWORD
        assert accepted_invite is not None
        assert accepted_invite.status == INVITE_STATUS_ACCEPTED
        assert accepted_invite.accepted_account_id == account.id
        assert accepted_invite.accepted_at is not None
        audit_events = list(
            session.scalars(select(PatientPortalAuditEvent).order_by(PatientPortalAuditEvent.id))
        )
        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_INVITE_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_ACTIVATION, AUDIT_OUTCOME_SUCCESS),
        ]
        assert audit_events[-1].account_id == account.id
        assert audit_events[-1].invite_id == invite_id

    second_activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, username="another.patient"),
    )

    assert second_activation_response.status_code == 400
    assert second_activation_response.json()["detail"] == (
        "activation details could not be verified"
    )


def test_patient_activation_can_enroll_sms_mfa_when_sender_is_configured() -> None:
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(sms_sender=sms_sender)
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    activated = client.post(
        "/auth/activate",
        json=activation_request(
            create_response.json()["invite_token"],
            mfa_delivery_method="sms",
            phone_number="+1 613 555 0199",
        ),
    )
    login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )

    assert activated.status_code == 201
    assert login.status_code == 200
    assert login.json()["mfa_delivery_method"] == "sms"
    assert sms_sender.messages[-1]["recipient"] == "+16135550199"
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.preferred_mfa_method == "sms"
        assert account.phone_number == "+16135550199"


def test_patient_activation_rejects_sms_when_sender_is_unavailable() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    response = client.post(
        "/auth/activate",
        json=activation_request(
            create_response.json()["invite_token"],
            mfa_delivery_method="sms",
            phone_number="+16135550199",
        ),
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "MFA delivery method is unavailable"
    with app.state.session_factory() as session:
        event = session.scalar(
            select(PatientPortalAuditEvent)
            .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION)
            .order_by(PatientPortalAuditEvent.id.desc())
        )
        assert event is not None
        assert event.outcome == AUDIT_OUTCOME_FAILURE


def test_patient_activation_rejects_identity_mismatch_without_account_leak() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )

    assert activation_response.status_code == 400
    assert activation_response.json()["detail"] == "activation details could not be verified"

    with app.state.session_factory() as session:
        assert session.scalar(select(PatientPortalAccount.id)) is None
        invite = session.scalar(select(PatientPortalInvite))
        audit_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION
            )
        )
        assert invite is not None
        assert invite.status == INVITE_STATUS_PENDING
        assert audit_event is not None
        assert audit_event.outcome == AUDIT_OUTCOME_FAILURE
        assert audit_event.reason == "invalid_details"


def test_patient_activation_rejects_expired_invite() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    created_invite = create_response.json()
    invite_token = created_invite["invite_token"]

    with app.state.session_factory() as session:
        invite = session.get(PatientPortalInvite, created_invite["id"])
        assert invite is not None
        invite.created_at = utc_now() - timedelta(days=8)
        invite.expires_at = utc_now() - timedelta(days=1)
        session.commit()

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token),
    )

    assert activation_response.status_code == 400
    assert activation_response.json()["detail"] == "activation details could not be verified"


def test_patient_activation_rejects_unavailable_username() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    first_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    first_token = first_create_response.json()["invite_token"]
    second_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(
            demographic_no=5678,
            email="second.patient@example.com",
            health_card_number="ZXCV 1234",
        ),
    )
    second_token = second_create_response.json()["invite_token"]

    assert client.post("/auth/activate", json=activation_request(first_token)).status_code == 201

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(
            second_token,
            email="second.patient@example.com",
            health_card_number="ZXCV-1234",
        ),
    )

    assert activation_response.status_code == 409
    assert activation_response.json()["detail"] == "username unavailable"


def test_patient_activation_rejects_weak_password() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(create_response.json()["invite_token"], password="weak"),
    )

    assert activation_response.status_code == 400
    assert "weak" not in activation_response.text


def test_patient_activation_rejects_oversized_json_body() -> None:
    app = migrated_development_app()
    oversized_body = (
        b'{"invite_code":"'
        + b"x" * web_support.MAX_JSON_BODY_BYTES
        + b'","email":"example.patient@example.com"}'
    )

    response = TestClient(app).post(
        "/auth/activate",
        content=oversized_body,
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 413
    assert response.json()["detail"] == "request body too large"


def test_patient_activation_requires_json_body() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/auth/activate",
        data={"invite_code": "unused"},
    )

    # A urlencoded post is a browser form submission, so the rejection renders the portal's
    # page rather than a raw JSON body.
    assert response.status_code == 403
    assert response.headers["content-type"].startswith("text/html")
    assert "Request could not be completed" in response.text


def test_patient_activation_validation_does_not_echo_health_card_number() -> None:
    app = migrated_development_app()
    invalid_health_card_number = "bad card ?"

    response = TestClient(app).post(
        "/auth/activate",
        json=activation_request("unused", health_card_number=invalid_health_card_number),
    )

    assert response.status_code == 400
    assert invalid_health_card_number not in response.text


def test_patient_activation_rejects_too_short_health_card_number() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/auth/activate",
        json=activation_request("unused", health_card_number="A1"),
    )

    assert response.status_code == 400


def test_activation_schema_failures_consume_the_failure_budget() -> None:
    app = migrated_development_app(
        activation_max_failures_per_invite=2,
        activation_max_failures_per_client=50,
    )
    client = TestClient(app)
    malformed = activation_request("", health_card_number="A1")

    responses = [client.post("/auth/activate", json=malformed) for _ in range(3)]

    assert [response.status_code for response in responses] == [400, 400, 429]
    with app.state.session_factory() as session:
        outcomes = list(
            session.scalars(
                select(PatientPortalAuditEvent.outcome)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION)
                .order_by(PatientPortalAuditEvent.id)
            )
        )
    assert outcomes == [AUDIT_OUTCOME_FAILURE, AUDIT_OUTCOME_FAILURE, AUDIT_OUTCOME_THROTTLED]


def test_patient_activation_rate_limits_failed_attempts() -> None:
    app = migrated_development_app(
        session_secret=NON_DEVELOPMENT_SESSION_SECRET,
        activation_max_failures_per_invite=2,
        activation_max_failures_per_client=50,
    )
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]

    for _ in range(2):
        response = client.post(
            "/auth/activate",
            json=activation_request(invite_token, health_card_number="WRONG1234"),
        )
        assert response.status_code == 400

    throttled_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )

    assert throttled_response.status_code == 429
    assert throttled_response.headers["retry-after"] == "3600"
    expected_client_hash = hash_sensitive_reference(
        AUDIT_HASH_SECRET,
        "activation_client",
        "testclient",
    )
    with app.state.session_factory() as session:
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION)
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert [event.outcome for event in audit_events] == [
            AUDIT_OUTCOME_FAILURE,
            AUDIT_OUTCOME_FAILURE,
            AUDIT_OUTCOME_THROTTLED,
        ]
        assert all(event.client_reference_hash == expected_client_hash for event in audit_events)


def test_patient_activation_rate_limit_ignores_header_from_untrusted_peer() -> None:
    app = migrated_development_app(
        session_secret=NON_DEVELOPMENT_SESSION_SECRET,
        trusted_client_ip_header="x-forwarded-for",
        trusted_proxy_cidrs="10.0.0.0/8",
    )
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]
    response = client.post(
        "/auth/activate",
        headers={"X-Forwarded-For": "203.0.113.7, 10.0.0.10"},
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )

    assert response.status_code == 400
    expected_client_hash = hash_sensitive_reference(
        AUDIT_HASH_SECRET,
        "activation_client",
        "testclient",
    )
    with app.state.session_factory() as session:
        audit_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION
            )
        )
        assert audit_event is not None
        assert audit_event.client_reference_hash == expected_client_hash


def test_patient_activation_rate_limit_window_expires() -> None:
    app = migrated_development_app(
        activation_failure_window_seconds=60,
        activation_max_failures_per_invite=1,
        activation_max_failures_per_client=50,
    )
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]
    failed_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )
    assert failed_response.status_code == 400

    with app.state.session_factory() as session:
        audit_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION
            )
        )
        assert audit_event is not None
        audit_event.created_at = utc_now() - timedelta(minutes=2)
        session.commit()

    activation_response = client.post("/auth/activate", json=activation_request(invite_token))

    assert activation_response.status_code == 201
