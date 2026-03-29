package com.simats.agritrace

import retrofit2.http.GET
import retrofit2.http.Query

interface ConsumerApi {
    @GET("consumer/verify_qr.php")
    suspend fun verifyQr(@Query("qr_payload") qrPayload: String): ConsumerVerifyResp

    @GET("consumer/batch_details.php")
    suspend fun batchDetails(
        @Query("batch_id") batchId: Long? = null,
        @Query("batch_code") batchCode: String? = null
    ): ConsumerVerifyResp

    @GET("consumer/recent_scans.php")
    suspend fun recentScans(): ConsumerRecentResp

    @GET("consumer/scan_history.php")
    suspend fun scanHistory(@Query("q") query: String? = null): ConsumerRecentResp

    @GET("consumer/full_journey.php")
    suspend fun fullJourney(
        @Query("batch_id") batchId: Long? = null,
        @Query("batch_code") batchCode: String? = null
    ): ConsumerJourneyResp
}
