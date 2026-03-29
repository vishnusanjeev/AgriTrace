package com.simats.agritrace

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface QrApi {

    @GET("qr/get.php")
    suspend fun getQr(@Query("batch_id") batchId: Int): QrGetResp

    @POST("qr/generate.php")
    suspend fun generateQr(@Body req: QrGenerateReq): QrGetResp
}
