package com.example.facerecognition.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * MobileFaceNet을 사용한 얼굴 인식 엔진
 * 
 * 모델 파일: assets/mobilefacenet.tflite
 * 입력 크기: 112x112x3 (RGB)
 * 출력 크기: 192차원 벡터 (또는 128차원, 모델에 따라 다름)
 */
class FaceRecognitionEngine(context: Context) {
    
    private var interpreter: Interpreter? = null
    private val inputImageSize = 112 // MobileFaceNet 입력 크기
    private val embeddingSize = 192 // 출력 벡터 차원 (모델에 따라 조정 필요)
    
    init {
        try {
            val modelBuffer = loadModelFile(context, "mobilefacenet.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4) // 멀티스레드 사용
                // GPU 가속 사용 시 (선택사항)
                // setUseXNNPACK(true)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * assets 폴더에서 모델 파일 로드
     */
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * 얼굴 이미지에서 임베딩 벡터 추출
     * @param faceBitmap 얼굴 이미지 (크기 무관, 내부에서 112x112로 리사이즈)
     * @return 192차원 임베딩 벡터 (정규화됨)
     */
    fun extractEmbedding(faceBitmap: Bitmap): FloatArray? {
        val interpreter = this.interpreter ?: return null
        
        // 1. 얼굴 이미지를 112x112로 리사이즈 및 전처리
        val resizedBitmap = Bitmap.createScaledBitmap(
            faceBitmap,
            inputImageSize,
            inputImageSize,
            true
        )
        
        // 2. ByteBuffer로 변환 (RGB, 정규화: -1.0 ~ 1.0)
        val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)
        
        // 3. 출력 버퍼 준비
        val outputBuffer = Array(1) { FloatArray(embeddingSize) }
        
        // 4. 추론 실행
        interpreter.run(inputBuffer, outputBuffer)
        
        // 5. 벡터 정규화 (L2 normalization)
        val embedding = outputBuffer[0]
        normalizeVector(embedding)
        
        return embedding
    }
    
    /**
     * Bitmap을 ByteBuffer로 변환 (RGB, 정규화: -1.0 ~ 1.0)
     */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputImageSize * inputImageSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()
        
        val intValues = IntArray(inputImageSize * inputImageSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var pixel = 0
        for (i in 0 until inputImageSize) {
            for (j in 0 until inputImageSize) {
                val value = intValues[pixel++]
                
                // RGB 추출 및 정규화 (-1.0 ~ 1.0)
                inputBuffer.putFloat(((value shr 16 and 0xFF) - 127.5f) / 128.0f) // R
                inputBuffer.putFloat(((value shr 8 and 0xFF) - 127.5f) / 128.0f)  // G
                inputBuffer.putFloat(((value and 0xFF) - 127.5f) / 128.0f)        // B
            }
        }
        
        return inputBuffer
    }
    
    /**
     * 벡터를 L2 정규화 (유클리드 거리 계산을 위해)
     */
    private fun normalizeVector(vector: FloatArray) {
        var sum = 0f
        for (value in vector) {
            sum += value * value
        }
        val norm = sqrt(sum)
        if (norm > 0) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
    }
    
    /**
     * 두 임베딩 벡터 간의 코사인 유사도 계산
     * @return 0.0 ~ 1.0 (1.0에 가까울수록 동일인)
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) { "벡터 크기가 일치하지 않습니다." }
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        
        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
    
    /**
     * 두 임베딩 벡터 간의 유클리드 거리 계산
     * @return 거리 값 (작을수록 동일인, 보통 1.0 이하면 동일인으로 판단)
     */
    fun euclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) { "벡터 크기가 일치하지 않습니다." }
        
        var sum = 0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
    
    /**
     * 리소스 해제
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
