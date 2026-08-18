"""Invite activation: the public route that turns an invite plus identity proof into an account."""

from typing import Annotated

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool
from starlette.responses import Response

from carlos_patient_portal.accounts import (
    ActivationError,
    ActivationThrottledError,
    UsernameUnavailableError,
    activate_patient_account,
)
from carlos_patient_portal.audit import hash_sensitive_reference
from carlos_patient_portal.i18n import DEFAULT_LOCALE, portal_text
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.models import MFA_DELIVERY_METHOD_SMS
from carlos_patient_portal.runtime import (
    PortalRuntime,
    RouteDependencies,
    function_scoped_database_dependency,
)
from carlos_patient_portal.schemas import ActivationResponse
from carlos_patient_portal.web_support import (
    ACTIVATION_TEMPLATE,
    MFA_DELIVERY_UNAVAILABLE_DETAIL,
    BrowserFormValidationError,
    get_activation_request_from_request,
    get_request_client_reference,
    is_urlencoded_form_request,
    render_public_auth_template,
)


def register_activation_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    identity_proof_secret = runtime.identity_proof_secret
    audit_hash_secret = runtime.audit_hash_secret
    activation_rate_limit = runtime.activation_rate_limit
    csrf_secret = runtime.token_keys.csrf
    text = portal_text(DEFAULT_LOCALE)

    @app.get("/auth/activate", name="activation_page")
    def activation_page(request: Request) -> Response:
        return render_public_auth_template(
            request,
            settings=settings,
            csrf_secret=csrf_secret,
            template_name=ACTIVATION_TEMPLATE,
            sms_mfa_available=runtime.sms_sender is not None,
        )

    @app.post(
        "/auth/activate",
        response_model=ActivationResponse,
        status_code=status.HTTP_201_CREATED,
    )
    async def activate_invite(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> dict[str, str] | Response:
        is_browser_form = is_urlencoded_form_request(request)
        try:
            payload = await get_activation_request_from_request(request, csrf_secret)
        except BrowserFormValidationError:
            if not is_browser_form:
                raise
            return render_public_auth_template(
                request,
                settings=settings,
                csrf_secret=csrf_secret,
                template_name=ACTIVATION_TEMPLATE,
                status_code=status.HTTP_400_BAD_REQUEST,
                error_message=text["password_mismatch"],
                sms_mfa_available=runtime.sms_sender is not None,
            )
        except RequestValidationError:
            if not is_browser_form:
                raise
            return render_public_auth_template(
                request,
                settings=settings,
                csrf_secret=csrf_secret,
                template_name=ACTIVATION_TEMPLATE,
                status_code=status.HTTP_400_BAD_REQUEST,
                error_message=text["activation_error"],
                sms_mfa_available=runtime.sms_sender is not None,
            )
        client_reference_hash = hash_sensitive_reference(
            audit_hash_secret,
            "activation_client",
            get_request_client_reference(request, settings),
        )
        if payload.mfa_delivery_method == MFA_DELIVERY_METHOD_SMS and runtime.sms_sender is None:
            if is_browser_form:
                return render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name=ACTIVATION_TEMPLATE,
                    status_code=status.HTTP_400_BAD_REQUEST,
                    error_message=text["mfa_delivery_unavailable"],
                    sms_mfa_available=False,
                )
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={"detail": MFA_DELIVERY_UNAVAILABLE_DETAIL},
            )
        try:
            account = await run_in_threadpool(
                activate_patient_account,
                session,
                invite_code=payload.invite_code,
                identity_proof=IdentityProof(
                    email=payload.email,
                    date_of_birth=payload.date_of_birth,
                    health_card_number=payload.health_card_number,
                ),
                username=payload.username,
                password=payload.password,
                preferred_mfa_method=payload.mfa_delivery_method,
                phone_number=payload.phone_number,
                proof_secret=identity_proof_secret,
                client_reference_hash=client_reference_hash,
                rate_limit=activation_rate_limit,
                expected_clinic_id=settings.clinic_id,
            )
        except UsernameUnavailableError:
            if is_browser_form:
                return render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name=ACTIVATION_TEMPLATE,
                    status_code=status.HTTP_409_CONFLICT,
                    error_message=text["username_unavailable"],
                    form_values={
                        "email": payload.email,
                        "date_of_birth": payload.date_of_birth.isoformat(),
                    },
                    sms_mfa_available=runtime.sms_sender is not None,
                )
            return JSONResponse(status_code=409, content={"detail": "username unavailable"})
        except ActivationThrottledError as exc:
            if is_browser_form:
                response = render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name=ACTIVATION_TEMPLATE,
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    error_message=text["activation_rate_limited"],
                    sms_mfa_available=runtime.sms_sender is not None,
                )
                response.headers["Retry-After"] = str(exc.retry_after_seconds)
                return response
            return JSONResponse(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                content={"detail": "too many activation attempts; try again later"},
                headers={"Retry-After": str(exc.retry_after_seconds)},
            )
        except ActivationError:
            if is_browser_form:
                return render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name=ACTIVATION_TEMPLATE,
                    status_code=status.HTTP_400_BAD_REQUEST,
                    error_message=text["activation_error"],
                    sms_mfa_available=runtime.sms_sender is not None,
                )
            return JSONResponse(
                status_code=400,
                content={"detail": "activation details could not be verified"},
            )
        if is_browser_form:
            return render_public_auth_template(
                request,
                settings=settings,
                csrf_secret=csrf_secret,
                template_name="auth_result.jinja",
                status_code=status.HTTP_201_CREATED,
                result_heading=text["activation_success_heading"],
                result_message=text["activation_success"],
            )
        return {"status": "activated", "username": account.username}
