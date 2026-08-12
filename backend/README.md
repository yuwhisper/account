# Auto Ledger Backend

## Setup

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
```

## Run

```powershell
.\.venv\Scripts\uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

API docs: http://127.0.0.1:8000/docs

## Notes

- Default JWT `SECRET_KEY` in `app/config.py` is for development only — replace it in production.
- SQLite DB file defaults to `account.db` in the working directory.
