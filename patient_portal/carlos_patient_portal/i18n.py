from dataclasses import dataclass

DEFAULT_LOCALE = "en"


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
        "account_status_contact_updated": "Contact update sent for staff review.",
        "account_status_mfa_updated": "MFA settings updated.",
        "account_status_no_change": "No account changes.",
        "account_status_password_updated": "Password updated.",
        "all_providers": "All providers",
        "activate_account": "Activate account",
        "activation_details": (
            "Enter the invitation details supplied by your clinic and create your "
            "portal sign-in."
        ),
        "activation_error": "The activation details could not be verified.",
        "activation_heading": "Activate your account",
        "activation_rate_limited": (
            "Too many activation attempts were made. Wait before trying again."
        ),
        "activation_success": "Your portal account is ready.",
        "activation_success_heading": "Account activated",
        "back_to_sign_in": "Back to sign in",
        "change_password": "Change password",
        "clinic": "Clinic",
        "clinic_help": "Clinic help",
        "clinic_help_message": (
            "Contact the clinic if you cannot access your account or verification method."
        ),
        "contact_info": "Contact info",
        "contact_the_clinic": "Contact the clinic",
        "copy": "Copy",
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
        "incorrect_mfa_code": "The code was not accepted. Try again or request a new code.",
        "incorrect_username_or_password": "Incorrect Username or Password",
        "invite_code": "Invitation code",
        "language_aria_label": "Language",
        "language_unavailable_message": "Language switching has not been implemented yet.",
        "language_unavailable_title": "Language not implemented",
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
        "previous": "Previous",
        "provider": "Provider",
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
        "session_locked_heading": "Account locked",
        "sign_in_aria_label": "Sign in",
        "sign_in_button": "Sign in",
        "sign_in_heading": "Sign in",
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


def portal_text(locale: str = DEFAULT_LOCALE) -> dict[str, str]:
    return TEXT_CATALOG.get(locale, TEXT_CATALOG[DEFAULT_LOCALE]).copy()


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
