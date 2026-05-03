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