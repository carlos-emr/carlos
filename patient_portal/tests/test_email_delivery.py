import smtplib
from email.message import EmailMessage

import pytest

from carlos_patient_portal.config import (
    DEFAULT_DEVELOPMENT_SMTP_FROM_ADDRESS,
    Settings,
)
from carlos_patient_portal.email_delivery import (
    PortalEmailDeliveryError,
    SmtpPortalEmailSender,
    build_portal_email_sender,
)


class RecordingSmtp:
    instance: "RecordingSmtp | None" = None
    refused_recipients: dict[str, tuple[int, bytes]] = {}

    def __init__(self, *, host: str, port: int, timeout: int) -> None:
        self.host = host
        self.port = port
        self.timeout = timeout
        self.started_tls = False
        self.login_credentials: tuple[str, str] | None = None
        self.message: EmailMessage | None = None
        RecordingSmtp.instance = self

    def __enter__(self) -> "RecordingSmtp":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def starttls(self, *, context: object) -> None:
        assert context is not None
        self.started_tls = True

    def login(self, username: str, password: str) -> None:
        self.login_credentials = (username, password)

    def send_message(self, message: EmailMessage) -> dict[str, tuple[int, bytes]]:
        self.message = message
        return self.refused_recipients


def smtp_settings(**overrides: object) -> Settings:
    return Settings(
        environment="development",
        smtp_host="mail.internal",
        smtp_port=2525,
        smtp_from_address="portal@example.test",
        **overrides,
    )


def test_smtp_sender_delivers_plain_text_code_with_tls_and_auth(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    RecordingSmtp.refused_recipients = {}
    monkeypatch.setattr(smtplib, "SMTP", RecordingSmtp)
    sender = SmtpPortalEmailSender(
        smtp_settings(
            smtp_starttls=True,
            smtp_username="portal-user",
            smtp_password="smtp-secret",
        )
    )

    sender.send_code(
        recipient="patient@example.test",
        code="123456",
        expires_in_seconds=600,
    )

    smtp = RecordingSmtp.instance
    assert smtp is not None
    assert (smtp.host, smtp.port, smtp.timeout) == ("mail.internal", 2525, 10)
    assert smtp.started_tls is True
    assert smtp.login_credentials == ("portal-user", "smtp-secret")
    assert smtp.message is not None
    assert smtp.message["From"] == "portal@example.test"
    assert smtp.message["To"] == "patient@example.test"
    assert smtp.message["Subject"] == "Your CARLOS Patient Portal verification code"
    assert smtp.message["Auto-Submitted"] == "auto-generated"
    assert "123456" in smtp.message.get_content()
    assert "10 minutes" in smtp.message.get_content()


def test_smtp_sender_wraps_refused_recipient_without_exposing_code(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    RecordingSmtp.refused_recipients = {"patient@example.test": (550, b"recipient rejected")}
    monkeypatch.setattr(smtplib, "SMTP", RecordingSmtp)
    sender = SmtpPortalEmailSender(smtp_settings())

    with pytest.raises(PortalEmailDeliveryError) as exc_info:
        sender.send_code(
            recipient="patient@example.test",
            code="654321",
            expires_in_seconds=600,
        )

    assert "654321" not in str(exc_info.value)
    assert "patient@example.test" not in repr(exc_info.value)
    assert exc_info.value.__cause__ is None


def test_smtp_sender_delivers_password_reset_link(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    RecordingSmtp.refused_recipients = {}
    monkeypatch.setattr(smtplib, "SMTP", RecordingSmtp)
    sender = SmtpPortalEmailSender(smtp_settings())
    reset_url = "https://portal.example.test/auth/password-reset/complete#token=reset-token"

    sender.send_password_reset(
        recipient="patient@example.test",
        reset_url=reset_url,
        expires_in_seconds=3600,
    )

    smtp = RecordingSmtp.instance
    assert smtp is not None
    assert smtp.message is not None
    assert smtp.message["Subject"] == "Reset your CARLOS Patient Portal password"
    assert reset_url in smtp.message.get_content()
    assert "60 minutes" in smtp.message.get_content()
    assert smtp.message["Auto-Submitted"] == "auto-generated"


def test_contact_change_notice_describes_immediate_portal_change(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    RecordingSmtp.refused_recipients = {}
    monkeypatch.setattr(smtplib, "SMTP", RecordingSmtp)
    sender = SmtpPortalEmailSender(smtp_settings())

    sender.send_contact_change_notice(recipient="patient@example.test")

    smtp = RecordingSmtp.instance
    assert smtp is not None
    assert smtp.message is not None
    assert smtp.message["Subject"] == ("Contact information changed for CARLOS Patient Portal")
    content = smtp.message.get_content()
    assert "in use now" in content
    assert "separately review the matching CARLOS chart" in content
    assert "before the new details are used" not in content


def test_smtp_sender_wraps_invalid_recipient_header() -> None:
    sender = SmtpPortalEmailSender(smtp_settings())

    with pytest.raises(PortalEmailDeliveryError):
        sender.send_code(
            recipient="patient@example.test\r\nBcc: attacker@example.test",
            code="123456",
            expires_in_seconds=600,
        )


def test_smtp_sender_is_only_built_for_complete_configuration() -> None:
    assert build_portal_email_sender(Settings(environment="development")) is None
    assert isinstance(build_portal_email_sender(smtp_settings()), SmtpPortalEmailSender)


def test_development_smtp_sender_uses_safe_default_from_address() -> None:
    sender = build_portal_email_sender(
        Settings(environment="development", smtp_host="mail.internal")
    )

    assert isinstance(sender, SmtpPortalEmailSender)
    assert sender.from_address == DEFAULT_DEVELOPMENT_SMTP_FROM_ADDRESS


@pytest.mark.parametrize(
    "settings_values",
    [
        {"smtp_from_address": "portal@example.test"},
        {
            "smtp_host": "mail.internal",
            "smtp_from_address": "portal@example.test",
            "smtp_username": "portal-user",
        },
        {
            "smtp_host": "mail.internal",
            "smtp_from_address": "portal@example.test",
            "smtp_password": "smtp-secret",
        },
    ],
)
def test_smtp_configuration_rejects_incomplete_settings(
    settings_values: dict[str, object],
) -> None:
    with pytest.raises(ValueError):
        Settings(environment="development", **settings_values)


def test_non_development_smtp_configuration_requires_from_address() -> None:
    with pytest.raises(ValueError, match="PATIENT_PORTAL_SMTP_FROM_ADDRESS"):
        Settings(
            environment="staging",
            smtp_host="mail.internal",
            internal_api_token="c" * 32,
            sms_webhook_url="https://sms.example.test/messages",
            sms_webhook_token="sms-webhook-token-value-32-characters",
            session_secret="s" * 32,
            identity_proof_secret="i" * 32,
            audit_hash_secret="a" * 32,
            unlock_secret_encryption_secret="u" * 32,
            internal_health_token="h" * 32,
        )


def test_non_development_smtp_requires_https_public_base_url() -> None:
    with pytest.raises(ValueError, match="PATIENT_PORTAL_PUBLIC_BASE_URL"):
        Settings(
            environment="staging",
            smtp_host="mail.internal",
            smtp_from_address="portal@example.test",
            smtp_starttls=True,
            internal_api_token="c" * 32,
            sms_webhook_url="https://sms.example.test/messages",
            sms_webhook_token="sms-webhook-token-value-32-characters",
            session_secret="s" * 32,
            identity_proof_secret="i" * 32,
            audit_hash_secret="a" * 32,
            unlock_secret_encryption_secret="u" * 32,
            internal_health_token="h" * 32,
        )
    with pytest.raises(ValueError, match="must use HTTPS"):
        Settings(
            environment="staging",
            public_base_url="http://portal.example.test",
            smtp_host="mail.internal",
            smtp_from_address="portal@example.test",
            smtp_starttls=True,
            internal_api_token="c" * 32,
            sms_webhook_url="https://sms.example.test/messages",
            sms_webhook_token="sms-webhook-token-value-32-characters",
            session_secret="s" * 32,
            identity_proof_secret="i" * 32,
            audit_hash_secret="a" * 32,
            unlock_secret_encryption_secret="u" * 32,
            internal_health_token="h" * 32,
        )

    settings = Settings(
        environment="staging",
        clinic_id="test-clinic",
        clinic_name="Test Clinic",
        public_base_url="https://portal.example.test/",
        smtp_host="mail.internal",
        smtp_from_address="portal@example.test",
        smtp_starttls=True,
        internal_api_token="c" * 32,
        sms_webhook_url="https://sms.example.test/messages",
        sms_webhook_token="sms-webhook-token-value-32-characters",
        session_secret="s" * 32,
        identity_proof_secret="i" * 32,
        audit_hash_secret="a" * 32,
        unlock_secret_encryption_secret="u" * 32,
        internal_health_token="h" * 32,
    )

    assert settings.public_base_url == "https://portal.example.test"


def test_non_development_smtp_requires_transport_encryption() -> None:
    with pytest.raises(ValueError, match="PATIENT_PORTAL_SMTP_STARTTLS"):
        Settings(
            environment="staging",
            public_base_url="https://portal.example.test",
            smtp_host="mail.internal",
            smtp_from_address="portal@example.test",
            internal_api_token="c" * 32,
            sms_webhook_url="https://sms.example.test/messages",
            sms_webhook_token="sms-webhook-token-value-32-characters",
            session_secret="s" * 32,
            identity_proof_secret="i" * 32,
            audit_hash_secret="a" * 32,
            unlock_secret_encryption_secret="u" * 32,
            internal_health_token="h" * 32,
        )
