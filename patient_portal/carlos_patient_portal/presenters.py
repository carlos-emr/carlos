"""Read-only assemblers that build one view model for one rendered page.

These follow the CARLOS `*ViewModelAssembler` contract in `docs/architecture/layer-names.md`:
each function builds the view state for exactly one view and performs read-only orchestration.
Assemblers must not write: no audit events, no session mutation, no commits. A write that belongs
to a page render belongs in the route that owns the request, not here.
"""

from datetime import UTC, date, datetime, timedelta
from datetime import time as datetime_time
from urllib.parse import urlencode
from zoneinfo import ZoneInfo

from sqlalchemy.orm import Session

from carlos_patient_portal.i18n import DEFAULT_LOCALE, format_portal_datetime, portal_text
from carlos_patient_portal.models import (
    UNLOCK_SECRET_STATUS_ACTIVE,
    UNLOCK_SECRET_TYPE_EMAIL,
    PatientPortalAccount,
    PatientPortalUnlockSecret,
)
from carlos_patient_portal.unlock_secrets import (
    DEFAULT_UNLOCK_SECRET_LIST_LIMIT,
    MAX_UNLOCK_SECRET_SEARCH_LENGTH,
    count_unlock_secrets,
    list_unlock_secret_provider_options,
    list_unlock_secrets,
)
from carlos_patient_portal.view_models import (
    EmailPasswordDashboardViewModel,
    EmailPasswordRowViewModel,
    ProviderFilterOptionViewModel,
)

EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE = DEFAULT_UNLOCK_SECRET_LIST_LIMIT
PORTAL_EMAIL_PASSWORD_PATH = "/portal/email-passwords"


def normalize_email_password_dashboard_search(search: str | None) -> str | None:
    if search is None:
        return None
    normalized_search = search.strip()
    if not normalized_search:
        return None
    return normalized_search[:MAX_UNLOCK_SECRET_SEARCH_LENGTH]


def normalize_email_password_dashboard_provider(provider: str | None) -> str | None:
    if provider is None:
        return None
    normalized_provider = provider.strip()
    if normalized_provider in {"id:", "name:"}:
        raise ValueError("structured provider filter must include a value")
    return normalized_provider or None


def dashboard_created_before(
    date_to: date | None,
    *,
    timezone_name: str = "UTC",
) -> datetime | None:
    """Return the exclusive upper bound for a local-date filter, expressed in UTC.

    Storage and FHIR instants stay in UTC while the patient filters by clinic-local dates, so the
    boundary is built in the clinic timezone and converted, not taken as a UTC midnight.
    """
    if date_to is None:
        return None
    if date_to == date.max:
        return datetime.max.replace(tzinfo=UTC)
    local_boundary = datetime.combine(
        date_to + timedelta(days=1),
        datetime_time.min,
        tzinfo=ZoneInfo(timezone_name),
    )
    return local_boundary.astimezone(UTC)


def _dashboard_created_from(
    date_from: date | None,
    *,
    timezone_name: str,
) -> datetime | None:
    if date_from is None:
        return None
    if date_from == date.min:
        return datetime.min.replace(tzinfo=UTC)
    return datetime.combine(
        date_from,
        datetime_time.min,
        tzinfo=ZoneInfo(timezone_name),
    ).astimezone(UTC)


def portal_email_password_page_href(
    *,
    search: str | None,
    provider: str | None,
    date_from: date | None,
    date_to: date | None,
    page: int,
    base_path: str = PORTAL_EMAIL_PASSWORD_PATH,
) -> str:
    query_params: dict[str, str] = {}
    normalized_search = normalize_email_password_dashboard_search(search)
    if normalized_search is not None:
        query_params["q"] = normalized_search
    normalized_provider = normalize_email_password_dashboard_provider(provider)
    if normalized_provider is not None:
        query_params["provider"] = normalized_provider
    if date_from is not None:
        query_params["date_from"] = date_from.isoformat()
    if date_to is not None:
        query_params["date_to"] = date_to.isoformat()
    if page > 1:
        query_params["page"] = str(page)
    query_string = urlencode(query_params)
    if not query_string:
        return base_path
    return f"{base_path}?{query_string}"


def assemble_email_password_row(
    unlock_secret: PatientPortalUnlockSecret,
    *,
    text: dict[str, str],
    timezone_name: str = "UTC",
    locale: str = DEFAULT_LOCALE,
) -> EmailPasswordRowViewModel:
    return EmailPasswordRowViewModel(
        id=unlock_secret.id,
        subject=unlock_secret.label or text["email_password"],
        provider=unlock_secret.created_by,
        sent_at=format_portal_datetime(
            unlock_secret.created_at,
            timezone_name=timezone_name,
            locale=locale,
        ),
        source_reference=unlock_secret.source_reference,
        is_available=unlock_secret.status == UNLOCK_SECRET_STATUS_ACTIVE,
    )


def assemble_email_password_dashboard(
    session: Session,
    account: PatientPortalAccount,
    *,
    search: str | None,
    provider: str | None,
    date_from: date | None,
    date_to: date | None,
    page: int,
    timezone_name: str = "UTC",
    filter_error: str | None = None,
    base_path: str = PORTAL_EMAIL_PASSWORD_PATH,
    locale: str = DEFAULT_LOCALE,
) -> EmailPasswordDashboardViewModel:
    """Build the email-password view state for `dashboard.jinja`."""
    text = portal_text(locale)
    normalized_search = normalize_email_password_dashboard_search(search)
    normalized_provider = normalize_email_password_dashboard_provider(provider)
    invalid_date_range = date_from is not None and date_to is not None and date_from > date_to
    has_filter_error = filter_error is not None or invalid_date_range
    has_filters = any((normalized_search, normalized_provider, date_from, date_to))
    created_from = _dashboard_created_from(date_from, timezone_name=timezone_name)
    created_before = dashboard_created_before(date_to, timezone_name=timezone_name)
    # A rejected filter must not run a query whose bounds were never valid.
    total_records = (
        0
        if has_filter_error
        else count_unlock_secrets(
            session,
            clinic_id=account.clinic_id,
            account_id=account.id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            search=normalized_search,
            provider=normalized_provider,
            created_from=created_from,
            created_before=created_before,
        )
    )
    total_pages = max(
        1,
        (total_records + EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE - 1)
        // EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE,
    )
    normalized_page = min(max(page, 1), total_pages)
    offset = (normalized_page - 1) * EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE
    records = (
        []
        if has_filter_error
        else list_unlock_secrets(
            session,
            clinic_id=account.clinic_id,
            account_id=account.id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            search=normalized_search,
            provider=normalized_provider,
            created_from=created_from,
            created_before=created_before,
            limit=EMAIL_PASSWORD_DASHBOARD_PAGE_SIZE,
            offset=offset,
        )
    )
    provider_options = list_unlock_secret_provider_options(
        session,
        clinic_id=account.clinic_id,
        account_id=account.id,
        demographic_no=account.demographic_no,
        secret_type=UNLOCK_SECRET_TYPE_EMAIL,
    )
    return EmailPasswordDashboardViewModel(
        rows=tuple(
            assemble_email_password_row(
                record,
                text=text,
                timezone_name=timezone_name,
                locale=locale,
            )
            for record in records
        ),
        search=normalized_search or "",
        provider=normalized_provider or "",
        provider_options=tuple(
            ProviderFilterOptionViewModel(value=value, label=label)
            for value, label in provider_options.options
        ),
        provider_option_values=tuple(value for value, _ in provider_options.options),
        provider_options_truncated=provider_options.truncated,
        date_from=date_from.isoformat() if date_from is not None else "",
        date_to=date_to.isoformat() if date_to is not None else "",
        has_filters=has_filters,
        filter_error=filter_error or (text["date_range_error"] if invalid_date_range else None),
        page=normalized_page,
        total_pages=total_pages,
        empty_message=(
            text["no_matching_email_passwords"] if has_filters else text["no_email_passwords"]
        ),
        previous_href=(
            portal_email_password_page_href(
                search=normalized_search,
                provider=normalized_provider,
                date_from=date_from,
                date_to=date_to,
                page=normalized_page - 1,
                base_path=base_path,
            )
            if normalized_page > 1
            else None
        ),
        next_href=(
            portal_email_password_page_href(
                search=normalized_search,
                provider=normalized_provider,
                date_from=date_from,
                date_to=date_to,
                page=normalized_page + 1,
                base_path=base_path,
            )
            if normalized_page < total_pages
            else None
        ),
    )
