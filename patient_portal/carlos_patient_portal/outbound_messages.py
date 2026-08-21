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


def email_change_confirmation_email_message(
    *,
    service_name: str,
    clinic_name: str,
    confirmation_url: str,
    expires_in_seconds: int,
    locale: str = DEFAULT_OUTBOUND_LOCALE,
) -> OutboundMessage:
    """Sent to the proposed address. Carries no patient or clinical detail beyond the clinic name.

    Until this link is opened the change has not happened, so the wording must not imply the
    account already uses this address.
    """
    _require_supported_locale(locale)
    expires_in_hours = max(1, expires_in_seconds // 3600)
    return OutboundMessage(
        subject=f"Confirm your new {service_name} email address",
        body=(
            f"This address was given as the new contact email for a {service_name} account.\n\n"
            f"Open this link to confirm it:\n{confirmation_url}\n\n"
            f"This link expires in {expires_in_hours} hours and can only be used once. "
            "Until it is used, the account keeps its previous email address.\n\n"
            f"If you were not expecting this, ignore this message or contact {clinic_name}."
        ),
    )


def email_change_requested_email_message(
    *,
    service_name: str,
    clinic_name: str,
    locale: str = DEFAULT_OUTBOUND_LOCALE,
) -> OutboundMessage:
    """Sent to the *current* address. This is the out-of-band alarm for an unwanted change.

    It deliberately does not include the proposed address: this mailbox may already be in someone
    else's hands, and the notice should not hand them confirmation of where the change points.
    """
    _require_supported_locale(locale)
    return OutboundMessage(
        subject=f"A contact change was requested for {service_name}",
        body=(
            f"Someone asked to change the contact details on your {service_name} account.\n\n"
            "Nothing has changed yet. The change only takes effect once the new email address is "
            "confirmed, and this address continues to receive your verification codes and "
            "password reset links until then.\n\n"
            f"If you did not request this, contact {clinic_name} immediately."
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
