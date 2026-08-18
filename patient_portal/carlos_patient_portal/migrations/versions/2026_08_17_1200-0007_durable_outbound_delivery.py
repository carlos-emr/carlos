"""Add the encrypted durable outbound-delivery queue.

Revision ID: 0007_durable_outbound_delivery
Revises: 0006_clinic_scoped_username
Create Date: 2026-08-17 12:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

_ALEMBIC_REVISION_IDENTIFIERS: dict[str, str | Sequence[str] | None] = {
    "revision": "0007_durable_outbound_delivery",
    "down_revision": "0006_clinic_scoped_username",
    "branch_labels": None,
    "depends_on": None,
}
globals().update(_ALEMBIC_REVISION_IDENTIFIERS)


def upgrade() -> None:
    op.create_table(
        "patient_portal_outbound_deliveries",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column(
            "account_id",
            sa.Integer(),
            sa.ForeignKey("patient_portal_accounts.id"),
            nullable=False,
        ),
        sa.Column(
            "reset_token_id",
            sa.Integer(),
            sa.ForeignKey("patient_portal_password_reset_tokens.id"),
            nullable=True,
        ),
        sa.Column("kind", sa.String(length=32), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("encrypted_payload", sa.LargeBinary(), nullable=False),
        sa.Column("encryption_nonce", sa.LargeBinary(), nullable=False),
        sa.Column("encryption_key_id", sa.String(length=64), nullable=False),
        sa.Column("message_id", sa.String(length=255), nullable=False),
        sa.Column("attempt_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("available_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("lease_expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("last_failure_code", sa.String(length=64), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("delivered_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint(
            "kind in ('password_reset', 'contact_change')",
            name="ck_pp_outbound_delivery_kind",
        ),
        sa.CheckConstraint(
            "status in ('pending', 'processing', 'delivered', 'failed')",
            name="ck_pp_outbound_delivery_status",
        ),
        sa.CheckConstraint(
            "attempt_count >= 0",
            name="ck_pp_outbound_delivery_attempts_non_negative",
        ),
        sa.CheckConstraint(
            "length(encryption_key_id) between 1 and 64",
            name="ck_pp_outbound_delivery_key_id_length",
        ),
        sa.CheckConstraint(
            "length(encryption_nonce) = 12",
            name="ck_pp_outbound_delivery_nonce_length",
        ),
        sa.CheckConstraint(
            "length(message_id) between 1 and 255",
            name="ck_pp_outbound_delivery_message_id_length",
        ),
        sa.CheckConstraint(
            "status != 'delivered' or delivered_at is not null",
            name="ck_pp_outbound_delivery_delivered_at_present",
        ),
    )
    op.create_index(
        "ix_pp_outbound_delivery_available",
        "patient_portal_outbound_deliveries",
        ["status", "available_at", "id"],
    )
    op.create_index(
        "ux_pp_outbound_delivery_message_id",
        "patient_portal_outbound_deliveries",
        ["message_id"],
        unique=True,
    )


def downgrade() -> None:
    connection = op.get_bind()
    undelivered_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_outbound_deliveries "
            "where status in ('pending', 'processing')"
        )
    ).scalar_one()
    if undelivered_count:
        raise RuntimeError(
            "downgrade would discard queued outbound deliveries; drain or explicitly fail them "
            "under an approved procedure before retrying"
        )
    op.drop_index(
        "ux_pp_outbound_delivery_message_id",
        table_name="patient_portal_outbound_deliveries",
    )
    op.drop_index(
        "ix_pp_outbound_delivery_available",
        table_name="patient_portal_outbound_deliveries",
    )
    op.drop_table("patient_portal_outbound_deliveries")
