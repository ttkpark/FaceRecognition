# ml/face_embedding.py
import numpy as np
from pathlib import Path
from typing import List, Optional

from config import settings

_interpreter = None


def _load_interpreter():
    global _interpreter
    if _interpreter is not None:
        return _interpreter
    path = Path(settings.face_model_path)
    if not path.exists():
        return None
    try:
        try:
            import tflite_runtime.interpreter as tflite
        except ImportError:
            import tensorflow.lite as tflite
        _interpreter = tflite.Interpreter(model_path=str(path), num_threads=4)
        _interpreter.allocate_tensors()
        return _interpreter
    except Exception:
        return None


def preprocess_face(face_bgr: np.ndarray, size: int = 112) -> np.ndarray:
    import cv2
    face_rgb = cv2.cvtColor(face_bgr, cv2.COLOR_BGR2RGB)
    resized = cv2.resize(face_rgb, (size, size), interpolation=cv2.INTER_LINEAR)
    return (resized.astype(np.float32) - 127.5) / 128.0


def l2_normalize(x: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(x)
    return x if norm < 1e-10 else x / norm


def extract_embedding(face_bgr: np.ndarray) -> Optional[List[float]]:
    interp = _load_interpreter()
    if interp is None:
        return None
    inp = preprocess_face(face_bgr)
    inp = np.expand_dims(inp, axis=0).astype(np.float32)
    input_details = interp.get_input_details()
    output_details = interp.get_output_details()
    interp.set_tensor(input_details[0]["index"], inp)
    interp.invoke()
    out = interp.get_tensor(output_details[0]["index"])
    out = l2_normalize(out.flatten())
    return out.tolist()


def cosine_similarity(a: List[float], b: List[float]) -> float:
    a, b = np.array(a, dtype=np.float32), np.array(b, dtype=np.float32)
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-10))


def is_model_loaded() -> bool:
    return _load_interpreter() is not None
