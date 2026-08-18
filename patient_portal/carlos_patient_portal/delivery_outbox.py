"""Encrypted transactional outbox for non-interactive portal email."""

import json
import logging
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import timedelta
from secrets import token_bytes
from threading import Event, Thread
from uuid import uuid4

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from sqlalchemy import or_, select, update
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session, sessionmaker

from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.auth import (
    PasswordResetRequestResult,
    PasswordResetTokenInvalidError,
    record_password_reset_delivery_outcome,
)
from carlos_patient_portal.email_delivery import PortalEmailDeliveryError, PortalEmailSender
from carlos_patient_portal.models import (
    AUDIT_ACTOR_TYPE_SYSTEM,
    AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    OUTBOX_KIND_CONTACT_CHANGE,
    OUTBOX_KIND_PASSWORD_RESET,
    OUTBOX_NONCE_LENGTH,
    OUTBOX_STATUS_DELIVERED,
    OUTBOX_STATUS_FAILED,
    OUTBOX_STATUS_PENDING,
    OUTBOX_STATUS_PROCESSING,
    PASSWORD_RESET_STATUS_PENDING,
    PASSWORD_RESET_STATUS_REVOKED,
    PatientPortalAccount,
    PatientPortalOutboundDelivery,
    PatientPortalPasswordResetToken,
    utc_now,
)

logger = logging.getLogger(__name__)

KEY_DERIVATION_INFO = b"carlos-patient-portal:outbound-delivery:v1"
ASSOCIATED_DATA_PREFIX = "carlos-patient-portal.outbound-delivery.v1"
OUTBOX_KEY_ID = "primary"
MAX_OUTBOX_PAYLOAD_BYTES = 4096
# Kept local rather than imported from account_settings so the worker process does not pull the
# interactive account-settings module in; the value must stay in step with
# ACCOUNT_SETTINGS_REASON_DELIVERY_UNAVAILABLE there.
OUTBOX_REASON_DELIVERY_UNAVAILABLE = "delivery_unavailable"
OUTBOX_AUDIT_ACTOR = "portal-outbox"


class OutboxPayloadError(Exception):
    """Raised when a queued payload cannot be authenticated or validated."""


@dataclass(frozen=True)
class DeliveryRunResult:
    delivery_id: int
    status: str


def derive_outbox_key(secret: str) -> bytes:
    normalized = secret.strip()
    if not normalized:
        raise ValueError("outbox encryption secret must not be blank")
    return HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=None,
        info=KEY_DERIVATION_INFO,
    ).derive(normalized.encode("utf-8"))


def _associated_data(*, kind: str, account_id: int, message_id: str) -> bytes:
    return f"{ASSOCIATED_DATA_PREFIX}:{kind}:{account_id}:{message_id}".encode()


def _encrypt_payload(
    payload: dict[str, object],
    *,
    encryption_secret: str,
    kind: str,
    account_id: int,
    message_id: str,
) -> tuple[bytes, bytes]:
    encoded = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode()
    if len(encoded) > MAX_OUTBOX_PAYLOAD_BYTES:
        raise ValueError("outbound delivery payload is too large")
    nonce = token_bytes(OUTBOX_NONCE_LENGTH)
    ciphertext = AESGCM(derive_outbox_key(encryption_secret)).encrypt(
        nonce,
        encoded,
        _associated_data(kind=kind, account_id=account_id, message_id=message_id),
    )
    return ciphertext, nonce


def _decrypt_payload(
    delivery: PatientPortalOutboundDelivery,
    *,
    encryption_secret: str,
) -> dict[str, object]:
    if delivery.encryption_key_id != OUTBOX_KEY_ID:
        raise OutboxPayloadError("outbound delivery key is unavailable")
    try:
        encoded = AESGCM(derive_outbox_key(encryption_secret)).decrypt(
            delivery.encryption_nonce,
            delivery.encrypted_payload,
            _associated_data(
                kind=delivery.kind,
                account_id=delivery.account_id,
                message_id=delivery.message_id,
            ),
        )
        payload = json.loads(encoded)
    except (InvalidTag, ValueError, TypeError):
        raise OutboxPayloadError("outbound delivery payload is invalid") from None
    if not isinstance(payload, dict):
        raise OutboxPayloadError("outbound delivery payload is invalid")
    return payload


def _new_message_id() -> str:
    return f"<{uuid4()}@patient-portal.carlos.invalid>"


def enqueue_password_reset_delivery(
    session: Session,
    *,
    result: PasswordResetRequestResult,
    reset_url: str,
    expires_in_seconds: int,
    encryption_secret: str,
) -> PatientPortalOutboundDelivery:
    if result.account_id is None or result.reset_token_id is None or result.recipient is None:
        raise PasswordResetTokenInvalidError()
    message_id = _new_message_id()
    ciphertext, nonce = _encrypt_payload(
        {
            "recipient": result.recipient,
            "reset_url": reset_url,
            "expires_in_seconds": expires_in_seconds,
        },
        encryption_secret=encryption_secret,
        kind=OUTBOX_KIND_PASSWORD_RESET,
        account_id=result.account_id,
        message_id=message_id,
    )
    delivery = PatientPortalOutboundDelivery(
        account_id=result.account_id,
        reset_token_id=result.reset_token_id,
        kind=OUTBOX_KIND_PASSWORD_RESET,
        status=OUTBOX_STATUS_PENDING,
        encrypted_payload=ciphertext,
        encryption_nonce=nonce,
        encryption_key_id=OUTBOX_KEY_ID,
        message_id=message_id,
        attempt_count=0,
        available_at=utc_now(),
        created_at=utc_now(),
    )
    session.add(delivery)
    session.flush()
    return delivery


def enqueue_contact_change_delivery(
    session: Session,
    *,
    account_id: int,
    recipient: str,
    encryption_secret: str,
) -> PatientPortalOutboundDelivery:
    message_id = _new_message_id()
    ciphertext, nonce = _encrypt_payload(
        {"recipient": recipient},
        encryption_secret=encryption_secret,
        kind=OUTBOX_KIND_CONTACT_CHANGE,
        account_id=account_id,
        message_id=message_id,
    )
    delivery = PatientPortalOutboundDelivery(
        account_id=account_id,
        kind=OUTBOX_KIND_CONTACT_CHANGE,
        status=OUTBOX_STATUS_PENDING,
        encrypted_payload=ciphertext,
        encryption_nonce=nonce,
        encryption_key_id=OUTBOX_KEY_ID,
        message_id=message_id,
        attempt_count=0,
        available_at=utc_now(),
        created_at=utc_now(),
    )
    session.add(delivery)
    session.flush()
    return delivery


def _claim_delivery(
    session: Session,
    *,
    lease_seconds: int,
    delivery_id: int | None = None,
) -> PatientPortalOutboundDelivery | None:
    now = utc_now()
    statement = (
        select(PatientPortalOutboundDelivery)
        .where(
            PatientPortalOutboundDelivery.available_at <= now,
            or_(
                PatientPortalOutboundDelivery.status == OUTBOX_STATUS_PENDING,
                (
                    (PatientPortalOutboundDelivery.status == OUTBOX_STATUS_PROCESSING)
                    & (PatientPortalOutboundDelivery.lease_expires_at <= now)
                ),
            ),
        )
        .order_by(PatientPortalOutboundDelivery.available_at, PatientPortalOutboundDelivery.id)
        .with_for_update(skip_locked=True)
        .limit(1)
    )
    if delivery_id is not None:
        statement = statement.where(PatientPortalOutboundDelivery.id == delivery_id)
    delivery = session.scalar(statement)
    if delivery is None:
        return None
    delivery.status = OUTBOX_STATUS_PROCESSING
    delivery.attempt_count += 1
    delivery.lease_expires_at = now + timedelta(seconds=lease_seconds)
    session.flush()
    return delivery


def _mark_terminal_reset_failure(session: Session, delivery: PatientPortalOutboundDelivery) -> None:
    if delivery.reset_token_id is None:
        return
    account = session.scalar(
        select(PatientPortalAccount)
        .where(PatientPortalAccount.id == delivery.account_id)
        .with_for_update()
    )
    reset_record = session.scalar(
        select(PatientPortalPasswordResetToken)
        .where(PatientPortalPasswordResetToken.id == delivery.reset_token_id)
        .with_for_update()
    )
    if (
        account is not None
        and reset_record is not None
        and reset_record.account_id == account.id
        and reset_record.status == PASSWORD_RESET_STATUS_PENDING
    ):
        reset_record.status = PASSWORD_RESET_STATUS_REVOKED
        session.flush()
    _record_reset_delivery_outcome_best_effort(
        session,
        delivery,
        outcome=AUDIT_OUTCOME_FAILURE,
    )


def _mark_terminal_contact_change_failure(
    session: Session,
    delivery: PatientPortalOutboundDelivery,
) -> None:
    """Record that a contact-change security notice was never delivered.

    The notice to the address a change moved away from is the only out-of-band alarm a patient gets,
    so exhausting the retry budget has to leave evidence. Without this the audit trail showed a
    successful contact update and nothing at all about the alarm that failed, which means a breach
    review cannot enumerate the patients who were never warned. The interactive development path in
    routes/portal.py already wrote this row; only the outbox-backed production path did not.
    """
    account = session.scalar(
        select(PatientPortalAccount).where(PatientPortalAccount.id == delivery.account_id)
    )
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
        outcome=AUDIT_OUTCOME_FAILURE,
        actor_type=AUDIT_ACTOR_TYPE_SYSTEM,
        actor=OUTBOX_AUDIT_ACTOR,
        clinic_id=account.clinic_id if account is not None else None,
        demographic_no=account.demographic_no if account is not None else None,
        account_id=delivery.account_id,
        reason=OUTBOX_REASON_DELIVERY_UNAVAILABLE,
    )
    session.flush()


def _record_reset_delivery_outcome_best_effort(
    session: Session,
    delivery: PatientPortalOutboundDelivery,
    *,
    outcome: str,
) -> bool:
    """Keep append-only audit failure from replacing the already-known delivery outcome."""
    try:
        with session.begin_nested():
            record_password_reset_delivery_outcome(
                session,
                result=PasswordResetRequestResult(
                    reset_token=None,
                    recipient=None,
                    reset_token_id=delivery.reset_token_id,
                    account_id=delivery.account_id,
                ),
                outcome=outcome,
            )
            session.flush()
    except PasswordResetTokenInvalidError:
        return False
    except SQLAlchemyError as exc:
        # Only the exception class is logged; no credential or exception message is emitted.
        # nosemgrep: python-logger-credential-disclosure -- logs only type(exc).__name__
        logger.error(  # NOSONAR - traceback details can contain database values
            "Password-reset delivery audit write failed: %s",
            type(exc).__name__,
        )
    return True


def _finish_delivery(
    session: Session,
    *,
    delivery_id: int,
    succeeded: bool,
    max_attempts: int,
    expected_attempt_count: int,
    failure_code: str | None = None,
) -> str:
    delivery = session.scalar(
        select(PatientPortalOutboundDelivery)
        .where(PatientPortalOutboundDelivery.id == delivery_id)
        .with_for_update()
    )
    if delivery is None:
        return OUTBOX_STATUS_FAILED
    if (
        delivery.status != OUTBOX_STATUS_PROCESSING
        or delivery.attempt_count != expected_attempt_count
    ):
        # An expired lease was reclaimed. The newer worker exclusively owns completion now.
        return delivery.status
    now = utc_now()
    delivery.lease_expires_at = None
    if succeeded:
        delivery.status = OUTBOX_STATUS_DELIVERED
        delivery.delivered_at = now
        delivery.last_failure_code = None
        if delivery.kind == OUTBOX_KIND_PASSWORD_RESET and delivery.reset_token_id is not None:
            if not _record_reset_delivery_outcome_best_effort(
                session,
                delivery,
                outcome=AUDIT_OUTCOME_SUCCESS,
            ):
                delivery.status = OUTBOX_STATUS_FAILED
                delivery.delivered_at = None
                delivery.last_failure_code = "reset_token_invalid"
        return delivery.status
    delivery.last_failure_code = (failure_code or "delivery_failed")[:64]
    if delivery.attempt_count >= max_attempts:
        delivery.status = OUTBOX_STATUS_FAILED
        if delivery.kind == OUTBOX_KIND_PASSWORD_RESET:
            _mark_terminal_reset_failure(session, delivery)
        elif delivery.kind == OUTBOX_KIND_CONTACT_CHANGE:
            _mark_terminal_contact_change_failure(session, delivery)
        return delivery.status
    delivery.status = OUTBOX_STATUS_PENDING
    delay_seconds = min(15 * 60, 2 ** min(delivery.attempt_count, 10))
    delivery.available_at = now + timedelta(seconds=delay_seconds)
    return delivery.status


def _renew_delivery_lease(
    session_factory: sessionmaker[Session],
    *,
    delivery_id: int,
    expected_attempt_count: int,
    lease_seconds: int,
) -> bool:
    with session_factory() as session, session.begin():
        result = session.execute(
            update(PatientPortalOutboundDelivery)
            .where(
                PatientPortalOutboundDelivery.id == delivery_id,
                PatientPortalOutboundDelivery.status == OUTBOX_STATUS_PROCESSING,
                PatientPortalOutboundDelivery.attempt_count == expected_attempt_count,
            )
            .values(lease_expires_at=utc_now() + timedelta(seconds=lease_seconds))
        )
        return result.rowcount == 1


def _renew_delivery_lease_until_stopped(
    session_factory: sessionmaker[Session],
    *,
    stop: Event,
    delivery_id: int,
    expected_attempt_count: int,
    lease_seconds: int,
) -> None:
    interval_seconds = max(0.1, lease_seconds / 3)
    while not stop.wait(interval_seconds):
        try:
            if not _renew_delivery_lease(
                session_factory,
                delivery_id=delivery_id,
                expected_attempt_count=expected_attempt_count,
                lease_seconds=lease_seconds,
            ):
                return
        except SQLAlchemyError as exc:
            # Keep database statement and parameter values out of logs.
            logger.error(  # NOSONAR - traceback details can contain database values
                "Outbound delivery lease renewal failed: %s",
                type(exc).__name__,
            )
            return


@contextmanager
def _delivery_lease_heartbeat(
    session_factory: sessionmaker[Session],
    *,
    delivery_id: int,
    expected_attempt_count: int,
    lease_seconds: int,
) -> Iterator[None]:
    stop = Event()
    heartbeat = Thread(
        target=_renew_delivery_lease_until_stopped,
        name=f"portal-outbox-lease-{delivery_id}",
        daemon=True,
        kwargs={
            "session_factory": session_factory,
            "stop": stop,
            "delivery_id": delivery_id,
            "expected_attempt_count": expected_attempt_count,
            "lease_seconds": lease_seconds,
        },
    )
    heartbeat.start()
    try:
        yield
    finally:
        stop.set()
        heartbeat.join(timeout=max(1.0, lease_seconds / 3 + 1))


def process_one_delivery(
    session_factory: sessionmaker[Session],
    *,
    email_sender: PortalEmailSender | None,
    encryption_secret: str,
    max_attempts: int,
    lease_seconds: int,
    delivery_id: int | None = None,
) -> DeliveryRunResult | None:
    with session_factory() as session:
        with session.begin():
            delivery = _claim_delivery(
                session,
                lease_seconds=lease_seconds,
                delivery_id=delivery_id,
            )
            if delivery is None:
                return None
            claimed_id = delivery.id
            claimed_attempt_count = delivery.attempt_count
            kind = delivery.kind
            reset_token_id = delivery.reset_token_id
            message_id = delivery.message_id
            try:
                payload = _decrypt_payload(delivery, encryption_secret=encryption_secret)
            except OutboxPayloadError:
                payload = None

    failure_code: str | None = None
    succeeded = False
    with _delivery_lease_heartbeat(
        session_factory,
        delivery_id=claimed_id,
        expected_attempt_count=claimed_attempt_count,
        lease_seconds=lease_seconds,
    ):
        if payload is None:
            failure_code = "payload_invalid"
        elif email_sender is None:
            failure_code = "email_unconfigured"
        else:
            try:
                recipient = payload.get("recipient")
                if not isinstance(recipient, str) or not recipient:
                    raise OutboxPayloadError("recipient is invalid")
                if kind == OUTBOX_KIND_PASSWORD_RESET:
                    with session_factory() as validation_session:
                        valid_reset = validation_session.scalar(
                            select(PatientPortalPasswordResetToken.id).where(
                                PatientPortalPasswordResetToken.id == reset_token_id,
                                PatientPortalPasswordResetToken.status
                                == PASSWORD_RESET_STATUS_PENDING,
                                PatientPortalPasswordResetToken.expires_at > utc_now(),
                            )
                        )
                    reset_url = payload.get("reset_url")
                    expires_in_seconds = payload.get("expires_in_seconds")
                    if valid_reset is None:
                        raise OutboxPayloadError("reset token is no longer deliverable")
                    if not isinstance(reset_url, str) or not isinstance(expires_in_seconds, int):
                        raise OutboxPayloadError("reset payload is invalid")
                    email_sender.send_password_reset(
                        recipient=recipient,
                        reset_url=reset_url,
                        expires_in_seconds=expires_in_seconds,
                        message_id=message_id,
                    )
                elif kind == OUTBOX_KIND_CONTACT_CHANGE:
                    email_sender.send_contact_change_notice(
                        recipient=recipient,
                        message_id=message_id,
                    )
                else:
                    raise OutboxPayloadError("delivery kind is invalid")
                succeeded = True
            except PortalEmailDeliveryError as exc:
                failure_code = type(exc).__name__
            except OutboxPayloadError:
                failure_code = "payload_invalid"

    with session_factory() as session:
        with session.begin():
            final_status = _finish_delivery(
                session,
                delivery_id=claimed_id,
                succeeded=succeeded,
                max_attempts=max_attempts,
                expected_attempt_count=claimed_attempt_count,
                failure_code=failure_code,
            )
    if not succeeded:
        logger.error("Outbound delivery attempt failed: %s", failure_code or "delivery_failed")
    return DeliveryRunResult(delivery_id=claimed_id, status=final_status)
