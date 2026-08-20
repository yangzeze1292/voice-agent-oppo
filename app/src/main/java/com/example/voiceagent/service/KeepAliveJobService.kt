package com.example.voiceagent.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.voiceagent.keepalive.OppoKeepAliveHelper

class KeepAliveJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val a11yOk = OppoKeepAliveHelper.isAccessibilityEnabled(
            this, AgentAccessibilityService::class.java
        )
        if (!a11yOk) {
            OppoKeepAliveHelper.postA11yDownNotification(this)
        } else if (AgentAccessibilityService.instance == null) {
            ContextCompat.startForegroundService(
                this, Intent(this, KeepAliveService::class.java)
            )
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
