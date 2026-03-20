"""RabbitMQ connection management and configuration."""
import aio_pika
import os

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost/")

connection = None
channel = None

async def connect():
    """Establishes a robust connection to RabbitMQ and initializes the channel."""
    global connection, channel
    connection = await aio_pika.connect_robust(RABBITMQ_URL)
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=1)

async def close():
    """Closes the RabbitMQ connection."""
    if connection:
        await connection.close()