from datetime import timedelta
import os
from pathlib import Path
import secrets


def _load_secret_key() -> str:
    """Use an environment secret, or create a persistent local-development secret."""
    configured = os.getenv("SECRET_KEY", "").strip()
    if configured:
        if len(configured) < 32:
            raise RuntimeError("SECRET_KEY must contain at least 32 characters")
        return configured

    secret_file = Path(os.getenv("SECRET_KEY_FILE", ".secret_key"))
    if secret_file.exists():
        value = secret_file.read_text(encoding="utf-8").strip()
        if len(value) >= 32:
            return value

    value = secrets.token_urlsafe(48)
    secret_file.write_text(value, encoding="utf-8")
    try:
        secret_file.chmod(0o600)
    except OSError:
        pass
    return value


SECRET_KEY = _load_secret_key()
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", str(60 * 24 * 7)))
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./account.db")


def access_token_expires() -> timedelta:
    return timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
