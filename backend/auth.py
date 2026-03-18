# auth.py - JWT-based authentication for API
from __future__ import annotations

import logging
import re
from typing import Optional

import jwt
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from config import AUTH_DISABLED, JWT_SECRET

logger = logging.getLogger(__name__)

security = HTTPBearer(auto_error=False)
DEV_USER_ID_HEADER = "X-User-Id"

USER_ID_MIN_LEN = 1
USER_ID_MAX_LEN = 80
USER_ID_PATTERN = re.compile(r"^[a-zA-Z0-9_-]+$")


def validate_user_id_format(user_id: str) -> bool:
    """Valid format: 1–80 chars, only letters, digits, underscore, hyphen."""
    if not user_id or len(user_id) < USER_ID_MIN_LEN or len(user_id) > USER_ID_MAX_LEN:
        return False
    return bool(USER_ID_PATTERN.fullmatch(user_id))


def get_user_id_from_token(credentials: Optional[HTTPAuthorizationCredentials]) -> Optional[str]:
    if not JWT_SECRET:
        logger.warning("JWT_SECRET not set; auth will reject all requests unless AUTH_DISABLED=1")
        return None
    if not credentials or credentials.scheme != "Bearer":
        return None
    try:
        payload = jwt.decode(
            credentials.credentials,
            JWT_SECRET,
            algorithms=["HS256"],
            options={"require": ["exp", "sub"]},
        )
        user_id = payload.get("sub")
        return str(user_id).strip() if user_id else None
    except jwt.ExpiredSignatureError:
        logger.debug("JWT expired")
        return None
    except jwt.InvalidTokenError as e:
        logger.debug("Invalid JWT: %s", e)
        return None


def require_user_id(
    request: Request,
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security),
) -> str:
    """
    Dependency: returns authenticated user_id.
    - When AUTH_DISABLED=1 (dev): reads user_id from X-User-Id header.
    - Otherwise: validates JWT Bearer and returns 'sub' claim.
    Raises 401 if user_id cannot be determined.
    """
    if AUTH_DISABLED:
        dev_uid = request.headers.get(DEV_USER_ID_HEADER, "").strip()
        if dev_uid:
            if not validate_user_id_format(dev_uid):
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Invalid user_id format (use letters, digits, underscore, hyphen; 1–80 chars)",
                )
            return dev_uid
        path_uid = request.path_params.get("user_id", "").strip()
        if path_uid:
            if not validate_user_id_format(path_uid):
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Invalid user_id format (use letters, digits, underscore, hyphen; 1–80 chars)",
                )
            return path_uid
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="When AUTH_DISABLED=1, provide X-User-Id header or user_id in path",
        )
    if not JWT_SECRET:
        # Dev/first-run fallback:
        # If JWT_SECRET is missing (common on fresh Render deploys), the backend would otherwise
        # reject every request with 503, preventing the app from even loading.
        # We accept the {user_id} from the path when it is valid. When JWT_SECRET is configured,
        # normal JWT auth applies.
        dev_uid = request.headers.get(DEV_USER_ID_HEADER, "").strip()
        if dev_uid and validate_user_id_format(dev_uid):
            return dev_uid

        path_uid = request.path_params.get("user_id", "").strip()
        if path_uid and validate_user_id_format(path_uid):
            return path_uid

        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Authentication not configured (set JWT_SECRET) or provide a valid user_id in path",
        )
    user_id = get_user_id_from_token(credentials)
    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid authorization",
        )
    if not validate_user_id_format(user_id):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid user_id format (use letters, digits, underscore, hyphen; 1–80 chars)",
        )
    return user_id


def validate_path_user_id(
    request: Request,
    auth_user_id: str = Depends(require_user_id),
) -> str:
    """Dependency for routes with {user_id} in path: ensures path matches authenticated user."""
    path_user_id = request.path_params.get("user_id", "")
    if not validate_user_id_format(path_user_id):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid user_id format (use letters, digits, underscore, hyphen; 1–80 chars)",
        )
    if path_user_id != auth_user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")
    return path_user_id
