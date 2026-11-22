"""Configuration comes only from environment variables (12-factor); nothing secret is in the code."""

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = Field(description="SQLAlchemy URL, role rw_app (user requests)")
    admin_database_url: str | None = Field(default=None, description="role rw_admin_user (reference-data management)")
    jwt_secret: str = Field(min_length=32, description="HMAC secret for access tokens")
    jwt_algorithm: str = "HS256"
    access_token_ttl_minutes: int = Field(default=15, ge=1, le=60)
    refresh_token_ttl_days: int = Field(default=30, ge=1, le=365)
    osrm_base_url: str = "http://localhost:5000"
    osrm_timeout_seconds: float = Field(default=10.0, gt=0)
    log_level: str = "INFO"
    cors_origins: list[str] = Field(default_factory=list)


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
