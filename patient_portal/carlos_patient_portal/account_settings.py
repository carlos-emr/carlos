import logging
from dataclasses import dataclass
from datetime import datetime, timedelta
from secrets import compare_digest, token_urlsafe

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.auth import (
    AUTH_REASON_PASSWORD_FAILURES,
    AuthPolicy,
    MfaDeliveryUnavailableError,
    PasswordHashUnusableError,
    cancel_pending_mfa_challenges,
    create_mfa_code,
    create_patient_session,
    ensure_mfa_delivery_available,
    hash_auth_token,
    is_past,
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
    AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_CONFIRM,
    AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_REQUEST,
    AUDIT_EVENT_ACCOUNT_MFA_UPDATE,
    AUDIT_EVENT_ACCOUNT_PASSWORD_CHANGE,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    CONTACT_REVIEW_DECISION_APPROVED,
    CONTACT_REVIEW_DECISION_REJECTED,
    CONTACT_REVIEW_DECISION_SUPERSEDED,
    CONTACT_REVIEW_STATUS_PENDING,
    CONTACT_REVIEW_STATUS_REVIEWED,
    EMAIL_CHANGE_STATUS_CONFIRMED,
    EMAIL_CHANGE_STATUS_PENDING,
    EMAIL_CHANGE_STATUS_REVOKED,
    MFA_DELIVERY_METHOD_SMS,
    SESSION_REVOKED_REASON_PASSWORD_CHANGE,
    PatientPortalAccount,
    PatientPortalContactReviewRequest,
    PatientPortalEmailChangeRequest,
    PatientPortalSession,
    utc_now,
)

logger = logging.getLogger(__name__)

ACCOUNT_SETTINGS_REASON_DELIVERY_UNAVAILABLE = "delivery_unavailable"
ACCOUNT_SETTINGS_REASON_NO_CHANGE = "no_change"
ACCOUNT_SETTINGS_REASON_PASSWORD_HASH_UNUSABLE = "password_hash_unusable"
ACCOUNT_SETTINGS_REASON_STEP_UP_FAILED = "step_up_failed"
ACCOUNT_SETTINGS_REASON_UPDATED = "updated"


CONTACT_UPDATE_OUTCOME_NO_CHANGE = "no_change"
CONTACT_UPDATE_OUTCOME_CONFIRMATION_REQUIRED = "confirmation_required"
CONTACT_UPDATE_OUTCOME_UPDATED = "updated"
ACCOUNT_SETTINGS_REASON_EMAIL_CONFIRMATION_REQUESTED = "email_confirmation_requested"
ACCOUNT_SETTINGS_REASON_CONTACT_CONFIRMATION_REQUESTED = "contact_confirmation_requested"


@dataclass(frozen=True)
class ContactUpdateResult:
    """What a contact-change submission did, and who now has to be told.

    Email and phone destinations are independently proven. The account keeps its current contact
    and factors until every changed destination has confirmed ownership.
    """

    outcome: str
    review_request: PatientPortalContactReviewRequest | None = None
    email_change_request: PatientPortalEmailChangeRequest | None = None
    confirmation_token: str | None = None
    confirmation_recipient: str | None = None
    phone_confirmation_code: str | None = None
    phone_confirmation_recipient: str | None = None
    notice_recipients: tuple[str, ...] = ()


@dataclass(frozen=True)
class EmailChangeConfirmation:
    """A confirmed email change, with the addresses that must be notified."""

    review_request: PatientPortalContactReviewRequest | None
    notice_recipients: tuple[str, ...]

    @property
    def applied(self) -> bool:
        return self.review_request is not None


class EmailChangeTokenInvalidError(Exception):
    """Raised when a confirmation token is unknown, expired, superseded, or already used."""


class PhoneChangeCodeInvalidError(Exception):
    """Raised for an invalid, expired, or exhausted phone enrollment code."""


class PhoneChangeRateLimitedError(Exception):
    def __init__(self, retry_after_seconds: int) -> None:
        super().__init__("phone confirmation was sent recently")
        self.retry_after_seconds = retry_after_seconds


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
    email_change_token_secret: str,
    email_change_token_ttl: timedelta,
    phone_change_code_ttl: timedelta,
) -> ContactUpdateResult:
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
        return ContactUpdateResult(outcome=CONTACT_UPDATE_OUTCOME_NO_CHANGE)

    # Every remaining case is a change to at least one destination: the equality check above
    # returned for the no-change case. Both destinations now prove ownership before anything moves,
    # so there is no immediate-apply path left to fall through to.
    return request_email_change(
        session,
        account,
        new_email=normalized_email,
        new_phone_number=normalized_phone_number,
        token_secret=email_change_token_secret,
        token_ttl=email_change_token_ttl,
        phone_code_ttl=phone_change_code_ttl,
        now=utc_now(),
    )


def request_email_change(
    session: Session,
    account: PatientPortalAccount,
    *,
    new_email: str,
    new_phone_number: str | None,
    token_secret: str,
    token_ttl: timedelta,
    phone_code_ttl: timedelta,
    now: datetime,
) -> ContactUpdateResult:
    """Record a proposed contact change and mint a one-time confirmation token.

    The account is not modified. Its email, phone, MFA destination, and reset destination all stay
    exactly as they were, so a mistyped address strands nothing and an attacker who has the
    password but not the mailbox gains nothing from submitting this form.
    """
    superseded_request = session.scalar(
        select(PatientPortalEmailChangeRequest)
        .where(
            PatientPortalEmailChangeRequest.account_id == account.id,
            PatientPortalEmailChangeRequest.status == EMAIL_CHANGE_STATUS_PENDING,
        )
        .with_for_update()
    )
    if superseded_request is not None:
        # One pending request per account, enforced by a partial unique index. Revoking rather
        # than reusing means a link already sent for the old proposal stops working.
        superseded_request.status = EMAIL_CHANGE_STATUS_REVOKED

    email_changed = account.email != new_email
    phone_changed = account.phone_number != new_phone_number
    phone_confirmation_required = phone_changed and new_phone_number is not None
    confirmation_token = token_urlsafe(32)
    phone_confirmation_code = create_mfa_code() if phone_confirmation_required else None
    email_change_request = PatientPortalEmailChangeRequest(
        account_id=account.id,
        token_hash=hash_auth_token(token_secret, "email_change", confirmation_token),
        status=EMAIL_CHANGE_STATUS_PENDING,
        new_email=new_email,
        new_phone_number=new_phone_number,
        created_at=now,
        expires_at=now + max(token_ttl, phone_code_ttl),
        email_confirmed_at=None if email_changed else now,
        phone_code_hash=(
            hash_auth_token(token_secret, "phone_change", phone_confirmation_code)
            if phone_confirmation_code is not None
            else None
        ),
        phone_confirmed_at=None if phone_confirmation_required else now,
        phone_code_sent_at=now if phone_confirmation_required else None,
        phone_failed_attempts=0,
    )
    session.add(email_change_request)
    session.flush()
    record_account_settings_audit_event(
        session,
        account,
        event_type=AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_REQUEST,
        outcome=AUDIT_OUTCOME_SUCCESS,
        reason=(
            ACCOUNT_SETTINGS_REASON_EMAIL_CONFIRMATION_REQUESTED
            if email_changed
            else ACCOUNT_SETTINGS_REASON_CONTACT_CONFIRMATION_REQUESTED
        ),
    )
    if email_change_request.email_confirmed_at is not None and (
        email_change_request.phone_confirmed_at is not None
    ):
        confirmation = apply_confirmed_contact_change(
            session,
            account,
            email_change_request,
            now=now,
        )
        return ContactUpdateResult(
            outcome=CONTACT_UPDATE_OUTCOME_UPDATED,
            review_request=confirmation.review_request,
            email_change_request=email_change_request,
            notice_recipients=confirmation.notice_recipients,
        )
    return ContactUpdateResult(
        outcome=CONTACT_UPDATE_OUTCOME_CONFIRMATION_REQUIRED,
        email_change_request=email_change_request,
        confirmation_token=confirmation_token if email_changed else None,
        confirmation_recipient=new_email if email_changed else None,
        phone_confirmation_code=phone_confirmation_code,
        phone_confirmation_recipient=(
            new_phone_number if phone_confirmation_required else None
        ),
        # Only the current address is notified. The proposed address gets the confirmation link
        # instead, and telling it "your contact details changed" would be untrue until it is used.
        notice_recipients=(account.email,),
    )


def confirm_email_change(
    session: Session,
    *,
    confirmation_token: str,
    token_secret: str,
    clinic_id: str,
    token_ttl: timedelta,
) -> EmailChangeConfirmation:
    """Apply a confirmed contact change and open the CARLOS demographic-sync review.

    This is where every effect that `update_account_contact` used to have immediately happens:
    the new address becomes the account's MFA and reset destination, factors tied to the old
    destination are revoked, and staff get a review carrying a coherent before/after snapshot.
    """
    now = utc_now()
    token_hash = hash_auth_token(token_secret, "email_change", confirmation_token)
    # Scoped by the owning account's clinic, like complete_password_reset: a token belonging to
    # another clinic's runtime must simply not be found here.
    request_locator = session.execute(
        select(
            PatientPortalEmailChangeRequest.id,
            PatientPortalEmailChangeRequest.account_id,
        )
        .join(
            PatientPortalAccount,
            PatientPortalAccount.id == PatientPortalEmailChangeRequest.account_id,
        )
        .where(
            PatientPortalEmailChangeRequest.token_hash == token_hash,
            PatientPortalAccount.clinic_id == clinic_id,
        )
    ).one_or_none()
    if request_locator is None:
        raise EmailChangeTokenInvalidError()

    account = session.scalar(
        select(PatientPortalAccount)
        .where(PatientPortalAccount.id == request_locator.account_id)
        .with_for_update()
        .execution_options(populate_existing=True)
    )
    email_change_request = session.scalar(
        select(PatientPortalEmailChangeRequest)
        .where(
            PatientPortalEmailChangeRequest.id == request_locator.id,
            PatientPortalEmailChangeRequest.token_hash == token_hash,
        )
        .with_for_update()
        .execution_options(populate_existing=True)
    )
    if email_change_request is not None and email_change_request.email_confirmed_at is not None:
        # Re-opening the email link must not cancel a request that is still waiting for proof of
        # phone ownership. Treat the link as spent while leaving the other confirmation usable.
        raise EmailChangeTokenInvalidError()
    if (
        email_change_request is None
        or email_change_request.status != EMAIL_CHANGE_STATUS_PENDING
        or is_past(email_change_request.created_at + token_ttl, now)
        or is_past(email_change_request.expires_at, now)
    ):
        if (
            email_change_request is not None
            and email_change_request.status == EMAIL_CHANGE_STATUS_PENDING
        ):
            email_change_request.status = EMAIL_CHANGE_STATUS_REVOKED
        raise EmailChangeTokenInvalidError()
    if (
        account is None
        or account.status != ACCOUNT_STATUS_ACTIVE
        or account.locked_at is not None
        or account.force_password_reset
    ):
        # A locked or disabled account must not be able to have its recovery address moved by a
        # link minted before the lock.
        email_change_request.status = EMAIL_CHANGE_STATUS_REVOKED
        raise EmailChangeTokenInvalidError()
    if (
        account.preferred_mfa_method == MFA_DELIVERY_METHOD_SMS
        and email_change_request.new_phone_number is None
    ):
        email_change_request.status = EMAIL_CHANGE_STATUS_REVOKED
        record_account_settings_audit_event(
            session,
            account,
            event_type=AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_CONFIRM,
            outcome=AUDIT_OUTCOME_FAILURE,
            reason=ACCOUNT_SETTINGS_REASON_DELIVERY_UNAVAILABLE,
        )
        raise EmailChangeTokenInvalidError()

    email_change_request.email_confirmed_at = now
    return apply_confirmed_contact_change(session, account, email_change_request, now=now)


def confirm_phone_change(
    session: Session,
    account: PatientPortalAccount,
    *,
    code: str,
    token_secret: str,
    max_failed_attempts: int,
    code_ttl: timedelta,
) -> EmailChangeConfirmation:
    now = utc_now()
    account = lock_account_for_settings(session, account.id)
    request = session.scalar(
        select(PatientPortalEmailChangeRequest)
        .where(
            PatientPortalEmailChangeRequest.account_id == account.id,
            PatientPortalEmailChangeRequest.status == EMAIL_CHANGE_STATUS_PENDING,
        )
        .with_for_update()
    )
    if (
        request is None
        or request.phone_code_hash is None
        or request.phone_confirmed_at is not None
        or request.phone_code_sent_at is None
        or is_past(request.phone_code_sent_at + code_ttl, now)
        or is_past(request.expires_at, now)
    ):
        raise PhoneChangeCodeInvalidError()
    candidate_hash = hash_auth_token(token_secret, "phone_change", code)
    if not compare_digest(request.phone_code_hash, candidate_hash):
        request.phone_failed_attempts += 1
        if request.phone_failed_attempts >= max_failed_attempts:
            request.status = EMAIL_CHANGE_STATUS_REVOKED
        raise PhoneChangeCodeInvalidError()
    request.phone_confirmed_at = now
    return apply_confirmed_contact_change(session, account, request, now=now)


def resend_phone_change_code(
    session: Session,
    account: PatientPortalAccount,
    *,
    token_secret: str,
    resend_cooldown: timedelta,
) -> tuple[str, str]:
    now = utc_now()
    account = lock_account_for_settings(session, account.id)
    request = session.scalar(
        select(PatientPortalEmailChangeRequest)
        .where(
            PatientPortalEmailChangeRequest.account_id == account.id,
            PatientPortalEmailChangeRequest.status == EMAIL_CHANGE_STATUS_PENDING,
        )
        .with_for_update()
    )
    if (
        request is None
        or request.new_phone_number is None
        or request.phone_code_hash is None
        or request.phone_confirmed_at is not None
        or is_past(request.expires_at, now)
    ):
        raise PhoneChangeCodeInvalidError()
    if request.phone_code_sent_at is not None:
        retry_at = request.phone_code_sent_at + resend_cooldown
        if not is_past(retry_at, now):
            remaining = max(1, int((retry_at - now).total_seconds()))
            raise PhoneChangeRateLimitedError(remaining)
    code = create_mfa_code()
    request.phone_code_hash = hash_auth_token(token_secret, "phone_change", code)
    request.phone_code_sent_at = now
    request.phone_failed_attempts = 0
    return code, request.new_phone_number


def apply_confirmed_contact_change(
    session: Session,
    account: PatientPortalAccount,
    request: PatientPortalEmailChangeRequest,
    *,
    now: datetime,
) -> EmailChangeConfirmation:
    if request.email_confirmed_at is None or request.phone_confirmed_at is None:
        return EmailChangeConfirmation(review_request=None, notice_recipients=())

    email_before = account.email
    phone_number_before = account.phone_number
    previous_review_request = session.scalar(
        select(PatientPortalContactReviewRequest)
        .where(
            PatientPortalContactReviewRequest.account_id == account.id,
            PatientPortalContactReviewRequest.status == CONTACT_REVIEW_STATUS_PENDING,
        )
        .with_for_update()
    )
    if previous_review_request is not None:
        previous_review_request.status = CONTACT_REVIEW_STATUS_REVIEWED
        previous_review_request.review_decision = CONTACT_REVIEW_DECISION_SUPERSEDED
        previous_review_request.reviewed_at = now
        previous_review_request.reviewed_by = account.username
        previous_review_request.reviewed_by_id = str(account.id)

    account.email = request.new_email
    account.phone_number = request.new_phone_number
    account.updated_at = now
    request.status = EMAIL_CHANGE_STATUS_CONFIRMED
    request.confirmed_at = now
    cancel_pending_mfa_challenges(session, account.id, now=now)
    revoke_pending_password_reset_tokens(session, account.id)
    review_request = PatientPortalContactReviewRequest(
        account_id=account.id,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        status=CONTACT_REVIEW_STATUS_PENDING,
        revision=token_urlsafe(24),
        email_before=email_before,
        email_after=account.email,
        phone_number_before=phone_number_before,
        phone_number_after=account.phone_number,
        requested_at=now,
    )
    session.add(review_request)
    session.flush()
    record_account_settings_audit_event(
        session,
        account,
        event_type=(
            AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_CONFIRM
            if email_before != account.email
            else AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE
        ),
        outcome=AUDIT_OUTCOME_SUCCESS,
        reason=ACCOUNT_SETTINGS_REASON_UPDATED,
    )
    return EmailChangeConfirmation(
        review_request=review_request,
        notice_recipients=tuple(dict.fromkeys((email_before, account.email))),
    )


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
    method_changed = account.preferred_mfa_method != normalized_method
    account.preferred_mfa_method = normalized_method
    account.updated_at = now
    # A patient switching away from a compromised mailbox/number expects the old channel to stop
    # authorizing sign-ins, so codes already delivered there are cancelled with the preference.
    if method_changed:
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
    """Record the CARLOS chart-sync decision without undoing verified portal contact.

    Rejection means staff determined the proposed CARLOS demographic sync should not be applied.
    Suspected takeover is a separate security action: staff must disable portal access through the
    `portal.account.manage` endpoint, which immediately revokes sessions and recovery artifacts.
    """
    review_account_id = (
        select(PatientPortalContactReviewRequest.account_id)
        .where(
            PatientPortalContactReviewRequest.id == review_request_id,
            PatientPortalContactReviewRequest.clinic_id == clinic_id,
        )
        .scalar_subquery()
    )
    account = session.scalar(
        select(PatientPortalAccount)
        .where(
            PatientPortalAccount.id == review_account_id,
            PatientPortalAccount.clinic_id == clinic_id,
        )
        .with_for_update()
    )
    if account is None:
        raise ContactReviewNotFoundError()
    review_request = session.scalar(
        select(PatientPortalContactReviewRequest)
        .where(
            PatientPortalContactReviewRequest.id == review_request_id,
            PatientPortalContactReviewRequest.clinic_id == clinic_id,
            PatientPortalContactReviewRequest.account_id == account.id,
            PatientPortalContactReviewRequest.demographic_no == account.demographic_no,
        )
        .with_for_update()
        .execution_options(populate_existing=True)
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
        # Same reasoning as auth.verify_account_password: the 503 this raises would roll the audit
        # row back with it. Deliberately the caller's session rather than a fresh one — nothing
        # else is pending on this branch, and taking a second connection to write one row would
        # add a pool acquisition to a path that only runs when the database is already suspect.
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
