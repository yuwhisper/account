from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


class UserCreate(BaseModel):
    email: str
    password: str = Field(min_length=6)


class UserLogin(BaseModel):
    email: str
    password: str


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
    amount_cents: int
    merchant: str = ""
    source: str = ""
    category_id: Optional[int] = None
    note: str = ""
    occurred_at: datetime
    updated_at: datetime
    type: str = Field(pattern="^(expense|income)$")


class TransactionPushRequest(BaseModel):
    transactions: list[TransactionIn]


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
