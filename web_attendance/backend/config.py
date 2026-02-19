# config.py
import os
from pathlib import Path
from pydantic_settings import BaseSettings

BASE_DIR = Path(__file__).resolve().parent
MODELS_DIR = BASE_DIR / "models"
DEFAULT_DB = BASE_DIR / "attendance.db"


class Settings(BaseSettings):
    host: str = "0.0.0.0"
    port: int = 8000
    database_url: str = ""
    face_model_path: str = ""
    similarity_threshold: float = 0.6
    max_images_per_register: int = 5
    face_cascade_path: str = ""
    # 미등록 얼굴 (C+B 방식)
    cluster_similarity_threshold: float = 0.88  # 같은 사람으로 묶을 최소 유사도 (높을수록 엄격)
    unknown_min_face_size: int = 60  # 저장할 최소 얼굴 크기 (w,h) - 품질 보장
    unknown_consecutive_frames: int = 4  # 연속 N프레임 미등록 시에만 저장

    class Config:
        env_file = ".env"
        extra = "ignore"

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        if not self.database_url:
            self.database_url = f"sqlite+aiosqlite:///{DEFAULT_DB.as_posix()}"
        if not self.face_model_path:
            self.face_model_path = str(MODELS_DIR / "mobilefacenet.tflite")
        if not self.face_cascade_path:
            import cv2
            self.face_cascade_path = str(Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml")


settings = Settings()
