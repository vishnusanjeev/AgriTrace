package com.simats.agritrace

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface FarmerApi {

    @GET("farmer/home.php")
    suspend fun getHome(): HomeResp

    @POST("farmer/create_crop_batch.php")
    suspend fun createCropBatch(@Body req: CreateCropBatchReq): CreateCropBatchResp

    @GET("farmer/certificate.php")
    suspend fun getCertificate(
        @Query("batch_id") batchId: Int?,
        @Query("batch_code") batchCode: String?
    ): CertificateResp

    @GET("farmer/batch_details.php")
    suspend fun getBatchDetails(@Query("batch_id") batchId: Int): BatchDetailsResp

    @GET("farmer/distributors.php")
    suspend fun getDistributors(): DistributorListResp

    @POST("farmer/transfer_to_distributor.php")
    suspend fun transferToDistributor(@Body req: TransferToDistributorReq): BasicResp
}
