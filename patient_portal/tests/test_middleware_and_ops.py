"""Cross-cutting middleware and operator-facing behaviour.

Security headers, host validation, rate limiting, maintenance mode, readiness and metrics probes,
transaction boundaries, and the maintenance CLI.
"""

import logging
from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy import select
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from carlos_patient_portal import cli, main, web_support
from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.config import (
    DEFAULT_DATABASE_URL,
)
from carlos_patient_portal.database import (
    create_portal_engine,
    create_session_factory,
    session_scope,
)
from carlos_patient_portal.i18n import (
    DEFAULT_LOCALE,
    portal_text,
)
from carlos_patient_portal.invites import (
    list_invites,
)
from carlos_patient_portal.maintenance import (
    BackupDestinationExistsError,
    BackupUnsupportedError,
    audit_retention_cutoff,
    backup_sqlite_database,
    cleanup_transient_auth_rows,
    prune_audit_events,
    restore_sqlite_database,
)
from carlos_patient_portal.models import (
    AUDIT_EVENT_LOGIN,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    PatientPortalAuditEvent,
    PatientPortalInvite,
    PatientPortalMfaChallenge,
    PatientPortalPasswordResetToken,
    PatientPortalSession,
)
from carlos_patient_portal.routes import fhir as fhir_routes
from tests.support import (
    DEV_ADMIN_TOKEN,
    INTERNAL_HEALTH_TOKEN,
    SEEDED_INVITE_EMAIL,
    WRONG_INTERNAL_HEALTH_TOKEN,
    activate_seeded_patient_account,
    create_service_invite,
    development_settings,
    expire_email_mfa_cooldown,
    migrated_development_app,
    production_settings,
    seeded_identity_proof,
    sign_in_patient_api_session,
    staging_settings,
    upgrade_to_head,
)


def test_health_endpoint_is_minimal() -> None:
    app = main.create_app(staging_settings())
    response = TestClient(app, base_url="https://portal.example.test").get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_operational_metrics_are_protected_and_request_logs_are_phi_safe(
    caplog: pytest.LogCaptureFixture,
) -> None:
    caplog.set_level(logging.INFO, logger="carlos_patient_portal.main")
    app = migrated_development_app(internal_health_token=INTERNAL_HEALTH_TOKEN)
    client = TestClient(app)

    response = client.get("/health", headers={"X-Request-ID": "portal-check-123"})
    hidden = client.get("/internal/metrics")
    metrics = client.get(
        "/internal/metrics",
        headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
    )

    assert response.headers["X-Request-ID"] == "portal-check-123"
    assert hidden.status_code == 404
    assert metrics.status_code == 200
    assert metrics.json()["requests"]["2xx"] >= 1
    request_logs = [
        record.message for record in caplog.records if '"event":"http_request"' in record.message
    ]
    assert request_logs
    assert all("?" not in message and "portal-check-123" in message for message in request_logs[:1])


def test_transient_auth_cleanup_is_batched_and_preserves_current_credentials() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    activate_seeded_patient_account(app, client)
    sign_in_patient_api_session(client)
    expire_email_mfa_cooldown(app)
    sign_in_patient_api_session(client)
    client.post(
        "/auth/password-reset/request",
        json={"username": "patient.user", "email": SEEDED_INVITE_EMAIL},
    )
    with app.state.session_factory() as session:
        with session.begin():
            create_service_invite(
                session,
                demographic_no=5678,
                identity_proof=seeded_identity_proof(
                    email="other.patient@example.com",
                    health_card_number="WXYZ 9876-5432",
                ),
            )
            old_created_at = datetime.now(UTC) - timedelta(days=50)
            old_expiry = datetime.now(UTC) - timedelta(days=40)
            sessions = list(
                session.scalars(select(PatientPortalSession).order_by(PatientPortalSession.id))
            )
            sessions[0].created_at = old_created_at
            sessions[0].expires_at = old_expiry
            challenge = session.scalar(
                select(PatientPortalMfaChallenge).order_by(PatientPortalMfaChallenge.id)
            )
            reset = session.scalar(select(PatientPortalPasswordResetToken))
            invite = session.scalar(
                select(PatientPortalInvite).where(PatientPortalInvite.demographic_no == 5678)
            )
            assert challenge is not None
            assert reset is not None
            assert invite is not None
            challenge.created_at = old_created_at
            challenge.expires_at = old_expiry
            reset.created_at = old_created_at
            reset.expires_at = old_expiry
            invite.created_at = old_created_at
            invite.expires_at = old_expiry

        cutoff = datetime.now(UTC) - timedelta(days=30)
        with session.begin():
            dry_run = cleanup_transient_auth_rows(
                session,
                before=cutoff,
                dry_run=True,
            )
        assert dry_run.total == 4
        with session.begin():
            deleted = cleanup_transient_auth_rows(session, before=cutoff)
        assert deleted.total == 4
        assert session.scalar(select(PatientPortalSession.id)) is not None


def test_non_development_csrf_cookie_is_secure() -> None:
    app = main.create_app(staging_settings())
    response = TestClient(app, base_url="https://portal.example.test").get("/")
    set_cookie = response.headers["set-cookie"]

    assert f"{web_support.CSRF_COOKIE_NAME}=" in set_cookie
    assert "HttpOnly" in set_cookie
    assert "Path=/auth" in set_cookie
    assert "SameSite=strict" in set_cookie
    assert "Secure" in set_cookie


def test_non_development_sign_in_shows_generic_field_hints() -> None:
    app = main.create_app(staging_settings())
    response = TestClient(app, base_url="https://portal.example.test").get("/")
    text = portal_text(DEFAULT_LOCALE)

    assert response.status_code == 200
    assert f'placeholder="{text["username_placeholder"]}"' in response.text
    assert f'placeholder="{text["password_placeholder"]}"' in response.text


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
    assert (
        client.get(
            "/internal/health/db",
            headers={"Authorization": f"Bearer {WRONG_INTERNAL_HEALTH_TOKEN}"},
        ).status_code
        == 404
    )
    assert (
        client.get(
            "/internal/health/db",
            headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
        ).status_code
        == 200
    )


def test_internal_readiness_reports_maintenance_without_hiding_liveness() -> None:
    app = migrated_development_app(
        internal_health_token=INTERNAL_HEALTH_TOKEN,
        maintenance_mode=True,
        maintenance_retry_after_seconds=120,
    )
    client = TestClient(app)

    sign_in_response = client.get("/")
    fhir_response = client.get("/fhir/metadata")
    public_health_response = client.get("/health")
    db_health_response = client.get(
        "/internal/health/db",
        headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
    )
    readiness_response = client.get(
        "/internal/readiness",
        headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
    )

    assert sign_in_response.status_code == 503
    # Browser navigation gets the maintenance page; FHIR below still gets an OperationOutcome.
    assert sign_in_response.headers["content-type"].startswith("text/html")
    assert "Portal unavailable" in sign_in_response.text
    assert sign_in_response.headers["retry-after"] == "120"
    assert sign_in_response.headers["cache-control"] == "no-store"
    assert fhir_response.status_code == 503
    assert fhir_response.headers["content-type"].startswith(fhir_routes.FHIR_JSON_MEDIA_TYPE)
    assert fhir_response.json()["resourceType"] == "OperationOutcome"
    assert public_health_response.status_code == 200
    assert public_health_response.json() == {"status": "ok"}
    assert db_health_response.status_code == 200
    assert db_health_response.json() == {"status": "ok", "database": "ok"}
    assert readiness_response.status_code == 503
    assert readiness_response.headers["retry-after"] == "120"
    assert readiness_response.json() == {
        "status": "maintenance",
        "database": "ok",
        "schema": "ok",
        "maintenance": True,
    }


def test_internal_readiness_rejects_unmigrated_database() -> None:
    app = main.create_app(
        development_settings(
            database_url="sqlite+pysqlite:///:memory:",
            internal_health_token=INTERNAL_HEALTH_TOKEN,
        )
    )

    response = TestClient(app).get(
        "/internal/readiness",
        headers={"Authorization": f"Bearer {INTERNAL_HEALTH_TOKEN}"},
    )

    assert response.status_code == 503
    assert response.json() == {
        "status": "unavailable",
        "database": "ok",
        "schema": "mismatch",
        "maintenance": False,
    }


def test_throttled_machine_surfaces_keep_their_json_shape() -> None:
    """Browser paths render a page; API clients must keep parsing `detail`.

    The HTML branch exists for patients, and it must not leak into surfaces something else is
    parsing. FHIR keeps its OperationOutcome, /api keeps its JSON object.
    """
    app = migrated_development_app(
        global_rate_limit_max_requests=1,
        global_rate_limit_window_seconds=60,
    )
    client = TestClient(app)

    client.get("/api/patient/email-passwords")
    api_response = client.get("/api/patient/email-passwords")

    assert api_response.status_code == 429
    assert api_response.headers["content-type"].startswith("application/json")
    assert api_response.json()["detail"] == "too many requests"

    fhir_app = migrated_development_app(
        global_rate_limit_max_requests=1,
        global_rate_limit_window_seconds=60,
    )
    fhir_client = TestClient(fhir_app)
    fhir_client.get("/fhir/Patient")
    fhir_response = fhir_client.get("/fhir/Patient")

    assert fhir_response.status_code == 429
    assert fhir_response.json()["resourceType"] == "OperationOutcome"


def test_global_rate_limit_throttles_patient_routes_and_exempts_health() -> None:
    app = main.create_app(
        development_settings(
            global_rate_limit_max_requests=2,
            global_rate_limit_window_seconds=60,
        )
    )
    client = TestClient(app)

    first_response = client.get("/")
    second_response = client.get("/")
    throttled_response = client.get("/")
    health_response = client.get("/health")

    assert first_response.status_code == 200
    assert second_response.status_code == 200
    assert throttled_response.status_code == 429
    # A patient hit this from a browser, so it renders a page rather than a raw JSON body.
    assert throttled_response.headers["content-type"].startswith("text/html")
    assert "Too many requests" in throttled_response.text
    assert throttled_response.headers["retry-after"] == "60"
    assert throttled_response.headers["cache-control"] == "no-store"
    assert health_response.status_code == 200


def test_rate_limiter_evicts_oldest_bucket_at_configured_capacity() -> None:
    limiter = main.InMemoryRateLimiter(
        window_seconds=60,
        max_requests=10,
        max_buckets=3,
    )

    for index in range(10):
        assert limiter.consume(f"client-{index}", now=1.0) is None

    assert len(limiter.buckets) == 3
    assert list(limiter.buckets) == ["client-7", "client-8", "client-9"]


def test_api_docs_are_available_in_development() -> None:
    app = main.create_app(
        development_settings(
            enable_dev_admin=True,
            dev_admin_token=DEV_ADMIN_TOKEN,
        )
    )

    response = TestClient(app).get("/api/openapi.json")

    assert response.status_code == 200
    paths = response.json()["paths"]
    assert "401" in paths["/auth/logout"]["post"]["responses"]
    assert "403" in paths["/portal/logout"]["post"]["responses"]
    assert {"400", "404", "409"} <= paths["/dev/admin/invites"]["post"]["responses"].keys()
    assert {"400", "404"} <= paths["/dev/admin/invites"]["get"]["responses"].keys()
    assert {"404", "409"} <= paths["/dev/admin/invites/{invite_id}/resend"]["post"][
        "responses"
    ].keys()
    assert {"404", "409"} <= paths["/dev/admin/invites/{invite_id}/revoke"]["post"][
        "responses"
    ].keys()
    assert "404" in paths["/dev/admin/accounts/{account_id}/unlock"]["post"]["responses"]
    assert "/internal/health/db" not in paths


def test_api_docs_are_disabled_outside_development() -> None:
    app = main.create_app(staging_settings())

    client = TestClient(app, base_url="https://portal.example.test")
    assert client.get("/api/openapi.json").status_code == 404
    assert client.get("/api/docs").status_code == 404
    assert client.get("/api/redoc").status_code == 404


def test_api_docs_are_disabled_in_production() -> None:
    app = main.create_app(production_settings())
    client = TestClient(app, base_url="https://portal.example.test")

    assert client.get("/api/openapi.json").status_code == 404
    assert client.get("/api/docs").status_code == 404
    assert client.get("/api/redoc").status_code == 404


def test_loopback_probes_are_allowed_while_untrusted_hosts_stay_rejected() -> None:
    """Strict Host validation must not reject the service's own liveness/readiness probes."""
    app = migrated_development_app(public_base_url="https://portal.example.test")
    client = TestClient(app, base_url="https://portal.example.test")

    loopback_probe = client.get("/health", headers={"Host": "127.0.0.1"})
    named_probe = client.get("/health", headers={"Host": "localhost"})
    canonical = client.get("/health", headers={"Host": "portal.example.test"})
    untrusted = client.get("/", headers={"Host": "attacker.example"})

    assert loopback_probe.status_code == 200
    assert named_probe.status_code == 200
    assert canonical.status_code == 200
    assert untrusted.status_code == 400


def test_probe_allowed_hosts_can_be_configured_for_orchestrated_deployments() -> None:
    app = migrated_development_app(
        public_base_url="https://portal.example.test",
        probe_allowed_hosts="portal.internal, 10.0.0.7",
    )
    client = TestClient(app, base_url="https://portal.example.test")

    assert client.get("/health", headers={"Host": "portal.internal"}).status_code == 200
    assert client.get("/health", headers={"Host": "10.0.0.7"}).status_code == 200
    # Configured aliases extend the loopback defaults, so adding a pod IP does not cost the
    # operator the local probe that was already working.
    assert client.get("/health", headers={"Host": "127.0.0.1"}).status_code == 200
    assert client.get("/", headers={"Host": "attacker.example"}).status_code == 400


def test_probe_allowed_hosts_exclusive_drops_the_loopback_defaults() -> None:
    app = migrated_development_app(
        public_base_url="https://portal.example.test",
        probe_allowed_hosts="portal.internal",
        probe_allowed_hosts_exclusive=True,
    )
    client = TestClient(app, base_url="https://portal.example.test")

    assert client.get("/health", headers={"Host": "portal.internal"}).status_code == 200
    assert client.get("/health", headers={"Host": "127.0.0.1"}).status_code == 400


def test_probe_allowed_hosts_wildcard_does_not_reach_trusted_host_middleware() -> None:
    """The wildcard is refused at startup, not quietly turned into allow_any.

    Asserted at the app boundary as well as in Settings, because the failure this guards against
    is specifically that TrustedHostMiddleware would have accepted every Host header.
    """
    with pytest.raises(ValidationError, match="PATIENT_PORTAL_PROBE_ALLOWED_HOSTS"):
        migrated_development_app(
            public_base_url="https://portal.example.test",
            probe_allowed_hosts="*",
        )


def test_route_transaction_commit_failure_prevents_success_response(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    app = main.create_app(development_settings(database_url="sqlite+pysqlite:///:memory:"))

    def fail_commit(_: Session) -> None:
        raise SQLAlchemyError("simulated commit failure")

    monkeypatch.setattr(Session, "commit", fail_commit)
    response = TestClient(app, raise_server_exceptions=False).get("/internal/health/db")

    assert response.status_code == 500


def test_audit_retention_prunes_only_events_older_than_cutoff() -> None:
    app = migrated_development_app()
    now = datetime(2026, 7, 23, 12, 0, tzinfo=UTC)
    cutoff = audit_retention_cutoff(365, now=now)

    with app.state.session_factory() as session:
        with session.begin():
            old_event = record_audit_event(
                session,
                event_type=AUDIT_EVENT_LOGIN,
                outcome=AUDIT_OUTCOME_FAILURE,
                actor_type="patient",
                reason="invalid_credentials",
            )
            recent_event = record_audit_event(
                session,
                event_type=AUDIT_EVENT_LOGIN,
                outcome=AUDIT_OUTCOME_SUCCESS,
                actor_type="patient",
                reason="mfa_required",
            )
            old_event.created_at = cutoff - timedelta(seconds=1)
            recent_event.created_at = cutoff
            old_event_id = old_event.id
            recent_event_id = recent_event.id

        with session.begin():
            first_prune_count = prune_audit_events(session, before=cutoff, batch_size=1)
        with session.begin():
            second_prune_count = prune_audit_events(session, before=cutoff, batch_size=1)

        remaining_event_ids = set(session.scalars(select(PatientPortalAuditEvent.id)))

    assert first_prune_count == 1
    assert second_prune_count == 0
    assert old_event_id not in remaining_event_ids
    assert recent_event_id in remaining_event_ids


def test_sqlite_backup_and_restore_round_trip(tmp_path) -> None:
    database_path = tmp_path / "portal.db"
    backup_path = tmp_path / "portal.backup.db"
    database_url = f"sqlite+pysqlite:///{database_path}"
    engine = create_portal_engine(database_url)
    upgrade_to_head(engine)
    session_factory = create_session_factory(engine)

    with session_scope(session_factory) as session:
        original_invite, _ = create_service_invite(session)
        original_invite_id = original_invite.id

    engine.dispose()
    created_backup_path = backup_sqlite_database(database_url, backup_path)

    assert created_backup_path == backup_path
    assert backup_path.exists()
    with pytest.raises(BackupDestinationExistsError):
        backup_sqlite_database(database_url, backup_path)
    with pytest.raises(BackupDestinationExistsError):
        backup_sqlite_database(database_url, database_path, overwrite=True)
    with pytest.raises(BackupDestinationExistsError):
        restore_sqlite_database(database_url, database_path, overwrite=True)

    engine = create_portal_engine(database_url)
    session_factory = create_session_factory(engine)
    with session_scope(session_factory) as session:
        create_service_invite(session, demographic_no=5678)
        assert len(list_invites(session, limit=10)) == 2
    engine.dispose()

    restored_path = restore_sqlite_database(database_url, backup_path, overwrite=True)

    assert restored_path == database_path
    engine = create_portal_engine(database_url)
    session_factory = create_session_factory(engine)
    with session_scope(session_factory) as session:
        restored_invites = list_invites(session, limit=10)
        assert [(invite.id, invite.demographic_no) for invite in restored_invites] == [
            (original_invite_id, 1234)
        ]
    engine.dispose()

    with pytest.raises(BackupUnsupportedError):
        backup_sqlite_database(DEFAULT_DATABASE_URL, tmp_path / "postgres.backup")


def test_non_development_sms_webhook_requires_https() -> None:
    with pytest.raises(ValidationError, match="must use HTTPS"):
        production_settings(sms_webhook_url="http://sms.example.test/messages")


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
