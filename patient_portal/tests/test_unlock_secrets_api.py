"""Generated passphrase storage: encryption, scoping, disclosure, and revocation."""

import re

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from carlos_patient_portal.models import (
    AUDIT_EVENT_UNLOCK_SECRET_CREATE,
    AUDIT_EVENT_UNLOCK_SECRET_READ,
    AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
    AUDIT_OUTCOME_SUCCESS,
    UNLOCK_SECRET_NONCE_LENGTH,
    UNLOCK_SECRET_STATUS_REVOKED,
    UNLOCK_SECRET_TYPE_EMAIL,
    PatientPortalAuditEvent,
    PatientPortalUnlockSecret,
)
from carlos_patient_portal.unlock_secrets import (
    UnlockSecretDecryptionError,
    UnlockSecretNotFoundError,
    UnlockSecretRevokedError,
    count_unlock_secrets,
    create_unlock_secret,
    generate_unlock_secret_value,
    list_unlock_secrets,
    read_unlock_secret,
    revoke_unlock_secret,
)
from tests.support import (
    UNLOCK_SECRET_ENCRYPTION_SECRET,
    activate_seeded_patient_account,
    migrated_development_app,
)


def test_generated_unlock_secret_value_uses_reviewed_email_pdf_format() -> None:
    for _ in range(25):
        generated_secret = generate_unlock_secret_value()

        assert re.fullmatch(r"[a-z]+-[a-z]+-\d{3}-[a-z]+-[a-z]+-\d{3}", generated_secret)


def test_unlock_secret_lifecycle_encrypts_decrypts_revokes_and_audits() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    raw_secret = "UnlockEmail9!"

    with app.state.session_factory() as session:
        with session.begin():
            created = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Email password",
                source_reference="message-3135",
            )
            unlock_secret_id = created.unlock_secret.id
            stored_secret = session.get(PatientPortalUnlockSecret, unlock_secret_id)

            assert created.secret == raw_secret
            assert stored_secret is not None
            assert stored_secret.encrypted_secret != raw_secret.encode("utf-8")
            assert raw_secret.encode("utf-8") not in stored_secret.encrypted_secret
            assert len(stored_secret.encryption_nonce) == UNLOCK_SECRET_NONCE_LENGTH
            assert stored_secret.account_id == account_id

            listed_secrets = list_unlock_secrets(
                session,
                clinic_id="default",
                account_id=account_id,
            )
            counted_secrets = count_unlock_secrets(
                session,
                clinic_id="default",
                account_id=account_id,
                search="Email",
            )
            missing_secret_count = count_unlock_secrets(
                session,
                clinic_id="default",
                account_id=account_id,
                search="missing",
            )
            with pytest.raises(ValueError, match="account_id or demographic_no"):
                list_unlock_secrets(session, clinic_id="default")
            with pytest.raises(ValueError, match="account_id or demographic_no"):
                count_unlock_secrets(session, clinic_id="default")
            with pytest.raises(ValueError, match="limit"):
                list_unlock_secrets(
                    session,
                    clinic_id="default",
                    account_id=account_id,
                    limit=0,
                )
            with pytest.raises(ValueError, match="offset"):
                list_unlock_secrets(
                    session,
                    clinic_id="default",
                    account_id=account_id,
                    offset=-1,
                )
            with pytest.raises(UnlockSecretNotFoundError):
                read_unlock_secret(
                    session,
                    unlock_secret_id,
                    clinic_id="default",
                    account_id=account_id,
                    demographic_no=5678,
                    actor_type="patient",
                    actor="patient.user",
                    encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                )
            decrypted_secret = read_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id="default",
                account_id=account_id,
                actor_type="patient",
                actor="patient.user",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            )
            revoked_secret = revoke_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id="default",
                demographic_no=1234,
                revoked_by="CarlosDoc",
                reason="staff_requested",
            )

            assert [secret.id for secret in listed_secrets] == [unlock_secret_id]
            assert counted_secrets == 1
            assert missing_secret_count == 0
            assert decrypted_secret == raw_secret
            assert revoked_secret.status == UNLOCK_SECRET_STATUS_REVOKED
            assert revoked_secret.last_viewed_at is not None

        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type.in_(
                        [
                            AUDIT_EVENT_UNLOCK_SECRET_CREATE,
                            AUDIT_EVENT_UNLOCK_SECRET_READ,
                            AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
                        ]
                    )
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )
        stored_secret = session.get(PatientPortalUnlockSecret, unlock_secret_id)

        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_UNLOCK_SECRET_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_UNLOCK_SECRET_READ, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_UNLOCK_SECRET_REVOKE, AUDIT_OUTCOME_SUCCESS),
        ]
        assert stored_secret is not None
        table_text = "|".join(
            str(value or "")
            for value in [
                stored_secret.label,
                stored_secret.source_reference,
                stored_secret.encryption_algorithm,
                stored_secret.encryption_key_id,
                stored_secret.status,
                stored_secret.created_by,
                stored_secret.revoked_by,
                stored_secret.revoke_reason,
            ]
        )
        audit_text = "|".join(
            str(value or "")
            for event in audit_events
            for value in [
                event.event_type,
                event.outcome,
                event.actor_type,
                event.actor,
                event.invite_token_hash,
                event.client_reference_hash,
                event.reason,
            ]
        )
        assert raw_secret not in table_text
        assert raw_secret not in audit_text

    with app.state.session_factory() as session:
        with pytest.raises(UnlockSecretRevokedError):
            read_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id="default",
                account_id=account_id,
                actor_type="patient",
                actor="patient.user",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            )


def test_unlock_secret_decryption_rejects_wrong_encryption_secret() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    with app.state.session_factory() as session:
        with session.begin():
            created = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            )
            unlock_secret_id = created.unlock_secret.id

        with pytest.raises(UnlockSecretDecryptionError):
            read_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id="default",
                account_id=account_id,
                actor_type="patient",
                actor="patient.user",
                encryption_secret="wrong" * 8,
            )


def test_unlock_secret_ciphertext_is_bound_to_its_record_context() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        with session.begin():
            first = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                source_reference="context-first",
            )
            second = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                source_reference="context-second",
            )
            first.unlock_secret.encrypted_secret = second.unlock_secret.encrypted_secret
            first.unlock_secret.encryption_nonce = second.unlock_secret.encryption_nonce

            with pytest.raises(UnlockSecretDecryptionError):
                read_unlock_secret(
                    session,
                    first.unlock_secret.id,
                    clinic_id="default",
                    demographic_no=1234,
                    actor_type="staff",
                    actor="CarlosDoc",
                    encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                )
