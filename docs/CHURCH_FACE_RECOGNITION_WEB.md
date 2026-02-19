# 교회 안면인식·사람인식 시스템 (웹 문서)

> Python 3.12 백엔드 + Flutter/웹 프론트엔드 기반 **교회 출석·등원 인식 시스템**의 구성, 설치, 사용 방법을 정리한 문서입니다.

---

## 1. 시스템 개요

### 1.1 목적

- **교회·예배·모임** 등에서 **얼굴 인식**으로 출석자 자동 기록
- 등록된 교인/참석자가 카메라에 비치면 **실시간 인식** 후 해당 날짜 출석으로 저장
- 관리자는 **웹 화면**에서 출석 조회, Excel 내보내기, 등록자 목록 확인

### 1.2 구성 요소

| 구분 | 기술 | 역할 |
|------|------|------|
| **백엔드** | Python 3.12, FastAPI, SQLite | REST API, 얼굴 감지·인식(ML), DB 저장 |
| **프론트엔드(앱)** | Flutter (Android/iOS) | 카메라 촬영 → 서버 전송, 사용자 등록, 출석 기록 조회 |
| **프론트엔드(웹)** | HTML/JS (정적 페이지) | 출석 조회, 사용자 목록, Excel 다운로드 (서버에서 제공) |

### 1.3 전체 구조

```
[Flutter 앱]  ──실시간 캡처/등록/조회──►  [웹 서버 :8000]
[웹 브라우저] ──출석 조회/Excel──►         │
                                          ├── API (REST)
                                          ├── ML (얼굴 감지·임베딩·매칭)
                                          └── DB (SQLite)
```

---

## 2. 백엔드 (Python 3.12)

### 2.1 환경

- **Python**: 3.12 권장 (가상환경에서 사용)
- **프레임워크**: FastAPI
- **DB**: SQLite (`backend/attendance.db`)
- **ML**: OpenCV(얼굴 감지) + TensorFlow Lite(임베딩, MobileFaceNet)

### 2.2 디렉터리 구조

```
web_attendance/backend/
├── main.py              # FastAPI 앱, 라우트
├── config.py            # 설정 (DB 경로, 모델 경로, 임계값 등)
├── database.py          # SQLite, Person/Attendance 모델
├── requirements.txt     # 패키지 목록
├── requirements-minimal.txt  # TensorFlow 제외 (Python 3.13 등)
├── ml/
│   ├── face_detector.py   # OpenCV 얼굴 감지
│   ├── face_embedding.py  # TFLite 192차원 임베딩
│   └── face_matcher.py    # 코사인 유사도 매칭
├── services/
│   └── face_service.py    # 감지·임베딩·매칭 오케스트레이션
├── static/
│   └── index.html         # 웹 관리 화면 (출석 조회 등)
└── models/
    └── mobilefacenet.tflite  # 여기에 배치 (선택)
```

### 2.3 설치 및 실행 (Windows, Python 3.12)

**방법 1 – 배치 파일 (권장)**

```bat
cd web_attendance\deploy
run_server.bat
```

- 최초 실행 시 `py -3.12 -m venv venv`로 가상환경 생성
- `pip install -r requirements.txt` 자동 실행
- 서버: **http://localhost:8000**

**방법 2 – 수동**

```bat
cd web_attendance\backend
py -3.12 -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

- 기존에 다른 Python으로 만든 `venv`가 있으면 **venv 폴더 삭제** 후 위 순서로 다시 실행하면 Python 3.12로 생성됩니다.

### 2.4 ML 모델 (얼굴 인식)

- **모델 파일**: `backend/models/mobilefacenet.tflite`
- Android 앱 `app/src/main/assets/mobilefacenet.tflite`를 위 경로에 복사해 사용 가능
- 모델이 없어도 **얼굴 감지**는 동작하지만, **누구인지 매칭(인식)**은 되지 않음
- 인식 시 **코사인 유사도 임계값 0.6** 사용 (설정 가능)

---

## 3. 프론트엔드

### 3.1 Flutter 앱 (모바일)

- **역할**: 실시간 출석(카메라 → 서버 전송), 사용자 등록(이름+사진), 출석 기록 조회
- **위치**: `web_attendance/client/`
- **실행**:
  ```bash
  cd web_attendance\client
  flutter pub get
  flutter run
  ```
- **서버 주소 설정**: 앱 내 **설정**에서 서버 Base URL 입력  
  - 예: `http://192.168.0.10:8000` (실기기), `http://10.0.2.2:8000` (에뮬레이터)

### 3.2 웹 관리 화면

- **URL**: 서버 기동 후 **http://localhost:8000/** (같은 PC) 또는 **http://서버IP:8000/**
- **기능**:
  - 서버 연결 확인
  - 날짜별 출석 기록 조회
  - Excel 내보내기
  - 등록 사용자 목록 확인
- **구현**: `backend/static/index.html` (단일 HTML + JS)

### 3.3 실시간 스트림 뷰어 (WebSocket)

- **URL**: **http://서버:8000/stream**
- **기능**:
  - Flutter 앱에서 전송하는 카메라 영상을 **실시간**으로 수신·표시
  - 서버에서 수행한 **얼굴 인식 결과** (이름, 출석 여부) 오버레이 표시
- **사용 순서**:
  1. 서버 실행 (`run_server.bat`)
  2. 웹 브라우저에서 `http://서버주소:8000/stream` 접속 → [연결] 클릭
  3. Flutter 앱에서 출석 탭 실행 (카메라 스트리밍 자동 시작)
- **프로토콜**: WebSocket `ws://서버:8000/ws/stream` — Flutter가 바이너리 이미지 전송, 서버가 웹 뷰어들에게 JSON(이미지 base64 + 인식 결과) 브로드캐스트

---

## 4. API 요약

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/health` | 서버 및 ML 모델 로드 여부 |
| GET | `/api/config` | 클라이언트용 설정 (임계값, 스로틀 간격 등) |
| GET | `/api/persons` | 등록된 사용자 목록 |
| POST | `/api/register` | 사용자 등록 (name + images, multipart) |
| DELETE | `/api/persons/:id` | 사용자 삭제 (관련 출석 CASCADE 삭제) |
| POST | `/api/recognize` | 이미지 1장 전송 → 얼굴 인식 + 당일 미출석 시 출석 기록 |
| GET | `/api/attendances` | 출석 목록 (쿼리: date, personId, from, to) |
| GET | `/api/attendances/check` | 특정 사용자·날짜 출석 여부 (personId, date) |
| GET | `/api/attendances/export` | Excel/CSV 내보내기 (from, to, format) |

자세한 요청/응답 형식은 **docs/WEB_SERVER_SPEC.md** 참고.

---

## 5. 데이터베이스 (SQLite)

- **파일**: `backend/attendance.db` (기본)
- **테이블**:
  - **persons**: id, name, embedding(JSON), createdAt
  - **attendances**: id, personId, timestamp, date(YYYY-MM-DD)
- 얼굴 원본 이미지는 저장하지 않고, **임베딩 벡터만** 저장 (개인정보 보호).

---

## 6. 배포 요약

| 항목 | 내용 |
|------|------|
| 서버 | Windows PC에서 `run_server.bat` 실행, 방화벽 8000 포트 허용 |
| DB | `backend/attendance.db` 유지 (재시작해도 유지) |
| Flutter 앱 | `flutter build apk` 후 APK 배포 또는 `flutter run`으로 테스트 |
| 웹 관리 | 브라우저에서 `http://서버주소:8000/` 접속 |

---

## 7. 요구사항 정리

- **백엔드**: Windows 10+, **Python 3.12** (가상환경 권장), 8000 포트 사용 가능
- **Flutter**: Flutter SDK, Android 21+ / iOS 12+
- **네트워크**: 앱·웹이 서버와 같은 LAN 또는 접근 가능한 주소

---

## 8. 관련 문서

- **웹 서버 명세 (API·아키텍처 상세)**: [docs/WEB_SERVER_SPEC.md](WEB_SERVER_SPEC.md)
- **프로젝트 빠른 시작**: [web_attendance/README.md](../web_attendance/README.md)
- **기존 Android 온디바이스 시스템**: [docs/README.md](README.md), [docs/API.md](API.md), [docs/ARCHITECTURE.md](ARCHITECTURE.md)

---

이 문서는 **교회 안면인식·사람인식 시스템**의 백엔드(Python 3.12)와 프론트엔드(Flutter·웹) 구성을 웹에서 참고할 수 있도록 정리한 내용입니다.
