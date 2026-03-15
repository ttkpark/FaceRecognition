Param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

Set-Location $repoRoot

Write-Host "[1/2] Installing pre-commit..."
python -m pip install pre-commit

Write-Host "[2/2] Registering git hook..."
python -m pre_commit install

Write-Host ""
Write-Host "Done. pre-commit hook is active for this clone."
