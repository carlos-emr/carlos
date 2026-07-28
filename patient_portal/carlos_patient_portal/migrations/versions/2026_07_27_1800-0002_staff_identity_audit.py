"""Add durable staff identity and audit targets.

Revision ID: 0002_staff_identity_audit
Revises: 0001_patient_portal_invites
Create Date: 2026-07-27 18:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

_ALEMBIC_REVISION_IDENTIFIERS: dict[str, str | Sequence[str] | None] = {
    "revision": "0002_staff_identity_audit",
    "down_revision": "0001_patient_portal_invites",
    "branch_labels": None,
    "depends_on": None,
}
globals().update(_ALEMBIC_REVISION_IDENTIFIERS)

_AUDIT_EVENT_TYPE_CHECK_V2 = (
    "event_type in "
    "('activation', 'account.contact_update', 'account.lock', "
    "'account.mfa_update', 'account.password_change', 'account.unlock', "
    "'invite.create', 'invite.list', 'invite.resend', 'invite.revoke', "
    "'login', 'mfa.challenge', 'mfa.delivery', 'mfa.resend', 'mfa.verify', "
    "'password_reset.complete', 'password_reset.delivery', "
    "'password_reset.request', 'session.logout', 'fhir.read', 'fhir.search', "
    "'unlock_secret.create', 'unlock_secret.list', 'unlock_secret.read', "
    "'unlock_secret.revoke')"
)

_AUDIT_EVENT_TYPE_CHECK_V1 = _AUDIT_EVENT_TYPE_CHECK_V2.replace(
    ", 'fhir.read', 'fhir.search'",
    "",
)


def upgrade() -> None:
    op.add_column(
        "patient_portal_accounts",
        sa.Column("locked_by_id", sa.String(length=128), nullable=True),
    )
    with op.batch_alter_table("patient_portal_contact_review_requests") as batch_op:
        batch_op.drop_constraint("ck_pp_contact_review_unreviewed_null", type_="check")
        batch_op.drop_constraint("ck_pp_contact_review_reviewed_present", type_="check")
        batch_op.add_column(sa.Column("reviewed_by_id", sa.String(length=128), nullable=True))
        batch_op.add_column(sa.Column("review_decision", sa.String(length=16), nullable=True))
        batch_op.create_check_constraint(
            "ck_pp_contact_review_unreviewed_null",
            (
                "status = 'reviewed' or "
                "(reviewed_at is null and reviewed_by is null and review_decision is null)"
            ),
        )
        batch_op.create_check_constraint(
            "ck_pp_contact_review_reviewed_present",
            (
                "status != 'reviewed' or "
                "(reviewed_at is not null and reviewed_by is not null and "
                "review_decision in ('approved', 'rejected'))"
            ),
        )
    op.add_column(
        "patient_portal_invites",
        sa.Column("created_by_id", sa.String(length=128), nullable=True),
    )
    op.add_column(
        "patient_portal_invites",
        sa.Column("last_sent_by_id", sa.String(length=128), nullable=True),
    )
    op.add_column(
        "patient_portal_invites",
        sa.Column("revoked_by_id", sa.String(length=128), nullable=True),
    )
    op.add_column(
        "patient_portal_unlock_secrets",
        sa.Column("created_by_id", sa.String(length=128), nullable=True),
    )
    op.add_column(
        "patient_portal_unlock_secrets",
        sa.Column("revoked_by_id", sa.String(length=128), nullable=True),
    )
    op.create_index(
        "ux_pp_unlock_secrets_source_reference",
        "patient_portal_unlock_secrets",
        ["clinic_id", "secret_type", "source_reference"],
        unique=True,
        sqlite_where=sa.text("source_reference is not null"),
        postgresql_where=sa.text("source_reference is not null"),
    )
    with op.batch_alter_table("patient_portal_audit_events") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_event_type",
            type_="check",
        )
        batch_op.add_column(sa.Column("actor_id", sa.String(length=128), nullable=True))
        batch_op.add_column(sa.Column("resource_type", sa.String(length=64), nullable=True))
        batch_op.add_column(sa.Column("resource_id", sa.String(length=128), nullable=True))
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_actor_id_length",
            "actor_id is null or length(actor_id) between 1 and 128",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_resource_type_length",
            "resource_type is null or length(resource_type) between 1 and 64",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_resource_id_length",
            "resource_id is null or length(resource_id) between 1 and 128",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_event_type",
            _AUDIT_EVENT_TYPE_CHECK_V2,
        )
    op.create_index(
        "ix_patient_portal_audit_events_actor_id_created",
        "patient_portal_audit_events",
        ["actor_id", "created_at"],
    )
    op.create_index(
        "ix_patient_portal_audit_events_resource_created",
        "patient_portal_audit_events",
        ["resource_type", "resource_id", "created_at"],
    )


def downgrade() -> None:
    connection = op.get_bind()
    version_two_audit_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_audit_events "
            "where event_type in ('fhir.read', 'fhir.search') "
            "or actor_id is not null or resource_type is not null or resource_id is not null"
        )
    ).scalar_one()
    invite_staff_identity_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_invites "
            "where created_by_id is not null or last_sent_by_id is not null"
        )
    ).scalar_one()
    if version_two_audit_count or invite_staff_identity_count:
        raise RuntimeError(
            "downgrade would discard staff identity or FHIR audit metadata; archive or "
            "remove those records under an approved retention procedure before retrying"
        )

    op.drop_index(
        "ix_patient_portal_audit_events_resource_created",
        table_name="patient_portal_audit_events",
    )
    op.drop_index(
        "ix_patient_portal_audit_events_actor_id_created",
        table_name="patient_portal_audit_events",
    )
    with op.batch_alter_table("patient_portal_audit_events") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_event_type",
            type_="check",
        )
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_resource_id_length",
            type_="check",
        )
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_resource_type_length",
            type_="check",
        )
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_actor_id_length",
            type_="check",
        )
        batch_op.drop_column("resource_id")
        batch_op.drop_column("resource_type")
        batch_op.drop_column("actor_id")
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_event_type",
            _AUDIT_EVENT_TYPE_CHECK_V1,
        )
    op.drop_index(
        "ux_pp_unlock_secrets_source_reference",
        table_name="patient_portal_unlock_secrets",
    )
    op.drop_column("patient_portal_unlock_secrets", "revoked_by_id")
    op.drop_column("patient_portal_unlock_secrets", "created_by_id")
    op.drop_column("patient_portal_invites", "revoked_by_id")
    op.drop_column("patient_portal_invites", "last_sent_by_id")
    op.drop_column("patient_portal_invites", "created_by_id")
    with op.batch_alter_table("patient_portal_contact_review_requests") as batch_op:
        batch_op.drop_constraint("ck_pp_contact_review_reviewed_present", type_="check")
        batch_op.drop_constraint("ck_pp_contact_review_unreviewed_null", type_="check")
        batch_op.drop_column("review_decision")
        batch_op.drop_column("reviewed_by_id")
        batch_op.create_check_constraint(
            "ck_pp_contact_review_unreviewed_null",
            "status = 'reviewed' or (reviewed_at is null and reviewed_by is null)",
        )
        batch_op.create_check_constraint(
            "ck_pp_contact_review_reviewed_present",
            "status != 'reviewed' or (reviewed_at is not null and reviewed_by is not null)",
        )
    op.drop_column("patient_portal_accounts", "locked_by_id")
