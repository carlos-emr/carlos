from dataclasses import dataclass
from datetime import UTC, date, datetime
from hashlib import sha256
from hmac import compare_digest
from hmac import new as new_hmac

from carlos_patient_portal.models import HASH_LENGTH, MAX_EMAIL_LENGTH

MAX_HEALTH_CARD_NUMBER_LENGTH = 64


@dataclass(frozen=True)
class IdentityProof:
    """Patient identity proof values supplied at invite creation and activation."""

    email: str
    date_of_birth: date
    health_card_number: str


def normalize_email(email: str) -> str:
    normalized_email = email.strip().casefold()
    local_part, separator, domain_part = normalized_email.partition("@")
    if (
        not separator
        or not local_part
        or not domain_part
        or len(normalized_email) > MAX_EMAIL_LENGTH
        or any(character.isspace() for character in normalized_email)
    ):
        raise ValueError("email must be a valid email address")
    return normalized_email


def normalize_date_of_birth(date_of_birth: date) -> str:
    if isinstance(date_of_birth, datetime):
        normalized_date = date_of_birth.date()
    else:
        normalized_date = date_of_birth
    if normalized_date > datetime.now(UTC).date():
        raise ValueError("date_of_birth must not be in the future")
    return normalized_date.isoformat()


def normalize_health_card_number(health_card_number: str) -> str:
    normalized_hcn = "".join(
        character
        for character in health_card_number.strip().upper()
        if not character.isspace() and character != "-"
    )
    if (
        not normalized_hcn
        or len(normalized_hcn) > MAX_HEALTH_CARD_NUMBER_LENGTH
        or not normalized_hcn.isalnum()
    ):
        raise ValueError("health_card_number must contain letters or numbers")
    return normalized_hcn


def hash_identity_value(secret: str, purpose: str, value: str) -> str:
    return new_hmac(
        secret.encode("utf-8"),
        f"{purpose}:{value}".encode(),
        sha256,
    ).hexdigest()


def build_identity_hashes(proof: IdentityProof, secret: str) -> dict[str, str]:
    email_hash = hash_identity_value(secret, "email", normalize_email(proof.email))
    date_of_birth_hash = hash_identity_value(
        secret,
        "date_of_birth",
        normalize_date_of_birth(proof.date_of_birth),
    )
    health_card_hash = hash_identity_value(
        secret,
        "health_card_number",
        normalize_health_card_number(proof.health_card_number),
    )
    return {
        "proof_email_hash": email_hash,
        "proof_date_of_birth_hash": date_of_birth_hash,
        "proof_health_card_hash": health_card_hash,
    }


def has_complete_identity_proof_hashes(
    email_hash: str | None,
    date_of_birth_hash: str | None,
    health_card_hash: str | None,
) -> bool:
    return all(
        hash_value is not None and len(hash_value) == HASH_LENGTH
        for hash_value in (email_hash, date_of_birth_hash, health_card_hash)
    )


def verify_identity_proof(
    proof: IdentityProof,
    secret: str,
    *,
    email_hash: str | None,
    date_of_birth_hash: str | None,
    health_card_hash: str | None,
) -> bool:
    if not has_complete_identity_proof_hashes(email_hash, date_of_birth_hash, health_card_hash):
        return False

    expected_hashes = build_identity_hashes(proof, secret)
    return (
        compare_digest(expected_hashes["proof_email_hash"], email_hash or "")
        and compare_digest(expected_hashes["proof_date_of_birth_hash"], date_of_birth_hash or "")
        and compare_digest(expected_hashes["proof_health_card_hash"], health_card_hash or "")
    )
