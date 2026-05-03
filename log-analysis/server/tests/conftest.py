import sys
from pathlib import Path

# Make the server package root importable so tests can use
# `from domain.models.input import ...` without installation.
sys.path.insert(0, str(Path(__file__).parent.parent))
