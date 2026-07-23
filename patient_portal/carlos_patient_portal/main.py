from collections.abc import AsyncGenerator, Awaitable, Callable, Generator
from contextlib import asynccontextmanager
from datetime import timedelta
from hashlib import sha256
from hmac import new as new_hmac
from http.cookies import SimpleCookie
from ipaddress import ip_address
from pathlib import Path as FilePath
from secrets import compare_digest, token_urlsafe
from time import time
from typing import Annotated, TypeVar
from urllib.parse import parse_qs

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, status
from fastapi import Path as PathParam
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from pydantic import BaseModel, ValidationError
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session
from starlette.responses import Response

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
    logout_patient_session,
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
    session_scope,
)
from carlos_patient_portal.identity import IdentityProof
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
    AUDIT_ACTOR_TYPE_STAFF,
    AUDIT_EVENT_INVITE_LIST,
    AUDIT_OUTCOME_SUCCESS,
    PatientPortalAccount,
    PatientPortalInvite,
)
from carlos_patient_portal.schemas import (
    AccountAdminResponse,
    ActivationRequest,
    ActivationResponse,
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

PACKAGE_DIR = FilePath(__file__).resolve().parent
RequestModel = TypeVar("RequestModel", bound=BaseModel)
templates = Jinja2Templates(directory=str(PACKAGE_DIR / "templates"))
CONTENT_SECURITY_POLICY = (
    "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; "
    "object-src 'none'"
)
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
VALIDATION_ERROR_PRIVATE_FIELDS = {"ctx", "input"}


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


def is_portal_path(path: str) -> bool:
    return path == PORTAL_SESSION_COOKIE_PATH or path.startswith(
        f"{PORTAL_SESSION_COOKIE_PATH}/"
    )


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
    }


def mfa_template_context(
    request: Request,
    *,
    settings: Settings,
    delivery: MfaChallengeDelivery,
    csrf_token: str,
) -> dict[str, object]:
    return {
        "request": request,
        "clinic_name": settings.clinic_name,
        "csrf_token": csrf_token,
        "development_mfa_code": delivery.code if settings.is_development else None,
        "mfa_challenge_token": delivery.challenge_token,
        "mfa_delivery_method": delivery.delivery_method,
        "service_name": settings.service_name,
    }


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
) -> dict[str, object]:
    account = authenticated_session.account
    return {
        "request": request,
        "service_name": settings.service_name,
        "clinic_name": settings.clinic_name,
        "account": account,
        "password_updated_date": account.password_updated_at.date().isoformat(),
        "active_module": active_module,
        "modules": portal_modules(active_module),
        "csrf_token": csrf_token,
    }


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


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
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
    database_engine = create_portal_engine(settings.database_url)
    session_factory = create_session_factory(database_engine)
    activation_rate_limit = ActivationRateLimit(
        failure_window=timedelta(seconds=settings.activation_failure_window_seconds),
        max_failures_per_invite=settings.activation_max_failures_per_invite,
        max_failures_per_client=settings.activation_max_failures_per_client,
    )
    auth_policy = auth_policy_from_settings(settings)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncGenerator[None, None]:
        try:
            yield
        finally:
            database_engine.dispose()

    app = FastAPI(
        title=settings.service_name,
        version="0.1.0",
        docs_url="/api/docs" if settings.is_development else None,
        redoc_url="/api/redoc" if settings.is_development else None,
        openapi_url="/api/openapi.json" if settings.is_development else None,
        lifespan=lifespan,
    )
    app.state.settings = settings
    app.state.database_engine = database_engine
    app.state.session_factory = session_factory
    app.mount(
        "/static",
        StaticFiles(directory=str(PACKAGE_DIR / "static")),
        name="static",
    )

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(
        _: Request,
        exc: RequestValidationError,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=422,
            content={"detail": sanitized_validation_errors(exc)},
        )

    @app.middleware("http")
    async def add_security_headers(
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        response = await call_next(request)
        for header, value in SECURITY_HEADERS.items():
            response.headers.setdefault(header, value)
        if not request.url.path.startswith("/api/"):
            response.headers.setdefault("Content-Security-Policy", CONTENT_SECURITY_POLICY)

        if (
            request.url.path in NO_STORE_PATHS
            or request.url.path.startswith("/auth/")
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

    def get_app_database_session() -> Generator[Session, None, None]:
        with session_scope(session_factory) as session:
            yield session

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
        csrf_token = create_csrf_token(csrf_secret)
        response = templates.TemplateResponse(
            request=request,
            name="index.html",
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
                token_secret=csrf_secret,
            )
        except (PortalSessionInvalidError, ValueError) as exc:
            raise HTTPException(status_code=401, detail="authentication required") from exc

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
            token_secret=csrf_secret,
        )

    def render_portal_page(
        request: Request,
        session: Session,
        *,
        active_module: str,
    ) -> Response:
        try:
            authenticated_session = get_authenticated_portal_cookie_session(request, session)
        except (PortalSessionInvalidError, ValueError):
            response = RedirectResponse("/", status_code=status.HTTP_303_SEE_OTHER)
            clear_portal_session_cookie(response, settings=settings)
            return response

        csrf_token = create_csrf_token(csrf_secret)
        response = templates.TemplateResponse(
            request=request,
            name="dashboard.html",
            context=portal_template_context(
                request,
                authenticated_session=authenticated_session,
                settings=settings,
                active_module=active_module,
                csrf_token=csrf_token,
            ),
        )
        set_csrf_cookie(
            response,
            csrf_token,
            settings=settings,
            path=PORTAL_SESSION_COOKIE_PATH,
        )
        return response

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
            if is_browser_form:
                return render_index_response(
                    request,
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    error_message="Sign-in could not be completed.",
                )
            return JSONResponse(
                status_code=401,
                content={"detail": "sign-in could not be completed"},
            )
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
                return render_index_response(
                    request,
                    status_code=status.HTTP_400_BAD_REQUEST,
                    error_message="MFA delivery method is unavailable.",
                )
            return JSONResponse(
                status_code=400,
                content={"detail": "MFA delivery method is unavailable"},
            )
        if is_browser_form and result.mfa_challenge is not None:
            csrf_token = create_csrf_token(csrf_secret)
            response = templates.TemplateResponse(
                request=request,
                name="mfa.html",
                context=mfa_template_context(
                    request,
                    settings=settings,
                    delivery=result.mfa_challenge,
                    csrf_token=csrf_token,
                ),
            )
            set_csrf_cookie(response, csrf_token, settings=settings, path=CSRF_COOKIE_PATH)
            return response
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
    def resend_mfa(
        payload: Annotated[MfaResendRequest, Depends(get_mfa_resend_request)],
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> dict[str, object] | JSONResponse:
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
            return JSONResponse(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                content={"detail": "MFA code was sent recently; try again later"},
                headers={"Retry-After": str(exc.retry_after_seconds)},
            )
        except (MfaChallengeNotFoundError, ValueError):
            return JSONResponse(status_code=400, content={"detail": "MFA could not be verified"})
        except AccountLockedError:
            return JSONResponse(
                status_code=status.HTTP_423_LOCKED,
                content={"detail": "account access is locked; contact the clinic for help"},
            )
        except PasswordResetRequiredError:
            return JSONResponse(
                status_code=403,
                content={"status": "password_reset_required"},
            )
        except MfaDeliveryUnavailableError:
            return JSONResponse(
                status_code=400,
                content={"detail": "MFA delivery method is unavailable"},
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
                return render_index_response(
                    request,
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    error_message="MFA could not be verified.",
                )
            return JSONResponse(status_code=401, content={"detail": "MFA could not be verified"})
        except (MfaChallengeNotFoundError, ValueError):
            if is_browser_form:
                return render_index_response(
                    request,
                    status_code=status.HTTP_400_BAD_REQUEST,
                    error_message="MFA could not be verified.",
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
    ) -> Response:
        return render_portal_page(request, session, active_module="account")

    @app.get("/portal/email-passwords")
    def portal_email_passwords(
        request: Request,
        session: Annotated[Session, Depends(get_app_database_session)],
    ) -> Response:
        return render_portal_page(request, session, active_module="email-passwords")

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

        session_token = request.cookies.get(PORTAL_SESSION_COOKIE_NAME)
        if session_token is not None:
            try:
                logout_patient_session(
                    session,
                    session_token=session_token,
                    token_secret=csrf_secret,
                )
            except (PortalSessionInvalidError, ValueError):
                pass
        clear_portal_session_cookie(response, settings=settings)
        return response

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

    return app
