import httpx
import logging
from langchain.tools import StructuredTool
from pydantic import BaseModel, Field
from config.settings import settings
from agents.tools.auth_context import auth_headers

logger = logging.getLogger(__name__)


class TransactionHistoryInput(BaseModel):
    account_number: str = Field(description="The bank account number to look up")
    limit: int = Field(default=10, description="Maximum number of recent transactions to return")


def get_transaction_history(account_number: str, limit: int = 10) -> str:
    """Get recent transaction history for an account."""
    url = f"{settings.PAYSTREAM_API_BASE}/api/v1/transactions/account/{account_number}"
    try:
        response = httpx.get(url, params={"limit": limit}, headers=auth_headers(), timeout=10.0)
        response.raise_for_status()
        return str(response.json())
    except httpx.HTTPError as e:
        logger.error(f"transaction_tool error calling {url}: {e}")
        return f"Unable to retrieve transaction history for {account_number}: {e}"


transaction_tool = StructuredTool.from_function(
    func=get_transaction_history,
    name="get_transaction_history",
    description="Get recent transaction history for an account",
    args_schema=TransactionHistoryInput,
)
