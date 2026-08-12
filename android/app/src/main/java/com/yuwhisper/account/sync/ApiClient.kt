package com.yuwhisper.account.sync

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit/OkHttp factory. Rebuilds when base URL changes; attaches Bearer token.
 */
class ApiClient(
    context: Context,
    private val tokenStore: TokenStore = TokenStore(context),
    private val syncPrefs: SyncPrefs = SyncPrefs(context),
) {
    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var retrofit: Retrofit? = null

    private val authInterceptor = Interceptor { chain ->
        val token = tokenStore.getAccessToken()
        val req = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        chain.proceed(req)
    }

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Synchronized
    private fun retrofit(): Retrofit {
        val base = syncPrefs.getBaseUrl()
        val existing = retrofit
        if (existing != null && cachedBaseUrl == base) {
            return existing
        }
        val created = Retrofit.Builder()
            .baseUrl("$base/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit = created
        cachedBaseUrl = base
        return created
    }

    fun invalidate() {
        synchronized(this) {
            retrofit = null
            cachedBaseUrl = null
        }
    }

    fun authApi(): AuthApi = retrofit().create(AuthApi::class.java)

    fun syncApi(): SyncApi = retrofit().create(SyncApi::class.java)
}
