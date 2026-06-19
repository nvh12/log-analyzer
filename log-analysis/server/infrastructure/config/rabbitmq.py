"""RabbitMQ connection management and configuration."""
import aio_pika
from infrastructure.config.settings import settings

connection = None
channel = None

async def connect():
    """Establishes a robust connection to RabbitMQ and initializes the channel."""
    global connection, channel
    connection = await aio_pika.connect_robust(settings.RABBITMQ_URL)
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=settings.RABBITMQ_PREFETCH_COUNT)

async def close():
    """Closes the RabbitMQ connection."""
    if connection:
        await connection.close()


DLX_NAME = "log.dlx"


async def declare_input_queue(chan, queue_name: str):
    """Declares an input queue with dead-letter routing to `{queue_name}.dlq`.

    log-processing (Java) publishes to this queue and declares the identical
    x-dead-letter-exchange/x-dead-letter-routing-key arguments on its side
    (RabbitMqConfig's normalizedHttpQueue/normalizedFlowQueue) — whichever service
    starts first creates the queue, the other's declare is a no-op since the
    arguments match exactly. They must stay in sync: a mismatched declare here
    would hit a 406 PRECONDITION_FAILED regardless of start order, since queue
    arguments are fixed at creation.
    """
    dlq_name = f"{queue_name}.dlq"
    await chan.declare_exchange(DLX_NAME, aio_pika.ExchangeType.DIRECT, durable=True)
    return await chan.declare_queue(
        queue_name,
        durable=True,
        arguments={
            "x-dead-letter-exchange": DLX_NAME,
            "x-dead-letter-routing-key": dlq_name,
        },
    )