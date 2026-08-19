"""Make three state invariants structural and index the outbox foreign keys.

Each constraint here already held in application code; the point is that nothing stopped a future
edit, a new code path, or a manual UPDATE from breaking it:

- A session carrying revoked_reason without revoked_at is a live bearer session that the audit trail
  reports as revoked, because authentication gates on the timestamp alone.
- An outbound delivery in 'processing' with no lease is invisible to the reclaim query
  (`lease_expires_at <= now` is NULL, never true), so it is never retried, never reaches 'failed',
  and strands a password-reset message with no failure code and no alert.
- A confirmed contact change without both ownership proofs would move the MFA destination and sync
  to the CARLOS demographic record on unproven input.

The indexes cover patient_portal_outbound_deliveries.reset_token_id, which is ON DELETE CASCADE
while reset tokens are bulk-deleted by cleanup-transient-auth. Unindexed, PostgreSQL enforces the
cascade with a sequential scan per deleted parent row, so the DELETE exceeds statement_timeout and
transient-auth cleanup stops completing at all.

Revision ID: 0009_invariants_outbox_indexes
Revises: 0008_phone_contact_confirmation
Create Date: 2026-08-18 12:00:00+00:00
"""

from collections.abc import Sequence

from alembic import context, op
from sqlalchemy import text

_ALEMBIC_REVISION_IDENTIFIERS: dict[str, str | Sequence[str] | None] = {
    "revision": "0009_invariants_outbox_indexes",
    "down_revision": "0008_phone_contact_confirmation",
    "branch_labels": None,
    "depends_on": None,
}
globals().update(_ALEMBIC_REVISION_IDENTIFIERS)


_SESSION_REVOCATION_CHECK = (
    "(revoked_at is null and revoked_reason is null) or "
    "(revoked_at is not null and revoked_reason is not null)"
)

_OUTBOX_LEASE_CHECK = (
    "(status = 'processing' and lease_expires_at is not null) or "
    "(status != 'processing' and lease_expires_at is null)"
)

_EMAIL_CHANGE_PROOF_CHECK = (
    "status != 'confirmed' or "
    "(email_confirmed_at is not null and phone_confirmed_at is not null)"
)


def upgrade() -> None:
    if context.is_offline_mode():
        raise RuntimeError(
            "migration 0009 requires an online connection to repair rows before adding constraints"
        )

    connection = op.get_bind()

    # Repair rather than refuse. Each of these is a half-written state the application could not
    # act on anyway, and an operator cannot reasonably hand-resolve them: a revocation reason
    # without a timestamp is an unenforced revocation, so completing it is the safe direction.
    connection.execute(
        text(
            "update patient_portal_sessions set revoked_reason = null "
            "where revoked_at is null and revoked_reason is not null"
        )
    )
    # A processing row with no lease is already unreachable by the worker; returning it to pending
    # makes it eligible for a fresh claim instead of leaving it stranded.
    connection.execute(
        text(
            "update patient_portal_outbound_deliveries set status = 'pending' "
            "where status = 'processing' and lease_expires_at is null"
        )
    )
    connection.execute(
        text(
            "update patient_portal_outbound_deliveries set lease_expires_at = null "
            "where status != 'processing' and lease_expires_at is not null"
        )
    )

    with op.batch_alter_table("patient_portal_sessions") as batch_op:
        batch_op.create_check_constraint(
            "ck_patient_portal_sessions_revocation_fields_complete",
            _SESSION_REVOCATION_CHECK,
        )

    with op.batch_alter_table("patient_portal_outbound_deliveries") as batch_op:
        batch_op.create_check_constraint(
            "ck_pp_outbound_delivery_lease_matches_status",
            _OUTBOX_LEASE_CHECK,
        )

    with op.batch_alter_table("patient_portal_email_change_requests") as batch_op:
        batch_op.create_check_constraint(
            "ck_pp_email_change_confirmed_requires_both_proofs",
            _EMAIL_CHANGE_PROOF_CHECK,
        )

    op.create_index(
        "ix_pp_outbound_delivery_reset_token",
        "patient_portal_outbound_deliveries",
        ["reset_token_id"],
    )
    op.create_index(
        "ix_pp_outbound_delivery_account",
        "patient_portal_outbound_deliveries",
        ["account_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_pp_outbound_delivery_account", "patient_portal_outbound_deliveries")
    op.drop_index("ix_pp_outbound_delivery_reset_token", "patient_portal_outbound_deliveries")

    with op.batch_alter_table("patient_portal_email_change_requests") as batch_op:
        batch_op.drop_constraint(
            "ck_pp_email_change_confirmed_requires_both_proofs",
            type_="check",
        )

    with op.batch_alter_table("patient_portal_outbound_deliveries") as batch_op:
        batch_op.drop_constraint("ck_pp_outbound_delivery_lease_matches_status", type_="check")

    with op.batch_alter_table("patient_portal_sessions") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_sessions_revocation_fields_complete",
            type_="check",
        )
