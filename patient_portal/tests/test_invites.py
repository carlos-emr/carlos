"""Invite lifecycle and the development-only staff API that exercises it."""

from datetime import timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from carlos_patient_portal import main, web_support
from carlos_patient_portal.config import (
    Settings,
)
from carlos_patient_portal.invites import (
    DEFAULT_INVITE_TTL,
    InviteNotFoundError,
    create_invite,
    hash_invite_token,
    list_invites,
    resend_invite,
    revoke_invite,
)
from carlos_patient_portal.models import (
    AUDIT_EVENT_INVITE_CREATE,
    AUDIT_EVENT_INVITE_LIST,
    AUDIT_EVENT_INVITE_RESEND,
    AUDIT_EVENT_INVITE_REVOKE,
    AUDIT_OUTCOME_SUCCESS,
    INVITE_STATUS_ACCEPTED,
    INVITE_STATUS_PENDING,
    INVITE_STATUS_REVOKED,
    PatientPortalAuditEvent,
    PatientPortalInvite,
    utc_now,
)
from tests.support import (
    AUDIT_HASH_SECRET,
    IDENTITY_PROOF_SECRET,
    INTERNAL_HEALTH_TOKEN,
    NON_DEVELOPMENT_SESSION_SECRET,
    TEST_CLINIC_ID,
    TEST_CLINIC_NAME,
    UNLOCK_SECRET_ENCRYPTION_SECRET,
    WRONG_DEV_ADMIN_TOKEN,
    activation_request,
    create_service_invite,
    dev_admin_headers,
    development_settings,
    migrated_development_app,
    parse_response_datetime,
    seeded_identity_proof,
    seeded_invite_request,
)


def test_dev_admin_invite_lifecycle() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor=" CarlosDoc "),
        json=seeded_invite_request(),
    )

    assert create_response.status_code == 201
    created_invite = create_response.json()
    invite_id = created_invite["id"]
    invite_token = created_invite["invite_token"]
    assert created_invite["clinic_id"] == "default"
    assert created_invite["demographic_no"] == 1234
    assert created_invite["status"] == "pending"
    assert created_invite["created_by"] == "CarlosDoc"
    assert created_invite["last_issued_by"] == "CarlosDoc"
    assert created_invite["issued_count"] == 1
    assert created_invite["has_identity_proof"] is True
    assert created_invite["accepted_at"] is None
    assert created_invite["accepted_account_id"] is None
    created_expires_at = parse_response_datetime(created_invite["expires_at"])
    created_at = parse_response_datetime(created_invite["created_at"])
    assert DEFAULT_INVITE_TTL - timedelta(seconds=1) <= created_expires_at - created_at
    assert created_expires_at - created_at <= DEFAULT_INVITE_TTL + timedelta(seconds=1)
    assert create_response.headers["cache-control"] == "no-store"

    with app.state.session_factory() as session:
        persisted_invite = session.get(PatientPortalInvite, invite_id)
        assert persisted_invite is not None
        assert persisted_invite.clinic_id == "default"
        assert persisted_invite.token_hash == hash_invite_token(invite_token)
        assert persisted_invite.token_hash != invite_token
        assert persisted_invite.expires_at is not None
        assert persisted_invite.proof_salt is not None
        assert persisted_invite.proof_hash_version == "v1"

    list_response = client.get(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        params={"demographic_no": 1234},
    )

    assert list_response.status_code == 200
    listed_invites = list_response.json()
    assert len(listed_invites) == 1
    assert listed_invites[0]["id"] == invite_id
    assert listed_invites[0]["clinic_id"] == "default"
    assert parse_response_datetime(listed_invites[0]["expires_at"]) == created_expires_at
    assert listed_invites[0]["has_identity_proof"] is True
    assert "invite_token" not in listed_invites[0]
    assert list_response.headers["cache-control"] == "no-store"

    resend_response = client.post(
        f"/dev/admin/invites/{invite_id}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert resend_response.status_code == 200
    resent_invite = resend_response.json()
    resent_token = resent_invite["invite_token"]
    assert resent_token != invite_token
    assert resent_invite["id"] != invite_id
    assert resent_invite["issued_count"] == 1
    assert resent_invite["supersedes_invite_id"] == invite_id
    assert resent_invite["last_issued_by"] == "Admin example"
    assert parse_response_datetime(resent_invite["expires_at"]) >= created_expires_at
    assert resend_response.headers["cache-control"] == "no-store"

    revoke_response = client.post(
        f"/dev/admin/invites/{resent_invite['id']}/revoke",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert revoke_response.status_code == 200
    revoked_invite = revoke_response.json()
    assert revoked_invite["status"] == "revoked"
    assert revoked_invite["revoked_by"] == "Admin example"
    assert "invite_token" not in revoked_invite

    revoked_resend_response = client.post(
        f"/dev/admin/invites/{resent_invite['id']}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert revoked_resend_response.status_code == 409
    assert revoked_resend_response.json()["detail"] == "invite has been revoked"


def test_new_invite_replaces_older_pending_invite_for_patient() -> None:
    app = migrated_development_app()
    client = TestClient(app)

    first_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="CarlosDoc"),
        json=seeded_invite_request(),
    )
    second_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="Admin example"),
        json=seeded_invite_request(),
    )

    assert first_create_response.status_code == 201
    assert second_create_response.status_code == 201
    first_invite = first_create_response.json()
    second_invite = second_create_response.json()
    assert second_invite["id"] != first_invite["id"]

    list_response = client.get(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        params={"demographic_no": 1234},
    )
    listed_invites = list_response.json()
    assert [invite["id"] for invite in listed_invites] == [
        second_invite["id"],
        first_invite["id"],
    ]
    assert [invite["status"] for invite in listed_invites] == ["pending", "revoked"]
    assert listed_invites[1]["revoked_by"] == "Admin example"

    old_activation_response = client.post(
        "/auth/activate",
        json=activation_request(first_invite["invite_token"]),
    )
    latest_activation_response = client.post(
        "/auth/activate",
        json=activation_request(second_invite["invite_token"]),
    )

    assert old_activation_response.status_code == 400
    assert latest_activation_response.status_code == 201


def test_invalid_replacement_invite_does_not_revoke_existing_pending_invite() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        pending_invite, _ = create_service_invite(session)
        session.commit()

        with pytest.raises(ValueError, match="email"):
            create_invite(
                session,
                1234,
                "Admin example",
                identity_proof=seeded_identity_proof(email="not-an-email"),
                proof_secret=IDENTITY_PROOF_SECRET,
            )

        persisted_invite = session.get(PatientPortalInvite, pending_invite.id)

        assert persisted_invite is not None
        assert persisted_invite.status == INVITE_STATUS_PENDING
        assert persisted_invite.revoked_at is None
        assert persisted_invite.revoked_by is None


def test_dev_admin_invite_rejects_patient_with_existing_account() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]
    activation_response = client.post("/auth/activate", json=activation_request(invite_token))

    duplicate_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert activation_response.status_code == 201
    assert duplicate_create_response.status_code == 409
    assert duplicate_create_response.json()["detail"] == "patient already has a portal account"


def test_accepted_invites_cannot_be_resent_or_revoked() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    created_invite = create_response.json()

    assert (
        client.post(
            "/auth/activate",
            json=activation_request(created_invite["invite_token"]),
        ).status_code
        == 201
    )

    resend_response = client.post(
        f"/dev/admin/invites/{created_invite['id']}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )
    revoke_response = client.post(
        f"/dev/admin/invites/{created_invite['id']}/revoke",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert resend_response.status_code == 409
    assert resend_response.json()["detail"] == "invite has already been accepted"
    assert revoke_response.status_code == 409
    assert revoke_response.json()["detail"] == "invite has already been accepted"


def test_dev_admin_invites_are_hidden_outside_development() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            clinic_id=TEST_CLINIC_ID,
            clinic_name=TEST_CLINIC_NAME,
            enable_dev_admin=True,
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            unlock_secret_encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert response.status_code == 404


def test_dev_admin_invites_require_explicit_development_flag() -> None:
    app = main.create_app(
        development_settings(
            database_url="sqlite+pysqlite:///:memory:",
            identity_proof_secret=IDENTITY_PROOF_SECRET,
        )
    )
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert response.status_code == 404


def test_dev_admin_invites_require_bearer_token() -> None:
    app = migrated_development_app()
    client = TestClient(app)

    missing_token_response = client.post("/dev/admin/invites", json=seeded_invite_request())
    missing_token_invalid_body_response = client.post("/dev/admin/invites", json={})
    wrong_token_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(WRONG_DEV_ADMIN_TOKEN),
        json=seeded_invite_request(),
    )

    assert missing_token_response.status_code == 404
    assert missing_token_invalid_body_response.status_code == 404
    assert wrong_token_response.status_code == 404


def test_dev_admin_invite_creation_rejects_oversized_json_body_after_auth() -> None:
    app = migrated_development_app()
    oversized_body = (
        b'{"demographic_no":1234,"email":"'
        + b"x" * web_support.MAX_JSON_BODY_BYTES
        + b'"}'
    )

    missing_token_response = TestClient(app).post(
        "/dev/admin/invites",
        content=oversized_body,
        headers={"Content-Type": "application/json"},
    )
    authenticated_response = TestClient(app).post(
        "/dev/admin/invites",
        content=oversized_body,
        headers={
            "Content-Type": "application/json",
            **dev_admin_headers(),
        },
    )

    assert missing_token_response.status_code == 404
    assert authenticated_response.status_code == 413
    assert authenticated_response.json()["detail"] == "request body too large"


def test_dev_admin_invite_requires_identity_proof() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json={"demographic_no": 1234},
    )

    assert response.status_code == 422
    assert "health_card_number" in response.text


def test_dev_admin_invite_requires_positive_demographic_no() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(demographic_no=0),
    )

    assert response.status_code == 422


def test_dev_admin_invite_list_rejects_invalid_bounds() -> None:
    app = migrated_development_app()
    client = TestClient(app)

    assert (
        client.get(
            "/dev/admin/invites", headers=dev_admin_headers(), params={"limit": 0}
        ).status_code
        == 422
    )
    assert (
        client.get(
            "/dev/admin/invites", headers=dev_admin_headers(), params={"limit": 101}
        ).status_code
        == 422
    )
    assert (
        client.get(
            "/dev/admin/invites", headers=dev_admin_headers(), params={"offset": -1}
        ).status_code
        == 422
    )


def test_dev_admin_unknown_invite_returns_not_found() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/dev/admin/invites/999/resend",
        headers=dev_admin_headers(),
    )

    assert response.status_code == 404
    assert response.json()["detail"] == "invite not found"


def test_invite_lifecycle_writes_audit_events() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_id = create_response.json()["id"]
    resend_response = client.post(
        f"/dev/admin/invites/{invite_id}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )
    assert resend_response.status_code == 200
    replacement_invite_id = resend_response.json()["id"]
    assert (
        client.post(
            f"/dev/admin/invites/{replacement_invite_id}/revoke",
            headers=dev_admin_headers(actor="Admin example"),
        ).status_code
        == 200
    )

    with app.state.session_factory() as session:
        audit_events = list(
            session.scalars(select(PatientPortalAuditEvent).order_by(PatientPortalAuditEvent.id))
        )

        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_INVITE_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_INVITE_RESEND, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_INVITE_REVOKE, AUDIT_OUTCOME_SUCCESS),
        ]


def test_invite_list_writes_audit_event() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="CarlosDoc"),
        json=seeded_invite_request(),
    )

    list_response = client.get(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="Admin example"),
        params={"demographic_no": 1234},
    )

    assert create_response.status_code == 201
    assert list_response.status_code == 200
    with app.state.session_factory() as session:
        audit_events = list(
            session.scalars(select(PatientPortalAuditEvent).order_by(PatientPortalAuditEvent.id))
        )

        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_INVITE_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_INVITE_LIST, AUDIT_OUTCOME_SUCCESS),
        ]
        assert audit_events[-1].actor == "Admin example"
        assert audit_events[-1].demographic_no == 1234


def test_invite_status_constraints_require_matching_metadata() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        accepted_invite, _ = create_service_invite(session)
        session.commit()

        accepted_invite.status = INVITE_STATUS_ACCEPTED
        with pytest.raises(IntegrityError):
            session.commit()
        session.rollback()

        revoked_invite, _ = create_service_invite(session, demographic_no=5678)
        session.commit()

        revoked_invite.status = INVITE_STATUS_REVOKED
        with pytest.raises(IntegrityError):
            session.commit()


def test_invite_constraints_allow_only_one_pending_invite_per_patient() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        first_invite, _ = create_service_invite(session)
        session.commit()

        duplicate_pending_invite = PatientPortalInvite(
            clinic_id=first_invite.clinic_id,
            demographic_no=first_invite.demographic_no,
            token_hash=hash_invite_token("manual-duplicate-token"),
            status=INVITE_STATUS_PENDING,
            created_by="CarlosDoc",
            created_at=utc_now(),
            updated_at=utc_now(),
            sent_count=1,
            last_sent_at=utc_now(),
            last_sent_by="CarlosDoc",
            expires_at=utc_now() + DEFAULT_INVITE_TTL,
            proof_email_hash=first_invite.proof_email_hash,
            proof_date_of_birth_hash=first_invite.proof_date_of_birth_hash,
            proof_health_card_hash=first_invite.proof_health_card_hash,
            proof_salt=first_invite.proof_salt,
            proof_hash_version=first_invite.proof_hash_version,
        )
        session.add(duplicate_pending_invite)
        with pytest.raises(IntegrityError):
            session.commit()


def test_invite_service_validates_future_carlos_callers() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        with pytest.raises(ValueError, match="demographic_no"):
            create_invite(
                session,
                0,
                "CarlosDoc",
                identity_proof=seeded_identity_proof(),
                proof_secret=IDENTITY_PROOF_SECRET,
            )
        with pytest.raises(ValueError, match="actor"):
            create_invite(
                session,
                1234,
                " ",
                identity_proof=seeded_identity_proof(),
                proof_secret=IDENTITY_PROOF_SECRET,
            )
        with pytest.raises(ValueError, match="actor"):
            create_invite(
                session,
                1234,
                "x" * 129,
                identity_proof=seeded_identity_proof(),
                proof_secret=IDENTITY_PROOF_SECRET,
            )
        with pytest.raises(ValueError, match="proof_secret"):
            create_invite(
                session,
                1234,
                "CarlosDoc",
                identity_proof=seeded_identity_proof(),
                proof_secret=" ",
            )
        with pytest.raises(ValueError, match="demographic_no"):
            list_invites(session, demographic_no=0)
        with pytest.raises(ValueError, match="limit"):
            list_invites(session, limit=0)
        with pytest.raises(ValueError, match="limit"):
            list_invites(session, limit=101)
        with pytest.raises(ValueError, match="offset"):
            list_invites(session, offset=-1)
        with pytest.raises(InviteNotFoundError):
            resend_invite(session, 999, " ")
        with pytest.raises(InviteNotFoundError):
            revoke_invite(session, 999, " ")


def test_invite_service_scopes_records_by_clinic() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        clinic_a_invite, _ = create_service_invite(
            session,
            1234,
            "CarlosDoc",
            clinic_id="clinic-a",
        )
        clinic_b_invite, _ = create_service_invite(
            session,
            1234,
            "CarlosDoc",
            clinic_id="clinic-b",
        )
        session.commit()

        clinic_a_invites = list_invites(session, demographic_no=1234, clinic_id="clinic-a")
        clinic_b_invites = list_invites(session, demographic_no=1234, clinic_id="clinic-b")

        assert [invite.id for invite in clinic_a_invites] == [clinic_a_invite.id]
        assert [invite.id for invite in clinic_b_invites] == [clinic_b_invite.id]
        with pytest.raises(InviteNotFoundError):
            resend_invite(
                session,
                clinic_a_invite.id,
                "CarlosDoc",
                clinic_id="clinic-b",
            )


def test_invite_identity_proof_hashes_are_salted_per_invite() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        first_invite, _ = create_service_invite(session, demographic_no=1234)
        second_invite, _ = create_service_invite(session, demographic_no=5678)

        assert first_invite.proof_salt is not None
        assert second_invite.proof_salt is not None
        assert first_invite.proof_salt != second_invite.proof_salt
        assert first_invite.proof_email_hash != second_invite.proof_email_hash
        assert first_invite.proof_health_card_hash != second_invite.proof_health_card_hash
