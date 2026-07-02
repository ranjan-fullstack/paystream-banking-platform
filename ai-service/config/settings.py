from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    GROQ_API_KEY: str = ""  # loaded from .env — never hardcode here, this file is git-tracked
    GROQ_MODEL: str = "llama-3.1-8b-instant"
    CHROMA_PERSIST_DIR: str = "./chroma_db"
    PAYSTREAM_API_BASE: str = "http://localhost:9000"
    AI_SERVICE_PORT: int = 9013
    LOG_LEVEL: str = "INFO"

    class Config:
        env_file = ".env"


settings = Settings()
