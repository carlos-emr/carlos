from collections.abc import Generator

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from carlos_patient_portal import main
from carlos_patient_portal.config import Settings
from carlos_patient_portal.database import get_database_session


def test_health_endpoint_reports_service_status() -> None:
    app = main.create_app()
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["service"] == "CARLOS Patient Portal"


def test_index_renders_sign_in_shell() -> None:
    app = main.create_app()
    response = TestClient(app).get("/")

    assert response.status_code == 200
    assert "CARLOS Patient Portal" in response.text
    assert "patient.username" in response.text
    assert "Maple Creek Medical" in response.text


def test_static_logo_asset_is_served() -> None:
    app = main.create_app()
    response = TestClient(app).get("/static/carlos-placeholder.svg")

    assert response.status_code == 200
    assert "image/svg+xml" in response.headers["content-type"]
    assert "<svg" in response.text


def test_database_health_uses_database_dependency() -> None:
    app = main.create_app()
    engine = create_engine("sqlite+pysqlite:///:memory:", connect_args={"check_same_thread": False})
    session_factory = sessionmaker(bind=engine, autoflush=False, autocommit=False)

    def override_session() -> Generator[Session, None, None]:
        with session_factory() as session:
            yield session

    app.dependency_overrides[get_database_session] = override_session

    response = TestClient(app).get("/health/db")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "ok"}


def test_login_route_is_explicitly_not_implemented() -> None:
    app = main.create_app()
    response = TestClient(app).post(
        "/auth/login",
        data={"username": "patient.username", "password": "not-used-yet"},
    )

    assert response.status_code == 501
    assert response.json()["detail"] == "login is not implemented yet"


def test_api_docs_are_available_outside_production() -> None:
    app = main.create_app(Settings(environment="development"))

    assert TestClient(app).get("/api/openapi.json").status_code == 200


def test_api_docs_are_disabled_in_production() -> None:
    app = main.create_app(Settings(environment="production", session_secret="production-secret"))

    assert TestClient(app).get("/api/openapi.json").status_code == 404
    assert TestClient(app).get("/api/docs").status_code == 404
    assert TestClient(app).get("/api/redoc").status_code == 404


def test_production_rejects_default_session_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(environment="production")


def test_module_does_not_create_global_app_on_import() -> None:
    assert not hasattr(main, "app")
