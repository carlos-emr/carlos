"""Shared runtime types for the portal's HTTP layer.

Extracted from `main.py` so route modules can depend on these without importing the application
module that composes them, which would be circular. Nothing here registers routes or performs I/O.
"""

from collections import OrderedDict
from collections.abc import Awaitable, Callable, Generator
from dataclasses import dataclass, field
from math import ceil
from threading import Lock
from time import monotonic

from fastapi import Depends, Request, Response
from fastapi.responses import RedirectResponse
from sqlalchemy import Engine
from sqlalchemy.orm import Session, sessionmaker

from carlos_patient_portal.accounts import ActivationRateLimit
from carlos_patient_portal.auth import AuthenticatedPortalSession, AuthPolicy
from carlos_patient_portal.config import Settings
from carlos_patient_portal.email_delivery import PortalEmailSender
from carlos_patient_portal.sms_delivery import PortalSmsSender
from carlos_patient_portal.token_keys import PortalTokenKeys

MAX_PAGE_OFFSET = 100_000
# Largest value a 64-bit signed integer key can hold; ids beyond this cannot exist in any row.
MAX_DATABASE_ID = 2**63 - 1



@dataclass
class PortalOperationalMetrics:
    _lock: Lock = field(default_factory=Lock)
    _request_counts: dict[str, int] = field(default_factory=dict)
    _failure_counts: dict[str, int] = field(default_factory=dict)
    _request_duration_ms_total: int = 0

    def record_request(self, status_code: int, duration_ms: int) -> None:
        status_group = f"{status_code // 100}xx"
        with self._lock:
            self._request_counts[status_group] = self._request_counts.get(status_group, 0) + 1
            self._request_duration_ms_total += max(0, duration_ms)

    def record_failure(self, failure_type: str) -> None:
        with self._lock:
            self._failure_counts[failure_type] = self._failure_counts.get(failure_type, 0) + 1

    def snapshot(self) -> dict[str, object]:
        with self._lock:
            return {
                "requests": dict(self._request_counts),
                "failures": dict(self._failure_counts),
                "request_duration_ms_total": self._request_duration_ms_total,
            }


@dataclass
class RateLimitBucket:
    window_started_at: float
    request_count: int


class InMemoryRateLimiter:
    """Small per-process limiter for pilot deployments before shared edge limits exist."""

    def __init__(
        self,
        *,
        window_seconds: int,
        max_requests: int,
        max_buckets: int = 10_000,
    ) -> None:
        self.window_seconds = window_seconds
        self.max_requests = max_requests
        self.max_buckets = max_buckets
        self.buckets: OrderedDict[str, RateLimitBucket] = OrderedDict()
        self.lock = Lock()

    def consume(self, key: str, *, now: float | None = None) -> int | None:
        """Charge one request to ``key``; return None if allowed, else the retry-after seconds.

        This mutates: it creates buckets, rolls windows, and increments the counter. Call it once
        per request. Peeking at the current state is deliberately not offered, because a caller
        that logged the value would silently spend a request from the budget.
        """
        current_time = now if now is not None else monotonic()
        with self.lock:
            self.prune_expired_buckets(current_time)
            bucket = self.buckets.get(key)
            if bucket is None:
                if len(self.buckets) >= self.max_buckets:
                    self.buckets.popitem(last=False)
                self.buckets[key] = RateLimitBucket(
                    window_started_at=current_time,
                    request_count=1,
                )
                return None
            self.buckets.move_to_end(key)

            elapsed_seconds = current_time - bucket.window_started_at
            if elapsed_seconds >= self.window_seconds:
                bucket.window_started_at = current_time
                bucket.request_count = 1
                return None

            if bucket.request_count >= self.max_requests:
                return max(1, ceil(self.window_seconds - elapsed_seconds))

            bucket.request_count += 1
            return None

    def prune_expired_buckets(self, current_time: float) -> None:
        while self.buckets:
            key, bucket = next(iter(self.buckets.items()))
            if current_time - bucket.window_started_at < self.window_seconds:
                break
            del self.buckets[key]


@dataclass(frozen=True)
class PortalRuntime:
    settings: Settings
    database_engine: Engine
    session_factory: sessionmaker[Session]
    # One independent key per token purpose, derived from PATIENT_PORTAL_SESSION_SECRET. Replaced
    # the single `csrf_secret` that was previously handed to CSRF signing, session hashing, MFA
    # hashing, and reset-token hashing alike.
    token_keys: PortalTokenKeys
    identity_proof_secret: str
    audit_hash_secret: str
    unlock_secret_encryption_secret: str
    activation_rate_limit: ActivationRateLimit
    auth_policy: AuthPolicy
    rate_limiter: InMemoryRateLimiter
    auth_rate_limiter: InMemoryRateLimiter
    email_sender: PortalEmailSender | None
    sms_sender: PortalSmsSender | None
    unlock_secret_encryption_keys: dict[str, str] | None = None
    unlock_secret_active_key_id: str = "primary"
    operational_metrics: PortalOperationalMetrics = field(default_factory=PortalOperationalMetrics)


@dataclass(frozen=True)
class RouteDependencies:
    get_app_database_session: Callable[..., Generator[Session, None, None]]
    require_internal_health_token: Callable[..., None]
    get_dev_admin_actor: Callable[..., str]
    get_authorization_bearer_token: Callable[..., str]
    get_authenticated_portal_session: Callable[..., AuthenticatedPortalSession]
    get_authenticated_fhir_session: Callable[..., AuthenticatedPortalSession]
    render_index_response: Callable[..., Response]
    render_portal_page: Callable[..., Response]
    get_portal_account_form_values: Callable[..., Awaitable[dict[str, list[str]]]]
    get_portal_cookie_session_or_redirect: Callable[
        [Request, Session],
        AuthenticatedPortalSession | RedirectResponse,
    ]
    render_account_change_error: Callable[..., Response]


class FhirApiError(Exception):
    """Raised when a FHIR endpoint should return an OperationOutcome."""

    def __init__(self, *, status_code: int, code: str, diagnostics: str) -> None:
        super().__init__(diagnostics)
        self.status_code = status_code
        self.code = code
        self.diagnostics = diagnostics


def function_scoped_database_dependency(
    dependency: Callable[[], Generator[Session, None, None]],
) -> object:
    # FastAPI supports dependency teardown before response delivery with scope="function".
    return Depends(dependency, scope="function")  # NOSONAR - Sonar's FastAPI stub lacks scope.
