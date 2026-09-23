from datetime import datetime, timezone

from sqlalchemy import DateTime, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )


class Category(Base):
    __tablename__ = "categories"
    __table_args__ = (UniqueConstraint("user_id", "client_id", name="uq_category_user_client"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), nullable=False, index=True)
    name: Mapped[str] = mapped_column(String(64), nullable=False)
    sort_order: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
        nullable=False,
    )
    # Avoid Mapped[Optional[str]] — SQLAlchemy 2.0.36 + Python 3.14 Union bug
    client_id = mapped_column(String(64), nullable=True)


class Transaction(Base):
    __tablename__ = "transactions"
    __table_args__ = (UniqueConstraint("user_id", "client_id", name="uq_transaction_user_client"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), nullable=False, index=True)
    client_id: Mapped[str] = mapped_column(String(64), nullable=False)
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    merchant: Mapped[str] = mapped_column(String(255), nullable=False, default="")
    source: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    # Avoid Mapped[Optional[...]] — SQLAlchemy 2.0.36 + Python 3.14 Union bug
    category_id = mapped_column(ForeignKey("categories.id"), nullable=True)
    note = mapped_column(String(512), nullable=False, default="")
    occurred_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, index=True
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, index=True
    )
    type: Mapped[str] = mapped_column(String(16), nullable=False)  # expense | income
