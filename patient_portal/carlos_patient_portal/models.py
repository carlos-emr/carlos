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
ACCOUNT_STATUS_ACTIVE = "active"
AUDIT_ACTOR_TYPE_PATIENT = "patient"
AUDIT_ACTOR_TYPE_STAFF = "staff"
AUDIT_EVENT_ACTIVATION = "activation"
AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE = "account.contact_update"
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
AUDIT_EVENT_SESSION_LOGOUT = "session.logout"
AUDIT_EVENT_FHIR_READ = "fhir.read"
AUDIT_EVENT_FHIR_SEARCH = "fhir.search"
AUDIT_EVENT_UNLOCK_SECRET_CREATE = "unlock_secret.create"
AUDIT_EVENT_UNLOCK_SECRET_LIST = "unlock_secret.list"
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
CONTACT_REVIEW_STATUS_PENDING = "pending"
CONTACT_REVIEW_STATUS_REVIEWED = "reviewed"
CONTACT_REVIEW_DECISION_APPROVED = "approved"
CONTACT_REVIEW_DECISION_REJECTED = "rejected"
UNLOCK_SECRET_STATUS_ACTIVE = "active"
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
            "status in ('active')",
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
            "locked_by is null or length(locked_by) between 1 and 128",
            name="ck_patient_portal_accounts_locked_by_length",
        ),
        CheckConstraint(
            "(locked_at is null and locked_by is null) or "
            "(locked_at is not null and locked_by is not null)",
            name="ck_patient_portal_accounts_lock_fields_complete",
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
    locked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    locked_by: Mapped[str | None] = mapped_column(String(128), nullable=True)
    locked_by_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    force_password_reset: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    last_login_at: Mapped[datetime | None] = mapped_column(
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
                "review_decision in ('approved', 'rejected'))"
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
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    account_id: Mapped[int] = mapped_column(
        ForeignKey(ACCOUNT_FOREIGN_KEY_TARGET),
        nullable=False,
    )
    clinic_id: Mapped[str] = mapped_column(String(MAX_CLINIC_ID_LENGTH), nullable=False)
    demographic_no: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
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
            (
                f"proof_salt is null or length(proof_salt) between 1 and "
                f"{MAX_PROOF_SALT_LENGTH}"
            ),
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
            "status in ('active', 'revoked')",
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
        CheckConstraint(
            (
                "event_type in "
                "('activation', 'account.contact_update', 'account.lock', "
                "'account.mfa_update', 'account.password_change', 'account.unlock', "
                "'invite.create', 'invite.list', 'invite.resend', 'invite.revoke', "
                "'login', 'mfa.challenge', 'mfa.delivery', 'mfa.resend', 'mfa.verify', "
                "'password_reset.complete', 'password_reset.delivery', "
                "'password_reset.request', 'session.logout', "
                "'fhir.read', 'fhir.search', "
                "'unlock_secret.create', 'unlock_secret.list', 'unlock_secret.read', "
                "'unlock_secret.revoke')"
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
