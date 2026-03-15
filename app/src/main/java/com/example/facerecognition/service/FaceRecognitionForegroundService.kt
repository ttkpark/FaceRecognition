package com.example.facerecognition.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.example.facerecognition.MainActivity
import com.example.facerecognition.R
import com.example.facerecognition.data.AppDatabase
import com.example.facerecognition.data.Attendance
import com.example.facerecognition.ml.FaceDetector
import com.example.facerecognition.ml.FaceMatcher
import com.example.facerecognition.ml.FaceRecognitionEngine
import com.example.facerecognition.ui.FaceImageAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FaceRecognitionForegroundService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val isProcessing = AtomicBoolean(false)
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var recognitionEngine: FaceRecognitionEngine
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceMatcher: FaceMatcher
    private lateinit var appDatabase: AppDatabase

    override fun onCreate() {
        super.onCreate()
        recognitionEngine = FaceRecognitionEngine(application)
        faceDetector = FaceDetector()
        faceMatcher = FaceMatcher(recognitionEngine)
        appDatabase = AppDatabase.getDatabase(application)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_STREAMING) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startCameraAnalysis()
        serviceRunning.update { true }
        return START_STICKY
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor, FaceImageAnalyzer { bitmap ->
                        if (isProcessing.compareAndSet(false, true)) {
                            serviceScope.launch {
                                try {
                                    recognizeAndRecord(bitmap)
                                } finally {
                                    isProcessing.set(false)
                                }
                            }
                        }
                    })
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis
                )
            } catch (_: Exception) {
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private suspend fun recognizeAndRecord(bitmap: android.graphics.Bitmap) {
        withContext(Dispatchers.IO) {
            val faces = faceDetector.detectFaces(bitmap)
            if (faces.isEmpty()) return@withContext

            val faceBitmap = faceDetector.cropFace(bitmap, faces[0].boundingBox)
            val embedding = recognitionEngine.extractEmbedding(faceBitmap) ?: return@withContext

            val personDao = appDatabase.personDao()
            val attendanceDao = appDatabase.attendanceDao()
            val persons = personDao.getAllPersons().first()
            val matchResult = faceMatcher.findBestMatch(embedding, persons) ?: return@withContext

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateFormat.format(Date())
            val hasAttended = attendanceDao.hasAttendedToday(matchResult.person.id, today) > 0

            if (!hasAttended) {
                val attendance = Attendance(
                    personId = matchResult.person.id,
                    date = today
                )
                attendanceDao.insertAttendance(attendance)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "얼굴 인식 스트리밍",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "백그라운드 얼굴 인식 스트리밍 상태를 표시합니다."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FaceRecognitionForegroundService::class.java).apply {
            action = ACTION_STOP_STREAMING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("얼굴 인식 스트리밍 실행 중")
            .setContentText("휴대폰 화면이 꺼져도 계속 인식합니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "중지", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceRunning.update { false }
        serviceScope.cancel()
        cameraExecutor.shutdown()
        recognitionEngine.close()
        faceDetector.close()
    }

    companion object {
        private const val CHANNEL_ID = "face_streaming_channel"
        private const val NOTIFICATION_ID = 1201
        private const val ACTION_STOP_STREAMING =
            "com.example.facerecognition.action.STOP_STREAMING"

        val serviceRunning = MutableStateFlow(false)

        fun start(context: Context) {
            val intent = Intent(context, FaceRecognitionForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FaceRecognitionForegroundService::class.java))
        }
    }
}
