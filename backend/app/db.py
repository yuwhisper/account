from collections.abc import Generator

from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import DATABASE_URL

connect_args: dict = {}
engine_kwargs: dict = {"pool_pre_ping": True, "pool_recycle": 3600}
if DATABASE_URL.startswith("sqlite"):
    connect_args["check_same_thread"] = False
elif DATABASE_URL.startswith("mysql"):
    connect_args["init_command"] = "SET time_zone = '+00:00'"
    engine_kwargs.update(pool_size=5, max_overflow=5)

engine = create_engine(DATABASE_URL, connect_args=connect_args, **engine_kwargs)

if DATABASE_URL.startswith("sqlite"):

    @event.listens_for(engine, "connect")
    def _set_sqlite_pragma(dbapi_connection, _connection_record) -> None:  # type: ignore[no-untyped-def]
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
