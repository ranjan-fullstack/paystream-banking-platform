import httpx
import logging
from langchain.tools import StructuredTool
from pydantic import BaseModel, Field
from config.settings import settings

logger = logging.getLogger(__name__)


class PaymentStatusInput(BaseModel):
    reference_number: str = Field(description="The payment reference number")
    payment_mode: str = Field(description="Payment mode: NEFT, RTGS, IMPS, or UPI")


def get_payment_status(reference_number: str, payment_mode: str) -> str:
    """Get status of a specific payment by reference number."""
    mode_path = payment_mode.strip().lower()
    url = f"{settings.PAYSTREAM_API_BASE}/api/v1/{mode_path}/{reference_number}"
    try:
        response = httpx.get(url, timeout=10.0)
        response.raise_for_status()
        return str(response.json())
    except httpx.HTTPError as e:
        logger.error(f"payment_tool error calling {url}: {e}")
        return f"Unable to retrieve payment status for {reference_number} ({payment_mode}): {e}"


payment_tool = StructuredTool.from_function(
    func=get_payment_status,
    name="get_payment_status",
    description="Get status of a specific payment by reference number",
    args_schema=PaymentStatusInput,
)
