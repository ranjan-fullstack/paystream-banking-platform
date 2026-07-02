import logging
from langchain.tools import StructuredTool
from pydantic import BaseModel, Field
from services.rag_service import rag_service

logger = logging.getLogger(__name__)


class RBIKnowledgeInput(BaseModel):
    query: str = Field(description="Natural language question about RBI/NPCI banking rules and regulations")


def query_rbi_knowledge(query: str) -> str:
    """Query RBI and NPCI guidelines for banking rules and regulations."""
    try:
        chunks = rag_service.query_rbi_guidelines(query, k=3)
        if not chunks:
            return "No relevant RBI guidelines found for this query."
        return "\n\n".join(chunks)
    except Exception as e:
        logger.error(f"rbi_knowledge_tool error: {e}")
        return f"Unable to query RBI knowledge base: {e}"


rbi_knowledge_tool = StructuredTool.from_function(
    func=query_rbi_knowledge,
    name="query_rbi_knowledge",
    description="Query RBI and NPCI guidelines for banking rules and regulations",
    args_schema=RBIKnowledgeInput,
)
