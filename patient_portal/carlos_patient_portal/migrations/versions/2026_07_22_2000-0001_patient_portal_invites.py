"""Add patient portal invites.

Revision ID: 0001_patient_portal_invites
Revises:
Create Date: 2026-07-22 20:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0001_patient_portal_invites"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "patient_portal_accounts",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("clinic_id", sa.String(length=64), nullable=False),
        sa.Column("demographic_no", sa.Integer(), nullable=False),
        sa.Column("username", sa.String(length=64), nullable=False),
        sa.Column("email", sa.String(length=254), nullable=False),
        sa.Column("password_hash", sa.String(length=512), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
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
        sa.Column("detail", sa.Text(), nullable=True),
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
            "event_type in ('activation', 'invite.create', 'invite.resend', 'invite.revoke')",
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
    op.drop_index("ix_patient_portal_accounts_status", table_name="patient_portal_accounts")
    op.drop_table("patient_portal_accounts")
