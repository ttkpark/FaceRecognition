#!/usr/bin/env bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "============================================================"
echo "FaceRecognition - 다른 PC에서 최초 설정 (Linux)"
echo "============================================================"
echo ""

echo "[1/2] 백엔드 가상환경 + 패키지 설치"
chmod +x web_attendance/backend/setup.sh web_attendance/backend/run.sh
./web_attendance/backend/setup.sh

echo ""
echo "[2/2] pre-commit 훅 (커밋 시 venv/대용량 파일 차단)"
read -p "설치할까요? [Y/n]: " INSTALL_HOOK
if [[ ! "$INSTALL_HOOK" =~ ^[nN] ]]; then
  chmod +x scripts/install_precommit.sh
  ./scripts/install_precommit.sh || echo "pre-commit 설치 실패. 나중에 scripts/install_precommit.sh 를 수동 실행할 수 있습니다."
else
  echo "건너뜀. 나중에 scripts/install_precommit.sh 로 설치 가능."
fi

echo ""
echo "설정 완료."
echo "- 서버 실행: ./web_attendance/backend/run.sh"
echo "- 자세한 절차: SETUP.md"
echo ""
