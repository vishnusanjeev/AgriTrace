package com.simats.agritrace

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface DistributorApi {
    @GET("distributor/batches.php")
    suspend fun getAssignedBatches(): DistributorBatchesResp

    @GET("distributor/home.php")
    suspend fun getHomeStats(): DistributorHomeResp

    @GET("distributor/incoming_transfers.php")
    suspend fun getIncomingTransfers(): IncomingTransfersResp

    @GET("distributor/received_products.php")
    suspend fun getReceivedProducts(): ReceivedProductsResp

    @GET("common/retailers_list.php")
    suspend fun getRetailersList(): RetailerListResp

    @GET("distributor/batch_details.php")
    suspend fun getBatchDetails(
        @Query("batch_id") batchId: Int?,
        @Query("batch_code") batchCode: String?
    ): BatchDetailsResp

    @POST("distributor/scan_verify.php")
    suspend fun scanVerify(@Body req: ScanVerifyReq): ScanVerifyResp

    @POST("distributor/confirm_pickup.php")
    suspend fun confirmPickup(@Body req: ConfirmPickupReq): ConfirmPickupResp

    @POST("distributor/update_location.php")
    suspend fun updateLocation(@Body req: UpdateLocationReq): BasicResp

    @POST("distributor/update_transport.php")
    suspend fun updateTransport(@Body req: UpdateTransportReq): BasicResp

    @GET("distributor/history.php")
    suspend fun getHistory(
        @Query("batch_id") batchId: Int?,
        @Query("batch_code") batchCode: String?
    ): DistributorHistoryResp

    @POST("distributor/transfer_accept.php")
    suspend fun acceptTransfer(@Body req: TransferActionReq): BasicResp

    @POST("distributor/transfer_reject.php")
    suspend fun rejectTransfer(@Body req: TransferActionReq): BasicResp

    @POST("distributor/transfer_to_retailer.php")
    suspend fun transferToRetailer(@Body req: TransferToRetailerReq): TransferToRetailerResp
}
