import logging
from datetime import UTC, datetime, timedelta

import pytest
from alembic import command
from alembic.config import Config
from argon2 import PasswordHasher
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy import create_engine, select, text
from sqlalchemy.orm import Session, sessionmaker

from carlos_patient_portal import auth, main
from carlos_patient_portal.config import MIN_PRODUCTION_SECRET_LENGTH, Settings
from carlos_patient_portal.credentials import hash_password
from carlos_patient_portal.database import Base, create_portal_engine
from carlos_patient_portal.interop import (
    build_fhir_organization_id,
    build_fhir_patient_id,
    build_fhir_practitioner_id,
    build_fhir_r4_capability_statement,
)
from carlos_patient_portal.maintenance import cleanup_transient_auth_rows
from carlos_patient_portal.models import (
    ACCOUNT_STATUS_ACTIVE,
    INVITE_STATUS_ACCEPTED,
    INVITE_STATUS_PENDING,
    INVITE_STATUS_REVOKED,
    INVITE_STATUS_SUPERSEDED,
    PatientPortalAccount,
    PatientPortalInvite,
)
from carlos_patient_portal.unlock_secrets import (
    UnlockSecretDecryptionError,
    create_unlock_secret,
    decrypt_unlock_secret_payload,
)

SECRET_LENGTH = MIN_PRODUCTION_SECRET_LENGTH
TEST_PASSWORD = "Stronger1!word"


def production_settings_values(**overrides: object) -> dict[str, object]:
    values: dict[str, object] = {
        "environment": "production",
        "clinic_id": "clinic-a",
        "clinic_name": "Clinic A",
        "public_base_url": "https://portal.example.test",
        "database_url": "postgresql+psycopg://localhost/portal",
        "session_secret": "s" * SECRET_LENGTH,
        "identity_proof_secret": "i" * SECRET_LENGTH,
        "audit_hash_secret": "a" * SECRET_LENGTH,
        "unlock_secret_encryption_secret": "u" * SECRET_LENGTH,
        "internal_health_token": "h" * SECRET_LENGTH,
        "internal_api_token": "c" * SECRET_LENGTH,
        "smtp_host": "mail.internal",
        "smtp_from_address": "portal@example.test",
        "smtp_starttls": True,
        "sms_webhook_url": "https://sms.example.test/messages",
        "sms_webhook_token": "m" * SECRET_LENGTH,
    }
    values.update(overrides)
    return values


def test_production_rejects_unsupported_database_and_placeholder_clinic() -> None:
    with pytest.raises(ValidationError, match=r"postgresql\+psycopg"):
        Settings(**production_settings_values(database_url="sqlite+pysqlite:////tmp/portal.db"))
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_CLINIC_ID"):
        Settings(**production_settings_values(clinic_id="default"))
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_CLINIC_NAME"):
        Settings(**production_settings_values(clinic_name="Maple Creek Medical"))


def test_non_development_rejects_cross_purpose_secret_reuse() -> None:
    with pytest.raises(ValidationError, match="must not reuse"):
        Settings(
            **production_settings_values(
                audit_hash_secret="i" * SECRET_LENGTH,
            )
        )


def test_mail_header_configuration_is_validated_at_startup() -> None:
    with pytest.raises(ValidationError, match="control characters"):
        Settings(**production_settings_values(clinic_name="Clinic A\r\nBcc: attacker@example.test"))
    with pytest.raises(ValidationError, match="mailbox address"):
        Settings(**production_settings_values(smtp_from_address="Portal <portal@example.test>"))


def test_canonical_host_is_enforced_without_rendering_credentials() -> None:
    settings = Settings(
        environment="staging",
        clinic_id="clinic-a",
        clinic_name="Clinic A",
        public_base_url="https://portal.example.test",
        database_url="sqlite+pysqlite:///:memory:",
        session_secret="s" * SECRET_LENGTH,
        identity_proof_secret="i" * SECRET_LENGTH,
        audit_hash_secret="a" * SECRET_LENGTH,
        unlock_secret_encryption_secret="u" * SECRET_LENGTH,
        internal_health_token="h" * SECRET_LENGTH,
    )
    app = main.create_app(settings)
    Base.metadata.create_all(app.state.database_engine)
    client = TestClient(app, base_url="https://portal.example.test")

    assert client.get("/").status_code == 200
    rejected = client.get("/", headers={"Host": "evil.example"})
    assert rejected.status_code == 400
    assert "password" not in rejected.text.casefold()
    assert logging.getLogger("uvicorn.access").disabled


def test_fhir_validation_errors_are_operation_outcomes_and_offsets_are_bounded() -> None:
    app = main.create_app(
        Settings(
            environment="development",
            database_url="sqlite+pysqlite:///:memory:",
            session_secret="s" * SECRET_LENGTH,
        )
    )
    Base.metadata.create_all(app.state.database_engine)
    client = TestClient(app)
    now = datetime.now(UTC)
    with app.state.session_factory() as session:
        account = PatientPortalAccount(
            clinic_id="default",
            demographic_no=1234,
            username="fhir.patient",
            email="fhir.patient@example.test",
            preferred_mfa_method="email",
            password_hash=hash_password(TEST_PASSWORD),
            status=ACCOUNT_STATUS_ACTIVE,
            created_at=now,
            updated_at=now,
            password_updated_at=now,
        )
        session.add(account)
        session.flush()
        token = auth.create_patient_session(
            session,
            account,
            policy=main.auth_policy_from_settings(app.state.settings),
            token_secret="s" * SECRET_LENGTH,
            now=now,
        )
        session.commit()
    headers = {"Authorization": f"Bearer {token}"}

    invalid_count = client.get("/fhir/Patient?_count=0", headers=headers)
    huge_offset = client.get(
        "/fhir/DocumentReference?_offset=100001",
        headers=headers,
    )
    patient_offset = client.get(
        "/api/patient/email-passwords?offset=100001",
        headers=headers,
    )

    for response in (invalid_count, huge_offset):
        assert response.status_code == 422
        assert response.headers["content-type"].startswith("application/fhir+json")
        assert response.json()["resourceType"] == "OperationOutcome"
        assert "100001" not in response.text
    assert patient_offset.status_code == 422


def test_fhir_ids_are_stable_opaque_and_collision_resistant() -> None:
    patient_ids = {
        build_fhir_patient_id("clinic_a", 1234),
        build_fhir_patient_id("clinic-a", 1234),
        build_fhir_patient_id("clinic a", 1234),
    }
    practitioner_ids = {
        build_fhir_practitioner_id(
            clinic_id="clinic-a",
            provider_id=provider_id,
            name="CarlosDoc",
        )
        for provider_id in ("provider_a", "provider-a", "provider a")
    }
    organization_ids = {
        build_fhir_organization_id(clinic_id) for clinic_id in ("clinic_a", "clinic-a", "clinic a")
    }

    assert len(patient_ids) == len(practitioner_ids) == len(organization_ids) == 3
    assert all(identifier.startswith("portal-") for identifier in patient_ids)
    assert all("1234" not in identifier for identifier in patient_ids)


def test_capability_statement_metadata_is_stable() -> None:
    first = build_fhir_r4_capability_statement(service_name="CARLOS Patient Portal")
    second = build_fhir_r4_capability_statement(service_name="CARLOS Patient Portal")

    assert first["date"] == second["date"]
    assert first["software"]["version"] == "0.1.0"


def test_session_enforces_idle_and_absolute_expiry(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    engine = create_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)
    policy = main.auth_policy_from_settings(Settings(environment="development"))
    started_at = datetime(2026, 7, 28, 12, 0, tzinfo=UTC)

    with session_factory() as session:
        account = PatientPortalAccount(
            clinic_id="default",
            demographic_no=1234,
            username="session.patient",
            email="session.patient@example.test",
            preferred_mfa_method="email",
            password_hash=hash_password(TEST_PASSWORD),
            status=ACCOUNT_STATUS_ACTIVE,
            created_at=started_at,
            updated_at=started_at,
            password_updated_at=started_at,
        )
        session.add(account)
        session.flush()
        active_token = auth.create_patient_session(
            session,
            account,
            policy=policy,
            token_secret="session-secret",
            now=started_at,
        )
        idle_token = auth.create_patient_session(
            session,
            account,
            policy=policy,
            token_secret="session-secret",
            now=started_at,
        )
        session.commit()

    monkeypatch.setattr(auth, "utc_now", lambda: started_at + timedelta(minutes=9))
    with session_factory() as session:
        auth.authenticate_session_token(
            session,
            session_token=active_token,
            token_secret="session-secret",
            idle_timeout=policy.session_idle_timeout,
        )
        session.commit()

    monkeypatch.setattr(auth, "utc_now", lambda: started_at + timedelta(minutes=11))
    with session_factory() as session:
        with pytest.raises(auth.PortalSessionInvalidError):
            auth.authenticate_session_token(
                session,
                session_token=idle_token,
                token_secret="session-secret",
                idle_timeout=policy.session_idle_timeout,
            )

    monkeypatch.setattr(auth, "utc_now", lambda: started_at + timedelta(minutes=61))
    with session_factory() as session:
        with pytest.raises(auth.PortalSessionInvalidError):
            auth.authenticate_session_token(
                session,
                session_token=active_token,
                token_secret="session-secret",
                idle_timeout=policy.session_idle_timeout,
            )


def test_successful_login_rehashes_legacy_argon2_parameters() -> None:
    engine = create_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)
    policy = main.auth_policy_from_settings(Settings(environment="development", require_mfa=False))
    legacy_hasher = PasswordHasher(
        time_cost=1,
        memory_cost=8192,
        parallelism=1,
    )
    legacy_hash = legacy_hasher.hash(TEST_PASSWORD)
    now = datetime.now(UTC)

    with session_factory() as session:
        account = PatientPortalAccount(
            clinic_id="default",
            demographic_no=1234,
            username="rehash.patient",
            email="rehash.patient@example.test",
            preferred_mfa_method="email",
            password_hash=legacy_hash,
            status=ACCOUNT_STATUS_ACTIVE,
            created_at=now,
            updated_at=now,
            password_updated_at=now,
        )
        session.add(account)
        session.commit()

    with session_factory() as session:
        result = auth.start_login(
            session,
            username="rehash.patient",
            password=TEST_PASSWORD,
            client_reference_hash="c" * 64,
            policy=policy,
            token_secret="session-secret",
            mfa_code_secret="mfa-secret",
            clinic_id="default",
        )
        session.commit()
        refreshed_hash = result.account.password_hash

    assert result.session_token is not None
    assert refreshed_hash != legacy_hash
    assert auth.password_hasher.verify(refreshed_hash, TEST_PASSWORD)


def test_transient_cleanup_retains_accepted_invites_and_rechecks_status() -> None:
    engine = create_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)
    now = datetime(2026, 7, 28, tzinfo=UTC)
    expired_at = now - timedelta(days=60)
    created_at = expired_at - timedelta(days=7)

    with session_factory() as session:
        account = PatientPortalAccount(
            clinic_id="default",
            demographic_no=1234,
            username="retention.patient",
            email="retention.patient@example.test",
            preferred_mfa_method="email",
            password_hash=hash_password(TEST_PASSWORD),
            status=ACCOUNT_STATUS_ACTIVE,
            created_at=created_at,
            updated_at=created_at,
            password_updated_at=created_at,
        )
        session.add(account)
        session.flush()
        invites = [
            PatientPortalInvite(
                clinic_id="default",
                demographic_no=demographic_no,
                token_hash=f"{demographic_no:064x}",
                status=invite_status,
                created_by="CarlosDoc",
                created_at=created_at,
                updated_at=created_at,
                sent_count=1,
                last_sent_at=created_at,
                last_sent_by="CarlosDoc",
                expires_at=expired_at,
                accepted_at=created_at if invite_status == INVITE_STATUS_ACCEPTED else None,
                accepted_account_id=(
                    account.id if invite_status == INVITE_STATUS_ACCEPTED else None
                ),
                revoked_at=created_at if invite_status == INVITE_STATUS_REVOKED else None,
                revoked_by="CarlosDoc" if invite_status == INVITE_STATUS_REVOKED else None,
            )
            for demographic_no, invite_status in (
                (1234, INVITE_STATUS_ACCEPTED),
                (1235, INVITE_STATUS_PENDING),
                (1236, INVITE_STATUS_REVOKED),
            )
        ]
        session.add_all(invites)
        session.commit()

    with session_factory() as session:
        result = cleanup_transient_auth_rows(
            session,
            before=now - timedelta(days=30),
        )
        session.commit()
        remaining_statuses = set(session.scalars(select(PatientPortalInvite.status)))

    assert result.invites == 2
    assert remaining_statuses == {INVITE_STATUS_ACCEPTED}


def test_transient_cleanup_unlinks_a_retained_replacement_invite() -> None:
    engine = create_portal_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)
    now = datetime(2026, 7, 28, tzinfo=UTC)
    old_expiry = now - timedelta(days=60)
    new_expiry = now + timedelta(days=7)

    with session_factory() as session:
        predecessor = PatientPortalInvite(
            clinic_id="default",
            demographic_no=1234,
            token_hash="1" * 64,
            status=INVITE_STATUS_SUPERSEDED,
            created_by="CarlosDoc",
            created_at=old_expiry - timedelta(days=7),
            updated_at=old_expiry,
            sent_count=1,
            last_sent_at=old_expiry,
            last_sent_by="CarlosDoc",
            expires_at=old_expiry,
        )
        session.add(predecessor)
        session.flush()
        replacement = PatientPortalInvite(
            clinic_id="default",
            demographic_no=1234,
            token_hash="2" * 64,
            status=INVITE_STATUS_PENDING,
            created_by="CarlosDoc",
            created_at=now,
            updated_at=now,
            sent_count=1,
            last_sent_at=now,
            last_sent_by="CarlosDoc",
            expires_at=new_expiry,
            supersedes_invite_id=predecessor.id,
        )
        session.add(replacement)
        session.commit()
        replacement_id = replacement.id

    with session_factory() as session:
        result = cleanup_transient_auth_rows(
            session,
            before=now - timedelta(days=30),
        )
        session.commit()
        retained_replacement = session.get(PatientPortalInvite, replacement_id)

    assert result.invites == 1
    assert retained_replacement is not None
    assert retained_replacement.status == INVITE_STATUS_PENDING
    assert retained_replacement.supersedes_invite_id is None


def test_unlock_secret_rejects_tampered_algorithm_metadata() -> None:
    engine = create_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    with Session(engine) as session:
        created = create_unlock_secret(
            session,
            clinic_id="default",
            demographic_no=1234,
            created_by="CarlosDoc",
            encryption_secret="u" * SECRET_LENGTH,
            source_reference="algorithm-test",
        )
        created.unlock_secret.encryption_algorithm = "unknown"
        with pytest.raises(UnlockSecretDecryptionError, match="metadata"):
            decrypt_unlock_secret_payload(
                created.unlock_secret,
                encryption_secret="u" * SECRET_LENGTH,
            )


def test_populated_v2_downgrade_fails_before_schema_changes(tmp_path) -> None:
    database_path = tmp_path / "migration.db"
    config = Config()
    config.set_main_option(
        "script_location",
        "carlos_patient_portal:migrations",
    )
    config.set_main_option("sqlalchemy.url", f"sqlite+pysqlite:///{database_path}")
    command.upgrade(config, "0002_staff_identity_audit")
    engine = create_engine(f"sqlite+pysqlite:///{database_path}")
    with engine.begin() as connection:
        connection.execute(
            text(
                "insert into patient_portal_audit_events "
                "(event_type, outcome, actor_type, created_at) "
                "values ('fhir.read', 'success', 'patient', :created_at)"
            ),
            {"created_at": "2026-07-28 00:00:00+00:00"},
        )

    with pytest.raises(RuntimeError, match="FHIR audit"):
        command.downgrade(config, "0001_patient_portal_invites")

    with engine.begin() as connection:
        connection.execute(text("delete from patient_portal_audit_events"))
        connection.execute(
            text(
                "insert into patient_portal_invites "
                "(clinic_id, demographic_no, token_hash, status, created_by, "
                "created_by_id, created_at, updated_at, sent_count, last_sent_at, "
                "last_sent_by, last_sent_by_id, expires_at) "
                "values ('clinic-a', 1234, :token_hash, 'pending', 'CarlosDoc', "
                "'provider-42', :created_at, :created_at, 1, :created_at, "
                "'CarlosDoc', 'provider-42', :expires_at)"
            ),
            {
                "token_hash": "1" * 64,
                "created_at": "2026-07-28 00:00:00+00:00",
                "expires_at": "2026-08-04 00:00:00+00:00",
            },
        )
    with pytest.raises(RuntimeError, match="staff identity"):
        command.downgrade(config, "0001_patient_portal_invites")

    with engine.connect() as connection:
        columns = {
            row[1]
            for row in connection.execute(text("pragma table_info(patient_portal_audit_events)"))
        }
        invite_columns = {
            row[1]
            for row in connection.execute(text("pragma table_info(patient_portal_invites)"))
        }
    assert {"actor_id", "resource_type", "resource_id"} <= columns
    assert {"created_by_id", "last_sent_by_id"} <= invite_columns
