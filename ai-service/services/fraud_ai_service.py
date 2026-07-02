import json
import logging
import re

from models.schemas import FraudAnalysisRequest, FraudAnalysisResponse, RiskLevel
from services.llm_service import llm_service
from services.rag_service import rag_service

logger = logging.getLogger(__name__)

FRAUD_ANALYSIS_PROMPT = """You are an expert banking fraud analyst at a Reserve Bank of India
regulated payment processor. Analyze this transaction for fraud risk.

TRANSACTION DETAILS:
- Reference: {transaction_reference}
- Amount: Rs {amount}
- Payment Mode: {payment_mode}
- Account: {account_number}
- Beneficiary: {beneficiary_account}
- Time: {transaction_time}
- Customer Risk Rating: {customer_risk_rating}

RECENT TRANSACTION HISTORY (last 10):
{recent_transactions}

RBI FRAUD GUIDELINES (from knowledge base):
{rag_context}

Analyze for these fraud indicators:
1. Velocity check: >5 transactions in 10 minutes
2. Amount anomaly: >3x average transaction amount
3. Odd hours: NEFT/RTGS outside business hours
4. Duplicate transaction: same amount + beneficiary within 5 mins
5. Structuring: multiple transactions just below Rs 10 lakh
6. High risk account indicators

Respond in this exact JSON format:
{{
    "risk_score": <0-100>,
    "risk_level": "<LOW|MEDIUM|HIGH|CRITICAL>",
    "fraud_indicators": ["<indicator1>", "<indicator2>"],
    "recommendation": "<APPROVE|REVIEW|BLOCK>",
    "explanation": "<detailed natural language explanation>"
}}
"""


class FraudAIService:
    async def analyze_transaction(self, request: FraudAnalysisRequest) -> FraudAnalysisResponse:
        rag_query = (
            f"fraud detection guidelines for {request.payment_mode.value} transaction "
            f"amount {request.amount} velocity structuring odd hours"
        )
        rag_context_chunks = rag_service.query_rbi_guidelines(rag_query, k=3)
        rag_context = "\n\n".join(rag_context_chunks) if rag_context_chunks else "No specific guidelines retrieved."

        prompt = FRAUD_ANALYSIS_PROMPT.format(
            transaction_reference=request.transaction_reference,
            amount=request.amount,
            payment_mode=request.payment_mode.value,
            account_number=request.account_number,
            beneficiary_account=request.beneficiary_account,
            transaction_time=request.transaction_time,
            customer_risk_rating=request.customer_risk_rating,
            recent_transactions=json.dumps(request.recent_transactions, indent=2) if request.recent_transactions else "None",
            rag_context=rag_context,
        )

        llm_output = await llm_service.generate(prompt)
        parsed = self._parse_llm_response(llm_output)

        return FraudAnalysisResponse(
            transaction_reference=request.transaction_reference,
            risk_level=parsed["risk_level"],
            risk_score=parsed["risk_score"],
            fraud_indicators=parsed["fraud_indicators"],
            recommendation=parsed["recommendation"],
            explanation=parsed["explanation"],
            rag_context_used=rag_context_chunks,
        )

    def _parse_llm_response(self, llm_output: str) -> dict:
        try:
            match = re.search(r"\{.*\}", llm_output, re.DOTALL)
            raw = match.group(0) if match else llm_output
            data = json.loads(raw)
            risk_level = RiskLevel(str(data.get("risk_level", "LOW")).upper())
            return {
                "risk_score": float(data.get("risk_score", 0.0)),
                "risk_level": risk_level,
                "fraud_indicators": list(data.get("fraud_indicators", [])),
                "recommendation": str(data.get("recommendation", "REVIEW")),
                "explanation": str(data.get("explanation", llm_output)),
            }
        except (json.JSONDecodeError, ValueError, AttributeError) as e:
            logger.error(f"Failed to parse LLM fraud response: {e}. Raw output: {llm_output}")
            return {
                "risk_score": 50.0,
                "risk_level": RiskLevel.MEDIUM,
                "fraud_indicators": ["LLM_RESPONSE_PARSE_ERROR"],
                "recommendation": "REVIEW",
                "explanation": f"Could not parse structured risk assessment from model. Raw response: {llm_output}",
            }


fraud_ai_service = FraudAIService()
