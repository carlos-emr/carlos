from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from carlos_patient_portal.config import Settings, get_settings
from carlos_patient_portal.database import check_database, get_database_session

PACKAGE_DIR = Path(__file__).resolve().parent
templates = Jinja2Templates(directory=str(PACKAGE_DIR / "templates"))


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title=settings.service_name,
        version="0.1.0",
        docs_url="/api/docs",
        redoc_url="/api/redoc",
        openapi_url="/api/openapi.json",
    )
    app.mount(
        "/static",
        StaticFiles(directory=str(PACKAGE_DIR / "static")),
        name="static",
    )

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
    def health(current_settings: Annotated[Settings, Depends(get_settings)]) -> dict[str, str]:
        return {
            "status": "ok",
            "service": current_settings.service_name,
            "environment": current_settings.environment,
        }

    @app.get("/health/db")
    def database_health(
        session: Annotated[Session, Depends(get_database_session)],
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


app = create_app()
