# ML 모델 준비 지시 프롬프트

## 목표
`web_attendance/backend/models/mobilefacenet.tflite` 파일을 준비하여 얼굴 인식 시스템이 동작하도록 설정하세요.

---

## 현재 상황
- **문제**: ML 모델 파일이 없어서 얼굴 인식이 동작하지 않음
- **영향**: 미등록 얼굴 저장, 클러스터링, 얼굴 인식 모두 실패
- **확인 방법**: `http://localhost:8000/api/health` → `ml_model_loaded: false`

---

## 요구사항

### 1. 모델 파일 경로
```
web_attendance/backend/models/mobilefacenet.tflite
```

### 2. 모델 사양
- **형식**: TensorFlow Lite (`.tflite`)
- **입력**: 112×112×3 RGB 이미지
- **전처리**: `(이미지 - 127.5) / 128.0` (정규화)
- **출력**: 128차원 또는 192차원 임베딩 벡터 (L2 정규화됨)
- **용도**: 얼굴 임베딩 추출 (Face Recognition)

### 3. 호환성
- **Python**: 3.12 환경
- **TensorFlow**: `tensorflow.lite` 또는 `tflite_runtime` 사용 가능
- **프레임워크**: TensorFlow Lite Interpreter

---

## 작업 단계

### Step 1: 모델 파일 확인
```bash
cd c:\Users\GH\Desktop\FaceRecognition\web_attendance\backend
Test-Path models\mobilefacenet.tflite
```

파일이 없으면 다음 단계로 진행.

### Step 2: 모델 다운로드/변환 방법 선택

#### 방법 A: GitHub 저장소에서 변환 (추천)
1. **fanqie03/MobileFaceNet_keras** 저장소 사용
   - URL: `https://github.com/fanqie03/MobileFaceNet_keras`
   - 이 저장소는 112×112 입력, 128차원 출력, 전처리 `(img-127.5)/128` 사용
   - `convert_tflite.py` 스크립트 포함

2. 작업 순서:
   ```bash
   git clone https://github.com/fanqie03/MobileFaceNet_keras.git
   cd MobileFaceNet_keras
   # .h5 모델이 있으면 convert_tflite.py 실행
   # 또는 사전 학습된 모델 다운로드 후 변환
   ```

3. 생성된 `.tflite` 파일을 `web_attendance/backend/models/` 에 복사

#### 방법 B: 직접 다운로드 (가능한 경우)
- GitHub, Hugging Face 등에서 `mobilefacenet.tflite` 검색
- 사양이 일치하는지 확인 (112×112 입력, 128차원 출력)

#### 방법 C: 다른 프로젝트에서 복사
- 기존 Android 앱의 `app/src/main/assets/mobilefacenet.tflite` 파일이 있다면
- 해당 파일을 `web_attendance/backend/models/` 에 복사

### Step 3: 모델 검증
```python
# web_attendance/backend/ 에서 실행
python -c "
from services.face_service import is_ml_ready
print('ML 모델 로드 상태:', is_ml_ready())
"
```

**예상 결과**: `ML 모델 로드 상태: True`

### Step 4: 서버 재시작
서버를 재시작하여 모델이 로드되는지 확인:
```bash
# 기존 서버 종료 후
cd c:\Users\GH\Desktop\FaceRecognition\web_attendance\backend
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

### Step 5: API 확인
```bash
curl http://localhost:8000/api/health
```

**예상 결과**:
```json
{
  "status": "ok",
  "ml_model_loaded": true
}
```

---

## 대안 모델 (호환성 확인 필요)

다음 모델들도 사용 가능할 수 있으나, 코드 수정이 필요할 수 있습니다:

1. **ArcFace TFLite** (mobilesec/arcface-tensorflowlite)
   - 출력: 512차원 → 코드 수정 필요

2. **FaceNet TFLite** (MuhammadHananAsghar/FaceNet_TFLITE)
   - 입력/출력 형식 확인 필요

3. **sirius-ai/MobileFaceNet_TF** (`.pb` → `.tflite` 변환 필요)
   - TensorFlow Lite Converter 사용

---

## 문제 해결

### 모델 로드 실패 시
1. 파일 경로 확인: `models/mobilefacenet.tflite` 존재 여부
2. 파일 권한 확인: 읽기 가능한지
3. TensorFlow Lite 라이브러리 확인:
   ```bash
   python -c "import tensorflow.lite; print('OK')"
   ```
4. 모델 형식 확인: `.tflite` 파일이 유효한 TensorFlow Lite 모델인지

### 모델 사양 불일치 시
- 입력 크기가 다르면: `ml/face_embedding.py`의 `preprocess_face()` 수정
- 출력 차원이 다르면: 임베딩 차원 확인 후 필요시 코드 수정
- 전처리가 다르면: `preprocess_face()` 함수 수정

---

## 성공 기준
- ✅ `models/mobilefacenet.tflite` 파일 존재
- ✅ `is_ml_ready()` → `True` 반환
- ✅ `/api/health` → `ml_model_loaded: true`
- ✅ 얼굴 인식 API 동작 (`/api/recognize`)
- ✅ 스트림에서 미등록 얼굴 자동 저장 동작

---

## 참고 문서
- 프로젝트 README: `web_attendance/README.md`
- API 문서: `docs/API.md`
- 아키텍처: `docs/ARCHITECTURE.md`
