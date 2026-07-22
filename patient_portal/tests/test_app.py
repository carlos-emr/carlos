from collections.abc import Generator

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from carlos_patient_portal.database import get_database_session
from carlos_patient_portal.main import create_app


def test_health_endpoint_reports_service_status() -> None:
    app = create_app()
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["service"] == "CARLOS Patient Portal"


def test_index_renders_sign_in_shell() -> None:
    app = create_app()
    response = TestClient(app).get("/")

    assert response.status_code == 200
    assert "CARLOS Patient Portal" in response.text
    assert "patient.username" in response.text
    assert "Maple Creek Medical" in response.text


def test_database_health_uses_database_dependency() -> None:
    app = create_app()
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
    app = create_app()
    response = TestClient(app).post(
        "/auth/login",
        data={"username": "patient.username", "password": "not-used-yet"},
    )

    assert response.status_code == 501
    assert response.json()["detail"] == "login is not implemented yet"
