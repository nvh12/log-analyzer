import io
import json
import hashlib
import joblib
import pytest
from testcontainers.minio import MinioContainer
from minio import Minio

from infrastructure.config.settings import settings
from infrastructure.model_store import MinIOModelStore

DUMMY_JSON_KEY = "ddos_feature_cols"
DUMMY_JSON_OBJECT = "xgboost/uc2_feature_cols.json"

DUMMY_PKL_KEY = "ddos_xgb"
DUMMY_PKL_OBJECT = "xgboost/ddos.pkl"

@pytest.fixture(scope="module")
def minio_container():
    with MinioContainer(image="minio/minio:RELEASE.2023-11-20T22-40-07Z") as minio:
        yield minio

@pytest.fixture
def setup_minio(minio_container):
    # Override settings
    config = minio_container.get_config()
    settings.MINIO_ENDPOINT = config["endpoint"]
    settings.MINIO_ACCESS_KEY = config["access_key"]
    settings.MINIO_SECRET_KEY = config["secret_key"]
    settings.MINIO_SECURE = False
    settings.MINIO_BUCKET = "test-models"

    # Set up client to upload dummy data
    client = Minio(
        settings.MINIO_ENDPOINT,
        access_key=settings.MINIO_ACCESS_KEY,
        secret_key=settings.MINIO_SECRET_KEY,
        secure=False
    )
    
    if not client.bucket_exists(settings.MINIO_BUCKET):
        client.make_bucket(settings.MINIO_BUCKET)

    # 1. Create a dummy json file
    dummy_json_data = ["feat_1", "feat_2"]
    json_bytes = json.dumps(dummy_json_data).encode("utf-8")
    client.put_object(
        settings.MINIO_BUCKET,
        DUMMY_JSON_OBJECT,
        io.BytesIO(json_bytes),
        len(json_bytes)
    )

    # 2. Create a dummy pickle file
    dummy_model = {"model_name": "dummy_xgb"}
    pkl_bytes = io.BytesIO()
    joblib.dump(dummy_model, pkl_bytes)
    pkl_bytes.seek(0)
    pkl_data = pkl_bytes.read()
    
    client.put_object(
        settings.MINIO_BUCKET,
        DUMMY_PKL_OBJECT,
        io.BytesIO(pkl_data),
        len(pkl_data)
    )

    # 3. Create the checksums.json manifest
    checksums = {
        DUMMY_PKL_OBJECT: hashlib.sha256(pkl_data).hexdigest()
    }
    checksums_bytes = json.dumps(checksums).encode("utf-8")
    client.put_object(
        settings.MINIO_BUCKET,
        "checksums.json",
        io.BytesIO(checksums_bytes),
        len(checksums_bytes)
    )

    yield client

def test_minio_store_load_all(setup_minio):
    store = MinIOModelStore()
    store.load_all()

    # Verify JSON load
    loaded_json = store.get(DUMMY_JSON_KEY)
    assert loaded_json == ["feat_1", "feat_2"]

    # Verify Pickle load
    loaded_model = store.get(DUMMY_PKL_KEY)
    assert loaded_model is not None
    assert loaded_model["model_name"] == "dummy_xgb"
