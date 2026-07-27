from fastapi.testclient import TestClient
from sqlalchemy import select

from carlos_patient_portal.account_settings import update_account_contact
from carlos_patient_portal.config import MIN_PRODUCTION_SECRET_LENGTH, Settings
from carlos_patient_portal.database import Base
from carlos_patient_portal.main import create_app
from carlos_patient_portal.models import (
    AUDIT_EVENT_ACCOUNT_UNLOCK,
    AUDIT_EVENT_INVITE_CREATE,
    AUDIT_EVENT_UNLOCK_SECRET_CREATE,
    AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
    PatientPortalAccount,
    PatientPortalAuditEvent,
    PatientPortalInvite,
    PatientPortalUnlockSecret,
    utc_now,
)

INTERNAL_API_TOKEN = "c" * MIN_PRODUCTION_SECRET_LENGTH
IDENTITY_PROOF_SECRET = "i" * MIN_PRODUCTION_SECRET_LENGTH
AUDIT_HASH_SECRET = "a" * MIN_PRODUCTION_SECRET_LENGTH
UNLOCK_SECRET = "u" * MIN_PRODUCTION_SECRET_LENGTH
PASSWORD = "Stronger1!word"


def internal_app():
    app = create_app(
        Settings(
            environment="development",
            database_url="sqlite+pysqlite:///:memory:",
            internal_api_token=INTERNAL_API_TOKEN,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            unlock_secret_encryption_secret=UNLOCK_SECRET,
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
    assert repeated.status_code == 201
    assert repeated.json()["created"] is False
    assert repeated.json()["secret"] == created.json()["secret"]
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
        json={"approve": True},
    )
    replay = client.post(
        f"/internal/carlos/contact-reviews/{review_id}/decision",
        headers=carlos_headers("portal.contact.review"),
        json={"approve": True},
    )

    assert cross_clinic.status_code == 200
    assert cross_clinic.json()["items"] == []
    assert pending.status_code == 200
    assert [item["id"] for item in pending.json()["items"]] == [review_id]
    assert approved.status_code == 200
    assert approved.json()["decision"] == "approved"
    assert replay.status_code == 404
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

    rejected = client.post(
        f"/internal/carlos/contact-reviews/{review_id}/decision",
        headers=carlos_headers("portal.contact.review"),
        json={"approve": False},
    )

    assert rejected.status_code == 200
    assert rejected.json()["decision"] == "rejected"
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.email == "example.patient@example.com"
