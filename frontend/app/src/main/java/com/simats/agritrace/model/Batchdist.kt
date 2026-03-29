package com.simats.agritrace.model

data class Batchdist(
    val success: Boolean,
    val batch_id: Int,
    val farmer_id: Int,
    val crop_name: String,
    val variety: String,
    val soil_type: String,
    val country: String,
    val state: String,
    val district: String,
    val pincode: String,
    val batch_code: String,
    val status: String,
    val created_at: String,
    val message: String? = null
)
