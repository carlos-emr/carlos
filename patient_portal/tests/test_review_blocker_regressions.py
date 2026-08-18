"""Regression cover for the blocking defects found in the PR #3220 review.

Each test here pins a control that was either absent or defeated, and would fail again if the
corresponding fix were reverted. Grouped in one module deliberately: they share no theme beyond
being the security regressions that review turned up, and keeping them together makes it obvious
which behaviour is load-bearing for that review.
"""

from datetime import timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from carlos_patient_portal import auth
from carlos_patient_portal.auth import (
    AUTH_LOCKED_BY_AUTOMATION,
    hash_auth_token,
    hash_mfa_code,
)
from carlos_patient_portal.delivery_outbox import (
    enqueue_contact_change_delivery,
    process_one_delivery,
)
from carlos_patient_portal.email_delivery import PortalEmailDeliveryError
from carlos_patient_portal.models import (
    AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
    AUDIT_EVENT_ACCOUNT_UNLOCK,
    AUDIT_OUTCOME_FAILURE,
    OUTBOX_KIND_CONTACT_CHANGE,
    OUTBOX_STATUS_FAILED,
    PatientPortalAccount,
    PatientPortalAuditEvent,
    PatientPortalOutboundDelivery,
    PatientPortalPasswordResetToken,
    utc_now,
)
from carlos_patient_portal.web_support import is_rate_limited_path
from tests.support import (
    INTERNAL_API_TOKEN,
    OUTBOX_ENCRYPTION_SECRET,
    SEEDED_INVITE_EMAIL,
    STRONG_PASSWORD,
    STRONG_RESET_PASSWORD,
    activate_seeded_patient_account,
    migrated_development_app,
)

SECRET = "regression-secret-value-32-characters"
SEEDED_USERNAME = "patient.user"


# --------------------------------------------------------------------------------------
# Blocker 4 - MFA challenge-token normalization asymmetry
# --------------------------------------------------------------------------------------


def test_mfa_code_hash_normalizes_the_challenge_token_like_the_lookup_hash() -> None:
    """A padded challenge token must not produce a code hash the clean token can never match.

    hash_auth_token strips before hashing, so a padded token still resolved to a real challenge. If
    hash_mfa_code does not strip identically, that challenge's code_hash is keyed on the padded form
    and the correct code can never verify -- while every rejected attempt spends the MFA failure
    budget toward a lockout.
    """
    padded = " abc123\n"
    clean = "abc123"

    assert hash_auth_token(SECRET, "mfa_challenge", padded) == hash_auth_token(
        SECRET, "mfa_challenge", clean
    )
    assert hash_mfa_code(SECRET, padded, "123456") == hash_mfa_code(SECRET, clean, "123456")


def test_mfa_code_hash_rejects_a_blank_challenge_token() -> None:
    with pytest.raises(ValueError):
        hash_mfa_code(SECRET, "   ", "123456")


# --------------------------------------------------------------------------------------
# Blocker 2 - unauthenticated write amplification against the internal API
# --------------------------------------------------------------------------------------


def test_internal_carlos_prefix_is_rate_limited_but_probe_endpoints_are_not() -> None:
    """Every failed /internal/carlos/** request writes an audit row, so it must be throttled.

    The probe endpoints must stay unthrottled: an orchestrator polls them on a fixed interval and a
    429 there turns a healthy service into a failing liveness check.
    """
    assert is_rate_limited_path("/internal/carlos/patients/1/unlock-secrets")
    assert is_rate_limited_path("/internal/carlos/contact-reviews")

    assert not is_rate_limited_path("/internal/health/db")
    assert not is_rate_limited_path("/internal/readiness")
    assert not is_rate_limited_path("/internal/metrics")

    # The patient-facing surface is unchanged.
    assert is_rate_limited_path("/auth/login")
    assert is_rate_limited_path("/portal/account")


def test_unauthenticated_internal_failures_are_attributable_to_a_client() -> None:
    """Without a client reference every unauthenticated failure row is identical.

    A flood then cannot be told apart from one misconfigured caller, which is precisely the signal
    the middleware exists to preserve.
    """
    # The internal router is only mounted when a service token is configured.
    app = migrated_development_app(internal_api_token=INTERNAL_API_TOKEN)
    client = TestClient(app)

    # Asserted so this cannot pass vacuously against an unmounted router: the audit middleware keys
    # off the path prefix, so a genuinely missing route would still produce a row.
    assert "/internal/carlos/contact-reviews" in {
        getattr(route, "path", "") for route in app.routes
    }

    response = client.get("/internal/carlos/contact-reviews")
    # 404, not 401: service-auth failure deliberately fails closed without confirming the endpoint
    # exists to an unauthenticated caller.
    assert response.status_code == 404

    with app.state.session_factory() as session:
        events = list(
            session.scalars(
                select(PatientPortalAuditEvent).where(
                    PatientPortalAuditEvent.resource_type == "internal_api"
                )
            )
        )

    assert events, "a failed internal request must leave an audit row"
    assert all(event.client_reference_hash is not None for event in events)


# --------------------------------------------------------------------------------------
# Blocker 3 - permanent, remotely triggerable account lockout
# --------------------------------------------------------------------------------------


def drive_account_into_lockout(client: TestClient, attempts: int) -> None:
    for _ in range(attempts):
        client.post(
            "/auth/login",
            json={"username": SEEDED_USERNAME, "password": "Wrong1!password"},
        )


def test_automated_lockout_expires_and_restores_self_service_sign_in(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """An automated lockout is time-boxed, so a remote attacker cannot permanently deny access.

    Ten wrong passwords against a known username used to set locked_at with nothing in the system
    able to clear it except clinic staff.
    """
    app = migrated_development_app(auth_max_failed_password_attempts=2)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    drive_account_into_lockout(client, attempts=2)
    locked_response = client.post(
        "/auth/login",
        json={"username": SEEDED_USERNAME, "password": STRONG_PASSWORD},
    )
    assert locked_response.status_code == 423

    started_at = utc_now()
    monkeypatch.setattr(auth, "utc_now", lambda: started_at + timedelta(seconds=901))
    recovered_response = client.post(
        "/auth/login",
        json={"username": SEEDED_USERNAME, "password": STRONG_PASSWORD},
    )

    # 423 (locked, staff-only exit) has become 403 password_reset_required, which is recoverable
    # in-band. force_password_reset is deliberately left set: the lock cannot distinguish "attacker
    # never had the password" from an MFA-failure lock where they did, so a credential refresh stays
    # the conservative default. What matters is that the patient can now complete it themselves.
    assert recovered_response.status_code == 403
    assert recovered_response.json() == {"status": "password_reset_required"}

    reset_request_response = client.post(
        "/auth/password-reset/request",
        json={"username": SEEDED_USERNAME, "email": SEEDED_INVITE_EMAIL},
    )
    assert reset_request_response.status_code == 202
    complete_reset_response = client.post(
        "/auth/password-reset/complete",
        json={
            "reset_token": reset_request_response.json()["development_reset_token"],
            "new_password": STRONG_RESET_PASSWORD,
        },
    )
    assert complete_reset_response.status_code == 200
    final_login_response = client.post(
        "/auth/login",
        json={"username": SEEDED_USERNAME, "password": STRONG_RESET_PASSWORD},
    )
    assert final_login_response.status_code == 200
    assert final_login_response.json()["status"] == "mfa_required"

    with app.state.session_factory() as session:
        stored = session.get(PatientPortalAccount, account_id)
        assert stored is not None
        assert stored.locked_at is None
        assert stored.failed_login_count == 0
        unlock_events = list(
            session.scalars(
                select(PatientPortalAuditEvent).where(
                    PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_UNLOCK
                )
            )
        )
    assert unlock_events, "an expired lockout must leave an audit record of the release"


def test_staff_initiated_lock_is_never_released_by_the_expiry(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Only automation-stamped locks are time-boxed; a deliberate staff lock must survive."""
    app = migrated_development_app(auth_max_failed_password_attempts=2)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    with app.state.session_factory() as session:
        with session.begin():
            stored = session.get(PatientPortalAccount, account_id)
            assert stored is not None
            stored.locked_at = utc_now() - timedelta(days=30)
            stored.locked_by = "provider-42"
            stored.locked_by_id = "provider-42"

    response = client.post(
        "/auth/login",
        json={"username": SEEDED_USERNAME, "password": STRONG_PASSWORD},
    )

    assert response.status_code == 423
    with app.state.session_factory() as session:
        stored = session.get(PatientPortalAccount, account_id)
        assert stored is not None
        assert stored.locked_at is not None


def test_locked_out_account_can_still_request_a_password_reset() -> None:
    """The reset path is the patient's only self-service route back in.

    Eligibility previously required locked_at to be null, so the endpoint answered 202 "reset link
    sent" and sent nothing at all.
    """
    app = migrated_development_app(auth_max_failed_password_attempts=2)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    drive_account_into_lockout(client, attempts=2)
    response = client.post(
        "/auth/password-reset/request",
        json={"username": SEEDED_USERNAME, "email": SEEDED_INVITE_EMAIL},
    )

    assert response.status_code == 202
    with app.state.session_factory() as session:
        stored = session.get(PatientPortalAccount, account_id)
        assert stored is not None
        assert stored.locked_by == AUTH_LOCKED_BY_AUTOMATION
        issued = list(
            session.scalars(
                select(PatientPortalPasswordResetToken).where(
                    PatientPortalPasswordResetToken.account_id == account_id
                )
            )
        )
    assert issued, "a locked-out patient must still be issued a reset token"


# --------------------------------------------------------------------------------------
# Blocker 5 - contact-change delivery failure left no audit evidence
# --------------------------------------------------------------------------------------


class AlwaysFailingEmailSender:
    """Stands in for an SMTP outage across every send the outbox can attempt."""

    def send_password_reset(self, *args: object, **kwargs: object) -> None:
        raise PortalEmailDeliveryError("smtp unavailable")

    def send_contact_change_notice(self, *args: object, **kwargs: object) -> None:
        raise PortalEmailDeliveryError("smtp unavailable")


def test_exhausted_contact_change_notice_records_a_failure_audit_event() -> None:
    """The notice to the address a change moved away from is the only out-of-band alarm a patient
    gets. Exhausting the retry budget previously produced a `failed` row and nothing else, so a
    breach review could not enumerate the patients who were never warned.
    """
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    with app.state.session_factory() as session:
        with session.begin():
            delivery = enqueue_contact_change_delivery(
                session,
                account_id=account_id,
                recipient="previous@example.test",
                encryption_secret=OUTBOX_ENCRYPTION_SECRET,
            )
            session.flush()
            delivery_id = delivery.id

    for _ in range(8):
        process_one_delivery(
            app.state.session_factory,
            email_sender=AlwaysFailingEmailSender(),
            encryption_secret=OUTBOX_ENCRYPTION_SECRET,
            max_attempts=8,
            lease_seconds=30,
            delivery_id=delivery_id,
        )
        # Each failure pushes available_at out by the retry backoff, and a row is only claimable
        # once it is due. Pull it back rather than sleeping so the test exercises the full retry
        # budget without wall-clock delay.
        with app.state.session_factory() as session:
            with session.begin():
                queued = session.get(PatientPortalOutboundDelivery, delivery_id)
                if queued is not None:
                    queued.available_at = utc_now() - timedelta(seconds=1)

    with app.state.session_factory() as session:
        stored = session.get(PatientPortalOutboundDelivery, delivery_id)
        assert stored is not None
        assert stored.status == OUTBOX_STATUS_FAILED
        assert stored.kind == OUTBOX_KIND_CONTACT_CHANGE

        failures = list(
            session.scalars(
                select(PatientPortalAuditEvent).where(
                    PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
                    PatientPortalAuditEvent.outcome == AUDIT_OUTCOME_FAILURE,
                )
            )
        )

    assert failures, "a terminally failed contact-change notice must leave a failure audit row"
    assert failures[0].account_id == account_id
