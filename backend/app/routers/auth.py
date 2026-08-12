from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import create_access_token, hash_password, verify_password
from app.db import get_db
from app.models import Category, User
from app.schemas import Token, UserCreate, UserLogin

router = APIRouter(prefix="/api/auth", tags=["auth"])

SEED_CATEGORIES = ["餐饮", "交通", "购物", "住房", "娱乐", "医疗", "教育", "其他"]


def seed_categories_for_user(db: Session, user_id: int) -> None:
    now = datetime.now(timezone.utc)
    for i, name in enumerate(SEED_CATEGORIES):
        db.add(
            Category(
                user_id=user_id,
                name=name,
                sort_order=(i + 1) * 10,
                updated_at=now,
            )
        )


@router.post("/register", status_code=status.HTTP_201_CREATED)
def register(payload: UserCreate, db: Session = Depends(get_db)) -> dict:
    email = payload.email.strip().lower()
    if len(payload.password.encode("utf-8")) > 72:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Password is too long",
        )
    existing = db.query(User).filter(User.email == email).first()
    if existing:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered")
    user = User(email=email, password_hash=hash_password(payload.password))
    db.add(user)
    db.flush()
    seed_categories_for_user(db, user.id)
    db.commit()
    db.refresh(user)
    return {"id": user.id, "email": user.email}


@router.post("/login", response_model=Token)
def login(payload: UserLogin, db: Session = Depends(get_db)) -> Token:
    email = payload.email.strip().lower()
    if len(payload.password.encode("utf-8")) > 72:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Incorrect email or password")
    user = db.query(User).filter(User.email == email).first()
    if user is None or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Incorrect email or password")
    return Token(access_token=create_access_token(user.id), token_type="bearer")
