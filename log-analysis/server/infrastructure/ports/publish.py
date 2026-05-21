import asyncio
import aio_pika
from application.ports.publish_port import PublisherPort
from domain.models.results import DetectionResult
from infrastructure.config import rabbitmq

class RabbitMQPublisherAdapter(PublisherPort):
    """RabbitMQ implementation for publishing detection results to a fanout exchange."""
    def __init__(self, exchange_name: str = "detection.results"):
        self._exchange_name = exchange_name
        self._exchange: aio_pika.abc.AbstractExchange | None = None
        self._declare_lock = asyncio.Lock()

    async def publish(self, result: DetectionResult) -> None:
        """Publishes the result to the detection.results fanout exchange."""

        if rabbitmq.channel is None:
            raise RuntimeError("RabbitMQ channel is not initialized")

        if self._exchange is None:
            async with self._declare_lock:
                if self._exchange is None:
                    self._exchange = await rabbitmq.channel.declare_exchange(
                        self._exchange_name,
                        aio_pika.ExchangeType.FANOUT,
                        durable=True
                    )

        message_body = result.model_dump_json().encode()
        await self._exchange.publish(
            aio_pika.Message(
                body=message_body,
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT
            ),
            routing_key=""
        )
