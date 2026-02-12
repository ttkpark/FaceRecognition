# 모델 파일 안내

이 폴더에 `mobilefacenet.tflite` 파일을 넣어주세요.

## 모델 다운로드 방법

1. **GitHub에서 다운로드:**
   - 검색어: "MobileFaceNet tflite"
   - 또는 다음 링크에서 다운로드:
     - https://github.com/tensorflow/models/tree/master/research/face_detection
     - https://github.com/leondgarse/Keras_insightface

2. **직접 변환:**
   - MobileFaceNet 모델을 TensorFlow Lite로 변환한 파일

3. **모델 사양:**
   - 입력 크기: 112x112x3 (RGB)
   - 출력 크기: 192차원 벡터 (또는 128차원, 모델에 따라 다름)
   - 파일명: `mobilefacenet.tflite`

## 참고

모델 파일이 없으면 앱이 실행되지 않습니다. 반드시 모델 파일을 추가해주세요.
