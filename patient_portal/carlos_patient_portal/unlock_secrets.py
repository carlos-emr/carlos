import re
from dataclasses import dataclass
from datetime import datetime
from functools import lru_cache
from pathlib import Path
from secrets import choice, randbelow, token_bytes

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from sqlalchemy import Select, func, or_, select
from sqlalchemy.orm import Session

from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.config import MIN_PRODUCTION_SECRET_LENGTH
from carlos_patient_portal.invites import normalize_clinic_id
from carlos_patient_portal.models import (
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_ACTOR_TYPE_STAFF,
    AUDIT_EVENT_UNLOCK_SECRET_CREATE,
    AUDIT_EVENT_UNLOCK_SECRET_READ,
    AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
    AUDIT_OUTCOME_SUCCESS,
    MAX_UNLOCK_SECRET_ACTOR_LENGTH,
    MAX_UNLOCK_SECRET_KEY_ID_LENGTH,
    MAX_UNLOCK_SECRET_LABEL_LENGTH,
    MAX_UNLOCK_SECRET_REVOKE_REASON_LENGTH,
    MAX_UNLOCK_SECRET_SOURCE_REFERENCE_LENGTH,
    UNLOCK_SECRET_NONCE_LENGTH,
    UNLOCK_SECRET_STATUS_ACTIVE,
    UNLOCK_SECRET_STATUS_REVOKED,
    UNLOCK_SECRET_TYPE_EMAIL,
    UNLOCK_SECRET_TYPE_PDF,
    PatientPortalAccount,
    PatientPortalUnlockSecret,
    utc_now,
)

ENCRYPTION_ALGORITHM = "AES-256-GCM-HKDF-SHA256"
ENCRYPTION_KEY_ID_DEFAULT = "primary"
KEY_DERIVATION_INFO = b"carlos-patient-portal:unlock-secret:v1"
ASSOCIATED_DATA_PREFIX = "carlos-patient-portal.unlock-secret.v1"
DEFAULT_UNLOCK_SECRET_LIST_LIMIT = 10
MAX_UNLOCK_SECRET_LIST_LIMIT = 100
MAX_UNLOCK_SECRET_SEARCH_LENGTH = 128
MAX_UNLOCK_SECRET_PROVIDER_OPTIONS = 100
MAX_UNLOCK_SECRET_PLAINTEXT_BYTES = 1024
UNLOCK_SECRET_WORDLIST_RESOURCE = "wordlists/patient_pdf_passphrase_english.txt"
UNLOCK_SECRET_WORDLIST_SIZE = 4096
UNLOCK_SECRET_WORD_PATTERN = re.compile(r"^[a-z]+$")
UNLOCK_SECRET_WORDLIST_ROW_PATTERN = re.compile(r"^(\d{4})\t([a-z]+)$")


class UnlockSecretNotFoundError(Exception):
    """Raised when an unlock secret does not exist in the requested scope."""


class UnlockSecretRevokedError(Exception):
    """Raised when an unlock secret was revoked before it was read."""


class UnlockSecretDecryptionError(Exception):
    """Raised when a stored unlock secret cannot be decrypted with the configured key."""


@dataclass(frozen=True)
class CreatedUnlockSecret:
    unlock_secret: PatientPortalUnlockSecret
    secret: str


@dataclass(frozen=True)
class EncryptedUnlockSecretPayload:
    encrypted_secret: bytes
    encryption_nonce: bytes


@lru_cache(maxsize=1)
def load_unlock_secret_words() -> tuple[str, ...]:
    wordlist_path = Path(__file__).resolve().parent / UNLOCK_SECRET_WORDLIST_RESOURCE
    words: list[str] = []
    with wordlist_path.open("r", encoding="utf-8") as wordlist:
        for line in wordlist:
            stripped_line = line.strip()
            if not stripped_line or stripped_line.startswith("#"):
                continue
            row_match = UNLOCK_SECRET_WORDLIST_ROW_PATTERN.fullmatch(stripped_line)
            if row_match is None or row_match.group(1) != f"{len(words):04d}":
                raise RuntimeError("unlock-secret wordlist contains an invalid row")
            word = row_match.group(2)
            if UNLOCK_SECRET_WORD_PATTERN.fullmatch(word) is None:
                raise RuntimeError("unlock-secret wordlist contains an invalid word")
            words.append(word)
    if len(words) != UNLOCK_SECRET_WORDLIST_SIZE or len(set(words)) != len(words):
        raise RuntimeError(
            f"unlock-secret wordlist must contain {UNLOCK_SECRET_WORDLIST_SIZE} unique words"
        )
    return tuple(words)


def generate_unlock_secret_value() -> str:
    words = load_unlock_secret_words()
    parts: list[str] = []
    for _ in range(2):
        parts.extend((choice(words), choice(words)))
        parts.append(f"{randbelow(1000):03d}")
    return "-".join(parts)


def encrypt_unlock_secret_payload(
    secret: str,
    *,
    encryption_secret: str,
    clinic_id: str,
    demographic_no: int,
    secret_type: str,
    encryption_key_id: str = ENCRYPTION_KEY_ID_DEFAULT,
) -> EncryptedUnlockSecretPayload:
    encoded_secret = validate_plaintext_secret(secret).encode("utf-8")
    nonce = token_bytes(UNLOCK_SECRET_NONCE_LENGTH)
    aesgcm = AESGCM(derive_unlock_secret_key(encryption_secret))
    encrypted_secret = aesgcm.encrypt(
        nonce,
        encoded_secret,
        build_associated_data(
            clinic_id=clinic_id,
            demographic_no=demographic_no,
            secret_type=secret_type,
            encryption_key_id=encryption_key_id,
        ),
    )
    return EncryptedUnlockSecretPayload(
        encrypted_secret=encrypted_secret,
        encryption_nonce=nonce,
    )


def decrypt_unlock_secret_payload(
    unlock_secret: PatientPortalUnlockSecret,
    *,
    encryption_secret: str,
) -> str:
    aesgcm = AESGCM(derive_unlock_secret_key(encryption_secret))
    try:
        decrypted_secret = aesgcm.decrypt(
            unlock_secret.encryption_nonce,
            unlock_secret.encrypted_secret,
            build_associated_data(
                clinic_id=unlock_secret.clinic_id,
                demographic_no=unlock_secret.demographic_no,
                secret_type=unlock_secret.secret_type,
                encryption_key_id=unlock_secret.encryption_key_id,
            ),
        )
    except InvalidTag as exc:
        raise UnlockSecretDecryptionError("unlock secret could not be decrypted") from exc

    try:
        return decrypted_secret.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise UnlockSecretDecryptionError("unlock secret plaintext was not valid UTF-8") from exc


def create_unlock_secret(
    session: Session,
    *,
    clinic_id: str,
    demographic_no: int,
    created_by: str,
    encryption_secret: str,
    secret_type: str = UNLOCK_SECRET_TYPE_EMAIL,
    secret: str | None = None,
    account_id: int | None = None,
    label: str | None = None,
    source_reference: str | None = None,
    encryption_key_id: str = ENCRYPTION_KEY_ID_DEFAULT,
) -> CreatedUnlockSecret:
    normalized_clinic_id = normalize_clinic_id(clinic_id)
    normalized_demographic_no = normalize_demographic_no(demographic_no)
    normalized_secret_type = normalize_unlock_secret_type(secret_type)
    normalized_created_by = normalize_required_text(
        created_by,
        field_name="created_by",
        max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
    )
    normalized_key_id = normalize_required_text(
        encryption_key_id,
        field_name="encryption_key_id",
        max_length=MAX_UNLOCK_SECRET_KEY_ID_LENGTH,
    )
    normalized_label = normalize_optional_text(
        label,
        field_name="label",
        max_length=MAX_UNLOCK_SECRET_LABEL_LENGTH,
    )
    normalized_source_reference = normalize_optional_text(
        source_reference,
        field_name="source_reference",
        max_length=MAX_UNLOCK_SECRET_SOURCE_REFERENCE_LENGTH,
    )
    resolved_account_id = resolve_account_id(
        session,
        clinic_id=normalized_clinic_id,
        demographic_no=normalized_demographic_no,
        account_id=account_id,
    )
    plaintext_secret = secret if secret is not None else generate_unlock_secret_value()
    encrypted_payload = encrypt_unlock_secret_payload(
        plaintext_secret,
        encryption_secret=encryption_secret,
        clinic_id=normalized_clinic_id,
        demographic_no=normalized_demographic_no,
        secret_type=normalized_secret_type,
        encryption_key_id=normalized_key_id,
    )
    now = utc_now()
    unlock_secret = PatientPortalUnlockSecret(
        clinic_id=normalized_clinic_id,
        demographic_no=normalized_demographic_no,
        account_id=resolved_account_id,
        secret_type=normalized_secret_type,
        status=UNLOCK_SECRET_STATUS_ACTIVE,
        label=normalized_label,
        source_reference=normalized_source_reference,
        encrypted_secret=encrypted_payload.encrypted_secret,
        encryption_nonce=encrypted_payload.encryption_nonce,
        encryption_algorithm=ENCRYPTION_ALGORITHM,
        encryption_key_id=normalized_key_id,
        created_by=normalized_created_by,
        created_at=now,
        updated_at=now,
    )
    session.add(unlock_secret)
    session.flush()
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_UNLOCK_SECRET_CREATE,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_STAFF,
        actor=normalized_created_by,
        clinic_id=normalized_clinic_id,
        demographic_no=normalized_demographic_no,
        account_id=resolved_account_id,
    )
    return CreatedUnlockSecret(unlock_secret=unlock_secret, secret=plaintext_secret)


def read_unlock_secret(
    session: Session,
    unlock_secret_id: int,
    *,
    clinic_id: str,
    encryption_secret: str,
    actor_type: str,
    actor: str,
    account_id: int | None = None,
    audit_account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
) -> str:
    normalized_actor_type = normalize_actor_type(actor_type)
    normalized_actor = normalize_required_text(
        actor,
        field_name="actor",
        max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
    )
    normalized_audit_account_id = (
        normalize_account_id(audit_account_id) if audit_account_id is not None else None
    )
    unlock_secret = get_scoped_unlock_secret(
        session,
        unlock_secret_id,
        clinic_id=clinic_id,
        account_id=account_id,
        demographic_no=demographic_no,
        secret_type=secret_type,
        for_update=True,
    )
    if unlock_secret.status == UNLOCK_SECRET_STATUS_REVOKED:
        raise UnlockSecretRevokedError()

    plaintext_secret = decrypt_unlock_secret_payload(
        unlock_secret,
        encryption_secret=encryption_secret,
    )
    now = utc_now()
    unlock_secret.last_viewed_at = now
    unlock_secret.updated_at = now
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_UNLOCK_SECRET_READ,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=normalized_actor_type,
        actor=normalized_actor,
        clinic_id=unlock_secret.clinic_id,
        demographic_no=unlock_secret.demographic_no,
        account_id=(
            normalized_audit_account_id
            if normalized_audit_account_id is not None
            else unlock_secret.account_id
        ),
    )
    return plaintext_secret


def revoke_unlock_secret(
    session: Session,
    unlock_secret_id: int,
    *,
    clinic_id: str,
    revoked_by: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    reason: str | None = None,
) -> PatientPortalUnlockSecret:
    normalized_revoked_by = normalize_required_text(
        revoked_by,
        field_name="revoked_by",
        max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
    )
    normalized_reason = normalize_optional_text(
        reason,
        field_name="reason",
        max_length=MAX_UNLOCK_SECRET_REVOKE_REASON_LENGTH,
    )
    unlock_secret = get_scoped_unlock_secret(
        session,
        unlock_secret_id,
        clinic_id=clinic_id,
        account_id=account_id,
        demographic_no=demographic_no,
        for_update=True,
    )
    if unlock_secret.status != UNLOCK_SECRET_STATUS_REVOKED:
        now = utc_now()
        unlock_secret.status = UNLOCK_SECRET_STATUS_REVOKED
        unlock_secret.revoked_at = now
        unlock_secret.revoked_by = normalized_revoked_by
        unlock_secret.revoke_reason = normalized_reason
        unlock_secret.updated_at = now
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_STAFF,
        actor=normalized_revoked_by,
        clinic_id=unlock_secret.clinic_id,
        demographic_no=unlock_secret.demographic_no,
        account_id=unlock_secret.account_id,
        reason=normalized_reason,
    )
    return unlock_secret


def _filtered_unlock_secret_statement(
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    include_revoked: bool = False,
    secret_type: str | None = None,
    search: str | None = None,
    provider: str | None = None,
    created_from: datetime | None = None,
    created_before: datetime | None = None,
) -> Select[tuple[PatientPortalUnlockSecret]]:
    statement = scoped_unlock_secret_statement(
        clinic_id=clinic_id,
        account_id=account_id,
        demographic_no=demographic_no,
        secret_type=secret_type,
    )
    if not include_revoked:
        statement = statement.where(PatientPortalUnlockSecret.status == UNLOCK_SECRET_STATUS_ACTIVE)
    normalized_search = normalize_optional_text(
        search,
        field_name="search",
        max_length=MAX_UNLOCK_SECRET_SEARCH_LENGTH,
    )
    if normalized_search is not None:
        like_pattern = f"%{escape_like_pattern(normalized_search)}%"
        statement = statement.where(
            or_(
                PatientPortalUnlockSecret.label.ilike(like_pattern, escape="\\"),
                PatientPortalUnlockSecret.source_reference.ilike(like_pattern, escape="\\"),
                PatientPortalUnlockSecret.created_by.ilike(like_pattern, escape="\\"),
            )
        )
    normalized_provider = normalize_optional_text(
        provider,
        field_name="provider",
        max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
    )
    if normalized_provider is not None:
        statement = statement.where(
            PatientPortalUnlockSecret.created_by == normalized_provider
        )
    if created_from is not None:
        statement = statement.where(PatientPortalUnlockSecret.created_at >= created_from)
    if created_before is not None:
        statement = statement.where(PatientPortalUnlockSecret.created_at < created_before)
    if (
        created_from is not None
        and created_before is not None
        and created_from >= created_before
    ):
        raise ValueError("created_from must be earlier than created_before")
    return statement


def count_unlock_secrets(
    session: Session,
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    include_revoked: bool = False,
    secret_type: str | None = None,
    search: str | None = None,
    provider: str | None = None,
    created_from: datetime | None = None,
    created_before: datetime | None = None,
) -> int:
    filtered_statement = _filtered_unlock_secret_statement(
        clinic_id=clinic_id,
        account_id=account_id,
        demographic_no=demographic_no,
        include_revoked=include_revoked,
        secret_type=secret_type,
        search=search,
        provider=provider,
        created_from=created_from,
        created_before=created_before,
    )
    count_statement = filtered_statement.with_only_columns(
        func.count(PatientPortalUnlockSecret.id),
        maintain_column_froms=True,
    ).order_by(None)
    return int(session.scalar(count_statement) or 0)


def list_unlock_secrets(
    session: Session,
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    include_revoked: bool = False,
    secret_type: str | None = None,
    search: str | None = None,
    provider: str | None = None,
    created_from: datetime | None = None,
    created_before: datetime | None = None,
    limit: int = DEFAULT_UNLOCK_SECRET_LIST_LIMIT,
    offset: int = 0,
) -> list[PatientPortalUnlockSecret]:
    normalized_limit = normalize_list_limit(limit)
    normalized_offset = normalize_list_offset(offset)
    statement = _filtered_unlock_secret_statement(
        clinic_id=clinic_id,
        account_id=account_id,
        demographic_no=demographic_no,
        include_revoked=include_revoked,
        secret_type=secret_type,
        search=search,
        provider=provider,
        created_from=created_from,
        created_before=created_before,
    )
    statement = (
        statement.order_by(
            PatientPortalUnlockSecret.created_at.desc(),
            PatientPortalUnlockSecret.id.desc(),
        )
        .limit(normalized_limit)
        .offset(normalized_offset)
    )
    return list(session.scalars(statement))


def list_unlock_secret_providers(
    session: Session,
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
) -> list[str]:
    scoped_statement = scoped_unlock_secret_statement(
        clinic_id=clinic_id,
        account_id=account_id,
        demographic_no=demographic_no,
        secret_type=secret_type,
    )
    statement = (
        scoped_statement.with_only_columns(PatientPortalUnlockSecret.created_by)
        .where(PatientPortalUnlockSecret.status == UNLOCK_SECRET_STATUS_ACTIVE)
        .distinct()
        .order_by(PatientPortalUnlockSecret.created_by)
        .limit(MAX_UNLOCK_SECRET_PROVIDER_OPTIONS)
    )
    return list(session.scalars(statement))


def get_scoped_unlock_secret(
    session: Session,
    unlock_secret_id: int,
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
    for_update: bool = False,
) -> PatientPortalUnlockSecret:
    if unlock_secret_id <= 0:
        raise UnlockSecretNotFoundError()
    statement = scoped_unlock_secret_statement(
        clinic_id=clinic_id,
        account_id=account_id,
        demographic_no=demographic_no,
        secret_type=secret_type,
    ).where(PatientPortalUnlockSecret.id == unlock_secret_id)
    if for_update:
        statement = statement.with_for_update()
    unlock_secret = session.scalar(statement)
    if unlock_secret is None:
        raise UnlockSecretNotFoundError()
    return unlock_secret


def scoped_unlock_secret_statement(
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
) -> Select[tuple[PatientPortalUnlockSecret]]:
    if account_id is None and demographic_no is None:
        raise ValueError("account_id or demographic_no is required")

    normalized_clinic_id = normalize_clinic_id(clinic_id)
    statement = select(PatientPortalUnlockSecret).where(
        PatientPortalUnlockSecret.clinic_id == normalized_clinic_id
    )
    if account_id is not None:
        statement = statement.where(
            PatientPortalUnlockSecret.account_id == normalize_account_id(account_id)
        )
    if demographic_no is not None:
        statement = statement.where(
            PatientPortalUnlockSecret.demographic_no == normalize_demographic_no(demographic_no)
        )
    if secret_type is not None:
        statement = statement.where(
            PatientPortalUnlockSecret.secret_type == normalize_unlock_secret_type(secret_type)
        )
    return statement


def resolve_account_id(
    session: Session,
    *,
    clinic_id: str,
    demographic_no: int,
    account_id: int | None,
) -> int | None:
    statement = select(PatientPortalAccount.id).where(
        PatientPortalAccount.clinic_id == clinic_id,
        PatientPortalAccount.demographic_no == demographic_no,
    )
    if account_id is not None:
        normalized_account_id = normalize_account_id(account_id)
        matched_account_id = session.scalar(
            statement.where(PatientPortalAccount.id == normalized_account_id)
        )
        if matched_account_id is None:
            raise ValueError("account_id does not match clinic_id and demographic_no")
        return matched_account_id
    return session.scalar(statement)


def derive_unlock_secret_key(encryption_secret: str) -> bytes:
    normalized_secret = encryption_secret.strip()
    if len(normalized_secret) < MIN_PRODUCTION_SECRET_LENGTH:
        raise ValueError(
            "encryption_secret must be at least "
            f"{MIN_PRODUCTION_SECRET_LENGTH} characters"
        )
    return HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=None,
        info=KEY_DERIVATION_INFO,
    ).derive(normalized_secret.encode("utf-8"))


def build_associated_data(
    *,
    clinic_id: str,
    demographic_no: int,
    secret_type: str,
    encryption_key_id: str,
) -> bytes:
    normalized_clinic_id = normalize_clinic_id(clinic_id)
    normalized_demographic_no = normalize_demographic_no(demographic_no)
    normalized_secret_type = normalize_unlock_secret_type(secret_type)
    normalized_key_id = normalize_required_text(
        encryption_key_id,
        field_name="encryption_key_id",
        max_length=MAX_UNLOCK_SECRET_KEY_ID_LENGTH,
    )
    return (
        f"{ASSOCIATED_DATA_PREFIX}|{normalized_clinic_id}|"
        f"{normalized_demographic_no}|{normalized_secret_type}|{normalized_key_id}"
    ).encode()


def validate_plaintext_secret(secret: str) -> str:
    if not secret or not secret.strip():
        raise ValueError("secret must not be blank")
    if len(secret.encode("utf-8")) > MAX_UNLOCK_SECRET_PLAINTEXT_BYTES:
        raise ValueError(
            "secret must be "
            f"{MAX_UNLOCK_SECRET_PLAINTEXT_BYTES} bytes or fewer"
        )
    return secret


def normalize_unlock_secret_type(secret_type: str) -> str:
    normalized_secret_type = secret_type.strip().casefold()
    if normalized_secret_type not in {UNLOCK_SECRET_TYPE_EMAIL, UNLOCK_SECRET_TYPE_PDF}:
        raise ValueError("secret_type must be email or pdf")
    return normalized_secret_type


def normalize_actor_type(actor_type: str) -> str:
    normalized_actor_type = actor_type.strip().casefold()
    if normalized_actor_type not in {AUDIT_ACTOR_TYPE_PATIENT, AUDIT_ACTOR_TYPE_STAFF}:
        raise ValueError("actor_type must be patient or staff")
    return normalized_actor_type


def normalize_demographic_no(demographic_no: int) -> int:
    if demographic_no <= 0:
        raise ValueError("demographic_no must be positive")
    return demographic_no


def normalize_account_id(account_id: int) -> int:
    if account_id <= 0:
        raise ValueError("account_id must be positive")
    return account_id


def normalize_list_limit(limit: int) -> int:
    if not 1 <= limit <= MAX_UNLOCK_SECRET_LIST_LIMIT:
        raise ValueError(f"limit must be between 1 and {MAX_UNLOCK_SECRET_LIST_LIMIT}")
    return limit


def normalize_list_offset(offset: int) -> int:
    if offset < 0:
        raise ValueError("offset must not be negative")
    return offset


def escape_like_pattern(value: str) -> str:
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def normalize_required_text(value: str, *, field_name: str, max_length: int) -> str:
    normalized_value = value.strip()
    if not normalized_value:
        raise ValueError(f"{field_name} must not be blank")
    if len(normalized_value) > max_length:
        raise ValueError(f"{field_name} must be {max_length} characters or fewer")
    return normalized_value


def normalize_optional_text(
    value: str | None,
    *,
    field_name: str,
    max_length: int,
) -> str | None:
    if value is None:
        return None
    normalized_value = value.strip()
    if not normalized_value:
        return None
    if len(normalized_value) > max_length:
        raise ValueError(f"{field_name} must be {max_length} characters or fewer")
    return normalized_value
