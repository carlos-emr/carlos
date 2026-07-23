import re

from argon2 import PasswordHasher

from carlos_patient_portal.models import MAX_USERNAME_LENGTH, MIN_USERNAME_LENGTH

MIN_PASSWORD_LENGTH = 12
MAX_PASSWORD_LENGTH = 256
USERNAME_PATTERN = re.compile(r"^[a-z0-9._-]+$")
PASSWORD_SYMBOL_PATTERN = re.compile(r"[^A-Za-z0-9]")
password_hasher = PasswordHasher(
    time_cost=3,
    memory_cost=65536,
    parallelism=4,
    hash_len=32,
    salt_len=16,
)


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
