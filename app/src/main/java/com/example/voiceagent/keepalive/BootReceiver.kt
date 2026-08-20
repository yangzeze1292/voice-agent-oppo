package com.example.voiceagent.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voiceagent.service.AgentAccessibilityService
import com.example.voiceagent.service.KeepAliveService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            OppoKeepAliveHelper.scheduleRecoveryJob(context)
            val a11yOk = OppoKeepAliveHelper.isAccessibilityEnabled(
                context, AgentAccessibilityService::class.java
            )
            if (a11yOk) {
                context.startForegroundService(Intent(context, KeepAliveService::class.java))
            }
        }
    }
}
