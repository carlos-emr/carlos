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

"""Scope the account username uniqueness to its clinic and audit retention overrides.

Revision ID: 0006_clinic_scoped_username
Revises: 0005_pending_email_confirmation
Create Date: 2026-08-14 12:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

_ALEMBIC_REVISION_IDENTIFIERS: dict[str, str | Sequence[str] | None] = {
    "revision": "0006_clinic_scoped_username",
    "down_revision": "0005_pending_email_confirmation",
    "branch_labels": None,
    "depends_on": None,
}
globals().update(_ALEMBIC_REVISION_IDENTIFIERS)

_AUDIT_EVENT_TYPE_CHECK_V5 = (
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

_AUDIT_EVENT_TYPE_CHECK_V6 = _AUDIT_EVENT_TYPE_CHECK_V5.replace(
    "'password_reset.request', ",
    "'password_reset.request', 'retention.policy_override', ",
)

_ACTOR_TYPE_CHECK_V1 = "actor_type in ('patient', 'staff')"
_ACTOR_TYPE_CHECK_V2 = "actor_type in ('patient', 'staff', 'system')"


def upgrade() -> None:
    # Widening a unique index can never fail on existing rows: every pair that was unique on
    # username alone is still unique on (clinic_id, username). No preflight is needed here, unlike
    # the downgrade below.
    with op.batch_alter_table("patient_portal_accounts") as batch_op:
        batch_op.drop_constraint(
            "ux_patient_portal_accounts_username",
            type_="unique",
        )
        batch_op.create_unique_constraint(
            "ux_patient_portal_accounts_username",
            ["clinic_id", "username"],
        )

    with op.batch_alter_table("patient_portal_audit_events") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_event_type",
            type_="check",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_event_type",
            _AUDIT_EVENT_TYPE_CHECK_V6,
        )
        # The retention-override event has no human actor; it records what the configuration says.
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_actor_type",
            type_="check",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_actor_type",
            _ACTOR_TYPE_CHECK_V2,
        )


def downgrade() -> None:
    connection = op.get_bind()
    # Narrowing back to a global username can fail on real data: two clinics sharing a database
    # may each hold the same username legitimately under the wider constraint. Refuse rather than
    # let the index creation fail halfway with a less useful message.
    duplicate_usernames = connection.execute(
        sa.text(
            "select count(*) from ("
            "select username from patient_portal_accounts "
            "group by username having count(*) > 1"
            ") as duplicates"
        )
    ).scalar_one()
    if duplicate_usernames:
        raise RuntimeError(
            "downgrade would violate a global username uniqueness constraint; "
            f"{duplicate_usernames} username(s) are in use by more than one clinic and must be "
            "renamed under an approved procedure before retrying"
        )
    audit_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_audit_events "
            "where event_type = 'retention.policy_override'"
        )
    ).scalar_one()
    if audit_count:
        raise RuntimeError(
            "downgrade would discard retention-override audit events; export them under an "
            "approved retention procedure before retrying"
        )

    with op.batch_alter_table("patient_portal_audit_events") as batch_op:
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_event_type",
            type_="check",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_event_type",
            _AUDIT_EVENT_TYPE_CHECK_V5,
        )
        batch_op.drop_constraint(
            "ck_patient_portal_audit_events_actor_type",
            type_="check",
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_audit_events_actor_type",
            _ACTOR_TYPE_CHECK_V1,
        )

    with op.batch_alter_table("patient_portal_accounts") as batch_op:
        batch_op.drop_constraint(
            "ux_patient_portal_accounts_username",
            type_="unique",
        )
        batch_op.create_unique_constraint(
            "ux_patient_portal_accounts_username",
            ["username"],
        )
