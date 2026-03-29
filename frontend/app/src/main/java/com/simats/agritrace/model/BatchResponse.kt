package com.simats.agritrace.model



data class BatchResponse(
    val success: Boolean,
    val message: String,
    val batch_id: Int?,
    val batch_code: String?
)
