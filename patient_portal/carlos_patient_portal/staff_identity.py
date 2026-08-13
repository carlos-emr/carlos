from dataclasses import dataclass
from secrets import compare_digest

from carlos_patient_portal.config import Settings
from carlos_patient_portal.invites import normalize_clinic_id, normalize_staff_actor

MAX_PERMISSION_LENGTH = 64
MAX_PERMISSION_COUNT = 32


class CarlosServiceAuthenticationError(Exception):
    """Raised when an internal request is not authenticated as CARLOS."""


class CarlosStaffPermissionError(Exception):
    """Raised when the authenticated CARLOS provider lacks a portal permission."""


@dataclass(frozen=True)
class StaffPrincipal:
    provider_id: str
    display_name: str
    clinic_id: str
    permissions: frozenset[str]

    def require(self, permission: str) -> None:
        if permission not in self.permissions:
            raise CarlosStaffPermissionError()


def normalize_permissions(value: str) -> frozenset[str]:
    permissions = tuple(
        permission.strip().casefold() for permission in value.split(",") if permission.strip()
    )
    if not permissions or len(permissions) > MAX_PERMISSION_COUNT:
        raise CarlosServiceAuthenticationError()
    if any(len(permission) > MAX_PERMISSION_LENGTH for permission in permissions):
        raise CarlosServiceAuthenticationError()
    return frozenset(permissions)


def matches_any_service_token(supplied_token: str, accepted_tokens: tuple[str, ...]) -> bool:
    """Compare against every accepted token without short-circuiting.

    `any(...)` would stop at the first match, so response time would reveal whether the active or
    the retired token was presented. Accumulating instead keeps the work constant for a fixed
    number of accepted tokens, and each comparison itself stays constant-time.
    """
    matched = False
    for accepted_token in accepted_tokens:
        matched |= compare_digest(accepted_token, supplied_token)
    return matched


def authenticate_carlos_staff(
    settings: Settings,
    *,
    authorization: str | None,
    provider_id: str | None,
    provider_name: str | None,
    clinic_id: str | None,
    permissions: str | None,
) -> StaffPrincipal:
    accepted_tokens = settings.accepted_internal_api_tokens
    scheme, _, supplied_token = (authorization or "").partition(" ")
    if (
        not accepted_tokens
        or scheme.casefold() != "bearer"
        or not supplied_token
        or not matches_any_service_token(supplied_token, accepted_tokens)
        or provider_id is None
        or provider_name is None
        or clinic_id is None
        or permissions is None
    ):
        raise CarlosServiceAuthenticationError()
    try:
        principal = StaffPrincipal(
            provider_id=normalize_staff_actor(provider_id),
            display_name=normalize_staff_actor(provider_name),
            clinic_id=normalize_clinic_id(clinic_id),
            permissions=normalize_permissions(permissions),
        )
        if principal.clinic_id != settings.clinic_id:
            raise CarlosServiceAuthenticationError()
        return principal
    except ValueError as exc:
        raise CarlosServiceAuthenticationError() from exc
