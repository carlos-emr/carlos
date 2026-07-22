from datetime import datetime

from pydantic import BaseModel, Field, field_validator


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


class InviteResponse(BaseModel):
    id: int
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


class InviteTokenResponse(InviteResponse):
    invite_token: str
