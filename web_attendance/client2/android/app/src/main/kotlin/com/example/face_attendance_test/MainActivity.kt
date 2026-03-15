package com.example.face_attendance_test

import android.content.Intent
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "startBackgroundStream" -> {
                    val baseUrl = call.argument<String>("baseUrl")
                    val wsUrl = toWsUrl(baseUrl)
                    if (wsUrl == null) {
                        result.error("INVALID_URL", "baseUrl이 올바르지 않습니다.", null)
                        return@setMethodCallHandler
                    }

                    val intent = Intent(this, BackgroundStreamService::class.java).apply {
                        action = BackgroundStreamService.ACTION_START
                        putExtra(BackgroundStreamService.EXTRA_WS_URL, wsUrl)
                    }
                    ContextCompat.startForegroundService(this, intent)
                    result.success(true)
                }

                "stopBackgroundStream" -> {
                    val intent = Intent(this, BackgroundStreamService::class.java).apply {
                        action = BackgroundStreamService.ACTION_STOP
                    }
                    startService(intent)
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        }
    }

    private fun toWsUrl(baseUrl: String?): String? {
        if (baseUrl.isNullOrBlank()) return null
        var trimmed = baseUrl.trim().removeSuffix("/")
        val secure = trimmed.startsWith("https://")
        trimmed = trimmed.replaceFirst(Regex("^https?://"), "")
        return "${if (secure) "wss" else "ws"}://$trimmed/ws/stream"
    }

    companion object {
        private const val CHANNEL = "background_stream"
    }
}
