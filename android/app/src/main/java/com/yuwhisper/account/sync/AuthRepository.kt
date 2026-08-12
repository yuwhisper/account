package com.yuwhisper.account.sync

import android.content.Context
import com.yuwhisper.account.sync.dto.AuthRequest
import retrofit2.HttpException

class AuthRepository(
    context: Context,
    private val tokenStore: TokenStore = TokenStore(context),
    private val syncPrefs: SyncPrefs = SyncPrefs(context),
    private val apiClient: ApiClient = ApiClient(context, tokenStore, syncPrefs),
) {
    fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    fun currentEmail(): String? = tokenStore.getEmail()

    fun getBaseUrl(): String = syncPrefs.getBaseUrl()

    fun setBaseUrl(url: String) {
        syncPrefs.setBaseUrl(url)
        apiClient.invalidate()
    }

    suspend fun register(email: String, password: String): Result<Unit> {
        return runCatching {
            apiClient.authApi().register(AuthRequest(email.trim(), password))
            login(email, password).getOrThrow()
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapError(it)) },
        )
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        return runCatching {
            val token = apiClient.authApi().login(AuthRequest(email.trim(), password))
            tokenStore.saveSession(token.access_token, email.trim())
            SyncWorker.ensurePeriodic(apiClientContext)
            SyncWorker.enqueueNow(apiClientContext)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapError(it)) },
        )
    }

    fun logout() {
        tokenStore.clear()
        SyncWorker.cancelAll(apiClientContext)
    }

    private val apiClientContext: Context = context.applicationContext

    private fun mapError(t: Throwable): Throwable {
        if (t is HttpException) {
            val body = t.response()?.errorBody()?.string().orEmpty()
            val detail = when (t.code()) {
                401 -> "邮箱或密码错误"
                409 -> "该邮箱已注册"
                else -> body.ifBlank { "HTTP ${t.code()}" }
            }
            return IllegalStateException(detail, t)
        }
        return t
    }
}
