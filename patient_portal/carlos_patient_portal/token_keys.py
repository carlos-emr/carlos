"""Per-purpose key derivation for the portal's authentication tokens.

`PATIENT_PORTAL_SESSION_SECRET` is a single configured value that several unrelated constructions
depend on: CSRF signatures, session-token hashes, MFA challenge-token and code hashes,
password-reset token hashes, and email-change confirmation tokens. Handing the same key to all of
them made their separation depend on every call site remembering to pass a distinct purpose string
to `hash_auth_token` — a convention, enforced by nothing. Deriving an independent key per purpose
here makes it structural instead: a signature produced for one role cannot verify in another even
if a purpose string is omitted.

The purpose strings inside `hash_auth_token` are deliberately kept as well. They now separate the
sub-purposes that share one derived key (session tokens and their lookups), and they cost nothing.

Rotating the configured secret rotates every derived key together, which signs every patient
out and invalidates pending MFA challenges and reset tokens. That is already the documented
behaviour of rotating `PATIENT_PORTAL_SESSION_SECRET`; deriving keys does not change it.
"""

from base64 import urlsafe_b64encode
from dataclasses import dataclass

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

# Versioned so a future change to the derivation can be introduced without silently producing
# different keys from the same configured secret.
TOKEN_KEY_DERIVATION_PREFIX = b"carlos-patient-portal:token-key:v1:"
DERIVED_TOKEN_KEY_BYTES = 32
CSRF_KEY_PURPOSE = "csrf"
SESSION_KEY_PURPOSE = "session"
MFA_KEY_PURPOSE = "mfa"
PASSWORD_RESET_KEY_PURPOSE = "password-reset"  # noqa: S105 -- a label, not a credential
EMAIL_CHANGE_KEY_PURPOSE = "email-change"


def derive_token_key(session_secret: str, purpose: str) -> str:
    """Derive one purpose-bound key from the configured session secret.

    Returned as text rather than bytes because the HMAC helpers that consume these keys take `str`
    secrets. `salt=None` is required and not an oversight: derivation has to be reproducible on
    every process start from the configured secret alone, and the per-purpose `info` supplies the
    separation a salt would otherwise provide.
    """
    normalized_secret = session_secret.strip()
    if not normalized_secret:
        raise ValueError("session secret must not be blank")
    normalized_purpose = purpose.strip()
    if not normalized_purpose:
        raise ValueError("token key purpose must not be blank")
    derived_key = HKDF(
        algorithm=hashes.SHA256(),
        length=DERIVED_TOKEN_KEY_BYTES,
        salt=None,
        info=TOKEN_KEY_DERIVATION_PREFIX + normalized_purpose.encode("utf-8"),
    ).derive(normalized_secret.encode("utf-8"))
    return urlsafe_b64encode(derived_key).decode("ascii")


@dataclass(frozen=True)
class PortalTokenKeys:
    """One independent key per authentication-token purpose.

    Held as separate named fields rather than a mapping so a route that wants the session key
    cannot reach for the CSRF key by passing the wrong string; the mistake is a type error.
    """

    csrf: str
    session: str
    mfa: str
    password_reset: str
    email_change: str

    @classmethod
    def derive(cls, session_secret: str) -> "PortalTokenKeys":
        return cls(
            csrf=derive_token_key(session_secret, CSRF_KEY_PURPOSE),
            session=derive_token_key(session_secret, SESSION_KEY_PURPOSE),
            mfa=derive_token_key(session_secret, MFA_KEY_PURPOSE),
            password_reset=derive_token_key(session_secret, PASSWORD_RESET_KEY_PURPOSE),
            email_change=derive_token_key(session_secret, EMAIL_CHANGE_KEY_PURPOSE),
        )
