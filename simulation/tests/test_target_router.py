"""Tests for presentation/routers/target_router.py FastAPI endpoints."""
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from presentation.routers.target_router import router


@pytest.fixture
def client():
    app = FastAPI()
    app.include_router(router)
    return TestClient(app, raise_server_exceptions=True)


# ---------------------------------------------------------------------------
# Pages
# ---------------------------------------------------------------------------

def test_homepage(client):
    response = client.get("/")
    assert response.status_code == 200
    assert response.json() == {"page": "home", "title": "Acme Corp", "message": "Welcome"}


def test_index_html(client):
    response = client.get("/index.html")
    assert response.status_code == 200
    assert response.json() == {"page": "home", "title": "Acme Corp"}


def test_about(client):
    response = client.get("/about")
    assert response.status_code == 200
    body = response.json()
    assert body["page"] == "about"
    assert body["founded"] == 2010


def test_contact(client):
    response = client.get("/contact")
    assert response.status_code == 200
    body = response.json()
    assert body["page"] == "contact"
    assert body["email"] == "info@acme.example"


def test_favicon_returns_204_with_no_body(client):
    response = client.get("/favicon.ico")
    assert response.status_code == 204
    assert response.content == b""


# ---------------------------------------------------------------------------
# Products
# ---------------------------------------------------------------------------

def test_list_products_returns_five_products(client):
    response = client.get("/products")
    assert response.status_code == 200
    products = response.json()["products"]
    assert len(products) == 5
    for i, product in enumerate(products, start=1):
        assert product["id"] == i
        assert product["name"] == f"Product {i}"
        assert 10 <= product["price"] <= 500


def test_get_product_valid_id(client):
    response = client.get("/products/42")
    assert response.status_code == 200
    body = response.json()
    assert body["id"] == 42
    assert body["name"] == "Product 42"
    assert 10 <= body["price"] <= 500


@pytest.mark.parametrize("product_id", [0, -1, 101, 1000])
def test_get_product_out_of_range_returns_404(client, product_id):
    response = client.get(f"/products/{product_id}")
    assert response.status_code == 404


def test_search_with_query(client):
    response = client.get("/search", params={"q": "widget"})
    assert response.status_code == 200
    assert response.json() == {"query": "widget", "results": [], "total": 0}


def test_search_without_query_defaults_to_empty_string(client):
    response = client.get("/search")
    assert response.status_code == 200
    assert response.json() == {"query": "", "results": [], "total": 0}


# ---------------------------------------------------------------------------
# REST API — users
# ---------------------------------------------------------------------------

def test_list_users(client):
    response = client.get("/api/v1/users")
    assert response.status_code == 200
    body = response.json()
    assert body["total"] == 3
    assert len(body["users"]) == 3
    assert body["users"][0] == {"id": 1, "username": "user1"}


def test_get_user_valid_id(client):
    response = client.get("/api/v1/users/5")
    assert response.status_code == 200
    body = response.json()
    assert body == {"id": 5, "username": "user5", "role": "member"}


def test_get_user_invalid_id_returns_404(client):
    response = client.get("/api/v1/users/0")
    assert response.status_code == 404


def test_create_user_returns_201(client):
    response = client.post("/api/v1/users", json={"username": "alice"})
    assert response.status_code == 201
    body = response.json()
    assert body["username"] == "alice"
    assert body["role"] == "member"
    assert 100 <= body["id"] <= 999


def test_create_user_without_username_defaults_to_unknown(client):
    response = client.post("/api/v1/users", json={})
    assert response.status_code == 201
    assert response.json()["username"] == "unknown"


# ---------------------------------------------------------------------------
# REST API — orders
# ---------------------------------------------------------------------------

def test_list_orders(client):
    response = client.get("/api/v1/orders")
    assert response.status_code == 200
    orders = response.json()["orders"]
    assert len(orders) == 3
    for order in orders:
        assert order["status"] == "delivered"
        assert 20 <= order["total"] <= 300


def test_get_order_valid_id(client):
    response = client.get("/api/v1/orders/7")
    assert response.status_code == 200
    body = response.json()
    assert body["id"] == 7
    assert body["status"] == "delivered"
    assert body["items"] == []


def test_get_order_invalid_id_returns_404(client):
    response = client.get("/api/v1/orders/0")
    assert response.status_code == 404


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("path", ["/login", "/signin", "/api/v1/login", "/api/auth/token"])
def test_login_endpoints_always_return_401(client, path):
    response = client.post(path, json={"username": "alice", "password": "wrong"})
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid credentials"


def test_login_endpoint_accepts_empty_body(client):
    response = client.post("/login", json={})
    assert response.status_code == 401


# ---------------------------------------------------------------------------
# Static assets
# ---------------------------------------------------------------------------

def test_css_returns_text_css(client):
    response = client.get("/static/style.css")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/css")
    assert "body" in response.text


def test_js_returns_application_javascript(client):
    response = client.get("/static/app.js")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/javascript")
    assert "use strict" in response.text
