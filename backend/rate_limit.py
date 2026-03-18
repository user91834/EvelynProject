# rate_limit.py - In-memory rate limiter for conversational / pseudo-sync use
from __future__ import annotations

import time
import threading
from collections import defaultdict

# Generous limit for real-time conversation + pseudo-sync (many requests per minute)
RATE_LIMIT_REQUESTS = 400
RATE_LIMIT_WINDOW_SEC = 60

_SKIP_PATHS = ("/ping", "/health", "/v1/ping", "/v1/health")
_SKIP_PREFIXES = ("/media", "/docs", "/redoc", "/openapi.json")

_window: dict[str, list[float]] = defaultdict(list)
_lock = threading.Lock()


def _skip_path(path: str) -> bool:
    if path in _SKIP_PATHS:
        return True
    return any(path.startswith(p) for p in _SKIP_PREFIXES)


def _get_key(request) -> str:
    """Per-user when possible, else per-IP."""
    user_id = request.path_params.get("user_id", "").strip()
    if user_id:
        return f"user:{user_id}"
    uid_header = request.headers.get("X-User-Id", "").strip()
    if uid_header:
        return f"user:{uid_header}"
    host = request.client.host if request.client else "unknown"
    return f"ip:{host}"


def _clean_old(ts_list: list[float]) -> None:
    cutoff = time.monotonic() - RATE_LIMIT_WINDOW_SEC
    while ts_list and ts_list[0] < cutoff:
        ts_list.pop(0)


def is_rate_limited(request) -> bool:
    """Returns True if request should be rejected (over limit)."""
    path = getattr(getattr(request, "url", None), "path", "") or ""
    if _skip_path(path):
        return False
    key = _get_key(request)
    now = time.monotonic()
    with _lock:
        _clean_old(_window[key])
        if len(_window[key]) >= RATE_LIMIT_REQUESTS:
            return True
        _window[key].append(now)
    return False
