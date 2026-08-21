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

"""Retain each patient portal invite issuance.

Revision ID: 0004_invite_issuance_history
Revises: 0003_portal_lifecycle_hardening
Create Date: 2026-07-28 20:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

_ALEMBIC_REVISION_IDENTIFIERS: dict[str, str | Sequence[str] | None] = {
    "revision": "0004_invite_issuance_history",
    "down_revision": "0003_portal_lifecycle_hardening",
    "branch_labels": None,
    "depends_on": None,
}
globals().update(_ALEMBIC_REVISION_IDENTIFIERS)


def upgrade() -> None:
    with op.batch_alter_table("patient_portal_invites") as batch_op:
        batch_op.drop_constraint("ck_patient_portal_invites_status", type_="check")
        batch_op.add_column(sa.Column("supersedes_invite_id", sa.Integer()))
        batch_op.create_foreign_key(
            "fk_patient_portal_invites_supersedes",
            "patient_portal_invites",
            ["supersedes_invite_id"],
            ["id"],
            ondelete="SET NULL",
        )
        batch_op.create_index(
            "ix_patient_portal_invites_supersedes_invite_id",
            ["supersedes_invite_id"],
        )
        batch_op.create_check_constraint(
            "ck_patient_portal_invites_status",
            "status in ('pending', 'revoked', 'accepted', 'superseded')",
        )


def downgrade() -> None:
    connection = op.get_bind()
    superseded_count = connection.execute(
        sa.text(
            "select count(*) from patient_portal_invites "
            "where status = 'superseded' or supersedes_invite_id is not null"
        )
    ).scalar_one()
    if superseded_count:
        raise RuntimeError(
            "downgrade would discard invite issuance history; remove superseded invite "
            "records under an approved retention procedure before retrying"
        )

    with op.batch_alter_table("patient_portal_invites") as batch_op:
        batch_op.drop_constraint("ck_patient_portal_invites_status", type_="check")
        batch_op.drop_index("ix_patient_portal_invites_supersedes_invite_id")
        batch_op.drop_constraint(
            "fk_patient_portal_invites_supersedes",
            type_="foreignkey",
        )
        batch_op.drop_column("supersedes_invite_id")
        batch_op.create_check_constraint(
            "ck_patient_portal_invites_status",
            "status in ('pending', 'revoked', 'accepted')",
        )
