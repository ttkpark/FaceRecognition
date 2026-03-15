@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================================
echo FaceRecognition - 다른 PC에서 최초 설정 (Windows)
echo ============================================================
echo.

echo [1/2] 백엔드 가상환경 + 패키지 설치
call web_attendance\backend\setup.bat
if errorlevel 1 (
    echo 백엔드 설정 실패. Python 3.10~3.12 설치 여부를 확인하세요.
    exit /b 1
)

echo.
cd /d "%~dp0"
echo [2/2] pre-commit 훅 (커밋 시 venv/대용량 파일 차단)
set /p INSTALL_HOOK="설치할까요? [Y/n]: "
if /i "%INSTALL_HOOK%"=="n" goto :done
if /i "%INSTALL_HOOK%"=="no" goto :done
powershell -ExecutionPolicy Bypass -File "scripts\install_precommit.ps1"
if errorlevel 1 (
    echo pre-commit 설치 실패. 나중에 scripts\install_precommit.ps1 을 수동 실행할 수 있습니다.
) else (
    echo pre-commit 훅이 활성화되었습니다.
)

:done
echo.
echo 설정 완료.
echo - 서버 실행: web_attendance\backend\run.bat
echo - 자세한 절차: SETUP.md
echo.
endlocal
