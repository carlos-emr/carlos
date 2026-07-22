import re

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy.engine import make_url

from carlos_patient_portal import cli, main
from carlos_patient_portal.config import (
    DEFAULT_DATABASE_URL,
    MIN_PRODUCTION_SECRET_LENGTH,
    Settings,
)

NON_DEVELOPMENT_SESSION_SECRET = "s" * MIN_PRODUCTION_SECRET_LENGTH
INTERNAL_HEALTH_TOKEN = "h" * MIN_PRODUCTION_SECRET_LENGTH
WRONG_INTERNAL_HEALTH_TOKEN = "w" * MIN_PRODUCTION_SECRET_LENGTH
CSRF_TOKEN_PATTERN = re.compile(r'name="csrf_token" value="([^"]+)"')


def development_settings(**overrides: object) -> Settings:
    values = {"environment": "development", **overrides}
    return Settings(**values)


def get_csrf_token(client: TestClient) -> str:
    response = client.get("/")
    match = CSRF_TOKEN_PATTERN.search(response.text)

    assert response.status_code == 200
    assert match is not None
    csrf_token = match.group(1)
    assert response.cookies.get(main.CSRF_COOKIE_NAME) == csrf_token
    return csrf_token


def test_health_endpoint_is_minimal() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_index_renders_sign_in_shell() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get("/")

    assert response.status_code == 200
    assert "CARLOS Patient Portal" in response.text
    assert 'placeholder="patient.username"' in response.text
    assert 'value="patient.username"' not in response.text
    assert 'name="csrf_token"' in response.text
    assert "nosemgrep" not in response.text
    assert "Maple Creek Medical" in response.text


def test_static_logo_asset_is_served() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get("/static/carlos-placeholder.svg")

    assert response.status_code == 200
    assert "image/svg+xml" in response.headers["content-type"]
    assert "<svg" in response.text


def test_sign_in_shell_uses_security_headers() -> None:
    app = main.create_app(development_settings())
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
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).get("/")

    assert response.headers["strict-transport-security"] == (
        "max-age=31536000; includeSubDomains"
    )


def test_internal_database_health_uses_app_database_settings() -> None:
    app = main.create_app(development_settings(database_url="sqlite+pysqlite:///:memory:"))
    response = TestClient(app).get("/internal/health/db")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "ok"}


def test_public_database_health_path_is_not_registered() -> None:
    app = main.create_app(development_settings())

    assert TestClient(app).get("/health/db").status_code == 404


def test_internal_database_health_requires_configured_token() -> None:
    app = main.create_app(
        development_settings(
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
    app = main.create_app(development_settings())
    client = TestClient(app)
    csrf_token = get_csrf_token(client)
    response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.username",
            "password": "unused",
        },
    )

    assert response.status_code == 501
    assert response.json()["detail"] == "login is not implemented yet"


def test_login_route_rejects_missing_csrf_token() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).post(
        "/auth/login",
        data={"username": "patient.username", "password": "unused"},
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "invalid CSRF token"


def test_login_route_rejects_tampered_csrf_token() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    csrf_token = get_csrf_token(client)
    response = client.post(
        "/auth/login",
        data={
            "csrf_token": f"{csrf_token}0",
            "username": "patient.username",
            "password": "unused",
        },
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "invalid CSRF token"


def test_login_route_rejects_csrf_token_without_matching_cookie() -> None:
    app = main.create_app(development_settings())
    client_with_cookie = TestClient(app)
    client_without_cookie = TestClient(app)
    csrf_token = get_csrf_token(client_with_cookie)
    response = client_without_cookie.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.username",
            "password": "unused",
        },
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "invalid CSRF token"


def test_login_route_rejects_oversized_form_body() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    csrf_token = get_csrf_token(client)
    response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.username",
            "password": "x" * main.MAX_FORM_BODY_BYTES,
        },
    )

    assert response.status_code == 413
    assert response.json()["detail"] == "request body too large"


def test_api_docs_are_available_in_development() -> None:
    app = main.create_app(development_settings())

    assert TestClient(app).get("/api/openapi.json").status_code == 200


def test_api_docs_are_disabled_outside_development() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )

    assert TestClient(app).get("/api/openapi.json").status_code == 404
    assert TestClient(app).get("/api/docs").status_code == 404
    assert TestClient(app).get("/api/redoc").status_code == 404


def test_api_docs_are_disabled_in_production() -> None:
    app = main.create_app(
        Settings(
            environment="production",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )

    assert TestClient(app).get("/api/openapi.json").status_code == 404
    assert TestClient(app).get("/api/docs").status_code == 404
    assert TestClient(app).get("/api/redoc").status_code == 404


def test_environment_aliases_are_normalized() -> None:
    settings = Settings(
        environment=" prod ",
        session_secret=NON_DEVELOPMENT_SESSION_SECRET,
        internal_health_token=INTERNAL_HEALTH_TOKEN,
    )

    assert settings.environment == "production"
    assert settings.is_production


def test_default_database_url_does_not_embed_credentials() -> None:
    database_url = make_url(DEFAULT_DATABASE_URL)

    assert database_url.username is None
    assert database_url.password is None


def test_development_defaults_do_not_embed_session_secret() -> None:
    assert development_settings().session_secret is None


def test_default_settings_reject_missing_production_secrets() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings()


def test_invalid_environment_is_rejected() -> None:
    with pytest.raises(ValidationError, match="environment"):
        Settings(environment="sandbox")


def test_session_secret_must_not_be_blank() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        development_settings(session_secret=" ")


def test_production_rejects_missing_session_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(environment="production", internal_health_token=INTERNAL_HEALTH_TOKEN)


def test_non_development_rejects_missing_session_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(environment="staging", internal_health_token=INTERNAL_HEALTH_TOKEN)


def test_production_rejects_short_session_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(
            environment="production",
            session_secret="short-value",
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )


def test_production_rejects_missing_internal_health_token() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        Settings(environment="production", session_secret=NON_DEVELOPMENT_SESSION_SECRET)


def test_non_development_rejects_missing_internal_health_token() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        Settings(environment="staging", session_secret=NON_DEVELOPMENT_SESSION_SECRET)


def test_internal_health_token_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        development_settings(internal_health_token="short")


def test_module_does_not_create_global_app_on_import() -> None:
    assert not hasattr(main, "app")


def test_packaged_migration_command_upgrades_to_head_by_default(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    upgraded: dict[str, str] = {}

    monkeypatch.setattr(
        cli,
        "get_settings",
        lambda: development_settings(database_url="sqlite+pysqlite:///:memory:"),
    )
    monkeypatch.setattr(
        cli.command,
        "upgrade",
        lambda config, revision: upgraded.update(
            revision=revision,
            script_location=config.get_main_option("script_location"),
            database_url=config.get_main_option("sqlalchemy.url"),
        ),
    )

    cli.migrate([])

    assert upgraded == {
        "revision": "head",
        "script_location": "carlos_patient_portal:migrations",
        "database_url": "sqlite+pysqlite:///:memory:",
    }
