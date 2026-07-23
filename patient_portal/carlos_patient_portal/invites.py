from datetime import timedelta
from hashlib import sha256
from secrets import token_urlsafe

from sqlalchemy import desc, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.identity import IdentityProof, build_identity_hashes
from carlos_patient_portal.models import (
    AUDIT_ACTOR_TYPE_STAFF,
    AUDIT_EVENT_INVITE_CREATE,
    AUDIT_EVENT_INVITE_RESEND,
    AUDIT_EVENT_INVITE_REVOKE,
    AUDIT_OUTCOME_SUCCESS,
    IDENTITY_PROOF_HASH_VERSION,
    INVITE_STATUS_ACCEPTED,
    INVITE_STATUS_PENDING,
    INVITE_STATUS_REVOKED,
    MAX_CLINIC_ID_LENGTH,
    PatientPortalAccount,
    PatientPortalInvite,
    utc_now,
)

INVITE_TOKEN_BYTES = 32
DEFAULT_INVITE_TTL = timedelta(days=7)
DEFAULT_INVITE_LIST_LIMIT = 10
MAX_INVITE_LIST_LIMIT = 100
MAX_ACTOR_LENGTH = 128
PROOF_SALT_BYTES = 16


class AccountAlreadyExistsError(Exception):
    """Raised when a patient already has a portal account."""


class PendingInviteExistsError(Exception):
    """Raised when another request creates a pending invite first."""


class InviteNotFoundError(Exception):
    """Raised when an invite id does not exist."""


class RevokedInviteError(Exception):
    """Raised when a revoked invite cannot be reused."""


class AcceptedInviteError(Exception):
    """Raised when an accepted invite cannot be reused."""


def create_invite_token() -> str:
    return token_urlsafe(INVITE_TOKEN_BYTES)


def create_proof_salt() -> str:
    return token_urlsafe(PROOF_SALT_BYTES)


def normalize_clinic_id(clinic_id: str) -> str:
    normalized_clinic_id = clinic_id.strip()
    if not normalized_clinic_id:
        raise ValueError("clinic_id must not be blank")
    if len(normalized_clinic_id) > MAX_CLINIC_ID_LENGTH:
        raise ValueError(f"clinic_id must be {MAX_CLINIC_ID_LENGTH} characters or fewer")
    return normalized_clinic_id


def normalize_staff_actor(actor: str) -> str:
    normalized_actor = actor.strip()
    if not normalized_actor:
        raise ValueError("actor must not be blank")
    if len(normalized_actor) > MAX_ACTOR_LENGTH:
        raise ValueError(f"actor must be {MAX_ACTOR_LENGTH} characters or fewer")
    return normalized_actor


def validate_demographic_no(demographic_no: int) -> None:
    if demographic_no <= 0:
        raise ValueError("demographic_no must be positive")


def validate_list_pagination(limit: int, offset: int) -> None:
    if limit < 1 or limit > MAX_INVITE_LIST_LIMIT:
        raise ValueError(f"limit must be between 1 and {MAX_INVITE_LIST_LIMIT}")
    if offset < 0:
        raise ValueError("offset must be zero or greater")


def hash_invite_token(token: str) -> str:
    return sha256(token.encode("utf-8")).hexdigest()


def patient_has_account(
    session: Session,
    *,
    clinic_id: str,
    demographic_no: int,
) -> bool:
    return (
        session.scalar(
            select(PatientPortalAccount.id).where(
                PatientPortalAccount.clinic_id == clinic_id,
                PatientPortalAccount.demographic_no == demographic_no,
            )
        )
        is not None
    )


def revoke_pending_invites_for_patient(
    session: Session,
    *,
    clinic_id: str,
    demographic_no: int,
    actor: str,
) -> None:
    now = utc_now()
    pending_invites = list(
        session.scalars(
            select(PatientPortalInvite)
            .where(
                PatientPortalInvite.clinic_id == clinic_id,
                PatientPortalInvite.demographic_no == demographic_no,
                PatientPortalInvite.status == INVITE_STATUS_PENDING,
            )
            .with_for_update()
        )
    )
    for invite in pending_invites:
        invite.status = INVITE_STATUS_REVOKED
        invite.revoked_at = now
        invite.revoked_by = actor
        invite.updated_at = now
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_INVITE_REVOKE,
            outcome=AUDIT_OUTCOME_SUCCESS,
            actor_type=AUDIT_ACTOR_TYPE_STAFF,
            actor=actor,
            clinic_id=clinic_id,
            demographic_no=demographic_no,
            invite_id=invite.id,
            reason="replaced_by_new_invite",
        )


def create_invite(
    session: Session,
    demographic_no: int,
    actor: str,
    *,
    identity_proof: IdentityProof,
    proof_secret: str,
    clinic_id: str = "default",
) -> tuple[PatientPortalInvite, str]:
    validate_demographic_no(demographic_no)
    normalized_clinic_id = normalize_clinic_id(clinic_id)
    normalized_actor = normalize_staff_actor(actor)
    if not proof_secret or not proof_secret.strip():
        raise ValueError("proof_secret must not be blank")
    proof_salt = create_proof_salt()
    proof_hashes = build_identity_hashes(identity_proof, proof_secret, proof_salt)

    if patient_has_account(
        session,
        clinic_id=normalized_clinic_id,
        demographic_no=demographic_no,
    ):
        raise AccountAlreadyExistsError()
    revoke_pending_invites_for_patient(
        session,
        clinic_id=normalized_clinic_id,
        demographic_no=demographic_no,
        actor=normalized_actor,
    )
    if patient_has_account(
        session,
        clinic_id=normalized_clinic_id,
        demographic_no=demographic_no,
    ):
        raise AccountAlreadyExistsError()

    invite_token = create_invite_token()
    now = utc_now()
    invite = PatientPortalInvite(
        clinic_id=normalized_clinic_id,
        demographic_no=demographic_no,
        token_hash=hash_invite_token(invite_token),
        status=INVITE_STATUS_PENDING,
        created_by=normalized_actor,
        created_at=now,
        updated_at=now,
        sent_count=1,
        last_sent_at=now,
        last_sent_by=normalized_actor,
        expires_at=now + DEFAULT_INVITE_TTL,
        proof_salt=proof_salt,
        proof_hash_version=IDENTITY_PROOF_HASH_VERSION,
        **proof_hashes,
    )
    try:
        with session.begin_nested():
            session.add(invite)
            session.flush()
    except IntegrityError as exc:
        raise PendingInviteExistsError() from exc
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_INVITE_CREATE,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_STAFF,
        actor=normalized_actor,
        clinic_id=normalized_clinic_id,
        demographic_no=demographic_no,
        invite_id=invite.id,
    )
    return invite, invite_token


def get_invite(
    session: Session,
    invite_id: int,
    *,
    clinic_id: str | None = None,
    lock: bool = False,
) -> PatientPortalInvite:
    statement = select(PatientPortalInvite).where(PatientPortalInvite.id == invite_id)
    if lock:
        statement = statement.with_for_update()
    invite = session.scalar(statement)
    if invite is None or (
        clinic_id is not None and invite.clinic_id != normalize_clinic_id(clinic_id)
    ):
        raise InviteNotFoundError()
    return invite


def list_invites(
    session: Session,
    demographic_no: int | None = None,
    limit: int = DEFAULT_INVITE_LIST_LIMIT,
    offset: int = 0,
    *,
    clinic_id: str = "default",
) -> list[PatientPortalInvite]:
    validate_list_pagination(limit, offset)
    normalized_clinic_id = normalize_clinic_id(clinic_id)
    statement = select(PatientPortalInvite).where(
        PatientPortalInvite.clinic_id == normalized_clinic_id
    )
    if demographic_no is not None:
        validate_demographic_no(demographic_no)
        statement = statement.where(PatientPortalInvite.demographic_no == demographic_no)
    statement = statement.order_by(
        desc(PatientPortalInvite.created_at),
        desc(PatientPortalInvite.id),
    )
    return list(session.scalars(statement.offset(offset).limit(limit)))


def resend_invite(
    session: Session,
    invite_id: int,
    actor: str,
    *,
    clinic_id: str | None = None,
) -> tuple[PatientPortalInvite, str]:
    invite = get_invite(session, invite_id, clinic_id=clinic_id, lock=True)
    if invite.status == INVITE_STATUS_REVOKED:
        raise RevokedInviteError()
    if invite.status == INVITE_STATUS_ACCEPTED:
        raise AcceptedInviteError()

    normalized_actor = normalize_staff_actor(actor)
    invite_token = create_invite_token()
    now = utc_now()
    invite.token_hash = hash_invite_token(invite_token)
    invite.status = INVITE_STATUS_PENDING
    invite.sent_count += 1
    invite.last_sent_at = now
    invite.last_sent_by = normalized_actor
    invite.expires_at = now + DEFAULT_INVITE_TTL
    invite.updated_at = now
    session.flush()
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_INVITE_RESEND,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_STAFF,
        actor=normalized_actor,
        clinic_id=invite.clinic_id,
        demographic_no=invite.demographic_no,
        invite_id=invite.id,
    )
    return invite, invite_token


def revoke_invite(
    session: Session,
    invite_id: int,
    actor: str,
    *,
    clinic_id: str | None = None,
) -> PatientPortalInvite:
    invite = get_invite(session, invite_id, clinic_id=clinic_id, lock=True)
    if invite.status == INVITE_STATUS_ACCEPTED:
        raise AcceptedInviteError()
    if invite.status != INVITE_STATUS_REVOKED:
        normalized_actor = normalize_staff_actor(actor)
        now = utc_now()
        invite.status = INVITE_STATUS_REVOKED
        invite.revoked_at = now
        invite.revoked_by = normalized_actor
        invite.updated_at = now
        session.flush()
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_INVITE_REVOKE,
            outcome=AUDIT_OUTCOME_SUCCESS,
            actor_type=AUDIT_ACTOR_TYPE_STAFF,
            actor=normalized_actor,
            clinic_id=invite.clinic_id,
            demographic_no=invite.demographic_no,
            invite_id=invite.id,
        )
    return invite
