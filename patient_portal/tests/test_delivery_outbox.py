import time
from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from threading import Event

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.exc import SQLAlchemyError

from carlos_patient_portal import delivery_outbox
from carlos_patient_portal.auth import PasswordResetRequestResult
from carlos_patient_portal.delivery_outbox import (
    enqueue_password_reset_delivery,
    process_one_delivery,
)
from carlos_patient_portal.models import (
    AUDIT_EVENT_PASSWORD_RESET_DELIVERY,
    OUTBOX_STATUS_DELIVERED,
    OUTBOX_STATUS_FAILED,
    OUTBOX_STATUS_PENDING,
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
    development_settings,
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


def test_delivery_retries_when_its_audit_row_cannot_be_written(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """An unwritable audit row must send the delivery back, not close it as delivered.

    Falling through to `delivered` meant a live password-reset link reached the patient with
    neither a SUCCESS nor a FAILURE row behind it - the exact condition a breach review cannot
    reconstruct. Retrying risks a duplicate email, which is the cheaper failure.
    """
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(
        email_sender=sender,
        outbox_encryption_secret=OUTBOX_ENCRYPTION_SECRET,
    )
    account_id = activate_seeded_patient_account(app, TestClient(app))
    delivery_id, reset_id = queue_reset(app, account_id)

    def fail_audit(*args: object, **kwargs: object) -> None:
        raise SQLAlchemyError("audit unavailable")

    monkeypatch.setattr(
        delivery_outbox,
        "record_password_reset_delivery_outcome",
        fail_audit,
    )
    result = process_one_delivery(
        app.state.session_factory,
        email_sender=sender,
        encryption_secret=OUTBOX_ENCRYPTION_SECRET,
        max_attempts=3,
        lease_seconds=60,
    )

    assert result is not None
    assert result.status == OUTBOX_STATUS_PENDING
    with app.state.session_factory() as session:
        delivery = session.get(PatientPortalOutboundDelivery, delivery_id)
        assert delivery.status == OUTBOX_STATUS_PENDING
        assert delivery.delivered_at is None
        # The token stays usable: the message did go out, and the patient must still be able
        # to complete the reset while the row is retried.
        assert session.get(PatientPortalPasswordResetToken, reset_id).status == (
            PASSWORD_RESET_STATUS_PENDING
        )


def test_reset_delivered_during_a_token_race_is_audited_as_superseded(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """A reset email that went out must never be recorded as a delivery failure.

    If the patient completes the reset, or requests another, between the send and
    _finish_delivery, record_password_reset_delivery_outcome raises. That previously set the
    row to `failed` - terminal, so the terminal handler never ran either - leaving a link that
    physically landed in the mailbox with neither a SUCCESS nor a FAILURE audit row, and
    sending operators after an SMTP problem that never happened.
    """
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(
        email_sender=sender,
        outbox_encryption_secret=OUTBOX_ENCRYPTION_SECRET,
    )
    account_id = activate_seeded_patient_account(app, TestClient(app))
    delivery_id, reset_id = queue_reset(app, account_id)

    def token_already_consumed(*args: object, **kwargs: object) -> None:
        raise delivery_outbox.PasswordResetTokenInvalidError()

    monkeypatch.setattr(
        delivery_outbox,
        "record_password_reset_delivery_outcome",
        token_already_consumed,
    )
    result = process_one_delivery(
        app.state.session_factory,
        email_sender=sender,
        encryption_secret=OUTBOX_ENCRYPTION_SECRET,
        max_attempts=3,
        lease_seconds=60,
    )

    assert result is not None
    assert result.status == OUTBOX_STATUS_DELIVERED
    assert len(sender.messages) == 1
    with app.state.session_factory() as session:
        assert session.get(PatientPortalOutboundDelivery, delivery_id).status == (
            OUTBOX_STATUS_DELIVERED
        )
        superseded = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_PASSWORD_RESET_DELIVERY,
                PatientPortalAuditEvent.reason == "password_reset:superseded",
            )
        )
        assert superseded is not None
        assert superseded.account_id == account_id


def test_retry_backoff_reaches_its_cap_and_is_jittered() -> None:
    """The retry budget must outlast an ordinary relay outage, and must not stampede it.

    At the previous 8 attempts the schedule was 2+4+8+16+32+64+128+256 = 510 seconds, so the
    15-minute cap was unreachable and a commonplace ~10-minute SMTP outage terminally failed
    everything queued - revoking each patient's pending reset token. The delay was also fully
    deterministic, so every queued row became available in the same instant.
    """
    settings = development_settings()
    ceilings = [
        min(delivery_outbox.OUTBOX_MAX_RETRY_DELAY_SECONDS, 2 ** min(attempt, 10))
        for attempt in range(1, settings.outbox_max_attempts + 1)
    ]

    # The cap is actually engaged rather than being dead configuration.
    assert delivery_outbox.OUTBOX_MAX_RETRY_DELAY_SECONDS in ceilings
    # And the budget outlasts a long relay outage.
    assert sum(ceilings) > 60 * 60

    samples = {delivery_outbox._retry_delay_seconds(6) for _ in range(50)}
    assert len(samples) > 1, "a deterministic delay stampedes the relay on recovery"
    assert all(0 < sample <= 2**6 for sample in samples)


def test_active_delivery_renews_its_lease_during_a_slow_provider_call(tmp_path) -> None:
    entered_provider = Event()
    release_provider = Event()

    class BlockingSender:
        def send_password_reset(self, **kwargs: object) -> None:
            entered_provider.set()
            assert release_provider.wait(timeout=5)

    app = migrated_development_app(
        database_url=f"sqlite+pysqlite:///{tmp_path / 'outbox.db'}",
        outbox_encryption_secret=OUTBOX_ENCRYPTION_SECRET,
    )
    account_id = activate_seeded_patient_account(app, TestClient(app))
    queue_reset(app, account_id)

    with ThreadPoolExecutor(max_workers=1) as executor:
        first_worker = executor.submit(
            process_one_delivery,
            app.state.session_factory,
            email_sender=BlockingSender(),
            encryption_secret=OUTBOX_ENCRYPTION_SECRET,
            max_attempts=3,
            lease_seconds=1,
        )
        assert entered_provider.wait(timeout=5)
        time.sleep(1.2)
        second_worker = process_one_delivery(
            app.state.session_factory,
            email_sender=RecordingPortalEmailSender(),
            encryption_secret=OUTBOX_ENCRYPTION_SECRET,
            max_attempts=3,
            lease_seconds=1,
        )
        release_provider.set()
        first_result = first_worker.result(timeout=5)

    assert second_worker is None
    assert first_result is not None
    assert first_result.status == OUTBOX_STATUS_DELIVERED


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
