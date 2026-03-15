import os
import joblib
import logging
from typing import Any
from domain.repository.model_repository import ModelRepository

logger = logging.getLogger(__name__)

MODEL_PATHS = {
    "traffic_if":  "models/isolation_forest/traffic.pkl",
    "ddos_if":     "models/isolation_forest/ddos.pkl",
    "web_if":      "models/isolation_forest/web.pkl",
    "web_svm":     "models/one_class_svm/web.pkl",
}

class LocalModelStore(ModelRepository):
    def __init__(self):
        self._models: dict[str, Any] = {}

    def load_all(self) -> None:
        """Load all models into the repository."""
        for key, path in MODEL_PATHS.items():
            if os.path.exists(path):
                try:
                    self._models[key] = joblib.load(path)
                    logger.info(f"Loaded model: {key}")
                except Exception as e:
                    logger.error(f"Failed to load model {key} from {path}: {e}")
                    self._models[key] = None
            else:
                self._models[key] = None
                logger.warning(f"Model not found, skipping: {path}")

    def unload_all(self) -> None:
        """Unload all models from the repository."""
        self._models.clear()

    def get(self, key: str) -> Any:
        """Retrieve a model by its key."""
        return self._models.get(key)

# Singleton instance
store = LocalModelStore()
