# MobileFaceNet 모델 파일 준비 가이드

## 목표
`web_attendance/backend/models/mobilefacenet.tflite` 파일을 준비하여 얼굴 인식 시스템이 동작하도록 설정합니다.

## 현재 상태 확인

```powershell
cd c:\Users\GH\Desktop\FaceRecognition\web_attendance\backend

# 모델 파일 존재 확인
Test-Path models\mobilefacenet.tflite

# 모델 로드 상태 확인
python -c "from services.face_service import is_ml_ready; print(is_ml_ready())"
```

## 모델 파일 다운로드 방법

### 방법 1: GitHub에서 직접 다운로드 (가장 간단)

1. **다음 링크에서 모델 파일 검색:**
   - GitHub 검색: "MobileFaceNet tflite"
   - 또는 다음 저장소 확인:
     - https://github.com/sirius-ai/MobileFaceNet_TF
     - https://github.com/fanqie03/MobileFaceNet_keras

2. **모델 파일 다운로드 후 배치:**
   ```powershell
   # 다운로드한 파일을 다음 위치에 복사
   Copy-Item "다운로드한파일.tflite" "models\mobilefacenet.tflite"
   ```

### 방법 2: .pb 파일 다운로드 후 변환

1. **.pb 파일 다운로드:**
   ```powershell
   # 임시 디렉토리 생성
   mkdir temp_model
   cd temp_model
   
   # GitHub에서 .pb 파일 다운로드
   # 브라우저에서 다음 URL 열기:
   # https://github.com/sirius-ai/MobileFaceNet_TF/raw/master/arch/pretrained_model/MobileFaceNet_9925_9680.pb
   # 파일을 다운로드하여 현재 디렉토리에 저장
   ```

2. **변환 스크립트 실행:**
   ```powershell
   cd c:\Users\GH\Desktop\FaceRecognition\web_attendance\backend
   
   # TensorFlow 설치 확인
   python -c "import tensorflow; print(tensorflow.__version__)"
   
   # 변환 실행
   python scripts\convert_pb_to_tflite.py `
     --pb_file temp_model\MobileFaceNet_9925_9680.pb `
     --output models\mobilefacenet.tflite `
     --input_name input `
     --input_shape "1,112,112,3" `
     --output_name embeddings
   ```

   **참고**: 입력/출력 노드 이름이 다를 수 있습니다. 다음 명령으로 확인:
   ```python
   import tensorflow as tf
   gf = tf.compat.v1.GraphDef()
   gf.ParseFromString(open('MobileFaceNet_9925_9680.pb', 'rb').read())
   print([n.name for n in gf.node])
   ```

### 방법 3: Keras .h5 파일 변환

1. **fanqie03/MobileFaceNet_keras 저장소 클론:**
   ```powershell
   cd ..
   git clone https://github.com/fanqie03/MobileFaceNet_keras.git
   cd MobileFaceNet_keras
   ```

2. **.h5 모델이 있으면 변환:**
   ```powershell
   python convert_tflite.py `
     --h5_file output\model.h5 `
     --tflite_file ..\web_attendance\backend\models\mobilefacenet.tflite
   ```

## 모델 사양 확인

다운로드한 모델이 다음 사양을 만족해야 합니다:

- **입력 크기**: 112×112×3 RGB 이미지
- **출력 크기**: 128차원 또는 192차원 임베딩 벡터
- **전처리**: `(이미지 - 127.5) / 128.0`
- **파일 형식**: TensorFlow Lite (.tflite)

## 모델 검증

모델 파일을 준비한 후 다음 단계로 검증합니다:

### 1단계: 파일 존재 확인
```powershell
Test-Path models\mobilefacenet.tflite
# True 출력되어야 함
```

### 2단계: 모델 로드 테스트
```powershell
python -c "from services.face_service import is_ml_ready; print(is_ml_ready())"
# True 출력되어야 함
```

### 3단계: 서버 실행 및 Health API 확인
```powershell
# 서버 실행 (백그라운드)
python -m uvicorn main:app --host 0.0.0.0 --port 8000

# 다른 터미널에서 Health 확인
curl http://localhost:8000/api/health
# 또는 브라우저에서 http://localhost:8000/api/health 열기
```

**예상 결과:**
```json
{
  "status": "ok",
  "ml_model_loaded": true
}
```

## 문제 해결

### 모델 파일이 없음
- `models` 디렉토리가 존재하는지 확인
- 파일 이름이 정확한지 확인: `mobilefacenet.tflite`

### 모델 로드 실패
- TensorFlow Lite 라이브러리 확인:
  ```powershell
  python -c "import tensorflow.lite; print('OK')"
  ```
- 파일 권한 확인: 읽기 가능한지
- 파일이 손상되지 않았는지 확인

### 모델 사양 불일치
- 입력 크기가 다르면: `ml/face_embedding.py`의 `preprocess_face()` 함수 수정
- 출력 차원이 다르면: 임베딩 차원 확인 후 필요시 코드 수정
- 전처리가 다르면: `preprocess_face()` 함수 수정

## 추가 리소스

- [ML_MODEL_SETUP_PROMPT.md](../../docs/ML_MODEL_SETUP_PROMPT.md) - 상세한 모델 준비 가이드
- [scripts/README.md](scripts/README.md) - 스크립트 사용 가이드
- [API.md](../../docs/API.md) - API 문서

## 성공 기준

다음 조건을 모두 만족하면 성공입니다:

- [x] `models/mobilefacenet.tflite` 파일 존재
- [x] `is_ml_ready()` → `True` 반환
- [x] `/api/health` → `ml_model_loaded: true`
- [x] 얼굴 인식 API 동작 (`/api/recognize`)
- [x] 스트림에서 미등록 얼굴 자동 저장 동작
