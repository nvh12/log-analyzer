from dependency_injector import containers, providers
from infrastructure.model_store import store
from infrastructure.ports.window import RedisWindowAdapter
from infrastructure.ports.publish import RabbitMQPublisherAdapter
from infrastructure.ports.history import RedisHistoryAdapter
from infrastructure.ports.lock import RedisLockAdapter

from infrastructure.jobs.detection_job import DetectionJobRunner
from application.traffic_use_case import TrafficUseCase
from application.ddos_use_case import DDoSUseCase
from application.web_attack_use_case import WebAttackUseCase
from application.error_use_case import ErrorUseCase
from application.drift_use_case import DriftUseCase

class Container(containers.DeclarativeContainer):
    """
    Dependency Injection container for wiring infrastructure adapters into use cases and job runners.
    """

    # Wiring configuration to allow injection into non-FastAPI components
    wiring_config = containers.WiringConfiguration(
        modules=[
            "presentation.consumers.consumer",
            "main",
        ],
    )

    # Infrastructure components (Singleton / Object)
    repository = providers.Object(store)
    window_adapter = providers.Singleton(RedisWindowAdapter)
    publisher_adapter = providers.Singleton(RabbitMQPublisherAdapter)
    history_adapter = providers.Singleton(RedisHistoryAdapter)
    lock_adapter = providers.Singleton(RedisLockAdapter)

    # Use Case Providers
    traffic_use_case = providers.Factory(
        TrafficUseCase,
        repository=repository,
        publisher=publisher_adapter,
    )

    ddos_use_case = providers.Factory(
        DDoSUseCase,
        repository=repository,
        publisher=publisher_adapter,
    )

    web_attack_use_case = providers.Factory(
        WebAttackUseCase,
        repository=repository,
        publisher=publisher_adapter,
    )

    error_use_case = providers.Factory(
        ErrorUseCase,
        repository=repository,
        publisher=publisher_adapter,
    )

    drift_use_case = providers.Factory(
        DriftUseCase,
        publisher=publisher_adapter,
    )

    # Job Runners
    detection_job_runner = providers.Singleton(
        DetectionJobRunner,
        window_adapter=window_adapter,
        history_adapter=history_adapter,
        lock_adapter=lock_adapter,
        traffic_use_case=traffic_use_case,
        ddos_use_case=ddos_use_case,
        web_attack_use_case=web_attack_use_case,
        error_use_case=error_use_case,
        drift_use_case=drift_use_case,
    )
