# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project

"""Hold a portal email change until the new mailbox confirms it.

Revision ID: 0005_pending_email_confirmation
Revises: 0004_invite_issuance_history
Create Date: 2026-08-13 12:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

_ALEMBIC_REVISION_IDENTIFIERS: dict[str, str | Sequence[str] | None] = {
    "revision": "0005_pending_email_confirmation",
    "down_revision": "0004_invite_issuance_history",
    "branch_labels": None,
    "depends_on": None,
}
globals().update(_ALEMBIC_REVISION_IDENTIFIERS)

_AUDIT_EVENT_TYPE_CHECK_V4 = (
    "event_type in "
    "('activation', 'account.contact_update', 'account.disable', "
    "'account.email_change_confirm', 'account.email_change_request', "
    "'account.enable', 'account.lock', "
    "'account.mfa_update', 'account.password_change', 'account.unlock', "
    "'invite.create', 'invite.list', 'invite.resend', 'invite.revoke', "
    "'login', 'mfa.challenge', 'mfa.delivery', 'mfa.resend', 'mfa.verify', "
    "'password_reset.complete', 'password_reset.delivery', "
    "'password_reset.request', 'session.logout', 'staff.action', "
    "'fhir.read', 'fhir.search', "
    "'unlock_secret.create', 'unlock_secret.list', 'unlock_secret.read', "
    "'unlock_secret.publish', 'unlock_secret.revoke')"
)

_AUDIT_EVENT_TYPE_CHECK_V3 = _AUDIT_EVENT_TYPE_CHECK_V4.replace(
    "'account.email_change_confirm', 'account.email_change_request', ",
    "",
)

def upgrade() -> None:
    op.create_table(
        "patient_portal_email_change_requests",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column(
            "account_id",
            sa.Integer(),
            sa.ForeignKey("patient_portal_accounts.id"),
            nullable=False,
        ),
        sa.Column("token_hash", sa.String(length=64), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("new_email", sa.String(length=254), nullable=False),
        sa.Column("new_phone_number", sa.String(length=32), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("confirmed_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint(
            "length(token_hash) = 64",
            name="ck_pp_email_change_token_hash_length",
        ),
        sa.CheckConstraint(
            "status in ('pending', 'confirmed', 'revoked')",
            name="ck_pp_email_change_status",
        ),
        sa.CheckConstraint(
            "expires_at > created_at",
            name="ck_pp_email_change_expires_after_created",
        ),
        sa.CheckConstraint(
            "length(new_email) between 1 and 254",
            name="ck_pp_email_change_new_email_length",
        ),
        sa.CheckConstraint(
            "new_phone_number is null or length(new_phone_number) between 1 and 32",
            name="ck_pp_email_change_new_phone_length",
        ),
        sa.CheckConstraint(
            "status = 'confirmed' or confirmed_at is null",
            name="ck_pp_email_change_unconfirmed_confirmed_at_null",
        ),
        sa.CheckConstraint(
            "status != 'confirmed' or confirmed_at is not null",
            name="ck_pp_email_change_confirmed_at_present",
        ),
    )
    op.create_index(
        "ux_pp_email_change_token_hash",
        "patient_portal_email_change_requests",
        ["token_hash"],
        unique=True,
    )
    op.create_index(
        "ix_pp_email_change_account_status",
        "patient_portal_email_change_requests",
        ["account_id", "status"],
    )
    op.create_index(
        "ux_pp_email_change_pending_account",
        "patient_portal_email_change_requests",
        ["account_id"],
        unique=True,
        sqlite_where=sa.text("status = 'pending'"),
        postgresql_where=sa.text("status = 'pending'"),
    )
    with op.batch_alter_table("patient_portal_audit_events") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_event_type",
            type_="check",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_event_type",
            _AUDIT_EVENT_TYPE_CHECK_V4,
        )


def downgrade() -> None:
    # Same refusal contract as 0003 and 0004: a rollback must not silently delete evidence that a
    # patient asked to move the address their MFA codes and reset links are delivered to.
    connection = op.get_bind()
    pending_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_email_change_requests where status = 'pending'"
        )
    ).scalar_one()
    if pending_count:
        raise RuntimeError(
            "downgrade would drop pending email-change requests; confirm, expire, or revoke them "
            "under an approved procedure before retrying"
        )
    audit_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_audit_events "
            "where event_type in ('account.email_change_confirm', 'account.email_change_request')"
        )
    ).scalar_one()
    if audit_count:
        raise RuntimeError(
            "downgrade would discard email-change audit events; export them under an approved "
            "retention procedure before retrying"
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
    op.drop_index(
        "ux_pp_email_change_pending_account",
        table_name="patient_portal_email_change_requests",
    )
    op.drop_index(
        "ix_pp_email_change_account_status",
        table_name="patient_portal_email_change_requests",
    )
    op.drop_index(
        "ux_pp_email_change_token_hash",
        table_name="patient_portal_email_change_requests",
    )
    op.drop_table("patient_portal_email_change_requests")
