from fastapi import FastAPI

from app.db import Base, engine
from app.models import User  # noqa: F401
from app.routers import auth as auth_router

Base.metadata.create_all(bind=engine)

app = FastAPI(title="Auto Ledger API")
app.include_router(auth_router.router)
