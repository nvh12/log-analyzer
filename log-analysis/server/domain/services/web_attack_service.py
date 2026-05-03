import logging
import re
import math
import numpy as np
from datetime import datetime
from typing import Optional
from urllib.parse import urlparse, parse_qs, unquote
from domain.repository.model_repository import ModelRepository
from domain.repository.repo_keys import WEB_MODEL, WEB_VOCAB
from domain.models.input import WebAttackInput
from domain.models.results import WebAttackResult, Severity
from domain.models.vocab import ParamVocab, levenshtein

logger = logging.getLogger(__name__)


# Rule Engine (Regex signatures)

SIGNATURES = {
    "sqli":           re.compile(
        r"('|--|;|\/\*|\*\/|xp_|union\s+select|select\s+.*\s+from|"
        r"insert\s+into|drop\s+table|exec\s*\()", re.IGNORECASE),
    "xss":            re.compile(
        r"(<script|javascript:|on\w+=|<iframe|<img[^>]+src\s*=\s*['\"]?javascript)",
        re.IGNORECASE),
    "path_traversal": re.compile(r"(\.\./|\.\.\\|%2e%2e%2f|%252e)", re.IGNORECASE),
    "rce":            re.compile(
        r"(;|\||`|\$\(|&&)\s*(ls|cat|id|whoami|wget|curl|bash|sh)\b", re.IGNORECASE),
}


def _rule_engine(req: WebAttackInput) -> tuple[bool, str | None]:
    payload = f"{req.url} ?{req.query_string or ''} {req.body or ''}"
    for name, pattern in SIGNATURES.items():
        if pattern.search(payload):
            return True, name
    return False, None


# Feature extraction

SPECIAL_CHARS = set("'\";<>(){}[]|$`!@#%^*~")

def _shannon_entropy(s: str) -> float:
    """Computes Shannon entropy of a string."""
    if not s:
        return 0.0
    length = len(s)
    freq = {}
    for ch in s:
        freq[ch] = freq.get(ch, 0) + 1
    return -sum((c / length) * math.log2(c / length) for c in freq.values())


def _count_special_chars(s: str) -> int:
    """Counts non-alphanumeric/non-space characters based on a fixed set."""
    return sum(1 for ch in s if ch in SPECIAL_CHARS)


def _count_params(url: str, query_string: str, body: str, content_type: str) -> int:
    """Counts parameters in query string and x-www-form-urlencoded body."""
    n = 0
    try:
        # Check both the query part of the URL and the separate query_string field
        parsed = urlparse(url)
        q1 = parse_qs(parsed.query, keep_blank_values=True)
        q2 = parse_qs(query_string or "", keep_blank_values=True)
        n += len({**q1, **q2}) # Merged unique keys
    except Exception:
        pass
    if body and content_type and "application/x-www-form-urlencoded" in content_type.lower():
        try:
            n += len(parse_qs(body, keep_blank_values=True))
        except Exception:
            pass
    return n


def _max_param_value_length(url: str, query_string: str, body: str, content_type: str) -> int:
    """Finds the maximum length of any parameter value."""
    vals = []
    try:
        parsed = urlparse(url)
        for v_list in parse_qs(parsed.query, keep_blank_values=True).values():
            vals.extend(v_list)
        for v_list in parse_qs(query_string or "", keep_blank_values=True).values():
            vals.extend(v_list)
    except Exception:
        pass
    if body and content_type and "application/x-www-form-urlencoded" in content_type.lower():
        try:
            for v_list in parse_qs(body, keep_blank_values=True).values():
                vals.extend(v_list)
        except Exception:
            pass
    return max((len(v) for v in vals), default=0)


def _url_path_depth(url: str) -> int:
    """Counts segments in the URL path."""
    try:
        path = urlparse(url).path
        segments = [s for s in path.split("/") if s]
        return len(segments)
    except Exception:
        return 0


def _parse_all_params(url: str, query_string: str, body: str, content_type: str) -> dict:
    """Utility to extract all parameter names for vocabulary checks."""
    params = {}
    try:
        parsed = urlparse(unquote(url))
        params.update(parse_qs(parsed.query, keep_blank_values=True))
        params.update(parse_qs(query_string or "", keep_blank_values=True))
    except Exception:
        pass
    if body and content_type and "application/x-www-form-urlencoded" in content_type.lower():
        try:
            params.update(parse_qs(body, keep_blank_values=True))
        except Exception:
            pass
    return params


def _extract_structural_features(req: WebAttackInput) -> list[float]:
    """Extracts the 9 structural features used in UC3 models."""
    decoded_url = unquote(req.url)
    query_string = req.query_string or ""
    decoded_body = req.body or ""
    headers = req.headers or {}
    content_type = headers.get("Content-Type", headers.get("content-type", ""))

    # Reconstruct full request string for request_length.
    # Uses CRLF (\r\n) to approximate HTTP/1.1 wire format.
    headers_str = "\r\n".join(f"{k}: {v}" for k, v in headers.items())
    full_request = (
        (req.method or "") + " "
        + decoded_url + ("?" + query_string if query_string else "") + " HTTP/1.1\r\n"
        + headers_str + "\r\n\r\n"
        + decoded_body
    )

    combined = decoded_url + query_string + decoded_body
    spec_count = _count_special_chars(combined)
    combined_len = max(len(combined), 1)

    return [
        float(len(full_request)),                  # request_length
        float(spec_count),                          # special_char_count
        float(spec_count / combined_len),          # special_char_ratio
        float(_shannon_entropy(decoded_url)),       # url_entropy (full URL as received; query already included if inline)
        float(_shannon_entropy(decoded_body)),      # body_entropy
        float(_count_params(decoded_url, query_string, decoded_body, content_type)),  # param_count
        float(_max_param_value_length(decoded_url, query_string, decoded_body, content_type)), # max_param_value_length
        float(_url_path_depth(decoded_url)),             # url_path_depth
        float(1.0 if len(decoded_body) > 0 else 0.0),    # has_body
    ]


def _extract_vocab_features(req: WebAttackInput, known_names: set) -> list[float]:
    """Extracts the 3 vocabulary-based features."""
    headers = req.headers or {}
    content_type = headers.get("Content-Type", headers.get("content-type", ""))
    params = _parse_all_params(req.url, req.query_string, req.body, content_type)
    names = list(params.keys())
    total = len(names)

    if total == 0:
        return [0.0, 0.0, 0.0]

    unknown_count = sum(1 for n in names if n not in known_names)

    max_min_ed = 0
    for name in names:
        if name in known_names:
            continue
        if not known_names:
            min_ed = len(name)
        else:
            min_ed = min(levenshtein(name, kn) for kn in known_names)
        max_min_ed = max(max_min_ed, min_ed)

    return [
        float(unknown_count),           # unknown_param_name_count
        float(unknown_count / total),   # unknown_param_name_ratio
        float(max_min_ed),              # max_param_name_min_edit_dist
    ]


# Main pipeline

def detect(req: WebAttackInput, repo: ModelRepository, window_start: Optional[datetime] = None, window_end: Optional[datetime] = None) -> WebAttackResult:
    """Detects web attacks using a 2-layer defensive cascade: Rule Engine -> XGBoost."""

    # Layer 1: Rule Engine (Regex-based signatures)
    matched, sig_name = _rule_engine(req)
    if matched:
        return WebAttackResult(
            anomaly=True,
            layer_triggered=f"rule_engine:{sig_name}",
            confidence=1.0,
            source_ip=req.source_ip,
            log_timestamp=req.timestamp,
            window_start=window_start,
            window_end=window_end,
            severity=Severity.HIGH if sig_name in ["sqli", "rce"] else Severity.MEDIUM,
        )

    # Layer 2: XGBoost (ML-based classification)
    xgb_art = repo.get(WEB_MODEL)
    if isinstance(xgb_art, dict) and "model" in xgb_art:
        struct_feats = _extract_structural_features(req)

        # Vocabulary resolution: external list (repo) takes priority over artifact-embedded object.
        ext_vocab = repo.get(WEB_VOCAB)
        if isinstance(ext_vocab, list):
            known_names = set(ext_vocab)
        else:
            vocab_obj = xgb_art.get("vocab")
            if isinstance(vocab_obj, ParamVocab):
                known_names = vocab_obj.known_names
            elif isinstance(vocab_obj, dict):
                known_names = set(vocab_obj.get("known_names", []))
            else:
                known_names = set()

        if not known_names:
            logger.warning(
                "web_xgb: no vocabulary loaded — all param names will be treated as unknown; "
                "load a ParamVocab via '%s' or embed one in the artifact under 'vocab'",
                WEB_VOCAB,
            )

        vocab_feats = _extract_vocab_features(req, known_names)
        
        # Combine and scale
        X = np.array([struct_feats + vocab_feats])
        X_scaled = xgb_art["scaler"].transform(X)
        
        # XGBoost output is probability [0, 1]
        prob = float(xgb_art["model"].predict_proba(X_scaled)[0, 1])
        threshold = xgb_art.get("threshold", 0.5)
        
        if prob > threshold:
            return WebAttackResult(
                anomaly=True,
                layer_triggered="xgboost",
                confidence=prob,
                source_ip=req.source_ip,
                log_timestamp=req.timestamp,
                window_start=window_start,
                window_end=window_end,
                severity=Severity.CRITICAL,
            )

    return WebAttackResult(
        anomaly=False,
        layer_triggered=None,
        confidence=0.0,
        source_ip=req.source_ip,
        log_timestamp=req.timestamp,
        window_start=window_start,
        window_end=window_end,
        severity=Severity.LOW,
    )
