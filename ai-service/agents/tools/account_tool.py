import httpx
import logging
from langchain.tools import StructuredTool
from pydantic import BaseModel, Field
from config.settings import settings

logger = logging.getLogger(__name__)


class AccountInfoInput(BaseModel):
    account_number: str = Field(description="The bank account number to look up")


def get_account_info(account_number: str) -> str:
    """Get account balance and details for a customer."""
    url = f"{settings.PAYSTREAM_API_BASE}/api/v1/accounts/number/{account_number}"
    try:
        response = httpx.get(url, timeout=10.0)
        response.raise_for_status()
        return str(response.json())
    except httpx.HTTPError as e:
        logger.error(f"account_tool error calling {url}: {e}")
        return f"Unable to retrieve account info for {account_number}: {e}"


account_tool = StructuredTool.from_function(
    func=get_account_info,
    name="get_account_info",
    description="Get account balance and details for a customer",
    args_schema=AccountInfoInput,
)
