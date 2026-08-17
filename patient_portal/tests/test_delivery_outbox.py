from datetime import timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from carlos_patient_portal.auth import PasswordResetRequestResult
from carlos_patient_portal.delivery_outbox import (
    enqueue_password_reset_delivery,
    process_one_delivery,
)
from carlos_patient_portal.models import (
    AUDIT_EVENT_PASSWORD_RESET_DELIVERY,
    OUTBOX_STATUS_DELIVERED,
    OUTBOX_STATUS_FAILED,
    OUTBOX_STATUS_PROCESSING,
    PASSWORD_RESET_STATUS_PENDING,
    PASSWORD_RESET_STATUS_REVOKED,
    PatientPortalAuditEvent,
    PatientPortalOutboundDelivery,
    PatientPortalPasswordResetToken,
    utc_now,
)
from tests.support import (
    OUTBOX_ENCRYPTION_SECRET,
    RecordingPortalEmailSender,
    activate_seeded_patient_account,
    migrated_development_app,
)


def queue_reset(app: object, account_id: int) -> tuple[int, int]:
    with app.state.session_factory() as session:
        with session.begin():
            reset = PatientPortalPasswordResetToken(
                account_id=account_id,
                token_hash="r" * 64,
                status=PASSWORD_RESET_STATUS_PENDING,
                created_at=utc_now(),
                expires_at=utc_now() + timedelta(hours=1),
            )
            session.add(reset)
            session.flush()
            delivery = enqueue_password_reset_delivery(
                session,
                result=PasswordResetRequestResult(
                    reset_token="raw-reset-token",
                    recipient="patient@example.test",
                    reset_token_id=reset.id,
                    account_id=account_id,
                ),
                reset_url="https://portal.example.test/reset#token=raw-reset-token",
                expires_in_seconds=3600,
                encryption_secret=OUTBOX_ENCRYPTION_SECRET,
            )
            return delivery.id, reset.id


def test_outbox_encrypts_and_delivers_reset_with_stable_message_id() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(
        email_sender=sender,
        outbox_encryption_secret=OUTBOX_ENCRYPTION_SECRET,
    )
    account_id = activate_seeded_patient_account(app, TestClient(app))
    delivery_id, reset_id = queue_reset(app, account_id)

    with app.state.session_factory() as session:
        queued = session.get(PatientPortalOutboundDelivery, delivery_id)
        assert queued is not None
        assert b"raw-reset-token" not in queued.encrypted_payload
        assert b"patient@example.test" not in queued.encrypted_payload
        expected_message_id = queued.message_id

    result = process_one_delivery(
        app.state.session_factory,
        email_sender=sender,
        encryption_secret=OUTBOX_ENCRYPTION_SECRET,
        max_attempts=3,
        lease_seconds=60,
    )

    assert result is not None
    assert result.status == OUTBOX_STATUS_DELIVERED
    assert sender.messages[-1]["message_id"] == expected_message_id
    with app.state.session_factory() as session:
        assert session.get(PatientPortalPasswordResetToken, reset_id).status == (
            PASSWORD_RESET_STATUS_PENDING
        )
        assert session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_PASSWORD_RESET_DELIVERY
            )
        ) is not None


def test_terminal_delivery_failure_revokes_reset_token() -> None:
    sender = RecordingPortalEmailSender(fail=True)
    app = migrated_development_app(
        email_sender=sender,
        outbox_encryption_secret=OUTBOX_ENCRYPTION_SECRET,
    )
    account_id = activate_seeded_patient_account(app, TestClient(app))
    delivery_id, reset_id = queue_reset(app, account_id)

    result = process_one_delivery(
        app.state.session_factory,
        email_sender=sender,
        encryption_secret=OUTBOX_ENCRYPTION_SECRET,
        max_attempts=1,
        lease_seconds=60,
    )

    assert result is not None
    assert result.status == OUTBOX_STATUS_FAILED
    with app.state.session_factory() as session:
        assert session.get(PatientPortalOutboundDelivery, delivery_id).last_failure_code == (
            "PortalEmailDeliveryError"
        )
        assert session.get(PatientPortalPasswordResetToken, reset_id).status == (
            PASSWORD_RESET_STATUS_REVOKED
        )


def test_expired_lease_recovers_after_worker_loss() -> None:
    class CrashingSender:
        def send_password_reset(self, **kwargs: object) -> None:
            # Model SMTP accepting the message followed by worker death before the delivery-state
            # commit. The retry must reuse the same Message-ID so downstream deduplication works.
            sender.send_password_reset(**kwargs)
            raise KeyboardInterrupt

    sender = RecordingPortalEmailSender()
    app = migrated_development_app(
        email_sender=sender,
        outbox_encryption_secret=OUTBOX_ENCRYPTION_SECRET,
    )
    account_id = activate_seeded_patient_account(app, TestClient(app))
    delivery_id, _ = queue_reset(app, account_id)

    with pytest.raises(KeyboardInterrupt):
        process_one_delivery(
            app.state.session_factory,
            email_sender=CrashingSender(),
            encryption_secret=OUTBOX_ENCRYPTION_SECRET,
            max_attempts=3,
            lease_seconds=60,
        )
    with app.state.session_factory() as session:
        with session.begin():
            delivery = session.get(PatientPortalOutboundDelivery, delivery_id)
            assert delivery is not None
            assert delivery.status == OUTBOX_STATUS_PROCESSING
            delivery.lease_expires_at = utc_now() - timedelta(seconds=1)

    recovered = process_one_delivery(
        app.state.session_factory,
        email_sender=sender,
        encryption_secret=OUTBOX_ENCRYPTION_SECRET,
        max_attempts=3,
        lease_seconds=60,
    )
    assert recovered is not None
    assert recovered.status == OUTBOX_STATUS_DELIVERED
    assert sender.messages[0]["message_id"] == sender.messages[1]["message_id"]


def test_reset_and_outbox_rollback_together_when_the_source_transaction_fails() -> None:
    app = migrated_development_app(outbox_encryption_secret=OUTBOX_ENCRYPTION_SECRET)
    account_id = activate_seeded_patient_account(app, TestClient(app))

    def fail_source_transaction() -> None:
        with app.state.session_factory() as session, session.begin():
            reset = PatientPortalPasswordResetToken(
                account_id=account_id,
                token_hash="t" * 64,
                status=PASSWORD_RESET_STATUS_PENDING,
                created_at=utc_now(),
                expires_at=utc_now() + timedelta(hours=1),
            )
            session.add(reset)
            session.flush()
            enqueue_password_reset_delivery(
                session,
                result=PasswordResetRequestResult(
                    reset_token="rolled-back-token",
                    recipient="patient@example.test",
                    reset_token_id=reset.id,
                    account_id=account_id,
                ),
                reset_url="https://portal.example.test/reset#token=rolled-back-token",
                expires_in_seconds=3600,
                encryption_secret=OUTBOX_ENCRYPTION_SECRET,
            )
            raise RuntimeError("simulated source transaction failure")

    with pytest.raises(RuntimeError):
        fail_source_transaction()

    with app.state.session_factory() as session:
        assert session.scalar(
            select(PatientPortalPasswordResetToken).where(
                PatientPortalPasswordResetToken.token_hash == "t" * 64
            )
        ) is None
        assert session.scalar(select(PatientPortalOutboundDelivery)) is None
