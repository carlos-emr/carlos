import re
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import datetime
from functools import lru_cache
from pathlib import Path
from secrets import choice, randbelow, token_bytes
from uuid import uuid4

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from sqlalchemy import Select, case, func, literal, or_, select
from sqlalchemy.orm import Session

from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.config import MIN_PRODUCTION_SECRET_LENGTH
from carlos_patient_portal.invites import normalize_clinic_id
from carlos_patient_portal.models import (
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_ACTOR_TYPE_STAFF,
    AUDIT_EVENT_UNLOCK_SECRET_CREATE,
    AUDIT_EVENT_UNLOCK_SECRET_PUBLISH,
    AUDIT_EVENT_UNLOCK_SECRET_READ,
    AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    MAX_UNLOCK_SECRET_ACTOR_LENGTH,
    MAX_UNLOCK_SECRET_KEY_ID_LENGTH,
    MAX_UNLOCK_SECRET_LABEL_LENGTH,
    MAX_UNLOCK_SECRET_REVOKE_REASON_LENGTH,
    MAX_UNLOCK_SECRET_SOURCE_REFERENCE_LENGTH,
    UNLOCK_SECRET_NONCE_LENGTH,
    UNLOCK_SECRET_STATUS_ACTIVE,
    UNLOCK_SECRET_STATUS_PENDING,
    UNLOCK_SECRET_STATUS_REVOKED,
    UNLOCK_SECRET_TYPE_EMAIL,
    UNLOCK_SECRET_TYPE_PDF,
    PatientPortalAccount,
    PatientPortalUnlockSecret,
    utc_now,
)

ENCRYPTION_ALGORITHM_V1 = "AES-256-GCM-HKDF-SHA256"
ENCRYPTION_ALGORITHM = "AES-256-GCM-HKDF-SHA256-v2"
ENCRYPTION_KEY_ID_DEFAULT = "primary"
KEY_DERIVATION_INFO = b"carlos-patient-portal:unlock-secret:v1"
ASSOCIATED_DATA_PREFIX_V1 = "carlos-patient-portal.unlock-secret.v1"
ASSOCIATED_DATA_PREFIX = "carlos-patient-portal.unlock-secret.v2"
DEFAULT_UNLOCK_SECRET_LIST_LIMIT = 10
MAX_UNLOCK_SECRET_LIST_LIMIT = 100
MAX_UNLOCK_SECRET_SEARCH_LENGTH = 128
MAX_UNLOCK_SECRET_PROVIDER_OPTIONS = 100
MAX_UNLOCK_SECRET_PLAINTEXT_BYTES = 1024
PROVIDER_FILTER_ID_PREFIX = "id:"
PROVIDER_FILTER_NAME_PREFIX = "name:"
MAX_UNLOCK_SECRET_PROVIDER_FILTER_LENGTH = MAX_UNLOCK_SECRET_ACTOR_LENGTH + len(
    PROVIDER_FILTER_NAME_PREFIX
)
UNLOCK_SECRET_WORDLIST_RESOURCE = "wordlists/patient_pdf_passphrase_english.txt"
UNLOCK_SECRET_WORDLIST_SIZE = 4096
UNLOCK_SECRET_WORD_PATTERN = re.compile(r"^[a-z]+$")
UNLOCK_SECRET_WORDLIST_ROW_PATTERN = re.compile(r"^(\d{4})\t([a-z]+)$")


class UnlockSecretNotFoundError(Exception):
    """Raised when an unlock secret does not exist in the requested scope."""


class UnlockSecretRevokedError(Exception):
    """Raised when an unlock secret was revoked before it was read."""


class UnlockSecretNotPublishedError(Exception):
    """Raised when a patient surface reads a secret CARLOS has not published yet.

    Creation and publication are separate steps: CARLOS creates the record to obtain the
    passphrase, encrypts and sends the message, and only then publishes. Patient-facing reads must
    treat a still-``pending`` record as absent so a passphrase is never disclosed for a message the
    patient has not received. Callers surface this exactly like revoked/not-found.
    """


class UnlockSecretDecryptionError(Exception):
    """Raised when a stored unlock secret cannot be decrypted with the configured key."""


@dataclass(frozen=True)
class ProviderFilterOptions:
    """Bounded provider filter choices plus whether more exist beyond the cap."""

    options: list[tuple[str, str]]
    truncated: bool


@dataclass(frozen=True)
class CreatedUnlockSecret:
    unlock_secret: PatientPortalUnlockSecret
    secret: str


@dataclass(frozen=True)
class DisclosedUnlockSecret:
    """One audited disclosure: the record that was read and the passphrase it held."""

    unlock_secret: PatientPortalUnlockSecret
    secret: str


@dataclass(frozen=True)
class EncryptedUnlockSecretPayload:
    encrypted_secret: bytes
    encryption_nonce: bytes
    encryption_context: str


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
    encryption_context: str | None = None,
) -> EncryptedUnlockSecretPayload:
    encoded_secret = validate_plaintext_secret(secret).encode("utf-8")
    nonce = token_bytes(UNLOCK_SECRET_NONCE_LENGTH)
    resolved_context = encryption_context or str(uuid4())
    aesgcm = AESGCM(derive_unlock_secret_key(encryption_secret))
    encrypted_secret = aesgcm.encrypt(
        nonce,
        encoded_secret,
        build_associated_data(
            clinic_id=clinic_id,
            demographic_no=demographic_no,
            secret_type=secret_type,
            encryption_key_id=encryption_key_id,
            encryption_context=resolved_context,
        ),
    )
    return EncryptedUnlockSecretPayload(
        encrypted_secret=encrypted_secret,
        encryption_nonce=nonce,
        encryption_context=resolved_context,
    )


def decrypt_unlock_secret_payload(
    unlock_secret: PatientPortalUnlockSecret,
    *,
    encryption_secret: str | None = None,
    encryption_keys: Mapping[str, str] | None = None,
) -> str:
    expected_algorithm = (
        ENCRYPTION_ALGORITHM_V1
        if unlock_secret.encryption_context is None
        else ENCRYPTION_ALGORITHM
    )
    if unlock_secret.encryption_algorithm != expected_algorithm:
        raise UnlockSecretDecryptionError("unlock secret encryption metadata is invalid")
    resolved_secret = encryption_secret
    if encryption_keys is not None:
        resolved_secret = encryption_keys.get(unlock_secret.encryption_key_id)
    if resolved_secret is None:
        raise UnlockSecretDecryptionError(
            f"unlock secret encryption key is unavailable: {unlock_secret.encryption_key_id}"
        )
    aesgcm = AESGCM(derive_unlock_secret_key(resolved_secret))
    try:
        decrypted_secret = aesgcm.decrypt(
            unlock_secret.encryption_nonce,
            unlock_secret.encrypted_secret,
            build_associated_data(
                clinic_id=unlock_secret.clinic_id,
                demographic_no=unlock_secret.demographic_no,
                secret_type=unlock_secret.secret_type,
                encryption_key_id=unlock_secret.encryption_key_id,
                encryption_context=unlock_secret.encryption_context,
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
    created_by_id: str | None = None,
    encryption_secret: str,
    secret_type: str = UNLOCK_SECRET_TYPE_EMAIL,
    secret: str | None = None,
    account_id: int | None = None,
    label: str | None = None,
    source_reference: str | None = None,
    encryption_key_id: str = ENCRYPTION_KEY_ID_DEFAULT,
    initial_status: str = UNLOCK_SECRET_STATUS_ACTIVE,
) -> CreatedUnlockSecret:
    normalized_clinic_id = normalize_clinic_id(clinic_id)
    normalized_demographic_no = normalize_demographic_no(demographic_no)
    normalized_secret_type = normalize_unlock_secret_type(secret_type)
    normalized_created_by = normalize_required_text(
        created_by,
        field_name="created_by",
        max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
    )
    normalized_created_by_id = normalize_optional_text(
        created_by_id,
        field_name="created_by_id",
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
    if initial_status not in {
        UNLOCK_SECRET_STATUS_ACTIVE,
        UNLOCK_SECRET_STATUS_PENDING,
    }:
        raise ValueError("initial_status must be pending or available")
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
        status=initial_status,
        label=normalized_label,
        source_reference=normalized_source_reference,
        encrypted_secret=encrypted_payload.encrypted_secret,
        encryption_nonce=encrypted_payload.encryption_nonce,
        encryption_algorithm=ENCRYPTION_ALGORITHM,
        encryption_key_id=normalized_key_id,
        encryption_context=encrypted_payload.encryption_context,
        created_by=normalized_created_by,
        created_by_id=normalized_created_by_id,
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
        actor_id=normalized_created_by_id,
        clinic_id=normalized_clinic_id,
        demographic_no=normalized_demographic_no,
        account_id=resolved_account_id,
        resource_type="unlock_secret",
        resource_id=str(unlock_secret.id),
    )
    return CreatedUnlockSecret(unlock_secret=unlock_secret, secret=plaintext_secret)


def publish_unlock_secret(
    session: Session,
    unlock_secret_id: int,
    *,
    clinic_id: str,
    demographic_no: int,
    published_by: str,
    published_by_id: str | None = None,
) -> PatientPortalUnlockSecret:
    unlock_secret = get_scoped_unlock_secret(
        session,
        unlock_secret_id,
        clinic_id=clinic_id,
        demographic_no=demographic_no,
        for_update=True,
    )
    if unlock_secret.status == UNLOCK_SECRET_STATUS_REVOKED:
        raise UnlockSecretRevokedError()
    already_published = unlock_secret.status == UNLOCK_SECRET_STATUS_ACTIVE
    if unlock_secret.status == UNLOCK_SECRET_STATUS_PENDING:
        unlock_secret.status = UNLOCK_SECRET_STATUS_ACTIVE
        unlock_secret.updated_at = utc_now()
    # CARLOS retries this call after a timeout, so a no-op republish must stay distinguishable
    # from the real publication that first made the passphrase patient-visible.
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_UNLOCK_SECRET_PUBLISH,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_STAFF,
        actor=normalize_required_text(
            published_by,
            field_name="published_by",
            max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
        ),
        actor_id=normalize_optional_text(
            published_by_id,
            field_name="published_by_id",
            max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
        ),
        clinic_id=unlock_secret.clinic_id,
        demographic_no=unlock_secret.demographic_no,
        account_id=unlock_secret.account_id,
        resource_type="unlock_secret",
        resource_id=str(unlock_secret.id),
        reason="already_published" if already_published else "published",
    )
    return unlock_secret


def read_unlock_secret(
    session: Session,
    unlock_secret_id: int,
    *,
    clinic_id: str,
    encryption_secret: str | None = None,
    encryption_keys: Mapping[str, str] | None = None,
    actor_type: str,
    actor: str,
    actor_id: str | None = None,
    account_id: int | None = None,
    audit_account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
    allow_pending: bool = False,
) -> str:
    """Disclose one passphrase, discarding the record. See `read_scoped_unlock_secret`."""
    return read_scoped_unlock_secret(
        session,
        unlock_secret_id,
        clinic_id=clinic_id,
        encryption_secret=encryption_secret,
        encryption_keys=encryption_keys,
        actor_type=actor_type,
        actor=actor,
        actor_id=actor_id,
        account_id=account_id,
        audit_account_id=audit_account_id,
        demographic_no=demographic_no,
        secret_type=secret_type,
        allow_pending=allow_pending,
    ).secret


def read_scoped_unlock_secret(
    session: Session,
    unlock_secret_id: int,
    *,
    clinic_id: str,
    encryption_secret: str | None = None,
    encryption_keys: Mapping[str, str] | None = None,
    actor_type: str,
    actor: str,
    actor_id: str | None = None,
    account_id: int | None = None,
    audit_account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
    allow_pending: bool = False,
) -> DisclosedUnlockSecret:
    """Audit and decrypt one scoped unlock secret, returning the record alongside its plaintext.

    Callers that render the record's metadata in the same response take this form; the row is
    already loaded and scope-checked here, so re-reading it would be a second query and a second
    chance for the two reads to disagree.
    """
    normalized_actor_type = normalize_actor_type(actor_type)
    normalized_actor = normalize_required_text(
        actor,
        field_name="actor",
        max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
    )
    normalized_audit_account_id = (
        normalize_account_id(audit_account_id) if audit_account_id is not None else None
    )
    normalized_actor_id = normalize_optional_text(
        actor_id,
        field_name="actor_id",
        max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
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
    # Staff/CARLOS retries legitimately re-read their own pending record; patient surfaces must not.
    if unlock_secret.status == UNLOCK_SECRET_STATUS_PENDING and not allow_pending:
        raise UnlockSecretNotPublishedError()

    try:
        plaintext_secret = decrypt_unlock_secret_payload(
            unlock_secret,
            encryption_secret=encryption_secret,
            encryption_keys=encryption_keys,
        )
    except UnlockSecretDecryptionError:
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_UNLOCK_SECRET_READ,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=normalized_actor_type,
            actor=normalized_actor,
            actor_id=normalized_actor_id,
            clinic_id=unlock_secret.clinic_id,
            demographic_no=unlock_secret.demographic_no,
            account_id=(
                normalized_audit_account_id
                if normalized_audit_account_id is not None
                else unlock_secret.account_id
            ),
            resource_type="unlock_secret",
            resource_id=str(unlock_secret.id),
            reason="decryption_failed",
        )
        raise
    now = utc_now()
    unlock_secret.last_viewed_at = now
    unlock_secret.updated_at = now
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_UNLOCK_SECRET_READ,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=normalized_actor_type,
        actor=normalized_actor,
        actor_id=normalized_actor_id,
        clinic_id=unlock_secret.clinic_id,
        demographic_no=unlock_secret.demographic_no,
        account_id=(
            normalized_audit_account_id
            if normalized_audit_account_id is not None
            else unlock_secret.account_id
        ),
        resource_type="unlock_secret",
        resource_id=str(unlock_secret.id),
    )
    return DisclosedUnlockSecret(unlock_secret=unlock_secret, secret=plaintext_secret)


def reencrypt_unlock_secrets(
    session: Session,
    *,
    encryption_keys: Mapping[str, str],
    active_key_id: str,
    limit: int = 100,
) -> int:
    active_secret = encryption_keys.get(active_key_id)
    if active_secret is None:
        raise ValueError("active unlock-secret encryption key is unavailable")
    records = list(
        session.scalars(
            select(PatientPortalUnlockSecret)
            .where(
                or_(
                    PatientPortalUnlockSecret.encryption_key_id != active_key_id,
                    PatientPortalUnlockSecret.encryption_context.is_(None),
                )
            )
            .order_by(PatientPortalUnlockSecret.id)
            .limit(normalize_list_limit(limit))
            .with_for_update()
        )
    )
    for record in records:
        plaintext = decrypt_unlock_secret_payload(record, encryption_keys=encryption_keys)
        encrypted_payload = encrypt_unlock_secret_payload(
            plaintext,
            encryption_secret=active_secret,
            clinic_id=record.clinic_id,
            demographic_no=record.demographic_no,
            secret_type=record.secret_type,
            encryption_key_id=active_key_id,
        )
        record.encrypted_secret = encrypted_payload.encrypted_secret
        record.encryption_nonce = encrypted_payload.encryption_nonce
        record.encryption_key_id = active_key_id
        record.encryption_context = encrypted_payload.encryption_context
        record.encryption_algorithm = ENCRYPTION_ALGORITHM
        record.updated_at = utc_now()
    return len(records)


def revoke_unlock_secret(
    session: Session,
    unlock_secret_id: int,
    *,
    clinic_id: str,
    revoked_by: str,
    revoked_by_id: str | None = None,
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
    normalized_revoked_by_id = normalize_optional_text(
        revoked_by_id,
        field_name="revoked_by_id",
        max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
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
        unlock_secret.revoked_by_id = normalized_revoked_by_id
        unlock_secret.revoke_reason = normalized_reason
        unlock_secret.updated_at = now
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_UNLOCK_SECRET_REVOKE,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_STAFF,
        actor=normalized_revoked_by,
        actor_id=normalized_revoked_by_id,
        clinic_id=unlock_secret.clinic_id,
        demographic_no=unlock_secret.demographic_no,
        account_id=unlock_secret.account_id,
        resource_type="unlock_secret",
        resource_id=str(unlock_secret.id),
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
        max_length=MAX_UNLOCK_SECRET_PROVIDER_FILTER_LENGTH,
    )
    if normalized_provider is not None:
        if normalized_provider.startswith(PROVIDER_FILTER_ID_PREFIX):
            provider_id = normalize_required_text(
                normalized_provider.removeprefix(PROVIDER_FILTER_ID_PREFIX),
                field_name="provider_id",
                max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
            )
            statement = statement.where(PatientPortalUnlockSecret.created_by_id == provider_id)
        elif normalized_provider.startswith(PROVIDER_FILTER_NAME_PREFIX):
            provider_name = normalize_required_text(
                normalized_provider.removeprefix(PROVIDER_FILTER_NAME_PREFIX),
                field_name="provider_name",
                max_length=MAX_UNLOCK_SECRET_ACTOR_LENGTH,
            )
            statement = statement.where(
                PatientPortalUnlockSecret.created_by_id.is_(None),
                PatientPortalUnlockSecret.created_by == provider_name,
            )
        else:
            # Preserve bookmarked filters created before stable provider values were introduced.
            statement = statement.where(PatientPortalUnlockSecret.created_by == normalized_provider)
    if created_from is not None:
        statement = statement.where(PatientPortalUnlockSecret.created_at >= created_from)
    if created_before is not None:
        statement = statement.where(PatientPortalUnlockSecret.created_at < created_before)
    if created_from is not None and created_before is not None and created_from >= created_before:
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


def list_unlock_secret_provider_identities(
    session: Session,
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
    limit: int = MAX_UNLOCK_SECRET_PROVIDER_OPTIONS,
    offset: int = 0,
) -> list[tuple[str | None, str]]:
    normalized_limit = normalize_list_limit(limit)
    normalized_offset = normalize_list_offset(offset)
    statement = (
        _unlock_secret_provider_identity_statement(
            clinic_id=clinic_id,
            account_id=account_id,
            demographic_no=demographic_no,
            secret_type=secret_type,
        )
        .limit(normalized_limit)
        .offset(normalized_offset)
    )
    return [(provider_id, display_name) for provider_id, display_name in session.execute(statement)]


def _unlock_secret_provider_identity_subquery(
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
):
    scoped_statement = scoped_unlock_secret_statement(
        clinic_id=clinic_id,
        account_id=account_id,
        demographic_no=demographic_no,
        secret_type=secret_type,
    )
    provider_identity_key = case(
        (
            PatientPortalUnlockSecret.created_by_id.is_not(None),
            literal("id:") + PatientPortalUnlockSecret.created_by_id,
        ),
        else_=literal("name:") + PatientPortalUnlockSecret.created_by,
    )
    return (
        scoped_statement.with_only_columns(
            PatientPortalUnlockSecret.created_by_id,
            PatientPortalUnlockSecret.created_by,
            func.row_number()
            .over(
                partition_by=provider_identity_key,
                order_by=(
                    PatientPortalUnlockSecret.created_at.desc(),
                    PatientPortalUnlockSecret.id.desc(),
                ),
            )
            .label("provider_rank"),
        )
        .where(PatientPortalUnlockSecret.status == UNLOCK_SECRET_STATUS_ACTIVE)
        .subquery()
    )


def _unlock_secret_provider_identity_statement(
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
):
    providers = _unlock_secret_provider_identity_subquery(
        clinic_id=clinic_id,
        account_id=account_id,
        demographic_no=demographic_no,
        secret_type=secret_type,
    )
    return (
        select(providers.c.created_by_id, providers.c.created_by)
        .where(providers.c.provider_rank == 1)
        .order_by(
            func.lower(providers.c.created_by),
            providers.c.created_by_id,
        )
    )


def list_unlock_secret_providers(
    session: Session,
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
) -> list[str]:
    return [
        display_name
        for _, display_name in session.execute(
            _unlock_secret_provider_identity_statement(
                clinic_id=clinic_id,
                account_id=account_id,
                demographic_no=demographic_no,
                secret_type=secret_type,
            )
        )
    ]


def list_unlock_secret_provider_options(
    session: Session,
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
    limit: int = MAX_UNLOCK_SECRET_PROVIDER_OPTIONS,
) -> ProviderFilterOptions:
    """Return a bounded provider filter list for the dashboard.

    Passwords are retained indefinitely, so an unbounded list would grow without limit and render
    every provider into the page. One extra row is fetched to report truncation to the UI; an
    already-selected provider outside the cap is preserved separately by the template.
    """
    normalized_limit = normalize_list_limit(limit)
    identities = list(
        session.execute(
            _unlock_secret_provider_identity_statement(
                clinic_id=clinic_id,
                account_id=account_id,
                demographic_no=demographic_no,
                secret_type=secret_type,
            ).limit(normalized_limit + 1)
        )
    )
    truncated = len(identities) > normalized_limit
    options = [
        (
            (
                f"{PROVIDER_FILTER_ID_PREFIX}{provider_id}"
                if provider_id is not None
                else f"{PROVIDER_FILTER_NAME_PREFIX}{display_name}"
            ),
            display_name,
        )
        for provider_id, display_name in identities[:normalized_limit]
    ]
    return ProviderFilterOptions(options=options, truncated=truncated)


def count_unlock_secret_providers(
    session: Session,
    *,
    clinic_id: str,
    account_id: int | None = None,
    demographic_no: int | None = None,
    secret_type: str | None = None,
) -> int:
    providers = _unlock_secret_provider_identity_subquery(
        clinic_id=clinic_id,
        account_id=account_id,
        demographic_no=demographic_no,
        secret_type=secret_type,
    )
    return int(
        session.scalar(
            select(func.count()).select_from(providers).where(providers.c.provider_rank == 1)
        )
        or 0
    )


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


def get_unlock_secret_by_source_reference(
    session: Session,
    *,
    clinic_id: str,
    demographic_no: int,
    secret_type: str,
    source_reference: str,
    for_update: bool = False,
) -> PatientPortalUnlockSecret | None:
    normalized_source_reference = normalize_required_text(
        source_reference,
        field_name="source_reference",
        max_length=MAX_UNLOCK_SECRET_SOURCE_REFERENCE_LENGTH,
    )
    statement = scoped_unlock_secret_statement(
        clinic_id=clinic_id,
        demographic_no=demographic_no,
        secret_type=secret_type,
    ).where(PatientPortalUnlockSecret.source_reference == normalized_source_reference)
    if for_update:
        statement = statement.with_for_update()
    return session.scalar(statement)


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


# Bounded by the size of a realistic keyring (active key plus retained keys mid-rotation). The
# derivation is deterministic by design, so caching only skips repeated work, never changes a
# result; the secret is already resident in process memory as a Settings field either way.
@lru_cache(maxsize=8)
def derive_unlock_secret_key(encryption_secret: str) -> bytes:
    """Derive the AES-256 key for one configured unlock-secret encryption secret.

    ``salt=None`` is required, not an oversight: every read must reproduce the key from the record's
    ``encryption_key_id`` alone, so the derivation has to be deterministic. Per-record uniqueness is
    supplied instead by the random nonce and by the record-bound associated data built in
    ``build_associated_data`` (clinic, demographic, type, key id, and a per-record context UUID).
    """
    normalized_secret = encryption_secret.strip()
    if len(normalized_secret) < MIN_PRODUCTION_SECRET_LENGTH:
        raise ValueError(
            f"encryption_secret must be at least {MIN_PRODUCTION_SECRET_LENGTH} characters"
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
    encryption_context: str | None = None,
) -> bytes:
    normalized_clinic_id = normalize_clinic_id(clinic_id)
    normalized_demographic_no = normalize_demographic_no(demographic_no)
    normalized_secret_type = normalize_unlock_secret_type(secret_type)
    normalized_key_id = normalize_required_text(
        encryption_key_id,
        field_name="encryption_key_id",
        max_length=MAX_UNLOCK_SECRET_KEY_ID_LENGTH,
    )
    if encryption_context is None:
        return (
            f"{ASSOCIATED_DATA_PREFIX_V1}|{normalized_clinic_id}|"
            f"{normalized_demographic_no}|{normalized_secret_type}|{normalized_key_id}"
        ).encode()
    normalized_context = normalize_required_text(
        encryption_context,
        field_name="encryption_context",
        max_length=36,
    )
    if len(normalized_context) != 36:
        raise UnlockSecretDecryptionError("unlock secret encryption context is invalid")
    return (
        f"{ASSOCIATED_DATA_PREFIX}|{normalized_clinic_id}|{normalized_demographic_no}|"
        f"{normalized_secret_type}|{normalized_key_id}|{normalized_context}"
    ).encode()


def validate_plaintext_secret(secret: str) -> str:
    if not secret or not secret.strip():
        raise ValueError("secret must not be blank")
    if len(secret.encode("utf-8")) > MAX_UNLOCK_SECRET_PLAINTEXT_BYTES:
        raise ValueError(f"secret must be {MAX_UNLOCK_SECRET_PLAINTEXT_BYTES} bytes or fewer")
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
    if offset < 0 or offset > 100_000:
        raise ValueError("offset must be between 0 and 100000")
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
