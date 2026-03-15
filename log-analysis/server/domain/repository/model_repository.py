from typing import Protocol, Any

class ModelRepository(Protocol):
    def get(self, key: str) -> Any:
        """Retrieve a model by its key."""
        ...
