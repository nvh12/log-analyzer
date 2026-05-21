import random

from fastapi import APIRouter, HTTPException
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel

router = APIRouter(tags=["target"])


# --- Pages ---

@router.get("/")
async def homepage() -> dict:
    return {"page": "home", "title": "Acme Corp", "message": "Welcome"}


@router.get("/index.html")
async def index_html() -> dict:
    return {"page": "home", "title": "Acme Corp"}


@router.get("/about")
async def about() -> dict:
    return {"page": "about", "title": "About Us", "founded": 2010}


@router.get("/contact")
async def contact() -> dict:
    return {"page": "contact", "title": "Contact", "email": "info@acme.example"}


@router.get("/favicon.ico", status_code=204)
async def favicon() -> None:
    return None


# --- Products ---

@router.get("/products")
async def list_products() -> dict:
    return {
        "products": [
            {"id": i, "name": f"Product {i}", "price": round(random.uniform(10, 500), 2)}
            for i in range(1, 6)
        ]
    }


@router.get("/products/{product_id}")
async def get_product(product_id: int) -> dict:
    if product_id < 1 or product_id > 100:
        raise HTTPException(status_code=404, detail="Product not found")
    return {"id": product_id, "name": f"Product {product_id}", "price": round(random.uniform(10, 500), 2)}


@router.get("/search")
async def search(q: str = "") -> dict:
    return {"query": q, "results": [], "total": 0}


# --- REST API ---

@router.get("/api/v1/users")
async def list_users() -> dict:
    return {"users": [{"id": i, "username": f"user{i}"} for i in range(1, 4)], "total": 3}


@router.get("/api/v1/users/{user_id}")
async def get_user(user_id: int) -> dict:
    if user_id < 1:
        raise HTTPException(status_code=404, detail="User not found")
    return {"id": user_id, "username": f"user{user_id}", "role": "member"}


@router.post("/api/v1/users", status_code=201)
async def create_user(body: dict) -> dict:
    return {"id": random.randint(100, 999), "username": body.get("username", "unknown"), "role": "member"}


@router.get("/api/v1/orders")
async def list_orders() -> dict:
    return {"orders": [{"id": i, "status": "delivered", "total": round(random.uniform(20, 300), 2)} for i in range(1, 4)]}


@router.get("/api/v1/orders/{order_id}")
async def get_order(order_id: int) -> dict:
    if order_id < 1:
        raise HTTPException(status_code=404, detail="Order not found")
    return {"id": order_id, "status": "delivered", "items": [], "total": round(random.uniform(20, 300), 2)}


# --- Auth ---

class LoginRequest(BaseModel):
    username: str = ""
    password: str = ""


@router.post("/login")
async def login(body: LoginRequest) -> dict:
    raise HTTPException(status_code=401, detail="Invalid credentials")


@router.post("/signin")
async def signin(body: LoginRequest) -> dict:
    raise HTTPException(status_code=401, detail="Invalid credentials")


@router.post("/api/v1/login")
async def api_login(body: LoginRequest) -> dict:
    raise HTTPException(status_code=401, detail="Invalid credentials")


@router.post("/api/auth/token")
async def auth_token(body: LoginRequest) -> dict:
    raise HTTPException(status_code=401, detail="Invalid credentials")


# --- Static assets ---

@router.get("/static/style.css")
async def css() -> PlainTextResponse:
    return PlainTextResponse("body{margin:0;font-family:sans-serif}", media_type="text/css")


@router.get("/static/app.js")
async def js() -> PlainTextResponse:
    return PlainTextResponse("'use strict';", media_type="application/javascript")
