from fastapi import APIRouter, HTTPException

from models.schemas import AgentRequest, AgentResponse
from services.banking_agent_service import banking_agent_service

router = APIRouter(prefix="/api/v1/ai/agent", tags=["Banking Agent"])


@router.post("/chat", response_model=AgentResponse)
async def chat(request: AgentRequest):
    try:
        return await banking_agent_service.chat(request)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Agent chat failed: {e}")


@router.delete("/conversation/{customer_id}")
async def clear_conversation(customer_id: str):
    cleared = banking_agent_service.clear_conversation(customer_id)
    return {"customer_id": customer_id, "cleared": cleared}
