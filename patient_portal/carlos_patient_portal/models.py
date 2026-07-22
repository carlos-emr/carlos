from datetime import UTC, datetime

from sqlalchemy import DateTime, Index, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from carlos_patient_portal.database import Base

INVITE_STATUS_PENDING = "pending"
INVITE_STATUS_REVOKED = "revoked"


def utc_now() -> datetime:
    return datetime.now(UTC)


class PatientPortalInvite(Base):
    """Staff-created invite for a patient portal account."""

    __tablename__ = "patient_portal_invites"
    __table_args__ = (
        Index("ix_patient_portal_invites_demographic_no", "demographic_no"),
        Index("ix_patient_portal_invites_status", "status"),
        Index("ux_patient_portal_invites_token_hash", "token_hash", unique=True),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    demographic_no: Mapped[int] = mapped_column(Integer, nullable=False)
    token_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
    created_by: Mapped[str] = mapped_column(String(128), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        nullable=False,
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        onupdate=utc_now,
        nullable=False,
    )
    sent_count: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    last_sent_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    last_sent_by: Mapped[str] = mapped_column(String(128), nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    revoked_by: Mapped[str | None] = mapped_column(String(128), nullable=True)
