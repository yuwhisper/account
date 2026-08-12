from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.exc import IntegrityError
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


def _to_utc_naive(dt: datetime) -> datetime:
    """Normalize to UTC wall time without tzinfo for SQLite-safe storage/compare."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=None)
    return dt.astimezone(timezone.utc).replace(tzinfo=None)


def _as_utc_aware(dt: datetime) -> datetime:
    """Interpret stored naive UTC (or any aware) as UTC-aware for API responses."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


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


def _fold_by_client_id(items: list[TransactionIn]) -> list[TransactionIn]:
    """Keep one row per client_id using last-write-wins on updated_at."""
    folded: dict[str, TransactionIn] = {}
    for item in items:
        prev = folded.get(item.client_id)
        if prev is None or _to_utc_naive(item.updated_at) >= _to_utc_naive(prev.updated_at):
            folded[item.client_id] = item
    return list(folded.values())


def _apply_fields(tx: Transaction, item: TransactionIn) -> None:
    tx.amount_cents = item.amount_cents
    tx.merchant = item.merchant
    tx.source = item.source
    tx.category_id = item.category_id
    tx.note = item.note
    tx.occurred_at = _to_utc_naive(item.occurred_at)
    tx.updated_at = _to_utc_naive(item.updated_at)
    tx.type = item.type


def _to_out(tx: Transaction) -> TransactionOut:
    return TransactionOut(
        id=tx.id,
        client_id=tx.client_id,
        amount_cents=tx.amount_cents,
        merchant=tx.merchant,
        source=tx.source,
        category_id=tx.category_id,
        note=tx.note or "",
        occurred_at=_as_utc_aware(tx.occurred_at),
        updated_at=_as_utc_aware(tx.updated_at),
        type=tx.type,
    )


@router.get("", response_model=list[TransactionOut])
def list_transactions(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> list[TransactionOut]:
    rows = (
        db.query(Transaction)
        .filter(Transaction.user_id == user.id)
        .order_by(Transaction.occurred_at.desc(), Transaction.id.desc())
        .all()
    )
    return [_to_out(tx) for tx in rows]


@router.post("/sync/push", response_model=dict)
def push_transactions(
    payload: TransactionPushRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> dict:
    try:
        for item in _fold_by_client_id(payload.transactions):
            _validate_category(db, user.id, item.category_id)
            existing = (
                db.query(Transaction)
                .filter(Transaction.user_id == user.id, Transaction.client_id == item.client_id)
                .first()
            )
            if existing is None:
                db.add(
                    Transaction(
                        user_id=user.id,
                        client_id=item.client_id,
                        amount_cents=item.amount_cents,
                        merchant=item.merchant,
                        source=item.source,
                        category_id=item.category_id,
                        note=item.note,
                        occurred_at=_to_utc_naive(item.occurred_at),
                        updated_at=_to_utc_naive(item.updated_at),
                        type=item.type,
                    )
                )
                db.flush()
                continue

            inbound = _to_utc_naive(item.updated_at)
            server = _to_utc_naive(existing.updated_at)
            if inbound < server:
                continue
            _apply_fields(existing, item)

        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="client_id conflict",
        ) from None
    return {"ok": True}


@router.get("/sync/pull", response_model=TransactionPullResponse)
def pull_transactions(
    since: datetime = Query(...),
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> TransactionPullResponse:
    since_utc = _to_utc_naive(since)
    # Freeze the upper bound before the query. Returning a later timestamp can skip
    # rows committed between the SELECT and response construction forever.
    server_time = datetime.now(timezone.utc)
    upper_bound = _to_utc_naive(server_time)
    rows = (
        db.query(Transaction)
        .filter(
            Transaction.user_id == user.id,
            Transaction.updated_at > since_utc,
            Transaction.updated_at <= upper_bound,
        )
        .order_by(Transaction.updated_at.asc(), Transaction.id.asc())
        .all()
    )
    return TransactionPullResponse(
        transactions=[_to_out(tx) for tx in rows],
        server_time=server_time,
    )
