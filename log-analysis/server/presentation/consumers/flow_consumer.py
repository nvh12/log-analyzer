import asyncio
import logging
import aio_pika
from pydantic import ValidationError
from dependency_injector.wiring import inject, Provide
from infrastructure.config import rabbitmq
from infrastructure.config.settings import settings
from presentation.schemas.flow_message import FlowMessage
from domain.models.input import DDoSInput, BruteForceInput
from application.ddos_use_case import DDoSUseCase
from application.brute_force_use_case import BruteForceUseCase
from dependencies.container import Container

logger = logging.getLogger(__name__)


def parse_flow(body: bytes) -> FlowMessage:
    return FlowMessage.model_validate_json(body)


def _to_ddos_input(msg: FlowMessage) -> DDoSInput:
    return DDoSInput(
        timestamp=msg.timestamp,
        source_ip=msg.source_ip,
        dest_ip=msg.dest_ip,
        source_port=msg.source_port,
        dest_port=msg.dest_port,
        features=msg.features,
    )


def _to_brute_force_input(msg: FlowMessage) -> BruteForceInput:
    return BruteForceInput(
        timestamp=msg.timestamp,
        source_ip=msg.source_ip,
        dest_ip=msg.dest_ip,
        source_port=msg.source_port,
        dest_port=msg.dest_port,
        features=msg.features,
    )


@inject
async def handle_flow_message(
    message: aio_pika.IncomingMessage,
    ddos_use_case: DDoSUseCase = Provide[Container.ddos_use_case],
    brute_force_use_case: BruteForceUseCase = Provide[Container.brute_force_use_case],
) -> None:
    """Processes a single flow record through UC2 (DDoS) and UC4 (Brute Force) in parallel."""
    async with message.process(requeue=False):
        try:
            flow_msg = parse_flow(message.body)
            await asyncio.gather(
                ddos_use_case.execute(_to_ddos_input(flow_msg)),
                brute_force_use_case.execute(_to_brute_force_input(flow_msg)),
            )
        except ValidationError as e:
            logger.error(
                "Invalid flow message schema, discarding. Error: %s | Body: %s",
                e, message.body[:500],
            )
        except Exception as e:
            logger.exception("Unexpected error processing flow message: %s", e)


async def start_flow_consumer() -> None:
    """Initializes and starts the RabbitMQ consumer for normalized flow records."""
    if rabbitmq.channel is None:
        raise RuntimeError("RabbitMQ channel not initialized — cannot start flow consumer")
    queue = await rabbitmq.channel.declare_queue(settings.QUEUE_IN_FLOW, durable=True)
    await queue.consume(handle_flow_message)
    await asyncio.Future()
