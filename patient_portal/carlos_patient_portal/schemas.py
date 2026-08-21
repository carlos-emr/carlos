# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project

from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from carlos_patient_portal.auth import normalize_phone_number
from carlos_patient_portal.credentials import (
    MAX_PASSWORD_LENGTH,
    validate_password,
    validate_username,
)
from carlos_patient_portal.identity import (
    MAX_HEALTH_CARD_NUMBER_LENGTH,
    MIN_HEALTH_CARD_NUMBER_LENGTH,
    normalize_date_of_birth,
    normalize_email,
    normalize_health_card_number,
)
from carlos_patient_portal.models import MAX_EMAIL_LENGTH

MfaDeliveryMethod = Literal["email", "sms"]


class InviteCreateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    demographic_no: int = Field(gt=0)
    email: str = Field(min_length=1, max_length=MAX_EMAIL_LENGTH)
    date_of_birth: date
    health_card_number: str = Field(
        min_length=MIN_HEALTH_CARD_NUMBER_LENGTH,
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
    issued_count: int
    last_issued_at: datetime
    last_issued_by: str
    expires_at: datetime
    revoked_at: datetime | None
    revoked_by: str | None
    has_identity_proof: bool
    accepted_at: datetime | None
    accepted_account_id: int | None
    supersedes_invite_id: int | None


class InviteTokenResponse(InviteResponse):
    invite_token: str


class ActivationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    invite_code: str = Field(min_length=1)
    email: str = Field(min_length=1, max_length=MAX_EMAIL_LENGTH)
    date_of_birth: date
    health_card_number: str = Field(
        min_length=MIN_HEALTH_CARD_NUMBER_LENGTH,
        max_length=MAX_HEALTH_CARD_NUMBER_LENGTH,
    )
    username: str = Field(min_length=1)
    password: str = Field(min_length=1, max_length=MAX_PASSWORD_LENGTH, repr=False)
    mfa_delivery_method: MfaDeliveryMethod = "email"
    phone_number: str | None = Field(default=None, max_length=32)

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

    @model_validator(mode="after")
    def validate_mfa_enrollment(self) -> "ActivationRequest":
        normalized_phone = normalize_phone_number(self.phone_number)
        if self.mfa_delivery_method == "sms" and normalized_phone is None:
            raise ValueError("phone_number is required for SMS MFA")
        self.phone_number = normalized_phone
        return self


class ActivationResponse(BaseModel):
    status: str
    username: str


class LoginRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    username: str = Field(min_length=1)
    password: str = Field(min_length=1, max_length=MAX_PASSWORD_LENGTH, repr=False)
    mfa_delivery_method: MfaDeliveryMethod | None = None


class LoginResponse(BaseModel):
    status: Literal["mfa_required", "signed_in", "password_reset_required"]
    mfa_challenge_token: str | None = None
    mfa_delivery_method: MfaDeliveryMethod | None = None
    session_token: str | None = None
    development_mfa_code: str | None = None


class MfaVerifyRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    mfa_challenge_token: str = Field(min_length=1)
    code: str = Field(min_length=1, max_length=16, repr=False)


class MfaVerifyResponse(BaseModel):
    status: Literal["signed_in"]
    session_token: str


class MfaResendRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    mfa_challenge_token: str = Field(min_length=1)
    mfa_delivery_method: MfaDeliveryMethod


class MfaChallengeResponse(BaseModel):
    status: Literal["mfa_required"]
    mfa_challenge_token: str
    mfa_delivery_method: MfaDeliveryMethod
    development_mfa_code: str | None = None


class PasswordResetRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    username: str = Field(min_length=1)
    email: str = Field(min_length=1, max_length=MAX_EMAIL_LENGTH)


class PasswordResetRequestResponse(BaseModel):
    status: Literal["reset_requested"]
    development_reset_token: str | None = None


class PasswordResetCompleteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    reset_token: str = Field(min_length=1)
    new_password: str = Field(min_length=1, max_length=MAX_PASSWORD_LENGTH, repr=False)

    @field_validator("new_password")
    @classmethod
    def validate_new_password(cls, value: str) -> str:
        return validate_password(value)


class PasswordResetCompleteResponse(BaseModel):
    status: Literal["password_reset"]
    username: str


class SessionResponse(BaseModel):
    status: Literal["authenticated"]
    username: str
    clinic_id: str
    demographic_no: int


class LogoutResponse(BaseModel):
    status: Literal["logged_out"]


class EmailPasswordRecordResponse(BaseModel):
    id: int
    label: str | None
    source_reference: str | None
    created_at: datetime
    updated_at: datetime
    last_viewed_at: datetime | None


class EmailPasswordListResponse(BaseModel):
    items: list[EmailPasswordRecordResponse]
    limit: int
    offset: int


class EmailPasswordSecretResponse(EmailPasswordRecordResponse):
    passphrase: str = Field(repr=False)


class AccountAdminResponse(BaseModel):
    id: int
    clinic_id: str
    demographic_no: int
    username: str
    email: str
    locked_at: datetime | None
    force_password_reset: bool
    failed_login_count: int
