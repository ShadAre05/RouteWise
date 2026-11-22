"""Engine and session handling. Every request is one transaction; the signed-in user id is passed to PostgreSQL so
that row-level security (database/db/migrations/V006) limits every query to that user's rows."""

import uuid
from collections.abc import Iterator

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.constants import RLS_SETTING_NAME


def make_engine(url: str) -> Engine:
    return create_engine(url, pool_pre_ping=True, pool_size=5, max_overflow=5)


def make_session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, expire_on_commit=False)


def set_rls_user(session: Session, user_id: uuid.UUID) -> None:
    """is_local=true: the setting disappears at COMMIT/ROLLBACK, so pooled connections cannot leak a user."""
    session.execute(text("SELECT set_config(:name, :value, true)"), {"name": RLS_SETTING_NAME, "value": str(user_id)})


def transactional_session(factory: sessionmaker[Session], user_id: uuid.UUID | None = None) -> Iterator[Session]:
    session = factory()
    try:
        if user_id is not None:
            set_rls_user(session, user_id)
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
