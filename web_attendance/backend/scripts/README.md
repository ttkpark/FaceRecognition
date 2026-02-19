# 모델 준비 스크립트

이 디렉토리에는 MobileFaceNet 모델 파일을 준비하기 위한 스크립트가 포함되어 있습니다.

## 모델 파일 요구사항

- **파일 경로**: `web_attendance/backend/models/mobilefacenet.tflite`
- **형식**: TensorFlow Lite (.tflite)
- **입력**: 112×112×3 RGB 이미지
- **출력**: 128차원 또는 192차원 임베딩 벡터
- **전처리**: `(이미지 - 127.5) / 128.0`

## 사용 방법

### 방법 1: 수동 다운로드 (권장)

1. 다음 저장소에서 모델 파일을 다운로드:
   - [sirius-ai/MobileFaceNet_TF](https://github.com/sirius-ai/MobileFaceNet_TF)
   - [fanqie03/MobileFaceNet_keras](https://github.com/fanqie03/MobileFaceNet_keras)
   - 또는 GitHub에서 "MobileFaceNet tflite" 검색

2. 다운로드한 파일을 `web_attendance/backend/models/mobilefacenet.tflite`에 복사

3. 모델 테스트:
   ```bash
   cd web_attendance/backend
   python -c "from services.face_service import is_ml_ready; print(is_ml_ready())"
   ```
   → `True`가 출력되어야 합니다.

### 방법 2: .pb 파일 변환

1. `.pb` 파일 다운로드:
   ```bash
   # GitHub에서 MobileFaceNet_9925_9680.pb 다운로드
   # https://github.com/sirius-ai/MobileFaceNet_TF/raw/master/arch/pretrained_model/MobileFaceNet_9925_9680.pb
   ```

2. 변환 스크립트 실행:
   ```bash
   python scripts/convert_pb_to_tflite.py \
     --pb_file MobileFaceNet_9925_9680.pb \
     --output models/mobilefacenet.tflite \
     --input_name input \
     --input_shape "1,112,112,3" \
     --output_name embeddings
   ```

   **참고**: 입력/출력 노드 이름은 모델에 따라 다를 수 있습니다.
   노드 이름 확인:
   ```python
   import tensorflow as tf
   gf = tf.compat.v1.GraphDef()
   gf.ParseFromString(open('model.pb', 'rb').read())
   print([n.name for n in gf.node])
   ```

### 방법 3: Keras .h5 파일 변환

1. `fanqie03/MobileFaceNet_keras` 저장소 클론:
   ```bash
   git clone https://github.com/fanqie03/MobileFaceNet_keras.git
   cd MobileFaceNet_keras
   ```

2. .h5 모델이 있으면 변환:
   ```bash
   python convert_tflite.py \
     --h5_file output/model.h5 \
     --tflite_file ../web_attendance/backend/models/mobilefacenet.tflite
   ```

## 모델 검증

모델이 올바르게 준비되었는지 확인:

```bash
cd web_attendance/backend

# 1. 파일 존재 확인
Test-Path models\mobilefacenet.tflite

# 2. 모델 로드 테스트
python -c "from services.face_service import is_ml_ready; print(is_ml_ready())"
# → True 출력되어야 함

# 3. 서버 실행 후 health 확인
python -m uvicorn main:app --host 0.0.0.0 --port 8000
# 다른 터미널에서:
curl http://localhost:8000/api/health
# → ml_model_loaded: true 확인
```

## 문제 해결

### 모델 로드 실패
- 파일 경로 확인: `models/mobilefacenet.tflite` 존재 여부
- 파일 권한 확인: 읽기 가능한지
- TensorFlow Lite 라이브러리 확인:
  ```bash
  python -c "import tensorflow.lite; print('OK')"
  ```

### 모델 사양 불일치
- 입력 크기가 다르면: `ml/face_embedding.py`의 `preprocess_face()` 수정
- 출력 차원이 다르면: 임베딩 차원 확인 후 필요시 코드 수정
- 전처리가 다르면: `preprocess_face()` 함수 수정

## 참고 링크

- [ML_MODEL_SETUP_PROMPT.md](../../../docs/ML_MODEL_SETUP_PROMPT.md) - 상세한 모델 준비 가이드
- [API.md](../../../docs/API.md) - API 문서
- [CHURCH_FACE_RECOGNITION_WEB.md](../../../docs/CHURCH_FACE_RECOGNITION_WEB.md) - 전체 시스템 문서
