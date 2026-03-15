from fastapi import FastAPI
from contextlib import asynccontextmanager
from infrastructure.model_store import store

@asynccontextmanager
async def lifespan(app: FastAPI):
    store.load_all()
    yield
    store.unload_all()

app = FastAPI(title="Detection Service", lifespan=lifespan)
