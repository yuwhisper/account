package com.yuwhisper.account.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Required auto-ledger permission steps (SMS excluded). */
enum class PermissionStepId {
    ACCESSIBILITY,
    NOTIFICATION_LISTENER,
    OVERLAY,
    POST_NOTIFICATIONS,
    BATTERY,
}

data class PermissionStep(
    val id: PermissionStepId,
    val title: String,
    val description: String,
    val isGranted: Boolean,
)

sealed class PermissionFixAction {
    data class StartActivity(val intent: Intent) : PermissionFixAction()
    data class RequestPermission(val permission: String) : PermissionFixAction()
    data object AlreadyGranted : PermissionFixAction()
}

/** Shared grant checks for onboarding + settings. */
object AutoLedgerPermissions {

    fun steps(context: Context): List<PermissionStep> = listOf(
        PermissionStep(
            id = PermissionStepId.ACCESSIBILITY,
            title = "无障碍",
            description = "读取微信、支付宝等付款成功页上的金额与商户，作为通知之外的第二捕获通道。",
            isGranted = CaptureAvailability.isAccessibilityEnabled(context),
        ),
        PermissionStep(
            id = PermissionStepId.NOTIFICATION_LISTENER,
            title = "通知使用权",
            description = "读取已开启「识别场景」应用的付款通知，用于自动弹出确认入账。",
            isGranted = CaptureAvailability.isNotificationListenerEnabled(context),
        ),
        PermissionStep(
            id = PermissionStepId.OVERLAY,
            title = "悬浮窗",
            description = "在其他应用上层弹出居中确认卡。部分机型显示为「显示在其他应用的上层」。",
            isGranted = canDrawOverlays(context),
        ),
        PermissionStep(
            id = PermissionStepId.POST_NOTIFICATIONS,
            title = "通知栏权限",
            description = "展示「自动记账运行中」与「待入账」通知。Android 13 及以上需单独授权。",
            isGranted = isPostNotificationsGranted(context),
        ),
        PermissionStep(
            id = PermissionStepId.BATTERY,
            title = "电池白名单 / 后台运行",
            description = "忽略电池优化，并建议在系统设置中允许自启动、锁定后台，降低无障碍与通知监听被杀概率。",
            isGranted = isIgnoringBatteryOptimizations(context),
        ),
    )

    fun isReady(context: Context): Boolean = steps(context).all { it.isGranted }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isPostNotificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun fixAction(context: Context, id: PermissionStepId): PermissionFixAction {
        if (steps(context).first { it.id == id }.isGranted) {
            return PermissionFixAction.AlreadyGranted
        }
        return when (id) {
            PermissionStepId.ACCESSIBILITY -> PermissionFixAction.StartActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            )
            PermissionStepId.NOTIFICATION_LISTENER -> PermissionFixAction.StartActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            )
            PermissionStepId.OVERLAY -> PermissionFixAction.StartActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            PermissionStepId.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionFixAction.RequestPermission(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionFixAction.AlreadyGranted
                }
            }
            PermissionStepId.BATTERY -> {
                val request = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                )
                val canRequest = request.resolveActivity(context.packageManager) != null
                if (canRequest) {
                    PermissionFixAction.StartActivity(request)
                } else {
                    PermissionFixAction.StartActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                }
            }
        }
    }
}
