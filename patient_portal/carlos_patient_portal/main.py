import json
import logging
from collections.abc import AsyncGenerator, Awaitable, Callable, Generator
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from datetime import UTC, date, datetime, timedelta
from datetime import time as datetime_time
from hashlib import sha256
from hmac import new as new_hmac
from http.cookies import SimpleCookie
from ipaddress import ip_address, ip_network
from math import ceil
from pathlib import Path as FilePath
from secrets import compare_digest, token_urlsafe
from threading import Lock
from time import monotonic, time
from typing import Annotated, TypeVar
from urllib.parse import parse_qs, quote, urlencode

from fastapi import BackgroundTasks, Depends, FastAPI, Header, HTTPException, Query, Request, status
from fastapi import Path as PathParam
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from jinja2 import Environment, FileSystemLoader
from pydantic import BaseModel, ValidationError
from sqlalchemy import Engine
from sqlalchemy.exc import OperationalError, SQLAlchemyError
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
    PasswordResetRequestResult,
    PasswordResetRequiredError,
    PasswordResetTokenInvalidError,
    PortalSessionInvalidError,
    authenticate_session_token,
    complete_password_reset,
    get_mfa_challenge_delivery_state,
    logout_patient_session,
    record_mfa_delivery_outcome,
    record_password_reset_delivery_outcome,
    request_password_reset,
    resend_mfa_challenge,
    start_login,
    unlock_patient_account,
    verify_mfa_challenge,
)
from carlos_patient_portal.config import Settings, get_settings
from carlos_patient_portal.database import (
    DatabaseSchemaMismatchError,
    check_database,
    check_database_schema_current,
    create_portal_engine,
    create_session_factory,
)
from carlos_patient_portal.email_delivery import (
    PortalEmailDeliveryError,
    PortalEmailSender,
    build_portal_email_sender,
)
from carlos_patient_portal.i18n import (
    DEFAULT_LOCALE,
    format_portal_datetime,
    portal_text,
    supported_locale_options,
)
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.internal_routes import register_carlos_internal_routes
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
    AUDIT_EVENT_FHIR_READ,
    AUDIT_EVENT_FHIR_SEARCH,
    AUDIT_EVENT_INVITE_LIST,
    AUDIT_EVENT_UNLOCK_SECRET_LIST,
    AUDIT_EVENT_UNLOCK_SECRET_READ,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    MAX_UNLOCK_SECRET_ACTOR_LENGTH,
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
from carlos_patient_portal.sms_delivery import (
    PortalSmsDeliveryError,
    PortalSmsSender,
    build_portal_sms_sender,
)
from carlos_patient_portal.unlock_secrets import (
    DEFAULT_UNLOCK_SECRET_LIST_LIMIT,
    MAX_UNLOCK_SECRET_LIST_LIMIT,
    MAX_UNLOCK_SECRET_SEARCH_LENGTH,
    UnlockSecretDecryptionError,
    UnlockSecretNotFoundError,
    UnlockSecretRevokedError,
    count_unlock_secret_providers,
    count_unlock_secrets,
    get_scoped_unlock_secret,
    list_unlock_secret_provider_identities,
    list_unlock_secret_providers,
    list_unlock_secrets,
    read_unlock_secret,
)

PACKAGE_DIR = FilePath(__file__).resolve().parent
RequestModel = TypeVar("RequestModel", bound=BaseModel)
templates = Jinja2Templates(
    env=Environment(
        loader=FileSystemLoader(str(PACKAGE_DIR / "templates")),
        autoescape=True,
    )
)
logger = logging.getLogger(__name__)
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
    {"slug": "dashboard", "label_key": "dashboard", "href": "/portal"},
    {"slug": "account", "label_key": "account", "href": "/portal/account"},
    {
        "slug": "email-passwords",
        "label_key": "email_passwords",
        "href": "/portal/email-passwords",
    },
    {"slug": "help", "label_key": "help", "href": "/portal/help"},
)
EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE = DEFAULT_UNLOCK_SECRET_LIST_LIMIT
ACCOUNT_NOTICE_MESSAGE_KEYS = {
    "contact-updated": "account_status_contact_updated",
    "mfa-updated": "account_status_mfa_updated",
    "no-change": "account_status_no_change",
    "password-updated": "account_status_password_updated",
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


@dataclass
class PortalOperationalMetrics:
    _lock: Lock = field(default_factory=Lock)
    _request_counts: dict[str, int] = field(default_factory=dict)
    _failure_counts: dict[str, int] = field(default_factory=dict)
    _request_duration_ms_total: int = 0

    def record_request(self, status_code: int, duration_ms: int) -> None:
        status_group = f"{status_code // 100}xx"
        with self._lock:
            self._request_counts[status_group] = self._request_counts.get(status_group, 0) + 1
            self._request_duration_ms_total += max(0, duration_ms)

    def record_failure(self, failure_type: str) -> None:
        with self._lock:
            self._failure_counts[failure_type] = self._failure_counts.get(failure_type, 0) + 1

    def snapshot(self) -> dict[str, object]:
        with self._lock:
            return {
                "requests": dict(self._request_counts),
                "failures": dict(self._failure_counts),
                "request_duration_ms_total": self._request_duration_ms_total,
            }


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
    email_sender: PortalEmailSender | None
    sms_sender: PortalSmsSender | None
    unlock_secret_encryption_keys: dict[str, str] | None = None
    unlock_secret_active_key_id: str = "primary"
    operational_metrics: PortalOperationalMetrics = field(
        default_factory=PortalOperationalMetrics
    )


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


def function_scoped_database_dependency(
    dependency: Callable[[], Generator[Session, None, None]],
) -> object:
    # FastAPI supports dependency teardown before response delivery with scope="function".
    return Depends(dependency, scope="function")  # NOSONAR - Sonar's FastAPI stub lacks scope.


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


def fhir_base_url(settings: Settings, request: Request) -> str:
    if settings.public_base_url is not None:
        return f"{settings.public_base_url.rstrip('/')}/fhir"
    return f"{str(request.base_url).rstrip('/')}/fhir"


def fhir_search_url(
    *,
    base_url: str,
    resource_type: str,
    count: int,
    offset: int,
    resource_id: str | None = None,
    subject: str | None = None,
) -> str:
    query: list[tuple[str, str]] = [
        ("_count", str(count)),
        ("_offset", str(offset)),
    ]
    if resource_id is not None:
        query.append(("_id", resource_id))
    if subject is not None:
        query.append(("subject", subject))
    return f"{base_url.rstrip('/')}/{resource_type}?{urlencode(query)}"


def fhir_bundle_links(
    *,
    base_url: str,
    resource_type: str,
    count: int,
    offset: int,
    total: int,
    resource_id: str | None = None,
    subject: str | None = None,
) -> tuple[str, str | None, str | None]:
    build_link = lambda page_offset: fhir_search_url(  # noqa: E731
        base_url=base_url,
        resource_type=resource_type,
        count=count,
        offset=page_offset,
        resource_id=resource_id,
        subject=subject,
    )
    self_link = build_link(offset)
    previous_link = build_link(max(0, offset - count)) if offset > 0 else None
    next_link = build_link(offset + count) if offset + count < total else None
    return self_link, previous_link, next_link


def record_fhir_access(
    session: Session,
    account: PatientPortalAccount,
    *,
    event_type: str,
    resource_type: str,
    resource_id: str | None,
    outcome: str,
    reason: str,
) -> None:
    record_audit_event(
        session,
        event_type=event_type,
        outcome=outcome,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        actor_id=str(account.id),
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        resource_type=resource_type,
        resource_id=resource_id,
        reason=reason,
    )


def find_fhir_practitioner(
    session: Session,
    account: PatientPortalAccount,
    practitioner_id: str,
) -> tuple[str | None, str] | None:
    total = count_unlock_secret_providers(
        session,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        secret_type=UNLOCK_SECRET_TYPE_EMAIL,
    )
    for provider_offset in range(0, total, MAX_UNLOCK_SECRET_LIST_LIMIT):
        provider_rows = list_unlock_secret_provider_identities(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            limit=MAX_UNLOCK_SECRET_LIST_LIMIT,
            offset=provider_offset,
        )
        for provider_id, name in provider_rows:
            if practitioner_id == build_fhir_practitioner_id(
                clinic_id=account.clinic_id,
                name=name,
                provider_id=provider_id,
            ):
                return provider_id, name
    return None


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
        password_reset_request_cooldown=timedelta(
            seconds=settings.password_reset_request_cooldown_seconds
        ),
        require_mfa=settings.require_mfa,
    )


def send_mfa_challenge(runtime: PortalRuntime, delivery: MfaChallengeDelivery) -> None:
    if delivery.delivery_method == MFA_DELIVERY_METHOD_EMAIL:
        if runtime.email_sender is None:
            if runtime.settings.is_development:
                return
            raise PortalEmailDeliveryError("MFA email delivery is not configured")
        runtime.email_sender.send_code(
            recipient=delivery.destination,
            code=delivery.code,
            expires_in_seconds=runtime.settings.mfa_code_ttl_seconds,
        )
        return

    if runtime.sms_sender is None:
        if runtime.settings.is_development:
            return
        raise PortalSmsDeliveryError("MFA SMS delivery is not configured")
    runtime.sms_sender.send_code(
        recipient=delivery.destination,
        code=delivery.code,
        expires_in_seconds=runtime.settings.mfa_code_ttl_seconds,
    )


def build_password_reset_url(
    request: Request,
    *,
    settings: Settings,
    reset_token: str,
) -> str:
    if settings.public_base_url is not None:
        base_url = settings.public_base_url.rstrip("/")
    elif settings.is_development:
        base_url = str(request.base_url).rstrip("/")
    else:
        raise PortalEmailDeliveryError("password reset email delivery is not configured")
    encoded_token = quote(reset_token, safe="")
    return base_url + "/auth/password-reset/complete#token=" + encoded_token


def send_password_reset_email(
    runtime: PortalRuntime,
    *,
    recipient: str,
    reset_url: str,
) -> None:
    if runtime.email_sender is None:
        if runtime.settings.is_development:
            return
        raise PortalEmailDeliveryError("password reset email delivery is not configured")
    runtime.email_sender.send_password_reset(
        recipient=recipient,
        reset_url=reset_url,
        expires_in_seconds=runtime.settings.password_reset_token_ttl_seconds,
    )


def deliver_password_reset(
    runtime: PortalRuntime,
    *,
    result: PasswordResetRequestResult,
    reset_url: str,
) -> None:
    outcome = AUDIT_OUTCOME_SUCCESS
    try:
        send_password_reset_email(
            runtime,
            recipient=result.recipient or "",
            reset_url=reset_url,
        )
    except PortalEmailDeliveryError as exc:
        outcome = AUDIT_OUTCOME_FAILURE
        runtime.operational_metrics.record_failure("password_reset_delivery")
        logger.error("Password reset email delivery failed: %s", type(exc).__name__)
    try:
        with runtime.session_factory() as session:
            with session.begin():
                record_password_reset_delivery_outcome(
                    session,
                    result=result,
                    outcome=outcome,
                )
    except (PasswordResetTokenInvalidError, SQLAlchemyError) as exc:
        logger.error(
            "Password reset delivery outcome persistence failed: %s",
            type(exc).__name__,
        )


def send_contact_change_notice(runtime: PortalRuntime, *, recipient: str) -> None:
    if runtime.email_sender is None:
        if runtime.settings.is_development:
            return
        raise PortalEmailDeliveryError("contact-change email delivery is not configured")
    sender = getattr(runtime.email_sender, "send_contact_change_notice", None)
    if sender is None:
        if runtime.settings.is_development:
            return
        raise PortalEmailDeliveryError("contact-change email delivery is not configured")
    sender(recipient=recipient)


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


def public_auth_template_context(
    request: Request,
    *,
    settings: Settings,
    csrf_token: str,
    error_message: str | None = None,
    notice_message: str | None = None,
    form_values: dict[str, str] | None = None,
    **extra_context: object,
) -> dict[str, object]:
    return {
        "request": request,
        "clinic_name": settings.clinic_name,
        "csrf_token": csrf_token,
        "error_message": error_message,
        "form_values": form_values or {},
        "notice_message": notice_message,
        "service_name": settings.service_name,
        "supported_locales": supported_locale_options(DEFAULT_LOCALE),
        "text": portal_text(DEFAULT_LOCALE),
        **extra_context,
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
    **extra_context: object,
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
            **extra_context,
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
    text = portal_text(DEFAULT_LOCALE)
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
            text["email_codes_rate"] if is_email else text["mfa_sms_rate"]
        ),
        "mfa_sms_available": (
            MFA_DELIVERY_METHOD_SMS in delivery.available_delivery_methods
        ),
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
    active_module: str,
    text: dict[str, str],
) -> tuple[dict[str, object], ...]:
    return tuple(
        {
            **module,
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
    account_notice: str | None = None,
    account_error: str | None = None,
    extra_context: dict[str, object] | None = None,
) -> dict[str, object]:
    account = authenticated_session.account
    text = portal_text(DEFAULT_LOCALE)
    context: dict[str, object] = {
        "request": request,
        "service_name": settings.service_name,
        "clinic_name": settings.clinic_name,
        "account": account,
        "password_updated_date": account.password_updated_at.date().isoformat(),
        "active_module": active_module,
        "modules": portal_modules(active_module, text),
        "csrf_token": csrf_token,
        "account_notice": account_notice,
        "account_error": account_error,
        "text": text,
    }
    if extra_context is not None:
        context.update(extra_context)
    return context


def email_password_dashboard_context(
    session: Session,
    account: PatientPortalAccount,
    *,
    search: str | None,
    provider: str | None,
    date_from: date | None,
    date_to: date | None,
    page: int,
    filter_error: str | None = None,
) -> dict[str, object]:
    text = portal_text(DEFAULT_LOCALE)
    normalized_search = normalize_email_password_dashboard_search(search)
    normalized_provider = normalize_email_password_dashboard_provider(provider)
    invalid_date_range = (
        date_from is not None
        and date_to is not None
        and date_from > date_to
    )
    has_filter_error = filter_error is not None or invalid_date_range
    created_from = (
        datetime.combine(date_from, datetime_time.min, tzinfo=UTC)
        if date_from is not None
        else None
    )
    created_before = (
        (
            datetime.max.replace(tzinfo=UTC)
            if date_to == date.max
            else datetime.combine(
                date_to + timedelta(days=1),
                datetime_time.min,
                tzinfo=UTC,
            )
        )
        if date_to is not None
        else None
    )
    total_records = (
        0
        if has_filter_error
        else count_unlock_secrets(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            search=normalized_search,
            provider=normalized_provider,
            created_from=created_from,
            created_before=created_before,
        )
    )
    total_pages = max(
        1,
        (total_records + EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE - 1)
        // EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE,
    )
    normalized_page = min(max(page, 1), total_pages)
    offset = (normalized_page - 1) * EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE
    records = (
        []
        if has_filter_error
        else list_unlock_secrets(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            search=normalized_search,
            provider=normalized_provider,
            created_from=created_from,
            created_before=created_before,
            limit=EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE,
            offset=offset,
        )
    )
    return {
        "rows": [
            email_password_dashboard_row(record, text=text)
            for record in records
        ],
        "search": normalized_search or "",
        "provider": normalized_provider or "",
        "provider_options": list_unlock_secret_providers(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
        ),
        "date_from": date_from.isoformat() if date_from is not None else "",
        "date_to": date_to.isoformat() if date_to is not None else "",
        "has_filters": any(
            (
                normalized_search,
                normalized_provider,
                date_from,
                date_to,
            )
        ),
        "filter_error": (
            filter_error
            or (text["date_range_error"] if invalid_date_range else None)
        ),
        "page": normalized_page,
        "total_pages": total_pages,
        "empty_message": (
            text["no_matching_email_passwords"]
            if any((normalized_search, normalized_provider, date_from, date_to))
            else text["no_email_passwords"]
        ),
        "previous_href": (
            portal_email_password_page_href(
                search=normalized_search,
                provider=normalized_provider,
                date_from=date_from,
                date_to=date_to,
                page=normalized_page - 1,
            )
            if normalized_page > 1
            else None
        ),
        "next_href": (
            portal_email_password_page_href(
                search=normalized_search,
                provider=normalized_provider,
                date_from=date_from,
                date_to=date_to,
                page=normalized_page + 1,
            )
            if normalized_page < total_pages
            else None
        ),
    }


def email_password_dashboard_row(
    unlock_secret: PatientPortalUnlockSecret,
    *,
    text: dict[str, str],
) -> dict[str, object]:
    return {
        "id": unlock_secret.id,
        "subject": unlock_secret.label or text["email_password"],
        "provider": unlock_secret.created_by,
        "sent_at": format_portal_datetime(unlock_secret.created_at),
        "source_reference": unlock_secret.source_reference,
        "is_available": unlock_secret.status == UNLOCK_SECRET_STATUS_ACTIVE,
    }


def normalize_email_password_dashboard_search(search: str | None) -> str | None:
    if search is None:
        return None
    normalized_search = search.strip()
    if not normalized_search:
        return None
    return normalized_search[:MAX_UNLOCK_SECRET_SEARCH_LENGTH]


def normalize_email_password_dashboard_provider(provider: str | None) -> str | None:
    if provider is None:
        return None
    normalized_provider = provider.strip()
    return normalized_provider or None


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


def portal_email_password_page_href(
    *,
    search: str | None,
    provider: str | None,
    date_from: date | None,
    date_to: date | None,
    page: int,
) -> str:
    query_params: dict[str, str] = {}
    normalized_search = normalize_email_password_dashboard_search(search)
    if normalized_search is not None:
        query_params["q"] = normalized_search
    normalized_provider = normalize_email_password_dashboard_provider(provider)
    if normalized_provider is not None:
        query_params["provider"] = normalized_provider
    if date_from is not None:
        query_params["date_from"] = date_from.isoformat()
    if date_to is not None:
        query_params["date_to"] = date_to.isoformat()
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
        raise HTTPException(status_code=403, detail="invalid CSRF token")
    return form_values


async def get_activation_request(request: Request) -> ActivationRequest:
    return await get_json_request_model(
        request,
        ActivationRequest,
        "activation requires an application/json request body",
    )


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
        raise BrowserFormValidationError("password confirmation does not match")
    try:
        return ActivationRequest.model_validate(
            {
                "invite_code": first_form_value(form_values, "invite_code"),
                "email": first_form_value(form_values, "email"),
                "date_of_birth": first_form_value(form_values, "date_of_birth"),
                "health_card_number": first_form_value(form_values, "health_card_number"),
                "username": first_form_value(form_values, "username"),
                "password": password,
            }
        )
    except ValidationError as exc:
        raise RequestValidationError(exc.errors()) from exc


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


def build_portal_runtime(
    settings: Settings,
    *,
    email_sender: PortalEmailSender | None = None,
    sms_sender: PortalSmsSender | None = None,
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
    unlock_secret_encryption_keys = settings.resolved_unlock_secret_keyring
    if not unlock_secret_encryption_keys:
        unlock_secret_encryption_keys = {"primary": token_urlsafe(32)}
    unlock_secret_active_key_id = settings.unlock_secret_active_key_id
    unlock_secret_encryption_secret = unlock_secret_encryption_keys[
        unlock_secret_active_key_id
    ]
    database_engine = create_portal_engine(
        settings.database_url,
        pool_size=settings.database_pool_size,
        max_overflow=settings.database_max_overflow,
        pool_timeout_seconds=settings.database_pool_timeout_seconds,
        connect_timeout_seconds=settings.database_connect_timeout_seconds,
        statement_timeout_ms=settings.database_statement_timeout_ms,
        lock_timeout_ms=settings.database_lock_timeout_ms,
        sqlite_busy_timeout_ms=settings.sqlite_busy_timeout_ms,
    )
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
        unlock_secret_encryption_keys=unlock_secret_encryption_keys,
        unlock_secret_active_key_id=unlock_secret_active_key_id,
        activation_rate_limit=activation_rate_limit,
        auth_policy=auth_policy,
        rate_limiter=rate_limiter,
        email_sender=(
            email_sender
            if email_sender is not None
            else build_portal_email_sender(settings)
        ),
        sms_sender=(
            sms_sender
            if sms_sender is not None
            else build_portal_sms_sender(settings)
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

    @app.exception_handler(OperationalError)
    async def database_operational_error_handler(
        request: Request,
        exc: OperationalError,
    ) -> JSONResponse:
        request.app.state.operational_metrics.record_failure("database")
        logger.warning("Portal database operation unavailable: %s", type(exc.orig).__name__)
        if request.url.path.startswith("/fhir/"):
            return fhir_operation_outcome_response(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                code="transient",
                diagnostics="service temporarily unavailable",
            )
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"detail": "service temporarily unavailable"},
            headers={"Retry-After": "1"},
        )


def register_security_middleware(app: FastAPI, runtime: PortalRuntime) -> None:
    settings = runtime.settings

    @app.middleware("http")
    async def record_operational_signal(
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        supplied_request_id = request.headers.get("X-Request-ID", "")
        request_id = (
            supplied_request_id
            if 1 <= len(supplied_request_id) <= 64
            and all(character.isalnum() or character in "._-" for character in supplied_request_id)
            else token_urlsafe(12)
        )
        started_at = monotonic()
        status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
        try:
            response = await call_next(request)
            status_code = response.status_code
            response.headers["X-Request-ID"] = request_id
            return response
        finally:
            duration_ms = round((monotonic() - started_at) * 1000)
            runtime.operational_metrics.record_request(status_code, duration_ms)
            route = request.scope.get("route")
            route_path = getattr(route, "path", "unmatched")
            logger.info(
                "%s",
                json.dumps(
                    {
                        "event": "http_request",
                        "request_id": request_id,
                        "method": request.method,
                        "route": route_path,
                        "status": status_code,
                        "duration_ms": duration_ms,
                    },
                    separators=(",", ":"),
                    sort_keys=True,
                ),
            )

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
                session.commit()
            except BaseException:
                session.rollback()
                raise

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
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
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
        request: Request,
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        authorization: Annotated[str | None, Header()] = None,
    ) -> AuthenticatedPortalSession:
        scheme, _, supplied_token = (authorization or "").partition(" ")
        if scheme.lower() != "bearer" or not supplied_token.strip():
            record_audit_event(
                session,
                event_type=AUDIT_EVENT_FHIR_SEARCH,
                outcome=AUDIT_OUTCOME_FAILURE,
                actor_type=AUDIT_ACTOR_TYPE_PATIENT,
                client_reference_hash=hash_sensitive_reference(
                    runtime.audit_hash_secret,
                    "fhir_client",
                    get_request_client_reference(request, settings),
                ),
                resource_type=request.url.path.split("/")[2][:64],
                reason="authentication_failed",
            )
            session.commit()
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
            record_audit_event(
                session,
                event_type=AUDIT_EVENT_FHIR_SEARCH,
                outcome=AUDIT_OUTCOME_FAILURE,
                actor_type=AUDIT_ACTOR_TYPE_PATIENT,
                client_reference_hash=hash_sensitive_reference(
                    runtime.audit_hash_secret,
                    "fhir_client",
                    get_request_client_reference(request, settings),
                ),
                resource_type=request.url.path.split("/")[2][:64],
                reason="authentication_failed",
            )
            session.commit()
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
        email_password_provider: str | None = None,
        email_password_date_from: date | None = None,
        email_password_date_to: date | None = None,
        email_password_page: int = 1,
        email_password_filter_error: str | None = None,
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
                provider=email_password_provider,
                date_from=email_password_date_from,
                date_to=email_password_date_to,
                page=email_password_page,
                filter_error=email_password_filter_error,
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
            account_error=portal_text(DEFAULT_LOCALE)["account_change_error"],
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
    email_sender: PortalEmailSender | None = None,
    sms_sender: PortalSmsSender | None = None,
) -> FastAPI:
    settings = settings or get_settings()
    runtime = build_portal_runtime(
        settings,
        email_sender=email_sender,
        sms_sender=sms_sender,
    )

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
    app.state.operational_metrics = runtime.operational_metrics
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
    register_carlos_internal_routes(app, runtime)


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
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> dict[str, str]:
        try:
            check_database(session)
        except SQLAlchemyError as exc:
            raise HTTPException(status_code=503, detail="database unavailable") from exc
        return {"status": "ok", "database": "ok"}

    @app.get("/internal/readiness", include_in_schema=False)
    def readiness(
        _: Annotated[None, Depends(require_internal_health_token)],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> JSONResponse:
        try:
            check_database(session)
        except SQLAlchemyError:
            return JSONResponse(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                content={
                    "status": "unavailable",
                    "database": "unavailable",
                    "schema": "unknown",
                    "maintenance": settings.maintenance_mode,
                },
            )
        try:
            check_database_schema_current(session)
        except (DatabaseSchemaMismatchError, SQLAlchemyError):
            return JSONResponse(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                content={
                    "status": "unavailable",
                    "database": "ok",
                    "schema": "mismatch",
                    "maintenance": settings.maintenance_mode,
                },
            )

        if settings.maintenance_mode:
            return JSONResponse(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                content={
                    "status": "maintenance",
                    "database": "ok",
                    "schema": "ok",
                    "maintenance": True,
                },
                headers={"Retry-After": str(settings.maintenance_retry_after_seconds)},
            )
        return JSONResponse(
            content={
                "status": "ok",
                "database": "ok",
                "schema": "ok",
                "maintenance": False,
            }
        )

    @app.get("/internal/metrics", include_in_schema=False)
    def operational_metrics(
        _: Annotated[None, Depends(require_internal_health_token)],
    ) -> dict[str, object]:
        return runtime.operational_metrics.snapshot()


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
            token_secret=csrf_secret,
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
            if is_browser_form:
                return render_locked_page(request)
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_423_LOCKED,
                browser_message=text["session_locked_details"],
                json_content={"detail": "account access is locked; contact the clinic for help"},
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
                json_content={"detail": "MFA delivery method is unavailable"},
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
            return JSONResponse(status_code=400, content={"detail": "MFA could not be verified"})
        except AccountLockedError:
            if is_browser_form:
                return render_locked_page(request)
            return JSONResponse(
                status_code=status.HTTP_423_LOCKED,
                content={"detail": "account access is locked; contact the clinic for help"},
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
                content={"detail": "MFA delivery method is unavailable"},
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
                        error_message=text["incorrect_mfa_code"],
                    )
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_401_UNAUTHORIZED,
                browser_message=text["mfa_verification_failed"],
                json_content={"detail": "MFA could not be verified"},
            )
        except (MfaChallengeNotFoundError, ValueError):
            return auth_error_response(
                is_browser_form=is_browser_form,
                request=request,
                render_index_response=render_index_response,
                status_code=status.HTTP_400_BAD_REQUEST,
                browser_message=text["mfa_verification_failed"],
                json_content={"detail": "MFA could not be verified"},
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
                json_content={"detail": "account access is locked; contact the clinic for help"},
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
                "/portal",
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
            template_name="password_reset_complete.jinja",
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
            token_secret=csrf_secret,
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
                    logger.error(
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
            return render_public_auth_template(
                request,
                settings=settings,
                csrf_secret=csrf_secret,
                template_name="password_reset_complete.jinja",
                status_code=status.HTTP_400_BAD_REQUEST,
                error_message=(
                    text["password_mismatch"]
                    if str(exc) == "password_mismatch"
                    else (
                        text["password_invalid"]
                        if str(exc) == "invalid_password"
                        else text["password_reset_complete_error"]
                    )
                ),
                reset_token=exc.safe_form_values.get("reset_token"),
            )
        try:
            account = await run_in_threadpool(
                complete_password_reset,
                session,
                reset_token=payload.reset_token,
                new_password=payload.new_password,
                token_secret=csrf_secret,
            )
        except PasswordResetTokenInvalidError:
            if is_browser_form:
                return render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name="password_reset_complete.jinja",
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
                base_url=fhir_base_url(settings, request),
            ),
        )

    @app.get("/fhir/Patient")
    def fhir_patient_search(
        request: Request,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        resource_id: Annotated[str | None, Query(alias="_id", max_length=64)] = None,
        count: Annotated[int, Query(alias="_count", ge=1, le=100)] = 20,
        offset: Annotated[int, Query(alias="_offset", ge=0)] = 0,
    ) -> JSONResponse:
        account = authenticated_session.account
        patient = build_fhir_r4_portal_patient(account)
        matches = resource_id is None or resource_id == patient["id"]
        resources = [patient] if matches and offset == 0 else []
        total = 1 if matches else 0
        base_url = fhir_base_url(settings, request)
        self_link, previous_link, next_link = fhir_bundle_links(
            base_url=base_url,
            resource_type="Patient",
            count=count,
            offset=offset,
            total=total,
            resource_id=resource_id,
        )
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_SEARCH,
            resource_type="Patient",
            resource_id=str(patient["id"]),
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="search",
        )
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=resources,
                base_url=base_url,
                self_link=self_link,
                previous_link=previous_link,
                next_link=next_link,
                total=total,
            )
        )

    @app.get("/fhir/Patient/{patient_id}")
    def fhir_patient_read(
        patient_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        expected_patient_id = build_fhir_patient_id(account.clinic_id, account.demographic_no)
        if patient_id != expected_patient_id:
            record_fhir_access(
                session,
                account,
                event_type=AUDIT_EVENT_FHIR_READ,
                resource_type="Patient",
                resource_id=patient_id,
                outcome=AUDIT_OUTCOME_FAILURE,
                reason="not_found",
            )
            session.commit()
            raise fhir_not_found()
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_READ,
            resource_type="Patient",
            resource_id=patient_id,
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="read",
        )
        return fhir_json_response(build_fhir_r4_portal_patient(account))

    @app.get("/fhir/DocumentReference")
    def fhir_document_reference_search(
        request: Request,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        resource_id: Annotated[str | None, Query(alias="_id", max_length=64)] = None,
        subject: Annotated[str | None, Query(max_length=128)] = None,
        count: Annotated[int, Query(alias="_count", ge=1, le=100)] = 20,
        offset: Annotated[int, Query(alias="_offset", ge=0)] = 0,
    ) -> JSONResponse:
        account = authenticated_session.account
        patient_reference = fhir_patient_reference(account)
        subject_matches = subject is None or subject.removeprefix("Patient/") == (
            patient_reference.removeprefix("Patient/")
        )
        records: list[PatientPortalUnlockSecret] = []
        total = 0
        if subject_matches and resource_id is not None:
            try:
                record = get_scoped_unlock_secret(
                    session,
                    parse_fhir_numeric_id(resource_id),
                    clinic_id=account.clinic_id,
                    demographic_no=account.demographic_no,
                    secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                )
                if record.status == UNLOCK_SECRET_STATUS_ACTIVE:
                    total = 1
                    if offset == 0:
                        records = [record]
            except (FhirApiError, UnlockSecretNotFoundError):
                pass
        elif subject_matches:
            total = count_unlock_secrets(
                session,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            )
            records = list_unlock_secrets(
                session,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                limit=count,
                offset=offset,
            )
        resources = [
            build_fhir_r4_document_reference(
                record,
                patient_reference=patient_reference,
            )
            for record in records
        ]
        base_url = fhir_base_url(settings, request)
        self_link, previous_link, next_link = fhir_bundle_links(
            base_url=base_url,
            resource_type="DocumentReference",
            count=count,
            offset=offset,
            total=total,
            resource_id=resource_id,
            subject=subject,
        )
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_SEARCH,
            resource_type="DocumentReference",
            resource_id=resource_id or patient_reference,
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="search",
        )
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=resources,
                base_url=base_url,
                self_link=self_link,
                previous_link=previous_link,
                next_link=next_link,
                total=total,
            )
        )

    @app.get("/fhir/DocumentReference/{document_reference_id}")
    def fhir_document_reference_read(
        document_reference_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
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
            record_fhir_access(
                session,
                account,
                event_type=AUDIT_EVENT_FHIR_READ,
                resource_type="DocumentReference",
                resource_id=document_reference_id,
                outcome=AUDIT_OUTCOME_FAILURE,
                reason="not_found",
            )
            session.commit()
            raise fhir_not_found() from exc
        if record.status != UNLOCK_SECRET_STATUS_ACTIVE:
            record_fhir_access(
                session,
                account,
                event_type=AUDIT_EVENT_FHIR_READ,
                resource_type="DocumentReference",
                resource_id=document_reference_id,
                outcome=AUDIT_OUTCOME_FAILURE,
                reason="not_found",
            )
            session.commit()
            raise fhir_not_found()
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_READ,
            resource_type="DocumentReference",
            resource_id=document_reference_id,
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="read",
        )
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
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        resource_id: Annotated[str | None, Query(alias="_id", max_length=64)] = None,
        count: Annotated[int, Query(alias="_count", ge=1, le=100)] = 20,
        offset: Annotated[int, Query(alias="_offset", ge=0)] = 0,
    ) -> JSONResponse:
        account = authenticated_session.account
        organization = build_fhir_r4_organization(
            clinic_id=account.clinic_id,
            clinic_name=settings.clinic_name,
        )
        matches = resource_id is None or resource_id == organization["id"]
        total = 1 if matches else 0
        resources = [organization] if matches and offset == 0 else []
        base_url = fhir_base_url(settings, request)
        self_link, previous_link, next_link = fhir_bundle_links(
            base_url=base_url,
            resource_type="Organization",
            count=count,
            offset=offset,
            total=total,
            resource_id=resource_id,
        )
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_SEARCH,
            resource_type="Organization",
            resource_id=str(organization["id"]),
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="search",
        )
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=resources,
                base_url=base_url,
                self_link=self_link,
                previous_link=previous_link,
                next_link=next_link,
                total=total,
            )
        )

    @app.get("/fhir/Organization/{organization_id}")
    def fhir_organization_read(
        organization_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        if organization_id != build_fhir_organization_id(account.clinic_id):
            record_fhir_access(
                session,
                account,
                event_type=AUDIT_EVENT_FHIR_READ,
                resource_type="Organization",
                resource_id=organization_id,
                outcome=AUDIT_OUTCOME_FAILURE,
                reason="not_found",
            )
            session.commit()
            raise fhir_not_found()
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_READ,
            resource_type="Organization",
            resource_id=organization_id,
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="read",
        )
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
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        resource_id: Annotated[str | None, Query(alias="_id", max_length=64)] = None,
        count: Annotated[int, Query(alias="_count", ge=1, le=100)] = 20,
        offset: Annotated[int, Query(alias="_offset", ge=0)] = 0,
    ) -> JSONResponse:
        account = authenticated_session.account
        total = count_unlock_secret_providers(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
        )
        if resource_id is None:
            provider_rows = list_unlock_secret_provider_identities(
                session,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                limit=count,
                offset=offset,
            )
        else:
            provider = find_fhir_practitioner(session, account, resource_id)
            provider_rows = [provider] if provider is not None and offset == 0 else []
            total = 1 if provider is not None else 0
        resources = []
        for provider_id, name in provider_rows:
            practitioner = build_fhir_r4_practitioner(
                clinic_id=account.clinic_id,
                name=name,
                provider_id=provider_id,
            )
            if resource_id is None or practitioner["id"] == resource_id:
                resources.append(practitioner)
        base_url = fhir_base_url(settings, request)
        self_link, previous_link, next_link = fhir_bundle_links(
            base_url=base_url,
            resource_type="Practitioner",
            count=count,
            offset=offset,
            total=total,
            resource_id=resource_id,
        )
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_SEARCH,
            resource_type="Practitioner",
            resource_id=resource_id or fhir_patient_reference(account),
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="search",
        )
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=resources,
                base_url=base_url,
                self_link=self_link,
                previous_link=previous_link,
                next_link=next_link,
                total=total,
            )
        )

    @app.get("/fhir/Practitioner/{practitioner_id}")
    def fhir_practitioner_read(
        practitioner_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        provider = find_fhir_practitioner(session, account, practitioner_id)
        if provider is not None:
            provider_id, name = provider
            record_fhir_access(
                session,
                account,
                event_type=AUDIT_EVENT_FHIR_READ,
                resource_type="Practitioner",
                resource_id=practitioner_id,
                outcome=AUDIT_OUTCOME_SUCCESS,
                reason="read",
            )
            return fhir_json_response(
                build_fhir_r4_practitioner(
                    clinic_id=account.clinic_id,
                    name=name,
                    provider_id=provider_id,
                )
            )
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_READ,
            resource_type="Practitioner",
            resource_id=practitioner_id,
            outcome=AUDIT_OUTCOME_FAILURE,
            reason="not_found",
        )
        session.commit()
        raise fhir_not_found()


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
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
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
                encryption_keys=(
                    runtime.unlock_secret_encryption_keys
                    or {"primary": runtime.unlock_secret_encryption_secret}
                ),
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
            runtime.operational_metrics.record_failure("unlock_secret_decryption")
            record_audit_event(
                session,
                event_type=AUDIT_EVENT_UNLOCK_SECRET_READ,
                outcome=AUDIT_OUTCOME_FAILURE,
                actor_type=AUDIT_ACTOR_TYPE_PATIENT,
                actor=account.username,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                account_id=account.id,
                reason="decryption_failed",
            )
            logger.error("Unlock-secret decryption failed")
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
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
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

        try:
            await run_in_threadpool(
                change_account_password,
                session,
                authenticated_session.account,
                authenticated_session.portal_session,
                current_password=first_form_value_or_empty(form_values, "current_password"),
                new_password=first_form_value_or_empty(form_values, "new_password"),
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
            "/portal/account?status=password-updated",
            status_code=status.HTTP_303_SEE_OTHER,
        )

    @app.post("/portal/account/contact")
    async def portal_account_contact(
        request: Request,
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
            review_request = await run_in_threadpool(
                update_account_contact,
                session,
                authenticated_session.account,
                current_password=first_form_value_or_empty(form_values, "current_password"),
                email=first_form_value_or_empty(form_values, "email"),
                phone_number=first_form_value(form_values, "phone_number"),
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
        if review_request is not None:
            try:
                await run_in_threadpool(
                    send_contact_change_notice,
                    runtime,
                    recipient=review_request.email_before,
                )
            except PortalEmailDeliveryError:
                runtime.operational_metrics.record_failure("contact_change_delivery")
                logger.error("Contact-change notice delivery failed")
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail="contact update could not be completed",
                ) from None
        status_key = "contact-updated" if review_request is not None else "no-change"
        return RedirectResponse(
            f"/portal/account?status={status_key}",
            status_code=status.HTTP_303_SEE_OTHER,
        )

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
            Query(max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH),
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
        return render_portal_page(
            request,
            session,
            active_module="email-passwords",
            status_code=(
                status.HTTP_400_BAD_REQUEST
                if filter_error is not None or invalid_date_range
                else status.HTTP_200_OK
            ),
            email_password_search=q,
            email_password_provider=provider,
            email_password_date_from=parsed_date_from,
            email_password_date_to=parsed_date_to,  # ggignore
            email_password_page=page,
            email_password_filter_error=filter_error,
        )

    @app.post("/portal/email-passwords/{email_password_id}/reveal")
    async def reveal_portal_email_password(
        email_password_id: Annotated[int, PathParam(gt=0)],
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
                content={"detail": "authentication required"},
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
            runtime.operational_metrics.record_failure("unlock_secret_decryption")
            record_audit_event(
                session,
                event_type=AUDIT_EVENT_UNLOCK_SECRET_READ,
                outcome=AUDIT_OUTCOME_FAILURE,
                actor_type=AUDIT_ACTOR_TYPE_PATIENT,
                actor=account.username,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                account_id=account.id,
                reason="decryption_failed",
            )
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

    @app.post("/portal/logout")
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
    csrf_secret = runtime.csrf_secret
    text = portal_text(DEFAULT_LOCALE)

    @app.get("/auth/activate", name="activation_page")
    def activation_page(request: Request) -> Response:
        return render_public_auth_template(
            request,
            settings=settings,
            csrf_secret=csrf_secret,
            template_name="activate.jinja",
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
                template_name="activate.jinja",
                status_code=status.HTTP_400_BAD_REQUEST,
                error_message=text["password_mismatch"],
            )
        except RequestValidationError:
            if not is_browser_form:
                raise
            return render_public_auth_template(
                request,
                settings=settings,
                csrf_secret=csrf_secret,
                template_name="activate.jinja",
                status_code=status.HTTP_400_BAD_REQUEST,
                error_message=text["activation_error"],
            )
        client_reference_hash = hash_sensitive_reference(
            audit_hash_secret,
            "activation_client",
            get_request_client_reference(request, settings),
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
                proof_secret=identity_proof_secret,
                client_reference_hash=client_reference_hash,
                rate_limit=activation_rate_limit,
            )
        except UsernameUnavailableError:
            if is_browser_form:
                return render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name="activate.jinja",
                    status_code=status.HTTP_409_CONFLICT,
                    error_message=text["username_unavailable"],
                    form_values={
                        "email": payload.email,
                        "date_of_birth": payload.date_of_birth.isoformat(),
                    },
                )
            return JSONResponse(status_code=409, content={"detail": "username unavailable"})
        except ActivationThrottledError as exc:
            if is_browser_form:
                response = render_public_auth_template(
                    request,
                    settings=settings,
                    csrf_secret=csrf_secret,
                    template_name="activate.jinja",
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    error_message=text["activation_rate_limited"],
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
                    template_name="activate.jinja",
                    status_code=status.HTTP_400_BAD_REQUEST,
                    error_message=text["activation_error"],
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

        @app.get("/dev/admin/invites", response_model=list[InviteResponse])
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
            return invite_response_payload(invite, invite_token)

        @app.post("/dev/admin/invites/{invite_id}/revoke", response_model=InviteResponse)
        def dev_revoke_invite(
            invite_id: Annotated[int, PathParam(gt=0)],
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
            return invite_response_payload(invite)

        @app.post(
            "/dev/admin/accounts/{account_id}/unlock",
            response_model=AccountAdminResponse,
        )
        def dev_unlock_account(
            account_id: Annotated[int, PathParam(gt=0)],
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
