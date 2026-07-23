from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """12-factor configuration: every value is overridable via SO_-prefixed env vars."""

    model_config = SettingsConfigDict(env_prefix="SO_", env_file=".env", extra="ignore")

    database_url: str = (
        "postgresql+asyncpg://second_opinion:second_opinion@localhost:5432/second_opinion"
    )
    redis_url: str = "redis://localhost:6379/0"

    s3_endpoint_url: str = "http://localhost:9000"
    s3_access_key: str = "minioadmin"
    s3_secret_key: str = "minioadmin"
    s3_bucket: str = "recordings"

    max_upload_bytes: int = 50 * 1024 * 1024

    # DPDP storage limitation: the worker's daily retention sweep purges the
    # audio blob + transcript rows of recordings older than this window.
    retention_days: int = 30

    # Auth: Firebase phone sign-in; the API verifies Firebase ID tokens.
    # "fake" accepts "fake:<uid>[:<phone>]" bearer tokens for local dev/tests.
    auth_provider: str = "firebase"  # "firebase" | "fake"
    firebase_project_id: str = ""

    log_format: str = "json"  # "json" | "console" (human-readable for local dev)

    # Speech stage (worker). "fake" runs the pipeline without external calls.
    speech_provider: str = "sarvam"  # "sarvam" | "fake"
    sarvam_api_key: str = ""
    sarvam_model: str = "saaras:v3"
    sarvam_job_timeout_s: int = 600

    # NLP stage (worker): relevance filter + structured extraction.
    nlp_provider: str = "sarvam"  # "sarvam" | "fake"
    sarvam_chat_model: str = "sarvam-30b"  # sarvam-m is deprecated; 105b for higher quality
    relevance_threshold: float = 0.35  # segments below this weight are discarded

    # Assessment stage (worker): triage output. Interim answer to Q3 — a
    # general Sarvam chat model behind the Assessor port until a medical LLM
    # is benchmarked.
    assessment_provider: str = "sarvam"  # "sarvam" | "fake"
    sarvam_assessment_model: str = "sarvam-30b"


@lru_cache
def get_settings() -> Settings:
    return Settings()
