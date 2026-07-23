"""Add patient portal invites.

Revision ID: 0001_patient_portal_invites
Revises:
Create Date: 2026-07-22 20:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

_ALEMBIC_REVISION_IDENTIFIERS: dict[str, str | Sequence[str] | None] = {
    "revision": "0001_patient_portal_invites",
    "down_revision": None,
    "branch_labels": None,
    "depends_on": None,
}
globals().update(_ALEMBIC_REVISION_IDENTIFIERS)


def upgrade() -> None:
    op.create_table(
        "patient_portal_accounts",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("clinic_id", sa.String(length=64), nullable=False),
        sa.Column("demographic_no", sa.Integer(), nullable=False),
        sa.Column("username", sa.String(length=64), nullable=False),
        sa.Column("email", sa.String(length=254), nullable=False),
        sa.Column("phone_number", sa.String(length=32), nullable=True),
        sa.Column("preferred_mfa_method", sa.String(length=8), nullable=False),
        sa.Column("password_hash", sa.String(length=512), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("failed_login_count", sa.Integer(), nullable=False),
        sa.Column("locked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("locked_by", sa.String(length=128), nullable=True),
        sa.Column("force_password_reset", sa.Boolean(), nullable=False),
        sa.Column("last_login_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("password_updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "demographic_no > 0",
            name="ck_patient_portal_accounts_demographic_no_positive",
        ),
        sa.CheckConstraint(
            "length(clinic_id) between 1 and 64",
            name="ck_patient_portal_accounts_clinic_id_length",
        ),
        sa.CheckConstraint(
            "length(username) between 3 and 64",
            name="ck_patient_portal_accounts_username_length",
        ),
        sa.CheckConstraint(
            "status in ('active')",
            name="ck_patient_portal_accounts_status",
        ),
        sa.CheckConstraint(
            "phone_number is null or length(phone_number) between 1 and 32",
            name="ck_patient_portal_accounts_phone_number_length",
        ),
        sa.CheckConstraint(
            "preferred_mfa_method in ('email', 'sms')",
            name="ck_patient_portal_accounts_preferred_mfa_method",
        ),
        sa.CheckConstraint(
            "failed_login_count >= 0",
            name="ck_patient_portal_accounts_failed_login_count_non_negative",
        ),
        sa.CheckConstraint(
            "locked_by is null or length(locked_by) between 1 and 128",
            name="ck_patient_portal_accounts_locked_by_length",
        ),
        sa.CheckConstraint(
            "(locked_at is null and locked_by is null) or "
            "(locked_at is not null and locked_by is not null)",
            name="ck_patient_portal_accounts_lock_fields_complete",
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "clinic_id",
            "demographic_no",
            name="ux_patient_portal_accounts_clinic_demographic",
        ),
        sa.UniqueConstraint("username", name="ux_patient_portal_accounts_username"),
    )
    op.create_index(
        "ix_patient_portal_accounts_status",
        "patient_portal_accounts",
        ["status"],
        unique=False,
    )
    op.create_table(
        "patient_portal_contact_review_requests",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("account_id", sa.Integer(), nullable=False),
        sa.Column("clinic_id", sa.String(length=64), nullable=False),
        sa.Column("demographic_no", sa.Integer(), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("email_before", sa.String(length=254), nullable=False),
        sa.Column("email_after", sa.String(length=254), nullable=False),
        sa.Column("phone_number_before", sa.String(length=32), nullable=True),
        sa.Column("phone_number_after", sa.String(length=32), nullable=True),
        sa.Column("requested_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("reviewed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("reviewed_by", sa.String(length=128), nullable=True),
        sa.CheckConstraint(
            "length(clinic_id) between 1 and 64",
            name="ck_patient_portal_contact_review_requests_clinic_id_length",
        ),
        sa.CheckConstraint(
            "demographic_no > 0",
            name="ck_patient_portal_contact_review_requests_demographic_no_positive",
        ),
        sa.CheckConstraint(
            "status in ('pending', 'reviewed')",
            name="ck_patient_portal_contact_review_requests_status",
        ),
        sa.CheckConstraint(
            "length(email_before) between 1 and 254",
            name="ck_patient_portal_contact_review_requests_email_before_length",
        ),
        sa.CheckConstraint(
            "length(email_after) between 1 and 254",
            name="ck_patient_portal_contact_review_requests_email_after_length",
        ),
        sa.CheckConstraint(
            "phone_number_before is null or length(phone_number_before) between 1 and 32",
            name="ck_patient_portal_contact_review_requests_phone_before_length",
        ),
        sa.CheckConstraint(
            "phone_number_after is null or length(phone_number_after) between 1 and 32",
            name="ck_patient_portal_contact_review_requests_phone_after_length",
        ),
        sa.CheckConstraint(
            "reviewed_by is null or length(reviewed_by) between 1 and 128",
            name="ck_patient_portal_contact_review_requests_reviewed_by_length",
        ),
        sa.CheckConstraint(
            "status = 'reviewed' or (reviewed_at is null and reviewed_by is null)",
            name="ck_patient_portal_contact_review_requests_unreviewed_fields_null",
        ),
        sa.CheckConstraint(
            "status != 'reviewed' or (reviewed_at is not null and reviewed_by is not null)",
            name="ck_patient_portal_contact_review_requests_reviewed_fields_present",
        ),
        sa.ForeignKeyConstraint(["account_id"], ["patient_portal_accounts.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_patient_portal_contact_review_requests_account_status",
        "patient_portal_contact_review_requests",
        ["account_id", "status"],
        unique=False,
    )
    op.create_index(
        "ix_patient_portal_contact_review_requests_clinic_status_requested",
        "patient_portal_contact_review_requests",
        ["clinic_id", "status", "requested_at"],
        unique=False,
    )
    op.create_table(
        "patient_portal_sessions",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("account_id", sa.Integer(), nullable=False),
        sa.Column("token_hash", sa.String(length=64), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_reason", sa.String(length=64), nullable=True),
        sa.CheckConstraint(
            "length(token_hash) = 64",
            name="ck_patient_portal_sessions_token_hash_length",
        ),
        sa.CheckConstraint(
            "expires_at > created_at",
            name="ck_patient_portal_sessions_expires_after_created",
        ),
        sa.CheckConstraint(
            "revoked_reason is null or length(revoked_reason) between 1 and 64",
            name="ck_patient_portal_sessions_revoked_reason_length",
        ),
        sa.ForeignKeyConstraint(["account_id"], ["patient_portal_accounts.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_patient_portal_sessions_account_expires",
        "patient_portal_sessions",
        ["account_id", "expires_at"],
        unique=False,
    )
    op.create_index(
        "ux_patient_portal_sessions_token_hash",
        "patient_portal_sessions",
        ["token_hash"],
        unique=True,
    )
    op.create_table(
        "patient_portal_mfa_challenges",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("account_id", sa.Integer(), nullable=False),
        sa.Column("challenge_token_hash", sa.String(length=64), nullable=False),
        sa.Column("code_hash", sa.String(length=64), nullable=False),
        sa.Column("delivery_method", sa.String(length=8), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("failed_attempts", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_email_sent_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("last_sms_sent_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("verified_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint(
            "length(challenge_token_hash) = 64",
            name="ck_patient_portal_mfa_challenges_token_hash_length",
        ),
        sa.CheckConstraint(
            "length(code_hash) = 64",
            name="ck_patient_portal_mfa_challenges_code_hash_length",
        ),
        sa.CheckConstraint(
            "delivery_method in ('email', 'sms')",
            name="ck_patient_portal_mfa_challenges_delivery_method",
        ),
        sa.CheckConstraint(
            "status in ('pending', 'verified', 'cancelled')",
            name="ck_patient_portal_mfa_challenges_status",
        ),
        sa.CheckConstraint(
            "failed_attempts >= 0",
            name="ck_patient_portal_mfa_challenges_failed_attempts_non_negative",
        ),
        sa.CheckConstraint(
            "expires_at > created_at",
            name="ck_patient_portal_mfa_challenges_expires_after_created",
        ),
        sa.CheckConstraint(
            "status = 'verified' or verified_at is null",
            name="ck_patient_portal_mfa_challenges_unverified_verified_at_null",
        ),
        sa.CheckConstraint(
            "status != 'verified' or verified_at is not null",
            name="ck_patient_portal_mfa_challenges_verified_at_present",
        ),
        sa.ForeignKeyConstraint(["account_id"], ["patient_portal_accounts.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_patient_portal_mfa_challenges_account_status",
        "patient_portal_mfa_challenges",
        ["account_id", "status"],
        unique=False,
    )
    op.create_index(
        "ux_patient_portal_mfa_challenges_token_hash",
        "patient_portal_mfa_challenges",
        ["challenge_token_hash"],
        unique=True,
    )
    op.create_table(
        "patient_portal_password_reset_tokens",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("account_id", sa.Integer(), nullable=False),
        sa.Column("token_hash", sa.String(length=64), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("used_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("client_reference_hash", sa.String(length=64), nullable=True),
        sa.CheckConstraint(
            "length(token_hash) = 64",
            name="ck_patient_portal_password_reset_tokens_token_hash_length",
        ),
        sa.CheckConstraint(
            "status in ('pending', 'used', 'revoked')",
            name="ck_patient_portal_password_reset_tokens_status",
        ),
        sa.CheckConstraint(
            "expires_at > created_at",
            name="ck_patient_portal_password_reset_tokens_expires_after_created",
        ),
        sa.CheckConstraint(
            "status = 'used' or used_at is null",
            name="ck_patient_portal_password_reset_tokens_unused_used_at_null",
        ),
        sa.CheckConstraint(
            "status != 'used' or used_at is not null",
            name="ck_patient_portal_password_reset_tokens_used_at_present",
        ),
        sa.CheckConstraint(
            "client_reference_hash is null or length(client_reference_hash) = 64",
            name="ck_patient_portal_password_reset_tokens_client_hash_length",
        ),
        sa.ForeignKeyConstraint(["account_id"], ["patient_portal_accounts.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_patient_portal_password_reset_tokens_account_status",
        "patient_portal_password_reset_tokens",
        ["account_id", "status"],
        unique=False,
    )
    op.create_index(
        "ux_patient_portal_password_reset_tokens_token_hash",
        "patient_portal_password_reset_tokens",
        ["token_hash"],
        unique=True,
    )
    op.create_table(
        "patient_portal_invites",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("clinic_id", sa.String(length=64), nullable=False),
        sa.Column("demographic_no", sa.Integer(), nullable=False),
        sa.Column("token_hash", sa.String(length=64), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("created_by", sa.String(length=128), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("sent_count", sa.Integer(), nullable=False),
        sa.Column("last_sent_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_sent_by", sa.String(length=128), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_by", sa.String(length=128), nullable=True),
        sa.Column("proof_email_hash", sa.String(length=64), nullable=True),
        sa.Column("proof_date_of_birth_hash", sa.String(length=64), nullable=True),
        sa.Column("proof_health_card_hash", sa.String(length=64), nullable=True),
        sa.Column("proof_salt", sa.String(length=64), nullable=True),
        sa.Column("proof_hash_version", sa.String(length=16), nullable=True),
        sa.Column("accepted_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("accepted_account_id", sa.Integer(), nullable=True),
        sa.CheckConstraint(
            "length(clinic_id) between 1 and 64",
            name="ck_patient_portal_invites_clinic_id_length",
        ),
        sa.CheckConstraint(
            "demographic_no > 0",
            name="ck_patient_portal_invites_demographic_no_positive",
        ),
        sa.CheckConstraint(
            "sent_count >= 0",
            name="ck_patient_portal_invites_sent_count_non_negative",
        ),
        sa.CheckConstraint(
            "length(token_hash) = 64",
            name="ck_patient_portal_invites_token_hash_length",
        ),
        sa.CheckConstraint(
            "proof_email_hash is null or length(proof_email_hash) = 64",
            name="ck_patient_portal_invites_proof_email_hash_length",
        ),
        sa.CheckConstraint(
            "proof_date_of_birth_hash is null or length(proof_date_of_birth_hash) = 64",
            name="ck_patient_portal_invites_proof_date_of_birth_hash_length",
        ),
        sa.CheckConstraint(
            "proof_health_card_hash is null or length(proof_health_card_hash) = 64",
            name="ck_patient_portal_invites_proof_health_card_hash_length",
        ),
        sa.CheckConstraint(
            "proof_salt is null or length(proof_salt) between 1 and 64",
            name="ck_patient_portal_invites_proof_salt_length",
        ),
        sa.CheckConstraint(
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
        sa.CheckConstraint(
            "proof_hash_version is null or proof_hash_version in ('v1')",
            name="ck_patient_portal_invites_proof_hash_version",
        ),
        sa.CheckConstraint(
            "expires_at > created_at",
            name="ck_patient_portal_invites_expires_after_created",
        ),
        sa.CheckConstraint(
            "status in ('pending', 'revoked', 'accepted')",
            name="ck_patient_portal_invites_status",
        ),
        sa.CheckConstraint(
            "status = 'accepted' or (accepted_at is null and accepted_account_id is null)",
            name="ck_patient_portal_invites_nonaccepted_fields_null",
        ),
        sa.CheckConstraint(
            "status != 'accepted' or (accepted_at is not null and accepted_account_id is not null)",
            name="ck_patient_portal_invites_accepted_fields_present",
        ),
        sa.CheckConstraint(
            "status = 'revoked' or (revoked_at is null and revoked_by is null)",
            name="ck_patient_portal_invites_nonrevoked_fields_null",
        ),
        sa.CheckConstraint(
            "status != 'revoked' or (revoked_at is not null and revoked_by is not null)",
            name="ck_patient_portal_invites_revoked_fields_present",
        ),
        sa.ForeignKeyConstraint(
            ["accepted_account_id"],
            ["patient_portal_accounts.id"],
        ),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_patient_portal_invites_clinic_demographic",
        "patient_portal_invites",
        ["clinic_id", "demographic_no"],
        unique=False,
    )
    op.create_index(
        "ix_patient_portal_invites_clinic_expires_at",
        "patient_portal_invites",
        ["clinic_id", "expires_at"],
        unique=False,
    )
    op.create_index(
        "ix_patient_portal_invites_clinic_status",
        "patient_portal_invites",
        ["clinic_id", "status"],
        unique=False,
    )
    op.create_index(
        "ux_patient_portal_invites_token_hash",
        "patient_portal_invites",
        ["token_hash"],
        unique=True,
    )
    op.create_index(
        "ux_patient_portal_invites_one_pending_per_patient",
        "patient_portal_invites",
        ["clinic_id", "demographic_no"],
        unique=True,
        sqlite_where=sa.text("status = 'pending'"),
        postgresql_where=sa.text("status = 'pending'"),
    )
    op.create_table(
        "patient_portal_unlock_secrets",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("clinic_id", sa.String(length=64), nullable=False),
        sa.Column("demographic_no", sa.Integer(), nullable=False),
        sa.Column("account_id", sa.Integer(), nullable=True),
        sa.Column("secret_type", sa.String(length=16), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("label", sa.String(length=128), nullable=True),
        sa.Column("source_reference", sa.String(length=128), nullable=True),
        sa.Column("encrypted_secret", sa.LargeBinary(), nullable=False),
        sa.Column("encryption_nonce", sa.LargeBinary(length=12), nullable=False),
        sa.Column("encryption_algorithm", sa.String(length=32), nullable=False),
        sa.Column("encryption_key_id", sa.String(length=64), nullable=False),
        sa.Column("created_by", sa.String(length=128), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_viewed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_by", sa.String(length=128), nullable=True),
        sa.Column("revoke_reason", sa.String(length=64), nullable=True),
        sa.CheckConstraint(
            "length(clinic_id) between 1 and 64",
            name="ck_patient_portal_unlock_secrets_clinic_id_length",
        ),
        sa.CheckConstraint(
            "demographic_no > 0",
            name="ck_patient_portal_unlock_secrets_demographic_no_positive",
        ),
        sa.CheckConstraint(
            "account_id is null or account_id > 0",
            name="ck_patient_portal_unlock_secrets_account_id_positive",
        ),
        sa.CheckConstraint(
            "secret_type in ('email', 'pdf')",
            name="ck_patient_portal_unlock_secrets_secret_type",
        ),
        sa.CheckConstraint(
            "status in ('active', 'revoked')",
            name="ck_patient_portal_unlock_secrets_status",
        ),
        sa.CheckConstraint(
            "label is null or length(label) between 1 and 128",
            name="ck_patient_portal_unlock_secrets_label_length",
        ),
        sa.CheckConstraint(
            "source_reference is null or length(source_reference) between 1 and 128",
            name="ck_patient_portal_unlock_secrets_source_reference_length",
        ),
        sa.CheckConstraint(
            "length(created_by) between 1 and 128",
            name="ck_patient_portal_unlock_secrets_created_by_length",
        ),
        sa.CheckConstraint(
            "length(encryption_algorithm) between 1 and 32",
            name="ck_patient_portal_unlock_secrets_algorithm_length",
        ),
        sa.CheckConstraint(
            "length(encryption_key_id) between 1 and 64",
            name="ck_patient_portal_unlock_secrets_key_id_length",
        ),
        sa.CheckConstraint(
            "length(encryption_nonce) = 12",
            name="ck_patient_portal_unlock_secrets_nonce_length",
        ),
        sa.CheckConstraint(
            "length(encrypted_secret) > 0",
            name="ck_patient_portal_unlock_secrets_ciphertext_present",
        ),
        sa.CheckConstraint(
            "revoked_by is null or length(revoked_by) between 1 and 128",
            name="ck_patient_portal_unlock_secrets_revoked_by_length",
        ),
        sa.CheckConstraint(
            "revoke_reason is null or length(revoke_reason) between 1 and 64",
            name="ck_patient_portal_unlock_secrets_revoke_reason_length",
        ),
        sa.CheckConstraint(
            "status = 'revoked' or "
            "(revoked_at is null and revoked_by is null and revoke_reason is null)",
            name="ck_patient_portal_unlock_secrets_nonrevoked_fields_null",
        ),
        sa.CheckConstraint(
            "status != 'revoked' or (revoked_at is not null and revoked_by is not null)",
            name="ck_patient_portal_unlock_secrets_revoked_fields_present",
        ),
        sa.ForeignKeyConstraint(["account_id"], ["patient_portal_accounts.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_patient_portal_unlock_secrets_account_created",
        "patient_portal_unlock_secrets",
        ["account_id", "created_at"],
        unique=False,
    )
    op.create_index(
        "ix_patient_portal_unlock_secrets_clinic_patient_status_created",
        "patient_portal_unlock_secrets",
        ["clinic_id", "demographic_no", "status", "created_at"],
        unique=False,
    )
    op.create_index(
        "ix_patient_portal_unlock_secrets_key_id",
        "patient_portal_unlock_secrets",
        ["encryption_key_id"],
        unique=False,
    )
    op.create_table(
        "patient_portal_audit_events",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("clinic_id", sa.String(length=64), nullable=True),
        sa.Column("event_type", sa.String(length=64), nullable=False),
        sa.Column("outcome", sa.String(length=16), nullable=False),
        sa.Column("actor_type", sa.String(length=16), nullable=False),
        sa.Column("actor", sa.String(length=128), nullable=True),
        sa.Column("demographic_no", sa.Integer(), nullable=True),
        sa.Column("invite_id", sa.Integer(), nullable=True),
        sa.Column("account_id", sa.Integer(), nullable=True),
        sa.Column("invite_token_hash", sa.String(length=64), nullable=True),
        sa.Column("client_reference_hash", sa.String(length=64), nullable=True),
        sa.Column("reason", sa.String(length=64), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "clinic_id is null or length(clinic_id) between 1 and 64",
            name="ck_patient_portal_audit_events_clinic_id_length",
        ),
        sa.CheckConstraint(
            "demographic_no is null or demographic_no > 0",
            name="ck_patient_portal_audit_events_demographic_no_positive",
        ),
        sa.CheckConstraint(
            (
                "event_type in ('activation', 'account.contact_update', 'account.lock', "
                "'account.mfa_update', 'account.password_change', 'account.unlock', "
                "'invite.create', 'invite.list', 'invite.resend', 'invite.revoke', "
                "'login', 'mfa.challenge', 'mfa.resend', 'mfa.verify', "
                "'password_reset.complete', 'password_reset.request', 'session.logout', "
                "'unlock_secret.create', 'unlock_secret.list', 'unlock_secret.read', "
                "'unlock_secret.revoke')"
            ),
            name="ck_patient_portal_audit_events_event_type",
        ),
        sa.CheckConstraint(
            "outcome in ('success', 'failure', 'throttled')",
            name="ck_patient_portal_audit_events_outcome",
        ),
        sa.CheckConstraint(
            "actor_type in ('patient', 'staff')",
            name="ck_patient_portal_audit_events_actor_type",
        ),
        sa.CheckConstraint(
            "invite_token_hash is null or length(invite_token_hash) = 64",
            name="ck_patient_portal_audit_events_invite_token_hash_length",
        ),
        sa.CheckConstraint(
            "client_reference_hash is null or length(client_reference_hash) = 64",
            name="ck_patient_portal_audit_events_client_reference_hash_length",
        ),
        sa.CheckConstraint(
            "length(event_type) between 1 and 64",
            name="ck_patient_portal_audit_events_event_type_length",
        ),
        sa.CheckConstraint(
            "length(outcome) between 1 and 16",
            name="ck_patient_portal_audit_events_outcome_length",
        ),
        sa.CheckConstraint(
            "length(actor_type) between 1 and 16",
            name="ck_patient_portal_audit_events_actor_type_length",
        ),
        sa.CheckConstraint(
            "reason is null or length(reason) between 1 and 64",
            name="ck_patient_portal_audit_events_reason_length",
        ),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_patient_portal_audit_events_activation_client",
        "patient_portal_audit_events",
        ["event_type", "client_reference_hash", "created_at"],
        unique=False,
    )
    op.create_index(
        "ix_patient_portal_audit_events_activation_invite",
        "patient_portal_audit_events",
        ["event_type", "invite_token_hash", "created_at"],
        unique=False,
    )
    op.create_index(
        "ix_patient_portal_audit_events_clinic_created",
        "patient_portal_audit_events",
        ["clinic_id", "created_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        "ix_patient_portal_audit_events_clinic_created",
        table_name="patient_portal_audit_events",
    )
    op.drop_index(
        "ix_patient_portal_audit_events_activation_invite",
        table_name="patient_portal_audit_events",
    )
    op.drop_index(
        "ix_patient_portal_audit_events_activation_client",
        table_name="patient_portal_audit_events",
    )
    op.drop_table("patient_portal_audit_events")
    op.drop_index(
        "ix_patient_portal_unlock_secrets_key_id",
        table_name="patient_portal_unlock_secrets",
    )
    op.drop_index(
        "ix_patient_portal_unlock_secrets_clinic_patient_status_created",
        table_name="patient_portal_unlock_secrets",
    )
    op.drop_index(
        "ix_patient_portal_unlock_secrets_account_created",
        table_name="patient_portal_unlock_secrets",
    )
    op.drop_table("patient_portal_unlock_secrets")
    op.drop_index(
        "ux_patient_portal_invites_one_pending_per_patient",
        table_name="patient_portal_invites",
    )
    op.drop_index("ux_patient_portal_invites_token_hash", table_name="patient_portal_invites")
    op.drop_index("ix_patient_portal_invites_clinic_status", table_name="patient_portal_invites")
    op.drop_index(
        "ix_patient_portal_invites_clinic_expires_at",
        table_name="patient_portal_invites",
    )
    op.drop_index(
        "ix_patient_portal_invites_clinic_demographic",
        table_name="patient_portal_invites",
    )
    op.drop_table("patient_portal_invites")
    op.drop_index(
        "ux_patient_portal_password_reset_tokens_token_hash",
        table_name="patient_portal_password_reset_tokens",
    )
    op.drop_index(
        "ix_patient_portal_password_reset_tokens_account_status",
        table_name="patient_portal_password_reset_tokens",
    )
    op.drop_table("patient_portal_password_reset_tokens")
    op.drop_index(
        "ux_patient_portal_mfa_challenges_token_hash",
        table_name="patient_portal_mfa_challenges",
    )
    op.drop_index(
        "ix_patient_portal_mfa_challenges_account_status",
        table_name="patient_portal_mfa_challenges",
    )
    op.drop_table("patient_portal_mfa_challenges")
    op.drop_index(
        "ux_patient_portal_sessions_token_hash",
        table_name="patient_portal_sessions",
    )
    op.drop_index(
        "ix_patient_portal_sessions_account_expires",
        table_name="patient_portal_sessions",
    )
    op.drop_table("patient_portal_sessions")
    op.drop_index(
        "ix_patient_portal_contact_review_requests_clinic_status_requested",
        table_name="patient_portal_contact_review_requests",
    )
    op.drop_index(
        "ix_patient_portal_contact_review_requests_account_status",
        table_name="patient_portal_contact_review_requests",
    )
    op.drop_table("patient_portal_contact_review_requests")
    op.drop_index("ix_patient_portal_accounts_status", table_name="patient_portal_accounts")
    op.drop_table("patient_portal_accounts")
