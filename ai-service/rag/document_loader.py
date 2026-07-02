import os
import logging
from langchain.text_splitter import RecursiveCharacterTextSplitter

logger = logging.getLogger(__name__)

KNOWLEDGE_BASE_DIR = os.path.join(os.path.dirname(__file__), "knowledge_base")
RBI_GUIDELINES_COLLECTION = "rbi_guidelines"
TRANSACTION_HISTORY_COLLECTION = "transaction_history"


class DocumentLoader:
    def __init__(self, vector_store):
        self.vector_store = vector_store
        self.splitter = RecursiveCharacterTextSplitter(
            chunk_size=500,
            chunk_overlap=50,
        )

    def load_knowledge_base(self):
        """Load and index all .txt files from rag/knowledge_base/ into ChromaDB."""
        if not os.path.isdir(KNOWLEDGE_BASE_DIR):
            logger.warning(f"Knowledge base directory not found: {KNOWLEDGE_BASE_DIR}")
            return 0

        texts = []
        metadatas = []
        for filename in sorted(os.listdir(KNOWLEDGE_BASE_DIR)):
            if not filename.endswith(".txt"):
                continue
            path = os.path.join(KNOWLEDGE_BASE_DIR, filename)
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()
            chunks = self.splitter.split_text(content)
            for chunk in chunks:
                texts.append(chunk)
                metadatas.append({"source": filename})

        if texts:
            self.vector_store.add_texts(RBI_GUIDELINES_COLLECTION, texts, metadatas)
            logger.info(f"Indexed {len(texts)} chunks from {KNOWLEDGE_BASE_DIR} into '{RBI_GUIDELINES_COLLECTION}'")
        return len(texts)

    def index_transactions(self, transactions: list):
        """Index a list of transaction dicts into the transaction_history collection."""
        if not transactions:
            return 0

        texts = []
        metadatas = []
        for txn in transactions:
            text = (
                f"Transaction {txn.get('reference', txn.get('transaction_reference', 'N/A'))}: "
                f"amount Rs {txn.get('amount', 'N/A')}, "
                f"mode {txn.get('payment_mode', txn.get('mode', 'N/A'))}, "
                f"status {txn.get('status', 'N/A')}, "
                f"beneficiary {txn.get('beneficiary_account', txn.get('beneficiary', 'N/A'))}, "
                f"time {txn.get('transaction_time', txn.get('created_at', 'N/A'))}"
            )
            texts.append(text)
            metadatas.append({"reference": txn.get("reference", txn.get("transaction_reference", ""))})

        self.vector_store.add_texts(TRANSACTION_HISTORY_COLLECTION, texts, metadatas)
        return len(texts)
