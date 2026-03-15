# 다른 PC에서 처음 설정하기

저장소를 클론한 뒤 **한 번만** 아래 순서대로 진행하면 됩니다.

## 1. 저장소 클론

```bash
git clone https://github.com/ttkpark/FaceRecognition.git
cd FaceRecognition
```

- 배포용이면 `main` 브랜치, 개발용이면 `develop` 브랜치 사용  
  `git checkout develop`  # 개발 시

---

## 2. 백엔드(서버) 설정

웹 서버를 띄우려면 Python 가상환경 + 패키지 설치가 필요합니다.

| OS      | 명령 |
|--------|------|
| Windows | `web_attendance\backend\setup.bat` 실행 (더블클릭 또는 CMD에서) |
| Linux   | `cd web_attendance/backend && chmod +x setup.sh run.sh && ./setup.sh` |

- **필수:** Python 3.10 ~ 3.12 설치 (Windows는 [python.org](https://www.python.org/downloads/)에서 설치 시 "Add Python to PATH" 체크)
- `venv` 폴더가 생기고 `pip install -r requirements.txt` 가 실행됩니다.

---

## 3. (선택) pre-commit 훅

커밋 시 `venv`·대용량 파일이 실수로 올라가는 것을 막으려면 한 번만 실행하세요.

| OS      | 명령 |
|--------|------|
| Windows | `powershell -ExecutionPolicy Bypass -File scripts\install_precommit.ps1` |
| Linux   | `chmod +x scripts/install_precommit.sh && ./scripts/install_precommit.sh` |

---

## 4. 서버 실행

| OS      | 명령 |
|--------|------|
| Windows | `web_attendance\backend\run.bat` |
| Linux   | `web_attendance/backend/run.sh` |

- 기본 포트 **8001** (다른 기기에서 접속 시 방화벽에서 8001 허용)
- 자세한 내용: `web_attendance/backend/SERVER_RUN.md`

---

## 5. (선택) 얼굴 인식 모델

- 모델 없이: 얼굴 **감지**만 동작, **인식(매칭)** 은 불가
- 인식까지 쓰려면: `web_attendance/backend/models/mobilefacenet.tflite` 파일 준비  
  → 방법은 `web_attendance/backend/MODEL_SETUP.md` 참고

---

## 한 번에 하기 (권장)

저장소 **루트**에서 아래만 실행하면 백엔드 설정 + (선택) pre-commit까지 한 번에 됩니다.

| OS      | 명령 |
|--------|------|
| Windows | `setup.bat` |
| Linux   | `chmod +x setup.sh && ./setup.sh` |

이후 서버 실행: `web_attendance\backend\run.bat` 또는 `web_attendance/backend/run.sh`

---

## 요약 (다른 PC에서 할 일)

1. **클론** → `cd FaceRecognition`
2. **전체 설정** → 루트에서 `setup.bat` (Windows) 또는 `./setup.sh` (Linux)
3. **서버 실행** → `web_attendance\backend\run.bat` 또는 `run.sh`

개별 단계가 필요하면 위 2~5절을 참고하고, 개발/배포는 각 문서(updater, SERVER_RUN, MODEL_SETUP)를 참고하면 됩니다.
