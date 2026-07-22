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
        "patient_portal_invites",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("demographic_no", sa.Integer(), nullable=False),
        sa.Column("token_hash", sa.String(length=64), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("created_by", sa.String(length=128), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("sent_count", sa.Integer(), nullable=False),
        sa.Column("last_sent_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_sent_by", sa.String(length=128), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_by", sa.String(length=128), nullable=True),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_patient_portal_invites_demographic_no",
        "patient_portal_invites",
        ["demographic_no"],
        unique=False,
    )
    op.create_index(
        "ix_patient_portal_invites_status",
        "patient_portal_invites",
        ["status"],
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
    op.drop_index("ix_patient_portal_invites_status", table_name="patient_portal_invites")
    op.drop_index("ix_patient_portal_invites_demographic_no", table_name="patient_portal_invites")
    op.drop_table("patient_portal_invites")
