from functools import lru_cache

from pydantic import SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration for the patient portal service."""

    service_name: str = "CARLOS Patient Portal"
    environment: str = "development"
    clinic_name: str = "Maple Creek Medical"
    database_url: str = "postgresql+psycopg://portal:portal@localhost:5432/carlos_portal"
    session_secret: SecretStr = SecretStr("change-me-in-development")
    internal_health_token: SecretStr | None = None

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="PATIENT_PORTAL_",
        extra="ignore",
    )

    @property
    def is_production(self) -> bool:
        return self.environment.lower() == "production"

    @model_validator(mode="after")
    def reject_unsafe_production_defaults(self) -> "Settings":
        is_default_secret = self.session_secret.get_secret_value() == "change-me-in-development"
        if self.is_production and is_default_secret:
            raise ValueError("PATIENT_PORTAL_SESSION_SECRET must be set in production")
        if self.is_production and self.internal_health_token is None:
            raise ValueError("PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN must be set in production")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
