import logging
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from functools import lru_cache
from hashlib import sha256
from hmac import compare_digest
from hmac import new as new_hmac
from math import ceil
from secrets import randbelow, token_urlsafe

from argon2.exceptions import InvalidHashError, VerificationError, VerifyMismatchError
from sqlalchemy import or_, select, update
from sqlalchemy.orm import Session

from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.credentials import (
    hash_password,
    password_needs_rehash,
    validate_password,
    validate_username,
    verify_password,
)
from carlos_patient_portal.identity import normalize_email
from carlos_patient_portal.invites import normalize_clinic_id, normalize_staff_actor
from carlos_patient_portal.models import (
    ACCOUNT_STATUS_ACTIVE,
    ACCOUNT_STATUS_DISABLED,
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_ACTOR_TYPE_STAFF,
    AUDIT_EVENT_ACCOUNT_DISABLE,
    AUDIT_EVENT_ACCOUNT_ENABLE,
    AUDIT_EVENT_ACCOUNT_LOCK,
    AUDIT_EVENT_ACCOUNT_UNLOCK,
    AUDIT_EVENT_LOGIN,
    AUDIT_EVENT_MFA_CHALLENGE,
    AUDIT_EVENT_MFA_DELIVERY,
    AUDIT_EVENT_MFA_RESEND,
    AUDIT_EVENT_MFA_VERIFY,
    AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
    AUDIT_EVENT_PASSWORD_RESET_DELIVERY,
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
    SESSION_REVOKED_REASON_ACCOUNT_DISABLED,
    SESSION_REVOKED_REASON_LOGOUT,
    SESSION_REVOKED_REASON_PASSWORD_RESET,
    PatientPortalAccount,
    PatientPortalMfaChallenge,
    PatientPortalPasswordResetToken,
    PatientPortalSession,
    utc_now,
)

logger = logging.getLogger(__name__)

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
AUTH_REASON_PASSWORD_HASH_UNUSABLE = "password_hash_unusable"
AUTH_REASON_MFA_FAILURES = "mfa_failures"
AUTH_TOKEN_BYTES = 32
MFA_CODE_DIGITS = 6
MFA_CODE_MODULUS = 10**MFA_CODE_DIGITS
SESSION_LAST_SEEN_UPDATE_INTERVAL = timedelta(minutes=5)
SESSION_REVOKED_REASON_IDLE_TIMEOUT = "idle_timeout"


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
    session_idle_timeout: timedelta
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
    expected_code_hash: str
    previous_code_hash: str | None = None
    previous_delivery_method: str | None = None
    previous_expires_at: datetime | None = None
    # The account-level send cooldown is reserved before delivery is attempted. A failed send
    # shortens that reservation to a retry grace rather than releasing it, so the cooldown that
    # was reserved has to travel with the delivery to be shortened correctly.
    previous_account_email_sent_at: datetime | None = None
    previous_account_sms_sent_at: datetime | None = None
    reserved_cooldown: timedelta | None = None


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
    reset_token_id: int | None = None
    account_id: int | None = None


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
    digits = "".join(character for character in normalized_number if character.isdigit())
    if normalized_number.startswith("+"):
        e164_number = f"+{digits}"
    elif len(digits) == 10:
        e164_number = f"+1{digits}"
    elif len(digits) == 11 and digits.startswith("1"):
        e164_number = f"+{digits}"
    else:
        raise ValueError("phone_number must use international format")
    if not 8 <= len(digits) <= 15:
        raise ValueError("phone_number must contain between 8 and 15 digits")
    return e164_number


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


# How soon a patient may retry after a delivery the provider reported as failed. Short enough that
# a real outage does not strand them behind a full cooldown, long enough that a provider which
# delivers and *then* reports failure cannot be driven into flooding a mailbox or an SMS number.
MFA_DELIVERY_FAILURE_RETRY_GRACE = timedelta(seconds=5)


def shorten_delivery_reservation(
    *,
    previous_sent_at: datetime | None,
    now: datetime,
    cooldown: timedelta,
) -> datetime:
    """Move a failed send's cooldown reservation forward to expire after the retry grace.

    Never returns a value earlier than the reservation already in place. Reaching a send at all
    requires the previous reservation to have aged past the cooldown, so this only ever shortens
    the *new* reservation — it cannot be used to unblock an account sooner than it already was.
    """
    shortened = now - cooldown + MFA_DELIVERY_FAILURE_RETRY_GRACE
    if previous_sent_at is None:
        return shortened
    comparable_previous = previous_sent_at
    if comparable_previous.tzinfo is None:
        comparable_previous = comparable_previous.replace(tzinfo=UTC)
    return max(shortened, comparable_previous)


def seconds_until_allowed(last_sent_at: datetime, now: datetime, cooldown: timedelta) -> int:
    comparable_last_sent_at = last_sent_at
    comparable_now = now
    if comparable_last_sent_at.tzinfo is None:
        comparable_last_sent_at = comparable_last_sent_at.replace(tzinfo=UTC)
    if comparable_now.tzinfo is None:
        comparable_now = comparable_now.replace(tzinfo=UTC)

    elapsed_seconds = (comparable_now - comparable_last_sent_at).total_seconds()
    return max(1, ceil(cooldown.total_seconds() - elapsed_seconds))


class PasswordHashUnusableError(Exception):
    """Raised when a stored password hash cannot be evaluated at all.

    This is a data-integrity fault (a truncated column, a partial restore, a non-Argon2 hash
    imported from elsewhere), not a wrong password. Collapsing it into "credentials invalid" would
    tell the patient they mistyped, burn their lockout budget, and record their own correct attempt
    in the audit trail as a failure.
    """


def password_matches(password_hash: str, password: str) -> bool:
    try:
        return verify_password(password_hash, password)
    except VerifyMismatchError:
        return False
    except (InvalidHashError, VerificationError) as exc:
        raise PasswordHashUnusableError() from exc


def verify_account_password(
    session: Session,
    account: PatientPortalAccount,
    password: str,
    *,
    client_reference_hash: str | None = None,
) -> bool:
    """Verify a real account password, distinguishing a wrong password from an unusable hash."""
    try:
        return password_matches(account.password_hash, password)
    except PasswordHashUnusableError:
        # Deliberately NOT counted against the lockout budget: the patient did nothing wrong.
        logger.error("Stored password hash is unusable; login cannot be evaluated")
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
            reason=AUTH_REASON_PASSWORD_HASH_UNUSABLE,
        )
        # The request aborts with a 503, whose teardown rolls back; commit so the operational
        # evidence of the integrity fault survives the failure it is describing.
        session.commit()
        raise


@lru_cache(maxsize=1)
def dummy_password_hash() -> str:
    """Argon2 hash of an unguessable value, used only to equalise failed-login timing.

    Derived lazily rather than at import: hashing costs ~64 MiB and a deliberate time cost, and
    paying that during module import charged every process start, test collection, and CLI
    invocation for something only the login path uses.
    """
    return hash_password(token_urlsafe(AUTH_TOKEN_BYTES))


def verify_dummy_password(password: str) -> None:
    # The dummy hash is derived in-process from a fresh random value, so it can only fail if the
    # process itself is broken; equalising login timing must never surface as an error to the
    # caller.
    try:
        password_matches(dummy_password_hash(), password)
    except PasswordHashUnusableError:
        logger.error("Dummy password hash is unusable")


def ensure_mfa_delivery_available(
    account: PatientPortalAccount,
    delivery_method: str,
) -> None:
    if delivery_method == MFA_DELIVERY_METHOD_EMAIL and account.email:
        return
    if (
        delivery_method == MFA_DELIVERY_METHOD_SMS
        and normalize_phone_number(account.phone_number) is not None
    ):
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


def revoke_pending_password_reset_tokens(
    session: Session,
    account_id: int,
) -> None:
    pending_tokens = list(
        session.scalars(
            select(PatientPortalPasswordResetToken)
            .where(
                PatientPortalPasswordResetToken.account_id == account_id,
                PatientPortalPasswordResetToken.status == PASSWORD_RESET_STATUS_PENDING,
            )
            .with_for_update()
        )
    )
    for reset_token in pending_tokens:
        reset_token.status = PASSWORD_RESET_STATUS_REVOKED


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
        account.locked_by_id = AUTH_LOCKED_BY_AUTOMATION
        account.force_password_reset = True
        account.updated_at = now
        revoke_account_sessions(
            session,
            account.id,
            reason=reason,
            now=now,
        )
        cancel_pending_mfa_challenges(session, account.id, now=now)
        revoke_pending_password_reset_tokens(session, account.id)
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
    session_token_secret: str,
    now: datetime,
) -> str:
    session_token = create_auth_token()
    portal_session = PatientPortalSession(
        account_id=account.id,
        token_hash=hash_auth_token(session_token_secret, "session", session_token),
        created_at=now,
        expires_at=now + policy.session_ttl,
        last_seen_at=now,
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
    challenge_token_secret: str,
    code_secret: str,
    now: datetime,
) -> MfaChallengeDelivery:
    normalized_delivery_method = normalize_mfa_delivery_method(delivery_method)
    ensure_mfa_delivery_available(account, normalized_delivery_method)
    if normalized_delivery_method == MFA_DELIVERY_METHOD_EMAIL:
        cooldown = policy.mfa_email_resend_cooldown
        last_sent_at = account.last_mfa_email_sent_at
    else:
        cooldown = policy.mfa_sms_resend_cooldown
        last_sent_at = account.last_mfa_sms_sent_at
    if last_sent_at is not None and not is_past(last_sent_at + cooldown, now):
        retry_after_seconds = seconds_until_allowed(last_sent_at, now, cooldown)
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_MFA_CHALLENGE,
            outcome=AUDIT_OUTCOME_THROTTLED,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            reason=normalized_delivery_method,
        )
        raise MfaRateLimitedError(retry_after_seconds)

    previous_account_email_sent_at = account.last_mfa_email_sent_at
    previous_account_sms_sent_at = account.last_mfa_sms_sent_at
    if normalized_delivery_method == MFA_DELIVERY_METHOD_EMAIL:
        account.last_mfa_email_sent_at = now
    else:
        account.last_mfa_sms_sent_at = now
    cancel_pending_mfa_challenges(session, account.id, now=now)

    challenge_token = create_auth_token()
    code = create_mfa_code()
    challenge = PatientPortalMfaChallenge(
        account_id=account.id,
        challenge_token_hash=hash_auth_token(
            challenge_token_secret, "mfa_challenge", challenge_token
        ),
        code_hash=hash_mfa_code(code_secret, challenge_token, code),
        delivery_method=normalized_delivery_method,
        status=MFA_CHALLENGE_STATUS_PENDING,
        failed_attempts=account.failed_mfa_count,
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
        expected_code_hash=challenge.code_hash,
        previous_account_email_sent_at=previous_account_email_sent_at,
        previous_account_sms_sent_at=previous_account_sms_sent_at,
        reserved_cooldown=cooldown,
    )


def start_login(
    session: Session,
    *,
    username: str,
    password: str,
    client_reference_hash: str,
    policy: AuthPolicy,
    session_token_secret: str,
    mfa_challenge_token_secret: str,
    mfa_code_secret: str,
    clinic_id: str,
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

    # The row lock is taken before the Argon2 verification, not after, and is therefore held for
    # the full hash cost plus the MFA write path. That is deliberate: the failure counter and the
    # lockout transition it feeds have to be serialized, and a snapshot read followed by a lock
    # would let two concurrent wrong-password attempts each read the same count and write the same
    # increment — the lockout budget is the control, so it cannot be racy.
    #
    # The cost is that concurrent attempts against one username queue behind the hash. With
    # lock_timeout at 5s (database.py) roughly 40-50 simultaneous attempts on the same account
    # start timing out that account's legitimate logins into a 503. That is bounded per-account
    # rather than service-wide, and the per-client login limiter plus the Argon2 concurrency
    # semaphore in credentials.py cap how fast a single source can drive it — but a distributed
    # source aimed at one known username can still cause it. Revisit alongside the shared edge
    # limit the README requires before pilot traffic.
    account = session.scalar(
        select(PatientPortalAccount)
        .where(
            PatientPortalAccount.username == normalized_username,
            PatientPortalAccount.clinic_id == clinic_id,
        )
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
        password_is_valid = verify_account_password(
            session, account, password, client_reference_hash=client_reference_hash
        )
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
            reason=(
                AUTH_REASON_ACCOUNT_LOCKED if password_is_valid else AUTH_REASON_INVALID_CREDENTIALS
            ),
        )
        if password_is_valid:
            raise AccountLockedError()
        raise InvalidCredentialsError()

    if not verify_account_password(
        session, account, password, client_reference_hash=client_reference_hash
    ):
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
        raise InvalidCredentialsError()

    if password_needs_rehash(account.password_hash):
        account.password_hash = hash_password(password)
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
        preferred_delivery_method = normalize_mfa_delivery_method(account.preferred_mfa_method)
        if (
            delivery_method is None
            and preferred_delivery_method not in available_mfa_delivery_methods(account)
        ):
            preferred_delivery_method = MFA_DELIVERY_METHOD_EMAIL
        requested_delivery_method = normalize_mfa_delivery_method(
            delivery_method or preferred_delivery_method
        )
        try:
            mfa_challenge = create_mfa_challenge(
                session,
                account,
                delivery_method=requested_delivery_method,
                policy=policy,
                challenge_token_secret=mfa_challenge_token_secret,
                code_secret=mfa_code_secret,
                now=now,
            )
        except (MfaDeliveryUnavailableError, MfaRateLimitedError) as exc:
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
                reason=(
                    AUTH_REASON_DELIVERY_UNAVAILABLE
                    if isinstance(exc, MfaDeliveryUnavailableError)
                    else "mfa_rate_limited"
                ),
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
        session_token_secret=session_token_secret,
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
    challenge_token_secret: str,
) -> PatientPortalMfaChallenge | None:
    return session.scalar(
        select(PatientPortalMfaChallenge).where(
            PatientPortalMfaChallenge.challenge_token_hash
            == hash_auth_token(challenge_token_secret, "mfa_challenge", challenge_token)
        )
    )


def lock_mfa_challenge(
    session: Session,
    challenge_id: int,
) -> PatientPortalMfaChallenge | None:
    return session.scalar(
        select(PatientPortalMfaChallenge)
        .where(PatientPortalMfaChallenge.id == challenge_id)
        .with_for_update()
        .execution_options(populate_existing=True)
    )


def available_mfa_delivery_methods(
    account: PatientPortalAccount,
) -> tuple[str, ...]:
    methods: list[str] = []
    if account.email:
        methods.append(MFA_DELIVERY_METHOD_EMAIL)
    if normalize_phone_number(account.phone_number) is not None:
        methods.append(MFA_DELIVERY_METHOD_SMS)
    return tuple(methods)


def get_mfa_challenge_delivery_state(
    session: Session,
    challenge_token: str,
    *,
    challenge_token_secret: str,
    preferred_delivery_method: str | None = None,
) -> MfaChallengeDelivery | None:
    challenge = get_mfa_challenge_for_token(
        session,
        challenge_token,
        challenge_token_secret=challenge_token_secret,
    )
    if challenge is None or challenge.status != MFA_CHALLENGE_STATUS_PENDING:
        return None
    account = session.scalar(
        select(PatientPortalAccount).where(PatientPortalAccount.id == challenge.account_id)
    )
    if account is None or account.status != ACCOUNT_STATUS_ACTIVE:
        return None

    available_methods = available_mfa_delivery_methods(account)
    delivery_method = challenge.delivery_method
    if preferred_delivery_method is not None:
        try:
            normalized_preferred_method = normalize_mfa_delivery_method(preferred_delivery_method)
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
        expected_code_hash=challenge.code_hash,
    )


def resend_mfa_challenge(
    session: Session,
    *,
    challenge_token: str,
    delivery_method: str,
    policy: AuthPolicy,
    challenge_token_secret: str,
    code_secret: str,
) -> MfaChallengeDelivery:
    now = utc_now()
    challenge = get_mfa_challenge_for_token(
        session,
        challenge_token,
        challenge_token_secret=challenge_token_secret,
    )
    if challenge is None:
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_MFA_RESEND,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            reason=AUTH_REASON_MFA_EXPIRED,
        )
        raise MfaChallengeNotFoundError()

    account = session.scalar(
        select(PatientPortalAccount)
        .where(PatientPortalAccount.id == challenge.account_id)
        .with_for_update()
    )
    if account is None or account.status != ACCOUNT_STATUS_ACTIVE:
        raise MfaChallengeNotFoundError()
    challenge = lock_mfa_challenge(session, challenge.id)
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
    if account.locked_at is not None:
        raise AccountLockedError()
    if account.force_password_reset:
        raise PasswordResetRequiredError()

    normalized_delivery_method = normalize_mfa_delivery_method(delivery_method)
    try:
        ensure_mfa_delivery_available(account, normalized_delivery_method)
    except MfaDeliveryUnavailableError:
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_MFA_RESEND,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            reason=AUTH_REASON_DELIVERY_UNAVAILABLE,
        )
        raise
    if normalized_delivery_method == MFA_DELIVERY_METHOD_EMAIL:
        cooldown = policy.mfa_email_resend_cooldown
        last_sent_at = account.last_mfa_email_sent_at
    else:
        cooldown = policy.mfa_sms_resend_cooldown
        last_sent_at = account.last_mfa_sms_sent_at

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

    previous_account_email_sent_at = account.last_mfa_email_sent_at
    previous_account_sms_sent_at = account.last_mfa_sms_sent_at
    if normalized_delivery_method == MFA_DELIVERY_METHOD_EMAIL:
        account.last_mfa_email_sent_at = now
    else:
        account.last_mfa_sms_sent_at = now
    previous_code_hash = challenge.code_hash
    previous_delivery_method = challenge.delivery_method
    previous_expires_at = challenge.expires_at
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
        expected_code_hash=challenge.code_hash,
        previous_code_hash=previous_code_hash,
        previous_delivery_method=previous_delivery_method,
        previous_expires_at=previous_expires_at,
        previous_account_email_sent_at=previous_account_email_sent_at,
        previous_account_sms_sent_at=previous_account_sms_sent_at,
        reserved_cooldown=cooldown,
    )


def record_mfa_delivery_outcome(
    session: Session,
    *,
    delivery: MfaChallengeDelivery,
    outcome: str,
) -> None:
    if outcome not in {AUDIT_OUTCOME_SUCCESS, AUDIT_OUTCOME_FAILURE}:
        raise ValueError("delivery outcome must be success or failure")

    challenge_locator = session.scalar(
        select(PatientPortalMfaChallenge).where(
            PatientPortalMfaChallenge.id == delivery.challenge_id
        )
    )
    if challenge_locator is None:
        raise MfaChallengeNotFoundError()
    account = session.scalar(
        select(PatientPortalAccount)
        .where(PatientPortalAccount.id == challenge_locator.account_id)
        .with_for_update()
    )
    if account is None:
        raise MfaChallengeNotFoundError()
    challenge = lock_mfa_challenge(session, challenge_locator.id)
    if challenge is None or challenge.account_id != account.id:
        raise MfaChallengeNotFoundError()
    if not compare_digest(challenge.code_hash, delivery.expected_code_hash):
        # A newer resend replaced this delivery's code while its provider call was in flight.
        # Its eventual result no longer owns either the challenge or the channel cooldown.
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_MFA_DELIVERY,
            outcome=outcome,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            actor=account.username,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            account_id=account.id,
            reason=f"{delivery.delivery_method}:superseded",
        )
        return

    if outcome == AUDIT_OUTCOME_SUCCESS:
        delivered_at = utc_now()
        challenge.updated_at = delivered_at
        if delivery.delivery_method == MFA_DELIVERY_METHOD_EMAIL:
            challenge.last_email_sent_at = delivered_at
            account.last_mfa_email_sent_at = delivered_at
        else:
            challenge.last_sms_sent_at = delivered_at
            account.last_mfa_sms_sent_at = delivered_at
    else:
        # A reported failure is not proof that nothing was delivered — greylisting, an SMTP
        # timeout after DATA is accepted, and a gateway that 5xx's after queueing all report
        # failure having sent. Releasing the reservation outright would therefore hand a degraded
        # provider an unbounded per-account send rate, because these timestamps are the only
        # account-scoped limit resend_mfa_challenge consults. Shorten the reservation instead: the
        # patient retries within seconds rather than a full window, and the rate stays bounded.
        failed_at = utc_now()
        cooldown = delivery.reserved_cooldown
        if delivery.delivery_method == MFA_DELIVERY_METHOD_EMAIL:
            account.last_mfa_email_sent_at = (
                shorten_delivery_reservation(
                    previous_sent_at=delivery.previous_account_email_sent_at,
                    now=failed_at,
                    cooldown=cooldown,
                )
                if cooldown is not None
                else delivery.previous_account_email_sent_at
            )
        else:
            account.last_mfa_sms_sent_at = (
                shorten_delivery_reservation(
                    previous_sent_at=delivery.previous_account_sms_sent_at,
                    now=failed_at,
                    cooldown=cooldown,
                )
                if cooldown is not None
                else delivery.previous_account_sms_sent_at
            )
        if (
            delivery.previous_code_hash is not None
            and delivery.previous_delivery_method is not None
            and delivery.previous_expires_at is not None
        ):
            # Resend failure: restore the code the patient still holds, alongside its cooldown.
            challenge.code_hash = delivery.previous_code_hash
            challenge.delivery_method = delivery.previous_delivery_method
            challenge.expires_at = delivery.previous_expires_at
            challenge.updated_at = utc_now()
        else:
            challenge.status = MFA_CHALLENGE_STATUS_CANCELLED
            challenge.updated_at = utc_now()

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
    challenge_token_secret: str,
    session_token_secret: str,
    code_secret: str,
) -> str:
    now = utc_now()
    challenge = get_mfa_challenge_for_token(
        session,
        challenge_token,
        challenge_token_secret=challenge_token_secret,
    )
    if challenge is None:
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_MFA_VERIFY,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            reason=AUTH_REASON_MFA_EXPIRED,
        )
        raise MfaChallengeNotFoundError()

    account = session.scalar(
        select(PatientPortalAccount)
        .where(PatientPortalAccount.id == challenge.account_id)
        .with_for_update()
    )
    if account is None or account.status != ACCOUNT_STATUS_ACTIVE:
        raise MfaChallengeNotFoundError()
    challenge = lock_mfa_challenge(session, challenge.id)
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
    if account.locked_at is not None:
        raise AccountLockedError()
    if account.force_password_reset:
        raise PasswordResetRequiredError()

    try:
        supplied_code_hash = hash_mfa_code(code_secret, challenge_token, code)
    except ValueError:
        supplied_code_hash = ""
    if not compare_digest(challenge.code_hash, supplied_code_hash):
        account.failed_mfa_count += 1
        challenge.failed_attempts = account.failed_mfa_count
        challenge.updated_at = now
        lockout_reached = account.failed_mfa_count >= policy.mfa_max_failed_attempts
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
        session_token_secret=session_token_secret,
        now=now,
    )
    account.last_login_at = now
    account.failed_login_count = 0
    account.failed_mfa_count = 0
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
    reset_token_secret: str,
    clinic_id: str,
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

    # Scoped like login: a clinic runtime may only issue resets for its own accounts.
    account = session.scalar(
        select(PatientPortalAccount)
        .where(
            PatientPortalAccount.username == normalized_username,
            PatientPortalAccount.clinic_id == clinic_id,
        )
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
        token_hash=hash_auth_token(reset_token_secret, "password_reset", reset_token_value),
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
        reset_token_id=reset_token.id,
        account_id=account.id,
    )


def record_password_reset_delivery_outcome(
    session: Session,
    *,
    result: PasswordResetRequestResult,
    outcome: str,
) -> None:
    if outcome not in {AUDIT_OUTCOME_SUCCESS, AUDIT_OUTCOME_FAILURE}:
        raise ValueError("delivery outcome must be success or failure")
    if result.reset_token_id is None or result.account_id is None:
        raise PasswordResetTokenInvalidError()

    account = session.scalar(
        select(PatientPortalAccount)
        .where(PatientPortalAccount.id == result.account_id)
        .with_for_update()
    )
    reset_record = session.scalar(
        select(PatientPortalPasswordResetToken)
        .where(PatientPortalPasswordResetToken.id == result.reset_token_id)
        .with_for_update()
        .execution_options(populate_existing=True)
    )
    if reset_record is None or account is None or reset_record.account_id != account.id:
        raise PasswordResetTokenInvalidError()
    if outcome == AUDIT_OUTCOME_FAILURE and reset_record.status == PASSWORD_RESET_STATUS_PENDING:
        reset_record.status = PASSWORD_RESET_STATUS_REVOKED

    record_audit_event(
        session,
        event_type=AUDIT_EVENT_PASSWORD_RESET_DELIVERY,
        outcome=outcome,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        reason=MFA_DELIVERY_METHOD_EMAIL,
    )


def complete_password_reset(
    session: Session,
    *,
    reset_token: str,
    new_password: str,
    reset_token_secret: str,
    clinic_id: str,
) -> PatientPortalAccount:
    validate_password(new_password)
    now = utc_now()
    token_hash = hash_auth_token(reset_token_secret, "password_reset", reset_token)
    # Scope the lookup itself by the owning account's clinic so a foreign-clinic token is simply
    # never found here, rather than being located and then revoked by an unrelated runtime.
    reset_locator = session.execute(
        select(
            PatientPortalPasswordResetToken.id,
            PatientPortalPasswordResetToken.account_id,
        )
        .join(
            PatientPortalAccount,
            PatientPortalAccount.id == PatientPortalPasswordResetToken.account_id,
        )
        .where(
            PatientPortalPasswordResetToken.token_hash == token_hash,
            PatientPortalAccount.clinic_id == clinic_id,
        )
    ).one_or_none()
    if reset_locator is None:
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            reason=AUTH_REASON_INVALID_RESET_TOKEN,
        )
        raise PasswordResetTokenInvalidError()

    account = session.scalar(
        select(PatientPortalAccount)
        .where(PatientPortalAccount.id == reset_locator.account_id)
        .with_for_update()
        .execution_options(populate_existing=True)
    )
    reset_record = session.scalar(
        select(PatientPortalPasswordResetToken)
        .where(
            PatientPortalPasswordResetToken.id == reset_locator.id,
            PatientPortalPasswordResetToken.token_hash == token_hash,
        )
        .with_for_update()
        .execution_options(populate_existing=True)
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

    if (
        account is None
        or reset_record.account_id != account.id
        or account.status != ACCOUNT_STATUS_ACTIVE
        or account.locked_at is not None
    ):
        reset_record.status = PASSWORD_RESET_STATUS_REVOKED
        record_audit_event(
            session,
            event_type=AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
            outcome=AUDIT_OUTCOME_FAILURE,
            actor_type=AUDIT_ACTOR_TYPE_PATIENT,
            reason=AUTH_REASON_INVALID_RESET_TOKEN,
        )
        raise PasswordResetTokenInvalidError()

    account.password_hash = hash_password(new_password)
    account.password_updated_at = now
    account.failed_login_count = 0
    account.failed_mfa_count = 0
    account.locked_at = None
    account.locked_by = None
    account.locked_by_id = None
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
    session_token_secret: str,
    idle_timeout: timedelta,
) -> AuthenticatedPortalSession:
    if idle_timeout <= timedelta(0):
        raise ValueError("idle_timeout must be positive")
    now = utc_now()
    portal_session = session.scalar(
        select(PatientPortalSession).where(
            PatientPortalSession.token_hash
            == hash_auth_token(session_token_secret, "session", session_token)
        )
    )
    if (
        portal_session is None
        or portal_session.revoked_at is not None
        or is_past(portal_session.expires_at, now)
    ):
        raise PortalSessionInvalidError()
    last_seen_at = portal_session.last_seen_at or portal_session.created_at
    if is_past(last_seen_at + idle_timeout, now):
        portal_session.revoked_at = now
        portal_session.revoked_reason = SESSION_REVOKED_REASON_IDLE_TIMEOUT
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

    last_seen_cutoff = now - SESSION_LAST_SEEN_UPDATE_INTERVAL
    session.execute(
        update(PatientPortalSession)
        .where(
            PatientPortalSession.id == portal_session.id,
            PatientPortalSession.revoked_at.is_(None),
            or_(
                PatientPortalSession.last_seen_at.is_(None),
                PatientPortalSession.last_seen_at < last_seen_cutoff,
            ),
        )
        .values(last_seen_at=now)
        .execution_options(synchronize_session=False)
    )
    return AuthenticatedPortalSession(account=account, portal_session=portal_session)


def logout_patient_session(
    session: Session,
    *,
    session_token: str,
    session_token_secret: str,
    idle_timeout: timedelta,
) -> None:
    authenticated_session = authenticate_session_token(
        session,
        session_token=session_token,
        session_token_secret=session_token_secret,
        idle_timeout=idle_timeout,
    )
    portal_session = session.scalar(
        select(PatientPortalSession)
        .where(PatientPortalSession.id == authenticated_session.portal_session.id)
        .with_for_update()
    )
    if portal_session is None or portal_session.revoked_at is not None:
        raise PortalSessionInvalidError()
    now = utc_now()
    portal_session.revoked_at = now
    portal_session.revoked_reason = SESSION_REVOKED_REASON_LOGOUT
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
    actor_id: str | None = None,
) -> PatientPortalAccount:
    normalized_actor = normalize_staff_actor(actor)
    normalized_actor_id = normalize_staff_actor(actor if actor_id is None else actor_id)
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
    account.failed_mfa_count = 0
    account.locked_at = None
    account.locked_by = None
    account.locked_by_id = None
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
        actor_id=normalized_actor_id,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        reason=AUTH_REASON_FORCE_PASSWORD_RESET,
    )
    return account


def set_patient_account_access(
    session: Session,
    account_id: int,
    actor: str,
    *,
    enabled: bool,
    clinic_id: str,
    actor_id: str | None = None,
    reason: str | None = None,
) -> PatientPortalAccount:
    normalized_actor = normalize_staff_actor(actor)
    normalized_actor_id = normalize_staff_actor(actor if actor_id is None else actor_id)
    normalized_clinic_id = normalize_clinic_id(clinic_id)
    normalized_reason = (reason or "staff_action").strip()
    if not normalized_reason or len(normalized_reason) > 64:
        raise ValueError("reason must contain 1 to 64 characters")
    account = session.scalar(
        select(PatientPortalAccount)
        .where(
            PatientPortalAccount.id == account_id,
            PatientPortalAccount.clinic_id == normalized_clinic_id,
        )
        .with_for_update()
    )
    if account is None:
        raise AccountNotFoundError()

    now = utc_now()
    desired_status = ACCOUNT_STATUS_ACTIVE if enabled else ACCOUNT_STATUS_DISABLED
    if account.status != desired_status:
        account.status = desired_status
        account.updated_at = now
        if enabled:
            account.disabled_at = None
            account.disabled_by = None
            account.disabled_by_id = None
            account.disabled_reason = None
            account.force_password_reset = True
        else:
            account.disabled_at = now
            account.disabled_by = normalized_actor
            account.disabled_by_id = normalized_actor_id
            account.disabled_reason = normalized_reason
            revoke_account_sessions(
                session,
                account.id,
                reason=SESSION_REVOKED_REASON_ACCOUNT_DISABLED,
                now=now,
            )
            cancel_pending_mfa_challenges(session, account.id, now=now)
            revoke_pending_password_reset_tokens(session, account.id)

    record_audit_event(
        session,
        event_type=(AUDIT_EVENT_ACCOUNT_ENABLE if enabled else AUDIT_EVENT_ACCOUNT_DISABLE),
        outcome=AUDIT_OUTCOME_SUCCESS,
        actor_type=AUDIT_ACTOR_TYPE_STAFF,
        actor=normalized_actor,
        actor_id=normalized_actor_id,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        reason=normalized_reason,
    )
    return account
