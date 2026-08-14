"""FHIR R4 endpoints for the patient-scoped portal profile.

Registered by `main.create_app`. Everything here is patient-scoped: each handler derives its
clinic and demographic from the authenticated session rather than from request input.
"""

from typing import Annotated
from urllib.parse import urlencode

from fastapi import Depends, FastAPI, Query, Request, status
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.auth import AuthenticatedPortalSession
from carlos_patient_portal.config import Settings
from carlos_patient_portal.interop import (
    build_fhir_organization_id,
    build_fhir_patient_id,
    build_fhir_practitioner_id,
    build_fhir_r4_bundle,
    build_fhir_r4_capability_statement,
    build_fhir_r4_document_reference,
    build_fhir_r4_organization,
    build_fhir_r4_portal_patient,
    build_fhir_r4_practitioner,
)
from carlos_patient_portal.models import (
    AUDIT_ACTOR_TYPE_PATIENT,
    AUDIT_EVENT_FHIR_READ,
    AUDIT_EVENT_FHIR_SEARCH,
    AUDIT_OUTCOME_FAILURE,
    AUDIT_OUTCOME_SUCCESS,
    MAX_AUDIT_RESOURCE_ID_LENGTH,
    UNLOCK_SECRET_STATUS_ACTIVE,
    UNLOCK_SECRET_TYPE_EMAIL,
    PatientPortalAccount,
    PatientPortalUnlockSecret,
)
from carlos_patient_portal.runtime import (
    MAX_DATABASE_ID,
    MAX_PAGE_OFFSET,
    FhirApiError,
    PortalRuntime,
    RouteDependencies,
    function_scoped_database_dependency,
)
from carlos_patient_portal.unlock_secrets import (
    MAX_UNLOCK_SECRET_LIST_LIMIT,
    UnlockSecretNotFoundError,
    count_unlock_secret_providers,
    count_unlock_secrets,
    get_scoped_unlock_secret,
    list_unlock_secret_provider_identities,
    list_unlock_secrets,
)

FHIR_JSON_MEDIA_TYPE = "application/fhir+json"


def fhir_search_url(
    *,
    base_url: str,
    resource_type: str,
    count: int,
    offset: int,
    resource_id: str | None = None,
    subject: str | None = None,
) -> str:
    query: list[tuple[str, str]] = [
        ("_count", str(count)),
        ("_offset", str(offset)),
    ]
    if resource_id is not None:
        query.append(("_id", resource_id))
    if subject is not None:
        query.append(("subject", subject))
    return f"{base_url.rstrip('/')}/{resource_type}?{urlencode(query)}"


def fhir_base_url(settings: Settings, request: Request) -> str:
    if settings.public_base_url is not None:
        return f"{settings.public_base_url.rstrip('/')}/fhir"
    return f"{str(request.base_url).rstrip('/')}/fhir"


def fhir_bundle_links(
    *,
    base_url: str,
    resource_type: str,
    count: int,
    offset: int,
    total: int,
    resource_id: str | None = None,
    subject: str | None = None,
) -> tuple[str, str | None, str | None]:
    def build_link(page_offset: int) -> str:
        return fhir_search_url(
            base_url=base_url,
            resource_type=resource_type,
            count=count,
            offset=page_offset,
            resource_id=resource_id,
            subject=subject,
        )

    self_link = build_link(offset)
    previous_link = build_link(max(0, offset - count)) if offset > 0 else None
    next_link = build_link(offset + count) if offset + count < total else None
    return self_link, previous_link, next_link


def fhir_json_response(
    payload: dict[str, object],
    *,
    status_code: int = status.HTTP_200_OK,
) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content=payload,
        media_type=FHIR_JSON_MEDIA_TYPE,
    )


def fhir_not_found() -> FhirApiError:
    return FhirApiError(
        status_code=status.HTTP_404_NOT_FOUND,
        code="not-found",
        diagnostics="resource not found",
    )


def fhir_patient_reference(account: PatientPortalAccount) -> str:
    return f"Patient/{build_fhir_patient_id(account.clinic_id, account.demographic_no)}"


def find_fhir_practitioner(
    session: Session,
    account: PatientPortalAccount,
    practitioner_id: str,
) -> tuple[str | None, str] | None:
    """Resolve one of this patient's providers by its FHIR logical id.

    Scans the patient's provider identities in pages, hashing each candidate, because the FHIR id
    is `sha256`-derived and cannot be reversed into a WHERE clause. That id scheme is deliberate --
    it keeps provider names out of URLs and out of any log that records a path -- and the scan is
    the price of it.

    Bounded by how many distinct providers have sent one patient an encrypted message, which is a
    handful, so the cost is small in practice rather than merely small in theory. If that stops
    holding, the fix is a lookup table keyed by the derived hash, not a change to the id scheme;
    changing the scheme would expose provider identifiers and invalidate every id already issued.
    """
    total = count_unlock_secret_providers(
        session,
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        secret_type=UNLOCK_SECRET_TYPE_EMAIL,
    )
    for provider_offset in range(0, total, MAX_UNLOCK_SECRET_LIST_LIMIT):
        provider_rows = list_unlock_secret_provider_identities(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            limit=MAX_UNLOCK_SECRET_LIST_LIMIT,
            offset=provider_offset,
        )
        for provider_id, name in provider_rows:
            if practitioner_id == build_fhir_practitioner_id(
                clinic_id=account.clinic_id,
                name=name,
                provider_id=provider_id,
            ):
                return provider_id, name
    return None


def parse_fhir_numeric_id(resource_id: str) -> int:
    try:
        parsed_id = int(resource_id)
    except ValueError as exc:
        raise fhir_not_found() from exc
    # Python ints are unbounded; anything beyond the column's range must be rejected here rather
    # than raising OverflowError/DataError inside the driver and surfacing as a 500.
    if not 0 < parsed_id <= MAX_DATABASE_ID:
        raise fhir_not_found()
    return parsed_id


def record_fhir_access(
    session: Session,
    account: PatientPortalAccount,
    *,
    event_type: str,
    resource_type: str,
    resource_id: str | None,
    outcome: str,
    reason: str,
) -> None:
    # The raw client-supplied id is stored for traceability, but it must never be able to violate
    # the column's length CHECK: an audit write that raises would turn a clean 404 into a 500 and
    # lose the very event recording the attempt.
    if resource_id is not None:
        # Control characters must go too: SQLite's length() stops at an embedded NUL, so a raw
        # "\x00" id measures as length 0 and fails the same CHECK from the other direction.
        resource_id = "".join(
            character if character.isprintable() else "?" for character in resource_id
        )
        if len(resource_id) > MAX_AUDIT_RESOURCE_ID_LENGTH:
            resource_id = f"{resource_id[: MAX_AUDIT_RESOURCE_ID_LENGTH - 1]}~"
        if not resource_id:
            resource_id = "?"
    record_audit_event(
        session,
        event_type=event_type,
        outcome=outcome,
        actor_type=AUDIT_ACTOR_TYPE_PATIENT,
        actor=account.username,
        actor_id=str(account.id),
        clinic_id=account.clinic_id,
        demographic_no=account.demographic_no,
        account_id=account.id,
        resource_type=resource_type,
        resource_id=resource_id,
        reason=reason,
    )


def register_fhir_routes(
    app: FastAPI,
    runtime: PortalRuntime,
    route_dependencies: RouteDependencies,
) -> None:
    settings = runtime.settings
    get_app_database_session = route_dependencies.get_app_database_session
    get_authenticated_fhir_session = route_dependencies.get_authenticated_fhir_session

    @app.get("/fhir/metadata")
    def fhir_metadata(request: Request) -> JSONResponse:
        return fhir_json_response(
            build_fhir_r4_capability_statement(
                service_name=settings.service_name,
                base_url=fhir_base_url(settings, request),
            ),
        )

    @app.get("/fhir/Patient")
    def fhir_patient_search(
        request: Request,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        resource_id: Annotated[str | None, Query(alias="_id", max_length=64)] = None,
        count: Annotated[int, Query(alias="_count", ge=1, le=100)] = 20,
        offset: Annotated[int, Query(alias="_offset", ge=0, le=MAX_PAGE_OFFSET)] = 0,
    ) -> JSONResponse:
        account = authenticated_session.account
        patient = build_fhir_r4_portal_patient(account)
        matches = resource_id is None or resource_id == patient["id"]
        resources = [patient] if matches and offset == 0 else []
        total = 1 if matches else 0
        base_url = fhir_base_url(settings, request)
        self_link, previous_link, next_link = fhir_bundle_links(
            base_url=base_url,
            resource_type="Patient",
            count=count,
            offset=offset,
            total=total,
            resource_id=resource_id,
        )
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_SEARCH,
            resource_type="Patient",
            resource_id=str(patient["id"]),
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="search",
        )
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=resources,
                base_url=base_url,
                self_link=self_link,
                previous_link=previous_link,
                next_link=next_link,
                total=total,
            )
        )

    @app.get("/fhir/Patient/{patient_id}")
    def fhir_patient_read(
        patient_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        expected_patient_id = build_fhir_patient_id(account.clinic_id, account.demographic_no)
        if patient_id != expected_patient_id:
            record_fhir_access(
                session,
                account,
                event_type=AUDIT_EVENT_FHIR_READ,
                resource_type="Patient",
                resource_id=patient_id,
                outcome=AUDIT_OUTCOME_FAILURE,
                reason="not_found",
            )
            session.commit()
            raise fhir_not_found()
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_READ,
            resource_type="Patient",
            resource_id=patient_id,
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="read",
        )
        return fhir_json_response(build_fhir_r4_portal_patient(account))

    @app.get("/fhir/DocumentReference")
    def fhir_document_reference_search(
        request: Request,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        resource_id: Annotated[str | None, Query(alias="_id", max_length=64)] = None,
        subject: Annotated[str | None, Query(max_length=128)] = None,
        count: Annotated[int, Query(alias="_count", ge=1, le=100)] = 20,
        offset: Annotated[int, Query(alias="_offset", ge=0, le=MAX_PAGE_OFFSET)] = 0,
    ) -> JSONResponse:
        account = authenticated_session.account
        patient_reference = fhir_patient_reference(account)
        subject_matches = subject is None or subject.removeprefix("Patient/") == (
            patient_reference.removeprefix("Patient/")
        )
        records: list[PatientPortalUnlockSecret] = []
        total = 0
        if subject_matches and resource_id is not None:
            try:
                record = get_scoped_unlock_secret(
                    session,
                    parse_fhir_numeric_id(resource_id),
                    clinic_id=account.clinic_id,
                    demographic_no=account.demographic_no,
                    secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                )
                if record.status == UNLOCK_SECRET_STATUS_ACTIVE:
                    total = 1
                    if offset == 0:
                        records = [record]
            except (FhirApiError, UnlockSecretNotFoundError):
                # A search for an id that is malformed or out of scope is an empty bundle, not an
                # error: FHIR search reports "no matches" the same way regardless of why. The read
                # handlers below deliberately do the opposite and audit the attempt, because a read
                # names one resource and a failed read is worth an investigator's attention.
                # The successful-search audit event below still records that the search happened.
                pass
        elif subject_matches:
            total = count_unlock_secrets(
                session,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            )
            records = list_unlock_secrets(
                session,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                limit=count,
                offset=offset,
            )
        resources = [
            build_fhir_r4_document_reference(
                record,
                patient_reference=patient_reference,
            )
            for record in records
        ]
        base_url = fhir_base_url(settings, request)
        self_link, previous_link, next_link = fhir_bundle_links(
            base_url=base_url,
            resource_type="DocumentReference",
            count=count,
            offset=offset,
            total=total,
            resource_id=resource_id,
            subject=subject,
        )
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_SEARCH,
            resource_type="DocumentReference",
            resource_id=resource_id or patient_reference,
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="search",
        )
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=resources,
                base_url=base_url,
                self_link=self_link,
                previous_link=previous_link,
                next_link=next_link,
                total=total,
            )
        )

    @app.get("/fhir/DocumentReference/{document_reference_id}")
    def fhir_document_reference_read(
        document_reference_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        try:
            # Parsing stays inside the audited branch so a malformed ID is recorded exactly like an
            # unknown one; otherwise `/fhir/DocumentReference/not-a-number` left no read event.
            record = get_scoped_unlock_secret(
                session,
                parse_fhir_numeric_id(document_reference_id),
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
            )
        except (UnlockSecretNotFoundError, FhirApiError) as exc:
            record_fhir_access(
                session,
                account,
                event_type=AUDIT_EVENT_FHIR_READ,
                resource_type="DocumentReference",
                resource_id=document_reference_id,
                outcome=AUDIT_OUTCOME_FAILURE,
                reason="not_found",
            )
            session.commit()
            raise fhir_not_found() from exc
        if record.status != UNLOCK_SECRET_STATUS_ACTIVE:
            record_fhir_access(
                session,
                account,
                event_type=AUDIT_EVENT_FHIR_READ,
                resource_type="DocumentReference",
                resource_id=document_reference_id,
                outcome=AUDIT_OUTCOME_FAILURE,
                reason="not_found",
            )
            session.commit()
            raise fhir_not_found()
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_READ,
            resource_type="DocumentReference",
            resource_id=document_reference_id,
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="read",
        )
        return fhir_json_response(
            build_fhir_r4_document_reference(
                record,
                patient_reference=fhir_patient_reference(account),
            )
        )

    @app.get("/fhir/Organization")
    def fhir_organization_search(
        request: Request,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        resource_id: Annotated[str | None, Query(alias="_id", max_length=64)] = None,
        count: Annotated[int, Query(alias="_count", ge=1, le=100)] = 20,
        offset: Annotated[int, Query(alias="_offset", ge=0, le=MAX_PAGE_OFFSET)] = 0,
    ) -> JSONResponse:
        account = authenticated_session.account
        organization = build_fhir_r4_organization(
            clinic_id=account.clinic_id,
            clinic_name=settings.clinic_name,
        )
        matches = resource_id is None or resource_id == organization["id"]
        total = 1 if matches else 0
        resources = [organization] if matches and offset == 0 else []
        base_url = fhir_base_url(settings, request)
        self_link, previous_link, next_link = fhir_bundle_links(
            base_url=base_url,
            resource_type="Organization",
            count=count,
            offset=offset,
            total=total,
            resource_id=resource_id,
        )
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_SEARCH,
            resource_type="Organization",
            resource_id=str(organization["id"]),
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="search",
        )
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=resources,
                base_url=base_url,
                self_link=self_link,
                previous_link=previous_link,
                next_link=next_link,
                total=total,
            )
        )

    @app.get("/fhir/Organization/{organization_id}")
    def fhir_organization_read(
        organization_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        if organization_id != build_fhir_organization_id(account.clinic_id):
            record_fhir_access(
                session,
                account,
                event_type=AUDIT_EVENT_FHIR_READ,
                resource_type="Organization",
                resource_id=organization_id,
                outcome=AUDIT_OUTCOME_FAILURE,
                reason="not_found",
            )
            session.commit()
            raise fhir_not_found()
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_READ,
            resource_type="Organization",
            resource_id=organization_id,
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="read",
        )
        return fhir_json_response(
            build_fhir_r4_organization(
                clinic_id=account.clinic_id,
                clinic_name=settings.clinic_name,
            )
        )

    @app.get("/fhir/Practitioner")
    def fhir_practitioner_search(
        request: Request,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
        resource_id: Annotated[str | None, Query(alias="_id", max_length=64)] = None,
        count: Annotated[int, Query(alias="_count", ge=1, le=100)] = 20,
        offset: Annotated[int, Query(alias="_offset", ge=0, le=MAX_PAGE_OFFSET)] = 0,
    ) -> JSONResponse:
        account = authenticated_session.account
        total = count_unlock_secret_providers(
            session,
            clinic_id=account.clinic_id,
            demographic_no=account.demographic_no,
            secret_type=UNLOCK_SECRET_TYPE_EMAIL,
        )
        if resource_id is None:
            provider_rows = list_unlock_secret_provider_identities(
                session,
                clinic_id=account.clinic_id,
                demographic_no=account.demographic_no,
                secret_type=UNLOCK_SECRET_TYPE_EMAIL,
                limit=count,
                offset=offset,
            )
        else:
            provider = find_fhir_practitioner(session, account, resource_id)
            provider_rows = [provider] if provider is not None and offset == 0 else []
            total = 1 if provider is not None else 0
        resources = []
        for provider_id, name in provider_rows:
            practitioner = build_fhir_r4_practitioner(
                clinic_id=account.clinic_id,
                name=name,
                provider_id=provider_id,
            )
            if resource_id is None or practitioner["id"] == resource_id:
                resources.append(practitioner)
        base_url = fhir_base_url(settings, request)
        self_link, previous_link, next_link = fhir_bundle_links(
            base_url=base_url,
            resource_type="Practitioner",
            count=count,
            offset=offset,
            total=total,
            resource_id=resource_id,
        )
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_SEARCH,
            resource_type="Practitioner",
            resource_id=resource_id or fhir_patient_reference(account),
            outcome=AUDIT_OUTCOME_SUCCESS,
            reason="search",
        )
        return fhir_json_response(
            build_fhir_r4_bundle(
                resources=resources,
                base_url=base_url,
                self_link=self_link,
                previous_link=previous_link,
                next_link=next_link,
                total=total,
            )
        )

    @app.get("/fhir/Practitioner/{practitioner_id}")
    def fhir_practitioner_read(
        practitioner_id: str,
        authenticated_session: Annotated[
            AuthenticatedPortalSession,
            Depends(get_authenticated_fhir_session),
        ],
        session: Annotated[Session, function_scoped_database_dependency(get_app_database_session)],
    ) -> JSONResponse:
        account = authenticated_session.account
        provider = find_fhir_practitioner(session, account, practitioner_id)
        if provider is not None:
            provider_id, name = provider
            record_fhir_access(
                session,
                account,
                event_type=AUDIT_EVENT_FHIR_READ,
                resource_type="Practitioner",
                resource_id=practitioner_id,
                outcome=AUDIT_OUTCOME_SUCCESS,
                reason="read",
            )
            return fhir_json_response(
                build_fhir_r4_practitioner(
                    clinic_id=account.clinic_id,
                    name=name,
                    provider_id=provider_id,
                )
            )
        record_fhir_access(
            session,
            account,
            event_type=AUDIT_EVENT_FHIR_READ,
            resource_type="Practitioner",
            resource_id=practitioner_id,
            outcome=AUDIT_OUTCOME_FAILURE,
            reason="not_found",
        )
        session.commit()
        raise fhir_not_found()
