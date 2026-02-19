#!/usr/bin/env python3
"""
MobileFaceNet 모델 다운로드 및 변환 스크립트

이 스크립트는 MobileFaceNet 모델을 다운로드하고 .tflite 형식으로 변환합니다.
"""
import os
import sys
import urllib.request
from pathlib import Path

# 프로젝트 루트 경로
BASE_DIR = Path(__file__).resolve().parent.parent
MODELS_DIR = BASE_DIR / "models"
TARGET_FILE = MODELS_DIR / "mobilefacenet.tflite"


def download_file(url: str, dest_path: Path):
    """파일을 다운로드합니다."""
    print(f"다운로드 중: {url}")
    print(f"저장 위치: {dest_path}")
    
    try:
        urllib.request.urlretrieve(url, dest_path)
        print(f"[성공] 다운로드 완료: {dest_path}")
        return True
    except Exception as e:
        print(f"[실패] 다운로드 실패: {e}")
        return False


def main():
    """메인 함수"""
    # models 디렉토리 생성
    MODELS_DIR.mkdir(exist_ok=True)
    
    # 이미 파일이 있으면 스킵
    if TARGET_FILE.exists():
        print(f"[확인] 모델 파일이 이미 존재합니다: {TARGET_FILE}")
        print(f"  파일 크기: {TARGET_FILE.stat().st_size / 1024 / 1024:.2f} MB")
        return
    
    print("=" * 60)
    print("MobileFaceNet 모델 다운로드")
    print("=" * 60)
    print()
    print("다음 방법 중 하나를 선택하세요:")
    print()
    print("방법 1: GitHub에서 직접 다운로드 (권장)")
    print("  - sirius-ai/MobileFaceNet_TF 저장소의 변환된 모델 사용")
    print("  - 또는 다른 공개 저장소에서 다운로드")
    print()
    print("방법 2: .pb 파일 다운로드 후 변환")
    print("  - MobileFaceNet_9925_9680.pb 다운로드")
    print("  - TensorFlow Lite Converter로 변환")
    print()
    print("방법 3: 수동 다운로드")
    print("  - 다음 링크에서 모델 파일을 다운로드하세요:")
    print("    https://github.com/sirius-ai/MobileFaceNet_TF/tree/master/arch/pretrained_model")
    print("  - 또는 다른 MobileFaceNet TFLite 모델 검색")
    print()
    print("=" * 60)
    print()
    print("[경고] 자동 다운로드는 현재 지원되지 않습니다.")
    print("   다음 단계를 따라주세요:")
    print()
    print("1. 모델 파일 다운로드:")
    print("   - GitHub: https://github.com/sirius-ai/MobileFaceNet_TF")
    print("   - 또는 다른 소스에서 mobilefacenet.tflite 파일 검색")
    print()
    print("2. 파일을 다음 위치에 복사:")
    print(f"   {TARGET_FILE}")
    print()
    print("3. 모델 사양 확인:")
    print("   - 입력: 112×112×3 RGB")
    print("   - 출력: 128차원 또는 192차원 임베딩")
    print("   - 전처리: (이미지 - 127.5) / 128.0")
    print()
    print("4. 테스트:")
    print(f"   cd {BASE_DIR}")
    print("   python -c \"from services.face_service import is_ml_ready; print(is_ml_ready())\"")
    print()


if __name__ == "__main__":
    main()
