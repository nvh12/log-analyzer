from dependency_injector import containers, providers
from infrastructure.config.settings import settings
from infrastructure.model_store import store
from infrastructure.ports.window import RedisWindowAdapter
from infrastructure.ports.publish import RabbitMQPublisherAdapter
from infrastructure.ports.result_repository import PostgresDetectionResultRepository
from infrastructure.ports.history import RedisHistoryAdapter
from infrastructure.ports.lock import RedisLockAdapter

from infrastructure.jobs.detection_job import DetectionJobRunner
from application.traffic_use_case import TrafficUseCase
from application.ddos_use_case import DDoSUseCase
from application.brute_force_use_case import BruteForceUseCase
from application.web_attack_use_case import WebAttackUseCase
from domain.models.input import TrafficThresholds


def _get_traffic_calibration(repo) -> dict:
    result = repo.get("traffic_calibration")
    return result if result is not None else {}


def create_traffic_thresholds(data: dict, settings) -> TrafficThresholds:
    """Maps JSON calibration artifact to TrafficThresholds model, using settings for non-calibrated fields."""
    t = data.get("thresholds", {})
    w = data.get("detector_weights", {})

    return TrafficThresholds(
        z_score_flag=t.get("zscore", settings.TRAFFIC_Z_SCORE_FLAG),
        iqr_multiplier=t.get("iqr", settings.TRAFFIC_IQR_MULTIPLIER),
        ema_alpha=settings.TRAFFIC_EMA_ALPHA,
        ema_dev_threshold=t.get("ema", settings.TRAFFIC_EMA_DEV_THRESHOLD),
        min_history=settings.TRAFFIC_MIN_HISTORY,
        ema_warmup=settings.TRAFFIC_EMA_WARMUP,
        seasonal_z_threshold=t.get("seasonal", settings.TRAFFIC_SEASONAL_Z_THRESHOLD),
        seasonal_min_bucket_size=settings.TRAFFIC_SEASONAL_MIN_BUCKET_SIZE,
        min_weighted_chosen=data.get("min_weighted_chosen", settings.TRAFFIC_MIN_WEIGHTED_CHOSEN),
        weight_ema=w.get("ema", settings.TRAFFIC_WEIGHT_EMA),
        weight_zscore=w.get("zscore", settings.TRAFFIC_WEIGHT_ZSCORE),
        weight_iqr=w.get("iqr", settings.TRAFFIC_WEIGHT_IQR),
        weight_seasonal=w.get("seasonal", settings.TRAFFIC_WEIGHT_SEASONAL),
        absolute_min_floor=t.get("absolute_min_floor", settings.TRAFFIC_ABSOLUTE_MIN_FLOOR),
        variance_min_floor=t.get("variance_min_floor", settings.TRAFFIC_VARIANCE_MIN_FLOOR),
    )



class Container(containers.DeclarativeContainer):
    """Dependency Injection container for wiring infrastructure adapters into use cases and job runners."""

    wiring_config = containers.WiringConfiguration(
        modules=[
            "presentation.consumers.consumer",
            "presentation.consumers.flow_consumer",
            "main",
        ],
    )

    _traffic_history_key = f"{settings.REDIS_NAMESPACE}:traffic"
    _seasonal_history_key = f"{settings.REDIS_NAMESPACE}:traffic_seasonal"

    # Infrastructure components
    repository = providers.Object(store)
    window_adapter = providers.Singleton(
        RedisWindowAdapter,
        window_seconds=settings.WINDOW_SECONDS,
        window_key=f"{settings.REDIS_NAMESPACE}:window:logs",
    )
    publisher_adapter = providers.Singleton(RabbitMQPublisherAdapter, exchange_name=settings.QUEUE_OUT)
    result_repository_adapter = providers.Singleton(PostgresDetectionResultRepository)
    history_adapter = providers.Singleton(RedisHistoryAdapter, history_ttl_seconds=settings.HISTORY_TTL_SECONDS)
    lock_adapter = providers.Singleton(RedisLockAdapter, key_prefix=f"{settings.REDIS_NAMESPACE}:lock:")

    # Use case providers
    _traffic_calibration = providers.Callable(_get_traffic_calibration, repo=repository)

    traffic_thresholds = providers.Factory(
        create_traffic_thresholds,
        data=_traffic_calibration,
        settings=settings,
    )

    traffic_use_case = providers.Singleton(
        TrafficUseCase,
        publisher=publisher_adapter,
        thresholds=traffic_thresholds,
        result_repository=result_repository_adapter,
    )

    # Flow track use cases (UC2 + UC4) — injected into the flow consumer
    ddos_use_case = providers.Singleton(
        DDoSUseCase,
        repository=repository,
        publisher=publisher_adapter,
        result_repository=result_repository_adapter,
    )

    brute_force_use_case = providers.Singleton(
        BruteForceUseCase,
        repository=repository,
        publisher=publisher_adapter,
        result_repository=result_repository_adapter,
    )

    web_attack_use_case = providers.Singleton(
        WebAttackUseCase,
        repository=repository,
        publisher=publisher_adapter,
        result_repository=result_repository_adapter,
    )

    # HTTP-track job runner (UC1 Traffic, UC3 Web Attack)
    detection_job_runner = providers.Singleton(
        DetectionJobRunner,
        window_adapter=window_adapter,
        history_adapter=history_adapter,
        lock_adapter=lock_adapter,
        traffic_use_case=traffic_use_case,
        web_attack_use_case=web_attack_use_case,
        traffic_history_key=_traffic_history_key,
        seasonal_history_key=_seasonal_history_key,
    )
