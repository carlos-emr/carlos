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

"""Locale resolution and the portal's user-facing text.

Translations are not written yet. Rather than advertise four languages that do nothing, the
machinery is real and every locale resolves through `TEXT_CATALOG` with a per-key fallback to
English: selecting French switches the locale, persists it, and renders the English string for any
key French has not defined. Adding a translation is then only a matter of adding keys to
`TEXT_CATALOG["fr"]` — no route, template, or context change is needed, and a partial translation
renders rather than raising KeyError.
"""

import math
from dataclasses import dataclass
from datetime import UTC, datetime
from zoneinfo import ZoneInfo

DEFAULT_LOCALE = "en"
# Display preference only — it selects strings and a date format, and carries no authorization or
# identity meaning, which is why the switch route can accept a GET without a CSRF token.
LOCALE_COOKIE_NAME = "portal_locale"
LOCALE_COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60
DEFAULT_DATETIME_FORMAT = "%Y-%m-%d %H:%M %Z"
DATETIME_FORMATS = {
    DEFAULT_LOCALE: DEFAULT_DATETIME_FORMAT,
    # Placeholders alongside the English catalog: ISO-style ordering is unambiguous in every one of
    # these locales, so it is a safe default until a translator sets the real convention.
    "fr": DEFAULT_DATETIME_FORMAT,
    "es": DEFAULT_DATETIME_FORMAT,
    "pl": DEFAULT_DATETIME_FORMAT,
    "pt-BR": DEFAULT_DATETIME_FORMAT,
}
SIGN_IN_LABEL = "Sign in"


@dataclass(frozen=True)
class LocaleOption:
    code: str
    short_label: str
    label: str


SUPPORTED_LOCALES: tuple[LocaleOption, ...] = (
    LocaleOption(code="en", short_label="EN", label="English"),
    LocaleOption(code="fr", short_label="FR", label="French"),
    LocaleOption(code="es", short_label="ES", label="Spanish"),
    LocaleOption(code="pl", short_label="PL", label="Polish"),
    LocaleOption(code="pt-BR", short_label="PT-BR", label="Portuguese (Brazil)"),
)

TEXT_CATALOG: dict[str, dict[str, str]] = {
    DEFAULT_LOCALE: {
        "account": "Account",
        "account_change_error": "Account change could not be completed.",
        "account_help": "Account help",
        "account_status_contact_notice_failed": (
            "Portal contact updated, but the confirmation email could not be sent. "
            "Contact the clinic if you did not expect this change."
        ),
        "account_status_contact_updated": (
            "Portal contact updated. Staff will review the matching CARLOS demographics."
        ),
        "account_status_contact_confirmation_required": (
            "Confirm the new email address and enter the code sent to the new phone number. "
            "Your current contact details remain active until both are confirmed."
        ),
        "account_status_email_confirmation_required": (
            "Check the new email address for a confirmation link. Your current address keeps "
            "receiving verification codes and password reset links until you use it."
        ),
        "account_status_email_confirmation_notice_failed": (
            "Confirmation could not be emailed to the new address. Your contact details were not "
            "changed. Try again, or contact the clinic."
        ),
        "account_status_phone_confirmation_required": (
            "Enter the confirmation code sent to the new phone number. Your current phone number "
            "remains active until it is confirmed."
        ),
        "account_status_phone_confirmation_invalid": (
            "The phone confirmation code is invalid or expired. Request another code and try "
            "again."
        ),
        "account_status_phone_confirmation_notice_failed": (
            "A confirmation code could not be sent to the new phone number. Your contact details "
            "were not changed. Try again, or contact the clinic."
        ),
        "account_status_phone_confirmation_rate_limited": (
            "A phone confirmation code was sent recently. Wait before requesting another."
        ),
        "account_status_mfa_updated": "MFA settings updated.",
        "account_status_no_change": "No account changes.",
        "account_status_password_updated": "Password updated.",
        "all_providers": "All providers",
        "activate_account": "Activate account",
        "activation_details": (
            "Enter the invitation details supplied by your clinic and create your portal sign-in."
        ),
        "activation_error": "The activation details could not be verified.",
        "activation_heading": "Activate your account",
        "activation_rate_limited": (
            "Too many activation attempts were made. Wait before trying again."
        ),
        "activation_success": "Your portal account is ready.",
        "activation_success_heading": "Account activated",
        "back_to_sign_in": "Back to sign in",
        "email_change_confirm": "Confirm email address",
        "email_change_complete_error": (
            "This confirmation link is no longer valid. Request the change again from your "
            "account page."
        ),
        "email_change_complete_heading": "Confirm your new email address",
        "email_change_complete_intro": (
            "Confirming updates the email address used for sign-in verification codes and "
            "password reset links."
        ),
        "email_change_success": (
            "Your email address is updated. Clinic staff will review the matching CARLOS chart "
            "details."
        ),
        "email_change_success_heading": "Email address confirmed",
        "email_change_phone_confirmation_pending": (
            "Your new email address is confirmed. Sign in and enter the code sent to the new "
            "phone number to finish the contact change."
        ),
        "change_password": "Change password",
        "clinic": "Clinic",
        "clinic_help": "Clinic help",
        "clinic_help_message": (
            "Contact the clinic if you cannot access your account or verification method."
        ),
        "contact_info": "Contact info",
        "contact_the_clinic": "Contact the clinic",
        "copy": "Copy",
        "copy_failed": "Select and copy manually",
        "copied": "Copied",
        "current_password": "Current password",
        "dashboard": "Dashboard",
        "dashboard_account_description": "Manage your password and contact information.",
        "dashboard_documents_description": "Documents may be available in a future release.",
        "dashboard_email_passwords_description": (
            "Retrieve passwords for encrypted messages from your clinic."
        ),
        "dashboard_greeting": "Patient portal",
        "dashboard_help_description": "Find clinic and account support information.",
        "dashboard_messages_description": "Secure messaging may be available in a future release.",
        "date_from": "From date",
        "date_format_error": "Enter valid from and to dates.",
        "filter_error": "Enter valid filters.",
        "date_range_error": "The from date must not be later than the to date.",
        "date_of_birth": "Date of birth",
        "date_to": "To date",
        "development_mfa_code": (
            "Development MFA code (same code as sent by email, to make testing quicker, "
            "will be removed later):"
        ),
        "development_reset_link": (
            "Development password reset link (also sent by email when email is configured):"
        ),
        "documents": "Documents",
        "email": "Email",
        "email_codes_rate": "Email codes can be resent once per minute.",
        "email_password": "Email password",
        "email_password_pages": "Email password pages",
        "email_passwords": "Email passwords",
        "email_passwords_region": "Email passwords",
        "filters": "Filters",
        "forgot_username_password": "Forgot username or password?",
        "forgot_username_help": "If you do not know your username, contact the clinic.",
        "future": "Future",
        "health_card_number": "Health card number (HCN/HIN)",
        "help": "Help",
        "hide_password": "Hide password",
        "incorrect_mfa_code": "The code was not accepted. Try again or request a new code.",
        "incorrect_username_or_password": "Incorrect Username or Password",
        "invite_code": "Invitation code",
        "language_aria_label": "Language",
        "logo_alt": "CARLOS",
        "logout": "Logout",
        "messages": "Messages",
        "mfa": "MFA",
        "mfa_code": "Code",
        "mfa_code_sent": "Code sent to {destination} by {method}.",
        "mfa_delivery_unavailable": "That delivery method is unavailable.",
        "mfa_email": "Email",
        "mfa_heading": "Verification code",
        "mfa_help_message": (
            "Contact {clinic_name} if you cannot receive or use your verification code."
        ),
        "mfa_new_code_sent": "A new code was sent by {method}.",
        "mfa_rate_limited": "A code was sent recently. Try again in {seconds} seconds.",
        "mfa_resend": "Resend code",
        "mfa_sign_in_again": "Sign in again to request a new verification code.",
        "mfa_send_by": "Send code by",
        "mfa_settings": "MFA settings",
        "mfa_sms": "SMS",
        "mfa_sms_rate": "SMS codes can be resent once every five minutes.",
        "mfa_verify": "Verify",
        "mfa_verification_failed": "MFA could not be verified.",
        "modal_close": "OK",
        "module_navigation": "Portal modules",
        "mfa_method": "Method",
        "new_password": "New password",
        "new_password_confirmation": "Confirm new password",
        "next": "Next",
        "no_account_changes": "No account changes.",
        "no_email_passwords": "No email passwords",
        "no_matching_email_passwords": "No matching email passwords",
        "of": "of",
        "open_reset_page": "Open reset page",
        "page": "Page",
        "password_label": "Password",
        "password_hidden": "Hidden",  # NOSONAR - translation label, not a credential
        "password_for": "Password for {subject}",
        "password_confirmation": "Confirm password",
        "password_invalid": "The new password does not meet the password requirements.",
        "password_mismatch": "The password confirmation does not match.",
        "password_placeholder": "password",
        "password_requirements": (
            "Use at least 12 characters with uppercase and lowercase letters, a number, "
            "and a symbol."
        ),
        "password_reset_complete_error": "The password reset link is invalid or has expired.",
        "password_reset_complete_heading": "Choose a new password",
        "password_reset_forced": "You must reset your password before signing in.",
        "password_reset_link_sent": (
            "If the account details match, a password reset link has been sent by email."
        ),
        "password_reset_request_details": (
            "Enter your portal username and the email address registered with your clinic."
        ),
        "password_reset_request_heading": "Reset your password",
        "password_reset_send": "Send reset link",  # NOSONAR - translation label
        "password_reset_success": "Your password has been reset. Sign in with the new password.",
        "password_reset_success_heading": "Password reset",
        "password_reset_update": "Update password",
        "password_updated": "Password updated",
        "patient_dashboard": "Patient dashboard",
        "phone": "Phone",
        "phone_required_for_sms": "Required when SMS is selected.",
        "phone_confirmation_code": "Phone confirmation code",
        "confirm_phone": "Confirm phone number",
        "resend_phone_confirmation": "Send another code",
        "previous": "Previous",
        "provider": "Provider",
        "provider_options_truncated": (
            "Only the most recent providers are listed. Use the search box to narrow your results."
        ),
        "reveal": "Reveal",
        "reveal_failed": "Password could not be revealed. Try again.",
        "revealing": "Revealing...",
        "reset_filters": "Clear filters",
        "search": "Search",
        "send_code_by": "Send code by",
        "sent": "Sent",
        "session_locked_details": (
            "Clinic staff must unlock this account before another sign-in or password reset. "
            "Contact the clinic for help."
        ),
        "service_busy_heading": "Too many requests",
        "service_busy_details": (
            "The portal received too many requests from this connection. Wait a moment and try "
            "again."
        ),
        "service_maintenance_heading": "Portal unavailable",
        "service_maintenance_details": (
            "The portal is temporarily unavailable for maintenance. Try again shortly, or contact "
            "the clinic if you need help now."
        ),
        "session_locked_heading": "Account locked",
        "sign_in_aria_label": SIGN_IN_LABEL,
        "sign_in_button": SIGN_IN_LABEL,
        "sign_in_heading": SIGN_IN_LABEL,
        "show_password": "Show password",
        "subject": "Subject",
        "unavailable": "Unavailable",
        "update_contact": "Update contact",
        "update_mfa": "Update MFA",
        "update_password": "Update password",
        "username": "Username",
        "username_label": "User Name",
        "username_placeholder": "username",
        "username_unavailable": "That username is unavailable.",
        "verification_delivery_failed": "Verification code could not be sent. Please try again.",
        "view_module": "Open",
    }
}

# Every supported locale gets an entry so `portal_text` never falls back wholesale to English and
# a translator can start filling one in without touching anything else. Empty today: the per-key
# merge in `portal_text` supplies the English string for anything a locale has not defined, which
# is what makes a half-finished translation render instead of raising KeyError.
for _locale in SUPPORTED_LOCALES:
    TEXT_CATALOG.setdefault(_locale.code, {})

def normalize_locale(value: str | None) -> str | None:
    """Match a requested locale to a supported one, case- and separator-insensitively.

    Accepts the shapes a browser actually sends: `PT-br`, `pt_BR`, and a bare `pt` all resolve to
    `pt-BR`. Returns None when nothing matches, so callers can distinguish "not supported" from
    "supported and happens to be the default".
    """
    if not value:
        return None
    candidate = value.strip().replace("_", "-").casefold()
    if not candidate:
        return None
    for locale in SUPPORTED_LOCALES:
        if locale.code.casefold() == candidate:
            return locale.code
    # A bare language subtag matches the first supported locale in that language, so `pt` finds
    # `pt-BR` without the caller having to know the region.
    language = candidate.split("-", 1)[0]
    for locale in SUPPORTED_LOCALES:
        if locale.code.casefold().split("-", 1)[0] == language:
            return locale.code
    return None


def _accept_language_weight(parameters: str) -> float:
    weight = 1.0
    for parameter in parameters.split(";"):
        name, _, raw_value = parameter.partition("=")
        if name.strip().casefold() != "q":
            continue
        try:
            weight = float(raw_value)
        except ValueError:
            weight = 0.0
        if not math.isfinite(weight) or not 0 <= weight <= 1:
            weight = 0.0
    return weight


def parse_accept_language(header_value: str | None) -> str | None:
    """Pick the highest-weighted supported locale from an Accept-Language header.

    Deliberately tolerant: a malformed q-value sorts as 0 rather than raising, because a bad header
    from one browser must not turn a page render into a 500.
    """
    if not header_value:
        return None
    candidates: list[tuple[float, int, str]] = []
    for position, part in enumerate(header_value.split(",")):
        tag, _, parameters = part.strip().partition(";")
        if not tag.strip() or tag.strip() == "*":
            continue
        weight = _accept_language_weight(parameters)
        if weight <= 0:
            continue
        # Position breaks ties in header order, which is the order the browser expressed.
        candidates.append((-weight, position, tag.strip()))
    for _, _, tag in sorted(candidates):
        matched = normalize_locale(tag)
        if matched is not None:
            return matched
    return None


def resolve_locale(
    *,
    cookie_value: str | None = None,
    accept_language: str | None = None,
) -> str:
    """Resolve the locale to render in: explicit choice first, then the browser's, then English."""
    return (
        normalize_locale(cookie_value)
        or parse_accept_language(accept_language)
        or DEFAULT_LOCALE
    )


def portal_text(locale: str = DEFAULT_LOCALE) -> dict[str, str]:
    """The text catalog for `locale`, with English filling any key it has not translated."""
    text = TEXT_CATALOG[DEFAULT_LOCALE].copy()
    if locale != DEFAULT_LOCALE:
        text.update(TEXT_CATALOG.get(locale, {}))
    return text


def supported_locale_options(current_locale: str = DEFAULT_LOCALE) -> tuple[dict[str, object], ...]:
    return tuple(
        {
            "code": locale.code,
            "short_label": locale.short_label,
            "label": locale.label,
            "is_selected": locale.code == current_locale,
        }
        for locale in SUPPORTED_LOCALES
    )


def format_portal_datetime(
    value: datetime,
    locale: str = DEFAULT_LOCALE,
    timezone_name: str = "UTC",
) -> str:
    date_format = DATETIME_FORMATS.get(locale, DATETIME_FORMATS[DEFAULT_LOCALE])
    utc_value = value if value.tzinfo is not None else value.replace(tzinfo=UTC)
    return utc_value.astimezone(ZoneInfo(timezone_name)).strftime(date_format)
