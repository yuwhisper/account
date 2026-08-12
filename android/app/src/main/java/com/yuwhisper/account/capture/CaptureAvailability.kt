package com.yuwhisper.account.capture

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat

/** Whether notification-listener / accessibility capture channels are usable. */
object CaptureAvailability {

    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, PaymentAccessibilityService::class.java)
        val enabledFlat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledFlat)
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component != null && component == expected) {
                return true
            }
        }
        // Fallback: running services list (some OEMs lag Secure settings).
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val running = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return running.any { info ->
            val id = info.resolveInfo?.serviceInfo?.let { si ->
                ComponentName(si.packageName, si.name)
            }
            id == expected
        }
    }

    fun isAnyCaptureChannelAvailable(context: Context): Boolean =
        isNotificationListenerEnabled(context) || isAccessibilityEnabled(context)
}
