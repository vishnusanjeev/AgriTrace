package com.simats.agritrace.model

data class CustomerLoginResponse(
    val status: Boolean,
    val message: String,
    val data: CustomerData? = null
)

data class CustomerData(
    val customer_id: String,
    val full_name: String,
    val phone: String,
    val email: String
)
