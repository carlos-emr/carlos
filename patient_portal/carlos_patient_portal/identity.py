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

import re
from dataclasses import dataclass
from datetime import UTC, date, datetime, timedelta
from hashlib import sha256
from hmac import compare_digest
from hmac import new as new_hmac

from carlos_patient_portal.models import HASH_LENGTH, MAX_EMAIL_LENGTH

MAX_HEALTH_CARD_NUMBER_LENGTH = 64
MIN_HEALTH_CARD_NUMBER_LENGTH = 4
EMAIL_PATTERN = re.compile(
    r"^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+"
    r"(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
    r"(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+"
    r"[A-Za-z]{2,63}$"
)


@dataclass(frozen=True)
class IdentityProof:
    """Patient identity proof values supplied at invite creation and activation."""

    email: str
    date_of_birth: date
    health_card_number: str


# C0 and C1 control characters. C1 matters as much as C0 here: Starlette decodes header bytes
# as latin-1, so byte 0x92 - the Windows-1252 right single quote, pervasive in legacy EMR name
# data - arrives as U+0092, a C1 control character.
CONTROL_CHARACTER_PATTERN = re.compile(r"[\x00-\x1f\x7f-\x9f]")


def reject_control_characters(value: str, field_name: str) -> None:
    if CONTROL_CHARACTER_PATTERN.search(value) is not None:
        raise ValueError(f"{field_name} must not contain control characters")


def normalize_email(email: str) -> str:
    normalized_email = email.strip().casefold()
    if (
        len(normalized_email) > MAX_EMAIL_LENGTH
        or EMAIL_PATTERN.fullmatch(normalized_email) is None
    ):
        raise ValueError("email must be a valid email address")
    return normalized_email


def normalize_date_of_birth(date_of_birth: date) -> str:
    if isinstance(date_of_birth, datetime):
        normalized_date = date_of_birth.date()
    else:
        normalized_date = date_of_birth
    today = datetime.now(UTC).date()
    try:
        earliest = today.replace(year=today.year - 126)
    except ValueError:
        # February 29 has no direct counterpart in most years. Treat February 28 as the
        # inclusive anniversary boundary rather than crashing during leap years.
        earliest = date(today.year - 126, 2, 28)
    latest = today + timedelta(days=1)
    if normalized_date < earliest or normalized_date > latest:
        raise ValueError("date_of_birth must be within the supported range")
    return normalized_date.isoformat()


def normalize_health_card_number(health_card_number: str) -> str:
    normalized_hcn = "".join(
        character
        for character in health_card_number.strip().upper()
        if not character.isspace() and character != "-"
    )
    if (
        len(normalized_hcn) < MIN_HEALTH_CARD_NUMBER_LENGTH
        or len(normalized_hcn) > MAX_HEALTH_CARD_NUMBER_LENGTH
        or not normalized_hcn.isascii()
        or not normalized_hcn.isalnum()
    ):
        raise ValueError("health_card_number must contain letters or numbers")
    return normalized_hcn


def hash_identity_value(secret: str, salt: str, purpose: str, value: str) -> str:
    return new_hmac(
        secret.encode("utf-8"),
        f"{purpose}:{salt}:{value}".encode(),
        sha256,
    ).hexdigest()


def build_identity_hashes(proof: IdentityProof, secret: str, salt: str) -> dict[str, str]:
    normalized_salt = salt.strip()
    if not normalized_salt:
        raise ValueError("salt must not be blank")
    email_hash = hash_identity_value(secret, normalized_salt, "email", normalize_email(proof.email))
    date_of_birth_hash = hash_identity_value(
        secret,
        normalized_salt,
        "date_of_birth",
        normalize_date_of_birth(proof.date_of_birth),
    )
    health_card_hash = hash_identity_value(
        secret,
        normalized_salt,
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
    salt: str | None,
    email_hash: str | None,
    date_of_birth_hash: str | None,
    health_card_hash: str | None,
) -> bool:
    if (
        salt is None
        or not salt.strip()
        or not has_complete_identity_proof_hashes(
            email_hash,
            date_of_birth_hash,
            health_card_hash,
        )
    ):
        return False

    try:
        expected_hashes = build_identity_hashes(proof, secret, salt)
    except ValueError:
        return False
    email_matches = compare_digest(expected_hashes["proof_email_hash"], email_hash or "")
    date_of_birth_matches = compare_digest(
        expected_hashes["proof_date_of_birth_hash"], date_of_birth_hash or ""
    )
    health_card_matches = compare_digest(
        expected_hashes["proof_health_card_hash"], health_card_hash or ""
    )
    return email_matches and date_of_birth_matches and health_card_matches
