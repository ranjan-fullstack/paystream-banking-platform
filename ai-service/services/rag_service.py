import logging
from services.llm_service import llm_service
from rag.vector_store import VectorStore
from rag.document_loader import DocumentLoader, RBI_GUIDELINES_COLLECTION, TRANSACTION_HISTORY_COLLECTION

logger = logging.getLogger(__name__)


class RAGService:
    def __init__(self):
        self.vector_store = VectorStore(llm_service.get_embeddings())
        self.document_loader = DocumentLoader(self.vector_store)

    def load_knowledge_base(self):
        return self.document_loader.load_knowledge_base()

    def query_rbi_guidelines(self, query: str, k: int = 3) -> list:
        try:
            results = self.vector_store.similarity_search(RBI_GUIDELINES_COLLECTION, query, k=k)
            return [doc.page_content for doc in results]
        except Exception as e:
            logger.error(f"RAG query error (rbi_guidelines): {e}")
            return []

    def index_transactions(self, transactions: list):
        return self.document_loader.index_transactions(transactions)

    def query_transaction_history(self, query: str, k: int = 5) -> list:
        try:
            results = self.vector_store.similarity_search(TRANSACTION_HISTORY_COLLECTION, query, k=k)
            return [doc.page_content for doc in results]
        except Exception as e:
            logger.error(f"RAG query error (transaction_history): {e}")
            return []


rag_service = RAGService()
