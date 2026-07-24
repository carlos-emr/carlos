import smtplib
import ssl
from email.message import EmailMessage
from typing import Protocol

from carlos_patient_portal.config import Settings


class PortalEmailDeliveryError(Exception):
    """Raised when a portal authentication email cannot be delivered."""


class PortalEmailSender(Protocol):
    def send_code(
        self,
        *,
        recipient: str,
        code: str,
        expires_in_seconds: int,
    ) -> None:
        raise NotImplementedError

    def send_password_reset(
        self,
        *,
        recipient: str,
        reset_url: str,
        expires_in_seconds: int,
    ) -> None:
        raise NotImplementedError


class SmtpPortalEmailSender:
    def __init__(self, settings: Settings) -> None:
        if settings.smtp_host is None or settings.resolved_smtp_from_address is None:
            raise ValueError("SMTP host and from address are required")
        self.host = settings.smtp_host
        self.port = settings.smtp_port
        self.from_address = settings.resolved_smtp_from_address
        self.starttls = settings.smtp_starttls
        self.username = settings.smtp_username
        self.password = (
            settings.smtp_password.get_secret_value()
            if settings.smtp_password is not None
            else None
        )
        self.timeout_seconds = settings.smtp_timeout_seconds
        self.service_name = settings.service_name
        self.clinic_name = settings.clinic_name

    def send_code(
        self,
        *,
        recipient: str,
        code: str,
        expires_in_seconds: int,
    ) -> None:
        message = EmailMessage()
        message["From"] = self.from_address
        message["To"] = recipient
        message["Subject"] = f"Your {self.service_name} verification code"
        message["Auto-Submitted"] = "auto-generated"
        expires_in_minutes = max(1, expires_in_seconds // 60)
        message.set_content(
            f"Your verification code for {self.service_name} is:\n\n"
            f"{code}\n\n"
            f"This code expires in {expires_in_minutes} minutes. "
            "Do not share this code with anyone.\n\n"
            f"If you did not try to sign in, contact {self.clinic_name}."
        )

        self._send_message(message)

    def send_password_reset(
        self,
        *,
        recipient: str,
        reset_url: str,
        expires_in_seconds: int,
    ) -> None:
        message = EmailMessage()
        message["From"] = self.from_address
        message["To"] = recipient
        message["Subject"] = f"Reset your {self.service_name} password"
        message["Auto-Submitted"] = "auto-generated"
        expires_in_minutes = max(1, expires_in_seconds // 60)
        message.set_content(
            f"A password reset was requested for your {self.service_name} account.\n\n"
            f"Open this link to choose a new password:\n{reset_url}\n\n"
            f"This link expires in {expires_in_minutes} minutes and can only be used once.\n\n"
            f"If you did not request this reset, contact {self.clinic_name}."
        )
        self._send_message(message)

    def _send_message(self, message: EmailMessage) -> None:
        try:
            with smtplib.SMTP(
                host=self.host,
                port=self.port,
                timeout=self.timeout_seconds,
            ) as smtp:
                if self.starttls:
                    # create_default_context enables CA validation and hostname checking.
                    smtp.starttls(context=ssl.create_default_context())  # NOSONAR
                if self.username is not None and self.password is not None:
                    smtp.login(self.username, self.password)
                refused_recipients = smtp.send_message(message)
                if refused_recipients:
                    raise PortalEmailDeliveryError("portal email delivery failed")
        except PortalEmailDeliveryError:
            raise
        except (OSError, smtplib.SMTPException, ValueError):
            raise PortalEmailDeliveryError("portal email delivery failed") from None


def build_portal_email_sender(settings: Settings) -> PortalEmailSender | None:
    if settings.smtp_host is None:
        return None
    return SmtpPortalEmailSender(settings)
