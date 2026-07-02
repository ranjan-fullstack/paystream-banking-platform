import json
from unittest.mock import AsyncMock, patch

import pytest

from models.schemas import FraudAnalysisRequest, PaymentMode, RiskLevel
from services.fraud_ai_service import fraud_ai_service


def _request(**overrides) -> FraudAnalysisRequest:
    defaults = dict(
        transaction_reference="NEFT20260101001",
        account_number="1234567890123456",
        amount=25000.0,
        payment_mode=PaymentMode.NEFT,
        beneficiary_account="9876543210987654",
        beneficiary_ifsc="HDFC0001234",
        transaction_time="2026-01-01T11:00:00",
        recent_transactions=[],
        customer_risk_rating="LOW",
    )
    defaults.update(overrides)
    return FraudAnalysisRequest(**defaults)


def _mock_llm_json(payload: dict):
    return AsyncMock(return_value=json.dumps(payload))


@pytest.mark.asyncio
@patch("services.fraud_ai_service.rag_service.query_rbi_guidelines", return_value=["NEFT guideline chunk"])
@patch("services.fraud_ai_service.llm_service.generate")
async def test_low_risk_transaction(mock_generate, _mock_rag):
    mock_generate.side_effect = _mock_llm_json({
        "risk_score": 5,
        "risk_level": "LOW",
        "fraud_indicators": [],
        "recommendation": "APPROVE",
        "explanation": "Normal amount during business hours, no anomalies detected.",
    })

    request = _request(amount=25000.0, transaction_time="2026-01-01T11:00:00")
    response = await fraud_ai_service.analyze_transaction(request)

    assert response.risk_level == RiskLevel.LOW
    assert response.recommendation == "APPROVE"
    assert response.rag_context_used == ["NEFT guideline chunk"]


@pytest.mark.asyncio
@patch("services.fraud_ai_service.rag_service.query_rbi_guidelines", return_value=["Velocity check guideline"])
@patch("services.fraud_ai_service.llm_service.generate")
async def test_high_risk_velocity(mock_generate, _mock_rag):
    mock_generate.side_effect = _mock_llm_json({
        "risk_score": 88,
        "risk_level": "HIGH",
        "fraud_indicators": ["VELOCITY_CHECK: 6 transactions in 5 minutes"],
        "recommendation": "REVIEW",
        "explanation": "Six transactions were initiated within a 5-minute window, exceeding the velocity threshold.",
    })

    recent = [
        {"amount": 5000, "transaction_time": f"2026-01-01T10:0{i}:00"} for i in range(6)
    ]
    request = _request(amount=5000.0, recent_transactions=recent)
    response = await fraud_ai_service.analyze_transaction(request)

    assert response.risk_level == RiskLevel.HIGH
    assert any("VELOCITY" in indicator.upper() for indicator in response.fraud_indicators)


@pytest.mark.asyncio
@patch("services.fraud_ai_service.rag_service.query_rbi_guidelines", return_value=["RTGS odd-hour guideline"])
@patch("services.fraud_ai_service.llm_service.generate")
async def test_odd_hours_rtgs(mock_generate, _mock_rag):
    mock_generate.side_effect = _mock_llm_json({
        "risk_score": 55,
        "risk_level": "MEDIUM",
        "fraud_indicators": ["ODD_HOURS: RTGS initiated at 23:00"],
        "recommendation": "REVIEW",
        "explanation": "RTGS transfer initiated outside normal business hours.",
    })

    request = _request(
        payment_mode=PaymentMode.RTGS,
        amount=300000.0,
        transaction_time="2026-01-01T23:00:00",
    )
    response = await fraud_ai_service.analyze_transaction(request)

    assert response.risk_level == RiskLevel.MEDIUM
    assert any("ODD_HOURS" in indicator.upper() for indicator in response.fraud_indicators)


@pytest.mark.asyncio
@patch("services.fraud_ai_service.rag_service.query_rbi_guidelines", return_value=["Structuring guideline"])
@patch("services.fraud_ai_service.llm_service.generate")
async def test_structuring_detection(mock_generate, _mock_rag):
    mock_generate.side_effect = _mock_llm_json({
        "risk_score": 90,
        "risk_level": "HIGH",
        "fraud_indicators": ["STRUCTURING: multiple transactions just below Rs 10 lakh"],
        "recommendation": "BLOCK",
        "explanation": "Three transactions of Rs 9.9 lakh each were made in quick succession, consistent with structuring to evade the NEFT reporting threshold.",
    })

    recent = [{"amount": 990000, "transaction_time": f"2026-01-01T10:0{i}:00"} for i in range(3)]
    request = _request(amount=990000.0, recent_transactions=recent)
    response = await fraud_ai_service.analyze_transaction(request)

    assert response.risk_level == RiskLevel.HIGH
    assert response.recommendation == "BLOCK"
    assert any("STRUCTURING" in indicator.upper() for indicator in response.fraud_indicators)
