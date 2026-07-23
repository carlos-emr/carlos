from datetime import UTC, datetime

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from carlos_patient_portal.database import Base

INVITE_STATUS_PENDING = "pending"
INVITE_STATUS_REVOKED = "revoked"
INVITE_STATUS_ACCEPTED = "accepted"
ACCOUNT_STATUS_ACTIVE = "active"
AUDIT_ACTOR_TYPE_PATIENT = "patient"
AUDIT_ACTOR_TYPE_STAFF = "staff"
AUDIT_EVENT_ACTIVATION = "activation"
AUDIT_EVENT_INVITE_CREATE = "invite.create"
AUDIT_EVENT_INVITE_RESEND = "invite.resend"
AUDIT_EVENT_INVITE_REVOKE = "invite.revoke"
AUDIT_OUTCOME_FAILURE = "failure"
AUDIT_OUTCOME_SUCCESS = "success"
AUDIT_OUTCOME_THROTTLED = "throttled"
MAX_CLINIC_ID_LENGTH = 64
MAX_EMAIL_LENGTH = 254
MIN_USERNAME_LENGTH = 3
MAX_USERNAME_LENGTH = 64
HASH_LENGTH = 64
MAX_AUDIT_EVENT_TYPE_LENGTH = 64
MAX_AUDIT_OUTCOME_LENGTH = 16
MAX_AUDIT_ACTOR_TYPE_LENGTH = 16
MAX_AUDIT_REASON_LENGTH = 64


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
        CheckConstraint(
            (
                "status = 'accepted' or "
                "(accepted_at is null and accepted_account_id is null)"
            ),
            name="ck_patient_portal_invites_nonaccepted_fields_null",
        ),
        CheckConstraint(
            (
                "status != 'accepted' or "
                "(accepted_at is not null and accepted_account_id is not null)"
            ),
            name="ck_patient_portal_invites_accepted_fields_present",
        ),
        CheckConstraint(
            "status = 'revoked' or (revoked_at is null and revoked_by is null)",
            name="ck_patient_portal_invites_nonrevoked_fields_null",
        ),
        CheckConstraint(
            "status != 'revoked' or (revoked_at is not null and revoked_by is not null)",
            name="ck_patient_portal_invites_revoked_fields_present",
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


class PatientPortalAuditEvent(Base):
    """Security-relevant event trail for portal-owned workflows."""

    __tablename__ = "patient_portal_audit_events"
    __table_args__ = (
        CheckConstraint(
            "clinic_id is null or length(clinic_id) between 1 and 64",
            name="ck_patient_portal_audit_events_clinic_id_length",
        ),
        CheckConstraint(
            "demographic_no is null or demographic_no > 0",
            name="ck_patient_portal_audit_events_demographic_no_positive",
        ),
        CheckConstraint(
            (
                "event_type in "
                "('activation', 'invite.create', 'invite.resend', 'invite.revoke')"
            ),
            name="ck_patient_portal_audit_events_event_type",
        ),
        CheckConstraint(
            "outcome in ('success', 'failure', 'throttled')",
            name="ck_patient_portal_audit_events_outcome",
        ),
        CheckConstraint(
            "actor_type in ('patient', 'staff')",
            name="ck_patient_portal_audit_events_actor_type",
        ),
        CheckConstraint(
            f"invite_token_hash is null or length(invite_token_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_audit_events_invite_token_hash_length",
        ),
        CheckConstraint(
            f"client_reference_hash is null or length(client_reference_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_audit_events_client_reference_hash_length",
        ),
        CheckConstraint(
            "length(event_type) between 1 and 64",
            name="ck_patient_portal_audit_events_event_type_length",
        ),
        CheckConstraint(
            "length(outcome) between 1 and 16",
            name="ck_patient_portal_audit_events_outcome_length",
        ),
        CheckConstraint(
            "length(actor_type) between 1 and 16",
            name="ck_patient_portal_audit_events_actor_type_length",
        ),
        CheckConstraint(
            "reason is null or length(reason) between 1 and 64",
            name="ck_patient_portal_audit_events_reason_length",
        ),
        Index(
            "ix_patient_portal_audit_events_activation_invite",
            "event_type",
            "invite_token_hash",
            "created_at",
        ),
        Index(
            "ix_patient_portal_audit_events_activation_client",
            "event_type",
            "client_reference_hash",
            "created_at",
        ),
        Index("ix_patient_portal_audit_events_clinic_created", "clinic_id", "created_at"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    clinic_id: Mapped[str | None] = mapped_column(String(MAX_CLINIC_ID_LENGTH), nullable=True)
    event_type: Mapped[str] = mapped_column(String(MAX_AUDIT_EVENT_TYPE_LENGTH), nullable=False)
    outcome: Mapped[str] = mapped_column(String(MAX_AUDIT_OUTCOME_LENGTH), nullable=False)
    actor_type: Mapped[str] = mapped_column(String(MAX_AUDIT_ACTOR_TYPE_LENGTH), nullable=False)
    actor: Mapped[str | None] = mapped_column(String(128), nullable=True)
    demographic_no: Mapped[int | None] = mapped_column(Integer, nullable=True)
    invite_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    account_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    invite_token_hash: Mapped[str | None] = mapped_column(String(HASH_LENGTH), nullable=True)
    client_reference_hash: Mapped[str | None] = mapped_column(String(HASH_LENGTH), nullable=True)
    reason: Mapped[str | None] = mapped_column(String(MAX_AUDIT_REASON_LENGTH), nullable=True)
    detail: Mapped[str | None] = mapped_column(Text(), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        nullable=False,
    )
