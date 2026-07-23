from datetime import timedelta
from hashlib import sha256
from secrets import token_urlsafe

from sqlalchemy import desc, select
from sqlalchemy.orm import Session

from carlos_patient_portal.models import (
    INVITE_STATUS_PENDING,
    INVITE_STATUS_REVOKED,
    PatientPortalInvite,
    utc_now,
)

INVITE_TOKEN_BYTES = 32
DEFAULT_INVITE_TTL = timedelta(days=7)
DEFAULT_INVITE_LIST_LIMIT = 10
MAX_INVITE_LIST_LIMIT = 100
MAX_ACTOR_LENGTH = 128


class InviteNotFoundError(Exception):
    """Raised when an invite id does not exist."""


class RevokedInviteError(Exception):
    """Raised when a revoked invite cannot be reused."""


def create_invite_token() -> str:
    return token_urlsafe(INVITE_TOKEN_BYTES)


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


def create_invite(
    session: Session,
    demographic_no: int,
    actor: str,
) -> tuple[PatientPortalInvite, str]:
    validate_demographic_no(demographic_no)
    normalized_actor = normalize_staff_actor(actor)
    invite_token = create_invite_token()
    now = utc_now()
    invite = PatientPortalInvite(
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
    )
    session.add(invite)
    session.commit()
    session.refresh(invite)
    return invite, invite_token


def get_invite(session: Session, invite_id: int) -> PatientPortalInvite:
    invite = session.get(PatientPortalInvite, invite_id)
    if invite is None:
        raise InviteNotFoundError()
    return invite


def list_invites(
    session: Session,
    demographic_no: int | None = None,
    limit: int = DEFAULT_INVITE_LIST_LIMIT,
    offset: int = 0,
) -> list[PatientPortalInvite]:
    validate_list_pagination(limit, offset)
    statement = select(PatientPortalInvite)
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
) -> tuple[PatientPortalInvite, str]:
    invite = get_invite(session, invite_id)
    if invite.status == INVITE_STATUS_REVOKED:
        raise RevokedInviteError()

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
    session.commit()
    session.refresh(invite)
    return invite, invite_token


def revoke_invite(
    session: Session,
    invite_id: int,
    actor: str,
) -> PatientPortalInvite:
    invite = get_invite(session, invite_id)
    if invite.status != INVITE_STATUS_REVOKED:
        normalized_actor = normalize_staff_actor(actor)
        now = utc_now()
        invite.status = INVITE_STATUS_REVOKED
        invite.revoked_at = now
        invite.revoked_by = normalized_actor
        invite.updated_at = now
        session.commit()
        session.refresh(invite)
    return invite
