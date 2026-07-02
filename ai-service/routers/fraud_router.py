from fastapi import APIRouter, HTTPException

from models.schemas import FraudAnalysisRequest, FraudAnalysisResponse
from services.fraud_ai_service import fraud_ai_service
from config.settings import settings

router = APIRouter(prefix="/api/v1/ai/fraud", tags=["Fraud Detection"])


@router.post("/analyze", response_model=FraudAnalysisResponse)
async def analyze_transaction(request: FraudAnalysisRequest):
    try:
        return await fraud_ai_service.analyze_transaction(request)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Fraud analysis failed: {e}")


@router.get("/health")
async def health():
    return {"status": "ok", "model": settings.OLLAMA_MODEL}
