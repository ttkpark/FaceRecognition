#!/usr/bin/env bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -f venv/bin/python ]]; then
    echo "[오류] 가상환경이 없습니다. 먼저 setup.sh 를 실행하세요."
    echo "  $SCRIPT_DIR/setup.sh"
    exit 1
fi

PORT="${1:-8001}"
export PORT
echo "서버 시작: http://0.0.0.0:$PORT (종료: Ctrl+C)"
echo ""
source venv/bin/activate
exec python main.py
