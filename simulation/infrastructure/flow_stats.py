"""Loads per-class flow feature statistics from MinIO for use in log generation."""
import json
import logging

from minio import Minio
from minio.error import S3Error

logger = logging.getLogger(__name__)

# MinIO object keys for per-class feature statistics produced by the training notebooks.
# Each file contains {"benign": {feat: {p25, p75, ...}}, "attack": {feat: {...}}}
_STAT_OBJECT_KEYS: dict[str, str] = {
    "ddos":       "flow/ddos/class_stats.json",
    "bruteforce": "flow/bruteforce/class_stats.json",
}


class FlowStatsLoader:
    """Downloads per-class CICFlowMeter feature statistics from MinIO."""

    def __init__(
        self,
        endpoint: str,
        access_key: str,
        secret_key: str,
        bucket: str,
        secure: bool = False,
    ):
        self._client = Minio(endpoint, access_key=access_key, secret_key=secret_key, secure=secure)
        self._bucket = bucket

    def load(self) -> dict:
        """Return {stats_key: {"benign": {...}, "attack": {...}}} for each available artifact."""
        result: dict = {}
        for key, object_name in _STAT_OBJECT_KEYS.items():
            response = None
            try:
                response = self._client.get_object(self._bucket, object_name)
                result[key] = json.loads(response.read())
                logger.info("Loaded flow class stats: %s (%s)", key, object_name)
            except S3Error:
                logger.warning(
                    "Flow class stats not found in MinIO: %s — generation will fall back to hardcoded ranges",
                    object_name,
                )
            except Exception as e:
                logger.warning(
                    "Failed to load flow class stats %s: %s — generation will fall back to hardcoded ranges",
                    object_name,
                    e,
                )
            finally:
                if response is not None:
                    try:
                        response.close()
                        response.release_conn()
                    except Exception:
                        pass
        return result
