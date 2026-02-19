# ml/face_detector.py
import cv2
import numpy as np
from pathlib import Path
from typing import List, Tuple

from config import settings


class FaceDetector:
    def __init__(self):
        path = settings.face_cascade_path
        if path and Path(path).exists():
            self._cascade = cv2.CascadeClassifier(path)
        else:
            self._cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
            )

    def detect_faces(self, image: np.ndarray) -> List[Tuple[int, int, int, int]]:
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        gray = cv2.equalizeHist(gray)
        faces = self._cascade.detectMultiScale(
            gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30)
        )
        return [tuple(map(int, (x, y, w, h))) for (x, y, w, h) in faces]

    def crop_face(self, image: np.ndarray, box: Tuple[int, int, int, int]) -> np.ndarray:
        x, y, w, h = box
        h_img, w_img = image.shape[:2]
        x1, y1 = max(0, x), max(0, y)
        x2, y2 = min(w_img, x + w), min(h_img, y + h)
        return image[y1:y2, x1:x2]
