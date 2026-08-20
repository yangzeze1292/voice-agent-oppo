package com.example.voiceagent.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import com.example.voiceagent.keepalive.OppoKeepAliveHelper
import com.example.voiceagent.util.ServiceBridge

class KeepAliveNLService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        ServiceBridge.tryEmit("[保活] NotificationListener 已连接")
        OppoKeepAliveHelper.scheduleRecoveryJob(this)
        if (AgentAccessibilityService.instance == null) {
            ServiceBridge.tryEmit("[保活] 无障碍服务未运行，启动保活前台服务")
            ContextCompat.startForegroundService(
                this, Intent(this, KeepAliveService::class.java)
            )
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
    }
}
