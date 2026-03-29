package com.simats.agritrace.model

data class Product(
    val batch_id: Int,
    val crop_name: String,
    val variety: String,
    val soil_type: String,
    val status: String,
    val created_at: String
)
