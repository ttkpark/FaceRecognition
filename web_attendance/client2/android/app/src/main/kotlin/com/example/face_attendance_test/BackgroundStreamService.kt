package com.example.face_attendance_test

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.PowerManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class BackgroundStreamService : LifecycleService() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val lastSentAt = AtomicLong(0L)
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentWsUrl: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:stream-lock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopStreaming()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val wsUrl = intent.getStringExtra(EXTRA_WS_URL)
                if (wsUrl.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                currentWsUrl = wsUrl
                startForeground(NOTIFICATION_ID, buildNotification())
                if (wakeLock?.isHeld != true) {
                    wakeLock?.acquire(10 * 60 * 60 * 1000L)
                }
                connectWebSocket(wsUrl)
                startCameraAnalysis()
                return START_STICKY
            }
        }
        return START_STICKY
    }

    private fun connectWebSocket(wsUrl: String) {
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = OkHttpClient.Builder()
            .pingInterval(10, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // no-op
            }
        })
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor) { image ->
                trySendFrame(image)
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
            } catch (_: Exception) {
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun trySendFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastSentAt.get() < FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastSentAt.set(now)

        val bytes = imageProxy.toJpegBytes()
        imageProxy.close()
        if (bytes.isNotEmpty()) {
            webSocket?.send(bytes.toByteString())
        }
    }

    private fun ImageProxy.toJpegBytes(): ByteArray {
        val image = image ?: return ByteArray(0)
        if (image.planes.size < 3) return ByteArray(0)

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

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            width,
            height,
            null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, out)
        return out.toByteArray()
    }

    private fun stopStreaming() {
        webSocket?.close(1000, "stop")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = null
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("스트리밍 실행 중")
        .setContentText("화면이 꺼져도 전송을 유지합니다.")
        .setSmallIcon(android.R.drawable.presence_video_online)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            0,
            "중지",
            PendingIntent.getService(
                this,
                1,
                Intent(this, BackgroundStreamService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Stream",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopStreaming()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.face_attendance_test.STREAM_START"
        const val ACTION_STOP = "com.example.face_attendance_test.STREAM_STOP"
        const val EXTRA_WS_URL = "extra_ws_url"
        private const val CHANNEL_ID = "stream_channel"
        private const val NOTIFICATION_ID = 4112
        private const val FRAME_INTERVAL_MS = 500L
    }
}
