"""Seed the isolated development database used by the Playwright CI smoke test."""

from datetime import date, timedelta

from carlos_patient_portal.accounts import ActivationRateLimit, activate_patient_account
from carlos_patient_portal.config import get_settings
from carlos_patient_portal.database import create_portal_engine, create_session_factory
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.invites import create_invite
from carlos_patient_portal.unlock_secrets import create_unlock_secret

DEVELOPMENT_PASSWORD = "".join(("Carlos", "2026", "!!"))


def main() -> None:
    settings = get_settings()
    keyring = settings.resolved_unlock_secret_keyring
    encryption_secret = keyring[settings.unlock_secret_active_key_id]
    engine = create_portal_engine(settings.database_url)
    session_factory = create_session_factory(engine)
    try:
        with session_factory() as session:
            with session.begin():
                _, invite_token = create_invite(
                    session,
                    1234,
                    "CI seed",
                    clinic_id=settings.clinic_id,
                    actor_id="ci-seed",
                    identity_proof=IdentityProof(
                        email="example.patient@example.com",
                        date_of_birth=date(1980, 5, 20),
                        health_card_number="ABCD 1234-5678",
                    ),
                    proof_secret=settings.identity_proof_secret.get_secret_value(),
                )
                account = activate_patient_account(
                    session,
                    invite_code=invite_token,
                    identity_proof=IdentityProof(
                        email="example.patient@example.com",
                        date_of_birth=date(1980, 5, 20),
                        health_card_number="ABCD 1234-5678",
                    ),
                    username="CarlosPatient",
                    password=DEVELOPMENT_PASSWORD,
                    proof_secret=settings.identity_proof_secret.get_secret_value(),
                    client_reference_hash="0" * 64,
                    rate_limit=ActivationRateLimit(
                        failure_window=timedelta(hours=1),
                        max_failures_per_invite=10,
                        max_failures_per_client=50,
                    ),
                    expected_clinic_id=settings.clinic_id,
                )
                for index, label in enumerate(
                    (
                        "Care plan password",
                        "Referral package password",
                        "Lab results password",
                        "Imaging report password",
                        "Consultation note password",
                        "Medication summary password",
                        "Discharge summary password",
                        "Specialist letter password",
                        "Appointment package password",
                        "Insurance form password",
                        "Vaccination record password",
                        "Treatment plan password",
                    ),
                    start=1,
                ):
                    create_unlock_secret(
                        session,
                        clinic_id=settings.clinic_id,
                        demographic_no=account.demographic_no,
                        account_id=account.id,
                        created_by="CarlosDoc",
                        created_by_id="provider-42",
                        encryption_secret=encryption_secret,
                        encryption_key_id=settings.unlock_secret_active_key_id,
                        label=label,
                        source_reference=f"ci-message-{index}",
                    )
    finally:
        engine.dispose()


if __name__ == "__main__":
    main()
