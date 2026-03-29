package com.simats.agritrace.model

data class FarmerBatchesResponse(
    val success: Boolean,
    val batches: List<FarmerBatch>
)