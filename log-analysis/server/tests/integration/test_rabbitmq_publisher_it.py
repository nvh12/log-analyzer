import pytest
import asyncio
import json
import aio_pika
from testcontainers.rabbitmq import RabbitMqContainer
from datetime import datetime

from infrastructure.config.settings import settings
from infrastructure.config import rabbitmq
from infrastructure.ports.publish import RabbitMQPublisherAdapter
from domain.models.results.ddos_result import DDoSResult
from domain.models.results.severity import Severity
from domain.models.results.detection_type import DetectionType

import pytest_asyncio

@pytest.fixture(scope="module")
def rabbitmq_container():
    with RabbitMqContainer("rabbitmq:3-management") as rmq:
        yield rmq

@pytest_asyncio.fixture
async def setup_rabbitmq(rabbitmq_container):
    # Override settings manually since testcontainers RabbitMqContainer lacks get_connection_url
    host = rabbitmq_container.get_container_host_ip()
    port = rabbitmq_container.get_exposed_port(5672)
    settings.RABBITMQ_URL = f"amqp://guest:guest@{host}:{port}/"
    
    # Initialize the global connection used by the adapter
    await rabbitmq.connect()
    
    yield rabbitmq_container
    
    # Teardown
    await rabbitmq.close()

@pytest.mark.asyncio
async def test_publish_message(setup_rabbitmq):
    # Prepare dummy result
    dummy_result = DDoSResult(
        detection_type=DetectionType.DDOS,
        anomaly=True,
        confidence=0.99,
        severity=Severity.CRITICAL,
        log_timestamp=datetime.now(),
        source_ip="192.168.1.1",
        dest_ip="10.0.0.1",
        source_port=12345,
        dest_port=80,
        layer_triggered="xgboost",
        method_flags={}
    )

    queue_name = "test.detection.results"
    adapter = RabbitMQPublisherAdapter(queue_name=queue_name)
    
    # Publish the message
    await adapter.publish(dummy_result)
    
    # Verify the message using a separate connection
    connection = await aio_pika.connect_robust(settings.RABBITMQ_URL)
    async with connection:
        channel = await connection.channel()
        queue = await channel.declare_queue(queue_name, durable=True)
        
        # Get the message from the queue
        message = await queue.get(timeout=2.0)
        assert message is not None
        
        # Acknowledge and verify
        await message.ack()
        payload = json.loads(message.body.decode())
        
        assert payload["detection_type"] == DetectionType.DDOS.value
        assert payload["anomaly"] is True
        assert payload["severity"] == Severity.CRITICAL.value
        assert payload["source_ip"] == "192.168.1.1"
