from langchain_groq import ChatGroq
from langchain_ollama import OllamaEmbeddings  # kept for embeddings — ChromaDB/RAG stays local
from config.settings import settings
import logging

logger = logging.getLogger(__name__)


class LLMService:
    def __init__(self):
        self.llm = ChatGroq(
            api_key=settings.GROQ_API_KEY,
            model=settings.GROQ_MODEL,
            temperature=0.1,  # low temp for consistent banking responses
        )
        # Embeddings still run against local Ollama — no external cost, no API key needed.
        self.embeddings = OllamaEmbeddings(
            model="nomic-embed-text",
        )

    async def generate(self, prompt: str) -> str:
        try:
            response = self.llm.invoke(prompt)
            # ChatGroq (a chat model) returns an AIMessage; unwrap to plain text
            # so callers (fraud/advisory services) keep seeing a str as before.
            return response.content if hasattr(response, "content") else response
        except Exception as e:
            logger.error(f"LLM generation error: {e}")
            raise

    def get_embeddings(self):
        return self.embeddings


llm_service = LLMService()
