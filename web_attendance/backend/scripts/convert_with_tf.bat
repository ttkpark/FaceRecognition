@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0.."

echo ============================================================
echo .pb to .tflite 변환 (TensorFlow 사용)
echo ============================================================
echo.
echo TensorFlow는 Python 3.11/3.12만 지원합니다.
echo 현재 시스템 기본 Python이 3.13/3.14이면 변환이 실패합니다.
echo.

set "PB_FILE=%~1"
if "%PB_FILE%"=="" set "PB_FILE=MobileFaceNet_9925_9680.pb"
set "OUT_FILE=models\mobilefacenet.tflite"

if not exist "%PB_FILE%" (
    echo [오류] .pb 파일을 찾을 수 없습니다: %PB_FILE%
    echo.
    echo 사용법: convert_with_tf.bat [경로\모델.pb]
    echo 예: convert_with_tf.bat MobileFaceNet_9925_9680.pb
    exit /b 1
)

:: Python 3.12 시도 (Windows py launcher)
set "PYTHON="
where py >nul 2>&1
if not errorlevel 1 (
    py -3.12 -c "import sys" 2>nul
    if not errorlevel 1 set "PYTHON=py -3.12"
)
if "%PYTHON%"=="" (
    where python3.12 >nul 2>&1
    if not errorlevel 1 set "PYTHON=python3.12"
)

if "%PYTHON%"=="" (
    echo [안내] Python 3.11 또는 3.12가 필요합니다.
    echo.
    echo 1. https://www.python.org/downloads/ 에서 Python 3.12 설치
    echo 2. 설치 시 "Add Python to PATH" 체크
    echo 3. 이 배치 파일을 다시 실행
    echo.
    echo 또는 변환만 Python 3.12 가상환경으로 실행:
    echo   py -3.12 -m venv venv_tf
    echo   venv_tf\Scripts\activate
    echo   pip install tensorflow
    echo   python scripts\convert_pb_to_tflite.py --pb_file "%PB_FILE%" --output %OUT_FILE%
    exit /b 1
)

echo 사용 Python: %PYTHON%
echo 입력: %PB_FILE%
echo 출력: %OUT_FILE%
echo.

:: 전용 가상환경(venv_tf)이 있으면 사용, 없으면 만들어서 사용
if not exist "venv_tf\Scripts\python.exe" (
    echo [1/3] Python 3.12 전용 가상환경 생성 중: venv_tf
    if "%PYTHON%"=="py -3.12" (
        call py -3.12 -m venv venv_tf
    ) else (
        "%PYTHON%" -m venv venv_tf
    )
    if errorlevel 1 (
        echo 가상환경 생성 실패. 위 안내대로 Python 3.12를 설치한 뒤 다시 시도하세요.
        exit /b 1
    )
)

echo [2/3] TensorFlow 설치 확인 중...
venv_tf\Scripts\python.exe -c "import tensorflow" 2>nul
if errorlevel 1 (
    echo TensorFlow 설치 중...
    venv_tf\Scripts\pip.exe install --quiet tensorflow
    venv_tf\Scripts\python.exe -c "import tensorflow" 2>nul
    if errorlevel 1 (
        echo TensorFlow 설치 실패. venv_tf가 Python 3.12로 만들어졌는지 확인하세요.
        exit /b 1
    )
)

echo [3/3] .pb -> .tflite 변환 중...
venv_tf\Scripts\python.exe scripts\convert_pb_to_tflite.py --pb_file "%PB_FILE%" --output %OUT_FILE%
if errorlevel 1 (
    echo 변환 실패. 입력/출력 노드 이름이 다를 수 있습니다.
    exit /b 1
)

echo.
echo 완료. 모델 파일: %OUT_FILE%
echo.
echo 검증 시 백엔드 가상환경을 활성화한 뒤 실행하세요.
echo   venv\Scripts\activate
echo   pip install -r requirements.txt
echo   python -c "from services.face_service import is_ml_ready; print(is_ml_ready())"
exit /b 0
