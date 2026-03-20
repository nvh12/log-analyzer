"""Entry point for the Detection Service."""
import asyncio
from fastapi import FastAPI
from contextlib import asynccontextmanager
from infrastructure.model_store import store
from infrastructure.config.rabbitmq import connect, close
from dependencies.container import Container
from presentation.consumers import start_consumer


# DI Container
container = Container()

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Manages application lifecycle."""
    app.container = container

    store.load_all()
    await connect()

    # Start RabbitMQ consumer
    consumer_task = asyncio.create_task(start_consumer())

    # Start detection jobs
    job_runner = container.detection_job_runner()
    job_runner.start()

    yield

    consumer_task.cancel()
    store.unload_all()
    await close()


app = FastAPI(title="Detection Service", lifespan=lifespan)
