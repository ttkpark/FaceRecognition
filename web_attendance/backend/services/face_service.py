# services/face_service.py
from typing import List, Optional, Tuple
import numpy as np
import cv2
from PIL import Image
import io

from config import settings
from ml.face_detector import FaceDetector
from ml.face_embedding import extract_embedding, is_model_loaded
from ml.face_matcher import find_best_match

_detector = None


def get_detector() -> FaceDetector:
    global _detector
    if _detector is None:
        _detector = FaceDetector()
    return _detector


def image_bytes_to_bgr(data: bytes) -> np.ndarray:
    arr = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        pil = Image.open(io.BytesIO(data)).convert("RGB")
        img = cv2.cvtColor(np.array(pil), cv2.COLOR_RGB2BGR)
    return img


def detect_and_embed_single(image_bgr: np.ndarray) -> Optional[List[float]]:
    det = get_detector()
    faces = det.detect_faces(image_bgr)
    if not faces:
        return None
    crop = det.crop_face(image_bgr, faces[0])
    return extract_embedding(crop)


def detect_and_embed_all(image_bgr: np.ndarray) -> List[List[float]]:
    det = get_detector()
    faces = det.detect_faces(image_bgr)
    result = []
    for box in faces:
        crop = det.crop_face(image_bgr, box)
        emb = extract_embedding(crop)
        if emb is not None:
            result.append(emb)
    return result


def get_unknown_face_candidate(
    image_bgr: np.ndarray,
    min_face_size: int = 60,
) -> Optional[Tuple[List[float], np.ndarray]]:
    """
    미등록 얼굴 후보 추출. 품질 필터 적용.
    반환: (embedding, crop_bgr) 또는 None
    - 얼굴 크기(w,h)가 min_face_size 이상일 때만
    - 가장 큰 얼굴 1개만 (정확도 우선)
    """
    det = get_detector()
    faces = det.detect_faces(image_bgr)
    if not faces:
        return None
    # 가장 큰 얼굴 선택
    best = max(faces, key=lambda b: b[2] * b[3])
    x, y, w, h = best
    if w < min_face_size or h < min_face_size:
        return None
    crop = det.crop_face(image_bgr, best)
    emb = extract_embedding(crop)
    if emb is None:
        return None
    return (emb, crop)


def recognize_and_match(
    image_bgr: np.ndarray,
    persons_with_embeddings: List[Tuple[int, str, List[List[float]]]],
) -> Optional[Tuple[int, str, float]]:
    emb = detect_and_embed_single(image_bgr)
    if emb is None:
        return None
    return find_best_match(emb, persons_with_embeddings, settings.similarity_threshold)


def is_ml_ready() -> bool:
    return is_model_loaded()
