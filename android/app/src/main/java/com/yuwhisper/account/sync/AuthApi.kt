package com.yuwhisper.account.sync

import com.yuwhisper.account.sync.dto.AuthRequest
import com.yuwhisper.account.sync.dto.RegisterResponse
import com.yuwhisper.account.sync.dto.TokenResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("/api/auth/register")
    suspend fun register(@Body body: AuthRequest): RegisterResponse

    @POST("/api/auth/login")
    suspend fun login(@Body body: AuthRequest): TokenResponse
}
