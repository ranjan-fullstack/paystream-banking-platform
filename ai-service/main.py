import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config.settings import settings
from routers import fraud_router, agent_router, advisory_router
from services.rag_service import rag_service

logging.basicConfig(level=settings.LOG_LEVEL)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="PayStream AI Service",
    version="1.0.0",
    description="AI layer for PayStream Banking Platform — Fraud Detection, Banking Agent, Financial Advisory",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(fraud_router.router)
app.include_router(agent_router.router)
app.include_router(advisory_router.router)


@app.on_event("startup")
async def startup_event():
    try:
        indexed = rag_service.load_knowledge_base()
        logger.info(f"RAG knowledge base ready ({indexed} chunks indexed).")
    except Exception as e:
        logger.error(f"Failed to initialize RAG knowledge base: {e}")


@app.get("/health")
async def health():
    return {"status": "ok"}
