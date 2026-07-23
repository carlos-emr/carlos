import re
from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy import select
from sqlalchemy.engine import make_url
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from carlos_patient_portal import cli, main
from carlos_patient_portal.audit import hash_sensitive_reference
from carlos_patient_portal.config import (
    DEFAULT_DATABASE_URL,
    MIN_PRODUCTION_SECRET_LENGTH,
    Settings,
)
from carlos_patient_portal.database import (
    Base,
    create_portal_engine,
    create_session_factory,
    session_scope,
)
from carlos_patient_portal.identity import IdentityProof
from carlos_patient_portal.interop import (
    FHIR_RELEASE,
    HL7_V2_VERSION,
    PortalPatientInteroperabilityIdentity,
    build_fhir_r4_patient,
    build_hl7_v251_patient_registration,
    validate_hl7_v251_message,
)
from carlos_patient_portal.invites import (
    DEFAULT_INVITE_TTL,
    InviteNotFoundError,
    create_invite,
    hash_invite_token,
    list_invites,
    resend_invite,
    revoke_invite,
)
from carlos_patient_portal.models import (
    AUDIT_EVENT_ACCOUNT_LOCK,
    AUDIT_EVENT_ACCOUNT_UNLOCK,
    AUDIT_EVENT_ACTIVATION,
    AUDIT_EVENT_INVITE_CREATE,
    AUDIT_EVENT_INVITE_LIST,
    AUDIT_EVENT_INVITE_RESEND,
    AUDIT_EVENT_INVITE_REVOKE,
    AUDIT_EVENT_LOGIN,
    AUDIT_EVENT_MFA_CHALLENGE,
    AUDIT_EVENT_MFA_RESEND,
    AUDIT_EVENT_MFA_VERIFY,
    AUDIT_EVENT_PASSWORD_RESET_COMPLETE,
    AUDIT_EVENT_PASSWORD_RESET_REQUEST,
    AUDIT_EVENT_SESSION_LOGOUT,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    AUDIT_OUTCOME_THROTTLED,
    INVITE_STATUS_ACCEPTED,
    INVITE_STATUS_PENDING,
    INVITE_STATUS_REVOKED,
    MFA_DELIVERY_METHOD_SMS,
    PatientPortalAccount,
    PatientPortalAuditEvent,
    PatientPortalInvite,
    PatientPortalMfaChallenge,
    PatientPortalPasswordResetToken,
    PatientPortalSession,
    utc_now,
)

NON_DEVELOPMENT_SESSION_SECRET = "s" * MIN_PRODUCTION_SECRET_LENGTH
IDENTITY_PROOF_SECRET = "i" * MIN_PRODUCTION_SECRET_LENGTH
AUDIT_HASH_SECRET = "a" * MIN_PRODUCTION_SECRET_LENGTH
INTERNAL_HEALTH_TOKEN = "h" * MIN_PRODUCTION_SECRET_LENGTH
WRONG_INTERNAL_HEALTH_TOKEN = "w" * MIN_PRODUCTION_SECRET_LENGTH
DEV_ADMIN_TOKEN = "d" * MIN_PRODUCTION_SECRET_LENGTH
WRONG_DEV_ADMIN_TOKEN = "x" * MIN_PRODUCTION_SECRET_LENGTH
CSRF_TOKEN_PATTERN = re.compile(r'name="csrf_token" value="([^"]+)"')
SEEDED_INVITE_EMAIL = "example.patient@example.com"
SEEDED_INVITE_DOB = "1980-05-20"
SEEDED_INVITE_HCN = "ABCD 1234-5678"
STRONG_PASSWORD = "Stronger1!word"
STRONG_RESET_PASSWORD = "Changed1!word"


def development_settings(**overrides: object) -> Settings:
    values = {"environment": "development", **overrides}
    return Settings(**values)


def migrated_development_app(**overrides: object) -> main.FastAPI:
    settings_values = {
        "database_url": "sqlite+pysqlite:///:memory:",
        "enable_dev_admin": True,
        "dev_admin_token": DEV_ADMIN_TOKEN,
        "identity_proof_secret": IDENTITY_PROOF_SECRET,
        "audit_hash_secret": AUDIT_HASH_SECRET,
        **overrides,
    }
    app = main.create_app(development_settings(**settings_values))
    Base.metadata.create_all(app.state.database_engine)
    return app


def dev_admin_headers(
    token: str = DEV_ADMIN_TOKEN,
    *,
    actor: str = "Dr example",
) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {token}",
        main.DEV_ADMIN_ACTOR_HEADER: actor,
    }


def get_csrf_token(client: TestClient) -> str:
    response = client.get("/")
    match = CSRF_TOKEN_PATTERN.search(response.text)

    assert response.status_code == 200
    assert match is not None
    csrf_token = match.group(1)
    assert response.cookies.get(main.CSRF_COOKIE_NAME) == csrf_token
    return csrf_token


def parse_response_datetime(value: str) -> datetime:
    parsed_datetime = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed_datetime.tzinfo is None:
        return parsed_datetime.replace(tzinfo=UTC)
    return parsed_datetime


def seeded_invite_request(**overrides: object) -> dict[str, object]:
    request_payload: dict[str, object] = {
        "demographic_no": 1234,
        "email": SEEDED_INVITE_EMAIL,
        "date_of_birth": SEEDED_INVITE_DOB,
        "health_card_number": SEEDED_INVITE_HCN,
    }
    request_payload.update(overrides)
    return request_payload


def seeded_identity_proof(**overrides: object) -> IdentityProof:
    proof_values: dict[str, object] = {
        "email": SEEDED_INVITE_EMAIL,
        "date_of_birth": datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        "health_card_number": SEEDED_INVITE_HCN,
    }
    proof_values.update(overrides)
    return IdentityProof(**proof_values)


def create_service_invite(
    session: Session,
    demographic_no: int = 1234,
    actor: str = "Dr example",
    *,
    clinic_id: str = "default",
    identity_proof: IdentityProof | None = None,
) -> tuple[PatientPortalInvite, str]:
    return create_invite(
        session,
        demographic_no,
        actor,
        identity_proof=identity_proof or seeded_identity_proof(),
        proof_secret=IDENTITY_PROOF_SECRET,
        clinic_id=clinic_id,
    )


def activation_request(invite_code: str, **overrides: object) -> dict[str, object]:
    request_payload: dict[str, object] = {
        "invite_code": invite_code,
        "email": f" {SEEDED_INVITE_EMAIL.upper()} ",
        "date_of_birth": SEEDED_INVITE_DOB,
        "health_card_number": "ABCD-1234 5678",
        "username": "Patient.User",
        "password": STRONG_PASSWORD,
    }
    request_payload.update(overrides)
    return request_payload


def activate_seeded_patient_account(
    app: main.FastAPI,
    client: TestClient,
    *,
    username: str = "patient.user",
    password: str = STRONG_PASSWORD,
    demographic_no: int = 1234,
    email: str = SEEDED_INVITE_EMAIL,
    health_card_number: str = SEEDED_INVITE_HCN,
) -> int:
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(
            demographic_no=demographic_no,
            email=email,
            health_card_number=health_card_number,
        ),
    )
    assert create_response.status_code == 201
    activate_response = client.post(
        "/auth/activate",
        json=activation_request(
            create_response.json()["invite_token"],
            username=username,
            password=password,
            email=email,
            health_card_number=health_card_number,
        ),
    )
    assert activate_response.status_code == 201
    with app.state.session_factory() as session:
        account_id = session.scalar(
            select(PatientPortalAccount.id).where(PatientPortalAccount.username == username)
        )
    assert account_id is not None
    return account_id


def sign_in_seeded_patient(app: main.FastAPI, client: TestClient) -> tuple[int, str]:
    account_id = activate_seeded_patient_account(app, client)
    login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )
    assert login_response.status_code == 200
    login_payload = login_response.json()
    verify_response = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": login_payload["mfa_challenge_token"],
            "code": login_payload["development_mfa_code"],
        },
    )
    assert verify_response.status_code == 200
    set_cookie_header = verify_response.headers.get("set-cookie", "")
    assert main.PORTAL_SESSION_COOKIE_NAME in verify_response.cookies
    assert f"{main.PORTAL_SESSION_COOKIE_NAME}=" in set_cookie_header
    assert "HttpOnly" in set_cookie_header
    assert "Path=/portal" in set_cookie_header
    assert "SameSite=strict" in set_cookie_header
    return account_id, verify_response.json()["session_token"]


def test_health_endpoint_is_minimal() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_index_renders_sign_in_shell() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get("/")

    assert response.status_code == 200
    assert "CARLOS Patient Portal" in response.text
    assert 'placeholder="patient.username"' in response.text
    assert 'value="patient.username"' not in response.text
    assert 'name="csrf_token"' in response.text
    assert "nosemgrep" not in response.text
    assert "Maple Creek Medical" in response.text


def test_static_logo_asset_is_served() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get("/static/carlos-placeholder.svg")

    assert response.status_code == 200
    assert "image/svg+xml" in response.headers["content-type"]
    assert "<svg" in response.text


def test_sign_in_shell_uses_security_headers() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get("/")

    assert response.headers["content-security-policy"] == (
        "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; "
        "object-src 'none'"
    )
    assert response.headers["x-frame-options"] == "DENY"
    assert response.headers["x-content-type-options"] == "nosniff"
    assert response.headers["referrer-policy"] == "same-origin"
    assert response.headers["cache-control"] == "no-store"
    assert response.headers["pragma"] == "no-cache"


def test_production_responses_include_hsts() -> None:
    app = main.create_app(
        Settings(
            environment="production",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).get("/")

    assert response.headers["strict-transport-security"] == (
        "max-age=31536000; includeSubDomains"
    )


def test_non_development_csrf_cookie_is_secure() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).get("/")
    set_cookie = response.headers["set-cookie"]

    assert f"{main.CSRF_COOKIE_NAME}=" in set_cookie
    assert "HttpOnly" in set_cookie
    assert "Path=/auth" in set_cookie
    assert "SameSite=strict" in set_cookie
    assert "Secure" in set_cookie


def test_internal_database_health_uses_app_database_settings() -> None:
    app = main.create_app(development_settings(database_url="sqlite+pysqlite:///:memory:"))
    response = TestClient(app).get("/internal/health/db")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "ok"}


def test_public_database_health_path_is_not_registered() -> None:
    app = main.create_app(development_settings())

    assert TestClient(app).get("/health/db").status_code == 404


def test_internal_database_health_requires_configured_token() -> None:
    app = main.create_app(
        development_settings(
            database_url="sqlite+pysqlite:///:memory:",
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    client = TestClient(app)

    assert client.get("/internal/health/db").status_code == 404
    assert client.get(
        "/internal/health/db",
        headers={"Authorization": f"Bearer {WRONG_INTERNAL_HEALTH_TOKEN}"},
    ).status_code == 404
    assert client.get(
        "/internal/health/db",
        headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
    ).status_code == 200


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
            (AUDIT_EVENT_MFA_VERIFY, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_SESSION_LOGOUT, AUDIT_OUTCOME_SUCCESS),
        ]


def test_dashboard_shell_requires_session_cookie() -> None:
    app = migrated_development_app()
    response = TestClient(app).get("/portal", follow_redirects=False)

    assert response.status_code == 303
    assert response.headers["location"] == "/"
    assert response.headers["cache-control"] == "no-store"


def test_dashboard_shell_navigation_and_cookie_logout() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id, _ = sign_in_seeded_patient(app, client)

    dashboard_response = client.get("/portal")

    assert dashboard_response.status_code == 200
    assert dashboard_response.headers["cache-control"] == "no-store"
    assert "HttpOnly" in dashboard_response.headers["set-cookie"]
    assert "Path=/portal" in dashboard_response.headers["set-cookie"]
    assert "SameSite=strict" in dashboard_response.headers["set-cookie"]
    assert 'data-active-module="account"' in dashboard_response.text
    assert 'href="/portal/account"' in dashboard_response.text
    assert 'href="/portal/email-passwords"' in dashboard_response.text
    assert 'href="/portal/help"' in dashboard_response.text
    assert 'class="logout-form"' in dashboard_response.text
    assert ">Logout</button>" in dashboard_response.text
    assert "patient.user" in dashboard_response.text

    email_passwords_response = client.get("/portal/email-passwords")
    help_response = client.get("/portal/help")

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
    assert "<th scope=\"col\">Subject</th>" in email_passwords_response.text
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


def test_portal_logout_rejects_invalid_csrf_without_revoking_session() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id, _ = sign_in_seeded_patient(app, client)

    dashboard_response = client.get("/portal")
    logout_response = client.post(
        "/portal/logout",
        data={"csrf_token": "invalid"},
        follow_redirects=False,
    )
    still_authenticated_response = client.get("/portal")

    assert dashboard_response.status_code == 200
    assert logout_response.status_code == 303
    assert logout_response.headers["location"] == "/"
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


def test_dashboard_styles_include_desktop_and_mobile_navigation_rules() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get("/static/styles.css")
    css = response.text

    assert response.status_code == 200
    assert ".dashboard-layout" in css
    assert "grid-template-columns: 220px minmax(0, 1fr);" in css
    assert "@media (max-width: 640px)" in css
    assert ".portal-topbar" in css
    assert "flex-direction: row;" in css
    assert ".module-nav" in css


def test_login_route_rejects_missing_csrf_token() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).post(
        "/auth/login",
        data={"username": "patient.username", "password": "unused"},
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "invalid CSRF token"


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

    assert response.status_code == 403
    assert response.json()["detail"] == "invalid CSRF token"


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

    assert response.status_code == 403
    assert response.json()["detail"] == "invalid CSRF token"


def test_login_route_rejects_oversized_form_body() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    csrf_token = get_csrf_token(client)
    response = client.post(
        "/auth/login",
        data={
            "csrf_token": csrf_token,
            "username": "patient.username",
            "password": "x" * main.MAX_FORM_BODY_BYTES,
        },
    )

    assert response.status_code == 413
    assert response.json()["detail"] == "request body too large"


def test_login_route_rejects_malformed_urlencoded_form_body() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    get_csrf_token(client)
    response = client.post(
        "/auth/login",
        content="csrf_token",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "invalid form body"


def test_login_route_rejects_invalid_utf8_form_body() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).post(
        "/auth/login",
        content=b"csrf_token=\xff",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "invalid form body"


def test_login_route_rejects_too_many_form_fields() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    get_csrf_token(client)
    form_body = "&".join(
        f"field{field_number}=x" for field_number in range(main.MAX_FORM_FIELD_COUNT + 1)
    )
    response = client.post(
        "/auth/login",
        content=form_body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "invalid form body"


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


def test_mfa_resend_limits_email_and_allows_switch_to_sms() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = activate_seeded_patient_account(app, client)
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.phone_number = "+1 555 123 4567"
        account.preferred_mfa_method = MFA_DELIVERY_METHOD_SMS
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
    assert sms_response.json()["mfa_delivery_method"] == "sms"
    assert re.fullmatch(r"\d{6}", sms_response.json()["development_mfa_code"])
    assert throttled_sms_response.status_code == 429
    assert throttled_sms_response.headers["retry-after"] == "300"

    verify_response = client.post(
        "/auth/mfa/verify",
        json={
            "mfa_challenge_token": challenge_token,
            "code": sms_response.json()["development_mfa_code"],
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
    assert lock_response.status_code == 423
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


def test_api_docs_are_available_in_development() -> None:
    app = main.create_app(development_settings())

    assert TestClient(app).get("/api/openapi.json").status_code == 200


def test_api_docs_are_disabled_outside_development() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )

    assert TestClient(app).get("/api/openapi.json").status_code == 404
    assert TestClient(app).get("/api/docs").status_code == 404
    assert TestClient(app).get("/api/redoc").status_code == 404


def test_api_docs_are_disabled_in_production() -> None:
    app = main.create_app(
        Settings(
            environment="production",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )

    assert TestClient(app).get("/api/openapi.json").status_code == 404
    assert TestClient(app).get("/api/docs").status_code == 404
    assert TestClient(app).get("/api/redoc").status_code == 404


def test_dev_admin_invite_lifecycle() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor=" Dr example "),
        json=seeded_invite_request(),
    )

    assert create_response.status_code == 201
    created_invite = create_response.json()
    invite_id = created_invite["id"]
    invite_token = created_invite["invite_token"]
    assert created_invite["clinic_id"] == "default"
    assert created_invite["demographic_no"] == 1234
    assert created_invite["status"] == "pending"
    assert created_invite["created_by"] == "Dr example"
    assert created_invite["last_sent_by"] == "Dr example"
    assert created_invite["sent_count"] == 1
    assert created_invite["has_identity_proof"] is True
    assert created_invite["accepted_at"] is None
    assert created_invite["accepted_account_id"] is None
    created_expires_at = parse_response_datetime(created_invite["expires_at"])
    created_at = parse_response_datetime(created_invite["created_at"])
    assert DEFAULT_INVITE_TTL - timedelta(seconds=1) <= created_expires_at - created_at
    assert created_expires_at - created_at <= DEFAULT_INVITE_TTL + timedelta(seconds=1)
    assert create_response.headers["cache-control"] == "no-store"

    with app.state.session_factory() as session:
        persisted_invite = session.get(PatientPortalInvite, invite_id)
        assert persisted_invite is not None
        assert persisted_invite.clinic_id == "default"
        assert persisted_invite.token_hash == hash_invite_token(invite_token)
        assert persisted_invite.token_hash != invite_token
        assert persisted_invite.expires_at is not None
        assert persisted_invite.proof_salt is not None
        assert persisted_invite.proof_hash_version == "v1"

    list_response = client.get(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        params={"demographic_no": 1234},
    )

    assert list_response.status_code == 200
    listed_invites = list_response.json()
    assert len(listed_invites) == 1
    assert listed_invites[0]["id"] == invite_id
    assert listed_invites[0]["clinic_id"] == "default"
    assert parse_response_datetime(listed_invites[0]["expires_at"]) == created_expires_at
    assert listed_invites[0]["has_identity_proof"] is True
    assert "invite_token" not in listed_invites[0]
    assert list_response.headers["cache-control"] == "no-store"

    resend_response = client.post(
        f"/dev/admin/invites/{invite_id}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert resend_response.status_code == 200
    resent_invite = resend_response.json()
    resent_token = resent_invite["invite_token"]
    assert resent_token != invite_token
    assert resent_invite["sent_count"] == 2
    assert resent_invite["last_sent_by"] == "Admin example"
    assert parse_response_datetime(resent_invite["expires_at"]) >= created_expires_at
    assert resend_response.headers["cache-control"] == "no-store"

    revoke_response = client.post(
        f"/dev/admin/invites/{invite_id}/revoke",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert revoke_response.status_code == 200
    revoked_invite = revoke_response.json()
    assert revoked_invite["status"] == "revoked"
    assert revoked_invite["revoked_by"] == "Admin example"
    assert "invite_token" not in revoked_invite

    revoked_resend_response = client.post(
        f"/dev/admin/invites/{invite_id}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert revoked_resend_response.status_code == 409
    assert revoked_resend_response.json()["detail"] == "invite has been revoked"


def test_new_invite_replaces_older_pending_invite_for_patient() -> None:
    app = migrated_development_app()
    client = TestClient(app)

    first_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="Dr example"),
        json=seeded_invite_request(),
    )
    second_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="Admin example"),
        json=seeded_invite_request(),
    )

    assert first_create_response.status_code == 201
    assert second_create_response.status_code == 201
    first_invite = first_create_response.json()
    second_invite = second_create_response.json()
    assert second_invite["id"] != first_invite["id"]

    list_response = client.get(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        params={"demographic_no": 1234},
    )
    listed_invites = list_response.json()
    assert [invite["id"] for invite in listed_invites] == [
        second_invite["id"],
        first_invite["id"],
    ]
    assert [invite["status"] for invite in listed_invites] == ["pending", "revoked"]
    assert listed_invites[1]["revoked_by"] == "Admin example"

    old_activation_response = client.post(
        "/auth/activate",
        json=activation_request(first_invite["invite_token"]),
    )
    latest_activation_response = client.post(
        "/auth/activate",
        json=activation_request(second_invite["invite_token"]),
    )

    assert old_activation_response.status_code == 400
    assert latest_activation_response.status_code == 201


def test_invalid_replacement_invite_does_not_revoke_existing_pending_invite() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        pending_invite, _ = create_service_invite(session)
        session.commit()

        with pytest.raises(ValueError, match="email"):
            create_invite(
                session,
                1234,
                "Admin example",
                identity_proof=seeded_identity_proof(email="not-an-email"),
                proof_secret=IDENTITY_PROOF_SECRET,
            )

        persisted_invite = session.get(PatientPortalInvite, pending_invite.id)

        assert persisted_invite is not None
        assert persisted_invite.status == INVITE_STATUS_PENDING
        assert persisted_invite.revoked_at is None
        assert persisted_invite.revoked_by is None


def test_patient_activation_creates_account_from_seeded_invite() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert create_response.status_code == 201
    created_invite = create_response.json()
    invite_id = created_invite["id"]
    invite_token = created_invite["invite_token"]
    assert created_invite["has_identity_proof"] is True

    with app.state.session_factory() as session:
        persisted_invite = session.get(PatientPortalInvite, invite_id)
        assert persisted_invite is not None
        assert persisted_invite.proof_email_hash is not None
        assert persisted_invite.proof_date_of_birth_hash is not None
        assert persisted_invite.proof_health_card_hash is not None
        assert persisted_invite.proof_salt is not None
        assert persisted_invite.proof_email_hash != SEEDED_INVITE_EMAIL
        assert persisted_invite.proof_health_card_hash != SEEDED_INVITE_HCN

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token),
    )

    assert activation_response.status_code == 201
    assert activation_response.json() == {"status": "activated", "username": "patient.user"}
    assert activation_response.headers["cache-control"] == "no-store"

    with app.state.session_factory() as session:
        account = session.scalar(
            select(PatientPortalAccount).where(PatientPortalAccount.username == "patient.user")
        )
        accepted_invite = session.get(PatientPortalInvite, invite_id)

        assert account is not None
        assert account.clinic_id == "default"
        assert account.demographic_no == 1234
        assert account.email == SEEDED_INVITE_EMAIL
        assert account.password_hash.startswith("$argon2id$")
        assert account.password_hash != STRONG_PASSWORD
        assert accepted_invite is not None
        assert accepted_invite.status == INVITE_STATUS_ACCEPTED
        assert accepted_invite.accepted_account_id == account.id
        assert accepted_invite.accepted_at is not None
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent).order_by(PatientPortalAuditEvent.id)
            )
        )
        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_INVITE_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_ACTIVATION, AUDIT_OUTCOME_SUCCESS),
        ]
        assert audit_events[-1].account_id == account.id
        assert audit_events[-1].invite_id == invite_id

    second_activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, username="another.patient"),
    )

    assert second_activation_response.status_code == 400
    assert second_activation_response.json()["detail"] == (
        "activation details could not be verified"
    )


def test_dev_admin_invite_rejects_patient_with_existing_account() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]
    activation_response = client.post("/auth/activate", json=activation_request(invite_token))

    duplicate_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert activation_response.status_code == 201
    assert duplicate_create_response.status_code == 409
    assert duplicate_create_response.json()["detail"] == "patient already has a portal account"


def test_patient_activation_rejects_identity_mismatch_without_account_leak() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )

    assert activation_response.status_code == 400
    assert activation_response.json()["detail"] == "activation details could not be verified"

    with app.state.session_factory() as session:
        assert session.scalar(select(PatientPortalAccount.id)) is None
        invite = session.scalar(select(PatientPortalInvite))
        audit_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION
            )
        )
        assert invite is not None
        assert invite.status == INVITE_STATUS_PENDING
        assert audit_event is not None
        assert audit_event.outcome == AUDIT_OUTCOME_FAILURE
        assert audit_event.reason == "invalid_details"


def test_patient_activation_rejects_expired_invite() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    created_invite = create_response.json()
    invite_token = created_invite["invite_token"]

    with app.state.session_factory() as session:
        invite = session.get(PatientPortalInvite, created_invite["id"])
        assert invite is not None
        invite.created_at = utc_now() - timedelta(days=8)
        invite.expires_at = utc_now() - timedelta(days=1)
        session.commit()

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token),
    )

    assert activation_response.status_code == 400
    assert activation_response.json()["detail"] == "activation details could not be verified"


def test_patient_activation_rejects_unavailable_username() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    first_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    first_token = first_create_response.json()["invite_token"]
    second_create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(
            demographic_no=5678,
            email="second.patient@example.com",
            health_card_number="ZXCV 1234",
        ),
    )
    second_token = second_create_response.json()["invite_token"]

    assert client.post("/auth/activate", json=activation_request(first_token)).status_code == 201

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(
            second_token,
            email="second.patient@example.com",
            health_card_number="ZXCV-1234",
        ),
    )

    assert activation_response.status_code == 409
    assert activation_response.json()["detail"] == "username unavailable"


def test_patient_activation_rejects_weak_password() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    activation_response = client.post(
        "/auth/activate",
        json=activation_request(create_response.json()["invite_token"], password="weak"),
    )

    assert activation_response.status_code == 422
    assert "weak" not in activation_response.text


def test_patient_activation_rejects_oversized_json_body() -> None:
    app = migrated_development_app()
    oversized_body = (
        b'{"invite_code":"'
        + b"x" * main.MAX_JSON_BODY_BYTES
        + b'","email":"example.patient@example.com"}'
    )

    response = TestClient(app).post(
        "/auth/activate",
        content=oversized_body,
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 413
    assert response.json()["detail"] == "request body too large"


def test_patient_activation_requires_json_body() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/auth/activate",
        data={"invite_code": "unused"},
    )

    assert response.status_code == 415
    assert response.json()["detail"] == "activation requires an application/json request body"


def test_patient_activation_validation_does_not_echo_health_card_number() -> None:
    app = migrated_development_app()
    invalid_health_card_number = "bad card ?"

    response = TestClient(app).post(
        "/auth/activate",
        json=activation_request("unused", health_card_number=invalid_health_card_number),
    )

    assert response.status_code == 422
    assert invalid_health_card_number not in response.text


def test_patient_activation_rejects_too_short_health_card_number() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/auth/activate",
        json=activation_request("unused", health_card_number="A1"),
    )

    assert response.status_code == 422


def test_patient_activation_rate_limits_failed_attempts() -> None:
    app = migrated_development_app(
        session_secret=NON_DEVELOPMENT_SESSION_SECRET,
        activation_max_failures_per_invite=2,
        activation_max_failures_per_client=50,
    )
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]

    for _ in range(2):
        response = client.post(
            "/auth/activate",
            json=activation_request(invite_token, health_card_number="WRONG1234"),
        )
        assert response.status_code == 400

    throttled_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )

    assert throttled_response.status_code == 429
    assert throttled_response.headers["retry-after"] == "3600"
    expected_client_hash = hash_sensitive_reference(
        AUDIT_HASH_SECRET,
        "activation_client",
        "testclient",
    )
    with app.state.session_factory() as session:
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION)
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert [event.outcome for event in audit_events] == [
            AUDIT_OUTCOME_FAILURE,
            AUDIT_OUTCOME_FAILURE,
            AUDIT_OUTCOME_THROTTLED,
        ]
        assert all(event.client_reference_hash == expected_client_hash for event in audit_events)


def test_patient_activation_rate_limit_can_use_trusted_proxy_header() -> None:
    app = migrated_development_app(
        session_secret=NON_DEVELOPMENT_SESSION_SECRET,
        trusted_client_ip_header="x-forwarded-for",
    )
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]
    response = client.post(
        "/auth/activate",
        headers={"X-Forwarded-For": "203.0.113.7, 10.0.0.10"},
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )

    assert response.status_code == 400
    expected_client_hash = hash_sensitive_reference(
        AUDIT_HASH_SECRET,
        "activation_client",
        "203.0.113.7",
    )
    with app.state.session_factory() as session:
        audit_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION
            )
        )
        assert audit_event is not None
        assert audit_event.client_reference_hash == expected_client_hash


def test_patient_activation_rate_limit_window_expires() -> None:
    app = migrated_development_app(
        activation_failure_window_seconds=60,
        activation_max_failures_per_invite=1,
        activation_max_failures_per_client=50,
    )
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_token = create_response.json()["invite_token"]
    failed_response = client.post(
        "/auth/activate",
        json=activation_request(invite_token, health_card_number="WRONG1234"),
    )
    assert failed_response.status_code == 400

    with app.state.session_factory() as session:
        audit_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACTIVATION
            )
        )
        assert audit_event is not None
        audit_event.created_at = utc_now() - timedelta(minutes=2)
        session.commit()

    activation_response = client.post("/auth/activate", json=activation_request(invite_token))

    assert activation_response.status_code == 201


def test_accepted_invites_cannot_be_resent_or_revoked() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    created_invite = create_response.json()

    assert client.post(
        "/auth/activate",
        json=activation_request(created_invite["invite_token"]),
    ).status_code == 201

    resend_response = client.post(
        f"/dev/admin/invites/{created_invite['id']}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    )
    revoke_response = client.post(
        f"/dev/admin/invites/{created_invite['id']}/revoke",
        headers=dev_admin_headers(actor="Admin example"),
    )

    assert resend_response.status_code == 409
    assert resend_response.json()["detail"] == "invite has already been accepted"
    assert revoke_response.status_code == 409
    assert revoke_response.json()["detail"] == "invite has already been accepted"


def test_dev_admin_invites_are_hidden_outside_development() -> None:
    app = main.create_app(
        Settings(
            environment="staging",
            enable_dev_admin=True,
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert response.status_code == 404


def test_dev_admin_invites_require_explicit_development_flag() -> None:
    app = main.create_app(
        development_settings(
            database_url="sqlite+pysqlite:///:memory:",
            identity_proof_secret=IDENTITY_PROOF_SECRET,
        )
    )
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )

    assert response.status_code == 404


def test_dev_admin_invites_require_bearer_token() -> None:
    app = migrated_development_app()
    client = TestClient(app)

    missing_token_response = client.post("/dev/admin/invites", json=seeded_invite_request())
    missing_token_invalid_body_response = client.post("/dev/admin/invites", json={})
    wrong_token_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(WRONG_DEV_ADMIN_TOKEN),
        json=seeded_invite_request(),
    )

    assert missing_token_response.status_code == 404
    assert missing_token_invalid_body_response.status_code == 404
    assert wrong_token_response.status_code == 404


def test_dev_admin_invite_creation_rejects_oversized_json_body_after_auth() -> None:
    app = migrated_development_app()
    oversized_body = b'{"demographic_no":1234,"email":"' + b"x" * main.MAX_JSON_BODY_BYTES + b'"}'

    missing_token_response = TestClient(app).post(
        "/dev/admin/invites",
        content=oversized_body,
        headers={"Content-Type": "application/json"},
    )
    authenticated_response = TestClient(app).post(
        "/dev/admin/invites",
        content=oversized_body,
        headers={
            "Content-Type": "application/json",
            **dev_admin_headers(),
        },
    )

    assert missing_token_response.status_code == 404
    assert authenticated_response.status_code == 413
    assert authenticated_response.json()["detail"] == "request body too large"


def test_dev_admin_invite_requires_identity_proof() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json={"demographic_no": 1234},
    )

    assert response.status_code == 422
    assert "health_card_number" in response.text


def test_dev_admin_invite_requires_positive_demographic_no() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(demographic_no=0),
    )

    assert response.status_code == 422


def test_dev_admin_invite_list_rejects_invalid_bounds() -> None:
    app = migrated_development_app()
    client = TestClient(app)

    assert (
        client.get("/dev/admin/invites", headers=dev_admin_headers(), params={"limit": 0})
        .status_code
        == 422
    )
    assert (
        client.get("/dev/admin/invites", headers=dev_admin_headers(), params={"limit": 101})
        .status_code
        == 422
    )
    assert (
        client.get("/dev/admin/invites", headers=dev_admin_headers(), params={"offset": -1})
        .status_code
        == 422
    )


def test_dev_admin_unknown_invite_returns_not_found() -> None:
    app = migrated_development_app()
    response = TestClient(app).post(
        "/dev/admin/invites/999/resend",
        headers=dev_admin_headers(),
    )

    assert response.status_code == 404
    assert response.json()["detail"] == "invite not found"


def test_invite_lifecycle_writes_audit_events() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(),
        json=seeded_invite_request(),
    )
    invite_id = create_response.json()["id"]
    assert client.post(
        f"/dev/admin/invites/{invite_id}/resend",
        headers=dev_admin_headers(actor="Admin example"),
    ).status_code == 200
    assert client.post(
        f"/dev/admin/invites/{invite_id}/revoke",
        headers=dev_admin_headers(actor="Admin example"),
    ).status_code == 200

    with app.state.session_factory() as session:
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent).order_by(PatientPortalAuditEvent.id)
            )
        )

        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_INVITE_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_INVITE_RESEND, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_INVITE_REVOKE, AUDIT_OUTCOME_SUCCESS),
        ]


def test_invite_list_writes_audit_event() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    create_response = client.post(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="Dr example"),
        json=seeded_invite_request(),
    )

    list_response = client.get(
        "/dev/admin/invites",
        headers=dev_admin_headers(actor="Admin example"),
        params={"demographic_no": 1234},
    )

    assert create_response.status_code == 201
    assert list_response.status_code == 200
    with app.state.session_factory() as session:
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent).order_by(PatientPortalAuditEvent.id)
            )
        )

        assert [(event.event_type, event.outcome) for event in audit_events] == [
            (AUDIT_EVENT_INVITE_CREATE, AUDIT_OUTCOME_SUCCESS),
            (AUDIT_EVENT_INVITE_LIST, AUDIT_OUTCOME_SUCCESS),
        ]
        assert audit_events[-1].actor == "Admin example"
        assert audit_events[-1].demographic_no == 1234


def test_invite_status_constraints_require_matching_metadata() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        accepted_invite, _ = create_service_invite(session)
        session.commit()

        accepted_invite.status = INVITE_STATUS_ACCEPTED
        with pytest.raises(IntegrityError):
            session.commit()
        session.rollback()

        revoked_invite, _ = create_service_invite(session, demographic_no=5678)
        session.commit()

        revoked_invite.status = INVITE_STATUS_REVOKED
        with pytest.raises(IntegrityError):
            session.commit()


def test_invite_constraints_allow_only_one_pending_invite_per_patient() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        first_invite, _ = create_service_invite(session)
        session.commit()

        duplicate_pending_invite = PatientPortalInvite(
            clinic_id=first_invite.clinic_id,
            demographic_no=first_invite.demographic_no,
            token_hash=hash_invite_token("manual-duplicate-token"),
            status=INVITE_STATUS_PENDING,
            created_by="Dr example",
            created_at=utc_now(),
            updated_at=utc_now(),
            sent_count=1,
            last_sent_at=utc_now(),
            last_sent_by="Dr example",
            expires_at=utc_now() + DEFAULT_INVITE_TTL,
            proof_email_hash=first_invite.proof_email_hash,
            proof_date_of_birth_hash=first_invite.proof_date_of_birth_hash,
            proof_health_card_hash=first_invite.proof_health_card_hash,
            proof_salt=first_invite.proof_salt,
            proof_hash_version=first_invite.proof_hash_version,
        )
        session.add(duplicate_pending_invite)
        with pytest.raises(IntegrityError):
            session.commit()


def test_invite_service_validates_future_carlos_callers() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        with pytest.raises(ValueError, match="demographic_no"):
            create_invite(
                session,
                0,
                "Dr example",
                identity_proof=seeded_identity_proof(),
                proof_secret=IDENTITY_PROOF_SECRET,
            )
        with pytest.raises(ValueError, match="actor"):
            create_invite(
                session,
                1234,
                " ",
                identity_proof=seeded_identity_proof(),
                proof_secret=IDENTITY_PROOF_SECRET,
            )
        with pytest.raises(ValueError, match="actor"):
            create_invite(
                session,
                1234,
                "x" * 129,
                identity_proof=seeded_identity_proof(),
                proof_secret=IDENTITY_PROOF_SECRET,
            )
        with pytest.raises(ValueError, match="proof_secret"):
            create_invite(
                session,
                1234,
                "Dr example",
                identity_proof=seeded_identity_proof(),
                proof_secret=" ",
            )
        with pytest.raises(ValueError, match="demographic_no"):
            list_invites(session, demographic_no=0)
        with pytest.raises(ValueError, match="limit"):
            list_invites(session, limit=0)
        with pytest.raises(ValueError, match="limit"):
            list_invites(session, limit=101)
        with pytest.raises(ValueError, match="offset"):
            list_invites(session, offset=-1)
        with pytest.raises(InviteNotFoundError):
            resend_invite(session, 999, " ")
        with pytest.raises(InviteNotFoundError):
            revoke_invite(session, 999, " ")


def test_invite_service_scopes_records_by_clinic() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        clinic_a_invite, _ = create_service_invite(
            session,
            1234,
            "Dr example",
            clinic_id="clinic-a",
        )
        clinic_b_invite, _ = create_service_invite(
            session,
            1234,
            "Dr example",
            clinic_id="clinic-b",
        )
        session.commit()

        clinic_a_invites = list_invites(session, demographic_no=1234, clinic_id="clinic-a")
        clinic_b_invites = list_invites(session, demographic_no=1234, clinic_id="clinic-b")

        assert [invite.id for invite in clinic_a_invites] == [clinic_a_invite.id]
        assert [invite.id for invite in clinic_b_invites] == [clinic_b_invite.id]
        with pytest.raises(InviteNotFoundError):
            resend_invite(
                session,
                clinic_a_invite.id,
                "Dr example",
                clinic_id="clinic-b",
            )


def test_invite_identity_proof_hashes_are_salted_per_invite() -> None:
    app = migrated_development_app()
    with app.state.session_factory() as session:
        first_invite, _ = create_service_invite(session, demographic_no=1234)
        second_invite, _ = create_service_invite(session, demographic_no=5678)

        assert first_invite.proof_salt is not None
        assert second_invite.proof_salt is not None
        assert first_invite.proof_salt != second_invite.proof_salt
        assert first_invite.proof_email_hash != second_invite.proof_email_hash
        assert first_invite.proof_health_card_hash != second_invite.proof_health_card_hash


def test_interop_helpers_build_valid_fhir_r4_and_hl7_v251_patient_identity() -> None:
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )

    fhir_patient = build_fhir_r4_patient(identity)
    hl7_message = build_hl7_v251_patient_registration(
        identity,
        message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
        message_control_id="MSG0001",
    )

    assert FHIR_RELEASE == "R4"
    assert fhir_patient["resourceType"] == "Patient"
    assert fhir_patient["birthDate"] == SEEDED_INVITE_DOB
    assert fhir_patient["name"][0]["family"] == "Patient"
    assert HL7_V2_VERSION == "2.5.1"
    assert "MSH|^~\\&|CARLOS|default|CARLOSPORTAL|default" in hl7_message
    assert "PID|||1234^^^default^MR~ABCD12345678^^^CARLOSHCN^JHN" in hl7_message
    assert "^NET^Internet^example.patient@example.com" in hl7_message
    assert "ADT^A04^ADT_A01" in hl7_message
    assert validate_hl7_v251_message(hl7_message) == hl7_message


def test_hl7_patient_identity_rejects_unsafe_hl7_values() -> None:
    identity = PortalPatientInteroperabilityIdentity(
        clinic_id="clinic-with-a-very-long-id-over-twenty-chars",
        demographic_no=1234,
        email=SEEDED_INVITE_EMAIL,
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )
    email_with_separator = PortalPatientInteroperabilityIdentity(
        clinic_id="default",
        demographic_no=1234,
        email="a&b@example.com",
        date_of_birth=datetime.fromisoformat(SEEDED_INVITE_DOB).date(),
        health_card_number=SEEDED_INVITE_HCN,
        family_name="Patient",
        given_name="Example",
    )

    with pytest.raises(ValueError, match="clinic_id"):
        build_hl7_v251_patient_registration(
            identity,
            message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
            message_control_id="MSG0001",
        )
    with pytest.raises(ValueError, match="email"):
        build_hl7_v251_patient_registration(
            email_with_separator,
            message_time=datetime(2026, 7, 23, 12, 0, tzinfo=UTC),
            message_control_id="MSG0001",
        )


def test_session_scope_commits_success_and_rolls_back_failure() -> None:
    engine = create_portal_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    session_factory = create_session_factory(engine)

    with session_scope(session_factory) as session:
        committed_invite, _ = create_service_invite(session, 1234, "Dr example")
        committed_invite_id = committed_invite.id

    with session_factory() as session:
        assert session.get(PatientPortalInvite, committed_invite_id) is not None

    with pytest.raises(RuntimeError, match="force rollback"):
        with session_scope(session_factory) as session:
            create_service_invite(session, 5678, "Dr example")
            raise RuntimeError("force rollback")

    with session_factory() as session:
        assert list_invites(session, demographic_no=5678) == []

    engine.dispose()


def test_environment_aliases_are_normalized() -> None:
    settings = Settings(
        environment=" prod ",
        session_secret=NON_DEVELOPMENT_SESSION_SECRET,
        identity_proof_secret=IDENTITY_PROOF_SECRET,
        audit_hash_secret=AUDIT_HASH_SECRET,
        internal_health_token=INTERNAL_HEALTH_TOKEN,
    )

    assert settings.environment == "production"
    assert settings.is_production


def test_default_database_url_does_not_embed_credentials() -> None:
    database_url = make_url(DEFAULT_DATABASE_URL)

    assert database_url.username is None
    assert database_url.password is None


def test_development_defaults_do_not_embed_session_secret() -> None:
    assert development_settings().session_secret is None


def test_development_dev_admin_is_disabled_by_default() -> None:
    assert not development_settings().is_dev_admin_enabled
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_DEV_ADMIN_TOKEN"):
        development_settings(enable_dev_admin=True)
    assert development_settings(
        enable_dev_admin=True,
        dev_admin_token=DEV_ADMIN_TOKEN,
    ).is_dev_admin_enabled


def test_clinic_id_is_normalized() -> None:
    settings = development_settings(clinic_id=" clinic-a ")

    assert settings.clinic_id == "clinic-a"


def test_clinic_id_must_not_be_blank() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_CLINIC_ID"):
        development_settings(clinic_id=" ")


def test_trusted_client_ip_header_is_normalized() -> None:
    settings = development_settings(trusted_client_ip_header=" X-Forwarded-For ")

    assert settings.trusted_client_ip_header == "x-forwarded-for"


def test_default_settings_reject_missing_production_secrets() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings()


def test_invalid_environment_is_rejected() -> None:
    with pytest.raises(ValidationError, match="environment"):
        Settings(environment="sandbox")


def test_session_secret_must_not_be_blank() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        development_settings(session_secret=" ")


def test_production_rejects_missing_session_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(environment="production", internal_health_token=INTERNAL_HEALTH_TOKEN)


def test_non_development_rejects_missing_session_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(environment="staging", internal_health_token=INTERNAL_HEALTH_TOKEN)


def test_production_rejects_short_session_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SESSION_SECRET"):
        Settings(
            environment="production",
            session_secret="short-value",
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )


def test_production_rejects_missing_internal_health_token() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        Settings(environment="production", session_secret=NON_DEVELOPMENT_SESSION_SECRET)


def test_non_development_rejects_missing_internal_health_token() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        Settings(environment="staging", session_secret=NON_DEVELOPMENT_SESSION_SECRET)


def test_non_development_rejects_missing_identity_proof_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_IDENTITY_PROOF_SECRET"):
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )


def test_non_development_rejects_missing_audit_hash_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_AUDIT_HASH_SECRET"):
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )


def test_internal_health_token_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        development_settings(internal_health_token="short")


def test_identity_proof_secret_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_IDENTITY_PROOF_SECRET"):
        development_settings(identity_proof_secret="short")


def test_audit_hash_secret_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_AUDIT_HASH_SECRET"):
        development_settings(audit_hash_secret="short")


def test_dev_admin_token_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_DEV_ADMIN_TOKEN"):
        development_settings(dev_admin_token="short")


def test_activation_rate_limit_settings_are_bounded() -> None:
    with pytest.raises(ValidationError, match="activation_failure_window_seconds"):
        development_settings(activation_failure_window_seconds=59)
    with pytest.raises(ValidationError, match="activation_max_failures_per_invite"):
        development_settings(activation_max_failures_per_invite=0)
    with pytest.raises(ValidationError, match="activation_max_failures_per_client"):
        development_settings(activation_max_failures_per_client=0)


def test_auth_policy_settings_are_bounded() -> None:
    with pytest.raises(ValidationError, match="auth_max_failed_password_attempts"):
        development_settings(auth_max_failed_password_attempts=0)
    with pytest.raises(ValidationError, match="mfa_max_failed_attempts"):
        development_settings(mfa_max_failed_attempts=0)
    with pytest.raises(ValidationError, match="session_ttl_seconds"):
        development_settings(session_ttl_seconds=299)
    with pytest.raises(ValidationError, match="mfa_code_ttl_seconds"):
        development_settings(mfa_code_ttl_seconds=59)
    with pytest.raises(ValidationError, match="mfa_email_resend_cooldown_seconds"):
        development_settings(mfa_email_resend_cooldown_seconds=29)
    with pytest.raises(ValidationError, match="mfa_sms_resend_cooldown_seconds"):
        development_settings(mfa_sms_resend_cooldown_seconds=59)
    with pytest.raises(ValidationError, match="password_reset_token_ttl_seconds"):
        development_settings(password_reset_token_ttl_seconds=299)
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_REQUIRE_MFA"):
        Settings(
            environment="production",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
            require_mfa=False,
        )


def test_module_does_not_create_global_app_on_import() -> None:
    assert not hasattr(main, "app")


def test_packaged_migration_command_upgrades_to_head_by_default(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    upgraded: dict[str, str] = {}

    monkeypatch.setattr(
        cli,
        "get_settings",
        lambda: development_settings(database_url="sqlite+pysqlite:///:memory:"),
    )
    monkeypatch.setattr(
        cli.command,
        "upgrade",
        lambda config, revision: upgraded.update(
            revision=revision,
            script_location=config.get_main_option("script_location"),
            database_url=config.get_main_option("sqlalchemy.url"),
        ),
    )

    cli.migrate([])

    assert upgraded == {
        "revision": "head",
        "script_location": "carlos_patient_portal:migrations",
        "database_url": "sqlite+pysqlite:///:memory:",
    }
