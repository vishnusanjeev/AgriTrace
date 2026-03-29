package com.simats.agritrace.models

import com.google.gson.annotations.SerializedName

data class RetailerBatchVerificationResp(
    @SerializedName("ok") val ok: Boolean? = null,
    @SerializedName("error") val error: String? = null,

    @SerializedName("batch_id") val batchId: Long? = null,
    @SerializedName("batch_code") val batchCode: String? = null,

    @SerializedName("crop_name") val cropName: String? = null,
    @SerializedName("quantity_kg") val quantityKg: String? = null,
    @SerializedName("harvest_date") val harvestDate: String? = null,

    @SerializedName("farmer_name") val farmerName: String? = null,
    @SerializedName("distributor_name") val distributorName: String? = null,

    // optional flags/labels depending on your PHP
    @SerializedName("blockchain_status") val blockchainStatus: String? = null,
    @SerializedName("verified_title") val verifiedTitle: String? = null,
    @SerializedName("verified_sub") val verifiedSub: String? = null
)
