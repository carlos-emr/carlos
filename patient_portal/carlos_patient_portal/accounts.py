from datetime import UTC, datetime

from argon2 import PasswordHasher
from sqlalchemy import select
from sqlalchemy.orm import Session

from carlos_patient_portal.credentials import validate_password, validate_username
from carlos_patient_portal.identity import IdentityProof, normalize_email, verify_identity_proof
from carlos_patient_portal.invites import hash_invite_token
from carlos_patient_portal.models import (
    ACCOUNT_STATUS_ACTIVE,
    INVITE_STATUS_ACCEPTED,
    INVITE_STATUS_PENDING,
    PatientPortalAccount,
    PatientPortalInvite,
    utc_now,
)

password_hasher = PasswordHasher()


class ActivationError(Exception):
    """Raised when invite activation details do not match an activatable invite."""


class UsernameUnavailableError(Exception):
    """Raised when the requested username is already in use."""


def is_expired(expires_at: datetime, now: datetime) -> bool:
    comparable_expires_at = expires_at
    comparable_now = now
    if comparable_expires_at.tzinfo is None:
        comparable_expires_at = comparable_expires_at.replace(tzinfo=UTC)
    if comparable_now.tzinfo is None:
        comparable_now = comparable_now.replace(tzinfo=UTC)
    return comparable_expires_at <= comparable_now


def activate_patient_account(
    session: Session,
    *,
    invite_code: str,
    identity_proof: IdentityProof,
    username: str,
    password: str,
    proof_secret: str,
) -> PatientPortalAccount:
    normalized_invite_code = invite_code.strip()
    if not normalized_invite_code:
        raise ActivationError()

    normalized_username = validate_username(username)
    validate_password(password)
    normalized_email = normalize_email(identity_proof.email)

    invite = session.scalar(
        select(PatientPortalInvite).where(
            PatientPortalInvite.token_hash == hash_invite_token(normalized_invite_code)
        )
    )
    if (
        invite is None
        or invite.status != INVITE_STATUS_PENDING
        or is_expired(invite.expires_at, utc_now())
        or not verify_identity_proof(
            identity_proof,
            proof_secret,
            email_hash=invite.proof_email_hash,
            date_of_birth_hash=invite.proof_date_of_birth_hash,
            health_card_hash=invite.proof_health_card_hash,
        )
    ):
        raise ActivationError()

    existing_account = session.scalar(
        select(PatientPortalAccount.id).where(
            PatientPortalAccount.clinic_id == invite.clinic_id,
            PatientPortalAccount.demographic_no == invite.demographic_no,
        )
    )
    if existing_account is not None:
        raise ActivationError()

    existing_username = session.scalar(
        select(PatientPortalAccount.id).where(PatientPortalAccount.username == normalized_username)
    )
    if existing_username is not None:
        raise UsernameUnavailableError()

    now = utc_now()
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
    session.add(account)
    session.flush()

    invite.status = INVITE_STATUS_ACCEPTED
    invite.accepted_at = now
    invite.accepted_account_id = account.id
    invite.updated_at = now
    session.flush()
    return account
