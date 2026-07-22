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


class InviteNotFoundError(Exception):
    """Raised when an invite id does not exist."""


class RevokedInviteError(Exception):
    """Raised when a revoked invite cannot be reused."""


def create_invite_token() -> str:
    return token_urlsafe(INVITE_TOKEN_BYTES)


def hash_invite_token(token: str) -> str:
    return sha256(token.encode("utf-8")).hexdigest()


def create_invite(
    session: Session,
    demographic_no: int,
    actor: str,
) -> tuple[PatientPortalInvite, str]:
    invite_token = create_invite_token()
    now = utc_now()
    invite = PatientPortalInvite(
        demographic_no=demographic_no,
        token_hash=hash_invite_token(invite_token),
        status=INVITE_STATUS_PENDING,
        created_by=actor,
        created_at=now,
        updated_at=now,
        sent_count=1,
        last_sent_at=now,
        last_sent_by=actor,
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
    limit: int = 10,
    offset: int = 0,
) -> list[PatientPortalInvite]:
    statement = select(PatientPortalInvite)
    if demographic_no is not None:
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

    invite_token = create_invite_token()
    now = utc_now()
    invite.token_hash = hash_invite_token(invite_token)
    invite.status = INVITE_STATUS_PENDING
    invite.sent_count += 1
    invite.last_sent_at = now
    invite.last_sent_by = actor
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
        now = utc_now()
        invite.status = INVITE_STATUS_REVOKED
        invite.revoked_at = now
        invite.revoked_by = actor
        invite.updated_at = now
        session.commit()
        session.refresh(invite)
    return invite
