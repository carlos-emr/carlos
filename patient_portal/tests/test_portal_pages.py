"""Authenticated portal pages: dashboard, account settings, and email passwords."""

import re
from dataclasses import fields
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from carlos_patient_portal import main, presenters, web_support
from carlos_patient_portal.config import (
    MIN_PRODUCTION_SECRET_LENGTH,
    Settings,
)
from carlos_patient_portal.i18n import (
    DEFAULT_LOCALE,
    LOCALE_COOKIE_NAME,
    SUPPORTED_LOCALES,
    format_portal_datetime,
    portal_text,
)
from carlos_patient_portal.models import (
    AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
    AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_CONFIRM,
    AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_REQUEST,
    AUDIT_EVENT_UNLOCK_SECRET_LIST,
    AUDIT_EVENT_UNLOCK_SECRET_READ,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    CONTACT_REVIEW_STATUS_PENDING,
    EMAIL_CHANGE_STATUS_CONFIRMED,
    EMAIL_CHANGE_STATUS_PENDING,
    EMAIL_CHANGE_STATUS_REVOKED,
    UNLOCK_SECRET_TYPE_EMAIL,
    UNLOCK_SECRET_TYPE_PDF,
    PatientPortalAccount,
    PatientPortalAuditEvent,
    PatientPortalContactReviewRequest,
    PatientPortalEmailChangeRequest,
    PatientPortalUnlockSecret,
)
from carlos_patient_portal.unlock_secrets import (
    create_unlock_secret,
    revoke_unlock_secret,
)
from carlos_patient_portal.view_models import (
    EmailPasswordDashboardViewModel,
    EmailPasswordRowViewModel,
)
from tests.support import (
    CSRF_TOKEN_PATTERN,
    SEEDED_INVITE_EMAIL,
    STRONG_PASSWORD,
    UNLOCK_SECRET_ENCRYPTION_SECRET,
    FailingNoticeSender,
    RecordingPortalEmailSender,
    RecordingPortalSmsSender,
    _sample_mfa_delivery,
    _template_variable_names,
    activate_seeded_patient_account,
    bearer_headers,
    browser_sign_in_seeded_patient,
    confirm_seeded_email_change,
    csrf_token_from_response,
    development_settings,
    migrated_development_app,
    request_seeded_email_change,
    run_with_email_sender,
    sign_in_patient_api_session,
    submit_contact_change,
)


def test_dashboard_datetime_and_date_boundary_use_clinic_timezone() -> None:
    assert (
        format_portal_datetime(
            datetime(2026, 1, 15, 5, 30, tzinfo=UTC),
            timezone_name="America/Toronto",
        )
        == "2026-01-15 00:30 EST"
    )
    assert presenters.dashboard_created_before(
        datetime(2026, 7, 15).date(),
        timezone_name="America/Toronto",
    ) == datetime(2026, 7, 16, 4, 0, tzinfo=UTC)


def test_index_renders_sign_in_shell() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get("/")
    text = portal_text(DEFAULT_LOCALE)

    assert response.status_code == 200
    assert text["username_placeholder"] == "username"
    assert text["password_placeholder"] == "password"
    assert "CARLOS Patient Portal" in response.text
    assert 'src="http://testserver/static/carlos-logo.png"' in response.text
    assert f'placeholder="{text["username_placeholder"]}"' in response.text
    assert f'placeholder="{text["password_placeholder"]}"' in response.text
    assert f">{text['forgot_username_password']}</a>" in response.text
    assert f">{text['activate_account']}</a>" in response.text
    assert 'id="portal-message-modal"' in response.text
    assert 'src="http://testserver/static/portal.js"' in response.text
    for locale in SUPPORTED_LOCALES:
        assert f'lang="{locale.code}"' in response.text
    assert f'value="{text["username_placeholder"]}"' not in response.text
    assert 'name="csrf_token"' in response.text
    assert "nosemgrep" not in response.text
    assert "Maple Creek Medical" in response.text


def test_language_switch_links_to_every_supported_locale() -> None:
    """Each inactive locale is a working link; the active one indicates state, not a control."""
    app = main.create_app(development_settings())
    response = TestClient(app).get("/")

    assert response.status_code == 200
    for locale in SUPPORTED_LOCALES:
        if locale.code == DEFAULT_LOCALE:
            assert f'<span class="text-tab selected" aria-current="true" lang="{locale.code}"' in (
                response.text
            )
        else:
            assert f'href="/locale/{locale.code}?next=' in response.text
    # The modal explained that a language was unavailable. Selecting one now works, so nothing
    # should still be advertising otherwise.
    assert "language_unavailable" not in response.text
    assert "data-language-code" not in response.text


def test_selecting_a_locale_persists_it_and_returns_to_the_same_page() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)

    switched = client.get("/locale/fr?next=/", follow_redirects=False)
    rendered = client.get("/")

    assert switched.status_code == 303
    assert switched.headers["location"] == "/"
    assert client.cookies[LOCALE_COOKIE_NAME] == "fr"
    # French has no catalog yet, so the page renders English strings through the per-key fallback —
    # but the selection is real: French is now the active tab.
    assert '<span class="text-tab selected" aria-current="true" lang="fr"' in rendered.text
    assert portal_text(DEFAULT_LOCALE)["username_placeholder"] in rendered.text


@pytest.mark.parametrize(
    "destination",
    [
        "//evil.example",
        "///evil.example",
        "/\\evil.example",
        "/\\\\evil.example",
        "https://evil.example",
        "http://evil.example",
        "javascript:alert(1)",
        "evil.example",
        "",
        # Browsers strip TAB/LF/CR while parsing a URL, so each of these reaches the network stack
        # as //evil.example. An earlier version of the validator checked only for a leading `//`
        # after folding backslashes and let every one of them through.
        "/\t/evil.example",
        "/\n/evil.example",
        "/\r/evil.example",
        "/ /evil.example",
        "/\x0b/evil.example",
        "/\x00//evil.example",
        "/\x85/evil.example",
    ],
)
def test_locale_switch_refuses_an_offsite_redirect(destination: str) -> None:
    """`next` is attacker-controllable, so nothing that leaves this origin may reach Location."""
    app = main.create_app(development_settings())
    response = TestClient(app).get(
        "/locale/fr",
        params={"next": destination},
        follow_redirects=False,
    )

    assert response.status_code == 303
    assert response.headers["location"] == "/"


@pytest.mark.parametrize(
    "destination",
    ["/", "/portal", "/portal/account", "/portal/email-passwords?page=2"],
)
def test_locale_switch_returns_to_a_local_path(destination: str) -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get(
        "/locale/fr",
        params={"next": destination},
        follow_redirects=False,
    )

    assert response.status_code == 303
    assert response.headers["location"] == destination


def test_locale_switch_refuses_an_unknown_locale() -> None:
    app = main.create_app(development_settings())

    assert TestClient(app).get("/locale/zz", follow_redirects=False).status_code == 404


def test_accept_language_selects_a_locale_when_none_was_chosen() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)

    response = client.get("/", headers={"Accept-Language": "pt-BR,pt;q=0.9,en;q=0.5"})
    unsupported = client.get("/", headers={"Accept-Language": "de-DE,de;q=0.9"})

    assert '<span class="text-tab selected" aria-current="true" lang="pt-BR"' in response.text
    # Nothing supported in the header falls back to English rather than picking arbitrarily.
    assert f'lang="{DEFAULT_LOCALE}"' in unsupported.text


def test_static_logo_asset_is_served() -> None:
    app = main.create_app(development_settings())
    response = TestClient(app).get("/static/carlos-logo.png")

    assert response.status_code == 200
    assert "image/png" in response.headers["content-type"]
    assert response.content.startswith(b"\x89PNG")


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


def test_jinja_templates_always_autoescape_jinja_files() -> None:
    assert web_support.templates.env.autoescape is True


def test_account_contact_update_creates_staff_review_request() -> None:
    sender = RecordingPortalEmailSender()
    sms_sender = RecordingPortalSmsSender()
    app = migrated_development_app(email_sender=sender, sms_sender=sms_sender)
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    account_response = client.get("/portal/account")
    csrf_token_match = CSRF_TOKEN_PATTERN.search(account_response.text)
    assert csrf_token_match is not None

    failed_response = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": csrf_token_match.group(1),
            "email": "new.patient@example.com",
            "phone_number": "+1 555 010 5555",
            "current_password": "Wrong1!password",
        },
    )
    fresh_account_response = client.get("/portal/account")
    fresh_csrf_token_match = CSRF_TOKEN_PATTERN.search(fresh_account_response.text)
    assert fresh_csrf_token_match is not None
    updated_response = client.post(
        "/portal/account/contact",
        data={
            "csrf_token": fresh_csrf_token_match.group(1),
            "email": " New.Patient@Example.com ",
            "phone_number": " +1 555 010 5555 ",
            "current_password": STRONG_PASSWORD,
        },
        follow_redirects=False,
    )
    notice_response = client.get("/portal/account?status=contact-confirmation-required")

    assert failed_response.status_code == 403
    assert "Account change could not be completed." in failed_response.text
    assert "Wrong1!password" not in failed_response.text
    assert updated_response.status_code == 303
    assert (
        updated_response.headers["location"]
        == "/portal/account?status=contact-confirmation-required"
    )
    assert notice_response.status_code == 200
    assert "Confirm the new email address" in notice_response.text
    assert sms_sender.messages[-1]["recipient"] == "+15550105555"
    # The confirmation link goes to the proposed address; the current address is told only that a
    # change was requested, and is not given the proposed address.
    assert sender.messages[-1] == {
        "recipient": SEEDED_INVITE_EMAIL,
        "type": "email_change_requested_notice",
    }
    confirmation_message = sender.messages[-2]
    assert confirmation_message["recipient"] == "new.patient@example.com"
    assert confirmation_message["type"] == "email_change_confirmation"
    confirmation_url = str(confirmation_message["confirmation_url"])
    assert "/auth/email-change/confirm#token=" in confirmation_url

    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        review_request = session.scalar(select(PatientPortalContactReviewRequest))
        email_change_request = session.scalar(select(PatientPortalEmailChangeRequest))

        # Nothing about the account has moved yet: a mistyped address strands no factor, and an
        # attacker holding only the password has not redirected verification codes or reset links.
        assert account is not None
        assert account.email == SEEDED_INVITE_EMAIL
        assert account.phone_number is None
        assert review_request is None
        assert email_change_request is not None
        assert email_change_request.status == EMAIL_CHANGE_STATUS_PENDING
        assert email_change_request.new_email == "new.patient@example.com"
        assert email_change_request.new_phone_number == "+15550105555"

    confirmation_page = client.get("/auth/email-change/confirm")
    confirmed = client.post(
        "/auth/email-change/confirm",
        data={
            "csrf_token": csrf_token_from_response(confirmation_page),
            "reset_token": confirmation_url.partition("#token=")[2],
        },
    )

    assert confirmed.status_code == 200
    assert "Email address confirmed" in confirmed.text
    assert "enter the code sent to the new phone number" in confirmed.text
    phone_confirmation_page = client.get("/portal/account")
    phone_confirmed = client.post(
        "/portal/account/contact/confirm-phone",
        data={
            "csrf_token": csrf_token_from_response(phone_confirmation_page),
            "phone_confirmation_code": sms_sender.messages[-1]["code"],
        },
        follow_redirects=False,
    )
    assert phone_confirmed.status_code == 303
    assert phone_confirmed.headers["location"] == "/portal/account?status=contact-updated"
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        review_request = session.scalar(select(PatientPortalContactReviewRequest))
        email_change_request = session.scalar(select(PatientPortalEmailChangeRequest))
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type.in_(
                        (
                            AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE,
                            AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_CONFIRM,
                            AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_REQUEST,
                        )
                    )
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert account is not None
        assert account.email == "new.patient@example.com"
        assert account.phone_number == "+15550105555"
        assert email_change_request is not None
        assert email_change_request.status == EMAIL_CHANGE_STATUS_CONFIRMED
        assert email_change_request.confirmed_at is not None
        # The CARLOS review opens only once the change is real, so its before/after snapshot
        # matches what the chart should be reconciled against.
        assert review_request is not None
        assert review_request.account_id == account_id
        assert review_request.status == CONTACT_REVIEW_STATUS_PENDING
        assert review_request.email_before == SEEDED_INVITE_EMAIL
        assert review_request.email_after == "new.patient@example.com"
        assert review_request.phone_number_before is None
        assert review_request.phone_number_after == "+15550105555"
        assert [
            (event.event_type, event.outcome, event.reason) for event in audit_events
        ] == [
            (AUDIT_EVENT_ACCOUNT_CONTACT_UPDATE, AUDIT_OUTCOME_FAILURE, "step_up_failed"),
            (
                AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_REQUEST,
                AUDIT_OUTCOME_SUCCESS,
                "email_confirmation_requested",
            ),
            (AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_CONFIRM, AUDIT_OUTCOME_SUCCESS, "updated"),
        ]
    # Both addresses learn the change happened, including the one it moved away from.
    assert sender.messages[-2:] == [
        {"recipient": SEEDED_INVITE_EMAIL, "type": "contact_change_notice"},
        {"recipient": "new.patient@example.com", "type": "contact_change_notice"},
    ]


def test_email_password_dashboard_populated_search_pagination_and_copy_controls() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    base_time = datetime(2026, 7, 23, 12, 0, tzinfo=UTC)
    secret_ids: dict[int, int] = {}
    secret_values: dict[int, str] = {}

    with app.state.session_factory() as session:
        with session.begin():
            for index in range(12):
                secret_value = f"PortalPwd{index:02d}!A"
                created = create_unlock_secret(
                    session,
                    clinic_id="default",
                    demographic_no=1234,
                    account_id=account_id,
                    secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                    secret=secret_value,
                    created_by="Clinic Nurse" if index == 5 else "CarlosDoc",
                    encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                    label="Lab report" if index == 5 else f"Message {index:02d}",
                    source_reference=f"message-{3135 + index}",
                )
                created_at = base_time + timedelta(days=index)
                created.unlock_secret.created_at = created_at
                created.unlock_secret.updated_at = created_at
                secret_ids[index] = created.unlock_secret.id
                secret_values[index] = secret_value

    page_one_response = client.get("/portal/email-passwords")
    csrf_token_match = CSRF_TOKEN_PATTERN.search(page_one_response.text)
    assert csrf_token_match is not None
    reveal_response = client.post(
        f"/portal/email-passwords/{secret_ids[11]}/reveal",
        data={"csrf_token": csrf_token_match.group(1)},
    )
    page_two_response = client.get("/portal/email-passwords?page=2")
    out_of_range_page_response = client.get("/portal/email-passwords?page=99")
    search_response = client.get(
        "/portal/email-passwords",
        params={"q": "lab", "provider": "", "date_from": "", "date_to": ""},
    )
    provider_response = client.get(
        "/portal/email-passwords",
        params={"provider": "Clinic Nurse"},
    )
    date_response = client.get(
        "/portal/email-passwords",
        params={"date_from": "2026-07-28", "date_to": "2026-07-28"},
    )
    invalid_date_response = client.get(
        "/portal/email-passwords",
        params={"date_from": "2026-07-29", "date_to": "2026-07-28"},
    )
    malformed_date_response = client.get(
        "/portal/email-passwords",
        params={"q": "lab", "date_from": "not-a-date", "date_to": ""},
    )
    maximum_date_response = client.get(
        "/portal/email-passwords",
        params={"date_to": "9999-12-31"},
    )

    assert page_one_response.status_code == 200
    assert "Message 11" in page_one_response.text
    assert "Message 02" in page_one_response.text
    assert "Message 01" not in page_one_response.text
    assert "PortalPwd01!A" not in page_one_response.text
    assert all(f"PortalPwd{index:02d}!A" not in page_one_response.text for index in range(12))
    assert page_one_response.text.index("Message 11") < page_one_response.text.index("Message 02")
    assert ">Hidden</code>" in page_one_response.text
    assert 'class="copyable-password"' in page_one_response.text
    assert 'data-copy-target="email-password-' in page_one_response.text
    assert 'data-reveal-url="/portal/email-passwords/' in page_one_response.text
    assert 'href="/portal/email-passwords?page=2"' in page_one_response.text
    assert "Page 1 of 2" in page_one_response.text
    assert reveal_response.status_code == 200
    assert reveal_response.json()["passphrase"] == secret_values[11]

    assert page_two_response.status_code == 200
    assert "Message 01" in page_two_response.text
    assert "Message 00" in page_two_response.text
    assert "Message 02" not in page_two_response.text
    assert 'href="/portal/email-passwords"' in page_two_response.text
    assert "Page 2 of 2" in page_two_response.text

    assert out_of_range_page_response.status_code == 200
    assert "Message 01" in out_of_range_page_response.text
    assert "Page 2 of 2" in out_of_range_page_response.text

    assert search_response.status_code == 200
    assert "Lab report" in search_response.text
    assert "Message 06" not in search_response.text
    assert "PortalPwd06!A" not in search_response.text
    assert 'value="lab"' in search_response.text
    assert "Page 1 of 1" in search_response.text

    assert provider_response.status_code == 200
    assert "Lab report" in provider_response.text
    assert "Message 06" not in provider_response.text
    assert '<option value="Clinic Nurse" selected>' in provider_response.text

    assert date_response.status_code == 200
    assert "Lab report" in date_response.text
    assert "Message 04" not in date_response.text
    assert "Message 06" not in date_response.text
    assert 'value="2026-07-28"' in date_response.text
    assert invalid_date_response.status_code == 400
    assert "from date must not be later" in invalid_date_response.text
    assert malformed_date_response.status_code == 400
    assert "text/html" in malformed_date_response.headers["content-type"]
    assert "Enter valid from and to dates." in malformed_date_response.text
    assert "Lab report" not in malformed_date_response.text
    assert maximum_date_response.status_code == 200

    with app.state.session_factory() as session:
        read_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type == AUDIT_EVENT_UNLOCK_SECRET_READ,
                    PatientPortalAuditEvent.outcome == AUDIT_OUTCOME_SUCCESS,
                    PatientPortalAuditEvent.account_id == account_id,
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert len(read_events) == 1
        assert all(event.actor_type == "patient" for event in read_events)


def test_email_password_dashboard_empty_search_and_unavailable_password_states() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    raw_secret = "HiddenEmail9!"

    empty_response = client.get("/portal/email-passwords")
    empty_search_response = client.get("/portal/email-passwords?q=missing")

    assert empty_response.status_code == 200
    assert "No email passwords" in empty_response.text
    assert "Page 1 of 1" in empty_response.text
    assert empty_search_response.status_code == 200
    assert "No matching email passwords" in empty_search_response.text
    assert "Page 1 of 1" in empty_search_response.text

    with app.state.session_factory() as session:
        with session.begin():
            created = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret,
                created_by="CarlosDoc",
                encryption_secret="v" * MIN_PRODUCTION_SECRET_LENGTH,
                label="Broken message",
                source_reference="message-4000",
            )
            unavailable_id = created.unlock_secret.id

    unavailable_response = client.get("/portal/email-passwords")
    csrf_token_match = CSRF_TOKEN_PATTERN.search(unavailable_response.text)
    assert csrf_token_match is not None
    reveal_response = client.post(
        f"/portal/email-passwords/{unavailable_id}/reveal",
        data={"csrf_token": csrf_token_match.group(1)},
    )

    assert unavailable_response.status_code == 200
    assert "Broken message" in unavailable_response.text
    assert "Hidden" in unavailable_response.text
    assert raw_secret not in unavailable_response.text
    assert reveal_response.status_code == 503
    assert reveal_response.json()["detail"] == "email password unavailable"
    with app.state.session_factory() as session:
        read_event = session.scalar(
            select(PatientPortalAuditEvent).where(
                PatientPortalAuditEvent.event_type == AUDIT_EVENT_UNLOCK_SECRET_READ,
                PatientPortalAuditEvent.outcome == AUDIT_OUTCOME_FAILURE,
                PatientPortalAuditEvent.account_id == account_id,
            )
        )

        assert read_event is not None
        assert read_event.reason == "decryption_failed"


def test_email_password_dashboard_escapes_stored_and_reflected_values() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    with app.state.session_factory() as session:
        with session.begin():
            create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret="Escaped1!word",
                created_by="<strong>Provider</strong>",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="<meta http-equiv=refresh content=0>",
                source_reference="<img src=x>",
            )

    stored_response = client.get("/portal/email-passwords")
    reflected_response = client.get(
        "/portal/email-passwords",
        params={"q": '<script nonce="x">alert(1)</script>'},
    )

    assert stored_response.status_code == 200
    assert "<strong>Provider</strong>" not in stored_response.text
    assert "<meta http-equiv" not in stored_response.text
    assert "<img src=x>" not in stored_response.text
    assert "&lt;strong&gt;Provider&lt;/strong&gt;" in stored_response.text
    assert reflected_response.status_code == 200
    assert '<script nonce="x">alert(1)</script>' not in reflected_response.text
    assert "&lt;script" in reflected_response.text


def test_non_portal_prefix_does_not_receive_portal_cache_rule() -> None:
    app = migrated_development_app()
    response = TestClient(app).get("/portalfoo")

    assert response.status_code == 404
    assert "cache-control" not in response.headers


def test_dashboard_styles_include_desktop_and_mobile_navigation_rules() -> None:
    app = main.create_app(development_settings())
    client = TestClient(app)
    response = client.get("/static/styles.css")
    script_response = client.get("/static/portal.js")
    css = response.text

    assert response.status_code == 200
    assert script_response.status_code == 200
    assert ".dashboard-layout" in css
    assert "grid-template-columns: 220px minmax(0, 1fr);" in css
    assert "@media (max-width: 640px)" in css
    assert ".portal-topbar" in css
    assert "flex-direction: row;" in css
    assert ".language-switch .text-tab" in css
    assert "min-height: 44px;" in css
    assert ".module-nav" in css
    assert "align-items: center;" in css
    assert "grid-template-rows: auto minmax(0, 1fr);" in css
    assert ".module-toolbar .search-field" in css
    assert "width: min(100%, 160px);" in css
    assert ".settings-section" in css
    assert ".password-copy-group" in css
    assert ".table-shell .email-password-table" in css
    assert ".email-password-table td::before" in css
    assert "content: attr(data-label);" in css
    assert "navigator.clipboard.writeText" in script_response.text


def test_patient_email_password_api_lists_retrieves_scoped_records_and_audits() -> None:
    app = migrated_development_app()
    client = TestClient(app)
    account_a_id = activate_seeded_patient_account(app, client)
    account_b_id = activate_seeded_patient_account(
        app,
        client,
        username="other.patient",
        demographic_no=5678,
        email="other.patient@example.com",
        health_card_number="WXYZ 9876-5432",
    )
    patient_a_token = sign_in_patient_api_session(client)
    raw_secret_a = "AlphaEmail9!"
    raw_secret_b = "BetaEmail9!"
    raw_secret_revoked = "RevokedEmail9!"
    raw_secret_pdf = "PdfEmail9!"
    raw_secret_unavailable = "WrongKeyEmail9!"

    with app.state.session_factory() as session:
        with session.begin():
            created_a = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_a,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Specialist reply",
                source_reference="message-3135",
            )
            created_b = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=5678,
                account_id=account_b_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_b,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Other patient reply",
                source_reference="message-3136",
            )
            created_revoked = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_revoked,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="Revoked reply",
                source_reference="message-3137",
            )
            created_pdf = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_PDF,
                secret=raw_secret_pdf,
                created_by="CarlosDoc",
                encryption_secret=UNLOCK_SECRET_ENCRYPTION_SECRET,
                label="PDF password",
                source_reference="document-3138",
            )
            created_unavailable = create_unlock_secret(
                session,
                clinic_id="default",
                demographic_no=1234,
                account_id=account_a_id,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                secret=raw_secret_unavailable,
                created_by="CarlosDoc",
                encryption_secret="v" * MIN_PRODUCTION_SECRET_LENGTH,
                label="Temporarily unavailable reply",
                source_reference="message-3139",
            )
            revoke_unlock_secret(
                session,
                created_revoked.unlock_secret.id,
                clinic_id="default",
                demographic_no=1234,
                revoked_by="CarlosDoc",
                reason="staff_requested",
            )
            active_a_id = created_a.unlock_secret.id
            other_patient_id = created_b.unlock_secret.id
            revoked_id = created_revoked.unlock_secret.id
            pdf_id = created_pdf.unlock_secret.id
            unavailable_id = created_unavailable.unlock_secret.id

    list_response = client.get(
        "/api/patient/email-passwords",
        headers=bearer_headers(patient_a_token),
    )
    retrieve_response = client.get(
        f"/api/patient/email-passwords/{active_a_id}",
        headers=bearer_headers(patient_a_token),
    )
    cross_patient_response = client.get(
        f"/api/patient/email-passwords/{other_patient_id}",
        headers=bearer_headers(patient_a_token),
    )
    revoked_response = client.get(
        f"/api/patient/email-passwords/{revoked_id}",
        headers=bearer_headers(patient_a_token),
    )
    pdf_response = client.get(
        f"/api/patient/email-passwords/{pdf_id}",
        headers=bearer_headers(patient_a_token),
    )
    unavailable_response = client.get(
        f"/api/patient/email-passwords/{unavailable_id}",
        headers=bearer_headers(patient_a_token),
    )

    assert list_response.status_code == 200
    assert list_response.headers["cache-control"] == "no-store"
    list_payload = list_response.json()
    assert list_payload["limit"] == 10
    assert list_payload["offset"] == 0
    assert [item["id"] for item in list_payload["items"]] == [unavailable_id, active_a_id]
    assert list_payload["items"][0]["label"] == "Temporarily unavailable reply"
    assert list_payload["items"][0]["source_reference"] == "message-3139"
    assert list_payload["items"][1]["label"] == "Specialist reply"
    assert list_payload["items"][1]["source_reference"] == "message-3135"
    assert all(
        raw_secret not in list_response.text
        for raw_secret in [
            raw_secret_a,
            raw_secret_b,
            raw_secret_revoked,
            raw_secret_pdf,
            raw_secret_unavailable,
        ]
    )

    assert retrieve_response.status_code == 200
    retrieve_payload = retrieve_response.json()
    assert retrieve_payload["id"] == active_a_id
    assert retrieve_payload["label"] == "Specialist reply"
    assert retrieve_payload["source_reference"] == "message-3135"
    assert retrieve_payload["passphrase"] == raw_secret_a
    assert raw_secret_b not in retrieve_response.text
    assert raw_secret_revoked not in retrieve_response.text
    assert raw_secret_pdf not in retrieve_response.text

    for not_found_response in [cross_patient_response, revoked_response, pdf_response]:
        assert not_found_response.status_code == 404
        assert not_found_response.json()["detail"] == "email password not found"
        assert raw_secret_b not in not_found_response.text
        assert raw_secret_revoked not in not_found_response.text
        assert raw_secret_pdf not in not_found_response.text
    assert unavailable_response.status_code == 503
    assert unavailable_response.json()["detail"] == "email password unavailable"
    assert raw_secret_unavailable not in unavailable_response.text

    with app.state.session_factory() as session:
        active_secret = session.get(PatientPortalUnlockSecret, active_a_id)
        other_patient_secret = session.get(PatientPortalUnlockSecret, other_patient_id)
        unavailable_secret = session.get(PatientPortalUnlockSecret, unavailable_id)
        audit_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type.in_(
                        [
                            AUDIT_EVENT_UNLOCK_SECRET_LIST,
                            AUDIT_EVENT_UNLOCK_SECRET_READ,
                        ]
                    )
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        )

        assert active_secret is not None
        assert active_secret.last_viewed_at is not None
        assert other_patient_secret is not None
        assert other_patient_secret.last_viewed_at is None
        assert unavailable_secret is not None
        assert unavailable_secret.last_viewed_at is None
        assert [
            (event.event_type, event.outcome, event.account_id, event.demographic_no, event.reason)
            for event in audit_events
        ] == [
            (AUDIT_EVENT_UNLOCK_SECRET_LIST, AUDIT_OUTCOME_SUCCESS, account_a_id, 1234, None),
            (AUDIT_EVENT_UNLOCK_SECRET_READ, AUDIT_OUTCOME_SUCCESS, account_a_id, 1234, None),
            (
                AUDIT_EVENT_UNLOCK_SECRET_READ,
                AUDIT_OUTCOME_FAILURE,
                account_a_id,
                1234,
                "not_available",
            ),
            (
                AUDIT_EVENT_UNLOCK_SECRET_READ,
                AUDIT_OUTCOME_FAILURE,
                account_a_id,
                1234,
                "not_available",
            ),
            (
                AUDIT_EVENT_UNLOCK_SECRET_READ,
                AUDIT_OUTCOME_FAILURE,
                account_a_id,
                1234,
                "not_available",
            ),
            (
                AUDIT_EVENT_UNLOCK_SECRET_READ,
                AUDIT_OUTCOME_FAILURE,
                account_a_id,
                1234,
                "decryption_failed",
            ),
        ]


def test_browser_email_password_index_records_a_sanitized_list_audit_event() -> None:
    """Browsing the password index must be auditable, without storing the raw query."""
    app = migrated_development_app()
    client = TestClient(app)
    browser_sign_in_seeded_patient(app, client)

    plain_view = client.get("/portal/email-passwords")
    filtered_view = client.get("/portal/email-passwords?q=biopsy%20result")

    assert plain_view.status_code == 200
    assert filtered_view.status_code == 200
    with app.state.session_factory() as session:
        list_events = list(
            session.scalars(
                select(PatientPortalAuditEvent)
                .where(PatientPortalAuditEvent.event_type == AUDIT_EVENT_UNLOCK_SECRET_LIST)
                .order_by(PatientPortalAuditEvent.id)
            )
        )
        assert [event.reason for event in list_events] == ["browser", "browser_filtered"]
        assert all(event.outcome == "success" for event in list_events)
        # The search term is PHI-bearing free text and must never be persisted.
        assert all("biopsy" not in (event.reason or "") for event in list_events)


def test_email_change_confirmation_delivery_failure_revokes_the_pending_request() -> None:
    """A pending change the patient can never confirm is worse than no change at all.

    It occupies the one-pending-per-account slot and leaves them believing something is in flight,
    so a confirmation that cannot be delivered must take the request down with it.
    """
    app_holder: dict[str, object] = {}

    def act():
        app = migrated_development_app()
        app_holder["app"] = app
        client = TestClient(app)
        browser_sign_in_seeded_patient(app, client)
        return submit_contact_change(app, client)

    response = run_with_email_sender(FailingNoticeSender(fail_confirmation=True), act)
    app = app_holder["app"]

    assert response.status_code == 303
    assert (
        response.headers["location"]
        == "/portal/account?status=email-confirmation-notice-failed"
    )
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        email_change_request = session.scalar(select(PatientPortalEmailChangeRequest))
        outcomes = [
            (event.outcome, event.reason)
            for event in session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type
                    == AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_REQUEST
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        ]

        assert account is not None
        assert account.email == SEEDED_INVITE_EMAIL
        assert email_change_request is not None
        assert email_change_request.status == EMAIL_CHANGE_STATUS_REVOKED
        assert outcomes == [
            (AUDIT_OUTCOME_SUCCESS, "email_confirmation_requested"),
            (AUDIT_OUTCOME_FAILURE, "delivery_unavailable"),
        ]


def test_contact_change_records_a_failure_when_the_security_notice_cannot_be_sent() -> None:
    """The old-address notice is the takeover alarm; its failure must be durable and visible."""
    app_holder: dict[str, object] = {}

    def act():
        app = migrated_development_app()
        app_holder["app"] = app
        client = TestClient(app)
        browser_sign_in_seeded_patient(app, client)
        # Confirmation delivery succeeds, so the change completes; only the notices fail.
        submit_contact_change(app, client)
        confirmation_page = client.get("/auth/email-change/confirm")
        with app.state.session_factory() as session:
            token_row = session.scalar(select(PatientPortalEmailChangeRequest))
            assert token_row is not None
        return client, confirmation_page

    client, confirmation_page = run_with_email_sender(FailingNoticeSender(), act)
    app = app_holder["app"]
    # The token is only ever delivered by email, so read it back the way the patient would: from
    # the link. Here the sender discarded it, so drive the service directly instead.
    with app.state.session_factory() as session:
        account = session.scalar(select(PatientPortalAccount))
        assert account is not None
        pending = session.scalar(select(PatientPortalEmailChangeRequest))
        assert pending is not None
        assert pending.status == EMAIL_CHANGE_STATUS_PENDING

    assert confirmation_page.status_code == 200
    with app.state.session_factory() as session:
        outcomes = [
            (event.outcome, event.reason)
            for event in session.scalars(
                select(PatientPortalAuditEvent)
                .where(
                    PatientPortalAuditEvent.event_type
                    == AUDIT_EVENT_ACCOUNT_EMAIL_CHANGE_REQUEST
                )
                .order_by(PatientPortalAuditEvent.id)
            )
        ]
        # The confirmation went out, so the request stands; only the advisory notice to the
        # current address failed, and that must not cancel what the patient asked for.
        assert outcomes == [(AUDIT_OUTCOME_SUCCESS, "email_confirmation_requested")]


def test_dashboard_template_only_reads_fields_the_view_model_declares() -> None:
    """The typed view model exists to make a template/field mismatch fail loudly.

    Jinja renders an unknown attribute as empty rather than raising, so before the view model
    a renamed context key produced a silently blank cell. This pins the template's field usage
    against the declared contract instead.
    """
    template = (web_support.PACKAGE_DIR / "templates" / "dashboard.jinja").read_text(
        encoding="utf-8"
    )
    dashboard_fields = {field.name for field in fields(EmailPasswordDashboardViewModel)}
    row_fields = {field.name for field in fields(EmailPasswordRowViewModel)}

    used_dashboard_fields = set(re.findall(r"email_passwords\.([a-z_]+)", template))
    used_row_fields = set(re.findall(r"\brow\.([a-z_]+)", template))

    assert used_dashboard_fields, "template no longer reads the email-password view model"
    undeclared = sorted(used_dashboard_fields - dashboard_fields)
    assert not undeclared, f"template reads undeclared dashboard fields: {undeclared}"
    undeclared_rows = sorted(used_row_fields - row_fields)
    assert not undeclared_rows, f"template reads undeclared row fields: {undeclared_rows}"


def test_email_password_view_model_is_immutable() -> None:
    """`*ViewModel` carries no behaviour and cannot be mutated after assembly."""
    row = EmailPasswordRowViewModel(
        id=1,
        subject="Lab results",
        provider="CarlosDoc",
        sent_at="2026-08-05 10:00 UTC",
        source_reference="message-1",
        is_available=True,
    )

    with pytest.raises(AttributeError):
        row.subject = "changed"  # type: ignore[misc]


def test_presenters_perform_no_writes() -> None:
    """`*ViewModelAssembler` is read-only orchestration; a write belongs to the route.

    Enforced structurally rather than by convention, because the previous version of the
    dashboard audit event was added inside the render path and looked perfectly reasonable there.
    """
    presenter_source = (web_support.PACKAGE_DIR / "presenters.py").read_text(encoding="utf-8")

    forbidden = re.findall(
        r"session\.(?:commit|add|flush|delete)\(|record_audit_event\(",
        presenter_source,
    )

    assert not forbidden, f"presenters.py must not write: {sorted(set(forbidden))}"


@pytest.mark.parametrize(
    ("template_name", "context_builder"),
    [
        ("index.jinja", "index_template_context"),
        ("activate.jinja", "public_auth_template_context"),
        ("password_reset_request.jinja", "public_auth_template_context"),
        ("password_reset_complete.jinja", "public_auth_template_context"),
        ("auth_result.jinja", "public_auth_template_context"),
        ("locked.jinja", "public_auth_template_context"),
        ("mfa.jinja", "mfa_template_context"),
    ],
)
def test_public_template_reads_only_keys_its_context_builder_supplies(
    template_name: str, context_builder: str
) -> None:
    """Every variable a public template reads must be supplied by its assembler.

    Jinja renders an unknown name as empty instead of raising, so an assembler that stops
    supplying a key -- or a template that starts reading a new one -- fails silently in the
    browser. This closes that gap for the pages that do not have a dedicated view model.
    """
    settings = Settings(environment="development", database_url="sqlite+pysqlite:///:memory:")
    builder = getattr(web_support, context_builder)
    # Minimal stand-in for a Request: only the attributes the assemblers actually read, so this
    # keeps failing loudly if one starts reaching for something else.
    request_stub = SimpleNamespace(
        cookies={},
        headers={},
        url=SimpleNamespace(path="/", query=""),
    )
    if context_builder == "mfa_template_context":
        context = builder(
            request_stub,
            settings=settings,
            delivery=_sample_mfa_delivery(),
            csrf_token="token",
        )
    else:
        context = builder(request_stub, settings=settings, csrf_token="token")
    # Derived from the assembler itself rather than a hand-maintained list, so the assertion
    # cannot drift away from what the code actually supplies.
    supplied = set(context) | {"url_for", "request"}

    used = _template_variable_names(template_name)

    undeclared = sorted(used - supplied)
    assert not undeclared, f"{template_name} reads keys no assembler supplies: {undeclared}"


def test_expired_email_change_confirmation_is_rejected() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender, email_change_token_ttl_seconds=300)
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    confirmation_token = request_seeded_email_change(app, client, sender)

    with app.state.session_factory() as session:
        with session.begin():
            pending = session.scalar(select(PatientPortalEmailChangeRequest))
            assert pending is not None
            pending.expires_at = pending.created_at + timedelta(seconds=1)
            pending.created_at = pending.created_at - timedelta(days=2)
            pending.expires_at = pending.created_at + timedelta(seconds=1)

    expired = confirm_seeded_email_change(client, confirmation_token)

    assert expired.status_code == 400
    assert "no longer valid" in expired.text
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        pending = session.scalar(select(PatientPortalEmailChangeRequest))
        assert account is not None
        assert account.email == SEEDED_INVITE_EMAIL
        # The expired row is closed out rather than left pending and blocking a fresh attempt.
        assert pending is not None
        assert pending.status == EMAIL_CHANGE_STATUS_REVOKED


def test_superseding_an_email_change_invalidates_the_link_already_sent() -> None:
    """A corrected typo must not leave the mistyped address able to take over the account."""
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    account_id = browser_sign_in_seeded_patient(app, client)
    mistyped_token = request_seeded_email_change(
        app,
        client,
        sender,
        email="typo.patient@example.com",
    )
    corrected_token = request_seeded_email_change(
        app,
        client,
        sender,
        email="corrected.patient@example.com",
    )

    superseded = confirm_seeded_email_change(client, mistyped_token)
    corrected = confirm_seeded_email_change(client, corrected_token)

    assert superseded.status_code == 400
    assert corrected.status_code == 200
    with app.state.session_factory() as session:
        account = session.get(PatientPortalAccount, account_id)
        assert account is not None
        assert account.email == "corrected.patient@example.com"


def test_email_change_confirmation_rejects_an_unknown_token() -> None:
    sender = RecordingPortalEmailSender()
    app = migrated_development_app(email_sender=sender)
    client = TestClient(app)
    browser_sign_in_seeded_patient(app, client)

    forged = confirm_seeded_email_change(client, "not-a-real-confirmation-token")

    assert forged.status_code == 400
    assert "no longer valid" in forged.text
