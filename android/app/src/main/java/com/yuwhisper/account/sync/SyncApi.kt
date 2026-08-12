package com.yuwhisper.account.sync

import com.yuwhisper.account.sync.dto.CategoryCreateRequest
import com.yuwhisper.account.sync.dto.CategoryDto
import com.yuwhisper.account.sync.dto.CategoryUpdateRequest
import com.yuwhisper.account.sync.dto.PushOkResponse
import com.yuwhisper.account.sync.dto.TransactionDto
import com.yuwhisper.account.sync.dto.TransactionPullResponse
import com.yuwhisper.account.sync.dto.TransactionPushRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SyncApi {
    @GET("/api/categories")
    suspend fun listCategories(): List<CategoryDto>

    @POST("/api/categories")
    suspend fun createCategory(@Body body: CategoryCreateRequest): CategoryDto

    @PATCH("/api/categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: Long,
        @Body body: CategoryUpdateRequest,
    ): CategoryDto

    @DELETE("/api/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Long)

    @POST("/api/transactions/sync/push")
    suspend fun pushTransactions(@Body body: TransactionPushRequest): PushOkResponse

    @GET("/api/transactions/sync/pull")
    suspend fun pullTransactions(@Query("since") since: String): TransactionPullResponse

    @GET("/api/transactions")
    suspend fun listTransactions(): List<TransactionDto>
}
