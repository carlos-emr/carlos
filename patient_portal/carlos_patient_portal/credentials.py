import re
from threading import BoundedSemaphore

from argon2 import PasswordHasher

from carlos_patient_portal.models import MAX_USERNAME_LENGTH, MIN_USERNAME_LENGTH

MIN_PASSWORD_LENGTH = 12
MAX_PASSWORD_LENGTH = 256
USERNAME_PATTERN = re.compile(r"^[a-z0-9._-]+$")
PASSWORD_SYMBOL_PATTERN = re.compile(r"[^A-Za-z0-9]")

# Defaults sized for a small clinic VM. Peak hashing memory is roughly
# max_concurrency * memory_cost — 4 * 64 MiB = 256 MiB here — so a deployment with a different
# container limit needs to be able to move them; see configure_password_hashing().
DEFAULT_PASSWORD_HASH_MAX_CONCURRENCY = 4
DEFAULT_PASSWORD_HASH_TIME_COST = 3
DEFAULT_PASSWORD_HASH_MEMORY_KIB = 65536
DEFAULT_PASSWORD_HASH_PARALLELISM = 4

PASSWORD_HASH_MAX_CONCURRENCY = DEFAULT_PASSWORD_HASH_MAX_CONCURRENCY
password_hasher = PasswordHasher(
    time_cost=DEFAULT_PASSWORD_HASH_TIME_COST,
    memory_cost=DEFAULT_PASSWORD_HASH_MEMORY_KIB,
    parallelism=DEFAULT_PASSWORD_HASH_PARALLELISM,
    hash_len=32,
    salt_len=16,
)
password_hash_semaphore = BoundedSemaphore(PASSWORD_HASH_MAX_CONCURRENCY)


def configure_password_hashing(
    *,
    max_concurrency: int,
    time_cost: int,
    memory_kib: int,
    parallelism: int,
) -> None:
    """Rebind the process-wide hasher and its concurrency budget from settings.

    Module-level rather than injected because hash_password/verify_password are called from
    services that have no access to Settings, and a portal process serves exactly one
    configuration. Called once during app construction, before any request is served.

    Changing these does not invalidate existing hashes: Argon2 encodes its own parameters in the
    hash string, so verification uses the parameters the hash was created with.
    """
    global PASSWORD_HASH_MAX_CONCURRENCY, password_hasher, password_hash_semaphore
    PASSWORD_HASH_MAX_CONCURRENCY = max_concurrency
    password_hasher = PasswordHasher(
        time_cost=time_cost,
        memory_cost=memory_kib,
        parallelism=parallelism,
        hash_len=32,
        salt_len=16,
    )
    password_hash_semaphore = BoundedSemaphore(max_concurrency)


def hash_password(password: str) -> str:
    with password_hash_semaphore:
        return password_hasher.hash(password)


def verify_password(password_hash: str, password: str) -> bool:
    with password_hash_semaphore:
        return password_hasher.verify(password_hash, password)


def validate_username(username: str) -> str:
    normalized_username = username.strip().casefold()
    if not MIN_USERNAME_LENGTH <= len(normalized_username) <= MAX_USERNAME_LENGTH:
        raise ValueError(
            f"username must be between {MIN_USERNAME_LENGTH} and {MAX_USERNAME_LENGTH} characters"
        )
    if not USERNAME_PATTERN.fullmatch(normalized_username):
        raise ValueError(
            "username may only contain letters, numbers, dots, underscores, or hyphens"
        )
    return normalized_username


def validate_password(password: str) -> str:
    if len(password) < MIN_PASSWORD_LENGTH:
        raise ValueError(f"password must be at least {MIN_PASSWORD_LENGTH} characters")
    if len(password) > MAX_PASSWORD_LENGTH:
        raise ValueError(f"password must be {MAX_PASSWORD_LENGTH} characters or fewer")
    if not any(character.isupper() for character in password):
        raise ValueError("password must contain an uppercase letter")
    if not any(character.islower() for character in password):
        raise ValueError("password must contain a lowercase letter")
    if not any(character.isdigit() for character in password):
        raise ValueError("password must contain a number")
    if PASSWORD_SYMBOL_PATTERN.search(password) is None:
        raise ValueError("password must contain a symbol")
    return password
