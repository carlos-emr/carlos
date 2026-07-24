from collections.abc import AsyncGenerator, Awaitable, Callable, Generator
from contextlib import asynccontextmanager
from dataclasses import dataclass
from datetime import timedelta
from hashlib import sha256
from hmac import new as new_hmac
from http.cookies import SimpleCookie
from ipaddress import ip_address
from math import ceil
from pathlib import Path as FilePath
from secrets import compare_digest, token_urlsafe
from threading import Lock
from time import monotonic, time
from typing import Annotated, TypeVar
from urllib.parse import parse_qs, urlencode

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, status
from fastapi import Path as PathParam
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from pydantic import BaseModel, ValidationError
from sqlalchemy import Engine
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session, sessionmaker
from starlette.concurrency import run_in_threadpool
from starlette.responses import Response

from carlos_patient_portal.account_settings import (
    AccountSettingsStepUpError,
    AccountSettingsValidationError,
    change_account_password,
    update_account_contact,
    update_account_mfa_method,
)
from carlos_patient_portal.accounts import (
    ActivationError,
    ActivationRateLimit,
    ActivationThrottledError,
    UsernameUnavailableError,
    activate_patient_account,
)
from carlos_patient_portal.audit import (
    UNKNOWN_CLIENT_REFERENCE,
    hash_sensitive_reference,
    record_audit_event,
)
from carlos_patient_portal.auth import (
    AccountLockedError,
    AccountNotFoundError,
    AuthenticatedPortalSession,
    AuthPolicy,
    InvalidCredentialsError,
    InvalidMfaCodeError,
    LoginResult,
    MfaChallengeDelivery,
    MfaChallengeNotFoundError,
    MfaDeliveryUnavailableError,
    MfaRateLimitedError,
    PasswordResetRequiredError,
    PasswordResetTokenInvalidError,
    PortalSessionInvalidError,
    authenticate_session_token,
    complete_password_reset,
    get_mfa_challenge_delivery_state,
    logout_patient_session,
    record_mfa_delivery_outcome,
    request_password_reset,
    resend_mfa_challenge,
    start_login,
    unlock_patient_account,
    verify_mfa_challenge,
)
from carlos_patient_portal.config import Settings, get_settings
from carlos_patient_portal.database import (
    check_database,
    create_portal_engine,
    create_session_factory,
)
from carlos_patient_portal.email_delivery import (
    MfaEmailDeliveryError,
    MfaEmailSender,
    build_mfa_email_sender,
)
from carlos_patient_portal.i18n import DEFAULT_LOCALE, portal_text, supported_locale_options
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.interop import (
    build_fhir_organization_id,
    build_fhir_patient_id,
    build_fhir_practitioner_id,
    build_fhir_r4_bundle,
    build_fhir_r4_capability_statement,
    build_fhir_r4_document_reference,
    build_fhir_r4_operation_outcome,
    build_fhir_r4_organization,
    build_fhir_r4_portal_patient,
    build_fhir_r4_practitioner,
)
from carlos_patient_portal.invites import (
    DEFAULT_INVITE_LIST_LIMIT,
    MAX_INVITE_LIST_LIMIT,
    AcceptedInviteError,
    AccountAlreadyExistsError,
    InviteNotFoundError,
    PendingInviteExistsError,
    RevokedInviteError,
    create_invite,
    list_invites,
    normalize_staff_actor,
    resend_invite,
    revoke_invite,
)
from carlos_patient_portal.models import (
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_ACTOR_TYPE_STAFF,
    AUDIT_EVENT_INVITE_LIST,
    AUDIT_EVENT_UNLOCK_SECRET_LIST,
    AUDIT_EVENT_UNLOCK_SECRET_READ,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    MFA_DELIVERY_METHOD_EMAIL,
    MFA_DELIVERY_METHOD_SMS,
    UNLOCK_SECRET_STATUS_ACTIVE,
    UNLOCK_SECRET_TYPE_EMAIL,
    PatientPortalAccount,
    PatientPortalInvite,
    PatientPortalUnlockSecret,
)
from carlos_patient_portal.schemas import (
    AccountAdminResponse,
    ActivationRequest,
    ActivationResponse,
    EmailPasswordListResponse,
    EmailPasswordSecretResponse,
    InviteCreateRequest,
    InviteResponse,
    InviteTokenResponse,
    LoginRequest,
    LoginResponse,
    LogoutResponse,
    MfaChallengeResponse,
    MfaResendRequest,
    MfaVerifyRequest,
    MfaVerifyResponse,
    PasswordResetCompleteRequest,
    PasswordResetCompleteResponse,
    PasswordResetRequest,
    PasswordResetRequestResponse,
    SessionResponse,
)
from carlos_patient_portal.unlock_secrets import (
    DEFAULT_UNLOCK_SECRET_LIST_LIMIT,
    MAX_UNLOCK_SECRET_LIST_LIMIT,
    MAX_UNLOCK_SECRET_SEARCH_LENGTH,
    UnlockSecretDecryptionError,
    UnlockSecretNotFoundError,
    UnlockSecretRevokedError,
    get_scoped_unlock_secret,
    list_unlock_secrets,
    read_unlock_secret,
)

PACKAGE_DIR = FilePath(__file__).resolve().parent
RequestModel = TypeVar("RequestModel", bound=BaseModel)
templates = Jinja2Templates(directory=str(PACKAGE_DIR / "templates"))
CONTENT_SECURITY_POLICY = (
    "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; "
    "object-src 'none'"
)
FHIR_JSON_MEDIA_TYPE = "application/fhir+json"
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
PORTAL_SESSION_COOKIE_PATH = "/portal"
PORTAL_MODULES = (
    {"slug": "account", "label": "Account", "href": "/portal/account"},
    {
        "slug": "email-passwords",
        "label": "Email passwords",
        "href": "/portal/email-passwords",
    },
    {"slug": "help", "label": "Help", "href": "/portal/help"},
)
EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE = DEFAULT_UNLOCK_SECRET_LIST_LIMIT
ACCOUNT_CHANGE_ERROR_MESSAGE = "Account change could not be completed."
ACCOUNT_NOTICE_MESSAGES = {
    "contact-updated": "Contact update sent for staff review.",
    "mfa-updated": "MFA settings updated.",
    "no-change": "No account changes.",
    "password-updated": "Password updated.",
}
VALIDATION_ERROR_PRIVATE_FIELDS = {"ctx", "input"}


@dataclass
class RateLimitBucket:
    window_started_at: float
    request_count: int


class InMemoryRateLimiter:
    """Small per-process limiter for pilot deployments before shared edge limits exist."""

    def __init__(self, *, window_seconds: int, max_requests: int) -> None:
        self.window_seconds = window_seconds
        self.max_requests = max_requests
        self.buckets: dict[str, RateLimitBucket] = {}
        self.lock = Lock()

    def retry_after_seconds(self, key: str, *, now: float | None = None) -> int | None:
        current_time = now if now is not None else monotonic()
        with self.lock:
            self.prune_expired_buckets(current_time)
            bucket = self.buckets.get(key)
            if bucket is None:
                self.buckets[key] = RateLimitBucket(
                    window_started_at=current_time,
                    request_count=1,
                )
                return None

            elapsed_seconds = current_time - bucket.window_started_at
            if elapsed_seconds >= self.window_seconds:
                bucket.window_started_at = current_time
                bucket.request_count = 1
                return None

            if bucket.request_count >= self.max_requests:
                return max(1, ceil(self.window_seconds - elapsed_seconds))

            bucket.request_count += 1
            return None

    def prune_expired_buckets(self, current_time: float) -> None:
        expired_keys = [
            key
            for key, bucket in self.buckets.items()
            if current_time - bucket.window_started_at >= self.window_seconds
        ]
        for key in expired_keys:
            del self.buckets[key]


class FhirApiError(Exception):
    """Raised when a FHIR endpoint should return an OperationOutcome."""

    def __init__(self, *, status_code: int, code: str, diagnostics: str) -> None:
        super().__init__(diagnostics)
        self.status_code = status_code
        self.code = code
        self.diagnostics = diagnostics


@dataclass(frozen=True)
class PortalRuntime:
    settings: Settings
    database_engine: Engine
    session_factory: sessionmaker[Session]
    csrf_secret: str
    identity_proof_secret: str
    audit_hash_secret: str
    unlock_secret_encryption_secret: str
    activation_rate_limit: ActivationRateLimit
    auth_policy: AuthPolicy
    rate_limiter: InMemoryRateLimiter
    mfa_email_sender: MfaEmailSender | None


@dataclass(frozen=True)
class RouteDependencies:
    get_app_database_session: Callable[..., Generator[Session, None, None]]
    require_internal_health_token: Callable[..., None]
    get_dev_admin_actor: Callable[..., str]
    get_authorization_bearer_token: Callable[..., str]
    get_authenticated_portal_session: Callable[..., AuthenticatedPortalSession]
    get_authenticated_fhir_session: Callable[..., AuthenticatedPortalSession]
    render_index_response: Callable[..., Response]
    render_portal_page: Callable[..., Response]
    get_portal_account_form_values: Callable[..., Awaitable[dict[str, list[str]]]]
    get_portal_cookie_session_or_redirect: Callable[
        [Request, Session],
        AuthenticatedPortalSession | RedirectResponse,
    ]
    render_account_change_error: Callable[..., Response]


def create_csrf_token(secret: str) -> str:
    issued_at = str(int(time()))
    nonce = token_urlsafe(24)
    message = f"{issued_at}.{nonce}"
    signature = sign_csrf_token(message, secret)
    return f"{message}.{signature}"


def sign_csrf_token(message: str, secret: str) -> str:
    return new_hmac(
        secret.encode("utf-8"),
        message.encode("utf-8"),
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
        path=path,
        samesite="strict",
        secure=not settings.is_development,
    )


def set_portal_session_cookie(
    response: Response,
    session_cookie_value: str,
    *,
    settings: Settings,
) -> None:
    portal_cookie = SimpleCookie()
    portal_cookie[PORTAL_SESSION_COOKIE_NAME] = session_cookie_value
    portal_cookie[PORTAL_SESSION_COOKIE_NAME]["httponly"] = True
    portal_cookie[PORTAL_SESSION_COOKIE_NAME]["max-age"] = str(settings.session_ttl_seconds)
    portal_cookie[PORTAL_SESSION_COOKIE_NAME]["path"] = PORTAL_SESSION_COOKIE_PATH
    portal_cookie[PORTAL_SESSION_COOKIE_NAME]["samesite"] = "strict"
    if not settings.is_development:
        portal_cookie[PORTAL_SESSION_COOKIE_NAME]["secure"] = True
    response.headers.append(
        "set-cookie",
        portal_cookie[PORTAL_SESSION_COOKIE_NAME].OutputString(),
    )


def clear_portal_session_cookie(response: Response, *, settings: Settings) -> None:
    response.delete_cookie(
        PORTAL_SESSION_COOKIE_NAME,
        path=PORTAL_SESSION_COOKIE_PATH,
        secure=not settings.is_development,
        httponly=True,
        samesite="strict",
    )


def logout_browser_session_cookie_token(
    session: Session,
    *,
    session_token: str | None,
    token_secret: str,
) -> None:
    if session_token is None:
        return
    try:
        logout_patient_session(
            session,
            session_token=session_token,
            token_secret=token_secret,
        )
    except (PortalSessionInvalidError, ValueError):
        return


def is_portal_path(path: str) -> bool:
    return path == PORTAL_SESSION_COOKIE_PATH or path.startswith(
        f"{PORTAL_SESSION_COOKIE_PATH}/"
    )


def is_patient_runtime_path(path: str) -> bool:
    return (
        path == "/"
        or path.startswith("/auth/")
        or path.startswith("/api/patient/")
        or path.startswith("/fhir/")
        or is_portal_path(path)
    )


def is_rate_limited_path(path: str) -> bool:
    return is_patient_runtime_path(path)


def is_maintenance_exempt_path(path: str) -> bool:
    return path == "/health" or path.startswith("/internal/")


def is_json_request(request: Request) -> bool:
    return request.headers.get("content-type", "").partition(";")[0].strip().lower() == (
        "application/json"
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
        "sent_count": invite.sent_count,
        "last_sent_at": invite.last_sent_at,
        "last_sent_by": invite.last_sent_by,
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


def fhir_json_response(
    payload: dict[str, object],
    *,
    status_code: int = status.HTTP_200_OK,
) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content=payload,
        media_type=FHIR_JSON_MEDIA_TYPE,
    )


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


def fhir_not_found() -> FhirApiError:
    return FhirApiError(
        status_code=status.HTTP_404_NOT_FOUND,
        code="not-found",
        diagnostics="resource not found",
    )


def parse_fhir_numeric_id(resource_id: str) -> int:
    try:
        parsed_id = int(resource_id)
    except ValueError as exc:
        raise fhir_not_found() from exc
    if parsed_id <= 0:
        raise fhir_not_found()
    return parsed_id


def fhir_patient_reference(account: PatientPortalAccount) -> str:
    return f"Patient/{build_fhir_patient_id(account.clinic_id, account.demographic_no)}"


def fhir_base_url(request: Request) -> str:
    return f"{str(request.base_url).rstrip('/')}/fhir"


def auth_policy_from_settings(settings: Settings) -> AuthPolicy:
    return AuthPolicy(
        max_failed_password_attempts=settings.auth_max_failed_password_attempts,
        mfa_max_failed_attempts=settings.mfa_max_failed_attempts,
        session_ttl=timedelta(seconds=settings.session_ttl_seconds),
        mfa_code_ttl=timedelta(seconds=settings.mfa_code_ttl_seconds),
        mfa_email_resend_cooldown=timedelta(
            seconds=settings.mfa_email_resend_cooldown_seconds
        ),
        mfa_sms_resend_cooldown=timedelta(seconds=settings.mfa_sms_resend_cooldown_seconds),
        password_reset_token_ttl=timedelta(seconds=settings.password_reset_token_ttl_seconds),
        require_mfa=settings.require_mfa,
    )


def send_mfa_challenge(runtime: PortalRuntime, delivery: MfaChallengeDelivery) -> None:
    if delivery.delivery_method == MFA_DELIVERY_METHOD_EMAIL:
        if runtime.mfa_email_sender is None:
            if runtime.settings.is_development:
                return
            raise MfaEmailDeliveryError("MFA email delivery is not configured")
        runtime.mfa_email_sender.send_code(
            recipient=delivery.destination,
            code=delivery.code,
            expires_in_seconds=runtime.settings.mfa_code_ttl_seconds,
        )
        return

    if not runtime.settings.is_development:
        raise MfaEmailDeliveryError("MFA delivery is not configured")


def record_mfa_delivery_and_commit(
    session: Session,
    *,
    delivery: MfaChallengeDelivery,
    outcome: str,
) -> None:
    record_mfa_delivery_outcome(session, delivery=delivery, outcome=outcome)
    session.commit()


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
) -> Response:
    if is_browser_form:
        return render_index_response(
            request,
            status_code=status_code,
            error_message=browser_message,
        )
    return JSONResponse(status_code=status_code, content=json_content)


def index_template_context(
    request: Request,
    *,
    settings: Settings,
    csrf_token: str,
    error_message: str | None = None,
) -> dict[str, object]:
    return {
        "request": request,
        "clinic_name": settings.clinic_name,
        "csrf_token": csrf_token,
        "error_message": error_message,
        "service_name": settings.service_name,
        "supported_locales": supported_locale_options(DEFAULT_LOCALE),
        "text": portal_text(DEFAULT_LOCALE),
    }


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
    return {
        "request": request,
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
        "mfa_email_available": (
            MFA_DELIVERY_METHOD_EMAIL in delivery.available_delivery_methods
        ),
        "mfa_email_selected": is_email,
        "mfa_rate_limit_message": (
            "Email codes can be resent once per minute."
            if is_email
            else "SMS codes can be resent once every five minutes."
        ),
        "mfa_sms_available": (
            MFA_DELIVERY_METHOD_SMS in delivery.available_delivery_methods
        ),
        "mfa_sms_selected": not is_email,
        "service_name": settings.service_name,
    }


def mask_mfa_destination(delivery: MfaChallengeDelivery) -> str:
    if delivery.delivery_method == MFA_DELIVERY_METHOD_EMAIL:
        local_part, separator, domain = delivery.destination.partition("@")
        if not separator:
            return "your email address"
        visible_prefix = local_part[:2] if len(local_part) > 1 else local_part[:1]
        return f"{visible_prefix}***@{domain}"

    digits = "".join(character for character in delivery.destination if character.isdigit())
    if len(digits) < 4:
        return "your mobile number"
    return f"***-***-{digits[-4:]}"


def portal_modules(active_module: str) -> tuple[dict[str, object], ...]:
    return tuple(
        {
            **module,
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
    account_notice: str | None = None,
    account_error: str | None = None,
    extra_context: dict[str, object] | None = None,
) -> dict[str, object]:
    account = authenticated_session.account
    context: dict[str, object] = {
        "request": request,
        "service_name": settings.service_name,
        "clinic_name": settings.clinic_name,
        "account": account,
        "password_updated_date": account.password_updated_at.date().isoformat(),
        "active_module": active_module,
        "modules": portal_modules(active_module),
        "csrf_token": csrf_token,
        "account_notice": account_notice,
        "account_error": account_error,
    }
    if extra_context is not None:
        context.update(extra_context)
    return context


def email_password_dashboard_context(
    session: Session,
    account: PatientPortalAccount,
    *,
    search: str | None,
    page: int,
    encryption_secret: str,
) -> dict[str, object]:
    normalized_search = normalize_email_password_dashboard_search(search)
    normalized_page = max(page, 1)
    offset = (normalized_page - 1) * EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE
    records = list_unlock_secrets(
        session,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        secret_type=UNLOCK_SECRET_TYPE_EMAIL,
        search=normalized_search,
        limit=EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE + 1,
        offset=offset,
    )
    visible_records = records[:EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE]
    has_next = len(records) > EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE
    return {
        "rows": [
            email_password_dashboard_row(
                session,
                account,
                record,
                encryption_secret=encryption_secret,
            )
            for record in visible_records
        ],
        "search": normalized_search or "",
        "page": normalized_page,
        "empty_message": (
            "No matching email passwords" if normalized_search else "No email passwords"
        ),
        "previous_href": (
            portal_email_password_page_href(search=normalized_search, page=normalized_page - 1)
            if normalized_page > 1
            else None
        ),
        "next_href": (
            portal_email_password_page_href(search=normalized_search, page=normalized_page + 1)
            if has_next
            else None
        ),
    }


def email_password_dashboard_row(
    session: Session,
    account: PatientPortalAccount,
    unlock_secret: PatientPortalUnlockSecret,
    *,
    encryption_secret: str,
) -> dict[str, object]:
    try:
        passphrase = read_unlock_secret(
            session,
            unlock_secret.id,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            audit_account_id=account.id,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            encryption_secret=encryption_secret,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
        )
    except (UnlockSecretDecryptionError, UnlockSecretNotFoundError, UnlockSecretRevokedError):
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
        passphrase = None

    return {
        "id": unlock_secret.id,
        "subject": unlock_secret.label or "Email password",
        "provider": unlock_secret.created_by,
        "sent_at": unlock_secret.created_at.strftime("%Y-%m-%d %H:%M"),
        "source_reference": unlock_secret.source_reference,
        "passphrase": passphrase,
        "is_available": passphrase is not None,
    }


def normalize_email_password_dashboard_search(search: str | None) -> str | None:
    if search is None:
        return None
    normalized_search = search.strip()
    if not normalized_search:
        return None
    return normalized_search[:MAX_UNLOCK_SECRET_SEARCH_LENGTH]


def portal_email_password_page_href(*, search: str | None, page: int) -> str:
    query_params: dict[str, str] = {}
    normalized_search = normalize_email_password_dashboard_search(search)
    if normalized_search is not None:
        query_params["q"] = normalized_search
    if page > 1:
        query_params["page"] = str(page)
    query_string = urlencode(query_params)
    if not query_string:
        return "/portal/email-passwords"
    return f"/portal/email-passwords?{query_string}"


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


async def get_activation_request(request: Request) -> ActivationRequest:
    return await get_json_request_model(
        request,
        ActivationRequest,
        "activation requires an application/json request body",
    )


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
        raise HTTPException(status_code=403, detail="invalid CSRF token")

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
        raise HTTPException(status_code=403, detail="invalid CSRF token")

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


async def get_password_reset_complete_request(request: Request) -> PasswordResetCompleteRequest:
    return await get_json_request_model(
        request,
        PasswordResetCompleteRequest,
        "password reset completion requires an application/json request body",
    )


async def get_invite_create_request(request: Request) -> InviteCreateRequest:
    return await get_json_request_model(
        request,
        InviteCreateRequest,
        "invite creation requires an application/json request body",
    )


async def get_json_request_model(
    request: Request,
    model_type: type[RequestModel],
    unsupported_media_type_detail: str,
) -> RequestModel:
    content_type = request.headers.get("content-type", "").partition(";")[0].strip().lower()
    if content_type != "application/json":
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
        raise HTTPException(status_code=403, detail="invalid CSRF token")

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


def parse_trusted_client_ip_header(header_name: str, header_value: str | None) -> str | None:
    if not header_value:
        return None

    candidate = (
        header_value.split(",", 1)[0].strip()
        if header_name == "x-forwarded-for"
        else header_value.strip()
    )
    if not candidate:
        return None

    try:
        return str(ip_address(candidate))
    except ValueError:
        return None


def get_request_client_reference(request: Request, settings: Settings) -> str:
    if settings.trusted_client_ip_header is not None:
        trusted_client_reference = parse_trusted_client_ip_header(
            settings.trusted_client_ip_header,
            request.headers.get(settings.trusted_client_ip_header),
        )
        if trusted_client_reference is not None:
            return trusted_client_reference

    if request.client is None or not request.client.host:
        return UNKNOWN_CLIENT_REFERENCE
    return request.client.host


def build_portal_runtime(
    settings: Settings,
    *,
    mfa_email_sender: MfaEmailSender | None = None,
) -> PortalRuntime:
    csrf_secret = (
        settings.session_secret.get_secret_value()
        if settings.session_secret is not None
        else token_urlsafe(32)
    )
    identity_proof_secret = (
        settings.identity_proof_secret.get_secret_value()
        if settings.identity_proof_secret is not None
        else token_urlsafe(32)
    )
    audit_hash_secret = (
        settings.audit_hash_secret.get_secret_value()
        if settings.audit_hash_secret is not None
        else token_urlsafe(32)
    )
    unlock_secret_encryption_secret = (
        settings.unlock_secret_encryption_secret.get_secret_value()
        if settings.unlock_secret_encryption_secret is not None
        else token_urlsafe(32)
    )
    database_engine = create_portal_engine(settings.database_url)
    session_factory = create_session_factory(database_engine)
    activation_rate_limit = ActivationRateLimit(
        failure_window=timedelta(seconds=settings.activation_failure_window_seconds),
        max_failures_per_invite=settings.activation_max_failures_per_invite,
        max_failures_per_client=settings.activation_max_failures_per_client,
    )
    auth_policy = auth_policy_from_settings(settings)
    rate_limiter = InMemoryRateLimiter(
        window_seconds=settings.global_rate_limit_window_seconds,
        max_requests=settings.global_rate_limit_max_requests,
    )
    return PortalRuntime(
        settings=settings,
        database_engine=database_engine,
        session_factory=session_factory,
        csrf_secret=csrf_secret,
        identity_proof_secret=identity_proof_secret,
        audit_hash_secret=audit_hash_secret,
        unlock_secret_encryption_secret=unlock_secret_encryption_secret,
        activation_rate_limit=activation_rate_limit,
        auth_policy=auth_policy,
        rate_limiter=rate_limiter,
        mfa_email_sender=(
            mfa_email_sender
            if mfa_email_sender is not None
            else build_mfa_email_sender(settings)
        ),
    )


def build_lifespan(database_engine: Engine) -> Callable[[FastAPI], AsyncGenerator[None, None]]:
    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncGenerator[None, None]:
        try:
            yield
        finally:
            database_engine.dispose()

    return lifespan


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(
        _: Request,
        exc: RequestValidationError,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=422,
            content={"detail": sanitized_validation_errors(exc)},
        )

    @app.exception_handler(FhirApiError)
    async def fhir_api_error_handler(
        _: Request,
        exc: FhirApiError,
    ) -> JSONResponse:
        return fhir_operation_outcome_response(
            status_code=exc.status_code,
            code=exc.code,
            diagnostics=exc.diagnostics,
        )


def register_security_middleware(app: FastAPI, runtime: PortalRuntime) -> None:
    settings = runtime.settings

    @app.middleware("http")
    async def add_security_headers(
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        if settings.maintenance_mode and not is_maintenance_exempt_path(request.url.path):
            if request.url.path.startswith("/fhir/"):
                response = fhir_operation_outcome_response(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    code="transient",
                    diagnostics="service temporarily unavailable",
                )
            else:
                response = JSONResponse(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    content={"detail": "service temporarily unavailable"},
                )
            response.headers["Retry-After"] = str(settings.maintenance_retry_after_seconds)
        elif is_rate_limited_path(request.url.path):
            retry_after_seconds = runtime.rate_limiter.retry_after_seconds(
                get_request_client_reference(request, settings)
            )
            if retry_after_seconds is None:
                response = await call_next(request)
            elif request.url.path.startswith("/fhir/"):
                response = fhir_operation_outcome_response(
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    code="throttled",
                    diagnostics="too many requests",
                )
                response.headers["Retry-After"] = str(retry_after_seconds)
            else:
                response = JSONResponse(
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    content={"detail": "too many requests"},
                    headers={"Retry-After": str(retry_after_seconds)},
                )
        else:
            response = await call_next(request)
        for header, value in SECURITY_HEADERS.items():
            response.headers.setdefault(header, value)
        if not request.url.path.startswith("/api/"):
            response.headers.setdefault("Content-Security-Policy", CONTENT_SECURITY_POLICY)

        if (
            request.url.path in NO_STORE_PATHS
            or request.url.path.startswith("/auth/")
            or request.url.path.startswith("/api/patient/")
            or request.url.path.startswith("/fhir/")
            or is_portal_path(request.url.path)
            or request.url.path.startswith("/internal/")
            or request.url.path.startswith("/dev/admin/")
        ):
            response.headers.setdefault("Cache-Control", "no-store")
            response.headers.setdefault("Pragma", "no-cache")

        if settings.is_production:
            response.headers.setdefault(
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains",
            )
        return response


def build_route_dependencies(runtime: PortalRuntime) -> RouteDependencies:
    settings = runtime.settings

    def get_app_database_session() -> Generator[Session, None, None]:
        with runtime.session_factory() as session:
            try:
                yield session
            except BaseException:
                session.rollback()
                raise
            else:
                session.commit()

    def require_internal_health_token(
        authorization: Annotated[str | None, Header()] = None,
    ) -> None:
        if settings.internal_health_token is None:
            return

        scheme, _, supplied_token = (authorization or "").partition(" ")
        expected_token = settings.internal_health_token.get_secret_value().strip()
        if scheme.lower() != "bearer" or not compare_digest(supplied_token, expected_token):
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="not found")

    def require_dev_admin_token(
        authorization: Annotated[str | None, Header()] = None,
    ) -> None:
        if settings.dev_admin_token is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="not found")

        scheme, _, supplied_token = (authorization or "").partition(" ")
        expected_token = settings.dev_admin_token.get_secret_value().strip()
        if scheme.lower() != "bearer" or not compare_digest(supplied_token, expected_token):
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="not found")

    def get_dev_admin_actor(
        _: Annotated[None, Depends(require_dev_admin_token)],
        actor_header: Annotated[str | None, Header(alias=DEV_ADMIN_ACTOR_HEADER)] = None,
    ) -> str:
        try:
            return normalize_staff_actor(actor_header or "dev-admin")
        except ValueError as exc:
            raise HTTPException(status_code=400, detail="invalid staff actor") from exc

    def render_index_response(
        request: Request,
        *,
        status_code: int = status.HTTP_200_OK,
        error_message: str | None = None,
    ) -> Response:
        csrf_token = create_csrf_token(runtime.csrf_secret)
        response = templates.TemplateResponse(
            request=request,
            name="index.jinja",
            context=index_template_context(
                request,
                settings=settings,
                csrf_token=csrf_token,
                error_message=error_message,
            ),
            status_code=status_code,
        )
        set_csrf_cookie(response, csrf_token, settings=settings, path=CSRF_COOKIE_PATH)
        return response

    def get_authorization_bearer_token(
        authorization: Annotated[str | None, Header()] = None,
    ) -> str:
        scheme, _, supplied_token = (authorization or "").partition(" ")
        if scheme.lower() != "bearer" or not supplied_token.strip():
            raise HTTPException(status_code=401, detail="authentication required")
        return supplied_token.strip()

    def get_authenticated_portal_session(
        session_token: Annotated[str, Depends(get_authorization_bearer_token)],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> AuthenticatedPortalSession:
        try:
            return authenticate_session_token(
                session,
                session_token=session_token,
                token_secret=runtime.csrf_secret,
            )
        except (PortalSessionInvalidError, ValueError) as exc:
            raise HTTPException(status_code=401, detail="authentication required") from exc

    def get_authenticated_fhir_session(
        session: Annotated[Session, Depends(get_app_database_session)],
        authorization: Annotated[str | None, Header()] = None,
    ) -> AuthenticatedPortalSession:
        scheme, _, supplied_token = (authorization or "").partition(" ")
        if scheme.lower() != "bearer" or not supplied_token.strip():
            raise FhirApiError(
                status_code=status.HTTP_401_UNAUTHORIZED,
                code="login",
                diagnostics="authentication required",
            )
        try:
            return authenticate_session_token(
                session,
                session_token=supplied_token.strip(),
                token_secret=runtime.csrf_secret,
            )
        except (PortalSessionInvalidError, ValueError) as exc:
            raise FhirApiError(
                status_code=status.HTTP_401_UNAUTHORIZED,
                code="login",
                diagnostics="authentication required",
            ) from exc

    def get_authenticated_portal_cookie_session(
        request: Request,
        session: Session,
    ) -> AuthenticatedPortalSession:
        session_token = request.cookies.get(PORTAL_SESSION_COOKIE_NAME)
        if session_token is None:
            raise PortalSessionInvalidError()
        return authenticate_session_token(
            session,
            session_token=session_token,
            token_secret=runtime.csrf_secret,
        )

    def render_portal_page(
        request: Request,
        session: Session,
        *,
        active_module: str,
        status_code: int = status.HTTP_200_OK,
        account_notice: str | None = None,
        account_error: str | None = None,
        email_password_search: str | None = None,
        email_password_page: int = 1,
    ) -> Response:
        try:
            authenticated_session = get_authenticated_portal_cookie_session(request, session)
        except (PortalSessionInvalidError, ValueError):
            response = RedirectResponse("/", status_code=status.HTTP_303_SEE_OTHER)
            clear_portal_session_cookie(response, settings=settings)
            return response

        extra_context: dict[str, object] = {}
        if active_module == "email-passwords":
            extra_context["email_passwords"] = email_password_dashboard_context(
                session,
                authenticated_session.account,
                search=email_password_search,
                page=email_password_page,
                encryption_secret=runtime.unlock_secret_encryption_secret,
            )
        csrf_token = create_csrf_token(runtime.csrf_secret)
        response = templates.TemplateResponse(
            request=request,
            name="dashboard.jinja",
            context=portal_template_context(
                request,
                authenticated_session=authenticated_session,
                settings=settings,
                active_module=active_module,
                csrf_token=csrf_token,
                account_notice=account_notice,
                account_error=account_error,
                extra_context=extra_context,
            ),
            status_code=status_code,
        )
        set_csrf_cookie(
            response,
            csrf_token,
            settings=settings,
            path=PORTAL_SESSION_COOKIE_PATH,
        )
        return response

    async def get_portal_account_form_values(
        request: Request,
        *,
        csrf_error_detail: str,
    ) -> dict[str, list[str]]:
        form_values = await get_urlencoded_form_values(
            request,
            MAX_FORM_BODY_BYTES,
            MAX_FORM_FIELD_COUNT,
        )
        csrf_token = first_form_value(form_values, CSRF_FORM_FIELD)
        csrf_cookie = request.cookies.get(CSRF_COOKIE_NAME)
        if not is_valid_csrf_submission(csrf_token, csrf_cookie, runtime.csrf_secret):
            raise HTTPException(status_code=403, detail=csrf_error_detail)
        return form_values

    def get_portal_cookie_session_or_redirect(
        request: Request,
        session: Session,
    ) -> AuthenticatedPortalSession | RedirectResponse:
        try:
            return get_authenticated_portal_cookie_session(request, session)
        except (PortalSessionInvalidError, ValueError):
            response = RedirectResponse("/", status_code=status.HTTP_303_SEE_OTHER)
            clear_portal_session_cookie(response, settings=settings)
            return response

    def render_account_change_error(
        request: Request,
        session: Session,
        *,
        status_code: int,
    ) -> Response:
        return render_portal_page(
            request,
            session,
            active_module="account",
            status_code=status_code,
            account_error=ACCOUNT_CHANGE_ERROR_MESSAGE,
        )

    return RouteDependencies(
        get_app_database_session=get_app_database_session,
        require_internal_health_token=require_internal_health_token,
        get_dev_admin_actor=get_dev_admin_actor,
        get_authorization_bearer_token=get_authorization_bearer_token,
        get_authenticated_portal_session=get_authenticated_portal_session,
        get_authenticated_fhir_session=get_authenticated_fhir_session,
        render_index_response=render_index_response,
        render_portal_page=render_portal_page,
        get_portal_account_form_values=get_portal_account_form_values,
        get_portal_cookie_session_or_redirect=get_portal_cookie_session_or_redirect,
        render_account_change_error=render_account_change_error,
    )


def create_app(
    settings: Settings | None = None,
    *,
    mfa_email_sender: MfaEmailSender | None = None,
) -> FastAPI:
    settings = settings or get_settings()
    runtime = build_portal_runtime(settings, mfa_email_sender=mfa_email_sender)

    app = FastAPI(
        title=settings.service_name,
        version="0.1.0",
        docs_url="/api/docs" if settings.is_development else None,
        redoc_url="/api/redoc" if settings.is_development else None,
        openapi_url="/api/openapi.json" if settings.is_development else None,
        lifespan=build_lifespan(runtime.database_engine),
    )
    app.state.settings = settings
    app.state.database_engine = runtime.database_engine
    app.state.session_factory = runtime.session_factory
    app.state.rate_limiter = runtime.rate_limiter
    app.state.unlock_secret_encryption_secret = runtime.unlock_secret_encryption_secret
    app.mount(
        "/static",
        StaticFiles(directory=str(PACKAGE_DIR / "static")),
        name="static",
    )
    register_exception_handlers(app)
    register_security_middleware(app, runtime)
    register_app_routes(app, runtime)
    return app


def register_app_routes(app: FastAPI, runtime: PortalRuntime) -> None:
    route_dependencies = build_route_dependencies(runtime)
    register_public_routes(app, runtime, route_dependencies)
    register_auth_routes(app, runtime, route_dependencies)
    register_fhir_routes(app, runtime, route_dependencies)
    register_patient_email_password_routes(app, runtime, route_dependencies)
    register_logout_route(app, runtime, route_dependencies)
    register_portal_routes(app, runtime, route_dependencies)
    register_activation_routes(app, runtime, route_dependencies)
    register_dev_admin_routes(app, runtime, route_dependencies)


def register_public_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    require_internal_health_token = route_dependencies.require_internal_health_token
    render_index_response = route_dependencies.render_index_response

    @app.get("/")
    def index(request: Request) -> Response:
        return render_index_response(request)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/internal/health/db", include_in_schema=False)
    def database_health(
        _: Annotated[None, Depends(require_internal_health_token)],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> dict[str, str]:
        try:
            check_database(session)
        except SQLAlchemyError as exc:
            raise HTTPException(status_code=503, detail="database unavailable") from exc
        return {"status": "ok", "database": "ok"}

    @app.get("/internal/readiness", include_in_schema=False)
    def readiness(
        _: Annotated[None, Depends(require_internal_health_token)],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> JSONResponse:
        try:
            check_database(session)
        except SQLAlchemyError:
            return JSONResponse(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                content={
                    "status": "unavailable",
                    "database": "unavailable",
                    "maintenance": settings.maintenance_mode,
                },
            )

        if settings.maintenance_mode:
            return JSONResponse(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                content={"status": "maintenance", "database": "ok", "maintenance": True},
                headers={"Retry-After": str(settings.maintenance_retry_after_seconds)},
            )
        return JSONResponse(
            content={"status": "ok", "database": "ok", "maintenance": False}
        )


def register_auth_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    get_authenticated_portal_session = (
        route_dependencies.get_authenticated_portal_session
    )
    render_index_response = route_dependencies.render_index_response
    csrf_secret = runtime.csrf_secret
    audit_hash_secret = runtime.audit_hash_secret
    auth_policy = runtime.auth_policy

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
            token_secret=csrf_secret,
            preferred_delivery_method=preferred_delivery_method,
        )

    @app.post("/auth/login", response_model=LoginResponse)
    async def login(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> dict[str, object] | Response:
        is_browser_form = is_urlencoded_form_request(request)
        payload = await get_login_request_from_request(request, csrf_secret)
        client_reference_hash = hash_sensitive_reference(
            audit_hash_secret,
            "login_client",
            get_request_client_reference(request, settings),
        )
        try:
            result = start_login(
                session,
                username=payload.username,
                password=payload.password,
                client_reference_hash=client_reference_hash,
                policy=auth_policy,
                token_secret=csrf_secret,
                mfa_code_secret=csrf_secret,
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
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_423_LOCKED,
                browser_message="Account access is locked; contact the clinic for help.",
                json_content={"detail": "account access is locked; contact the clinic for help"},
            )
        except PasswordResetRequiredError:
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_403_FORBIDDEN,
                browser_message="Password reset is required before sign-in.",
                json_content={"status": "password_reset_required"},
            )
        except MfaDeliveryUnavailableError:
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_400_BAD_REQUEST,
                browser_message="MFA delivery method is unavailable.",
                json_content={"detail": "MFA delivery method is unavailable"},
            )
        if result.mfa_challenge is not None:
            session.commit()
            try:
                await run_in_threadpool(send_mfa_challenge, runtime, result.mfa_challenge)
            except MfaEmailDeliveryError:
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
                    browser_message="Verification code could not be sent. Please try again.",
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
                "/portal",
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
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> dict[str, object] | Response:
        is_browser_form = is_urlencoded_form_request(request)
        payload = await get_mfa_resend_request_from_request(request, csrf_secret)
        try:
            delivery = resend_mfa_challenge(
                session,
                challenge_token=payload.mfa_challenge_token,
                delivery_method=payload.mfa_delivery_method,
                policy=auth_policy,
                token_secret=csrf_secret,
                code_secret=csrf_secret,
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
                            "A code was sent recently. "
                            f"Try again in {exc.retry_after_seconds} seconds."
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
                    error_message="Sign in again to request a new verification code.",
                )
            return JSONResponse(status_code=400, content={"detail": "MFA could not be verified"})
        except AccountLockedError:
            if is_browser_form:
                return render_index_response(
                    request,
                    status_code=status.HTTP_423_LOCKED,
                    error_message="Account access is locked; contact the clinic for help.",
                )
            return JSONResponse(
                status_code=status.HTTP_423_LOCKED,
                content={"detail": "account access is locked; contact the clinic for help"},
            )
        except PasswordResetRequiredError:
            if is_browser_form:
                return render_index_response(
                    request,
                    status_code=status.HTTP_403_FORBIDDEN,
                    error_message="Password reset is required before sign-in.",
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
                        error_message="That delivery method is unavailable.",
                    )
            return JSONResponse(
                status_code=400,
                content={"detail": "MFA delivery method is unavailable"},
            )
        session.commit()
        try:
            await run_in_threadpool(send_mfa_challenge, runtime, delivery)
        except MfaEmailDeliveryError:
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
                        error_message="Verification code could not be sent. Please try again.",
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
                notice_message=(
                    f"A new code was sent by {delivery.delivery_method.upper()}."
                ),
            )
        return mfa_challenge_response_payload(delivery, settings=settings)

    @app.post("/auth/mfa/verify", response_model=MfaVerifyResponse)
    async def verify_mfa(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> dict[str, str] | Response:
        is_browser_form = is_urlencoded_form_request(request)
        payload = await get_mfa_verify_request_from_request(request, csrf_secret)
        try:
            session_token = verify_mfa_challenge(
                session,
                challenge_token=payload.mfa_challenge_token,
                code=payload.code,
                policy=auth_policy,
                token_secret=csrf_secret,
                code_secret=csrf_secret,
            )
        except InvalidMfaCodeError:
            if is_browser_form:
                delivery_state = get_browser_mfa_delivery_state(session, payload)
                if delivery_state is not None:
                    return render_mfa_page(
                        request,
                        delivery_state,
                        status_code=status.HTTP_401_UNAUTHORIZED,
                        error_message="The code was not accepted. Try again or request a new code.",
                    )
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_401_UNAUTHORIZED,
                browser_message="MFA could not be verified.",
                json_content={"detail": "MFA could not be verified"},
            )
        except (MfaChallengeNotFoundError, ValueError):
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_400_BAD_REQUEST,
                browser_message="MFA could not be verified.",
                json_content={"detail": "MFA could not be verified"},
            )
        except AccountLockedError:
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_423_LOCKED,
                browser_message="Account access is locked; contact the clinic for help.",
                json_content={"detail": "account access is locked; contact the clinic for help"},
            )
        except PasswordResetRequiredError:
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_403_FORBIDDEN,
                browser_message="Password reset is required before sign-in.",
                json_content={"status": "password_reset_required"},
            )
        if is_browser_form:
            redirect_response = RedirectResponse(
                "/portal",
                status_code=status.HTTP_303_SEE_OTHER,
            )
            set_portal_session_cookie(redirect_response, session_token, settings=settings)
            return redirect_response
        return {"status": "signed_in", "session_token": session_token}

    @app.post(
        "/auth/password-reset/request",
        response_model=PasswordResetRequestResponse,
        status_code=status.HTTP_202_ACCEPTED,
    )
    def request_reset(
        request: Request,
        payload: Annotated[PasswordResetRequest, Depends(get_password_reset_request)],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> dict[str, object]:
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
            token_secret=csrf_secret,
        )
        return password_reset_request_response_payload(result.reset_token, settings=settings)

    @app.post("/auth/password-reset/complete", response_model=PasswordResetCompleteResponse)
    def complete_reset(
        payload: Annotated[
            PasswordResetCompleteRequest,
            Depends(get_password_reset_complete_request),
        ],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> dict[str, str] | JSONResponse:
        try:
            account = complete_password_reset(
                session,
                reset_token=payload.reset_token,
                new_password=payload.new_password,
                token_secret=csrf_secret,
            )
        except PasswordResetTokenInvalidError:
            return JSONResponse(
                status_code=400,
                content={"detail": "password reset could not be completed"},
            )
        return {"status": "password_reset", "username": account.username}

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


def register_fhir_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    get_authenticated_fhir_session = route_dependencies.get_authenticated_fhir_session

    @app.get("/fhir/metadata")
    def fhir_metadata(request: Request) -> JSONResponse:
        return fhir_json_response(
            build_fhir_r4_capability_statement(
                service_name=settings.service_name,
                base_url=fhir_base_url(request),
            ),
        )

    @app.get("/fhir/Patient")
    def fhir_patient_search(
        request: Request,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
    ) -> JSONResponse:
        patient = build_fhir_r4_portal_patient(authenticated_session.account)
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=[patient],
                base_url=fhir_base_url(request),
                self_link=str(request.url),
            )
        )

    @app.get("/fhir/Patient/{patient_id}")
    def fhir_patient_read(
        patient_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
    ) -> JSONResponse:
        account = authenticated_session.account
        expected_patient_id = build_fhir_patient_id(account.clinic_id, account.demographic_no)
        if patient_id != expected_patient_id:
            raise fhir_not_found()
        return fhir_json_response(build_fhir_r4_portal_patient(account))

    @app.get("/fhir/DocumentReference")
    def fhir_document_reference_search(
        request: Request,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        records = list_unlock_secrets(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            limit=MAX_UNLOCK_SECRET_LIST_LIMIT,
        )
        resources = [
            build_fhir_r4_document_reference(
                record,
                patient_reference=fhir_patient_reference(account),
            )
            for record in records
        ]
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=resources,
                base_url=fhir_base_url(request),
                self_link=str(request.url),
            )
        )

    @app.get("/fhir/DocumentReference/{document_reference_id}")
    def fhir_document_reference_read(
        document_reference_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        record_id = parse_fhir_numeric_id(document_reference_id)
        try:
            record = get_scoped_unlock_secret(
                session,
                record_id,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            )
        except UnlockSecretNotFoundError as exc:
            raise fhir_not_found() from exc
        if record.status != UNLOCK_SECRET_STATUS_ACTIVE:
            raise fhir_not_found()
        return fhir_json_response(
            build_fhir_r4_document_reference(
                record,
                patient_reference=fhir_patient_reference(account),
            )
        )

    @app.get("/fhir/Organization")
    def fhir_organization_search(
        request: Request,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
    ) -> JSONResponse:
        account = authenticated_session.account
        organization = build_fhir_r4_organization(
            clinic_id=account.clinic_id,
            clinic_name=settings.clinic_name,
        )
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=[organization],
                base_url=fhir_base_url(request),
                self_link=str(request.url),
            )
        )

    @app.get("/fhir/Organization/{organization_id}")
    def fhir_organization_read(
        organization_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
    ) -> JSONResponse:
        account = authenticated_session.account
        if organization_id != build_fhir_organization_id(account.clinic_id):
            raise fhir_not_found()
        return fhir_json_response(
            build_fhir_r4_organization(
                clinic_id=account.clinic_id,
                clinic_name=settings.clinic_name,
            )
        )

    @app.get("/fhir/Practitioner")
    def fhir_practitioner_search(
        request: Request,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        records = list_unlock_secrets(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            limit=MAX_UNLOCK_SECRET_LIST_LIMIT,
        )
        resources = [
            build_fhir_r4_practitioner(clinic_id=account.clinic_id, name=name)
            for name in sorted({record.created_by for record in records})
        ]
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=resources,
                base_url=fhir_base_url(request),
                self_link=str(request.url),
            )
        )

    @app.get("/fhir/Practitioner/{practitioner_id}")
    def fhir_practitioner_read(
        practitioner_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        records = list_unlock_secrets(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            limit=MAX_UNLOCK_SECRET_LIST_LIMIT,
        )
        for name in sorted({record.created_by for record in records}):
            if practitioner_id == build_fhir_practitioner_id(
                clinic_id=account.clinic_id,
                name=name,
            ):
                return fhir_json_response(
                    build_fhir_r4_practitioner(clinic_id=account.clinic_id, name=name)
                )
        raise fhir_not_found()


def register_patient_email_password_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    get_app_database_session = route_dependencies.get_app_database_session
    get_authenticated_portal_session = route_dependencies.get_authenticated_portal_session
    unlock_secret_encryption_secret = runtime.unlock_secret_encryption_secret

    @app.get("/api/patient/email-passwords", response_model=EmailPasswordListResponse)
    def list_patient_email_passwords(
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_portal_session),
        ],
        session: Annotated[Session, Depends(get_app_database_session)],
        limit: Annotated[
            int,
            Query(ge=1, le=MAX_UNLOCK_SECRET_LIST_LIMIT),
        ] = DEFAULT_UNLOCK_SECRET_LIST_LIMIT,
        offset: Annotated[int, Query(ge=0)] = 0,
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
        email_password_id: Annotated[int, PathParam(gt=0)],
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_portal_session),
        ],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> Response | dict[str, object]:
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
                encryption_secret=unlock_secret_encryption_secret,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            )
            unlock_secret = get_scoped_unlock_secret(
                session,
                email_password_id,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            )
        except (UnlockSecretNotFoundError, UnlockSecretRevokedError):
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
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                content={"detail": "email password unavailable"},
            )
        return email_password_secret_response_payload(unlock_secret, passphrase=passphrase)


def register_logout_route(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    get_authorization_bearer_token = route_dependencies.get_authorization_bearer_token
    csrf_secret = runtime.csrf_secret

    @app.post("/auth/logout", response_model=LogoutResponse)
    def logout(
        session_token: Annotated[str, Depends(get_authorization_bearer_token)],
        response: Response,
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> dict[str, str]:
        try:
            logout_patient_session(
                session,
                session_token=session_token,
                token_secret=csrf_secret,
            )
        except (PortalSessionInvalidError, ValueError) as exc:
            raise HTTPException(status_code=401, detail="authentication required") from exc
        clear_portal_session_cookie(response, settings=settings)
        return {"status": "logged_out"}


def register_portal_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    render_portal_page = route_dependencies.render_portal_page
    get_portal_account_form_values = route_dependencies.get_portal_account_form_values
    get_portal_cookie_session_or_redirect = (
        route_dependencies.get_portal_cookie_session_or_redirect
    )
    render_account_change_error = route_dependencies.render_account_change_error
    csrf_secret = runtime.csrf_secret

    @app.get("/portal")
    def portal_dashboard(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> Response:
        return render_portal_page(request, session, active_module="account")

    @app.get("/portal/account")
    def portal_account(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
        account_status: Annotated[
            str | None,
            Query(alias="status", max_length=32),
        ] = None,
    ) -> Response:
        return render_portal_page(
            request,
            session,
            active_module="account",
            account_notice=ACCOUNT_NOTICE_MESSAGES.get(account_status or ""),
        )

    @app.post("/portal/account/password")
    async def portal_account_password(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> Response:
        form_values = await get_portal_account_form_values(
            request,
            csrf_error_detail="password change could not be completed",
        )
        authenticated_session = get_portal_cookie_session_or_redirect(request, session)
        if isinstance(authenticated_session, RedirectResponse):
            return authenticated_session

        try:
            change_account_password(
                session,
                authenticated_session.account,
                authenticated_session.portal_session,
                current_password=first_form_value_or_empty(form_values, "current_password"),
                new_password=first_form_value_or_empty(form_values, "new_password"),
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
            "/portal/account?status=password-updated",
            status_code=status.HTTP_303_SEE_OTHER,
        )

    @app.post("/portal/account/contact")
    async def portal_account_contact(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> Response:
        form_values = await get_portal_account_form_values(
            request,
            csrf_error_detail="contact update could not be completed",
        )
        authenticated_session = get_portal_cookie_session_or_redirect(request, session)
        if isinstance(authenticated_session, RedirectResponse):
            return authenticated_session

        try:
            review_request = update_account_contact(
                session,
                authenticated_session.account,
                current_password=first_form_value_or_empty(form_values, "current_password"),
                email=first_form_value_or_empty(form_values, "email"),
                phone_number=first_form_value(form_values, "phone_number"),
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
        status_key = "contact-updated" if review_request is not None else "no-change"
        return RedirectResponse(
            f"/portal/account?status={status_key}",
            status_code=status.HTTP_303_SEE_OTHER,
        )

    @app.post("/portal/account/mfa")
    async def portal_account_mfa(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> Response:
        form_values = await get_portal_account_form_values(
            request,
            csrf_error_detail="MFA update could not be completed",
        )
        authenticated_session = get_portal_cookie_session_or_redirect(request, session)
        if isinstance(authenticated_session, RedirectResponse):
            return authenticated_session

        try:
            update_account_mfa_method(
                session,
                authenticated_session.account,
                current_password=first_form_value_or_empty(form_values, "current_password"),
                preferred_mfa_method=first_form_value_or_empty(
                    form_values,
                    "preferred_mfa_method",
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
        return RedirectResponse(
            "/portal/account?status=mfa-updated",
            status_code=status.HTTP_303_SEE_OTHER,
        )

    @app.get("/portal/email-passwords")
    def portal_email_passwords(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
        q: Annotated[str | None, Query(max_length=MAX_UNLOCK_SECRET_SEARCH_LENGTH)] = None,
        page: Annotated[int, Query(ge=1)] = 1,
    ) -> Response:
        return render_portal_page(
            request,
            session,
            active_module="email-passwords",
            email_password_search=q,
            email_password_page=page,
        )

    @app.get("/portal/help")
    def portal_help(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> Response:
        return render_portal_page(request, session, active_module="help")

    @app.post("/portal/logout")
    async def portal_logout(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> Response:
        form_values = await get_urlencoded_form_values(
            request,
            MAX_FORM_BODY_BYTES,
            MAX_FORM_FIELD_COUNT,
        )
        csrf_token = first_form_value(form_values, CSRF_FORM_FIELD)
        csrf_cookie = request.cookies.get(CSRF_COOKIE_NAME)
        response = RedirectResponse("/", status_code=status.HTTP_303_SEE_OTHER)
        if not is_valid_csrf_submission(csrf_token, csrf_cookie, csrf_secret):
            raise HTTPException(status_code=403, detail="logout could not be completed")

        logout_browser_session_cookie_token(
            session,
            session_token=request.cookies.get(PORTAL_SESSION_COOKIE_NAME),
            token_secret=csrf_secret,
        )
        clear_portal_session_cookie(response, settings=settings)
        return response


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

    @app.post(
        "/auth/activate",
        response_model=ActivationResponse,
        status_code=status.HTTP_201_CREATED,
    )
    def activate_invite(
        request: Request,
        payload: Annotated[ActivationRequest, Depends(get_activation_request)],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> dict[str, str] | JSONResponse:
        client_reference_hash = hash_sensitive_reference(
            audit_hash_secret,
            "activation_client",
            get_request_client_reference(request, settings),
        )
        try:
            account = activate_patient_account(
                session,
                invite_code=payload.invite_code,
                identity_proof=IdentityProof(
                    email=payload.email,
                    date_of_birth=payload.date_of_birth,
                    health_card_number=payload.health_card_number,
                ),
                username=payload.username,
                password=payload.password,
                proof_secret=identity_proof_secret,
                client_reference_hash=client_reference_hash,
                rate_limit=activation_rate_limit,
            )
        except UsernameUnavailableError:
            return JSONResponse(status_code=409, content={"detail": "username unavailable"})
        except ActivationThrottledError as exc:
            return JSONResponse(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                content={"detail": "too many activation attempts; try again later"},
                headers={"Retry-After": str(exc.retry_after_seconds)},
            )
        except ActivationError:
            return JSONResponse(
                status_code=400,
                content={"detail": "activation details could not be verified"},
            )
        return {"status": "activated", "username": account.username}


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
        )
        def dev_create_invite(
            actor: Annotated[str, Depends(get_dev_admin_actor)],
            payload: Annotated[InviteCreateRequest, Depends(get_invite_create_request)],
            session: Annotated[Session, Depends(get_app_database_session)],
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

        @app.get("/dev/admin/invites", response_model=list[InviteResponse])
        def dev_list_invites(
            actor: Annotated[str, Depends(get_dev_admin_actor)],
            session: Annotated[Session, Depends(get_app_database_session)],
            demographic_no: Annotated[int | None, Query(gt=0)] = None,
            limit: Annotated[
                int,
                Query(ge=1, le=MAX_INVITE_LIST_LIMIT),
            ] = DEFAULT_INVITE_LIST_LIMIT,
            offset: Annotated[int, Query(ge=0)] = 0,
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
            return [
                invite_response_payload(invite)
                for invite in invites
            ]

        @app.post("/dev/admin/invites/{invite_id}/resend", response_model=InviteTokenResponse)
        def dev_resend_invite(
            invite_id: Annotated[int, PathParam(gt=0)],
            actor: Annotated[str, Depends(get_dev_admin_actor)],
            session: Annotated[Session, Depends(get_app_database_session)],
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
            return invite_response_payload(invite, invite_token)

        @app.post("/dev/admin/invites/{invite_id}/revoke", response_model=InviteResponse)
        def dev_revoke_invite(
            invite_id: Annotated[int, PathParam(gt=0)],
            actor: Annotated[str, Depends(get_dev_admin_actor)],
            session: Annotated[Session, Depends(get_app_database_session)],
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
            return invite_response_payload(invite)

        @app.post(
            "/dev/admin/accounts/{account_id}/unlock",
            response_model=AccountAdminResponse,
        )
        def dev_unlock_account(
            account_id: Annotated[int, PathParam(gt=0)],
            actor: Annotated[str, Depends(get_dev_admin_actor)],
            session: Annotated[Session, Depends(get_app_database_session)],
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
