from services.llm_service import llm_service


def get_embeddings():
    """Returns the shared Ollama embeddings instance (nomic-embed-text)."""
    return llm_service.get_embeddings()
