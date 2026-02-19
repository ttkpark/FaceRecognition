# database.py
import json
from datetime import datetime
from typing import List, Optional

from sqlalchemy import Column, Integer, BigInteger, String, Text, DateTime, ForeignKey, select, delete
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import declarative_base

from config import settings

_db_path = str(settings.database_url).replace("sqlite+aiosqlite:///", "")
engine = create_async_engine(f"sqlite+aiosqlite:///{_db_path}", echo=False)
AsyncSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
Base = declarative_base()


class PersonModel(Base):
    __tablename__ = "persons"
    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String(255), nullable=False)
    embedding = Column(Text, nullable=False)
    createdAt = Column(DateTime, default=datetime.utcnow)

    def get_embeddings(self) -> List[List[float]]:
        return json.loads(self.embedding)


class AttendanceModel(Base):
    __tablename__ = "attendances"
    id = Column(Integer, primary_key=True, autoincrement=True)
    personId = Column(Integer, ForeignKey("persons.id", ondelete="CASCADE"), nullable=False)
    timestamp = Column(BigInteger, nullable=False)
    date = Column(String(10), nullable=False)


class UnknownFaceModel(Base):
    """미등록 얼굴 임시 저장 (클러스터링 후 관리자가 이름 부여)"""
    __tablename__ = "unknown_faces"
    id = Column(Integer, primary_key=True, autoincrement=True)
    embedding = Column(Text, nullable=False)  # JSON array
    thumbnail = Column(Text, nullable=True)  # base64 JPEG
    firstSeen = Column(BigInteger, nullable=False)
    lastSeen = Column(BigInteger, nullable=False)
    frameCount = Column(Integer, default=1)
    clusterId = Column(Integer, nullable=True)  # 클러스터링 후 할당 (0=미처리)

    def get_embedding(self) -> List[float]:
        return json.loads(self.embedding)


async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


async def get_db():
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()
