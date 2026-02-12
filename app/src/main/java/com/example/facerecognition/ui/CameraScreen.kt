package com.example.facerecognition.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.facerecognition.viewmodel.FaceRecognitionViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: FaceRecognitionViewModel,
    onNavigateToRegister: () -> Unit = {},
    onNavigateToAttendance: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA
        )
    )
    
    val recognizedPerson by viewModel.recognizedPerson.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!cameraPermissionState.allPermissionsGranted) {
            Text("카메라 권한이 필요합니다.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { cameraPermissionState.launchMultiplePermissionRequest() }) {
                Text("권한 요청")
            }
        } else {
            // 카메라 미리보기
            CameraPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onImageCaptured = { bitmap ->
                    viewModel.recognizeFace(bitmap)
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 인식 결과 표시
            if (isProcessing) {
                CircularProgressIndicator()
                Text("인식 중...")
            } else if (recognizedPerson != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "인식됨: ${recognizedPerson!!.name}",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "출석이 기록되었습니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                Text("얼굴을 카메라에 맞춰주세요")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 네비게이션 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onNavigateToRegister) {
                    Text("사용자 등록")
                }
                Button(onClick = onNavigateToAttendance) {
                    Text("출석 기록")
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var imageAnalysis: ImageAnalysis? by remember { mutableStateOf(null) }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val capture = ImageCapture.Builder()
                    .setTargetRotation(previewView.display.rotation)
                    .build()
                imageCapture = capture
                
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            Executors.newSingleThreadExecutor(),
                            FaceImageAnalyzer { bitmap ->
                                onImageCaptured(bitmap)
                            }
                        )
                    }
                imageAnalysis = analysis
                
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        capture,
                        analysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        }
    )
}

/**
 * ImageAnalysis를 위한 Analyzer
 */
class FaceImageAnalyzer(
    private val onImageCaptured: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {
    
    private var lastAnalyzedTime = 0L
    private val analysisInterval = 500L // 500ms마다 분석
    
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastAnalyzedTime < analysisInterval) {
            imageProxy.close()
            return
        }
        
        lastAnalyzedTime = currentTime
        
        val bitmap = imageProxy.toBitmap()
        onImageCaptured(bitmap)
        
        imageProxy.close()
    }
    
    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        val image = this.image!!
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            width,
            height,
            null
        )
        
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, width, height),
            100,
            out
        )
        val imageBytes = out.toByteArray()
        
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size
        )
        
        // 회전 보정
        val matrix = Matrix().apply {
            postRotate(imageInfo.rotationDegrees.toFloat())
        }
        
        return android.graphics.Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
}
