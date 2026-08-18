"""Shared fixtures, builders, and recording test doubles for the portal test modules.

Split out of the former single `test_app.py` so each area's tests can be read without scrolling
past the others. Nothing here asserts; it only builds apps, seeds accounts, drives the sign-in
flow, and records what the outbound senders were asked to deliver.
"""

import re
from datetime import UTC, datetime, timedelta

from alembic.config import Config
from alembic.runtime.migration import MigrationContext
from alembic.script import ScriptDirectory
from fastapi.testclient import TestClient
from jinja2 import meta
from sqlalchemy import select
from sqlalchemy.orm import Session

from carlos_patient_portal import main, web_support
from carlos_patient_portal.auth import MfaChallengeDelivery
from carlos_patient_portal.config import (
    MIN_PRODUCTION_SECRET_LENGTH,
    Settings,
)
from carlos_patient_portal.database import (
    Base,
)
from carlos_patient_portal.email_delivery import PortalEmailDeliveryError, PortalEmailSender
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.invites import (
    create_invite,
)
from carlos_patient_portal.models import (
    PatientPortalAccount,
    PatientPortalInvite,
    PatientPortalMfaChallenge,
    utc_now,
)
from carlos_patient_portal.sms_delivery import PortalSmsDeliveryError, PortalSmsSender

NON_DEVELOPMENT_SESSION_SECRET = "s" * MIN_PRODUCTION_SECRET_LENGTH


IDENTITY_PROOF_SECRET = "i" * MIN_PRODUCTION_SECRET_LENGTH


AUDIT_HASH_SECRET = "a" * MIN_PRODUCTION_SECRET_LENGTH

OUTBOX_ENCRYPTION_SECRET = "o" * MIN_PRODUCTION_SECRET_LENGTH


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
        **non_development_settings_values("production"),
        **overrides,
    }
    return Settings(**values)


def staging_settings(**overrides: object) -> Settings:
    values = {
        **non_development_settings_values("staging"),
        **overrides,
    }
    return Settings(**values)


def non_development_settings_values(environment: str) -> dict[str, object]:
    return {
        "environment": environment,
        "clinic_id": TEST_CLINIC_ID,
        "clinic_name": TEST_CLINIC_NAME,
        "session_secret": NON_DEVELOPMENT_SESSION_SECRET,
        "identity_proof_secret": IDENTITY_PROOF_SECRET,
        "audit_hash_secret": AUDIT_HASH_SECRET,
        "outbox_encryption_secret": OUTBOX_ENCRYPTION_SECRET,
        "unlock_secret_encryption_secret": UNLOCK_SECRET_ENCRYPTION_SECRET,
        "internal_health_token": INTERNAL_HEALTH_TOKEN,
        "internal_api_token": INTERNAL_API_TOKEN,
        "smtp_host": "mail.internal",
        "smtp_from_address": "portal@example.test",
        "smtp_starttls": True,
        "public_base_url": "https://portal.example.test",
        "sms_webhook_url": "https://sms.example.test/messages",
        "sms_webhook_token": "sms-webhook-token-value-32-characters",
    }


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
    alembic_config.set_main_option("script_location", str(web_support.PACKAGE_DIR / "migrations"))
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
        message_id: str | None = None,
    ) -> None:
        message: dict[str, object] = {
            "recipient": recipient,
            "reset_url": reset_url,
            "expires_in_seconds": expires_in_seconds,
        }
        if message_id is not None:
            message["message_id"] = message_id
        self.messages.append(message)
        if self.fail:
            raise PortalEmailDeliveryError("simulated delivery failure")

    def send_contact_change_notice(
        self, *, recipient: str, message_id: str | None = None
    ) -> None:
        message: dict[str, object] = {
            "recipient": recipient,
            "type": "contact_change_notice",
        }
        if message_id is not None:
            message["message_id"] = message_id
        self.messages.append(message)
        if self.fail:
            raise PortalEmailDeliveryError("simulated delivery failure")

    def send_email_change_confirmation(
        self,
        *,
        recipient: str,
        confirmation_url: str,
        expires_in_seconds: int,
    ) -> None:
        self.messages.append(
            {
                "recipient": recipient,
                "type": "email_change_confirmation",
                "confirmation_url": confirmation_url,
                "expires_in_seconds": expires_in_seconds,
            }
        )
        if self.fail:
            raise PortalEmailDeliveryError("simulated delivery failure")

    def send_email_change_requested_notice(self, *, recipient: str) -> None:
        self.messages.append({"recipient": recipient, "type": "email_change_requested_notice"})
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
        web_support.DEV_ADMIN_ACTOR_HEADER: actor,
    }


def get_csrf_token(client: TestClient) -> str:
    response = client.get("/")
    match = CSRF_TOKEN_PATTERN.search(response.text)

    assert response.status_code == 200
    assert match is not None
    csrf_token = match.group(1)
    assert response.cookies.get(web_support.CSRF_COOKIE_NAME) == csrf_token
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
    assert web_support.PORTAL_SESSION_COOKIE_NAME not in login_response.cookies
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
    assert web_support.PORTAL_SESSION_COOKIE_NAME in verify_response.cookies
    assert f"{web_support.PORTAL_SESSION_COOKIE_NAME}=" in set_cookie_header
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


class FailingNoticeSender:
    """A sender whose advisory notices fail, used to pin what a failed alarm must do."""

    def __init__(self, *, fail_confirmation: bool = False) -> None:
        self.fail_confirmation = fail_confirmation

    def send_code(self, **kwargs: object) -> None:
        return None

    def send_password_reset(self, **kwargs: object) -> None:
        return None

    def send_contact_change_notice(self, **kwargs: object) -> None:
        raise PortalEmailDeliveryError("portal email delivery failed")

    def send_email_change_requested_notice(self, **kwargs: object) -> None:
        raise PortalEmailDeliveryError("portal email delivery failed")

    def send_email_change_confirmation(self, **kwargs: object) -> None:
        if self.fail_confirmation:
            raise PortalEmailDeliveryError("portal email delivery failed")
        return None


def run_with_email_sender(sender: object, action):
    original_builder = main.build_portal_email_sender
    main.build_portal_email_sender = lambda settings: sender
    try:
        return action()
    finally:
        main.build_portal_email_sender = original_builder


def submit_contact_change(app, client, **overrides: str):
    csrf_token = CSRF_TOKEN_PATTERN.search(client.get("/portal/account").text)
    assert csrf_token is not None
    return client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token.group(1),
            "current_password": STRONG_PASSWORD,
            "email": "replacement.patient@example.com",
            "phone_number": "",
            **overrides,
        },
        follow_redirects=False,
    )


def _sample_mfa_delivery() -> MfaChallengeDelivery:
    return MfaChallengeDelivery(
        challenge_id=1,
        challenge_token="challenge-token",
        code="123456",
        delivery_method="email",
        destination="patient@example.com",
        available_delivery_methods=("email",),
        expires_at=utc_now(),
        expected_code_hash="c" * 64,
    )


def _template_variable_names(template_name: str) -> set[str]:
    """Top-level names a template (and its includes) reads, per Jinja's own parser."""
    environment = web_support.templates.env
    source, _, _ = environment.loader.get_source(environment, template_name)
    names = meta.find_undeclared_variables(environment.parse(source))
    for included in meta.find_referenced_templates(environment.parse(source)):
        if included is not None:
            names |= _template_variable_names(included)
    return names


def request_seeded_email_change(
    app,
    client: TestClient,
    sender: RecordingPortalEmailSender,
    *,
    email: str = "replacement.patient@example.com",
) -> str:
    """Submit a contact change and return the confirmation token from the delivered link."""
    account_response = client.get("/portal/account")
    response = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token_from_response(account_response),
            "email": email,
            "phone_number": "",
            "current_password": STRONG_PASSWORD,
        },
        follow_redirects=False,
    )
    assert response.status_code == 303
    confirmation = next(
        message
        for message in reversed(sender.messages)
        if message.get("type") == "email_change_confirmation"
    )
    return str(confirmation["confirmation_url"]).partition("#token=")[2]


def confirm_seeded_email_change(client: TestClient, confirmation_token: str):
    confirmation_page = client.get("/auth/email-change/confirm")
    return client.post(
        "/auth/email-change/confirm",
        data={
            "csrf_token": csrf_token_from_response(confirmation_page),
            "reset_token": confirmation_token,
        },
    )
