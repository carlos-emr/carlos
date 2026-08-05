import json
from email.utils import parseaddr
from functools import lru_cache
from ipaddress import ip_address, ip_network
from typing import Literal
from urllib.parse import parse_qs, urlsplit, urlunsplit
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from carlos_patient_portal.database import (
    DEFAULT_DATABASE_CONNECT_TIMEOUT_SECONDS,
    DEFAULT_DATABASE_LOCK_TIMEOUT_MS,
    DEFAULT_DATABASE_MAX_OVERFLOW,
    DEFAULT_DATABASE_POOL_SIZE,
    DEFAULT_DATABASE_POOL_TIMEOUT_SECONDS,
    DEFAULT_DATABASE_STATEMENT_TIMEOUT_MS,
    DEFAULT_SQLITE_BUSY_TIMEOUT_MS,
)

Environment = Literal["development", "staging", "test", "production"]
TrustedClientIpHeader = Literal["x-forwarded-for", "x-real-ip"]
DEFAULT_DATABASE_URL = "postgresql+psycopg://localhost:5432/carlos_portal"
DEFAULT_DEVELOPMENT_SMTP_FROM_ADDRESS = "carlos-test@openo-dev.local"
MIN_PRODUCTION_SECRET_LENGTH = 32
MAX_CLINIC_ID_LENGTH = 64
# A conservative day count guarantees at least 25 complete calendar years,
# including every leap-day distribution, before an event becomes eligible.
DEFAULT_AUDIT_RETENTION_DAYS = 25 * 366
# Loopback only: a Host header an attacker could poison into a patient-visible link is useless if
# it points back at the patient's own machine. Real deployments add pod IPs/service names here.
DEFAULT_PROBE_ALLOWED_HOSTS = ("127.0.0.1", "localhost", "[::1]")
DEFAULT_CLINIC_ID = "default"
DEFAULT_CLINIC_NAME = "Maple Creek Medical"
ENVIRONMENT_ALIASES = {
    "dev": "development",
    "prod": "production",
}


def parse_unlock_secret_keyring(encoded_keyring: str) -> dict[str, str]:
    try:
        parsed_keyring = json.loads(encoded_keyring)
    except json.JSONDecodeError as exc:
        raise ValueError(
            "PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_KEYRING must be a JSON object"
        ) from exc
    if not isinstance(parsed_keyring, dict) or not parsed_keyring:
        raise ValueError(
            "PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_KEYRING must be a non-empty JSON object"
        )

    normalized_keyring: dict[str, str] = {}
    for key_id, secret in parsed_keyring.items():
        if not isinstance(key_id, str) or not key_id.strip() or len(key_id.strip()) > 64:
            raise ValueError("unlock-secret key IDs must contain 1 to 64 characters")
        if not isinstance(secret, str) or len(secret.strip()) < MIN_PRODUCTION_SECRET_LENGTH:
            raise ValueError(
                "each unlock-secret encryption key must be at least "
                f"{MIN_PRODUCTION_SECRET_LENGTH} characters"
            )
        normalized_keyring[key_id.strip()] = secret.strip()
    return normalized_keyring


class Settings(BaseSettings):
    """Runtime configuration for the patient portal service."""

    service_name: str = "CARLOS Patient Portal"
    environment: Environment = "production"
    clinic_id: str = Field(default=DEFAULT_CLINIC_ID, max_length=MAX_CLINIC_ID_LENGTH)
    clinic_name: str = DEFAULT_CLINIC_NAME
    clinic_timezone: str = Field(default="America/Toronto", min_length=1, max_length=64)
    public_base_url: str | None = Field(default=None, max_length=2048)
    # Container/Kubernetes/load-balancer probes reach the service by pod IP or service name, not by
    # the canonical public host. Without these aliases a correctly configured instance answers
    # 400 "Invalid host header" to its own liveness/readiness probes and is marked dead.
    probe_allowed_hosts: str | None = Field(default=None, max_length=1024)
    database_url: str = DEFAULT_DATABASE_URL
    database_pool_size: int = Field(default=DEFAULT_DATABASE_POOL_SIZE, ge=1, le=100)
    database_max_overflow: int = Field(default=DEFAULT_DATABASE_MAX_OVERFLOW, ge=0, le=100)
    database_pool_timeout_seconds: int = Field(
        default=DEFAULT_DATABASE_POOL_TIMEOUT_SECONDS,
        ge=1,
        le=60,
    )
    database_connect_timeout_seconds: int = Field(
        default=DEFAULT_DATABASE_CONNECT_TIMEOUT_SECONDS,
        ge=1,
        le=60,
    )
    database_statement_timeout_ms: int = Field(
        default=DEFAULT_DATABASE_STATEMENT_TIMEOUT_MS,
        ge=100,
        le=120_000,
    )
    database_lock_timeout_ms: int = Field(
        default=DEFAULT_DATABASE_LOCK_TIMEOUT_MS,
        ge=100,
        le=60_000,
    )
    sqlite_busy_timeout_ms: int = Field(
        default=DEFAULT_SQLITE_BUSY_TIMEOUT_MS,
        ge=100,
        le=60_000,
    )
    enable_dev_admin: bool = False
    dev_admin_token: SecretStr | None = None
    session_secret: SecretStr | None = None
    identity_proof_secret: SecretStr | None = None
    audit_hash_secret: SecretStr | None = None
    unlock_secret_encryption_secret: SecretStr | None = None
    unlock_secret_encryption_keyring: SecretStr | None = None
    unlock_secret_active_key_id: str = Field(default="primary", min_length=1, max_length=64)
    internal_health_token: SecretStr | None = None
    internal_api_token: SecretStr | None = None
    smtp_host: str | None = Field(default=None, max_length=253)
    smtp_port: int = Field(default=25, ge=1, le=65535)
    smtp_from_address: str | None = Field(default=None, max_length=254)
    smtp_starttls: bool = False
    smtp_username: str | None = Field(default=None, max_length=254)
    smtp_password: SecretStr | None = None
    smtp_timeout_seconds: int = Field(default=10, ge=1, le=60)
    sms_webhook_url: str | None = Field(default=None, max_length=2048)
    sms_webhook_token: SecretStr | None = None
    sms_sender_id: str = Field(default="CARLOS", min_length=1, max_length=32)
    sms_timeout_seconds: int = Field(default=10, ge=1, le=60)
    trusted_client_ip_header: TrustedClientIpHeader | None = None
    trusted_proxy_cidrs: str | None = Field(default=None, max_length=2048)
    activation_failure_window_seconds: int = Field(default=3600, ge=60, le=86400)
    activation_max_failures_per_invite: int = Field(default=10, ge=1, le=100)
    activation_max_failures_per_client: int = Field(default=50, ge=1, le=1000)
    require_mfa: bool = True
    auth_max_failed_password_attempts: int = Field(default=10, ge=1, le=1000)
    mfa_max_failed_attempts: int = Field(default=10, ge=1, le=100)
    session_ttl_seconds: int = Field(default=60 * 60, ge=300, le=30 * 24 * 60 * 60)
    session_idle_timeout_seconds: int = Field(default=10 * 60, ge=60, le=24 * 60 * 60)
    mfa_code_ttl_seconds: int = Field(default=10 * 60, ge=60, le=60 * 60)
    mfa_email_resend_cooldown_seconds: int = Field(default=60, ge=30, le=60 * 60)
    mfa_sms_resend_cooldown_seconds: int = Field(default=5 * 60, ge=60, le=60 * 60)
    password_reset_token_ttl_seconds: int = Field(default=60 * 60, ge=300, le=24 * 60 * 60)
    password_reset_request_cooldown_seconds: int = Field(
        default=60,
        ge=30,
        le=60 * 60,
    )
    global_rate_limit_window_seconds: int = Field(default=60, ge=1, le=60 * 60)
    global_rate_limit_max_requests: int = Field(default=300, ge=1, le=10000)
    auth_rate_limit_window_seconds: int = Field(default=60, ge=1, le=60 * 60)
    auth_rate_limit_max_requests: int = Field(default=10, ge=1, le=100)
    rate_limit_max_buckets: int = Field(default=10_000, ge=100, le=1_000_000)
    audit_retention_days: int = Field(
        default=DEFAULT_AUDIT_RETENTION_DAYS,
        ge=DEFAULT_AUDIT_RETENTION_DAYS,
        le=100 * 366,
    )
    maintenance_mode: bool = False
    maintenance_retry_after_seconds: int = Field(default=5 * 60, ge=60, le=24 * 60 * 60)

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="PATIENT_PORTAL_",
        extra="ignore",
    )

    @property
    def is_production(self) -> bool:
        return self.environment == "production"

    @property
    def is_development(self) -> bool:
        return self.environment == "development"

    @property
    def is_dev_admin_enabled(self) -> bool:
        return self.is_development and self.enable_dev_admin

    @property
    def is_internal_api_enabled(self) -> bool:
        return self.internal_api_token is not None

    @property
    def probe_host_aliases(self) -> tuple[str, ...]:
        """Explicitly configured hostnames that health/readiness probes may use."""
        if self.probe_allowed_hosts is None:
            return DEFAULT_PROBE_ALLOWED_HOSTS
        aliases = tuple(
            alias.strip() for alias in self.probe_allowed_hosts.split(",") if alias.strip()
        )
        return aliases or DEFAULT_PROBE_ALLOWED_HOSTS

    @property
    def allowed_hosts(self) -> tuple[str, ...]:
        if self.public_base_url is None:
            return ("127.0.0.1", "localhost", "testserver")
        hostname = urlsplit(self.public_base_url).hostname
        if hostname is None:
            raise ValueError("PATIENT_PORTAL_PUBLIC_BASE_URL must contain a hostname")
        # The canonical public host stays authoritative for patient/FHIR links; the probe aliases
        # only widen which Host headers are accepted, never which URLs are generated.
        allowed = [hostname]
        allowed.extend(alias for alias in self.probe_host_aliases if alias != hostname)
        return tuple(allowed)

    @property
    def resolved_smtp_from_address(self) -> str | None:
        if self.smtp_from_address is not None:
            return self.smtp_from_address
        if self.is_development and self.smtp_host is not None:
            return DEFAULT_DEVELOPMENT_SMTP_FROM_ADDRESS
        return None

    @field_validator("environment", mode="before")
    @classmethod
    def normalize_environment(cls, value: object) -> object:
        if isinstance(value, str):
            normalized_value = value.strip().lower()
            return ENVIRONMENT_ALIASES.get(normalized_value, normalized_value)
        return value

    @field_validator("trusted_client_ip_header", mode="before")
    @classmethod
    def normalize_trusted_client_ip_header(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip().lower()
        return value

    @field_validator(
        "public_base_url",
        "smtp_host",
        "smtp_from_address",
        "smtp_username",
        "sms_webhook_url",
        "sms_sender_id",
        "trusted_proxy_cidrs",
        "unlock_secret_active_key_id",
        "service_name",
        "clinic_name",
        mode="before",
    )
    @classmethod
    def normalize_optional_smtp_value(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip() or None
        return value

    @field_validator("service_name", "clinic_name", "smtp_host", "sms_sender_id")
    @classmethod
    def reject_header_control_characters(cls, value: str | None) -> str | None:
        if value is not None and any(
            ord(character) < 32 or ord(character) == 127 for character in value
        ):
            raise ValueError("configuration text must not contain control characters")
        return value

    @field_validator("smtp_from_address")
    @classmethod
    def validate_smtp_from_address(cls, value: str | None) -> str | None:
        if value is None:
            return None
        display_name, parsed_address = parseaddr(value)
        if display_name or parsed_address != value or "@" not in parsed_address:
            raise ValueError(
                "PATIENT_PORTAL_SMTP_FROM_ADDRESS must be one mailbox address "
                "without a display name"
            )
        local_part, _, domain = parsed_address.rpartition("@")
        if (
            not local_part
            or not domain
            or domain.startswith(".")
            or domain.endswith(".")
            or "." not in domain
        ):
            raise ValueError("PATIENT_PORTAL_SMTP_FROM_ADDRESS must be a valid mailbox address")
        return parsed_address

    @field_validator("public_base_url")
    @classmethod
    def validate_public_base_url(cls, value: str | None) -> str | None:
        if value is None:
            return None
        parsed_url = urlsplit(value)
        if (
            parsed_url.scheme not in {"http", "https"}
            or not parsed_url.netloc
            or parsed_url.username is not None
            or parsed_url.password is not None
            or parsed_url.query
            or parsed_url.fragment
        ):
            raise ValueError(
                "PATIENT_PORTAL_PUBLIC_BASE_URL must be an HTTP(S) origin without "
                "credentials, query, or fragment"
            )
        normalized_path = parsed_url.path.rstrip("/")
        return urlunsplit(
            (
                parsed_url.scheme,
                parsed_url.netloc,
                normalized_path,
                "",
                "",
            )
        )

    @field_validator("sms_webhook_url")
    @classmethod
    def validate_sms_webhook_url(cls, value: str | None) -> str | None:
        if value is None:
            return None
        parsed_url = urlsplit(value)
        if (
            parsed_url.scheme not in {"http", "https"}
            or not parsed_url.netloc
            or parsed_url.username is not None
            or parsed_url.password is not None
            or parsed_url.query
            or parsed_url.fragment
        ):
            raise ValueError(
                "PATIENT_PORTAL_SMS_WEBHOOK_URL must be an HTTP(S) URL without "
                "credentials, query, or fragment"
            )
        return value

    @field_validator("clinic_id")
    @classmethod
    def normalize_clinic_id(cls, value: str) -> str:
        clinic_id = value.strip()
        if not clinic_id:
            raise ValueError("PATIENT_PORTAL_CLINIC_ID must not be blank")
        return clinic_id

    @field_validator("clinic_timezone")
    @classmethod
    def validate_clinic_timezone(cls, value: str) -> str:
        timezone_name = value.strip()
        try:
            ZoneInfo(timezone_name)
        except ZoneInfoNotFoundError as exc:
            raise ValueError("PATIENT_PORTAL_CLINIC_TIMEZONE must be an IANA timezone") from exc
        return timezone_name

    @field_validator(
        "session_secret",
        "identity_proof_secret",
        "audit_hash_secret",
        "unlock_secret_encryption_secret",
        "unlock_secret_encryption_keyring",
        "internal_health_token",
        "internal_api_token",
        "dev_admin_token",
        "smtp_password",
        "sms_webhook_token",
        mode="before",
    )
    @classmethod
    def strip_secret_value(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value

    def secret_value(self, field_name: str) -> str | None:
        value = getattr(self, field_name)
        if value is None:
            return None
        return value.get_secret_value().strip()

    @property
    def resolved_unlock_secret_keyring(self) -> dict[str, str]:
        encoded_keyring = self.secret_value("unlock_secret_encryption_keyring")
        if encoded_keyring is None:
            legacy_secret = self.secret_value("unlock_secret_encryption_secret")
            if legacy_secret is not None and self.unlock_secret_active_key_id != "primary":
                raise ValueError(
                    "PATIENT_PORTAL_UNLOCK_SECRET_ACTIVE_KEY_ID must be primary when "
                    "using PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET"
                )
            return {"primary": legacy_secret} if legacy_secret is not None else {}
        if self.secret_value("unlock_secret_encryption_secret") is not None:
            raise ValueError(
                "configure either PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET or "
                "PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_KEYRING, not both"
            )
        normalized_keyring = parse_unlock_secret_keyring(encoded_keyring)
        if self.unlock_secret_active_key_id not in normalized_keyring:
            raise ValueError("PATIENT_PORTAL_UNLOCK_SECRET_ACTIVE_KEY_ID must exist in the keyring")
        return normalized_keyring

    def validate_secret_policy(self) -> None:
        secret_fields = {
            "identity_proof_secret": "PATIENT_PORTAL_IDENTITY_PROOF_SECRET",
            "audit_hash_secret": "PATIENT_PORTAL_AUDIT_HASH_SECRET",
            "unlock_secret_encryption_secret": ("PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET"),
            "internal_health_token": "PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN",
            "internal_api_token": "PATIENT_PORTAL_INTERNAL_API_TOKEN",
            "dev_admin_token": "PATIENT_PORTAL_DEV_ADMIN_TOKEN",
            "sms_webhook_token": "PATIENT_PORTAL_SMS_WEBHOOK_TOKEN",
        }
        for field_name, environment_name in secret_fields.items():
            secret_value = self.secret_value(field_name)
            if secret_value is not None and len(secret_value) < MIN_PRODUCTION_SECRET_LENGTH:
                raise ValueError(
                    f"{environment_name} must be at least "
                    f"{MIN_PRODUCTION_SECRET_LENGTH} characters when set"
                )
        _ = self.resolved_unlock_secret_keyring

        session_secret_value = self.secret_value("session_secret")
        if self.session_secret is not None and not session_secret_value:
            raise ValueError("PATIENT_PORTAL_SESSION_SECRET must not be blank")
        if not self.is_development and session_secret_value is None:
            raise ValueError("PATIENT_PORTAL_SESSION_SECRET must be set outside development")
        if (
            not self.is_development
            and session_secret_value is not None
            and len(session_secret_value) < MIN_PRODUCTION_SECRET_LENGTH
        ):
            raise ValueError(
                "PATIENT_PORTAL_SESSION_SECRET must be a value with at least "
                f"{MIN_PRODUCTION_SECRET_LENGTH} characters outside development"
            )
        if not self.is_development:
            secret_domains: dict[str, str] = {}
            configured_secrets = {
                "PATIENT_PORTAL_SESSION_SECRET": session_secret_value,
                "PATIENT_PORTAL_IDENTITY_PROOF_SECRET": self.secret_value("identity_proof_secret"),
                "PATIENT_PORTAL_AUDIT_HASH_SECRET": self.secret_value("audit_hash_secret"),
                "PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN": self.secret_value("internal_health_token"),
                "PATIENT_PORTAL_INTERNAL_API_TOKEN": self.secret_value("internal_api_token"),
                "PATIENT_PORTAL_SMS_WEBHOOK_TOKEN": self.secret_value("sms_webhook_token"),
            }
            configured_secrets.update(
                {
                    f"PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_KEYRING[{key_id}]": value
                    for key_id, value in self.resolved_unlock_secret_keyring.items()
                }
            )
            for field_name, secret_value in configured_secrets.items():
                if secret_value is None:
                    continue
                reused_by = secret_domains.get(secret_value)
                if reused_by is not None:
                    raise ValueError(
                        f"{field_name} must not reuse the value configured for {reused_by}"
                    )
                secret_domains[secret_value] = field_name

    def validate_admin_and_mfa_policy(self) -> None:
        if self.is_dev_admin_enabled and self.dev_admin_token is None:
            raise ValueError(
                "PATIENT_PORTAL_DEV_ADMIN_TOKEN must be set when development admin API is enabled"
            )
        if self.is_production and not self.require_mfa:
            raise ValueError("PATIENT_PORTAL_REQUIRE_MFA must stay enabled in production")

    def validate_smtp_policy(self) -> None:
        self.validate_smtp_credentials()
        self.validate_smtp_sender()
        self.validate_smtp_transport()

    def validate_smtp_credentials(self) -> None:
        smtp_password_value = self.secret_value("smtp_password")
        if (self.smtp_username is None) != (smtp_password_value is None):
            raise ValueError(
                "PATIENT_PORTAL_SMTP_USERNAME and PATIENT_PORTAL_SMTP_PASSWORD "
                "must be configured together"
            )

    def validate_smtp_sender(self) -> None:
        if self.smtp_host is None:
            if self.smtp_from_address is not None:
                raise ValueError(
                    "PATIENT_PORTAL_SMTP_HOST is required when "
                    "PATIENT_PORTAL_SMTP_FROM_ADDRESS is set"
                )
            if self.smtp_username is not None:
                raise ValueError(
                    "PATIENT_PORTAL_SMTP_HOST is required when SMTP credentials are set"
                )
        elif self.resolved_smtp_from_address is None:
            raise ValueError(
                "PATIENT_PORTAL_SMTP_FROM_ADDRESS is required when PATIENT_PORTAL_SMTP_HOST is set"
            )

    def validate_smtp_transport(self) -> None:
        if not self.is_development and self.smtp_host is not None:
            if not self.smtp_starttls:
                raise ValueError("PATIENT_PORTAL_SMTP_STARTTLS must be enabled outside development")
            if self.public_base_url is None:
                raise ValueError(
                    "PATIENT_PORTAL_PUBLIC_BASE_URL is required when SMTP is configured "
                    "outside development"
                )
            if not self.public_base_url.startswith("https://"):
                raise ValueError(
                    "PATIENT_PORTAL_PUBLIC_BASE_URL must use HTTPS outside development"
                )
        if self.is_production and self.smtp_host is None:
            raise ValueError("PATIENT_PORTAL_SMTP_HOST must be set in production")

    def validate_sms_policy(self) -> None:
        if (self.sms_webhook_url is None) != (self.sms_webhook_token is None):
            raise ValueError(
                "PATIENT_PORTAL_SMS_WEBHOOK_URL and PATIENT_PORTAL_SMS_WEBHOOK_TOKEN "
                "must be configured together"
            )
        if (
            not self.is_development
            and self.sms_webhook_url is not None
            and not self.sms_webhook_url.startswith("https://")
        ):
            raise ValueError("PATIENT_PORTAL_SMS_WEBHOOK_URL must use HTTPS outside development")
        if self.is_production and self.sms_webhook_url is None:
            raise ValueError("PATIENT_PORTAL_SMS_WEBHOOK_URL must be set in production")

    def validate_proxy_policy(self) -> None:
        if (self.trusted_client_ip_header is None) != (self.trusted_proxy_cidrs is None):
            raise ValueError(
                "PATIENT_PORTAL_TRUSTED_CLIENT_IP_HEADER and "
                "PATIENT_PORTAL_TRUSTED_PROXY_CIDRS must be configured together"
            )
        if self.trusted_proxy_cidrs is None:
            return
        try:
            parsed_networks = tuple(
                ip_network(value.strip(), strict=False)
                for value in self.trusted_proxy_cidrs.split(",")
                if value.strip()
            )
        except ValueError as exc:
            raise ValueError(
                "PATIENT_PORTAL_TRUSTED_PROXY_CIDRS must contain valid comma-separated CIDRs"
            ) from exc
        if not parsed_networks:
            raise ValueError("PATIENT_PORTAL_TRUSTED_PROXY_CIDRS must contain at least one CIDR")

    def validate_database_transport_policy(self) -> None:
        if not self.is_production:
            return
        parsed_url = urlsplit(self.database_url)
        if parsed_url.scheme != "postgresql+psycopg":
            raise ValueError("production PATIENT_PORTAL_DATABASE_URL must use postgresql+psycopg")
        database_host = parsed_url.hostname
        if database_host is None or database_host.casefold() == "localhost":
            return
        try:
            database_address = ip_address(database_host)
        except ValueError:
            database_address = None
        if database_address is not None and database_address.is_loopback:
            return
        ssl_mode = parse_qs(parsed_url.query).get("sslmode", [None])[-1]
        if ssl_mode != "verify-full":
            raise ValueError(
                "remote production PostgreSQL connections must set sslmode=verify-full "
                "in PATIENT_PORTAL_DATABASE_URL"
            )

    def validate_clinic_policy(self) -> None:
        if self.is_development:
            return
        if self.clinic_id == DEFAULT_CLINIC_ID:
            raise ValueError(
                "PATIENT_PORTAL_CLINIC_ID must be explicitly configured outside development"
            )
        if self.clinic_name == DEFAULT_CLINIC_NAME:
            raise ValueError(
                "PATIENT_PORTAL_CLINIC_NAME must be explicitly configured outside development"
            )

    def validate_session_policy(self) -> None:
        if self.session_idle_timeout_seconds > self.session_ttl_seconds:
            raise ValueError(
                "PATIENT_PORTAL_SESSION_IDLE_TIMEOUT_SECONDS must not exceed "
                "PATIENT_PORTAL_SESSION_TTL_SECONDS"
            )

    def validate_required_production_services(self) -> None:
        if self.is_development:
            return
        required_secrets = {
            "internal_health_token": "PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN",
            "identity_proof_secret": "PATIENT_PORTAL_IDENTITY_PROOF_SECRET",
            "audit_hash_secret": "PATIENT_PORTAL_AUDIT_HASH_SECRET",
        }
        for field_name, environment_name in required_secrets.items():
            if getattr(self, field_name) is None:
                raise ValueError(f"{environment_name} must be set outside development")
        if not self.resolved_unlock_secret_keyring:
            raise ValueError(
                "PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET or "
                "PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_KEYRING must be set "
                "outside development"
            )
        if self.is_production and self.internal_api_token is None:
            raise ValueError("PATIENT_PORTAL_INTERNAL_API_TOKEN must be set in production")

    @model_validator(mode="after")
    def reject_unsafe_runtime_policy(self) -> "Settings":
        self.validate_secret_policy()
        self.validate_required_production_services()
        self.validate_admin_and_mfa_policy()
        self.validate_smtp_policy()
        self.validate_sms_policy()
        self.validate_proxy_policy()
        self.validate_database_transport_policy()
        self.validate_clinic_policy()
        self.validate_session_policy()
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
