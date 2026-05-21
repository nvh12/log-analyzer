import aio_pika
from infrastructure.config.settings import settings

connection = None
channel = None


async def connect():
    global connection, channel
    connection = await aio_pika.connect_robust(settings.RABBITMQ_URL)
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=settings.RABBITMQ_PREFETCH_COUNT)


async def close():
    if connection:
        await connection.close()
