"""Authentication routes: login, MFA, password reset, email-change confirmation, and logout.

Each route serves two clients from one handler: the server-rendered browser form, which is CSRF
protected and answered with a page, and a JSON bearer client, which is answered with a payload.
Error responses are deliberately uniform across both so a failure reason is not leaked by shape.
"""

import logging
from typing import Annotated

from fastapi import BackgroundTasks, Depends, FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, RedirectResponse
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool
from starlette.responses import Response

from carlos_patient_portal.account_settings import (
    EmailChangeTokenInvalidError,
    confirm_email_change,
)
from carlos_patient_portal.audit import hash_sensitive_reference
from carlos_patient_portal.auth import (
    AccountLockedError,
    AuthenticatedPortalSession,
    InvalidCredentialsError,
    InvalidMfaCodeError,
    MfaChallengeDelivery,
    MfaChallengeNotFoundError,
    MfaDeliveryUnavailableError,
    MfaRateLimitedError,
    PasswordResetRequiredError,
    PasswordResetTokenInvalidError,
    PortalSessionInvalidError,
    complete_password_reset,
    get_mfa_challenge_delivery_state,
    logout_patient_session,
    record_password_reset_delivery_outcome,
    request_password_reset,
    resend_mfa_challenge,
    start_login,
    verify_mfa_challenge,
)
from carlos_patient_portal.email_delivery import PortalEmailDeliveryError
from carlos_patient_portal.i18n import DEFAULT_LOCALE, portal_text
from carlos_patient_portal.models import AUDIT_OUTCOME_FAILURE, AUDIT_OUTCOME_SUCCESS
from carlos_patient_portal.notifications import (
    build_password_reset_url,
    deliver_password_reset,
    record_mfa_delivery_and_commit,
    send_contact_change_notice,
    send_mfa_challenge,
    send_password_reset_email,
)
from carlos_patient_portal.runtime import (
    PortalRuntime,
    RouteDependencies,
    function_scoped_database_dependency,
)
from carlos_patient_portal.schemas import (
    LoginResponse,
    LogoutResponse,
    MfaChallengeResponse,
    MfaResendRequest,
    MfaVerifyRequest,
    MfaVerifyResponse,
    PasswordResetCompleteResponse,
    PasswordResetRequestResponse,
    SessionResponse,
)
from carlos_patient_portal.sms_delivery import PortalSmsDeliveryError
from carlos_patient_portal.web_support import (
    ACCOUNT_LOCKED_DETAIL,
    AUTH_LOGOUT_RESPONSES,
    AUTHENTICATION_REQUIRED_DETAIL,
    CSRF_COOKIE_PATH,
    EMAIL_CHANGE_TEMPLATE,
    MFA_DELIVERY_UNAVAILABLE_DETAIL,
    MFA_VERIFICATION_FAILED_DETAIL,
    PASSWORD_RESET_COMPLETE_TEMPLATE,
    PORTAL_ROOT_PATH,
    BrowserFormValidationError,
    auth_error_response,
    clear_portal_session_cookie,
    create_csrf_token,
    first_form_value_or_empty,
    get_csrf_urlencoded_form_values,
    get_login_request_from_request,
    get_mfa_resend_request_from_request,
    get_mfa_verify_request_from_request,
    get_password_reset_complete_request_from_request,
    get_password_reset_request_from_request,
    get_request_client_reference,
    is_urlencoded_form_request,
    login_response_payload,
    mfa_challenge_response_payload,
    mfa_template_context,
    password_reset_request_response_payload,
    render_public_auth_template,
    set_csrf_cookie,
    set_portal_session_cookie,
    templates,
)

logger = logging.getLogger(__name__)


def register_auth_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    get_authenticated_portal_session = route_dependencies.get_authenticated_portal_session
    render_index_response = route_dependencies.render_index_response
    csrf_secret = runtime.token_keys.csrf
    audit_hash_secret = runtime.audit_hash_secret
    auth_policy = runtime.auth_policy
    text = portal_text(DEFAULT_LOCALE)

    def render_locked_page(request: Request) -> Response:
        return render_public_auth_template(
            request,
            settings=settings,
            csrf_secret=csrf_secret,
            template_name="locked.jinja",
            status_code=status.HTTP_423_LOCKED,
        )

    def render_password_reset_request(
        request: Request,
        *,
        status_code: int = status.HTTP_200_OK,
        error_message: str | None = None,
        notice_message: str | None = None,
        form_values: dict[str, str] | None = None,
        development_reset_url: str | None = None,
    ) -> Response:
        return render_public_auth_template(
            request,
            settings=settings,
            csrf_secret=csrf_secret,
            template_name="password_reset_request.jinja",
            status_code=status_code,
            error_message=error_message,
            notice_message=notice_message,
            form_values=form_values,
            development_reset_url=development_reset_url,
        )

    def render_mfa_page(
        request: Request,
        delivery: MfaChallengeDelivery,
        *,
        status_code: int = status.HTTP_200_OK,
        error_message: str | None = None,
        notice_message: str | None = None,
        retry_after_seconds: int | None = None,
    ) -> Response:
        csrf_token = create_csrf_token(csrf_secret)
        response = templates.TemplateResponse(
            request=request,
            name="mfa.jinja",
            context=mfa_template_context(
                request,
                settings=settings,
                delivery=delivery,
                csrf_token=csrf_token,
                error_message=error_message,
                notice_message=notice_message,
            ),
            status_code=status_code,
        )
        if retry_after_seconds is not None:
            response.headers["Retry-After"] = str(retry_after_seconds)
        set_csrf_cookie(response, csrf_token, settings=settings, path=CSRF_COOKIE_PATH)
        return response

    def get_browser_mfa_delivery_state(
        session: Session,
        payload: MfaResendRequest | MfaVerifyRequest,
        *,
        preferred_delivery_method: str | None = None,
    ) -> MfaChallengeDelivery | None:
        return get_mfa_challenge_delivery_state(
            session,
            payload.mfa_challenge_token,
            challenge_token_secret=runtime.token_keys.mfa,
            preferred_delivery_method=preferred_delivery_method,
        )

    @app.post("/auth/login", response_model=LoginResponse)
    async def login(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> dict[str, object] | Response:
        is_browser_form = is_urlencoded_form_request(request)
        payload = await get_login_request_from_request(request, csrf_secret)
        client_reference_hash = hash_sensitive_reference(
            audit_hash_secret,
            "login_client",
            get_request_client_reference(request, settings),
        )
        try:
            result = await run_in_threadpool(
                start_login,
                session,
                username=payload.username,
                password=payload.password,
                client_reference_hash=client_reference_hash,
                policy=auth_policy,
                session_token_secret=runtime.token_keys.session,
                mfa_challenge_token_secret=runtime.token_keys.mfa,
                mfa_code_secret=runtime.token_keys.mfa,
                clinic_id=settings.clinic_id,
                delivery_method=payload.mfa_delivery_method,
            )
        except InvalidCredentialsError:
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_401_UNAUTHORIZED,
                browser_message=portal_text()["incorrect_username_or_password"],
                json_content={"detail": "sign-in could not be completed"},
            )
        except AccountLockedError:
            if is_browser_form:
                return render_locked_page(request)
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_423_LOCKED,
                browser_message=text["session_locked_details"],
                json_content={"detail": ACCOUNT_LOCKED_DETAIL},
            )
        except PasswordResetRequiredError:
            if is_browser_form:
                return render_password_reset_request(
                    request,
                    status_code=status.HTTP_403_FORBIDDEN,
                    notice_message=text["password_reset_forced"],
                    form_values={"username": payload.username},
                )
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_403_FORBIDDEN,
                browser_message=text["password_reset_forced"],
                json_content={"status": "password_reset_required"},
            )
        except MfaDeliveryUnavailableError:
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_400_BAD_REQUEST,
                browser_message=text["mfa_delivery_unavailable"],
                json_content={"detail": MFA_DELIVERY_UNAVAILABLE_DETAIL},
            )
        except MfaRateLimitedError as exc:
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                browser_message=text["mfa_rate_limited"].format(
                    seconds=exc.retry_after_seconds,
                ),
                json_content={"detail": "MFA code was sent recently; try again later"},
                headers={"Retry-After": str(exc.retry_after_seconds)},
            )
        if result.mfa_challenge is not None:
            session.commit()
            try:
                await run_in_threadpool(send_mfa_challenge, runtime, result.mfa_challenge)
            except (PortalEmailDeliveryError, PortalSmsDeliveryError):
                runtime.operational_metrics.record_failure("mfa_delivery")
                record_mfa_delivery_and_commit(
                    session,
                    delivery=result.mfa_challenge,
                    outcome=AUDIT_OUTCOME_FAILURE,
                )
                return auth_error_response(
                    is_browser_form=is_browser_form,
                    request=request,
                    render_index_response=render_index_response,
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    browser_message=text["verification_delivery_failed"],
                    json_content={"detail": "verification code could not be sent"},
                )
            record_mfa_delivery_and_commit(
                session,
                delivery=result.mfa_challenge,
                outcome=AUDIT_OUTCOME_SUCCESS,
            )
        if is_browser_form and result.mfa_challenge is not None:
            return render_mfa_page(request, result.mfa_challenge)
        if is_browser_form and result.session_token is not None:
            redirect_response = RedirectResponse(
                PORTAL_ROOT_PATH,
                status_code=status.HTTP_303_SEE_OTHER,
            )
            set_portal_session_cookie(
                redirect_response,
                result.session_token,
                settings=settings,
            )
            return redirect_response
        return login_response_payload(result, settings=settings)

    @app.post("/auth/mfa/resend", response_model=MfaChallengeResponse)
    async def resend_mfa(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> dict[str, object] | Response:
        is_browser_form = is_urlencoded_form_request(request)
        payload = await get_mfa_resend_request_from_request(request, csrf_secret)
        try:
            delivery = await run_in_threadpool(
                resend_mfa_challenge,
                session,
                challenge_token=payload.mfa_challenge_token,
                delivery_method=payload.mfa_delivery_method,
                policy=auth_policy,
                challenge_token_secret=runtime.token_keys.mfa,
                code_secret=runtime.token_keys.mfa,
            )
        except MfaRateLimitedError as exc:
            if is_browser_form:
                delivery_state = get_browser_mfa_delivery_state(
                    session,
                    payload,
                    preferred_delivery_method=payload.mfa_delivery_method,
                )
                if delivery_state is not None:
                    return render_mfa_page(
                        request,
                        delivery_state,
                        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                        error_message=(
                            text["mfa_rate_limited"].format(
                                seconds=exc.retry_after_seconds,
                            )
                        ),
                        retry_after_seconds=exc.retry_after_seconds,
                    )
            return JSONResponse(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                content={"detail": "MFA code was sent recently; try again later"},
                headers={"Retry-After": str(exc.retry_after_seconds)},
            )
        except (MfaChallengeNotFoundError, ValueError):
            if is_browser_form:
                return render_index_response(
                    request,
                    status_code=status.HTTP_400_BAD_REQUEST,
                    error_message=text["mfa_sign_in_again"],
                )
            return JSONResponse(
                status_code=400,
                content={"detail": MFA_VERIFICATION_FAILED_DETAIL},
            )
        except AccountLockedError:
            if is_browser_form:
                return render_locked_page(request)
            return JSONResponse(
                status_code=status.HTTP_423_LOCKED,
                content={"detail": ACCOUNT_LOCKED_DETAIL},
            )
        except PasswordResetRequiredError:
            if is_browser_form:
                return render_password_reset_request(
                    request,
                    status_code=status.HTTP_403_FORBIDDEN,
                    notice_message=text["password_reset_forced"],
                )
            return JSONResponse(
                status_code=403,
                content={"status": "password_reset_required"},
            )
        except MfaDeliveryUnavailableError:
            if is_browser_form:
                delivery_state = get_browser_mfa_delivery_state(session, payload)
                if delivery_state is not None:
                    return render_mfa_page(
                        request,
                        delivery_state,
                        status_code=status.HTTP_400_BAD_REQUEST,
                        error_message=text["mfa_delivery_unavailable"],
                    )
            return JSONResponse(
                status_code=400,
                content={"detail": MFA_DELIVERY_UNAVAILABLE_DETAIL},
            )
        session.commit()
        try:
            await run_in_threadpool(send_mfa_challenge, runtime, delivery)
        except (PortalEmailDeliveryError, PortalSmsDeliveryError):
            runtime.operational_metrics.record_failure("mfa_delivery")
            record_mfa_delivery_and_commit(
                session,
                delivery=delivery,
                outcome=AUDIT_OUTCOME_FAILURE,
            )
            if is_browser_form:
                delivery_state = get_browser_mfa_delivery_state(session, payload)
                if delivery_state is not None:
                    return render_mfa_page(
                        request,
                        delivery_state,
                        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                        error_message=text["verification_delivery_failed"],
                    )
            return JSONResponse(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                content={"detail": "verification code could not be sent"},
            )
        record_mfa_delivery_and_commit(
            session,
            delivery=delivery,
            outcome=AUDIT_OUTCOME_SUCCESS,
        )
        if is_browser_form:
            return render_mfa_page(
                request,
                delivery,
                notice_message=text["mfa_new_code_sent"].format(
                    method=delivery.delivery_method.upper(),
                ),
            )
        return mfa_challenge_response_payload(delivery, settings=settings)

    @app.post("/auth/mfa/verify", response_model=MfaVerifyResponse)
    async def verify_mfa(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> dict[str, str] | Response:
        is_browser_form = is_urlencoded_form_request(request)
        payload = await get_mfa_verify_request_from_request(request, csrf_secret)
        try:
            session_token = await run_in_threadpool(
                verify_mfa_challenge,
                session,
                challenge_token=payload.mfa_challenge_token,
                code=payload.code,
                policy=auth_policy,
                challenge_token_secret=runtime.token_keys.mfa,
                session_token_secret=runtime.token_keys.session,
                code_secret=runtime.token_keys.mfa,
            )
        except InvalidMfaCodeError:
            if is_browser_form:
                delivery_state = get_browser_mfa_delivery_state(session, payload)
                if delivery_state is not None:
                    return render_mfa_page(
                        request,
                        delivery_state,
                        status_code=status.HTTP_401_UNAUTHORIZED,
                        error_message=text["incorrect_mfa_code"],
                    )
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_401_UNAUTHORIZED,
                browser_message=text["mfa_verification_failed"],
                json_content={"detail": MFA_VERIFICATION_FAILED_DETAIL},
            )
        except (MfaChallengeNotFoundError, ValueError):
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_400_BAD_REQUEST,
                browser_message=text["mfa_verification_failed"],
                json_content={"detail": MFA_VERIFICATION_FAILED_DETAIL},
            )
        except AccountLockedError:
            if is_browser_form:
                return render_locked_page(request)
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_423_LOCKED,
                browser_message=text["session_locked_details"],
                json_content={"detail": ACCOUNT_LOCKED_DETAIL},
            )
        except PasswordResetRequiredError:
            if is_browser_form:
                return render_password_reset_request(
                    request,
                    status_code=status.HTTP_403_FORBIDDEN,
                    notice_message=text["password_reset_forced"],
                )
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_403_FORBIDDEN,
                browser_message=text["password_reset_forced"],
                json_content={"status": "password_reset_required"},
            )
        if is_browser_form:
            redirect_response = RedirectResponse(
                PORTAL_ROOT_PATH,
                status_code=status.HTTP_303_SEE_OTHER,
            )
            set_portal_session_cookie(redirect_response, session_token, settings=settings)
            return redirect_response
        return {"status": "signed_in", "session_token": session_token}

    @app.get("/auth/password-reset", name="password_reset_request_page")
    def password_reset_request_page(request: Request) -> Response:
        return render_password_reset_request(request)

    @app.get(
        "/auth/password-reset/complete",
        name="password_reset_complete_page",
    )
    def password_reset_complete_page(request: Request) -> Response:
        return render_public_auth_template(
            request,
            settings=settings,
            csrf_secret=csrf_secret,
            template_name=PASSWORD_RESET_COMPLETE_TEMPLATE,
        )

    @app.post(
        "/auth/password-reset/request",
        response_model=PasswordResetRequestResponse,
        status_code=status.HTTP_202_ACCEPTED,
    )
    async def request_reset(
        request: Request,
        background_tasks: BackgroundTasks,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> dict[str, object] | Response:
        is_browser_form = is_urlencoded_form_request(request)
        try:
            payload = await get_password_reset_request_from_request(request, csrf_secret)
        except (BrowserFormValidationError, RequestValidationError):
            if not is_browser_form:
                raise
            return render_password_reset_request(
                request,
                status_code=status.HTTP_400_BAD_REQUEST,
                error_message=text["password_reset_request_details"],
            )
        client_reference_hash = hash_sensitive_reference(
            audit_hash_secret,
            "password_reset_client",
            get_request_client_reference(request, settings),
        )
        result = request_password_reset(
            session,
            username=payload.username,
            email=payload.email,
            client_reference_hash=client_reference_hash,
            policy=auth_policy,
            reset_token_secret=runtime.token_keys.password_reset,
            clinic_id=settings.clinic_id,
        )
        response_reset_token = result.reset_token
        development_reset_url = None
        if result.reset_token is not None and result.recipient is not None:
            session.commit()
            reset_url = build_password_reset_url(
                request,
                settings=settings,
                reset_token=result.reset_token,
            )
            if settings.is_development:
                try:
                    await run_in_threadpool(
                        send_password_reset_email,
                        runtime,
                        recipient=result.recipient,
                        reset_url=reset_url,
                    )
                except PortalEmailDeliveryError as exc:
                    record_password_reset_delivery_outcome(
                        session,
                        result=result,
                        outcome=AUDIT_OUTCOME_FAILURE,
                    )
                    session.commit()
                    # SMTP exceptions may contain recipient data; keep this log PHI-safe. The
                    # message is a fixed literal and the sole interpolation is the exception
                    # class name, so the credential-disclosure rule below has nothing to disclose.
                    # nosemgrep: python-logger-credential-disclosure -- logs only type(exc).__name__
                    logger.error(  # NOSONAR
                        "Password reset email delivery failed: %s",
                        type(exc).__name__,
                    )
                    response_reset_token = None
                else:
                    record_password_reset_delivery_outcome(
                        session,
                        result=result,
                        outcome=AUDIT_OUTCOME_SUCCESS,
                    )
                    session.commit()
                    development_reset_url = reset_url
            else:
                background_tasks.add_task(
                    deliver_password_reset,
                    runtime,
                    result=result,
                    reset_url=reset_url,
                )
        if is_browser_form:
            return render_password_reset_request(
                request,
                status_code=status.HTTP_202_ACCEPTED,
                notice_message=text["password_reset_link_sent"],
                form_values={
                    "username": payload.username,
                    "email": payload.email,
                },
                development_reset_url=development_reset_url,
            )
        return password_reset_request_response_payload(response_reset_token, settings=settings)

    @app.post("/auth/password-reset/complete", response_model=PasswordResetCompleteResponse)
    async def complete_reset(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> dict[str, str] | Response:
        is_browser_form = is_urlencoded_form_request(request)
        try:
            payload = await get_password_reset_complete_request_from_request(
                request,
                csrf_secret,
            )
        except BrowserFormValidationError as exc:
            if not is_browser_form:
                raise
            error_key = {
                "password_mismatch": "password_mismatch",
                "invalid_password": "password_invalid",
            }.get(str(exc), "password_reset_complete_error")
            return render_public_auth_template(
                request,
                settings=settings,
                csrf_secret=csrf_secret,
                template_name=PASSWORD_RESET_COMPLETE_TEMPLATE,
                status_code=status.HTTP_400_BAD_REQUEST,
                error_message=text[error_key],
                reset_token=exc.safe_form_values.get("reset_token"),
            )
        try:
            account = await run_in_threadpool(
                complete_password_reset,
                session,
                reset_token=payload.reset_token,
                new_password=payload.new_password,
                reset_token_secret=runtime.token_keys.password_reset,
                clinic_id=settings.clinic_id,
            )
        except PasswordResetTokenInvalidError:
            if is_browser_form:
                return render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name=PASSWORD_RESET_COMPLETE_TEMPLATE,
                    status_code=status.HTTP_400_BAD_REQUEST,
                    error_message=text["password_reset_complete_error"],
                )
            return JSONResponse(
                status_code=400,
                content={"detail": "password reset could not be completed"},
            )
        if is_browser_form:
            return render_public_auth_template(
                request,
                settings=settings,
                csrf_secret=csrf_secret,
                template_name="auth_result.jinja",
                result_heading=text["password_reset_success_heading"],
                result_message=text["password_reset_success"],
            )
        return {"status": "password_reset", "username": account.username}

    @app.get("/auth/email-change/confirm", name="email_change_confirm_page")
    def email_change_confirm_page(request: Request) -> Response:
        return render_public_auth_template(
            request,
            settings=settings,
            csrf_secret=csrf_secret,
            template_name=EMAIL_CHANGE_TEMPLATE,
        )

    @app.post("/auth/email-change/confirm", name="confirm_email_change_submission")
    async def confirm_email_change_submission(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response:
        """Apply a confirmed contact change.

        Public and unauthenticated by design: the patient may open this link in the mail client on
        another device, where the portal session cookie does not exist. Possession of the one-time
        token, which only the proposed mailbox received, is the proof being checked.
        """
        form_values = await get_csrf_urlencoded_form_values(
            request,
            csrf_secret,
            unsupported_media_type_detail=(
                "email change confirmation requires a form request body"
            ),
        )
        confirmation_token = first_form_value_or_empty(form_values, "reset_token")
        try:
            confirmation = await run_in_threadpool(
                confirm_email_change,
                session,
                confirmation_token=confirmation_token,
                token_secret=runtime.token_keys.email_change,
                clinic_id=settings.clinic_id,
            )
        except (EmailChangeTokenInvalidError, ValueError):
            # Deliberately one generic outcome: an expired link, a superseded link, a link for a
            # locked account, and a forged token must not be distinguishable from each other.
            return render_public_auth_template(
                request,
                settings=settings,
                csrf_secret=csrf_secret,
                template_name=EMAIL_CHANGE_TEMPLATE,
                status_code=status.HTTP_400_BAD_REQUEST,
                error_message=text["email_change_complete_error"],
            )
        session.commit()
        for recipient in confirmation.notice_recipients:
            try:
                await run_in_threadpool(
                    send_contact_change_notice,
                    runtime,
                    recipient=recipient,
                )
            except PortalEmailDeliveryError:
                # The change is already committed and the patient is looking at the result page,
                # so a failed notice is recorded and surfaced in metrics rather than rolled back.
                runtime.operational_metrics.record_failure("contact_change_delivery")
                logger.error("Contact-change notice delivery failed")
        return render_public_auth_template(
            request,
            settings=settings,
            csrf_secret=csrf_secret,
            template_name="auth_result.jinja",
            result_heading=text["email_change_success_heading"],
            result_message=text["email_change_success"],
        )

    @app.get("/auth/session", response_model=SessionResponse)
    def read_session(
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_portal_session),
        ],
    ) -> dict[str, object]:
        account = authenticated_session.account
        return {
            "status": "authenticated",
            "username": account.username,
            "clinic_id": account.clinic_id,
            "demographic_no": account.demographic_no,
        }


def register_logout_route(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    get_authorization_bearer_token = route_dependencies.get_authorization_bearer_token

    @app.post(
        "/auth/logout",
        response_model=LogoutResponse,
        responses=AUTH_LOGOUT_RESPONSES,
    )
    def logout(
        session_token: Annotated[str, Depends(get_authorization_bearer_token)],
        response: Response,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> dict[str, str]:
        try:
            logout_patient_session(
                session,
                session_token=session_token,
                session_token_secret=runtime.token_keys.session,
                idle_timeout=runtime.auth_policy.session_idle_timeout,
            )
        except (PortalSessionInvalidError, ValueError) as exc:
            raise HTTPException(status_code=401, detail=AUTHENTICATION_REQUIRED_DETAIL) from exc
        clear_portal_session_cookie(response, settings=settings)
        return {"status": "logged_out"}
