"""Error-path tests: malformed messages route to the dead-letter queue and drop_audit."""
import asyncio
import json

import aio_pika
import pytest

from helpers import poll_until


@pytest.mark.asyncio
async def test_unparseable_message_lands_in_drop_audit(rmq_connection, pg_conn):
    """
    Publishing a non-JSON payload to log.raw triggers the DLX dead-letter path:
      • Spring AMQP's JacksonJsonMessageConverter fails to deserialize the body.
      • setDefaultRequeueRejected(false) causes the container to nack the message.
      • RabbitMQ routes it via log.dlx to log.raw.dlq.
      • DeadLetterConsumer records it in drop_audit with reason DEAD_LETTERED.
    """
    channel = await rmq_connection.channel()

    await channel.default_exchange.publish(
        aio_pika.Message(
            body=b"this is not valid json {{{{",
            content_type="application/json",
            delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
        ),
        routing_key="log.raw",
    )

    async def drop_audit_has_entry():
        n = await pg_conn.fetchval(
            "SELECT COUNT(*) FROM drop_audit WHERE drop_reason = 'DEAD_LETTERED'"
        )
        return n >= 1

    await poll_until(drop_audit_has_entry, timeout=15)

    # The bad message must not have been normalized.
    n_http = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_http")
    n_flow = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_flow")
    assert n_http == 0
    assert n_flow == 0


@pytest.mark.asyncio
async def test_null_received_at_defaults_to_now_and_processes(rmq_connection, pg_conn):
    """
    A RawLog with receivedAt=null is not dropped: RawLogConsumer defaults
    receivedAt to Instant.now() and enqueues it normally, so the log is
    processed like any other — no drop_audit entry should result.
    """
    channel = await rmq_connection.channel()

    payload = json.dumps({
        "id": "test-null-ts",
        "rawMessage": '127.0.0.1 - - [01/Jan/2024:00:00:00 +0000] "GET / HTTP/1.1" 200 512',
        "source": "HTTP",
        "receivedAt": None,
        "headers": {},
    }).encode()

    await channel.default_exchange.publish(
        aio_pika.Message(
            body=payload,
            content_type="application/json",
            delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
        ),
        routing_key="log.raw",
    )

    async def normalized_http_has_entry():
        n = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_http")
        return n >= 1

    await poll_until(normalized_http_has_entry, timeout=15)

    n_flow  = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_flow")
    n_audit = await pg_conn.fetchval("SELECT COUNT(*) FROM drop_audit")
    assert n_flow == 0
    assert n_audit == 0
