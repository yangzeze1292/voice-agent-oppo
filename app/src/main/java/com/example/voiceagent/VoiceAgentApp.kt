package com.example.voiceagent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class VoiceAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_KEEPALIVE, "保活服务", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_STATUS, "状态通知", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    companion object {
        lateinit var instance: VoiceAgentApp
            private set
        const val CH_KEEPALIVE = "keep_alive"
        const val CH_STATUS = "status"
    }
}
