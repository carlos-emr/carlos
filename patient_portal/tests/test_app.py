import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from carlos_patient_portal import main
from carlos_patient_portal.config import Settings


def test_health_endpoint_is_minimal() -> None:
    app = main.create_app(Settings(environment="staging"))
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


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


def test_internal_database_health_uses_app_database_settings() -> None:
    app = main.create_app(Settings(database_url="sqlite+pysqlite:///:memory:"))
    response = TestClient(app).get("/internal/health/db")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "ok"}


def test_public_database_health_path_is_not_registered() -> None:
    app = main.create_app()

    assert TestClient(app).get("/health/db").status_code == 404


def test_internal_database_health_requires_configured_token() -> None:
    app = main.create_app(
        Settings(
            database_url="sqlite+pysqlite:///:memory:",
            internal_health_token="health-token",
        )
    )
    client = TestClient(app)

    assert client.get("/internal/health/db").status_code == 404
    assert client.get(
        "/internal/health/db",
        headers={"Authorization": "Bearer wrong-token"},
    ).status_code == 404
    assert client.get(
        "/internal/health/db",
        headers={"Authorization": "Bearer health-token"},
    ).status_code == 200


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
    app = main.create_app(
        Settings(
            environment="production",
            session_secret="production-secret",
            internal_health_token="health-token",
        )
    )

    assert TestClient(app).get("/api/openapi.json").status_code == 404
    assert TestClient(app).get("/api/docs").status_code == 404
    assert TestClient(app).get("/api/redoc").status_code == 404


def test_production_rejects_default_session_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(environment="production")


def test_production_rejects_missing_internal_health_token() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        Settings(environment="production", session_secret="production-secret")


def test_module_does_not_create_global_app_on_import() -> None:
    assert not hasattr(main, "app")
