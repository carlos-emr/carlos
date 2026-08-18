"""Harden portal account, contact, and unlock-secret lifecycles.

Revision ID: 0003_portal_lifecycle_hardening
Revises: 0002_staff_identity_audit
Create Date: 2026-07-28 18:00:00+00:00
"""

from collections.abc import Sequence
from uuid import uuid4

import sqlalchemy as sa
from alembic import context, op

_ALEMBIC_REVISION_IDENTIFIERS: dict[str, str | Sequence[str] | None] = {
    "revision": "0003_portal_lifecycle_hardening",
    "down_revision": "0002_staff_identity_audit",
    "branch_labels": None,
    "depends_on": None,
}
globals().update(_ALEMBIC_REVISION_IDENTIFIERS)

_AUDIT_EVENT_TYPE_CHECK_V3 = (
    "event_type in "
    "('activation', 'account.contact_update', 'account.disable', 'account.enable', "
    "'account.lock', 'account.mfa_update', 'account.password_change', 'account.unlock', "
    "'invite.create', 'invite.list', 'invite.resend', 'invite.revoke', "
    "'login', 'mfa.challenge', 'mfa.delivery', 'mfa.resend', 'mfa.verify', "
    "'password_reset.complete', 'password_reset.delivery', "
    "'password_reset.request', 'session.logout', 'staff.action', "
    "'fhir.read', 'fhir.search', "
    "'unlock_secret.create', 'unlock_secret.list', 'unlock_secret.read', "
    "'unlock_secret.publish', 'unlock_secret.revoke')"
)

_AUDIT_EVENT_TYPE_CHECK_V2 = _AUDIT_EVENT_TYPE_CHECK_V3.replace(
    "'account.contact_update', 'account.disable', 'account.enable', ",
    "'account.contact_update', ",
).replace(
    "'unlock_secret.publish', ",
    "",
).replace(
    "'staff.action', ",
    "",
)


def upgrade() -> None:
    if context.is_offline_mode():
        raise RuntimeError("migration 0003 requires an online connection for revision backfill")
    with op.batch_alter_table("patient_portal_accounts") as batch_op:
        batch_op.drop_constraint("ck_patient_portal_accounts_status", type_="check")
        batch_op.add_column(
            sa.Column("failed_mfa_count", sa.Integer(), nullable=False, server_default="0")
        )
        batch_op.add_column(sa.Column("last_mfa_email_sent_at", sa.DateTime(timezone=True)))
        batch_op.add_column(sa.Column("last_mfa_sms_sent_at", sa.DateTime(timezone=True)))
        batch_op.add_column(sa.Column("disabled_at", sa.DateTime(timezone=True)))
        batch_op.add_column(sa.Column("disabled_by", sa.String(length=128)))
        batch_op.add_column(sa.Column("disabled_by_id", sa.String(length=128)))
        batch_op.add_column(sa.Column("disabled_reason", sa.String(length=64)))
        batch_op.create_check_constraint(
            "ck_patient_portal_accounts_status",
            "status in ('active', 'disabled')",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_accounts_failed_mfa_count_non_negative",
            "failed_mfa_count >= 0",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_accounts_disabled_fields_complete",
            "(status = 'active' and disabled_at is null and disabled_by is null) or "
            "(status = 'disabled' and disabled_at is not null and disabled_by is not null)",
        )

    with op.batch_alter_table("patient_portal_contact_review_requests") as batch_op:
        batch_op.drop_constraint("ck_pp_contact_review_reviewed_present", type_="check")
        batch_op.add_column(sa.Column("revision", sa.String(length=64), nullable=True))
        batch_op.create_check_constraint(
            "ck_pp_contact_review_reviewed_present",
            "status != 'reviewed' or "
            "(reviewed_at is not null and reviewed_by is not null and "
            "review_decision is not null and "
            "review_decision in ('approved', 'rejected', 'superseded', 'legacy') and "
            "(review_decision = 'legacy' or reviewed_by_id is not null))",
        )
    connection = op.get_bind()
    review_ids = connection.execute(
        sa.text("select id from patient_portal_contact_review_requests")
    ).scalars()
    for review_id in review_ids:
        connection.execute(
            sa.text(
                "update patient_portal_contact_review_requests "
                "set revision = :revision where id = :review_id"
            ),
            {"revision": str(uuid4()), "review_id": review_id},
        )
    with op.batch_alter_table("patient_portal_contact_review_requests") as batch_op:
        batch_op.alter_column("revision", existing_type=sa.String(length=64), nullable=False)
        batch_op.create_index("ux_pp_contact_review_revision", ["revision"], unique=True)

    with op.batch_alter_table("patient_portal_unlock_secrets") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_unlock_secrets_status",
            type_="check",
        )
        batch_op.add_column(sa.Column("encryption_context", sa.String(length=36)))
        batch_op.create_check_constraint(
            "ck_patient_portal_unlock_secrets_status",
            "status in ('active', 'pending', 'available', 'revoked')",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_unlock_secrets_context_length",
            "encryption_context is null or length(encryption_context) = 36",
        )
    connection.execute(
        sa.text(
            "update patient_portal_unlock_secrets "
            "set status = 'available' where status = 'active'"
        )
    )
    with op.batch_alter_table("patient_portal_unlock_secrets") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_unlock_secrets_status",
            type_="check",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_unlock_secrets_status",
            "status in ('pending', 'available', 'revoked')",
        )

    with op.batch_alter_table("patient_portal_audit_events") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_event_type",
            type_="check",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_event_type",
            _AUDIT_EVENT_TYPE_CHECK_V3,
        )


def downgrade() -> None:
    if context.is_offline_mode():
        raise RuntimeError("migration 0003 requires an online connection for downgrade preflight")
    connection = op.get_bind()
    disabled_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_accounts where status = 'disabled' "
            "or failed_mfa_count != 0 or last_mfa_email_sent_at is not null "
            "or last_mfa_sms_sent_at is not null or disabled_at is not null "
            "or disabled_by is not null or disabled_by_id is not null "
            "or disabled_reason is not null"
        )
    ).scalar_one()
    pending_secret_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_unlock_secrets where status = 'pending'"
        )
    ).scalar_one()
    version_two_secret_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_unlock_secrets "
            "where encryption_context is not null"
        )
    ).scalar_one()
    superseded_review_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_contact_review_requests "
            "where review_decision = 'superseded'"
        )
    ).scalar_one()
    review_revision_count = connection.execute(
        sa.text("select count(*) from patient_portal_contact_review_requests")
    ).scalar_one()
    version_three_audit_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_audit_events "
            "where event_type in ("
            "'account.disable', 'account.enable', 'staff.action', 'unlock_secret.publish'"
            ")"
        )
    ).scalar_one()
    if (
        disabled_count
        or pending_secret_count
        or version_two_secret_count
        or superseded_review_count
        or review_revision_count
        or version_three_audit_count
    ):
        raise RuntimeError(
            "downgrade would discard lifecycle, review-revision, or encryption data; remove "
            "v3-only records "
            "under an approved retention/migration procedure before retrying"
        )

    with op.batch_alter_table("patient_portal_audit_events") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_event_type",
            type_="check",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_event_type",
            _AUDIT_EVENT_TYPE_CHECK_V2,
        )

    with op.batch_alter_table("patient_portal_unlock_secrets") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_unlock_secrets_context_length",
            type_="check",
        )
        batch_op.drop_constraint(
            "ck_patient_portal_unlock_secrets_status",
            type_="check",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_unlock_secrets_status",
            "status in ('active', 'available', 'revoked')",
        )
    connection.execute(
        sa.text(
            "update patient_portal_unlock_secrets "
            "set status = 'active' where status = 'available'"
        )
    )
    with op.batch_alter_table("patient_portal_unlock_secrets") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_unlock_secrets_status",
            type_="check",
        )
        batch_op.drop_column("encryption_context")
        batch_op.create_check_constraint(
            "ck_patient_portal_unlock_secrets_status",
            "status in ('active', 'revoked')",
        )

    with op.batch_alter_table("patient_portal_contact_review_requests") as batch_op:
        batch_op.drop_index("ux_pp_contact_review_revision")
        batch_op.drop_constraint("ck_pp_contact_review_reviewed_present", type_="check")
        batch_op.drop_column("revision")
        batch_op.create_check_constraint(
            "ck_pp_contact_review_reviewed_present",
            "status != 'reviewed' or "
            "(reviewed_at is not null and reviewed_by is not null and "
            "review_decision is not null and "
            "review_decision in ('approved', 'rejected', 'legacy') and "
            "(review_decision = 'legacy' or reviewed_by_id is not null))",
        )

    with op.batch_alter_table("patient_portal_accounts") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_accounts_disabled_fields_complete",
            type_="check",
        )
        batch_op.drop_constraint(
            "ck_patient_portal_accounts_failed_mfa_count_non_negative",
            type_="check",
        )
        batch_op.drop_constraint("ck_patient_portal_accounts_status", type_="check")
        batch_op.drop_column("disabled_reason")
        batch_op.drop_column("disabled_by_id")
        batch_op.drop_column("disabled_by")
        batch_op.drop_column("disabled_at")
        batch_op.drop_column("last_mfa_sms_sent_at")
        batch_op.drop_column("last_mfa_email_sent_at")
        batch_op.drop_column("failed_mfa_count")
        batch_op.create_check_constraint(
            "ck_patient_portal_accounts_status",
            "status in ('active')",
        )
