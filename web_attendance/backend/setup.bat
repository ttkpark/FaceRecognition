@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================================
echo Face Recognition Backend - 설치 (Windows)
echo ============================================================
echo.

set "PY=python"
where py >nul 2>&1
if not errorlevel 1 (
    py -3.12 -c "import sys" 2>nul
    if not errorlevel 1 set "PY=py -3.12"
)
if "%PY%"=="python" (
    where python >nul 2>&1
    if errorlevel 1 (
        echo [오류] Python을 찾을 수 없습니다.
        echo https://www.python.org/downloads/ 에서 Python 3.10~3.12 설치 후 "Add to PATH" 체크하세요.
        exit /b 1
    )
)

echo [1/2] 가상환경 생성: venv
if exist "venv\Scripts\python.exe" (
    echo   venv 가 이미 있습니다. 건너뜁니다.
) else (
    "%PY%" -m venv venv
    if errorlevel 1 (
        echo 가상환경 생성 실패. Python 3.10~3.12 인지 확인하세요.
        exit /b 1
    )
)

echo [2/2] 패키지 설치: pip install -r requirements.txt
call venv\Scripts\activate.bat
pip install -r requirements.txt
if errorlevel 1 (
    echo 패키지 설치 실패.
    exit /b 1
)

echo.
echo 설치 완료. 서버 실행: run.bat [포트]
echo   기본 포트 8001: run.bat
echo   다른 포트:       run.bat 8000
echo.
endlocal
