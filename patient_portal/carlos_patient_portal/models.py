from datetime import UTC, datetime

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    LargeBinary,
    String,
    UniqueConstraint,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from carlos_patient_portal.database import Base

INVITE_STATUS_PENDING = "pending"
INVITE_STATUS_REVOKED = "revoked"
INVITE_STATUS_ACCEPTED = "accepted"
INVITE_STATUS_SUPERSEDED = "superseded"
ACCOUNT_STATUS_ACTIVE = "active"
ACCOUNT_STATUS_DISABLED = "disabled"
AUDIT_ACTOR_TYPE_PATIENT = "patient"
AUDIT_ACTOR_TYPE_STAFF = "staff"
# The portal itself, for events no human initiated — a configuration policy recorded at startup.
AUDIT_ACTOR_TYPE_SYSTEM = "system"
AUDIT_EVENT_ACTIVATION = "activation"
AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE = "account.contact_update"
AUDIT_EVENT_ACCOUNT_DISABLE = "account.disable"
AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_CONFIRM = "account.email_change_confirm"
AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_REQUEST = "account.email_change_request"
AUDIT_EVENT_ACCOUNT_ENABLE = "account.enable"
AUDIT_EVENT_ACCOUNT_LOCK = "account.lock"
AUDIT_EVENT_ACCOUNT_MFA_UPDATE = "account.mfa_update"
AUDIT_EVENT_ACCOUNT_PASSWORD_CHANGE = "account.password_change"
AUDIT_EVENT_ACCOUNT_UNLOCK = "account.unlock"
AUDIT_EVENT_INVITE_CREATE = "invite.create"
AUDIT_EVENT_INVITE_LIST = "invite.list"
AUDIT_EVENT_INVITE_RESEND = "invite.resend"
AUDIT_EVENT_INVITE_REVOKE = "invite.revoke"
AUDIT_EVENT_LOGIN = "login"
AUDIT_EVENT_MFA_CHALLENGE = "mfa.challenge"
AUDIT_EVENT_MFA_DELIVERY = "mfa.delivery"
AUDIT_EVENT_MFA_RESEND = "mfa.resend"
AUDIT_EVENT_MFA_VERIFY = "mfa.verify"
AUDIT_EVENT_PASSWORD_RESET_COMPLETE = "password_reset.complete"
AUDIT_EVENT_PASSWORD_RESET_DELIVERY = "password_reset.delivery"
AUDIT_EVENT_PASSWORD_RESET_REQUEST = "password_reset.request"
AUDIT_EVENT_RETENTION_POLICY_OVERRIDE = "retention.policy_override"
AUDIT_EVENT_SESSION_LOGOUT = "session.logout"
AUDIT_EVENT_STAFF_ACTION = "staff.action"
AUDIT_EVENT_FHIR_READ = "fhir.read"
AUDIT_EVENT_FHIR_SEARCH = "fhir.search"
AUDIT_EVENT_UNLOCK_SECRET_CREATE = "unlock_secret.create"
AUDIT_EVENT_UNLOCK_SECRET_LIST = "unlock_secret.list"
AUDIT_EVENT_UNLOCK_SECRET_PUBLISH = "unlock_secret.publish"
AUDIT_EVENT_UNLOCK_SECRET_READ = "unlock_secret.read"
AUDIT_EVENT_UNLOCK_SECRET_REVOKE = "unlock_secret.revoke"
AUDIT_OUTCOME_FAILURE = "failure"
AUDIT_OUTCOME_SUCCESS = "success"
AUDIT_OUTCOME_THROTTLED = "throttled"
MFA_DELIVERY_METHOD_EMAIL = "email"
MFA_DELIVERY_METHOD_SMS = "sms"
MFA_CHALLENGE_STATUS_CANCELLED = "cancelled"
MFA_CHALLENGE_STATUS_PENDING = "pending"
MFA_CHALLENGE_STATUS_VERIFIED = "verified"
PASSWORD_RESET_STATUS_PENDING = "pending"  # NOSONAR - workflow status, not a credential
PASSWORD_RESET_STATUS_REVOKED = "revoked"  # NOSONAR - workflow status, not a credential
PASSWORD_RESET_STATUS_USED = "used"
SESSION_REVOKED_REASON_LOGOUT = "logout"
SESSION_REVOKED_REASON_PASSWORD_CHANGE = "password_change"
SESSION_REVOKED_REASON_PASSWORD_RESET = "password_reset"
SESSION_REVOKED_REASON_ACCOUNT_DISABLED = "account_disabled"
EMAIL_CHANGE_STATUS_PENDING = "pending"
EMAIL_CHANGE_STATUS_CONFIRMED = "confirmed"
EMAIL_CHANGE_STATUS_REVOKED = "revoked"
OUTBOX_STATUS_PENDING = "pending"
OUTBOX_STATUS_PROCESSING = "processing"
OUTBOX_STATUS_DELIVERED = "delivered"
OUTBOX_STATUS_FAILED = "failed"
OUTBOX_KIND_PASSWORD_RESET = "password_reset"
OUTBOX_KIND_CONTACT_CHANGE = "contact_change"
CONTACT_REVIEW_STATUS_PENDING = "pending"
CONTACT_REVIEW_STATUS_REVIEWED = "reviewed"
CONTACT_REVIEW_DECISION_APPROVED = "approved"
CONTACT_REVIEW_DECISION_REJECTED = "rejected"
CONTACT_REVIEW_DECISION_SUPERSEDED = "superseded"
UNLOCK_SECRET_STATUS_PENDING = "pending"
UNLOCK_SECRET_STATUS_AVAILABLE = "available"
UNLOCK_SECRET_STATUS_ACTIVE = UNLOCK_SECRET_STATUS_AVAILABLE
UNLOCK_SECRET_STATUS_REVOKED = "revoked"
UNLOCK_SECRET_TYPE_EMAIL = "email"
UNLOCK_SECRET_TYPE_PDF = "pdf"
MAX_CLINIC_ID_LENGTH = 64
MAX_EMAIL_LENGTH = 254
MAX_PHONE_NUMBER_LENGTH = 32
MIN_USERNAME_LENGTH = 3
MAX_USERNAME_LENGTH = 64
HASH_LENGTH = 64
MAX_PROOF_SALT_LENGTH = 64
IDENTITY_PROOF_HASH_VERSION = "v1"
MAX_AUDIT_EVENT_TYPE_LENGTH = 64
MAX_AUDIT_OUTCOME_LENGTH = 16
MAX_AUDIT_ACTOR_TYPE_LENGTH = 16
MAX_AUDIT_ACTOR_ID_LENGTH = 128
MAX_AUDIT_RESOURCE_TYPE_LENGTH = 64
MAX_AUDIT_RESOURCE_ID_LENGTH = 128
MAX_AUDIT_REASON_LENGTH = 64
MAX_UNLOCK_SECRET_LABEL_LENGTH = 128
MAX_UNLOCK_SECRET_SOURCE_REFERENCE_LENGTH = 128
MAX_UNLOCK_SECRET_ACTOR_LENGTH = 128
MAX_UNLOCK_SECRET_STATUS_LENGTH = 16
MAX_UNLOCK_SECRET_TYPE_LENGTH = 16
MAX_UNLOCK_SECRET_ALGORITHM_LENGTH = 32
MAX_UNLOCK_SECRET_KEY_ID_LENGTH = 64
MAX_UNLOCK_SECRET_REVOKE_REASON_LENGTH = 64
UNLOCK_SECRET_NONCE_LENGTH = 12
OUTBOX_NONCE_LENGTH = 12
ACCOUNT_FOREIGN_KEY_TARGET = "patient_portal_accounts.id"
DEMOGRAPHIC_NO_POSITIVE_SQL = "demographic_no > 0"
EXPIRY_AFTER_CREATION_SQL = "expires_at > created_at"
PENDING_STATUS_SQL = "status = 'pending'"


def utc_now() -> datetime:
    return datetime.now(UTC)


class PatientPortalAccount(Base):
    """Patient-owned portal account created after invite activation."""

    __tablename__ = "patient_portal_accounts"
    __table_args__ = (
        CheckConstraint(
            DEMOGRAPHIC_NO_POSITIVE_SQL,
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
            "status in ('active', 'disabled')",
            name="ck_patient_portal_accounts_status",
        ),
        CheckConstraint(
            "phone_number is null or length(phone_number) between 1 and 32",
            name="ck_patient_portal_accounts_phone_number_length",
        ),
        CheckConstraint(
            "preferred_mfa_method in ('email', 'sms')",
            name="ck_patient_portal_accounts_preferred_mfa_method",
        ),
        CheckConstraint(
            "failed_login_count >= 0",
            name="ck_patient_portal_accounts_failed_login_count_non_negative",
        ),
        CheckConstraint(
            "failed_mfa_count >= 0",
            name="ck_patient_portal_accounts_failed_mfa_count_non_negative",
        ),
        CheckConstraint(
            "locked_by is null or length(locked_by) between 1 and 128",
            name="ck_patient_portal_accounts_locked_by_length",
        ),
        CheckConstraint(
            "(locked_at is null and locked_by is null) or "
            "(locked_at is not null and locked_by is not null)",
            name="ck_patient_portal_accounts_lock_fields_complete",
        ),
        CheckConstraint(
            "(status = 'active' and disabled_at is null and disabled_by is null) or "
            "(status = 'disabled' and disabled_at is not null and disabled_by is not null)",
            name="ck_patient_portal_accounts_disabled_fields_complete",
        ),
        UniqueConstraint(
            "clinic_id",
            "demographic_no",
            name="ux_patient_portal_accounts_clinic_demographic",
        ),
        # Scoped by clinic like every other uniqueness rule in this file. The deployment model is
        # one database per clinic, under which a global index would also work — but leaving this
        # one rule single-tenant made the schema disagree with itself, and it is the constraint a
        # future shared-database mode would silently collide on.
        UniqueConstraint("clinic_id", "username", name="ux_patient_portal_accounts_username"),
        Index("ix_patient_portal_accounts_status", "status"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    clinic_id: Mapped[str] = mapped_column(String(MAX_CLINIC_ID_LENGTH), nullable=False)
    demographic_no: Mapped[int] = mapped_column(Integer, nullable=False)
    username: Mapped[str] = mapped_column(String(MAX_USERNAME_LENGTH), nullable=False)
    email: Mapped[str] = mapped_column(String(MAX_EMAIL_LENGTH), nullable=False)
    phone_number: Mapped[str | None] = mapped_column(
        String(MAX_PHONE_NUMBER_LENGTH),
        nullable=True,
    )
    preferred_mfa_method: Mapped[str] = mapped_column(
        String(8),
        nullable=False,
        default=MFA_DELIVERY_METHOD_EMAIL,
    )
    password_hash: Mapped[str] = mapped_column(String(512), nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
    failed_login_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    failed_mfa_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    locked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    locked_by: Mapped[str | None] = mapped_column(String(128), nullable=True)
    locked_by_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    disabled_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    disabled_by: Mapped[str | None] = mapped_column(String(128), nullable=True)
    disabled_by_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    disabled_reason: Mapped[str | None] = mapped_column(String(64), nullable=True)
    force_password_reset: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    last_login_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    last_mfa_email_sent_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    last_mfa_sms_sent_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
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


class PatientPortalContactReviewRequest(Base):
    """Patient contact change waiting for staff review before CARLOS demographic sync."""

    __tablename__ = "patient_portal_contact_review_requests"
    __table_args__ = (
        CheckConstraint(
            f"length(clinic_id) between 1 and {MAX_CLINIC_ID_LENGTH}",
            name="ck_patient_portal_contact_review_requests_clinic_id_length",
        ),
        CheckConstraint(
            DEMOGRAPHIC_NO_POSITIVE_SQL,
            name="ck_pp_contact_review_demographic_positive",
        ),
        CheckConstraint(
            "status in ('pending', 'reviewed')",
            name="ck_patient_portal_contact_review_requests_status",
        ),
        CheckConstraint(
            f"length(email_before) between 1 and {MAX_EMAIL_LENGTH}",
            name="ck_patient_portal_contact_review_requests_email_before_length",
        ),
        CheckConstraint(
            f"length(email_after) between 1 and {MAX_EMAIL_LENGTH}",
            name="ck_patient_portal_contact_review_requests_email_after_length",
        ),
        CheckConstraint(
            "phone_number_before is null or length(phone_number_before) between 1 and 32",
            name="ck_patient_portal_contact_review_requests_phone_before_length",
        ),
        CheckConstraint(
            "phone_number_after is null or length(phone_number_after) between 1 and 32",
            name="ck_patient_portal_contact_review_requests_phone_after_length",
        ),
        CheckConstraint(
            "reviewed_by is null or length(reviewed_by) between 1 and 128",
            name="ck_patient_portal_contact_review_requests_reviewed_by_length",
        ),
        CheckConstraint(
            (
                "status = 'reviewed' or "
                "(reviewed_at is null and reviewed_by is null and review_decision is null)"
            ),
            name="ck_pp_contact_review_unreviewed_null",
        ),
        CheckConstraint(
            (
                "status != 'reviewed' or "
                "(reviewed_at is not null and reviewed_by is not null and "
                "review_decision is not null and "
                "review_decision in ('approved', 'rejected', 'superseded'))"
            ),
            name="ck_pp_contact_review_reviewed_present",
        ),
        Index(
            "ix_patient_portal_contact_review_requests_account_status",
            "account_id",
            "status",
        ),
        Index(
            "ux_portal_contact_review_pending_account",
            "account_id",
            unique=True,
            sqlite_where=text(PENDING_STATUS_SQL),
            postgresql_where=text(PENDING_STATUS_SQL),
        ),
        Index(
            "ix_pp_contact_review_clinic_status_requested",
            "clinic_id",
            "status",
            "requested_at",
        ),
        Index(
            "ux_pp_contact_review_revision",
            "revision",
            unique=True,
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    account_id: Mapped[int] = mapped_column(
        ForeignKey(ACCOUNT_FOREIGN_KEY_TARGET),
        nullable=False,
    )
    clinic_id: Mapped[str] = mapped_column(String(MAX_CLINIC_ID_LENGTH), nullable=False)
    demographic_no: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
    revision: Mapped[str] = mapped_column(String(64), nullable=False)
    email_before: Mapped[str] = mapped_column(String(MAX_EMAIL_LENGTH), nullable=False)
    email_after: Mapped[str] = mapped_column(String(MAX_EMAIL_LENGTH), nullable=False)
    phone_number_before: Mapped[str | None] = mapped_column(
        String(MAX_PHONE_NUMBER_LENGTH),
        nullable=True,
    )
    phone_number_after: Mapped[str | None] = mapped_column(
        String(MAX_PHONE_NUMBER_LENGTH),
        nullable=True,
    )
    requested_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        nullable=False,
    )
    reviewed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    reviewed_by: Mapped[str | None] = mapped_column(String(128), nullable=True)
    reviewed_by_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    review_decision: Mapped[str | None] = mapped_column(String(16), nullable=True)


class PatientPortalSession(Base):
    """Opaque bearer session created after password and MFA checks pass."""

    __tablename__ = "patient_portal_sessions"
    __table_args__ = (
        CheckConstraint(
            f"length(token_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_sessions_token_hash_length",
        ),
        CheckConstraint(
            EXPIRY_AFTER_CREATION_SQL,
            name="ck_patient_portal_sessions_expires_after_created",
        ),
        CheckConstraint(
            "revoked_reason is null or length(revoked_reason) between 1 and 64",
            name="ck_patient_portal_sessions_revoked_reason_length",
        ),
        Index("ux_patient_portal_sessions_token_hash", "token_hash", unique=True),
        Index("ix_patient_portal_sessions_account_expires", "account_id", "expires_at"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    account_id: Mapped[int] = mapped_column(
        ForeignKey(ACCOUNT_FOREIGN_KEY_TARGET),
        nullable=False,
    )
    token_hash: Mapped[str] = mapped_column(String(HASH_LENGTH), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        nullable=False,
    )
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    revoked_reason: Mapped[str | None] = mapped_column(String(64), nullable=True)


class PatientPortalMfaChallenge(Base):
    """Short-lived MFA challenge with a hashed one-time code."""

    __tablename__ = "patient_portal_mfa_challenges"
    __table_args__ = (
        CheckConstraint(
            f"length(challenge_token_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_mfa_challenges_token_hash_length",
        ),
        CheckConstraint(
            f"length(code_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_mfa_challenges_code_hash_length",
        ),
        CheckConstraint(
            "delivery_method in ('email', 'sms')",
            name="ck_patient_portal_mfa_challenges_delivery_method",
        ),
        CheckConstraint(
            "status in ('pending', 'verified', 'cancelled')",
            name="ck_patient_portal_mfa_challenges_status",
        ),
        CheckConstraint(
            "failed_attempts >= 0",
            name="ck_patient_portal_mfa_challenges_failed_attempts_non_negative",
        ),
        CheckConstraint(
            EXPIRY_AFTER_CREATION_SQL,
            name="ck_patient_portal_mfa_challenges_expires_after_created",
        ),
        CheckConstraint(
            "status = 'verified' or verified_at is null",
            name="ck_patient_portal_mfa_challenges_unverified_verified_at_null",
        ),
        CheckConstraint(
            "status != 'verified' or verified_at is not null",
            name="ck_patient_portal_mfa_challenges_verified_at_present",
        ),
        Index(
            "ux_patient_portal_mfa_challenges_token_hash",
            "challenge_token_hash",
            unique=True,
        ),
        Index("ix_patient_portal_mfa_challenges_account_status", "account_id", "status"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    account_id: Mapped[int] = mapped_column(
        ForeignKey(ACCOUNT_FOREIGN_KEY_TARGET),
        nullable=False,
    )
    challenge_token_hash: Mapped[str] = mapped_column(String(HASH_LENGTH), nullable=False)
    code_hash: Mapped[str] = mapped_column(String(HASH_LENGTH), nullable=False)
    delivery_method: Mapped[str] = mapped_column(String(8), nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
    failed_attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
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
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    last_email_sent_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    last_sms_sent_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    verified_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class PatientPortalPasswordResetToken(Base):
    """Short-lived one-time token for patient password reset."""

    __tablename__ = "patient_portal_password_reset_tokens"
    __table_args__ = (
        CheckConstraint(
            f"length(token_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_password_reset_tokens_token_hash_length",
        ),
        CheckConstraint(
            "status in ('pending', 'used', 'revoked')",
            name="ck_patient_portal_password_reset_tokens_status",
        ),
        CheckConstraint(
            EXPIRY_AFTER_CREATION_SQL,
            name="ck_patient_portal_password_reset_tokens_expires_after_created",
        ),
        CheckConstraint(
            "status = 'used' or used_at is null",
            name="ck_patient_portal_password_reset_tokens_unused_used_at_null",
        ),
        CheckConstraint(
            "status != 'used' or used_at is not null",
            name="ck_patient_portal_password_reset_tokens_used_at_present",
        ),
        CheckConstraint(
            f"client_reference_hash is null or length(client_reference_hash) = {HASH_LENGTH}",
            name="ck_patient_portal_password_reset_tokens_client_hash_length",
        ),
        Index(
            "ux_patient_portal_password_reset_tokens_token_hash",
            "token_hash",
            unique=True,
        ),
        Index(
            "ix_patient_portal_password_reset_tokens_account_status",
            "account_id",
            "status",
        ),
        Index(
            "ux_portal_reset_pending_account",
            "account_id",
            unique=True,
            sqlite_where=text(PENDING_STATUS_SQL),
            postgresql_where=text(PENDING_STATUS_SQL),
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    account_id: Mapped[int] = mapped_column(
        ForeignKey(ACCOUNT_FOREIGN_KEY_TARGET),
        nullable=False,
    )
    token_hash: Mapped[str] = mapped_column(String(HASH_LENGTH), nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        nullable=False,
    )
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    client_reference_hash: Mapped[str | None] = mapped_column(String(HASH_LENGTH), nullable=True)


class PatientPortalEmailChangeRequest(Base):
    """A requested portal email address, held until the new mailbox proves it can receive mail.

    Mirrors `PatientPortalPasswordResetToken`: an opaque one-time token, stored only as a keyed
    hash, with a short expiry and a partial unique index allowing one pending request per account.

    The pending row carries the phone number too. A contact change is reviewed by staff against
    the CARLOS chart as a single before/after snapshot, so applying the phone immediately while the
    email waited would split one change into two reviews with an incoherent "before".
    """

    __tablename__ = "patient_portal_email_change_requests"
    __table_args__ = (
        CheckConstraint(
            f"length(token_hash) = {HASH_LENGTH}",
            name="ck_pp_email_change_token_hash_length",
        ),
        CheckConstraint(
            "status in ('pending', 'confirmed', 'revoked')",
            name="ck_pp_email_change_status",
        ),
        CheckConstraint(
            EXPIRY_AFTER_CREATION_SQL,
            name="ck_pp_email_change_expires_after_created",
        ),
        CheckConstraint(
            f"length(new_email) between 1 and {MAX_EMAIL_LENGTH}",
            name="ck_pp_email_change_new_email_length",
        ),
        CheckConstraint(
            "new_phone_number is null or length(new_phone_number) between 1 and 32",
            name="ck_pp_email_change_new_phone_length",
        ),
        CheckConstraint(
            "status = 'confirmed' or confirmed_at is null",
            name="ck_pp_email_change_unconfirmed_confirmed_at_null",
        ),
        CheckConstraint(
            "status != 'confirmed' or confirmed_at is not null",
            name="ck_pp_email_change_confirmed_at_present",
        ),
        CheckConstraint(
            f"phone_code_hash is null or length(phone_code_hash) = {HASH_LENGTH}",
            name="ck_pp_email_change_phone_code_hash_length",
        ),
        CheckConstraint(
            "phone_failed_attempts >= 0",
            name="ck_pp_email_change_phone_attempts_non_negative",
        ),
        Index("ux_pp_email_change_token_hash", "token_hash", unique=True),
        Index("ix_pp_email_change_account_status", "account_id", "status"),
        Index(
            "ux_pp_email_change_pending_account",
            "account_id",
            unique=True,
            sqlite_where=text(PENDING_STATUS_SQL),
            postgresql_where=text(PENDING_STATUS_SQL),
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    account_id: Mapped[int] = mapped_column(
        ForeignKey(ACCOUNT_FOREIGN_KEY_TARGET),
        nullable=False,
    )
    token_hash: Mapped[str] = mapped_column(String(HASH_LENGTH), nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
    new_email: Mapped[str] = mapped_column(String(MAX_EMAIL_LENGTH), nullable=False)
    new_phone_number: Mapped[str | None] = mapped_column(
        String(MAX_PHONE_NUMBER_LENGTH),
        nullable=True,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        nullable=False,
    )
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    confirmed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    email_confirmed_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    phone_code_hash: Mapped[str | None] = mapped_column(String(HASH_LENGTH), nullable=True)
    phone_confirmed_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    phone_code_sent_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    phone_failed_attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)


class PatientPortalOutboundDelivery(Base):
    """Durable encrypted handoff for non-interactive email delivery."""

    __tablename__ = "patient_portal_outbound_deliveries"
    __table_args__ = (
        CheckConstraint(
            "kind in ('password_reset', 'contact_change')",
            name="ck_pp_outbound_delivery_kind",
        ),
        CheckConstraint(
            "status in ('pending', 'processing', 'delivered', 'failed')",
            name="ck_pp_outbound_delivery_status",
        ),
        CheckConstraint(
            "attempt_count >= 0",
            name="ck_pp_outbound_delivery_attempts_non_negative",
        ),
        CheckConstraint(
            "length(encryption_key_id) between 1 and 64",
            name="ck_pp_outbound_delivery_key_id_length",
        ),
        CheckConstraint(
            "length(message_id) between 1 and 255",
            name="ck_pp_outbound_delivery_message_id_length",
        ),
        CheckConstraint(
            "status != 'delivered' or delivered_at is not null",
            name="ck_pp_outbound_delivery_delivered_at_present",
        ),
        Index(
            "ix_pp_outbound_delivery_available",
            "status",
            "available_at",
            "id",
        ),
        Index("ux_pp_outbound_delivery_message_id", "message_id", unique=True),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    account_id: Mapped[int] = mapped_column(
        ForeignKey(ACCOUNT_FOREIGN_KEY_TARGET),
        nullable=False,
    )
    reset_token_id: Mapped[int | None] = mapped_column(
        ForeignKey("patient_portal_password_reset_tokens.id"),
        nullable=True,
    )
    kind: Mapped[str] = mapped_column(String(32), nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
    encrypted_payload: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    encryption_nonce: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    encryption_key_id: Mapped[str] = mapped_column(String(64), nullable=False)
    message_id: Mapped[str] = mapped_column(String(255), nullable=False)
    attempt_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    available_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    lease_expires_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    last_failure_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utc_now, nullable=False
    )
    delivered_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class PatientPortalInvite(Base):
    """Staff-created invite for a patient portal account."""

    __tablename__ = "patient_portal_invites"
    __table_args__ = (
        CheckConstraint(
            f"length(clinic_id) between 1 and {MAX_CLINIC_ID_LENGTH}",
            name="ck_patient_portal_invites_clinic_id_length",
        ),
        CheckConstraint(
            DEMOGRAPHIC_NO_POSITIVE_SQL,
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
            (f"proof_salt is null or length(proof_salt) between 1 and {MAX_PROOF_SALT_LENGTH}"),
            name="ck_patient_portal_invites_proof_salt_length",
        ),
        CheckConstraint(
            (
                "(proof_email_hash is null and proof_date_of_birth_hash is null and "
                "proof_health_card_hash is null and proof_salt is null and "
                "proof_hash_version is null) or "
                "(proof_email_hash is not null and proof_date_of_birth_hash is not null and "
                "proof_health_card_hash is not null and proof_salt is not null and "
                "proof_hash_version is not null)"
            ),
            name="ck_patient_portal_invites_proof_fields_complete",
        ),
        CheckConstraint(
            "proof_hash_version is null or proof_hash_version in ('v1')",
            name="ck_patient_portal_invites_proof_hash_version",
        ),
        CheckConstraint(
            EXPIRY_AFTER_CREATION_SQL,
            name="ck_patient_portal_invites_expires_after_created",
        ),
        CheckConstraint(
            "status in ('pending', 'revoked', 'accepted', 'superseded')",
            name="ck_patient_portal_invites_status",
        ),
        CheckConstraint(
            ("status = 'accepted' or (accepted_at is null and accepted_account_id is null)"),
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
        Index(
            "ix_patient_portal_invites_supersedes_invite_id",
            "supersedes_invite_id",
        ),
        Index("ux_patient_portal_invites_token_hash", "token_hash", unique=True),
        Index(
            "ux_patient_portal_invites_one_pending_per_patient",
            "clinic_id",
            "demographic_no",
            unique=True,
            sqlite_where=text(PENDING_STATUS_SQL),
            postgresql_where=text(PENDING_STATUS_SQL),
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    clinic_id: Mapped[str] = mapped_column(String(MAX_CLINIC_ID_LENGTH), nullable=False)
    demographic_no: Mapped[int] = mapped_column(Integer, nullable=False)
    token_hash: Mapped[str] = mapped_column(String(HASH_LENGTH), nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
    created_by: Mapped[str] = mapped_column(String(128), nullable=False)
    created_by_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
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
    last_sent_by_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    revoked_by: Mapped[str | None] = mapped_column(String(128), nullable=True)
    revoked_by_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    proof_email_hash: Mapped[str | None] = mapped_column(String(HASH_LENGTH), nullable=True)
    proof_date_of_birth_hash: Mapped[str | None] = mapped_column(
        String(HASH_LENGTH),
        nullable=True,
    )
    proof_health_card_hash: Mapped[str | None] = mapped_column(
        String(HASH_LENGTH),
        nullable=True,
    )
    proof_salt: Mapped[str | None] = mapped_column(String(MAX_PROOF_SALT_LENGTH), nullable=True)
    proof_hash_version: Mapped[str | None] = mapped_column(String(16), nullable=True)
    accepted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    accepted_account_id: Mapped[int | None] = mapped_column(
        ForeignKey(ACCOUNT_FOREIGN_KEY_TARGET),
        nullable=True,
    )
    supersedes_invite_id: Mapped[int | None] = mapped_column(
        ForeignKey("patient_portal_invites.id", ondelete="SET NULL"),
        nullable=True,
    )


class PatientPortalUnlockSecret(Base):
    """Encrypted generated password for a patient email or PDF."""

    __tablename__ = "patient_portal_unlock_secrets"
    __table_args__ = (
        CheckConstraint(
            f"length(clinic_id) between 1 and {MAX_CLINIC_ID_LENGTH}",
            name="ck_patient_portal_unlock_secrets_clinic_id_length",
        ),
        CheckConstraint(
            DEMOGRAPHIC_NO_POSITIVE_SQL,
            name="ck_patient_portal_unlock_secrets_demographic_no_positive",
        ),
        CheckConstraint(
            "account_id is null or account_id > 0",
            name="ck_patient_portal_unlock_secrets_account_id_positive",
        ),
        CheckConstraint(
            "secret_type in ('email', 'pdf')",
            name="ck_patient_portal_unlock_secrets_secret_type",
        ),
        CheckConstraint(
            "status in ('pending', 'available', 'revoked')",
            name="ck_patient_portal_unlock_secrets_status",
        ),
        CheckConstraint(
            "label is null or length(label) between 1 and 128",
            name="ck_patient_portal_unlock_secrets_label_length",
        ),
        CheckConstraint(
            "source_reference is null or length(source_reference) between 1 and 128",
            name="ck_patient_portal_unlock_secrets_source_reference_length",
        ),
        CheckConstraint(
            "length(created_by) between 1 and 128",
            name="ck_patient_portal_unlock_secrets_created_by_length",
        ),
        CheckConstraint(
            "length(encryption_algorithm) between 1 and 32",
            name="ck_patient_portal_unlock_secrets_algorithm_length",
        ),
        CheckConstraint(
            "length(encryption_key_id) between 1 and 64",
            name="ck_patient_portal_unlock_secrets_key_id_length",
        ),
        CheckConstraint(
            "encryption_context is null or length(encryption_context) = 36",
            name="ck_patient_portal_unlock_secrets_context_length",
        ),
        CheckConstraint(
            f"length(encryption_nonce) = {UNLOCK_SECRET_NONCE_LENGTH}",
            name="ck_patient_portal_unlock_secrets_nonce_length",
        ),
        CheckConstraint(
            "length(encrypted_secret) > 0",
            name="ck_patient_portal_unlock_secrets_ciphertext_present",
        ),
        CheckConstraint(
            "revoked_by is null or length(revoked_by) between 1 and 128",
            name="ck_patient_portal_unlock_secrets_revoked_by_length",
        ),
        CheckConstraint(
            "revoke_reason is null or length(revoke_reason) between 1 and 64",
            name="ck_patient_portal_unlock_secrets_revoke_reason_length",
        ),
        CheckConstraint(
            "status = 'revoked' or "
            "(revoked_at is null and revoked_by is null and revoke_reason is null)",
            name="ck_patient_portal_unlock_secrets_nonrevoked_fields_null",
        ),
        CheckConstraint(
            "status != 'revoked' or (revoked_at is not null and revoked_by is not null)",
            name="ck_patient_portal_unlock_secrets_revoked_fields_present",
        ),
        Index(
            "ix_patient_portal_unlock_secrets_clinic_patient_status_created",
            "clinic_id",
            "demographic_no",
            "status",
            "created_at",
        ),
        Index(
            "ix_patient_portal_unlock_secrets_account_created",
            "account_id",
            "created_at",
        ),
        Index(
            "ix_patient_portal_unlock_secrets_key_id",
            "encryption_key_id",
        ),
        Index(
            "ux_pp_unlock_secrets_source_reference",
            "clinic_id",
            "secret_type",
            "source_reference",
            unique=True,
            sqlite_where=text("source_reference is not null"),
            postgresql_where=text("source_reference is not null"),
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    clinic_id: Mapped[str] = mapped_column(String(MAX_CLINIC_ID_LENGTH), nullable=False)
    demographic_no: Mapped[int] = mapped_column(Integer, nullable=False)
    account_id: Mapped[int | None] = mapped_column(
        ForeignKey(ACCOUNT_FOREIGN_KEY_TARGET),
        nullable=True,
    )
    secret_type: Mapped[str] = mapped_column(String(MAX_UNLOCK_SECRET_TYPE_LENGTH), nullable=False)
    status: Mapped[str] = mapped_column(String(MAX_UNLOCK_SECRET_STATUS_LENGTH), nullable=False)
    label: Mapped[str | None] = mapped_column(
        String(MAX_UNLOCK_SECRET_LABEL_LENGTH),
        nullable=True,
    )
    source_reference: Mapped[str | None] = mapped_column(
        String(MAX_UNLOCK_SECRET_SOURCE_REFERENCE_LENGTH),
        nullable=True,
    )
    encrypted_secret: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    encryption_nonce: Mapped[bytes] = mapped_column(
        LargeBinary(UNLOCK_SECRET_NONCE_LENGTH),
        nullable=False,
    )
    encryption_algorithm: Mapped[str] = mapped_column(
        String(MAX_UNLOCK_SECRET_ALGORITHM_LENGTH),
        nullable=False,
    )
    encryption_key_id: Mapped[str] = mapped_column(
        String(MAX_UNLOCK_SECRET_KEY_ID_LENGTH),
        nullable=False,
    )
    encryption_context: Mapped[str | None] = mapped_column(String(36), nullable=True)
    created_by: Mapped[str] = mapped_column(String(MAX_UNLOCK_SECRET_ACTOR_LENGTH), nullable=False)
    created_by_id: Mapped[str | None] = mapped_column(
        String(MAX_UNLOCK_SECRET_ACTOR_LENGTH),
        nullable=True,
    )
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
    last_viewed_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    revoked_by: Mapped[str | None] = mapped_column(
        String(MAX_UNLOCK_SECRET_ACTOR_LENGTH),
        nullable=True,
    )
    revoked_by_id: Mapped[str | None] = mapped_column(
        String(MAX_UNLOCK_SECRET_ACTOR_LENGTH),
        nullable=True,
    )
    revoke_reason: Mapped[str | None] = mapped_column(
        String(MAX_UNLOCK_SECRET_REVOKE_REASON_LENGTH),
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
        # Enumerated rather than free text, deliberately. It makes a typo'd event type impossible
        # to store, so a query filtering on a type can never silently miss rows -- which is the
        # whole value of an audit trail you reason about by event type. The cost is real and
        # accepted: adding a type needs an Alembic migration (and, per 0002, a preflight over
        # existing rows). A lookup table or a module constant plus a "constant is used" test were
        # both considered and rejected: each moves the guarantee from write time to review time.
        CheckConstraint(
            (
                "event_type in "
                "('activation', 'account.contact_update', 'account.disable', "
                "'account.email_change_confirm', 'account.email_change_request', "
                "'account.enable', 'account.lock', "
                "'account.mfa_update', 'account.password_change', 'account.unlock', "
                "'invite.create', 'invite.list', 'invite.resend', 'invite.revoke', "
                "'login', 'mfa.challenge', 'mfa.delivery', 'mfa.resend', 'mfa.verify', "
                "'password_reset.complete', 'password_reset.delivery', "
                "'password_reset.request', 'retention.policy_override', 'session.logout', "
                "'staff.action', "
                "'fhir.read', 'fhir.search', "
                "'unlock_secret.create', 'unlock_secret.list', 'unlock_secret.read', "
                "'unlock_secret.publish', 'unlock_secret.revoke')"
            ),
            name="ck_patient_portal_audit_events_event_type",
        ),
        CheckConstraint(
            "outcome in ('success', 'failure', 'throttled')",
            name="ck_patient_portal_audit_events_outcome",
        ),
        CheckConstraint(
            "actor_type in ('patient', 'staff', 'system')",
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
        CheckConstraint(
            "actor_id is null or length(actor_id) between 1 and 128",
            name="ck_patient_portal_audit_events_actor_id_length",
        ),
        CheckConstraint(
            "resource_type is null or length(resource_type) between 1 and 64",
            name="ck_patient_portal_audit_events_resource_type_length",
        ),
        CheckConstraint(
            "resource_id is null or length(resource_id) between 1 and 128",
            name="ck_patient_portal_audit_events_resource_id_length",
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
        Index("ix_patient_portal_audit_events_actor_id_created", "actor_id", "created_at"),
        Index(
            "ix_patient_portal_audit_events_resource_created",
            "resource_type",
            "resource_id",
            "created_at",
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    clinic_id: Mapped[str | None] = mapped_column(String(MAX_CLINIC_ID_LENGTH), nullable=True)
    event_type: Mapped[str] = mapped_column(String(MAX_AUDIT_EVENT_TYPE_LENGTH), nullable=False)
    outcome: Mapped[str] = mapped_column(String(MAX_AUDIT_OUTCOME_LENGTH), nullable=False)
    actor_type: Mapped[str] = mapped_column(String(MAX_AUDIT_ACTOR_TYPE_LENGTH), nullable=False)
    actor: Mapped[str | None] = mapped_column(String(128), nullable=True)
    actor_id: Mapped[str | None] = mapped_column(
        String(MAX_AUDIT_ACTOR_ID_LENGTH),
        nullable=True,
    )
    demographic_no: Mapped[int | None] = mapped_column(Integer, nullable=True)
    # Deliberately a plain Integer, not a ForeignKey. Audit rows have to outlive the rows they
    # describe: a cascading FK would delete history along with a pruned invite, and a restricting
    # one would block the retention cleanup that pruning exists for. So a reference here can dangle
    # once an expired invite is cleaned up, and that is the accepted trade — the event itself
    # carries the actor, outcome, reason, and hashed token needed to investigate without it.
    invite_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    account_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    invite_token_hash: Mapped[str | None] = mapped_column(String(HASH_LENGTH), nullable=True)
    client_reference_hash: Mapped[str | None] = mapped_column(String(HASH_LENGTH), nullable=True)
    resource_type: Mapped[str | None] = mapped_column(
        String(MAX_AUDIT_RESOURCE_TYPE_LENGTH),
        nullable=True,
    )
    resource_id: Mapped[str | None] = mapped_column(
        String(MAX_AUDIT_RESOURCE_ID_LENGTH),
        nullable=True,
    )
    reason: Mapped[str | None] = mapped_column(String(MAX_AUDIT_REASON_LENGTH), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=utc_now,
        nullable=False,
    )
