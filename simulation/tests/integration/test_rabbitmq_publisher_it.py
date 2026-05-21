import pytest
import asyncio
import json

import aio_pika
from testcontainers.rabbitmq import RabbitMqContainer

import pytest_asyncio

import infrastructure.config.rabbitmq as rmq_config
from infrastructure.config.settings import settings
from infrastructure.ports.publish import RabbitMQPublisherAdapter
from domain.models.raw_log import RawLog, LogSource


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def rabbitmq_container():
    with RabbitMqContainer("rabbitmq:3-management") as rmq:
        yield rmq


@pytest_asyncio.fixture
async def setup_rabbitmq(rabbitmq_container):
    host = rabbitmq_container.get_container_host_ip()
    port = rabbitmq_container.get_exposed_port(5672)

    # Reset global state before connecting
    rmq_config.connection = None
    rmq_config.channel = None

    # Override the URL so rmq_config.connect() targets the container
    settings.RABBITMQ_URL = f"amqp://guest:guest@{host}:{port}/"

    await rmq_config.connect()
    yield
    await rmq_config.close()


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_channel_is_initialized_after_connect(setup_rabbitmq):
    assert rmq_config.channel is not None


@pytest.mark.asyncio
async def test_publish_sends_message_to_queue(setup_rabbitmq):
    queue_name = "test.simulation.raw"
    # Queue must exist before publishing; default exchange drops messages with no binding.
    await rmq_config.channel.declare_queue(queue_name, durable=False)
    adapter = RabbitMQPublisherAdapter(queue_name=queue_name)

    log = RawLog(
        rawMessage='127.0.0.1 - - [01/Jan/2025:00:00:00 +0000] "GET / HTTP/1.1" 200 512 "-" "curl/8.5.0"',
        source=LogSource.HTTP,
    )
    await adapter.publish(log)

    # Verify by consuming the message from the queue
    connection = await aio_pika.connect_robust(settings.RABBITMQ_URL)
    async with connection:
        ch = await connection.channel()
        queue = await ch.declare_queue(queue_name, durable=False)
        msg = await queue.get(timeout=3.0)
        assert msg is not None
        await msg.ack()
        payload = json.loads(msg.body.decode())
        assert payload["source"] == LogSource.HTTP.value
        assert payload["rawMessage"] == log.rawMessage
        assert payload["id"] == log.id


@pytest.mark.asyncio
async def test_publish_raises_when_channel_is_none(setup_rabbitmq):
    original_channel = rmq_config.channel
    rmq_config.channel = None
    adapter = RabbitMQPublisherAdapter(queue_name="test.queue")
    try:
        with pytest.raises(RuntimeError, match="channel not initialized"):
            await adapter.publish(RawLog(rawMessage="x", source=LogSource.HTTP))
    finally:
        rmq_config.channel = original_channel


@pytest.mark.asyncio
async def test_published_log_has_correct_source_field(setup_rabbitmq):
    queue_name = "test.simulation.flow"
    await rmq_config.channel.declare_queue(queue_name, durable=False)
    adapter = RabbitMQPublisherAdapter(queue_name=queue_name)

    log = RawLog(
        rawMessage='{"timestamp": 1700000000.0, "source_ip": "1.2.3.4", "dest_ip": "10.0.0.1",'
                   ' "source_port": 12345, "dest_port": 80, "features": {}}',
        source=LogSource.FLOW,
    )
    await adapter.publish(log)

    connection = await aio_pika.connect_robust(settings.RABBITMQ_URL)
    async with connection:
        ch = await connection.channel()
        queue = await ch.declare_queue(queue_name, durable=False)
        msg = await queue.get(timeout=3.0)
        assert msg is not None
        await msg.ack()
        payload = json.loads(msg.body.decode())
        assert payload["source"] == LogSource.FLOW.value
