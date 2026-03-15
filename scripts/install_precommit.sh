#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

echo "[1/2] Installing pre-commit..."
python3 -m pip install pre-commit

echo "[2/2] Registering git hook..."
python3 -m pre_commit install

echo ""
echo "Done. pre-commit hook is active for this clone."
