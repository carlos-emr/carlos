import logging
import re
from concurrent.futures import ThreadPoolExecutor
from dataclasses import fields
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from urllib.parse import parse_qs, urlsplit

import pytest
from alembic.config import Config
from alembic.runtime.migration import MigrationContext
from alembic.script import ScriptDirectory
from fastapi.testclient import TestClient
from fhir.resources.bundle import Bundle
from fhir.resources.capabilitystatement import CapabilityStatement
from fhir.resources.documentreference import DocumentReference
from fhir.resources.operationoutcome import OperationOutcome
from fhir.resources.organization import Organization
from fhir.resources.patient import Patient
from fhir.resources.practitioner import Practitioner
from jinja2 import meta
from pydantic import ValidationError
from sqlalchemy import select
from sqlalchemy.engine import make_url
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session

from carlos_patient_portal import cli, main, presenters
from carlos_patient_portal.account_settings import update_account_mfa_method
from carlos_patient_portal.audit import hash_sensitive_reference, record_audit_event
from carlos_patient_portal.auth import MfaChallengeDelivery
from carlos_patient_portal.config import (
    DEFAULT_AUDIT_RETENTION_DAYS,
    DEFAULT_DATABASE_URL,
    MIN_PRODUCTION_SECRET_LENGTH,
    Settings,
)
from carlos_patient_portal.database import (
    Base,
    create_portal_engine,
    create_session_factory,
    session_scope,
)
from carlos_patient_portal.email_delivery import PortalEmailDeliveryError, PortalEmailSender
from carlos_patient_portal.i18n import (
    DEFAULT_LOCALE,
    SUPPORTED_LOCALES,
    format_portal_datetime,
    portal_text,
)
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.interop import (
    FHIR_RELEASE,
    FHIR_VERSION,
    HL7_PATIENT_REGISTRATION_PROFILE_ID,
    HL7_V2_VERSION,
    Hl7ConformanceProfileError,
    PortalPatientInteroperabilityIdentity,
    build_fhir_patient_id,
    build_fhir_practitioner_id,
    build_fhir_r4_patient,
    build_fhir_r4_practitioner,
    build_hl7_v251_patient_registration,
    load_hl7_v251_patient_registration_profile,
    validate_hl7_v251_message,
    validate_hl7_v251_patient_registration_profile,
)
from carlos_patient_portal.invites import (
    DEFAULT_INVITE_TTL,
    InviteNotFoundError,
    create_invite,
    hash_invite_token,
    list_invites,
    resend_invite,
    revoke_invite,
)
from carlos_patient_portal.maintenance import (
    BackupDestinationExistsError,
    BackupUnsupportedError,
    audit_retention_cutoff,
    backup_sqlite_database,
    cleanup_transient_auth_rows,
    prune_audit_events,
    restore_sqlite_database,
)
from carlos_patient_portal.models import (
    AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
    AUDIT_EVENT_ACCOUNT_LOCK,
    AUDIT_EVENT_ACCOUNT_MFA_UPDATE,
    AUDIT_EVENT_ACCOUNT_PASSWORD_CHANGE,
    AUDIT_EVENT_ACCOUNT_UNLOCK,
    AUDIT_EVENT_ACTIVATION,
    AUDIT_EVENT_INVITE_CREATE,
    AUDIT_EVENT_INVITE_LIST,
    AUDIT_EVENT_INVITE_RESEND,
    AUDIT_EVENT_INVITE_REVOKE,
    AUDIT_EVENT_LOGIN,
    AUDIT_EVENT_MFA_CHALLENGE,
    AUDIT_EVENT_MFA_DELIVERY,
    AUDIT_EVENT_MFA_RESEND,
    AUDIT_EVENT_MFA_VERIFY,
    AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
    AUDIT_EVENT_PASSWORD_RESET_DELIVERY,
    AUDIT_EVENT_PASSWORD_RESET_REQUEST,
    AUDIT_EVENT_SESSION_LOGOUT,
    AUDIT_EVENT_UNLOCK_SECRET_CREATE,
    AUDIT_EVENT_UNLOCK_SECRET_LIST,
    AUDIT_EVENT_UNLOCK_SECRET_READ,
    AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    AUDIT_OUTCOME_THROTTLED,
    CONTACT_REVIEW_STATUS_PENDING,
    INVITE_STATUS_ACCEPTED,
    INVITE_STATUS_PENDING,
    INVITE_STATUS_REVOKED,
    SESSION_REVOKED_REASON_PASSWORD_CHANGE,
    UNLOCK_SECRET_NONCE_LENGTH,
    UNLOCK_SECRET_STATUS_REVOKED,
    UNLOCK_SECRET_TYPE_EMAIL,
    UNLOCK_SECRET_TYPE_PDF,
    PatientPortalAccount,
    PatientPortalAuditEvent,
    PatientPortalContactReviewRequest,
    PatientPortalInvite,
    PatientPortalMfaChallenge,
    PatientPortalPasswordResetToken,
    PatientPortalSession,
    PatientPortalUnlockSecret,
    utc_now,
)
from carlos_patient_portal.sms_delivery import PortalSmsDeliveryError, PortalSmsSender
from carlos_patient_portal.unlock_secrets import (
    MAX_UNLOCK_SECRET_PROVIDER_OPTIONS,
    UnlockSecretDecryptionError,
    UnlockSecretNotFoundError,
    UnlockSecretRevokedError,
    count_unlock_secrets,
    create_unlock_secret,
    generate_unlock_secret_value,
    list_unlock_secret_provider_options,
    list_unlock_secret_providers,
    list_unlock_secrets,
    read_unlock_secret,
    revoke_unlock_secret,
)
from carlos_patient_portal.view_models import (
    EmailPasswordDashboardViewModel,
    EmailPasswordRowViewModel,
    ProviderFilterOptionViewModel,
)

NON_DEVELOPMENT_SESSION_SECRET = "s" * MIN_PRODUCTION_SECRET_LENGTH
IDENTITY_PROOF_SECRET = "i" * MIN_PRODUCTION_SECRET_LENGTH
AUDIT_HASH_SECRET = "a" * MIN_PRODUCTION_SECRET_LENGTH
UNLOCK_SECRET_ENCRYPTION_SECRET = "u" * MIN_PRODUCTION_SECRET_LENGTH
INTERNAL_HEALTH_TOKEN = "h" * MIN_PRODUCTION_SECRET_LENGTH
INTERNAL_API_TOKEN = "c" * MIN_PRODUCTION_SECRET_LENGTH
WRONG_INTERNAL_HEALTH_TOKEN = "w" * MIN_PRODUCTION_SECRET_LENGTH
DEV_ADMIN_TOKEN = "d" * MIN_PRODUCTION_SECRET_LENGTH
WRONG_DEV_ADMIN_TOKEN = "x" * MIN_PRODUCTION_SECRET_LENGTH
CSRF_TOKEN_PATTERN = re.compile(r'name="csrf_token" value="([^"]+)"')
DEVELOPMENT_MFA_CODE_PATTERN = re.compile(
    r"Development MFA code \(same code as sent by email, to make testing quicker, "
    r"will be removed later\): (\d{6})"
)
MFA_CHALLENGE_TOKEN_PATTERN = re.compile(r'name="mfa_challenge_token" value="([^"]+)"')
SEEDED_INVITE_EMAIL = "example.patient@example.com"
SEEDED_INVITE_DOB = "1980-05-20"
SEEDED_INVITE_HCN = "ABCD 1234-5678"
STRONG_PASSWORD = "Stronger1!word"
STRONG_RESET_PASSWORD = "Changed1!word"
CONCURRENT_WRONG_PASSWORD = "".join(("Wrong", "2026", "!!"))
TEST_CLINIC_ID = "test-clinic"
TEST_CLINIC_NAME = "Test Clinic"


def development_settings(**overrides: object) -> Settings:
    values = {"environment": "development", **overrides}
    return Settings(**values)


def production_settings(**overrides: object) -> Settings:
    values = {
        "environment": "production",
        "clinic_id": TEST_CLINIC_ID,
        "clinic_name": TEST_CLINIC_NAME,
        "session_secret": NON_DEVELOPMENT_SESSION_SECRET,
        "identity_proof_secret": IDENTITY_PROOF_SECRET,
        "audit_hash_secret": AUDIT_HASH_SECRET,
        "unlock_secret_encryption_secret": UNLOCK_SECRET_ENCRYPTION_SECRET,
        "internal_health_token": INTERNAL_HEALTH_TOKEN,
        "internal_api_token": INTERNAL_API_TOKEN,
        "smtp_host": "mail.internal",
        "smtp_from_address": "portal@example.test",
        "smtp_starttls": True,
        "public_base_url": "https://portal.example.test",
        "sms_webhook_url": "https://sms.example.test/messages",
        "sms_webhook_token": "sms-webhook-token-value-32-characters",
        **overrides,
    }
    return Settings(**values)


def migrated_development_app(
    *,
    email_sender: PortalEmailSender | None = None,
    sms_sender: PortalSmsSender | None = None,
    **overrides: object,
) -> main.FastAPI:
    settings_values = {
        "database_url": "sqlite+pysqlite:///:memory:",
        "enable_dev_admin": True,
        "dev_admin_token": DEV_ADMIN_TOKEN,
        "identity_proof_secret": IDENTITY_PROOF_SECRET,
        "audit_hash_secret": AUDIT_HASH_SECRET,
        "unlock_secret_encryption_secret": UNLOCK_SECRET_ENCRYPTION_SECRET,
        **overrides,
    }
    app = main.create_app(
        development_settings(**settings_values),
        email_sender=email_sender,
        sms_sender=sms_sender,
    )
    Base.metadata.create_all(app.state.database_engine)
    alembic_config = Config()
    alembic_config.set_main_option("script_location", str(main.PACKAGE_DIR / "migrations"))
    migration_scripts = ScriptDirectory.from_config(alembic_config)
    with app.state.database_engine.begin() as connection:
        MigrationContext.configure(connection).stamp(
            migration_scripts,
            migration_scripts.get_current_head(),
        )
    return app


class RecordingPortalEmailSender:
    def __init__(self, *, fail: bool = False) -> None:
        self.fail = fail
        self.app: main.FastAPI | None = None
        self.challenge_was_committed = False
        self.messages: list[dict[str, object]] = []

    def send_code(
        self,
        *,
        recipient: str,
        code: str,
        expires_in_seconds: int,
    ) -> None:
        if self.app is not None:
            with self.app.state.session_factory() as session:
                self.challenge_was_committed = (
                    session.scalar(select(PatientPortalMfaChallenge.id)) is not None
                )
        self.messages.append(
            {
                "recipient": recipient,
                "code": code,
                "expires_in_seconds": expires_in_seconds,
            }
        )
        if self.fail:
            raise PortalEmailDeliveryError("simulated delivery failure")

    def send_password_reset(
        self,
        *,
        recipient: str,
        reset_url: str,
        expires_in_seconds: int,
    ) -> None:
        self.messages.append(
            {
                "recipient": recipient,
                "reset_url": reset_url,
                "expires_in_seconds": expires_in_seconds,
            }
        )
        if self.fail:
            raise PortalEmailDeliveryError("simulated delivery failure")

    def send_contact_change_notice(self, *, recipient: str) -> None:
        self.messages.append({"recipient": recipient, "type": "contact_change_notice"})
        if self.fail:
            raise PortalEmailDeliveryError("simulated delivery failure")


class RecordingPortalSmsSender:
    def __init__(self, *, fail: bool = False) -> None:
        self.fail = fail
        self.messages: list[dict[str, object]] = []

    def send_code(
        self,
        *,
        recipient: str,
        code: str,
        expires_in_seconds: int,
    ) -> None:
        self.messages.append(
            {
                "recipient": recipient,
                "code": code,
                "expires_in_seconds": expires_in_seconds,
            }
        )
        if self.fail:
            raise PortalSmsDeliveryError("simulated delivery failure")


def dev_admin_headers(
    token: str = DEV_ADMIN_TOKEN,
    *,
    actor: str = "CarlosDoc",
) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {token}",
        main.DEV_ADMIN_ACTOR_HEADER: actor,
    }


def get_csrf_token(client: TestClient) -> str:
    response = client.get("/")
    match = CSRF_TOKEN_PATTERN.search(response.text)

    assert response.status_code == 200
    assert match is not None
    csrf_token = match.group(1)
    assert response.cookies.get(main.CSRF_COOKIE_NAME) == csrf_token
    return csrf_token


def csrf_token_from_response(response: object) -> str:
    match = CSRF_TOKEN_PATTERN.search(str(getattr(response, "text", "")))
    assert match is not None
    return match.group(1)


def parse_response_datetime(value: str) -> datetime:
    parsed_datetime = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed_datetime.tzinfo is None:
        return parsed_datetime.replace(tzinfo=UTC)
    return parsed_datetime


def seeded_invite_request(**overrides: object) -> dict[str, object]:
    request_payload: dict[str, object] = {
        "demographic_no": 1234,
        "email": SEEDED_INVITE_EMAIL,
        "date_of_birth": SEEDED_INVITE_DOB,
        "health_card_number": SEEDED_INVITE_HCN,
    }
    request_payload.update(overrides)
    return request_payload


def seeded_identity_proof(**overrides: object) -> IdentityProof:
    proof_values: dict[str, object] = {
        "email": SEEDED_INVITE_EMAIL,
        "date_of_birth": datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        "health_card_number": SEEDED_INVITE_HCN,
    }
    proof_values.update(overrides)
    return IdentityProof(**proof_values)


def create_service_invite(
    session: Session,
    demographic_no: int = 1234,
    actor: str = "CarlosDoc",
    *,
    clinic_id: str = "default",
    identity_proof: IdentityProof | None = None,
) -> tuple[PatientPortalInvite, str]:
    return create_invite(
        session,
        demographic_no,
        actor,
        identity_proof=identity_proof or seeded_identity_proof(),
        proof_secret=IDENTITY_PROOF_SECRET,
        clinic_id=clinic_id,
    )


def activation_request(invite_code: str, **overrides: object) -> dict[str, object]:
    request_payload: dict[str, object] = {
        "invite_code": invite_code,
        "email": f" {SEEDED_INVITE_EMAIL.upper()} ",
        "date_of_birth": SEEDED_INVITE_DOB,
        "health_card_number": "ABCD-1234 5678",
        "username": "Patient.User",
        "password": STRONG_PASSWORD,
    }
    request_payload.update(overrides)
    return request_payload


def activate_seeded_patient_account(
    app: main.FastAPI,
    client: TestClient,
    *,
    username: str = "patient.user",
    password: str = STRONG_PASSWORD,
    demographic_no: int = 1234,
    email: str = SEEDED_INVITE_EMAIL,
    health_card_number: str = SEEDED_INVITE_HCN,
) -> int:
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(
            demographic_no=demographic_no,
            email=email,
            health_card_number=health_card_number,
        ),
    )
    assert create_response.status_code == 201
    activate_response = client.post(
        "/auth/activate",
        json=activation_request(
            create_response.json()["invite_token"],
            username=username,
            password=password,
            email=email,
            health_card_number=health_card_number,
        ),
    )
    assert activate_response.status_code == 201
    with app.state.session_factory() as session:
        account_id = session.scalar(
            select(PatientPortalAccount.id).where(PatientPortalAccount.username == username)
        )
    assert account_id is not None
    return account_id


def browser_sign_in_seeded_patient(app: main.FastAPI, client: TestClient) -> int:
    account_id = activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )

    assert login_response.status_code == 200
    assert "Verification code" in login_response.text
    assert main.PORTAL_SESSION_COOKIE_NAME not in login_response.cookies
    mfa_challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    mfa_code_match = DEVELOPMENT_MFA_CODE_PATTERN.search(login_response.text)
    csrf_token_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert mfa_challenge_token_match is not None
    assert mfa_code_match is not None
    assert csrf_token_match is not None

    verify_response = client.post(
        "/auth/mfa/verify",
        data={
            "csrf_token": csrf_token_match.group(1),
            "mfa_challenge_token": mfa_challenge_token_match.group(1),
            "code": mfa_code_match.group(1),
        },
        follow_redirects=False,
    )

    assert verify_response.status_code == 303
    assert verify_response.headers["location"] == "/portal"
    set_cookie_header = verify_response.headers.get("set-cookie", "")
    assert main.PORTAL_SESSION_COOKIE_NAME in verify_response.cookies
    assert f"{main.PORTAL_SESSION_COOKIE_NAME}=" in set_cookie_header
    assert "HttpOnly" in set_cookie_header
    assert "Path=/portal" in set_cookie_header
    assert "SameSite=strict" in set_cookie_header
    return account_id


def sign_in_patient_api_session(
    client: TestClient,
    *,
    username: str = "patient.user",
    password: str = STRONG_PASSWORD,
) -> str:
    login_response = client.post(
        "/auth/login",
        json={"username": username, "password": password},
    )
    assert login_response.status_code == 200
    login_payload = login_response.json()
    verify_response = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": login_payload["mfa_challenge_token"],
            "code": login_payload["development_mfa_code"],
        },
    )

    assert verify_response.status_code == 200
    return str(verify_response.json()["session_token"])


def bearer_headers(session_token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {session_token}"}


def expire_email_mfa_cooldown(app) -> None:
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.last_mfa_email_sent_at is not None
        account.last_mfa_email_sent_at -= timedelta(seconds=61)
        session.commit()


def test_health_endpoint_is_minimal() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            clinic_id=TEST_CLINIC_ID,
            clinic_name=TEST_CLINIC_NAME,
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            unlock_secret_encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_dashboard_datetime_and_date_boundary_use_clinic_timezone() -> None:
    assert (
        format_portal_datetime(
            datetime(2026, 1, 15, 5, 30, tzinfo=UTC),
            timezone_name="America/Toronto",
        )
        == "2026-01-15 00:30 EST"
    )
    assert presenters.dashboard_created_before(
        datetime(2026, 7, 15).date(),
        timezone_name="America/Toronto",
    ) == datetime(2026, 7, 16, 4, 0, tzinfo=UTC)


def test_operational_metrics_are_protected_and_request_logs_are_phi_safe(
    caplog: pytest.LogCaptureFixture,
) -> None:
    caplog.set_level(logging.INFO, logger="carlos_patient_portal.main")
    app = migrated_development_app(internal_health_token=INTERNAL_HEALTH_TOKEN)
    client = TestClient(app)

    response = client.get("/health", headers={"X-Request-ID": "portal-check-123"})
    hidden = client.get("/internal/metrics")
    metrics = client.get(
        "/internal/metrics",
        headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
    )

    assert response.headers["X-Request-ID"] == "portal-check-123"
    assert hidden.status_code == 404
    assert metrics.status_code == 200
    assert metrics.json()["requests"]["2xx"] >= 1
    request_logs = [
        record.message for record in caplog.records if '"event":"http_request"' in record.message
    ]
    assert request_logs
    assert all("?" not in message and "portal-check-123" in message for message in request_logs[:1])


def test_file_sqlite_concurrent_login_failures_do_not_return_raw_500(tmp_path) -> None:
    database_path = tmp_path / "concurrent-login.db"
    app = migrated_development_app(
        database_url=f"sqlite+pysqlite:///{database_path}",
        auth_max_failed_password_attempts=100,
        sqlite_busy_timeout_ms=10_000,
    )
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    with ThreadPoolExecutor(max_workers=6) as executor:
        responses = list(
            executor.map(
                lambda _: client.post(
                    "/auth/login",
                    json={
                        "username": "patient.user",
                        "password": CONCURRENT_WRONG_PASSWORD,
                    },
                ),
                range(6),
            )
        )

    assert all(response.status_code in {401, 503} for response in responses)
    assert all(response.status_code != 500 for response in responses)


def test_transient_auth_cleanup_is_batched_and_preserves_current_credentials() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    sign_in_patient_api_session(client)
    expire_email_mfa_cooldown(app)
    sign_in_patient_api_session(client)
    client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    with app.state.session_factory() as session:
        with session.begin():
            create_service_invite(
                session,
                demographic_no=5678,
                identity_proof=seeded_identity_proof(
                    email="other.patient@example.com",
                    health_card_number="WXYZ 9876-5432",
                ),
            )
            old_created_at = datetime.now(UTC) - timedelta(days=50)
            old_expiry = datetime.now(UTC) - timedelta(days=40)
            sessions = list(
                session.scalars(select(PatientPortalSession).order_by(PatientPortalSession.id))
            )
            sessions[0].created_at = old_created_at
            sessions[0].expires_at = old_expiry
            challenge = session.scalar(
                select(PatientPortalMfaChallenge).order_by(PatientPortalMfaChallenge.id)
            )
            reset = session.scalar(select(PatientPortalPasswordResetToken))
            invite = session.scalar(
                select(PatientPortalInvite).where(PatientPortalInvite.demographic_no == 5678)
            )
            assert challenge is not None
            assert reset is not None
            assert invite is not None
            challenge.created_at = old_created_at
            challenge.expires_at = old_expiry
            reset.created_at = old_created_at
            reset.expires_at = old_expiry
            invite.created_at = old_created_at
            invite.expires_at = old_expiry

        cutoff = datetime.now(UTC) - timedelta(days=30)
        with session.begin():
            dry_run = cleanup_transient_auth_rows(
                session,
                before=cutoff,
                dry_run=True,
            )
        assert dry_run.total == 4
        with session.begin():
            deleted = cleanup_transient_auth_rows(session, before=cutoff)
        assert deleted.total == 4
        assert session.scalar(select(PatientPortalSession.id)) is not None


def test_index_renders_sign_in_shell() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get("/")
    text = portal_text(DEFAULT_LOCALE)

    assert response.status_code == 200
    assert text["username_placeholder"] == "username"
    assert text["password_placeholder"] == "password"
    assert "CARLOS Patient Portal" in response.text
    assert 'src="http://testserver/static/carlos-logo.png"' in response.text
    assert f'placeholder="{text["username_placeholder"]}"' in response.text
    assert f'placeholder="{text["password_placeholder"]}"' in response.text
    assert f">{text['forgot_username_password']}</a>" in response.text
    assert f">{text['activate_account']}</a>" in response.text
    assert text["language_unavailable_message"] in response.text
    assert 'data-modal-title="' in response.text
    assert 'id="portal-message-modal"' in response.text
    assert 'src="http://testserver/static/portal.js"' in response.text
    for locale in SUPPORTED_LOCALES:
        assert f'data-language-code="{locale.code}"' in response.text
    assert f'value="{text["username_placeholder"]}"' not in response.text
    assert 'name="csrf_token"' in response.text
    assert "nosemgrep" not in response.text
    assert "Maple Creek Medical" in response.text


def test_browser_activation_form_creates_account_without_repopulating_proof_values() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    invite_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    activation_page = client.get("/auth/activate")

    assert activation_page.status_code == 200
    assert 'type="date"' in activation_page.text
    assert "Activate your account" in activation_page.text

    activation_response = client.post(
        "/auth/activate",
        data={
            "csrf_token": csrf_token_from_response(activation_page),
            "invite_code": invite_response.json()["invite_token"],
            "email": SEEDED_INVITE_EMAIL,
            "date_of_birth": SEEDED_INVITE_DOB,
            "health_card_number": SEEDED_INVITE_HCN,
            "username": "browser.patient",
            "password": STRONG_PASSWORD,
            "password_confirmation": STRONG_PASSWORD,
        },
    )

    assert activation_response.status_code == 201
    assert "Account activated" in activation_response.text
    assert SEEDED_INVITE_HCN not in activation_response.text
    assert invite_response.json()["invite_token"] not in activation_response.text
    with app.state.session_factory() as session:
        account = session.scalar(
            select(PatientPortalAccount).where(PatientPortalAccount.username == "browser.patient")
        )
        assert account is not None


def test_browser_activation_rejects_password_mismatch_without_echoing_secrets() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activation_page = client.get("/auth/activate")

    response = client.post(
        "/auth/activate",
        data={
            "csrf_token": csrf_token_from_response(activation_page),
            "invite_code": "sensitive-invite-code",
            "email": SEEDED_INVITE_EMAIL,
            "date_of_birth": SEEDED_INVITE_DOB,
            "health_card_number": SEEDED_INVITE_HCN,
            "username": "browser.patient",
            "password": STRONG_PASSWORD,
            "password_confirmation": "Different1!word",  # ggignore
        },
    )

    assert response.status_code == 400
    assert "password confirmation does not match" in response.text.lower()
    assert "sensitive-invite-code" not in response.text
    assert SEEDED_INVITE_HCN not in response.text
    assert STRONG_PASSWORD not in response.text


def test_static_logo_asset_is_served() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get("/static/carlos-logo.png")

    assert response.status_code == 200
    assert "image/png" in response.headers["content-type"]
    assert response.content.startswith(b"\x89PNG")


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


def test_jinja_templates_always_autoescape_jinja_files() -> None:
    assert main.templates.env.autoescape is True


def test_production_responses_include_hsts() -> None:
    app = main.create_app(production_settings())
    response = TestClient(app, base_url="https://portal.example.test").get("/")

    assert response.headers["strict-transport-security"] == ("max-age=31536000; includeSubDomains")


def test_non_development_csrf_cookie_is_secure() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            clinic_id=TEST_CLINIC_ID,
            clinic_name=TEST_CLINIC_NAME,
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            unlock_secret_encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).get("/")
    set_cookie = response.headers["set-cookie"]

    assert f"{main.CSRF_COOKIE_NAME}=" in set_cookie
    assert "HttpOnly" in set_cookie
    assert "Path=/auth" in set_cookie
    assert "SameSite=strict" in set_cookie
    assert "Secure" in set_cookie


def test_non_development_sign_in_shows_generic_field_hints() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            clinic_id=TEST_CLINIC_ID,
            clinic_name=TEST_CLINIC_NAME,
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            unlock_secret_encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).get("/")
    text = portal_text(DEFAULT_LOCALE)

    assert response.status_code == 200
    assert f'placeholder="{text["username_placeholder"]}"' in response.text
    assert f'placeholder="{text["password_placeholder"]}"' in response.text


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
    assert (
        client.get(
            "/internal/health/db",
            headers={"Authorization": f"Bearer {WRONG_INTERNAL_HEALTH_TOKEN}"},
        ).status_code
        == 404
    )
    assert (
        client.get(
            "/internal/health/db",
            headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
        ).status_code
        == 200
    )


def test_internal_readiness_reports_maintenance_without_hiding_liveness() -> None:
    app = migrated_development_app(
        internal_health_token=INTERNAL_HEALTH_TOKEN,
        maintenance_mode=True,
        maintenance_retry_after_seconds=120,
    )
    client = TestClient(app)

    sign_in_response = client.get("/")
    fhir_response = client.get("/fhir/metadata")
    public_health_response = client.get("/health")
    db_health_response = client.get(
        "/internal/health/db",
        headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
    )
    readiness_response = client.get(
        "/internal/readiness",
        headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
    )

    assert sign_in_response.status_code == 503
    assert sign_in_response.json()["detail"] == "service temporarily unavailable"
    assert sign_in_response.headers["retry-after"] == "120"
    assert sign_in_response.headers["cache-control"] == "no-store"
    assert fhir_response.status_code == 503
    assert fhir_response.headers["content-type"].startswith(main.FHIR_JSON_MEDIA_TYPE)
    assert fhir_response.json()["resourceType"] == "OperationOutcome"
    assert public_health_response.status_code == 200
    assert public_health_response.json() == {"status": "ok"}
    assert db_health_response.status_code == 200
    assert db_health_response.json() == {"status": "ok", "database": "ok"}
    assert readiness_response.status_code == 503
    assert readiness_response.headers["retry-after"] == "120"
    assert readiness_response.json() == {
        "status": "maintenance",
        "database": "ok",
        "schema": "ok",
        "maintenance": True,
    }


def test_internal_readiness_rejects_unmigrated_database() -> None:
    app = main.create_app(
        development_settings(
            database_url="sqlite+pysqlite:///:memory:",
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )

    response = TestClient(app).get(
        "/internal/readiness",
        headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
    )

    assert response.status_code == 503
    assert response.json() == {
        "status": "unavailable",
        "database": "ok",
        "schema": "mismatch",
        "maintenance": False,
    }


def test_global_rate_limit_throttles_patient_routes_and_exempts_health() -> None:
    app = main.create_app(
        development_settings(
            global_rate_limit_max_requests=2,
            global_rate_limit_window_seconds=60,
        )
    )
    client = TestClient(app)

    first_response = client.get("/")
    second_response = client.get("/")
    throttled_response = client.get("/")
    health_response = client.get("/health")

    assert first_response.status_code == 200
    assert second_response.status_code == 200
    assert throttled_response.status_code == 429
    assert throttled_response.json()["detail"] == "too many requests"
    assert throttled_response.headers["retry-after"] == "60"
    assert throttled_response.headers["cache-control"] == "no-store"
    assert health_response.status_code == 200


def test_rate_limiter_evicts_oldest_bucket_at_configured_capacity() -> None:
    limiter = main.InMemoryRateLimiter(
        window_seconds=60,
        max_requests=10,
        max_buckets=3,
    )

    for index in range(10):
        assert limiter.retry_after_seconds(f"client-{index}", now=1.0) is None

    assert len(limiter.buckets) == 3
    assert list(limiter.buckets) == ["client-7", "client-8", "client-9"]


def test_login_rate_limit_runs_before_repeated_password_verification() -> None:
    app = migrated_development_app(auth_rate_limit_max_requests=2)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    responses = [
        client.post(
            "/auth/login",
            json={"username": "patient.user", "password": "Wrong1!password"},
        )
        for _ in range(3)
    ]

    assert [response.status_code for response in responses] == [401, 401, 429]


def test_login_mfa_session_and_logout_happy_path() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    login_response = client.post(
        "/auth/login",
        json={"username": "Patient.User", "password": STRONG_PASSWORD},
    )

    assert login_response.status_code == 200
    login_payload = login_response.json()
    assert login_payload["status"] == "mfa_required"
    assert login_payload["mfa_delivery_method"] == "email"
    assert login_payload["mfa_challenge_token"]
    assert re.fullmatch(r"\d{6}", login_payload["development_mfa_code"])
    assert login_payload["session_token"] is None
    assert login_response.headers["cache-control"] == "no-store"

    verify_response = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": login_payload["mfa_challenge_token"],
            "code": login_payload["development_mfa_code"],
        },
    )

    assert verify_response.status_code == 200
    session_token = verify_response.json()["session_token"]
    assert session_token
    assert main.PORTAL_SESSION_COOKIE_NAME not in verify_response.cookies

    session_response = client.get(
        "/auth/session",
        headers={"Authorization": f"Bearer {session_token}"},
    )

    assert session_response.status_code == 200
    assert session_response.json() == {
        "status": "authenticated",
        "username": "patient.user",
        "clinic_id": "default",
        "demographic_no": 1234,
    }

    logout_response = client.post(
        "/auth/logout",
        headers={"Authorization": f"Bearer {session_token}"},
    )
    expired_session_response = client.get(
        "/auth/session",
        headers={"Authorization": f"Bearer {session_token}"},
    )

    assert logout_response.status_code == 200
    assert logout_response.json() == {"status": "logged_out"}
    assert expired_session_response.status_code == 401
    with app.state.session_factory() as session:
        portal_session = session.scalar(select(PatientPortalSession))
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type.in_(
                        [
                            AUDIT_EVENT_LOGIN,
                            AUDIT_EVENT_MFA_CHALLENGE,
                            AUDIT_EVENT_MFA_DELIVERY,
                            AUDIT_EVENT_MFA_VERIFY,
                            AUDIT_EVENT_SESSION_LOGOUT,
                        ]
                    )
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert portal_session is not None
        assert portal_session.account_id == account_id
        assert portal_session.revoked_reason == "logout"
        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_MFA_CHALLENGE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_LOGIN, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_MFA_DELIVERY, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_MFA_VERIFY, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_SESSION_LOGOUT, AUDIT_OUTCOME_SUCCESS),
            # Re-using the revoked token is a rejected authentication and is now audited, so a
            # token replayed after logout cannot be probed without leaving a trace.
            (AUDIT_EVENT_LOGIN, AUDIT_OUTCOME_FAILURE),
        ]
        assert audit_events[-1].reason == "authentication_failed"


def test_login_sends_mfa_email_after_committing_challenge() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    sender.app = app
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    response = client.post(
        "/auth/login",
        json={"username": "Patient.User", "password": STRONG_PASSWORD},
    )

    assert response.status_code == 200
    assert sender.challenge_was_committed is True
    assert sender.messages == [
        {
            "recipient": SEEDED_INVITE_EMAIL,
            "code": response.json()["development_mfa_code"],
            "expires_in_seconds": 600,
        }
    ]
    with app.state.session_factory() as session:
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        assert challenge is not None
        assert challenge.last_email_sent_at is not None


def test_login_mfa_delivery_failure_is_generic_and_audited() -> None:
    sender = RecordingPortalEmailSender(fail=True)
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    response = client.post(
        "/auth/login",
        json={"username": "Patient.User", "password": STRONG_PASSWORD},
    )

    assert response.status_code == 503
    assert response.json() == {"detail": "verification code could not be sent"}
    assert len(sender.messages) == 1
    sent_code = sender.messages[0]["code"]
    assert isinstance(sent_code, str)
    assert sent_code not in response.text
    with app.state.session_factory() as session:
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        delivery_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_MFA_DELIVERY
            )
        )

        assert challenge is not None
        assert challenge.last_email_sent_at is None
        assert delivery_event is not None
        assert delivery_event.outcome == AUDIT_OUTCOME_FAILURE
        assert sent_code not in (delivery_event.reason or "")


def test_failed_mfa_method_switch_preserves_the_previous_delivered_code() -> None:
    email_sender = RecordingPortalEmailSender()
    sms_sender = RecordingPortalSmsSender(fail=True)
    app = migrated_development_app(
        email_sender=email_sender,
        sms_sender=sms_sender,
    )
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        with session.begin():
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            account.phone_number = "+16135550199"

    login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    challenge_token = login.json()["mfa_challenge_token"]
    original_code = email_sender.messages[-1]["code"]
    switched = client.post(
        "/auth/mfa/resend",
        json={
            "mfa_challenge_token": challenge_token,
            "mfa_delivery_method": "sms",
        },
    )
    verified = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": challenge_token,
            "code": original_code,
        },
    )

    assert switched.status_code == 503
    assert verified.status_code == 200


def test_mfa_email_resend_delivers_new_code_after_cooldown() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    login_response = client.post(
        "/auth/login",
        json={"username": "Patient.User", "password": STRONG_PASSWORD},
    )
    with app.state.session_factory() as session:
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        assert challenge is not None
        assert challenge.last_email_sent_at is not None
        challenge.last_email_sent_at -= timedelta(seconds=61)
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.last_mfa_email_sent_at is not None
        account.last_mfa_email_sent_at -= timedelta(seconds=61)
        session.commit()

    resend_response = client.post(
        "/auth/mfa/resend",
        json={
            "mfa_challenge_token": login_response.json()["mfa_challenge_token"],
            "mfa_delivery_method": "email",
        },
    )

    assert resend_response.status_code == 200
    assert len(sender.messages) == 2
    assert sender.messages[0]["code"] != sender.messages[1]["code"]
    assert sender.messages[1] == {
        "recipient": SEEDED_INVITE_EMAIL,
        "code": resend_response.json()["development_mfa_code"],
        "expires_in_seconds": 600,
    }


def test_fresh_login_enforces_account_mfa_cooldown_and_carries_failure_budget() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    first = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    immediate = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    failed_code = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": first.json()["mfa_challenge_token"],
            "code": "not-a-code",
        },
    )
    expire_email_mfa_cooldown(app)
    replacement = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )

    assert first.status_code == 200
    assert immediate.status_code == 429
    assert failed_code.status_code == 401
    assert replacement.status_code == 200
    assert len(sender.messages) == 2
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        pending = list(
            session.scalars(
                select(PatientPortalMfaChallenge).where(
                    PatientPortalMfaChallenge.status == "pending"
                )
            )
        )
        assert account is not None
        assert account.failed_mfa_count == 1
        assert len(pending) == 1
        assert pending[0].failed_attempts == 1


def test_form_login_error_renders_sign_in_page() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)

    response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": "wrong",
        },
    )

    assert response.status_code == 401
    assert "Sign in" in response.text
    assert "Incorrect Username or Password" in response.text
    assert response.headers["content-type"].startswith("text/html")


def test_form_mfa_error_keeps_retry_and_resend_screen() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    mfa_challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    mfa_code_match = DEVELOPMENT_MFA_CODE_PATTERN.search(login_response.text)
    csrf_token_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert login_response.status_code == 200
    assert mfa_challenge_token_match is not None
    assert mfa_code_match is not None
    assert csrf_token_match is not None
    invalid_mfa_code = "000000" if mfa_code_match.group(1) != "000000" else "111111"

    response = client.post(
        "/auth/mfa/verify",
        data={
            "csrf_token": csrf_token_match.group(1),
            "mfa_challenge_token": mfa_challenge_token_match.group(1),
            "code": invalid_mfa_code,
        },
    )

    assert response.status_code == 401
    assert "Verification code" in response.text
    assert "The code was not accepted. Try again or request a new code." in response.text
    assert "Resend code" in response.text
    assert 'value="email"' in response.text
    assert 'value="sms"' in response.text
    assert response.headers["content-type"].startswith("text/html")


def test_form_mfa_resend_sends_new_email_after_cooldown() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    original_code_match = DEVELOPMENT_MFA_CODE_PATTERN.search(login_response.text)
    resend_csrf_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert challenge_token_match is not None
    assert original_code_match is not None
    assert resend_csrf_match is not None
    assert "Code sent to ex***@example.com by EMAIL." in login_response.text
    assert "Resend code" in login_response.text
    with app.state.session_factory() as session:
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        assert challenge is not None
        assert challenge.last_email_sent_at is not None
        challenge.last_email_sent_at -= timedelta(seconds=61)
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.last_mfa_email_sent_at is not None
        account.last_mfa_email_sent_at -= timedelta(seconds=61)
        session.commit()

    response = client.post(
        "/auth/mfa/resend",
        data={
            "csrf_token": resend_csrf_match.group(1),
            "mfa_challenge_token": challenge_token_match.group(1),
            "mfa_delivery_method": "email",
        },
    )

    resent_code_match = DEVELOPMENT_MFA_CODE_PATTERN.search(response.text)
    assert response.status_code == 200
    assert "A new code was sent by EMAIL." in response.text
    assert resent_code_match is not None
    assert resent_code_match.group(1) != original_code_match.group(1)
    assert len(sender.messages) == 2
    assert sender.messages[-1]["code"] == resent_code_match.group(1)


def test_form_mfa_resend_shows_cooldown_without_leaving_screen() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    resend_csrf_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert challenge_token_match is not None
    assert resend_csrf_match is not None

    response = client.post(
        "/auth/mfa/resend",
        data={
            "csrf_token": resend_csrf_match.group(1),
            "mfa_challenge_token": challenge_token_match.group(1),
            "mfa_delivery_method": "email",
        },
    )

    assert response.status_code == 429
    assert response.headers["retry-after"] == "60"
    assert "A code was sent recently. Try again in 60 seconds." in response.text
    assert "Verification code" in response.text
    assert "Resend code" in response.text


def test_form_mfa_resend_can_switch_to_sms() -> None:
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(sms_sender=sms_sender)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.phone_number = "+1 555 123 4567"
        session.commit()
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    resend_csrf_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert challenge_token_match is not None
    assert resend_csrf_match is not None

    response = client.post(
        "/auth/mfa/resend",
        data={
            "csrf_token": resend_csrf_match.group(1),
            "mfa_challenge_token": challenge_token_match.group(1),
            "mfa_delivery_method": "sms",
        },
    )

    assert response.status_code == 200
    assert "A new code was sent by SMS." in response.text
    assert 'value="sms"' in response.text
    assert sms_sender.messages[-1]["recipient"] == "+15551234567"


def test_form_mfa_resend_rejects_tampered_csrf_token() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    resend_csrf_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert challenge_token_match is not None
    assert resend_csrf_match is not None

    response = client.post(
        "/auth/mfa/resend",
        data={
            "csrf_token": f"{resend_csrf_match.group(1)}0",
            "mfa_challenge_token": challenge_token_match.group(1),
            "mfa_delivery_method": "email",
        },
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "invalid CSRF token"


def test_dashboard_shell_requires_session_cookie() -> None:
    app = migrated_development_app()
    response = TestClient(app).get("/portal", follow_redirects=False)

    assert response.status_code == 303
    assert response.headers["location"] == "/"
    assert response.headers["cache-control"] == "no-store"


def test_dashboard_shell_navigation_and_cookie_logout() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)

    dashboard_response = client.get("/portal")

    assert dashboard_response.status_code == 200
    assert dashboard_response.headers["cache-control"] == "no-store"
    assert "HttpOnly" in dashboard_response.headers["set-cookie"]
    assert "Path=/portal" in dashboard_response.headers["set-cookie"]
    assert "SameSite=strict" in dashboard_response.headers["set-cookie"]
    assert 'data-active-module="dashboard"' in dashboard_response.text
    assert "Documents may be available in a future release." in dashboard_response.text
    assert "Secure messaging may be available in a future release." in dashboard_response.text
    assert 'href="/portal/account"' in dashboard_response.text
    assert 'href="/portal/email-passwords"' in dashboard_response.text
    assert 'href="/portal/help"' in dashboard_response.text
    assert 'class="logout-form"' in dashboard_response.text
    assert ">Logout</button>" in dashboard_response.text
    assert "patient.user" in dashboard_response.text

    account_response = client.get("/portal/account")
    email_passwords_response = client.get("/portal/email-passwords")
    help_response = client.get("/portal/help")

    assert account_response.status_code == 200
    assert 'action="http://testserver/portal/account/password"' in account_response.text
    assert 'action="http://testserver/portal/account/contact"' in account_response.text
    assert 'action="http://testserver/portal/account/mfa"' in account_response.text
    assert email_passwords_response.status_code == 200
    assert 'data-active-module="email-passwords"' in email_passwords_response.text
    email_passwords_link_start = email_passwords_response.text.index(
        'href="/portal/email-passwords"'
    )
    email_passwords_link_open = email_passwords_response.text.rindex(
        "<a",
        0,
        email_passwords_link_start,
    )
    email_passwords_link = email_passwords_response.text[
        email_passwords_link_open : email_passwords_response.text.index(
            "</a>",
            email_passwords_link_start,
        )
    ]
    assert 'aria-current="page"' in email_passwords_link
    assert "selected" in email_passwords_link
    assert 'data-active-module="account"' not in email_passwords_response.text
    assert '<th scope="col">Subject</th>' in email_passwords_response.text
    assert "No email passwords" in email_passwords_response.text
    assert help_response.status_code == 200
    assert 'data-active-module="help"' in help_response.text
    assert "Maple Creek Medical" in help_response.text

    match = CSRF_TOKEN_PATTERN.search(help_response.text)
    assert match is not None
    logout_response = client.post(
        "/portal/logout",
        data={"csrf_token": match.group(1)},
        follow_redirects=False,
    )
    redirected_response = client.get("/portal", follow_redirects=False)

    assert logout_response.status_code == 303
    assert logout_response.headers["location"] == "/"
    assert redirected_response.status_code == 303
    with app.state.session_factory() as session:
        portal_session = session.scalar(
            select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
        )
        logout_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_SESSION_LOGOUT
            )
        )

        assert portal_session is not None
        assert portal_session.revoked_reason == "logout"
        assert logout_event is not None
        assert logout_event.account_id == account_id


def test_account_password_change_requires_step_up_and_revokes_other_sessions() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    expire_email_mfa_cooldown(app)
    sign_in_patient_api_session(client)
    account_response = client.get("/portal/account")
    csrf_token_match = CSRF_TOKEN_PATTERN.search(account_response.text)
    assert csrf_token_match is not None

    failed_response = client.post(
        "/portal/account/password",
        data={
            "csrf_token": csrf_token_match.group(1),
            "current_password": "Wrong1!password",
            "new_password": STRONG_RESET_PASSWORD,
            "new_password_confirmation": STRONG_RESET_PASSWORD,
        },
    )
    fresh_account_response = client.get("/portal/account")
    fresh_csrf_token_match = CSRF_TOKEN_PATTERN.search(fresh_account_response.text)
    assert fresh_csrf_token_match is not None
    previous_cookie = client.cookies.get(main.PORTAL_SESSION_COOKIE_NAME)
    assert previous_cookie is not None
    changed_response = client.post(
        "/portal/account/password",
        data={
            "csrf_token": fresh_csrf_token_match.group(1),
            "current_password": STRONG_PASSWORD,
            "new_password": STRONG_RESET_PASSWORD,
            "new_password_confirmation": STRONG_RESET_PASSWORD,
        },
        follow_redirects=False,
    )
    replacement_cookie = client.cookies.get(main.PORTAL_SESSION_COOKIE_NAME)
    assert replacement_cookie is not None
    assert replacement_cookie != previous_cookie
    copied_cookie_client = TestClient(app)
    copied_cookie_client.cookies.set(
        main.PORTAL_SESSION_COOKIE_NAME,
        previous_cookie,
        path=main.PORTAL_SESSION_COOKIE_PATH,
    )
    copied_cookie_response = copied_cookie_client.get("/portal", follow_redirects=False)
    notice_response = client.get("/portal/account?status=password-updated")
    old_password_login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    expire_email_mfa_cooldown(app)
    new_password_login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_RESET_PASSWORD},
    )
    still_signed_in_response = client.get("/portal")

    assert failed_response.status_code == 403
    assert "Account change could not be completed." in failed_response.text
    assert "Wrong1!password" not in failed_response.text
    assert changed_response.status_code == 303
    assert changed_response.headers["location"] == "/portal/account?status=password-updated"
    assert notice_response.status_code == 200
    assert "Password updated." in notice_response.text
    assert old_password_login_response.status_code == 401
    assert new_password_login_response.status_code == 200
    assert new_password_login_response.json()["status"] == "mfa_required"
    assert still_signed_in_response.status_code == 200
    assert copied_cookie_response.status_code == 303
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        portal_sessions = list(
            session.scalars(
                select(PatientPortalSession)
                .where(PatientPortalSession.account_id == account_id)
                .order_by(PatientPortalSession.id)
            )
        )
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_PASSWORD_CHANGE)
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert account is not None
        assert account.password_hash != STRONG_PASSWORD
        assert account.password_hash != STRONG_RESET_PASSWORD
        assert any(
            portal_session.revoked_reason == SESSION_REVOKED_REASON_PASSWORD_CHANGE
            for portal_session in portal_sessions
        )
        assert any(portal_session.revoked_at is None for portal_session in portal_sessions)
        assert [(event.outcome, event.reason) for event in audit_events] == [
            (AUDIT_OUTCOME_FAILURE, "step_up_failed"),
            (AUDIT_OUTCOME_SUCCESS, "updated"),
        ]


def test_account_contact_update_creates_staff_review_request() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    account_response = client.get("/portal/account")
    csrf_token_match = CSRF_TOKEN_PATTERN.search(account_response.text)
    assert csrf_token_match is not None

    failed_response = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token_match.group(1),
            "email": "new.patient@example.com",
            "phone_number": "+1 555 010 5555",
            "current_password": "Wrong1!password",
        },
    )
    fresh_account_response = client.get("/portal/account")
    fresh_csrf_token_match = CSRF_TOKEN_PATTERN.search(fresh_account_response.text)
    assert fresh_csrf_token_match is not None
    updated_response = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": fresh_csrf_token_match.group(1),
            "email": " New.Patient@Example.com ",
            "phone_number": " +1 555 010 5555 ",
            "current_password": STRONG_PASSWORD,
        },
        follow_redirects=False,
    )
    notice_response = client.get("/portal/account?status=contact-updated")

    assert failed_response.status_code == 403
    assert "Account change could not be completed." in failed_response.text
    assert "Wrong1!password" not in failed_response.text
    assert updated_response.status_code == 303
    assert updated_response.headers["location"] == "/portal/account?status=contact-updated"
    assert notice_response.status_code == 200
    assert "Portal contact updated" in notice_response.text
    assert sender.messages[-2:] == [
        {
            "recipient": SEEDED_INVITE_EMAIL,
            "type": "contact_change_notice",
        },
        {
            "recipient": "new.patient@example.com",
            "type": "contact_change_notice",
        },
    ]
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        review_request = session.scalar(select(PatientPortalContactReviewRequest))
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE)
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert account is not None
        assert account.email == "new.patient@example.com"
        assert account.phone_number == "+15550105555"
        assert review_request is not None
        assert review_request.account_id == account_id
        assert review_request.status == CONTACT_REVIEW_STATUS_PENDING
        assert review_request.email_before == SEEDED_INVITE_EMAIL
        assert review_request.email_after == "new.patient@example.com"
        assert review_request.phone_number_before is None
        assert review_request.phone_number_after == "+15550105555"
        assert [(event.outcome, event.reason) for event in audit_events] == [
            (AUDIT_OUTCOME_FAILURE, "step_up_failed"),
            (AUDIT_OUTCOME_SUCCESS, "updated"),
        ]


def test_account_contact_update_revokes_reset_token_for_old_email() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    browser_sign_in_seeded_patient(app, client)
    reset_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    reset_token = reset_response.json()["development_reset_token"]
    account_response = client.get("/portal/account")

    changed = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token_from_response(account_response),
            "email": "replacement.patient@example.com",
            "phone_number": "",
            "current_password": STRONG_PASSWORD,
        },
        follow_redirects=False,
    )
    old_token = client.post(
        "/auth/password-reset/complete",
        json={
            "reset_token": reset_token,
            "new_password": STRONG_RESET_PASSWORD,
        },
    )

    assert changed.status_code == 303
    assert old_token.status_code == 400


def test_account_settings_step_up_failures_lock_and_revoke_session() -> None:
    app = migrated_development_app(auth_max_failed_password_attempts=2)
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    account_response = client.get("/portal/account")

    first_failure = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token_from_response(account_response),
            "email": "attacker@example.test",
            "phone_number": "",
            "current_password": "Wrong1!password",
        },
    )
    second_failure = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token_from_response(first_failure),
            "email": "attacker@example.test",
            "phone_number": "",
            "current_password": "Wrong1!password",
        },
        follow_redirects=False,
    )
    portal_response = client.get("/portal", follow_redirects=False)

    assert first_failure.status_code == 403
    assert second_failure.status_code == 303
    assert portal_response.status_code == 303
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        portal_session = session.scalar(
            select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
        )
        assert account is not None
        assert account.locked_at is not None
        assert account.email == SEEDED_INVITE_EMAIL
        assert portal_session is not None
        assert portal_session.revoked_at is not None
        # Pin the reason, not just non-null: the lazy kill on next use also sets revoked_at, so
        # this assertion held even with lock-time revocation deleted.
        assert portal_session.revoked_reason == "password_failures"


def test_account_mfa_settings_require_phone_then_allow_sms() -> None:
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(sms_sender=sms_sender)
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    account_response = client.get("/portal/account")
    csrf_token_match = CSRF_TOKEN_PATTERN.search(account_response.text)
    assert csrf_token_match is not None
    assert re.search(r'<option\s+value="sms"[^>]*disabled', account_response.text)

    unavailable_response = client.post(
        "/portal/account/mfa",
        data={
            "csrf_token": csrf_token_match.group(1),
            "preferred_mfa_method": "sms",
            "current_password": STRONG_PASSWORD,
        },
    )
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.phone_number = "+1 555 010 5555"
        session.commit()
    fresh_account_response = client.get("/portal/account")
    fresh_csrf_token_match = CSRF_TOKEN_PATTERN.search(fresh_account_response.text)
    assert fresh_csrf_token_match is not None
    assert not re.search(
        r'<option\s+value="sms"[^>]*disabled',
        fresh_account_response.text,
    )
    updated_response = client.post(
        "/portal/account/mfa",
        data={
            "csrf_token": fresh_csrf_token_match.group(1),
            "preferred_mfa_method": "sms",
            "current_password": STRONG_PASSWORD,
        },
        follow_redirects=False,
    )
    login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )

    assert unavailable_response.status_code == 400
    assert "Account change could not be completed." in unavailable_response.text
    assert updated_response.status_code == 303
    assert login_response.status_code == 200
    assert login_response.json()["mfa_delivery_method"] == "sms"
    assert sms_sender.messages[-1]["recipient"] == "+15550105555"
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_MFA_UPDATE)
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert account is not None
        assert account.preferred_mfa_method == "sms"
        assert [(event.outcome, event.reason) for event in audit_events] == [
            (AUDIT_OUTCOME_FAILURE, "delivery_unavailable"),
            (AUDIT_OUTCOME_SUCCESS, "sms"),
        ]


def test_login_uses_sms_preference_when_phone_is_available() -> None:
    email_sender = RecordingPortalEmailSender()
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(
        email_sender=email_sender,
        sms_sender=sms_sender,
    )
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.preferred_mfa_method = "sms"
        account.phone_number = "+1 555 010 5555"
        session.commit()

    login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )

    assert login_response.status_code == 200
    assert login_response.json()["status"] == "mfa_required"
    assert login_response.json()["mfa_delivery_method"] == "sms"
    assert email_sender.messages == []
    assert sms_sender.messages[-1]["recipient"] == "+15550105555"


def test_email_password_dashboard_populated_search_pagination_and_copy_controls() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    base_time = datetime(2026, 7, 23, 12, 0, tzinfo=UTC)
    secret_ids: dict[int, int] = {}
    secret_values: dict[int, str] = {}

    with app.state.session_factory() as session:
        with session.begin():
            for index in range(12):
                secret_value = f"PortalPwd{index:02d}!A"
                created = create_unlock_secret(
                    session,
                    clinic_id="default",
                    demographic_no=1234,
                    account_id=account_id,
                    secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                    secret=secret_value,
                    created_by="Clinic Nurse" if index == 5 else "CarlosDoc",
                    encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                    label="Lab report" if index == 5 else f"Message {index:02d}",
                    source_reference=f"message-{3135 + index}",
                )
                created_at = base_time + timedelta(days=index)
                created.unlock_secret.created_at = created_at
                created.unlock_secret.updated_at = created_at
                secret_ids[index] = created.unlock_secret.id
                secret_values[index] = secret_value

    page_one_response = client.get("/portal/email-passwords")
    csrf_token_match = CSRF_TOKEN_PATTERN.search(page_one_response.text)
    assert csrf_token_match is not None
    reveal_response = client.post(
        f"/portal/email-passwords/{secret_ids[11]}/reveal",
        data={"csrf_token": csrf_token_match.group(1)},
    )
    page_two_response = client.get("/portal/email-passwords?page=2")
    out_of_range_page_response = client.get("/portal/email-passwords?page=99")
    search_response = client.get(
        "/portal/email-passwords",
        params={"q": "lab", "provider": "", "date_from": "", "date_to": ""},
    )
    provider_response = client.get(
        "/portal/email-passwords",
        params={"provider": "Clinic Nurse"},
    )
    date_response = client.get(
        "/portal/email-passwords",
        params={"date_from": "2026-07-28", "date_to": "2026-07-28"},
    )
    invalid_date_response = client.get(
        "/portal/email-passwords",
        params={"date_from": "2026-07-29", "date_to": "2026-07-28"},
    )
    malformed_date_response = client.get(
        "/portal/email-passwords",
        params={"q": "lab", "date_from": "not-a-date", "date_to": ""},
    )
    maximum_date_response = client.get(
        "/portal/email-passwords",
        params={"date_to": "9999-12-31"},
    )

    assert page_one_response.status_code == 200
    assert "Message 11" in page_one_response.text
    assert "Message 02" in page_one_response.text
    assert "Message 01" not in page_one_response.text
    assert "PortalPwd01!A" not in page_one_response.text
    assert all(f"PortalPwd{index:02d}!A" not in page_one_response.text for index in range(12))
    assert page_one_response.text.index("Message 11") < page_one_response.text.index("Message 02")
    assert ">Hidden</code>" in page_one_response.text
    assert 'class="copyable-password"' in page_one_response.text
    assert 'data-copy-target="email-password-' in page_one_response.text
    assert 'data-reveal-url="/portal/email-passwords/' in page_one_response.text
    assert 'href="/portal/email-passwords?page=2"' in page_one_response.text
    assert "Page 1 of 2" in page_one_response.text
    assert reveal_response.status_code == 200
    assert reveal_response.json()["passphrase"] == secret_values[11]

    assert page_two_response.status_code == 200
    assert "Message 01" in page_two_response.text
    assert "Message 00" in page_two_response.text
    assert "Message 02" not in page_two_response.text
    assert 'href="/portal/email-passwords"' in page_two_response.text
    assert "Page 2 of 2" in page_two_response.text

    assert out_of_range_page_response.status_code == 200
    assert "Message 01" in out_of_range_page_response.text
    assert "Page 2 of 2" in out_of_range_page_response.text

    assert search_response.status_code == 200
    assert "Lab report" in search_response.text
    assert "Message 06" not in search_response.text
    assert "PortalPwd06!A" not in search_response.text
    assert 'value="lab"' in search_response.text
    assert "Page 1 of 1" in search_response.text

    assert provider_response.status_code == 200
    assert "Lab report" in provider_response.text
    assert "Message 06" not in provider_response.text
    assert '<option value="Clinic Nurse" selected>' in provider_response.text

    assert date_response.status_code == 200
    assert "Lab report" in date_response.text
    assert "Message 04" not in date_response.text
    assert "Message 06" not in date_response.text
    assert 'value="2026-07-28"' in date_response.text
    assert invalid_date_response.status_code == 400
    assert "from date must not be later" in invalid_date_response.text
    assert malformed_date_response.status_code == 400
    assert "text/html" in malformed_date_response.headers["content-type"]
    assert "Enter valid from and to dates." in malformed_date_response.text
    assert "Lab report" not in malformed_date_response.text
    assert maximum_date_response.status_code == 200

    with app.state.session_factory() as session:
        read_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type == AUDIT_EVENT_UNLOCK_SECRET_READ,
                    PatientPortalAuditEvent.outcome == AUDIT_OUTCOME_SUCCESS,
                    PatientPortalAuditEvent.account_id == account_id,
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert len(read_events) == 1
        assert all(event.actor_type == "patient" for event in read_events)


def test_email_password_dashboard_empty_search_and_unavailable_password_states() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    raw_secret = "HiddenEmail9!"

    empty_response = client.get("/portal/email-passwords")
    empty_search_response = client.get("/portal/email-passwords?q=missing")

    assert empty_response.status_code == 200
    assert "No email passwords" in empty_response.text
    assert "Page 1 of 1" in empty_response.text
    assert empty_search_response.status_code == 200
    assert "No matching email passwords" in empty_search_response.text
    assert "Page 1 of 1" in empty_search_response.text

    with app.state.session_factory() as session:
        with session.begin():
            created = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret,
                created_by="CarlosDoc",
                encryption_secret="v" * MIN_PRODUCTION_SECRET_LENGTH,
                label="Broken message",
                source_reference="message-4000",
            )
            unavailable_id = created.unlock_secret.id

    unavailable_response = client.get("/portal/email-passwords")
    csrf_token_match = CSRF_TOKEN_PATTERN.search(unavailable_response.text)
    assert csrf_token_match is not None
    reveal_response = client.post(
        f"/portal/email-passwords/{unavailable_id}/reveal",
        data={"csrf_token": csrf_token_match.group(1)},
    )

    assert unavailable_response.status_code == 200
    assert "Broken message" in unavailable_response.text
    assert "Hidden" in unavailable_response.text
    assert raw_secret not in unavailable_response.text
    assert reveal_response.status_code == 503
    assert reveal_response.json()["detail"] == "email password unavailable"
    with app.state.session_factory() as session:
        read_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_UNLOCK_SECRET_READ,
                PatientPortalAuditEvent.outcome == AUDIT_OUTCOME_FAILURE,
                PatientPortalAuditEvent.account_id == account_id,
            )
        )

        assert read_event is not None
        assert read_event.reason == "decryption_failed"


def test_email_password_dashboard_escapes_stored_and_reflected_values() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    with app.state.session_factory() as session:
        with session.begin():
            create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret="Escaped1!word",
                created_by="<strong>Provider</strong>",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="<meta http-equiv=refresh content=0>",
                source_reference="<img src=x>",
            )

    stored_response = client.get("/portal/email-passwords")
    reflected_response = client.get(
        "/portal/email-passwords",
        params={"q": '<script nonce="x">alert(1)</script>'},
    )

    assert stored_response.status_code == 200
    assert "<strong>Provider</strong>" not in stored_response.text
    assert "<meta http-equiv" not in stored_response.text
    assert "<img src=x>" not in stored_response.text
    assert "&lt;strong&gt;Provider&lt;/strong&gt;" in stored_response.text
    assert reflected_response.status_code == 200
    assert '<script nonce="x">alert(1)</script>' not in reflected_response.text
    assert "&lt;script" in reflected_response.text


def test_portal_logout_rejects_invalid_csrf_without_revoking_session() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)

    dashboard_response = client.get("/portal")
    logout_response = client.post(
        "/portal/logout",
        data={"csrf_token": "invalid"},
        follow_redirects=False,
    )
    still_authenticated_response = client.get("/portal")

    assert dashboard_response.status_code == 200
    assert logout_response.status_code == 403
    assert logout_response.json()["detail"] == "logout could not be completed"
    assert still_authenticated_response.status_code == 200
    with app.state.session_factory() as session:
        portal_session = session.scalar(
            select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
        )
        logout_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_SESSION_LOGOUT
            )
        )

        assert portal_session is not None
        assert portal_session.revoked_reason is None
        assert logout_event is None


def test_portal_logout_clears_invalid_session_cookie() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    dashboard_response = client.get("/portal")
    match = CSRF_TOKEN_PATTERN.search(dashboard_response.text)
    assert match is not None
    with app.state.session_factory() as session:
        portal_session = session.scalar(
            select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
        )
        assert portal_session is not None
        portal_session.revoked_at = utc_now()
        portal_session.revoked_reason = "test"
        session.commit()

    response = client.post(
        "/portal/logout",
        data={"csrf_token": match.group(1)},
        follow_redirects=False,
    )
    set_cookie_header = response.headers.get("set-cookie", "")

    assert response.status_code == 303
    assert response.headers["location"] == "/"
    assert f"{main.PORTAL_SESSION_COOKIE_NAME}=" in set_cookie_header
    assert "Max-Age=0" in set_cookie_header
    with app.state.session_factory() as session:
        logout_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_SESSION_LOGOUT
            )
        )
        assert logout_event is None


def test_dashboard_clears_invalid_session_cookie() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    with app.state.session_factory() as session:
        portal_session = session.scalar(
            select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
        )
        assert portal_session is not None
        portal_session.revoked_at = utc_now()
        portal_session.revoked_reason = "test"
        session.commit()

    response = client.get("/portal", follow_redirects=False)
    set_cookie_header = response.headers.get("set-cookie", "")

    assert response.status_code == 303
    assert response.headers["location"] == "/"
    assert f"{main.PORTAL_SESSION_COOKIE_NAME}=" in set_cookie_header
    assert "Max-Age=0" in set_cookie_header
    assert "Path=/portal" in set_cookie_header


def test_non_portal_prefix_does_not_receive_portal_cache_rule() -> None:
    app = migrated_development_app()
    response = TestClient(app).get("/portalfoo")

    assert response.status_code == 404
    assert "cache-control" not in response.headers


def test_dashboard_styles_include_desktop_and_mobile_navigation_rules() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    response = client.get("/static/styles.css")
    script_response = client.get("/static/portal.js")
    css = response.text

    assert response.status_code == 200
    assert script_response.status_code == 200
    assert ".dashboard-layout" in css
    assert "grid-template-columns: 220px minmax(0, 1fr);" in css
    assert "@media (max-width: 640px)" in css
    assert ".portal-topbar" in css
    assert "flex-direction: row;" in css
    assert ".language-switch .text-tab" in css
    assert "min-height: 44px;" in css
    assert ".module-nav" in css
    assert "align-items: center;" in css
    assert "grid-template-rows: auto minmax(0, 1fr);" in css
    assert ".module-toolbar .search-field" in css
    assert "width: min(100%, 160px);" in css
    assert ".settings-section" in css
    assert ".password-copy-group" in css
    assert ".table-shell .email-password-table" in css
    assert ".email-password-table td::before" in css
    assert "content: attr(data-label);" in css
    assert "navigator.clipboard.writeText" in script_response.text


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


def test_login_route_rejects_malformed_urlencoded_form_body() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    get_csrf_token(client)
    response = client.post(
        "/auth/login",
        content="csrf_token",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "invalid form body"


def test_login_route_rejects_invalid_utf8_form_body() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).post(
        "/auth/login",
        content=b"csrf_token=\xff",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "invalid form body"


def test_login_route_rejects_too_many_form_fields() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    get_csrf_token(client)
    form_body = "&".join(
        f"field{field_number}=x" for field_number in range(main.MAX_FORM_FIELD_COUNT + 1)
    )
    response = client.post(
        "/auth/login",
        content=form_body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "invalid form body"


def test_login_rejects_bad_password_with_generic_error_and_audit() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": "Wrong1!password"},
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "sign-in could not be completed"
    assert "Wrong1!password" not in response.text
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        audit_event = session.scalar(
            select(PatientPortalAuditEvent)
            .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_LOGIN)
            .order_by(PatientPortalAuditEvent.id.desc())
        )

        assert account is not None
        assert account.failed_login_count == 1
        assert account.locked_at is None
        assert audit_event is not None
        assert audit_event.outcome == AUDIT_OUTCOME_FAILURE
        assert audit_event.reason == "invalid_credentials"


def test_mfa_resend_limits_email_and_sms_independently() -> None:
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(sms_sender=sms_sender)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.phone_number = "+1 555 123 4567"
        session.commit()

    login_response = client.post(
        "/auth/login",
        json={
            "username": "patient.user",
            "password": STRONG_PASSWORD,
            "mfa_delivery_method": "email",
        },
    )
    challenge_token = login_response.json()["mfa_challenge_token"]

    throttled_email_response = client.post(
        "/auth/mfa/resend",
        json={"mfa_challenge_token": challenge_token, "mfa_delivery_method": "email"},
    )
    sms_response = client.post(
        "/auth/mfa/resend",
        json={"mfa_challenge_token": challenge_token, "mfa_delivery_method": "sms"},
    )
    throttled_sms_response = client.post(
        "/auth/mfa/resend",
        json={"mfa_challenge_token": challenge_token, "mfa_delivery_method": "sms"},
    )

    assert login_response.status_code == 200
    assert throttled_email_response.status_code == 429
    assert throttled_email_response.headers["retry-after"] == "60"
    assert sms_response.status_code == 200
    assert throttled_sms_response.status_code == 429
    assert throttled_sms_response.headers["retry-after"] == "300"

    verify_response = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": challenge_token,
            "code": sms_sender.messages[-1]["code"],
        },
    )

    assert verify_response.status_code == 200
    assert verify_response.json()["status"] == "signed_in"
    with app.state.session_factory() as session:
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_MFA_RESEND)
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert challenge is not None
        assert challenge.delivery_method == "sms"
        assert challenge.status == "verified"
        assert [event.outcome for event in audit_events] == [
            AUDIT_OUTCOME_THROTTLED,
            AUDIT_OUTCOME_SUCCESS,
            AUDIT_OUTCOME_THROTTLED,
        ]


def test_bad_mfa_attempts_lock_account() -> None:
    app = migrated_development_app(mfa_max_failed_attempts=2)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    login_payload = login_response.json()
    challenge_token = login_payload["mfa_challenge_token"]
    wrong_code = "111111" if login_payload["development_mfa_code"] == "000000" else "000000"

    first_bad_response = client.post(
        "/auth/mfa/verify",
        json={"mfa_challenge_token": challenge_token, "code": wrong_code},
    )
    second_bad_response = client.post(
        "/auth/mfa/verify",
        json={"mfa_challenge_token": challenge_token, "code": wrong_code},
    )

    assert first_bad_response.status_code == 401
    assert first_bad_response.json()["detail"] == "MFA could not be verified"
    assert second_bad_response.status_code == 423
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        lock_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_LOCK
            )
        )

        assert account is not None
        assert account.locked_at is not None
        assert account.force_password_reset is True
        assert challenge is not None
        assert challenge.status == "cancelled"
        assert lock_event is not None
        assert lock_event.reason == "mfa_failures"


def test_password_lockout_staff_unlock_and_forced_reset() -> None:
    app = migrated_development_app(auth_max_failed_password_attempts=2)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    first_bad_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": "Wrong1!password"},
    )
    lock_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": "Wrong1!password"},
    )
    locked_login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )

    assert first_bad_response.status_code == 401
    assert lock_response.status_code == 401
    assert locked_login_response.status_code == 423

    unlock_response = client.post(
        f"/dev/admin/accounts/{account_id}/unlock",
        headers=dev_admin_headers(actor="Admin example"),
    )
    forced_reset_login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    reset_request_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    reset_token = reset_request_response.json()["development_reset_token"]
    complete_reset_response = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": reset_token, "new_password": STRONG_RESET_PASSWORD},
    )
    new_login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_RESET_PASSWORD},
    )

    assert unlock_response.status_code == 200
    assert unlock_response.json()["locked_at"] is None
    assert unlock_response.json()["force_password_reset"] is True
    assert forced_reset_login_response.status_code == 403
    assert forced_reset_login_response.json() == {"status": "password_reset_required"}
    assert reset_request_response.status_code == 202
    assert reset_token
    assert complete_reset_response.status_code == 200
    assert complete_reset_response.json() == {
        "status": "password_reset",
        "username": "patient.user",
    }
    assert new_login_response.status_code == 200
    assert new_login_response.json()["status"] == "mfa_required"
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        reset_records = list(session.scalars(select(PatientPortalPasswordResetToken)))
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type.in_(
                        [
                            AUDIT_EVENT_ACCOUNT_LOCK,
                            AUDIT_EVENT_ACCOUNT_UNLOCK,
                            AUDIT_EVENT_PASSWORD_RESET_REQUEST,
                            AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
                        ]
                    )
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert account is not None
        assert account.failed_login_count == 0
        assert account.locked_at is None
        assert account.force_password_reset is False
        assert len(reset_records) == 1
        assert reset_records[0].status == "used"
        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_ACCOUNT_LOCK, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_ACCOUNT_UNLOCK, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_PASSWORD_RESET_REQUEST, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_PASSWORD_RESET_COMPLETE, AUDIT_OUTCOME_SUCCESS),
        ]


def test_browser_password_reset_sends_fragment_link_and_completes_reset() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    reset_page = client.get("/auth/password-reset")

    request_response = client.post(
        "/auth/password-reset/request",
        data={
            "csrf_token": csrf_token_from_response(reset_page),
            "username": "patient.user",
            "email": SEEDED_INVITE_EMAIL,
        },
    )

    assert request_response.status_code == 202
    assert "If the account details match" in request_response.text
    assert len(sender.messages) == 1
    reset_url = str(sender.messages[0]["reset_url"])
    parsed_reset_url = urlsplit(reset_url)
    assert parsed_reset_url.query == ""
    assert parsed_reset_url.path == "/auth/password-reset/complete"
    reset_token = parse_qs(parsed_reset_url.fragment)["token"][0]
    assert reset_token not in parsed_reset_url.path
    with app.state.session_factory() as session:
        delivery_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_PASSWORD_RESET_DELIVERY
            )
        )
        assert delivery_event is not None
        assert delivery_event.outcome == AUDIT_OUTCOME_SUCCESS
        assert delivery_event.reason == "email"
    cooldown_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    assert cooldown_response.status_code == 202
    assert cooldown_response.json()["development_reset_token"] is None
    assert len(sender.messages) == 1

    complete_page = client.get(parsed_reset_url.path)
    mismatch_response = client.post(
        "/auth/password-reset/complete",
        data={
            "csrf_token": csrf_token_from_response(complete_page),
            "reset_token": reset_token,
            "new_password": STRONG_RESET_PASSWORD,
            "new_password_confirmation": "Different1!word",
        },
    )
    complete_response = client.post(
        "/auth/password-reset/complete",
        data={
            "csrf_token": csrf_token_from_response(mismatch_response),
            "reset_token": reset_token,
            "new_password": STRONG_RESET_PASSWORD,
            "new_password_confirmation": STRONG_RESET_PASSWORD,
        },
    )
    login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_RESET_PASSWORD},
    )

    assert mismatch_response.status_code == 400
    assert "password confirmation does not match" in mismatch_response.text.lower()
    assert f'value="{reset_token}" data-reset-token' in mismatch_response.text
    assert complete_response.status_code == 200
    assert "Password reset" in complete_response.text
    assert reset_token not in complete_response.text
    assert login_response.status_code == 200
    assert login_response.json()["status"] == "mfa_required"


def test_password_reset_delivery_failure_revokes_token_and_is_audited(
    caplog: pytest.LogCaptureFixture,
) -> None:
    sender = RecordingPortalEmailSender(fail=True)
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )

    assert response.status_code == 202
    assert response.json()["development_reset_token"] is None
    assert SEEDED_INVITE_EMAIL not in caplog.text
    with app.state.session_factory() as session:
        reset_record = session.scalar(select(PatientPortalPasswordResetToken))
        delivery_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_PASSWORD_RESET_DELIVERY
            )
        )
        assert reset_record is not None
        assert reset_record.status == "revoked"
        assert delivery_event is not None
        assert delivery_event.outcome == AUDIT_OUTCOME_FAILURE
        assert delivery_event.reason == "email"


def test_locked_account_browser_page_and_reset_require_staff_unlock() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        with session.begin():
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            account.locked_at = utc_now()
            account.locked_by = "security-policy"
            account.force_password_reset = True

    csrf_token = get_csrf_token(client)
    locked_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    reset_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )

    assert locked_response.status_code == 423
    assert "Account locked" in locked_response.text
    assert "Clinic staff must unlock this account" in locked_response.text
    assert reset_response.status_code == 202
    assert reset_response.json()["development_reset_token"] is None
    assert sender.messages == []

    unlock_response = client.post(
        f"/dev/admin/accounts/{account_id}/unlock",
        headers=dev_admin_headers(),
    )
    unlocked_reset_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )

    assert unlock_response.status_code == 200
    assert unlocked_reset_response.json()["development_reset_token"]
    assert len(sender.messages) == 1


def test_account_lock_revokes_preexisting_password_reset_token() -> None:
    app = migrated_development_app(auth_max_failed_password_attempts=1)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    reset_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    reset_token = reset_response.json()["development_reset_token"]

    lock_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": "Wrong1!password"},
    )
    # Assert the token is already dead BEFORE attempting redemption. Redeeming first would let the
    # redeem-time guard revoke it, so this test passed even with lock-time revocation removed.
    with app.state.session_factory() as session:
        locked_account = session.get(PatientPortalAccount, account_id)
        revoked_at_lock_time = session.scalar(select(PatientPortalPasswordResetToken))
        assert locked_account is not None
        assert locked_account.locked_at is not None
        assert revoked_at_lock_time is not None
        assert revoked_at_lock_time.status == "revoked"

    complete_response = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": reset_token, "new_password": STRONG_RESET_PASSWORD},
    )

    assert lock_response.status_code == 401
    assert complete_response.status_code == 400
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        reset_record = session.scalar(select(PatientPortalPasswordResetToken))
        assert account is not None
        assert account.locked_at is not None
        assert reset_record is not None
        assert reset_record.status == "revoked"


def test_database_allows_only_one_pending_password_reset_per_account() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    assert response.status_code == 202

    now = utc_now()
    with app.state.session_factory() as session:
        session.add(
            PatientPortalPasswordResetToken(
                account_id=account_id,
                token_hash="z" * 64,
                status="pending",
                created_at=now,
                expires_at=now + timedelta(hours=1),
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()
        session.rollback()


def test_api_docs_are_available_in_development() -> None:
    app = main.create_app(
        development_settings(
            enable_dev_admin=True,
            dev_admin_token=DEV_ADMIN_TOKEN,
        )
    )

    response = TestClient(app).get("/api/openapi.json")

    assert response.status_code == 200
    paths = response.json()["paths"]
    assert "401" in paths["/auth/logout"]["post"]["responses"]
    assert "403" in paths["/portal/logout"]["post"]["responses"]
    assert {"400", "404", "409"} <= paths["/dev/admin/invites"]["post"]["responses"].keys()
    assert {"400", "404"} <= paths["/dev/admin/invites"]["get"]["responses"].keys()
    assert {"404", "409"} <= paths["/dev/admin/invites/{invite_id}/resend"]["post"][
        "responses"
    ].keys()
    assert {"404", "409"} <= paths["/dev/admin/invites/{invite_id}/revoke"]["post"][
        "responses"
    ].keys()
    assert "404" in paths["/dev/admin/accounts/{account_id}/unlock"]["post"]["responses"]
    assert "/internal/health/db" not in paths


def test_api_docs_are_disabled_outside_development() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            clinic_id=TEST_CLINIC_ID,
            clinic_name=TEST_CLINIC_NAME,
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            unlock_secret_encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )

    assert TestClient(app).get("/api/openapi.json").status_code == 404
    assert TestClient(app).get("/api/docs").status_code == 404
    assert TestClient(app).get("/api/redoc").status_code == 404


def test_api_docs_are_disabled_in_production() -> None:
    app = main.create_app(production_settings())
    client = TestClient(app, base_url="https://portal.example.test")

    assert client.get("/api/openapi.json").status_code == 404
    assert client.get("/api/docs").status_code == 404
    assert client.get("/api/redoc").status_code == 404


def test_dev_admin_invite_lifecycle() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor=" CarlosDoc "),
        json=seeded_invite_request(),
    )

    assert create_response.status_code == 201
    created_invite = create_response.json()
    invite_id = created_invite["id"]
    invite_token = created_invite["invite_token"]
    assert created_invite["clinic_id"] == "default"
    assert created_invite["demographic_no"] == 1234
    assert created_invite["status"] == "pending"
    assert created_invite["created_by"] == "CarlosDoc"
    assert created_invite["last_issued_by"] == "CarlosDoc"
    assert created_invite["issued_count"] == 1
    assert created_invite["has_identity_proof"] is True
    assert created_invite["accepted_at"] is None
    assert created_invite["accepted_account_id"] is None
    created_expires_at = parse_response_datetime(created_invite["expires_at"])
    created_at = parse_response_datetime(created_invite["created_at"])
    assert DEFAULT_INVITE_TTL - timedelta(seconds=1) <= created_expires_at - created_at
    assert created_expires_at - created_at <= DEFAULT_INVITE_TTL + timedelta(seconds=1)
    assert create_response.headers["cache-control"] == "no-store"

    with app.state.session_factory() as session:
        persisted_invite = session.get(PatientPortalInvite, invite_id)
        assert persisted_invite is not None
        assert persisted_invite.clinic_id == "default"
        assert persisted_invite.token_hash == hash_invite_token(invite_token)
        assert persisted_invite.token_hash != invite_token
        assert persisted_invite.expires_at is not None
        assert persisted_invite.proof_salt is not None
        assert persisted_invite.proof_hash_version == "v1"

    list_response = client.get(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        params={"demographic_no": 1234},
    )

    assert list_response.status_code == 200
    listed_invites = list_response.json()
    assert len(listed_invites) == 1
    assert listed_invites[0]["id"] == invite_id
    assert listed_invites[0]["clinic_id"] == "default"
    assert parse_response_datetime(listed_invites[0]["expires_at"]) == created_expires_at
    assert listed_invites[0]["has_identity_proof"] is True
    assert "invite_token" not in listed_invites[0]
    assert list_response.headers["cache-control"] == "no-store"

    resend_response = client.post(
        f"/dev/admin/invites/{invite_id}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert resend_response.status_code == 200
    resent_invite = resend_response.json()
    resent_token = resent_invite["invite_token"]
    assert resent_token != invite_token
    assert resent_invite["id"] != invite_id
    assert resent_invite["issued_count"] == 1
    assert resent_invite["supersedes_invite_id"] == invite_id
    assert resent_invite["last_issued_by"] == "Admin example"
    assert parse_response_datetime(resent_invite["expires_at"]) >= created_expires_at
    assert resend_response.headers["cache-control"] == "no-store"

    revoke_response = client.post(
        f"/dev/admin/invites/{resent_invite['id']}/revoke",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert revoke_response.status_code == 200
    revoked_invite = revoke_response.json()
    assert revoked_invite["status"] == "revoked"
    assert revoked_invite["revoked_by"] == "Admin example"
    assert "invite_token" not in revoked_invite

    revoked_resend_response = client.post(
        f"/dev/admin/invites/{resent_invite['id']}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert revoked_resend_response.status_code == 409
    assert revoked_resend_response.json()["detail"] == "invite has been revoked"


def test_new_invite_replaces_older_pending_invite_for_patient() -> None:
    app = migrated_development_app()
    client = TestClient(app)

    first_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="CarlosDoc"),
        json=seeded_invite_request(),
    )
    second_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="Admin example"),
        json=seeded_invite_request(),
    )

    assert first_create_response.status_code == 201
    assert second_create_response.status_code == 201
    first_invite = first_create_response.json()
    second_invite = second_create_response.json()
    assert second_invite["id"] != first_invite["id"]

    list_response = client.get(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        params={"demographic_no": 1234},
    )
    listed_invites = list_response.json()
    assert [invite["id"] for invite in listed_invites] == [
        second_invite["id"],
        first_invite["id"],
    ]
    assert [invite["status"] for invite in listed_invites] == ["pending", "revoked"]
    assert listed_invites[1]["revoked_by"] == "Admin example"

    old_activation_response = client.post(
        "/auth/activate",
        json=activation_request(first_invite["invite_token"]),
    )
    latest_activation_response = client.post(
        "/auth/activate",
        json=activation_request(second_invite["invite_token"]),
    )

    assert old_activation_response.status_code == 400
    assert latest_activation_response.status_code == 201


def test_invalid_replacement_invite_does_not_revoke_existing_pending_invite() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        pending_invite, _ = create_service_invite(session)
        session.commit()

        with pytest.raises(ValueError, match="email"):
            create_invite(
                session,
                1234,
                "Admin example",
                identity_proof=seeded_identity_proof(email="not-an-email"),
                proof_secret=IDENTITY_PROOF_SECRET,
            )

        persisted_invite = session.get(PatientPortalInvite, pending_invite.id)

        assert persisted_invite is not None
        assert persisted_invite.status == INVITE_STATUS_PENDING
        assert persisted_invite.revoked_at is None
        assert persisted_invite.revoked_by is None


def test_patient_activation_creates_account_from_seeded_invite() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert create_response.status_code == 201
    created_invite = create_response.json()
    invite_id = created_invite["id"]
    invite_token = created_invite["invite_token"]
    assert created_invite["has_identity_proof"] is True

    with app.state.session_factory() as session:
        persisted_invite = session.get(PatientPortalInvite, invite_id)
        assert persisted_invite is not None
        assert persisted_invite.proof_email_hash is not None
        assert persisted_invite.proof_date_of_birth_hash is not None
        assert persisted_invite.proof_health_card_hash is not None
        assert persisted_invite.proof_salt is not None
        assert persisted_invite.proof_email_hash != SEEDED_INVITE_EMAIL
        assert persisted_invite.proof_health_card_hash != SEEDED_INVITE_HCN

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token),
    )

    assert activation_response.status_code == 201
    assert activation_response.json() == {"status": "activated", "username": "patient.user"}
    assert activation_response.headers["cache-control"] == "no-store"

    with app.state.session_factory() as session:
        account = session.scalar(
            select(PatientPortalAccount).where(PatientPortalAccount.username == "patient.user")
        )
        accepted_invite = session.get(PatientPortalInvite, invite_id)

        assert account is not None
        assert account.clinic_id == "default"
        assert account.demographic_no == 1234
        assert account.email == SEEDED_INVITE_EMAIL
        assert account.password_hash.startswith("$argon2id$")
        assert account.password_hash != STRONG_PASSWORD
        assert accepted_invite is not None
        assert accepted_invite.status == INVITE_STATUS_ACCEPTED
        assert accepted_invite.accepted_account_id == account.id
        assert accepted_invite.accepted_at is not None
        audit_events = list(
            session.scalars(select(PatientPortalAuditEvent).order_by(PatientPortalAuditEvent.id))
        )
        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_INVITE_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_ACTIVATION, AUDIT_OUTCOME_SUCCESS),
        ]
        assert audit_events[-1].account_id == account.id
        assert audit_events[-1].invite_id == invite_id

    second_activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, username="another.patient"),
    )

    assert second_activation_response.status_code == 400
    assert second_activation_response.json()["detail"] == (
        "activation details could not be verified"
    )


def test_patient_activation_can_enroll_sms_mfa_when_sender_is_configured() -> None:
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(sms_sender=sms_sender)
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    activated = client.post(
        "/auth/activate",
        json=activation_request(
            create_response.json()["invite_token"],
            mfa_delivery_method="sms",
            phone_number="+1 613 555 0199",
        ),
    )
    login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )

    assert activated.status_code == 201
    assert login.status_code == 200
    assert login.json()["mfa_delivery_method"] == "sms"
    assert sms_sender.messages[-1]["recipient"] == "+16135550199"
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.preferred_mfa_method == "sms"
        assert account.phone_number == "+16135550199"


def test_patient_activation_rejects_sms_when_sender_is_unavailable() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    response = client.post(
        "/auth/activate",
        json=activation_request(
            create_response.json()["invite_token"],
            mfa_delivery_method="sms",
            phone_number="+16135550199",
        ),
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "MFA delivery method is unavailable"


def test_dev_admin_invite_rejects_patient_with_existing_account() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]
    activation_response = client.post("/auth/activate", json=activation_request(invite_token))

    duplicate_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert activation_response.status_code == 201
    assert duplicate_create_response.status_code == 409
    assert duplicate_create_response.json()["detail"] == "patient already has a portal account"


def test_patient_activation_rejects_identity_mismatch_without_account_leak() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )

    assert activation_response.status_code == 400
    assert activation_response.json()["detail"] == "activation details could not be verified"

    with app.state.session_factory() as session:
        assert session.scalar(select(PatientPortalAccount.id)) is None
        invite = session.scalar(select(PatientPortalInvite))
        audit_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION
            )
        )
        assert invite is not None
        assert invite.status == INVITE_STATUS_PENDING
        assert audit_event is not None
        assert audit_event.outcome == AUDIT_OUTCOME_FAILURE
        assert audit_event.reason == "invalid_details"


def test_patient_activation_rejects_expired_invite() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    created_invite = create_response.json()
    invite_token = created_invite["invite_token"]

    with app.state.session_factory() as session:
        invite = session.get(PatientPortalInvite, created_invite["id"])
        assert invite is not None
        invite.created_at = utc_now() - timedelta(days=8)
        invite.expires_at = utc_now() - timedelta(days=1)
        session.commit()

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token),
    )

    assert activation_response.status_code == 400
    assert activation_response.json()["detail"] == "activation details could not be verified"


def test_patient_activation_rejects_unavailable_username() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    first_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    first_token = first_create_response.json()["invite_token"]
    second_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(
            demographic_no=5678,
            email="second.patient@example.com",
            health_card_number="ZXCV 1234",
        ),
    )
    second_token = second_create_response.json()["invite_token"]

    assert client.post("/auth/activate", json=activation_request(first_token)).status_code == 201

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(
            second_token,
            email="second.patient@example.com",
            health_card_number="ZXCV-1234",
        ),
    )

    assert activation_response.status_code == 409
    assert activation_response.json()["detail"] == "username unavailable"


def test_patient_activation_rejects_weak_password() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(create_response.json()["invite_token"], password="weak"),
    )

    assert activation_response.status_code == 422
    assert "weak" not in activation_response.text


def test_patient_activation_rejects_oversized_json_body() -> None:
    app = migrated_development_app()
    oversized_body = (
        b'{"invite_code":"'
        + b"x" * main.MAX_JSON_BODY_BYTES
        + b'","email":"example.patient@example.com"}'
    )

    response = TestClient(app).post(
        "/auth/activate",
        content=oversized_body,
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 413
    assert response.json()["detail"] == "request body too large"


def test_patient_activation_requires_json_body() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/auth/activate",
        data={"invite_code": "unused"},
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "invalid CSRF token"


def test_patient_activation_validation_does_not_echo_health_card_number() -> None:
    app = migrated_development_app()
    invalid_health_card_number = "bad card ?"

    response = TestClient(app).post(
        "/auth/activate",
        json=activation_request("unused", health_card_number=invalid_health_card_number),
    )

    assert response.status_code == 422
    assert invalid_health_card_number not in response.text


def test_patient_activation_rejects_too_short_health_card_number() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/auth/activate",
        json=activation_request("unused", health_card_number="A1"),
    )

    assert response.status_code == 422


def test_patient_activation_rate_limits_failed_attempts() -> None:
    app = migrated_development_app(
        session_secret=NON_DEVELOPMENT_SESSION_SECRET,
        activation_max_failures_per_invite=2,
        activation_max_failures_per_client=50,
    )
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]

    for _ in range(2):
        response = client.post(
            "/auth/activate",
            json=activation_request(invite_token, health_card_number="WRONG1234"),
        )
        assert response.status_code == 400

    throttled_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )

    assert throttled_response.status_code == 429
    assert throttled_response.headers["retry-after"] == "3600"
    expected_client_hash = hash_sensitive_reference(
        AUDIT_HASH_SECRET,
        "activation_client",
        "testclient",
    )
    with app.state.session_factory() as session:
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION)
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert [event.outcome for event in audit_events] == [
            AUDIT_OUTCOME_FAILURE,
            AUDIT_OUTCOME_FAILURE,
            AUDIT_OUTCOME_THROTTLED,
        ]
        assert all(event.client_reference_hash == expected_client_hash for event in audit_events)


def test_patient_activation_rate_limit_ignores_header_from_untrusted_peer() -> None:
    app = migrated_development_app(
        session_secret=NON_DEVELOPMENT_SESSION_SECRET,
        trusted_client_ip_header="x-forwarded-for",
        trusted_proxy_cidrs="10.0.0.0/8",
    )
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]
    response = client.post(
        "/auth/activate",
        headers={"X-Forwarded-For": "203.0.113.7, 10.0.0.10"},
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )

    assert response.status_code == 400
    expected_client_hash = hash_sensitive_reference(
        AUDIT_HASH_SECRET,
        "activation_client",
        "testclient",
    )
    with app.state.session_factory() as session:
        audit_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION
            )
        )
        assert audit_event is not None
        assert audit_event.client_reference_hash == expected_client_hash


def test_patient_activation_rate_limit_window_expires() -> None:
    app = migrated_development_app(
        activation_failure_window_seconds=60,
        activation_max_failures_per_invite=1,
        activation_max_failures_per_client=50,
    )
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]
    failed_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )
    assert failed_response.status_code == 400

    with app.state.session_factory() as session:
        audit_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION
            )
        )
        assert audit_event is not None
        audit_event.created_at = utc_now() - timedelta(minutes=2)
        session.commit()

    activation_response = client.post("/auth/activate", json=activation_request(invite_token))

    assert activation_response.status_code == 201


def test_accepted_invites_cannot_be_resent_or_revoked() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    created_invite = create_response.json()

    assert (
        client.post(
            "/auth/activate",
            json=activation_request(created_invite["invite_token"]),
        ).status_code
        == 201
    )

    resend_response = client.post(
        f"/dev/admin/invites/{created_invite['id']}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )
    revoke_response = client.post(
        f"/dev/admin/invites/{created_invite['id']}/revoke",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert resend_response.status_code == 409
    assert resend_response.json()["detail"] == "invite has already been accepted"
    assert revoke_response.status_code == 409
    assert revoke_response.json()["detail"] == "invite has already been accepted"


def test_dev_admin_invites_are_hidden_outside_development() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            clinic_id=TEST_CLINIC_ID,
            clinic_name=TEST_CLINIC_NAME,
            enable_dev_admin=True,
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            unlock_secret_encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert response.status_code == 404


def test_dev_admin_invites_require_explicit_development_flag() -> None:
    app = main.create_app(
        development_settings(
            database_url="sqlite+pysqlite:///:memory:",
            identity_proof_secret=IDENTITY_PROOF_SECRET,
        )
    )
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert response.status_code == 404


def test_dev_admin_invites_require_bearer_token() -> None:
    app = migrated_development_app()
    client = TestClient(app)

    missing_token_response = client.post("/dev/admin/invites", json=seeded_invite_request())
    missing_token_invalid_body_response = client.post("/dev/admin/invites", json={})
    wrong_token_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(WRONG_DEV_ADMIN_TOKEN),
        json=seeded_invite_request(),
    )

    assert missing_token_response.status_code == 404
    assert missing_token_invalid_body_response.status_code == 404
    assert wrong_token_response.status_code == 404


def test_dev_admin_invite_creation_rejects_oversized_json_body_after_auth() -> None:
    app = migrated_development_app()
    oversized_body = b'{"demographic_no":1234,"email":"' + b"x" * main.MAX_JSON_BODY_BYTES + b'"}'

    missing_token_response = TestClient(app).post(
        "/dev/admin/invites",
        content=oversized_body,
        headers={"Content-Type": "application/json"},
    )
    authenticated_response = TestClient(app).post(
        "/dev/admin/invites",
        content=oversized_body,
        headers={
            "Content-Type": "application/json",
            **dev_admin_headers(),
        },
    )

    assert missing_token_response.status_code == 404
    assert authenticated_response.status_code == 413
    assert authenticated_response.json()["detail"] == "request body too large"


def test_dev_admin_invite_requires_identity_proof() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json={"demographic_no": 1234},
    )

    assert response.status_code == 422
    assert "health_card_number" in response.text


def test_dev_admin_invite_requires_positive_demographic_no() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(demographic_no=0),
    )

    assert response.status_code == 422


def test_dev_admin_invite_list_rejects_invalid_bounds() -> None:
    app = migrated_development_app()
    client = TestClient(app)

    assert (
        client.get(
            "/dev/admin/invites", headers=dev_admin_headers(), params={"limit": 0}
        ).status_code
        == 422
    )
    assert (
        client.get(
            "/dev/admin/invites", headers=dev_admin_headers(), params={"limit": 101}
        ).status_code
        == 422
    )
    assert (
        client.get(
            "/dev/admin/invites", headers=dev_admin_headers(), params={"offset": -1}
        ).status_code
        == 422
    )


def test_dev_admin_unknown_invite_returns_not_found() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/dev/admin/invites/999/resend",
        headers=dev_admin_headers(),
    )

    assert response.status_code == 404
    assert response.json()["detail"] == "invite not found"


def test_invite_lifecycle_writes_audit_events() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_id = create_response.json()["id"]
    resend_response = client.post(
        f"/dev/admin/invites/{invite_id}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )
    assert resend_response.status_code == 200
    replacement_invite_id = resend_response.json()["id"]
    assert (
        client.post(
            f"/dev/admin/invites/{replacement_invite_id}/revoke",
            headers=dev_admin_headers(actor="Admin example"),
        ).status_code
        == 200
    )

    with app.state.session_factory() as session:
        audit_events = list(
            session.scalars(select(PatientPortalAuditEvent).order_by(PatientPortalAuditEvent.id))
        )

        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_INVITE_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_INVITE_RESEND, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_INVITE_REVOKE, AUDIT_OUTCOME_SUCCESS),
        ]


def test_invite_list_writes_audit_event() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="CarlosDoc"),
        json=seeded_invite_request(),
    )

    list_response = client.get(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="Admin example"),
        params={"demographic_no": 1234},
    )

    assert create_response.status_code == 201
    assert list_response.status_code == 200
    with app.state.session_factory() as session:
        audit_events = list(
            session.scalars(select(PatientPortalAuditEvent).order_by(PatientPortalAuditEvent.id))
        )

        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_INVITE_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_INVITE_LIST, AUDIT_OUTCOME_SUCCESS),
        ]
        assert audit_events[-1].actor == "Admin example"
        assert audit_events[-1].demographic_no == 1234


def test_invite_status_constraints_require_matching_metadata() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        accepted_invite, _ = create_service_invite(session)
        session.commit()

        accepted_invite.status = INVITE_STATUS_ACCEPTED
        with pytest.raises(IntegrityError):
            session.commit()
        session.rollback()

        revoked_invite, _ = create_service_invite(session, demographic_no=5678)
        session.commit()

        revoked_invite.status = INVITE_STATUS_REVOKED
        with pytest.raises(IntegrityError):
            session.commit()


def test_invite_constraints_allow_only_one_pending_invite_per_patient() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        first_invite, _ = create_service_invite(session)
        session.commit()

        duplicate_pending_invite = PatientPortalInvite(
            clinic_id=first_invite.clinic_id,
            demographic_no=first_invite.demographic_no,
            token_hash=hash_invite_token("manual-duplicate-token"),
            status=INVITE_STATUS_PENDING,
            created_by="CarlosDoc",
            created_at=utc_now(),
            updated_at=utc_now(),
            sent_count=1,
            last_sent_at=utc_now(),
            last_sent_by="CarlosDoc",
            expires_at=utc_now() + DEFAULT_INVITE_TTL,
            proof_email_hash=first_invite.proof_email_hash,
            proof_date_of_birth_hash=first_invite.proof_date_of_birth_hash,
            proof_health_card_hash=first_invite.proof_health_card_hash,
            proof_salt=first_invite.proof_salt,
            proof_hash_version=first_invite.proof_hash_version,
        )
        session.add(duplicate_pending_invite)
        with pytest.raises(IntegrityError):
            session.commit()


def test_invite_service_validates_future_carlos_callers() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        with pytest.raises(ValueError, match="demographic_no"):
            create_invite(
                session,
                0,
                "CarlosDoc",
                identity_proof=seeded_identity_proof(),
                proof_secret=IDENTITY_PROOF_SECRET,
            )
        with pytest.raises(ValueError, match="actor"):
            create_invite(
                session,
                1234,
                " ",
                identity_proof=seeded_identity_proof(),
                proof_secret=IDENTITY_PROOF_SECRET,
            )
        with pytest.raises(ValueError, match="actor"):
            create_invite(
                session,
                1234,
                "x" * 129,
                identity_proof=seeded_identity_proof(),
                proof_secret=IDENTITY_PROOF_SECRET,
            )
        with pytest.raises(ValueError, match="proof_secret"):
            create_invite(
                session,
                1234,
                "CarlosDoc",
                identity_proof=seeded_identity_proof(),
                proof_secret=" ",
            )
        with pytest.raises(ValueError, match="demographic_no"):
            list_invites(session, demographic_no=0)
        with pytest.raises(ValueError, match="limit"):
            list_invites(session, limit=0)
        with pytest.raises(ValueError, match="limit"):
            list_invites(session, limit=101)
        with pytest.raises(ValueError, match="offset"):
            list_invites(session, offset=-1)
        with pytest.raises(InviteNotFoundError):
            resend_invite(session, 999, " ")
        with pytest.raises(InviteNotFoundError):
            revoke_invite(session, 999, " ")


def test_invite_service_scopes_records_by_clinic() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        clinic_a_invite, _ = create_service_invite(
            session,
            1234,
            "CarlosDoc",
            clinic_id="clinic-a",
        )
        clinic_b_invite, _ = create_service_invite(
            session,
            1234,
            "CarlosDoc",
            clinic_id="clinic-b",
        )
        session.commit()

        clinic_a_invites = list_invites(session, demographic_no=1234, clinic_id="clinic-a")
        clinic_b_invites = list_invites(session, demographic_no=1234, clinic_id="clinic-b")

        assert [invite.id for invite in clinic_a_invites] == [clinic_a_invite.id]
        assert [invite.id for invite in clinic_b_invites] == [clinic_b_invite.id]
        with pytest.raises(InviteNotFoundError):
            resend_invite(
                session,
                clinic_a_invite.id,
                "CarlosDoc",
                clinic_id="clinic-b",
            )


def test_invite_identity_proof_hashes_are_salted_per_invite() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        first_invite, _ = create_service_invite(session, demographic_no=1234)
        second_invite, _ = create_service_invite(session, demographic_no=5678)

        assert first_invite.proof_salt is not None
        assert second_invite.proof_salt is not None
        assert first_invite.proof_salt != second_invite.proof_salt
        assert first_invite.proof_email_hash != second_invite.proof_email_hash
        assert first_invite.proof_health_card_hash != second_invite.proof_health_card_hash


def test_generated_unlock_secret_value_uses_reviewed_email_pdf_format() -> None:
    for _ in range(25):
        generated_secret = generate_unlock_secret_value()

        assert re.fullmatch(r"[a-z]+-[a-z]+-\d{3}-[a-z]+-[a-z]+-\d{3}", generated_secret)


def test_unlock_secret_lifecycle_encrypts_decrypts_revokes_and_audits() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    raw_secret = "UnlockEmail9!"

    with app.state.session_factory() as session:
        with session.begin():
            created = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Email password",
                source_reference="message-3135",
            )
            unlock_secret_id = created.unlock_secret.id
            stored_secret = session.get(PatientPortalUnlockSecret, unlock_secret_id)

            assert created.secret == raw_secret
            assert stored_secret is not None
            assert stored_secret.encrypted_secret != raw_secret.encode("utf-8")
            assert raw_secret.encode("utf-8") not in stored_secret.encrypted_secret
            assert len(stored_secret.encryption_nonce) == UNLOCK_SECRET_NONCE_LENGTH
            assert stored_secret.account_id == account_id

            listed_secrets = list_unlock_secrets(
                session,
                clinic_id="default",
                account_id=account_id,
            )
            counted_secrets = count_unlock_secrets(
                session,
                clinic_id="default",
                account_id=account_id,
                search="Email",
            )
            missing_secret_count = count_unlock_secrets(
                session,
                clinic_id="default",
                account_id=account_id,
                search="missing",
            )
            with pytest.raises(ValueError, match="account_id or demographic_no"):
                list_unlock_secrets(session, clinic_id="default")
            with pytest.raises(ValueError, match="account_id or demographic_no"):
                count_unlock_secrets(session, clinic_id="default")
            with pytest.raises(ValueError, match="limit"):
                list_unlock_secrets(
                    session,
                    clinic_id="default",
                    account_id=account_id,
                    limit=0,
                )
            with pytest.raises(ValueError, match="offset"):
                list_unlock_secrets(
                    session,
                    clinic_id="default",
                    account_id=account_id,
                    offset=-1,
                )
            with pytest.raises(UnlockSecretNotFoundError):
                read_unlock_secret(
                    session,
                    unlock_secret_id,
                    clinic_id="default",
                    account_id=account_id,
                    demographic_no=5678,
                    actor_type="patient",
                    actor="patient.user",
                    encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                )
            decrypted_secret = read_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id="default",
                account_id=account_id,
                actor_type="patient",
                actor="patient.user",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            )
            revoked_secret = revoke_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id="default",
                demographic_no=1234,
                revoked_by="CarlosDoc",
                reason="staff_requested",
            )

            assert [secret.id for secret in listed_secrets] == [unlock_secret_id]
            assert counted_secrets == 1
            assert missing_secret_count == 0
            assert decrypted_secret == raw_secret
            assert revoked_secret.status == UNLOCK_SECRET_STATUS_REVOKED
            assert revoked_secret.last_viewed_at is not None

        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type.in_(
                        [
                            AUDIT_EVENT_UNLOCK_SECRET_CREATE,
                            AUDIT_EVENT_UNLOCK_SECRET_READ,
                            AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
                        ]
                    )
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )
        stored_secret = session.get(PatientPortalUnlockSecret, unlock_secret_id)

        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_UNLOCK_SECRET_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_UNLOCK_SECRET_READ, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_UNLOCK_SECRET_REVOKE, AUDIT_OUTCOME_SUCCESS),
        ]
        assert stored_secret is not None
        table_text = "|".join(
            str(value or "")
            for value in [
                stored_secret.label,
                stored_secret.source_reference,
                stored_secret.encryption_algorithm,
                stored_secret.encryption_key_id,
                stored_secret.status,
                stored_secret.created_by,
                stored_secret.revoked_by,
                stored_secret.revoke_reason,
            ]
        )
        audit_text = "|".join(
            str(value or "")
            for event in audit_events
            for value in [
                event.event_type,
                event.outcome,
                event.actor_type,
                event.actor,
                event.invite_token_hash,
                event.client_reference_hash,
                event.reason,
            ]
        )
        assert raw_secret not in table_text
        assert raw_secret not in audit_text

    with app.state.session_factory() as session:
        with pytest.raises(UnlockSecretRevokedError):
            read_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id="default",
                account_id=account_id,
                actor_type="patient",
                actor="patient.user",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            )


def test_patient_email_password_api_lists_retrieves_scoped_records_and_audits() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_a_id = activate_seeded_patient_account(app, client)
    account_b_id = activate_seeded_patient_account(
        app,
        client,
        username="other.patient",
        demographic_no=5678,
        email="other.patient@example.com",
        health_card_number="WXYZ 9876-5432",
    )
    patient_a_token = sign_in_patient_api_session(client)
    raw_secret_a = "AlphaEmail9!"
    raw_secret_b = "BetaEmail9!"
    raw_secret_revoked = "RevokedEmail9!"
    raw_secret_pdf = "PdfEmail9!"
    raw_secret_unavailable = "WrongKeyEmail9!"

    with app.state.session_factory() as session:
        with session.begin():
            created_a = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_a,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Specialist reply",
                source_reference="message-3135",
            )
            created_b = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=5678,
                account_id=account_b_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_b,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Other patient reply",
                source_reference="message-3136",
            )
            created_revoked = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_revoked,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Revoked reply",
                source_reference="message-3137",
            )
            created_pdf = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_PDF,
                secret=raw_secret_pdf,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="PDF password",
                source_reference="document-3138",
            )
            created_unavailable = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_unavailable,
                created_by="CarlosDoc",
                encryption_secret="v" * MIN_PRODUCTION_SECRET_LENGTH,
                label="Temporarily unavailable reply",
                source_reference="message-3139",
            )
            revoke_unlock_secret(
                session,
                created_revoked.unlock_secret.id,
                clinic_id="default",
                demographic_no=1234,
                revoked_by="CarlosDoc",
                reason="staff_requested",
            )
            active_a_id = created_a.unlock_secret.id
            other_patient_id = created_b.unlock_secret.id
            revoked_id = created_revoked.unlock_secret.id
            pdf_id = created_pdf.unlock_secret.id
            unavailable_id = created_unavailable.unlock_secret.id

    list_response = client.get(
        "/api/patient/email-passwords",
        headers=bearer_headers(patient_a_token),
    )
    retrieve_response = client.get(
        f"/api/patient/email-passwords/{active_a_id}",
        headers=bearer_headers(patient_a_token),
    )
    cross_patient_response = client.get(
        f"/api/patient/email-passwords/{other_patient_id}",
        headers=bearer_headers(patient_a_token),
    )
    revoked_response = client.get(
        f"/api/patient/email-passwords/{revoked_id}",
        headers=bearer_headers(patient_a_token),
    )
    pdf_response = client.get(
        f"/api/patient/email-passwords/{pdf_id}",
        headers=bearer_headers(patient_a_token),
    )
    unavailable_response = client.get(
        f"/api/patient/email-passwords/{unavailable_id}",
        headers=bearer_headers(patient_a_token),
    )

    assert list_response.status_code == 200
    assert list_response.headers["cache-control"] == "no-store"
    list_payload = list_response.json()
    assert list_payload["limit"] == 10
    assert list_payload["offset"] == 0
    assert [item["id"] for item in list_payload["items"]] == [unavailable_id, active_a_id]
    assert list_payload["items"][0]["label"] == "Temporarily unavailable reply"
    assert list_payload["items"][0]["source_reference"] == "message-3139"
    assert list_payload["items"][1]["label"] == "Specialist reply"
    assert list_payload["items"][1]["source_reference"] == "message-3135"
    assert all(
        raw_secret not in list_response.text
        for raw_secret in [
            raw_secret_a,
            raw_secret_b,
            raw_secret_revoked,
            raw_secret_pdf,
            raw_secret_unavailable,
        ]
    )

    assert retrieve_response.status_code == 200
    retrieve_payload = retrieve_response.json()
    assert retrieve_payload["id"] == active_a_id
    assert retrieve_payload["label"] == "Specialist reply"
    assert retrieve_payload["source_reference"] == "message-3135"
    assert retrieve_payload["passphrase"] == raw_secret_a
    assert raw_secret_b not in retrieve_response.text
    assert raw_secret_revoked not in retrieve_response.text
    assert raw_secret_pdf not in retrieve_response.text

    for not_found_response in [cross_patient_response, revoked_response, pdf_response]:
        assert not_found_response.status_code == 404
        assert not_found_response.json()["detail"] == "email password not found"
        assert raw_secret_b not in not_found_response.text
        assert raw_secret_revoked not in not_found_response.text
        assert raw_secret_pdf not in not_found_response.text
    assert unavailable_response.status_code == 503
    assert unavailable_response.json()["detail"] == "email password unavailable"
    assert raw_secret_unavailable not in unavailable_response.text

    with app.state.session_factory() as session:
        active_secret = session.get(PatientPortalUnlockSecret, active_a_id)
        other_patient_secret = session.get(PatientPortalUnlockSecret, other_patient_id)
        unavailable_secret = session.get(PatientPortalUnlockSecret, unavailable_id)
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type.in_(
                        [
                            AUDIT_EVENT_UNLOCK_SECRET_LIST,
                            AUDIT_EVENT_UNLOCK_SECRET_READ,
                        ]
                    )
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert active_secret is not None
        assert active_secret.last_viewed_at is not None
        assert other_patient_secret is not None
        assert other_patient_secret.last_viewed_at is None
        assert unavailable_secret is not None
        assert unavailable_secret.last_viewed_at is None
        assert [
            (event.event_type, event.outcome, event.account_id, event.demographic_no, event.reason)
            for event in audit_events
        ] == [
            (AUDIT_EVENT_UNLOCK_SECRET_LIST, AUDIT_OUTCOME_SUCCESS, account_a_id, 1234, None),
            (AUDIT_EVENT_UNLOCK_SECRET_READ, AUDIT_OUTCOME_SUCCESS, account_a_id, 1234, None),
            (
                AUDIT_EVENT_UNLOCK_SECRET_READ,
                AUDIT_OUTCOME_FAILURE,
                account_a_id,
                1234,
                "not_available",
            ),
            (
                AUDIT_EVENT_UNLOCK_SECRET_READ,
                AUDIT_OUTCOME_FAILURE,
                account_a_id,
                1234,
                "not_available",
            ),
            (
                AUDIT_EVENT_UNLOCK_SECRET_READ,
                AUDIT_OUTCOME_FAILURE,
                account_a_id,
                1234,
                "not_available",
            ),
            (
                AUDIT_EVENT_UNLOCK_SECRET_READ,
                AUDIT_OUTCOME_FAILURE,
                account_a_id,
                1234,
                "decryption_failed",
            ),
        ]


def test_patient_email_password_api_requires_session_and_valid_pagination() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    patient_token = sign_in_patient_api_session(client)
    auth_headers = bearer_headers(patient_token)

    unauthenticated_response = client.get("/api/patient/email-passwords")
    too_small_limit_response = client.get(
        "/api/patient/email-passwords?limit=0",
        headers=auth_headers,
    )
    too_large_limit_response = client.get(
        "/api/patient/email-passwords?limit=101",
        headers=auth_headers,
    )
    negative_offset_response = client.get(
        "/api/patient/email-passwords?offset=-1",
        headers=auth_headers,
    )

    assert unauthenticated_response.status_code == 401
    assert too_small_limit_response.status_code == 422
    assert too_large_limit_response.status_code == 422
    assert negative_offset_response.status_code == 422


def test_fhir_metadata_returns_capability_statement() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    response = client.get("/fhir/metadata")
    preflight = client.options(
        "/fhir/Patient",
        headers={
            "Origin": "https://client.example.test",
            "Access-Control-Request-Method": "GET",
        },
    )
    payload = response.json()

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/fhir+json")
    assert response.headers["cache-control"] == "no-store"
    assert payload["resourceType"] == "CapabilityStatement"
    assert payload["fhirVersion"] == FHIR_VERSION
    assert payload["implementation"]["url"] == "http://testserver/fhir"
    assert payload["rest"][0]["security"]["cors"] is False
    assert preflight.status_code == 405
    assert "access-control-allow-origin" not in preflight.headers
    assert {resource["type"] for resource in payload["rest"][0]["resource"]} == {
        "DocumentReference",
        "Organization",
        "Patient",
        "Practitioner",
    }
    CapabilityStatement(payload)


def test_fhir_patient_endpoints_are_bearer_authenticated_and_patient_scoped() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    patient_token = sign_in_patient_api_session(client)
    auth_headers = bearer_headers(patient_token)
    patient_id = build_fhir_patient_id("default", 1234)

    unauthenticated_response = client.get("/fhir/Patient")
    search_response = client.get("/fhir/Patient", headers=auth_headers)
    read_response = client.get(f"/fhir/Patient/{patient_id}", headers=auth_headers)
    wrong_patient_response = client.get(
        "/fhir/Patient/portal-default-5678",
        headers=auth_headers,
    )

    assert unauthenticated_response.status_code == 401
    assert unauthenticated_response.headers["content-type"].startswith("application/fhir+json")
    assert unauthenticated_response.json()["resourceType"] == "OperationOutcome"
    OperationOutcome(unauthenticated_response.json())

    assert search_response.status_code == 200
    search_payload = search_response.json()
    assert search_payload["resourceType"] == "Bundle"
    assert search_payload["total"] == 1
    assert search_payload["link"][0] == {
        "relation": "self",
        "url": "http://testserver/fhir/Patient?_count=20&_offset=0",
    }
    assert search_payload["entry"][0]["fullUrl"] == (f"http://testserver/fhir/Patient/{patient_id}")
    assert search_payload["entry"][0]["resource"]["id"] == patient_id
    Bundle(search_payload)

    assert read_response.status_code == 200
    patient_payload = read_response.json()
    assert patient_payload["resourceType"] == "Patient"
    assert patient_payload["id"] == patient_id
    assert patient_payload["identifier"][0]["value"] == "default/1234"
    assert patient_payload["telecom"][0]["value"] == SEEDED_INVITE_EMAIL
    Patient(patient_payload)

    assert wrong_patient_response.status_code == 404
    assert wrong_patient_response.json()["resourceType"] == "OperationOutcome"
    OperationOutcome(wrong_patient_response.json())


def test_fhir_document_organization_and_practitioner_resources_are_scoped() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_a_id = activate_seeded_patient_account(app, client)
    account_b_id = activate_seeded_patient_account(
        app,
        client,
        username="other.patient",
        demographic_no=5678,
        email="other.patient@example.com",
        health_card_number="WXYZ 9876-5432",
    )
    patient_a_token = sign_in_patient_api_session(client)
    auth_headers = bearer_headers(patient_a_token)
    raw_secret_a = "FhirEmail9!"
    raw_secret_b = "OtherFhir9!"
    raw_secret_revoked = "RevokedFhir9!"

    with app.state.session_factory() as session:
        with session.begin():
            created_a = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_a,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Specialist message",
                source_reference="message-3135",
            )
            created_b = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=5678,
                account_id=account_b_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_b,
                created_by="Dr other",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Other patient message",
                source_reference="message-3136",
            )
            created_revoked = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_revoked,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Revoked message",
                source_reference="message-3137",
            )
            revoke_unlock_secret(
                session,
                created_revoked.unlock_secret.id,
                clinic_id="default",
                demographic_no=1234,
                revoked_by="CarlosDoc",
                reason="staff_requested",
            )
            active_a_id = created_a.unlock_secret.id
            other_patient_id = created_b.unlock_secret.id
            revoked_id = created_revoked.unlock_secret.id

    document_search_response = client.get("/fhir/DocumentReference", headers=auth_headers)
    document_read_response = client.get(
        f"/fhir/DocumentReference/{active_a_id}",
        headers=auth_headers,
    )
    other_patient_response = client.get(
        f"/fhir/DocumentReference/{other_patient_id}",
        headers=auth_headers,
    )
    revoked_response = client.get(
        f"/fhir/DocumentReference/{revoked_id}",
        headers=auth_headers,
    )
    organization_search_response = client.get("/fhir/Organization", headers=auth_headers)
    organization_id = organization_search_response.json()["entry"][0]["resource"]["id"]
    organization_read_response = client.get(
        f"/fhir/Organization/{organization_id}",
        headers=auth_headers,
    )
    practitioner_search_response = client.get("/fhir/Practitioner", headers=auth_headers)
    practitioner_id = practitioner_search_response.json()["entry"][0]["resource"]["id"]
    practitioner_read_response = client.get(
        f"/fhir/Practitioner/{practitioner_id}",
        headers=auth_headers,
    )

    assert document_search_response.status_code == 200
    document_search_payload = document_search_response.json()
    assert document_search_payload["resourceType"] == "Bundle"
    assert document_search_payload["total"] == 1
    assert document_search_payload["link"][0] == {
        "relation": "self",
        "url": "http://testserver/fhir/DocumentReference?_count=20&_offset=0",
    }
    assert document_search_payload["entry"][0]["fullUrl"] == (
        f"http://testserver/fhir/DocumentReference/{active_a_id}"
    )
    assert document_search_payload["entry"][0]["resource"]["id"] == str(active_a_id)
    assert raw_secret_a not in document_search_response.text
    assert raw_secret_b not in document_search_response.text
    assert raw_secret_revoked not in document_search_response.text
    Bundle(document_search_payload)

    assert document_read_response.status_code == 200
    document_payload = document_read_response.json()
    assert document_payload["resourceType"] == "DocumentReference"
    assert document_payload["subject"]["reference"] == (
        f"Patient/{build_fhir_patient_id('default', 1234)}"
    )
    assert document_payload["description"] == "Specialist message"
    assert document_payload["date"].endswith("Z")
    assert document_payload["masterIdentifier"]["value"] == "message-3135"
    assert raw_secret_a not in document_read_response.text
    DocumentReference(document_payload)

    for unavailable_response in [other_patient_response, revoked_response]:
        assert unavailable_response.status_code == 404
        assert unavailable_response.json()["resourceType"] == "OperationOutcome"
        OperationOutcome(unavailable_response.json())

    assert organization_search_response.status_code == 200
    organization_search_payload = organization_search_response.json()
    assert organization_search_payload["total"] == 1
    assert organization_search_payload["link"][0] == {
        "relation": "self",
        "url": "http://testserver/fhir/Organization?_count=20&_offset=0",
    }
    assert organization_search_payload["entry"][0]["fullUrl"] == (
        f"http://testserver/fhir/Organization/{organization_id}"
    )
    Organization(organization_search_payload["entry"][0]["resource"])
    assert organization_read_response.status_code == 200
    assert organization_read_response.json()["name"] == "Maple Creek Medical"
    Organization(organization_read_response.json())

    assert practitioner_search_response.status_code == 200
    practitioner_search_payload = practitioner_search_response.json()
    assert practitioner_search_payload["total"] == 1
    assert practitioner_search_payload["link"][0] == {
        "relation": "self",
        "url": "http://testserver/fhir/Practitioner?_count=20&_offset=0",
    }
    assert practitioner_search_payload["entry"][0]["fullUrl"] == (
        f"http://testserver/fhir/Practitioner/{practitioner_id}"
    )
    assert practitioner_search_payload["entry"][0]["resource"]["name"][0]["text"] == "CarlosDoc"
    Practitioner(practitioner_search_payload["entry"][0]["resource"])
    assert practitioner_read_response.status_code == 200
    assert practitioner_read_response.json()["name"][0]["text"] == "CarlosDoc"
    Practitioner(practitioner_read_response.json())


def test_fhir_practitioner_uses_stable_provider_identity_after_rename() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)
    with app.state.session_factory() as session:
        with session.begin():
            create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                created_by="Dr Before",
                created_by_id="provider-42",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                source_reference="provider-before",
            )
            create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                created_by="Dr After",
                created_by_id="provider-42",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                source_reference="provider-after",
            )

    response = client.get(
        "/fhir/Practitioner",
        headers=bearer_headers(token),
    )

    assert response.status_code == 200
    assert response.json()["total"] == 1
    assert response.json()["entry"][0]["resource"]["name"][0]["text"] == "Dr After"
    Bundle(response.json())
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        dashboard = presenters.assemble_email_password_dashboard(
            session,
            account,
            search=None,
            provider="id:provider-42",
            date_from=None,
            date_to=None,
            page=1,
        )

    assert [row.provider for row in dashboard.rows] == ["Dr After", "Dr Before"]
    assert dashboard.provider_options == (
        ProviderFilterOptionViewModel(value="id:provider-42", label="Dr After"),
    )


def test_browser_email_password_index_records_a_sanitized_list_audit_event() -> None:
    """Browsing the password index must be auditable, without storing the raw query."""
    app = migrated_development_app()
    client = TestClient(app)
    browser_sign_in_seeded_patient(app, client)

    plain_view = client.get("/portal/email-passwords")
    filtered_view = client.get("/portal/email-passwords?q=biopsy%20result")

    assert plain_view.status_code == 200
    assert filtered_view.status_code == 200
    with app.state.session_factory() as session:
        list_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_UNLOCK_SECRET_LIST)
                .order_by(PatientPortalAuditEvent.id)
            )
        )
        assert [event.reason for event in list_events] == ["browser", "browser_filtered"]
        assert all(event.outcome == "success" for event in list_events)
        # The search term is PHI-bearing free text and must never be persisted.
        assert all("biopsy" not in (event.reason or "") for event in list_events)


def test_changing_mfa_method_cancels_codes_already_sent_to_the_old_channel() -> None:
    """Switching away from a compromised channel must invalidate codes delivered to it."""
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)
    expire_email_mfa_cooldown(app)
    # A second sign-in leaves a live challenge addressed to the current (old) channel.
    stale_login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    assert stale_login.status_code == 200

    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        update_account_mfa_method(
            session,
            account,
            current_password=STRONG_PASSWORD,
            preferred_mfa_method="email",
            max_failed_password_attempts=5,
        )
        session.commit()

    stale_verify = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": stale_login.json()["mfa_challenge_token"],
            "code": stale_login.json()["development_mfa_code"],
        },
    )

    assert token
    # The cancelled challenge is no longer a usable credential.
    assert stale_verify.status_code == 400
    with app.state.session_factory() as session:
        statuses = list(
            session.scalars(
                select(PatientPortalMfaChallenge.status).order_by(PatientPortalMfaChallenge.id)
            )
        )
        assert "pending" not in statuses


def test_loopback_probes_are_allowed_while_untrusted_hosts_stay_rejected() -> None:
    """Strict Host validation must not reject the service's own liveness/readiness probes."""
    app = migrated_development_app(public_base_url="https://portal.example.test")
    client = TestClient(app, base_url="https://portal.example.test")

    loopback_probe = client.get("/health", headers={"Host": "127.0.0.1"})
    named_probe = client.get("/health", headers={"Host": "localhost"})
    canonical = client.get("/health", headers={"Host": "portal.example.test"})
    untrusted = client.get("/", headers={"Host": "attacker.example"})

    assert loopback_probe.status_code == 200
    assert named_probe.status_code == 200
    assert canonical.status_code == 200
    assert untrusted.status_code == 400


def test_probe_allowed_hosts_can_be_configured_for_orchestrated_deployments() -> None:
    app = migrated_development_app(
        public_base_url="https://portal.example.test",
        probe_allowed_hosts="portal.internal, 10.0.0.7",
    )
    client = TestClient(app, base_url="https://portal.example.test")

    assert client.get("/health", headers={"Host": "portal.internal"}).status_code == 200
    assert client.get("/health", headers={"Host": "10.0.0.7"}).status_code == 200
    # An explicit allowlist replaces the loopback defaults rather than adding to them.
    assert client.get("/health", headers={"Host": "127.0.0.1"}).status_code == 400


def test_fhir_document_reference_read_audits_malformed_and_unknown_ids() -> None:
    """A malformed ID must leave the same audited trail as an unknown one, not silently 404."""
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)

    malformed = client.get(
        "/fhir/DocumentReference/not-a-number",
        headers=bearer_headers(token),
    )
    unknown = client.get(
        "/fhir/DocumentReference/999999",
        headers=bearer_headers(token),
    )

    assert malformed.status_code == 404
    assert unknown.status_code == 404
    with app.state.session_factory() as session:
        read_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == "fhir.read")
                .order_by(PatientPortalAuditEvent.id)
            )
        )
        assert [event.resource_id for event in read_events] == ["not-a-number", "999999"]
        assert all(event.outcome == "failure" for event in read_events)
        assert all(event.reason == "not_found" for event in read_events)


def test_fhir_document_reference_search_pages_all_results_and_uses_canonical_origin() -> None:
    app = migrated_development_app(public_base_url="https://portal.example.test")
    client = TestClient(app, base_url="https://portal.example.test")
    account_id = activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)

    with app.state.session_factory() as session:
        with session.begin():
            for index in range(105):
                create_unlock_secret(
                    session,
                    clinic_id="default",
                    demographic_no=1234,
                    account_id=account_id,
                    secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                    secret=f"PagedSecret{index:03d}!",
                    created_by=f"Provider {index:03d}",
                    created_by_id=f"provider-{index:03d}",
                    encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                    label=f"Paged message {index:03d}",
                    source_reference=f"paged-message-{index:03d}",
                )

    first_page = client.get(
        "/fhir/DocumentReference?_count=100&_offset=0",
        headers=bearer_headers(token),
    )
    second_page = client.get(
        "/fhir/DocumentReference?_count=100&_offset=100",
        headers=bearer_headers(token),
    )

    assert first_page.status_code == 200
    assert first_page.json()["total"] == 105
    assert len(first_page.json()["entry"]) == 100
    assert first_page.json()["link"] == [
        {
            "relation": "self",
            "url": ("https://portal.example.test/fhir/DocumentReference?_count=100&_offset=0"),
        },
        {
            "relation": "next",
            "url": ("https://portal.example.test/fhir/DocumentReference?_count=100&_offset=100"),
        },
    ]
    assert second_page.status_code == 200
    assert second_page.json()["total"] == 105
    assert len(second_page.json()["entry"]) == 5
    assert second_page.json()["link"][1]["relation"] == "previous"
    assert all(
        entry["fullUrl"].startswith("https://portal.example.test/fhir/")
        for entry in first_page.json()["entry"]
    )
    Bundle(first_page.json())
    Bundle(second_page.json())
    assert (
        client.get(
            "/fhir/DocumentReference",
            headers={**bearer_headers(token), "Host": "attacker.example"},
        ).status_code
        == 400
    )

    with app.state.session_factory() as session:
        fhir_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == "fhir.search")
                .order_by(PatientPortalAuditEvent.id)
            )
        )
        assert len(fhir_events) == 2
        assert all(event.resource_type == "DocumentReference" for event in fhir_events)
        provider_options = list_unlock_secret_providers(
            session,
            clinic_id="default",
            demographic_no=1234,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
        )
        assert len(provider_options) == 105
        # The dashboard filter is capped so a lifetime of retained passwords cannot render an
        # unbounded <select>; truncation is reported so the UI can say so.
        dashboard_options = list_unlock_secret_provider_options(
            session,
            clinic_id="default",
            demographic_no=1234,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
        )
        assert len(dashboard_options.options) == MAX_UNLOCK_SECRET_PROVIDER_OPTIONS
        assert dashboard_options.truncated is True


def test_unlock_secret_decryption_rejects_wrong_encryption_secret() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    with app.state.session_factory() as session:
        with session.begin():
            created = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
            )
            unlock_secret_id = created.unlock_secret.id

        with pytest.raises(UnlockSecretDecryptionError):
            read_unlock_secret(
                session,
                unlock_secret_id,
                clinic_id="default",
                account_id=account_id,
                actor_type="patient",
                actor="patient.user",
                encryption_secret="wrong" * 8,
            )


def test_unlock_secret_ciphertext_is_bound_to_its_record_context() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        with session.begin():
            first = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                source_reference="context-first",
            )
            second = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                source_reference="context-second",
            )
            first.unlock_secret.encrypted_secret = second.unlock_secret.encrypted_secret
            first.unlock_secret.encryption_nonce = second.unlock_secret.encryption_nonce

            with pytest.raises(UnlockSecretDecryptionError):
                read_unlock_secret(
                    session,
                    first.unlock_secret.id,
                    clinic_id="default",
                    demographic_no=1234,
                    actor_type="staff",
                    actor="CarlosDoc",
                    encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                )


def test_interop_helpers_build_valid_fhir_r4_and_hl7_v251_patient_identity() -> None:
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )

    fhir_patient = build_fhir_r4_patient(identity)
    fhir_practitioner = build_fhir_r4_practitioner(
        clinic_id="default",
        name=" Dr | example\nprovider ",
    )
    hl7_message = build_hl7_v251_patient_registration(
        identity,
        message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
        message_control_id="MSG0001",
    )
    hl7_profile = load_hl7_v251_patient_registration_profile()

    assert FHIR_RELEASE == "R4"
    assert fhir_patient["resourceType"] == "Patient"
    assert fhir_patient["birthDate"] == SEEDED_INVITE_DOB
    assert fhir_patient["name"][0]["family"] == "Patient"
    assert fhir_practitioner["resourceType"] == "Practitioner"
    assert fhir_practitioner["name"][0]["text"] == "Dr | example provider"
    assert fhir_practitioner["id"] == build_fhir_practitioner_id(
        clinic_id="default",
        name=" Dr | example\nprovider ",
    )
    Practitioner(fhir_practitioner)
    assert HL7_V2_VERSION == "2.5.1"
    assert "MSH|^~\\&|CARLOS|default|CARLOSPORTAL|default" in hl7_message
    assert "PID|||1234^^^default^MR~ABCD12345678^^^CARLOSHCN^JHN" in hl7_message
    assert "^NET^Internet^example.patient@example.com" in hl7_message
    assert "ADT^A04^ADT_A01" in hl7_message
    assert validate_hl7_v251_message(hl7_message) == hl7_message
    assert hl7_profile["id"] == HL7_PATIENT_REGISTRATION_PROFILE_ID
    assert hl7_profile["message_structure"] == "ADT_A01"
    assert validate_hl7_v251_patient_registration_profile(hl7_message) == hl7_message


def test_hl7_patient_registration_profile_rejects_nonconforming_messages() -> None:
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )
    hl7_message = build_hl7_v251_patient_registration(
        identity,
        message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
        message_control_id="MSG0001",
    )
    wrong_receiver = hl7_message.replace("CARLOSPORTAL", "OTHERAPP", 1)
    missing_health_card = hl7_message.replace("~ABCD12345678^^^CARLOSHCN^JHN", "", 1)
    missing_visit = hl7_message.replace("\rPV1||O", "", 1)

    with pytest.raises(Hl7ConformanceProfileError, match="MSH-5"):
        validate_hl7_v251_patient_registration_profile(wrong_receiver)
    with pytest.raises(Hl7ConformanceProfileError, match="JHN"):
        validate_hl7_v251_patient_registration_profile(missing_health_card)
    with pytest.raises(Hl7ConformanceProfileError, match="PV1"):
        validate_hl7_v251_patient_registration_profile(missing_visit)


def test_hl7_patient_identity_rejects_unsafe_hl7_values() -> None:
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="clinic-with-a-very-long-id-over-twenty-chars",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )
    email_with_separator = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email="a&b@example.com",
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )

    with pytest.raises(ValueError, match="clinic_id"):
        build_hl7_v251_patient_registration(
            identity,
            message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
            message_control_id="MSG0001",
        )
    with pytest.raises(ValueError, match="email"):
        build_hl7_v251_patient_registration(
            email_with_separator,
            message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
            message_control_id="MSG0001",
        )


def test_session_scope_commits_success() -> None:
    engine = create_portal_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    session_factory = create_session_factory(engine)

    with session_scope(session_factory) as session:
        committed_invite, _ = create_service_invite(session, 1234, "CarlosDoc")
        committed_invite_id = committed_invite.id

    with session_factory() as session:
        assert session.get(PatientPortalInvite, committed_invite_id) is not None

    engine.dispose()


def test_route_transaction_commit_failure_prevents_success_response(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    app = main.create_app(development_settings(database_url="sqlite+pysqlite:///:memory:"))

    def fail_commit(_: Session) -> None:
        raise SQLAlchemyError("simulated commit failure")

    monkeypatch.setattr(Session, "commit", fail_commit)
    response = TestClient(app, raise_server_exceptions=False).get("/internal/health/db")

    assert response.status_code == 500


def test_audit_retention_prunes_only_events_older_than_cutoff() -> None:
    app = migrated_development_app()
    now = datetime(2026, 7, 23, 12, 0, tzinfo=UTC)
    cutoff = audit_retention_cutoff(365, now=now)

    with app.state.session_factory() as session:
        with session.begin():
            old_event = record_audit_event(
                session,
                event_type=AUDIT_EVENT_LOGIN,
                outcome=AUDIT_OUTCOME_FAILURE,
                actor_type="patient",
                reason="invalid_credentials",
            )
            recent_event = record_audit_event(
                session,
                event_type=AUDIT_EVENT_LOGIN,
                outcome=AUDIT_OUTCOME_SUCCESS,
                actor_type="patient",
                reason="mfa_required",
            )
            old_event.created_at = cutoff - timedelta(seconds=1)
            recent_event.created_at = cutoff
            old_event_id = old_event.id
            recent_event_id = recent_event.id

        with session.begin():
            first_prune_count = prune_audit_events(session, before=cutoff, batch_size=1)
        with session.begin():
            second_prune_count = prune_audit_events(session, before=cutoff, batch_size=1)

        remaining_event_ids = set(session.scalars(select(PatientPortalAuditEvent.id)))

    assert first_prune_count == 1
    assert second_prune_count == 0
    assert old_event_id not in remaining_event_ids
    assert recent_event_id in remaining_event_ids


def test_sqlite_backup_and_restore_round_trip(tmp_path) -> None:
    database_path = tmp_path / "portal.db"
    backup_path = tmp_path / "portal.backup.db"
    database_url = f"sqlite+pysqlite:///{database_path}"
    engine = create_portal_engine(database_url)
    Base.metadata.create_all(engine)
    session_factory = create_session_factory(engine)

    with session_scope(session_factory) as session:
        original_invite, _ = create_service_invite(session)
        original_invite_id = original_invite.id

    engine.dispose()
    created_backup_path = backup_sqlite_database(database_url, backup_path)

    assert created_backup_path == backup_path
    assert backup_path.exists()
    with pytest.raises(BackupDestinationExistsError):
        backup_sqlite_database(database_url, backup_path)
    with pytest.raises(BackupDestinationExistsError):
        backup_sqlite_database(database_url, database_path, overwrite=True)
    with pytest.raises(BackupDestinationExistsError):
        restore_sqlite_database(database_url, database_path, overwrite=True)

    engine = create_portal_engine(database_url)
    session_factory = create_session_factory(engine)
    with session_scope(session_factory) as session:
        create_service_invite(session, demographic_no=5678)
        assert len(list_invites(session, limit=10)) == 2
    engine.dispose()

    restored_path = restore_sqlite_database(database_url, backup_path, overwrite=True)

    assert restored_path == database_path
    engine = create_portal_engine(database_url)
    session_factory = create_session_factory(engine)
    with session_scope(session_factory) as session:
        restored_invites = list_invites(session, limit=10)
        assert [(invite.id, invite.demographic_no) for invite in restored_invites] == [
            (original_invite_id, 1234)
        ]
    engine.dispose()

    with pytest.raises(BackupUnsupportedError):
        backup_sqlite_database(DEFAULT_DATABASE_URL, tmp_path / "postgres.backup")


def test_database_identifiers_fit_postgresql_limit() -> None:
    identifiers = []
    for table in Base.metadata.tables.values():
        identifiers.extend(
            constraint.name for constraint in table.constraints if constraint.name is not None
        )
        identifiers.extend(index.name for index in table.indexes if index.name is not None)

    assert sorted(name for name in identifiers if len(name) > 63) == []


def test_environment_aliases_are_normalized() -> None:
    settings = production_settings(
        environment=" prod ",
    )

    assert settings.environment == "production"
    assert settings.is_production


def test_default_database_url_does_not_embed_credentials() -> None:
    database_url = make_url(DEFAULT_DATABASE_URL)

    assert database_url.username is None
    assert database_url.password is None


def test_development_defaults_do_not_embed_session_secret() -> None:
    assert development_settings().session_secret is None


def test_development_dev_admin_is_disabled_by_default() -> None:
    assert not development_settings().is_dev_admin_enabled
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_DEV_ADMIN_TOKEN"):
        development_settings(enable_dev_admin=True)
    assert development_settings(
        enable_dev_admin=True,
        dev_admin_token=DEV_ADMIN_TOKEN,
    ).is_dev_admin_enabled


def test_clinic_id_is_normalized() -> None:
    settings = development_settings(clinic_id=" clinic-a ")

    assert settings.clinic_id == "clinic-a"


def test_clinic_id_must_not_be_blank() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_CLINIC_ID"):
        development_settings(clinic_id=" ")


def test_trusted_client_ip_header_is_normalized() -> None:
    settings = development_settings(
        trusted_client_ip_header=" X-Forwarded-For ",
        trusted_proxy_cidrs="10.0.0.0/8",
    )

    assert settings.trusted_client_ip_header == "x-forwarded-for"


def test_trusted_proxy_chain_uses_rightmost_untrusted_client() -> None:
    client_address = main.parse_trusted_client_ip_header(
        "x-forwarded-for",
        "198.51.100.200, 203.0.113.7, 10.0.0.10",
        peer_address="10.0.0.20",
        trusted_proxy_cidrs="10.0.0.0/8",
    )
    spoofed_header = main.parse_trusted_client_ip_header(
        "x-forwarded-for",
        "203.0.113.99",
        peer_address="192.0.2.10",
        trusted_proxy_cidrs="10.0.0.0/8",
    )

    assert client_address == "203.0.113.7"
    assert spoofed_header is None


def test_trusted_proxy_header_and_cidrs_must_be_configured_together() -> None:
    with pytest.raises(ValidationError, match="TRUSTED_PROXY_CIDRS"):
        development_settings(trusted_client_ip_header="x-forwarded-for")
    with pytest.raises(ValidationError, match="TRUSTED_CLIENT_IP_HEADER"):
        development_settings(trusted_proxy_cidrs="10.0.0.0/8")


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


def test_non_development_rejects_missing_identity_proof_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_IDENTITY_PROOF_SECRET"):
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )


def test_non_development_rejects_missing_audit_hash_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_AUDIT_HASH_SECRET"):
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )


def test_non_development_rejects_missing_unlock_secret_encryption_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET"):
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )


def test_production_requires_configured_mfa_email_delivery() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SMTP_HOST"):
        production_settings(
            smtp_host=None,
            smtp_from_address=None,
        )


def test_production_requires_configured_mfa_sms_delivery() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SMS_WEBHOOK_URL"):
        production_settings(sms_webhook_url=None, sms_webhook_token=None)


def test_non_development_sms_webhook_requires_https() -> None:
    with pytest.raises(ValidationError, match="must use HTTPS"):
        production_settings(sms_webhook_url="http://sms.example.test/messages")


def test_production_rejects_remote_postgresql_without_verified_tls() -> None:
    with pytest.raises(ValidationError, match="sslmode=verify-full"):
        production_settings(database_url="postgresql+psycopg://portal@database.example.test/portal")


def test_production_accepts_remote_postgresql_with_verified_tls() -> None:
    settings = production_settings(
        database_url=(
            "postgresql+psycopg://portal@database.example.test/portal"
            "?sslmode=verify-full&sslrootcert=/run/secrets/database-ca.pem"
        )
    )

    assert "sslmode=verify-full" in settings.database_url


def test_default_password_lockout_threshold_is_ten() -> None:
    assert development_settings().auth_max_failed_password_attempts == 10


def test_internal_health_token_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        development_settings(internal_health_token="short")


def test_identity_proof_secret_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_IDENTITY_PROOF_SECRET"):
        development_settings(identity_proof_secret="short")


def test_audit_hash_secret_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_AUDIT_HASH_SECRET"):
        development_settings(audit_hash_secret="short")


def test_unlock_secret_encryption_secret_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET"):
        development_settings(unlock_secret_encryption_secret="short")


def test_dev_admin_token_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_DEV_ADMIN_TOKEN"):
        development_settings(dev_admin_token="short")


def test_activation_rate_limit_settings_are_bounded() -> None:
    with pytest.raises(ValidationError, match="activation_failure_window_seconds"):
        development_settings(activation_failure_window_seconds=59)
    with pytest.raises(ValidationError, match="activation_max_failures_per_invite"):
        development_settings(activation_max_failures_per_invite=0)
    with pytest.raises(ValidationError, match="activation_max_failures_per_client"):
        development_settings(activation_max_failures_per_client=0)


def test_auth_policy_settings_are_bounded() -> None:
    with pytest.raises(ValidationError, match="auth_max_failed_password_attempts"):
        development_settings(auth_max_failed_password_attempts=0)
    with pytest.raises(ValidationError, match="mfa_max_failed_attempts"):
        development_settings(mfa_max_failed_attempts=0)
    with pytest.raises(ValidationError, match="session_ttl_seconds"):
        development_settings(session_ttl_seconds=299)
    with pytest.raises(ValidationError, match="mfa_code_ttl_seconds"):
        development_settings(mfa_code_ttl_seconds=59)
    with pytest.raises(ValidationError, match="mfa_email_resend_cooldown_seconds"):
        development_settings(mfa_email_resend_cooldown_seconds=29)
    with pytest.raises(ValidationError, match="mfa_sms_resend_cooldown_seconds"):
        development_settings(mfa_sms_resend_cooldown_seconds=59)
    with pytest.raises(ValidationError, match="password_reset_token_ttl_seconds"):
        development_settings(password_reset_token_ttl_seconds=299)
    with pytest.raises(ValidationError, match="password_reset_request_cooldown_seconds"):
        development_settings(password_reset_request_cooldown_seconds=29)
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_REQUIRE_MFA"):
        production_settings(require_mfa=False)


def test_hardening_settings_are_bounded() -> None:
    settings = development_settings()

    assert settings.audit_retention_days == DEFAULT_AUDIT_RETENTION_DAYS
    assert settings.global_rate_limit_window_seconds == 60
    assert settings.global_rate_limit_max_requests == 300
    assert settings.maintenance_mode is False
    assert settings.maintenance_retry_after_seconds == 300
    with pytest.raises(ValidationError, match="global_rate_limit_window_seconds"):
        development_settings(global_rate_limit_window_seconds=0)
    with pytest.raises(ValidationError, match="global_rate_limit_max_requests"):
        development_settings(global_rate_limit_max_requests=0)
    with pytest.raises(ValidationError, match="audit_retention_days"):
        development_settings(audit_retention_days=25 * 365)
    with pytest.raises(ValidationError, match="maintenance_retry_after_seconds"):
        development_settings(maintenance_retry_after_seconds=59)


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


def test_unusable_password_hash_is_a_server_fault_not_a_patient_lockout() -> None:
    """A hash that cannot be parsed must not be blamed on the patient or burn their budget."""
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.password_hash = "$2b$12$notanargon2hash00000000000000000000000000000000000000"
        session.commit()

    responses = [
        client.post("/auth/login", json={"username": "patient.user", "password": STRONG_PASSWORD})
        for _ in range(3)
    ]

    assert [response.status_code for response in responses] == [503, 503, 503]
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        # The correct password was supplied every time; the patient must not be pushed to lockout.
        assert account.failed_login_count == 0
        assert account.locked_at is None
        reasons = {
            event.reason
            for event in session.scalars(
                select(PatientPortalAuditEvent).where(
                    PatientPortalAuditEvent.event_type == AUDIT_EVENT_LOGIN,
                    PatientPortalAuditEvent.outcome == AUDIT_OUTCOME_FAILURE,
                )
            )
        }
        assert "password_hash_unusable" in reasons
        assert "invalid_credentials" not in reasons


def test_contact_change_records_a_failure_when_the_security_notice_cannot_be_sent() -> None:
    """The old-address notice is the takeover alarm; its failure must be durable and visible."""

    class FailingNoticeSender:
        def send_code(self, **kwargs: object) -> None:
            return None

        def send_password_reset(self, **kwargs: object) -> None:
            return None

        def send_contact_change_notice(self, **kwargs: object) -> None:
            raise PortalEmailDeliveryError("portal email delivery failed")

    original_builder = main.build_portal_email_sender
    main.build_portal_email_sender = lambda settings: FailingNoticeSender()
    try:
        app = migrated_development_app()
        client = TestClient(app)
        browser_sign_in_seeded_patient(app, client)
        csrf_token = CSRF_TOKEN_PATTERN.search(client.get("/portal/account").text)
        assert csrf_token is not None
        response = client.post(
            "/portal/account/contact",
            data={
                "csrf_token": csrf_token.group(1),
                "current_password": STRONG_PASSWORD,
                "email": "replacement.patient@example.com",
                "phone_number": "",
            },
            follow_redirects=False,
        )
    finally:
        main.build_portal_email_sender = original_builder

    assert response.status_code == 303
    assert response.headers["location"] == "/portal/account?status=contact-updated-notice-failed"
    with app.state.session_factory() as session:
        outcomes = [
            (event.outcome, event.reason)
            for event in session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE)
                .order_by(PatientPortalAuditEvent.id)
            )
        ]
        # The change really did happen, and the fact the alarm never fired is recorded alongside it.
        assert outcomes == [
            (AUDIT_OUTCOME_SUCCESS, "updated"),
            (AUDIT_OUTCOME_FAILURE, "delivery_unavailable"),
        ]


def test_password_change_revokes_a_reset_token_issued_before_the_change() -> None:
    """A reset link captured before the patient changed their password must not still work."""
    app = migrated_development_app()
    client = TestClient(app)
    browser_sign_in_seeded_patient(app, client)
    reset_request = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    stale_token = reset_request.json()["development_reset_token"]
    assert stale_token

    csrf_token = CSRF_TOKEN_PATTERN.search(client.get("/portal/account").text)
    assert csrf_token is not None
    replacement_password = "V1ctimChosen!pass"
    change = client.post(
        "/portal/account/password",
        data={
            "csrf_token": csrf_token.group(1),
            "current_password": STRONG_PASSWORD,
            "new_password": replacement_password,
            "new_password_confirmation": replacement_password,
        },
        follow_redirects=False,
    )
    replay = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": stale_token, "new_password": "Att4ckerChosen!x"},
    )

    assert change.status_code == 303
    assert replay.status_code == 400
    with app.state.session_factory() as session:
        statuses = [
            token.status for token in session.scalars(select(PatientPortalPasswordResetToken))
        ]
        assert statuses == ["revoked"]


def test_failed_mfa_delivery_does_not_reserve_the_resend_cooldown() -> None:
    """A provider outage must not throttle a patient who never received a code."""

    class FailingSender:
        def send_code(self, **kwargs: object) -> None:
            raise PortalEmailDeliveryError("portal email delivery failed")

        def send_password_reset(self, **kwargs: object) -> None:
            return None

        def send_contact_change_notice(self, **kwargs: object) -> None:
            return None

    original_builder = main.build_portal_email_sender
    main.build_portal_email_sender = lambda settings: FailingSender()
    try:
        app = migrated_development_app()
        client = TestClient(app)
        activate_seeded_patient_account(app, client)
        first = client.post(
            "/auth/login", json={"username": "patient.user", "password": STRONG_PASSWORD}
        )
        immediate_retry = client.post(
            "/auth/login", json={"username": "patient.user", "password": STRONG_PASSWORD}
        )
    finally:
        main.build_portal_email_sender = original_builder

    assert first.status_code == 503
    # Previously 429 "sent recently": the cooldown was reserved before the send that never landed.
    assert immediate_retry.status_code == 503


def test_oversized_and_malformed_resource_ids_are_audited_not_five_hundreds() -> None:
    """Client-supplied ids must never reach the driver or break the audit write."""
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)

    oversized = [
        client.get(f"/fhir/{resource}/{'a' * 300}", headers=bearer_headers(token))
        for resource in ("Patient", "Organization", "Practitioner", "DocumentReference")
    ]
    control_character = client.get("/fhir/Practitioner/%00", headers=bearer_headers(token))
    huge_numeric = client.get(
        f"/api/patient/email-passwords/{2**63}", headers=bearer_headers(token)
    )

    assert [response.status_code for response in oversized] == [404, 404, 404, 404]
    assert control_character.status_code == 404
    assert huge_numeric.status_code == 422
    with app.state.session_factory() as session:
        read_events = list(
            session.scalars(
                select(PatientPortalAuditEvent).where(
                    PatientPortalAuditEvent.event_type == "fhir.read"
                )
            )
        )
        # Every rejected probe still leaves a trace; over-length ids used to lose the event.
        assert len(read_events) == 5
        assert all(len(event.resource_id or "") <= 128 for event in read_events)


def test_password_reset_redemption_revokes_every_preexisting_session() -> None:
    """Reset is the takeover-recovery path: a stolen session must not survive it.

    Unlike the lock path there is no lazy-kill backstop here, because redemption clears
    ``force_password_reset``; the eager revocation is the only control.
    """
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    first_token = sign_in_patient_api_session(client)
    expire_email_mfa_cooldown(app)
    second_token = sign_in_patient_api_session(client)
    reset_request = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    reset_token = reset_request.json()["development_reset_token"]

    completed = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": reset_token, "new_password": STRONG_RESET_PASSWORD},
    )

    assert completed.status_code == 200
    assert client.get("/auth/session", headers=bearer_headers(first_token)).status_code == 401
    assert client.get("/auth/session", headers=bearer_headers(second_token)).status_code == 401
    with app.state.session_factory() as session:
        sessions = list(session.scalars(select(PatientPortalSession)))
        assert len(sessions) == 2
        assert all(row.revoked_at is not None for row in sessions)
        assert all(row.revoked_reason == "password_reset" for row in sessions)


def test_dashboard_template_only_reads_fields_the_view_model_declares() -> None:
    """The typed view model exists to make a template/field mismatch fail loudly.

    Jinja renders an unknown attribute as empty rather than raising, so before the view model
    a renamed context key produced a silently blank cell. This pins the template's field usage
    against the declared contract instead.
    """
    template = (main.PACKAGE_DIR / "templates" / "dashboard.jinja").read_text(encoding="utf-8")
    dashboard_fields = {field.name for field in fields(EmailPasswordDashboardViewModel)}
    row_fields = {field.name for field in fields(EmailPasswordRowViewModel)}

    used_dashboard_fields = set(re.findall(r"email_passwords\.([a-z_]+)", template))
    used_row_fields = set(re.findall(r"\brow\.([a-z_]+)", template))

    assert used_dashboard_fields, "template no longer reads the email-password view model"
    undeclared = sorted(used_dashboard_fields - dashboard_fields)
    assert not undeclared, f"template reads undeclared dashboard fields: {undeclared}"
    undeclared_rows = sorted(used_row_fields - row_fields)
    assert not undeclared_rows, f"template reads undeclared row fields: {undeclared_rows}"


def test_email_password_view_model_is_immutable() -> None:
    """`*ViewModel` carries no behaviour and cannot be mutated after assembly."""
    row = EmailPasswordRowViewModel(
        id=1,
        subject="Lab results",
        provider="CarlosDoc",
        sent_at="2026-08-05 10:00 UTC",
        source_reference="message-1",
        is_available=True,
    )

    with pytest.raises(AttributeError):
        row.subject = "changed"  # type: ignore[misc]


def test_presenters_perform_no_writes() -> None:
    """`*ViewModelAssembler` is read-only orchestration; a write belongs to the route.

    Enforced structurally rather than by convention, because the previous version of the
    dashboard audit event was added inside the render path and looked perfectly reasonable there.
    """
    presenter_source = (main.PACKAGE_DIR / "presenters.py").read_text(encoding="utf-8")

    forbidden = re.findall(
        r"session\.(?:commit|add|flush|delete)\(|record_audit_event\(",
        presenter_source,
    )

    assert not forbidden, f"presenters.py must not write: {sorted(set(forbidden))}"


def _sample_mfa_delivery() -> MfaChallengeDelivery:
    return MfaChallengeDelivery(
        challenge_id=1,
        challenge_token="challenge-token",
        code="123456",
        delivery_method="email",
        destination="patient@example.com",
        available_delivery_methods=("email",),
        expires_at=utc_now(),
    )


def _template_variable_names(template_name: str) -> set[str]:
    """Top-level names a template (and its includes) reads, per Jinja's own parser."""
    environment = main.templates.env
    source, _, _ = environment.loader.get_source(environment, template_name)
    names = meta.find_undeclared_variables(environment.parse(source))
    for included in meta.find_referenced_templates(environment.parse(source)):
        if included is not None:
            names |= _template_variable_names(included)
    return names


@pytest.mark.parametrize(
    ("template_name", "context_builder"),
    [
        ("index.jinja", "index_template_context"),
        ("activate.jinja", "public_auth_template_context"),
        ("password_reset_request.jinja", "public_auth_template_context"),
        ("password_reset_complete.jinja", "public_auth_template_context"),
        ("auth_result.jinja", "public_auth_template_context"),
        ("locked.jinja", "public_auth_template_context"),
        ("mfa.jinja", "mfa_template_context"),
    ],
)
def test_public_template_reads_only_keys_its_context_builder_supplies(
    template_name: str, context_builder: str
) -> None:
    """Every variable a public template reads must be supplied by its assembler.

    Jinja renders an unknown name as empty instead of raising, so an assembler that stops
    supplying a key -- or a template that starts reading a new one -- fails silently in the
    browser. This closes that gap for the pages that do not have a dedicated view model.
    """
    settings = Settings(environment="development", database_url="sqlite+pysqlite:///:memory:")
    builder = getattr(main, context_builder)
    if context_builder == "mfa_template_context":
        context = builder(
            SimpleNamespace(),
            settings=settings,
            delivery=_sample_mfa_delivery(),
            csrf_token="token",
        )
    else:
        context = builder(SimpleNamespace(), settings=settings, csrf_token="token")
    # Derived from the assembler itself rather than a hand-maintained list, so the assertion
    # cannot drift away from what the code actually supplies.
    supplied = set(context) | {"url_for", "request"}

    used = _template_variable_names(template_name)

    undeclared = sorted(used - supplied)
    assert not undeclared, f"{template_name} reads keys no assembler supplies: {undeclared}"
