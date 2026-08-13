import logging
from secrets import token_urlsafe

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.auth import (
    AUTH_REASON_PASSWORD_FAILURES,
    AuthPolicy,
    MfaDeliveryUnavailableError,
    PasswordHashUnusableError,
    cancel_pending_mfa_challenges,
    create_patient_session,
    ensure_mfa_delivery_available,
    lock_account,
    normalize_mfa_delivery_method,
    normalize_phone_number,
    password_matches,
    revoke_account_sessions,
    revoke_pending_password_reset_tokens,
)
from carlos_patient_portal.credentials import hash_password, validate_password
from carlos_patient_portal.identity import normalize_email
from carlos_patient_portal.models import (
    ACCOUNT_STATUS_ACTIVE,
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_ACTOR_TYPE_STAFF,
    AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
    AUDIT_EVENT_ACCOUNT_MFA_UPDATE,
    AUDIT_EVENT_ACCOUNT_PASSWORD_CHANGE,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    CONTACT_REVIEW_DECISION_APPROVED,
    CONTACT_REVIEW_DECISION_REJECTED,
    CONTACT_REVIEW_DECISION_SUPERSEDED,
    CONTACT_REVIEW_STATUS_PENDING,
    CONTACT_REVIEW_STATUS_REVIEWED,
    MFA_DELIVERY_METHOD_SMS,
    SESSION_REVOKED_REASON_PASSWORD_CHANGE,
    PatientPortalAccount,
    PatientPortalContactReviewRequest,
    PatientPortalSession,
    utc_now,
)

logger = logging.getLogger(__name__)

ACCOUNT_SETTINGS_REASON_DELIVERY_UNAVAILABLE = "delivery_unavailable"
ACCOUNT_SETTINGS_REASON_NO_CHANGE = "no_change"
ACCOUNT_SETTINGS_REASON_PASSWORD_HASH_UNUSABLE = "password_hash_unusable"
ACCOUNT_SETTINGS_REASON_STEP_UP_FAILED = "step_up_failed"
ACCOUNT_SETTINGS_REASON_UPDATED = "updated"


class AccountSettingsStepUpError(Exception):
    """Raised when a sensitive account setting change fails current-password verification."""


class AccountSettingsValidationError(Exception):
    """Raised when an account setting value would leave the account unusable."""


class ContactReviewNotFoundError(Exception):
    """Raised when a staff contact review is missing or no longer pending."""


class ContactReviewConflictError(Exception):
    """Raised when a staff decision does not match the reviewed revision."""


def change_account_password(
    session: Session,
    account: PatientPortalAccount,
    portal_session: PatientPortalSession,
    *,
    current_password: str,
    new_password: str,
    max_failed_password_attempts: int,
    policy: AuthPolicy,
    session_token_secret: str,
) -> str:
    validate_password(new_password)
    account = lock_account_for_settings(session, account.id)
    if portal_session.account_id != account.id or portal_session.revoked_at is not None:
        raise AccountSettingsStepUpError()
    verify_current_password(
        session,
        account,
        current_password=current_password,
        event_type=AUDIT_EVENT_ACCOUNT_PASSWORD_CHANGE,
        max_failed_password_attempts=max_failed_password_attempts,
    )
    now = utc_now()
    account.password_hash = hash_password(new_password)
    account.password_updated_at = now
    account.failed_login_count = 0
    account.failed_mfa_count = 0
    account.updated_at = now
    revoke_account_sessions(
        session,
        account.id,
        reason=SESSION_REVOKED_REASON_PASSWORD_CHANGE,
        now=now,
    )
    replacement_session_token = create_patient_session(
        session,
        account,
        policy=policy,
        session_token_secret=session_token_secret,
        now=now,
    )
    cancel_pending_mfa_challenges(session, account.id, now=now)
    # A reset link issued before this change must not be able to override the new password;
    # lock_account, staff disable, and contact update all revoke here too.
    revoke_pending_password_reset_tokens(session, account.id)
    record_account_settings_audit_event(
        session,
        account,
        event_type=AUDIT_EVENT_ACCOUNT_PASSWORD_CHANGE,
        outcome=AUDIT_OUTCOME_SUCCESS,
        reason=ACCOUNT_SETTINGS_REASON_UPDATED,
    )
    return replacement_session_token


def update_account_contact(
    session: Session,
    account: PatientPortalAccount,
    *,
    current_password: str,
    email: str,
    phone_number: str | None,
    max_failed_password_attempts: int,
) -> PatientPortalContactReviewRequest | None:
    normalized_email = normalize_email(email)
    normalized_phone_number = normalize_phone_number(phone_number)
    account = lock_account_for_settings(session, account.id)
    verify_current_password(
        session,
        account,
        current_password=current_password,
        event_type=AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
        max_failed_password_attempts=max_failed_password_attempts,
    )
    if account.preferred_mfa_method == MFA_DELIVERY_METHOD_SMS and normalized_phone_number is None:
        record_account_settings_audit_event(
            session,
            account,
            event_type=AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
            outcome=AUDIT_OUTCOME_FAILURE,
            reason=ACCOUNT_SETTINGS_REASON_DELIVERY_UNAVAILABLE,
        )
        raise AccountSettingsValidationError()

    if account.email == normalized_email and account.phone_number == normalized_phone_number:
        record_account_settings_audit_event(
            session,
            account,
            event_type=AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason=ACCOUNT_SETTINGS_REASON_NO_CHANGE,
        )
        return None

    now = utc_now()
    previous_review_request = session.scalar(
        select(PatientPortalContactReviewRequest)
        .where(
            PatientPortalContactReviewRequest.account_id == account.id,
            PatientPortalContactReviewRequest.status == CONTACT_REVIEW_STATUS_PENDING,
        )
        .with_for_update()
    )
    email_before = account.email
    phone_number_before = account.phone_number
    if previous_review_request is not None:
        previous_review_request.status = CONTACT_REVIEW_STATUS_REVIEWED
        previous_review_request.review_decision = CONTACT_REVIEW_DECISION_SUPERSEDED
        previous_review_request.reviewed_at = now
        previous_review_request.reviewed_by = account.username
        previous_review_request.reviewed_by_id = str(account.id)

    account.email = normalized_email
    account.phone_number = normalized_phone_number
    account.updated_at = now
    cancel_pending_mfa_challenges(session, account.id, now=now)
    revoke_pending_password_reset_tokens(session, account.id)
    review_request = PatientPortalContactReviewRequest(
        account_id=account.id,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        status=CONTACT_REVIEW_STATUS_PENDING,
        revision=token_urlsafe(24),
        email_before=email_before,
        email_after=normalized_email,
        phone_number_before=phone_number_before,
        phone_number_after=normalized_phone_number,
        requested_at=now,
    )
    session.add(review_request)
    session.flush()
    record_account_settings_audit_event(
        session,
        account,
        event_type=AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
        outcome=AUDIT_OUTCOME_SUCCESS,
        reason=ACCOUNT_SETTINGS_REASON_UPDATED,
    )
    return review_request


def update_account_mfa_method(
    session: Session,
    account: PatientPortalAccount,
    *,
    current_password: str,
    preferred_mfa_method: str,
    max_failed_password_attempts: int,
) -> None:
    normalized_method = normalize_mfa_delivery_method(preferred_mfa_method)
    account = lock_account_for_settings(session, account.id)
    verify_current_password(
        session,
        account,
        current_password=current_password,
        event_type=AUDIT_EVENT_ACCOUNT_MFA_UPDATE,
        max_failed_password_attempts=max_failed_password_attempts,
    )
    try:
        ensure_mfa_delivery_available(account, normalized_method)
    except MfaDeliveryUnavailableError as exc:
        record_account_settings_audit_event(
            session,
            account,
            event_type=AUDIT_EVENT_ACCOUNT_MFA_UPDATE,
            outcome=AUDIT_OUTCOME_FAILURE,
            reason=ACCOUNT_SETTINGS_REASON_DELIVERY_UNAVAILABLE,
        )
        raise AccountSettingsValidationError() from exc

    now = utc_now()
    account.preferred_mfa_method = normalized_method
    account.updated_at = now
    # A patient switching away from a compromised mailbox/number expects the old channel to stop
    # authorizing sign-ins, so codes already delivered there are cancelled with the preference.
    cancel_pending_mfa_challenges(session, account.id, now=now)
    record_account_settings_audit_event(
        session,
        account,
        event_type=AUDIT_EVENT_ACCOUNT_MFA_UPDATE,
        outcome=AUDIT_OUTCOME_SUCCESS,
        reason=normalized_method,
    )


def list_pending_contact_reviews(
    session: Session,
    *,
    clinic_id: str,
    limit: int = 100,
    offset: int = 0,
) -> list[PatientPortalContactReviewRequest]:
    if limit < 1 or limit > 100:
        raise ValueError("limit must be between 1 and 100")
    if offset < 0 or offset > 100_000:
        raise ValueError("offset must be between 0 and 100000")
    return list(
        session.scalars(
            select(PatientPortalContactReviewRequest)
            .where(
                PatientPortalContactReviewRequest.clinic_id == clinic_id,
                PatientPortalContactReviewRequest.status == CONTACT_REVIEW_STATUS_PENDING,
            )
            .order_by(
                PatientPortalContactReviewRequest.requested_at,
                PatientPortalContactReviewRequest.id,
            )
            .limit(limit)
            .offset(offset)
        )
    )


def count_pending_contact_reviews(session: Session, *, clinic_id: str) -> int:
    return int(
        session.scalar(
            select(func.count(PatientPortalContactReviewRequest.id)).where(
                PatientPortalContactReviewRequest.clinic_id == clinic_id,
                PatientPortalContactReviewRequest.status == CONTACT_REVIEW_STATUS_PENDING,
            )
        )
        or 0
    )


def review_contact_update(
    session: Session,
    review_request_id: int,
    *,
    clinic_id: str,
    reviewer: str,
    reviewer_id: str,
    approve: bool,
    expected_revision: str,
) -> PatientPortalContactReviewRequest:
    review_request = session.scalar(
        select(PatientPortalContactReviewRequest)
        .where(
            PatientPortalContactReviewRequest.id == review_request_id,
            PatientPortalContactReviewRequest.clinic_id == clinic_id,
        )
        .with_for_update()
    )
    if review_request is None:
        raise ContactReviewNotFoundError()
    decision = CONTACT_REVIEW_DECISION_APPROVED if approve else CONTACT_REVIEW_DECISION_REJECTED
    if review_request.revision != expected_revision:
        raise ContactReviewConflictError()
    if review_request.status == CONTACT_REVIEW_STATUS_REVIEWED:
        if review_request.review_decision == decision:
            return review_request
        raise ContactReviewConflictError()
    account = session.scalar(
        select(PatientPortalAccount)
        .where(
            PatientPortalAccount.id == review_request.account_id,
            PatientPortalAccount.clinic_id == clinic_id,
            PatientPortalAccount.demographic_no == review_request.demographic_no,
        )
        .with_for_update()
    )
    if account is None:
        raise ContactReviewNotFoundError()

    now = utc_now()
    review_request.status = CONTACT_REVIEW_STATUS_REVIEWED
    review_request.review_decision = decision
    review_request.reviewed_at = now
    review_request.reviewed_by = reviewer
    review_request.reviewed_by_id = reviewer_id
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_STAFF,
        actor=reviewer,
        actor_id=reviewer_id,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        resource_type="contact_review",
        resource_id=str(review_request.id),
        reason=decision,
    )
    return review_request


def verify_current_password(
    session: Session,
    account: PatientPortalAccount,
    *,
    current_password: str,
    event_type: str,
    max_failed_password_attempts: int,
) -> None:
    if max_failed_password_attempts <= 0:
        raise ValueError("max_failed_password_attempts must be positive")
    try:
        step_up_succeeded = password_matches(account.password_hash, current_password)
    except PasswordHashUnusableError:
        # An unreadable hash is a server fault. Charging it to the patient here would lock them
        # out of the very screen they would use to set a working password.
        logger.error("Stored password hash is unusable; step-up cannot be evaluated")
        record_account_settings_audit_event(
            session,
            account,
            event_type=event_type,
            outcome=AUDIT_OUTCOME_FAILURE,
            reason=ACCOUNT_SETTINGS_REASON_PASSWORD_HASH_UNUSABLE,
        )
        session.commit()
        raise
    if step_up_succeeded:
        account.failed_login_count = 0
        return
    now = utc_now()
    account.failed_login_count += 1
    account.updated_at = now
    if account.failed_login_count >= max_failed_password_attempts:
        lock_account(
            session,
            account,
            reason=AUTH_REASON_PASSWORD_FAILURES,
            now=now,
        )
    record_account_settings_audit_event(
        session,
        account,
        event_type=event_type,
        outcome=AUDIT_OUTCOME_FAILURE,
        reason=ACCOUNT_SETTINGS_REASON_STEP_UP_FAILED,
    )
    raise AccountSettingsStepUpError()


def lock_account_for_settings(
    session: Session,
    account_id: int,
) -> PatientPortalAccount:
    account = session.scalar(
        select(PatientPortalAccount)
        .where(PatientPortalAccount.id == account_id)
        .with_for_update()
        .execution_options(populate_existing=True)
    )
    if (
        account is None
        or account.status != ACCOUNT_STATUS_ACTIVE
        or account.locked_at is not None
        or account.force_password_reset
    ):
        raise AccountSettingsStepUpError()
    return account


def record_account_settings_audit_event(
    session: Session,
    account: PatientPortalAccount,
    *,
    event_type: str,
    outcome: str,
    reason: str,
) -> None:
    record_audit_event(
        session,
        event_type=event_type,
        outcome=outcome,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        reason=reason,
    )
