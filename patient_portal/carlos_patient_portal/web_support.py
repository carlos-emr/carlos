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

"""Shared HTTP plumbing for the portal's route modules.

CSRF tokens and cookies, session cookies, request-body parsing and size limits, path and
content-type predicates, response payload shapes, and the Jinja template environment with the
context builders each public page needs.

Extracted from `main.py` so the route modules can depend on this without importing the application
module that composes them, which would be circular. Nothing here registers a route, opens a
database session, or performs I/O beyond rendering a template.
"""

import json
import re
from collections.abc import Callable
from datetime import date, timedelta
from hashlib import sha256
from hmac import new as new_hmac
from ipaddress import ip_address, ip_network
from pathlib import Path as FilePath
from secrets import compare_digest, token_urlsafe
from time import time
from typing import TypeVar
from urllib.parse import parse_qs, urlsplit

from fastapi import HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.templating import Jinja2Templates
from jinja2 import Environment, FileSystemLoader
from pydantic import BaseModel, ValidationError
from sqlalchemy.orm import Session
from starlette.responses import Response

from carlos_patient_portal.audit import UNKNOWN_CLIENT_REFERENCE
from carlos_patient_portal.auth import (
    AuthenticatedPortalSession,
    LoginResult,
    MfaChallengeDelivery,
    PortalSessionInvalidError,
    logout_patient_session,
)
from carlos_patient_portal.config import Settings
from carlos_patient_portal.i18n import (
    LOCALE_COOKIE_NAME,
    portal_text,
    resolve_locale,
    supported_locale_options,
)
from carlos_patient_portal.interop import build_fhir_r4_operation_outcome
from carlos_patient_portal.models import (
    MFA_DELIVERY_METHOD_EMAIL,
    MFA_DELIVERY_METHOD_SMS,
    PatientPortalAccount,
    PatientPortalInvite,
    PatientPortalUnlockSecret,
)
from carlos_patient_portal.routes.fhir import fhir_json_response
from carlos_patient_portal.schemas import (
    ActivationRequest,
    InviteCreateRequest,
    LoginRequest,
    MfaResendRequest,
    MfaVerifyRequest,
    PasswordResetCompleteRequest,
    PasswordResetRequest,
)
from carlos_patient_portal.view_models import EmailPasswordDashboardViewModel

PACKAGE_DIR = FilePath(__file__).resolve().parent


RequestModel = TypeVar("RequestModel", bound=BaseModel)


# Semgrep's direct-use-of-jinja2 rule targets Flask, where it advises render_template() over a
# hand-built Environment because Flask's own default is autoescape=False. This is Starlette's
# Jinja2Templates — the framework-supplied renderer — and autoescape is set explicitly, so the
# escaping the rule protects is on. Marked inline on both lines because the rule is Semgrep PRO
# and cannot be run locally to confirm which line the match anchors to.
templates = Jinja2Templates(  # nosemgrep: direct-use-of-jinja2 -- see comment above
    env=Environment(  # nosemgrep: direct-use-of-jinja2 -- see comment above
        loader=FileSystemLoader(str(PACKAGE_DIR / "templates")),
        autoescape=True,
    )
)


CONTENT_SECURITY_POLICY = (
    "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; "
    "object-src 'none'"
)


FHIR_PATH_PREFIX = "/fhir/"


PORTAL_ROOT_PATH = "/portal"
JSON_MEDIA_TYPE = "application/json"


SERVICE_UNAVAILABLE_DETAIL = "service temporarily unavailable"


AUTHENTICATION_REQUIRED_DETAIL = "authentication required"


# Shared by login, resend, and activation so every JSON surface stays equally generic; the browser
# equivalents come from the locale catalog.
MFA_DELIVERY_UNAVAILABLE_DETAIL = "MFA delivery method is unavailable"


NOT_FOUND_DETAIL = "not found"


INVALID_CSRF_DETAIL = "invalid CSRF token"


ACCOUNT_LOCKED_DETAIL = "account access is locked; contact the clinic for help"


MFA_VERIFICATION_FAILED_DETAIL = "MFA could not be verified"


PASSWORD_RESET_COMPLETE_TEMPLATE = "password_reset_complete.jinja"


EMAIL_CHANGE_TEMPLATE = "email_change_complete.jinja"


ACTIVATION_TEMPLATE = "activate.jinja"


AUTH_LOGOUT_RESPONSES = {
    status.HTTP_401_UNAUTHORIZED: {"description": "Authentication is required."},
}


PORTAL_LOGOUT_RESPONSES = {
    status.HTTP_403_FORBIDDEN: {"description": "The CSRF token is invalid."},
}


DEV_ADMIN_COMMON_RESPONSES = {
    status.HTTP_400_BAD_REQUEST: {"description": "The staff actor is invalid."},
    status.HTTP_404_NOT_FOUND: {
        "description": "The endpoint is unavailable or the resource was not found."
    },
}


DEV_ADMIN_CONFLICT_RESPONSES = {
    **DEV_ADMIN_COMMON_RESPONSES,
    status.HTTP_409_CONFLICT: {"description": "The requested state transition conflicts."},
}


SECURITY_HEADERS = {
    "Referrer-Policy": "same-origin",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
}


NO_STORE_PATHS = {"/"}


MAX_FORM_BODY_BYTES = 16 * 1024


MAX_JSON_BODY_BYTES = 16 * 1024


MAX_FORM_FIELD_COUNT = 20


DEV_ADMIN_ACTOR_HEADER = "X-CARLOS-Staff-Actor"


CSRF_COOKIE_NAME = "carlos_portal_csrf"


CSRF_COOKIE_PATH = "/auth"


CSRF_FORM_FIELD = "csrf_token"


CSRF_TOKEN_TTL_SECONDS = 60 * 60


CSRF_FUTURE_SKEW_SECONDS = 60


PORTAL_SESSION_COOKIE_NAME = "carlos_portal_session"


PORTAL_SESSION_COOKIE_PATH = PORTAL_ROOT_PATH


PORTAL_MODULES = (
    {"slug": "dashboard", "label_key": "dashboard", "route_name": "portal_dashboard"},
    {"slug": "account", "label_key": "account", "route_name": "portal_account"},
    {
        "slug": "email-passwords",
        "label_key": "email_passwords",
        "route_name": "portal_email_passwords",
    },
    {"slug": "help", "label_key": "help", "route_name": "portal_help"},
)


ACCOUNT_NOTICE_MESSAGE_KEYS = {
    "contact-confirmation-required": "account_status_contact_confirmation_required",
    "contact-updated": "account_status_contact_updated",
    "contact-updated-notice-failed": "account_status_contact_notice_failed",
    "email-confirmation-required": "account_status_email_confirmation_required",
    "email-confirmation-notice-failed": "account_status_email_confirmation_notice_failed",
    "phone-confirmation-required": "account_status_phone_confirmation_required",
    "phone-confirmation-invalid": "account_status_phone_confirmation_invalid",
    "phone-confirmation-notice-failed": "account_status_phone_confirmation_notice_failed",
    "phone-confirmation-rate-limited": "account_status_phone_confirmation_rate_limited",
    "mfa-updated": "account_status_mfa_updated",
    "no-change": "account_status_no_change",
    "password-updated": "account_status_password_updated",
}


VALIDATION_ERROR_PRIVATE_FIELDS = {"ctx", "input"}


# Backslash plus everything at or below U+0020 and the C1 range. Browsers strip TAB/LF/CR while
# parsing a URL and fold backslashes to slashes, so any of these can turn a path that looks local
# into an authority once the browser is done with it.
UNSAFE_REDIRECT_CHARACTER_PATTERN = re.compile(r"[\\\x00-\x20\x7f-\x9f]")


class BrowserFormValidationError(Exception):
    """Raised when a browser form cannot be validated without exposing field details."""

    def __init__(
        self,
        message: str,
        *,
        safe_form_values: dict[str, str] | None = None,
    ) -> None:
        super().__init__(message)
        self.safe_form_values = safe_form_values or {}


def wants_html_response(path: str) -> bool:
    """Whether a patient reached `path` from a browser and should get a page, not a JSON body.

    Rate limiting and maintenance mode are the two states a real patient hits on an ordinary
    browser navigation, so they are the two that most need a readable page. Machine surfaces keep
    their JSON/FHIR shapes: an API client parsing `{"detail": ...}` must not start receiving HTML.
    """
    return not (
        path.startswith("/api/")
        or path.startswith(FHIR_PATH_PREFIX)
        or path.startswith("/internal/")
        or path.startswith("/dev/admin/")
    )


def service_notice_response(
    request: Request,
    *,
    settings: Settings,
    status_code: int,
    heading_key: str,
    message_key: str,
    retry_after_seconds: int,
) -> Response:
    """Render the browser-facing page for a throttled or unavailable portal."""
    locale = request_locale(request)
    text = portal_text(locale)
    return templates.TemplateResponse(
        request=request,
        name="service_notice.jinja",
        context={
            "clinic_name": settings.clinic_name,
            "service_name": settings.service_name,
            "locale": locale,
            "locale_switch_target": locale_switch_targets(request),
            "supported_locales": supported_locale_options(locale),
            "text": text,
            "notice_heading": text[heading_key],
            "notice_message": text[message_key],
        },
        status_code=status_code,
        headers={"Retry-After": str(retry_after_seconds)},
    )


def is_safe_local_redirect(destination: str) -> bool:
    """Whether `destination` is a same-origin path this app may redirect a patient to.

    Deliberately an allowlist of shape rather than a blocklist of known-bad prefixes. An earlier
    version here checked only for a leading `//` after folding backslashes, which let
    `/<TAB>/evil.example` through: browsers strip TAB, LF, and CR while parsing a URL, so that
    value reaches the network stack as `//evil.example` and leaves the origin. A leading space
    behaves the same way in some parsers.

    So: the value must start with a single `/`, contain no backslash and no character at or below
    U+0020 or in the C1 range, and `urlsplit` must see neither a scheme nor an authority. Paths
    this application generates never contain any of those, so nothing legitimate is lost.
    """
    if not destination.startswith("/") or destination.startswith("//"):
        return False
    if UNSAFE_REDIRECT_CHARACTER_PATTERN.search(destination) is not None:
        return False
    parsed_destination = urlsplit(destination)
    return not parsed_destination.scheme and not parsed_destination.netloc


def request_locale(request: Request) -> str:
    """The locale to render this request in."""
    return resolve_locale(
        cookie_value=request.cookies.get(LOCALE_COOKIE_NAME),
        accept_language=request.headers.get("Accept-Language"),
    )


def locale_switch_targets(request: Request) -> str:
    """The path a language button should return the patient to.

    Query string included so a filtered dashboard survives a language change, but never the
    fragment — reset tokens live there and must not travel in a redirect target.
    """
    scope = getattr(request, "scope", {})
    destination = f"{scope.get('root_path', '').rstrip('/')}{request.url.path}"
    if request.url.query:
        destination = f"{destination}?{request.url.query}"
    return destination if is_safe_local_redirect(destination) else "/"


def create_csrf_token(secret: str) -> str:
    issued_at = str(int(time()))
    nonce = token_urlsafe(24)
    message = f"{issued_at}.{nonce}"
    signature = sign_csrf_token(message, secret)
    return f"{message}.{signature}"


def sign_csrf_token(message: str, secret: str) -> str:
    # The same session secret also keys session tokens, MFA codes, and reset tokens, so the signed
    # message carries a purpose prefix exactly like hash_auth_token/hash_mfa_code. Without it, the
    # domain separation between the secret's four roles would rest on the input formats happening
    # never to collide rather than on anything structural.
    return new_hmac(
        secret.encode("utf-8"),
        f"csrf_token:{message}".encode(),
        sha256,
    ).hexdigest()


def is_valid_csrf_token(token: str | None, secret: str) -> bool:
    if token is None:
        return False

    issued_at_value, separator, signed_part = token.partition(".")
    nonce, separator_2, supplied_signature = signed_part.partition(".")
    if not separator or not separator_2 or not nonce or not supplied_signature:
        return False

    try:
        issued_at = int(issued_at_value)
    except ValueError:
        return False

    current_time = int(time())
    if issued_at > current_time + CSRF_FUTURE_SKEW_SECONDS:
        return False
    if current_time - issued_at > CSRF_TOKEN_TTL_SECONDS:
        return False

    expected_signature = sign_csrf_token(f"{issued_at_value}.{nonce}", secret)
    return compare_digest(supplied_signature, expected_signature)


def is_valid_csrf_submission(
    form_token: str | None,
    cookie_token: str | None,
    secret: str,
) -> bool:
    if form_token is None or cookie_token is None:
        return False
    if not compare_digest(form_token, cookie_token):
        return False
    return is_valid_csrf_token(form_token, secret)


def set_csrf_cookie(
    response: Response,
    csrf_token: str,
    *,
    settings: Settings,
    path: str,
) -> None:
    response.set_cookie(
        CSRF_COOKIE_NAME,
        csrf_token,
        httponly=True,
        max_age=CSRF_TOKEN_TTL_SECONDS,
        path=deployment_cookie_path(settings, path),
        samesite="strict",
        secure=not settings.is_development,
    )


def set_portal_session_cookie(
    response: Response,
    session_cookie_value: str,
    *,
    settings: Settings,
) -> None:
    # set_cookie appends rather than replaces, so this composes with the CSRF cookie written on the
    # same response; there is no need to build the header by hand.
    response.set_cookie(
        PORTAL_SESSION_COOKIE_NAME,
        session_cookie_value,
        httponly=True,
        max_age=settings.session_ttl_seconds,
        path=deployment_cookie_path(settings, PORTAL_SESSION_COOKIE_PATH),
        samesite="strict",
        secure=not settings.is_development,
    )


def clear_portal_session_cookie(response: Response, *, settings: Settings) -> None:
    response.delete_cookie(
        PORTAL_SESSION_COOKIE_NAME,
        path=deployment_cookie_path(settings, PORTAL_SESSION_COOKIE_PATH),
        secure=not settings.is_development,
        httponly=True,
        samesite="strict",
    )


def deployment_cookie_path(settings: Settings, application_path: str) -> str:
    """Translate an application route path to the browser-visible deployment path."""
    deployment_path = f"{settings.root_path}{application_path}"
    return deployment_path or "/"


def logout_browser_session_cookie_token(
    session: Session,
    *,
    session_token: str | None,
    session_token_secret: str,
    idle_timeout: timedelta,
) -> None:
    if session_token is None:
        return
    try:
        logout_patient_session(
            session,
            session_token=session_token,
            session_token_secret=session_token_secret,
            idle_timeout=idle_timeout,
        )
    except (PortalSessionInvalidError, ValueError):
        return


def is_portal_path(path: str) -> bool:
    return path == PORTAL_SESSION_COOKIE_PATH or path.startswith(f"{PORTAL_SESSION_COOKIE_PATH}/")


def is_patient_runtime_path(path: str) -> bool:
    return (
        path == "/"
        or path.startswith("/auth/")
        or path.startswith("/api/patient/")
        or path.startswith(FHIR_PATH_PREFIX)
        or is_portal_path(path)
    )


def is_rate_limited_path(path: str) -> bool:
    # /internal/carlos/** is throttled because every failed request there writes an audit row in its
    # own session, so an unauthenticated caller can force unbounded inserts into a table with a
    # 25-year retention floor and bury the real staff-action failures. The reference proxy restricts
    # the prefix by source address, but the application must not depend on the edge being configured
    # correctly for that.
    #
    # The probe endpoints (/internal/health/db, /internal/readiness, /internal/metrics) are
    # deliberately excluded: they are polled on a fixed interval by orchestrators and throttling
    # them would turn a healthy service into a failing liveness check.
    return is_patient_runtime_path(path) or path.startswith("/internal/carlos/")


def is_maintenance_exempt_path(path: str) -> bool:
    return path in {
        "/health",
        "/internal/health/db",
        "/internal/readiness",
        "/internal/metrics",
    }


def is_json_request(request: Request) -> bool:
    return request.headers.get("content-type", "").partition(";")[0].strip().lower() == (
        JSON_MEDIA_TYPE
    )


def is_urlencoded_form_request(request: Request) -> bool:
    return request.headers.get("content-type", "").partition(";")[0].strip().lower() == (
        "application/x-www-form-urlencoded"
    )


def sanitized_validation_errors(exc: RequestValidationError) -> list[dict[str, object]]:
    return [
        {
            field_name: field_value
            for field_name, field_value in error.items()
            if field_name not in VALIDATION_ERROR_PRIVATE_FIELDS
        }
        for error in exc.errors()
    ]


def invite_response_payload(
    invite: PatientPortalInvite,
    invite_token: str | None = None,
) -> dict[str, object]:
    payload: dict[str, object] = {
        "id": invite.id,
        "clinic_id": invite.clinic_id,
        "demographic_no": invite.demographic_no,
        "status": invite.status,
        "created_by": invite.created_by,
        "created_at": invite.created_at,
        "updated_at": invite.updated_at,
        "issued_count": invite.sent_count,
        "last_issued_at": invite.last_sent_at,
        "last_issued_by": invite.last_sent_by,
        "expires_at": invite.expires_at,
        "revoked_at": invite.revoked_at,
        "revoked_by": invite.revoked_by,
        "has_identity_proof": all(
            (
                invite.proof_email_hash,
                invite.proof_date_of_birth_hash,
                invite.proof_health_card_hash,
                invite.proof_salt,
                invite.proof_hash_version,
            )
        ),
        "accepted_at": invite.accepted_at,
        "accepted_account_id": invite.accepted_account_id,
        "supersedes_invite_id": invite.supersedes_invite_id,
    }
    if invite_token is not None:
        payload["invite_token"] = invite_token
    return payload


def account_admin_response_payload(account: PatientPortalAccount) -> dict[str, object]:
    return {
        "id": account.id,
        "clinic_id": account.clinic_id,
        "demographic_no": account.demographic_no,
        "username": account.username,
        "email": account.email,
        "locked_at": account.locked_at,
        "force_password_reset": account.force_password_reset,
        "failed_login_count": account.failed_login_count,
    }


def email_password_record_response_payload(
    unlock_secret: PatientPortalUnlockSecret,
) -> dict[str, object]:
    return {
        "id": unlock_secret.id,
        "label": unlock_secret.label,
        "source_reference": unlock_secret.source_reference,
        "created_at": unlock_secret.created_at,
        "updated_at": unlock_secret.updated_at,
        "last_viewed_at": unlock_secret.last_viewed_at,
    }


def email_password_secret_response_payload(
    unlock_secret: PatientPortalUnlockSecret,
    *,
    passphrase: str,
) -> dict[str, object]:
    return {
        **email_password_record_response_payload(unlock_secret),
        "passphrase": passphrase,
    }


def fhir_operation_outcome_response(
    *,
    status_code: int,
    code: str,
    diagnostics: str,
) -> JSONResponse:
    return fhir_json_response(
        build_fhir_r4_operation_outcome(code=code, diagnostics=diagnostics),
        status_code=status_code,
    )


def mfa_challenge_response_payload(
    delivery: MfaChallengeDelivery,
    *,
    settings: Settings,
) -> dict[str, object]:
    payload: dict[str, object] = {
        "status": "mfa_required",
        "mfa_challenge_token": delivery.challenge_token,
        "mfa_delivery_method": delivery.delivery_method,
    }
    if settings.is_development:
        payload["development_mfa_code"] = delivery.code
    return payload


def login_response_payload(
    result: LoginResult,
    *,
    settings: Settings,
) -> dict[str, object]:
    payload: dict[str, object] = {"status": result.status}
    if result.session_token is not None:
        payload["session_token"] = result.session_token
    if result.mfa_challenge is not None:
        payload.update(mfa_challenge_response_payload(result.mfa_challenge, settings=settings))
    return payload


def password_reset_request_response_payload(
    reset_token: str | None,
    *,
    settings: Settings,
) -> dict[str, object]:
    payload: dict[str, object] = {"status": "reset_requested"}
    if settings.is_development and reset_token is not None:
        payload["development_reset_token"] = reset_token
    return payload


def auth_error_response(
    *,
    is_browser_form: bool,
    request: Request,
    render_index_response: Callable[..., Response],
    status_code: int,
    browser_message: str,
    json_content: dict[str, object],
    headers: dict[str, str] | None = None,
) -> Response:
    if is_browser_form:
        response = render_index_response(
            request,
            status_code=status_code,
            error_message=browser_message,
        )
        if headers:
            response.headers.update(headers)
        return response
    return JSONResponse(status_code=status_code, content=json_content, headers=headers)


def index_template_context(
    request: Request,
    *,
    settings: Settings,
    csrf_token: str,
    error_message: str | None = None,
) -> dict[str, object]:
    locale = request_locale(request)
    return {
        "request": request,
        "locale": locale,
        "clinic_name": settings.clinic_name,
        "csrf_token": csrf_token,
        "error_message": error_message,
        "service_name": settings.service_name,
        "supported_locales": supported_locale_options(locale),
        "locale_switch_target": locale_switch_targets(request),
        "text": portal_text(locale),
    }


def public_auth_template_context(
    request: Request,
    *,
    settings: Settings,
    csrf_token: str,
    error_message: str | None = None,
    notice_message: str | None = None,
    form_values: dict[str, str] | None = None,
    sms_mfa_available: bool | None = None,
    reset_token: str | None = None,
    development_reset_url: str | None = None,
    result_heading: str | None = None,
    result_message: str | None = None,
) -> dict[str, object]:
    # Declared explicitly rather than as **extra_context: these five keys are read by individual
    # public templates, and a kwargs bag let a caller typo one into silence.
    locale = request_locale(request)
    return {
        "request": request,
        "locale": locale,
        "clinic_name": settings.clinic_name,
        "csrf_token": csrf_token,
        "error_message": error_message,
        "form_values": form_values or {},
        "notice_message": notice_message,
        "service_name": settings.service_name,
        "supported_locales": supported_locale_options(locale),
        "locale_switch_target": locale_switch_targets(request),
        "text": portal_text(locale),
        "sms_mfa_available": sms_mfa_available,
        "reset_token": reset_token,
        "development_reset_url": development_reset_url,
        "result_heading": result_heading,
        "result_message": result_message,
    }


def render_public_auth_template(
    request: Request,
    *,
    settings: Settings,
    csrf_secret: str,
    template_name: str,
    status_code: int = status.HTTP_200_OK,
    error_message: str | None = None,
    notice_message: str | None = None,
    form_values: dict[str, str] | None = None,
    sms_mfa_available: bool | None = None,
    reset_token: str | None = None,
    development_reset_url: str | None = None,
    result_heading: str | None = None,
    result_message: str | None = None,
) -> Response:
    csrf_token = create_csrf_token(csrf_secret)
    response = templates.TemplateResponse(
        request=request,
        name=template_name,
        context=public_auth_template_context(
            request,
            settings=settings,
            csrf_token=csrf_token,
            error_message=error_message,
            notice_message=notice_message,
            form_values=form_values,
            sms_mfa_available=sms_mfa_available,
            reset_token=reset_token,
            development_reset_url=development_reset_url,
            result_heading=result_heading,
            result_message=result_message,
        ),
        status_code=status_code,
    )
    set_csrf_cookie(response, csrf_token, settings=settings, path=CSRF_COOKIE_PATH)
    return response


def mfa_template_context(
    request: Request,
    *,
    settings: Settings,
    delivery: MfaChallengeDelivery,
    csrf_token: str,
    error_message: str | None = None,
    notice_message: str | None = None,
) -> dict[str, object]:
    is_email = delivery.delivery_method == MFA_DELIVERY_METHOD_EMAIL
    locale = request_locale(request)
    text = portal_text(locale)
    return {
        "request": request,
        "locale": locale,
        "clinic_name": settings.clinic_name,
        "csrf_token": csrf_token,
        "development_mfa_code": (
            delivery.code if settings.is_development and delivery.code else None
        ),
        "error_message": error_message,
        "notice_message": notice_message,
        "masked_mfa_destination": mask_mfa_destination(delivery),
        "mfa_challenge_token": delivery.challenge_token,
        "mfa_delivery_method": delivery.delivery_method,
        "mfa_email_available": (MFA_DELIVERY_METHOD_EMAIL in delivery.available_delivery_methods),
        "mfa_email_selected": is_email,
        "mfa_rate_limit_message": (text["email_codes_rate"] if is_email else text["mfa_sms_rate"]),
        "mfa_sms_available": (MFA_DELIVERY_METHOD_SMS in delivery.available_delivery_methods),
        "mfa_sms_selected": not is_email,
        "service_name": settings.service_name,
        "text": text,
    }


def mask_mfa_destination(delivery: MfaChallengeDelivery) -> str:
    if delivery.delivery_method == MFA_DELIVERY_METHOD_EMAIL:
        local_part, separator, domain = delivery.destination.partition("@")
        if not separator:
            return "your email address"
        visible_prefix = local_part[:2] if len(local_part) > 1 else local_part[:1]
        return visible_prefix + "***@" + domain

    digits = "".join(character for character in delivery.destination if character.isdigit())
    if len(digits) < 4:
        return "your mobile number"
    return f"***-***-{digits[-4:]}"


def portal_modules(
    request: Request,
    active_module: str,
    text: dict[str, str],
) -> tuple[dict[str, object], ...]:
    return tuple(
        {
            **module,
            "href": request.url_for(str(module["route_name"])).path,
            "label": text[module["label_key"]],
            "is_active": module["slug"] == active_module,
        }
        for module in PORTAL_MODULES
    )


def portal_template_context(
    request: Request,
    *,
    authenticated_session: AuthenticatedPortalSession,
    settings: Settings,
    active_module: str,
    csrf_token: str,
    sms_mfa_available: bool,
    account_notice: str | None = None,
    account_error: str | None = None,
    email_passwords: EmailPasswordDashboardViewModel | None = None,
) -> dict[str, object]:
    account = authenticated_session.account
    locale = request_locale(request)
    text = portal_text(locale)
    context: dict[str, object] = {
        "request": request,
        "locale": locale,
        "service_name": settings.service_name,
        "clinic_name": settings.clinic_name,
        "account": account,
        "password_updated_date": account.password_updated_at.date().isoformat(),
        "active_module": active_module,
        "modules": portal_modules(request, active_module, text),
        "csrf_token": csrf_token,
        "account_notice": account_notice,
        "account_error": account_error,
        "sms_mfa_available": sms_mfa_available and account.phone_number is not None,
        "text": text,
    }
    # The only module with its own view state; typed so a rename is a type error, not a blank page.
    context["email_passwords"] = email_passwords
    return context


def parse_optional_email_password_date(value: str | None) -> date | None:
    if value is None:
        return None
    normalized_value = value.strip()
    if not normalized_value:
        return None
    if len(normalized_value) != 10:
        raise ValueError("date must use YYYY-MM-DD format")
    parsed_date = date.fromisoformat(normalized_value)
    if parsed_date.isoformat() != normalized_value:
        raise ValueError("date must use YYYY-MM-DD format")
    return parsed_date


async def read_limited_request_body(request: Request, max_bytes: int) -> bytes:
    body = bytearray()
    async for chunk in request.stream():
        if len(body) + len(chunk) > max_bytes:
            raise HTTPException(
                status_code=status.HTTP_413_CONTENT_TOO_LARGE,
                detail="request body too large",
            )
        body.extend(chunk)
    return bytes(body)


async def get_urlencoded_form_values(
    request: Request,
    max_body_bytes: int,
    max_fields: int,
) -> dict[str, list[str]]:
    content_type = request.headers.get("content-type", "").partition(";")[0].strip().lower()
    if content_type != "application/x-www-form-urlencoded":
        return {}

    try:
        body = (await read_limited_request_body(request, max_body_bytes)).decode("utf-8")
    except UnicodeDecodeError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="invalid form body",
        ) from exc
    try:
        return parse_qs(
            body,
            keep_blank_values=True,
            max_num_fields=max_fields,
            strict_parsing=True,
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="invalid form body",
        ) from exc


async def get_csrf_urlencoded_form_values(
    request: Request,
    csrf_secret: str,
    *,
    unsupported_media_type_detail: str,
) -> dict[str, list[str]]:
    if not is_urlencoded_form_request(request):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=unsupported_media_type_detail,
        )
    form_values = await get_urlencoded_form_values(
        request,
        MAX_FORM_BODY_BYTES,
        MAX_FORM_FIELD_COUNT,
    )
    if not is_valid_csrf_submission(
        first_form_value(form_values, CSRF_FORM_FIELD),
        request.cookies.get(CSRF_COOKIE_NAME),
        csrf_secret,
    ):
        # This helper is shared by routes that document their own browser/API responses.
        raise HTTPException(status_code=403, detail=INVALID_CSRF_DETAIL)  # NOSONAR
    return form_values


async def get_activation_request(request: Request) -> ActivationRequest:
    content_type = request.headers.get("content-type", "").partition(";")[0].strip().lower()
    if content_type != JSON_MEDIA_TYPE:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="activation requires an application/json request body",
        )
    body = await read_limited_request_body(request, MAX_JSON_BODY_BYTES)
    invite_code = ""
    try:
        raw_payload = json.loads(body)
        if isinstance(raw_payload, dict) and isinstance(raw_payload.get("invite_code"), str):
            invite_code = raw_payload["invite_code"]
    except (json.JSONDecodeError, UnicodeDecodeError):
        pass
    try:
        return ActivationRequest.model_validate_json(body)
    except ValidationError as exc:
        raise RequestValidationError(
            exc.errors(),
            body={"invite_code": invite_code},
        ) from exc


async def get_activation_request_from_request(
    request: Request,
    csrf_secret: str,
) -> ActivationRequest:
    if is_json_request(request):
        return await get_activation_request(request)

    form_values = await get_csrf_urlencoded_form_values(
        request,
        csrf_secret,
        unsupported_media_type_detail=(
            "activation requires an application/json or form request body"
        ),
    )
    password = first_form_value_or_empty(form_values, "password")
    if password != first_form_value_or_empty(form_values, "password_confirmation"):
        raise BrowserFormValidationError(
            "password confirmation does not match",
            safe_form_values={
                "invite_code": first_form_value_or_empty(form_values, "invite_code")
            },
        )
    try:
        return ActivationRequest.model_validate(
            {
                "invite_code": first_form_value(form_values, "invite_code"),
                "email": first_form_value(form_values, "email"),
                "date_of_birth": first_form_value(form_values, "date_of_birth"),
                "health_card_number": first_form_value(form_values, "health_card_number"),
                "username": first_form_value(form_values, "username"),
                "password": password,
                "mfa_delivery_method": first_form_value_or_empty(
                    form_values,
                    "mfa_delivery_method",
                )
                or MFA_DELIVERY_METHOD_EMAIL,
                "phone_number": first_form_value(form_values, "phone_number"),
            }
        )
    except ValidationError as exc:
        raise RequestValidationError(
            exc.errors(),
            body={"invite_code": first_form_value_or_empty(form_values, "invite_code")},
        ) from exc


async def get_mfa_verify_request(request: Request) -> MfaVerifyRequest:
    return await get_json_request_model(
        request,
        MfaVerifyRequest,
        "MFA verification requires an application/json request body",
    )


async def get_mfa_verify_request_from_request(
    request: Request,
    csrf_secret: str,
) -> MfaVerifyRequest:
    if is_json_request(request):
        return await get_mfa_verify_request(request)

    if not is_urlencoded_form_request(request):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="MFA verification requires an application/json or form request body",
        )

    form_values = await get_urlencoded_form_values(
        request,
        MAX_FORM_BODY_BYTES,
        MAX_FORM_FIELD_COUNT,
    )
    csrf_token = first_form_value(form_values, CSRF_FORM_FIELD)
    csrf_cookie = request.cookies.get(CSRF_COOKIE_NAME)
    if not is_valid_csrf_submission(csrf_token, csrf_cookie, csrf_secret):
        # This helper is shared by routes that document their own browser/API responses.
        raise HTTPException(status_code=403, detail=INVALID_CSRF_DETAIL)  # NOSONAR

    try:
        return MfaVerifyRequest.model_validate(
            {
                "mfa_challenge_token": first_form_value(
                    form_values,
                    "mfa_challenge_token",
                ),
                "code": first_form_value(form_values, "code"),
            }
        )
    except ValidationError as exc:
        raise RequestValidationError(exc.errors()) from exc


async def get_mfa_resend_request(request: Request) -> MfaResendRequest:
    return await get_json_request_model(
        request,
        MfaResendRequest,
        "MFA resend requires an application/json request body",
    )


async def get_mfa_resend_request_from_request(
    request: Request,
    csrf_secret: str,
) -> MfaResendRequest:
    if is_json_request(request):
        return await get_mfa_resend_request(request)

    if not is_urlencoded_form_request(request):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="MFA resend requires an application/json or form request body",
        )

    form_values = await get_urlencoded_form_values(
        request,
        MAX_FORM_BODY_BYTES,
        MAX_FORM_FIELD_COUNT,
    )
    csrf_token = first_form_value(form_values, CSRF_FORM_FIELD)
    csrf_cookie = request.cookies.get(CSRF_COOKIE_NAME)
    if not is_valid_csrf_submission(csrf_token, csrf_cookie, csrf_secret):
        # This helper is shared by routes that document their own browser/API responses.
        raise HTTPException(status_code=403, detail=INVALID_CSRF_DETAIL)  # NOSONAR

    try:
        return MfaResendRequest.model_validate(
            {
                "mfa_challenge_token": first_form_value(
                    form_values,
                    "mfa_challenge_token",
                ),
                "mfa_delivery_method": first_form_value(
                    form_values,
                    "mfa_delivery_method",
                ),
            }
        )
    except ValidationError as exc:
        raise RequestValidationError(exc.errors()) from exc


async def get_password_reset_request(request: Request) -> PasswordResetRequest:
    return await get_json_request_model(
        request,
        PasswordResetRequest,
        "password reset request requires an application/json request body",
    )


async def get_password_reset_request_from_request(
    request: Request,
    csrf_secret: str,
) -> PasswordResetRequest:
    if is_json_request(request):
        return await get_password_reset_request(request)

    form_values = await get_csrf_urlencoded_form_values(
        request,
        csrf_secret,
        unsupported_media_type_detail=(
            "password reset request requires an application/json or form request body"
        ),
    )
    try:
        return PasswordResetRequest.model_validate(
            {
                "username": first_form_value(form_values, "username"),
                "email": first_form_value(form_values, "email"),
            }
        )
    except ValidationError as exc:
        raise RequestValidationError(exc.errors()) from exc


async def get_password_reset_complete_request(request: Request) -> PasswordResetCompleteRequest:
    return await get_json_request_model(
        request,
        PasswordResetCompleteRequest,
        "password reset completion requires an application/json request body",
    )


async def get_password_reset_complete_request_from_request(
    request: Request,
    csrf_secret: str,
) -> PasswordResetCompleteRequest:
    if is_json_request(request):
        return await get_password_reset_complete_request(request)

    form_values = await get_csrf_urlencoded_form_values(
        request,
        csrf_secret,
        unsupported_media_type_detail=(
            "password reset completion requires an application/json or form request body"
        ),
    )
    new_password = first_form_value_or_empty(form_values, "new_password")
    reset_token = first_form_value_or_empty(form_values, "reset_token")
    if new_password != first_form_value_or_empty(
        form_values,
        "new_password_confirmation",
    ):
        raise BrowserFormValidationError(
            "password_mismatch",
            safe_form_values={"reset_token": reset_token},
        )
    try:
        return PasswordResetCompleteRequest.model_validate(
            {
                "reset_token": reset_token,
                "new_password": new_password,
            }
        )
    except ValidationError as exc:
        raise BrowserFormValidationError(
            "invalid_password" if reset_token else "invalid_reset_form",
            safe_form_values={"reset_token": reset_token},
        ) from exc


async def get_invite_create_request(request: Request) -> InviteCreateRequest:
    return await get_json_request_model(
        request,
        InviteCreateRequest,
        "invite creation requires an application/json request body",
    )


# PEP 695 function syntax requires Python 3.12; the portal supports Python 3.11.
async def get_json_request_model(  # NOSONAR
    request: Request,
    model_type: type[RequestModel],
    unsupported_media_type_detail: str,
) -> RequestModel:
    content_type = request.headers.get("content-type", "").partition(";")[0].strip().lower()
    if content_type != JSON_MEDIA_TYPE:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=unsupported_media_type_detail,
        )

    body = await read_limited_request_body(request, MAX_JSON_BODY_BYTES)
    try:
        return model_type.model_validate_json(body)
    except ValidationError as exc:
        raise RequestValidationError(exc.errors()) from exc


def first_form_value(form_values: dict[str, list[str]], field_name: str) -> str | None:
    values = form_values.get(field_name)
    if not values:
        return None
    return values[0]


def first_form_value_or_empty(form_values: dict[str, list[str]], field_name: str) -> str:
    return first_form_value(form_values, field_name) or ""


async def get_login_request_from_request(request: Request, csrf_secret: str) -> LoginRequest:
    if is_json_request(request):
        return await get_json_request_model(
            request,
            LoginRequest,
            "login requires an application/json request body",
        )

    if not is_urlencoded_form_request(request):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="login requires an application/json or form request body",
        )

    form_values = await get_urlencoded_form_values(
        request,
        MAX_FORM_BODY_BYTES,
        MAX_FORM_FIELD_COUNT,
    )
    csrf_token = first_form_value(form_values, CSRF_FORM_FIELD)
    csrf_cookie = request.cookies.get(CSRF_COOKIE_NAME)
    if not is_valid_csrf_submission(csrf_token, csrf_cookie, csrf_secret):
        # This helper is shared by routes that document their own browser/API responses.
        raise HTTPException(status_code=403, detail=INVALID_CSRF_DETAIL)  # NOSONAR

    try:
        return LoginRequest.model_validate(
            {
                "username": first_form_value(form_values, "username"),
                "password": first_form_value(form_values, "password"),
                "mfa_delivery_method": first_form_value(form_values, "mfa_delivery_method"),
            }
        )
    except ValidationError as exc:
        raise RequestValidationError(exc.errors()) from exc


def parse_trusted_client_ip_header(
    header_name: str,
    header_value: str | None,
    *,
    peer_address: str | None,
    trusted_proxy_cidrs: str | None,
) -> str | None:
    if not header_value or not peer_address or not trusted_proxy_cidrs:
        return None

    try:
        peer_ip = ip_address(peer_address)
        trusted_networks = tuple(
            ip_network(value.strip(), strict=False)
            for value in trusted_proxy_cidrs.split(",")
            if value.strip()
        )
    except ValueError:
        return None
    if not any(peer_ip in network for network in trusted_networks):
        return None

    try:
        if header_name != "x-forwarded-for":
            return str(ip_address(header_value.strip()))
        forwarded_ips = tuple(
            ip_address(value.strip()) for value in header_value.split(",") if value.strip()
        )
    except ValueError:
        return None
    if not forwarded_ips:
        return None

    for candidate in reversed(forwarded_ips):
        if not any(candidate in network for network in trusted_networks):
            return str(candidate)
    return str(forwarded_ips[0])


def get_request_client_reference(request: Request, settings: Settings) -> str:
    if settings.trusted_client_ip_header is not None:
        trusted_client_reference = parse_trusted_client_ip_header(
            settings.trusted_client_ip_header,
            request.headers.get(settings.trusted_client_ip_header),
            peer_address=request.client.host if request.client is not None else None,
            trusted_proxy_cidrs=settings.trusted_proxy_cidrs,
        )
        if trusted_client_reference is not None:
            return trusted_client_reference

    if request.client is None or not request.client.host:
        return UNKNOWN_CLIENT_REFERENCE
    return request.client.host
