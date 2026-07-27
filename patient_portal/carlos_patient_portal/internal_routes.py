from collections.abc import Generator
from typing import Annotated, Protocol

from fastapi import Depends, FastAPI, Header, HTTPException, Path, status
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from carlos_patient_portal.account_settings import (
    ContactReviewNotFoundError,
    list_pending_contact_reviews,
    review_contact_update,
)
from carlos_patient_portal.accounts import find_account_id_for_patient
from carlos_patient_portal.auth import AccountNotFoundError, unlock_patient_account
from carlos_patient_portal.config import Settings
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.invites import (
    AcceptedInviteError,
    AccountAlreadyExistsError,
    InviteNotFoundError,
    PendingInviteExistsError,
    RevokedInviteError,
    create_invite,
    list_invites,
    resend_invite,
    revoke_invite,
)
from carlos_patient_portal.models import (
    UNLOCK_SECRET_STATUS_REVOKED,
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
    MAX_UNLOCK_SECRET_PLAINTEXT_BYTES,
    UnlockSecretNotFoundError,
    create_unlock_secret,
    decrypt_unlock_secret_payload,
    get_unlock_secret_by_source_reference,
    revoke_unlock_secret,
)

PERMISSION_INVITE_MANAGE = "portal.invite.manage"
PERMISSION_ACCOUNT_UNLOCK = "portal.account.unlock"
PERMISSION_SECRET_MANAGE = "portal.secret.manage"
PERMISSION_CONTACT_REVIEW = "portal.contact.review"
COMMON_INTERNAL_RESPONSES = {
    status.HTTP_403_FORBIDDEN: {"description": "Staff permission is required."},
    status.HTTP_404_NOT_FOUND: {"description": "Resource or service endpoint was not found."},
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


class InternalUnlockSecretRequest(BaseModel):
    source_reference: str = Field(min_length=1, max_length=128)
    label: str | None = Field(default=None, max_length=128)
    secret_type: str = Field(default="email", pattern="^(email|pdf)$")
    secret: str | None = Field(
        default=None,
        min_length=1,
        max_length=MAX_UNLOCK_SECRET_PLAINTEXT_BYTES,
        repr=False,
    )


class InternalUnlockSecretRevokeRequest(BaseModel):
    reason: str | None = Field(default=None, max_length=64)


class InternalContactReviewDecision(BaseModel):
    approve: bool


def require_permission(principal: StaffPrincipal, permission: str) -> None:
    try:
        principal.require(permission)
    except CarlosStaffPermissionError as exc:
        # Sonar cannot associate dependency-level errors with every route's OpenAPI responses.
        raise HTTPException(status_code=403, detail="permission denied") from exc  # NOSONAR


def invite_payload(invite, *, invite_token: str | None = None) -> dict[str, object]:
    payload: dict[str, object] = {
        "id": invite.id,
        "clinic_id": invite.clinic_id,
        "demographic_no": invite.demographic_no,
        "status": invite.status,
        "created_by_id": invite.created_by_id,
        "created_by": invite.created_by,
        "sent_count": invite.sent_count,
        "expires_at": invite.expires_at,
        "accepted_account_id": invite.accepted_account_id,
    }
    if invite_token is not None:
        payload["invite_token"] = invite_token
    return payload


def register_carlos_internal_routes(app: FastAPI, runtime: InternalRuntime) -> None:
    if not runtime.settings.is_internal_api_enabled:
        return

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

    # FastAPI supports function-scoped teardown; Sonar's FastAPI stub does not.
    session_dependency = Depends(get_database_session, scope="function")  # NOSONAR

    @app.post(
        "/internal/carlos/patients/{demographic_no}/invites",
        status_code=201,
        responses=INTERNAL_CREATE_INVITE_RESPONSES,
    )
    def internal_create_invite(
        demographic_no: Annotated[int, Path(gt=0)],
        payload: InviteCreateRequest,
        principal: Annotated[StaffPrincipal, Depends(get_staff_principal)],
        session: Annotated[Session, session_dependency],
    ) -> dict[str, object]:
        require_permission(principal, PERMISSION_INVITE_MANAGE)
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
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_list_invites(
        demographic_no: Annotated[int, Path(gt=0)],
        principal: Annotated[StaffPrincipal, Depends(get_staff_principal)],
        session: Annotated[Session, session_dependency],
    ) -> list[dict[str, object]]:
        require_permission(principal, PERMISSION_INVITE_MANAGE)
        return [
            invite_payload(invite)
            for invite in list_invites(
                session,
                demographic_no=demographic_no,
                clinic_id=principal.clinic_id,
                limit=100,
            )
        ]

    @app.post(
        "/internal/carlos/invites/{invite_id}/resend",
        responses=INTERNAL_CONFLICT_RESPONSES,
    )
    def internal_resend_invite(
        invite_id: Annotated[int, Path(gt=0)],
        principal: Annotated[StaffPrincipal, Depends(get_staff_principal)],
        session: Annotated[Session, session_dependency],
    ) -> dict[str, object]:
        require_permission(principal, PERMISSION_INVITE_MANAGE)
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
        except (RevokedInviteError, AcceptedInviteError) as exc:
            raise HTTPException(status_code=409, detail="invite cannot be resent") from exc
        return invite_payload(invite, invite_token=invite_token)

    @app.post(
        "/internal/carlos/invites/{invite_id}/revoke",
        responses=INTERNAL_CONFLICT_RESPONSES,
    )
    def internal_revoke_invite(
        invite_id: Annotated[int, Path(gt=0)],
        principal: Annotated[StaffPrincipal, Depends(get_staff_principal)],
        session: Annotated[Session, session_dependency],
    ) -> dict[str, object]:
        require_permission(principal, PERMISSION_INVITE_MANAGE)
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
        return invite_payload(invite)

    @app.post(
        "/internal/carlos/patients/{demographic_no}/unlock",
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_unlock_account(
        demographic_no: Annotated[int, Path(gt=0)],
        principal: Annotated[StaffPrincipal, Depends(get_staff_principal)],
        session: Annotated[Session, session_dependency],
    ) -> dict[str, object]:
        require_permission(principal, PERMISSION_ACCOUNT_UNLOCK)
        account_id = find_account_id_for_patient(
            session,
            clinic_id=principal.clinic_id,
            demographic_no=demographic_no,
        )
        if account_id is None:
            raise HTTPException(status_code=404, detail="portal account not found")
        try:
            account = unlock_patient_account(
                session,
                account_id,
                principal.display_name,
                actor_id=principal.provider_id,
                clinic_id=principal.clinic_id,
            )
        except AccountNotFoundError as exc:
            raise HTTPException(status_code=404, detail="portal account not found") from exc
        return {
            "id": account.id,
            "clinic_id": account.clinic_id,
            "demographic_no": account.demographic_no,
            "locked_at": account.locked_at,
            "force_password_reset": account.force_password_reset,
        }

    @app.post(
        "/internal/carlos/patients/{demographic_no}/unlock-secrets",
        status_code=status.HTTP_201_CREATED,
        responses=INTERNAL_CONFLICT_RESPONSES,
    )
    def internal_create_unlock_secret(
        demographic_no: Annotated[int, Path(gt=0)],
        payload: InternalUnlockSecretRequest,
        principal: Annotated[StaffPrincipal, Depends(get_staff_principal)],
        session: Annotated[Session, session_dependency],
    ) -> dict[str, object]:
        require_permission(principal, PERMISSION_SECRET_MANAGE)
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
            plaintext = decrypt_unlock_secret_payload(
                existing_secret,
                encryption_keys=(
                    runtime.unlock_secret_encryption_keys
                    or {"primary": runtime.unlock_secret_encryption_secret}
                ),
            )
            return {
                "id": existing_secret.id,
                "created": False,
                "secret": plaintext,
                "source_reference": existing_secret.source_reference,
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
                    secret=payload.secret,
                    label=payload.label,
                    source_reference=payload.source_reference,
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
            plaintext = decrypt_unlock_secret_payload(
                existing_secret,
                encryption_keys=(
                    runtime.unlock_secret_encryption_keys
                    or {"primary": runtime.unlock_secret_encryption_secret}
                ),
            )
            return {
                "id": existing_secret.id,
                "created": False,
                "secret": plaintext,
                "source_reference": existing_secret.source_reference,
            }
        return {
            "id": created.unlock_secret.id,
            "created": True,
            "secret": created.secret,
            "source_reference": created.unlock_secret.source_reference,
        }

    @app.post(
        "/internal/carlos/unlock-secrets/{unlock_secret_id}/revoke",
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_revoke_unlock_secret(
        unlock_secret_id: Annotated[int, Path(gt=0)],
        payload: InternalUnlockSecretRevokeRequest,
        principal: Annotated[StaffPrincipal, Depends(get_staff_principal)],
        session: Annotated[Session, session_dependency],
    ) -> dict[str, object]:
        require_permission(principal, PERMISSION_SECRET_MANAGE)
        try:
            unlock_secret = revoke_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id=principal.clinic_id,
                revoked_by=principal.display_name,
                revoked_by_id=principal.provider_id,
                demographic_no=session.scalar(
                    select(PatientPortalUnlockSecret.demographic_no).where(
                        PatientPortalUnlockSecret.id == unlock_secret_id,
                        PatientPortalUnlockSecret.clinic_id == principal.clinic_id,
                    )
                ),
                reason=payload.reason,
            )
        except (UnlockSecretNotFoundError, ValueError) as exc:
            raise HTTPException(status_code=404, detail="unlock secret not found") from exc
        return {"id": unlock_secret.id, "status": unlock_secret.status}

    @app.get(
        "/internal/carlos/contact-reviews",
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_list_contact_reviews(
        principal: Annotated[StaffPrincipal, Depends(get_staff_principal)],
        session: Annotated[Session, session_dependency],
    ) -> dict[str, object]:
        require_permission(principal, PERMISSION_CONTACT_REVIEW)
        reviews = list_pending_contact_reviews(
            session,
            clinic_id=principal.clinic_id,
        )
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
                }
                for review in reviews
            ]
        }

    @app.post(
        "/internal/carlos/contact-reviews/{review_request_id}/decision",
        responses=COMMON_INTERNAL_RESPONSES,
    )
    def internal_review_contact_update(
        review_request_id: Annotated[int, Path(gt=0)],
        payload: InternalContactReviewDecision,
        principal: Annotated[StaffPrincipal, Depends(get_staff_principal)],
        session: Annotated[Session, session_dependency],
    ) -> dict[str, object]:
        require_permission(principal, PERMISSION_CONTACT_REVIEW)
        try:
            review = review_contact_update(
                session,
                review_request_id,
                clinic_id=principal.clinic_id,
                reviewer=principal.display_name,
                reviewer_id=principal.provider_id,
                approve=payload.approve,
            )
        except ContactReviewNotFoundError as exc:
            raise HTTPException(status_code=404, detail="contact review not found") from exc
        return {
            "id": review.id,
            "status": review.status,
            "decision": review.review_decision,
        }
