from fastapi import APIRouter, Header, HTTPException

from models.schemas import AgentRequest, AgentResponse
from services.banking_agent_service import banking_agent_service

router = APIRouter(prefix="/api/v1/ai/agent", tags=["Banking Agent"])


@router.post("/chat", response_model=AgentResponse)
async def chat(request: AgentRequest, authorization: str | None = Header(default=None)):
    # The customer's JWT always comes from the inbound Authorization header
    # (forwarded by api-gateway), never from the request body — a body field
    # would let a caller simply omit or forge the token in JSON.
    if authorization and authorization.lower().startswith("bearer "):
        request.jwt_token = authorization[len("Bearer "):]
    try:
        return await banking_agent_service.chat(request)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Agent chat failed: {e}")


@router.delete("/conversation/{customer_id}")
async def clear_conversation(customer_id: str):
    cleared = banking_agent_service.clear_conversation(customer_id)
    return {"customer_id": customer_id, "cleared": cleared}
