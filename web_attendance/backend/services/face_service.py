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
) -> Optional[Tuple[List[float], np.ndarray, Tuple[int, int, int, int]]]:
    """
    미등록 얼굴 후보 추출. 품질 필터 적용.
    반환: (embedding, crop_bgr, box_xywh) 또는 None
    - 얼굴 크기(w,h)가 min_face_size 이상일 때만
    - 가장 큰 얼굴 1개만 (정확도 우선)
    """
    det = get_detector()
    faces = det.detect_faces(image_bgr)
    if not faces:
        return None
    best = max(faces, key=lambda b: b[2] * b[3])
    x, y, w, h = best
    if w < min_face_size or h < min_face_size:
        return None
    crop = det.crop_face(image_bgr, best)
    emb = extract_embedding(crop)
    if emb is None:
        return None
    return (emb, crop, (x, y, w, h))


def recognize_and_match(
    image_bgr: np.ndarray,
    persons_with_embeddings: List[Tuple[int, str, List[List[float]]]],
) -> Tuple[Optional[Tuple[int, str, float]], Optional[Tuple[int, int, int, int]]]:
    """(매칭 결과, 얼굴 박스 x,y,w,h). 매칭 없거나 얼굴 없으면 (None, None) 또는 (None, box)."""
    det = get_detector()
    faces = det.detect_faces(image_bgr)
    if not faces:
        return (None, None)
    best = max(faces, key=lambda b: b[2] * b[3])
    x, y, w, h = best
    crop = det.crop_face(image_bgr, best)
    emb = extract_embedding(crop)
    if emb is None:
        return (None, (x, y, w, h))
    match = find_best_match(emb, persons_with_embeddings, settings.similarity_threshold)
    return (match, (x, y, w, h))


def recognize_and_match_all(
    image_bgr: np.ndarray,
    persons_with_embeddings: List[Tuple[int, str, List[List[float]]]],
) -> List[Tuple[Optional[Tuple[int, str, float]], Tuple[int, int, int, int]]]:
    """
    모든 얼굴을 인식하고 매칭 결과를 반환.
    반환: [(match_or_none, box_xywh), ...]
    """
    det = get_detector()
    faces = det.detect_faces(image_bgr)
    if not faces:
        return []

    # 화면 표시 안정성을 위해 위->아래, 좌->우 순으로 정렬
    faces = sorted(faces, key=lambda b: (b[1], b[0]))
    results: List[Tuple[Optional[Tuple[int, str, float]], Tuple[int, int, int, int]]] = []
    for box in faces:
        crop = det.crop_face(image_bgr, box)
        emb = extract_embedding(crop)
        if emb is None:
            results.append((None, box))
            continue
        match = find_best_match(emb, persons_with_embeddings, settings.similarity_threshold)
        results.append((match, box))
    return results


def is_ml_ready() -> bool:
    return is_model_loaded()
