import logging

import aio_pika
from aio_pika import Message, DeliveryMode

from application.ports.publish_port import PublishPort
from domain.models.raw_log import RawLog
import infrastructure.config.rabbitmq as rmq_config

logger = logging.getLogger(__name__)


class RabbitMQPublisherAdapter(PublishPort):
    def __init__(self, queue_name: str):
        self._queue_name = queue_name

    async def publish(self, log: RawLog) -> None:
        if rmq_config.channel is None:
            raise RuntimeError("RabbitMQ channel not initialized")
        body = log.model_dump_json().encode()
        message = Message(body, content_type="application/json", delivery_mode=DeliveryMode.PERSISTENT)
        await rmq_config.channel.default_exchange.publish(message, routing_key=self._queue_name)
        logger.debug("Published log id=%s source=%s", log.id, log.source)
