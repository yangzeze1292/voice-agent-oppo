package com.example.voiceagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.voiceagent.keepalive.OppoKeepAliveHelper
import com.example.voiceagent.service.AgentAccessibilityService
import com.example.voiceagent.service.KeepAliveService
import com.example.voiceagent.service.ScreenCaptureService
import com.example.voiceagent.ui.screen.MainScreen
import com.example.voiceagent.ui.screen.PermissionGuideScreen
import com.example.voiceagent.ui.theme.VoiceAgentTheme
import com.example.voiceagent.util.ServiceBridge

class MainActivity : ComponentActivity() {

    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val i = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, i)
        } else {
            ServiceBridge.tryEmit("[截图] 用户取消录屏授权，录屏兜底不可用")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestMissingRuntimePermissions()
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))

        setContent {
            VoiceAgentTheme {
                val a11yOk = OppoKeepAliveHelper.isAccessibilityEnabled(
                    this, AgentAccessibilityService::class.java
                )
                val nlOk = OppoKeepAliveHelper.isNotificationListenerEnabled(this)
                if (a11yOk && nlOk) {
                    MainScreen(
                        vm = viewModel(),
                        onRequestProjection = ::requestProjection
                    )
                } else {
                    PermissionGuideScreen(onAllDone = { recreate() })
                }
            }
        }
    }

    private fun requestProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun requestMissingRuntimePermissions() {
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isNotEmpty()) runtimePermissions.launch(missing.toTypedArray())
    }
}
