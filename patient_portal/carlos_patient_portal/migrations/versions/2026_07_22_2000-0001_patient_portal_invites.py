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


def downgrade() -> None:
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
