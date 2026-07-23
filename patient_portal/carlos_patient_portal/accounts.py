from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from argon2 import PasswordHasher
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from carlos_patient_portal.audit import (
    count_recent_activation_failures,
    record_activation_failure,
    record_audit_event,
)
from carlos_patient_portal.credentials import validate_password, validate_username
from carlos_patient_portal.identity import IdentityProof, normalize_email, verify_identity_proof
from carlos_patient_portal.invites import hash_invite_token
from carlos_patient_portal.models import (
    ACCOUNT_STATUS_ACTIVE,
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_EVENT_ACTIVATION,
    AUDIT_OUTCOME_SUCCESS,
    AUDIT_OUTCOME_THROTTLED,
    INVITE_STATUS_ACCEPTED,
    INVITE_STATUS_PENDING,
    PatientPortalAccount,
    PatientPortalInvite,
    utc_now,
)

password_hasher = PasswordHasher(
    time_cost=3,
    memory_cost=65536,
    parallelism=4,
    hash_len=32,
    salt_len=16,
)
ACTIVATION_REASON_INTEGRITY_CONFLICT = "integrity_conflict"
ACTIVATION_REASON_INVALID_DETAILS = "invalid_details"
ACTIVATION_REASON_USERNAME_UNAVAILABLE = "username_unavailable"
ACTIVATION_REASON_RATE_LIMITED = "rate_limited"


class ActivationError(Exception):
    """Raised when invite activation details do not match an activatable invite."""


class UsernameUnavailableError(Exception):
    """Raised when the requested username is already in use."""


class ActivationThrottledError(Exception):
    """Raised when activation attempts are temporarily throttled."""


@dataclass(frozen=True)
class ActivationRateLimit:
    """Sliding-window activation failure limits."""

    failure_window: timedelta
    max_failures_per_invite: int
    max_failures_per_client: int


def is_expired(expires_at: datetime, now: datetime) -> bool:
    comparable_expires_at = expires_at
    comparable_now = now
    if comparable_expires_at.tzinfo is None:
        comparable_expires_at = comparable_expires_at.replace(tzinfo=UTC)
    if comparable_now.tzinfo is None:
        comparable_now = comparable_now.replace(tzinfo=UTC)
    return comparable_expires_at <= comparable_now


def find_account_id_for_patient(
    session: Session,
    *,
    clinic_id: str,
    demographic_no: int,
) -> int | None:
    return session.scalar(
        select(PatientPortalAccount.id).where(
            PatientPortalAccount.clinic_id == clinic_id,
            PatientPortalAccount.demographic_no == demographic_no,
        )
    )


def find_account_id_for_username(session: Session, username: str) -> int | None:
    return session.scalar(
        select(PatientPortalAccount.id).where(PatientPortalAccount.username == username)
    )


def enforce_activation_rate_limit(
    session: Session,
    *,
    invite_token_hash: str,
    client_reference_hash: str,
    rate_limit: ActivationRateLimit,
    now: datetime,
) -> None:
    failure_window_start = now - rate_limit.failure_window
    invite_failure_count = count_recent_activation_failures(
        session,
        since=failure_window_start,
        invite_token_hash=invite_token_hash,
    )
    client_failure_count = count_recent_activation_failures(
        session,
        since=failure_window_start,
        client_reference_hash=client_reference_hash,
    )
    if (
        invite_failure_count >= rate_limit.max_failures_per_invite
        or client_failure_count >= rate_limit.max_failures_per_client
    ):
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_ACTIVATION,
            outcome=AUDIT_OUTCOME_THROTTLED,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            invite_token_hash=invite_token_hash,
            client_reference_hash=client_reference_hash,
            reason=ACTIVATION_REASON_RATE_LIMITED,
        )
        raise ActivationThrottledError()


def activate_patient_account(
    session: Session,
    *,
    invite_code: str,
    identity_proof: IdentityProof,
    username: str,
    password: str,
    proof_secret: str,
    client_reference_hash: str,
    rate_limit: ActivationRateLimit,
) -> PatientPortalAccount:
    normalized_invite_code = invite_code.strip()
    if not normalized_invite_code:
        raise ActivationError()

    normalized_username = validate_username(username)
    validate_password(password)
    normalized_email = normalize_email(identity_proof.email)
    invite_token_hash = hash_invite_token(normalized_invite_code)
    now = utc_now()
    enforce_activation_rate_limit(
        session,
        invite_token_hash=invite_token_hash,
        client_reference_hash=client_reference_hash,
        rate_limit=rate_limit,
        now=now,
    )

    invite = session.scalar(
        select(PatientPortalInvite)
        .where(PatientPortalInvite.token_hash == invite_token_hash)
        .with_for_update()
    )
    if (
        invite is None
        or invite.status != INVITE_STATUS_PENDING
        or is_expired(invite.expires_at, now)
        or not verify_identity_proof(
            identity_proof,
            proof_secret,
            email_hash=invite.proof_email_hash,
            date_of_birth_hash=invite.proof_date_of_birth_hash,
            health_card_hash=invite.proof_health_card_hash,
        )
    ):
        record_activation_failure(
            session,
            invite_token_hash=invite_token_hash,
            client_reference_hash=client_reference_hash,
            reason=ACTIVATION_REASON_INVALID_DETAILS,
            clinic_id=invite.clinic_id if invite else None,
            demographic_no=invite.demographic_no if invite else None,
            invite_id=invite.id if invite else None,
        )
        raise ActivationError()

    existing_account = find_account_id_for_patient(
        session,
        clinic_id=invite.clinic_id,
        demographic_no=invite.demographic_no,
    )
    if existing_account is not None:
        record_activation_failure(
            session,
            invite_token_hash=invite_token_hash,
            client_reference_hash=client_reference_hash,
            reason=ACTIVATION_REASON_INVALID_DETAILS,
            clinic_id=invite.clinic_id,
            demographic_no=invite.demographic_no,
            invite_id=invite.id,
        )
        raise ActivationError()

    existing_username = find_account_id_for_username(session, normalized_username)
    if existing_username is not None:
        record_activation_failure(
            session,
            invite_token_hash=invite_token_hash,
            client_reference_hash=client_reference_hash,
            reason=ACTIVATION_REASON_USERNAME_UNAVAILABLE,
            clinic_id=invite.clinic_id,
            demographic_no=invite.demographic_no,
            invite_id=invite.id,
        )
        raise UsernameUnavailableError()

    account = PatientPortalAccount(
        clinic_id=invite.clinic_id,
        demographic_no=invite.demographic_no,
        username=normalized_username,
        email=normalized_email,
        password_hash=password_hasher.hash(password),
        status=ACCOUNT_STATUS_ACTIVE,
        created_at=now,
        updated_at=now,
        password_updated_at=now,
    )
    try:
        with session.begin_nested():
            session.add(account)
            session.flush()

            invite.status = INVITE_STATUS_ACCEPTED
            invite.accepted_at = now
            invite.accepted_account_id = account.id
            invite.updated_at = now
            session.flush()
    except IntegrityError as exc:
        reason = ACTIVATION_REASON_INTEGRITY_CONFLICT
        if find_account_id_for_username(session, normalized_username) is not None:
            reason = ACTIVATION_REASON_USERNAME_UNAVAILABLE
        record_activation_failure(
            session,
            invite_token_hash=invite_token_hash,
            client_reference_hash=client_reference_hash,
            reason=reason,
            clinic_id=invite.clinic_id,
            demographic_no=invite.demographic_no,
            invite_id=invite.id,
        )
        if reason == ACTIVATION_REASON_USERNAME_UNAVAILABLE:
            raise UsernameUnavailableError() from exc
        raise ActivationError() from exc
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_ACTIVATION,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        clinic_id=invite.clinic_id,
        demographic_no=invite.demographic_no,
        invite_id=invite.id,
        account_id=account.id,
        invite_token_hash=invite_token_hash,
        client_reference_hash=client_reference_hash,
    )
    return account
