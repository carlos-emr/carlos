"""Login, MFA, sessions, lockout, and password reset."""

import re
from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from urllib.parse import parse_qs, urlsplit

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from carlos_patient_portal import auth, main, web_support
from carlos_patient_portal.account_settings import update_account_mfa_method
from carlos_patient_portal.auth import (
    MFA_DELIVERY_FAILURE_RETRY_GRACE,
    seconds_until_allowed,
)
from carlos_patient_portal.database import (
    create_portal_engine,
    create_session_factory,
    session_scope,
)
from carlos_patient_portal.email_delivery import PortalEmailDeliveryError
from carlos_patient_portal.models import (
    AUDIT_EVENT_ACCOUNT_LOCK,
    AUDIT_EVENT_ACCOUNT_PASSWORD_CHANGE,
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
    EMAIL_CHANGE_STATUS_REVOKED,
    PASSWORD_RESET_STATUS_USED,
    SESSION_REVOKED_REASON_PASSWORD_CHANGE,
    PatientPortalAccount,
    PatientPortalAuditEvent,
    PatientPortalEmailChangeRequest,
    PatientPortalInvite,
    PatientPortalMfaChallenge,
    PatientPortalPasswordResetToken,
    PatientPortalSession,
    utc_now,
)
from tests.support import (
    CONCURRENT_WRONG_PASSWORD,
    CSRF_TOKEN_PATTERN,
    DEVELOPMENT_MFA_CODE_PATTERN,
    MFA_CHALLENGE_TOKEN_PATTERN,
    SEEDED_INVITE_EMAIL,
    STRONG_PASSWORD,
    STRONG_RESET_PASSWORD,
    RecordingPortalEmailSender,
    RecordingPortalSmsSender,
    activate_seeded_patient_account,
    bearer_headers,
    browser_sign_in_seeded_patient,
    confirm_seeded_email_change,
    create_service_invite,
    csrf_token_from_response,
    dev_admin_headers,
    development_settings,
    expire_email_mfa_cooldown,
    get_csrf_token,
    migrated_development_app,
    request_seeded_email_change,
    sign_in_patient_api_session,
    upgrade_to_head,
)


def assert_browser_notice(response, *, status_code: int, leaked_detail: str) -> None:
    """A rejected browser form must render the portal's page, not a raw JSON body.

    Registering an HTTPException handler changed these responses deliberately: a patient who
    left a page open past the 60-minute CSRF TTL previously got {"detail": ...} in their
    browser window with no way back. The rejection itself is unchanged - same status, same
    refusal - so each caller still asserts its own security consequence separately.
    """
    assert response.status_code == status_code
    assert response.headers["content-type"].startswith("text/html")
    assert "Request could not be completed" in response.text
    assert f'"{leaked_detail}"' not in response.text


def test_file_sqlite_concurrent_login_failures_do_not_return_raw_500(tmp_path) -> None:
    database_path = tmp_path / "concurrent-login.db"
    app = migrated_development_app(
        database_url=f"sqlite+pysqlite:///{database_path}",
        auth_max_failed_password_attempts=100,
        sqlite_busy_timeout_ms=10_000,
    )
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    with ThreadPoolExecutor(max_workers=6) as executor:
        responses = list(
            executor.map(
                lambda _: client.post(
                    "/auth/login",
                    json={
                        "username": "patient.user",
                        "password": CONCURRENT_WRONG_PASSWORD,
                    },
                ),
                range(6),
            )
        )

    assert all(response.status_code in {401, 503} for response in responses)
    assert all(response.status_code != 500 for response in responses)


def test_login_rate_limit_runs_before_repeated_password_verification() -> None:
    app = migrated_development_app(auth_rate_limit_max_requests=2)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    responses = [
        client.post(
            "/auth/login",
            json={"username": "patient.user", "password": "Wrong1!password"},
        )
        for _ in range(3)
    ]

    assert [response.status_code for response in responses] == [401, 401, 429]


def test_browser_login_validation_renders_sign_in_page() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    sign_in_page = client.get("/")

    response = client.post(
        "/auth/login",
        data={"csrf_token": csrf_token_from_response(sign_in_page), "username": ""},
    )

    assert response.status_code == 400
    assert "text/html" in response.headers["content-type"]
    assert "Sign in" in response.text


def test_login_mfa_session_and_logout_happy_path() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    login_response = client.post(
        "/auth/login",
        json={"username": "Patient.User", "password": STRONG_PASSWORD},
    )

    assert login_response.status_code == 200
    login_payload = login_response.json()
    assert login_payload["status"] == "mfa_required"
    assert login_payload["mfa_delivery_method"] == "email"
    assert login_payload["mfa_challenge_token"]
    assert re.fullmatch(r"\d{6}", login_payload["development_mfa_code"])
    assert login_payload["session_token"] is None
    assert login_response.headers["cache-control"] == "no-store"

    verify_response = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": login_payload["mfa_challenge_token"],
            "code": login_payload["development_mfa_code"],
        },
    )

    assert verify_response.status_code == 200
    session_token = verify_response.json()["session_token"]
    assert session_token
    assert web_support.PORTAL_SESSION_COOKIE_NAME not in verify_response.cookies

    session_response = client.get(
        "/auth/session",
        headers={"Authorization": f"Bearer {session_token}"},
    )

    assert session_response.status_code == 200
    assert session_response.json() == {
        "status": "authenticated",
        "username": "patient.user",
        "clinic_id": "default",
        "demographic_no": 1234,
    }

    logout_response = client.post(
        "/auth/logout",
        headers={"Authorization": f"Bearer {session_token}"},
    )
    expired_session_response = client.get(
        "/auth/session",
        headers={"Authorization": f"Bearer {session_token}"},
    )

    assert logout_response.status_code == 200
    assert logout_response.json() == {"status": "logged_out"}
    assert expired_session_response.status_code == 401
    with app.state.session_factory() as session:
        portal_session = session.scalar(select(PatientPortalSession))
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type.in_(
                        [
                            AUDIT_EVENT_LOGIN,
                            AUDIT_EVENT_MFA_CHALLENGE,
                            AUDIT_EVENT_MFA_DELIVERY,
                            AUDIT_EVENT_MFA_VERIFY,
                            AUDIT_EVENT_SESSION_LOGOUT,
                        ]
                    )
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert portal_session is not None
        assert portal_session.account_id == account_id
        assert portal_session.revoked_reason == "logout"
        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_MFA_CHALLENGE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_LOGIN, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_MFA_DELIVERY, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_MFA_VERIFY, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_SESSION_LOGOUT, AUDIT_OUTCOME_SUCCESS),
            # Re-using the revoked token is a rejected authentication and is now audited, so a
            # token replayed after logout cannot be probed without leaving a trace.
            (AUDIT_EVENT_LOGIN, AUDIT_OUTCOME_FAILURE),
        ]
        assert audit_events[-1].reason == "authentication_failed"


def test_login_sends_mfa_email_after_committing_challenge() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    sender.app = app
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    response = client.post(
        "/auth/login",
        json={"username": "Patient.User", "password": STRONG_PASSWORD},
    )

    assert response.status_code == 200
    assert sender.challenge_was_committed is True
    assert sender.messages == [
        {
            "recipient": SEEDED_INVITE_EMAIL,
            "code": response.json()["development_mfa_code"],
            "expires_in_seconds": 600,
        }
    ]
    with app.state.session_factory() as session:
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        assert challenge is not None
        assert challenge.last_email_sent_at is not None


def test_login_mfa_delivery_failure_is_generic_and_audited() -> None:
    sender = RecordingPortalEmailSender(fail=True)
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    response = client.post(
        "/auth/login",
        json={"username": "Patient.User", "password": STRONG_PASSWORD},
    )

    assert response.status_code == 503
    assert response.json() == {"detail": "verification code could not be sent"}
    assert len(sender.messages) == 1
    sent_code = sender.messages[0]["code"]
    assert isinstance(sent_code, str)
    assert sent_code not in response.text
    with app.state.session_factory() as session:
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        delivery_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_MFA_DELIVERY
            )
        )

        assert challenge is not None
        assert challenge.last_email_sent_at is None
        assert delivery_event is not None
        assert delivery_event.outcome == AUDIT_OUTCOME_FAILURE
        assert sent_code not in (delivery_event.reason or "")


def test_failed_mfa_method_switch_preserves_the_previous_delivered_code() -> None:
    email_sender = RecordingPortalEmailSender()
    sms_sender = RecordingPortalSmsSender(fail=True)
    app = migrated_development_app(
        email_sender=email_sender,
        sms_sender=sms_sender,
    )
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        with session.begin():
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            account.phone_number = "+16135550199"

    login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    challenge_token = login.json()["mfa_challenge_token"]
    original_code = email_sender.messages[-1]["code"]
    switched = client.post(
        "/auth/mfa/resend",
        json={
            "mfa_challenge_token": challenge_token,
            "mfa_delivery_method": "sms",
        },
    )
    verified = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": challenge_token,
            "code": original_code,
        },
    )

    assert switched.status_code == 503
    assert verified.status_code == 200


def test_mfa_email_resend_delivers_new_code_after_cooldown() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    login_response = client.post(
        "/auth/login",
        json={"username": "Patient.User", "password": STRONG_PASSWORD},
    )
    with app.state.session_factory() as session:
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        assert challenge is not None
        assert challenge.last_email_sent_at is not None
        challenge.last_email_sent_at -= timedelta(seconds=61)
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.last_mfa_email_sent_at is not None
        account.last_mfa_email_sent_at -= timedelta(seconds=61)
        session.commit()

    resend_response = client.post(
        "/auth/mfa/resend",
        json={
            "mfa_challenge_token": login_response.json()["mfa_challenge_token"],
            "mfa_delivery_method": "email",
        },
    )

    assert resend_response.status_code == 200
    assert len(sender.messages) == 2
    assert sender.messages[0]["code"] != sender.messages[1]["code"]
    assert sender.messages[1] == {
        "recipient": SEEDED_INVITE_EMAIL,
        "code": resend_response.json()["development_mfa_code"],
        "expires_in_seconds": 600,
    }


def test_fresh_login_enforces_account_mfa_cooldown_and_carries_failure_budget() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    first = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    immediate = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    failed_code = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": first.json()["mfa_challenge_token"],
            "code": "not-a-code",
        },
    )
    expire_email_mfa_cooldown(app)
    replacement = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )

    assert first.status_code == 200
    assert immediate.status_code == 429
    assert failed_code.status_code == 401
    assert replacement.status_code == 200
    assert len(sender.messages) == 2
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        pending = list(
            session.scalars(
                select(PatientPortalMfaChallenge).where(
                    PatientPortalMfaChallenge.status == "pending"
                )
            )
        )
        assert account is not None
        assert account.failed_mfa_count == 1
        assert len(pending) == 1
        assert pending[0].failed_attempts == 1


def test_form_login_error_renders_sign_in_page() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)

    response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": "wrong",
        },
    )

    assert response.status_code == 401
    assert "Sign in" in response.text
    assert "Incorrect Username or Password" in response.text
    assert response.headers["content-type"].startswith("text/html")


def test_form_mfa_error_keeps_retry_and_resend_screen() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    mfa_challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    mfa_code_match = DEVELOPMENT_MFA_CODE_PATTERN.search(login_response.text)
    csrf_token_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert login_response.status_code == 200
    assert mfa_challenge_token_match is not None
    assert mfa_code_match is not None
    assert csrf_token_match is not None
    invalid_mfa_code = "000000" if mfa_code_match.group(1) != "000000" else "111111"

    response = client.post(
        "/auth/mfa/verify",
        data={
            "csrf_token": csrf_token_match.group(1),
            "mfa_challenge_token": mfa_challenge_token_match.group(1),
            "code": invalid_mfa_code,
        },
    )

    assert response.status_code == 401
    assert "Verification code" in response.text
    assert "The code was not accepted. Try again or request a new code." in response.text
    assert "Resend code" in response.text
    assert 'value="email"' in response.text
    assert 'value="sms"' in response.text
    assert response.headers["content-type"].startswith("text/html")


def test_form_mfa_resend_sends_new_email_after_cooldown() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    original_code_match = DEVELOPMENT_MFA_CODE_PATTERN.search(login_response.text)
    resend_csrf_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert challenge_token_match is not None
    assert original_code_match is not None
    assert resend_csrf_match is not None
    assert "Code sent to ex***@example.com by EMAIL." in login_response.text
    assert "Resend code" in login_response.text
    with app.state.session_factory() as session:
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        assert challenge is not None
        assert challenge.last_email_sent_at is not None
        challenge.last_email_sent_at -= timedelta(seconds=61)
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        assert account.last_mfa_email_sent_at is not None
        account.last_mfa_email_sent_at -= timedelta(seconds=61)
        session.commit()

    response = client.post(
        "/auth/mfa/resend",
        data={
            "csrf_token": resend_csrf_match.group(1),
            "mfa_challenge_token": challenge_token_match.group(1),
            "mfa_delivery_method": "email",
        },
    )

    resent_code_match = DEVELOPMENT_MFA_CODE_PATTERN.search(response.text)
    assert response.status_code == 200
    assert "A new code was sent by EMAIL." in response.text
    assert resent_code_match is not None
    assert resent_code_match.group(1) != original_code_match.group(1)
    assert len(sender.messages) == 2
    assert sender.messages[-1]["code"] == resent_code_match.group(1)


def test_form_mfa_resend_shows_cooldown_without_leaving_screen() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    resend_csrf_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert challenge_token_match is not None
    assert resend_csrf_match is not None

    response = client.post(
        "/auth/mfa/resend",
        data={
            "csrf_token": resend_csrf_match.group(1),
            "mfa_challenge_token": challenge_token_match.group(1),
            "mfa_delivery_method": "email",
        },
    )

    assert response.status_code == 429
    assert response.headers["retry-after"] == "60"
    assert "A code was sent recently. Try again in 60 seconds." in response.text
    assert "Verification code" in response.text
    assert "Resend code" in response.text


def test_form_mfa_resend_can_switch_to_sms() -> None:
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(sms_sender=sms_sender)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.phone_number = "+1 555 123 4567"
        session.commit()
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    resend_csrf_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert challenge_token_match is not None
    assert resend_csrf_match is not None

    response = client.post(
        "/auth/mfa/resend",
        data={
            "csrf_token": resend_csrf_match.group(1),
            "mfa_challenge_token": challenge_token_match.group(1),
            "mfa_delivery_method": "sms",
        },
    )

    assert response.status_code == 200
    assert "A new code was sent by SMS." in response.text
    assert 'value="sms"' in response.text
    assert sms_sender.messages[-1]["recipient"] == "+15551234567"


def test_form_mfa_resend_rejects_tampered_csrf_token() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    challenge_token_match = MFA_CHALLENGE_TOKEN_PATTERN.search(login_response.text)
    resend_csrf_match = CSRF_TOKEN_PATTERN.search(login_response.text)
    assert challenge_token_match is not None
    assert resend_csrf_match is not None

    response = client.post(
        "/auth/mfa/resend",
        data={
            "csrf_token": f"{resend_csrf_match.group(1)}0",
            "mfa_challenge_token": challenge_token_match.group(1),
            "mfa_delivery_method": "email",
        },
    )

    assert_browser_notice(response, status_code=403, leaked_detail="invalid CSRF token")


def test_dashboard_shell_requires_session_cookie() -> None:
    app = migrated_development_app()
    response = TestClient(app).get("/portal", follow_redirects=False)

    assert response.status_code == 303
    assert response.headers["location"] == "/"
    assert response.headers["cache-control"] == "no-store"


def test_dashboard_shell_navigation_and_cookie_logout() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)

    dashboard_response = client.get("/portal")

    assert dashboard_response.status_code == 200
    assert dashboard_response.headers["cache-control"] == "no-store"
    assert "HttpOnly" in dashboard_response.headers["set-cookie"]
    assert "Path=/portal" in dashboard_response.headers["set-cookie"]
    assert "SameSite=strict" in dashboard_response.headers["set-cookie"]
    assert 'data-active-module="dashboard"' in dashboard_response.text
    assert "Documents may be available in a future release." in dashboard_response.text
    assert "Secure messaging may be available in a future release." in dashboard_response.text
    assert 'href="/portal/account"' in dashboard_response.text
    assert 'href="/portal/email-passwords"' in dashboard_response.text
    assert 'href="/portal/help"' in dashboard_response.text
    assert 'class="logout-form"' in dashboard_response.text
    assert ">Logout</button>" in dashboard_response.text
    assert "patient.user" in dashboard_response.text

    account_response = client.get("/portal/account")
    email_passwords_response = client.get("/portal/email-passwords")
    help_response = client.get("/portal/help")

    assert account_response.status_code == 200
    assert 'action="http://testserver/portal/account/password"' in account_response.text
    assert 'action="http://testserver/portal/account/contact"' in account_response.text
    assert 'action="http://testserver/portal/account/mfa"' in account_response.text
    assert email_passwords_response.status_code == 200
    assert 'data-active-module="email-passwords"' in email_passwords_response.text
    email_passwords_link_start = email_passwords_response.text.index(
        'href="/portal/email-passwords"'
    )
    email_passwords_link_open = email_passwords_response.text.rindex(
        "<a",
        0,
        email_passwords_link_start,
    )
    email_passwords_link = email_passwords_response.text[
        email_passwords_link_open : email_passwords_response.text.index(
            "</a>",
            email_passwords_link_start,
        )
    ]
    assert 'aria-current="page"' in email_passwords_link
    assert "selected" in email_passwords_link
    assert 'data-active-module="account"' not in email_passwords_response.text
    assert '<th scope="col">Subject</th>' in email_passwords_response.text
    assert "No email passwords" in email_passwords_response.text
    assert help_response.status_code == 200
    assert 'data-active-module="help"' in help_response.text
    assert "Maple Creek Medical" in help_response.text

    match = CSRF_TOKEN_PATTERN.search(help_response.text)
    assert match is not None
    logout_response = client.post(
        "/portal/logout",
        data={"csrf_token": match.group(1)},
        follow_redirects=False,
    )
    redirected_response = client.get("/portal", follow_redirects=False)

    assert logout_response.status_code == 303
    assert logout_response.headers["location"] == "/"
    assert redirected_response.status_code == 303
    with app.state.session_factory() as session:
        portal_session = session.scalar(
            select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
        )
        logout_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_SESSION_LOGOUT
            )
        )

        assert portal_session is not None
        assert portal_session.revoked_reason == "logout"
        assert logout_event is not None
        assert logout_event.account_id == account_id


def test_account_password_change_requires_step_up_and_revokes_other_sessions() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    expire_email_mfa_cooldown(app)
    sign_in_patient_api_session(client)
    account_response = client.get("/portal/account")
    csrf_token_match = CSRF_TOKEN_PATTERN.search(account_response.text)
    assert csrf_token_match is not None

    failed_response = client.post(
        "/portal/account/password",
        data={
            "csrf_token": csrf_token_match.group(1),
            "current_password": "Wrong1!password",
            "new_password": STRONG_RESET_PASSWORD,
            "new_password_confirmation": STRONG_RESET_PASSWORD,
        },
    )
    fresh_account_response = client.get("/portal/account")
    fresh_csrf_token_match = CSRF_TOKEN_PATTERN.search(fresh_account_response.text)
    assert fresh_csrf_token_match is not None
    previous_cookie = client.cookies.get(web_support.PORTAL_SESSION_COOKIE_NAME)
    assert previous_cookie is not None
    changed_response = client.post(
        "/portal/account/password",
        data={
            "csrf_token": fresh_csrf_token_match.group(1),
            "current_password": STRONG_PASSWORD,
            "new_password": STRONG_RESET_PASSWORD,
            "new_password_confirmation": STRONG_RESET_PASSWORD,
        },
        follow_redirects=False,
    )
    replacement_cookie = client.cookies.get(web_support.PORTAL_SESSION_COOKIE_NAME)
    assert replacement_cookie is not None
    assert replacement_cookie != previous_cookie
    copied_cookie_client = TestClient(app)
    copied_cookie_client.cookies.set(
        web_support.PORTAL_SESSION_COOKIE_NAME,
        previous_cookie,
        path=web_support.PORTAL_SESSION_COOKIE_PATH,
    )
    copied_cookie_response = copied_cookie_client.get("/portal", follow_redirects=False)
    notice_response = client.get("/portal/account?status=password-updated")
    old_password_login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    expire_email_mfa_cooldown(app)
    new_password_login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_RESET_PASSWORD},
    )
    still_signed_in_response = client.get("/portal")

    assert failed_response.status_code == 403
    assert "Account change could not be completed." in failed_response.text
    assert "Wrong1!password" not in failed_response.text
    assert changed_response.status_code == 303
    assert changed_response.headers["location"] == "/portal/account?status=password-updated"
    assert notice_response.status_code == 200
    assert "Password updated." in notice_response.text
    assert old_password_login_response.status_code == 401
    assert new_password_login_response.status_code == 200
    assert new_password_login_response.json()["status"] == "mfa_required"
    assert still_signed_in_response.status_code == 200
    assert copied_cookie_response.status_code == 303
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        portal_sessions = list(
            session.scalars(
                select(PatientPortalSession)
                .where(PatientPortalSession.account_id == account_id)
                .order_by(PatientPortalSession.id)
            )
        )
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_PASSWORD_CHANGE)
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert account is not None
        assert account.password_hash != STRONG_PASSWORD
        assert account.password_hash != STRONG_RESET_PASSWORD
        assert any(
            portal_session.revoked_reason == SESSION_REVOKED_REASON_PASSWORD_CHANGE
            for portal_session in portal_sessions
        )
        assert any(portal_session.revoked_at is None for portal_session in portal_sessions)
        assert [(event.outcome, event.reason) for event in audit_events] == [
            (AUDIT_OUTCOME_FAILURE, "step_up_failed"),
            (AUDIT_OUTCOME_SUCCESS, "updated"),
        ]


def test_account_contact_update_revokes_reset_token_for_old_email() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    browser_sign_in_seeded_patient(app, client)
    reset_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    reset_token = reset_response.json()["development_reset_token"]
    account_response = client.get("/portal/account")

    changed = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token_from_response(account_response),
            "email": "replacement.patient@example.com",
            "phone_number": "",
            "current_password": STRONG_PASSWORD,
        },
        follow_redirects=False,
    )
    still_valid = client.post(
        "/auth/password-reset/complete",
        json={
            "reset_token": reset_token,
            "new_password": STRONG_RESET_PASSWORD,
        },
    )

    assert changed.status_code == 303
    # Requesting a change revokes nothing: the account still belongs to the old address, and a
    # reset already in flight to it must keep working.
    assert still_valid.status_code == 200


def test_confirming_an_email_change_revokes_reset_tokens_for_the_old_address() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    browser_sign_in_seeded_patient(app, client)
    account_response = client.get("/portal/account")

    client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token_from_response(account_response),
            "email": "replacement.patient@example.com",
            "phone_number": "",
            "current_password": STRONG_PASSWORD,
        },
        follow_redirects=False,
    )
    confirmation_url = str(
        next(
            message
            for message in reversed(sender.messages)
            if message.get("type") == "email_change_confirmation"
        )["confirmation_url"]
    )
    reset_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    reset_token = reset_response.json()["development_reset_token"]
    confirmation_page = client.get("/auth/email-change/confirm")
    confirmed = client.post(
        "/auth/email-change/confirm",
        data={
            "csrf_token": csrf_token_from_response(confirmation_page),
            "reset_token": confirmation_url.partition("#token=")[2],
        },
    )
    stale_token = client.post(
        "/auth/password-reset/complete",
        json={
            "reset_token": reset_token,
            "new_password": STRONG_RESET_PASSWORD,
        },
    )

    assert confirmed.status_code == 200
    # A reset link delivered to the address the account just moved away from must not be able to
    # set a password on the account under its new address.
    assert stale_token.status_code == 400


def test_login_uses_sms_preference_when_phone_is_available() -> None:
    email_sender = RecordingPortalEmailSender()
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(
        email_sender=email_sender,
        sms_sender=sms_sender,
    )
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.preferred_mfa_method = "sms"
        account.phone_number = "+1 555 010 5555"
        session.commit()

    login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )

    assert login_response.status_code == 200
    assert login_response.json()["status"] == "mfa_required"
    assert login_response.json()["mfa_delivery_method"] == "sms"
    assert email_sender.messages == []
    assert sms_sender.messages[-1]["recipient"] == "+15550105555"


def test_portal_logout_rejects_invalid_csrf_without_revoking_session() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)

    dashboard_response = client.get("/portal")
    logout_response = client.post(
        "/portal/logout",
        data={"csrf_token": "invalid"},
        follow_redirects=False,
    )
    still_authenticated_response = client.get("/portal")

    assert dashboard_response.status_code == 200
    assert_browser_notice(
        logout_response, status_code=403, leaked_detail="logout could not be completed"
    )
    assert still_authenticated_response.status_code == 200
    with app.state.session_factory() as session:
        portal_session = session.scalar(
            select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
        )
        logout_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_SESSION_LOGOUT
            )
        )

        assert portal_session is not None
        assert portal_session.revoked_reason is None
        assert logout_event is None


def test_portal_logout_clears_invalid_session_cookie() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    dashboard_response = client.get("/portal")
    match = CSRF_TOKEN_PATTERN.search(dashboard_response.text)
    assert match is not None
    with app.state.session_factory() as session:
        portal_session = session.scalar(
            select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
        )
        assert portal_session is not None
        portal_session.revoked_at = utc_now()
        portal_session.revoked_reason = "test"
        session.commit()

    response = client.post(
        "/portal/logout",
        data={"csrf_token": match.group(1)},
        follow_redirects=False,
    )
    set_cookie_header = response.headers.get("set-cookie", "")

    assert response.status_code == 303
    assert response.headers["location"] == "/"
    assert f"{web_support.PORTAL_SESSION_COOKIE_NAME}=" in set_cookie_header
    assert "Max-Age=0" in set_cookie_header
    with app.state.session_factory() as session:
        logout_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_SESSION_LOGOUT
            )
        )
        assert logout_event is None


def test_dashboard_clears_invalid_session_cookie() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    with app.state.session_factory() as session:
        portal_session = session.scalar(
            select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
        )
        assert portal_session is not None
        portal_session.revoked_at = utc_now()
        portal_session.revoked_reason = "test"
        session.commit()

    response = client.get("/portal", follow_redirects=False)
    set_cookie_header = response.headers.get("set-cookie", "")

    assert response.status_code == 303
    assert response.headers["location"] == "/"
    assert f"{web_support.PORTAL_SESSION_COOKIE_NAME}=" in set_cookie_header
    assert "Max-Age=0" in set_cookie_header
    assert "Path=/portal" in set_cookie_header


def test_login_route_rejects_tampered_csrf_token() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    csrf_token = get_csrf_token(client)
    response = client.post(
        "/auth/login",
        data={
            "csrf_token": f"{csrf_token}0",
            "username": "patient.username",
            "password": "unused",
        },
    )

    assert_browser_notice(response, status_code=403, leaked_detail="invalid CSRF token")


def test_login_route_rejects_csrf_token_without_matching_cookie() -> None:
    app = main.create_app(development_settings())
    client_with_cookie = TestClient(app)
    client_without_cookie = TestClient(app)
    csrf_token = get_csrf_token(client_with_cookie)
    response = client_without_cookie.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.username",
            "password": "unused",
        },
    )

    assert_browser_notice(response, status_code=403, leaked_detail="invalid CSRF token")


def test_login_route_rejects_oversized_form_body() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    csrf_token = get_csrf_token(client)
    response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.username",
            "password": "x" * web_support.MAX_FORM_BODY_BYTES,
        },
    )

    assert_browser_notice(response, status_code=413, leaked_detail="request body too large")


def test_login_route_rejects_malformed_urlencoded_form_body() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    get_csrf_token(client)
    response = client.post(
        "/auth/login",
        content="csrf_token",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )

    assert_browser_notice(response, status_code=400, leaked_detail="invalid form body")


def test_login_route_rejects_invalid_utf8_form_body() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).post(
        "/auth/login",
        content=b"csrf_token=\xff",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )

    assert_browser_notice(response, status_code=400, leaked_detail="invalid form body")


def test_login_route_rejects_too_many_form_fields() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    get_csrf_token(client)
    form_body = "&".join(
        f"field{field_number}=x" for field_number in range(web_support.MAX_FORM_FIELD_COUNT + 1)
    )
    response = client.post(
        "/auth/login",
        content=form_body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )

    assert_browser_notice(response, status_code=400, leaked_detail="invalid form body")


def test_login_rejects_bad_password_with_generic_error_and_audit() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": "Wrong1!password"},
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "sign-in could not be completed"
    assert "Wrong1!password" not in response.text
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        audit_event = session.scalar(
            select(PatientPortalAuditEvent)
            .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_LOGIN)
            .order_by(PatientPortalAuditEvent.id.desc())
        )

        assert account is not None
        assert account.failed_login_count == 1
        assert account.locked_at is None
        assert audit_event is not None
        assert audit_event.outcome == AUDIT_OUTCOME_FAILURE
        assert audit_event.reason == "invalid_credentials"


def test_mfa_resend_limits_email_and_sms_independently() -> None:
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(sms_sender=sms_sender)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.phone_number = "+1 555 123 4567"
        session.commit()

    login_response = client.post(
        "/auth/login",
        json={
            "username": "patient.user",
            "password": STRONG_PASSWORD,
            "mfa_delivery_method": "email",
        },
    )
    challenge_token = login_response.json()["mfa_challenge_token"]

    throttled_email_response = client.post(
        "/auth/mfa/resend",
        json={"mfa_challenge_token": challenge_token, "mfa_delivery_method": "email"},
    )
    sms_response = client.post(
        "/auth/mfa/resend",
        json={"mfa_challenge_token": challenge_token, "mfa_delivery_method": "sms"},
    )
    throttled_sms_response = client.post(
        "/auth/mfa/resend",
        json={"mfa_challenge_token": challenge_token, "mfa_delivery_method": "sms"},
    )

    assert login_response.status_code == 200
    assert throttled_email_response.status_code == 429
    assert throttled_email_response.headers["retry-after"] == "60"
    assert sms_response.status_code == 200
    assert throttled_sms_response.status_code == 429
    assert throttled_sms_response.headers["retry-after"] == "300"

    verify_response = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": challenge_token,
            "code": sms_sender.messages[-1]["code"],
        },
    )

    assert verify_response.status_code == 200
    assert verify_response.json()["status"] == "signed_in"
    with app.state.session_factory() as session:
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_MFA_RESEND)
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert challenge is not None
        assert challenge.delivery_method == "sms"
        assert challenge.status == "verified"
        assert [event.outcome for event in audit_events] == [
            AUDIT_OUTCOME_THROTTLED,
            AUDIT_OUTCOME_SUCCESS,
            AUDIT_OUTCOME_THROTTLED,
        ]


def test_unknown_login_fields_are_rejected_rather_than_silently_ignored() -> None:
    """`mfa_method` is a plausible slip for `mfa_delivery_method`.

    With extra="ignore" the login succeeded and issued an *email* challenge, sending the code to
    a destination the caller did not select, with nothing in the response to signal the drop.
    """
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    response = client.post(
        "/auth/login",
        json={
            "username": "patient.user",
            "password": STRONG_PASSWORD,
            "mfa_method": "sms",
        },
    )

    assert response.status_code == 422
    assert "mfa_method" in response.text
    assert "mfa_challenge_token" not in response.json()


def test_bad_mfa_attempts_lock_account() -> None:
    app = migrated_development_app(mfa_max_failed_attempts=2)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    login_payload = login_response.json()
    challenge_token = login_payload["mfa_challenge_token"]
    wrong_code = "111111" if login_payload["development_mfa_code"] == "000000" else "000000"

    first_bad_response = client.post(
        "/auth/mfa/verify",
        json={"mfa_challenge_token": challenge_token, "code": wrong_code},
    )
    second_bad_response = client.post(
        "/auth/mfa/verify",
        json={"mfa_challenge_token": challenge_token, "code": wrong_code},
    )

    assert first_bad_response.status_code == 401
    assert first_bad_response.json()["detail"] == "MFA could not be verified"
    assert second_bad_response.status_code == 423
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        challenge = session.scalar(select(PatientPortalMfaChallenge))
        lock_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_LOCK
            )
        )

        assert account is not None
        assert account.locked_at is not None
        assert account.force_password_reset is True
        assert challenge is not None
        assert challenge.status == "cancelled"
        assert lock_event is not None
        assert lock_event.reason == "mfa_failures"


def test_password_lockout_staff_unlock_and_forced_reset() -> None:
    app = migrated_development_app(auth_max_failed_password_attempts=2)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)

    first_bad_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": "Wrong1!password"},
    )
    lock_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": "Wrong1!password"},
    )
    locked_login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )

    assert first_bad_response.status_code == 401
    assert lock_response.status_code == 401
    assert locked_login_response.status_code == 423

    unlock_response = client.post(
        f"/dev/admin/accounts/{account_id}/unlock",
        headers=dev_admin_headers(actor="Admin example"),
    )
    forced_reset_login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    reset_request_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    reset_token = reset_request_response.json()["development_reset_token"]
    complete_reset_response = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": reset_token, "new_password": STRONG_RESET_PASSWORD},
    )
    new_login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_RESET_PASSWORD},
    )

    assert unlock_response.status_code == 200
    assert unlock_response.json()["locked_at"] is None
    assert unlock_response.json()["force_password_reset"] is True
    assert forced_reset_login_response.status_code == 403
    assert forced_reset_login_response.json() == {"status": "password_reset_required"}
    assert reset_request_response.status_code == 202
    assert reset_token
    assert complete_reset_response.status_code == 200
    assert complete_reset_response.json() == {
        "status": "password_reset",
        "username": "patient.user",
    }
    assert new_login_response.status_code == 200
    assert new_login_response.json()["status"] == "mfa_required"
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        reset_records = list(session.scalars(select(PatientPortalPasswordResetToken)))
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type.in_(
                        [
                            AUDIT_EVENT_ACCOUNT_LOCK,
                            AUDIT_EVENT_ACCOUNT_UNLOCK,
                            AUDIT_EVENT_PASSWORD_RESET_REQUEST,
                            AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
                        ]
                    )
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert account is not None
        assert account.failed_login_count == 0
        assert account.locked_at is None
        assert account.force_password_reset is False
        assert len(reset_records) == 1
        assert reset_records[0].status == "used"
        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_ACCOUNT_LOCK, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_ACCOUNT_UNLOCK, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_PASSWORD_RESET_REQUEST, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_PASSWORD_RESET_COMPLETE, AUDIT_OUTCOME_SUCCESS),
        ]


def test_browser_password_reset_sends_fragment_link_and_completes_reset() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    reset_page = client.get("/auth/password-reset")

    request_response = client.post(
        "/auth/password-reset/request",
        data={
            "csrf_token": csrf_token_from_response(reset_page),
            "username": "patient.user",
            "email": SEEDED_INVITE_EMAIL,
        },
    )

    assert request_response.status_code == 202
    assert "If the account details match" in request_response.text
    assert len(sender.messages) == 1
    reset_url = str(sender.messages[0]["reset_url"])
    parsed_reset_url = urlsplit(reset_url)
    assert parsed_reset_url.query == ""
    assert parsed_reset_url.path == "/auth/password-reset/complete"
    reset_token = parse_qs(parsed_reset_url.fragment)["token"][0]
    assert reset_token not in parsed_reset_url.path
    with app.state.session_factory() as session:
        delivery_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_PASSWORD_RESET_DELIVERY
            )
        )
        assert delivery_event is not None
        assert delivery_event.outcome == AUDIT_OUTCOME_SUCCESS
        assert delivery_event.reason == "email"
    cooldown_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    assert cooldown_response.status_code == 202
    assert cooldown_response.json()["development_reset_token"] is None
    assert len(sender.messages) == 1

    complete_page = client.get(parsed_reset_url.path)
    mismatch_response = client.post(
        "/auth/password-reset/complete",
        data={
            "csrf_token": csrf_token_from_response(complete_page),
            "reset_token": reset_token,
            "new_password": STRONG_RESET_PASSWORD,
            "new_password_confirmation": "Different1!word",
        },
    )
    complete_response = client.post(
        "/auth/password-reset/complete",
        data={
            "csrf_token": csrf_token_from_response(mismatch_response),
            "reset_token": reset_token,
            "new_password": STRONG_RESET_PASSWORD,
            "new_password_confirmation": STRONG_RESET_PASSWORD,
        },
    )
    login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_RESET_PASSWORD},
    )

    assert mismatch_response.status_code == 400
    assert "password confirmation does not match" in mismatch_response.text.lower()
    assert f'value="{reset_token}" data-reset-token' in mismatch_response.text
    assert complete_response.status_code == 200
    assert "Password reset" in complete_response.text
    assert reset_token not in complete_response.text
    assert login_response.status_code == 200
    assert login_response.json()["status"] == "mfa_required"


def test_password_reset_delivery_failure_revokes_token_and_is_audited(
    caplog: pytest.LogCaptureFixture,
) -> None:
    sender = RecordingPortalEmailSender(fail=True)
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )

    assert response.status_code == 202
    assert response.json()["development_reset_token"] is None
    assert SEEDED_INVITE_EMAIL not in caplog.text
    with app.state.session_factory() as session:
        reset_record = session.scalar(select(PatientPortalPasswordResetToken))
        delivery_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_PASSWORD_RESET_DELIVERY
            )
        )
        assert reset_record is not None
        assert reset_record.status == "revoked"
        assert delivery_event is not None
        assert delivery_event.outcome == AUDIT_OUTCOME_FAILURE
        assert delivery_event.reason == "email"


def test_locked_account_browser_page_and_reset_require_staff_unlock() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        with session.begin():
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            account.locked_at = utc_now()
            account.locked_by = "security-policy"
            account.force_password_reset = True

    csrf_token = get_csrf_token(client)
    locked_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )
    reset_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )

    assert locked_response.status_code == 423
    assert "Account locked" in locked_response.text
    assert "Clinic staff must unlock this account" in locked_response.text
    assert reset_response.status_code == 202
    assert reset_response.json()["development_reset_token"] is None
    assert sender.messages == []

    unlock_response = client.post(
        f"/dev/admin/accounts/{account_id}/unlock",
        headers=dev_admin_headers(),
    )
    unlocked_reset_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )

    assert unlock_response.status_code == 200
    assert unlocked_reset_response.json()["development_reset_token"]
    assert len(sender.messages) == 1


def test_account_lock_revokes_preexisting_password_reset_token() -> None:
    app = migrated_development_app(auth_max_failed_password_attempts=1)
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    reset_response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    reset_token = reset_response.json()["development_reset_token"]

    lock_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": "Wrong1!password"},
    )
    # Assert the token is already dead BEFORE attempting redemption. Redeeming first would let the
    # redeem-time guard revoke it, so this test passed even with lock-time revocation removed.
    with app.state.session_factory() as session:
        locked_account = session.get(PatientPortalAccount, account_id)
        revoked_at_lock_time = session.scalar(select(PatientPortalPasswordResetToken))
        assert locked_account is not None
        assert locked_account.locked_at is not None
        assert revoked_at_lock_time is not None
        assert revoked_at_lock_time.status == "revoked"

    complete_response = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": reset_token, "new_password": STRONG_RESET_PASSWORD},
    )

    assert lock_response.status_code == 401
    assert complete_response.status_code == 400
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        reset_record = session.scalar(select(PatientPortalPasswordResetToken))
        assert account is not None
        assert account.locked_at is not None
        assert reset_record is not None
        assert reset_record.status == "revoked"


def test_database_allows_only_one_pending_password_reset_per_account() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    assert response.status_code == 202

    now = utc_now()
    with app.state.session_factory() as session:
        session.add(
            PatientPortalPasswordResetToken(
                account_id=account_id,
                token_hash="z" * 64,
                status="pending",
                created_at=now,
                expires_at=now + timedelta(hours=1),
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()
        session.rollback()


def test_patient_email_password_api_requires_session_and_valid_pagination() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    patient_token = sign_in_patient_api_session(client)
    auth_headers = bearer_headers(patient_token)

    unauthenticated_response = client.get("/api/patient/email-passwords")
    too_small_limit_response = client.get(
        "/api/patient/email-passwords?limit=0",
        headers=auth_headers,
    )
    too_large_limit_response = client.get(
        "/api/patient/email-passwords?limit=101",
        headers=auth_headers,
    )
    negative_offset_response = client.get(
        "/api/patient/email-passwords?offset=-1",
        headers=auth_headers,
    )

    assert unauthenticated_response.status_code == 401
    assert too_small_limit_response.status_code == 422
    assert too_large_limit_response.status_code == 422
    assert negative_offset_response.status_code == 422


def test_changing_mfa_method_cancels_codes_already_sent_to_the_old_channel() -> None:
    """Switching away from a compromised channel must invalidate codes delivered to it."""
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)
    expire_email_mfa_cooldown(app)
    # A second sign-in leaves a live challenge addressed to the current (old) channel.
    stale_login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    assert stale_login.status_code == 200

    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.phone_number = "+15550105555"
        session.flush()
        update_account_mfa_method(
            session,
            account,
            current_password=STRONG_PASSWORD,
            preferred_mfa_method="sms",
            max_failed_password_attempts=5,
        )
        session.commit()

    stale_verify = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": stale_login.json()["mfa_challenge_token"],
            "code": stale_login.json()["development_mfa_code"],
        },
    )

    assert token
    # The cancelled challenge is no longer a usable credential.
    assert stale_verify.status_code == 400


def test_saving_unchanged_mfa_method_preserves_live_challenge() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        update_account_mfa_method(
            session,
            account,
            current_password=STRONG_PASSWORD,
            preferred_mfa_method="email",
            max_failed_password_attempts=5,
        )
        session.commit()

    verified = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": login.json()["mfa_challenge_token"],
            "code": login.json()["development_mfa_code"],
        },
    )

    assert verified.status_code == 200
    with app.state.session_factory() as session:
        statuses = list(
            session.scalars(
                select(PatientPortalMfaChallenge.status).order_by(PatientPortalMfaChallenge.id)
            )
        )
        assert "pending" not in statuses


def test_session_scope_commits_success() -> None:
    engine = create_portal_engine("sqlite+pysqlite:///:memory:")
    upgrade_to_head(engine)
    session_factory = create_session_factory(engine)

    with session_scope(session_factory) as session:
        committed_invite, _ = create_service_invite(session, 1234, "CarlosDoc")
        committed_invite_id = committed_invite.id

    with session_factory() as session:
        assert session.get(PatientPortalInvite, committed_invite_id) is not None

    engine.dispose()


def test_unusable_password_hash_is_a_server_fault_not_a_patient_lockout() -> None:
    """A hash that cannot be parsed must not be blamed on the patient or burn their budget."""
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.password_hash = "$2b$12$notanargon2hash00000000000000000000000000000000000000"
        session.commit()

    responses = [
        client.post("/auth/login", json={"username": "patient.user", "password": STRONG_PASSWORD})
        for _ in range(3)
    ]

    assert [response.status_code for response in responses] == [503, 503, 503]
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        # The correct password was supplied every time; the patient must not be pushed to lockout.
        assert account.failed_login_count == 0
        assert account.locked_at is None
        reasons = {
            event.reason
            for event in session.scalars(
                select(PatientPortalAuditEvent).where(
                    PatientPortalAuditEvent.event_type == AUDIT_EVENT_LOGIN,
                    PatientPortalAuditEvent.outcome == AUDIT_OUTCOME_FAILURE,
                )
            )
        }
        assert "password_hash_unusable" in reasons
        assert "invalid_credentials" not in reasons


def test_password_change_revokes_a_reset_token_issued_before_the_change() -> None:
    """A reset link captured before the patient changed their password must not still work."""
    app = migrated_development_app()
    client = TestClient(app)
    browser_sign_in_seeded_patient(app, client)
    reset_request = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    stale_token = reset_request.json()["development_reset_token"]
    assert stale_token

    csrf_token = CSRF_TOKEN_PATTERN.search(client.get("/portal/account").text)
    assert csrf_token is not None
    replacement_password = "V1ctimChosen!pass"
    change = client.post(
        "/portal/account/password",
        data={
            "csrf_token": csrf_token.group(1),
            "current_password": STRONG_PASSWORD,
            "new_password": replacement_password,
            "new_password_confirmation": replacement_password,
        },
        follow_redirects=False,
    )
    replay = client.post(
        "/auth/password-reset/complete",
        json={
            "reset_token": stale_token,
            "new_password": "Att4ckerChosen!x",  # ggignore - test fixture, never a real credential
        },
    )

    assert change.status_code == 303
    assert replay.status_code == 400
    with app.state.session_factory() as session:
        statuses = [
            token.status for token in session.scalars(select(PatientPortalPasswordResetToken))
        ]
        assert statuses == ["revoked"]


def test_failed_mfa_delivery_does_not_reserve_the_full_resend_cooldown() -> None:
    """A provider outage must not strand a patient behind a full window for a code they never got.

    The reservation is shortened to a retry grace rather than released outright — see
    ``test_reporting_delivery_failure_still_bounds_the_per_account_send_rate`` for why releasing it
    is unsafe. What this pins is the patient-facing half: the wait after a failure is seconds, not
    the 60-second cooldown a successful send would have earned.
    """

    class FailingSender:
        def send_code(self, **kwargs: object) -> None:
            raise PortalEmailDeliveryError("portal email delivery failed")

        def send_password_reset(self, **kwargs: object) -> None:
            return None

        def send_contact_change_notice(self, **kwargs: object) -> None:
            return None

    original_builder = main.build_portal_email_sender
    main.build_portal_email_sender = lambda settings: FailingSender()
    try:
        app = migrated_development_app()
        client = TestClient(app)
        activate_seeded_patient_account(app, client)
        first = client.post(
            "/auth/login", json={"username": "patient.user", "password": STRONG_PASSWORD}
        )
        immediate_retry = client.post(
            "/auth/login", json={"username": "patient.user", "password": STRONG_PASSWORD}
        )
    finally:
        main.build_portal_email_sender = original_builder

    assert first.status_code == 503
    # The retry grace is still running, so this is throttled — but for seconds, not the full
    # 60-second window a delivered code would have reserved.
    assert immediate_retry.status_code == 429
    assert 0 < int(immediate_retry.headers["retry-after"]) <= (
        MFA_DELIVERY_FAILURE_RETRY_GRACE.total_seconds()
    )


def test_reporting_delivery_failure_still_bounds_the_per_account_send_rate() -> None:
    """A provider that delivers and then reports failure must not become a message amplifier.

    ``last_mfa_*_sent_at`` is the only account-scoped limit on outbound codes, and
    ``resend_mfa_challenge`` consults nothing else. Releasing it on every reported failure treats
    "the adapter
    raised" as "nothing was delivered", which is false for greylisting, an SMTP timeout after DATA
    is accepted, or a gateway that 5xx's after queueing. The reservation is therefore shortened to
    a retry grace rather than cleared: the patient can retry in seconds instead of a full minute,
    but a degraded provider cannot flood their mailbox.

    Driven through /auth/mfa/resend deliberately. The login path cancels its challenge and the
    global auth limiter masks most of the effect, which is why this survived the login-only test
    directly above.
    """

    class DeliveringThenFailingSender:
        """Delivers the message, then reports failure — the degraded-provider shape.

        Healthy until ``degraded`` is set, so a challenge can be established the normal way before
        the provider starts misbehaving. A login whose delivery fails cancels its challenge, which
        is precisely why the flood is only reachable through the resend route.
        """

        def __init__(self) -> None:
            self.messages: list[object] = []
            self.degraded = False

        def send_code(self, **kwargs: object) -> None:
            self.messages.append(kwargs.get("code"))
            if self.degraded:
                raise PortalEmailDeliveryError("portal email delivery failed after handoff")

        def send_password_reset(self, **kwargs: object) -> None:
            return None

        def send_contact_change_notice(self, **kwargs: object) -> None:
            return None

    sender = DeliveringThenFailingSender()
    original_builder = main.build_portal_email_sender
    main.build_portal_email_sender = lambda settings: sender
    try:
        app = migrated_development_app()
        client = TestClient(app)
        activate_seeded_patient_account(app, client)
        login_response = client.post(
            "/auth/login",
            json={
                "username": "patient.user",
                "password": STRONG_PASSWORD,
                "mfa_delivery_method": "email",
            },
        )
        assert login_response.status_code == 200
        challenge_token = login_response.json()["mfa_challenge_token"]
        sender.degraded = True
        expire_email_mfa_cooldown(app)
        resend_statuses = [
            client.post(
                "/auth/mfa/resend",
                json={
                    "mfa_challenge_token": challenge_token,
                    "mfa_delivery_method": "email",
                },
            ).status_code
            for _ in range(6)
        ]
    finally:
        main.build_portal_email_sender = original_builder

    # The first resend is attempted and reports failure; the rest are refused by the account
    # cooldown before any message is handed to the provider. Before the fix every one of these
    # was attempted, and the patient's mailbox received all six.
    assert resend_statuses == [503, 429, 429, 429, 429, 429]
    assert len(sender.messages) == 2  # the login code, plus the one failed resend

    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        # The reservation was shortened, not released: still set, but expiring within the grace
        # rather than a full cooldown, so the patient is not stranded by an outage either.
        assert account.last_mfa_email_sent_at is not None
        seconds_remaining = seconds_until_allowed(
            account.last_mfa_email_sent_at,
            utc_now(),
            timedelta(seconds=60),
        )
        assert 0 < seconds_remaining <= MFA_DELIVERY_FAILURE_RETRY_GRACE.total_seconds()


def test_idle_timeout_revocation_persists_on_every_bearer_surface() -> None:
    """A 401 must leave the revocation *reason* behind, not just fail.

    authenticate_session_token marks the row revoked and then raises; the function-scoped database
    dependency would roll that back on the error path, so the reason it recorded would be lost and
    the same stale row re-examined on every later request. Asserted at the route rather than on the
    service call, because the service call alone never commits and would pass either way.
    """
    app = migrated_development_app(session_idle_timeout_seconds=60)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)

    with app.state.session_factory() as session:
        portal_session = session.scalar(select(PatientPortalSession))
        assert portal_session is not None
        portal_session.last_seen_at = utc_now() - timedelta(minutes=30)
        session.commit()

    response = client.get("/auth/session", headers=bearer_headers(token))

    assert response.status_code == 401
    with app.state.session_factory() as session:
        portal_session = session.scalar(select(PatientPortalSession))
        assert portal_session is not None
        assert portal_session.revoked_at is not None
        assert portal_session.revoked_reason == "idle_timeout"


def test_idle_timeout_revocation_persists_when_logout_returns_unauthorized() -> None:
    app = migrated_development_app(session_idle_timeout_seconds=60)
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    token = sign_in_patient_api_session(client)
    with app.state.session_factory() as session:
        portal_session = session.scalar(select(PatientPortalSession))
        assert portal_session is not None
        portal_session.last_seen_at = utc_now() - timedelta(minutes=30)
        session.commit()

    response = client.post("/auth/logout", headers=bearer_headers(token))

    assert response.status_code == 401
    with app.state.session_factory() as session:
        portal_session = session.scalar(select(PatientPortalSession))
        assert portal_session is not None
        assert portal_session.revoked_reason == "idle_timeout"


def test_malformed_bearer_authentication_is_audited() -> None:
    app = migrated_development_app()
    client = TestClient(app)

    response = client.get(
        "/auth/session",
        headers={"Authorization": "Basic malformed-credential"},
    )

    assert response.status_code == 401
    with app.state.session_factory() as session:
        event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_LOGIN,
                PatientPortalAuditEvent.outcome == AUDIT_OUTCOME_FAILURE,
            )
        )
        assert event is not None
        assert event.reason == "authentication_failed"


def test_password_reset_redemption_revokes_every_preexisting_session() -> None:
    """Reset is the takeover-recovery path: a stolen session must not survive it.

    Unlike the lock path there is no lazy-kill backstop here, because redemption clears
    ``force_password_reset``; the eager revocation is the only control.
    """
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    first_token = sign_in_patient_api_session(client)
    expire_email_mfa_cooldown(app)
    second_token = sign_in_patient_api_session(client)
    reset_request = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    reset_token = reset_request.json()["development_reset_token"]

    completed = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": reset_token, "new_password": STRONG_RESET_PASSWORD},
    )

    assert completed.status_code == 200
    assert client.get("/auth/session", headers=bearer_headers(first_token)).status_code == 401
    assert client.get("/auth/session", headers=bearer_headers(second_token)).status_code == 401
    with app.state.session_factory() as session:
        sessions = list(session.scalars(select(PatientPortalSession)))
        assert len(sessions) == 2
        assert all(row.revoked_at is not None for row in sessions)
        assert all(row.revoked_reason == "password_reset" for row in sessions)


def test_pending_email_change_leaves_mfa_delivery_on_the_current_address() -> None:
    """The property the whole deferral exists for.

    Someone who has the password but not the mailbox can submit this form. Until the new address
    is confirmed, the second factor must keep going to the address the patient actually controls,
    or the step-up password check would be the only thing standing between a stolen password and
    a full account takeover.
    """
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    request_seeded_email_change(app, client, sender)
    with app.state.session_factory() as session:
        with session.begin():
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            # Clear the send cooldown left by the sign-in above; this test is about the
            # destination, not the throttle.
            account.last_mfa_email_sent_at = None

    csrf_token = get_csrf_token(client)
    login_response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.user",
            "password": STRONG_PASSWORD,
        },
    )

    assert login_response.status_code == 200
    mfa_message = next(
        message for message in reversed(sender.messages) if "code" in message
    )
    assert mfa_message["recipient"] == SEEDED_INVITE_EMAIL


def test_pending_email_change_leaves_password_reset_on_the_current_address() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    browser_sign_in_seeded_patient(app, client)
    request_seeded_email_change(app, client, sender)

    to_new_address = client.post(
        "/auth/password-reset/request",
        json={
            "username": "patient.user",
            "email": "replacement.patient@example.com",
        },
    )
    to_current_address = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )

    # Both return the same generic acceptance; only the current address actually matches.
    assert to_new_address.status_code == 202
    assert to_new_address.json().get("development_reset_token") is None
    assert to_current_address.status_code == 202
    assert to_current_address.json().get("development_reset_token") is not None


def test_email_change_confirmation_is_refused_for_a_locked_account() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    confirmation_token = request_seeded_email_change(app, client, sender)
    with app.state.session_factory() as session:
        with session.begin():
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            account.locked_at = utc_now()
            account.locked_by = "staff"

    refused = confirm_seeded_email_change(client, confirmation_token)

    assert refused.status_code == 400
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        pending = session.scalar(select(PatientPortalEmailChangeRequest))
        # A link minted before the lock must not be able to move the recovery address afterwards.
        assert account is not None
        assert account.email == SEEDED_INVITE_EMAIL
        assert pending is not None
        assert pending.status == EMAIL_CHANGE_STATUS_REVOKED


def _reset_token_for(app, client) -> str:
    """Request a reset and return the raw token from the development response."""
    response = client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    assert response.status_code == 202
    return response.json()["development_reset_token"]


def test_expired_mfa_challenge_is_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    """The entire expiry column was untested: no test ever expired an MFA challenge."""
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    login = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    payload = login.json()
    started_at = utc_now()
    monkeypatch.setattr(auth, "utc_now", lambda: started_at + timedelta(minutes=11))

    response = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": payload["mfa_challenge_token"],
            "code": payload["development_mfa_code"],
        },
    )

    assert response.status_code in {400, 401}
    assert "session_token" not in response.json()


def test_expired_password_reset_token_is_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    """An expired reset token that still works is account takeover."""
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    reset_token = _reset_token_for(app, client)
    started_at = utc_now()
    monkeypatch.setattr(auth, "utc_now", lambda: started_at + timedelta(hours=2))

    response = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": reset_token, "new_password": "Rotated2026!x"},
    )

    assert response.status_code in {400, 401}
    # And the old password still works, so nothing was changed by the rejected request.
    assert (
        client.post(
            "/auth/login",
            json={"username": "patient.user", "password": STRONG_PASSWORD},
        ).status_code
        == 200
    )


def test_consumed_password_reset_token_cannot_be_replayed() -> None:
    """Replay was tested only for status == "revoked", never for status == "used"."""
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    reset_token = _reset_token_for(app, client)

    first = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": reset_token, "new_password": "Rotated2026!x"},
    )
    replay = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": reset_token, "new_password": "Attacker2026!x"},
    )

    assert first.status_code == 200
    with app.state.session_factory() as session:
        record = session.scalar(select(PatientPortalPasswordResetToken))
        assert record.status == PASSWORD_RESET_STATUS_USED
    assert replay.status_code in {400, 401}
    # The replay's password must not have taken effect.
    assert (
        client.post(
            "/auth/login",
            json={"username": "patient.user", "password": "Attacker2026!x"},
        ).status_code
        == 401
    )


@pytest.mark.parametrize(
    ("path", "body"),
    [
        ("/auth/mfa/verify", {"mfa_challenge_token": "f" * 43, "code": "000000"}),
        (
            "/auth/password-reset/complete",
            {"reset_token": "f" * 43, "new_password": "Forged2026!x"},
        ),
    ],
)
def test_well_formed_but_forged_tokens_are_rejected(path: str, body: dict) -> None:
    """The entire forged column was untested: no test ever sent a well-formed random token."""
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)

    response = client.post(path, json=body)

    assert response.status_code in {400, 401}
    assert "session_token" not in response.text


def test_a_reset_token_cannot_be_used_against_another_account() -> None:
    """Cross-account was untested everywhere, including reset_record.account_id != account.id."""
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    reset_token = _reset_token_for(app, client)

    # Re-point the token at a second account, which is what a mix-up in the lookup would allow.
    with app.state.session_factory() as session:
        with session.begin():
            victim = session.scalar(
                select(PatientPortalAccount).where(
                    PatientPortalAccount.username == "patient.user"
                )
            )
            other = PatientPortalAccount(
                clinic_id=victim.clinic_id,
                demographic_no=9911,
                username="other.patient",
                email="other.patient@example.com",
                preferred_mfa_method="email",
                password_hash=victim.password_hash,
                status=victim.status,
                created_at=utc_now(),
                updated_at=utc_now(),
            )
            session.add(other)
            session.flush()
            other_id = other.id

    response = client.post(
        "/auth/password-reset/complete",
        json={"reset_token": reset_token, "new_password": "CrossAccount2026!x"},
    )

    # The token belongs to patient.user, so it may only ever change patient.user's password.
    assert response.status_code == 200
    with app.state.session_factory() as session:
        assert session.get(PatientPortalAccount, other_id).password_hash == (
            session.scalar(
                select(PatientPortalAccount).where(
                    PatientPortalAccount.username == "other.patient"
                )
            ).password_hash
        )
    assert (
        client.post(
            "/auth/login",
            json={"username": "other.patient", "password": "CrossAccount2026!x"},
        ).status_code
        == 401
    )
