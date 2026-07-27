import os
from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import inspect, select, text
from sqlalchemy.orm import Session

from carlos_patient_portal.config import Settings
from carlos_patient_portal.database import create_portal_engine
from carlos_patient_portal.main import create_app
from carlos_patient_portal.models import PatientPortalAccount

POSTGRES_URL = os.getenv("PORTAL_TEST_POSTGRES_URL")
INTERNAL_TOKEN = "i" * 32
POSTGRES_PATIENT_PASSWORD = "".join(("Postgres", "2026", "!!"))
WRONG_PASSWORD = "".join(("Wrong", "2026", "!!"))

pytestmark = pytest.mark.skipif(
    POSTGRES_URL is None,
    reason="PORTAL_TEST_POSTGRES_URL is required for PostgreSQL integration tests",
)


def clean_postgresql_database() -> None:
    assert POSTGRES_URL is not None
    engine = create_portal_engine(POSTGRES_URL)
    try:
        table_names = [
            name
            for name in inspect(engine).get_table_names()
            if name.startswith("patient_portal_")
        ]
        if table_names:
            quoted_names = ", ".join(f'"{name}"' for name in table_names)
            with engine.begin() as connection:
                connection.execute(text(f"TRUNCATE TABLE {quoted_names} CASCADE"))
    finally:
        engine.dispose()


def staff_headers() -> dict[str, str]:
    return {
        "Authorization": f"Bearer {INTERNAL_TOKEN}",
        "X-CARLOS-Provider-ID": "postgres-provider",
        "X-CARLOS-Provider-Name": "PostgreSQL Test",
        "X-CARLOS-Clinic-ID": "postgres-clinic",
        "X-CARLOS-Permissions": "portal.invite.manage",
    }


def test_postgresql_serializes_invite_and_login_security_updates() -> None:
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    app = create_app(
        Settings(
            environment="development",
            database_url=POSTGRES_URL,
            internal_api_token=INTERNAL_TOKEN,
            identity_proof_secret="p" * 32,
            audit_hash_secret="a" * 32,
            unlock_secret_encryption_secret="u" * 32,
            auth_max_failed_password_attempts=5,
        )
    )
    client = TestClient(app)
    invite_payload = {
        "demographic_no": 1234,
        "email": "postgres.patient@example.com",
        "date_of_birth": "1980-05-20",
        "health_card_number": "ABCD 1234-5678",
    }

    with ThreadPoolExecutor(max_workers=2) as executor:
        invite_responses = list(
            executor.map(
                lambda _: client.post(
                    "/internal/carlos/patients/1234/invites",
                    headers=staff_headers(),
                    json=invite_payload,
                ),
                range(2),
            )
        )
    assert sorted(response.status_code for response in invite_responses) == [201, 409]
    invite_token = next(
        response.json()["invite_token"]
        for response in invite_responses
        if response.status_code == 201
    )
    activation = client.post(
        "/auth/activate",
        json={
            "invite_code": invite_token,
            "email": invite_payload["email"],
            "date_of_birth": invite_payload["date_of_birth"],
            "health_card_number": invite_payload["health_card_number"],
            "username": "postgres.patient",
            "password": POSTGRES_PATIENT_PASSWORD,
        },
    )
    assert activation.status_code == 201

    with ThreadPoolExecutor(max_workers=6) as executor:
        login_responses = list(
            executor.map(
                lambda _: client.post(
                    "/auth/login",
                    json={"username": "postgres.patient", "password": WRONG_PASSWORD},
                ),
                range(6),
            )
        )
    assert all(response.status_code == 401 for response in login_responses)

    engine = create_portal_engine(POSTGRES_URL)
    try:
        with Session(engine) as session:
            account = session.scalar(
                select(PatientPortalAccount).where(
                    PatientPortalAccount.username == "postgres.patient"
                )
            )
            assert account is not None
            assert account.failed_login_count >= 5
            assert account.locked_at is not None
    finally:
        engine.dispose()

    locked_login = client.post(
        "/auth/login",
        json={
            "username": "postgres.patient",
            "password": POSTGRES_PATIENT_PASSWORD,
        },
    )
    assert locked_login.status_code == 423
