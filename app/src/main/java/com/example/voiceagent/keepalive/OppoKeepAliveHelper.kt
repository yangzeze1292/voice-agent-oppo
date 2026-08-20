package com.example.voiceagent.keepalive

import android.app.Notification
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.voiceagent.R
import com.example.voiceagent.VoiceAgentApp
import com.example.voiceagent.service.KeepAliveJobService

object OppoKeepAliveHelper {

    private const val JOB_RECOVERY_ID = 0x5641
    private const val A11Y_DOWN_NOTIF_ID = 2002

    fun isAccessibilityEnabled(ctx: Context, clazz: Class<*>): Boolean {
        val expected = ComponentName(ctx, clazz).flattenToString()
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun isNotificationListenerEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(
            ctx.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any {
            it.equals(
                ComponentName(ctx, "com.example.voiceagent.service.KeepAliveNLService").flattenToString(),
                true
            )
        }
    }

    fun scheduleRecoveryJob(ctx: Context) {
        val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        if (js.getPendingJob(JOB_RECOVERY_ID) != null) return
        val job = JobInfo.Builder(JOB_RECOVERY_ID, ComponentName(ctx, KeepAliveJobService::class.java))
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(true)
            .setRequiresDeviceIdle(false)
            .build()
        js.schedule(job)
    }

    fun postA11yDownNotification(ctx: Context) {
        val pi = PendingIntent.getActivity(
            ctx, 0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(ctx, VoiceAgentApp.CH_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(ctx.getString(R.string.a11y_down_title))
            .setContentText(ctx.getString(R.string.a11y_down_text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(A11Y_DOWN_NOTIF_ID, n) }
    }

    fun openAccessibilitySettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openNotificationListenerSettings(ctx: Context) {
        ctx.startActivity(
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openAppDetailSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun openOppoAutoStart(ctx: Context) {
        val intents = listOf(
            Intent("com.coloros.safecenter.permission.PermissionManagerActivity"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        )
        val target = intents.firstOrNull { it.resolveActivity(ctx.packageManager) != null }
            ?: return openAppDetailSettings(ctx)
        ctx.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
