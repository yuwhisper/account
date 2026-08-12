from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.auth import get_current_user
from app.db import get_db
from app.models import Category, Transaction, User
from app.schemas import (
    TransactionIn,
    TransactionOut,
    TransactionPullResponse,
    TransactionPushRequest,
)

router = APIRouter(prefix="/api/transactions", tags=["transactions"])


def _ensure_aware(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt


def _validate_category(db: Session, user_id: int, category_id: int | None) -> None:
    if category_id is None:
        return
    cat = (
        db.query(Category)
        .filter(Category.id == category_id, Category.user_id == user_id)
        .first()
    )
    if cat is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="category_id not found for this user",
        )


def _apply_fields(tx: Transaction, item: TransactionIn) -> None:
    tx.amount_cents = item.amount_cents
    tx.merchant = item.merchant
    tx.source = item.source
    tx.category_id = item.category_id
    tx.note = item.note
    tx.occurred_at = item.occurred_at
    tx.updated_at = item.updated_at
    tx.type = item.type


@router.get("", response_model=list[TransactionOut])
def list_transactions(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> list[Transaction]:
    return (
        db.query(Transaction)
        .filter(Transaction.user_id == user.id)
        .order_by(Transaction.occurred_at.desc(), Transaction.id.desc())
        .all()
    )


@router.post("/sync/push", response_model=dict)
def push_transactions(
    payload: TransactionPushRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> dict:
    for item in payload.transactions:
        _validate_category(db, user.id, item.category_id)
        existing = (
            db.query(Transaction)
            .filter(Transaction.user_id == user.id, Transaction.client_id == item.client_id)
            .first()
        )
        if existing is None:
            tx = Transaction(
                user_id=user.id,
                client_id=item.client_id,
                amount_cents=item.amount_cents,
                merchant=item.merchant,
                source=item.source,
                category_id=item.category_id,
                note=item.note,
                occurred_at=item.occurred_at,
                updated_at=item.updated_at,
                type=item.type,
            )
            db.add(tx)
            continue

        inbound = _ensure_aware(item.updated_at)
        server = _ensure_aware(existing.updated_at)
        if inbound < server:
            continue
        _apply_fields(existing, item)

    db.commit()
    return {"ok": True}


@router.get("/sync/pull", response_model=TransactionPullResponse)
def pull_transactions(
    since: datetime = Query(...),
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> TransactionPullResponse:
    since_aware = _ensure_aware(since)
    rows = (
        db.query(Transaction)
        .filter(Transaction.user_id == user.id, Transaction.updated_at > since_aware)
        .order_by(Transaction.updated_at.asc(), Transaction.id.asc())
        .all()
    )
    return TransactionPullResponse(
        transactions=rows,
        server_time=datetime.now(timezone.utc),
    )
