package com.example.voiceagent.service

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.voiceagent.MainActivity
import com.example.voiceagent.R
import com.example.voiceagent.VoiceAgentApp
import com.example.voiceagent.util.ScreenCapture
import com.example.voiceagent.util.ServiceBridge

class ScreenCaptureService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (code != Activity.RESULT_OK || data == null) {
            ServiceBridge.tryEmit("[截图] 未获得录屏授权，录屏兜底不可用")
            stopSelf()
            return START_NOT_STICKY
        }
        if (ScreenCapture.start(applicationContext, code, data)) {
            ServiceBridge.tryEmit("[截图] 录屏兜底已启动（无障碍截图失败时自动切换）")
        } else {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ScreenCapture.instance?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, VoiceAgentApp.CH_KEEPALIVE)
            .setContentTitle(getString(R.string.notif_projection_title))
            .setContentText(getString(R.string.notif_projection_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1002
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
    }
}
