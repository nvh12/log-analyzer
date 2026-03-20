import asyncio
import aio_pika
from dependency_injector.wiring import inject, Provide
from infrastructure.config.rabbitmq import channel
from presentation.schemas.log_message import LogMessage
from application.ports.window_port import WindowPort
from dependencies.container import Container

QUEUE_IN = "log.normalized"


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
    async with message.process():
        log_message = parse_log(message.body)
        log = log_message.to_domain()
        await window_adapter.add_log(log)

        # Analysis is typically triggered periodically or on certain events.
        # Placeholder for triggering use cases using the new architecture.


async def start_consumer() -> None:
    """
    Initializes and starts the RabbitMQ consumer for normalized logs.
    """
    queue = await channel.declare_queue(QUEUE_IN, durable=True)
    await queue.consume(handle_message)
    await asyncio.Future()
