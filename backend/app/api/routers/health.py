from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.api import deps

router = APIRouter(tags=["service"])


@router.get("/health", summary="Liveness: the process is running")
def health() -> dict[str, str]:
    """Answers 200 while the process runs. Use it for container liveness probes; it does not touch the database."""
    return {"status": "ok"}


@router.get("/health/ready", summary="Readiness: the database answers")
def ready(session: Session = Depends(deps.anonymous_session)) -> dict[str, str]:
    """Answers 200 when the database accepts a query, otherwise 500. Use it for readiness probes."""
    session.execute(text("SELECT 1"))
    return {"status": "ready"}
