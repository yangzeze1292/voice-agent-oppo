package com.example.voiceagent.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.voiceagent.MainActivity
import com.example.voiceagent.R
import com.example.voiceagent.VoiceAgentApp
import com.example.voiceagent.keepalive.OppoKeepAliveHelper
import com.example.voiceagent.util.ServiceBridge

class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var pollStarted = false
    private var lastA11yEnabled: Boolean? = null
    private var lastAliveNotice = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPolling()
        OppoKeepAliveHelper.scheduleRecoveryJob(this)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        pollStarted = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPolling() {
        if (pollStarted) return
        pollStarted = true
        handler.post(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkHealth()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun checkHealth() {
        val enabled = OppoKeepAliveHelper.isAccessibilityEnabled(this, AgentAccessibilityService::class.java)
        val alive = AgentAccessibilityService.instance != null
        if (lastA11yEnabled != enabled) {
            lastA11yEnabled = enabled
            if (!enabled) {
                ServiceBridge.tryEmit("[保活] 检测到无障碍服务被关闭，请在设置中重新开启")
                OppoKeepAliveHelper.postA11yDownNotification(this)
            } else {
                ServiceBridge.tryEmit("[保活] 无障碍服务已开启")
            }
        } else if (enabled && !alive && !lastAliveNotice) {
            lastAliveNotice = true
            ServiceBridge.tryEmit("[保活] 无障碍服务失活，等待系统重新绑定…")
        } else if (enabled && alive) {
            lastAliveNotice = false
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, VoiceAgentApp.CH_KEEPALIVE)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1001
        private const val POLL_INTERVAL_MS = 30_000L
    }
}
