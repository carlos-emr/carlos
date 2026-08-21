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

"""Invite activation: the public route that turns an invite plus identity proof into an account."""

from typing import Annotated

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool
from starlette.responses import Response

from carlos_patient_portal.accounts import (
    ActivationDeliveryUnavailableError,
    ActivationError,
    ActivationThrottledError,
    UsernameUnavailableError,
    activate_patient_account,
    record_invalid_activation_request,
)
from carlos_patient_portal.audit import hash_sensitive_reference
from carlos_patient_portal.i18n import portal_text
from carlos_patient_portal.identity import IdentityProof
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
    request_locale,
)


def localized_activation_text(request: Request) -> dict[str, str]:
    return portal_text(request_locale(request))


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
        client_reference_hash = hash_sensitive_reference(
            audit_hash_secret,
            "activation_client",
            get_request_client_reference(request, settings),
        )

        def throttled_response(exc: ActivationThrottledError) -> Response:
            if is_browser_form:
                response = render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name=ACTIVATION_TEMPLATE,
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    error_message=localized_activation_text(request)[
                        "activation_rate_limited"
                    ],
                    sms_mfa_available=runtime.sms_sender is not None,
                )
                response.headers["Retry-After"] = str(exc.retry_after_seconds)
                return response
            return JSONResponse(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                content={"detail": "too many activation attempts; try again later"},
                headers={"Retry-After": str(exc.retry_after_seconds)},
            )

        async def charge_invalid_payload(invite_code: str | None) -> Response | None:
            try:
                await run_in_threadpool(
                    record_invalid_activation_request,
                    session,
                    invite_code=invite_code,
                    client_reference_hash=client_reference_hash,
                    rate_limit=activation_rate_limit,
                )
            except ActivationThrottledError as exc:
                return throttled_response(exc)
            return None

        try:
            payload = await get_activation_request_from_request(request, csrf_secret)
        except BrowserFormValidationError as exc:
            if response := await charge_invalid_payload(
                exc.safe_form_values.get("invite_code")
            ):
                return response
            if not is_browser_form:
                raise
            return render_public_auth_template(
                request,
                settings=settings,
                csrf_secret=csrf_secret,
                template_name=ACTIVATION_TEMPLATE,
                status_code=status.HTTP_400_BAD_REQUEST,
                error_message=localized_activation_text(request)["password_mismatch"],
                sms_mfa_available=runtime.sms_sender is not None,
            )
        except RequestValidationError as exc:
            validation_body = exc.body if isinstance(exc.body, dict) else {}
            invite_code = validation_body.get("invite_code")
            if response := await charge_invalid_payload(
                invite_code if isinstance(invite_code, str) else None
            ):
                return response
            if is_browser_form:
                return render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name=ACTIVATION_TEMPLATE,
                    status_code=status.HTTP_400_BAD_REQUEST,
                    error_message=localized_activation_text(request)["activation_error"],
                    sms_mfa_available=runtime.sms_sender is not None,
                )
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={"detail": "activation details could not be verified"},
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
                sms_delivery_available=runtime.sms_sender is not None,
                proof_secret=identity_proof_secret,
                client_reference_hash=client_reference_hash,
                rate_limit=activation_rate_limit,
                expected_clinic_id=settings.clinic_id,
            )
        except ActivationDeliveryUnavailableError:
            if is_browser_form:
                return render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name=ACTIVATION_TEMPLATE,
                    status_code=status.HTTP_400_BAD_REQUEST,
                    error_message=localized_activation_text(request)["mfa_delivery_unavailable"],
                    sms_mfa_available=False,
                )
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={"detail": MFA_DELIVERY_UNAVAILABLE_DETAIL},
            )
        except UsernameUnavailableError:
            if is_browser_form:
                return render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name=ACTIVATION_TEMPLATE,
                    status_code=status.HTTP_409_CONFLICT,
                    error_message=localized_activation_text(request)["username_unavailable"],
                    form_values={
                        "email": payload.email,
                        "date_of_birth": payload.date_of_birth.isoformat(),
                    },
                    sms_mfa_available=runtime.sms_sender is not None,
                )
            return JSONResponse(status_code=409, content={"detail": "username unavailable"})
        except ActivationThrottledError as exc:
            return throttled_response(exc)
        except ActivationError:
            if is_browser_form:
                return render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name=ACTIVATION_TEMPLATE,
                    status_code=status.HTTP_400_BAD_REQUEST,
                    error_message=localized_activation_text(request)["activation_error"],
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
                result_heading=localized_activation_text(request)["activation_success_heading"],
                result_message=localized_activation_text(request)["activation_success"],
            )
        return {"status": "activated", "username": account.username}
