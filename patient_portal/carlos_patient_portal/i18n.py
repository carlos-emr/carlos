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
        "account_recovery_unavailable_message": (
            "Username and password recovery has not been implemented yet. "
            "Contact the clinic for help."
        ),
        "account_recovery_unavailable_title": "Account recovery not implemented",
        "continue": "Sign in",
        "forgot_username_password": "Forgot username or password?",
        "incorrect_username_or_password": "Incorrect Username or Password",
        "language_aria_label": "Language",
        "language_unavailable_message": "Language switching has not been implemented yet.",
        "language_unavailable_title": "Language not implemented",
        "logo_alt": "CARLOS",
        "modal_close": "OK",
        "password_label": "Password",
        "password_placeholder": "Carlos2026!",
        "sign_in_aria_label": "Sign in",
        "sign_in_heading": "Sign in",
        "username_label": "User Name",
        "username_placeholder": "CarlosPatient",
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
