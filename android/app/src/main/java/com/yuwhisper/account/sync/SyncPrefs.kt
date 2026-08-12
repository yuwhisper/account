package com.yuwhisper.account.sync

import android.content.Context

/** Configurable API base URL (emulator default: 10.0.2.2). */
class SyncPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBaseUrl(): String {
        val raw = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty().trim()
        return normalizeBaseUrl(if (raw.isEmpty()) DEFAULT_BASE_URL else raw)
    }

    fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, normalizeBaseUrl(url)).apply()
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"

        private const val PREFS_NAME = "account_sync_prefs"
        private const val KEY_BASE_URL = "base_url"

        fun normalizeBaseUrl(url: String): String {
            val trimmed = url.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
        }
    }
}
