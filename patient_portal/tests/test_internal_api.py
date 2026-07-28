import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from carlos_patient_portal import internal_routes
from carlos_patient_portal.account_settings import update_account_contact
from carlos_patient_portal.config import MIN_PRODUCTION_SECRET_LENGTH, Settings
from carlos_patient_portal.credentials import hash_password
from carlos_patient_portal.database import Base
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
    PatientPortalUnlockSecret,
    utc_now,
)

INTERNAL_API_TOKEN = "c" * MIN_PRODUCTION_SECRET_LENGTH
IDENTITY_PROOF_SECRET = "i" * MIN_PRODUCTION_SECRET_LENGTH
AUDIT_HASH_SECRET = "a" * MIN_PRODUCTION_SECRET_LENGTH
UNLOCK_SECRET = "u" * MIN_PRODUCTION_SECRET_LENGTH
PASSWORD = "Stronger1!word"


def internal_app(**overrides: object):
    app = create_app(
        Settings(
            environment="development",
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
    revoked = client.post(
        f"/internal/carlos/invites/{invite_id}/revoke",
        headers=headers,
    )
    rejected_resend = client.post(
        f"/internal/carlos/invites/{invite_id}/resend",
        headers=headers,
    )
    missing_revoke = client.post(
        "/internal/carlos/invites/999999/revoke",
        headers=headers,
    )

    assert listed.status_code == 200
    assert [item["id"] for item in listed.json()] == [invite_id]
    assert resent.status_code == 200
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
    assert app.state.operational_metrics.snapshot()["failures"][
        "unlock_secret_decryption"
    ] == 1
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
    assert [item["id"] for item in after_publish.json()["items"]] == [
        created.json()["id"]
    ]


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
            review = update_account_contact(
                session,
                account,
                current_password=PASSWORD,
                email="updated.patient@example.com",
                phone_number="+16135550199",
                max_failed_password_attempts=10,
            )
            assert review is not None
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

    assert cross_clinic.status_code == 200
    assert cross_clinic.json()["items"] == []
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
            review = update_account_contact(
                session,
                account,
                current_password=PASSWORD,
                email="rejected.patient@example.com",
                phone_number=None,
                max_failed_password_attempts=10,
            )
            assert review is not None
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
            first = update_account_contact(
                session,
                account,
                current_password=PASSWORD,
                email="first.patient@example.com",
                phone_number=None,
                max_failed_password_attempts=10,
            )
            second = update_account_contact(
                session,
                account,
                current_password=PASSWORD,
                email="second.patient@example.com",
                phone_number=None,
                max_failed_password_attempts=10,
            )
            assert first is not None and second is not None
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
