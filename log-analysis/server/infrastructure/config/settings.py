from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", case_sensitive=True, extra="ignore")

    # External services
    POSTGRES_DSN: str = "postgresql://localhost:5432/log-analyzer"
    POSTGRES_MIN_CONNECTIONS: int = 2
    POSTGRES_MAX_CONNECTIONS: int = 10
    REDIS_URL: str = "redis://localhost:6379/0"
    REDIS_SOCKET_CONNECT_TIMEOUT: int = 5
    REDIS_SOCKET_TIMEOUT: int = 5
    REDIS_MAX_CONNECTIONS: int = 100
    REDIS_HEALTH_CHECK_INTERVAL: int = 30
    RABBITMQ_URL: str
    RABBITMQ_PREFETCH_COUNT: int = 1

    # MinIO
    MINIO_ENDPOINT: str = "localhost:9000"
    MINIO_ACCESS_KEY: str
    MINIO_SECRET_KEY: str
    MINIO_BUCKET: str = "models"
    MINIO_SECURE: bool = False

    # Queue names
    QUEUE_IN_HTTP: str = "log.normalized.http"
    QUEUE_IN_FLOW: str = "log.normalized.flow"
    QUEUE_OUT: str = "detection.results"

    # HTTP-track detection job cron schedules (6-field: second minute hour day month day_of_week)
    CRON_TRAFFIC: str = "*/10 * * * * *"
    CRON_WEB_ATTACK: str = "*/5 * * * * *"
    # Window / history / lock
    WINDOW_SECONDS: int = 60
    HISTORY_TTL_SECONDS: int = 7 * 24 * 3600   # 7 days
    LOCK_TIMEOUT_SECONDS: int = 30

    # Redis key namespace — change per deployment to avoid cross-env key collisions
    REDIS_NAMESPACE: str = "detection"

    # Traffic detection thresholds
    TRAFFIC_Z_SCORE_FLAG: float = 3.328
    TRAFFIC_IQR_MULTIPLIER: float = 2.288
    TRAFFIC_EMA_ALPHA: float = 0.1
    TRAFFIC_EMA_DEV_THRESHOLD: float = 3.190
    TRAFFIC_MIN_HISTORY: int = 5
    TRAFFIC_EMA_WARMUP: int = 5
    TRAFFIC_SEASONAL_Z_THRESHOLD: float = 4.047
    TRAFFIC_SEASONAL_MIN_BUCKET_SIZE: int = 3
    TRAFFIC_MIN_WEIGHTED_CHOSEN: float = 1.5
    TRAFFIC_WEIGHT_EMA: float = 0.5
    TRAFFIC_WEIGHT_ZSCORE: float = 0.5
    TRAFFIC_WEIGHT_IQR: float = 1.0
    TRAFFIC_WEIGHT_SEASONAL: float = 1.0

    @field_validator("CRON_TRAFFIC", "CRON_WEB_ATTACK", mode="before")
    @classmethod
    def validate_cron(cls, v: str) -> str:
        fields = str(v).strip().split()
        if len(fields) not in (5, 6):
            raise ValueError(
                f"Invalid cron expression '{v}': expected 5 or 6 space-separated fields, got {len(fields)}"
            )
        return v


settings = Settings()
