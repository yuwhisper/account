package com.yuwhisper.account.sync.dto

data class AuthRequest(
    val email: String,
    val password: String,
)

data class TokenResponse(
    val access_token: String,
    val token_type: String = "bearer",
)

data class RegisterResponse(
    val id: Long,
    val email: String,
)
