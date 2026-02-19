# 웹 서버 기반 얼굴 인식 출석 (web_attendance)

Flutter 앱 + Windows 웹 서버로 **실시간 출석 체크**와 **DB 연동**을 제공합니다.

## 구조

```
web_attendance/
├── backend/          # Python FastAPI 웹 서버 (Windows, ML 처리)
├── client/           # Flutter 앱 (출석/등록/기록)
├── client2/           # Flutter 테스트 앱 (API/스트림/등록·인식 검증)
├── deploy/           # Windows 배포 스크립트
└── README.md
```

## 빠른 시작 (Windows)

### 1. 서버 실행

**방법 A – 배치 파일 (권장)**  
`deploy` 폴더에서 더블클릭 또는 CMD에서:

```bat
deploy\run_server.bat
```

**방법 B – PowerShell**

```powershell
.\deploy\run_server.ps1
```

**방법 C – 수동 (Python 3.12 권장)**

```bat
cd web_attendance\backend
py -3.12 -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

- 가상환경은 **Python 3.12**로 생성됩니다 (`py -3.12`). Python 3.12가 없으면 [python.org](https://www.python.org/downloads/)에서 설치하세요.
- 이미 다른 버전으로 만든 `venv`가 있다면, **backend\venv** 폴더를 삭제한 뒤 `run_server.bat`을 다시 실행하면 Python 3.12로 새로 만들어집니다.

- 서버: **http://localhost:8000**
- 웹 관리: **http://localhost:8000/** (출석 조회, 사용자 목록)

### 2. ML 모델 (얼굴 인식용)

- **모델 없이**: 얼굴 감지(OpenCV)만 동작. 인식(매칭)은 불가.
- **모델 있음**: `mobilefacenet.tflite`를 **backend/models/** 에 넣으면 인식 가능.
  - 기존 Android 앱의 `app/src/main/assets/mobilefacenet.tflite`를 복사해 사용하면 됨.

### 3. Flutter 앱 실행

- 같은 PC에서 서버 실행 후:

```bash
cd web_attendance\client
flutter pub get
flutter run
```

- **설정**에서 서버 주소 지정:
  - 에뮬레이터: `http://10.0.2.2:8000`
  - 실제 기기: PC IP 사용 (예: `http://192.168.0.10:8000`)

## 기능

| 기능 | 설명 |
|------|------|
| **실시간 출석** | Flutter 카메라 → WebSocket/HTTP로 서버 전송 → 인식 시 출석 자동 기록 |
| **실시간 스트림** | Flutter → WebSocket → 웹 브라우저에서 실시간 영상 + 인식 결과 확인 |
| **사용자 등록** | 이름 + 사진 최대 5장 → 서버에서 얼굴 감지·임베딩 저장 |
| **출석 기록** | 날짜별 조회, Excel 내보내기 (웹 관리 또는 API) |
| **웹 관리** | http://localhost:8000/ 에서 출석 조회, 사용자 목록, Excel 다운로드 |
| **스트림 뷰어** | http://localhost:8000/stream 에서 실시간 카메라 영상 + 얼굴 인식 결과 |

## API 요약

- `GET  /api/health` – 서버/ML 상태
- `GET  /api/config` – 클라이언트 설정
- `GET  /api/persons` – 등록 사용자 목록
- `POST /api/register` – 사용자 등록 (name + images)
- `POST /api/recognize` – 얼굴 인식 + 출석 기록
- `GET  /api/attendances` – 출석 목록 (date, personId 등)
- `GET  /api/attendances/export?from=&to=&format=xlsx` – Excel 내보내기
- `WebSocket /ws/stream` – 실시간 이미지 스트리밍 (Flutter → 서버 → 웹 뷰어)

자세한 명세: 프로젝트 루트 **docs/WEB_SERVER_SPEC.md**

## 배포

- **서버**: Windows PC에서 `run_server.bat` 실행 후 방화벽에서 8000 포트 허용.
- **Flutter**: APK 빌드 시 `flutter build apk` 후 `build/app/outputs/flutter-apk/` 에서 배포.
- DB는 기본 **backend/attendance.db** (SQLite). 서버 재시작해도 유지됨.

## 요구사항

- **서버**: Windows 10+, Python 3.10+
- **Flutter**: Flutter SDK, Android 21+ / iOS 12+
