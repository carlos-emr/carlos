from datetime import timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from carlos_patient_portal.account_settings import (
    EmailChangeTokenInvalidError,
    PhoneChangeCodeInvalidError,
    confirm_email_change,
    confirm_phone_change,
    update_account_contact,
)
from carlos_patient_portal.models import (
    EMAIL_CHANGE_STATUS_CONFIRMED,
    EMAIL_CHANGE_STATUS_PENDING,
    PatientPortalAccount,
    PatientPortalContactReviewRequest,
    PatientPortalEmailChangeRequest,
)
from tests.support import (
    STRONG_PASSWORD,
    activate_seeded_patient_account,
    migrated_development_app,
)

CONTACT_TOKEN_SECRET = "contact-confirmation-test-key-0001"


def test_phone_only_change_requires_code_and_budgets_failed_attempts() -> None:
    app = migrated_development_app()
    account_id = activate_seeded_patient_account(app, TestClient(app))
    with app.state.session_factory() as session:
        with session.begin():
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            result = update_account_contact(
                session,
                account,
                current_password=STRONG_PASSWORD,
                email=account.email,
                phone_number="+1 555 010 4040",
                max_failed_password_attempts=10,
                email_change_token_secret=CONTACT_TOKEN_SECRET,
                email_change_token_ttl=timedelta(days=1),
                phone_change_code_ttl=timedelta(minutes=10),
            )
            assert result.phone_confirmation_code is not None
            code = result.phone_confirmation_code

    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        request = session.scalar(select(PatientPortalEmailChangeRequest))
        assert account is not None
        assert request is not None
        assert account.phone_number is None
        assert request.phone_code_hash != code
        with pytest.raises(PhoneChangeCodeInvalidError):
            confirm_phone_change(
                session,
                account,
                code="000000",
                token_secret=CONTACT_TOKEN_SECRET,
                max_failed_attempts=10,
                code_ttl=timedelta(minutes=10),
            )
        session.commit()

    with app.state.session_factory() as session:
        with session.begin():
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            confirmation = confirm_phone_change(
                session,
                account,
                code=code,
                token_secret=CONTACT_TOKEN_SECRET,
                max_failed_attempts=10,
                code_ttl=timedelta(minutes=10),
            )
            assert confirmation.applied

    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        request = session.scalar(select(PatientPortalEmailChangeRequest))
        review = session.scalar(select(PatientPortalContactReviewRequest))
        assert account is not None
        assert request is not None
        assert account.phone_number == "+15550104040"
        assert request.phone_failed_attempts == 1
        assert request.status == EMAIL_CHANGE_STATUS_CONFIRMED
        assert review is not None


def test_reopening_confirmed_email_link_does_not_cancel_pending_phone_proof() -> None:
    app = migrated_development_app()
    account_id = activate_seeded_patient_account(app, TestClient(app))
    with app.state.session_factory() as session:
        with session.begin():
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            result = update_account_contact(
                session,
                account,
                current_password=STRONG_PASSWORD,
                email="new.patient@example.com",
                phone_number="+1 555 010 5050",
                max_failed_password_attempts=10,
                email_change_token_secret=CONTACT_TOKEN_SECRET,
                email_change_token_ttl=timedelta(days=1),
                phone_change_code_ttl=timedelta(minutes=10),
            )
            assert result.confirmation_token is not None
            assert result.phone_confirmation_code is not None
            email_token = result.confirmation_token
            phone_code = result.phone_confirmation_code
            confirmation = confirm_email_change(
                session,
                confirmation_token=email_token,
                token_secret=CONTACT_TOKEN_SECRET,
                clinic_id=account.clinic_id,
            )
            assert not confirmation.applied

    with app.state.session_factory() as session:
        with pytest.raises(EmailChangeTokenInvalidError):
            confirm_email_change(
                session,
                confirmation_token=email_token,
                token_secret=CONTACT_TOKEN_SECRET,
                clinic_id="default",
            )
        session.commit()

    with app.state.session_factory() as session:
        with session.begin():
            account = session.get(PatientPortalAccount, account_id)
            request = session.scalar(select(PatientPortalEmailChangeRequest))
            assert account is not None
            assert request is not None
            assert request.status == EMAIL_CHANGE_STATUS_PENDING
            confirmation = confirm_phone_change(
                session,
                account,
                code=phone_code,
                token_secret=CONTACT_TOKEN_SECRET,
                max_failed_attempts=10,
                code_ttl=timedelta(minutes=10),
            )
            assert confirmation.applied
