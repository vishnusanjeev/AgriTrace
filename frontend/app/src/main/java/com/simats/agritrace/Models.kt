package com.simats.agritrace

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("full_name") val full_name: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone_e164") val phone_e164: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("email_verified") val email_verified: Boolean = false,
    @SerializedName("location") val location: String? = null,
    @SerializedName("profile_image_url") val profile_image_url: String? = null
)

data class AuthResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("token") val token: String? = null,
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class BasicResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("dev_otp") val dev_otp: String? = null
)

data class LoginReq(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("password") val password: String
)

data class RegisterReq(
    @SerializedName("full_name") val full_name: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone_e164") val phone_e164: String? = null,
    @SerializedName("password") val password: String,
    @SerializedName("role") val role: String
)

data class OtpRequestReq(
    @SerializedName("email") val email: String,
    @SerializedName("purpose") val purpose: String
)

data class OtpVerifyReq(
    @SerializedName("email") val email: String,
    @SerializedName("purpose") val purpose: String,
    @SerializedName("otp") val otp: String
)

data class ResetPasswordReq(
    @SerializedName("email") val email: String,
    @SerializedName("otp") val otp: String,
    @SerializedName("new_password") val new_password: String
)

data class CreateCropBatchReq(
    @SerializedName("crop_name") val crop_name: String,
    @SerializedName("category") val category: String,
    @SerializedName("quantity_kg") val quantity_kg: Double,
    @SerializedName("harvest_date") val harvest_date: String,
    @SerializedName("seed_variety") val seed_variety: String? = null,
    @SerializedName("fertilizers_used") val fertilizers_used: String? = null,
    @SerializedName("irrigation_method") val irrigation_method: String? = null,
    @SerializedName("is_organic") val is_organic: Int = 0
)

data class CreateCropBatchResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("crop") val crop: IdOnly? = null,
    @SerializedName("batch") val batch: BatchDto? = null,
    @SerializedName("snapshot") val snapshot: SnapshotDto? = null,
    @SerializedName("blockchain") val blockchain: BlockchainDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class IdOnly(
    @SerializedName("id") val id: Long = 0L
)

data class BatchDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("status") val status: String? = null
)

data class SnapshotDto(
    @SerializedName("version") val version: Int = 0,
    @SerializedName("hash_hex") val hash_hex: String? = null
)

data class BlockchainDto(
    @SerializedName("event_name") val event_name: String? = null,
    @SerializedName("chain_id") val chain_id: String? = null,
    @SerializedName("contract_address") val contract_address: String? = null,
    @SerializedName("tx_hash") val tx_hash: String? = null,
    @SerializedName("block_number") val block_number: Long? = null,
    @SerializedName("status") val status: String? = null
)

data class HomeStatsDto(
    @SerializedName("active_batches") val active_batches: Int = 0,
    @SerializedName("qr_scans") val qr_scans: Int = 0
)

data class RecentBatchDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("harvest_date") val harvest_date: String? = null,
    @SerializedName("created_at") val created_at: String? = null
)

data class HomeResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("stats") val stats: HomeStatsDto? = null,
    @SerializedName("recent") val recent: List<RecentBatchDto>? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class CertificateBatchDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("harvest_date") val harvest_date: String? = null,
    @SerializedName("seed_variety") val seed_variety: String? = null,
    @SerializedName("fertilizers_used") val fertilizers_used: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("product_type") val product_type: String? = null
)

data class CertificateFarmerDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("location") val location: String? = null
)

data class CertificateChainDto(
    @SerializedName("chain_id") val chain_id: String? = null,
    @SerializedName("tx_hash") val tx_hash: String? = null,
    @SerializedName("block_number") val block_number: Long? = null,
    @SerializedName("block_hash") val block_hash: String? = null,
    @SerializedName("confirmed_at") val confirmed_at: String? = null
)

data class CertificateResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("batch") val batch: CertificateBatchDto? = null,
    @SerializedName("farmer") val farmer: CertificateFarmerDto? = null,
    @SerializedName("blockchain") val blockchain: CertificateChainDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class QrDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("qr_payload") val qr_payload: String? = null,
    @SerializedName("generated_by_user_id") val generated_by_user_id: Long = 0L,
    @SerializedName("created_at") val created_at: String? = null
)

data class QrGetResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("qr") val qr: QrDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class QrGenerateReq(
    @SerializedName("batch_id") val batch_id: Int
)

data class VerifyBatchDto(
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("harvest_date") val harvest_date: String? = null
)

data class VerifyChainDto(
    @SerializedName("chain_id") val chain_id: String? = null,
    @SerializedName("hash_hex") val hash_hex: String? = null
)

data class VerifyScanDto(
    @SerializedName("updated") val updated: Boolean = false
)

data class VerifyBatchResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("status") val status: String? = null,
    @SerializedName("batch") val batch: VerifyBatchDto? = null,
    @SerializedName("chain") val chain: VerifyChainDto? = null,
    @SerializedName("scan") val scan: VerifyScanDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class ScanVerifyReq(
    @SerializedName("qr_payload") val qr_payload: String
)

data class ScanVerifyBatchDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("harvest_date") val harvest_date: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("farmer_name") val farmer_name: String? = null,
    @SerializedName("farmer_location") val farmer_location: String? = null
)

data class ScanVerifyInfoDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("db_hash") val db_hash: String? = null,
    @SerializedName("chain_hash") val chain_hash: String? = null
)

data class ScanVerifyTransferDto(
    @SerializedName("status") val status: String? = null
)

data class ScanVerifyActionsDto(
    @SerializedName("canConfirmPickup") val canConfirmPickup: Boolean = false
)

data class ScanVerifyResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("batch") val batch: ScanVerifyBatchDto? = null,
    @SerializedName("verified") val verified: ScanVerifyInfoDto? = null,
    @SerializedName("transfer") val transfer: ScanVerifyTransferDto? = null,
    @SerializedName("actions") val actions: ScanVerifyActionsDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class ConfirmPickupReq(
    @SerializedName("batch_id") val batch_id: Int
)

data class ConfirmPickupResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("status") val status: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class ConfirmReceiptReq(
    @SerializedName("batch_id") val batch_id: Int,
    @SerializedName("store_location") val store_location: String,
    @SerializedName("date_time") val date_time: String
)

data class ConfirmReceiptResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("status") val status: String? = null,
    @SerializedName("qr_payload") val qr_payload: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class UpdateLocationReq(
    @SerializedName("batch_id") val batch_id: Int,
    @SerializedName("location_text") val location_text: String,
    @SerializedName("temperature_c") val temperature_c: Double? = null,
    @SerializedName("remarks") val remarks: String? = null
)

data class UpdateTransportReq(
    @SerializedName("batch_id") val batch_id: Long,
    @SerializedName("event_time") val event_time: String? = null,
    @SerializedName("vehicle_id") val vehicle_id: String,
    @SerializedName("location_text") val location_text: String,
    @SerializedName("temperature_c") val temperature_c: Double? = null,
    @SerializedName("storage_conditions") val storage_conditions: String? = null
)

data class BatchDetailsDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("harvest_date") val harvest_date: String? = null,
    @SerializedName("seed_variety") val seed_variety: String? = null,
    @SerializedName("fertilizers_used") val fertilizers_used: String? = null,
    @SerializedName("irrigation_method") val irrigation_method: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("product_type") val product_type: String? = null
)

data class BatchDetailsResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("batch") val batch: BatchDetailsDto? = null,
    @SerializedName("farmer") val farmer: CertificateFarmerDto? = null,
    @SerializedName("blockchain") val blockchain: CertificateChainDto? = null,
    @SerializedName("transfer") val transfer: TransferInfoDto? = null,
    @SerializedName("location_update") val location_update: LocationUpdateDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class TransferInfoDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("updated_at") val updated_at: String? = null,
    @SerializedName("to_user_id") val to_user_id: Long? = null,
    @SerializedName("to_user_name") val to_user_name: String? = null,
    @SerializedName("to_user_phone") val to_user_phone: String? = null,
    @SerializedName("transport_updated") val transport_updated: Boolean? = null,
    @SerializedName("retailer_assigned") val retailer_assigned: Boolean? = null
)

data class LocationUpdateDto(
    @SerializedName("location_text") val location_text: String? = null,
    @SerializedName("temperature_c") val temperature_c: Double? = null,
    @SerializedName("remarks") val remarks: String? = null,
    @SerializedName("recorded_at") val recorded_at: String? = null
)

data class DistributorDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("full_name") val full_name: String? = null,
    @SerializedName("phone_e164") val phone_e164: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("location") val location: String? = null
)

data class RetailerDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("full_name") val full_name: String? = null,
    @SerializedName("phone_e164") val phone_e164: String? = null,
    @SerializedName("location") val location: String? = null
)

data class DistributorListResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("items") val items: List<DistributorDto>? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class RetailerListResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("items") val items: List<RetailerDto>? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class TransferToDistributorReq(
    @SerializedName("batch_id") val batch_id: Long,
    @SerializedName("distributor_id") val distributor_id: Long
)

data class TransferToRetailerReq(
    @SerializedName("batch_id") val batch_id: Long,
    @SerializedName("retailer_id") val retailer_id: Long
)

data class TransferToRetailerResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("transfer_id") val transfer_id: Long? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class DistributorBatchItemDto(
    @SerializedName("transfer_id") val transfer_id: Long = 0L,
    @SerializedName("transfer_status") val transfer_status: String? = null,
    @SerializedName("transfer_created_at") val transfer_created_at: String? = null,
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("batch_created_at") val batch_created_at: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null
)

data class DistributorBatchesResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("items") val items: List<DistributorBatchItemDto>? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class DistributorStatsDto(
    @SerializedName("handled") val handled: Int = 0,
    @SerializedName("in_transit") val in_transit: Int = 0,
    @SerializedName("delivered") val delivered: Int = 0,
    @SerializedName("incoming") val incoming: Int = 0,
    @SerializedName("inventory") val inventory: Int = 0
)

data class DistributorHomeResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("stats") val stats: DistributorStatsDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class HistoryMetaDto(
    @SerializedName("location_text") val location_text: String? = null,
    @SerializedName("temperature_c") val temperature_c: Double? = null,
    @SerializedName("remarks") val remarks: String? = null
)

data class HistoryEventDto(
    @SerializedName("source") val source: String? = null,
    @SerializedName("time") val time: String? = null,
    @SerializedName("event_type") val event_type: String? = null,
    @SerializedName("result") val result: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("actor_role") val actor_role: String? = null,
    @SerializedName("meta") val meta: HistoryMetaDto? = null,
    @SerializedName("chain_id") val chain_id: String? = null,
    @SerializedName("tx_hash") val tx_hash: String? = null,
    @SerializedName("block_number") val block_number: Long? = null,
    @SerializedName("confirmed_at") val confirmed_at: String? = null
)

data class DistributorHistoryResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("items") val items: List<HistoryEventDto>? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class IncomingTransferDto(
    @SerializedName("transfer_id") val transfer_id: Long = 0L,
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("farmer_name") val farmer_name: String? = null,
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("tx_hash") val tx_hash: String? = null
)

data class IncomingTransfersResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("data") val data: List<IncomingTransferDto>? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class TransferActionReq(
    @SerializedName("transfer_id") val transfer_id: Long
)

data class ReceivedProductDto(
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("farmer_name") val farmer_name: String? = null,
    @SerializedName("transfer_status") val transfer_status: String? = null,
    @SerializedName("transfer_created_at") val transfer_created_at: String? = null
)

data class ReceivedProductsResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("data") val data: List<ReceivedProductDto>? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class RetailerStatsDto(
    @SerializedName("received") val received: Int = 0,
    @SerializedName("available") val available: Int = 0,
    @SerializedName("sold") val sold: Int = 0,
    @SerializedName("incoming") val incoming: Int = 0
)

data class RetailerRecentBatchDto(
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("harvest_date") val harvest_date: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("created_at") val created_at: String? = null
)

data class RetailerHomeResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("stats") val stats: RetailerStatsDto? = null,
    @SerializedName("recent") val recent: List<RetailerRecentBatchDto>? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class RetailerIncomingResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("items") val items: List<RetailerIncomingItemDto>? = null,
    @SerializedName("error") val error: String? = null
)

data class RetailerIncomingItemDto(
    @SerializedName("transfer_id") val transfer_id: Long = 0L,
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("from_name") val from_name: String? = null,
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("tx_hash") val tx_hash: String? = null
)

data class RetailerInventoryResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("items") val items: List<RetailerInventoryItemDto>? = null,
    @SerializedName("error") val error: String? = null
)

data class RetailerInventoryItemDto(
    @SerializedName("transfer_id") val transfer_id: Long = 0L,
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("transfer_created_at") val transfer_created_at: String? = null,
    @SerializedName("from_name") val from_name: String? = null,
    @SerializedName("farmer_name") val farmer_name: String? = null,
    @SerializedName("batch_status") val batch_status: String? = null
)

data class RetailerSalesResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("items") val items: List<RetailerSaleItemDto>? = null,
    @SerializedName("error") val error: String? = null
)

data class RetailerSaleItemDto(
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("sold_at") val sold_at: String? = null,
    @SerializedName("price") val price: String? = null,

    @SerializedName("status") val status: String? = null,
    @SerializedName("created_at") val created_at: String? = null
)


data class RetailerBatchDetailsBatchDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("harvest_date") val harvest_date: String? = null,
    @SerializedName("status") val status: String? = null   // ✅ ADD THIS
)


data class RetailerBatchDistributorDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("location") val location: String? = null
)

data class RetailerBatchTransferDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("receipt_confirmed") val receipt_confirmed: Boolean? = null
)

data class RetailerBatchDetailsResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("batch") val batch: RetailerBatchDetailsBatchDto? = null,
    @SerializedName("farmer") val farmer: CertificateFarmerDto? = null,
    @SerializedName("distributor") val distributor: RetailerBatchDistributorDto? = null,
    @SerializedName("transfer") val transfer: RetailerBatchTransferDto? = null,
    @SerializedName("blockchain") val blockchain: CertificateChainDto? = null,
    @SerializedName("error") val error: String? = null
)

data class ConsumerVerifyResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("data") val data: ConsumerVerifyDataDto? = null,
    @SerializedName("error") val error: String? = null
)

data class ConsumerVerifyDataDto(
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("quantity_kg") val quantity_kg: String? = null,
    @SerializedName("harvest_date") val harvest_date: String? = null,
    @SerializedName("is_organic") val is_organic: Int = 0,
    @SerializedName("farmer_name") val farmer_name: String? = null,
    @SerializedName("farmer_location") val farmer_location: String? = null,
    @SerializedName("verified") val verified: Boolean = false,
    @SerializedName("verification_reason") val verification_reason: String? = null,
    @SerializedName("chain") val chain: ConsumerChainDto? = null,
    @SerializedName("journey_summary") val journey_summary: List<ConsumerJourneySummaryDto>? = null
)

data class ConsumerChainDto(
    @SerializedName("tx_hash") val tx_hash: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("confirmed_at") val confirmed_at: String? = null
)

data class ConsumerJourneySummaryDto(
    @SerializedName("title") val title: String? = null,
    @SerializedName("subtitle") val subtitle: String? = null,
    @SerializedName("time") val time: String? = null
)

data class ConsumerScanItemDto(
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("scanned_at") val scanned_at: String? = null,
    @SerializedName("result") val result: String? = null
)

data class ConsumerRecentResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("items") val items: List<ConsumerScanItemDto>? = null,
    @SerializedName("error") val error: String? = null
)

data class ConsumerJourneyItemDto(
    @SerializedName("time") val time: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("tag1") val tag1: String? = null,
    @SerializedName("tag2") val tag2: String? = null
)

data class ConsumerJourneyResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("batch") val batch: ConsumerJourneyBatchDto? = null,
    @SerializedName("items") val items: List<ConsumerJourneyItemDto>? = null,
    @SerializedName("error") val error: String? = null
)

data class ConsumerJourneyBatchDto(
    @SerializedName("batch_id") val batch_id: Long = 0L,
    @SerializedName("batch_code") val batch_code: String? = null,
    @SerializedName("crop_name") val crop_name: String? = null,
    @SerializedName("farmer_name") val farmer_name: String? = null,
    @SerializedName("farmer_location") val farmer_location: String? = null
)

data class ProfileUpdateReq(
    @SerializedName("full_name") val full_name: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone_e164") val phone_e164: String? = null,
    @SerializedName("location") val location: String? = null
)

data class ProfileResp(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

