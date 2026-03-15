#!/usr/bin/env bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "============================================================"
echo "Face Recognition Backend - 설치 (Linux)"
echo "============================================================"
echo ""

if ! command -v python3 &>/dev/null; then
    echo "[오류] python3 를 찾을 수 없습니다. 설치 후 다시 실행하세요."
    echo "  Ubuntu/Debian: sudo apt install python3 python3-venv python3-pip"
    exit 1
fi

PYTHON="python3"
if command -v python3.12 &>/dev/null; then
    PYTHON="python3.12"
elif command -v python3.11 &>/dev/null; then
    PYTHON="python3.11"
fi
echo "사용 Python: $PYTHON"

echo "[1/2] 가상환경 생성: venv"
if [[ -f venv/bin/python ]]; then
    echo "  venv 가 이미 있습니다. 건너뜁니다."
else
    "$PYTHON" -m venv venv
fi

echo "[2/2] 패키지 설치: pip install -r requirements.txt"
source venv/bin/activate
pip install -r requirements.txt

echo ""
echo "설치 완료. 서버 실행: ./run.sh [포트]"
echo "  기본 포트 8001: ./run.sh"
echo "  다른 포트:       ./run.sh 8000"
echo ""
