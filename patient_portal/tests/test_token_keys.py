import pytest

from carlos_patient_portal.auth import hash_auth_token
from carlos_patient_portal.config import MIN_PRODUCTION_SECRET_LENGTH, Settings
from carlos_patient_portal.main import build_portal_runtime, sign_csrf_token
from carlos_patient_portal.token_keys import PortalTokenKeys, derive_token_key

SESSION_SECRET = "s" * MIN_PRODUCTION_SECRET_LENGTH


def development_settings(**overrides: object) -> Settings:
    values: dict[str, object] = {
        "environment": "development",
        "database_url": "sqlite+pysqlite:///:memory:",
        "session_secret": SESSION_SECRET,
        **overrides,
    }
    return Settings(**values)


def test_derived_keys_are_distinct_for_every_purpose() -> None:
    keys = PortalTokenKeys.derive(SESSION_SECRET)
    derived_values = (keys.csrf, keys.session, keys.mfa, keys.password_reset)

    assert len(set(derived_values)) == len(derived_values)
    # The configured secret must never be usable directly as a token key.
    assert SESSION_SECRET not in derived_values


def test_derivation_is_reproducible_across_processes() -> None:
    assert PortalTokenKeys.derive(SESSION_SECRET) == PortalTokenKeys.derive(SESSION_SECRET)
    assert PortalTokenKeys.derive(SESSION_SECRET) != PortalTokenKeys.derive("t" * 32)


def test_derivation_ignores_surrounding_whitespace_but_not_content() -> None:
    assert derive_token_key(f"  {SESSION_SECRET}  ", "session") == derive_token_key(
        SESSION_SECRET,
        "session",
    )
    assert derive_token_key(SESSION_SECRET, "session") != derive_token_key(SESSION_SECRET, "csrf")


@pytest.mark.parametrize(
    ("session_secret", "purpose"),
    [("", "session"), ("   ", "session"), (SESSION_SECRET, ""), (SESSION_SECRET, "  ")],
)
def test_derivation_rejects_blank_inputs(session_secret: str, purpose: str) -> None:
    with pytest.raises(ValueError):
        derive_token_key(session_secret, purpose)


def test_token_hash_from_one_key_does_not_verify_under_another() -> None:
    """The property the derivation exists to guarantee.

    Previously all four constructions shared one key, so separation rested entirely on each call
    site passing the right purpose string to `hash_auth_token`. A call site that omitted or
    duplicated one could mint a value accepted in a different role; with separate keys it cannot.
    """
    keys = PortalTokenKeys.derive(SESSION_SECRET)
    token = "shared-opaque-token"

    session_hash = hash_auth_token(keys.session, "session", token)
    reset_hash = hash_auth_token(keys.password_reset, "password_reset", token)
    # Even with the purpose string dropped, the keys alone keep the two roles apart.
    assert hash_auth_token(keys.session, "session", token) != hash_auth_token(
        keys.password_reset,
        "session",
        token,
    )
    assert session_hash != reset_hash


def test_csrf_signature_does_not_verify_as_a_session_token_hash() -> None:
    keys = PortalTokenKeys.derive(SESSION_SECRET)
    message = "1700000000.nonce"

    assert sign_csrf_token(message, keys.csrf) != hash_auth_token(keys.session, "session", message)


def test_runtime_derives_keys_rather_than_reusing_the_configured_secret() -> None:
    runtime = build_portal_runtime(development_settings())

    assert runtime.token_keys.csrf != SESSION_SECRET
    assert runtime.token_keys == PortalTokenKeys.derive(SESSION_SECRET)
    runtime.database_engine.dispose()


def test_runtime_without_a_configured_secret_still_derives_usable_keys() -> None:
    """Development may run without PATIENT_PORTAL_SESSION_SECRET; keys are ephemeral, not absent."""
    first = build_portal_runtime(development_settings(session_secret=None))
    second = build_portal_runtime(development_settings(session_secret=None))

    assert first.token_keys != second.token_keys
    assert all(
        key
        for key in (
            first.token_keys.csrf,
            first.token_keys.session,
            first.token_keys.mfa,
            first.token_keys.password_reset,
        )
    )
    first.database_engine.dispose()
    second.database_engine.dispose()
