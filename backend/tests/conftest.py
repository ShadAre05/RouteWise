import os

# settings used by every test; there is no real secret here
os.environ.setdefault("DATABASE_URL", "postgresql+psycopg://unused@localhost/unused")
os.environ.setdefault("JWT_SECRET", "unit-test-secret-unit-test-secret-0123456789")

import pytest  # noqa: E402

from app.core.config import Settings  # noqa: E402


@pytest.fixture
def settings() -> Settings:
    return Settings(
        database_url="postgresql+psycopg://unused@localhost/unused",
        jwt_secret="unit-test-secret-unit-test-secret-0123456789",
    )
