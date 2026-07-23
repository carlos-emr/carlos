from datetime import date, datetime

from pydantic import BaseModel, Field, field_validator, model_validator

from carlos_patient_portal.credentials import (
    MAX_PASSWORD_LENGTH,
    validate_password,
    validate_username,
)
from carlos_patient_portal.identity import (
    MAX_HEALTH_CARD_NUMBER_LENGTH,
    normalize_date_of_birth,
    normalize_email,
    normalize_health_card_number,
)
from carlos_patient_portal.models import MAX_EMAIL_LENGTH


class StaffActorRequest(BaseModel):
    actor: str = Field(min_length=1, max_length=128)

    @field_validator("actor")
    @classmethod
    def strip_actor(cls, value: str) -> str:
        actor = value.strip()
        if not actor:
            raise ValueError("actor must not be blank")
        return actor


class InviteCreateRequest(StaffActorRequest):
    demographic_no: int = Field(gt=0)
    email: str = Field(min_length=1, max_length=MAX_EMAIL_LENGTH)
    date_of_birth: date
    health_card_number: str = Field(
        min_length=1,
        max_length=MAX_HEALTH_CARD_NUMBER_LENGTH,
    )

    @model_validator(mode="after")
    def validate_identity_proof(self) -> "InviteCreateRequest":
        normalize_email(self.email)
        normalize_date_of_birth(self.date_of_birth)
        normalize_health_card_number(self.health_card_number)
        return self


class InviteResponse(BaseModel):
    id: int
    clinic_id: str
    demographic_no: int
    status: str
    created_by: str
    created_at: datetime
    updated_at: datetime
    sent_count: int
    last_sent_at: datetime
    last_sent_by: str
    expires_at: datetime
    revoked_at: datetime | None
    revoked_by: str | None
    has_identity_proof: bool
    accepted_at: datetime | None
    accepted_account_id: int | None


class InviteTokenResponse(InviteResponse):
    invite_token: str


class ActivationRequest(BaseModel):
    invite_code: str = Field(min_length=1)
    email: str = Field(min_length=1, max_length=MAX_EMAIL_LENGTH)
    date_of_birth: date
    health_card_number: str = Field(min_length=1, max_length=MAX_HEALTH_CARD_NUMBER_LENGTH)
    username: str = Field(min_length=1)
    password: str = Field(min_length=1, max_length=MAX_PASSWORD_LENGTH, repr=False)

    @field_validator("invite_code")
    @classmethod
    def strip_invite_code(cls, value: str) -> str:
        invite_code = value.strip()
        if not invite_code:
            raise ValueError("invite_code must not be blank")
        return invite_code

    @field_validator("email")
    @classmethod
    def validate_email(cls, value: str) -> str:
        return normalize_email(value)

    @field_validator("date_of_birth")
    @classmethod
    def validate_date_of_birth(cls, value: date) -> date:
        normalize_date_of_birth(value)
        return value

    @field_validator("health_card_number")
    @classmethod
    def validate_health_card_number(cls, value: str) -> str:
        return normalize_health_card_number(value)

    @field_validator("username")
    @classmethod
    def validate_activation_username(cls, value: str) -> str:
        return validate_username(value)

    @field_validator("password")
    @classmethod
    def validate_activation_password(cls, value: str) -> str:
        return validate_password(value)


class ActivationResponse(BaseModel):
    status: str
    username: str
