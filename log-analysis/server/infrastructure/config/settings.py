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
    CRON_TRAFFIC: str = "0 * * * * *"
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
    # 5 was too few to estimate variance reliably: with a near-flat baseline (req
    # counts barely varying minute to minute), a handful of samples can land on a
    # sample std well below the true population std purely by chance, which then
    # makes z-score/IQR/EMA (each clamped to its own *_VARIANCE_FLOOR once measured
    # std drops that low) flag an ordinary ~5-10% fluctuation as a spike. More
    # samples make the variance estimate — and therefore these thresholds — stable.
    TRAFFIC_MIN_HISTORY: int = 20
    TRAFFIC_SEASONAL_Z_THRESHOLD: float = 4.047
    TRAFFIC_SEASONAL_MIN_BUCKET_SIZE: int = 3
    TRAFFIC_MIN_WEIGHTED_CHOSEN: float = 1.5
    TRAFFIC_WEIGHT_EMA: float = 0.5
    TRAFFIC_WEIGHT_ZSCORE: float = 0.5
    TRAFFIC_WEIGHT_IQR: float = 1.0
    TRAFFIC_WEIGHT_SEASONAL: float = 1.0
    TRAFFIC_ABSOLUTE_MIN_FLOOR: float = 15.0
    # Each non-seasonal detector gets its own variance floor (rather than one
    # shared value) since z-score/IQR/EMA can have different natural noise
    # scales on the same traffic — see Z_SCORE_VARIANCE_FLOOR / IQR_VARIANCE_FLOOR
    # / EMA_VARIANCE_FLOOR in traffic_spike_ensemble_rule.ipynb (cell 1).
    TRAFFIC_Z_SCORE_VARIANCE_FLOOR: float = 5.0
    TRAFFIC_IQR_VARIANCE_FLOOR: float = 5.0
    TRAFFIC_EMA_VARIANCE_FLOOR: float = 5.0
    # Effective floor is max(absolute, pct * recent baseline mean): at a baseline well
    # above the absolute floor, traffic that happens to be steadier than calibration
    # assumed (e.g. std well under the absolute floor) no longer gets an ordinary few-
    # percent fluctuation amplified into a spike just because the absolute floor clamps it.
    TRAFFIC_Z_SCORE_VARIANCE_FLOOR_PCT: float = 0.03
    TRAFFIC_IQR_VARIANCE_FLOOR_PCT: float = 0.03
    TRAFFIC_EMA_VARIANCE_FLOOR_PCT: float = 0.03
    TRAFFIC_SEASONAL_SCALE_FLOOR: float = 5.0
    TRAFFIC_SEASONAL_WINDOW_DAYS: int = 28
    TRAFFIC_LOW_VOLUME_JUMP_MULTIPLIER: float = 3.0


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
