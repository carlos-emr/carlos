from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from hashlib import sha256
from hmac import compare_digest
from hmac import new as new_hmac
from math import ceil
from secrets import randbelow, token_urlsafe

from argon2.exceptions import InvalidHashError, VerificationError, VerifyMismatchError
from sqlalchemy import select
from sqlalchemy.orm import Session

from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.credentials import password_hasher, validate_password, validate_username
from carlos_patient_portal.identity import normalize_email
from carlos_patient_portal.invites import normalize_clinic_id, normalize_staff_actor
from carlos_patient_portal.models import (
    ACCOUNT_STATUS_ACTIVE,
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_ACTOR_TYPE_STAFF,
    AUDIT_EVENT_ACCOUNT_LOCK,
    AUDIT_EVENT_ACCOUNT_UNLOCK,
    AUDIT_EVENT_LOGIN,
    AUDIT_EVENT_MFA_CHALLENGE,
    AUDIT_EVENT_MFA_DELIVERY,
    AUDIT_EVENT_MFA_RESEND,
    AUDIT_EVENT_MFA_VERIFY,
    AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
    AUDIT_EVENT_PASSWORD_RESET_REQUEST,
    AUDIT_EVENT_SESSION_LOGOUT,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    AUDIT_OUTCOME_THROTTLED,
    MAX_PHONE_NUMBER_LENGTH,
    MFA_CHALLENGE_STATUS_CANCELLED,
    MFA_CHALLENGE_STATUS_PENDING,
    MFA_CHALLENGE_STATUS_VERIFIED,
    MFA_DELIVERY_METHOD_EMAIL,
    MFA_DELIVERY_METHOD_SMS,
    PASSWORD_RESET_STATUS_PENDING,
    PASSWORD_RESET_STATUS_REVOKED,
    PASSWORD_RESET_STATUS_USED,
    SESSION_REVOKED_REASON_LOGOUT,
    SESSION_REVOKED_REASON_PASSWORD_RESET,
    PatientPortalAccount,
    PatientPortalMfaChallenge,
    PatientPortalPasswordResetToken,
    PatientPortalSession,
    utc_now,
)

AUTH_LOCKED_BY_AUTOMATION = "portal-auth"
AUTH_REASON_ACCOUNT_LOCKED = "account_locked"
AUTH_REASON_DELIVERY_UNAVAILABLE = "delivery_unavailable"
AUTH_REASON_FORCE_PASSWORD_RESET = "force_password_reset"
AUTH_REASON_INVALID_CODE = "invalid_code"
AUTH_REASON_INVALID_CREDENTIALS = "invalid_credentials"
AUTH_REASON_INVALID_RESET_TOKEN = "invalid_reset_token"
AUTH_REASON_MFA_EXPIRED = "mfa_expired"
AUTH_REASON_MFA_REQUIRED = "mfa_required"
AUTH_REASON_PASSWORD_FAILURES = "password_failures"
AUTH_REASON_MFA_FAILURES = "mfa_failures"
AUTH_TOKEN_BYTES = 32
MFA_CODE_DIGITS = 6
MFA_CODE_MODULUS = 10**MFA_CODE_DIGITS
DUMMY_PASSWORD_HASH = password_hasher.hash(token_urlsafe(AUTH_TOKEN_BYTES))


class InvalidCredentialsError(Exception):
    """Raised when username and password cannot start a session."""


class AccountLockedError(Exception):
    """Raised when account access requires staff unlock."""


class PasswordResetRequiredError(Exception):
    """Raised when the account must complete password reset before sign-in."""


class MfaChallengeNotFoundError(Exception):
    """Raised when an MFA challenge token is invalid, expired, or already used."""


class MfaDeliveryUnavailableError(Exception):
    """Raised when the requested MFA channel is unavailable for the account."""


class MfaRateLimitedError(Exception):
    """Raised when MFA code delivery is requested too frequently."""

    def __init__(self, retry_after_seconds: int) -> None:
        super().__init__()
        self.retry_after_seconds = retry_after_seconds


class InvalidMfaCodeError(Exception):
    """Raised when an MFA challenge code does not match."""


class PasswordResetTokenInvalidError(Exception):
    """Raised when a reset token is invalid, expired, or already used."""


class PortalSessionInvalidError(Exception):
    """Raised when a bearer session token cannot authenticate a patient."""


class AccountNotFoundError(Exception):
    """Raised when a staff account-management action cannot find an account."""


@dataclass(frozen=True)
class AuthPolicy:
    """Runtime auth/session policy derived from portal settings."""

    max_failed_password_attempts: int
    mfa_max_failed_attempts: int
    session_ttl: timedelta
    mfa_code_ttl: timedelta
    mfa_email_resend_cooldown: timedelta
    mfa_sms_resend_cooldown: timedelta
    password_reset_token_ttl: timedelta
    password_reset_request_cooldown: timedelta
    require_mfa: bool


@dataclass(frozen=True)
class MfaChallengeDelivery:
    challenge_id: int
    challenge_token: str
    code: str
    delivery_method: str
    destination: str
    available_delivery_methods: tuple[str, ...]
    expires_at: datetime


@dataclass(frozen=True)
class LoginResult:
    status: str
    account: PatientPortalAccount
    session_token: str | None = None
    mfa_challenge: MfaChallengeDelivery | None = None


@dataclass(frozen=True)
class PasswordResetRequestResult:
    reset_token: str | None
    recipient: str | None


@dataclass(frozen=True)
class AuthenticatedPortalSession:
    account: PatientPortalAccount
    portal_session: PatientPortalSession


def hash_auth_token(secret: str, purpose: str, token: str) -> str:
    normalized_secret = secret.strip()
    if not normalized_secret:
        raise ValueError("secret must not be blank")
    normalized_token = token.strip()
    if not normalized_token:
        raise ValueError("token must not be blank")
    return new_hmac(
        normalized_secret.encode("utf-8"),
        f"auth_token:{purpose}:{normalized_token}".encode(),
        sha256,
    ).hexdigest()


def create_auth_token() -> str:
    return token_urlsafe(AUTH_TOKEN_BYTES)


def create_mfa_code() -> str:
    return f"{randbelow(MFA_CODE_MODULUS):0{MFA_CODE_DIGITS}d}"


def normalize_mfa_delivery_method(delivery_method: str | None) -> str:
    normalized_method = (delivery_method or MFA_DELIVERY_METHOD_EMAIL).strip().casefold()
    if normalized_method not in {MFA_DELIVERY_METHOD_EMAIL, MFA_DELIVERY_METHOD_SMS}:
        raise ValueError("delivery_method must be email or sms")
    return normalized_method


def normalize_phone_number(phone_number: str | None) -> str | None:
    if phone_number is None:
        return None

    normalized_number = phone_number.strip()
    if not normalized_number:
        return None
    if len(normalized_number) > MAX_PHONE_NUMBER_LENGTH:
        raise ValueError(f"phone_number must be {MAX_PHONE_NUMBER_LENGTH} characters or fewer")
    if sum(character.isdigit() for character in normalized_number) < 7:
        raise ValueError("phone_number must include at least seven digits")
    return normalized_number


def hash_mfa_code(secret: str, challenge_token: str, code: str) -> str:
    normalized_code = code.strip()
    if len(normalized_code) != MFA_CODE_DIGITS or not normalized_code.isdigit():
        raise ValueError("MFA code must be six digits")
    return new_hmac(
        secret.encode("utf-8"),
        f"mfa_code:{challenge_token}:{normalized_code}".encode(),
        sha256,
    ).hexdigest()


def is_past(value: datetime, now: datetime) -> bool:
    comparable_value = value
    comparable_now = now
    if comparable_value.tzinfo is None:
        comparable_value = comparable_value.replace(tzinfo=UTC)
    if comparable_now.tzinfo is None:
        comparable_now = comparable_now.replace(tzinfo=UTC)
    return comparable_value <= comparable_now


def seconds_until_allowed(last_sent_at: datetime, now: datetime, cooldown: timedelta) -> int:
    comparable_last_sent_at = last_sent_at
    comparable_now = now
    if comparable_last_sent_at.tzinfo is None:
        comparable_last_sent_at = comparable_last_sent_at.replace(tzinfo=UTC)
    if comparable_now.tzinfo is None:
        comparable_now = comparable_now.replace(tzinfo=UTC)

    elapsed_seconds = (comparable_now - comparable_last_sent_at).total_seconds()
    return max(1, ceil(cooldown.total_seconds() - elapsed_seconds))


def password_matches(password_hash: str, password: str) -> bool:
    try:
        return password_hasher.verify(password_hash, password)
    except (InvalidHashError, VerificationError, VerifyMismatchError):
        return False


def verify_dummy_password(password: str) -> None:
    password_matches(DUMMY_PASSWORD_HASH, password)


def ensure_mfa_delivery_available(
    account: PatientPortalAccount,
    delivery_method: str,
) -> None:
    if delivery_method == MFA_DELIVERY_METHOD_EMAIL and account.email:
        return
    if delivery_method == MFA_DELIVERY_METHOD_SMS and normalize_phone_number(account.phone_number):
        return
    raise MfaDeliveryUnavailableError()


def revoke_account_sessions(
    session: Session,
    account_id: int,
    *,
    reason: str,
    now: datetime,
) -> None:
    active_sessions = list(
        session.scalars(
            select(PatientPortalSession)
            .where(
                PatientPortalSession.account_id == account_id,
                PatientPortalSession.revoked_at.is_(None),
            )
            .with_for_update()
        )
    )
    for portal_session in active_sessions:
        portal_session.revoked_at = now
        portal_session.revoked_reason = reason


def cancel_pending_mfa_challenges(
    session: Session,
    account_id: int,
    *,
    now: datetime,
) -> None:
    pending_challenges = list(
        session.scalars(
            select(PatientPortalMfaChallenge)
            .where(
                PatientPortalMfaChallenge.account_id == account_id,
                PatientPortalMfaChallenge.status == MFA_CHALLENGE_STATUS_PENDING,
            )
            .with_for_update()
        )
    )
    for challenge in pending_challenges:
        challenge.status = MFA_CHALLENGE_STATUS_CANCELLED
        challenge.updated_at = now


def lock_account(
    session: Session,
    account: PatientPortalAccount,
    *,
    reason: str,
    now: datetime,
) -> None:
    if account.locked_at is None:
        account.locked_at = now
        account.locked_by = AUTH_LOCKED_BY_AUTOMATION
        account.force_password_reset = True
        account.updated_at = now
        revoke_account_sessions(
            session,
            account.id,
            reason=reason,
            now=now,
        )
        cancel_pending_mfa_challenges(session, account.id, now=now)
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_ACCOUNT_LOCK,
            outcome=AUDIT_OUTCOME_SUCCESS,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            reason=reason,
        )


def create_patient_session(
    session: Session,
    account: PatientPortalAccount,
    *,
    policy: AuthPolicy,
    token_secret: str,
    now: datetime,
) -> str:
    session_token = create_auth_token()
    portal_session = PatientPortalSession(
        account_id=account.id,
        token_hash=hash_auth_token(token_secret, "session", session_token),
        created_at=now,
        expires_at=now + policy.session_ttl,
    )
    session.add(portal_session)
    session.flush()
    return session_token


def create_mfa_challenge(
    session: Session,
    account: PatientPortalAccount,
    *,
    delivery_method: str,
    policy: AuthPolicy,
    token_secret: str,
    code_secret: str,
    now: datetime,
) -> MfaChallengeDelivery:
    normalized_delivery_method = normalize_mfa_delivery_method(delivery_method)
    ensure_mfa_delivery_available(account, normalized_delivery_method)

    challenge_token = create_auth_token()
    code = create_mfa_code()
    challenge = PatientPortalMfaChallenge(
        account_id=account.id,
        challenge_token_hash=hash_auth_token(token_secret, "mfa_challenge", challenge_token),
        code_hash=hash_mfa_code(code_secret, challenge_token, code),
        delivery_method=normalized_delivery_method,
        status=MFA_CHALLENGE_STATUS_PENDING,
        failed_attempts=0,
        created_at=now,
        updated_at=now,
        expires_at=now + policy.mfa_code_ttl,
        last_email_sent_at=None,
        last_sms_sent_at=None,
    )
    session.add(challenge)
    session.flush()
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_MFA_CHALLENGE,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        reason=normalized_delivery_method,
    )
    destination = (
        account.email
        if normalized_delivery_method == MFA_DELIVERY_METHOD_EMAIL
        else normalize_phone_number(account.phone_number)
    )
    if destination is None:
        raise MfaDeliveryUnavailableError()
    return MfaChallengeDelivery(
        challenge_id=challenge.id,
        challenge_token=challenge_token,
        code=code,
        delivery_method=normalized_delivery_method,
        destination=destination,
        available_delivery_methods=available_mfa_delivery_methods(account),
        expires_at=challenge.expires_at,
    )


def start_login(
    session: Session,
    *,
    username: str,
    password: str,
    client_reference_hash: str,
    policy: AuthPolicy,
    token_secret: str,
    mfa_code_secret: str,
    delivery_method: str | None = None,
) -> LoginResult:
    now = utc_now()
    try:
        normalized_username = validate_username(username)
    except ValueError:
        verify_dummy_password(password)
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_LOGIN,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            client_reference_hash=client_reference_hash,
            reason=AUTH_REASON_INVALID_CREDENTIALS,
        )
        raise InvalidCredentialsError() from None

    account = session.scalar(
        select(PatientPortalAccount)
        .where(PatientPortalAccount.username == normalized_username)
        .with_for_update()
    )
    if account is None or account.status != ACCOUNT_STATUS_ACTIVE:
        verify_dummy_password(password)
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_LOGIN,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            client_reference_hash=client_reference_hash,
            reason=AUTH_REASON_INVALID_CREDENTIALS,
        )
        raise InvalidCredentialsError()

    if account.locked_at is not None:
        verify_dummy_password(password)
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_LOGIN,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            client_reference_hash=client_reference_hash,
            reason=AUTH_REASON_ACCOUNT_LOCKED,
        )
        raise AccountLockedError()

    if not password_matches(account.password_hash, password):
        account.failed_login_count += 1
        account.updated_at = now
        lockout_reached = account.failed_login_count >= policy.max_failed_password_attempts
        if lockout_reached:
            lock_account(session, account, reason=AUTH_REASON_PASSWORD_FAILURES, now=now)
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_LOGIN,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            client_reference_hash=client_reference_hash,
            reason=AUTH_REASON_ACCOUNT_LOCKED
            if lockout_reached
            else AUTH_REASON_INVALID_CREDENTIALS,
        )
        if lockout_reached:
            raise AccountLockedError()
        raise InvalidCredentialsError()

    if password_hasher.check_needs_rehash(account.password_hash):
        account.password_hash = password_hasher.hash(password)
    account.failed_login_count = 0
    account.updated_at = now

    if account.force_password_reset:
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_LOGIN,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            client_reference_hash=client_reference_hash,
            reason=AUTH_REASON_FORCE_PASSWORD_RESET,
        )
        raise PasswordResetRequiredError()

    if policy.require_mfa:
        requested_delivery_method = normalize_mfa_delivery_method(
            delivery_method or account.preferred_mfa_method
        )
        try:
            mfa_challenge = create_mfa_challenge(
                session,
                account,
                delivery_method=requested_delivery_method,
                policy=policy,
                token_secret=token_secret,
                code_secret=mfa_code_secret,
                now=now,
            )
        except MfaDeliveryUnavailableError:
            record_audit_event(
                session,
                event_type=AUDIT_EVENT_LOGIN,
                outcome=AUDIT_OUTCOME_FAILURE,
                actor_type=AUDIT_ACTOR_TYPE_PATIENT,
                actor=account.username,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                account_id=account.id,
                client_reference_hash=client_reference_hash,
                reason=AUTH_REASON_DELIVERY_UNAVAILABLE,
            )
            raise
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_LOGIN,
            outcome=AUDIT_OUTCOME_SUCCESS,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            client_reference_hash=client_reference_hash,
            reason=AUTH_REASON_MFA_REQUIRED,
        )
        return LoginResult(
            status="mfa_required",
            account=account,
            mfa_challenge=mfa_challenge,
        )

    session_token = create_patient_session(
        session,
        account,
        policy=policy,
        token_secret=token_secret,
        now=now,
    )
    account.last_login_at = now
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_LOGIN,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        client_reference_hash=client_reference_hash,
    )
    return LoginResult(status="signed_in", account=account, session_token=session_token)


def get_mfa_challenge_for_token(
    session: Session,
    challenge_token: str,
    *,
    token_secret: str,
) -> PatientPortalMfaChallenge | None:
    return session.scalar(
        select(PatientPortalMfaChallenge)
        .where(
            PatientPortalMfaChallenge.challenge_token_hash
            == hash_auth_token(token_secret, "mfa_challenge", challenge_token)
        )
        .with_for_update()
    )


def available_mfa_delivery_methods(
    account: PatientPortalAccount,
) -> tuple[str, ...]:
    methods = [MFA_DELIVERY_METHOD_EMAIL]
    if normalize_phone_number(account.phone_number) is not None:
        methods.append(MFA_DELIVERY_METHOD_SMS)
    return tuple(methods)


def get_mfa_challenge_delivery_state(
    session: Session,
    challenge_token: str,
    *,
    token_secret: str,
    preferred_delivery_method: str | None = None,
) -> MfaChallengeDelivery | None:
    challenge = get_mfa_challenge_for_token(
        session,
        challenge_token,
        token_secret=token_secret,
    )
    if challenge is None or challenge.status != MFA_CHALLENGE_STATUS_PENDING:
        return None
    account = session.get(PatientPortalAccount, challenge.account_id)
    if account is None or account.status != ACCOUNT_STATUS_ACTIVE:
        return None

    available_methods = available_mfa_delivery_methods(account)
    delivery_method = challenge.delivery_method
    if preferred_delivery_method is not None:
        try:
            normalized_preferred_method = normalize_mfa_delivery_method(
                preferred_delivery_method
            )
        except ValueError:
            normalized_preferred_method = delivery_method
        if normalized_preferred_method in available_methods:
            delivery_method = normalized_preferred_method

    destination = (
        account.email
        if delivery_method == MFA_DELIVERY_METHOD_EMAIL
        else normalize_phone_number(account.phone_number)
    )
    if destination is None:
        return None
    return MfaChallengeDelivery(
        challenge_id=challenge.id,
        challenge_token=challenge_token,
        code="",
        delivery_method=delivery_method,
        destination=destination,
        available_delivery_methods=available_methods,
        expires_at=challenge.expires_at,
    )


def resend_mfa_challenge(
    session: Session,
    *,
    challenge_token: str,
    delivery_method: str,
    policy: AuthPolicy,
    token_secret: str,
    code_secret: str,
) -> MfaChallengeDelivery:
    now = utc_now()
    challenge = get_mfa_challenge_for_token(
        session,
        challenge_token,
        token_secret=token_secret,
    )
    if (
        challenge is None
        or challenge.status != MFA_CHALLENGE_STATUS_PENDING
        or is_past(challenge.expires_at, now)
    ):
        if challenge is not None and challenge.status == MFA_CHALLENGE_STATUS_PENDING:
            challenge.status = MFA_CHALLENGE_STATUS_CANCELLED
            challenge.updated_at = now
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_MFA_RESEND,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            reason=AUTH_REASON_MFA_EXPIRED,
        )
        raise MfaChallengeNotFoundError()

    account = session.get(PatientPortalAccount, challenge.account_id)
    if account is None or account.status != ACCOUNT_STATUS_ACTIVE:
        raise MfaChallengeNotFoundError()
    if account.locked_at is not None:
        raise AccountLockedError()
    if account.force_password_reset:
        raise PasswordResetRequiredError()

    normalized_delivery_method = normalize_mfa_delivery_method(delivery_method)
    ensure_mfa_delivery_available(account, normalized_delivery_method)
    if normalized_delivery_method == MFA_DELIVERY_METHOD_EMAIL:
        cooldown = policy.mfa_email_resend_cooldown
        last_sent_at = challenge.last_email_sent_at
    else:
        cooldown = policy.mfa_sms_resend_cooldown
        last_sent_at = challenge.last_sms_sent_at

    if last_sent_at is not None and not is_past(last_sent_at + cooldown, now):
        retry_after_seconds = seconds_until_allowed(last_sent_at, now, cooldown)
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_MFA_RESEND,
            outcome=AUDIT_OUTCOME_THROTTLED,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            reason=normalized_delivery_method,
        )
        raise MfaRateLimitedError(retry_after_seconds)

    code = create_mfa_code()
    challenge.code_hash = hash_mfa_code(code_secret, challenge_token, code)
    challenge.delivery_method = normalized_delivery_method
    challenge.expires_at = now + policy.mfa_code_ttl
    challenge.updated_at = now
    session.flush()
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_MFA_RESEND,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        reason=normalized_delivery_method,
    )
    destination = (
        account.email
        if normalized_delivery_method == MFA_DELIVERY_METHOD_EMAIL
        else normalize_phone_number(account.phone_number)
    )
    if destination is None:
        raise MfaDeliveryUnavailableError()
    return MfaChallengeDelivery(
        challenge_id=challenge.id,
        challenge_token=challenge_token,
        code=code,
        delivery_method=normalized_delivery_method,
        destination=destination,
        available_delivery_methods=available_mfa_delivery_methods(account),
        expires_at=challenge.expires_at,
    )


def record_mfa_delivery_outcome(
    session: Session,
    *,
    delivery: MfaChallengeDelivery,
    outcome: str,
) -> None:
    if outcome not in {AUDIT_OUTCOME_SUCCESS, AUDIT_OUTCOME_FAILURE}:
        raise ValueError("delivery outcome must be success or failure")

    challenge = session.scalar(
        select(PatientPortalMfaChallenge)
        .where(PatientPortalMfaChallenge.id == delivery.challenge_id)
        .with_for_update()
    )
    if challenge is None:
        raise MfaChallengeNotFoundError()
    account = session.get(PatientPortalAccount, challenge.account_id)
    if account is None:
        raise MfaChallengeNotFoundError()

    if outcome == AUDIT_OUTCOME_SUCCESS:
        delivered_at = utc_now()
        challenge.updated_at = delivered_at
        if delivery.delivery_method == MFA_DELIVERY_METHOD_EMAIL:
            challenge.last_email_sent_at = delivered_at
        else:
            challenge.last_sms_sent_at = delivered_at

    record_audit_event(
        session,
        event_type=AUDIT_EVENT_MFA_DELIVERY,
        outcome=outcome,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        reason=delivery.delivery_method,
    )


def verify_mfa_challenge(
    session: Session,
    *,
    challenge_token: str,
    code: str,
    policy: AuthPolicy,
    token_secret: str,
    code_secret: str,
) -> str:
    now = utc_now()
    challenge = get_mfa_challenge_for_token(
        session,
        challenge_token,
        token_secret=token_secret,
    )
    if (
        challenge is None
        or challenge.status != MFA_CHALLENGE_STATUS_PENDING
        or is_past(challenge.expires_at, now)
    ):
        if challenge is not None and challenge.status == MFA_CHALLENGE_STATUS_PENDING:
            challenge.status = MFA_CHALLENGE_STATUS_CANCELLED
            challenge.updated_at = now
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_MFA_VERIFY,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            reason=AUTH_REASON_MFA_EXPIRED,
        )
        raise MfaChallengeNotFoundError()

    account = session.get(PatientPortalAccount, challenge.account_id)
    if account is None or account.status != ACCOUNT_STATUS_ACTIVE:
        raise MfaChallengeNotFoundError()
    if account.locked_at is not None:
        raise AccountLockedError()
    if account.force_password_reset:
        raise PasswordResetRequiredError()

    try:
        supplied_code_hash = hash_mfa_code(code_secret, challenge_token, code)
    except ValueError:
        supplied_code_hash = ""
    if not compare_digest(challenge.code_hash, supplied_code_hash):
        challenge.failed_attempts += 1
        challenge.updated_at = now
        lockout_reached = challenge.failed_attempts >= policy.mfa_max_failed_attempts
        if lockout_reached:
            challenge.status = MFA_CHALLENGE_STATUS_CANCELLED
            lock_account(session, account, reason=AUTH_REASON_MFA_FAILURES, now=now)
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_MFA_VERIFY,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            reason=AUTH_REASON_ACCOUNT_LOCKED if lockout_reached else AUTH_REASON_INVALID_CODE,
        )
        if lockout_reached:
            raise AccountLockedError()
        raise InvalidMfaCodeError()

    challenge.status = MFA_CHALLENGE_STATUS_VERIFIED
    challenge.verified_at = now
    challenge.updated_at = now
    session_token = create_patient_session(
        session,
        account,
        policy=policy,
        token_secret=token_secret,
        now=now,
    )
    account.last_login_at = now
    account.failed_login_count = 0
    account.updated_at = now
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_MFA_VERIFY,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        reason=challenge.delivery_method,
    )
    return session_token


def request_password_reset(
    session: Session,
    *,
    username: str,
    email: str,
    client_reference_hash: str,
    policy: AuthPolicy,
    token_secret: str,
) -> PasswordResetRequestResult:
    now = utc_now()
    try:
        normalized_username = validate_username(username)
        normalized_email = normalize_email(email)
    except ValueError:
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_PASSWORD_RESET_REQUEST,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            client_reference_hash=client_reference_hash,
            reason=AUTH_REASON_INVALID_CREDENTIALS,
        )
        return PasswordResetRequestResult(reset_token=None, recipient=None)

    account = session.scalar(
        select(PatientPortalAccount)
        .where(PatientPortalAccount.username == normalized_username)
        .with_for_update()
    )
    if (
        account is None
        or account.email != normalized_email
        or account.status != ACCOUNT_STATUS_ACTIVE
        or account.locked_at is not None
    ):
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_PASSWORD_RESET_REQUEST,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            client_reference_hash=client_reference_hash,
            reason=AUTH_REASON_INVALID_CREDENTIALS,
        )
        return PasswordResetRequestResult(reset_token=None, recipient=None)

    existing_reset_tokens = list(
        session.scalars(
            select(PatientPortalPasswordResetToken)
            .where(
                PatientPortalPasswordResetToken.account_id == account.id,
                PatientPortalPasswordResetToken.status == PASSWORD_RESET_STATUS_PENDING,
            )
            .with_for_update()
        )
    )
    if any(
        not is_past(
            reset_token.created_at + policy.password_reset_request_cooldown,
            now,
        )
        for reset_token in existing_reset_tokens
    ):
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_PASSWORD_RESET_REQUEST,
            outcome=AUDIT_OUTCOME_THROTTLED,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            client_reference_hash=client_reference_hash,
        )
        return PasswordResetRequestResult(reset_token=None, recipient=None)
    for reset_token in existing_reset_tokens:
        reset_token.status = PASSWORD_RESET_STATUS_REVOKED

    reset_token_value = create_auth_token()
    reset_token = PatientPortalPasswordResetToken(
        account_id=account.id,
        token_hash=hash_auth_token(token_secret, "password_reset", reset_token_value),
        status=PASSWORD_RESET_STATUS_PENDING,
        created_at=now,
        expires_at=now + policy.password_reset_token_ttl,
        client_reference_hash=client_reference_hash,
    )
    session.add(reset_token)
    session.flush()
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_PASSWORD_RESET_REQUEST,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        client_reference_hash=client_reference_hash,
    )
    return PasswordResetRequestResult(
        reset_token=reset_token_value,
        recipient=account.email,
    )


def complete_password_reset(
    session: Session,
    *,
    reset_token: str,
    new_password: str,
    token_secret: str,
) -> PatientPortalAccount:
    validate_password(new_password)
    now = utc_now()
    token_hash = hash_auth_token(token_secret, "password_reset", reset_token)
    reset_record = session.scalar(
        select(PatientPortalPasswordResetToken)
        .where(PatientPortalPasswordResetToken.token_hash == token_hash)
        .with_for_update()
    )
    if (
        reset_record is None
        or reset_record.status != PASSWORD_RESET_STATUS_PENDING
        or is_past(reset_record.expires_at, now)
    ):
        if reset_record is not None and reset_record.status == PASSWORD_RESET_STATUS_PENDING:
            reset_record.status = PASSWORD_RESET_STATUS_REVOKED
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            reason=AUTH_REASON_INVALID_RESET_TOKEN,
        )
        raise PasswordResetTokenInvalidError()

    account = session.get(PatientPortalAccount, reset_record.account_id)
    if account is None or account.status != ACCOUNT_STATUS_ACTIVE:
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            reason=AUTH_REASON_INVALID_RESET_TOKEN,
        )
        raise PasswordResetTokenInvalidError()

    account.password_hash = password_hasher.hash(new_password)
    account.password_updated_at = now
    account.failed_login_count = 0
    account.locked_at = None
    account.locked_by = None
    account.force_password_reset = False
    account.updated_at = now
    reset_record.status = PASSWORD_RESET_STATUS_USED
    reset_record.used_at = now
    revoke_account_sessions(
        session,
        account.id,
        reason=SESSION_REVOKED_REASON_PASSWORD_RESET,
        now=now,
    )
    cancel_pending_mfa_challenges(session, account.id, now=now)
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
    )
    return account


def authenticate_session_token(
    session: Session,
    *,
    session_token: str,
    token_secret: str,
) -> AuthenticatedPortalSession:
    now = utc_now()
    portal_session = session.scalar(
        select(PatientPortalSession)
        .where(
            PatientPortalSession.token_hash
            == hash_auth_token(token_secret, "session", session_token)
        )
        .with_for_update()
    )
    if (
        portal_session is None
        or portal_session.revoked_at is not None
        or is_past(portal_session.expires_at, now)
    ):
        raise PortalSessionInvalidError()

    account = session.get(PatientPortalAccount, portal_session.account_id)
    if (
        account is None
        or account.status != ACCOUNT_STATUS_ACTIVE
        or account.locked_at is not None
        or account.force_password_reset
    ):
        portal_session.revoked_at = now
        portal_session.revoked_reason = AUTH_REASON_ACCOUNT_LOCKED
        raise PortalSessionInvalidError()

    portal_session.last_seen_at = now
    return AuthenticatedPortalSession(account=account, portal_session=portal_session)


def logout_patient_session(
    session: Session,
    *,
    session_token: str,
    token_secret: str,
) -> None:
    authenticated_session = authenticate_session_token(
        session,
        session_token=session_token,
        token_secret=token_secret,
    )
    now = utc_now()
    authenticated_session.portal_session.revoked_at = now
    authenticated_session.portal_session.revoked_reason = SESSION_REVOKED_REASON_LOGOUT
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_SESSION_LOGOUT,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=authenticated_session.account.username,
        clinic_id=authenticated_session.account.clinic_id,
        demographic_no=authenticated_session.account.demographic_no,
        account_id=authenticated_session.account.id,
    )


def unlock_patient_account(
    session: Session,
    account_id: int,
    actor: str,
    *,
    clinic_id: str | None = None,
) -> PatientPortalAccount:
    normalized_actor = normalize_staff_actor(actor)
    statement = select(PatientPortalAccount).where(PatientPortalAccount.id == account_id)
    if clinic_id is not None:
        statement = statement.where(
            PatientPortalAccount.clinic_id == normalize_clinic_id(clinic_id)
        )
    account = session.scalar(statement.with_for_update())
    if account is None:
        raise AccountNotFoundError()

    now = utc_now()
    account.failed_login_count = 0
    account.locked_at = None
    account.locked_by = None
    account.force_password_reset = True
    account.updated_at = now
    revoke_account_sessions(
        session,
        account.id,
        reason=AUTH_REASON_FORCE_PASSWORD_RESET,
        now=now,
    )
    cancel_pending_mfa_challenges(session, account.id, now=now)
    record_audit_event(
        session,
        event_type=AUDIT_EVENT_ACCOUNT_UNLOCK,
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_STAFF,
        actor=normalized_actor,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        reason=AUTH_REASON_FORCE_PASSWORD_RESET,
    )
    return account
