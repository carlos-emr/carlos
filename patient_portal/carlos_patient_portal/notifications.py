"""Outbound message delivery for portal authentication and account workflows.

Every function here decides *whether* a message can be sent and turns a delivery failure into a
`PortalEmailDeliveryError`/`PortalSmsDeliveryError` the caller can act on. Composing the message
text belongs to `outbound_messages.py`; transport belongs to the sender adapters.

Extracted from `main.py` alongside `web_support.py` so route modules can send without importing
the application module.
"""

import logging
from urllib.parse import quote

from fastapi import Request
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from carlos_patient_portal.auth import (
    MfaChallengeDelivery,
    PasswordResetRequestResult,
    PasswordResetTokenInvalidError,
    record_mfa_delivery_outcome,
    record_password_reset_delivery_outcome,
)
from carlos_patient_portal.config import Settings
from carlos_patient_portal.email_delivery import PortalEmailDeliveryError
from carlos_patient_portal.models import (
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    MFA_DELIVERY_METHOD_EMAIL,
)
from carlos_patient_portal.runtime import PortalRuntime
from carlos_patient_portal.sms_delivery import PortalSmsDeliveryError

logger = logging.getLogger(__name__)

def send_mfa_challenge(runtime: PortalRuntime, delivery: MfaChallengeDelivery) -> None:
    if delivery.delivery_method == MFA_DELIVERY_METHOD_EMAIL:
        if runtime.email_sender is None:
            if runtime.settings.is_development:
                return
            raise PortalEmailDeliveryError("MFA email delivery is not configured")
        runtime.email_sender.send_code(
            recipient=delivery.destination,
            code=delivery.code,
            expires_in_seconds=runtime.settings.mfa_code_ttl_seconds,
        )
        return

    if runtime.sms_sender is None:
        if runtime.settings.is_development:
            return
        raise PortalSmsDeliveryError("MFA SMS delivery is not configured")
    runtime.sms_sender.send_code(
        recipient=delivery.destination,
        code=delivery.code,
        expires_in_seconds=runtime.settings.mfa_code_ttl_seconds,
    )


def build_password_reset_url(
    request: Request,
    *,
    settings: Settings,
    reset_token: str,
) -> str:
    if settings.public_base_url is not None:
        base_url = settings.public_base_url.rstrip("/")
    elif settings.is_development:
        base_url = str(request.base_url).rstrip("/")
    else:
        raise PortalEmailDeliveryError("password reset email delivery is not configured")
    encoded_token = quote(reset_token, safe="")
    return base_url + "/auth/password-reset/complete#token=" + encoded_token


def send_password_reset_email(
    runtime: PortalRuntime,
    *,
    recipient: str,
    reset_url: str,
) -> None:
    if runtime.email_sender is None:
        if runtime.settings.is_development:
            return
        raise PortalEmailDeliveryError("password reset email delivery is not configured")
    runtime.email_sender.send_password_reset(
        recipient=recipient,
        reset_url=reset_url,
        expires_in_seconds=runtime.settings.password_reset_token_ttl_seconds,
    )


def deliver_password_reset(
    runtime: PortalRuntime,
    *,
    result: PasswordResetRequestResult,
    reset_url: str,
) -> None:
    outcome = AUDIT_OUTCOME_SUCCESS
    try:
        send_password_reset_email(
            runtime,
            recipient=result.recipient or "",
            reset_url=reset_url,
        )
    except PortalEmailDeliveryError as exc:
        outcome = AUDIT_OUTCOME_FAILURE
        runtime.operational_metrics.record_failure("password_reset_delivery")
        # Exception details can contain SMTP recipient data; log only the sanitized type. The
        # message is a fixed literal and the sole interpolation is the exception class name, so
        # the credential-disclosure rule below has nothing to disclose.
        # nosemgrep: python-logger-credential-disclosure -- logs only type(exc).__name__
        logger.error("Password reset email delivery failed: %s", type(exc).__name__)  # NOSONAR
    try:
        with runtime.session_factory() as session:
            with session.begin():
                record_password_reset_delivery_outcome(
                    session,
                    result=result,
                    outcome=outcome,
                )
    except (PasswordResetTokenInvalidError, SQLAlchemyError) as exc:
        # Exception details can contain database values; log only the sanitized type. The message
        # is a fixed literal and the sole interpolation is the exception class name, so the
        # credential-disclosure rule below has nothing to disclose.
        # nosemgrep: python-logger-credential-disclosure -- logs only type(exc).__name__
        logger.error(  # NOSONAR
            "Password reset delivery outcome persistence failed: %s",
            type(exc).__name__,
        )


def build_email_change_confirmation_url(
    request: Request,
    *,
    settings: Settings,
    confirmation_token: str,
) -> str:
    """Build the confirmation link, carrying the token in the fragment like a reset link.

    A fragment is not sent in the HTTP request, so the token cannot land in an access log or a
    proxy trace on its way to the portal.
    """
    if settings.public_base_url is not None:
        base_url = settings.public_base_url.rstrip("/")
    elif settings.is_development:
        base_url = str(request.base_url).rstrip("/")
    else:
        raise PortalEmailDeliveryError("email-change confirmation delivery is not configured")
    return base_url + "/auth/email-change/confirm#token=" + quote(confirmation_token, safe="")


def send_email_change_confirmation(
    runtime: PortalRuntime,
    *,
    recipient: str,
    confirmation_url: str,
) -> None:
    if runtime.email_sender is None:
        if runtime.settings.is_development:
            return
        raise PortalEmailDeliveryError("email-change confirmation delivery is not configured")
    # PortalEmailSender is a structural protocol, so a configured sender can be missing this
    # method. Unlike the advisory notices, this one is load-bearing — without it the patient can
    # never confirm — so a missing method fails the request in every environment rather than
    # leaving a pending change nothing can complete.
    sender = getattr(runtime.email_sender, "send_email_change_confirmation", None)
    if sender is None:
        raise PortalEmailDeliveryError("email-change confirmation delivery is not configured")
    sender(
        recipient=recipient,
        confirmation_url=confirmation_url,
        expires_in_seconds=runtime.settings.email_change_token_ttl_seconds,
    )


def send_email_change_requested_notice(runtime: PortalRuntime, *, recipient: str) -> None:
    if runtime.email_sender is None:
        if runtime.settings.is_development:
            return
        raise PortalEmailDeliveryError("email-change notice delivery is not configured")
    # Injected test doubles and older senders may predate this method; a missing notice must not
    # take down the change itself, and the confirmation link is the load-bearing message.
    sender = getattr(runtime.email_sender, "send_email_change_requested_notice", None)
    if sender is None:
        if runtime.settings.is_development:
            return
        raise PortalEmailDeliveryError("email-change notice delivery is not configured")
    sender(recipient=recipient)


def send_contact_change_notice(runtime: PortalRuntime, *, recipient: str) -> None:
    if runtime.email_sender is None:
        if runtime.settings.is_development:
            return
        raise PortalEmailDeliveryError("contact-change email delivery is not configured")
    sender = getattr(runtime.email_sender, "send_contact_change_notice", None)
    if sender is None:
        if runtime.settings.is_development:
            return
        raise PortalEmailDeliveryError("contact-change email delivery is not configured")
    sender(recipient=recipient)


def record_mfa_delivery_and_commit(
    session: Session,
    *,
    delivery: MfaChallengeDelivery,
    outcome: str,
) -> None:
    record_mfa_delivery_outcome(session, delivery=delivery, outcome=outcome)
    session.commit()
