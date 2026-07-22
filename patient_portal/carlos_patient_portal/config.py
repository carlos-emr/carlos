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

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="PATIENT_PORTAL_",
        extra="ignore",
    )

    @property
    def is_production(self) -> bool:
        return self.environment.lower() == "production"

    @model_validator(mode="after")
    def reject_development_secret_in_production(self) -> "Settings":
        is_default_secret = self.session_secret.get_secret_value() == "change-me-in-development"
        if self.is_production and is_default_secret:
            raise ValueError("PATIENT_PORTAL_SESSION_SECRET must be set in production")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
