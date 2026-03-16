# 서버 실행 가이드

## 1. 실행/설치 스크립트 사용 (권장)

`backend` 폴더에 있는 스크립트로 한 번에 실행할 수 있습니다.

### 최초 1회: 설치

| OS      | 명령 |
|--------|------|
| Windows | `setup.bat` 더블클릭 또는 `cmd`에서 `setup.bat` 실행 |
| Linux   | `chmod +x setup.sh run.sh` 후 `./setup.sh` |

- 가상환경 `venv` 생성 및 `pip install -r requirements.txt` 수행
- Python 3.10~3.12 필요 (Windows는 3.12 우선 시도)

### 서버 실행

| OS      | 명령 | 비고 |
|--------|------|------|
| Windows | `run.bat` | 기본 포트 **8001** |
| Windows | `run.bat 8000` | 포트 8000으로 실행 |
| Linux   | `./run.sh` | 기본 포트 **8001** |
| Linux   | `./run.sh 8000` | 포트 8000으로 실행 |

- **Linux에서 처음 한 번:** `chmod +x run.sh` 로 실행 권한 부여
- 가상환경이 없으면 "먼저 setup 실행" 안내가 나옵니다.

---

## 2. 포트 8001로 네트워크 접속

다른 PC나 모바일에서 접속하려면 **포트 8001**로 서버를 띄우고, 방화벽에서 해당 포트를 허용하세요. 위 `run.bat` / `./run.sh` 가 기본 8001을 사용합니다.

### 수동 실행 (환경 변수로 포트 지정)

**PowerShell:**
```powershell
cd c:\Users\GH\Desktop\FaceRecognition\web_attendance\backend
$env:PORT = "8001"
.\venv\Scripts\Activate.ps1
python main.py
```

**CMD:**
```cmd
cd c:\Users\GH\Desktop\FaceRecognition\web_attendance\backend
set PORT=8001
venv\Scripts\activate.bat
python main.py
```

### .env 파일로 포트 고정

`backend` 폴더에 `.env` 파일을 만들고 다음을 넣습니다.

```
PORT=8001
HOST=0.0.0.0
```

이후 서버 실행:
```powershell
cd c:\Users\GH\Desktop\FaceRecognition\web_attendance\backend
.\venv\Scripts\Activate.ps1
python main.py
```

### 접속 주소

- **본인 PC:** http://localhost:8001  
- **같은 네트워크 다른 기기:** http://<이_PC_IP_주소>:8001  

PC IP 확인 (PowerShell): `Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notmatch 'Loopback' }`

### 방화벽

- Windows 방화벽에서 **인바운드** 규칙으로 **TCP 8001** 포트 허용이 필요할 수 있습니다.
- 제어판 → Windows Defender 방화벽 → 고급 설정 → 인바운드 규칙 → 새 규칙 → 포트 → TCP 8001

---

**포트 8000**으로 쓰려면 `run.bat 8000` 또는 `./run.sh 8000` 으로 실행하면 됩니다.

---

## 문제 해결: greenlet DLL 오류 (Windows)

서버 시작 시 아래처럼 나오면:

```text
ValueError: the greenlet library is required to use this function.
DLL load failed while importing _greenlet: 지정된 모듈을 찾을 수 없습니다.
```

**1) Visual C++ 재배포 패키지 설치**

- [Microsoft Visual C++ 재배포 패키지](https://learn.microsoft.com/ko-kr/cpp/windows/latest-supported-vc-redist) 에서 **x64** 용 최신 버전 설치 후 재부팅/터미널 다시 연 뒤 서버 재실행.

**2) greenlet만 다시 설치**

가상환경 활성화 후:

```bat
pip install --force-reinstall --no-cache-dir "greenlet>=3.0.0,<4.0.0"
```

**3) 그래도 안 되면: Microsoft Store Python 대신 python.org Python 사용**

- Microsoft Store로 설치한 Python에서는 위 오류가 자주 납니다.
- [python.org 다운로드](https://www.python.org/downloads/) 에서 Windows용 설치 프로그램으로 **Python 3.12** 설치 (설치 시 "Add Python to PATH" 체크).
- 기존 `venv` 삭제 후 다시 설정:

```bat
cd web_attendance\backend
rmdir /s /q venv
setup.bat
```

이후 `run.bat` 또는 `python -m uvicorn main:app --host 0.0.0.0 --port 8001` 로 실행해 보세요.
