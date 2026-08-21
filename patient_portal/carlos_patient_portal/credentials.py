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
from threading import BoundedSemaphore

from argon2 import PasswordHasher

from carlos_patient_portal.models import MAX_USERNAME_LENGTH, MIN_USERNAME_LENGTH

MIN_PASSWORD_LENGTH = 12
MAX_PASSWORD_LENGTH = 256
USERNAME_PATTERN = re.compile(r"^[a-z0-9._-]+$")

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


def password_needs_rehash(password_hash: str) -> bool:
    """Check against the currently configured hasher rather than the import-time default."""
    return password_hasher.check_needs_rehash(password_hash)


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
    """Enforce the portal's password policy.

    The composition rules below (upper, lower, digit, symbol) are kept deliberately, and are worth
    a note because they run against current guidance: NIST SP 800-63B section 5.1.1.2 recommends
    *against* composition rules and *for* a length minimum plus a breached-password check, on the
    evidence that composition rules push users toward `Password1!` shapes.

    They are retained for the pilot because the better replacement is a breached-password check,
    which the portal does not have yet and which needs a wordlist, a refresh story, and an offline
    lookup path before it can be relied on. Removing the rules before that lands would weaken the
    policy rather than modernise it. The stronger half of the guidance is already in place: a
    12-character minimum, Argon2id, mandatory MFA, and account lockout.

    Do not "fix" this by deleting the class checks on their own — replace them with the blocklist.
    """
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
    if not any(
        not character.isalnum() and not character.isspace() for character in password
    ):
        raise ValueError("password must contain a symbol")
    return password
