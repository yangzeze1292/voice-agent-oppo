package com.example.voiceagent.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.voiceagent.keepalive.OppoKeepAliveHelper
import com.example.voiceagent.service.AgentAccessibilityService

@Composable
fun PermissionGuideScreen(onAllDone: () -> Unit) {
    val ctx = LocalContext.current
    var a11yOk by remember { mutableStateOf(OppoKeepAliveHelper.isAccessibilityEnabled(ctx, AgentAccessibilityService::class.java)) }
    var nlOk by remember { mutableStateOf(OppoKeepAliveHelper.isNotificationListenerEnabled(ctx)) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("OPPO ColorOS 16 权限引导", style = MaterialTheme.typography.headlineSmall)
        Text("为确保锁屏后无障碍服务不被杀死，请按顺序完成以下步骤。", style = MaterialTheme.typography.bodyMedium)

        GuideStep(
            title = "1. 开启无障碍服务",
            done = a11yOk,
            action = { OppoKeepAliveHelper.openAccessibilitySettings(ctx) },
            onCheck = { a11yOk = OppoKeepAliveHelper.isAccessibilityEnabled(ctx, AgentAccessibilityService::class.java) }
        )
        GuideStep(
            title = "2. 开启通知访问权限（保活兜底）",
            done = nlOk,
            action = { OppoKeepAliveHelper.openNotificationListenerSettings(ctx) },
            onCheck = { nlOk = OppoKeepAliveHelper.isNotificationListenerEnabled(ctx) }
        )
        GuideStep(
            title = "3. 自启动管理 → 允许本应用",
            done = false,
            action = { OppoKeepAliveHelper.openOppoAutoStart(ctx) },
            onCheck = null
        )
        GuideStep(
            title = "4. 电池 → 耗电保护 → 关闭后台冻结 + 异常耗电优化",
            done = false,
            action = { OppoKeepAliveHelper.openAppDetailSettings(ctx) },
            onCheck = null
        )
        GuideStep(
            title = "5. 应用管理 → 冷冻室 → 移除本应用",
            done = false,
            action = { OppoKeepAliveHelper.openAppDetailSettings(ctx) },
            onCheck = null
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onAllDone,
            enabled = a11yOk && nlOk,
            modifier = Modifier.fillMaxWidth()
        ) { Text("完成，进入应用") }
    }
}

@Composable
private fun GuideStep(
    title: String,
    done: Boolean,
    action: () -> Unit,
    onCheck: (() -> Unit)? = null
) {
    val color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    Surface(color = color, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = { action() }) { Text("去设置") }
            if (onCheck != null) TextButton(onClick = onCheck) { Text("检查") }
        }
    }
}
