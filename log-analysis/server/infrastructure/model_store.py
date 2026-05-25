import asyncio
import hashlib
import io
import json
import logging
import joblib
import sys
from typing import Any
from minio import Minio
from minio.error import S3Error
from domain.repository.model_repository import ModelRepository
from infrastructure.config.settings import settings
from domain.models.vocab import ParamVocab

# Pickling compatibility hack: notebooks train models in __main__
# This ensures joblib can find the ParamVocab class when loading UC3 models.
if "__main__" in sys.modules:
    sys.modules["__main__"].ParamVocab = ParamVocab

logger = logging.getLogger(__name__)

# Maps model key → MinIO object name. Keys ending in .json are loaded as JSON lists/dicts.
MODEL_OBJECT_KEYS: dict[str, str] = {
    "ddos_xgb": "flow/ddos/xgboost.pkl",
    "brute_force_xgb": "flow/bruteforce/xgboost.pkl",
    "ddos_feature_cols": "flow/feature_cols.json",

    "web_if": "webattack/isolation_forest.pkl",
    "web_svm": "webattack/one_class_svm.pkl",
    "web_xgb": "webattack/xgboost.pkl",
    "web_vocab": "webattack/vocab.json",

    "traffic_calibration": "trafficspike/ensemble_calibration.json",
}
 
class MinIOModelStore(ModelRepository):
    def __init__(self):
        self._models: dict[str, Any] = {}
        self._client = Minio(
            settings.MINIO_ENDPOINT,
            access_key=settings.MINIO_ACCESS_KEY,
            secret_key=settings.MINIO_SECRET_KEY,
            secure=settings.MINIO_SECURE,
        )

    def _load_checksums(self) -> dict[str, str]:
        """Fetches the pickle checksum manifest from MinIO. Returns empty dict on failure."""
        response = None
        try:
            response = self._client.get_object(settings.MINIO_BUCKET, "checksums.json")
            return json.loads(response.read())
        except S3Error:
            logger.error(
                "Checksum manifest (checksums.json) not found in MinIO — all pickle models will be refused"
            )
            return {}
        except Exception as e:
            logger.error("Failed to load checksum manifest: %s — all pickle models will be refused", e)
            return {}
        finally:
            if response is not None:
                try:
                    response.close()
                    response.release_conn()
                except Exception:
                    pass

    def load_all(self) -> None:
        """Downloads and deserializes all models from MinIO into memory."""
        checksums = self._load_checksums()
        for key, object_name in MODEL_OBJECT_KEYS.items():
            response = None
            try:
                response = self._client.get_object(settings.MINIO_BUCKET, object_name)
                data = response.read()
                if object_name.endswith(".json"):
                    self._models[key] = json.loads(data)
                else:
                    expected = checksums.get(object_name)
                    if not expected:
                        raise ValueError(f"No checksum in manifest for {object_name} — refusing to deserialize")
                    actual = hashlib.sha256(data).hexdigest()
                    if actual != expected:
                        raise ValueError(
                            f"Checksum mismatch for {object_name}: expected {expected}, got {actual}"
                        )
                    self._models[key] = joblib.load(io.BytesIO(data))
                logger.info(
                    "Loaded model: %s (s3://%s/%s)",
                    key,
                    settings.MINIO_BUCKET,
                    object_name,
                )
            except S3Error as e:
                self._models[key] = None
                logger.warning(
                    "Model file not found in MinIO, skipping: %s — %s", object_name, e
                )
            except Exception as e:
                self._models[key] = None
                logger.error(
                    "Unexpected error loading model %s from %s — detection for this model will be disabled: %s",
                    key,
                    object_name,
                    e,
                    exc_info=True,
                )
            finally:
                if response is not None:
                    try:
                        response.close()
                        response.release_conn()
                    except Exception:
                        pass

        missing = [k for k, v in self._models.items() if v is None]
        if missing:
            logger.error(
                "Model load complete — %d/%d models MISSING (detection degraded): %s",
                len(missing),
                len(MODEL_OBJECT_KEYS),
                missing,
            )
        else:
            logger.info(
                "Model load complete — all %d models loaded.", len(MODEL_OBJECT_KEYS)
            )

    async def load_all_async(self) -> None:
        """Non-blocking wrapper: runs load_all() in a thread pool to avoid blocking the event loop."""
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(None, self.load_all)

    def unload_all(self) -> None:
        """Removes all models from memory."""
        self._models.clear()

    def get(self, key: str) -> Any:
        """Retrieves a loaded model by key."""
        return self._models.get(key)


# Singleton instance
store = MinIOModelStore()
