from fastapi import FastAPI
from contextlib import asynccontextmanager
from infrastructure.model_store import store
from infrastructure.config.rabbitmq import connect, close
from dependencies.container import Container


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize container and wire components
    container = Container()
    app.container = container

    store.load_all()
    await connect()
    yield
    store.unload_all()
    await close()


app = FastAPI(title="Detection Service", lifespan=lifespan)
