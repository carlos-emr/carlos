import json

import pytest

from carlos_patient_portal.config import Settings
from carlos_patient_portal.sms_delivery import (
    PortalSmsDeliveryError,
    WebhookPortalSmsSender,
)

SMS_TOKEN = "sms-webhook-token-value-32-characters"


class FakeResponse:
    status = 202

    def read(self) -> bytes:
        return b""


def sms_settings() -> Settings:
    return Settings(
        environment="development",
        sms_webhook_url="https://sms.example.test/messages",
        sms_webhook_token=SMS_TOKEN,
    )


def test_sms_webhook_sends_bearer_authenticated_json(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, object] = {}

    class FakeConnection:
        def __init__(self, host, port, **kwargs):
            captured["host"] = host
            captured["port"] = port
            captured["kwargs"] = kwargs

        def request(self, method, path, *, body, headers):
            captured["method"] = method
            captured["path"] = path
            captured["body"] = body
            captured["headers"] = headers

        def getresponse(self):
            return FakeResponse()

        def close(self):
            captured["closed"] = True

    monkeypatch.setattr("carlos_patient_portal.sms_delivery.HTTPSConnection", FakeConnection)
    sender = WebhookPortalSmsSender(sms_settings())

    sender.send_code(
        recipient="+15550105555",
        code="123456",
        expires_in_seconds=600,
    )

    payload = json.loads(captured["body"])
    assert captured["host"] == "sms.example.test"
    assert captured["path"] == "/messages"
    assert captured["headers"]["Authorization"] == f"Bearer {SMS_TOKEN}"
    assert payload["to"] == "+15550105555"
    assert "123456" in payload["message"]
    assert captured["kwargs"]["timeout"] == 10
    assert captured["closed"] is True


def test_sms_webhook_failure_does_not_expose_recipient(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FailingConnection:
        def __init__(self, *_: object, **__: object):
            pass

        def request(self, *_: object, **__: object):
            raise OSError("gateway rejected +15550105555")

        def close(self):
            pass

    monkeypatch.setattr("carlos_patient_portal.sms_delivery.HTTPSConnection", FailingConnection)
    sender = WebhookPortalSmsSender(sms_settings())

    with pytest.raises(PortalSmsDeliveryError) as raised_error:
        sender.send_code(
            recipient="+15550105555",
            code="123456",
            expires_in_seconds=600,
        )

    assert "+15550105555" not in str(raised_error.value)
    assert raised_error.value.__cause__ is None
    assert raised_error.value.__context__ is not None
    assert raised_error.value.__suppress_context__
