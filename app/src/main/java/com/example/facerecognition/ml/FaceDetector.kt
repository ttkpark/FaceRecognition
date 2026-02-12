package com.example.facerecognition.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

/**
 * ML Kit을 사용한 얼굴 감지 클래스
 */
class FaceDetector {
    
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.1f)
            .enableTracking()
            .build()
    )
    
    /**
     * Bitmap에서 얼굴 감지
     * @return 감지된 얼굴 리스트
     */
    suspend fun detectFaces(bitmap: Bitmap): List<Face> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return detector.process(image).await()
    }
    
    /**
     * 얼굴 영역을 Bitmap으로 자르기
     */
    fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        val x = boundingBox.left.coerceAtLeast(0)
        val y = boundingBox.top.coerceAtLeast(0)
        val width = boundingBox.width().coerceAtMost(bitmap.width - x)
        val height = boundingBox.height().coerceAtMost(bitmap.height - y)
        
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }
    
    /**
     * 얼굴을 정면으로 회전시키기 (선택사항)
     * ML Kit이 회전 각도를 제공하는 경우 사용
     */
    fun rotateFace(bitmap: Bitmap, rotationDegrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * 리소스 해제
     */
    fun close() {
        detector.close()
    }
}
