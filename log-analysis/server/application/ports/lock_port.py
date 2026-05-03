from abc import ABC, abstractmethod
from contextlib import asynccontextmanager


class LockNotAcquiredError(RuntimeError):
    """Raised by LockPort.lock() when the resource lock is already held."""


class LockPort(ABC):
    """
    Interface for distributed locking to coordinate job execution across multiple instances.
    """
    @abstractmethod
    @asynccontextmanager
    async def lock(self, resource: str, timeout: int = 10):
        """Acquire an exclusive lock for a given resource name."""
        yield
