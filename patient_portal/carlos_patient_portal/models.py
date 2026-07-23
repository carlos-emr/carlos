from datetime import UTC, datetime

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from carlos_patient_portal.database import Base

INVITE_STATUS_PENDING = "pending"
INVITE_STATUS_REVOKED = "revoked"
INVITE_STATUS_ACCEPTED = "accepted"
ACCOUNT_STATUS_ACTIVE = "active"
MAX_CLINIC_ID_LENGTH = 64
MAX_EMAIL_LENGTH = 254
MIN_USERNAME_LENGTH = 3
MAX_USERNAME_LENGTH = 64
HASH_LENGTH = 64


def utc_now() -> datetime:
    return datetime.now(UTC)


class PatientPortalAccount(Base):
    """Patient-owned portal account created after invite activation."""

    __tablename__ = "patient_portal_accounts"
    __table_args__ = (
        CheckConstraint(
            "demographic_no > 0",
            name="ck_patient_portal_accounts_demographic_no_positive",
        ),
        CheckConstraint(
            f"length(clinic_id) between 1 and {MAX_CLINIC_ID_LENGTH}",
            name="ck_patient_portal_accounts_clinic_id_length",
        ),
        CheckConstraint(
            f"length(username) between {MIN_USERNAME_LENGTH} and {MAX_USERNAME_LENGTH}",
            name="ck_patient_portal_accounts_username_length",
        ),
        CheckConstraint(
            "status in ('active')",
            name="ck_patient_portal_accounts_status",
        ),
        UniqueConstraint(
            "clinic_id",
            "demographic_no",
            name="ux_patient_portal_accounts_clinic_demographic",
        ),
        UniqueConstraint("username", name="ux_patient_portal_accounts_username"),
        Index("ix_patient_portal_accounts_status", "status"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    clinic_id: Mapped[str] = mapped_column(String(MAX_CLINIC_ID_LENGTH), nullable=False)
    demographic_no: Mapped[int] = mapped_column(Integer, nullable=False)
    username: Mapped[str] = mapped_column(String(MAX_USERNAME_LENGTH), nullable=False)
    email: Mapped[str] = mapped_column(String(MAX_EMAIL_LENGTH), nullable=False)
    password_hash: Mapped[str] = mapped_column(String(512), nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
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
    password_updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        nullable=False,
    )


class PatientPortalInvite(Base):
    """Staff-created invite for a patient portal account."""

    __tablename__ = "patient_portal_invites"
    __table_args__ = (
        CheckConstraint(
            f"length(clinic_id) between 1 and {MAX_CLINIC_ID_LENGTH}",
            name="ck_patient_portal_invites_clinic_id_length",
        ),
        CheckConstraint(
            "demographic_no > 0",
            name="ck_patient_portal_invites_demographic_no_positive",
        ),
        CheckConstraint(
            "sent_count >= 0",
            name="ck_patient_portal_invites_sent_count_non_negative",
        ),
        CheckConstraint(
            f"length(token_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_invites_token_hash_length",
        ),
        CheckConstraint(
            f"proof_email_hash is null or length(proof_email_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_invites_proof_email_hash_length",
        ),
        CheckConstraint(
            f"proof_date_of_birth_hash is null or length(proof_date_of_birth_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_invites_proof_date_of_birth_hash_length",
        ),
        CheckConstraint(
            f"proof_health_card_hash is null or length(proof_health_card_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_invites_proof_health_card_hash_length",
        ),
        CheckConstraint(
            "expires_at > created_at",
            name="ck_patient_portal_invites_expires_after_created",
        ),
        CheckConstraint(
            "status in ('pending', 'revoked', 'accepted')",
            name="ck_patient_portal_invites_status",
        ),
        Index(
            "ix_patient_portal_invites_clinic_demographic",
            "clinic_id",
            "demographic_no",
        ),
        Index("ix_patient_portal_invites_clinic_expires_at", "clinic_id", "expires_at"),
        Index("ix_patient_portal_invites_clinic_status", "clinic_id", "status"),
        Index("ux_patient_portal_invites_token_hash", "token_hash", unique=True),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    clinic_id: Mapped[str] = mapped_column(String(MAX_CLINIC_ID_LENGTH), nullable=False)
    demographic_no: Mapped[int] = mapped_column(Integer, nullable=False)
    token_hash: Mapped[str] = mapped_column(String(HASH_LENGTH), nullable=False)
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
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    revoked_by: Mapped[str | None] = mapped_column(String(128), nullable=True)
    proof_email_hash: Mapped[str | None] = mapped_column(String(HASH_LENGTH), nullable=True)
    proof_date_of_birth_hash: Mapped[str | None] = mapped_column(
        String(HASH_LENGTH),
        nullable=True,
    )
    proof_health_card_hash: Mapped[str | None] = mapped_column(
        String(HASH_LENGTH),
        nullable=True,
    )
    accepted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    accepted_account_id: Mapped[int | None] = mapped_column(
        ForeignKey("patient_portal_accounts.id"),
        nullable=True,
    )
