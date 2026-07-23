from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

Environment = Literal["development", "staging", "test", "production"]
DEFAULT_DATABASE_URL = "postgresql+psycopg://localhost:5432/carlos_portal"
MIN_PRODUCTION_SECRET_LENGTH = 32
MAX_CLINIC_ID_LENGTH = 64
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
    database_url: str = DEFAULT_DATABASE_URL
    enable_dev_admin: bool = False
    dev_admin_token: SecretStr | None = None
    session_secret: SecretStr | None = None
    identity_proof_secret: SecretStr | None = None
    internal_health_token: SecretStr | None = None
    activation_failure_window_seconds: int = Field(default=3600, ge=60, le=86400)
    activation_max_failures_per_invite: int = Field(default=10, ge=1, le=100)
    activation_max_failures_per_client: int = Field(default=50, ge=1, le=1000)

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

    @field_validator("environment", mode="before")
    @classmethod
    def normalize_environment(cls, value: object) -> object:
        if isinstance(value, str):
            normalized_value = value.strip().lower()
            return ENVIRONMENT_ALIASES.get(normalized_value, normalized_value)
        return value

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
        "internal_health_token",
        "dev_admin_token",
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

        identity_proof_secret_value: str | None = None
        if self.identity_proof_secret is not None:
            identity_proof_secret_value = self.identity_proof_secret.get_secret_value().strip()
            if len(identity_proof_secret_value) < MIN_PRODUCTION_SECRET_LENGTH:
                raise ValueError(
                    "PATIENT_PORTAL_IDENTITY_PROOF_SECRET must be at least "
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
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
