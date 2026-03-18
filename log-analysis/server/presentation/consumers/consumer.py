import asyncio
import aio_pika
from dependency_injector.wiring import inject, Provide
from infrastructure.config.rabbitmq import channel
from presentation.schemas.log_message import LogMessage
from application.ports.window_port import WindowPort
from application.detection_service import DetectionService
from dependencies.container import Container

QUEUE_IN = "log.normalized"


def parse_log(body: bytes) -> LogMessage:
    return LogMessage.model_validate_json(body)


@inject
async def handle_message(
    message: aio_pika.IncomingMessage,
    detection_service: DetectionService = Provide[Container.detection_service],
    window_adapter: WindowPort = Provide[Container.window_adapter],
) -> None:
    async with message.process():
        log_message = parse_log(message.body)
        log = log_message.to_domain()
        await window_adapter.add_log(log)

        # Get the current window of logs for analysis
        window = await window_adapter.get_window()

        # result = detection_service.execute(input_data)
        # logger.info(f"Detection result: {result}")


async def start_consumer() -> None:
    queue = await channel.declare_queue(QUEUE_IN, durable=True)
    await queue.consume(handle_message)
    await asyncio.Future()
