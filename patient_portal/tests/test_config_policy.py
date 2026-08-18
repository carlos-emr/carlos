"""Settings validation: which configurations the portal refuses to start with.

Most of these assert a *refusal*. The portal defaults to production and fails closed, so each
missing or weak secret has to be pinned individually or a regression silently ships a deployment
that starts without it.
"""

import re

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy import select
from sqlalchemy.engine import make_url

from carlos_patient_portal import credentials, main, web_support
from carlos_patient_portal.config import (
    DEFAULT_AUDIT_RETENTION_DAYS,
    DEFAULT_DATABASE_URL,
    Settings,
)
from carlos_patient_portal.database import (
    Base,
)
from carlos_patient_portal.identity import normalize_email, normalize_health_card_number
from carlos_patient_portal.models import (
    AUDIT_EVENT_ACCOUNT_MFA_UPDATE,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    PatientPortalAccount,
    PatientPortalAuditEvent,
    PatientPortalSession,
)
from tests.support import (
    AUDIT_HASH_SECRET,
    CSRF_TOKEN_PATTERN,
    DEV_ADMIN_TOKEN,
    IDENTITY_PROOF_SECRET,
    INTERNAL_HEALTH_TOKEN,
    NON_DEVELOPMENT_SESSION_SECRET,
    SEEDED_INVITE_EMAIL,
    STRONG_PASSWORD,
    RecordingPortalSmsSender,
    browser_sign_in_seeded_patient,
    csrf_token_from_response,
    development_settings,
    migrated_development_app,
    production_settings,
    staging_settings,
)


def test_production_responses_include_hsts() -> None:
    app = main.create_app(production_settings())
    response = TestClient(app, base_url="https://portal.example.test").get("/")

    assert response.headers["strict-transport-security"] == ("max-age=31536000; includeSubDomains")


def test_internal_database_health_uses_app_database_settings() -> None:
    app = main.create_app(development_settings(database_url="sqlite+pysqlite:///:memory:"))
    response = TestClient(app).get("/internal/health/db")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "ok"}


def test_account_settings_step_up_failures_lock_and_revoke_session() -> None:
    app = migrated_development_app(auth_max_failed_password_attempts=2)
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    account_response = client.get("/portal/account")

    first_failure = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token_from_response(account_response),
            "email": "attacker@example.test",
            "phone_number": "",
            "current_password": "Wrong1!password",
        },
    )
    second_failure = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token_from_response(first_failure),
            "email": "attacker@example.test",
            "phone_number": "",
            "current_password": "Wrong1!password",
        },
        follow_redirects=False,
    )
    portal_response = client.get("/portal", follow_redirects=False)

    assert first_failure.status_code == 403
    assert second_failure.status_code == 303
    assert portal_response.status_code == 303
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        portal_session = session.scalar(
            select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
        )
        assert account is not None
        assert account.locked_at is not None
        assert account.email == SEEDED_INVITE_EMAIL
        assert portal_session is not None
        assert portal_session.revoked_at is not None
        # Pin the reason, not just non-null: the lazy kill on next use also sets revoked_at, so
        # this assertion held even with lock-time revocation deleted.
        assert portal_session.revoked_reason == "password_failures"


def test_account_mfa_settings_require_phone_then_allow_sms() -> None:
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(sms_sender=sms_sender)
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    account_response = client.get("/portal/account")
    csrf_token_match = CSRF_TOKEN_PATTERN.search(account_response.text)
    assert csrf_token_match is not None
    assert re.search(r'<option\s+value="sms"[^>]*disabled', account_response.text)

    unavailable_response = client.post(
        "/portal/account/mfa",
        data={
            "csrf_token": csrf_token_match.group(1),
            "preferred_mfa_method": "sms",
            "current_password": STRONG_PASSWORD,
        },
    )
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        account.phone_number = "+1 555 010 5555"
        session.commit()
    fresh_account_response = client.get("/portal/account")
    fresh_csrf_token_match = CSRF_TOKEN_PATTERN.search(fresh_account_response.text)
    assert fresh_csrf_token_match is not None
    assert not re.search(
        r'<option\s+value="sms"[^>]*disabled',
        fresh_account_response.text,
    )
    updated_response = client.post(
        "/portal/account/mfa",
        data={
            "csrf_token": fresh_csrf_token_match.group(1),
            "preferred_mfa_method": "sms",
            "current_password": STRONG_PASSWORD,
        },
        follow_redirects=False,
    )
    login_response = client.post(
        "/auth/login",
        json={"username": "patient.user", "password": STRONG_PASSWORD},
    )

    assert unavailable_response.status_code == 400
    assert "Account change could not be completed." in unavailable_response.text
    assert updated_response.status_code == 303
    assert login_response.status_code == 200
    assert login_response.json()["mfa_delivery_method"] == "sms"
    assert sms_sender.messages[-1]["recipient"] == "+15550105555"
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_ACCOUNT_MFA_UPDATE)
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert account is not None
        assert account.preferred_mfa_method == "sms"
        assert [(event.outcome, event.reason) for event in audit_events] == [
            (AUDIT_OUTCOME_FAILURE, "delivery_unavailable"),
            (AUDIT_OUTCOME_SUCCESS, "sms"),
        ]


def test_login_route_rejects_missing_csrf_token() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).post(
        "/auth/login",
        data={"username": "patient.username", "password": "unused"},
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "invalid CSRF token"


def test_database_identifiers_fit_postgresql_limit() -> None:
    identifiers = []
    for table in Base.metadata.tables.values():
        identifiers.extend(
            constraint.name for constraint in table.constraints if constraint.name is not None
        )
        identifiers.extend(index.name for index in table.indexes if index.name is not None)

    assert sorted(name for name in identifiers if len(name) > 63) == []


def test_environment_aliases_are_normalized() -> None:
    settings = production_settings(
        environment=" prod ",
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


@pytest.mark.parametrize("clinic_id", ["clinic id", "clinic/one", "a" * 21])
def test_clinic_id_must_fit_hl7_identifier_policy(clinic_id: str) -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_CLINIC_ID"):
        development_settings(clinic_id=clinic_id)


def test_trusted_client_ip_header_is_normalized() -> None:
    settings = development_settings(
        trusted_client_ip_header=" X-Forwarded-For ",
        trusted_proxy_cidrs="10.0.0.0/8",
    )

    assert settings.trusted_client_ip_header == "x-forwarded-for"


def test_trusted_proxy_chain_uses_rightmost_untrusted_client() -> None:
    client_address = web_support.parse_trusted_client_ip_header(
        "x-forwarded-for",
        "198.51.100.200, 203.0.113.7, 10.0.0.10",
        peer_address="10.0.0.20",
        trusted_proxy_cidrs="10.0.0.0/8",
    )
    spoofed_header = web_support.parse_trusted_client_ip_header(
        "x-forwarded-for",
        "203.0.113.99",
        peer_address="192.0.2.10",
        trusted_proxy_cidrs="10.0.0.0/8",
    )

    assert client_address == "203.0.113.7"
    assert spoofed_header is None


def test_trusted_proxy_header_and_cidrs_must_be_configured_together() -> None:
    with pytest.raises(ValidationError, match="TRUSTED_PROXY_CIDRS"):
        development_settings(trusted_client_ip_header="x-forwarded-for")
    with pytest.raises(ValidationError, match="TRUSTED_CLIENT_IP_HEADER"):
        development_settings(trusted_proxy_cidrs="10.0.0.0/8")


def test_short_audit_retention_requires_an_explicit_opt_in() -> None:
    """Retention below the regulatory default must be deliberate, not a typo."""
    with pytest.raises(ValidationError, match="ALLOW_SHORT_AUDIT_RETENTION"):
        development_settings(audit_retention_days=365)

    settings = development_settings(
        audit_retention_days=365,
        allow_short_audit_retention=True,
    )

    assert settings.audit_retention_days == 365
    assert settings.audit_retention_is_shortened is True
    # The opt-out does not remove the floor entirely: a trail too short to investigate a live
    # incident is not a supported configuration either.
    with pytest.raises(ValidationError):
        development_settings(audit_retention_days=1, allow_short_audit_retention=True)


def test_short_audit_retention_is_recorded_in_the_audit_trail() -> None:
    """Narrowing the security trail must itself be visible in the trail.

    Asserted through a started app, not Settings, because the point is that the row exists by the
    time the portal serves traffic — an operator asking later why an event is missing needs to find
    this without access to the deployment's environment.
    """
    app = migrated_development_app(
        audit_retention_days=365,
        allow_short_audit_retention=True,
    )
    # Queried inside the client context: leaving it runs the lifespan teardown, which disposes
    # the engine and takes the in-memory database with it.
    with TestClient(app):
        with app.state.session_factory() as session:
            events = list(
                session.scalars(
                    select(PatientPortalAuditEvent).where(
                        PatientPortalAuditEvent.event_type == "retention.policy_override"
                    )
                )
            )

    assert len(events) == 1
    assert events[0].actor_type == "system"
    assert events[0].outcome == AUDIT_OUTCOME_SUCCESS
    assert events[0].reason == "retention_days=365"


def test_default_audit_retention_records_no_override_event() -> None:
    app = migrated_development_app()
    with TestClient(app):
        with app.state.session_factory() as session:
            events = list(
                session.scalars(
                    select(PatientPortalAuditEvent).where(
                        PatientPortalAuditEvent.event_type == "retention.policy_override"
                    )
                )
            )

    assert events == []


def test_password_hash_cost_is_configurable_and_reaches_the_hasher() -> None:
    """Peak hashing memory is max_concurrency * memory_kib, so a deployment must be able to size it.

    Asserted through create_app rather than on Settings alone, because the value only matters if it
    actually reaches the process-wide hasher before any request is served.
    """
    original_concurrency = credentials.PASSWORD_HASH_MAX_CONCURRENCY
    original_hasher = credentials.password_hasher
    try:
        migrated_development_app(
            password_hash_max_concurrency=2,
            password_hash_memory_kib=16384,
            password_hash_parallelism=1,
            password_hash_time_cost=2,
        )

        assert credentials.PASSWORD_HASH_MAX_CONCURRENCY == 2
        assert credentials.password_hasher.memory_cost == 16384
        assert credentials.password_hasher.parallelism == 1
        assert credentials.password_hasher.time_cost == 2
        # Argon2 encodes its parameters in the hash, so a hash made under the old cost still
        # verifies after a reconfiguration.
        legacy_hash = original_hasher.hash("Stronger1!word")
        assert credentials.verify_password(legacy_hash, "Stronger1!word")
    finally:
        credentials.configure_password_hashing(
            max_concurrency=original_concurrency,
            time_cost=original_hasher.time_cost,
            memory_kib=original_hasher.memory_cost,
            parallelism=original_hasher.parallelism,
        )


def test_subpath_hosting_is_wired_through_to_url_generation() -> None:
    """A base URL with a path must reach FastAPI, not just the link builders.

    The link builders already prepend `public_base_url`, so emailed reset links were correct even
    before this. What was missing is that the app itself did not know it was mounted under a
    prefix, so `url_for` produced root-relative asset and route URLs that a subpath deployment
    would 404 on. Contract: the proxy strips the prefix, so the app routes on `/auth/login` and
    generates `/patient/auth/login` back out.
    """
    settings = development_settings(public_base_url="https://portal.example.test/patient")
    app = main.create_app(settings)

    assert settings.root_path == "/patient"
    assert app.root_path == "/patient"

    response = TestClient(app, base_url="https://portal.example.test").get("/")

    assert response.status_code == 200
    # Static assets and form actions carry the prefix, which is what a subpath deployment needs.
    assert "/patient/static/styles.css" in response.text
    assert 'action="https://portal.example.test/patient/auth/login"' in response.text
    assert 'href="/patient/locale/fr?next=/patient/"' in response.text
    assert "Path=/patient/auth" in response.headers["set-cookie"]


def test_root_mounted_deployment_keeps_unprefixed_urls() -> None:
    settings = development_settings(public_base_url="https://portal.example.test")
    app = main.create_app(settings)

    response = TestClient(app, base_url="https://portal.example.test").get("/")

    assert settings.root_path == ""
    assert app.root_path == ""
    assert "/static/styles.css" in response.text
    assert "/patient/static" not in response.text


@pytest.mark.parametrize(
    "email",
    [".patient@example.com", "patient.@example.com", "pa..tient@example.com"],
)
def test_email_local_part_rejects_invalid_dot_placement(email: str) -> None:
    with pytest.raises(ValueError, match="valid email"):
        normalize_email(email)


def test_password_whitespace_does_not_satisfy_symbol_requirement() -> None:
    with pytest.raises(ValueError, match="symbol"):
        credentials.validate_password("Password123 ")


def test_password_unicode_alphanumeric_does_not_satisfy_symbol_requirement() -> None:
    with pytest.raises(ValueError, match="symbol"):
        credentials.validate_password("Password123é")


def test_url_ports_and_unlock_key_ids_fail_during_settings_validation() -> None:
    with pytest.raises(ValidationError, match="valid port"):
        development_settings(public_base_url="http://portal.example.test:not-a-port")
    with pytest.raises(ValidationError, match="PUBLIC_BASE_URL"):
        development_settings(public_base_url="https://:443/patient")
    with pytest.raises(ValidationError, match="valid port"):
        development_settings(
            sms_webhook_url="http://sms.example.test:99999/messages",
            sms_webhook_token="test-token",
        )
    with pytest.raises(ValidationError, match="must be primary"):
        development_settings(unlock_secret_active_key_id="secondary")
    with pytest.raises(ValidationError, match="unique after trimming"):
        development_settings(
            unlock_secret_encryption_keyring=(
                '{" secondary ": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", '
                '"secondary": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}'
            ),
            unlock_secret_active_key_id="secondary",
        )
    with pytest.raises(ValidationError, match="duplicate JSON member"):
        development_settings(
            unlock_secret_encryption_keyring=(
                '{"secondary": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", '
                '"secondary": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}'
            ),
            unlock_secret_active_key_id="secondary",
        )


def test_health_card_number_rejects_non_ascii_alphanumeric_characters() -> None:
    with pytest.raises(ValueError, match="letters or numbers"):
        normalize_health_card_number("ABCD１２３４")


def test_probe_allowed_hosts_rejects_wildcards() -> None:
    """A wildcard here silently disables canonical-Host enforcement in production.

    Starlette's TrustedHostMiddleware treats any `*` entry as allow_any, so a single environment
    variable would turn off the Host check entirely with no startup error — and an operator
    debugging a failing probe is exactly the person likely to try it.
    """
    for value in ("*", "portal.internal,*", "*.example.test", " * "):
        with pytest.raises(ValidationError, match="PATIENT_PORTAL_PROBE_ALLOWED_HOSTS"):
            development_settings(probe_allowed_hosts=value)


def test_probe_allowed_hosts_extends_the_loopback_defaults() -> None:
    """Adding a pod IP must not silently drop 127.0.0.1 and break the local probe."""
    settings = development_settings(
        public_base_url="https://portal.example.test",
        probe_allowed_hosts="portal.internal, 10.0.0.7",
    )

    assert settings.allowed_hosts == (
        "portal.example.test",
        "portal.internal",
        "10.0.0.7",
        "127.0.0.1",
        "localhost",
        "[::1]",
    )


def test_probe_allowed_hosts_can_exclude_the_loopback_defaults() -> None:
    """The opt-out stays available for deployments that must not answer to loopback."""
    settings = development_settings(
        public_base_url="https://portal.example.test",
        probe_allowed_hosts="portal.internal",
        probe_allowed_hosts_exclusive=True,
    )

    assert settings.allowed_hosts == ("portal.example.test", "portal.internal")


def test_exclusive_probe_hosts_without_aliases_do_not_restore_loopback_defaults() -> None:
    with_public_host = development_settings(
        public_base_url="https://portal.example.test",
        probe_allowed_hosts_exclusive=True,
    )
    without_public_host = development_settings(probe_allowed_hosts_exclusive=True)

    assert with_public_host.allowed_hosts == ("portal.example.test",)
    assert without_public_host.allowed_hosts == ("testserver",)


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


def test_non_development_rejects_missing_unlock_secret_encryption_secret() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET"):
        Settings(
            environment="staging",
            session_secret=NON_DEVELOPMENT_SESSION_SECRET,
            identity_proof_secret=IDENTITY_PROOF_SECRET,
            audit_hash_secret=AUDIT_HASH_SECRET,
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )


def test_production_requires_configured_mfa_email_delivery() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SMTP_HOST"):
        production_settings(
            smtp_host=None,
            smtp_from_address=None,
        )


def test_production_requires_configured_mfa_sms_delivery() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SMS_WEBHOOK_URL"):
        production_settings(sms_webhook_url=None, sms_webhook_token=None)


def test_staging_fails_closed_without_delivery_services_or_internal_api_token() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SMTP_HOST"):
        staging_settings(smtp_host=None, smtp_from_address=None)
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_SMS_WEBHOOK_URL"):
        staging_settings(sms_webhook_url=None, sms_webhook_token=None)
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_API_TOKEN"):
        staging_settings(internal_api_token=None)
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_OUTBOX_ENCRYPTION_SECRET"):
        staging_settings(outbox_encryption_secret=None)


def test_production_rejects_remote_postgresql_without_verified_tls() -> None:
    with pytest.raises(ValidationError, match="sslmode=verify-full"):
        production_settings(database_url="postgresql+psycopg://portal@database.example.test/portal")


def test_production_accepts_remote_postgresql_with_verified_tls() -> None:
    settings = production_settings(
        database_url=(
            "postgresql+psycopg://portal@database.example.test/portal"
            "?sslmode=verify-full&sslrootcert=/run/secrets/database-ca.pem"
        )
    )

    assert "sslmode=verify-full" in settings.database_url


def test_default_password_lockout_threshold_is_ten() -> None:
    assert development_settings().auth_max_failed_password_attempts == 10


def test_internal_health_token_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN"):
        development_settings(internal_health_token="short")


def test_identity_proof_secret_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_IDENTITY_PROOF_SECRET"):
        development_settings(identity_proof_secret="short")


def test_audit_hash_secret_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_AUDIT_HASH_SECRET"):
        development_settings(audit_hash_secret="short")


def test_unlock_secret_encryption_secret_must_be_long_when_configured() -> None:
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET"):
        development_settings(unlock_secret_encryption_secret="short")


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
    with pytest.raises(ValidationError, match="password_reset_request_cooldown_seconds"):
        development_settings(password_reset_request_cooldown_seconds=29)
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_REQUIRE_MFA"):
        production_settings(require_mfa=False)


def test_hardening_settings_are_bounded() -> None:
    settings = development_settings()

    assert settings.audit_retention_days == DEFAULT_AUDIT_RETENTION_DAYS
    assert settings.global_rate_limit_window_seconds == 60
    assert settings.global_rate_limit_max_requests == 300
    assert settings.maintenance_mode is False
    assert settings.maintenance_retry_after_seconds == 300
    with pytest.raises(ValidationError, match="global_rate_limit_window_seconds"):
        development_settings(global_rate_limit_window_seconds=0)
    with pytest.raises(ValidationError, match="global_rate_limit_max_requests"):
        development_settings(global_rate_limit_max_requests=0)
    # 25 * 365 under-retains by the six leap days in a 25-year span, so it is still refused —
    # now by the opt-in check rather than a bare field bound. See the retention tests above.
    with pytest.raises(ValidationError, match="ALLOW_SHORT_AUDIT_RETENTION"):
        development_settings(audit_retention_days=25 * 365)
    with pytest.raises(ValidationError, match="maintenance_retry_after_seconds"):
        development_settings(maintenance_retry_after_seconds=59)


def test_module_does_not_create_global_app_on_import() -> None:
    assert not hasattr(main, "app")
