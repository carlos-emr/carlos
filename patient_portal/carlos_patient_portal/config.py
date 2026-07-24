from functools import lru_cache
from typing import Literal
from urllib.parse import urlsplit, urlunsplit

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

Environment = Literal["development", "staging", "test", "production"]
TrustedClientIpHeader = Literal["x-forwarded-for", "x-real-ip"]
DEFAULT_DATABASE_URL = "postgresql+psycopg://localhost:5432/carlos_portal"
DEFAULT_DEVELOPMENT_SMTP_FROM_ADDRESS = "carlos-test@openo-dev.local"
MIN_PRODUCTION_SECRET_LENGTH = 32
MAX_CLINIC_ID_LENGTH = 64
DEFAULT_AUDIT_RETENTION_DAYS = 25 * 365
ENVIRONMENT_ALIASES = {
    "dev": "development",
    "prod": "production",
}


class Settings(BaseSettings):
    """Runtime configuration for the patient portal service."""

    service_name: str = "CARLOS Patient Portal"
    environment: Environment = "production"
    clinic_id: str = Field(default="default", max_length=MAX_CLINIC_ID_LENGTH)
    clinic_name: str = "Maple Creek Medical"
    public_base_url: str | None = Field(default=None, max_length=2048)
    database_url: str = DEFAULT_DATABASE_URL
    enable_dev_admin: bool = False
    dev_admin_token: SecretStr | None = None
    session_secret: SecretStr | None = None
    identity_proof_secret: SecretStr | None = None
    audit_hash_secret: SecretStr | None = None
    unlock_secret_encryption_secret: SecretStr | None = None
    internal_health_token: SecretStr | None = None
    smtp_host: str | None = Field(default=None, max_length=253)
    smtp_port: int = Field(default=25, ge=1, le=65535)
    smtp_from_address: str | None = Field(default=None, max_length=254)
    smtp_starttls: bool = False
    smtp_username: str | None = Field(default=None, max_length=254)
    smtp_password: SecretStr | None = None
    smtp_timeout_seconds: int = Field(default=10, ge=1, le=60)
    trusted_client_ip_header: TrustedClientIpHeader | None = None
    activation_failure_window_seconds: int = Field(default=3600, ge=60, le=86400)
    activation_max_failures_per_invite: int = Field(default=10, ge=1, le=100)
    activation_max_failures_per_client: int = Field(default=50, ge=1, le=1000)
    require_mfa: bool = True
    auth_max_failed_password_attempts: int = Field(default=50, ge=1, le=1000)
    mfa_max_failed_attempts: int = Field(default=10, ge=1, le=100)
    session_ttl_seconds: int = Field(default=12 * 60 * 60, ge=300, le=30 * 24 * 60 * 60)
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
    audit_retention_days: int = Field(default=DEFAULT_AUDIT_RETENTION_DAYS, ge=365, le=100 * 365)
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
        mode="before",
    )
    @classmethod
    def normalize_optional_smtp_value(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip() or None
        return value

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

    @field_validator("clinic_id")
    @classmethod
    def normalize_clinic_id(cls, value: str) -> str:
        clinic_id = value.strip()
        if not clinic_id:
            raise ValueError("PATIENT_PORTAL_CLINIC_ID must not be blank")
        return clinic_id

    @field_validator(
        "session_secret",
        "identity_proof_secret",
        "audit_hash_secret",
        "unlock_secret_encryption_secret",
        "internal_health_token",
        "dev_admin_token",
        "smtp_password",
        mode="before",
    )
    @classmethod
    def strip_secret_value(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value

    @model_validator(mode="after")
    def reject_unsafe_production_defaults(self) -> "Settings":
        session_secret_value: str | None = None
        if self.session_secret is not None:
            session_secret_value = self.session_secret.get_secret_value().strip()
            if not session_secret_value:
                raise ValueError("PATIENT_PORTAL_SESSION_SECRET must not be blank")

        if self.internal_health_token is not None:
            internal_health_token_value = self.internal_health_token.get_secret_value().strip()
            if len(internal_health_token_value) < MIN_PRODUCTION_SECRET_LENGTH:
                raise ValueError(
                    "PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN must be at least "
                    f"{MIN_PRODUCTION_SECRET_LENGTH} characters when set"
                )

        if self.dev_admin_token is not None:
            dev_admin_token_value = self.dev_admin_token.get_secret_value().strip()
            if len(dev_admin_token_value) < MIN_PRODUCTION_SECRET_LENGTH:
                raise ValueError(
                    "PATIENT_PORTAL_DEV_ADMIN_TOKEN must be at least "
                    f"{MIN_PRODUCTION_SECRET_LENGTH} characters when set"
                )

        if self.is_dev_admin_enabled and self.dev_admin_token is None:
            raise ValueError(
                "PATIENT_PORTAL_DEV_ADMIN_TOKEN must be set when development admin API is enabled"
            )

        if self.is_production and not self.require_mfa:
            raise ValueError("PATIENT_PORTAL_REQUIRE_MFA must stay enabled in production")

        smtp_password_value = (
            self.smtp_password.get_secret_value().strip()
            if self.smtp_password is not None
            else None
        )
        if (self.smtp_username is None) != (smtp_password_value is None):
            raise ValueError(
                "PATIENT_PORTAL_SMTP_USERNAME and PATIENT_PORTAL_SMTP_PASSWORD "
                "must be configured together"
            )
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
                "PATIENT_PORTAL_SMTP_FROM_ADDRESS is required when "
                "PATIENT_PORTAL_SMTP_HOST is set"
            )
        if not self.is_development and self.smtp_host is not None:
            if self.public_base_url is None:
                raise ValueError(
                    "PATIENT_PORTAL_PUBLIC_BASE_URL is required when SMTP is configured "
                    "outside development"
                )
            if not self.public_base_url.startswith("https://"):
                raise ValueError(
                    "PATIENT_PORTAL_PUBLIC_BASE_URL must use HTTPS outside development"
                )

        identity_proof_secret_value: str | None = None
        if self.identity_proof_secret is not None:
            identity_proof_secret_value = self.identity_proof_secret.get_secret_value().strip()
            if len(identity_proof_secret_value) < MIN_PRODUCTION_SECRET_LENGTH:
                raise ValueError(
                    "PATIENT_PORTAL_IDENTITY_PROOF_SECRET must be at least "
                    f"{MIN_PRODUCTION_SECRET_LENGTH} characters when set"
                )

        audit_hash_secret_value: str | None = None
        if self.audit_hash_secret is not None:
            audit_hash_secret_value = self.audit_hash_secret.get_secret_value().strip()
            if len(audit_hash_secret_value) < MIN_PRODUCTION_SECRET_LENGTH:
                raise ValueError(
                    "PATIENT_PORTAL_AUDIT_HASH_SECRET must be at least "
                    f"{MIN_PRODUCTION_SECRET_LENGTH} characters when set"
                )

        unlock_secret_encryption_secret_value: str | None = None
        if self.unlock_secret_encryption_secret is not None:
            unlock_secret_encryption_secret_value = (
                self.unlock_secret_encryption_secret.get_secret_value().strip()
            )
            if len(unlock_secret_encryption_secret_value) < MIN_PRODUCTION_SECRET_LENGTH:
                raise ValueError(
                    "PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET must be at least "
                    f"{MIN_PRODUCTION_SECRET_LENGTH} characters when set"
                )

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
        if not self.is_development and self.internal_health_token is None:
            raise ValueError(
                "PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN must be set outside development"
            )
        if not self.is_development and identity_proof_secret_value is None:
            raise ValueError(
                "PATIENT_PORTAL_IDENTITY_PROOF_SECRET must be set outside development"
            )
        if not self.is_development and audit_hash_secret_value is None:
            raise ValueError("PATIENT_PORTAL_AUDIT_HASH_SECRET must be set outside development")
        if not self.is_development and unlock_secret_encryption_secret_value is None:
            raise ValueError(
                "PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET must be set outside development"
            )
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
