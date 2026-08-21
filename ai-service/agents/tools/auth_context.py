import contextvars
from typing import Optional

# Holds the calling customer's JWT for the lifetime of a single agent
# invocation. Tools read this to authenticate their PayStream API calls —
# it is never exposed to the LLM as a tool argument, since the model must
# not be able to see or set the token itself.
_jwt_token_var: contextvars.ContextVar[Optional[str]] = contextvars.ContextVar(
    "jwt_token", default=None
)


def set_jwt_token(token: Optional[str]) -> None:
    _jwt_token_var.set(token)


def get_jwt_token() -> Optional[str]:
    return _jwt_token_var.get()


def auth_headers() -> dict:
    token = get_jwt_token()
    return {"Authorization": f"Bearer {token}"} if token else {}
