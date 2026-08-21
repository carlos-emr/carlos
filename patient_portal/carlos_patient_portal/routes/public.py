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

"""Public and infrastructure routes: the sign-in shell, liveness, readiness, and metrics.

The `/internal/health/*`, `/internal/readiness`, and `/internal/metrics` routes are excluded from
the OpenAPI schema and gated on the health token; expose them only to trusted infrastructure.
"""

from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.responses import JSONResponse, RedirectResponse
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session
from starlette.responses import Response

from carlos_patient_portal.database import (
    DatabaseSchemaMismatchError,
    check_database,
    check_database_schema_current,
)
from carlos_patient_portal.i18n import (
    LOCALE_COOKIE_MAX_AGE_SECONDS,
    LOCALE_COOKIE_NAME,
    normalize_locale,
)
from carlos_patient_portal.runtime import (
    PortalRuntime,
    RouteDependencies,
    function_scoped_database_dependency,
)
from carlos_patient_portal.web_support import NOT_FOUND_DETAIL, is_safe_local_redirect


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

    @app.get(
        "/locale/{locale_code}",
        responses={404: {"description": "Locale is not supported."}},
    )
    def select_locale(request: Request, locale_code: str, next: str = "/") -> Response:
        """Persist a display-language choice and return the patient to where they were.

        A GET without a CSRF token is deliberate. The cookie this writes selects strings and a date
        format and nothing else: it carries no identity, grants no access, and changes no stored
        state, so the worst a forged request achieves is showing a patient the wrong language,
        which they can undo with one click. Requiring a token would mean putting a POST form behind
        every header button on pages that are served before any session exists.
        """
        resolved_locale = normalize_locale(locale_code)
        if resolved_locale is None:
            raise HTTPException(status_code=404, detail=NOT_FOUND_DETAIL)
        # Only a local path is ever followed. An absolute URL, a scheme-relative "//evil.example",
        # or a backslash variant would turn a language link into an open redirect.
        destination = next if is_safe_local_redirect(next) else request.url_for("index").path
        response = RedirectResponse(url=destination, status_code=status.HTTP_303_SEE_OTHER)
        # HttpOnly even though this is only a display preference: nothing on the page reads it
        # from script, so withholding it from JavaScript costs nothing and keeps every cookie the
        # portal sets consistent.
        response.set_cookie(
            LOCALE_COOKIE_NAME,
            resolved_locale,
            max_age=LOCALE_COOKIE_MAX_AGE_SECONDS,
            httponly=True,
            samesite="lax",
            secure=not settings.is_development,
            path="/",
        )
        return response

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
            session.rollback()
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
            session.rollback()
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
