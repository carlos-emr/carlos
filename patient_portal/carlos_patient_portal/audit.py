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

from dataclasses import dataclass
from datetime import datetime
from hashlib import sha256
from hmac import new as new_hmac

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from carlos_patient_portal.models import (
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_EVENT_ACTIVATION,
    AUDIT_OUTCOME_FAILURE,
    HASH_LENGTH,
    PatientPortalAuditEvent,
    utc_now,
)

UNKNOWN_CLIENT_REFERENCE = "unknown"


@dataclass(frozen=True)
class ActivationFailureSummary:
    count: int
    oldest_created_at: datetime | None


def hash_sensitive_reference(secret: str, purpose: str, value: str) -> str:
    normalized_value = value.strip() or UNKNOWN_CLIENT_REFERENCE
    return new_hmac(
        secret.encode("utf-8"),
        f"{purpose}:{normalized_value}".encode(),
        sha256,
    ).hexdigest()


# Keyword-only fields deliberately mirror the sparse audit row; grouping them would only hide which
# values each security-sensitive call records.
def record_audit_event(
    session: Session,  # NOSONAR
    *,
    event_type: str,
    outcome: str,
    actor_type: str,
    actor: str | None = None,
    actor_id: str | None = None,
    clinic_id: str | None = None,
    demographic_no: int | None = None,
    invite_id: int | None = None,
    account_id: int | None = None,
    invite_token_hash: str | None = None,
    client_reference_hash: str | None = None,
    resource_type: str | None = None,
    resource_id: str | None = None,
    reason: str | None = None,
) -> PatientPortalAuditEvent:
    audit_event = PatientPortalAuditEvent(
        clinic_id=clinic_id,
        event_type=event_type,
        outcome=outcome,
        actor_type=actor_type,
        actor=actor,
        actor_id=actor_id,
        demographic_no=demographic_no,
        invite_id=invite_id,
        account_id=account_id,
        invite_token_hash=invite_token_hash,
        client_reference_hash=client_reference_hash,
        resource_type=resource_type,
        resource_id=resource_id,
        reason=reason,
        created_at=utc_now(),
    )
    session.add(audit_event)
    session.flush()
    return audit_event


def summarize_recent_activation_failures(
    session: Session,
    *,
    since: datetime,
    invite_token_hash: str | None = None,
    client_reference_hash: str | None = None,
) -> ActivationFailureSummary:
    if invite_token_hash is None and client_reference_hash is None:
        raise ValueError("invite_token_hash or client_reference_hash is required")

    statement = (
        select(
            func.count(PatientPortalAuditEvent.id),
            func.min(PatientPortalAuditEvent.created_at),
        )
        .select_from(PatientPortalAuditEvent)
        .where(
            PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION,
            PatientPortalAuditEvent.outcome == AUDIT_OUTCOME_FAILURE,
            PatientPortalAuditEvent.created_at >= since,
        )
    )
    if invite_token_hash is not None:
        if len(invite_token_hash) != HASH_LENGTH:
            raise ValueError("invite_token_hash must be a hash value")
        statement = statement.where(PatientPortalAuditEvent.invite_token_hash == invite_token_hash)
    if client_reference_hash is not None:
        if len(client_reference_hash) != HASH_LENGTH:
            raise ValueError("client_reference_hash must be a hash value")
        statement = statement.where(
            PatientPortalAuditEvent.client_reference_hash == client_reference_hash
        )
    count, oldest_created_at = session.execute(statement).one()
    return ActivationFailureSummary(
        count=int(count or 0),
        oldest_created_at=oldest_created_at,
    )


def record_activation_failure(
    session: Session,
    *,
    invite_token_hash: str,
    client_reference_hash: str,
    reason: str,
    clinic_id: str | None = None,
    demographic_no: int | None = None,
    invite_id: int | None = None,
) -> PatientPortalAuditEvent:
    return record_audit_event(
        session,
        event_type=AUDIT_EVENT_ACTIVATION,
        outcome=AUDIT_OUTCOME_FAILURE,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        clinic_id=clinic_id,
        demographic_no=demographic_no,
        invite_id=invite_id,
        invite_token_hash=invite_token_hash,
        client_reference_hash=client_reference_hash,
        reason=reason,
    )
