from fastapi import APIRouter, HTTPException

from models.schemas import AdvisoryRequest, AdvisoryResponse
from services.advisory_service import advisory_service

router = APIRouter(prefix="/api/v1/ai/advisory", tags=["Financial Advisory"])


@router.post("/advice", response_model=AdvisoryResponse)
async def get_advice(request: AdvisoryRequest):
    try:
        return await advisory_service.get_advice(request)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Advisory generation failed: {e}")
