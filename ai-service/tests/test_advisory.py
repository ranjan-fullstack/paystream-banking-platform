from unittest.mock import AsyncMock, patch

import pytest

from models.schemas import AdvisoryRequest
from services.advisory_service import advisory_service


@pytest.mark.parametrize(
    "amount,expected_rail",
    [
        (5000, "UPI"),
        (150000, "IMPS"),
        (500000, "RTGS"),
        (1500000, "NEFT"),
    ],
)
def test_payment_rail_recommendation(amount, expected_rail):
    assert advisory_service._recommend_payment_rail(amount) == expected_rail


def test_spending_analysis():
    transactions = [
        {"payment_mode": "UPI", "amount": 500, "status": "COMPLETED", "beneficiary_account": "A1"},
        {"payment_mode": "UPI", "amount": 1500, "status": "COMPLETED", "beneficiary_account": "A1"},
        {"payment_mode": "NEFT", "amount": 20000, "status": "FAILED", "beneficiary_account": "A2"},
    ]

    summary = advisory_service._analyze_transactions(transactions)

    assert "UPI" in summary
    assert "NEFT" in summary
    assert "A1" in summary  # most frequent beneficiary
    assert "33.3%" in summary  # 1 of 3 failed


@pytest.mark.asyncio
@patch("services.advisory_service.rag_service.index_transactions")
@patch("services.advisory_service.rag_service.query_rbi_guidelines", return_value=["RTGS rejection reasons guideline"])
@patch("services.advisory_service.llm_service.generate", new_callable=AsyncMock)
async def test_rbi_guideline_query(mock_generate, _mock_rag, _mock_index):
    mock_generate.return_value = (
        "Your RTGS transfer was likely rejected because the amount was below the "
        "RBI-mandated minimum of Rs 2,00,000, or the beneficiary IFSC was invalid."
    )

    request = AdvisoryRequest(
        customer_id="CIF001234",
        account_number="1234567890123456",
        query="Why was my RTGS rejected?",
        transaction_history=[],
        account_balance=100000.0,
    )
    response = await advisory_service.get_advice(request)

    assert "RTGS" in response.advice
    assert response.rag_sources == ["RTGS rejection reasons guideline"]
