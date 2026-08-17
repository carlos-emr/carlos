"""Authenticated patient routes: the dashboard, account settings, and email passwords.

Browser routes here authenticate from the `/portal`-scoped session cookie and answer an invalid
session with a redirect to the sign-in page; the `/api/patient/*` routes authenticate from a
bearer token and answer with JSON. Both surfaces audit every disclosure of a stored passphrase.
"""

import logging
from datetime import timedelta
from typing import Annotated

from fastapi import BackgroundTasks, Depends, FastAPI, HTTPException, Query, Request, status
from fastapi import Path as PathParam
from fastapi.responses import JSONResponse, RedirectResponse
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool
from starlette.responses import Response

from carlos_patient_portal.account_settings import (
    ACCOUNT_SETTINGS_REASON_DELIVERY_UNAVAILABLE,
    CONTACT_UPDATE_OUTCOME_CONFIRMATION_REQUIRED,
    CONTACT_UPDATE_OUTCOME_NO_CHANGE,
    AccountSettingsStepUpError,
    AccountSettingsValidationError,
    ContactUpdateResult,
    PhoneChangeCodeInvalidError,
    PhoneChangeRateLimitedError,
    change_account_password,
    confirm_phone_change,
    record_account_settings_audit_event,
    resend_phone_change_code,
    update_account_contact,
    update_account_mfa_method,
)
from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.auth import AuthenticatedPortalSession
from carlos_patient_portal.delivery_outbox import (
    enqueue_contact_change_delivery,
    process_one_delivery,
)
from carlos_patient_portal.email_delivery import PortalEmailDeliveryError
from carlos_patient_portal.i18n import DEFAULT_LOCALE, portal_text
from carlos_patient_portal.models import (
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
    AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_REQUEST,
    AUDIT_EVENT_UNLOCK_SECRET_LIST,
    AUDIT_EVENT_UNLOCK_SECRET_READ,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    EMAIL_CHANGE_STATUS_REVOKED,
    UNLOCK_SECRET_TYPE_EMAIL,
    PatientPortalAccount,
)
from carlos_patient_portal.notifications import (
    build_email_change_confirmation_url,
    send_contact_change_notice,
    send_email_change_confirmation,
    send_email_change_requested_notice,
)
from carlos_patient_portal.presenters import (
    normalize_email_password_dashboard_provider,
    normalize_email_password_dashboard_search,
)
from carlos_patient_portal.runtime import (
    MAX_DATABASE_ID,
    MAX_PAGE_OFFSET,
    PortalRuntime,
    RouteDependencies,
    function_scoped_database_dependency,
)
from carlos_patient_portal.schemas import EmailPasswordListResponse, EmailPasswordSecretResponse
from carlos_patient_portal.sms_delivery import PortalSmsDeliveryError
from carlos_patient_portal.unlock_secrets import (
    DEFAULT_UNLOCK_SECRET_LIST_LIMIT,
    MAX_UNLOCK_SECRET_LIST_LIMIT,
    MAX_UNLOCK_SECRET_PROVIDER_FILTER_LENGTH,
    MAX_UNLOCK_SECRET_SEARCH_LENGTH,
    UnlockSecretDecryptionError,
    UnlockSecretNotFoundError,
    UnlockSecretNotPublishedError,
    UnlockSecretRevokedError,
    list_unlock_secrets,
    read_scoped_unlock_secret,
    read_unlock_secret,
)
from carlos_patient_portal.web_support import (
    ACCOUNT_NOTICE_MESSAGE_KEYS,
    AUTHENTICATION_REQUIRED_DETAIL,
    CSRF_COOKIE_NAME,
    CSRF_FORM_FIELD,
    MAX_FORM_BODY_BYTES,
    MAX_FORM_FIELD_COUNT,
    PORTAL_LOGOUT_RESPONSES,
    PORTAL_ROOT_PATH,
    PORTAL_SESSION_COOKIE_NAME,
    clear_portal_session_cookie,
    email_password_record_response_payload,
    email_password_secret_response_payload,
    first_form_value,
    first_form_value_or_empty,
    get_urlencoded_form_values,
    is_valid_csrf_submission,
    logout_browser_session_cookie_token,
    parse_optional_email_password_date,
    set_portal_session_cookie,
)

logger = logging.getLogger(__name__)


def register_patient_email_password_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    get_app_database_session = route_dependencies.get_app_database_session
    get_authenticated_portal_session = route_dependencies.get_authenticated_portal_session

    @app.get("/api/patient/email-passwords", response_model=EmailPasswordListResponse)
    def list_patient_email_passwords(
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_portal_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        limit: Annotated[
            int,
            Query(ge=1, le=MAX_UNLOCK_SECRET_LIST_LIMIT),
        ] = DEFAULT_UNLOCK_SECRET_LIST_LIMIT,
        offset: Annotated[int, Query(ge=0, le=MAX_PAGE_OFFSET)] = 0,
    ) -> dict[str, object]:
        account = authenticated_session.account
        records = list_unlock_secrets(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            limit=limit,
            offset=offset,
        )
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_UNLOCK_SECRET_LIST,
            outcome=AUDIT_OUTCOME_SUCCESS,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
        )
        return {
            "items": [email_password_record_response_payload(record) for record in records],
            "limit": limit,
            "offset": offset,
        }

    @app.get(
        "/api/patient/email-passwords/{email_password_id}",
        response_model=EmailPasswordSecretResponse,
    )
    def retrieve_patient_email_password(
        email_password_id: Annotated[int, PathParam(gt=0, le=MAX_DATABASE_ID)],
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_portal_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response | dict[str, object]:
        account = authenticated_session.account
        try:
            disclosure = read_scoped_unlock_secret(
                session,
                email_password_id,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                audit_account_id=account.id,
                actor_type=AUDIT_ACTOR_TYPE_PATIENT,
                actor=account.username,
                encryption_keys=(
                    runtime.unlock_secret_encryption_keys
                    or {"primary": runtime.unlock_secret_encryption_secret}
                ),
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            )
        except (
            UnlockSecretNotFoundError,
            UnlockSecretRevokedError,
            UnlockSecretNotPublishedError,
        ):
            record_audit_event(
                session,
                event_type=AUDIT_EVENT_UNLOCK_SECRET_READ,
                outcome=AUDIT_OUTCOME_FAILURE,
                actor_type=AUDIT_ACTOR_TYPE_PATIENT,
                actor=account.username,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                account_id=account.id,
                reason="not_available",
            )
            return JSONResponse(
                status_code=status.HTTP_404_NOT_FOUND,
                content={"detail": "email password not found"},
            )
        except UnlockSecretDecryptionError:
            runtime.operational_metrics.record_failure("unlock_secret_decryption")
            logger.error("Unlock-secret decryption failed")
            return JSONResponse(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                content={"detail": "email password unavailable"},
            )
        return email_password_secret_response_payload(
            disclosure.unlock_secret,
            passphrase=disclosure.secret,
        )


def register_portal_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    render_portal_page = route_dependencies.render_portal_page
    get_portal_account_form_values = route_dependencies.get_portal_account_form_values
    get_portal_cookie_session_or_redirect = route_dependencies.get_portal_cookie_session_or_redirect
    render_account_change_error = route_dependencies.render_account_change_error
    csrf_secret = runtime.token_keys.csrf

    @app.get(PORTAL_ROOT_PATH)
    def portal_dashboard(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response:
        return render_portal_page(request, session, active_module="dashboard")

    @app.get("/portal/account")
    def portal_account(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        account_status: Annotated[
            str | None,
            Query(alias="status", max_length=32),
        ] = None,
    ) -> Response:
        return render_portal_page(
            request,
            session,
            active_module="account",
            account_notice=portal_text(DEFAULT_LOCALE).get(
                ACCOUNT_NOTICE_MESSAGE_KEYS.get(account_status or "", ""),
            ),
        )

    @app.post("/portal/account/password")
    async def portal_account_password(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response:
        form_values = await get_portal_account_form_values(
            request,
            csrf_error_detail="password change could not be completed",
        )
        authenticated_session = get_portal_cookie_session_or_redirect(request, session)
        if isinstance(authenticated_session, RedirectResponse):
            return authenticated_session

        new_password = first_form_value_or_empty(form_values, "new_password")
        if new_password != first_form_value_or_empty(
            form_values,
            "new_password_confirmation",
        ):
            return render_account_change_error(
                request,
                session,
                status_code=status.HTTP_400_BAD_REQUEST,
            )
        try:
            replacement_session_token = await run_in_threadpool(
                change_account_password,
                session,
                authenticated_session.account,
                authenticated_session.portal_session,
                current_password=first_form_value_or_empty(form_values, "current_password"),
                new_password=new_password,
                max_failed_password_attempts=settings.auth_max_failed_password_attempts,
                policy=runtime.auth_policy,
                session_token_secret=runtime.token_keys.session,
            )
        except AccountSettingsStepUpError:
            return render_account_change_error(
                request,
                session,
                status_code=status.HTTP_403_FORBIDDEN,
            )
        except (AccountSettingsValidationError, ValueError):
            return render_account_change_error(
                request,
                session,
                status_code=status.HTTP_400_BAD_REQUEST,
            )
        response = RedirectResponse(
            "/portal/account?status=password-updated",
            status_code=status.HTTP_303_SEE_OTHER,
        )
        set_portal_session_cookie(
            response,
            replacement_session_token,
            settings=settings,
        )
        return response

    @app.post("/portal/account/contact")
    async def portal_account_contact(
        request: Request,
        background_tasks: BackgroundTasks,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response:
        form_values = await get_portal_account_form_values(
            request,
            csrf_error_detail="contact update could not be completed",
        )
        authenticated_session = get_portal_cookie_session_or_redirect(request, session)
        if isinstance(authenticated_session, RedirectResponse):
            return authenticated_session

        try:
            contact_update = await run_in_threadpool(
                update_account_contact,
                session,
                authenticated_session.account,
                current_password=first_form_value_or_empty(form_values, "current_password"),
                email=first_form_value_or_empty(form_values, "email"),
                phone_number=first_form_value(form_values, "phone_number"),
                max_failed_password_attempts=settings.auth_max_failed_password_attempts,
                email_change_token_secret=runtime.token_keys.email_change,
                email_change_token_ttl=timedelta(
                    seconds=settings.email_change_token_ttl_seconds
                ),
                phone_change_code_ttl=timedelta(
                    seconds=settings.phone_change_code_ttl_seconds
                ),
            )
        except AccountSettingsStepUpError:
            return render_account_change_error(
                request,
                session,
                status_code=status.HTTP_403_FORBIDDEN,
            )
        except (AccountSettingsValidationError, ValueError):
            return render_account_change_error(
                request,
                session,
                status_code=status.HTTP_400_BAD_REQUEST,
            )
        if contact_update.outcome == CONTACT_UPDATE_OUTCOME_NO_CHANGE:
            return redirect_to_account_status("no-change")
        if contact_update.outcome == CONTACT_UPDATE_OUTCOME_CONFIRMATION_REQUIRED:
            session.commit()
            return await deliver_email_change_request(
                request,
                session,
                authenticated_session.account,
                contact_update=contact_update,
            )
        return await deliver_contact_change_notices(
            session,
            authenticated_session.account,
            background_tasks=background_tasks,
            recipients=contact_update.notice_recipients,
            success_status_key="contact-updated",
            failure_status_key="contact-updated-notice-failed",
        )

    def redirect_to_account_status(status_key: str) -> RedirectResponse:
        return RedirectResponse(
            f"/portal/account?status={status_key}",
            status_code=status.HTTP_303_SEE_OTHER,
        )

    async def deliver_contact_change_notices(
        session: Session,
        account: PatientPortalAccount,
        *,
        background_tasks: BackgroundTasks,
        recipients: tuple[str, ...],
        success_status_key: str,
        failure_status_key: str,
    ) -> Response:
        """Notify every affected address, and make a failed notice durable rather than silent.

        The notice to the address a change moved away from is the only out-of-band alarm a patient
        gets, so a delivery failure has to be visible to them and recorded, not swallowed into an
        unqualified success row.
        """
        if not settings.is_development:
            deliveries = [
                enqueue_contact_change_delivery(
                    session,
                    account_id=account.id,
                    recipient=recipient,
                    encryption_secret=runtime.outbox_encryption_secret,
                )
                for recipient in recipients
            ]
            session.commit()
            for delivery in deliveries:
                background_tasks.add_task(
                    process_one_delivery,
                    runtime.session_factory,
                    email_sender=runtime.email_sender,
                    encryption_secret=runtime.outbox_encryption_secret,
                    max_attempts=settings.outbox_max_attempts,
                    lease_seconds=settings.outbox_lease_seconds,
                    delivery_id=delivery.id,
                )
            return redirect_to_account_status(success_status_key)

        session.commit()
        notices_delivered = True
        for recipient in recipients:
            try:
                await run_in_threadpool(
                    send_contact_change_notice,
                    runtime,
                    recipient=recipient,
                )
            except PortalEmailDeliveryError:
                notices_delivered = False
                runtime.operational_metrics.record_failure("contact_change_delivery")
                logger.error("Contact-change notice delivery failed")
        if notices_delivered:
            return redirect_to_account_status(success_status_key)
        record_account_settings_audit_event(
            session,
            account,
            event_type=AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
            outcome=AUDIT_OUTCOME_FAILURE,
            reason=ACCOUNT_SETTINGS_REASON_DELIVERY_UNAVAILABLE,
        )
        session.commit()
        return redirect_to_account_status(failure_status_key)

    async def deliver_email_change_request(
        request: Request,
        session: Session,
        account: PatientPortalAccount,
        *,
        contact_update: ContactUpdateResult,
    ) -> Response:
        """Send the confirmation link, and revoke the request if it cannot be delivered.

        A pending request the patient can never confirm is worse than no request: it occupies the
        one-pending-per-account slot and leaves them believing a change is in flight. If the
        confirmation cannot be sent, the request is revoked and the patient is told plainly.
        """
        try:
            if contact_update.confirmation_recipient is not None:
                confirmation_url = build_email_change_confirmation_url(
                    request,
                    settings=settings,
                    confirmation_token=contact_update.confirmation_token or "",
                )
                await run_in_threadpool(
                    send_email_change_confirmation,
                    runtime,
                    recipient=contact_update.confirmation_recipient,
                    confirmation_url=confirmation_url,
                )
            if contact_update.phone_confirmation_recipient is not None:
                if runtime.sms_sender is None:
                    raise PortalEmailDeliveryError("phone confirmation delivery is not configured")
                await run_in_threadpool(
                    runtime.sms_sender.send_code,
                    recipient=contact_update.phone_confirmation_recipient,
                    code=contact_update.phone_confirmation_code or "",
                    expires_in_seconds=settings.phone_change_code_ttl_seconds,
                )
        except (PortalEmailDeliveryError, PortalSmsDeliveryError):
            runtime.operational_metrics.record_failure("email_change_delivery")
            logger.error("Email-change confirmation delivery failed")
            if contact_update.email_change_request is not None:
                contact_update.email_change_request.status = EMAIL_CHANGE_STATUS_REVOKED
            record_account_settings_audit_event(
                session,
                account,
                event_type=AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_REQUEST,
                outcome=AUDIT_OUTCOME_FAILURE,
                reason=ACCOUNT_SETTINGS_REASON_DELIVERY_UNAVAILABLE,
            )
            session.commit()
            return redirect_to_account_status("email-confirmation-notice-failed")
        # Best-effort: the current address is told a change was requested. Unlike the confirmation
        # itself, failing to send this must not cancel the request the patient just made.
        try:
            await run_in_threadpool(
                send_email_change_requested_notice,
                runtime,
                recipient=account.email,
            )
        except PortalEmailDeliveryError:
            runtime.operational_metrics.record_failure("contact_change_delivery")
            logger.error("Email-change requested notice delivery failed")
        if (
            contact_update.confirmation_recipient is not None
            and contact_update.phone_confirmation_recipient is not None
        ):
            return redirect_to_account_status("contact-confirmation-required")
        if contact_update.phone_confirmation_recipient is not None:
            return redirect_to_account_status("phone-confirmation-required")
        return redirect_to_account_status("email-confirmation-required")

    @app.post("/portal/account/contact/confirm-phone")
    async def portal_account_confirm_phone(
        request: Request,
        background_tasks: BackgroundTasks,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response:
        form_values = await get_portal_account_form_values(
            request,
            csrf_error_detail="phone confirmation could not be completed",
        )
        authenticated_session = get_portal_cookie_session_or_redirect(request, session)
        if isinstance(authenticated_session, RedirectResponse):
            return authenticated_session
        try:
            confirmation = await run_in_threadpool(
                confirm_phone_change,
                session,
                authenticated_session.account,
                code=first_form_value_or_empty(form_values, "phone_confirmation_code"),
                token_secret=runtime.token_keys.email_change,
                max_failed_attempts=settings.phone_change_max_failed_attempts,
                code_ttl=timedelta(seconds=settings.phone_change_code_ttl_seconds),
            )
        except (PhoneChangeCodeInvalidError, ValueError):
            session.commit()
            return redirect_to_account_status("phone-confirmation-invalid")
        if not confirmation.applied:
            session.commit()
            return redirect_to_account_status("email-confirmation-required")
        return await deliver_contact_change_notices(
            session,
            authenticated_session.account,
            background_tasks=background_tasks,
            recipients=confirmation.notice_recipients,
            success_status_key="contact-updated",
            failure_status_key="contact-updated-notice-failed",
        )

    @app.post("/portal/account/contact/resend-phone")
    async def portal_account_resend_phone(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response:
        await get_portal_account_form_values(
            request,
            csrf_error_detail="phone confirmation could not be resent",
        )
        authenticated_session = get_portal_cookie_session_or_redirect(request, session)
        if isinstance(authenticated_session, RedirectResponse):
            return authenticated_session
        try:
            code, recipient = resend_phone_change_code(
                session,
                authenticated_session.account,
                token_secret=runtime.token_keys.email_change,
                resend_cooldown=timedelta(
                    seconds=settings.phone_change_resend_cooldown_seconds
                ),
            )
            if runtime.sms_sender is None:
                raise PortalSmsDeliveryError("phone confirmation delivery is not configured")
            await run_in_threadpool(
                runtime.sms_sender.send_code,
                recipient=recipient,
                code=code,
                expires_in_seconds=settings.phone_change_code_ttl_seconds,
            )
            session.commit()
        except PhoneChangeRateLimitedError:
            session.rollback()
            return redirect_to_account_status("phone-confirmation-rate-limited")
        except (PhoneChangeCodeInvalidError, PortalSmsDeliveryError, ValueError):
            # Preserve the last successfully delivered code when a resend fails.
            session.rollback()
            return redirect_to_account_status("phone-confirmation-invalid")
        return redirect_to_account_status("phone-confirmation-required")

    @app.post("/portal/account/mfa")
    async def portal_account_mfa(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response:
        form_values = await get_portal_account_form_values(
            request,
            csrf_error_detail="MFA update could not be completed",
        )
        authenticated_session = get_portal_cookie_session_or_redirect(request, session)
        if isinstance(authenticated_session, RedirectResponse):
            return authenticated_session

        try:
            await run_in_threadpool(
                update_account_mfa_method,
                session,
                authenticated_session.account,
                current_password=first_form_value_or_empty(form_values, "current_password"),
                preferred_mfa_method=first_form_value_or_empty(
                    form_values,
                    "preferred_mfa_method",
                ),
                max_failed_password_attempts=settings.auth_max_failed_password_attempts,
            )
        except AccountSettingsStepUpError:
            return render_account_change_error(
                request,
                session,
                status_code=status.HTTP_403_FORBIDDEN,
            )
        except (AccountSettingsValidationError, ValueError):
            return render_account_change_error(
                request,
                session,
                status_code=status.HTTP_400_BAD_REQUEST,
            )
        return RedirectResponse(
            "/portal/account?status=mfa-updated",
            status_code=status.HTTP_303_SEE_OTHER,
        )

    @app.get("/portal/email-passwords")
    def portal_email_passwords(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        q: Annotated[str | None, Query(max_length=MAX_UNLOCK_SECRET_SEARCH_LENGTH)] = None,
        provider: Annotated[
            str | None,
            Query(max_length=MAX_UNLOCK_SECRET_PROVIDER_FILTER_LENGTH),
        ] = None,
        date_from: Annotated[str | None, Query()] = None,
        date_to: Annotated[str | None, Query()] = None,
        page: Annotated[int, Query(ge=1)] = 1,
    ) -> Response:
        filter_error: str | None = None
        try:
            parsed_date_from = parse_optional_email_password_date(date_from)
            parsed_date_to = parse_optional_email_password_date(date_to)
        except ValueError:
            parsed_date_from = None
            parsed_date_to = None
            filter_error = portal_text(DEFAULT_LOCALE)["date_format_error"]
        invalid_date_range = (
            parsed_date_from is not None
            and parsed_date_to is not None
            and parsed_date_from > parsed_date_to
        )
        authenticated_session = get_portal_cookie_session_or_redirect(request, session)
        if isinstance(authenticated_session, RedirectResponse):
            return authenticated_session
        # The JSON and FHIR surfaces audit list access; the browser index must too, so
        # "opened/searched the password list" stays distinguishable from "never accessed it".
        # Only whether filters were used is recorded — never the raw query, which can be PHI.
        # This is the route's write: the assembler below stays read-only.
        account = authenticated_session.account
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_UNLOCK_SECRET_LIST,
            outcome=AUDIT_OUTCOME_SUCCESS,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            # Normalised exactly as the assembler does, so a whitespace-only filter is not
            # recorded as a search the patient never actually ran.
            reason=(
                "browser_filtered"
                if any(
                    (
                        normalize_email_password_dashboard_search(q),
                        normalize_email_password_dashboard_provider(provider),
                        parsed_date_from,
                        parsed_date_to,
                    )
                )
                else "browser"
            ),
        )
        session.commit()
        return render_portal_page(
            request,
            session,
            authenticated_session=authenticated_session,
            active_module="email-passwords",
            status_code=(
                status.HTTP_400_BAD_REQUEST
                if filter_error is not None or invalid_date_range
                else status.HTTP_200_OK
            ),
            email_password_search=q,
            email_password_provider=provider,
            email_password_date_from=parsed_date_from,
            email_password_date_to=parsed_date_to,
            email_password_page=page,
            email_password_filter_error=filter_error,
        )

    @app.post("/portal/email-passwords/{email_password_id}/reveal")
    async def reveal_portal_email_password(
        email_password_id: Annotated[int, PathParam(gt=0, le=MAX_DATABASE_ID)],
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response:
        await get_portal_account_form_values(
            request,
            csrf_error_detail="email password could not be revealed",
        )
        authenticated_session = get_portal_cookie_session_or_redirect(request, session)
        if isinstance(authenticated_session, RedirectResponse):
            return JSONResponse(
                status_code=status.HTTP_401_UNAUTHORIZED,
                content={"detail": AUTHENTICATION_REQUIRED_DETAIL},
            )
        account = authenticated_session.account
        try:
            passphrase = read_unlock_secret(
                session,
                email_password_id,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                audit_account_id=account.id,
                actor_type=AUDIT_ACTOR_TYPE_PATIENT,
                actor=account.username,
                encryption_keys=(
                    runtime.unlock_secret_encryption_keys
                    or {"primary": runtime.unlock_secret_encryption_secret}
                ),
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            )
        except (
            UnlockSecretNotFoundError,
            UnlockSecretRevokedError,
            UnlockSecretNotPublishedError,
        ):
            record_audit_event(
                session,
                event_type=AUDIT_EVENT_UNLOCK_SECRET_READ,
                outcome=AUDIT_OUTCOME_FAILURE,
                actor_type=AUDIT_ACTOR_TYPE_PATIENT,
                actor=account.username,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                account_id=account.id,
                reason="not_available",
            )
            return JSONResponse(
                status_code=status.HTTP_404_NOT_FOUND,
                content={"detail": "email password not found"},
            )
        except UnlockSecretDecryptionError:
            runtime.operational_metrics.record_failure("unlock_secret_decryption")
            logger.error("Unlock-secret decryption failed")
            return JSONResponse(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                content={"detail": "email password unavailable"},
            )
        return JSONResponse(content={"passphrase": passphrase})

    @app.get("/portal/help")
    def portal_help(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response:
        return render_portal_page(request, session, active_module="help")

    @app.post("/portal/logout", responses=PORTAL_LOGOUT_RESPONSES)
    async def portal_logout(
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> Response:
        form_values = await get_urlencoded_form_values(
            request,
            MAX_FORM_BODY_BYTES,
            MAX_FORM_FIELD_COUNT,
        )
        csrf_token = first_form_value(form_values, CSRF_FORM_FIELD)
        csrf_cookie = request.cookies.get(CSRF_COOKIE_NAME)
        if not is_valid_csrf_submission(csrf_token, csrf_cookie, csrf_secret):
            raise HTTPException(status_code=403, detail="logout could not be completed")

        response = RedirectResponse("/", status_code=status.HTTP_303_SEE_OTHER)
        logout_browser_session_cookie_token(
            session,
            session_token=request.cookies.get(PORTAL_SESSION_COOKIE_NAME),
            session_token_secret=runtime.token_keys.session,
            idle_timeout=runtime.auth_policy.session_idle_timeout,
        )
        clear_portal_session_cookie(response, settings=settings)
        return response
