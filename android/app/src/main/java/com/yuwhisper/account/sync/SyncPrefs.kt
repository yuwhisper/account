package com.yuwhisper.account.sync

import android.content.Context
import java.net.URI

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
            val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
            val uri = runCatching { URI(normalized) }
                .getOrElse { throw IllegalArgumentException("服务器地址格式不正确") }
            val host = uri.host?.lowercase()
                ?: throw IllegalArgumentException("服务器地址缺少主机名")
            require(uri.userInfo == null && uri.fragment == null && uri.query == null) {
                "服务器地址不能包含账号、参数或片段"
            }
            val localDevHost = host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
            require(uri.scheme == "https" || (uri.scheme == "http" && localDevHost)) {
                "远程服务器必须使用 HTTPS"
            }
            return normalized
        }
    }
}
