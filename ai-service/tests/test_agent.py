from unittest.mock import patch, MagicMock

import pytest

from models.schemas import AgentRequest
from services.banking_agent_service import BankingAgentService


def _agent_action(tool: str, tool_input):
    action = MagicMock()
    action.tool = tool
    action.tool_input = tool_input
    return action


@pytest.mark.asyncio
@patch("services.banking_agent_service.banking_agent_executor")
async def test_balance_query(mock_executor):
    mock_executor.invoke.return_value = {
        "output": "Your account balance is Rs 45,320.00.",
        "intermediate_steps": [
            (_agent_action("get_account_info", {"account_number": "1234567890123456"}), "{...}"),
        ],
    }

    service = BankingAgentService()
    request = AgentRequest(
        customer_id="CIF001234",
        account_number="1234567890123456",
        query="What is my account balance?",
    )
    response = await service.chat(request)

    assert "balance" in response.response.lower()
    assert "get_account_info" in response.tools_used
    assert response.requires_confirmation is False


@pytest.mark.asyncio
@patch("services.banking_agent_service.banking_agent_executor")
async def test_payment_status_query(mock_executor):
    mock_executor.invoke.return_value = {
        "output": "NEFT/REF123 is COMPLETED, settled on 2026-01-01.",
        "intermediate_steps": [
            (_agent_action("get_payment_status", {"reference_number": "REF123", "payment_mode": "NEFT"}), "{...}"),
        ],
    }

    service = BankingAgentService()
    request = AgentRequest(
        customer_id="CIF001234",
        account_number="1234567890123456",
        query="What is status of NEFT/REF123?",
    )
    response = await service.chat(request)

    assert "COMPLETED" in response.response
    assert "get_payment_status" in response.tools_used


@pytest.mark.asyncio
@patch("services.banking_agent_service.banking_agent_executor")
async def test_rbi_rules_query(mock_executor):
    mock_executor.invoke.return_value = {
        "output": "The RTGS minimum amount is Rs 2,00,000, with no maximum limit.",
        "intermediate_steps": [
            (_agent_action("query_rbi_knowledge", {"query": "RTGS minimum amount"}), "guideline text"),
        ],
    }

    service = BankingAgentService()
    request = AgentRequest(
        customer_id="CIF001234",
        account_number="1234567890123456",
        query="What is the RTGS minimum amount?",
    )
    response = await service.chat(request)

    assert "2,00,000" in response.response
    assert "query_rbi_knowledge" in response.tools_used
    assert response.requires_confirmation is False


@pytest.mark.asyncio
@patch("services.banking_agent_service.banking_agent_executor")
async def test_upi_limit_query(mock_executor):
    mock_executor.invoke.return_value = {
        "output": "No — the UPI per-transaction limit is Rs 1,00,000, so Rs 2 lakh via UPI is not possible. Consider IMPS or RTGS instead.",
        "intermediate_steps": [
            (_agent_action("query_rbi_knowledge", {"query": "UPI transaction limit"}), "guideline text"),
        ],
    }

    service = BankingAgentService()
    request = AgentRequest(
        customer_id="CIF001234",
        account_number="1234567890123456",
        query="Can I send Rs 2 lakh via UPI?",
    )
    response = await service.chat(request)

    # Phrased as a question — must NOT be treated as a live transfer instruction.
    assert response.requires_confirmation is False
    mock_executor.invoke.assert_called_once()
    assert "1,00,000" in response.response


@pytest.mark.asyncio
@patch("services.banking_agent_service.banking_agent_executor")
async def test_payment_initiation_requires_confirmation(mock_executor):
    service = BankingAgentService()
    request = AgentRequest(
        customer_id="CIF009999",
        account_number="1234567890123456",
        query="Send Rs 50000 to account 9876543210987654",
    )
    response = await service.chat(request)

    assert response.requires_confirmation is True
    assert response.confirmation_details["amount"] == 50000.0
    mock_executor.invoke.assert_not_called()
