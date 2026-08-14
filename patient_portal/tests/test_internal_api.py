from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from carlos_patient_portal import internal_routes
from carlos_patient_portal.account_settings import confirm_email_change, update_account_contact
from carlos_patient_portal.config import MIN_PRODUCTION_SECRET_LENGTH, Settings
from carlos_patient_portal.credentials import hash_password
from carlos_patient_portal.database import Base
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.invites import create_invite
from carlos_patient_portal.main import create_app
from carlos_patient_portal.models import (
    AUDIT_EVENT_ACCOUNT_UNLOCK,
    AUDIT_EVENT_INVITE_CREATE,
    AUDIT_EVENT_STAFF_ACTION,
    AUDIT_EVENT_UNLOCK_SECRET_CREATE,
    AUDIT_EVENT_UNLOCK_SECRET_PUBLISH,
    AUDIT_EVENT_UNLOCK_SECRET_READ,
    AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
    PatientPortalAccount,
    PatientPortalAuditEvent,
    PatientPortalContactReviewRequest,
    PatientPortalInvite,
    PatientPortalPasswordResetToken,
    PatientPortalSession,
    PatientPortalUnlockSecret,
    utc_now,
)

INTERNAL_API_TOKEN = "c" * MIN_PRODUCTION_SECRET_LENGTH
IDENTITY_PROOF_SECRET = "i" * MIN_PRODUCTION_SECRET_LENGTH
AUDIT_HASH_SECRET = "a" * MIN_PRODUCTION_SECRET_LENGTH
UNLOCK_SECRET = "u" * MIN_PRODUCTION_SECRET_LENGTH
PASSWORD = "Stronger1!word"
EMAIL_CHANGE_TOKEN_SECRET = "e" * MIN_PRODUCTION_SECRET_LENGTH


def apply_contact_change(
    session,
    account: PatientPortalAccount,
    *,
    email: str,
    phone_number: str | None = None,
    clinic_id: str = "clinic-a",
) -> PatientPortalContactReviewRequest:
    """Run a contact change end to end, the way a patient does.

    An email change is a two-step flow — request, then confirm from the new mailbox — and the
    CARLOS review these tests operate on is only created by the second step.
    """
    contact_update = update_account_contact(
        session,
        account,
        current_password=PASSWORD,
        email=email,
        phone_number=phone_number,
        max_failed_password_attempts=10,
        email_change_token_secret=EMAIL_CHANGE_TOKEN_SECRET,
        email_change_token_ttl=timedelta(days=1),
    )
    assert contact_update.confirmation_token is not None
    confirmation = confirm_email_change(
        session,
        confirmation_token=contact_update.confirmation_token,
        token_secret=EMAIL_CHANGE_TOKEN_SECRET,
        clinic_id=clinic_id,
    )
    return confirmation.review_request


def internal_app(**overrides: object):
    app = create_app(
        Settings(
            environment="development",
            clinic_id="clinic-a",
            clinic_name="Clinic A",
            database_url="sqlite+pysqlite:///:memory:",
            internal_api_token=INTERNAL_API_TOKEN,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            unlock_secret_encryption_secret=UNLOCK_SECRET,
            **overrides,
        )
    )
    Base.metadata.create_all(app.state.database_engine)
    return app


def carlos_headers(
    *permissions: str,
    clinic_id: str = "clinic-a",
    token: str = INTERNAL_API_TOKEN,
) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {token}",
        "X-CARLOS-Provider-ID": "provider-42",
        "X-CARLOS-Provider-Name": "CarlosDoc",
        "X-CARLOS-Clinic-ID": clinic_id,
        "X-CARLOS-Permissions": ",".join(permissions),
    }


def invite_request(demographic_no: int = 1234) -> dict[str, object]:
    return {
        "demographic_no": demographic_no,
        "email": "example.patient@example.com",
        "date_of_birth": "1980-05-20",
        "health_card_number": "ABCD 1234-5678",
    }


def test_internal_api_rejects_a_non_ascii_bearer_token_without_a_server_error() -> None:
    """A byte >= 0x80 in the Authorization header must fail closed, not 500.

    Starlette latin-1 decodes header bytes, and `compare_digest` raises TypeError on str operands
    outside ASCII. Comparing str therefore turned an unauthenticated request into a 500, which
    tells the caller its token reached the comparison at all. Sent as raw bytes because that is
    what a real client puts on the wire.
    """
    app = internal_app()
    client = TestClient(app, raise_server_exceptions=False)

    response = client.get(
        "/internal/carlos/contact-reviews",
        headers={
            b"Authorization": b"Bearer tok\xe9n",
            b"X-CARLOS-Provider-ID": b"p1",
            b"X-CARLOS-Provider-Name": b"P",
            b"X-CARLOS-Clinic-ID": b"clinic-a",
            b"X-CARLOS-Permissions": b"portal.contact.review",
        },
    )

    assert response.status_code == 404


def test_internal_api_requires_service_authentication_and_permission() -> None:
    client = TestClient(internal_app())

    missing_auth = client.post(
        "/internal/carlos/patients/1234/invites",
        json=invite_request(),
    )
    wrong_token = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage", token="x" * 32),
        json=invite_request(),
    )
    missing_permission = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.account.unlock"),
        json=invite_request(),
    )

    assert missing_auth.status_code == 404
    assert wrong_token.status_code == 404
    assert missing_permission.status_code == 403
    with client.app.state.session_factory() as session:
        failures = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_STAFF_ACTION)
                .order_by(PatientPortalAuditEvent.id)
            )
        )
        assert [event.reason for event in failures] == [
            "authentication_failed",
            "authentication_failed",
            "authorization_failed",
        ]


def test_internal_mutations_reject_unknown_or_blank_fields_and_publish_schemas() -> None:
    client = TestClient(internal_app())
    invite_payload = {**invite_request(), "unexpected": "value"}

    extra_field = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_payload,
    )
    blank_reason = client.post(
        "/internal/carlos/patients/1234/portal-account/access",
        headers=carlos_headers("portal.account.manage"),
        json={"enabled": False, "reason": "  "},
    )
    typo = client.post(
        "/internal/carlos/patients/1234/portal-account/access",
        headers=carlos_headers("portal.account.manage"),
        json={"enabled": False, "reasno": "staff request"},
    )
    openapi = client.get("/api/openapi.json").json()

    assert extra_field.status_code == 422
    assert blank_reason.status_code == 422
    assert typo.status_code == 422
    invite_operation = openapi["paths"]["/internal/carlos/patients/{demographic_no}/invites"][
        "post"
    ]
    assert invite_operation["responses"]["201"]["content"]["application/json"]["schema"]
    access_operation = openapi["paths"][
        "/internal/carlos/patients/{demographic_no}/portal-account/access"
    ]["post"]
    assert access_operation["responses"]["200"]["content"]["application/json"]["schema"]


def test_maintenance_mode_blocks_internal_business_mutations() -> None:
    client = TestClient(internal_app(maintenance_mode=True))

    mutation = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )
    health = client.get("/health")

    assert mutation.status_code == 503
    assert health.status_code == 200


def test_internal_invite_records_stable_provider_identity() -> None:
    app = internal_app()
    client = TestClient(app)

    response = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )

    assert response.status_code == 201
    assert response.json()["invite_token"]
    with app.state.session_factory() as session:
        invite = session.scalar(select(PatientPortalInvite))
        audit = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_INVITE_CREATE
            )
        )
        assert invite is not None
        assert invite.created_by_id == "provider-42"
        assert invite.created_by == "CarlosDoc"
        assert audit is not None
        assert audit.actor_id == "provider-42"
        assert audit.actor == "CarlosDoc"


def test_internal_invite_list_resend_and_revoke_lifecycle() -> None:
    app = internal_app()
    client = TestClient(app)
    headers = carlos_headers("portal.invite.manage")
    created = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=headers,
        json=invite_request(),
    )
    invite_id = created.json()["id"]

    listed = client.get(
        "/internal/carlos/patients/1234/invites",
        headers=headers,
    )
    resent = client.post(
        f"/internal/carlos/invites/{invite_id}/resend",
        headers=headers,
    )
    resent_id = resent.json()["id"]
    revoked = client.post(
        f"/internal/carlos/invites/{resent_id}/revoke",
        headers=headers,
    )
    rejected_resend = client.post(
        f"/internal/carlos/invites/{resent_id}/resend",
        headers=headers,
    )
    missing_revoke = client.post(
        "/internal/carlos/invites/999999/revoke",
        headers=headers,
    )

    assert listed.status_code == 200
    assert [item["id"] for item in listed.json()] == [invite_id]
    assert resent.status_code == 200
    assert resent_id != invite_id
    assert resent.json()["supersedes_invite_id"] == invite_id
    assert resent.json()["invite_token"] != created.json()["invite_token"]
    assert revoked.status_code == 200
    assert revoked.json()["status"] == "revoked"
    assert rejected_resend.status_code == 409
    assert missing_revoke.status_code == 404


def test_internal_unlock_secret_is_idempotent_scoped_and_target_audited() -> None:
    app = internal_app()
    client = TestClient(app)
    request = {
        "source_reference": "email-message-123",
        "label": "Care plan",
        "secret_type": "email",
    }
    headers = carlos_headers("portal.secret.manage")

    created = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=headers,
        json=request,
    )
    repeated = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=headers,
        json=request,
    )
    published = client.post(
        f"/internal/carlos/unlock-secrets/{created.json()['id']}/publish",
        headers=headers,
    )
    cross_patient_replay = client.post(
        "/internal/carlos/patients/5678/unlock-secrets",
        headers=headers,
        json=request,
    )
    cross_clinic_revoke = client.post(
        f"/internal/carlos/unlock-secrets/{created.json()['id']}/revoke",
        headers=carlos_headers("portal.secret.manage", clinic_id="clinic-b"),
        json={"reason": "message_recalled"},
    )
    revoked = client.post(
        f"/internal/carlos/unlock-secrets/{created.json()['id']}/revoke",
        headers=headers,
        json={"reason": "message_recalled"},
    )

    assert created.status_code == 201
    assert created.json()["created"] is True
    assert created.json()["status"] == "pending"
    assert repeated.status_code == 201
    assert repeated.json()["created"] is False
    assert repeated.json()["secret"] == created.json()["secret"]
    assert published.status_code == 200
    assert published.json()["status"] == "available"
    assert cross_patient_replay.status_code == 409
    assert cross_clinic_revoke.status_code == 404
    assert revoked.status_code == 200
    with app.state.session_factory() as session:
        secret = session.get(PatientPortalUnlockSecret, created.json()["id"])
        events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type.in_(
                        {
                            AUDIT_EVENT_UNLOCK_SECRET_CREATE,
                            AUDIT_EVENT_UNLOCK_SECRET_PUBLISH,
                            AUDIT_EVENT_UNLOCK_SECRET_READ,
                            AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
                        }
                    )
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )
        assert secret is not None
        assert secret.created_by_id == "provider-42"
        assert secret.revoked_by_id == "provider-42"
        assert [(event.resource_type, event.resource_id) for event in events] == [
            ("unlock_secret", str(secret.id)),
            ("unlock_secret", str(secret.id)),
            ("unlock_secret", str(secret.id)),
            ("unlock_secret", str(secret.id)),
        ]


@pytest.mark.parametrize("force_integrity_race", [False, True])
def test_internal_unlock_secret_retry_decryption_failure_is_stable_and_audited(
    monkeypatch: pytest.MonkeyPatch,
    force_integrity_race: bool,
) -> None:
    app = internal_app()
    client = TestClient(app)
    headers = carlos_headers("portal.secret.manage")
    payload = {
        "source_reference": "corrupted-idempotent-retry",
        "secret_type": "email",
    }
    created = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=headers,
        json=payload,
    )
    secret_id = created.json()["id"]
    with app.state.session_factory() as session:
        secret = session.get(PatientPortalUnlockSecret, secret_id)
        assert secret is not None
        secret.encrypted_secret = bytes(len(secret.encrypted_secret))
        session.commit()

    if force_integrity_race:
        monkeypatch.setattr(
            internal_routes,
            "get_unlock_secret_by_source_reference",
            lambda *args, **kwargs: None,
        )

        def raise_integrity_error(*args, **kwargs):
            raise IntegrityError("insert", {}, RuntimeError("simulated uniqueness race"))

        monkeypatch.setattr(internal_routes, "create_unlock_secret", raise_integrity_error)

    repeated = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=headers,
        json=payload,
    )

    assert repeated.status_code == 503
    assert repeated.json() == {"detail": "unlock secret is temporarily unavailable"}
    assert app.state.operational_metrics.snapshot()["failures"]["unlock_secret_decryption"] == 1
    with app.state.session_factory() as session:
        failure = session.scalar(
            select(PatientPortalAuditEvent)
            .where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_UNLOCK_SECRET_READ,
                PatientPortalAuditEvent.outcome == "failure",
                PatientPortalAuditEvent.resource_id == str(secret_id),
            )
            .order_by(PatientPortalAuditEvent.id.desc())
        )
        assert failure is not None
        assert failure.actor_id == "provider-42"
        assert failure.reason == "decryption_failed"


def test_internal_unlock_secret_rejects_caller_plaintext_and_pdf_type() -> None:
    client = TestClient(internal_app())
    headers = carlos_headers("portal.secret.manage")

    supplied_secret = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=headers,
        json={
            "source_reference": "weak-message",
            "secret_type": "email",
            "secret": "x",
        },
    )
    pdf_type = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=headers,
        json={
            "source_reference": "pdf-message",
            "secret_type": "pdf",
        },
    )

    assert supplied_secret.status_code == 422
    assert pdf_type.status_code == 422


def test_internal_unlock_secret_is_hidden_until_carlos_publishes_it() -> None:
    app = internal_app()
    client = TestClient(app)
    invite = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )
    client.post(
        "/auth/activate",
        json={
            "invite_code": invite.json()["invite_token"],
            "email": "example.patient@example.com",
            "date_of_birth": "1980-05-20",
            "health_card_number": "ABCD 1234-5678",
            "username": "patient.user",
            "password": PASSWORD,
        },
    )
    login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": PASSWORD},
    )
    verified = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": login.json()["mfa_challenge_token"],
            "code": login.json()["development_mfa_code"],
        },
    )
    patient_headers = {
        "Authorization": f"Bearer {verified.json()['session_token']}",
    }
    created = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=carlos_headers("portal.secret.manage"),
        json={"source_reference": "delivery-pending", "secret_type": "email"},
    )
    before_publish = client.get(
        "/api/patient/email-passwords",
        headers=patient_headers,
    )
    published = client.post(
        f"/internal/carlos/unlock-secrets/{created.json()['id']}/publish",
        headers=carlos_headers("portal.secret.manage"),
    )
    after_publish = client.get(
        "/api/patient/email-passwords",
        headers=patient_headers,
    )

    assert before_publish.json()["items"] == []
    assert published.json()["status"] == "available"
    assert [item["id"] for item in after_publish.json()["items"]] == [created.json()["id"]]


def test_pending_unlock_secret_cannot_be_retrieved_by_id_before_publication() -> None:
    """A known/guessed pending ID must not disclose a passphrase for an unsent message."""
    app = internal_app()
    client = TestClient(app)
    invite = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )
    client.post(
        "/auth/activate",
        json={
            "invite_code": invite.json()["invite_token"],
            "email": "example.patient@example.com",
            "date_of_birth": "1980-05-20",
            "health_card_number": "ABCD 1234-5678",
            "username": "patient.user",
            "password": PASSWORD,
        },
    )
    login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": PASSWORD},
    )
    verified = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": login.json()["mfa_challenge_token"],
            "code": login.json()["development_mfa_code"],
        },
    )
    patient_headers = {"Authorization": f"Bearer {verified.json()['session_token']}"}
    created = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=carlos_headers("portal.secret.manage"),
        json={"source_reference": "delivery-pending", "secret_type": "email"},
    )
    secret_id = created.json()["id"]

    before_publish = client.get(
        f"/api/patient/email-passwords/{secret_id}",
        headers=patient_headers,
    )
    client.post(
        f"/internal/carlos/unlock-secrets/{secret_id}/publish",
        headers=carlos_headers("portal.secret.manage"),
    )
    after_publish = client.get(
        f"/api/patient/email-passwords/{secret_id}",
        headers=patient_headers,
    )

    assert created.json()["status"] == "pending"
    # Indistinguishable from revoked/not-found, so the ID space leaks nothing.
    assert before_publish.status_code == 404
    assert before_publish.json() == {"detail": "email password not found"}
    assert after_publish.status_code == 200
    assert after_publish.json()["passphrase"] == created.json()["secret"]


def test_internal_retry_still_reads_its_own_pending_unlock_secret() -> None:
    """The pending read guard must not break idempotent CARLOS create retries."""
    app = internal_app()
    client = TestClient(app)
    payload = {"source_reference": "delivery-retry", "secret_type": "email"}
    created = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=carlos_headers("portal.secret.manage"),
        json=payload,
    )
    retried = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=carlos_headers("portal.secret.manage"),
        json=payload,
    )

    assert created.status_code == 201
    assert created.json()["created"] is True
    assert retried.json()["created"] is False
    assert retried.json()["id"] == created.json()["id"]
    assert retried.json()["secret"] == created.json()["secret"]
    assert retried.json()["status"] == "pending"


def test_foreign_clinic_invite_cannot_be_activated_through_this_runtime() -> None:
    """A Clinic B invite must not be redeemable under Clinic A branding/origin."""
    app = internal_app()
    client = TestClient(app)
    # The internal API is already clinic-locked, so a foreign invite can only reach this runtime
    # through a database shared with another clinic's portal instance.
    with app.state.session_factory() as session:
        _, foreign_token = create_invite(
            session,
            1234,
            "Clinic B Staff",
            clinic_id="clinic-b",
            identity_proof=IdentityProof(
                email="example.patient@example.com",
                date_of_birth=date(1980, 5, 20),
                health_card_number="ABCD 1234-5678",
            ),
            proof_secret=IDENTITY_PROOF_SECRET,
        )
        session.commit()

    activation = client.post(
        "/auth/activate",
        json={
            "invite_code": foreign_token,
            "email": "example.patient@example.com",
            "date_of_birth": "1980-05-20",
            "health_card_number": "ABCD 1234-5678",
            "username": "patient.user",
            "password": PASSWORD,
        },
    )

    assert activation.status_code == 400
    with app.state.session_factory() as session:
        assert session.scalars(select(PatientPortalAccount)).all() == []


def test_password_reset_does_not_cross_clinic_boundaries() -> None:
    """Clinic A must neither issue nor redeem a reset for a Clinic B account."""
    app = internal_app()
    client = TestClient(app)
    with app.state.session_factory() as session:
        session.add(
            PatientPortalAccount(
                clinic_id="clinic-b",
                demographic_no=4321,
                username="foreign.patient",
                email="foreign.patient@example.com",
                password_hash=hash_password(PASSWORD),
                status="active",
            )
        )
        session.commit()

    reset_request = client.post(
        "/auth/password-reset/request",
        json={"username": "foreign.patient", "email": "foreign.patient@example.com"},
    )

    # The external response stays generic; only the absence of a token proves the scope check.
    assert reset_request.status_code == 202
    assert reset_request.json()["development_reset_token"] is None
    with app.state.session_factory() as session:
        assert session.scalars(select(PatientPortalPasswordResetToken)).all() == []


def test_internal_staff_unlock_forces_fresh_password_reset() -> None:
    app = internal_app()
    client = TestClient(app)
    invite = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )
    activation = client.post(
        "/auth/activate",
        json={
            "invite_code": invite.json()["invite_token"],
            "email": "example.patient@example.com",
            "date_of_birth": "1980-05-20",
            "health_card_number": "ABCD 1234-5678",
            "username": "patient.user",
            "password": PASSWORD,
        },
    )
    assert activation.status_code == 201
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        account.locked_at = utc_now()
        account.locked_by = "security-policy"
        account.locked_by_id = "security-policy"
        session.commit()

    response = client.post(
        "/internal/carlos/patients/1234/unlock",
        headers=carlos_headers("portal.account.unlock"),
    )

    assert response.status_code == 200
    assert response.json()["force_password_reset"] is True
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        audit = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_UNLOCK
            )
        )
        assert account is not None
        assert account.locked_at is None
        assert account.locked_by_id is None
        assert audit is not None
        assert audit.actor_id == "provider-42"


def test_internal_staff_can_disable_and_reenable_portal_access() -> None:
    app = internal_app()
    client = TestClient(app)
    invite = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )
    client.post(
        "/auth/activate",
        json={
            "invite_code": invite.json()["invite_token"],
            "email": "example.patient@example.com",
            "date_of_birth": "1980-05-20",
            "health_card_number": "ABCD 1234-5678",
            "username": "patient.user",
            "password": PASSWORD,
        },
    )
    headers = carlos_headers("portal.account.manage")

    initial = client.get(
        "/internal/carlos/patients/1234/portal-account",
        headers=headers,
    )
    disabled = client.post(
        "/internal/carlos/patients/1234/portal-account/access",
        headers=headers,
        json={"enabled": False, "reason": "patient_requested"},
    )
    disabled_login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": PASSWORD},
    )
    cross_clinic = client.post(
        "/internal/carlos/patients/1234/portal-account/access",
        headers=carlos_headers("portal.account.manage", clinic_id="clinic-b"),
        json={"enabled": True, "reason": "staff_reviewed"},
    )
    enabled = client.post(
        "/internal/carlos/patients/1234/portal-account/access",
        headers=headers,
        json={"enabled": True, "reason": "staff_reviewed"},
    )

    assert initial.json()["status"] == "active"
    assert disabled.json()["status"] == "disabled"
    assert disabled_login.status_code == 401
    assert cross_clinic.status_code == 404
    assert enabled.json() == {
        "id": initial.json()["id"],
        "status": "active",
        "force_password_reset": True,
    }


def test_internal_contact_review_is_clinic_scoped_and_applies_staff_decision() -> None:
    app = internal_app()
    client = TestClient(app)
    invite = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )
    activation = client.post(
        "/auth/activate",
        json={
            "invite_code": invite.json()["invite_token"],
            "email": "example.patient@example.com",
            "date_of_birth": "1980-05-20",
            "health_card_number": "ABCD 1234-5678",
            "username": "patient.user",
            "password": PASSWORD,
        },
    )
    assert activation.status_code == 201
    with app.state.session_factory() as session:
        with session.begin():
            account = session.scalar(select(PatientPortalAccount))
            assert account is not None
            review = apply_contact_change(
                session,
                account,
                email="updated.patient@example.com",
                phone_number="+16135550199",
            )
            review_id = review.id
            review_revision = review.revision

    cross_clinic = client.get(
        "/internal/carlos/contact-reviews",
        headers=carlos_headers("portal.contact.review", clinic_id="clinic-b"),
    )
    pending = client.get(
        "/internal/carlos/contact-reviews",
        headers=carlos_headers("portal.contact.review"),
    )
    approved = client.post(
        f"/internal/carlos/contact-reviews/{review_id}/decision",
        headers=carlos_headers("portal.contact.review"),
        json={"approve": True, "revision": review_revision},
    )
    replay = client.post(
        f"/internal/carlos/contact-reviews/{review_id}/decision",
        headers=carlos_headers("portal.contact.review"),
        json={"approve": True, "revision": review_revision},
    )

    assert cross_clinic.status_code == 404
    assert pending.status_code == 200
    assert [item["id"] for item in pending.json()["items"]] == [review_id]
    assert approved.status_code == 200
    assert approved.json()["decision"] == "approved"
    assert replay.status_code == 200
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.email == "updated.patient@example.com"
        assert account.phone_number == "+16135550199"


def test_internal_contact_review_rejection_retains_current_contact() -> None:
    app = internal_app()
    client = TestClient(app)
    invite = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )
    client.post(
        "/auth/activate",
        json={
            "invite_code": invite.json()["invite_token"],
            "email": "example.patient@example.com",
            "date_of_birth": "1980-05-20",
            "health_card_number": "ABCD 1234-5678",
            "username": "patient.user",
            "password": PASSWORD,
        },
    )
    with app.state.session_factory() as session:
        with session.begin():
            account = session.scalar(select(PatientPortalAccount))
            assert account is not None
            review = apply_contact_change(
                session,
                account,
                email="rejected.patient@example.com",
            )
            review_id = review.id
            review_revision = review.revision

    rejected = client.post(
        f"/internal/carlos/contact-reviews/{review_id}/decision",
        headers=carlos_headers("portal.contact.review"),
        json={"approve": False, "revision": review_revision},
    )

    assert rejected.status_code == 200
    assert rejected.json()["decision"] == "rejected"
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.email == "rejected.patient@example.com"


def test_internal_contact_review_rejects_superseded_revision() -> None:
    app = internal_app()
    client = TestClient(app)
    invite = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )
    client.post(
        "/auth/activate",
        json={
            "invite_code": invite.json()["invite_token"],
            "email": "example.patient@example.com",
            "date_of_birth": "1980-05-20",
            "health_card_number": "ABCD 1234-5678",
            "username": "patient.user",
            "password": PASSWORD,
        },
    )
    with app.state.session_factory() as session:
        with session.begin():
            account = session.scalar(select(PatientPortalAccount))
            assert account is not None
            first = apply_contact_change(
                session,
                account,
                email="first.patient@example.com",
            )
            apply_contact_change(
                session,
                account,
                email="second.patient@example.com",
            )
            first_id = first.id
            first_revision = first.revision

    stale = client.post(
        f"/internal/carlos/contact-reviews/{first_id}/decision",
        headers=carlos_headers("portal.contact.review"),
        json={"approve": True, "revision": first_revision},
    )

    assert stale.status_code == 409
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.email == "second.patient@example.com"


def test_internal_contact_review_feed_pages_beyond_one_hundred_requests() -> None:
    app = internal_app()
    client = TestClient(app)
    now = utc_now()
    password_hash = hash_password(PASSWORD)
    with app.state.session_factory() as session:
        with session.begin():
            accounts = [
                PatientPortalAccount(
                    clinic_id="clinic-a",
                    demographic_no=10_000 + index,
                    username=f"review.patient.{index}",
                    email=f"review.patient.{index}@example.com",
                    preferred_mfa_method="email",
                    password_hash=password_hash,
                    status="active",
                    created_at=now,
                    updated_at=now,
                    password_updated_at=now,
                )
                for index in range(101)
            ]
            session.add_all(accounts)
            session.flush()
            session.add_all(
                [
                    PatientPortalContactReviewRequest(
                        account_id=account.id,
                        clinic_id=account.clinic_id,
                        demographic_no=account.demographic_no,
                        status="pending",
                        revision=f"review-revision-{index}",
                        email_before=account.email,
                        email_after=f"updated.{account.email}",
                        requested_at=now,
                    )
                    for index, account in enumerate(accounts)
                ]
            )

    first_page = client.get(
        "/internal/carlos/contact-reviews",
        headers=carlos_headers("portal.contact.review"),
        params={"limit": 100, "offset": 0},
    )
    second_page = client.get(
        "/internal/carlos/contact-reviews",
        headers=carlos_headers("portal.contact.review"),
        params={"limit": 100, "offset": 100},
    )

    assert first_page.status_code == 200
    assert len(first_page.json()["items"]) == 100
    assert first_page.json()["total"] == 101
    assert first_page.json()["next_offset"] == 100
    assert second_page.status_code == 200
    assert len(second_page.json()["items"]) == 1
    assert second_page.json()["total"] == 101
    assert second_page.json()["next_offset"] is None


# (method, path, body, required permission). Kept in lockstep with the registered internal routes
# by test_internal_route_permission_manifest_covers_every_route below, so a new CARLOS endpoint
# fails the build until its authorization expectation is declared here.
# (method, path, required permission). No request bodies are needed: authorization now runs in
# the dependency phase, so a caller lacking the permission is rejected before the request model is
# ever validated. That ordering is itself asserted below.
INTERNAL_ROUTE_PERMISSIONS = (
    ("POST", "/internal/carlos/patients/1234/invites", "portal.invite.manage"),
    ("GET", "/internal/carlos/patients/1234/invites", "portal.invite.manage"),
    ("POST", "/internal/carlos/invites/1/resend", "portal.invite.manage"),
    ("POST", "/internal/carlos/invites/1/revoke", "portal.invite.manage"),
    ("POST", "/internal/carlos/patients/1234/unlock", "portal.account.unlock"),
    ("GET", "/internal/carlos/patients/1234/portal-account", "portal.account.manage"),
    ("POST", "/internal/carlos/patients/1234/portal-account/access", "portal.account.manage"),
    ("POST", "/internal/carlos/patients/1234/unlock-secrets", "portal.secret.manage"),
    ("POST", "/internal/carlos/unlock-secrets/1/publish", "portal.secret.manage"),
    ("POST", "/internal/carlos/unlock-secrets/1/revoke", "portal.secret.manage"),
    ("GET", "/internal/carlos/contact-reviews", "portal.contact.review"),
    ("POST", "/internal/carlos/contact-reviews/1/decision", "portal.contact.review"),
)
UNRELATED_PERMISSION = "portal.something.else"


def test_internal_openapi_contract_is_stable() -> None:
    """Pin the CARLOS-facing contract so a registrar refactor cannot move it silently.

    The routes are registered by four per-domain registrars now. That split was intended to be
    behaviour-free, and this is what makes "behaviour-free" checkable: paths, methods, status
    codes, and response model names are the contract CARLOS integrates against.
    """
    app = internal_app()
    paths = {
        path: sorted(operations)
        for path, operations in app.openapi()["paths"].items()
        if path.startswith("/internal/carlos/")
    }

    assert paths == {
        "/internal/carlos/contact-reviews": ["get"],
        "/internal/carlos/contact-reviews/{review_request_id}/decision": ["post"],
        "/internal/carlos/invites/{invite_id}/resend": ["post"],
        "/internal/carlos/invites/{invite_id}/revoke": ["post"],
        "/internal/carlos/patients/{demographic_no}/invites": ["get", "post"],
        "/internal/carlos/patients/{demographic_no}/portal-account": ["get"],
        "/internal/carlos/patients/{demographic_no}/portal-account/access": ["post"],
        "/internal/carlos/patients/{demographic_no}/unlock": ["post"],
        "/internal/carlos/patients/{demographic_no}/unlock-secrets": ["post"],
        "/internal/carlos/unlock-secrets/{unlock_secret_id}/publish": ["post"],
        "/internal/carlos/unlock-secrets/{unlock_secret_id}/revoke": ["post"],
    }


def test_internal_route_permission_manifest_covers_every_route() -> None:
    """A newly added internal route must declare its authorization expectation."""
    app = internal_app()
    registered = {
        (method, route.path)
        for route in app.routes
        if getattr(route, "path", "").startswith("/internal/carlos")
        for method in sorted(getattr(route, "methods", set()) - {"HEAD", "OPTIONS"})
    }
    declared = {
        (method, path.replace("1234", "{demographic_no}"))
        for method, path, _ in INTERNAL_ROUTE_PERMISSIONS
    }
    normalized_declared = {
        (
            method,
            path.replace("/invites/1/", "/invites/{invite_id}/")
            .replace("/unlock-secrets/1/", "/unlock-secrets/{unlock_secret_id}/")
            .replace("/contact-reviews/1/", "/contact-reviews/{review_request_id}/"),
        )
        for method, path in declared
    }

    assert normalized_declared == registered


@pytest.mark.parametrize(("method", "path", "permission"), INTERNAL_ROUTE_PERMISSIONS)
def test_internal_route_rejects_a_caller_without_its_permission(
    method: str, path: str, permission: str
) -> None:
    """Holding some other permission must never satisfy a route's own requirement.

    No body is sent deliberately: authorization must reject the caller before request-model
    validation could turn this into a 422.
    """
    app = internal_app()
    client = TestClient(app)

    response = client.request(
        method,
        path,
        headers=carlos_headers(UNRELATED_PERMISSION),
    )

    assert response.status_code == 403, f"{method} {path} accepted an unrelated permission"
    assert response.json() == {"detail": "permission denied"}
    with app.state.session_factory() as session:
        failures = [
            event.reason
            for event in session.scalars(
                select(PatientPortalAuditEvent).where(
                    PatientPortalAuditEvent.event_type == AUDIT_EVENT_STAFF_ACTION
                )
            )
        ]
        assert failures == ["authorization_failed"]


def test_staff_disable_immediately_revokes_an_already_authenticated_session() -> None:
    """Disabling access is the emergency cut-off; an issued session must die with it."""
    app = internal_app()
    client = TestClient(app)
    invite = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )
    client.post(
        "/auth/activate",
        json={
            "invite_code": invite.json()["invite_token"],
            "email": "example.patient@example.com",
            "date_of_birth": "1980-05-20",
            "health_card_number": "ABCD 1234-5678",
            "username": "patient.user",
            "password": PASSWORD,
        },
    )
    login = client.post("/auth/login", json={"username": "patient.user", "password": PASSWORD})
    verified = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": login.json()["mfa_challenge_token"],
            "code": login.json()["development_mfa_code"],
        },
    )
    token = verified.json()["session_token"]
    assert client.get("/auth/session", headers={"Authorization": f"Bearer {token}"}).status_code

    disabled = client.post(
        "/internal/carlos/patients/1234/portal-account/access",
        headers=carlos_headers("portal.account.manage"),
        json={"enabled": False, "reason": "patient_requested"},
    )
    after = client.get("/auth/session", headers={"Authorization": f"Bearer {token}"})

    assert disabled.status_code == 200
    assert after.status_code == 401
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.status == "disabled"
        assert account.disabled_at is not None
        assert account.disabled_reason == "patient_requested"
        sessions = list(session.scalars(select(PatientPortalSession)))
        assert sessions
        # The reason distinguishes eager revocation from the lazy kill that would otherwise
        # satisfy this test even with disable-time revocation deleted.
        assert all(row.revoked_at is not None for row in sessions)
        assert all(row.revoked_reason == "account_disabled" for row in sessions)


def test_revoked_unlock_secret_cannot_be_republished() -> None:
    """Revocation must be terminal, including against an idempotent CARLOS retry."""
    app = internal_app()
    client = TestClient(app)
    created = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=carlos_headers("portal.secret.manage"),
        json={"source_reference": "revoked-message", "secret_type": "email"},
    )
    secret_id = created.json()["id"]
    client.post(
        f"/internal/carlos/unlock-secrets/{secret_id}/publish",
        headers=carlos_headers("portal.secret.manage"),
    )
    revoked = client.post(
        f"/internal/carlos/unlock-secrets/{secret_id}/revoke",
        headers=carlos_headers("portal.secret.manage"),
        json={"reason": "leaked"},
    )
    republish = client.post(
        f"/internal/carlos/unlock-secrets/{secret_id}/publish",
        headers=carlos_headers("portal.secret.manage"),
    )

    assert revoked.status_code == 200
    assert republish.status_code == 409
    with app.state.session_factory() as session:
        secret = session.get(PatientPortalUnlockSecret, secret_id)
        assert secret is not None
        assert secret.status == "revoked"
        assert secret.revoked_at is not None


def test_repeated_publish_is_distinguishable_from_the_first_publication() -> None:
    """An idempotent CARLOS retry must not look like a second real publication."""
    app = internal_app()
    client = TestClient(app)
    created = client.post(
        "/internal/carlos/patients/1234/unlock-secrets",
        headers=carlos_headers("portal.secret.manage"),
        json={"source_reference": "retry-message", "secret_type": "email"},
    )
    secret_id = created.json()["id"]
    for _ in range(3):
        client.post(
            f"/internal/carlos/unlock-secrets/{secret_id}/publish",
            headers=carlos_headers("portal.secret.manage"),
        )

    with app.state.session_factory() as session:
        reasons = [
            event.reason
            for event in session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_UNLOCK_SECRET_PUBLISH)
                .order_by(PatientPortalAuditEvent.id)
            )
        ]
        assert reasons == ["published", "already_published", "already_published"]


def test_internal_api_rejects_in_order_authentication_then_authorization_then_validation() -> None:
    """CARLOS checks privileges before touching request data; the portal boundary must match.

    Authorization running after body validation would leak the endpoint's schema to a caller who
    is not allowed to use it, via 422 field errors.
    """
    client = TestClient(internal_app())
    path = "/internal/carlos/patients/1234/portal-account/access"
    invalid_body = {"enabled": False, "unexpected_field": 1}
    valid_body = {"enabled": False, "reason": "staff_action"}

    bad_token = client.post(
        path,
        headers=carlos_headers("portal.account.manage", token="x" * 32),
        json=invalid_body,
    )
    wrong_permission = client.post(
        path, headers=carlos_headers(UNRELATED_PERMISSION), json=invalid_body
    )
    authorized_invalid_body = client.post(
        path, headers=carlos_headers("portal.account.manage"), json=invalid_body
    )
    authorized_valid_body = client.post(
        path, headers=carlos_headers("portal.account.manage"), json=valid_body
    )

    # Authentication wins over everything, then authorization, and only then the request model.
    assert bad_token.status_code == 404
    assert wrong_permission.status_code == 403
    assert authorized_invalid_body.status_code == 422
    # A permitted caller with a valid body reaches the service, which reports no such account.
    assert authorized_valid_body.status_code == 404


PREVIOUS_INTERNAL_API_TOKEN = "r" * MIN_PRODUCTION_SECRET_LENGTH


def test_previous_internal_api_token_is_accepted_during_rotation() -> None:
    """Both tokens work while _PREVIOUS is set, so CARLOS can be cut over independently."""
    client = TestClient(
        internal_app(internal_api_token_previous=PREVIOUS_INTERNAL_API_TOKEN)
    )

    with_active = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers("portal.invite.manage"),
        json=invite_request(),
    )
    with_previous = client.post(
        "/internal/carlos/patients/1235/invites",
        headers=carlos_headers(
            "portal.invite.manage",
            token=PREVIOUS_INTERNAL_API_TOKEN,
        ),
        json=invite_request(1235),
    )

    assert with_active.status_code == 201
    assert with_previous.status_code == 201


def test_retired_internal_api_token_stops_working_once_previous_is_cleared() -> None:
    client = TestClient(internal_app())

    retired = client.post(
        "/internal/carlos/patients/1234/invites",
        headers=carlos_headers(
            "portal.invite.manage",
            token=PREVIOUS_INTERNAL_API_TOKEN,
        ),
        json=invite_request(),
    )

    # Same generic 404 as any other unauthenticated internal request: the route family must not
    # confirm its own existence to a caller without a currently valid service token.
    assert retired.status_code == 404


def test_previous_internal_api_token_must_be_configured_alongside_an_active_token() -> None:
    with pytest.raises(ValueError, match="INTERNAL_API_TOKEN must be set"):
        Settings(
            environment="development",
            database_url="sqlite+pysqlite:///:memory:",
            internal_api_token_previous=PREVIOUS_INTERNAL_API_TOKEN,
        )


def test_previous_internal_api_token_must_differ_from_the_active_token() -> None:
    with pytest.raises(ValueError, match="must differ from"):
        Settings(
            environment="development",
            database_url="sqlite+pysqlite:///:memory:",
            internal_api_token=INTERNAL_API_TOKEN,
            internal_api_token_previous=INTERNAL_API_TOKEN,
        )
