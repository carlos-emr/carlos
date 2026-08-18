import logging
from collections.abc import Awaitable, Callable, Generator
from dataclasses import dataclass
from datetime import datetime
from typing import Annotated, Protocol

from fastapi import Depends, FastAPI, Header, HTTPException, Path, Query, Request, status
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session, sessionmaker
from starlette.concurrency import run_in_threadpool
from starlette.responses import Response

from carlos_patient_portal.account_settings import (
    ContactReviewConflictError,
    ContactReviewNotFoundError,
    count_pending_contact_reviews,
    list_pending_contact_reviews,
    review_contact_update,
)
from carlos_patient_portal.accounts import find_account_id_for_patient
from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.auth import (
    AccountNotFoundError,
    set_patient_account_access,
    unlock_patient_account,
)
from carlos_patient_portal.config import Settings
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.invites import (
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
    AUDIT_EVENT_STAFF_ACTION,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    UNLOCK_SECRET_STATUS_PENDING,
    UNLOCK_SECRET_STATUS_REVOKED,
    PatientPortalAccount,
    PatientPortalInvite,
    PatientPortalUnlockSecret,
)
from carlos_patient_portal.schemas import InviteCreateRequest
from carlos_patient_portal.staff_identity import (
    CarlosServiceAuthenticationError,
    CarlosStaffPermissionError,
    StaffPrincipal,
    authenticate_carlos_staff,
)
from carlos_patient_portal.unlock_secrets import (
    UnlockSecretDecryptionError,
    UnlockSecretNotFoundError,
    UnlockSecretRevokedError,
    create_unlock_secret,
    get_unlock_secret_by_source_reference,
    publish_unlock_secret,
    read_unlock_secret,
    revoke_unlock_secret,
)

logger = logging.getLogger(__name__)

PERMISSION_INVITE_MANAGE = "portal.invite.manage"
PERMISSION_ACCOUNT_UNLOCK = "portal.account.unlock"
PERMISSION_ACCOUNT_MANAGE = "portal.account.manage"
PERMISSION_SECRET_MANAGE = "portal.secret.manage"
PERMISSION_CONTACT_REVIEW = "portal.contact.review"
# Part of the CARLOS/Java boundary: one deliberately generic 404 shared by every account lookup so
# the contract cannot drift branch by branch.
INTERNAL_ACCOUNT_NOT_FOUND_DETAIL = "portal account not found"


class InternalErrorResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    detail: str


COMMON_INTERNAL_RESPONSES = {
    status.HTTP_403_FORBIDDEN: {
        "description": "Staff permission is required.",
        "model": InternalErrorResponse,
    },
    status.HTTP_404_NOT_FOUND: {
        "description": "Resource or service endpoint was not found.",
        "model": InternalErrorResponse,
    },
}
INTERNAL_CONFLICT_RESPONSES = {
    **COMMON_INTERNAL_RESPONSES,
    status.HTTP_409_CONFLICT: {"description": "The requested state transition conflicts."},
}
INTERNAL_CREATE_INVITE_RESPONSES = {
    **INTERNAL_CONFLICT_RESPONSES,
    status.HTTP_400_BAD_REQUEST: {"description": "The demographic scope does not match."},
}


class InternalRuntime(Protocol):
    settings: Settings
    session_factory: sessionmaker[Session]
    identity_proof_secret: str
    unlock_secret_encryption_secret: str
    unlock_secret_encryption_keys: dict[str, str] | None
    unlock_secret_active_key_id: str
    operational_metrics: "InternalOperationalMetrics"


class InternalOperationalMetrics(Protocol):
    def record_failure(self, failure_type: str) -> None:
        raise NotImplementedError


class InternalUnlockSecretRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    source_reference: str = Field(min_length=1, max_length=128)
    label: str | None = Field(default=None, max_length=128)
    secret_type: str = Field(default="email", pattern="^email$")


class InternalUnlockSecretRevokeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    reason: str | None = Field(default=None, max_length=64)


class InternalContactReviewDecision(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    approve: bool
    revision: str = Field(min_length=1, max_length=64)


class InternalAccountAccessRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    enabled: bool
    reason: str = Field(default="staff_action", min_length=1, max_length=64)


class InternalInviteResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: int
    clinic_id: str
    demographic_no: int
    status: str
    created_by_id: str | None
    created_by: str
    issued_count: int
    last_issued_at: datetime
    last_issued_by: str
    expires_at: datetime
    accepted_account_id: int | None
    supersedes_invite_id: int | None


class InternalInviteTokenResponse(InternalInviteResponse):
    invite_token: str


class InternalAccountUnlockResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: int
    clinic_id: str
    demographic_no: int
    locked_at: datetime | None
    force_password_reset: bool


class InternalAccountStatusResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: int
    clinic_id: str
    demographic_no: int
    status: str
    locked: bool
    force_password_reset: bool
    disabled_at: datetime | None
    disabled_reason: str | None


class InternalAccountAccessResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: int
    status: str
    force_password_reset: bool


class InternalUnlockSecretCreateResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: int
    created: bool
    secret: str
    source_reference: str | None
    status: str


class InternalUnlockSecretStatusResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: int
    status: str


class InternalContactReviewResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: int
    clinic_id: str
    demographic_no: int
    email_before: str
    email_after: str
    phone_number_before: str | None
    phone_number_after: str | None
    requested_at: datetime
    revision: str


class InternalContactReviewListResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    items: list[InternalContactReviewResponse]
    limit: int
    offset: int
    total: int
    next_offset: int | None


class InternalContactReviewDecisionResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: int
    status: str
    decision: str | None


def require_permission(principal: StaffPrincipal, permission: str) -> None:
    try:
        principal.require(permission)
    except CarlosStaffPermissionError as exc:
        # Sonar cannot associate dependency-level errors with every route's OpenAPI responses.
        raise HTTPException(status_code=403, detail="permission denied") from exc  # NOSONAR


def invite_payload(
    invite: PatientPortalInvite,
    *,
    invite_token: str | None = None,
) -> dict[str, object]:
    payload: dict[str, object] = {
        "id": invite.id,
        "clinic_id": invite.clinic_id,
        "demographic_no": invite.demographic_no,
        "status": invite.status,
        "created_by_id": invite.created_by_id,
        "created_by": invite.created_by,
        "issued_count": invite.sent_count,
        "last_issued_at": invite.last_sent_at,
        "last_issued_by": invite.last_sent_by,
        "expires_at": invite.expires_at,
        "accepted_account_id": invite.accepted_account_id,
        "supersedes_invite_id": invite.supersedes_invite_id,
    }
    if invite_token is not None:
        payload["invite_token"] = invite_token
    return payload


@dataclass(frozen=True)
class InternalRouteDependencies:
    """Shared FastAPI dependencies for the CARLOS-facing routes.

    Passed to the per-domain registrars below so each declares only the routes it owns. Holding
    these as fields rather than closing over them keeps a registrar's requirements visible in
    its signature, and stops one domain reaching for another's state.
    """

    staff_principal_requiring: Callable[[str], Callable[..., StaffPrincipal]]
    session_dependency: object
    disclose_unlock_secret: Callable[[Session, PatientPortalUnlockSecret, StaffPrincipal], str]


def register_internal_failure_audit(app: FastAPI, runtime: InternalRuntime) -> None:
    """Record every failed /internal/carlos/** request as a staff-action audit event."""

    def record_failed_internal_action(request: Request, status_code: int) -> None:
        principal: StaffPrincipal | None = None
        try:
            principal = authenticate_carlos_staff(
                runtime.settings,
                authorization=request.headers.get("Authorization"),
                provider_id=request.headers.get("X-CARLOS-Provider-ID"),
                provider_name=request.headers.get("X-CARLOS-Provider-Name"),
                clinic_id=request.headers.get("X-CARLOS-Clinic-ID"),
                permissions=request.headers.get("X-CARLOS-Permissions"),
            )
        except CarlosServiceAuthenticationError:
            # Do not trust provider or clinic headers unless service authentication succeeds.
            principal = None
        reason = (
            "authentication_failed"
            if principal is None
            else {
                403: "authorization_failed",
                404: "not_found",
                409: "conflict",
                422: "validation_failed",
                429: "throttled",
            }.get(status_code, "internal_failure")
        )
        try:
            with runtime.session_factory() as audit_session:
                with audit_session.begin():
                    record_audit_event(
                        audit_session,
                        event_type=AUDIT_EVENT_STAFF_ACTION,
                        outcome=AUDIT_OUTCOME_FAILURE,
                        actor_type=AUDIT_ACTOR_TYPE_STAFF,
                        actor=principal.display_name if principal is not None else "carlos-service",
                        actor_id=principal.provider_id if principal is not None else None,
                        clinic_id=principal.clinic_id if principal is not None else None,
                        resource_type="internal_api",
                        reason=reason,
                    )
        except SQLAlchemyError as exc:
            runtime.operational_metrics.record_failure("internal_audit")
            # This middleware is the only record of failed staff actions; if it cannot write, the
            # security log stops while the API keeps serving, so it must be loud in the logs.
            logger.error(
                "Internal staff-action audit write failed: %s reason=%s status=%s",
                type(exc).__name__,
                reason,
                status_code,
            )

    @app.middleware("http")
    async def audit_failed_internal_action(
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        try:
            response = await call_next(request)
        except Exception:
            if request.url.path.startswith("/internal/carlos/"):
                await run_in_threadpool(record_failed_internal_action, request, 500)
            raise
        if request.url.path.startswith("/internal/carlos/") and response.status_code >= 400:
            await run_in_threadpool(
                record_failed_internal_action,
                request,
                response.status_code,
            )
        return response

def build_internal_dependencies(runtime: InternalRuntime) -> InternalRouteDependencies:
    """Construct the authentication, transaction, and disclosure dependencies once."""
    def get_database_session() -> Generator[Session, None, None]:
        with runtime.session_factory() as session:
            with session.begin():
                yield session

    def get_staff_principal(
        authorization: Annotated[str | None, Header()] = None,
        provider_id: Annotated[str | None, Header(alias="X-CARLOS-Provider-ID")] = None,
        provider_name: Annotated[str | None, Header(alias="X-CARLOS-Provider-Name")] = None,
        clinic_id: Annotated[str | None, Header(alias="X-CARLOS-Clinic-ID")] = None,
        permissions: Annotated[str | None, Header(alias="X-CARLOS-Permissions")] = None,
    ) -> StaffPrincipal:
        try:
            return authenticate_carlos_staff(
                runtime.settings,
                authorization=authorization,
                provider_id=provider_id,
                provider_name=provider_name,
                clinic_id=clinic_id,
                permissions=permissions,
            )
        except CarlosServiceAuthenticationError as exc:
            raise HTTPException(status_code=404, detail="not found") from exc

    def staff_principal_requiring(permission: str) -> Callable[..., StaffPrincipal]:
        """Authorize in the dependency phase, not in the handler body.

        FastAPI resolves dependencies before it validates the request model, so checking the
        permission here makes 403 precede 422 — matching CARLOS's auth-first rule, where
        `hasPrivilege` is the first statement in every 2Action, and keeping a caller who lacks
        permission from learning the endpoint's schema through validation errors.
        """

        def dependency(
            principal: Annotated[StaffPrincipal, Depends(get_staff_principal)],
        ) -> StaffPrincipal:
            require_permission(principal, permission)
            return principal

        return dependency

    # FastAPI supports function-scoped teardown; Sonar's FastAPI stub does not.
    session_dependency = Depends(get_database_session, scope="function")  # NOSONAR

    def disclose_unlock_secret(
        session: Session,
        unlock_secret: PatientPortalUnlockSecret,
        principal: StaffPrincipal,
    ) -> str:
        try:
            return read_unlock_secret(
                session,
                unlock_secret.id,
                clinic_id=principal.clinic_id,
                encryption_keys=(
                    runtime.unlock_secret_encryption_keys
                    or {"primary": runtime.unlock_secret_encryption_secret}
                ),
                actor_type="staff",
                actor=principal.display_name,
                actor_id=principal.provider_id,
                demographic_no=unlock_secret.demographic_no,
                # CARLOS retries read back the record it just created, before publishing it.
                allow_pending=True,
            )
        except UnlockSecretDecryptionError as exc:
            # read_unlock_secret already wrote the `decryption_failed` audit row into this
            # request's transaction, and the 503 raised below would otherwise unwind and discard
            # it — losing the only durable record that a stored secret is unreadable, which is the
            # exact condition the runbook pages on. Committing here ends the transaction opened by
            # `get_database_session`; unwinding through that already-committed context manager is
            # expected and is covered by test_internal_api.py's retry-decryption-failure test.
            session.commit()
            runtime.operational_metrics.record_failure("unlock_secret_decryption")
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="unlock secret is temporarily unavailable",
            ) from exc

    return InternalRouteDependencies(
        staff_principal_requiring=staff_principal_requiring,
        session_dependency=session_dependency,
        disclose_unlock_secret=disclose_unlock_secret,
    )


def register_internal_invite_routes(
    app: FastAPI,
    runtime: InternalRuntime,
    deps: InternalRouteDependencies,
) -> None:
    """Invite lifecycle: create, list, resend, and revoke."""
    @app.post(
        "/internal/carlos/patients/{demographic_no}/invites",
        status_code=201,
        response_model=InternalInviteTokenResponse,
        responses=INTERNAL_CREATE_INVITE_RESPONSES,
    )
    def internal_create_invite(
        demographic_no: Annotated[int, Path(gt=0)],
        payload: InviteCreateRequest,
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_INVITE_MANAGE))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> dict[str, object]:
        if payload.demographic_no != demographic_no:
            raise HTTPException(status_code=400, detail="demographic scope mismatch")
        try:
            invite, invite_token = create_invite(
                session,
                demographic_no,
                principal.display_name,
                actor_id=principal.provider_id,
                identity_proof=IdentityProof(
                    email=payload.email,
                    date_of_birth=payload.date_of_birth,
                    health_card_number=payload.health_card_number,
                ),
                proof_secret=runtime.identity_proof_secret,
                clinic_id=principal.clinic_id,
            )
        except AccountAlreadyExistsError as exc:
            raise HTTPException(status_code=409, detail="portal account already exists") from exc
        except PendingInviteExistsError as exc:
            raise HTTPException(status_code=409, detail="pending invite already exists") from exc
        return invite_payload(invite, invite_token=invite_token)

    @app.get(
        "/internal/carlos/patients/{demographic_no}/invites",
        response_model=list[InternalInviteResponse],
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_list_invites(
        demographic_no: Annotated[int, Path(gt=0)],
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_INVITE_MANAGE))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> list[dict[str, object]]:
        invites = list_invites(
            session,
            demographic_no=demographic_no,
            clinic_id=principal.clinic_id,
            limit=100,
        )
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_INVITE_LIST,
            outcome=AUDIT_OUTCOME_SUCCESS,
            actor_type=AUDIT_ACTOR_TYPE_STAFF,
            actor=principal.display_name,
            actor_id=principal.provider_id,
            clinic_id=principal.clinic_id,
            demographic_no=demographic_no,
        )
        return [
            invite_payload(invite)
            for invite in invites
        ]

    @app.post(
        "/internal/carlos/invites/{invite_id}/resend",
        response_model=InternalInviteTokenResponse,
        responses=INTERNAL_CONFLICT_RESPONSES,
    )
    def internal_resend_invite(
        invite_id: Annotated[int, Path(gt=0)],
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_INVITE_MANAGE))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> dict[str, object]:
        try:
            invite, invite_token = resend_invite(
                session,
                invite_id,
                principal.display_name,
                actor_id=principal.provider_id,
                clinic_id=principal.clinic_id,
            )
        except InviteNotFoundError as exc:
            raise HTTPException(status_code=404, detail="invite not found") from exc
        except (RevokedInviteError, AcceptedInviteError, SupersededInviteError) as exc:
            raise HTTPException(status_code=409, detail="invite cannot be resent") from exc
        return invite_payload(invite, invite_token=invite_token)

    @app.post(
        "/internal/carlos/invites/{invite_id}/revoke",
        response_model=InternalInviteResponse,
        responses=INTERNAL_CONFLICT_RESPONSES,
    )
    def internal_revoke_invite(
        invite_id: Annotated[int, Path(gt=0)],
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_INVITE_MANAGE))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> dict[str, object]:
        try:
            invite = revoke_invite(
                session,
                invite_id,
                principal.display_name,
                actor_id=principal.provider_id,
                clinic_id=principal.clinic_id,
            )
        except InviteNotFoundError as exc:
            raise HTTPException(status_code=404, detail="invite not found") from exc
        except AcceptedInviteError as exc:
            raise HTTPException(
                status_code=409,
                detail="accepted invite cannot be revoked",
            ) from exc
        except SupersededInviteError as exc:
            raise HTTPException(
                status_code=409,
                detail="superseded invite cannot be revoked",
            ) from exc
        return invite_payload(invite)


def register_internal_account_routes(
    app: FastAPI,
    runtime: InternalRuntime,
    deps: InternalRouteDependencies,
) -> None:
    """Portal account state: unlock, status, and access."""
    @app.post(
        "/internal/carlos/patients/{demographic_no}/unlock",
        response_model=InternalAccountUnlockResponse,
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_unlock_account(
        demographic_no: Annotated[int, Path(gt=0)],
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_ACCOUNT_UNLOCK))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> dict[str, object]:
        account_id = find_account_id_for_patient(
            session,
            clinic_id=principal.clinic_id,
            demographic_no=demographic_no,
        )
        if account_id is None:
            raise HTTPException(status_code=404, detail=INTERNAL_ACCOUNT_NOT_FOUND_DETAIL)
        try:
            account = unlock_patient_account(
                session,
                account_id,
                principal.display_name,
                actor_id=principal.provider_id,
                clinic_id=principal.clinic_id,
            )
        except AccountNotFoundError as exc:
            raise HTTPException(status_code=404, detail=INTERNAL_ACCOUNT_NOT_FOUND_DETAIL) from exc
        return {
            "id": account.id,
            "clinic_id": account.clinic_id,
            "demographic_no": account.demographic_no,
            "locked_at": account.locked_at,
            "force_password_reset": account.force_password_reset,
        }

    @app.get(
        "/internal/carlos/patients/{demographic_no}/portal-account",
        response_model=InternalAccountStatusResponse,
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_get_account_status(
        demographic_no: Annotated[int, Path(gt=0)],
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_ACCOUNT_MANAGE))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> dict[str, object]:
        account = session.scalar(
            select(PatientPortalAccount).where(
                PatientPortalAccount.clinic_id == principal.clinic_id,
                PatientPortalAccount.demographic_no == demographic_no,
            )
        )
        if account is None:
            raise HTTPException(status_code=404, detail=INTERNAL_ACCOUNT_NOT_FOUND_DETAIL)
        return {
            "id": account.id,
            "clinic_id": account.clinic_id,
            "demographic_no": account.demographic_no,
            "status": account.status,
            "locked": account.locked_at is not None,
            "force_password_reset": account.force_password_reset,
            "disabled_at": account.disabled_at,
            "disabled_reason": account.disabled_reason,
        }

    @app.post(
        "/internal/carlos/patients/{demographic_no}/portal-account/access",
        response_model=InternalAccountAccessResponse,
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_set_account_access(
        demographic_no: Annotated[int, Path(gt=0)],
        payload: InternalAccountAccessRequest,
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_ACCOUNT_MANAGE))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> dict[str, object]:
        account_id = find_account_id_for_patient(
            session,
            clinic_id=principal.clinic_id,
            demographic_no=demographic_no,
        )
        if account_id is None:
            raise HTTPException(status_code=404, detail=INTERNAL_ACCOUNT_NOT_FOUND_DETAIL)
        try:
            account = set_patient_account_access(
                session,
                account_id,
                principal.display_name,
                enabled=payload.enabled,
                clinic_id=principal.clinic_id,
                actor_id=principal.provider_id,
                reason=payload.reason,
            )
        except AccountNotFoundError as exc:
            raise HTTPException(status_code=404, detail=INTERNAL_ACCOUNT_NOT_FOUND_DETAIL) from exc
        except ValueError as exc:
            raise HTTPException(status_code=422, detail="invalid account access request") from exc
        return {
            "id": account.id,
            "status": account.status,
            "force_password_reset": account.force_password_reset,
        }


def register_internal_unlock_secret_routes(
    app: FastAPI,
    runtime: InternalRuntime,
    deps: InternalRouteDependencies,
) -> None:
    """Unlock secrets: create, publish, and revoke."""
    @app.post(
        "/internal/carlos/patients/{demographic_no}/unlock-secrets",
        status_code=status.HTTP_201_CREATED,
        response_model=InternalUnlockSecretCreateResponse,
        responses=INTERNAL_CONFLICT_RESPONSES,
    )
    def internal_create_unlock_secret(
        demographic_no: Annotated[int, Path(gt=0)],
        payload: InternalUnlockSecretRequest,
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_SECRET_MANAGE))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> dict[str, object]:
        existing_secret = get_unlock_secret_by_source_reference(
            session,
            clinic_id=principal.clinic_id,
            demographic_no=demographic_no,
            secret_type=payload.secret_type,
            source_reference=payload.source_reference,
            for_update=True,
        )
        if existing_secret is not None:
            if existing_secret.status == UNLOCK_SECRET_STATUS_REVOKED:
                raise HTTPException(status_code=409, detail="source reference was revoked")
            plaintext = deps.disclose_unlock_secret(session, existing_secret, principal)
            return {
                "id": existing_secret.id,
                "created": False,
                "secret": plaintext,
                "source_reference": existing_secret.source_reference,
                "status": existing_secret.status,
            }
        try:
            with session.begin_nested():
                created = create_unlock_secret(
                    session,
                    clinic_id=principal.clinic_id,
                    demographic_no=demographic_no,
                    created_by=principal.display_name,
                    created_by_id=principal.provider_id,
                    encryption_secret=runtime.unlock_secret_encryption_secret,
                    encryption_key_id=runtime.unlock_secret_active_key_id,
                    secret_type=payload.secret_type,
                    label=payload.label,
                    source_reference=payload.source_reference,
                    initial_status=UNLOCK_SECRET_STATUS_PENDING,
                )
        except IntegrityError:
            existing_secret = session.scalar(
                select(PatientPortalUnlockSecret)
                .where(
                    PatientPortalUnlockSecret.clinic_id == principal.clinic_id,
                    PatientPortalUnlockSecret.secret_type == payload.secret_type,
                    PatientPortalUnlockSecret.source_reference == payload.source_reference,
                )
                .with_for_update()
            )
            if existing_secret is None:
                raise
            if existing_secret.demographic_no != demographic_no:
                raise HTTPException(
                    status_code=409,
                    detail="source reference belongs to another patient",
                ) from None
            if existing_secret.status == UNLOCK_SECRET_STATUS_REVOKED:
                raise HTTPException(
                    status_code=409,
                    detail="source reference was revoked",
                ) from None
            plaintext = deps.disclose_unlock_secret(session, existing_secret, principal)
            return {
                "id": existing_secret.id,
                "created": False,
                "secret": plaintext,
                "source_reference": existing_secret.source_reference,
                "status": existing_secret.status,
            }
        return {
            "id": created.unlock_secret.id,
            "created": True,
            "secret": created.secret,
            "source_reference": created.unlock_secret.source_reference,
            "status": created.unlock_secret.status,
        }

    @app.post(
        "/internal/carlos/unlock-secrets/{unlock_secret_id}/publish",
        response_model=InternalUnlockSecretStatusResponse,
        responses=INTERNAL_CONFLICT_RESPONSES,
    )
    def internal_publish_unlock_secret(
        unlock_secret_id: Annotated[int, Path(gt=0)],
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_SECRET_MANAGE))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> dict[str, object]:
        demographic_no = session.scalar(
            select(PatientPortalUnlockSecret.demographic_no).where(
                PatientPortalUnlockSecret.id == unlock_secret_id,
                PatientPortalUnlockSecret.clinic_id == principal.clinic_id,
            )
        )
        if demographic_no is None:
            raise HTTPException(status_code=404, detail="unlock secret not found")
        try:
            unlock_secret = publish_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id=principal.clinic_id,
                demographic_no=demographic_no,
                published_by=principal.display_name,
                published_by_id=principal.provider_id,
            )
        except (UnlockSecretNotFoundError, UnlockSecretRevokedError) as exc:
            raise HTTPException(
                status_code=409,
                detail="unlock secret cannot be published",
            ) from exc
        return {"id": unlock_secret.id, "status": unlock_secret.status}

    @app.post(
        "/internal/carlos/unlock-secrets/{unlock_secret_id}/revoke",
        response_model=InternalUnlockSecretStatusResponse,
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_revoke_unlock_secret(
        unlock_secret_id: Annotated[int, Path(gt=0)],
        payload: InternalUnlockSecretRevokeRequest,
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_SECRET_MANAGE))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> dict[str, object]:
        # Resolved and checked here, matching the publish handler above. Passing a None
        # demographic through to revoke_unlock_secret would still produce a 404, but only via a
        # scope-validation ValueError raised three frames down — an implementation detail, not a
        # decision this route made.
        demographic_no = session.scalar(
            select(PatientPortalUnlockSecret.demographic_no).where(
                PatientPortalUnlockSecret.id == unlock_secret_id,
                PatientPortalUnlockSecret.clinic_id == principal.clinic_id,
            )
        )
        if demographic_no is None:
            raise HTTPException(status_code=404, detail="unlock secret not found")
        try:
            unlock_secret = revoke_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id=principal.clinic_id,
                revoked_by=principal.display_name,
                revoked_by_id=principal.provider_id,
                demographic_no=demographic_no,
                reason=payload.reason,
            )
        except (UnlockSecretNotFoundError, ValueError) as exc:
            raise HTTPException(status_code=404, detail="unlock secret not found") from exc
        return {"id": unlock_secret.id, "status": unlock_secret.status}


def register_internal_contact_review_routes(
    app: FastAPI,
    runtime: InternalRuntime,
    deps: InternalRouteDependencies,
) -> None:
    """CARLOS-chart contact reviews: list and decide."""
    @app.get(
        "/internal/carlos/contact-reviews",
        response_model=InternalContactReviewListResponse,
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_list_contact_reviews(
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_CONTACT_REVIEW))
        ],
        session: Annotated[Session, deps.session_dependency],
        limit: Annotated[int, Query(ge=1, le=100)] = 50,
        offset: Annotated[int, Query(ge=0, le=100_000)] = 0,
    ) -> dict[str, object]:
        reviews = list_pending_contact_reviews(
            session,
            clinic_id=principal.clinic_id,
            limit=limit,
            offset=offset,
        )
        total = count_pending_contact_reviews(session, clinic_id=principal.clinic_id)
        return {
            "items": [
                {
                    "id": review.id,
                    "clinic_id": review.clinic_id,
                    "demographic_no": review.demographic_no,
                    "email_before": review.email_before,
                    "email_after": review.email_after,
                    "phone_number_before": review.phone_number_before,
                    "phone_number_after": review.phone_number_after,
                    "requested_at": review.requested_at,
                    "revision": review.revision,
                }
                for review in reviews
            ],
            "limit": limit,
            "offset": offset,
            "total": total,
            "next_offset": offset + limit if offset + len(reviews) < total else None,
        }

    @app.post(
        "/internal/carlos/contact-reviews/{review_request_id}/decision",
        response_model=InternalContactReviewDecisionResponse,
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_review_contact_update(
        review_request_id: Annotated[int, Path(gt=0)],
        payload: InternalContactReviewDecision,
        principal: Annotated[
            StaffPrincipal, Depends(deps.staff_principal_requiring(PERMISSION_CONTACT_REVIEW))
        ],
        session: Annotated[Session, deps.session_dependency],
    ) -> dict[str, object]:
        try:
            review = review_contact_update(
                session,
                review_request_id,
                clinic_id=principal.clinic_id,
                reviewer=principal.display_name,
                reviewer_id=principal.provider_id,
                approve=payload.approve,
                expected_revision=payload.revision,
            )
        except ContactReviewNotFoundError as exc:
            raise HTTPException(status_code=404, detail="contact review not found") from exc
        except ContactReviewConflictError as exc:
            raise HTTPException(status_code=409, detail="contact review revision conflict") from exc
        return {
            "id": review.id,
            "status": review.status,
            "decision": review.review_decision,
        }


def register_carlos_internal_routes(app: FastAPI, runtime: InternalRuntime) -> None:
    """Register the CARLOS-facing internal API, one registrar per permission domain."""
    if not runtime.settings.is_internal_api_enabled:
        return
    register_internal_failure_audit(app, runtime)
    deps = build_internal_dependencies(runtime)
    register_internal_invite_routes(app, runtime, deps)
    register_internal_account_routes(app, runtime, deps)
    register_internal_unlock_secret_routes(app, runtime, deps)
    register_internal_contact_review_routes(app, runtime, deps)
