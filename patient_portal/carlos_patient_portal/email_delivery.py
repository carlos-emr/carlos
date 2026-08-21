# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project

import smtplib
import ssl
from email.message import EmailMessage
from typing import Protocol

from carlos_patient_portal.config import Settings
from carlos_patient_portal.outbound_messages import (
    OutboundMessage,
    contact_change_email_message,
    email_change_confirmation_email_message,
    email_change_requested_email_message,
    mfa_email_message,
    password_reset_email_message,
)

# One deliberately generic message for every adapter failure path. Detailed SMTP/header failure
# classification belongs in privacy-safe metrics/logs, never in text that can reach a patient.
PORTAL_EMAIL_DELIVERY_ERROR_MESSAGE = "portal email delivery failed"


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
        message_id: str | None = None,
    ) -> None:
        raise NotImplementedError

    def send_contact_change_notice(
        self, *, recipient: str, message_id: str | None = None
    ) -> None:
        raise NotImplementedError

    def send_email_change_confirmation(
        self,
        *,
        recipient: str,
        confirmation_url: str,
        expires_in_seconds: int,
    ) -> None:
        raise NotImplementedError

    def send_email_change_requested_notice(self, *, recipient: str) -> None:
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
        self._send_message(
            self._build_message(
                recipient,
                mfa_email_message(
                    service_name=self.service_name,
                    clinic_name=self.clinic_name,
                    code=code,
                    expires_in_seconds=expires_in_seconds,
                ),
            )
        )

    def send_password_reset(
        self,
        *,
        recipient: str,
        reset_url: str,
        expires_in_seconds: int,
        message_id: str | None = None,
    ) -> None:
        self._send_message(
            self._build_message(
                recipient,
                password_reset_email_message(
                    service_name=self.service_name,
                    clinic_name=self.clinic_name,
                    reset_url=reset_url,
                    expires_in_seconds=expires_in_seconds,
                ),
                message_id=message_id,
            )
        )

    def send_contact_change_notice(
        self, *, recipient: str, message_id: str | None = None
    ) -> None:
        self._send_message(
            self._build_message(
                recipient,
                contact_change_email_message(
                    service_name=self.service_name,
                    clinic_name=self.clinic_name,
                ),
                message_id=message_id,
            )
        )

    def send_email_change_confirmation(
        self,
        *,
        recipient: str,
        confirmation_url: str,
        expires_in_seconds: int,
    ) -> None:
        self._send_message(
            self._build_message(
                recipient,
                email_change_confirmation_email_message(
                    service_name=self.service_name,
                    clinic_name=self.clinic_name,
                    confirmation_url=confirmation_url,
                    expires_in_seconds=expires_in_seconds,
                ),
            )
        )

    def send_email_change_requested_notice(self, *, recipient: str) -> None:
        self._send_message(
            self._build_message(
                recipient,
                email_change_requested_email_message(
                    service_name=self.service_name,
                    clinic_name=self.clinic_name,
                ),
            )
        )

    def _build_message(
        self,
        recipient: str,
        content: OutboundMessage,
        *,
        message_id: str | None = None,
    ) -> EmailMessage:
        try:
            message = EmailMessage()
            message["From"] = self.from_address
            message["To"] = recipient
            message["Subject"] = content.subject
            message["Auto-Submitted"] = "auto-generated"
            if message_id is not None:
                message["Message-ID"] = message_id
            message.set_content(content.body)
            return message
        except (TypeError, ValueError):
            raise PortalEmailDeliveryError(PORTAL_EMAIL_DELIVERY_ERROR_MESSAGE) from None

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
                    raise PortalEmailDeliveryError(PORTAL_EMAIL_DELIVERY_ERROR_MESSAGE)
        except PortalEmailDeliveryError:
            raise
        except (OSError, smtplib.SMTPException, ValueError):
            raise PortalEmailDeliveryError(PORTAL_EMAIL_DELIVERY_ERROR_MESSAGE) from None


def build_portal_email_sender(settings: Settings) -> PortalEmailSender | None:
    if settings.smtp_host is None:
        return None
    return SmtpPortalEmailSender(settings)
