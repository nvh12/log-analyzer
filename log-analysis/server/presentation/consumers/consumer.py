import asyncio
import logging
import aio_pika
from pydantic import ValidationError
from dependency_injector.wiring import inject, Provide
from infrastructure.config.rabbitmq import channel
from infrastructure.config.settings import settings
from presentation.schemas.log_message import LogMessage
from application.ports.window_port import WindowPort
from dependencies.container import Container

logger = logging.getLogger(__name__)


def parse_log(body: bytes) -> LogMessage:
    """Parses a serialized JSON log message into a LogMessage schema."""
    return LogMessage.model_validate_json(body)


@inject
async def handle_message(
    message: aio_pika.IncomingMessage,
    window_adapter: WindowPort = Provide[Container.window_adapter]
) -> None:
    """
    Processes normalized log messages and adds them to the sliding window.
    """
    async with message.process(requeue=False):
        try:
            log_message = parse_log(message.body)
            log = log_message.to_domain()
            await window_adapter.add_log(log)
        except ValidationError as e:
            logger.error(
                "Invalid log message schema, discarding. Error: %s | Body: %s",
                e, message.body[:500],
            )
        except Exception as e:
            logger.exception("Unexpected error processing message: %s", e)


async def start_consumer() -> None:
    """
    Initializes and starts the RabbitMQ consumer for normalized logs.
    """
    if channel is None:
        raise RuntimeError("RabbitMQ channel not initialized — cannot start HTTP log consumer")
    queue = await channel.declare_queue(settings.QUEUE_IN_HTTP, durable=True)
    await queue.consume(handle_message)
    await asyncio.Future()
