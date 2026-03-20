from typing import Protocol, Any

class ModelRepository(Protocol):
    """Interface for retrieving machine learning models."""

    def get(self, key: str) -> Any:
        """Retrieve a model by its key."""
        ...
