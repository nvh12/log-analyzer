import re
import math
import numpy as np
from collections import Counter
from domain.repository.model_repository import ModelRepository
from domain.models.input import WebAttackInput
from domain.models.results import WebAttackResult


# ── Layer 1: Rule Engine (Regex signatures) ───────────────────────────────────

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
    payload = f"{req.url} {req.body}"
    for name, pattern in SIGNATURES.items():
        if pattern.search(payload):
            return True, name
    return False, None


# ── Layer 2 & 3: Feature extraction ──────────────────────────────────────────

def _url_entropy(url: str) -> float:
    if not url:
        return 0.0
    freq = Counter(url)
    total = len(url)
    return -sum((c / total) * math.log2(c / total) for c in freq.values())


def _extract_features(req: WebAttackInput) -> list[float]:
    payload = f"{req.url} {req.body}"
    special = len(re.findall(r"[^a-zA-Z0-9\s]", payload))
    params  = len(re.findall(r"[?&][^=]+=", req.url))
    return [
        float(len(payload)),           # request_length
        float(special),                # special_char_count
        _url_entropy(req.url),         # url_entropy
        float(params),                 # param_count
    ]


# ── Main pipeline ─────────────────────────────────────────────────────────────

def detect(req: WebAttackInput, repo: ModelRepository) -> WebAttackResult:
    # Layer 1 — Rule engine
    matched, sig_name = _rule_engine(req)
    if matched:
        return WebAttackResult(anomaly=True, layer_triggered=f"rule_engine:{sig_name}", confidence=1.0)

    features = _extract_features(req)

    # Layer 2 — Isolation Forest (structural anomaly)
    if_model = repo.get("web_if")
    if if_model is not None:
        score = float(if_model.decision_function([features])[0])
        pred  = if_model.predict([features])[0]
        if pred == -1:
            return WebAttackResult(
                anomaly=True,
                layer_triggered="isolation_forest",
                confidence=min(1.0, -score),
            )

    # Layer 3 — One-Class SVM (zero-day)
    svm_model = repo.get("web_svm")
    if svm_model is not None:
        pred = svm_model.predict([features])[0]   # -1 = outlier (attack)
        if pred == -1:
            score = float(svm_model.decision_function([features])[0])
            return WebAttackResult(
                anomaly=True,
                layer_triggered="one_class_svm",
                confidence=min(1.0, -score),
            )

    return WebAttackResult(anomaly=False, layer_triggered=None, confidence=0.0)
