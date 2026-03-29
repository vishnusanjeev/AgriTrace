package com.simats.agritrace.model

import com.google.gson.annotations.SerializedName

data class FarmerBatch(
    @SerializedName("batch_id") val batchId: String,
    @SerializedName("cropName") val cropName: String,
    @SerializedName("variety") val variety: String,
    @SerializedName("status") val status: String,
    @SerializedName("soil") val soil: String,
    @SerializedName("country") val country: String,
    @SerializedName("state") val state: String,
    @SerializedName("district") val district: String,
    @SerializedName("pincode") val pincode: String,
    @SerializedName("created_at") val createdAt: String
)
