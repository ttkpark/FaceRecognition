@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

if not exist "venv\Scripts\python.exe" (
    echo [오류] 가상환경이 없습니다. 먼저 setup.bat 을 실행하세요.
    echo   %~dp0setup.bat
    exit /b 1
)

set "PORT=%~1"
if "%PORT%"=="" set "PORT=8001"
set "PORT=%PORT%"

echo 서버 시작: http://0.0.0.0:%PORT% (종료: Ctrl+C)
echo.
call venv\Scripts\activate.bat
python main.py
endlocal
