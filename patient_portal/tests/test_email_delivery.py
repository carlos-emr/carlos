import smtplib
from email.message import EmailMessage

import pytest

from carlos_patient_portal.config import (
    DEFAULT_DEVELOPMENT_SMTP_FROM_ADDRESS,
    Settings,
)
from carlos_patient_portal.email_delivery import (
    MfaEmailDeliveryError,
    SmtpMfaEmailSender,
    build_mfa_email_sender,
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
    sender = SmtpMfaEmailSender(
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
    RecordingSmtp.refused_recipients = {
        "patient@example.test": (550, b"recipient rejected")
    }
    monkeypatch.setattr(smtplib, "SMTP", RecordingSmtp)
    sender = SmtpMfaEmailSender(smtp_settings())

    with pytest.raises(MfaEmailDeliveryError) as exc_info:
        sender.send_code(
            recipient="patient@example.test",
            code="654321",
            expires_in_seconds=600,
        )

    assert "654321" not in str(exc_info.value)


def test_smtp_sender_is_only_built_for_complete_configuration() -> None:
    assert build_mfa_email_sender(Settings(environment="development")) is None
    assert isinstance(build_mfa_email_sender(smtp_settings()), SmtpMfaEmailSender)


def test_development_smtp_sender_uses_safe_default_from_address() -> None:
    sender = build_mfa_email_sender(
        Settings(environment="development", smtp_host="mail.internal")
    )

    assert isinstance(sender, SmtpMfaEmailSender)
    assert sender.from_address == DEFAULT_DEVELOPMENT_SMTP_FROM_ADDRESS


@pytest.mark.parametrize(
    "settings_values",
    [
        {"smtp_from_address": "portal@example.test"},
        {"smtp_host": "mail.internal", "smtp_from_address": "portal@example.test",
         "smtp_username": "portal-user"},
        {"smtp_host": "mail.internal", "smtp_from_address": "portal@example.test",
         "smtp_password": "smtp-secret"},
    ],
)
def test_smtp_configuration_rejects_incomplete_settings(
    settings_values: dict[str, object],
) -> None:
    with pytest.raises(ValueError):
        Settings(environment="development", **settings_values)


def test_non_development_smtp_configuration_requires_from_address() -> None:
    with pytest.raises(ValueError, match="PATIENT_PORTAL_SMTP_FROM_ADDRESS"):
        Settings(environment="staging", smtp_host="mail.internal")
