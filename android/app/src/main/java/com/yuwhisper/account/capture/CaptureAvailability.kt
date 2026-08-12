package com.yuwhisper.account.capture

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

/** Whether notification-listener / accessibility capture channels are usable. */
object CaptureAvailability {

    fun isNotificationListenerEnabled(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun isAccessibilityEnabled(context: Context): Boolean {
        // Live connection is the strongest signal (settings string can lag on some OEMs).
        if (PaymentAccessibilityService.instance != null) return true

        val expected = ComponentName(context, PaymentAccessibilityService::class.java)
        val expectedFlat = expected.flattenToString()
        val enabledFlat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        if (!enabledFlat.isNullOrBlank()) {
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledFlat)
            while (splitter.hasNext()) {
                val raw = splitter.next()
                if (raw.equals(expectedFlat, ignoreCase = true)) return true
                val component = ComponentName.unflattenFromString(raw) ?: continue
                if (component == expected) return true
                if (component.packageName == expected.packageName &&
                    component.className.endsWith("PaymentAccessibilityService")
                ) {
                    return true
                }
            }
        }
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val running = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return running.any { info ->
            val id = info.resolveInfo?.serviceInfo?.let { si ->
                ComponentName(si.packageName, si.name)
            }
            id == expected ||
                (id?.packageName == expected.packageName &&
                    id.className.endsWith("PaymentAccessibilityService"))
        }
    }

    fun isAnyCaptureChannelAvailable(context: Context): Boolean =
        isNotificationListenerEnabled(context) || isAccessibilityEnabled(context)
}
