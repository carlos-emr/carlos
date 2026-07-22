import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from carlos_patient_portal import main
from carlos_patient_portal.config import Settings

PRODUCTION_SESSION_SECRET = "production-session-secret-0000000001"
INTERNAL_HEALTH_TOKEN = "internal-health-token-0000000000001"
WRONG_INTERNAL_HEALTH_TOKEN = "wrong-internal-health-token-0000000000"


def test_health_endpoint_is_minimal() -> None:
    app = main.create_app(
        Settings(environment="staging", internal_health_token=INTERNAL_HEALTH_TOKEN)
    )
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_index_renders_sign_in_shell() -> None:
    app = main.create_app()
    response = TestClient(app).get("/")

    assert response.status_code == 200
    assert "CARLOS Patient Portal" in response.text
    assert 'placeholder="patient.username"' in response.text
    assert 'value="patient.username"' not in response.text
    assert "Maple Creek Medical" in response.text


def test_static_logo_asset_is_served() -> None:
    app = main.create_app()
    response = TestClient(app).get("/static/carlos-placeholder.svg")

    assert response.status_code == 200
    assert "image/svg+xml" in response.headers["content-type"]
    assert "<svg" in response.text


def test_sign_in_shell_uses_security_headers() -> None:
    app = main.create_app()
    response = TestClient(app).get("/")

    assert response.headers["content-security-policy"] == (
        "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; "
        "object-src 'none'"
    )
    assert response.headers["x-frame-options"] == "DENY"
    assert response.headers["x-content-type-options"] == "nosniff"
    assert response.headers["referrer-policy"] == "same-origin"
    assert response.headers["cache-control"] == "no-store"
    assert response.headers["pragma"] == "no-cache"


def test_production_responses_include_hsts() -> None:
    app = main.create_app(
        Settings(
            environment="production",
            session_secret=PRODUCTION_SESSION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).get("/")

    assert response.headers["strict-transport-security"] == (
        "max-age=31536000; includeSubDomains"
    )


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
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    client = TestClient(app)

    assert client.get("/internal/health/db").status_code == 404
    assert client.get(
        "/internal/health/db",
        headers={"Authorization": f"Bearer {WRONG_INTERNAL_HEALTH_TOKEN}"},
    ).status_code == 404
    assert client.get(
        "/internal/health/db",
        headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
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
            session_secret=PRODUCTION_SESSION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )

    assert TestClient(app).get("/api/openapi.json").status_code == 404
    assert TestClient(app).get("/api/docs").status_code == 404
    assert TestClient(app).get("/api/redoc").status_code == 404


def test_environment_aliases_are_normalized() -> None:
    settings = Settings(
        environment=" prod ",
        session_secret=PRODUCTION_SESSION_SECRET,
        internal_health_token=INTERNAL_HEALTH_TOKEN,
    )

    assert settings.environment == "production"
    assert settings.is_production


def test_invalid_environment_is_rejected() -> None:
    with pytest.raises(ValidationError, match="environment"):
        Settings(environment="sandbox")


def test_session_secret_must_not_be_blank() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(session_secret=" ")


def test_production_rejects_default_session_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(environment="production", internal_health_token=INTERNAL_HEALTH_TOKEN)


def test_production_rejects_short_session_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(
            environment="production",
            session_secret="short-production-secret",
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )


def test_production_rejects_missing_internal_health_token() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        Settings(environment="production", session_secret=PRODUCTION_SESSION_SECRET)


def test_non_development_rejects_missing_internal_health_token() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        Settings(environment="staging")


def test_internal_health_token_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        Settings(internal_health_token="short-token")


def test_module_does_not_create_global_app_on_import() -> None:
    assert not hasattr(main, "app")
