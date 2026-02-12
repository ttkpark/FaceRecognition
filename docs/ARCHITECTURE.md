# 시스템 아키텍처 문서

## 개요

본 문서는 얼굴 인식 출석 시스템의 기술적 아키텍처와 구현 세부사항을 설명합니다.

## 아키텍처 패턴

### MVVM (Model-View-ViewModel)

```
┌─────────────┐
│    View     │  (Compose UI)
│  (Screen)   │
└──────┬──────┘
       │
       │ observe StateFlow
       │
┌──────▼──────────┐
│   ViewModel     │
│ (Business Logic)│
└──────┬──────────┘
       │
       │ use
       │
┌──────▼──────────┐
│   Repository    │
│  (Data Layer)   │
└─────────────────┘
```

## 레이어 구조

### 1. Presentation Layer (UI)

**구성 요소:**
- `MainScreen.kt`: 네비게이션 및 화면 전환
- `CameraScreen.kt`: 실시간 카메라 프리뷰 및 얼굴 인식
- `RegisterScreen.kt`: 사용자 등록 화면
- `AttendanceScreen.kt`: 출석 기록 조회 화면

**기술:**
- Jetpack Compose
- Material Design 3
- StateFlow를 통한 상태 관리

### 2. ViewModel Layer

**구성 요소:**
- `FaceRecognitionViewModel.kt`

**책임:**
- UI 상태 관리 (StateFlow)
- 비즈니스 로직 처리
- 데이터 레이어와의 통신 조율
- ML 레이어 호출

**주요 기능:**
```kotlin
- recognizeFace(): 얼굴 인식 수행
- registerPerson(): 사용자 등록
- recordAttendance(): 출석 기록
- loadAllPersons(): 등록된 사용자 로드
- loadAttendances(): 출석 기록 로드
```

### 3. ML Layer (Machine Learning)

**구성 요소:**
- `FaceDetector.kt`: ML Kit 얼굴 감지
- `FaceRecognitionEngine.kt`: TFLite 얼굴 인식
- `FaceMatcher.kt`: 벡터 매칭

**처리 흐름:**
```
1. FaceDetector.detectFaces()
   └─> ML Kit으로 얼굴 위치 감지
   
2. FaceRecognitionEngine.extractEmbedding()
   └─> TFLite로 임베딩 벡터 추출
   
3. FaceMatcher.findBestMatch()
   └─> 코사인 유사도로 매칭
```

### 4. Data Layer

**구성 요소:**
- `AppDatabase.kt`: Room 데이터베이스
- `PersonDao.kt`: 사용자 데이터 접근
- `AttendanceDao.kt`: 출석 기록 데이터 접근

**데이터 모델:**
- `Person`: 사용자 정보 (이름, 임베딩 벡터)
- `Attendance`: 출석 기록 (사용자 ID, 타임스탬프, 날짜)

## 데이터 흐름

### 얼굴 인식 프로세스

```
[카메라 프레임]
    ↓
[ImageAnalysis Analyzer]
    ↓
[Bitmap 변환]
    ↓
[ViewModel.recognizeFace()]
    ↓
┌─────────────────────────┐
│ 1. FaceDetector         │
│    .detectFaces()       │ → 얼굴 감지
└─────────────────────────┘
    ↓
┌─────────────────────────┐
│ 2. FaceRecognitionEngine│
│    .extractEmbedding()  │ → 임베딩 추출
└─────────────────────────┘
    ↓
┌─────────────────────────┐
│ 3. FaceMatcher          │
│    .findBestMatch()     │ → 벡터 비교
└─────────────────────────┘
    ↓
[매칭 결과]
    ↓
[출석 기록 저장]
    ↓
[UI 업데이트]
```

### 사용자 등록 프로세스

```
[사용자 입력: 이름 + 사진들]
    ↓
[ViewModel.registerPerson()]
    ↓
┌─────────────────────────┐
│ 각 사진마다:            │
│ 1. 얼굴 감지            │
│ 2. 임베딩 추출          │
└─────────────────────────┘
    ↓
[임베딩 벡터들을 JSON으로 변환]
    ↓
[Room Database에 저장]
    ↓
[UI 업데이트]
```

## ML 모델 상세

### MobileFaceNet

**입력 전처리:**
1. 얼굴 영역 크롭 (ML Kit Bounding Box 사용)
2. 112x112 픽셀로 리사이즈
3. RGB 정규화: `(pixel - 127.5) / 128.0` (범위: -1.0 ~ 1.0)

**출력 후처리:**
1. L2 정규화 (벡터 길이를 1로 정규화)
2. 코사인 유사도 계산에 사용

**벡터 비교:**
```kotlin
cosineSimilarity(v1, v2) = dot(v1, v2) / (||v1|| * ||v2||)
```

**임계값:**
- 코사인 유사도 ≥ 0.6: 동일인으로 판단
- 모델 및 환경에 따라 조정 가능

## 성능 최적화 전략

### 1. 스로틀링 (Throttling)

**목적:** 발열 및 배터리 소모 최소화

**구현:**
```kotlin
private var lastRecognitionTime = 0L
private val recognitionInterval = 500L // 500ms

if (currentTime - lastRecognitionTime < recognitionInterval) {
    return // 스킵
}
```

**효과:**
- CPU 사용률 감소
- 발열 방지
- 배터리 수명 연장

### 2. 비동기 처리

**Coroutines 사용:**
- `Dispatchers.IO`: 데이터베이스 및 파일 I/O
- `Dispatchers.Main`: UI 업데이트
- `viewModelScope`: ViewModel 생명주기 관리

### 3. 메모리 관리

**Bitmap 처리:**
- 사용 후 즉시 해제
- 적절한 크기로 리사이즈
- 불필요한 복사 최소화

**TFLite 인터프리터:**
- 앱 생명주기 동안 재사용
- `onCleared()`에서 리소스 해제

## 에러 처리

### 모델 파일 없음
```kotlin
try {
    val modelBuffer = loadModelFile(context, "mobilefacenet.tflite")
    // ...
} catch (e: Exception) {
    e.printStackTrace()
    // 앱 실행 불가
}
```

### 얼굴 감지 실패
```kotlin
val faces = faceDetector.detectFaces(bitmap)
if (faces.isEmpty()) {
    // 얼굴 없음 처리
    return
}
```

### 임베딩 추출 실패
```kotlin
val embedding = recognitionEngine.extractEmbedding(faceBitmap)
    ?: throw Exception("임베딩 추출 실패")
```

## 보안 고려사항

### 데이터 저장
- 모든 데이터는 기기 내부 저장소에 저장
- 외부 접근 불가 (앱 샌드박스)
- 데이터베이스 암호화 (선택사항)

### 개인정보 보호
- 얼굴 이미지는 저장하지 않음
- 임베딩 벡터만 저장 (역변환 불가능)
- 출석 기록은 로컬에만 저장

## 확장 가능성

### 향후 개선 방향

1. **GPU 가속**
   ```kotlin
   val options = Interpreter.Options().apply {
       addDelegate(GpuDelegate())
   }
   ```

2. **NPU 활용**
   - Android Neural Networks API 사용
   - 하드웨어 가속

3. **모델 최적화**
   - 양자화 (Quantization)
   - 모델 크기 감소
   - 추론 속도 향상

4. **클라우드 연동** (선택사항)
   - Firebase Storage
   - 백업 및 복원
   - 다중 기기 동기화

## 참고 자료

- [TensorFlow Lite 가이드](https://www.tensorflow.org/lite)
- [ML Kit 문서](https://developers.google.com/ml-kit)
- [Room Database 가이드](https://developer.android.com/training/data-storage/room)
- [CameraX 문서](https://developer.android.com/training/camerax)
