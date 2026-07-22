from collections.abc import AsyncGenerator, Awaitable, Callable, Generator
from contextlib import asynccontextmanager
from hashlib import sha256
from hmac import new as new_hmac
from pathlib import Path as FilePath
from secrets import compare_digest, token_urlsafe
from time import time
from typing import Annotated
from urllib.parse import parse_qs

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, status
from fastapi import Path as PathParam
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session
from starlette.responses import Response

from carlos_patient_portal.config import Settings, get_settings
from carlos_patient_portal.database import (
    check_database,
    create_portal_engine,
    create_session_factory,
    session_scope,
)
from carlos_patient_portal.invites import (
    InviteNotFoundError,
    RevokedInviteError,
    create_invite,
    list_invites,
    resend_invite,
    revoke_invite,
)
from carlos_patient_portal.models import PatientPortalInvite
from carlos_patient_portal.schemas import (
    InviteCreateRequest,
    InviteResponse,
    InviteTokenResponse,
    StaffActorRequest,
)

PACKAGE_DIR = FilePath(__file__).resolve().parent
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
NO_STORE_PATHS = {"/", "/auth/login"}
MAX_FORM_BODY_BYTES = 16 * 1024
MAX_FORM_FIELD_COUNT = 20
CSRF_COOKIE_NAME = "carlos_portal_csrf"
CSRF_COOKIE_PATH = "/auth"
CSRF_FORM_FIELD = "csrf_token"
CSRF_TOKEN_TTL_SECONDS = 60 * 60
CSRF_FUTURE_SKEW_SECONDS = 60


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


def invite_response_payload(
    invite: PatientPortalInvite,
    invite_token: str | None = None,
) -> dict[str, object]:
    payload: dict[str, object] = {
        "id": invite.id,
        "demographic_no": invite.demographic_no,
        "status": invite.status,
        "created_by": invite.created_by,
        "created_at": invite.created_at,
        "updated_at": invite.updated_at,
        "sent_count": invite.sent_count,
        "last_sent_at": invite.last_sent_at,
        "last_sent_by": invite.last_sent_by,
        "revoked_at": invite.revoked_at,
        "revoked_by": invite.revoked_by,
    }
    if invite_token is not None:
        payload["invite_token"] = invite_token
    return payload


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

    body = (await read_limited_request_body(request, max_body_bytes)).decode(
        "utf-8",
        errors="replace",
    )
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


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    csrf_secret = (
        settings.session_secret.get_secret_value()
        if settings.session_secret is not None
        else token_urlsafe(32)
    )
    database_engine = create_portal_engine(settings.database_url)
    session_factory = create_session_factory(database_engine)

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
        yield from session_scope(session_factory)

    def require_internal_health_token(
        authorization: Annotated[str | None, Header()] = None,
    ) -> None:
        if settings.internal_health_token is None:
            return

        scheme, _, supplied_token = (authorization or "").partition(" ")
        expected_token = settings.internal_health_token.get_secret_value().strip()
        if scheme.lower() != "bearer" or not compare_digest(supplied_token, expected_token):
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="not found")

    @app.get("/")
    def index(request: Request) -> Response:
        csrf_token = create_csrf_token(csrf_secret)
        response = templates.TemplateResponse(
            request=request,
            name="index.html",
            context={
                "request": request,
                "clinic_name": settings.clinic_name,
                "csrf_token": csrf_token,
                "service_name": settings.service_name,
            },
        )
        response.set_cookie(
            CSRF_COOKIE_NAME,
            csrf_token,
            httponly=True,
            max_age=CSRF_TOKEN_TTL_SECONDS,
            path=CSRF_COOKIE_PATH,
            samesite="strict",
            secure=not settings.is_development,
        )
        return response

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

    @app.post("/auth/login")
    async def login_placeholder(request: Request) -> None:
        form_values = await get_urlencoded_form_values(
            request,
            MAX_FORM_BODY_BYTES,
            MAX_FORM_FIELD_COUNT,
        )
        csrf_values = form_values.get(CSRF_FORM_FIELD)
        csrf_token = csrf_values[0] if csrf_values else None
        csrf_cookie = request.cookies.get(CSRF_COOKIE_NAME)
        if not is_valid_csrf_submission(csrf_token, csrf_cookie, csrf_secret):
            raise HTTPException(status_code=403, detail="invalid CSRF token")
        raise HTTPException(status_code=501, detail="login is not implemented yet")

    if settings.is_development:

        @app.post(
            "/dev/admin/invites",
            response_model=InviteTokenResponse,
            status_code=status.HTTP_201_CREATED,
        )
        def dev_create_invite(
            payload: InviteCreateRequest,
            session: Annotated[Session, Depends(get_app_database_session)],
        ) -> dict[str, object]:
            invite, invite_token = create_invite(
                session,
                payload.demographic_no,
                payload.actor,
            )
            return invite_response_payload(invite, invite_token)

        @app.get("/dev/admin/invites", response_model=list[InviteResponse])
        def dev_list_invites(
            session: Annotated[Session, Depends(get_app_database_session)],
            demographic_no: Annotated[int | None, Query(gt=0)] = None,
            limit: Annotated[int, Query(ge=1, le=100)] = 10,
            offset: Annotated[int, Query(ge=0)] = 0,
        ) -> list[dict[str, object]]:
            return [
                invite_response_payload(invite)
                for invite in list_invites(
                    session,
                    demographic_no=demographic_no,
                    limit=limit,
                    offset=offset,
                )
            ]

        @app.post("/dev/admin/invites/{invite_id}/resend", response_model=InviteTokenResponse)
        def dev_resend_invite(
            invite_id: Annotated[int, PathParam(gt=0)],
            payload: StaffActorRequest,
            session: Annotated[Session, Depends(get_app_database_session)],
        ) -> dict[str, object]:
            try:
                invite, invite_token = resend_invite(session, invite_id, payload.actor)
            except InviteNotFoundError as exc:
                raise HTTPException(status_code=404, detail="invite not found") from exc
            except RevokedInviteError as exc:
                raise HTTPException(status_code=409, detail="invite has been revoked") from exc
            return invite_response_payload(invite, invite_token)

        @app.post("/dev/admin/invites/{invite_id}/revoke", response_model=InviteResponse)
        def dev_revoke_invite(
            invite_id: Annotated[int, PathParam(gt=0)],
            payload: StaffActorRequest,
            session: Annotated[Session, Depends(get_app_database_session)],
        ) -> dict[str, object]:
            try:
                invite = revoke_invite(session, invite_id, payload.actor)
            except InviteNotFoundError as exc:
                raise HTTPException(status_code=404, detail="invite not found") from exc
            return invite_response_payload(invite)

    return app
