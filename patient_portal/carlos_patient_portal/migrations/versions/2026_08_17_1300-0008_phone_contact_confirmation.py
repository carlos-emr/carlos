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
    op.create_index(
        "ix_pp_sessions_expires", "patient_portal_sessions", ["expires_at", "id"]
    )
    op.create_index(
        "ix_pp_sessions_revoked", "patient_portal_sessions", ["revoked_at", "id"]
    )
    op.create_index(
        "ix_pp_mfa_expires", "patient_portal_mfa_challenges", ["expires_at", "id"]
    )
    op.create_index(
        "ix_pp_reset_expires",
        "patient_portal_password_reset_tokens",
        ["expires_at", "id"],
    )
    op.create_index(
        "ix_pp_invites_expires_status",
        "patient_portal_invites",
        ["expires_at", "status", "id"],
    )
    op.create_index(
        "ix_pp_audit_created", "patient_portal_audit_events", ["created_at", "id"]
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
    op.drop_index("ix_pp_audit_created", table_name="patient_portal_audit_events")
    op.drop_index("ix_pp_invites_expires_status", table_name="patient_portal_invites")
    op.drop_index(
        "ix_pp_reset_expires", table_name="patient_portal_password_reset_tokens"
    )
    op.drop_index("ix_pp_mfa_expires", table_name="patient_portal_mfa_challenges")
    op.drop_index("ix_pp_sessions_revoked", table_name="patient_portal_sessions")
    op.drop_index("ix_pp_sessions_expires", table_name="patient_portal_sessions")
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
