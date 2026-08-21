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

"""Development-only staff API, registered only when the portal runs with dev admin enabled.

Stands in for the CARLOS-backed staff actions until the Java side is wired to
`internal_routes.py`. It derives its actor from a request header rather than an authenticated
session, which is exactly why it must never be reachable outside development.
"""

from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Query, status
from fastapi import Path as PathParam
from sqlalchemy.orm import Session

from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.auth import AccountNotFoundError, unlock_patient_account
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.invites import (
    DEFAULT_INVITE_LIST_LIMIT,
    MAX_INVITE_LIST_LIMIT,
    AcceptedInviteError,
    AccountAlreadyExistsError,
    InviteNotFoundError,
    PendingInviteExistsError,
    RevokedInviteError,
    SupersededInviteError,
    create_invite,
    list_invites,
    resend_invite,
    revoke_invite,
)
from carlos_patient_portal.models import (
    AUDIT_ACTOR_TYPE_STAFF,
    AUDIT_EVENT_INVITE_LIST,
    AUDIT_OUTCOME_SUCCESS,
)
from carlos_patient_portal.runtime import (
    MAX_DATABASE_ID,
    MAX_PAGE_OFFSET,
    PortalRuntime,
    RouteDependencies,
    function_scoped_database_dependency,
)
from carlos_patient_portal.schemas import (
    AccountAdminResponse,
    InviteCreateRequest,
    InviteResponse,
    InviteTokenResponse,
)
from carlos_patient_portal.web_support import (
    DEV_ADMIN_COMMON_RESPONSES,
    DEV_ADMIN_CONFLICT_RESPONSES,
    account_admin_response_payload,
    get_invite_create_request,
    invite_response_payload,
)


def register_dev_admin_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    get_dev_admin_actor = route_dependencies.get_dev_admin_actor
    identity_proof_secret = runtime.identity_proof_secret

    if settings.is_dev_admin_enabled:

        @app.post(
            "/dev/admin/invites",
            response_model=InviteTokenResponse,
            status_code=status.HTTP_201_CREATED,
            responses=DEV_ADMIN_CONFLICT_RESPONSES,
        )
        def dev_create_invite(
            actor: Annotated[str, Depends(get_dev_admin_actor)],
            payload: Annotated[InviteCreateRequest, Depends(get_invite_create_request)],
            session: Annotated[
                Session,
                function_scoped_database_dependency(get_app_database_session),
            ],
        ) -> dict[str, object]:
            identity_proof = IdentityProof(
                email=payload.email,
                date_of_birth=payload.date_of_birth,
                health_card_number=payload.health_card_number,
            )
            try:
                invite, invite_token = create_invite(
                    session,
                    payload.demographic_no,
                    actor,
                    identity_proof=identity_proof,
                    proof_secret=identity_proof_secret,
                    clinic_id=settings.clinic_id,
                )
            except AccountAlreadyExistsError as exc:
                raise HTTPException(
                    status_code=409,
                    detail="patient already has a portal account",
                ) from exc
            except PendingInviteExistsError as exc:
                raise HTTPException(
                    status_code=409,
                    detail="pending invite already exists",
                ) from exc
            return invite_response_payload(invite, invite_token)

        @app.get(
            "/dev/admin/invites",
            response_model=list[InviteResponse],
            responses=DEV_ADMIN_COMMON_RESPONSES,
        )
        def dev_list_invites(
            actor: Annotated[str, Depends(get_dev_admin_actor)],
            session: Annotated[
                Session,
                function_scoped_database_dependency(get_app_database_session),
            ],
            demographic_no: Annotated[int | None, Query(gt=0)] = None,
            limit: Annotated[
                int,
                Query(ge=1, le=MAX_INVITE_LIST_LIMIT),
            ] = DEFAULT_INVITE_LIST_LIMIT,
            offset: Annotated[int, Query(ge=0, le=MAX_PAGE_OFFSET)] = 0,
        ) -> list[dict[str, object]]:
            invites = list_invites(
                session,
                demographic_no=demographic_no,
                limit=limit,
                offset=offset,
                clinic_id=settings.clinic_id,
            )
            record_audit_event(
                session,
                event_type=AUDIT_EVENT_INVITE_LIST,
                outcome=AUDIT_OUTCOME_SUCCESS,
                actor_type=AUDIT_ACTOR_TYPE_STAFF,
                actor=actor,
                clinic_id=settings.clinic_id,
                demographic_no=demographic_no,
            )
            return [invite_response_payload(invite) for invite in invites]

        @app.post(
            "/dev/admin/invites/{invite_id}/resend",
            response_model=InviteTokenResponse,
            responses=DEV_ADMIN_CONFLICT_RESPONSES,
        )
        def dev_resend_invite(
            invite_id: Annotated[int, PathParam(gt=0, le=MAX_DATABASE_ID)],
            actor: Annotated[str, Depends(get_dev_admin_actor)],
            session: Annotated[
                Session,
                function_scoped_database_dependency(get_app_database_session),
            ],
        ) -> dict[str, object]:
            try:
                invite, invite_token = resend_invite(
                    session,
                    invite_id,
                    actor,
                    clinic_id=settings.clinic_id,
                )
            except InviteNotFoundError as exc:
                raise HTTPException(status_code=404, detail="invite not found") from exc
            except RevokedInviteError as exc:
                raise HTTPException(status_code=409, detail="invite has been revoked") from exc
            except AcceptedInviteError as exc:
                raise HTTPException(
                    status_code=409,
                    detail="invite has already been accepted",
                ) from exc
            except SupersededInviteError as exc:
                raise HTTPException(status_code=409, detail="invite was superseded") from exc
            return invite_response_payload(invite, invite_token)

        @app.post(
            "/dev/admin/invites/{invite_id}/revoke",
            response_model=InviteResponse,
            responses=DEV_ADMIN_CONFLICT_RESPONSES,
        )
        def dev_revoke_invite(
            invite_id: Annotated[int, PathParam(gt=0, le=MAX_DATABASE_ID)],
            actor: Annotated[str, Depends(get_dev_admin_actor)],
            session: Annotated[
                Session,
                function_scoped_database_dependency(get_app_database_session),
            ],
        ) -> dict[str, object]:
            try:
                invite = revoke_invite(
                    session,
                    invite_id,
                    actor,
                    clinic_id=settings.clinic_id,
                )
            except InviteNotFoundError as exc:
                raise HTTPException(status_code=404, detail="invite not found") from exc
            except AcceptedInviteError as exc:
                raise HTTPException(
                    status_code=409,
                    detail="invite has already been accepted",
                ) from exc
            except SupersededInviteError as exc:
                raise HTTPException(status_code=409, detail="invite was superseded") from exc
            return invite_response_payload(invite)

        @app.post(
            "/dev/admin/accounts/{account_id}/unlock",
            response_model=AccountAdminResponse,
            responses=DEV_ADMIN_COMMON_RESPONSES,
        )
        def dev_unlock_account(
            account_id: Annotated[int, PathParam(gt=0, le=MAX_DATABASE_ID)],
            actor: Annotated[str, Depends(get_dev_admin_actor)],
            session: Annotated[
                Session,
                function_scoped_database_dependency(get_app_database_session),
            ],
        ) -> dict[str, object]:
            try:
                account = unlock_patient_account(
                    session,
                    account_id,
                    actor,
                    clinic_id=settings.clinic_id,
                )
            except AccountNotFoundError as exc:
                raise HTTPException(status_code=404, detail="account not found") from exc
            return account_admin_response_payload(account)
