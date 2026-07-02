import logging
from collections import defaultdict

from models.schemas import AdvisoryRequest, AdvisoryResponse
from services.llm_service import llm_service
from services.rag_service import rag_service

logger = logging.getLogger(__name__)

ADVISORY_PROMPT = """You are a financial advisor at PayStream Banking Platform.

CUSTOMER QUERY: {query}

ACCOUNT INFO:
- Current Balance: Rs {account_balance}

TRANSACTION ANALYSIS:
{transaction_summary}

RELEVANT RBI GUIDELINES:
{rbi_context}

Provide personalized financial advice. Include:
1. Direct answer to customer's query
2. Spending pattern insights from their history
3. Recommended payment rail for their use case with RBI limits
4. Any relevant warnings or tips

Keep response professional, accurate, and helpful.
Cite RBI limits and guidelines where relevant.
"""


class FinancialAdvisoryService:
    async def get_advice(self, request: AdvisoryRequest) -> AdvisoryResponse:
        rbi_chunks = rag_service.query_rbi_guidelines(request.query, k=3)
        rbi_context = "\n\n".join(rbi_chunks) if rbi_chunks else "No specific guidelines retrieved."

        if request.transaction_history:
            rag_service.index_transactions(request.transaction_history)

        transaction_summary = self._analyze_transactions(request.transaction_history)

        prompt = ADVISORY_PROMPT.format(
            query=request.query,
            account_balance=request.account_balance,
            transaction_summary=transaction_summary,
            rbi_context=rbi_context,
        )

        advice = await llm_service.generate(prompt)

        recommended_amount = self._extract_amount_hint(request)
        recommended_rail = self._recommend_payment_rail(recommended_amount) if recommended_amount is not None else None

        insights = self._build_insights(transaction_summary)

        return AdvisoryResponse(
            advice=advice,
            insights=insights,
            recommended_payment_rail=recommended_rail,
            rag_sources=rbi_chunks,
        )

    def _analyze_transactions(self, transactions: list) -> str:
        if not transactions:
            return "No transaction history provided."

        by_mode = defaultdict(list)
        beneficiary_counts = defaultdict(int)
        failed_count = 0

        for txn in transactions:
            mode = txn.get("payment_mode", txn.get("mode", "UNKNOWN"))
            amount = txn.get("amount", 0)
            status = txn.get("status", "UNKNOWN")
            beneficiary = txn.get("beneficiary_account", txn.get("beneficiary", "UNKNOWN"))

            by_mode[mode].append(amount)
            beneficiary_counts[beneficiary] += 1
            if str(status).upper() in ("FAILED", "REJECTED", "RETURNED"):
                failed_count += 1

        lines = []
        for mode, amounts in by_mode.items():
            total = sum(amounts)
            avg = total / len(amounts) if amounts else 0
            lines.append(f"- {mode}: {len(amounts)} transactions, total Rs {total:.2f}, average Rs {avg:.2f}")

        top_beneficiary = max(beneficiary_counts, key=beneficiary_counts.get) if beneficiary_counts else None
        if top_beneficiary:
            lines.append(f"- Most frequent beneficiary: {top_beneficiary} ({beneficiary_counts[top_beneficiary]} transfers)")

        total_txns = len(transactions)
        failure_rate = (failed_count / total_txns * 100) if total_txns else 0
        lines.append(f"- Failed/rejected rate: {failure_rate:.1f}% ({failed_count}/{total_txns})")

        return "\n".join(lines)

    def _build_insights(self, transaction_summary: str) -> list:
        return [line.strip("- ") for line in transaction_summary.split("\n") if line.startswith("-")]

    def _extract_amount_hint(self, request: AdvisoryRequest):
        if request.transaction_history:
            amounts = [txn.get("amount") for txn in request.transaction_history if txn.get("amount")]
            if amounts:
                return sum(amounts) / len(amounts)
        return None

    def _recommend_payment_rail(self, amount: float) -> str:
        if amount < 100000:
            return "UPI"
        if amount < 200000:
            return "IMPS"
        if amount <= 1000000:
            return "RTGS"
        return "NEFT"


advisory_service = FinancialAdvisoryService()
