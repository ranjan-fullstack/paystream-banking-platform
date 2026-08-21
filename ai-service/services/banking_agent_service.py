import logging
import re

from models.schemas import AgentRequest, AgentResponse
from agents.banking_agent import invoke_agent

logger = logging.getLogger(__name__)

PAYMENT_INTENT_PATTERN = re.compile(
    r"\b(send|transfer|pay|initiate|remit)\b.*?\b(rs\.?|inr|rupees|₹)?\s*([\d,]+(?:\.\d+)?)",
    re.IGNORECASE,
)

# Queries phrased as questions ("Can I send...?", "What if I transfer...?") are
# informational, not a live instruction to move money — never treated as intent.
QUESTION_PATTERN = re.compile(
    r"^\s*(can|could|may|what|how|is|are|does|do|why|when|where|who|should)\b|\?\s*$",
    re.IGNORECASE,
)

# in-memory conversation store, keyed by customer_id — cleared via DELETE /conversation/{customer_id}
_conversation_store: dict[str, list] = {}


class BankingAgentService:
    async def chat(self, request: AgentRequest) -> AgentResponse:
        history = _conversation_store.setdefault(request.customer_id, list(request.conversation_history))

        payment_intent = self._detect_payment_intent(request.query)
        if payment_intent:
            history.append({"role": "user", "content": request.query})
            confirmation_message = (
                f"I found a request to transfer Rs {payment_intent['amount']}. "
                f"For your security, I cannot initiate this transfer myself — "
                f"please confirm the details below to proceed."
            )
            history.append({"role": "assistant", "content": confirmation_message})
            return AgentResponse(
                response=confirmation_message,
                actions_taken=["detected_payment_initiation_intent"],
                tools_used=[],
                requires_confirmation=True,
                confirmation_details={
                    "customer_id": request.customer_id,
                    "account_number": request.account_number,
                    "amount": payment_intent["amount"],
                    "raw_query": request.query,
                },
            )

        conversation_text = self._format_history(history)
        try:
            result = invoke_agent(
                {
                    "input": request.query,
                    "customer_id": request.customer_id,
                    "account_number": request.account_number,
                    "conversation_history": conversation_text,
                },
                jwt_token=request.jwt_token,
            )
        except Exception as e:
            logger.error(f"Banking agent execution error: {e}")
            return AgentResponse(
                response="I'm unable to process that request right now. Please try again shortly.",
                actions_taken=[],
                tools_used=[],
                requires_confirmation=False,
            )

        output = result.get("output", "")
        intermediate_steps = result.get("intermediate_steps", [])
        tools_used = []
        actions_taken = []
        for action, observation in intermediate_steps:
            tools_used.append(action.tool)
            actions_taken.append(f"{action.tool}({action.tool_input})")

        history.append({"role": "user", "content": request.query})
        history.append({"role": "assistant", "content": output})

        return AgentResponse(
            response=output,
            actions_taken=actions_taken,
            tools_used=list(dict.fromkeys(tools_used)),
            requires_confirmation=False,
        )

    def clear_conversation(self, customer_id: str) -> bool:
        return _conversation_store.pop(customer_id, None) is not None

    def _detect_payment_intent(self, query: str) -> dict | None:
        if QUESTION_PATTERN.search(query):
            return None
        match = PAYMENT_INTENT_PATTERN.search(query)
        if not match:
            return None
        amount_str = match.group(3).replace(",", "")
        try:
            amount = float(amount_str)
        except ValueError:
            return None
        return {"amount": amount}

    def _format_history(self, history: list) -> str:
        if not history:
            return "(no prior conversation)"
        lines = [f"{turn.get('role', 'user')}: {turn.get('content', '')}" for turn in history]
        return "\n".join(lines)


banking_agent_service = BankingAgentService()
