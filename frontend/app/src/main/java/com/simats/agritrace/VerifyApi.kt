package com.simats.agritrace

import retrofit2.http.GET
import retrofit2.http.Query

interface VerifyApi {
    @GET("verify/batch.php")
    suspend fun verifyBatch(@Query("qr_payload") qrPayload: String): VerifyBatchResp
}
