from pydantic import BaseModel
from typing import Optional, List
from enum import Enum


class PaymentMode(str, Enum):
    NEFT = "NEFT"
    RTGS = "RTGS"
    IMPS = "IMPS"
    UPI = "UPI"


class RiskLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


# FRAUD DETECTION
class FraudAnalysisRequest(BaseModel):
    transaction_reference: str
    account_number: str
    amount: float
    payment_mode: PaymentMode
    beneficiary_account: str
    beneficiary_ifsc: Optional[str] = None
    transaction_time: str
    recent_transactions: List[dict] = []
    customer_risk_rating: Optional[str] = "LOW"


class FraudAnalysisResponse(BaseModel):
    transaction_reference: str
    risk_level: RiskLevel
    risk_score: float  # 0.0 to 100.0
    fraud_indicators: List[str]
    recommendation: str  # APPROVE / REVIEW / BLOCK
    explanation: str  # LLM natural language explanation
    rag_context_used: List[str]  # which RBI guidelines were referenced


# BANKING AGENT
class AgentRequest(BaseModel):
    customer_id: str
    account_number: str
    query: str  # natural language query from customer
    conversation_history: List[dict] = []  # for multi-turn conversation
    jwt_token: Optional[str] = None  # forwarded as Authorization: Bearer <token> to PayStream APIs


class AgentResponse(BaseModel):
    response: str
    actions_taken: List[str]
    tools_used: List[str]
    requires_confirmation: bool = False
    confirmation_details: Optional[dict] = None


# FINANCIAL ADVISORY
class AdvisoryRequest(BaseModel):
    customer_id: str
    account_number: str
    query: str
    transaction_history: List[dict] = []
    account_balance: float = 0.0


class AdvisoryResponse(BaseModel):
    advice: str
    insights: List[str]
    recommended_payment_rail: Optional[str] = None
    rag_sources: List[str] = []
