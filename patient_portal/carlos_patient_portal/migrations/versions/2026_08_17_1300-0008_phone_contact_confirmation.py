"""Require ownership proof for a changed phone destination.

Revision ID: 0008_phone_contact_confirmation
Revises: 0007_durable_outbound_delivery
Create Date: 2026-08-17 13:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

_ALEMBIC_REVISION_IDENTIFIERS: dict[str, str | Sequence[str] | None] = {
    "revision": "0008_phone_contact_confirmation",
    "down_revision": "0007_durable_outbound_delivery",
    "branch_labels": None,
    "depends_on": None,
}
globals().update(_ALEMBIC_REVISION_IDENTIFIERS)


def upgrade() -> None:
    with op.batch_alter_table("patient_portal_email_change_requests") as batch_op:
        batch_op.add_column(sa.Column("email_confirmed_at", sa.DateTime(timezone=True)))
        batch_op.add_column(sa.Column("phone_code_hash", sa.String(length=64)))
        batch_op.add_column(sa.Column("phone_confirmed_at", sa.DateTime(timezone=True)))
        batch_op.add_column(sa.Column("phone_code_sent_at", sa.DateTime(timezone=True)))
        batch_op.add_column(
            sa.Column(
                "phone_failed_attempts",
                sa.Integer(),
                nullable=False,
                server_default="0",
            )
        )
        batch_op.create_check_constraint(
            "ck_pp_email_change_phone_code_hash_length",
            "phone_code_hash is null or length(phone_code_hash) = 64",
        )
        batch_op.create_check_constraint(
            "ck_pp_email_change_phone_attempts_non_negative",
            "phone_failed_attempts >= 0",
        )
    # Existing pending rows were created when only new email ownership was required. Preserve that
    # contract across upgrade by treating their proposed phone value as already confirmed.
    op.execute(
        "update patient_portal_email_change_requests "
        "set phone_confirmed_at = created_at where status = 'pending'"
    )


def downgrade() -> None:
    connection = op.get_bind()
    pending_phone_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_email_change_requests "
            "where status = 'pending' and phone_code_hash is not null"
        )
    ).scalar_one()
    if pending_phone_count:
        raise RuntimeError(
            "downgrade would discard pending phone-ownership proofs; confirm or revoke them "
            "under an approved procedure before retrying"
        )
    with op.batch_alter_table("patient_portal_email_change_requests") as batch_op:
        batch_op.drop_constraint(
            "ck_pp_email_change_phone_attempts_non_negative", type_="check"
        )
        batch_op.drop_constraint("ck_pp_email_change_phone_code_hash_length", type_="check")
        batch_op.drop_column("phone_failed_attempts")
        batch_op.drop_column("phone_code_sent_at")
        batch_op.drop_column("phone_confirmed_at")
        batch_op.drop_column("phone_code_hash")
        batch_op.drop_column("email_confirmed_at")
