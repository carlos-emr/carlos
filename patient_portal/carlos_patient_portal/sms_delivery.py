import json
import ssl
from http.client import HTTPConnection, HTTPException, HTTPSConnection
from typing import Protocol
from urllib.parse import urlsplit

from carlos_patient_portal.config import Settings


class PortalSmsDeliveryError(Exception):
    """Raised when a portal authentication SMS cannot be delivered."""


class PortalSmsSender(Protocol):
    def send_code(
        self,
        *,
        recipient: str,
        code: str,
        expires_in_seconds: int,
    ) -> None:
        raise NotImplementedError


class WebhookPortalSmsSender:
    """Provider-neutral JSON webhook used by the deployment's SMS gateway."""

    def __init__(self, settings: Settings) -> None:
        if settings.sms_webhook_url is None or settings.sms_webhook_token is None:
            raise ValueError("SMS webhook URL and token are required")
        self.url = settings.sms_webhook_url
        self.token = settings.sms_webhook_token.get_secret_value()
        self.timeout_seconds = settings.sms_timeout_seconds
        self.sender_id = settings.sms_sender_id
        self.service_name = settings.service_name
        self.clinic_name = settings.clinic_name
        parsed_url = urlsplit(self.url)
        self.connection_type = (
            HTTPSConnection if parsed_url.scheme == "https" else HTTPConnection
        )
        self.connection_host = parsed_url.hostname
        self.connection_port = parsed_url.port
        self.request_path = parsed_url.path or "/"

    def send_code(
        self,
        *,
        recipient: str,
        code: str,
        expires_in_seconds: int,
    ) -> None:
        body = json.dumps(
            {
                "to": recipient,
                "sender_id": self.sender_id,
                "message": (
                    f"Your {self.service_name} verification code is {code}. "
                    f"It expires in {max(1, expires_in_seconds // 60)} minutes. "
                    f"Do not share it. Contact {self.clinic_name} if you did not request it."
                ),
            }
        ).encode()
        connection = self.connection_type(
            self.connection_host,
            self.connection_port,
            timeout=self.timeout_seconds,
            **(
                {"context": ssl.create_default_context()}
                if self.connection_type is HTTPSConnection
                else {}
            ),
        )
        try:
            connection.request(
                "POST",
                self.request_path,
                body=body,
                headers={
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json",
                "User-Agent": "carlos-patient-portal/0.1",
                },
            )
            response = connection.getresponse()
            response.read()
            if not 200 <= response.status < 300:
                raise PortalSmsDeliveryError("portal SMS delivery failed")
        except PortalSmsDeliveryError:
            raise
        except (HTTPException, OSError, TimeoutError, ValueError):
            raise PortalSmsDeliveryError("portal SMS delivery failed") from None
        finally:
            connection.close()


def build_portal_sms_sender(settings: Settings) -> PortalSmsSender | None:
    if settings.sms_webhook_url is None:
        return None
    return WebhookPortalSmsSender(settings)
