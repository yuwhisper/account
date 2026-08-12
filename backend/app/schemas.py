from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


class UserCreate(BaseModel):
    email: str = Field(min_length=3, max_length=255)
    password: str = Field(min_length=6, max_length=72)

    model_config = {"str_strip_whitespace": True}


class UserLogin(BaseModel):
    email: str = Field(min_length=3, max_length=255)
    password: str = Field(min_length=1, max_length=72)

    model_config = {"str_strip_whitespace": True}


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


class UserOut(BaseModel):
    id: int
    email: str

    model_config = {"from_attributes": True}


class CategoryCreate(BaseModel):
    name: str = Field(min_length=1, max_length=64)
    sort_order: int = 0
    client_id: Optional[str] = None


class CategoryUpdate(BaseModel):
    name: Optional[str] = Field(default=None, min_length=1, max_length=64)
    sort_order: Optional[int] = None
    client_id: Optional[str] = None


class CategoryOut(BaseModel):
    id: int
    name: str
    sort_order: int
    updated_at: datetime
    client_id: Optional[str] = None

    model_config = {"from_attributes": True}


class TransactionIn(BaseModel):
    client_id: str = Field(min_length=1, max_length=64)
    amount_cents: int = Field(ge=0, le=9_000_000_000_000_000)
    merchant: str = Field(default="", max_length=255)
    source: str = Field(default="", max_length=64)
    category_id: Optional[int] = None
    note: str = Field(default="", max_length=512)
    occurred_at: datetime
    updated_at: datetime
    type: str = Field(pattern="^(expense|income)$")


class TransactionPushRequest(BaseModel):
    transactions: list[TransactionIn] = Field(max_length=500)


class TransactionOut(BaseModel):
    id: int
    client_id: str
    amount_cents: int
    merchant: str
    source: str
    category_id: Optional[int] = None
    note: str
    occurred_at: datetime
    updated_at: datetime
    type: str

    model_config = {"from_attributes": True}


class TransactionPullResponse(BaseModel):
    transactions: list[TransactionOut]
    server_time: datetime
