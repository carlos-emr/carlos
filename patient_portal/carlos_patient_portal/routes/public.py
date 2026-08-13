"""Public and infrastructure routes: the sign-in shell, liveness, readiness, and metrics.

The `/internal/health/*`, `/internal/readiness`, and `/internal/metrics` routes are excluded from
the OpenAPI schema and gated on the health token; expose them only to trusted infrastructure.
"""

from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.responses import JSONResponse
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session
from starlette.responses import Response

from carlos_patient_portal.database import (
    DatabaseSchemaMismatchError,
    check_database,
    check_database_schema_current,
)
from carlos_patient_portal.runtime import (
    PortalRuntime,
    RouteDependencies,
    function_scoped_database_dependency,
)


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
            # This private health probe is intentionally excluded from the OpenAPI schema.
            raise HTTPException(  # NOSONAR
                status_code=503,
                detail="database unavailable",
            ) from exc
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
