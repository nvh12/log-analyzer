"""Downloads a CSV from MinIO and returns rows as feature dicts for replay."""
import csv
import io
import logging

from minio import Minio
from minio.error import S3Error

logger = logging.getLogger(__name__)

_NON_FEATURE_COLS = frozenset({"label", "label_orig", "attack_type", "Timestamp"})


class ReplayLoader:
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

    def load(self, object_key: str) -> list[dict[str, float]]:
        """Download a CSV from MinIO and return one feature dict per row.

        Non-feature columns (label, Timestamp, etc.) are silently dropped.
        Non-numeric values are coerced to 0.0.
        """
        response = None
        try:
            response = self._client.get_object(self._bucket, object_key)
            content = response.read().decode("utf-8")
        except S3Error as e:
            raise FileNotFoundError(f"Object not found in MinIO bucket '{self._bucket}': {object_key}") from e
        finally:
            if response is not None:
                try:
                    response.close()
                    response.release_conn()
                except Exception:
                    pass

        reader = csv.DictReader(io.StringIO(content))
        rows: list[dict[str, float]] = []
        for row in reader:
            features: dict[str, float] = {}
            for col, val in row.items():
                if col in _NON_FEATURE_COLS:
                    continue
                try:
                    features[col] = float(val)
                except (ValueError, TypeError):
                    features[col] = 0.0
            if features:
                rows.append(features)

        logger.info("Loaded %d rows from MinIO: %s/%s", len(rows), self._bucket, object_key)
        return rows
