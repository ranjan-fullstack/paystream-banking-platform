import chromadb
from langchain_community.vectorstores import Chroma
from config.settings import settings
import logging

logger = logging.getLogger(__name__)


class VectorStore:
    def __init__(self, embeddings):
        self.client = chromadb.PersistentClient(
            path=settings.CHROMA_PERSIST_DIR,
        )
        self.embeddings = embeddings

    def get_or_create_collection(self, collection_name: str):
        return Chroma(
            client=self.client,
            collection_name=collection_name,
            embedding_function=self.embeddings,
        )

    def similarity_search(self, collection_name: str, query: str, k: int = 3):
        store = self.get_or_create_collection(collection_name)
        return store.similarity_search(query, k=k)

    def add_texts(self, collection_name: str, texts: list, metadatas: list = None):
        store = self.get_or_create_collection(collection_name)
        return store.add_texts(texts=texts, metadatas=metadatas)
