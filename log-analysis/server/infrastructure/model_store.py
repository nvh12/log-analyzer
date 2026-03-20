import os
import joblib
import logging
from pathlib import Path
from typing import Any
from domain.repository.model_repository import ModelRepository

logger = logging.getLogger(__name__)

# Anchor model paths to this file's directory
_BASE_DIR = Path(__file__).parent

MODEL_PATHS = {
    "traffic_if":  _BASE_DIR / "models/isolation_forest/traffic.pkl",
    "ddos_if":     _BASE_DIR / "models/isolation_forest/ddos.pkl",
    "web_if":      _BASE_DIR / "models/isolation_forest/web.pkl",
    "web_svm":     _BASE_DIR / "models/one_class_svm/web.pkl",
}

class LocalModelStore(ModelRepository):
    def __init__(self):
        self._models: dict[str, Any] = {}

    def load_all(self) -> None:
        """Load all models from MODEL_PATHS."""
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
        """Removes all models from memory."""
        self._models.clear()


    def get(self, key: str) -> Any:
        """Retrieves a loaded model by key from the store."""
        return self._models.get(key)


# Singleton instance
store = LocalModelStore()
