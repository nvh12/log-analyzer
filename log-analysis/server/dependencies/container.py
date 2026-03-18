from dependency_injector import containers, providers
from infrastructure.model_store import store
from application.ports.window_port import WindowPort
from infrastructure.ports.window import RedisWindowAdapter
from application.traffic_use_case import TrafficUseCase
from application.ddos_use_case import DDoSUseCase
from application.web_attack_use_case import WebAttackUseCase
from application.error_use_case import ErrorUseCase
from application.drift_use_case import DriftUseCase
from application.detection_service import DetectionService


class Container(containers.DeclarativeContainer):
    """
    Main dependency injection container for the application.
    """

    # Wiring configuration to allow injection into non-FastAPI components
    wiring_config = containers.WiringConfiguration(
        modules=[
            "presentation.consumers.consumer",
        ],
    )

    # Infrastructure components (Singleton / Object)
    repository = providers.Object(store)
    window_adapter = providers.Singleton(RedisWindowAdapter)

    # Use Case Providers
    traffic_use_case = providers.Factory(
        TrafficUseCase,
        repository=repository,
    )

    ddos_use_case = providers.Factory(
        DDoSUseCase,
        repository=repository,
    )

    web_attack_use_case = providers.Factory(
        WebAttackUseCase,
        repository=repository,
    )

    error_use_case = providers.Factory(
        ErrorUseCase,
        repository=repository,
    )

    drift_use_case = providers.Factory(
        DriftUseCase,
    )

    detection_service = providers.Factory(
        DetectionService,
        repository=repository,
    )
