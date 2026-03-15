#!/usr/bin/env python3
"""
.pb 파일을 .tflite로 변환하는 스크립트

사용법:
    python convert_pb_to_tflite.py --pb_file MobileFaceNet_9925_9680.pb --output mobilefacenet.tflite
"""
import argparse
import sys
from pathlib import Path

try:
    import tensorflow as tf
except ImportError:
    print("오류: TensorFlow가 설치되어 있지 않습니다.")
    print()
    print("TensorFlow는 Python 3.11, 3.12만 지원합니다. (3.13/3.14 미지원)")
    print()
    print("해결 방법:")
    print("  1) Python 3.12 설치 후 전용 가상환경에서 변환:")
    print("     py -3.12 -m venv venv_tf")
    print("     venv_tf\\Scripts\\activate")
    print("     pip install tensorflow")
    print("     python scripts/convert_pb_to_tflite.py --pb_file ... --output ...")
    print()
    print("  2) 또는 배치 파일 사용 (Python 3.12 있으면 자동으로 venv 생성):")
    print("     scripts\\convert_with_tf.bat MobileFaceNet_9925_9680.pb")
    sys.exit(1)


def convert_pb_to_tflite(pb_file: Path, output_file: Path, 
                         input_name: str = "input",
                         input_shape: str = "1,112,112,3",
                         output_name: str = "embeddings"):
    """
    .pb 파일을 .tflite로 변환합니다.
    
    Args:
        pb_file: 입력 .pb 파일 경로
        output_file: 출력 .tflite 파일 경로
        input_name: 입력 노드 이름
        input_shape: 입력 형태 (예: "1,112,112,3")
        output_name: 출력 노드 이름
    """
    if not pb_file.exists():
        print(f"오류: 파일을 찾을 수 없습니다: {pb_file}")
        return False
    
    print(f"변환 중: {pb_file} -> {output_file}")
    print(f"입력: {input_name}, 형태: {input_shape}")
    print(f"출력: {output_name}")
    
    try:
        # TensorFlow 2.x 사용
        if hasattr(tf.compat.v1, 'GraphDef'):
            # TF 2.x에서 TF 1.x 변환기 사용
            converter = tf.compat.v1.lite.TFLiteConverter.from_frozen_graph(
                str(pb_file),
                input_arrays=[input_name],
                input_shapes={input_name: [int(x) for x in input_shape.split(",")]},
                output_arrays=[output_name]
            )
        else:
            # TF 1.x 스타일
            converter = tf.lite.TFLiteConverter.from_frozen_graph(
                str(pb_file),
                input_arrays=[input_name],
                input_shapes={input_name: [int(x) for x in input_shape.split(",")]},
                output_arrays=[output_name]
            )
        
        tflite_model = converter.convert()
        
        # 출력 디렉토리 생성
        output_file.parent.mkdir(parents=True, exist_ok=True)
        
        # 파일 저장
        with open(output_file, 'wb') as f:
            f.write(tflite_model)
        
        print("[완료] 변환 완료:", output_file)
        print(f"  파일 크기: {output_file.stat().st_size / 1024 / 1024:.2f} MB")
        return True
        
    except Exception as e:
        print("[실패] 변환 실패:", e)
        print()
        print("문제 해결:")
        print("1. 입력/출력 노드 이름 확인:")
        print("   python -c \"import tensorflow as tf; gf = tf.compat.v1.GraphDef(); gf.ParseFromString(open('model.pb', 'rb').read()); print([n.name for n in gf.node])\"")
        print()
        print("2. TensorFlow 버전 확인:")
        print(f"   현재 버전: {tf.__version__}")
        print()
        print("3. 대안: TensorFlow 1.x 사용 또는 SavedModel 형식으로 변환 후 변환")
        return False


def main():
    parser = argparse.ArgumentParser(description='Convert .pb to .tflite')
    parser.add_argument('--pb_file', required=True, help='Input .pb file path')
    parser.add_argument('--output', required=True, help='Output .tflite file path')
    parser.add_argument('--input_name', default='input', help='Input node name (default: input)')
    parser.add_argument('--input_shape', default='1,112,112,3', help='Input shape (default: 1,112,112,3)')
    parser.add_argument('--output_name', default='embeddings', help='Output node name (default: embeddings)')
    
    args = parser.parse_args()
    
    pb_file = Path(args.pb_file)
    output_file = Path(args.output)
    
    success = convert_pb_to_tflite(
        pb_file, output_file,
        args.input_name, args.input_shape, args.output_name
    )
    
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
