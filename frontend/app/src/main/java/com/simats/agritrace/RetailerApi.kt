package com.simats.agritrace

import com.simats.agritrace.models.RetailerHistoryResp
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RetailerApi {

    @GET("retailer/home.php")
    suspend fun getHome(): RetailerHomeResp

    @GET("retailer/incoming_stock.php")
    suspend fun getIncomingStock(): RetailerIncomingResp

    @GET("retailer/received_inventory.php")
    suspend fun getReceivedInventory(): RetailerInventoryResp

    @GET("retailer/sales_history.php")
    suspend fun getSalesHistory(): RetailerSalesResp

    // ✅ FIX: use DTO model, NOT Activity class name
    @GET("retailer/batch_details.php")
    suspend fun getBatchDetails(
        @Query("batch_id") batchId: Long?,
        @Query("batch_code") batchCode: String?
    ): RetailerBatchDetailsResp

    @GET("retailer/history.php")
    suspend fun getHistory(
        @Query("batch_id") batchId: Long?,
        @Query("batch_code") batchCode: String?
    ): RetailerHistoryResp  // ✅ FIX: retailer history response (avoid mismatched type)

    @POST("retailer/accept_stock.php")
    suspend fun acceptStock(@Body req: TransferActionReq): BasicResp

    @POST("retailer/reject_stock.php")
    suspend fun rejectStock(@Body req: TransferActionReq): BasicResp

    @POST("retailer/mark_available.php")
    suspend fun markAvailable(@Body req: ConfirmPickupReq): BasicResp

    @POST("retailer/mark_sold.php")
    suspend fun markSold(@Body req: ConfirmPickupReq): BasicResp

    @POST("retailer/confirm_receipt.php")
    suspend fun confirmReceipt(@Body req: ConfirmReceiptReq): ConfirmReceiptResp
}
