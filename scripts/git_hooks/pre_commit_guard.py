#!/usr/bin/env python3
"""Guard staged files from runtime artifacts and oversized blobs."""

from __future__ import annotations

import re
import subprocess
import sys
from dataclasses import dataclass

MAX_BYTES = 50 * 1024 * 1024  # 50 MB

FORBIDDEN_RULES = [
    (re.compile(r"(^|/)(venv|\.venv)/"), "virtual environment files"),
    (re.compile(r"(^|/)__pycache__/"), "python bytecode cache"),
    (re.compile(r"\.py[co]$"), "python compiled bytecode"),
    (re.compile(r"^web_attendance/backend/attendance\.db$"), "local sqlite database"),
]


@dataclass
class Violation:
    path: str
    reason: str


def run_git(args: list[str]) -> str:
    result = subprocess.run(
        ["git", *args],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        msg = result.stderr.strip() or result.stdout.strip() or f"git {' '.join(args)} failed"
        raise RuntimeError(msg)
    return result.stdout


def staged_paths() -> list[str]:
    raw = run_git(["diff", "--cached", "--name-only", "-z"])
    return [p for p in raw.split("\x00") if p]


def staged_blob_size(path: str) -> int:
    out = run_git(["cat-file", "-s", f":{path}"]).strip()
    return int(out)


def main() -> int:
    try:
        paths = staged_paths()
    except RuntimeError as exc:
        print(f"[pre-commit] failed to inspect staged files: {exc}")
        return 1

    if not paths:
        return 0

    violations: list[Violation] = []

    for path in paths:
        normalized = path.replace("\\", "/")

        for pattern, reason in FORBIDDEN_RULES:
            if pattern.search(normalized):
                violations.append(Violation(path=normalized, reason=reason))
                break

        try:
            size = staged_blob_size(path)
        except Exception:
            # Deleted files or special cases can fail size lookup; skip those.
            continue

        if size > MAX_BYTES:
            size_mb = size / (1024 * 1024)
            violations.append(
                Violation(
                    path=normalized,
                    reason=f"file too large ({size_mb:.2f} MB > 50.00 MB)",
                )
            )

    if not violations:
        return 0

    print("[pre-commit] commit blocked.")
    print("The following staged files are not allowed:")
    for v in violations:
        print(f"  - {v.path}: {v.reason}")

    print("\nHow to fix:")
    print("  1) Unstage/remove these files from git index")
    print("     git rm -r --cached <path>")
    print("  2) Add ignore rules in .gitignore if needed")
    print("  3) Re-add valid files and commit again")
    return 1


if __name__ == "__main__":
    sys.exit(main())
