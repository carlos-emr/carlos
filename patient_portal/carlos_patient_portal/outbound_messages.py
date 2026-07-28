from dataclasses import dataclass

DEFAULT_OUTBOUND_LOCALE = "en"


@dataclass(frozen=True)
class OutboundMessage:
    subject: str
    body: str


def mfa_email_message(
    *,
    service_name: str,
    clinic_name: str,
    code: str,
    expires_in_seconds: int,
    locale: str = DEFAULT_OUTBOUND_LOCALE,
) -> OutboundMessage:
    _require_supported_locale(locale)
    expires_in_minutes = max(1, expires_in_seconds // 60)
    return OutboundMessage(
        subject=f"Your {service_name} verification code",
        body=(
            f"Your verification code for {service_name} is:\n\n"
            f"{code}\n\n"
            f"This code expires in {expires_in_minutes} minutes. "
            "Do not share this code with anyone.\n\n"
            f"If you did not try to sign in, contact {clinic_name}."
        ),
    )


def password_reset_email_message(
    *,
    service_name: str,
    clinic_name: str,
    reset_url: str,
    expires_in_seconds: int,
    locale: str = DEFAULT_OUTBOUND_LOCALE,
) -> OutboundMessage:
    _require_supported_locale(locale)
    expires_in_minutes = max(1, expires_in_seconds // 60)
    return OutboundMessage(
        subject=f"Reset your {service_name} password",
        body=(
            f"A password reset was requested for your {service_name} account.\n\n"
            f"Open this link to choose a new password:\n{reset_url}\n\n"
            f"This link expires in {expires_in_minutes} minutes and can only be used once.\n\n"
            f"If you did not request this reset, contact {clinic_name}."
        ),
    )


def contact_change_email_message(
    *,
    service_name: str,
    clinic_name: str,
    locale: str = DEFAULT_OUTBOUND_LOCALE,
) -> OutboundMessage:
    _require_supported_locale(locale)
    return OutboundMessage(
        subject=f"Contact information changed for {service_name}",
        body=(
            f"The contact information for your {service_name} account changed. "
            "The new portal contact details are in use now. Clinic staff may separately "
            "review the matching CARLOS chart information.\n\n"
            f"If you did not make this change, contact {clinic_name} immediately."
        ),
    )


def mfa_sms_message(
    *,
    service_name: str,
    clinic_name: str,
    code: str,
    expires_in_seconds: int,
    locale: str = DEFAULT_OUTBOUND_LOCALE,
) -> str:
    _require_supported_locale(locale)
    return (
        f"Your {service_name} verification code is {code}. "
        f"It expires in {max(1, expires_in_seconds // 60)} minutes. "
        f"Do not share it. Contact {clinic_name} if you did not request it."
    )


def _require_supported_locale(locale: str) -> None:
    if locale != DEFAULT_OUTBOUND_LOCALE:
        raise ValueError("outbound message locale is not supported")
