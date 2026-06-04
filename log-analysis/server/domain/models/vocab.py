from urllib.parse import urlparse, parse_qs, unquote
from typing import Set, Dict, List, Iterable


def levenshtein(s1: str, s2: str) -> int:
    """Standard Levenshtein (edit) distance between two strings."""
    if len(s1) < len(s2):
        return levenshtein(s2, s1)
    if not s2:
        return len(s1)
    prev = list(range(len(s2) + 1))
    for i, c1 in enumerate(s1):
        curr = [i + 1]
        for j, c2 in enumerate(s2):
            curr.append(min(
                curr[j] + 1,
                prev[j + 1] + 1,
                prev[j] + (0 if c1 == c2 else 1),
            ))
        prev = curr
    return prev[-1]


class ParamVocab:
    """
    Parameter-name vocabulary learned from benign traffic.
    Matches the implementation in UC3 training notebooks.
    """

    def __init__(self):
        self.known_names: Set[str] = set()

    @classmethod
    def from_names(cls, names: Iterable[str]) -> "ParamVocab":
        """Create a ParamVocab pre-loaded with a collection of known parameter names."""
        vocab = cls()
        vocab.load(names)
        return vocab

    def load(self, names: Iterable[str]) -> None:
        """Populate known_names from an iterable of parameter name strings."""
        self.known_names.update(names)

    @staticmethod
    def _parse_params(url: str, body: str, content_type: str) -> Dict[str, List[str]]:
        params = {}
        try:
            parsed = urlparse(unquote(url))
            params.update(parse_qs(parsed.query, keep_blank_values=True))
        except Exception:
            pass
        if isinstance(body, str) and isinstance(content_type, str):
            if "application/x-www-form-urlencoded" in content_type.lower():
                try:
                    params.update(parse_qs(body, keep_blank_values=True))
                except Exception:
                    pass
        return params
