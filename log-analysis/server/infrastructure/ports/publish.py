import aio_pika
import json
from application.ports.publish_port import PublisherPort
from domain.models.results import DetectionResult
from infrastructure.config.rabbitmq import channel

class RabbitMQPublisherAdapter(PublisherPort):
    """RabbitMQ implementation for publishing detection results."""
    def __init__(self, queue_name: str = "detection.results"):
        self._queue_name = queue_name
        self._queue_declared = False

    async def publish(self, result: DetectionResult) -> None:
        """Publishes the result to a RabbitMQ queue."""

        if channel is None:
            raise RuntimeError("RabbitMQ channel is not initialized")

        # Declare queue once; subsequent calls are no-ops on the broker side anyway,
        # but skip the round-trip after the first successful declaration.
        if not self._queue_declared:
            await channel.declare_queue(self._queue_name, durable=True)
            self._queue_declared = True
        
        message_body = result.model_dump_json().encode()
        await channel.default_exchange.publish(
            aio_pika.Message(
                body=message_body,
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT
            ),
            routing_key=self._queue_name
        )
