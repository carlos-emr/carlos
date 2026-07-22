from functools import lru_cache
from typing import Literal

from pydantic import SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

Environment = Literal["development", "staging", "test", "production"]
DEFAULT_SESSION_SECRET = "change-me-in-development"
MIN_PRODUCTION_SECRET_LENGTH = 32
ENVIRONMENT_ALIASES = {
    "dev": "development",
    "prod": "production",
}


class Settings(BaseSettings):
    """Runtime configuration for the patient portal service."""

    service_name: str = "CARLOS Patient Portal"
    environment: Environment = "development"
    clinic_name: str = "Maple Creek Medical"
    database_url: str = "postgresql+psycopg://portal:portal@localhost:5432/carlos_portal"
    session_secret: SecretStr = SecretStr(DEFAULT_SESSION_SECRET)
    internal_health_token: SecretStr | None = None

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

    @field_validator("environment", mode="before")
    @classmethod
    def normalize_environment(cls, value: object) -> object:
        if isinstance(value, str):
            normalized_value = value.strip().lower()
            return ENVIRONMENT_ALIASES.get(normalized_value, normalized_value)
        return value

    @field_validator("session_secret", "internal_health_token", mode="before")
    @classmethod
    def strip_secret_value(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value

    @model_validator(mode="after")
    def reject_unsafe_production_defaults(self) -> "Settings":
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

        is_default_secret = session_secret_value == DEFAULT_SESSION_SECRET
        is_short_secret = len(session_secret_value) < MIN_PRODUCTION_SECRET_LENGTH
        if self.is_production and (is_default_secret or is_short_secret):
            raise ValueError(
                "PATIENT_PORTAL_SESSION_SECRET must be a non-default value with at least "
                f"{MIN_PRODUCTION_SECRET_LENGTH} characters in production"
            )
        if not self.is_development and self.internal_health_token is None:
            raise ValueError(
                "PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN must be set outside development"
            )
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
