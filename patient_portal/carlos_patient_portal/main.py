"""Application factory and composition root for the CARLOS patient portal.

Builds the runtime (settings, engine, derived token keys, rate limiters, senders), installs the
exception handlers and security middleware, and registers the route modules under `routes/`.
Request handling itself lives in those modules; shared HTTP plumbing lives in `web_support.py`
and outbound delivery in `notifications.py`.
"""

import json
import logging
from collections.abc import AsyncGenerator, Awaitable, Callable, Generator
from contextlib import asynccontextmanager
from datetime import date, timedelta
from secrets import compare_digest, token_urlsafe
from time import monotonic
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import Engine
from sqlalchemy.exc import OperationalError, SQLAlchemyError
from sqlalchemy.orm import Session
from starlette.middleware.trustedhost import TrustedHostMiddleware
from starlette.responses import Response

from carlos_patient_portal.accounts import ActivationRateLimit
from carlos_patient_portal.audit import hash_sensitive_reference, record_audit_event
from carlos_patient_portal.auth import (
    AuthenticatedPortalSession,
    AuthPolicy,
    PasswordHashUnusableError,
    PortalSessionInvalidError,
    authenticate_session_token,
)
from carlos_patient_portal.config import (
    DEFAULT_AUDIT_RETENTION_DAYS,
    Settings,
    get_settings,
)
from carlos_patient_portal.credentials import configure_password_hashing
from carlos_patient_portal.database import (
    create_portal_engine,
    create_session_factory,
)
from carlos_patient_portal.email_delivery import PortalEmailSender, build_portal_email_sender
from carlos_patient_portal.i18n import DEFAULT_LOCALE, portal_text
from carlos_patient_portal.internal_routes import register_carlos_internal_routes
from carlos_patient_portal.invites import normalize_staff_actor
from carlos_patient_portal.models import (
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_ACTOR_TYPE_SYSTEM,
    AUDIT_EVENT_FHIR_SEARCH,
    AUDIT_EVENT_LOGIN,
    AUDIT_EVENT_RETENTION_POLICY_OVERRIDE,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
)
from carlos_patient_portal.presenters import assemble_email_password_dashboard
from carlos_patient_portal.routes.activation import register_activation_routes
from carlos_patient_portal.routes.auth import register_auth_routes, register_logout_route
from carlos_patient_portal.routes.dev_admin import register_dev_admin_routes
from carlos_patient_portal.routes.fhir import register_fhir_routes
from carlos_patient_portal.routes.portal import (
    register_patient_email_password_routes,
    register_portal_routes,
)
from carlos_patient_portal.routes.public import register_public_routes
from carlos_patient_portal.runtime import (
    FhirApiError,
    InMemoryRateLimiter,
    PortalRuntime,
    RouteDependencies,
    function_scoped_database_dependency,
)
from carlos_patient_portal.sms_delivery import PortalSmsSender, build_portal_sms_sender
from carlos_patient_portal.token_keys import PortalTokenKeys
from carlos_patient_portal.unlock_secrets import load_unlock_secret_words
from carlos_patient_portal.view_models import EmailPasswordDashboardViewModel
from carlos_patient_portal.web_support import (
    AUTHENTICATION_REQUIRED_DETAIL,
    CONTENT_SECURITY_POLICY,
    CSRF_COOKIE_NAME,
    CSRF_COOKIE_PATH,
    CSRF_FORM_FIELD,
    DEV_ADMIN_ACTOR_HEADER,
    FHIR_PATH_PREFIX,
    MAX_FORM_BODY_BYTES,
    MAX_FORM_FIELD_COUNT,
    NO_STORE_PATHS,
    NOT_FOUND_DETAIL,
    PACKAGE_DIR,
    PORTAL_SESSION_COOKIE_NAME,
    PORTAL_SESSION_COOKIE_PATH,
    SECURITY_HEADERS,
    SERVICE_UNAVAILABLE_DETAIL,
    clear_portal_session_cookie,
    create_csrf_token,
    fhir_operation_outcome_response,
    first_form_value,
    get_request_client_reference,
    get_urlencoded_form_values,
    index_template_context,
    is_maintenance_exempt_path,
    is_portal_path,
    is_rate_limited_path,
    is_valid_csrf_submission,
    portal_template_context,
    sanitized_validation_errors,
    service_notice_response,
    set_csrf_cookie,
    templates,
    wants_html_response,
)

logger = logging.getLogger(__name__)


def auth_policy_from_settings(settings: Settings) -> AuthPolicy:
    return AuthPolicy(
        max_failed_password_attempts=settings.auth_max_failed_password_attempts,
        mfa_max_failed_attempts=settings.mfa_max_failed_attempts,
        session_ttl=timedelta(seconds=settings.session_ttl_seconds),
        session_idle_timeout=timedelta(seconds=settings.session_idle_timeout_seconds),
        mfa_code_ttl=timedelta(seconds=settings.mfa_code_ttl_seconds),
        mfa_email_resend_cooldown=timedelta(seconds=settings.mfa_email_resend_cooldown_seconds),
        mfa_sms_resend_cooldown=timedelta(seconds=settings.mfa_sms_resend_cooldown_seconds),
        password_reset_token_ttl=timedelta(seconds=settings.password_reset_token_ttl_seconds),
        password_reset_request_cooldown=timedelta(
            seconds=settings.password_reset_request_cooldown_seconds
        ),
        require_mfa=settings.require_mfa,
    )


def build_portal_runtime(
    settings: Settings,
    *,
    email_sender: PortalEmailSender | None = None,
    sms_sender: PortalSmsSender | None = None,
) -> PortalRuntime:
    # Development without a configured secret gets an ephemeral one, so tokens minted by this
    # process stay valid for its lifetime and nothing survives a restart.
    token_keys = PortalTokenKeys.derive(
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
    unlock_secret_encryption_secret = unlock_secret_encryption_keys[unlock_secret_active_key_id]
    # Packaged data that a bad build can truncate or corrupt. Loading it here turns that into a
    # startup failure instead of a 500 the first time CARLOS asks for a passphrase, which could be
    # long after the deploy and is invisible to the readiness probe. The loader is lru_cached, so
    # the parse is paid once either way.
    load_unlock_secret_words()
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
        max_buckets=settings.rate_limit_max_buckets,
    )
    auth_rate_limiter = InMemoryRateLimiter(
        window_seconds=settings.auth_rate_limit_window_seconds,
        max_requests=settings.auth_rate_limit_max_requests,
        max_buckets=settings.rate_limit_max_buckets,
    )
    return PortalRuntime(
        settings=settings,
        database_engine=database_engine,
        session_factory=session_factory,
        token_keys=token_keys,
        identity_proof_secret=identity_proof_secret,
        audit_hash_secret=audit_hash_secret,
        unlock_secret_encryption_secret=unlock_secret_encryption_secret,
        unlock_secret_encryption_keys=unlock_secret_encryption_keys,
        unlock_secret_active_key_id=unlock_secret_active_key_id,
        activation_rate_limit=activation_rate_limit,
        auth_policy=auth_policy,
        rate_limiter=rate_limiter,
        auth_rate_limiter=auth_rate_limiter,
        email_sender=(
            email_sender if email_sender is not None else build_portal_email_sender(settings)
        ),
        sms_sender=(sms_sender if sms_sender is not None else build_portal_sms_sender(settings)),
    )


def record_audit_retention_override(runtime: PortalRuntime) -> None:
    """Log and audit a deployment running below the regulatory-default audit retention.

    Shortening retention is a legitimate response to a deletion obligation, but it narrows the
    security trail, so it must not be a silent property of an environment variable. Written into
    the trail itself so the shortening is visible to whoever later asks why an event is missing.

    Best-effort: a portal that cannot write this row must still start, because refusing to boot
    would turn a database hiccup into an outage. The log line survives either way.
    """
    settings = runtime.settings
    if not settings.audit_retention_is_shortened:
        return
    logger.warning(
        "Audit retention is configured below the regulatory default: %s days (default %s)",
        settings.audit_retention_days,
        DEFAULT_AUDIT_RETENTION_DAYS,
    )
    try:
        with runtime.session_factory() as session:
            with session.begin():
                record_audit_event(
                    session,
                    event_type=AUDIT_EVENT_RETENTION_POLICY_OVERRIDE,
                    outcome=AUDIT_OUTCOME_SUCCESS,
                    actor_type=AUDIT_ACTOR_TYPE_SYSTEM,
                    actor="configuration",
                    clinic_id=settings.clinic_id,
                    resource_type="audit_retention",
                    reason=f"retention_days={settings.audit_retention_days}",
                )
    except SQLAlchemyError as exc:
        runtime.operational_metrics.record_failure("retention_policy_audit")
        logger.error(
            "Audit-retention override could not be recorded: %s",
            type(exc).__name__,
        )


def build_lifespan(
    database_engine: Engine,
    runtime: PortalRuntime | None = None,
) -> Callable[[FastAPI], AsyncGenerator[None, None]]:
    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncGenerator[None, None]:
        if runtime is not None:
            record_audit_retention_override(runtime)
        try:
            yield
        finally:
            database_engine.dispose()

    return lifespan


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(
        request: Request,
        exc: RequestValidationError,
    ) -> JSONResponse:
        if request.url.path.startswith(FHIR_PATH_PREFIX):
            return fhir_operation_outcome_response(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                code="invalid",
                diagnostics="request parameters are invalid",
            )
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

    @app.exception_handler(PasswordHashUnusableError)
    async def password_hash_unusable_handler(
        request: Request,
        exc: PasswordHashUnusableError,
    ) -> JSONResponse:
        # A stored hash that cannot be evaluated is a server-side data fault, not a bad password.
        # Returning 401/403 here would tell the patient they mistyped and, on the settings screen,
        # would charge the attempt to a lockout budget they cannot recover from.
        request.app.state.operational_metrics.record_failure("password_hash_unusable")
        logger.error("Stored password hash is unusable; credential check cannot be completed")
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"detail": SERVICE_UNAVAILABLE_DETAIL},
            headers={"Retry-After": "1"},
        )

    @app.exception_handler(OperationalError)
    async def database_operational_error_handler(
        request: Request,
        exc: OperationalError,
    ) -> JSONResponse:
        request.app.state.operational_metrics.record_failure("database")
        logger.warning("Portal database operation unavailable: %s", type(exc.orig).__name__)
        if request.url.path.startswith(FHIR_PATH_PREFIX):
            return fhir_operation_outcome_response(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                code="transient",
                diagnostics=SERVICE_UNAVAILABLE_DETAIL,
            )
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"detail": SERVICE_UNAVAILABLE_DETAIL},
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
            if request.url.path.startswith(FHIR_PATH_PREFIX):
                response = fhir_operation_outcome_response(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    code="transient",
                    diagnostics=SERVICE_UNAVAILABLE_DETAIL,
                )
            elif wants_html_response(request.url.path):
                # A patient who navigated here in a browser gets a page, not a raw JSON body.
                response = service_notice_response(
                    request,
                    settings=settings,
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    heading_key="service_maintenance_heading",
                    message_key="service_maintenance_details",
                    retry_after_seconds=settings.maintenance_retry_after_seconds,
                )
            else:
                response = JSONResponse(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    content={"detail": SERVICE_UNAVAILABLE_DETAIL},
                )
            response.headers["Retry-After"] = str(settings.maintenance_retry_after_seconds)
        elif is_rate_limited_path(request.url.path):
            is_login = request.url.path == "/auth/login"
            limiter = runtime.auth_rate_limiter if is_login else runtime.rate_limiter
            client_reference = get_request_client_reference(request, settings)
            retry_after_seconds = limiter.consume(
                f"login:{client_reference}" if is_login else client_reference
            )
            if retry_after_seconds is None:
                response = await call_next(request)
            elif request.url.path.startswith(FHIR_PATH_PREFIX):
                response = fhir_operation_outcome_response(
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    code="throttled",
                    diagnostics="too many requests",
                )
                response.headers["Retry-After"] = str(retry_after_seconds)
            elif wants_html_response(request.url.path):
                response = service_notice_response(
                    request,
                    settings=settings,
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    heading_key="service_busy_heading",
                    message_key="service_busy_details",
                    retry_after_seconds=retry_after_seconds,
                )
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
            or request.url.path.startswith(FHIR_PATH_PREFIX)
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
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=NOT_FOUND_DETAIL)

    def require_dev_admin_token(
        authorization: Annotated[str | None, Header()] = None,
    ) -> None:
        if settings.dev_admin_token is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=NOT_FOUND_DETAIL)

        scheme, _, supplied_token = (authorization or "").partition(" ")
        expected_token = settings.dev_admin_token.get_secret_value().strip()
        if scheme.lower() != "bearer" or not compare_digest(supplied_token, expected_token):
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=NOT_FOUND_DETAIL)

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
        csrf_token = create_csrf_token(runtime.token_keys.csrf)
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
            raise HTTPException(status_code=401, detail=AUTHENTICATION_REQUIRED_DETAIL)
        return supplied_token.strip()

    def get_authenticated_portal_session(
        request: Request,
        session_token: Annotated[str, Depends(get_authorization_bearer_token)],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> AuthenticatedPortalSession:
        try:
            return authenticate_session_token(
                session,
                session_token=session_token,
                session_token_secret=runtime.token_keys.session,
                idle_timeout=runtime.auth_policy.session_idle_timeout,
            )
        except (PortalSessionInvalidError, ValueError) as exc:
            # These JSON routes serve the same PHI as the FHIR surface, so failed bearer
            # authentication must be equally visible to an investigator. The commit also persists
            # any revocation authenticate_session_token wrote (idle timeout, account disabled),
            # which the dependency teardown would otherwise roll back on this error path.
            record_audit_event(
                session,
                # Reuses the existing `login` type (the FHIR dependency likewise reuses
                # `fhir.search`) so no audit-constraint migration is needed; the
                # `authentication_failed` reason keeps it distinct from a password failure.
                event_type=AUDIT_EVENT_LOGIN,
                outcome=AUDIT_OUTCOME_FAILURE,
                actor_type=AUDIT_ACTOR_TYPE_PATIENT,
                client_reference_hash=hash_sensitive_reference(
                    runtime.audit_hash_secret,
                    "portal_client",
                    get_request_client_reference(request, settings),
                ),
                reason="authentication_failed",
            )
            session.commit()
            raise HTTPException(status_code=401, detail=AUTHENTICATION_REQUIRED_DETAIL) from exc

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
                diagnostics=AUTHENTICATION_REQUIRED_DETAIL,
            )
        try:
            return authenticate_session_token(
                session,
                session_token=supplied_token.strip(),
                session_token_secret=runtime.token_keys.session,
                idle_timeout=runtime.auth_policy.session_idle_timeout,
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
                diagnostics=AUTHENTICATION_REQUIRED_DETAIL,
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
            session_token_secret=runtime.token_keys.session,
            idle_timeout=runtime.auth_policy.session_idle_timeout,
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
        authenticated_session: AuthenticatedPortalSession | None = None,
    ) -> Response:
        if authenticated_session is None:
            try:
                authenticated_session = get_authenticated_portal_cookie_session(request, session)
            except (PortalSessionInvalidError, ValueError):
                response = RedirectResponse("/", status_code=status.HTTP_303_SEE_OTHER)
                clear_portal_session_cookie(response, settings=settings)
                return response

        email_passwords: EmailPasswordDashboardViewModel | None = None
        if active_module == "email-passwords":
            email_passwords = assemble_email_password_dashboard(
                session,
                authenticated_session.account,
                search=email_password_search,
                provider=email_password_provider,
                date_from=email_password_date_from,
                date_to=email_password_date_to,
                page=email_password_page,
                timezone_name=settings.clinic_timezone,
                filter_error=email_password_filter_error,
            )
        csrf_token = create_csrf_token(runtime.token_keys.csrf)
        response = templates.TemplateResponse(
            request=request,
            name="dashboard.jinja",
            context=portal_template_context(
                request,
                authenticated_session=authenticated_session,
                settings=settings,
                active_module=active_module,
                csrf_token=csrf_token,
                sms_mfa_available=runtime.sms_sender is not None,
                account_notice=account_notice,
                account_error=account_error,
                email_passwords=email_passwords,
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
        if not is_valid_csrf_submission(csrf_token, csrf_cookie, runtime.token_keys.csrf):
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
    # Before anything can serve a request, so no hash is ever produced with the module defaults
    # when the deployment configured something else.
    configure_password_hashing(
        max_concurrency=settings.password_hash_max_concurrency,
        time_cost=settings.password_hash_time_cost,
        memory_kib=settings.password_hash_memory_kib,
        parallelism=settings.password_hash_parallelism,
    )
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
        lifespan=build_lifespan(runtime.database_engine, runtime),
    )
    app.state.settings = settings
    app.state.database_engine = runtime.database_engine
    app.state.session_factory = runtime.session_factory
    app.state.rate_limiter = runtime.rate_limiter
    app.state.auth_rate_limiter = runtime.auth_rate_limiter
    app.state.operational_metrics = runtime.operational_metrics
    app.state.unlock_secret_encryption_secret = runtime.unlock_secret_encryption_secret
    logging.getLogger("uvicorn.access").disabled = not settings.is_development
    app.mount(
        "/static",
        StaticFiles(directory=str(PACKAGE_DIR / "static")),
        name="static",
    )
    register_exception_handlers(app)
    register_security_middleware(app, runtime)
    register_app_routes(app, runtime)
    # Added last so it ends up outermost: Starlette prepends each middleware, so registering the
    # host check first would let request logging, metrics, maintenance responses, and rate-limit
    # bucket consumption all run for a request whose Host header is rejected a layer later.
    app.add_middleware(
        TrustedHostMiddleware,
        allowed_hosts=list(settings.allowed_hosts),
    )
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
