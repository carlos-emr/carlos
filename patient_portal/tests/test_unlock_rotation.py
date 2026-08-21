import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from carlos_patient_portal.models import PatientPortalUnlockSecret
from carlos_patient_portal.unlock_secrets import (
    UnlockSecretDecryptionError,
    create_unlock_secret,
    decrypt_unlock_secret_payload,
    reencrypt_unlock_secrets,
)
from tests.support import upgrade_to_head


def test_unlock_secret_keyring_rotation_preserves_plaintext() -> None:
    engine = create_engine(
        "sqlite+pysqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    upgrade_to_head(engine)
    old_secret = "o" * 32
    new_secret = "n" * 32
    with Session(engine) as session:
        with session.begin():
            created = create_unlock_secret(
                session,
                clinic_id="clinic-a",
                demographic_no=1234,
                created_by="CarlosDoc",
                created_by_id="provider-42",
                encryption_secret=old_secret,
                encryption_key_id="2026-01",
                secret="CarePlan2026!!",
                source_reference="rotation-test",
            )
            record_id = created.unlock_secret.id

        with session.begin():
            rotated = reencrypt_unlock_secrets(
                session,
                encryption_keys={"2026-01": old_secret, "2026-07": new_secret},
                active_key_id="2026-07",
            )
        record = session.scalar(
            select(PatientPortalUnlockSecret).where(
                PatientPortalUnlockSecret.id == record_id
            )
        )
        assert record is not None
        assert rotated == 1
        assert record.encryption_key_id == "2026-07"
        assert (
            decrypt_unlock_secret_payload(
                record,
                encryption_keys={"2026-07": new_secret},
            )
            == "CarePlan2026!!"
        )


def _rotation_session() -> Session:
    engine = create_engine(
        "sqlite+pysqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    upgrade_to_head(engine)
    return Session(engine)


def _create(session: Session, *, secret: str, key_id: str, reference: str, plaintext: str) -> int:
    with session.begin():
        created = create_unlock_secret(
            session,
            clinic_id="clinic-a",
            demographic_no=1234,
            created_by="CarlosDoc",
            created_by_id="provider-42",
            encryption_secret=secret,
            encryption_key_id=key_id,
            secret=plaintext,
            source_reference=reference,
        )
        return created.unlock_secret.id


def test_a_record_on_a_retired_key_still_decrypts_under_the_keyring() -> None:
    """The property rotation exists to provide, and the one the suite never asserted.

    The original test decrypted the *re-encrypted* record with the *new* key, which only shows
    that re-encryption wrote something readable. It never showed that data still sitting on the
    old key remains readable - the failure mode being guarded against is permanently
    unrecoverable patient credentials.
    """
    old_secret, new_secret = "o" * 32, "n" * 32
    with _rotation_session() as session:
        record_id = _create(
            session,
            secret=old_secret,
            key_id="2026-01",
            reference="not-yet-rotated",
            plaintext="CarePlan2026!!",
        )
        record = session.get(PatientPortalUnlockSecret, record_id)

        assert record.encryption_key_id == "2026-01"
        assert (
            decrypt_unlock_secret_payload(
                record,
                encryption_keys={"2026-01": old_secret, "2026-07": new_secret},
            )
            == "CarePlan2026!!"
        )

        # And a keyring that has dropped the retired key fails loudly rather than silently.
        with pytest.raises(UnlockSecretDecryptionError):
            decrypt_unlock_secret_payload(record, encryption_keys={"2026-07": new_secret})


def test_new_writes_use_the_active_key_not_primary() -> None:
    """No test ever configured a non-"primary" active key for a *write*."""
    with _rotation_session() as session:
        record_id = _create(
            session,
            secret="a" * 32,
            key_id="2026-07",
            reference="written-after-rotation",
            plaintext="NewSecret2026!",
        )
        record = session.get(PatientPortalUnlockSecret, record_id)

        assert record.encryption_key_id == "2026-07"
        assert record.encryption_key_id != "primary"


def test_rotation_leaves_records_already_on_the_active_key_untouched() -> None:
    """A rotation pass must be idempotent and must not churn ciphertext it does not need to."""
    old_secret, new_secret = "o" * 32, "n" * 32
    keyring = {"2026-01": old_secret, "2026-07": new_secret}
    with _rotation_session() as session:
        stale_id = _create(
            session, secret=old_secret, key_id="2026-01", reference="stale", plaintext="Stale1!"
        )
        current_id = _create(
            session, secret=new_secret, key_id="2026-07", reference="current", plaintext="Curr1!"
        )
        untouched_before = session.get(PatientPortalUnlockSecret, current_id).encrypted_secret
        # The read above opened an implicit transaction; close it before begin().
        session.rollback()

        with session.begin():
            first_pass = reencrypt_unlock_secrets(
                session, encryption_keys=keyring, active_key_id="2026-07"
            )
        session.rollback()
        with session.begin():
            second_pass = reencrypt_unlock_secrets(
                session, encryption_keys=keyring, active_key_id="2026-07"
            )

        assert first_pass == 1, "only the stale record needed re-encryption"
        assert second_pass == 0, "a second pass must be a no-op"
        assert session.get(PatientPortalUnlockSecret, current_id).encrypted_secret == (
            untouched_before
        )
        assert (
            decrypt_unlock_secret_payload(
                session.get(PatientPortalUnlockSecret, stale_id),
                encryption_keys={"2026-07": new_secret},
            )
            == "Stale1!"
        )


def test_rotation_refuses_a_keyring_without_its_active_key() -> None:
    """The active-key-absent branch was untested; existing failures took the InvalidTag path."""
    with _rotation_session() as session:
        _create(
            session, secret="o" * 32, key_id="2026-01", reference="any", plaintext="Any1!"
        )
        with pytest.raises(ValueError, match="active unlock-secret encryption key is unavailable"):
            with session.begin():
                reencrypt_unlock_secrets(
                    session,
                    encryption_keys={"2026-01": "o" * 32},
                    active_key_id="2026-07",
                )
