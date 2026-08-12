package com.yuwhisper.account.capture

import android.content.Context

/** Persists the auto-bookkeeping master switch, onboarding, and battery-related prefs. */
object AutoBookkeepingPrefs {
    private const val PREFS = "auto_bookkeeping"
    private const val KEY_MASTER_ENABLED = "master_enabled"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    /** Default false: avoid sticky foreground service / constant wake. */
    private const val KEY_SHOW_STATUS_NOTIFICATION = "show_status_notification"

    fun isMasterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASTER_ENABLED, false)

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun isShowStatusNotification(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_STATUS_NOTIFICATION, false)

    fun setShowStatusNotification(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_STATUS_NOTIFICATION, enabled).apply()
    }

    fun isOnboardingCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
            .putBoolean(KEY_ONBOARDING_SEEN, true)
            .apply()
    }

    fun hasSeenOnboarding(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_SEEN, false)

    fun setOnboardingSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply()
    }

    /** First launch, or master on while required permissions incomplete. */
    fun shouldShowPermissionOnboarding(context: Context): Boolean {
        if (!hasSeenOnboarding(context)) return true
        if (isMasterEnabled(context) && !AutoLedgerPermissions.isReady(context)) return true
        return false
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
