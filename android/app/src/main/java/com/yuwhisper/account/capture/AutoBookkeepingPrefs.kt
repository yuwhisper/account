package com.yuwhisper.account.capture

import android.content.Context

/** Persists the auto-bookkeeping master switch (settings). */
object AutoBookkeepingPrefs {
    private const val PREFS = "auto_bookkeeping"
    private const val KEY_MASTER_ENABLED = "master_enabled"

    fun isMasterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASTER_ENABLED, false)

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
