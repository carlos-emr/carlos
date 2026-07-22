from collections.abc import AsyncGenerator, Generator
from contextlib import asynccontextmanager
from pathlib import Path
from secrets import compare_digest
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from carlos_patient_portal.config import Settings, get_settings
from carlos_patient_portal.database import (
    check_database,
    create_portal_engine,
    create_session_factory,
    session_scope,
)

PACKAGE_DIR = Path(__file__).resolve().parent
templates = Jinja2Templates(directory=str(PACKAGE_DIR / "templates"))


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
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
        docs_url=None if settings.is_production else "/api/docs",
        redoc_url=None if settings.is_production else "/api/redoc",
        openapi_url=None if settings.is_production else "/api/openapi.json",
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

    def get_app_database_session() -> Generator[Session, None, None]:
        yield from session_scope(session_factory)

    def require_internal_health_token(
        authorization: Annotated[str | None, Header()] = None,
    ) -> None:
        if settings.internal_health_token is None:
            return

        scheme, _, supplied_token = (authorization or "").partition(" ")
        expected_token = settings.internal_health_token.get_secret_value()
        if scheme.lower() != "bearer" or not compare_digest(supplied_token, expected_token):
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="not found")

    @app.get("/", response_class=HTMLResponse)
    def index(request: Request) -> HTMLResponse:
        return templates.TemplateResponse(
            request=request,
            name="index.html",
            context={
                "request": request,
                "clinic_name": settings.clinic_name,
                "service_name": settings.service_name,
            },
        )

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
    def login_placeholder() -> None:
        raise HTTPException(status_code=501, detail="login is not implemented yet")

    return app
