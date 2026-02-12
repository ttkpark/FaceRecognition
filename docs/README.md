# 얼굴 인식 출석 시스템 (On-Device Face Recognition Attendance System)

## 프로젝트 개요

안드로이드 기기에서 인터넷 연결 없이 동작하는 온디바이스 AI 기반 얼굴 인식 출석 시스템입니다. TensorFlow Lite와 ML Kit을 활용하여 실시간 얼굴 인식 및 출석 기록을 수행합니다.

## 주요 특징

- ✅ **온디바이스 AI**: 인터넷 연결 없이 모든 처리가 기기 내에서 수행
- ✅ **실시간 얼굴 인식**: 카메라를 통한 실시간 얼굴 감지 및 인식
- ✅ **출석 자동 기록**: 인식된 사용자의 출석을 자동으로 기록
- ✅ **다중 사진 등록**: 정확도 향상을 위한 다중 각도 사진 등록 지원
- ✅ **로컬 데이터베이스**: Room Database를 사용한 오프라인 데이터 저장
- ✅ **성능 최적화**: 스로틀링을 통한 발열 관리 및 배터리 최적화

## 기술 스택

### 핵심 기술
- **언어**: Kotlin
- **UI 프레임워크**: Jetpack Compose
- **아키텍처**: MVVM (Model-View-ViewModel)

### AI/ML 라이브러리
- **TensorFlow Lite 2.14.0**: 얼굴 임베딩 추출 (MobileFaceNet 모델)
- **ML Kit Face Detection 16.1.7**: 얼굴 감지

### 데이터베이스
- **Room 2.6.1**: 로컬 데이터베이스 (SQLite 기반)

### 카메라
- **CameraX 1.3.3**: 카메라 프리뷰 및 이미지 캡처

### 기타
- **Accompanist Permissions 0.34.0**: 런타임 권한 처리

## 시스템 아키텍처

### 1. 얼굴 감지 (Face Detection)
```
카메라 프레임 → ML Kit Face Detection → 얼굴 위치(Bounding Box) 추출
```

### 2. 얼굴 인식 (Face Recognition)
```
얼굴 이미지 → MobileFaceNet (TFLite) → 192차원 임베딩 벡터
```

### 3. 벡터 매칭 (Vector Matching)
```
현재 임베딩 벡터 ↔ 등록된 벡터들 → 코사인 유사도 계산 → 매칭 결과
```

### 4. 출석 기록 (Attendance Recording)
```
매칭 성공 → Room Database 저장 → 출석 기록 완료
```

## 프로젝트 구조

```
app/src/main/java/com/example/facerecognition/
├── data/                    # 데이터베이스 레이어
│   ├── Person.kt           # 사용자 엔티티
│   ├── Attendance.kt       # 출석 기록 엔티티
│   ├── PersonDao.kt        # 사용자 DAO
│   ├── AttendanceDao.kt    # 출석 기록 DAO
│   └── AppDatabase.kt     # Room 데이터베이스
│
├── ml/                      # 머신러닝 레이어
│   ├── FaceDetector.kt     # ML Kit 얼굴 감지
│   ├── FaceRecognitionEngine.kt  # TFLite 얼굴 인식 엔진
│   └── FaceMatcher.kt      # 벡터 매칭 로직
│
├── viewmodel/               # 뷰모델 레이어
│   └── FaceRecognitionViewModel.kt  # 메인 뷰모델
│
├── ui/                      # UI 레이어
│   ├── MainScreen.kt       # 메인 화면 (네비게이션)
│   ├── CameraScreen.kt     # 카메라 화면
│   ├── RegisterScreen.kt   # 사용자 등록 화면
│   └── AttendanceScreen.kt # 출석 기록 화면
│
└── MainActivity.kt         # 메인 액티비티
```

## 모델 파일 설정

### MobileFaceNet 모델 다운로드

앱을 실행하기 전에 다음 단계를 수행해야 합니다:

1. `mobilefacenet.tflite` 파일을 다운로드
2. `app/src/main/assets/` 폴더에 파일 복사

### 모델 사양
- **입력 크기**: 112x112x3 (RGB)
- **출력 크기**: 192차원 벡터
- **모델 크기**: 약 4MB
- **정확도**: LFW 기준 99% 이상

### 모델 다운로드 링크
- GitHub 검색: "MobileFaceNet tflite"
- 참고 저장소:
  - https://github.com/tensorflow/models
  - https://github.com/leondgarse/Keras_insightface

## 사용 방법

### 1. 사용자 등록
1. 앱 실행 후 "등록" 탭 선택
2. 이름 입력
3. 카메라에 얼굴을 맞추고 여러 각도로 촬영 (최대 5장)
4. "등록하기" 버튼 클릭

### 2. 출석 기록
1. "카메라" 탭 선택
2. 카메라에 얼굴을 맞춤
3. 자동으로 얼굴 인식 및 출석 기록

### 3. 출석 기록 조회
1. "출석" 탭 선택
2. 등록된 출석 기록 확인

## 성능 최적화

### 스로틀링 (Throttling)
- 얼굴 인식은 500ms(0.5초)마다 수행
- 발열 및 배터리 소모 최소화

### 멀티스레딩
- TensorFlow Lite 인터프리터: 4개 스레드 사용
- 백그라운드 스레드에서 AI 연산 수행

### 정확도 향상
- 다중 사진 등록: 5장의 다양한 각도 사진 사용
- 벡터 비교: 코사인 유사도 사용 (임계값: 0.6)

## 데이터베이스 스키마

### Person 테이블
```sql
CREATE TABLE persons (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    embedding TEXT NOT NULL,  -- JSON 배열 형태의 벡터들
    createdAt INTEGER NOT NULL
);
```

### Attendance 테이블
```sql
CREATE TABLE attendances (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    personId INTEGER NOT NULL,
    timestamp INTEGER NOT NULL,
    date TEXT NOT NULL,  -- "YYYY-MM-DD" 형식
    FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE CASCADE
);
```

## 권한 요구사항

- `CAMERA`: 카메라 접근 (필수)

## 최소 요구사항

- **Android API Level**: 28 (Android 9.0) 이상
- **컴파일 SDK**: 36
- **타겟 SDK**: 36
- **최소 SDK**: 28

## 빌드 방법

1. Android Studio에서 프로젝트 열기
2. Gradle Sync 실행
3. `app/src/main/assets/` 폴더에 `mobilefacenet.tflite` 파일 추가
4. 빌드 및 실행

## 주의사항

1. **모델 파일 필수**: 모델 파일이 없으면 앱이 실행되지 않습니다.
2. **카메라 권한**: 첫 실행 시 카메라 권한을 허용해야 합니다.
3. **발열 관리**: 오래 사용 시 기기가 뜨거워질 수 있으므로 적절한 휴식이 필요합니다.
4. **조명 조건**: 어두운 환경에서는 인식 정확도가 떨어질 수 있습니다.

## 향후 개선 사항

- [ ] Excel 내보내기 기능
- [ ] 출석 통계 및 차트
- [ ] 얼굴 인식 임계값 조정 기능
- [ ] 다중 얼굴 동시 인식
- [ ] 클라우드 백업 기능 (선택사항)

## 라이선스

이 프로젝트는 교육 목적으로 제작되었습니다.

## 개발자 정보

전자공학과 프로젝트 - 온디바이스 AI 얼굴 인식 시스템
