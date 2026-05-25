from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", case_sensitive=True, extra="ignore")

    REDIS_URL: str = "redis://localhost:6379/0"
    REDIS_SOCKET_CONNECT_TIMEOUT: int = 5
    REDIS_SOCKET_TIMEOUT: int = 5
    REDIS_MAX_CONNECTIONS: int = 100
    REDIS_HEALTH_CHECK_INTERVAL: int = 30

    RABBITMQ_URL: str
    RABBITMQ_PREFETCH_COUNT: int = 1

    QUEUE_RAW: str = "log.raw"

    REDIS_NAMESPACE: str = "simulation"

    ADMIN_API_KEY: str

    MINIO_ENDPOINT: str = "localhost:9000"
    MINIO_ACCESS_KEY: str = ""
    MINIO_SECRET_KEY: str = ""
    MINIO_BUCKET: str = "models"
    MINIO_SECURE: bool = False

    UVICORN_WORKERS: int = 1


settings = Settings()
