import os
from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from threading import Barrier, Event, Lock

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import func, inspect, select, text
from sqlalchemy.dialects import postgresql, sqlite
from sqlalchemy.exc import DBAPIError
from sqlalchemy.orm import Session, sessionmaker

from carlos_patient_portal.account_settings import (
    CONTACT_UPDATE_OUTCOME_CONFIRMATION_REQUIRED,
    AccountSettingsStepUpError,
    EmailChangeTokenInvalidError,
    confirm_email_change,
    confirm_phone_change,
    update_account_contact,
    update_account_mfa_method,
)
from carlos_patient_portal.auth import create_patient_session, hash_auth_token
from carlos_patient_portal.config import Settings
from carlos_patient_portal.credentials import hash_password
from carlos_patient_portal.database import create_portal_engine
from carlos_patient_portal.delivery_outbox import (
    enqueue_contact_change_delivery,
    process_one_delivery,
)
from carlos_patient_portal.main import auth_policy_from_settings, create_app
from carlos_patient_portal.models import (
    ACCOUNT_STATUS_ACTIVE,
    CONTACT_REVIEW_STATUS_PENDING,
    EMAIL_CHANGE_STATUS_PENDING,
    OUTBOX_STATUS_DELIVERED,
    OUTBOX_STATUS_PENDING,
    PatientPortalAccount,
    PatientPortalContactReviewRequest,
    PatientPortalEmailChangeRequest,
    PatientPortalOutboundDelivery,
    PatientPortalSession,
    utc_now,
)
from carlos_patient_portal.token_keys import PortalTokenKeys

POSTGRES_URL = os.getenv("PORTAL_TEST_POSTGRES_URL")
INTERNAL_TOKEN = "i" * 32
POSTGRES_PATIENT_PASSWORD = "".join(("Postgres", "2026", "!!"))
POSTGRES_RESET_PASSWORD = "".join(("ResetPostgres", "2026", "!!"))
WRONG_PASSWORD = "".join(("Wrong", "2026", "!!"))

pytestmark = pytest.mark.skipif(
    POSTGRES_URL is None,
    reason="PORTAL_TEST_POSTGRES_URL is required for PostgreSQL integration tests",
)


def request_with_fresh_client(app, method: str, path: str, **kwargs):
    client = TestClient(app)
    try:
        return client.request(method, path, **kwargs)
    finally:
        client.close()


def test_postgresql_runtime_role_cannot_rewrite_or_delete_audit_events() -> None:
    """Exercise the deployment grant model against the real audit table."""
    assert POSTGRES_URL is not None
    engine = create_portal_engine(POSTGRES_URL)
    owner_role = "portal_test_audit_owner"
    runtime_role = "portal_test_runtime"
    original_owner = ""
    try:
        with engine.begin() as connection:
            original_owner = str(
                connection.scalar(
                    text(
                        "select pg_get_userbyid(relowner) from pg_class "
                        "where oid = 'public.patient_portal_audit_events'::regclass"
                    )
                )
            )
            connection.execute(text(f'DROP ROLE IF EXISTS "{runtime_role}"'))
            connection.execute(text(f'DROP ROLE IF EXISTS "{owner_role}"'))
            connection.execute(text(f'CREATE ROLE "{owner_role}" NOLOGIN'))
            connection.execute(text(f'CREATE ROLE "{runtime_role}" NOLOGIN'))
            connection.execute(
                text(
                    f'ALTER TABLE public.patient_portal_audit_events OWNER TO "{owner_role}"'
                )
            )
            connection.execute(
                text(
                    f'GRANT SELECT, INSERT ON public.patient_portal_audit_events '
                    f'TO "{runtime_role}"'
                )
            )
            connection.execute(
                text(
                    f'GRANT USAGE, SELECT ON SEQUENCE patient_portal_audit_events_id_seq '
                    f'TO "{runtime_role}"'
                )
            )

        with engine.begin() as connection:
            # Transaction-local role switching prevents a pooled connection from returning to the
            # test harness as the restricted runtime role after this commit.
            connection.execute(text(f'SET LOCAL ROLE "{runtime_role}"'))
            event_id = connection.scalar(
                text(
                    "insert into patient_portal_audit_events "
                    "(event_type, outcome, actor_type, created_at) "
                    "values ('login', 'success', 'system', now()) returning id"
                )
            )
        for statement in (
            "update patient_portal_audit_events set outcome = 'failure' where id = :event_id",
            "delete from patient_portal_audit_events where id = :event_id",
        ):
            with engine.connect() as connection, pytest.raises(DBAPIError):
                connection.execute(text(f'SET LOCAL ROLE "{runtime_role}"'))
                connection.execute(text(statement), {"event_id": event_id})
    finally:
        if original_owner:
            quoted_owner = engine.dialect.identifier_preparer.quote(original_owner)
            with engine.begin() as connection:
                connection.execute(
                    text(
                        "ALTER TABLE public.patient_portal_audit_events "
                        f"OWNER TO {quoted_owner}"
                    )
                )
                connection.execute(text(f'DROP OWNED BY "{runtime_role}"'))
                connection.execute(text(f'DROP ROLE IF EXISTS "{runtime_role}"'))
                connection.execute(text(f'DROP OWNED BY "{owner_role}"'))
                connection.execute(text(f'DROP ROLE IF EXISTS "{owner_role}"'))
        engine.dispose()


def clean_postgresql_database() -> None:
    assert POSTGRES_URL is not None
    engine = create_portal_engine(POSTGRES_URL)
    try:
        table_names = [
            name for name in inspect(engine).get_table_names() if name.startswith("patient_portal_")
        ]
        if table_names:
            quoted_names = ", ".join(f'"{name}"' for name in table_names)
            with engine.begin() as connection:
                connection.execute(text(f"TRUNCATE TABLE {quoted_names} CASCADE"))
    finally:
        engine.dispose()


def staff_headers() -> dict[str, str]:
    return {
        "Authorization": f"Bearer {INTERNAL_TOKEN}",
        "X-CARLOS-Provider-ID": "postgres-provider",
        "X-CARLOS-Provider-Name": "PostgreSQL Test",
        "X-CARLOS-Clinic-ID": "postgres-clinic",
        "X-CARLOS-Permissions": "portal.invite.manage",
    }


def postgres_settings(**overrides: object) -> Settings:
    assert POSTGRES_URL is not None
    values = {
        "environment": "development",
        "clinic_id": "postgres-clinic",
        "clinic_name": "PostgreSQL Clinic",
        "database_url": POSTGRES_URL,
        "session_secret": "s" * 32,
        "internal_api_token": INTERNAL_TOKEN,
        "identity_proof_secret": "p" * 32,
        "audit_hash_secret": "a" * 32,
        "unlock_secret_encryption_secret": "u" * 32,
        **overrides,
    }
    return Settings(**values)


def postgres_session_token_secret(settings: Settings) -> str:
    """The session-token key the app derives, for tests that mint a session out of band.

    Sessions created here are then read back through the running app, so the key has to be the
    derived one rather than the raw configured secret.
    """
    assert settings.session_secret is not None
    return PortalTokenKeys.derive(settings.session_secret.get_secret_value()).session


def postgres_email_change_token_secret(settings: Settings) -> str:
    """The email-change key the app derives, for tests calling the service directly.

    These tests drive `update_account_contact`/`confirm_email_change` on their own sessions rather
    than through a route, so they have to derive the same key the route would pass.
    """
    assert settings.session_secret is not None
    return PortalTokenKeys.derive(settings.session_secret.get_secret_value()).email_change


def insert_postgres_account(*, username: str, demographic_no: int) -> int:
    assert POSTGRES_URL is not None
    engine = create_portal_engine(POSTGRES_URL)
    try:
        now = utc_now()
        with Session(engine) as session:
            account = PatientPortalAccount(
                clinic_id="postgres-clinic",
                demographic_no=demographic_no,
                username=username,
                email=f"{username}@example.com",
                preferred_mfa_method="email",
                password_hash=hash_password(POSTGRES_PATIENT_PASSWORD),
                status=ACCOUNT_STATUS_ACTIVE,
                created_at=now,
                updated_at=now,
                password_updated_at=now,
            )
            session.add(account)
            session.commit()
            return account.id
    finally:
        engine.dispose()


def test_postgresql_password_reset_and_contact_update_serialize_without_deadlock() -> None:
    """Two writers contending for one account row must serialize, not deadlock.

    The contact change here is phone-only on purpose. That is the branch that still applies
    immediately and still revokes pending reset tokens, so exactly one of the two operations can
    win — which is the mutual exclusion this test exists to pin. An email change now defers all of
    that to confirmation and would let both succeed; that path is covered separately below.
    """
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    account_id = insert_postgres_account(
        username="reset.patient",
        demographic_no=5234,
    )
    settings = postgres_settings()
    app = create_app(settings)
    client = TestClient(app)
    reset_request = client.post(
        "/auth/password-reset/request",
        json={
            "username": "reset.patient",
            "email": "reset.patient@example.com",
        },
    )
    assert reset_request.status_code == 202
    reset_token = reset_request.json()["development_reset_token"]
    assert reset_token

    engine = create_portal_engine(POSTGRES_URL)
    barrier = Barrier(2)

    def update_contact() -> str:
        with Session(engine) as session:
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            barrier.wait(timeout=10)
            try:
                contact_update = update_account_contact(
                    session,
                    account,
                    current_password=POSTGRES_PATIENT_PASSWORD,
                    email="reset.patient@example.com",
                    phone_number="+15550001234",
                    max_failed_password_attempts=10,
                    email_change_token_secret=postgres_email_change_token_secret(settings),
                    email_change_token_ttl=timedelta(days=1),
                    phone_change_code_ttl=timedelta(minutes=10),
                )
                assert contact_update.phone_confirmation_code is not None
                confirm_phone_change(
                    session,
                    account,
                    code=contact_update.phone_confirmation_code,
                    token_secret=postgres_email_change_token_secret(settings),
                    max_failed_attempts=10,
                    code_ttl=timedelta(minutes=10),
                )
            except AccountSettingsStepUpError:
                session.rollback()
                return "step_up_failed"
            session.commit()
            return "updated"

    def complete_reset() -> int:
        barrier.wait(timeout=10)
        response = client.post(
            "/auth/password-reset/complete",
            json={
                "reset_token": reset_token,
                "new_password": POSTGRES_RESET_PASSWORD,
            },
        )
        return response.status_code

    try:
        with ThreadPoolExecutor(max_workers=2) as executor:
            contact_future = executor.submit(update_contact)
            reset_future = executor.submit(complete_reset)
            contact_result = contact_future.result(timeout=15)
            reset_status = reset_future.result(timeout=15)
        assert (contact_result, reset_status) in {
            ("updated", 400),
            ("step_up_failed", 200),
        }
    finally:
        engine.dispose()


def test_postgresql_serializes_invite_and_login_security_updates() -> None:
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    app = create_app(postgres_settings(auth_max_failed_password_attempts=5))
    client = TestClient(app)
    invite_payload = {
        "demographic_no": 1234,
        "email": "postgres.patient@example.com",
        "date_of_birth": "1980-05-20",
        "health_card_number": "ABCD 1234-5678",
    }

    with ThreadPoolExecutor(max_workers=2) as executor:
        invite_responses = list(
            executor.map(
                lambda _: request_with_fresh_client(
                    app,
                    "POST",
                    "/internal/carlos/patients/1234/invites",
                    headers=staff_headers(),
                    json=invite_payload,
                ),
                range(2),
            )
        )
    assert sorted(response.status_code for response in invite_responses) == [201, 409]
    invite_token = next(
        response.json()["invite_token"]
        for response in invite_responses
        if response.status_code == 201
    )
    activation = client.post(
        "/auth/activate",
        json={
            "invite_code": invite_token,
            "email": invite_payload["email"],
            "date_of_birth": invite_payload["date_of_birth"],
            "health_card_number": invite_payload["health_card_number"],
            "username": "postgres.patient",
            "password": POSTGRES_PATIENT_PASSWORD,
        },
    )
    assert activation.status_code == 201

    with ThreadPoolExecutor(max_workers=6) as executor:
        login_responses = list(
            executor.map(
                lambda _: request_with_fresh_client(
                    app,
                    "POST",
                    "/auth/login",
                    json={"username": "postgres.patient", "password": WRONG_PASSWORD},
                ),
                range(6),
            )
        )
    assert all(response.status_code == 401 for response in login_responses)

    engine = create_portal_engine(POSTGRES_URL)
    try:
        with Session(engine) as session:
            account = session.scalar(
                select(PatientPortalAccount).where(
                    PatientPortalAccount.username == "postgres.patient"
                )
            )
            assert account is not None
            assert account.failed_login_count >= 5
            assert account.locked_at is not None
    finally:
        engine.dispose()

    locked_login = client.post(
        "/auth/login",
        json={
            "username": "postgres.patient",
            "password": POSTGRES_PATIENT_PASSWORD,
        },
    )
    assert locked_login.status_code == 423


def test_postgresql_serializes_account_settings_failures_and_session_reads() -> None:
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    account_id = insert_postgres_account(
        username="settings.patient",
        demographic_no=2234,
    )
    engine = create_portal_engine(POSTGRES_URL)
    barrier = Barrier(6)

    def fail_step_up() -> None:
        with Session(engine) as session:
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            barrier.wait(timeout=10)
            try:
                update_account_mfa_method(
                    session,
                    account,
                    current_password=WRONG_PASSWORD,
                    preferred_mfa_method="email",
                    max_failed_password_attempts=5,
                )
            except AccountSettingsStepUpError:
                session.commit()

    try:
        with Session(engine) as session:
            now = utc_now()
            session.add(
                PatientPortalSession(
                    account_id=account_id,
                    token_hash="f" * 64,
                    created_at=now,
                    last_seen_at=now,
                    expires_at=now.replace(year=now.year + 1),
                )
            )
            session.commit()
        with ThreadPoolExecutor(max_workers=6) as executor:
            list(executor.map(lambda _: fail_step_up(), range(6)))
        with Session(engine) as session:
            account = session.get(PatientPortalAccount, account_id)
            portal_session = session.scalar(
                select(PatientPortalSession).where(PatientPortalSession.account_id == account_id)
            )
            assert account is not None
            assert account.failed_login_count >= 5
            assert account.locked_at is not None
            assert portal_session is not None
            assert portal_session.revoked_at is not None
    finally:
        engine.dispose()


def test_postgresql_allows_concurrent_reads_for_one_patient_session() -> None:
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    account_id = insert_postgres_account(
        username="session.patient",
        demographic_no=2734,
    )
    settings = postgres_settings()
    app = create_app(settings)
    engine = create_portal_engine(POSTGRES_URL)
    try:
        with Session(engine) as session:
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            session_token = create_patient_session(
                session,
                account,
                policy=auth_policy_from_settings(settings),
                session_token_secret=postgres_session_token_secret(settings),
                now=utc_now(),
            )
            session.commit()

        def read_session() -> int:
            response = request_with_fresh_client(
                app,
                "GET",
                "/auth/session",
                headers={"Authorization": f"Bearer {session_token}"},
            )
            return response.status_code

        with ThreadPoolExecutor(max_workers=8) as executor:
            statuses = list(executor.map(lambda _: read_session(), range(16)))

        assert statuses == [200] * 16
    finally:
        engine.dispose()


def test_postgresql_activation_limit_and_mfa_delivery_reservation_are_atomic() -> None:
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    release_delivery = Event()
    delivery_started = Event()

    class BlockingEmailSender:
        def send_code(self, **_: object) -> None:
            delivery_started.set()
            assert release_delivery.wait(timeout=15)

        def send_password_reset(self, **_: object) -> None:
            return None

        def send_contact_change_notice(self, **_: object) -> None:
            return None

    app = create_app(
        postgres_settings(
            activation_max_failures_per_invite=2,
            activation_max_failures_per_client=50,
        ),
        email_sender=BlockingEmailSender(),
    )
    client = TestClient(app)
    invite_payload = {
        "demographic_no": 3234,
        "email": "activation.patient@example.com",
        "date_of_birth": "1980-05-20",
        "health_card_number": "ABCD 1234-5678",
    }
    invite = client.post(
        "/internal/carlos/patients/3234/invites",
        headers=staff_headers(),
        json=invite_payload,
    )
    invite_token = invite.json()["invite_token"]
    invalid_activation = {
        "invite_code": invite_token,
        "email": invite_payload["email"],
        "date_of_birth": invite_payload["date_of_birth"],
        "health_card_number": "WRONG 1234",
        "username": "activation.patient",
        "password": POSTGRES_PATIENT_PASSWORD,
    }

    with ThreadPoolExecutor(max_workers=6) as executor:
        responses = list(
            executor.map(
                lambda _: request_with_fresh_client(
                    app,
                    "POST",
                    "/auth/activate",
                    json=invalid_activation,
                ),
                range(6),
            )
        )
    assert sorted(response.status_code for response in responses) == [
        400,
        400,
        429,
        429,
        429,
        429,
    ]

    valid_activation = {
        **invalid_activation,
        "health_card_number": invite_payload["health_card_number"],
    }
    # Use a new invite because the failed-attempt budget is intentionally exhausted.
    second_invite_payload = {**invite_payload, "demographic_no": 3235}
    second_invite = client.post(
        "/internal/carlos/patients/3235/invites",
        headers=staff_headers(),
        json=second_invite_payload,
    )
    valid_activation.update(
        invite_code=second_invite.json()["invite_token"],
        username="mfa.patient",
    )
    assert client.post("/auth/activate", json=valid_activation).status_code == 201

    first_client = TestClient(app)
    second_client = TestClient(app)
    with ThreadPoolExecutor(max_workers=1) as executor:
        first_login = executor.submit(
            first_client.post,
            "/auth/login",
            json={
                "username": "mfa.patient",
                "password": POSTGRES_PATIENT_PASSWORD,
            },
        )
        assert delivery_started.wait(timeout=15)
        concurrent_login = second_client.post(
            "/auth/login",
            json={
                "username": "mfa.patient",
                "password": POSTGRES_PATIENT_PASSWORD,
            },
        )
        release_delivery.set()
        first_response = first_login.result(timeout=15)

    assert first_response.status_code == 200
    assert concurrent_login.status_code == 429


def test_postgresql_unlock_idempotency_and_contact_review_replacement() -> None:
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    account_id = insert_postgres_account(
        username="contact.patient",
        demographic_no=4234,
    )
    settings = postgres_settings()
    app = create_app(settings)
    secret_headers = {
        **staff_headers(),
        "X-CARLOS-Permissions": "portal.secret.manage",
    }
    payload = {
        "source_reference": "concurrent-message",
        "secret_type": "email",
    }
    with ThreadPoolExecutor(max_workers=2) as executor:
        secret_responses = list(
            executor.map(
                lambda _: request_with_fresh_client(
                    app,
                    "POST",
                    "/internal/carlos/patients/4234/unlock-secrets",
                    headers=secret_headers,
                    json=payload,
                ),
                range(2),
            )
        )
    assert all(response.status_code == 201 for response in secret_responses)
    assert len({response.json()["id"] for response in secret_responses}) == 1
    assert len({response.json()["secret"] for response in secret_responses}) == 1

    engine = create_portal_engine(POSTGRES_URL)
    barrier = Barrier(2)

    def update_contact(index: int) -> None:
        with Session(engine) as session:
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            barrier.wait(timeout=10)
            contact_update = update_account_contact(
                session,
                account,
                current_password=POSTGRES_PATIENT_PASSWORD,
                email="contact.patient@example.com",
                phone_number=f"+1555000{index:04d}",
                max_failed_password_attempts=10,
                email_change_token_secret=postgres_email_change_token_secret(settings),
                email_change_token_ttl=timedelta(days=1),
                phone_change_code_ttl=timedelta(minutes=10),
            )
            assert contact_update.phone_confirmation_code is not None
            confirm_phone_change(
                session,
                account,
                code=contact_update.phone_confirmation_code,
                token_secret=postgres_email_change_token_secret(settings),
                max_failed_attempts=10,
                code_ttl=timedelta(minutes=10),
            )
            session.commit()

    try:
        with ThreadPoolExecutor(max_workers=2) as executor:
            list(executor.map(update_contact, range(2)))
        with Session(engine) as session:
            pending_count = session.scalar(
                select(func.count(PatientPortalContactReviewRequest.id)).where(
                    PatientPortalContactReviewRequest.account_id == account_id,
                    PatientPortalContactReviewRequest.status == CONTACT_REVIEW_STATUS_PENDING,
                )
            )
            assert pending_count == 1
    finally:
        engine.dispose()


def test_postgresql_concurrent_email_changes_leave_one_pending_confirmation() -> None:
    """Racing email-change requests must collapse to exactly one live confirmation link.

    `test_superseding_an_email_change_invalidates_the_link_already_sent` already pins the
    sequential case. What only PostgreSQL can pin is that it still holds when two writers arrive
    together: `request_email_change` serializes on `SELECT ... FOR UPDATE` over the pending row,
    and SQLite has no row locks to serialize with. Without it the two inserts race the
    `ux_pp_email_change_pending_account` partial unique index, and a patient who submitted twice
    ends up either holding two working links to two different addresses or seeing a 500.
    """
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    account_id = insert_postgres_account(
        username="confirm.patient",
        demographic_no=4235,
    )
    settings = postgres_settings()
    token_secret = postgres_email_change_token_secret(settings)
    engine = create_portal_engine(POSTGRES_URL)
    barrier = Barrier(2)

    def request_change(index: int) -> str | None:
        with Session(engine) as session:
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            barrier.wait(timeout=10)
            result = update_account_contact(
                session,
                account,
                current_password=POSTGRES_PATIENT_PASSWORD,
                email=f"confirm.patient.{index}@example.com",
                phone_number=None,
                max_failed_password_attempts=10,
                email_change_token_secret=token_secret,
                email_change_token_ttl=timedelta(days=1),
                phone_change_code_ttl=timedelta(minutes=10),
            )
            assert result.outcome == CONTACT_UPDATE_OUTCOME_CONFIRMATION_REQUIRED
            session.commit()
            return result.confirmation_token

    try:
        with ThreadPoolExecutor(max_workers=2) as executor:
            tokens = list(executor.map(request_change, range(2)))

        with Session(engine) as session:
            pending_requests = list(
                session.scalars(
                    select(PatientPortalEmailChangeRequest).where(
                        PatientPortalEmailChangeRequest.account_id == account_id,
                        PatientPortalEmailChangeRequest.status == EMAIL_CHANGE_STATUS_PENDING,
                    )
                )
            )
            assert len(pending_requests) == 1
            # Neither address has touched the account: both MFA and password reset still deliver
            # to the address the patient proved at activation.
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            assert account.email == "confirm.patient@example.com"

        live_email = pending_requests[0].new_email
        # Which thread committed last is not deterministic, so identify the surviving link by its
        # stored hash rather than by thread index.
        live_token = next(
            token
            for token in tokens
            if token is not None
            and hash_auth_token(token_secret, "email_change", token)
            == pending_requests[0].token_hash
        )
        superseded_token = next(token for token in tokens if token != live_token)

        # The loser's link is dead even though the patient legitimately requested it.
        with Session(engine) as session:
            with pytest.raises(EmailChangeTokenInvalidError):
                confirm_email_change(
                    session,
                    confirmation_token=superseded_token,
                    token_secret=token_secret,
                    clinic_id="postgres-clinic",
                    token_ttl=timedelta(days=1),
                )
            session.rollback()

        with Session(engine) as session:
            confirmation = confirm_email_change(
                session,
                confirmation_token=live_token,
                token_secret=token_secret,
                clinic_id="postgres-clinic",
                token_ttl=timedelta(days=1),
            )
            assert confirmation.review_request is not None
            session.commit()

        with Session(engine) as session:
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            assert account.email == live_email
            review_count = session.scalar(
                select(func.count(PatientPortalContactReviewRequest.id)).where(
                    PatientPortalContactReviewRequest.account_id == account_id,
                    PatientPortalContactReviewRequest.status == CONTACT_REVIEW_STATUS_PENDING,
                )
            )
            assert review_count == 1
    finally:
        engine.dispose()


def test_postgresql_staff_revocation_races_in_flight_patient_requests() -> None:
    """Staff disabling an account must win against concurrent authenticated reads.

    Session authentication is deliberately lock-free for throughput, so revocation and an
    in-flight request can interleave. What must hold is that once the disable commits, no
    later request is served, and every session row is revoked.
    """
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    account_id = insert_postgres_account(username="revoked.patient", demographic_no=7101)
    settings = postgres_settings()
    app = create_app(settings)
    client = TestClient(app)
    engine = create_portal_engine(POSTGRES_URL)
    tokens: list[str] = []
    try:
        with Session(engine) as session:
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            for _ in range(8):
                tokens.append(
                    create_patient_session(
                        session,
                        account,
                        policy=auth_policy_from_settings(settings),
                        session_token_secret=postgres_session_token_secret(settings),
                        now=utc_now(),
                    )
                )
            session.commit()

        barrier = Barrier(len(tokens) + 1)
        statuses: list[int] = []

        def read_session(token: str) -> None:
            barrier.wait(timeout=10)
            statuses.append(
                request_with_fresh_client(
                    app,
                    "GET",
                    "/auth/session",
                    headers={"Authorization": f"Bearer {token}"},
                ).status_code
            )

        def revoke_access() -> None:
            barrier.wait(timeout=10)
            request_with_fresh_client(
                app,
                "POST",
                "/internal/carlos/patients/7101/portal-account/access",
                headers={**staff_headers(), "X-CARLOS-Permissions": "portal.account.manage"},
                json={"enabled": False, "reason": "staff_action"},
            )

        with ThreadPoolExecutor(max_workers=len(tokens) + 1) as executor:
            futures = [executor.submit(read_session, token) for token in tokens]
            futures.append(executor.submit(revoke_access))
            for future in futures:
                future.result(timeout=30)

        # Interleaving decides how many in-flight reads land before the commit; the invariant
        # is that afterwards nothing is authenticated and no session row survives unrevoked.
        assert all(status in {200, 401} for status in statuses)
        after = [
            client.get("/auth/session", headers={"Authorization": f"Bearer {token}"}).status_code
            for token in tokens
        ]
        assert set(after) == {401}
        with Session(engine) as session:
            account = session.get(PatientPortalAccount, account_id)
            assert account is not None
            assert account.status == "disabled"
            unrevoked = session.scalar(
                select(func.count(PatientPortalSession.id)).where(
                    PatientPortalSession.account_id == account_id,
                    PatientPortalSession.revoked_at.is_(None),
                )
            )
            assert unrevoked == 0
    finally:
        engine.dispose()


def test_postgresql_concurrent_fresh_logins_leave_one_usable_mfa_challenge() -> None:
    """Concurrent fresh logins must not leave several independently valid MFA challenges.

    A superseded challenge is what carries the failure budget across logins, so two live
    challenges would also reset an attacker's per-challenge attempt allowance.
    """
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    insert_postgres_account(username="mfa.race.patient", demographic_no=7102)
    app = create_app(postgres_settings())
    engine = create_portal_engine(POSTGRES_URL)
    barrier = Barrier(4)
    responses: list[tuple[int, dict]] = []

    def login(_: int) -> None:
        barrier.wait(timeout=10)
        response = request_with_fresh_client(
            app,
            "POST",
            "/auth/login",
            json={"username": "mfa.race.patient", "password": POSTGRES_PATIENT_PASSWORD},
        )
        responses.append((response.status_code, response.json()))

    try:
        with ThreadPoolExecutor(max_workers=4) as executor:
            list(executor.map(login, range(4)))

        accepted = [body for status, body in responses if status == 200]
        with Session(engine) as session:
            pending = list(
                session.execute(
                    text(
                        "select id, status from patient_portal_mfa_challenges "
                        "where status = 'pending' order by id"
                    )
                )
            )
        # The account-level send cooldown should admit exactly one delivery per window, and
        # at most one challenge may remain pending regardless of how the logins interleaved.
        assert len(pending) <= 1
        assert len(accepted) <= 1
        verifiable = [body for body in accepted if body.get("mfa_challenge_token")]
        assert len(verifiable) == len(accepted)
    finally:
        engine.dispose()


def test_postgresql_outbox_claim_is_exclusive_under_concurrent_workers() -> None:
    """Row locking must actually exclude a second worker.

    Every outbox test runs on SQLite, where SQLAlchemy compiles
    `select(...).with_for_update(skip_locked=True)` to a plain SELECT - so `skip_locked` could be
    deleted with a green suite. The single two-worker SQLite test passes via the lease predicate
    rather than the lock, because the claim transaction commits before the sender is called.
    PatientPortalOutboundDelivery appeared zero times in this file.

    The failure mode this leaves uncovered is duplicate delivery of PHI-bearing patient email.
    """
    assert POSTGRES_URL is not None
    clean_postgresql_database()
    account_id = insert_postgres_account(username="outbox.patient", demographic_no=8821)
    engine = create_portal_engine(POSTGRES_URL)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)
    try:
        with Session(engine) as session:
            with session.begin():
                enqueue_contact_change_delivery(
                    session,
                    account_id=account_id,
                    recipient="outbox.patient@example.com",
                    encryption_secret="o" * 32,
                )

        started = Barrier(2)
        senders: list[str] = []
        sender_lock = Lock()

        class CountingSender:
            def send_contact_change_notice(self, **kwargs: object) -> None:
                with sender_lock:
                    senders.append(str(kwargs.get("message_id")))

        def claim_one() -> object:
            started.wait(timeout=10)
            return process_one_delivery(
                session_factory,
                email_sender=CountingSender(),
                encryption_secret="o" * 32,
                max_attempts=3,
                lease_seconds=60,
            )

        with ThreadPoolExecutor(max_workers=2) as executor:
            results = [future.result(timeout=30) for future in
                       [executor.submit(claim_one), executor.submit(claim_one)]]

        claimed = [result for result in results if result is not None]
        assert len(claimed) == 1, "both workers claimed the same row"
        assert len(senders) == 1, "the message was delivered twice"
        with Session(engine) as session:
            total = session.scalar(select(func.count(PatientPortalOutboundDelivery.id)))
            assert total == 1
            assert session.scalar(select(PatientPortalOutboundDelivery)).status == (
                OUTBOX_STATUS_DELIVERED
            )
    finally:
        engine.dispose()


def test_postgresql_outbox_claim_compiles_to_for_update_skip_locked() -> None:
    """Pin the dialect behaviour the test above depends on.

    On SQLite the same statement compiles without any locking clause, which is exactly why the
    concurrency guarantee cannot be demonstrated there.
    """
    statement = (
        select(PatientPortalOutboundDelivery)
        .where(PatientPortalOutboundDelivery.status == OUTBOX_STATUS_PENDING)
        .with_for_update(skip_locked=True)
    )
    compiled = str(statement.compile(dialect=postgresql.dialect()))

    assert "FOR UPDATE SKIP LOCKED" in compiled
    assert "FOR UPDATE SKIP LOCKED" not in str(statement.compile(dialect=sqlite.dialect()))
