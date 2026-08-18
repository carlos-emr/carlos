from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from carlos_patient_portal.models import PatientPortalUnlockSecret
from carlos_patient_portal.unlock_secrets import (
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
