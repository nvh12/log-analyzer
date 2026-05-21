from dependency_injector import containers, providers

from infrastructure.config.settings import settings
from infrastructure.config.redis import redis_client
from infrastructure.ports.publish import RabbitMQPublisherAdapter
from application.simulation_use_case import SimulationUseCase


class Container(containers.DeclarativeContainer):
    wiring_config = containers.WiringConfiguration(
        modules=["main"],
    )

    publisher_adapter = providers.Singleton(
        RabbitMQPublisherAdapter, queue_name=settings.QUEUE_RAW
    )

    simulation_use_case = providers.Singleton(
        SimulationUseCase,
        publisher=publisher_adapter,
        redis=providers.Object(redis_client),
        namespace=settings.REDIS_NAMESPACE,
    )
