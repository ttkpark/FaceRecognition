package com.example.facerecognition.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
fun RegisterScreen(
    viewModel: FaceRecognitionViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA)
    )
    
    var name by remember { mutableStateOf("") }
    val capturedImages = remember { mutableStateListOf<Bitmap>() }
    val isProcessing by viewModel.isProcessing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "사용자 등록",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 이름 입력
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("이름") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (!cameraPermissionState.allPermissionsGranted) {
            Button(onClick = { cameraPermissionState.launchMultiplePermissionRequest() }) {
                Text("카메라 권한 요청")
            }
        } else {
            // 카메라 미리보기
            CameraPreviewForRegister(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                onImageCaptured = { bitmap ->
                    if (capturedImages.size < 5) {
                        capturedImages.add(bitmap)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("촬영된 사진 (최대 5장, 다양한 각도로 촬영하세요)")
            
            // 촬영된 사진 목록
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(capturedImages) { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured face",
                        modifier = Modifier.size(100.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 촬영 버튼
            Button(
                onClick = { /* 촬영은 자동으로 수행됨 */ },
                enabled = capturedImages.size < 5
            ) {
                Text("사진 촬영 (${capturedImages.size}/5)")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 등록 버튼
            Button(
                onClick = {
                    if (name.isNotBlank() && capturedImages.isNotEmpty()) {
                        viewModel.registerPerson(name, capturedImages.toList())
                        name = ""
                        capturedImages.clear()
                    }
                },
                enabled = !isProcessing && name.isNotBlank() && capturedImages.isNotEmpty()
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("등록하기")
                }
            }
            
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(onClick = onBack) {
            Text("뒤로가기")
        }
    }
}

@Composable
fun CameraPreviewForRegister(
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
                            RegisterImageAnalyzer { bitmap ->
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

class RegisterImageAnalyzer(
    private val onImageCaptured: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {
    
    private var lastAnalyzedTime = 0L
    private val analysisInterval = 1000L // 1초마다 분석
    
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
